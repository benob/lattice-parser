import java.util.*;

class StackNode implements Comparable<StackNode> {
    static public int numInstances = 0;
    public int compareTo(StackNode other) {
        if(other.input.id < input.id) return 1;
        return -1;
    }
    public StackNode next;
    public Vector<StackNode> children;
    public String label = null;
    public InputArc input;
    public Vector<StackNode> createdFrom;
    int id; // state id set during output
    public String representation = null;
    public String toString() {
        if(representation != null) return representation;
        StringBuilder output = new StringBuilder("(" + input.word);
        output.append(input.id);
        output.append(":");
        output.append(label);
        for(StackNode child: children) {
            output.append(child.toString());
        }
        output.append(")");
        representation = output.toString();
        return representation;
    }

    public StackNode(StackNode next, Vector<StackNode> children, InputArc input) {
        numInstances++;
        this.next = next;
        this.children = new Vector<StackNode>(children);
        this.input = input;
    }
    public StackNode(StackNode next, InputArc input) {
        numInstances++;
        this.next = next;
        this.children = new Vector<StackNode>();
        this.input = input;
    }
    public void print(int parent) {
        System.out.println(input.id + " " + input.word + " " + parent);
        for(StackNode child: children) {
            child.print(this.input.id);
        }
    }
    int parentId = 0;
    public void setParentId() {
        for(StackNode child: children) {
            child.parentId = input.id;
            child.setParentId();
        }
    }
    public Vector<StackNode> collect() {
        Vector<StackNode> output = new Vector<StackNode>();
        output.add(this);
        for(StackNode child: children) {
            output.addAll(child.collect());
        }
        Collections.sort(output);
        return output;
    }
}
