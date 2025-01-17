package ca.uhn.fhir.jpa.starter.opensrp.practionerdetail;

import ca.uhn.fhir.jpa.api.dao.DaoRegistry;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.jpa.starter.opensrp.location.LocationHierarchy;
import ca.uhn.fhir.jpa.starter.opensrp.location.LocationHierarchyResourceProvider;
import ca.uhn.fhir.model.api.annotation.Description;
import ca.uhn.fhir.rest.annotation.OptionalParam;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.api.server.SystemRequestDetails;
import ca.uhn.fhir.rest.param.*;
import ca.uhn.fhir.rest.server.BundleProviders;
import ca.uhn.fhir.rest.server.IResourceProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PractitionerDetailsResourceProvider implements IResourceProvider {
	private static final Logger log = LoggerFactory.getLogger(PractitionerDetailsResourceProvider.class);
	private final LocationHierarchyResourceProvider locationHierarchyResourceProvider;
	private final IFhirResourceDao<OrganizationAffiliation> orgAffiliationDao;
	private final IFhirResourceDao<Practitioner> practitionerDao;
	private final IFhirResourceDao<CareTeam> careTeamDao;
	private final IFhirResourceDao<Location> locationDao;
	private final IFhirResourceDao<Organization> organizationDao;
	private final IFhirResourceDao<PractitionerRole> practitionerRoleDao;
	private final IFhirResourceDao<Group> groupIFhirResourceDao;

	public PractitionerDetailsResourceProvider(LocationHierarchyResourceProvider locationHierarchyResourceProvider,
															 DaoRegistry daoRegistry) {
		this.locationHierarchyResourceProvider = locationHierarchyResourceProvider;
		this.orgAffiliationDao = daoRegistry.getResourceDao(OrganizationAffiliation.class);
		this.practitionerDao = daoRegistry.getResourceDao(Practitioner.class);
		this.careTeamDao = daoRegistry.getResourceDao(CareTeam.class);
		this.locationDao = daoRegistry.getResourceDao(Location.class);
		this.organizationDao = daoRegistry.getResourceDao(Organization.class);
		this.practitionerRoleDao = daoRegistry.getResourceDao(PractitionerRole.class);
		this.groupIFhirResourceDao = daoRegistry.getResourceDao(Group.class);
	}

	@Override
	public Class<? extends IBaseResource> getResourceType() {
		return PractitionerDetail.class;
	}

	@Search(allowUnknownParams = true)
	public IBundleProvider search(HttpServletRequest theServletRequest,
											HttpServletResponse theServletResponse,
											RequestDetails theRequestDetails,
											@Description(shortDefinition = "Practitioner")
											@OptionalParam(name = "keycloak-uuid")
											TokenParam theIdentifier
	) {
		if (theIdentifier == null) return BundleProviders.newEmptyList();
		return find(theRequestDetails, theIdentifier);
	}

	@NotNull
	public IBundleProvider find(RequestDetails theRequestDetails, TokenParam theIdentifier) {
		var practitioner = fetchPractitionerByUserId(theIdentifier.getValue(), theRequestDetails);
		if (practitioner == null) return BundleProviders.newEmptyList();
		var practitionerId = practitioner.getIdElement().getIdPart();

		var practitionerDetails = new PractitionerDetail();
		practitionerDetails.setId(practitionerId);

		var fhirPractitionerDetail = new FhirPractitionerDetails();
		fhirPractitionerDetail.setPractitioners(List.of(practitioner));
		fhirPractitionerDetail.setPractitionerId(practitioner.getIdElement().toUnqualifiedVersionless().getIdElement());
		practitionerDetails.setFhirPractitionerDetails(fhirPractitionerDetail);

		var careTeams = fetchPractitionerCareTeam(practitionerId, theRequestDetails);
		fhirPractitionerDetail.setCareTeams(careTeams);

		var practitionerRoles = fetchPractitionerRole(practitionerId, theRequestDetails);
		fhirPractitionerDetail.setPractitionerRoles(practitionerRoles);

		var careTeamManagingOrganizationIds = extractManagingOrganizationFromCareTeam(careTeams);
		var practitionerOrganizationIds = extractManagingOrganizationFromPractitionerRole(practitionerRoles);
		var organizationIds = Stream.concat(careTeamManagingOrganizationIds.stream(), practitionerOrganizationIds.stream())
			.distinct()
			.collect(Collectors.toList());
		var organizations = fetchOrganization(organizationIds, theRequestDetails);
		fhirPractitionerDetail.setOrganizations(organizations);

		var groups = fetchAssignedGroups(practitionerId, theRequestDetails);
		fhirPractitionerDetail.setGroups(groups);

		var orgAffiliations = fetchOrganizationAffiliation(organizationIds, theRequestDetails);
		fhirPractitionerDetail.setOrganizationAffiliations(orgAffiliations);

		var locationIds = extractLocationIdsFromOrganizationAffiliation(orgAffiliations);
		var locationHierarchy = fetchLocationHierarchy(locationIds, theRequestDetails);
		fhirPractitionerDetail.setLocationHierarchyList(locationHierarchy);

		var locations = fetchLocations(locationIds, theRequestDetails);
		fhirPractitionerDetail.setLocations(locations);

		return BundleProviders.newList(practitionerDetails);
	}

	private Practitioner fetchPractitionerByUserId(String userId, RequestDetails requestDetails) {
		var param = new SearchParameterMap(Practitioner.SP_IDENTIFIER, new TokenParam(userId));
		var result = this.practitionerDao.search(param, requestDetails);
		return (Practitioner) result.getAllResources().stream().findFirst().orElse(null);
	}

	private List<CareTeam> fetchPractitionerCareTeam(String practitionerId, RequestDetails requestDetails) {
		var practitionerRef = new ReferenceParam(practitionerId);
		var param = new SearchParameterMap();
		param.add(CareTeam.SP_PARTICIPANT, practitionerRef);

		var result = this.careTeamDao.search(param, requestDetails);
		return result.getAllResources().stream().map(r -> (CareTeam) r).collect(Collectors.toList());
	}

	// TODO
	private List<Location> fetchLocations(Set<String> locationIds, RequestDetails requestDetails) {
		if (locationIds.isEmpty()) return Collections.emptyList();

		var ids = new TokenOrListParam();
		locationIds.forEach(i -> ids.addOr(new TokenParam(i)));
		var param = new SearchParameterMap();
		param.add(Location.SP_RES_ID, ids);

		var result = this.locationDao.search(param, requestDetails);
		return result.getAllResources().stream().map(r -> (Location) r).collect(Collectors.toList());
	}

	private List<Organization> fetchOrganization(List<String> organizationIds, RequestDetails requestDetails) {
		if (organizationIds.isEmpty()) return Collections.emptyList();

		var ids = new TokenOrListParam();
		organizationIds.forEach(i -> ids.addOr(new TokenParam(i)));
		var param = new SearchParameterMap();
		param.add(Organization.SP_RES_ID, ids);

		var result = this.organizationDao.search(param, requestDetails);
		return result.getAllResources().stream().map(r -> (Organization) r).collect(Collectors.toList());
	}

	private List<PractitionerRole> fetchPractitionerRole(String practitionerId, RequestDetails requestDetails) {
		var params = new SearchParameterMap(PractitionerRole.SP_PRACTITIONER, new ReferenceParam(practitionerId));
		var result = this.practitionerRoleDao.search(params, requestDetails);
		return result.getAllResources().stream().map(r -> (PractitionerRole) r).collect(Collectors.toList());
	}

	private List<Group> fetchAssignedGroups(String practitionerId, RequestDetails requestDetails) {
		var params = new SearchParameterMap(Group.SP_CODE, new TokenParam("http://snomed.info/sct", "405623001"));
		params.add(Group.SP_MEMBER, new ReferenceParam(practitionerId));

		var result = this.groupIFhirResourceDao.search(params, requestDetails);
		return result.getAllResources().stream().map(r -> (Group) r).collect(Collectors.toList());
	}

	private List<LocationHierarchy> fetchLocationHierarchy(Set<String> locationIds, RequestDetails requestDetails) {
		if (locationIds.isEmpty()) return Collections.emptyList();

		return locationIds.parallelStream().map(id -> {
			var request = new SystemRequestDetails(requestDetails);
			return locationHierarchyResourceProvider.getLocationHierarchy(request, id);
		}).collect(Collectors.toList());
	}

	private List<OrganizationAffiliation> fetchOrganizationAffiliation(List<String> organizationIds, RequestDetails requestDetails) {
		if (organizationIds.isEmpty()) return Collections.emptyList();

		var params = new SearchParameterMap();
		var ids = new StringOrListParam();
		organizationIds.forEach(i -> ids.addOr(new StringParam(i)));
		params.add(OrganizationAffiliation.SP_PRIMARY_ORGANIZATION, ids);

		var result = this.orgAffiliationDao.search(params, requestDetails);
		return result.getAllResources().stream().map(r -> (OrganizationAffiliation) r).collect(Collectors.toList());
	}

	private Set<String> extractManagingOrganizationFromCareTeam(List<CareTeam> careTeams) {
		return careTeams.stream()
			.filter(CareTeam::hasManagingOrganization)
			.flatMap(it -> it.getManagingOrganization().stream())
			.map(it -> it.getReferenceElement().getIdPart())
			.collect(Collectors.toSet());
	}

	private Set<String> extractManagingOrganizationFromPractitionerRole(List<PractitionerRole> careTeams) {
		return careTeams.stream()
			.filter(PractitionerRole::hasOrganization)
			.map(it -> it.getOrganization().getReferenceElement().getIdPart())
			.collect(Collectors.toSet());
	}

	private Set<String> extractLocationIdsFromOrganizationAffiliation(List<OrganizationAffiliation> organizationAffiliations) {
		if (organizationAffiliations.isEmpty()) return Collections.emptySet();

		return organizationAffiliations.stream()
			.map(it -> it.getLocationFirstRep().getReferenceElement().getIdPart())
			.collect(Collectors.toSet());
	}
}
