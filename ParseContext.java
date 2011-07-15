import java.util.Vector;

class ParseContext {
    static public int numInstances = 0;
    public StackNode stack;
    public InputArc[] input;
    public ParseContext(StackNode stack, InputArc[] input) {
        numInstances++;
        this.stack = stack;
        this.input = input;
    }
}
