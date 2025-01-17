package ca.uhn.fhir.jpa.starter.opensrp.location;

import ca.uhn.fhir.model.api.IElement;
import ca.uhn.fhir.model.api.annotation.Child;
import ca.uhn.fhir.model.api.annotation.DatatypeDef;
import ca.uhn.fhir.util.ElementUtil;
import org.hl7.fhir.instance.model.api.ICompositeType;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Type;

@DatatypeDef(
        name = "ChildTreeNode"
)
public class ChildTreeNode extends Type implements ICompositeType {
    @Child(
            name = "childId",
            type = {StringType.class},
            order = 0,
            min = 1,
            max = 1,
            modifier = false,
            summary = false
    )
    private StringType childId;
    @Child(
            name = "treeNode",
            type = {TreeNode.class}
    )
    private TreeNode treeNode = new TreeNode();

    public ChildTreeNode() {
    }

    public StringType getChildId() {
        return this.childId;
    }

    public ChildTreeNode setChildId(StringType childId) {
        this.childId = childId;
        return this;
    }

    public TreeNode getChildren() {
        if (this.treeNode == null) {
            this.treeNode = new TreeNode();
        }

        return this.treeNode;
    }

    public ChildTreeNode setChildren(TreeNode children) {
        this.treeNode = children;
        return this;
    }

    public Type copy() {
        ChildTreeNode childTreeNode = new ChildTreeNode();
        this.copyValues(childTreeNode);
        return childTreeNode;
    }

    public boolean isEmpty() {
        return ElementUtil.isEmpty(new IElement[]{this.childId});
    }

    protected Type typedCopy() {
        return this.copy();
    }
}
