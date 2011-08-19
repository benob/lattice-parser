class ChildNode implements Iterable {
    class ChildNodeIterator<ChildNode> {
        ChildNode node;
        ChildNode next() {
            return node.next;
        }
    }
    public ChildNodeIterator<ChildNode> iterator() {
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
}
