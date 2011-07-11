import java.util.Vector;

class ParseContext {
    public StackNode stack;
    public InputArc[] input;
    public ParseContext(StackNode stack, InputArc[] input) {
        this.stack = stack;
        this.input = input;
    }
}
