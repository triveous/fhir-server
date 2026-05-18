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
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.IntegerType;
import org.hl7.fhir.r4.model.Parameters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the SystemAwareRequestTenantPartitionInterceptor's
 * default-only-resource-types path: a Composition seeded in DEFAULT is
 * readable via a per-tenant URL (e.g. /fhir/TENANT-A/Composition?identifier=…),
 * because the read is transparently rerouted to DEFAULT.
 *
 * The matching application property is injected via @SpringBootTest properties.
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = {Application.class}, properties =
	{
		"spring.datasource.url=jdbc:h2:mem:dbr4-mt-default-only",
		"hapi.fhir.fhir_version=r4",
		"hapi.fhir.cr_enabled=false",
		"hapi.fhir.partitioning.partitioning_include_in_search_hashes=false",
		"hapi.fhir.partitioning.default_only_resource_types[0]=Composition",
		"hapi.fhir.partitioning.default_only_resource_types[1]=StructureMap",
		"hapi.fhir.partitioning.default_only_resource_types[2]=Library",
	})
class MultitenantDefaultOnlyResourceTypesR4IT {

	private IGenericClient ourClient;
	private FhirContext ourCtx;

	@LocalServerPort
	private int port;

	private static UrlTenantSelectionInterceptor ourClientTenantInterceptor;

	@Test
	void testDefaultOnlyCompositionReadFromTenantPartition() {

		// Create TENANT-A
		ourClientTenantInterceptor.setTenantId("DEFAULT");
		ourClient
			.operation()
			.onServer()
			.named(ProviderConstants.PARTITION_MANAGEMENT_CREATE_PARTITION)
			.withParameter(Parameters.class, ProviderConstants.PARTITION_MANAGEMENT_PARTITION_ID, new IntegerType(1))
			.andParameter(ProviderConstants.PARTITION_MANAGEMENT_PARTITION_NAME, new CodeType("TENANT-A"))
			.execute();

		// Seed a Composition in DEFAULT with a unique identifier
		String identValue = "default-only-it-" + UUID.randomUUID();
		Composition seed = new Composition();
		seed.setStatus(Composition.CompositionStatus.FINAL);
		seed.getType().setText("test");
		seed.setTitle("default-only-it");
		seed.getIdentifier().setSystem("urn:test").setValue(identValue);
		seed.addAuthor().setDisplay("tester");

		ourClientTenantInterceptor.setTenantId("DEFAULT");
		String createdId = ourClient.create().resource(seed).execute().getId().getIdPart();

		// Read via TENANT-A — should be transparently served from DEFAULT
		ourClientTenantInterceptor.setTenantId("TENANT-A");
		Bundle searchResult = ourClient.search()
			.forResource(Composition.class)
			.where(Composition.IDENTIFIER.exactly().systemAndValues("urn:test", identValue))
			.returnBundle(Bundle.class)
			.cacheControl(new CacheControlDirective().setNoCache(true))
			.execute();

		assertEquals(1, searchResult.getEntry().size(), "expected one Composition hit from TENANT-A search");
		Composition found = (Composition) searchResult.getEntry().get(0).getResource();
		assertTrue(found.getIdElement().getIdPart().equals(createdId),
			"expected returned id to match the seeded DEFAULT id (created=" + createdId + ", found=" + found.getIdElement().getIdPart() + ")");
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
