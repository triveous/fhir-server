package ca.uhn.fhir.jpa.starter.opensrp.location;

import ca.uhn.fhir.jpa.api.dao.DaoRegistry;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.model.api.annotation.Description;
import ca.uhn.fhir.rest.annotation.OptionalParam;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.param.ReferenceParam;
import ca.uhn.fhir.rest.param.TokenParam;
import ca.uhn.fhir.rest.server.BundleProviders;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import jakarta.annotation.Nullable;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Location;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.StringType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class LocationHierarchyResourceProvider implements IResourceProvider {
	private static final Logger log = LoggerFactory.getLogger(LocationHierarchyResourceProvider.class);
	private final IFhirResourceDao<Location> locationDao;

	public LocationHierarchyResourceProvider(DaoRegistry registry) {
		this.locationDao = registry.getResourceDao(Location.class);
	}

	@Override
	public Class<? extends IBaseResource> getResourceType() {
		return LocationHierarchy.class;
	}

	@Search(allowUnknownParams = true)
	public IBundleProvider search(HttpServletRequest theServletRequest,
											HttpServletResponse theServletResponse,
											RequestDetails theRequestDetails,
											@Description(shortDefinition = "Location Id")
											@OptionalParam(name = "identifier")
											TokenParam theIdentifier
	) {
		var hierarchy = theIdentifier != null && theIdentifier.getValue() != null ?
			getLocationHierarchy(theRequestDetails, theIdentifier.getValue())
			: null;
		if (hierarchy == null) return BundleProviders.newEmptyList();
		return BundleProviders.newList(hierarchy);
	}

	public LocationHierarchy getLocationHierarchy(RequestDetails requestDetails, String locationId) {
		Location location = getLocationById(requestDetails, locationId);
		LocationHierarchyTree locationHierarchyTree = new LocationHierarchyTree();
		LocationHierarchy locationHierarchy = new LocationHierarchy();
		if (location != null) {
			var locations = getLocationHierarchyInternal(requestDetails, locationId, location);
			// It is needed since the parentId(defined in the partOf) doesn't have the url prefix for the id
			// where are the location id has the url. In order to create parent child relationship. Had to remove
			// the location id
			locations = locations.stream()
				.peek(l -> l.setId(String.format("%s/%s", ResourceType.Location.name(), l.getIdPart())))
				.collect(Collectors.toList());
			locationHierarchyTree.buildTreeFromList(locations);
			var locationIdString = new StringType().setId(locationId).getIdElement();
			locationHierarchy.setLocationId(locationIdString);
			locationHierarchy.setId(new IdType(ResourceType.Location.name(), locationId));
			locationHierarchy.setLocationHierarchyTree(locationHierarchyTree);
			return locationHierarchy;
		}
		return null;
	}

	private List<Location> getLocationHierarchyInternal(RequestDetails requestDetails, String locationId, Location parentLocation) {
		return descendants(requestDetails, locationId, parentLocation);
	}

	public List<Location> descendants(RequestDetails requestDetails, String locationId, Location parentLocation) {

		var childLocationBundle = fetchChildLocation(locationId, requestDetails);

		List<Location> allLocations = new ArrayList<>();
		if (parentLocation != null) allLocations.add(parentLocation);


		if (childLocationBundle != null) {
			for (var childLocationEntity : childLocationBundle) {
				allLocations.add(childLocationEntity);
				allLocations.addAll(descendants(requestDetails, childLocationEntity.getIdElement().getIdPart(), null));
			}
		}

		return allLocations;
	}

	@Nullable
	private Location getLocationById(RequestDetails requestDetails, String locationId) {
		try {
			return this.locationDao.read(new IdType(locationId), requestDetails);
		} catch (ResourceNotFoundException e) {
			return null;
		} catch (Exception e) {
			log.error("Failed to get resource", e);
			return null;
		}
	}

	private List<Location> fetchChildLocation(String locationId, RequestDetails requestDetails) {
		SearchParameterMap searchParameterMap = new SearchParameterMap();
		searchParameterMap.add(Location.SP_PARTOF, new ReferenceParam(new IdType(ResourceType.Location.name(), locationId)));
		var result = this.locationDao.search(searchParameterMap, requestDetails);
		return result.getAllResources().stream().map(l -> (Location) l).collect(Collectors.toList());
	}
}
