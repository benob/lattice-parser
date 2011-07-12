import java.util.*;
class InputNode {
    public Vector<InputArc> outgoing;
    public InputNode(Vector<InputArc> arcs) {
        if(arcs != null) outgoing = arcs;
        else outgoing = new Vector<InputArc>();
    }
    void number(int start) {
        LinkedList<InputArc> queue = new LinkedList<InputArc>(outgoing);
        while(queue.size() > 0) {
            InputArc first = queue.removeFirst();
            first.id = start;
            if(first.next != null) queue.addAll(first.next.outgoing);
            start++;
        }
    }
}
