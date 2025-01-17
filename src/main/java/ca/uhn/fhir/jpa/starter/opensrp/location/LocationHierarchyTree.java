package ca.uhn.fhir.jpa.starter.opensrp.location;

import ca.uhn.fhir.model.api.IElement;
import ca.uhn.fhir.model.api.annotation.Child;
import ca.uhn.fhir.model.api.annotation.DatatypeDef;
import ca.uhn.fhir.util.ElementUtil;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.instance.model.api.ICompositeType;
import org.hl7.fhir.r4.model.Location;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Type;

import java.util.Iterator;
import java.util.List;

@DatatypeDef(
        name = "LocationHierarchyTree"
)
public class LocationHierarchyTree extends Type implements ICompositeType {
    @Child(
            name = "locationsHierarchy"
    )
    private Tree locationsHierarchy = new Tree();

    public LocationHierarchyTree() {
    }

    public void addLocation(Location location) {
        StringType idString = new StringType();
        idString.setValue(location.getId());
        if (location.getPartOf() != null && !StringUtils.isEmpty(location.getPartOf().getReference())) {
            StringType parentId = new StringType();
            parentId.setValue(location.getPartOf().getReference());
            this.locationsHierarchy.addNode((String)idString.getValue(), location.getName(), location, (String)parentId.getValue());
        } else {
            this.locationsHierarchy.addNode((String)idString.getValue(), location.getName(), location, (String)null);
        }

    }

    public void buildTreeFromList(List<Location> locations) {
        Iterator var2 = locations.iterator();

        while(var2.hasNext()) {
            Location location = (Location)var2.next();
            this.addLocation(location);
        }

    }

    public Tree getLocationsHierarchy() {
        return this.locationsHierarchy;
    }

    public LocationHierarchyTree setLocationsHierarchy(Tree locationsHierarchy) {
        this.locationsHierarchy = locationsHierarchy;
        return this;
    }

    public Type copy() {
        LocationHierarchyTree locationHierarchyTree = new LocationHierarchyTree();
        this.copyValues(locationHierarchyTree);
        return locationHierarchyTree;
    }

    public boolean isEmpty() {
        return ElementUtil.isEmpty(new IElement[]{this.locationsHierarchy});
    }

    protected Type typedCopy() {
        return this.copy();
    }
}

