package ca.uhn.fhir.jpa.starter.authnz.inbound.authorization;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.interceptor.model.ReadPartitionIdRequestDetails;
import ca.uhn.fhir.interceptor.model.RequestPartitionId;
import ca.uhn.fhir.jpa.partition.IRequestPartitionHelperSvc;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.api.server.SystemRequestDetails;
import ca.uhn.fhir.rest.server.interceptor.partition.RequestTenantPartitionInterceptor;

// Stock RequestTenantPartitionInterceptor throws when a request has no tenant.
// Server-internal work (e.g. SearchParameter registry refresh on startup)
// issues SystemRequestDetails without a URL tenant. Route those to the
// DEFAULT partition — that's where server-wide HAPI artifacts (custom
// SearchParameters, StructureDefinitions) live. allPartitions() is rejected
// at runtime by HAPI-1220 when allowReferencesAcrossPartitions is NOT_ALLOWED.
//
// On reads, the stock interceptor also routes everything to the URL tenant.
// That breaks definitional/non-partitionable resources (StructureDefinition,
// SearchParameter, ValueSet, CodeSystem, Questionnaire, …) which HAPI-1318
// forces to live in DEFAULT only — a GET at /fhir/<tenant>/<Type> for those
// would scope to the tenant partition and always return empty. We widen
// non-partitionable reads to DEFAULT using HAPI's authoritative list via
// IRequestPartitionHelperSvc#isResourcePartitionable.
public class SystemAwareRequestTenantPartitionInterceptor extends RequestTenantPartitionInterceptor {

	private final IRequestPartitionHelperSvc myPartitionHelperSvc;

	public SystemAwareRequestTenantPartitionInterceptor(IRequestPartitionHelperSvc thePartitionHelperSvc) {
		this.myPartitionHelperSvc = thePartitionHelperSvc;
	}

	@Override
	protected RequestPartitionId extractPartitionIdFromRequest(RequestDetails theRequestDetails) {
		if (theRequestDetails instanceof SystemRequestDetails) {
			return RequestPartitionId.defaultPartition();
		}
		return super.extractPartitionIdFromRequest(theRequestDetails);
	}

	@Hook(Pointcut.STORAGE_PARTITION_IDENTIFY_READ)
	public RequestPartitionId identifyReadPartition(
			RequestDetails theRequestDetails, ReadPartitionIdRequestDetails theReadDetails) {
		if (theRequestDetails instanceof SystemRequestDetails) {
			return RequestPartitionId.defaultPartition();
		}
		String resourceType = theReadDetails != null ? theReadDetails.getResourceType() : null;
		if (resourceType != null && !myPartitionHelperSvc.isResourcePartitionable(resourceType)) {
			return RequestPartitionId.defaultPartition();
		}
		return super.extractPartitionIdFromRequest(theRequestDetails);
	}
}
