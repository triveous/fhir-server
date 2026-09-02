package ca.uhn.fhir.jpa.starter.observability;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.rest.api.RestOperationTypeEnum;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.exceptions.BaseServerResponseException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

// Per-endpoint HTTP response counter: breaks 4xx/5xx (and 2xx) down by FHIR
// resource type + REST operation + status code, instead of just an aggregate
// error rate.
//
// Why this exists instead of Spring Boot's standard http_server_requests_seconds_count:
// HAPI's RestfulServer is a raw HttpServlet, not a Spring MVC DispatcherServlet
// with @RequestMapping controllers. Micrometer's WebMvcTagsProvider needs a
// matched @RequestMapping route template to fill in the `uri` tag; with none to
// find, it falls back to "UNKNOWN" for almost every real request here.
// Confirmed live against this deployment's /actuator/prometheus output (and a
// Cloud Monitoring metricDescriptors check) before writing this class - do not
// re-litigate that and try to get this from the Spring MVC metric instead.
//
// So we tap HAPI's own interceptor pointcuts directly (ca.uhn.fhir.interceptor.api.Pointcut):
//  - SERVER_OUTGOING_RESPONSE fires on the normal (non-exception) response path.
//    We read the real outgoing status off HttpServletResponse rather than
//    assuming 2xx, since a provider can legitimately set a non-2xx status
//    without throwing.
//  - SERVER_HANDLE_EXCEPTION fires when request handling throws a
//    BaseServerResponseException - this is where most 4xx (e.g.
//    ResourceNotFoundException, InvalidRequestException) and 5xx (e.g.
//    InternalErrorException) responses actually originate.
// Both pointcuts accept RequestDetails as a hook parameter (same accessors as
// the servlet-specific ServletRequestDetails subtype; using the supertype here
// matches how the rest of this package's interceptors take RequestDetails -
// see SystemAwareRequestTenantPartitionInterceptor, AuthenticationInterceptor).
//
// Cardinality, on purpose: tags are resource type (~150 FHIR resource names,
// bounded by the spec), REST operation type (~27 RestOperationTypeEnum values,
// bounded by HAPI), and exact HTTP status code (bounded, effectively <100
// distinct values in practice). All closed, small enumerations. Deliberately
// NOT tagging by raw URL/path or tenant slug - that is exactly the mistake
// that caused the fhir-gateway cardinality blowup (unbounded label values from
// tenant-slug-containing paths). See claude/fhir-gateway-observability-findings.md.
//
// Registration: this class is a Spring bean picked up via the
// hapi.fhir.custom-bean-packages component scan (@Component below) and then
// registered as a HAPI interceptor via hapi.fhir.custom-interceptor-classes -
// see application.yaml (and, for the staging deployment, aa-infra's
// components/hapi-fhir/overlays/staging/patch-configmap.yaml, which replaces
// this file's search path entirely via SPRING_CONFIG_LOCATION and so must
// re-declare both keys itself). Going through custom-bean-packages, rather
// than relying on the reflective no-arg-constructor fallback in
// StarterJpaConfig#registerCustomInterceptors, is what lets that method
// resolve this class from the Spring application context instead of
// reflectively `new`-ing it - required for MeterRegistry to be
// constructor-injected below.
@Component
@Interceptor
public class FhirEndpointMetricsInterceptor {

	// Micrometer dot-notation base name. The Prometheus exporter renders this
	// as `fhir_endpoint_requests_total` at scrape time.
	static final String METRIC_NAME = "fhir.endpoint.requests";

	// Fallback tag values so we never hand Micrometer a null tag (it throws)
	// and every request still produces exactly one well-formed time series.
	static final String UNKNOWN_OPERATION = "UNKNOWN";
	// getResourceName() is null for system/type-less requests (transaction,
	// batch, $metadata, capabilities, etc.) - that's not an error case, there
	// is just no single resource type to attribute the request to.
	static final String SYSTEM_LEVEL_RESOURCE = "_system_";

	private final MeterRegistry meterRegistry;

	public FhirEndpointMetricsInterceptor(MeterRegistry meterRegistry) {
		this.meterRegistry = meterRegistry;
	}

	@Hook(Pointcut.SERVER_OUTGOING_RESPONSE)
	public void outgoingResponse(RequestDetails theRequestDetails, HttpServletResponse theResponse) {
		recordResponse(theRequestDetails, theResponse.getStatus());
		// void return = "continue processing normally" per the Pointcut
		// javadoc. This hook only ever observes the response, never replaces
		// it, so it must never return false here.
	}

	@Hook(Pointcut.SERVER_HANDLE_EXCEPTION)
	public void handleException(RequestDetails theRequestDetails, BaseServerResponseException theException) {
		recordResponse(theRequestDetails, theException.getStatusCode());
		// Same as above: void = let HAPI generate/send its normal
		// OperationOutcome error response. We are not handling the request.
	}

	private void recordResponse(RequestDetails theRequestDetails, int theStatusCode) {
		String resourceType = theRequestDetails == null ? null : theRequestDetails.getResourceName();
		if (resourceType == null) {
			resourceType = SYSTEM_LEVEL_RESOURCE;
		}

		RestOperationTypeEnum operationType = theRequestDetails == null ? null : theRequestDetails.getRestOperationType();
		String operation = operationType == null ? UNKNOWN_OPERATION : operationType.name();

		Counter.builder(METRIC_NAME)
			.description("Count of FHIR server HTTP responses, broken down by resource type, REST operation, and HTTP status code")
			.tag("resource_type", resourceType)
			.tag("operation", operation)
			.tag("status", String.valueOf(theStatusCode))
			.register(meterRegistry)
			.increment();
	}
}
