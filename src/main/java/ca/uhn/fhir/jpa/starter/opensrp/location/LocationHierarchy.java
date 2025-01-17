package ca.uhn.fhir.jpa.starter.opensrp.location;

import ca.uhn.fhir.model.api.annotation.Child;
import ca.uhn.fhir.model.api.annotation.Description;
import ca.uhn.fhir.model.api.annotation.ResourceDef;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Location;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.StringType;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom Resource to keep support for OpenSRP
 */

@ResourceDef(
        name = "LocationHierarchy",
        profile = "http://hl7.org/fhir/profiles/custom-resource"
)
public class LocationHierarchy extends Location {
    @Child(
            name = "locationId",
            type = {StringType.class},
            order = 5,
            min = 0,
            max = 1,
            modifier = false,
            summary = true
    )
    @Description(
            shortDefinition = "Unique id to the location",
            formalDefinition = "Id of the location whose location hierarchy will be displayed."
    )
    protected StringType locationId;
    @Child(
            name = "LocationHierarchyTree",
            type = {LocationHierarchyTree.class}
    )
    @Description(
            shortDefinition = "Complete Location Hierarchy Tree",
            formalDefinition = "Consists of Location Hierarchy Tree and Parent Child Identifiers List"
    )
    private LocationHierarchyTree locationHierarchyTree;

    public LocationHierarchy() {
    }

    public Location copy() {
        Location location = new Location();
        Bundle bundle = new Bundle();
        List<Bundle.BundleEntryComponent> theEntry = new ArrayList();
        Bundle.BundleEntryComponent entryComponent = new Bundle.BundleEntryComponent();
        entryComponent.setResource(new Bundle());
        theEntry.add(entryComponent);
        bundle.setEntry(theEntry);
        this.copyValues(location);
        return location;
    }

    public ResourceType getResourceType() {
        return ResourceType.Bundle;
    }

    public StringType getLocationId() {
        return this.locationId;
    }

    public void setLocationId(StringType locationId) {
        this.locationId = locationId;
    }

    public LocationHierarchyTree getLocationHierarchyTree() {
        return this.locationHierarchyTree;
    }

    public void setLocationHierarchyTree(LocationHierarchyTree locationHierarchyTree) {
        this.locationHierarchyTree = locationHierarchyTree;
    }
}
