package ca.uhn.fhir.jpa.starter;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.CacheControlDirective;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;
import ca.uhn.fhir.rest.client.interceptor.LoggingInterceptor;
import ca.uhn.fhir.rest.client.interceptor.UrlTenantSelectionInterceptor;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import ca.uhn.fhir.rest.server.provider.ProviderConstants;
import org.hl7.fhir.r4.model.Basic;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.IntegerType;
import org.hl7.fhir.r4.model.Parameters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies the SystemAwareRequestTenantPartitionInterceptor's per-(type, id)
 * widening path (hapi.fhir.partitioning.default_only_resource_ids):
 *
 *   - A Basic resource with id "feature-flags" seeded in DEFAULT IS readable
 *     via GET /fhir/TENANT-A/Basic/feature-flags (widened to DEFAULT).
 *   - A Basic resource with id "other-id" seeded in DEFAULT is NOT readable
 *     via GET /fhir/TENANT-A/Basic/other-id (proves the widening is narrow:
 *     the same resource type is not widened across the board).
 *   - A PUT against /fhir/TENANT-A/Basic/feature-flags stays tenant-scoped
 *     and does NOT overwrite the DEFAULT-partition resource.
 *
 * The matching application property is injected via @SpringBootTest properties.
 * A distinct H2 URL is used to avoid pool collisions with sibling ITs.
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = {Application.class}, properties =
	{
		"spring.datasource.url=jdbc:h2:mem:dbr4-mt-default-only-ids",
		"hapi.fhir.fhir_version=r4",
		"hapi.fhir.cr_enabled=false",
		"hapi.fhir.partitioning.partitioning_include_in_search_hashes=false",
		"hapi.fhir.partitioning.default_only_resource_ids.Basic[0]=feature-flags",
		"hapi.fhir.client_id_strategy=ANY",
	})
class MultitenantDefaultOnlyResourceIdsR4IT {

	private IGenericClient ourClient;
	private FhirContext ourCtx;

	@LocalServerPort
	private int port;

	private static UrlTenantSelectionInterceptor ourClientTenantInterceptor;

	@Test
	void testDefaultOnlyResourceIdWideningIsNarrow() {

		// Create TENANT-A
		ourClientTenantInterceptor.setTenantId("DEFAULT");
		ourClient
			.operation()
			.onServer()
			.named(ProviderConstants.PARTITION_MANAGEMENT_CREATE_PARTITION)
			.withParameter(Parameters.class, ProviderConstants.PARTITION_MANAGEMENT_PARTITION_ID, new IntegerType(1))
			.andParameter(ProviderConstants.PARTITION_MANAGEMENT_PARTITION_NAME, new CodeType("TENANT-A"))
			.execute();

		// Seed Basic/feature-flags in DEFAULT
		Basic featureFlags = new Basic();
		featureFlags.setId("feature-flags");
		featureFlags.setCode(new CodeableConcept().setText("feature-flags-default"));

		ourClientTenantInterceptor.setTenantId("DEFAULT");
		ourClient.update().resource(featureFlags).execute();

		// Seed Basic/other-id in DEFAULT (should NOT be widened)
		Basic otherId = new Basic();
		otherId.setId("other-id");
		otherId.setCode(new CodeableConcept().setText("other-id-default"));
		ourClient.update().resource(otherId).execute();

		// GET /fhir/TENANT-A/Basic/feature-flags — widened to DEFAULT, must return seed
		ourClientTenantInterceptor.setTenantId("TENANT-A");
		Basic readWidened = ourClient.read()
			.resource(Basic.class)
			.withId("feature-flags")
			.cacheControl(new CacheControlDirective().setNoCache(true))
			.execute();
		assertEquals("feature-flags-default", readWidened.getCode().getText(),
			"expected widened read to return the DEFAULT-partition feature-flags resource");

		// GET /fhir/TENANT-A/Basic/other-id — NOT widened, must 404
		assertThrows(ResourceNotFoundException.class, () ->
			ourClient.read()
				.resource(Basic.class)
				.withId("other-id")
				.cacheControl(new CacheControlDirective().setNoCache(true))
				.execute(),
			"expected non-widened id to 404 on tenant URL — confirms (type, id) widening is narrow");

		// PUT /fhir/TENANT-A/Basic/feature-flags — must stay tenant-scoped (no widening on write).
		// After the PUT, the DEFAULT-partition resource must still have its original code text
		// when read via DEFAULT directly.
		Basic tenantScoped = new Basic();
		tenantScoped.setId("feature-flags");
		tenantScoped.setCode(new CodeableConcept().setText("feature-flags-tenant"));
		ourClient.update().resource(tenantScoped).execute();

		ourClientTenantInterceptor.setTenantId("DEFAULT");
		Basic stillDefault = ourClient.read()
			.resource(Basic.class)
			.withId("feature-flags")
			.cacheControl(new CacheControlDirective().setNoCache(true))
			.execute();
		assertEquals("feature-flags-default", stillDefault.getCode().getText(),
			"write against /fhir/TENANT-A/Basic/feature-flags must NOT overwrite the DEFAULT resource");
	}

	@BeforeEach
	void beforeEach() {
		ourClientTenantInterceptor = new UrlTenantSelectionInterceptor();
		ourCtx = FhirContext.forR4();
		ourCtx.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);
		ourCtx.getRestfulClientFactory().setSocketTimeout(1200 * 1000);
		String ourServerBase = "http://localhost:" + port + "/fhir/";
		ourClient = ourCtx.newRestfulGenericClient(ourServerBase);
		ourClient.registerInterceptor(new LoggingInterceptor(true));
		ourClient.registerInterceptor(ourClientTenantInterceptor);
	}
}
