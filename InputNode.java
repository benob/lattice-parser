/*This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; version 2 of the License.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.
*/

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
