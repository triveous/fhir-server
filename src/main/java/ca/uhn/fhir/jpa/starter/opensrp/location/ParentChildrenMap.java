package ca.uhn.fhir.jpa.starter.opensrp.location;


import ca.uhn.fhir.model.api.IElement;
import ca.uhn.fhir.model.api.annotation.Child;
import ca.uhn.fhir.model.api.annotation.DatatypeDef;
import ca.uhn.fhir.util.ElementUtil;
import org.hl7.fhir.instance.model.api.ICompositeType;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Type;

import java.util.List;

@DatatypeDef(
        name = "ParentChildrenMap"
)
public class ParentChildrenMap extends Type implements ICompositeType {
    @Child(
            name = "identifier",
            type = {StringType.class},
            order = 0,
            min = 1,
            max = 1,
            modifier = false,
            summary = false
    )
    private StringType identifier;
    @Child(
            name = "childIdentifiers",
            type = {StringType.class},
            order = 1,
            min = 0,
            max = -1,
            modifier = false,
            summary = false
    )
    private List<StringType> childIdentifiers;

    public ParentChildrenMap() {
    }

    public StringType getIdentifier() {
        return this.identifier;
    }

    public ParentChildrenMap setIdentifier(StringType identifier) {
        this.identifier = identifier;
        return this;
    }

    public List<StringType> getChildIdentifiers() {
        return this.childIdentifiers;
    }

    public ParentChildrenMap setChildIdentifiers(List<StringType> childIdentifiers) {
        this.childIdentifiers = childIdentifiers;
        return this;
    }

    public Type copy() {
        ParentChildrenMap parentChildrenMap = new ParentChildrenMap();
        this.copyValues(parentChildrenMap);
        return parentChildrenMap;
    }

    public boolean isEmpty() {
        return ElementUtil.isEmpty(new IElement[]{this.identifier});
    }

    protected Type typedCopy() {
        return this.copy();
    }
}
