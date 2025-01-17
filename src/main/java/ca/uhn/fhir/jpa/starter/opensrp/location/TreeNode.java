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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@DatatypeDef(
        name = "TreeNode"
)
public class TreeNode extends Type implements ICompositeType {
    @Child(
            name = "name",
            type = {StringType.class},
            order = 0,
            min = 1,
            max = 1,
            modifier = false,
            summary = false
    )
    protected StringType name;
    @Child(
            name = "nodeId",
            type = {StringType.class},
            order = 2
    )
    private StringType nodeId;
    @Child(
            name = "label",
            type = {StringType.class},
            order = 3
    )
    private StringType label;
    @Child(
            name = "node",
            type = {Location.class},
            order = 4
    )
    private Location node;
    @Child(
            name = "parent",
            type = {StringType.class},
            order = 5
    )
    private StringType parent;
    @Child(
            name = "children",
            type = {ChildTreeNode.class},
            order = 6,
            min = 0,
            max = -1,
            modifier = false,
            summary = false
    )
    private List<ChildTreeNode> children;

    public TreeNode() {
        this.children = new ArrayList();
    }

    public TreeNode(StringType name, StringType nodeId, StringType label, Location node, StringType parent) {
        this.name = name;
        this.nodeId = nodeId;
        this.label = label;
        this.node = node;
        this.parent = parent;
    }

    public StringType getName() {
        if (this.name == null) {
            this.name = new StringType();
        }

        return this.name;
    }

    public TreeNode setName(StringType name) {
        this.name = name;
        return this;
    }

    public StringType getLabel() {
        return this.label;
    }

    public TreeNode setLabel(StringType label) {
        this.label = label;
        return this;
    }

    public Type copy() {
        TreeNode treeNode = new TreeNode();
        this.copyValues(treeNode);
        return treeNode;
    }

    public boolean isEmpty() {
        return ElementUtil.isEmpty(new IElement[]{this.node});
    }

    protected Type typedCopy() {
        return this.copy();
    }

    public StringType getNodeId() {
        return this.nodeId;
    }

    public TreeNode setNodeId(StringType nodeId) {
        this.nodeId = nodeId;
        return this;
    }

    public Location getNode() {
        return this.node;
    }

    public TreeNode setNode(Location node) {
        this.node = node;
        return this;
    }

    public StringType getParent() {
        return this.parent;
    }

    public TreeNode setParent(StringType parent) {
        this.parent = parent;
        return this;
    }

    public List<ChildTreeNode> getChildren() {
        if (this.children == null) {
            this.children = new ArrayList();
        }

        return this.children;
    }

    public TreeNode setChildren(List<ChildTreeNode> children) {
        this.children = children;
        return this;
    }

    public void addChild(TreeNode node) {
        if (this.children == null) {
            this.children = new ArrayList();
        }

        ChildTreeNode childTreeNode = new ChildTreeNode();
        childTreeNode.setChildId(node.getNodeId());
        List<TreeNode> treeNodeList = new ArrayList();
        TreeNode treeNode = new TreeNode();
        treeNode.setNode(node.getNode());
        treeNode.setNodeId(node.getNodeId());
        treeNode.setLabel(node.getLabel());
        treeNode.setParent(node.getParent());
        treeNodeList.add(treeNode);
        childTreeNode.setChildren(treeNode);
        this.children.add(childTreeNode);
    }

    public TreeNode findChild(String childId) {
        String idString = LocationUtils.cleanIdString(childId);
        if (this.children != null && !this.children.isEmpty()) {
            Iterator var3 = this.children.iterator();

            while(var3.hasNext()) {
                ChildTreeNode child = (ChildTreeNode)var3.next();
                if (isChildFound(child, idString)) {
                    return child.getChildren();
                }

                if (child != null && child.getChildren() != null) {
                    TreeNode node = child.getChildren().findChild(idString);
                    if (node != null) {
                        return node;
                    }
                }
            }
        }

        return null;
    }

    private static boolean isChildFound(ChildTreeNode child, String idString) {
        return child != null && child.getChildren() != null && child.getChildren().getNodeId() != null && StringUtils.isNotBlank((CharSequence)child.getChildren().getNodeId().getValue()) && ((String)child.getChildren().getNodeId().getValue()).equals(idString);
    }
}
