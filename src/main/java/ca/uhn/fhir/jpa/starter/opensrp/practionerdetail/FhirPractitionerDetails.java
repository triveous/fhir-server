package ca.uhn.fhir.jpa.starter.opensrp.practionerdetail;


import ca.uhn.fhir.jpa.starter.opensrp.location.LocationHierarchy;
import ca.uhn.fhir.model.api.annotation.Child;
import ca.uhn.fhir.model.api.annotation.DatatypeDef;
import ca.uhn.fhir.util.ElementUtil;
import org.hl7.fhir.instance.model.api.ICompositeType;
import org.hl7.fhir.r4.model.*;

import java.util.List;

@DatatypeDef(
	name = "fhir"
)
public class FhirPractitionerDetails extends Type implements ICompositeType {
	@Child(
		name = "careteams",
		type = {CareTeam.class},
		order = 1,
		min = 0,
		max = -1,
		modifier = false,
		summary = false
	)
	List<CareTeam> careTeams;
	@Child(
		name = "teams",
		type = {Organization.class},
		order = 2,
		min = 0,
		max = -1,
		modifier = false,
		summary = false
	)
	List<Organization> organizations;
	@Child(
		name = "locations",
		type = {Location.class},
		order = 3,
		min = 0,
		max = -1,
		modifier = false,
		summary = false
	)
	private List<Location> locations;
	@Child(
		name = "locationHierarchyList",
		type = {LocationHierarchy.class},
		order = 4,
		min = 0,
		max = -1,
		modifier = false,
		summary = false
	)
	private List<LocationHierarchy> locationHierarchyList;
	@Child(
		name = "practitionerRoles",
		type = {PractitionerRole.class},
		order = 5,
		min = 0,
		max = -1,
		modifier = false,
		summary = false
	)
	List<PractitionerRole> practitionerRoles;
	@Child(
		name = "groups",
		type = {Group.class},
		order = 6,
		min = 0,
		max = -1,
		modifier = false,
		summary = false
	)
	List<Group> groups;
	@Child(
		name = "practitioner",
		type = {Practitioner.class},
		order = 7,
		min = 0,
		max = -1,
		modifier = false,
		summary = false
	)
	private List<Practitioner> practitioners;
	@Child(
		name = "organizationAffiliation",
		type = {OrganizationAffiliation.class},
		order = 8,
		min = 0,
		max = -1,
		modifier = false,
		summary = false
	)
	private List<OrganizationAffiliation> organizationAffiliations;
	@Child(
		name = "practitionerId",
		type = {StringType.class},
		order = 9,
		min = 0,
		max = -1,
		modifier = false,
		summary = false
	)
	private StringType practitionerId;

	public FhirPractitionerDetails() {
	}

	public List<CareTeam> getCareTeams() {
		return this.careTeams;
	}

	public void setCareTeams(List<CareTeam> careTeams) {
		this.careTeams = careTeams;
	}

	public List<Organization> getOrganizations() {
		return this.organizations;
	}

	public void setOrganizations(List<Organization> organizations) {
		this.organizations = organizations;
	}

	public List<Location> getLocations() {
		return this.locations;
	}

	public void setLocations(List<Location> locations) {
		this.locations = locations;
	}

	public List<LocationHierarchy> getLocationHierarchyList() {
		return this.locationHierarchyList;
	}

	public void setLocationHierarchyList(List<LocationHierarchy> locationHierarchyList) {
		this.locationHierarchyList = locationHierarchyList;
	}

	public List<PractitionerRole> getPractitionerRoles() {
		return this.practitionerRoles;
	}

	public void setPractitionerRoles(List<PractitionerRole> practitionerRoles) {
		this.practitionerRoles = practitionerRoles;
	}

	public List<Group> getGroups() {
		return this.groups;
	}

	public void setGroups(List<Group> groups) {
		this.groups = groups;
	}

	public List<Practitioner> getPractitioners() {
		return this.practitioners;
	}

	public void setPractitioners(List<Practitioner> practitioners) {
		this.practitioners = practitioners;
	}

	public List<OrganizationAffiliation> getOrganizationAffiliations() {
		return this.organizationAffiliations;
	}

	public void setOrganizationAffiliations(List<OrganizationAffiliation> organizationAffiliations) {
		this.organizationAffiliations = organizationAffiliations;
	}

	public StringType getPractitionerId() {
		return this.practitionerId;
	}

	public void setPractitionerId(StringType practitionerId) {
		this.practitionerId = practitionerId;
	}

	public Type copy() {
		FhirPractitionerDetails fhirPractitionerDetails = new FhirPractitionerDetails();
		this.copyValues(fhirPractitionerDetails);
		return fhirPractitionerDetails;
	}

	public boolean isEmpty() {
		return ElementUtil.isEmpty(this.practitioners);
	}

	protected Type typedCopy() {
		return this.copy();
	}
}
