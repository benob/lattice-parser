import java.util.Vector;

class ParseContext {
    static public int numInstances = 0;
    public StackElement stack;
    public InputArc[] input;
    public ParseContext(StackElement stack, InputArc[] input) {
        numInstances++;
        this.stack = stack;
        this.input = input;
    }
}
