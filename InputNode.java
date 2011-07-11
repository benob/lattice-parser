import java.util.Vector;
class InputNode {
    public int id;
    public Vector<InputArc> outgoing;
    public InputNode(Vector<InputArc> arcs) {
        if(arcs != null) outgoing = arcs;
        else outgoing = new Vector<InputArc>();
    }
    public void number(int id) {
        if(id >= this.id) {
            this.id = id;
            for(InputArc arc: outgoing) {
                arc.number(id);
            }
        }
    }
}
