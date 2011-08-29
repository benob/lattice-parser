import java.util.Vector;

class ParseContext {
    static public int numInstances = 0;
    public TreeNode stack;
    public InputArc[] input;
    public ParseContext(TreeNode stack, InputArc[] input) {
        numInstances++;
        this.stack = stack;
        this.input = input;
    }
}
