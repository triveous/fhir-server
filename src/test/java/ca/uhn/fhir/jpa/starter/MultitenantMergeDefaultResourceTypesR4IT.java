package ca.uhn.fhir.jpa.starter;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.CacheControlDirective;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;
import ca.uhn.fhir.rest.client.interceptor.LoggingInterceptor;
import ca.uhn.fhir.rest.client.interceptor.UrlTenantSelectionInterceptor;
import ca.uhn.fhir.rest.server.provider.ProviderConstants;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.IntegerType;
import org.hl7.fhir.r4.model.Location;
import org.hl7.fhir.r4.model.Parameters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies the SystemAwareRequestTenantPartitionInterceptor's
 * merge-default-resource-types path. Unlike default-only, a merge type (here
 * Location) is read from the tenant partition AND DEFAULT as a union:
 * <ul>
 *   <li>a tenant's own Location is readable by id via /fhir/TENANT-A/Location/&lt;id&gt;
 *       (this is the case that 404'd when Location was default-only),</li>
 *   <li>a shared Location seeded in DEFAULT is still readable via the tenant URL,</li>
 *   <li>a search returns both.</li>
 * </ul>
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = {Application.class}, properties =
	{
		"spring.datasource.url=jdbc:h2:mem:dbr4-mt-merge-default",
		"hapi.fhir.fhir_version=r4",
		"hapi.fhir.cr_enabled=false",
		"hapi.fhir.partitioning.partitioning_include_in_search_hashes=false",
		"hapi.fhir.partitioning.merge_default_resource_types[0]=Location",
	})
class MultitenantMergeDefaultResourceTypesR4IT {

	private IGenericClient ourClient;
	private FhirContext ourCtx;

	@LocalServerPort
	private int port;

	private static UrlTenantSelectionInterceptor ourClientTenantInterceptor;

	@Test
	void testMergeTypeReadsTenantAndDefaultAsUnion() {

		// Create TENANT-A
		ourClientTenantInterceptor.setTenantId("DEFAULT");
		ourClient
			.operation()
			.onServer()
			.named(ProviderConstants.PARTITION_MANAGEMENT_CREATE_PARTITION)
			.withParameter(Parameters.class, ProviderConstants.PARTITION_MANAGEMENT_PARTITION_ID, new IntegerType(1))
			.andParameter(ProviderConstants.PARTITION_MANAGEMENT_PARTITION_NAME, new CodeType("TENANT-A"))
			.execute();

		String identValue = "merge-it-" + UUID.randomUUID();

		// Shared Location seeded in DEFAULT
		Location sharedSeed = new Location();
		sharedSeed.setName("shared-default-location");
		sharedSeed.addIdentifier().setSystem("urn:test").setValue(identValue);
		ourClientTenantInterceptor.setTenantId("DEFAULT");
		String defaultId = ourClient.create().resource(sharedSeed).execute().getId().getIdPart();

		// Tenant-owned Location written through the TENANT-A URL (writes stay tenant-scoped)
		Location tenantOwned = new Location();
		tenantOwned.setName("tenant-owned-location");
		tenantOwned.addIdentifier().setSystem("urn:test").setValue(identValue);
		ourClientTenantInterceptor.setTenantId("TENANT-A");
		String tenantId = ourClient.create().resource(tenantOwned).execute().getId().getIdPart();

		// 1) Read the tenant-owned Location by id via TENANT-A — the case that used
		//    to 404 when Location was default-only (read forced to DEFAULT).
		ourClientTenantInterceptor.setTenantId("TENANT-A");
		Location gotTenant = ourClient.read().resource(Location.class).withId(tenantId).execute();
		assertEquals("tenant-owned-location", gotTenant.getName(),
			"tenant-owned Location must be readable by id from its own tenant URL");

		// 2) Read the shared DEFAULT Location by id via TENANT-A — still served from DEFAULT.
		ourClientTenantInterceptor.setTenantId("TENANT-A");
		Location gotShared = ourClient.read().resource(Location.class).withId(defaultId).execute();
		assertEquals("shared-default-location", gotShared.getName(),
			"shared DEFAULT Location must remain readable from a tenant URL");

		// 3) Search via TENANT-A returns the union of both partitions.
		ourClientTenantInterceptor.setTenantId("TENANT-A");
		Bundle searchResult = ourClient.search()
			.forResource(Location.class)
			.where(Location.IDENTIFIER.exactly().systemAndValues("urn:test", identValue))
			.returnBundle(Bundle.class)
			.cacheControl(new CacheControlDirective().setNoCache(true))
			.execute();

		assertEquals(2, searchResult.getEntry().size(),
			"TENANT-A Location search must return both the tenant-owned and the shared DEFAULT instance");
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
