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

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

@DatatypeDef(
        name = "Tree"
)
public class Tree extends Type implements ICompositeType {
    @Child(
            name = "listOfNodes",
            type = {SingleTreeNode.class}
    )
    private SingleTreeNode listOfNodes = new SingleTreeNode();
    @Child(
            name = "parentChildren",
            type = {ParentChildrenMap.class},
            order = 1,
            min = 0,
            max = -1,
            modifier = false,
            summary = false
    )
    private List<ParentChildrenMap> parentChildren = new ArrayList();

    public SingleTreeNode getTree() {
        return this.listOfNodes;
    }

    public Tree() {
    }

    private void addToParentChildRelation(String parentId, String id) {
        if (this.parentChildren == null) {
            this.parentChildren = new CopyOnWriteArrayList();
        }

        var k = this.getChildrenIdsByParentId(parentId);
        List<StringType> kids = k != null ? k : new ArrayList<>();

        StringType idStringType = new StringType();
        String idString = LocationUtils.cleanIdString(id);
        idStringType.setValue(idString);
        StringType parentStringType = new StringType();
        parentStringType.setValue(parentId);
        kids.add(idStringType);
        AtomicReference<Boolean> setParentChildMap = new AtomicReference(false);
        this.parentChildren.parallelStream().filter((parentChildrenMapx) -> {
            return parentChildrenMapx != null && parentChildrenMapx.getIdentifier() != null && StringUtils.isNotBlank((CharSequence) parentChildrenMapx.getIdentifier().getValue()) && ((String) parentChildrenMapx.getIdentifier().getValue()).equals(parentId);
        }).forEach((innerParentChildrenMap) -> {
            innerParentChildrenMap.setChildIdentifiers(kids);
            setParentChildMap.set(true);
        });
        if (!(Boolean) setParentChildMap.get()) {
            ParentChildrenMap parentChildrenMap = new ParentChildrenMap();
            parentChildrenMap.setIdentifier(parentStringType);
            parentChildrenMap.setChildIdentifiers((List) kids);
            this.parentChildren.add(parentChildrenMap);
        }

    }

    private List<StringType> getChildrenIdsByParentId(String parentId) {
        Optional<List<StringType>> kidsOptional = this.parentChildren.parallelStream().filter((parentChildrenMap) -> {
            return parentChildrenMap != null && parentChildrenMap.getIdentifier() != null && StringUtils.isNotBlank((CharSequence) parentChildrenMap.getIdentifier().getValue()) && ((String) parentChildrenMap.getIdentifier().getValue()).equals(parentId);
        }).map(ParentChildrenMap::getChildIdentifiers).filter(Objects::nonNull).findFirst();
        return kidsOptional.orElse(null);
    }

    public void addNode(String id, String label, Location node, String parentId) {
        if (this.listOfNodes == null) {
            this.listOfNodes = new SingleTreeNode();
        }


        TreeNode treenode = this.getNode(id);
        if (treenode == null) {
            TreeNode treeNode = this.makeNode(id, null, label, node, parentId);
            if (parentId != null) {
                this.addToParentChildRelation(parentId, id);
                TreeNode parentNode = this.getNode(parentId);
                if (parentNode != null) {
                    parentNode.addChild(treeNode);
                } else {
                    SingleTreeNode singleTreeNode = getSingleTreeNode(id, treeNode);
                    this.listOfNodes = singleTreeNode;
                }
            } else {
                SingleTreeNode singleTreeNode = getSingleTreeNode(id, treeNode);
                this.listOfNodes = singleTreeNode;
            }

        } else {
            throw new IllegalArgumentException("Node with ID " + id + " already exists in tree");
        }
    }

    private static SingleTreeNode getSingleTreeNode(String id, TreeNode treeNode) {
        String idString = LocationUtils.cleanIdString(id);
        SingleTreeNode singleTreeNode = new SingleTreeNode();
        StringType treeNodeId = new StringType();
        treeNodeId.setValue(idString);
        singleTreeNode.setTreeNodeId(treeNodeId);
        singleTreeNode.setTreeNode(treeNode);
        return singleTreeNode;
    }

    private TreeNode makeNode(String currentNodeId, TreeNode treenode, String label, Location node, String parentId) {
        if (treenode == null) {
            treenode = new TreeNode();
            StringType nodeId = new StringType();
            String idString = LocationUtils.cleanIdString(currentNodeId);
            nodeId.setValue(idString);
            treenode.setNodeId(nodeId);
            StringType labelString = new StringType();
            labelString.setValue(label);
            treenode.setLabel(labelString);
            treenode.setNode(node);
            StringType parentIdString = new StringType();
            String parentIdStringVar = LocationUtils.cleanIdString(parentId);
            parentIdString.setValue(parentIdStringVar);
            treenode.setParent(parentIdString);
        }

        return treenode;
    }

    @Nullable
    public TreeNode getNode(String id) {
        String idString = LocationUtils.cleanIdString(id);
        if (this.listOfNodes.getTreeNodeId() != null && StringUtils.isNotBlank((CharSequence) this.listOfNodes.getTreeNodeId().getValue()) && ((String) this.listOfNodes.getTreeNodeId().getValue()).equals(idString)) {
            return this.listOfNodes.getTreeNode();
        } else {
            return this.listOfNodes != null && this.listOfNodes.getTreeNode() != null ? this.listOfNodes.getTreeNode().findChild(idString) : null;
        }
    }

    public SingleTreeNode getListOfNodes() {
        return this.listOfNodes;
    }

    public void setListOfNodes(SingleTreeNode listOfNodes) {
        this.listOfNodes = listOfNodes;
    }

    public List<ParentChildrenMap> getParentChildren() {
        return this.parentChildren;
    }

    public void setParentChildren(List<ParentChildrenMap> parentChildren) {
        this.parentChildren = parentChildren;
    }

    public Type copy() {
        Tree tree = new Tree();
        this.copyValues(tree);
        return tree;
    }

    public boolean isEmpty() {
        return ElementUtil.isEmpty(new IElement[]{this.listOfNodes});
    }

    protected Type typedCopy() {
        return this.copy();
    }
}
