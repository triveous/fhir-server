package ca.uhn.fhir.jpa.starter.opensrp.practionerdetail;

import ca.uhn.fhir.model.api.annotation.Child;
import ca.uhn.fhir.model.api.annotation.Description;
import ca.uhn.fhir.model.api.annotation.ResourceDef;
import ca.uhn.fhir.model.api.annotation.SearchParamDefinition;
import ca.uhn.fhir.rest.gclient.TokenClientParam;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.ResourceType;

import java.util.ArrayList;

@ResourceDef(
	name = "PractitionerDetail",
	profile = "http://hl7.org/fhir/profiles/custom-resource"
)
public class PractitionerDetail extends Practitioner {
	@Child(
		name = "fhir",
		type = {FhirPractitionerDetails.class}
	)
	@Description(
		shortDefinition = "Get resources from FHIR Server",
		formalDefinition = "Get resources from FHIR Server"
	)
	private FhirPractitionerDetails fhirPractitionerDetails;
	@SearchParamDefinition(
		name = "keycloak-uuid",
		path = "PractitionerDetails.keycloak-uuid",
		description = "A practitioner's keycloak-uuid",
		type = "token"
	)
	public static final String SP_KEYCLOAK_UUID = "keycloak-uuid";
	public static final TokenClientParam KEYCLOAK_UUID = new TokenClientParam("keycloak-uuid");

	public PractitionerDetail() {
	}

	public Practitioner copy() {
		var practitioner = new Practitioner();
		var bundle = new Bundle();
		var theEntry = new ArrayList<Bundle.BundleEntryComponent>();
		var entryComponent = new Bundle.BundleEntryComponent();
		entryComponent.setResource(new Bundle());
		theEntry.add(entryComponent);
		bundle.setEntry(theEntry);
		this.copyValues(practitioner);
		return practitioner;
	}

	public ResourceType getResourceType() {
		return ResourceType.Bundle;
	}

	public FhirPractitionerDetails getFhirPractitionerDetails() {
		return this.fhirPractitionerDetails;
	}

	public void setFhirPractitionerDetails(FhirPractitionerDetails fhirPractitionerDetails) {
		this.fhirPractitionerDetails = fhirPractitionerDetails;
	}
}
