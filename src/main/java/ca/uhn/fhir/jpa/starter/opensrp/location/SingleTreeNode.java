package ca.uhn.fhir.jpa.starter.opensrp.location;

import ca.uhn.fhir.model.api.IElement;
import ca.uhn.fhir.model.api.annotation.Child;
import ca.uhn.fhir.model.api.annotation.DatatypeDef;
import ca.uhn.fhir.util.ElementUtil;
import org.hl7.fhir.instance.model.api.ICompositeType;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Type;

@DatatypeDef(
        name = "SingleTreeNode"
)
public class SingleTreeNode extends Type implements ICompositeType {
    @Child(
            name = "treeNodeId",
            type = {StringType.class},
            order = 0
    )
    private StringType treeNodeId;
    @Child(
            name = "treeNode",
            type = {TreeNode.class},
            order = 1,
            min = 0,
            max = -1,
            modifier = false,
            summary = false
    )
    private TreeNode treeNode;

    public SingleTreeNode() {
    }

    public Type copy() {
        SingleTreeNode singleTreeNode = new SingleTreeNode();
        this.copyValues(singleTreeNode);
        return singleTreeNode;
    }

    public boolean isEmpty() {
        return ElementUtil.isEmpty(new IElement[]{this.treeNodeId, this.treeNode});
    }

    protected Type typedCopy() {
        return this.copy();
    }

    public StringType getTreeNodeId() {
        return this.treeNodeId;
    }

    public SingleTreeNode setTreeNodeId(StringType treeNodeId) {
        this.treeNodeId = treeNodeId;
        return this;
    }

    public TreeNode getTreeNode() {
        return this.treeNode;
    }

    public SingleTreeNode setTreeNode(TreeNode treeNode) {
        this.treeNode = treeNode;
        return this;
    }
}
