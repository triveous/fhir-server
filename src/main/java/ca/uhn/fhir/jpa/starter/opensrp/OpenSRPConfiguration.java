package ca.uhn.fhir.jpa.starter.opensrp;

import ca.uhn.fhir.jpa.api.dao.DaoRegistry;
import ca.uhn.fhir.jpa.starter.annotations.OnR4Condition;
import ca.uhn.fhir.jpa.starter.cr.CrConfigCondition;
import ca.uhn.fhir.jpa.starter.opensrp.location.LocationHierarchyResourceProvider;
import ca.uhn.fhir.jpa.starter.opensrp.practionerdetail.PractitionerDetailsResourceProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenSRPConfiguration {


	@Conditional({OnR4Condition.class})
	@Bean
	LocationHierarchyResourceProvider locationResourceProvider(DaoRegistry daoRegistry) {
		return new LocationHierarchyResourceProvider(daoRegistry);
	}

	@Bean
	@Conditional({OnR4Condition.class})
	PractitionerDetailsResourceProvider practitionerDetailsResourceProvider(LocationHierarchyResourceProvider locationHierarchyResourceProvider,
																									DaoRegistry daoRegistry) {
		return new PractitionerDetailsResourceProvider(locationHierarchyResourceProvider, daoRegistry);
	}
}
