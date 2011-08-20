import java.util.*;
class ChildNode implements Iterable<ChildNode> {
    public class ChildNodeIterator implements Iterator<ChildNode> {
        ChildNode node;
        public ChildNode next() {
            ChildNode saved = node;
            node = node.next;
            return saved;
        }
        public boolean hasNext() {
            return node != null;
        }
        public void remove() {
        }
        public ChildNodeIterator(ChildNode node) {
            this.node = node;
        }
    }
    public ChildNodeIterator iterator() {
        return new ChildNodeIterator(this);
    }
    public TreeNode node;
    public String label;
    public ChildNode next;
    public ChildNode(TreeNode node, String label, ChildNode next) {
        this.node = node;
        this.label = label;
        this.next = next;
    }
    // WARNING: only children of node
    public String toString() {
         StringBuilder output = new StringBuilder();
         if(node.children != null) {
             for (ChildNode child: node.children) {
                 output.append(child.node.toString());
                 output.append(",");
             }
         }
        return output.toString();
        //return label + "/" + node.toString();
    }
}
