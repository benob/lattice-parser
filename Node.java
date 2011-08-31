/*This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; version 3 of the License.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.
*/

import java.util.*;
import java.io.*;

class Node implements Comparable<Node> {
    Node parent;
    public int id;
    public int parent_id;
    public String word, lemma, tag;
    public String label;
    public Vector<Node> children;
    public Node previous;
    public Node next;
    public Node nextInStack;
    public Node leftMostChild;
    public Node rightMostChild;
    public Node(int id) {
        this.id = id;
        children = new Vector<Node>();
    }
    public Node(Node peer) {
        id = peer.id;
        word = peer.word;
        tag = peer.tag;
        lemma = peer.lemma;
        children = new Vector<Node>();
    }
    public Node() {
        children = new Vector<Node>();
    }
    boolean isOrNode;
    public Node(TreeNode node, String label) { // create non-shared structure from treenode
        isOrNode = false;
        id = node.input.id;
        word = node.input.word;
        tag = node.input.tag;
        this.label = label;
        children = new Vector<Node>();
        if(node.children != null) {
            for(ChildNode child: node.children) {
                children.add(new Node(child.node, child.label));
            }
        }
    }
    /*public Node clone() {
        Node node = new Node(this);
        node.children = children.clone();
    }*/
    public Node(String line) throws NumberFormatException {
        String tokens[] = line.trim().split("\t");
        //1   Influential _   JJ  _   _   2   NMOD    _   _
        id = Integer.parseInt(tokens[0]);
        word = tokens[1];
        tag = tokens[3];
        parent_id = Integer.parseInt(tokens[6]);
        label = tokens[7];
        children = new Vector<Node>();
    }
    public Node(int id, String word, String tag, int parent_id, String label) {
        this.id = id;
        this.word = word;
        this.tag = tag;
        this.parent_id = parent_id;
        this.label = label;
    }
    public void addChild(Node child) {
        child.parent_id = id;
        children.add(child);
        if(leftMostChild == null || child.id < leftMostChild.id) leftMostChild = child;
        if(rightMostChild == null || child.id > rightMostChild.id) rightMostChild = child;
    }
    public void removeChild(Node child) {
        children.remove(child);
        if(children.size() > 0) {
            if(child == leftMostChild) {
                leftMostChild = children.get(0);
                int min = leftMostChild.id;
                for(Node node: children) {
                    if(node.id < min) {
                        min = node.id;
                        leftMostChild = node;
                    }
                }
            }
            if(child == rightMostChild) {
                rightMostChild = children.get(0);
                int min = rightMostChild.id;
                for(Node node: children) {
                    if(node.id < min) {
                        min = node.id;
                        rightMostChild = node;
                    }
                }
            }
        }
    }
    public String factorRepresentation() {
        StringBuffer output = new StringBuffer("");
        for(Node child: children) {
            output.append(child.subTreeRepresentation());
        }
        return output.toString();
    }

    public String subTreeRepresentation() {
        StringBuffer output = new StringBuffer("(");
        output.append(label).append(":").append(id);
        for(Node child: children) {
            output.append(child.subTreeRepresentation());
        }
        output.append(")");
        return output.toString();
    }

    public String toSexp() {
        StringBuffer output = new StringBuffer("(");
        output.append(label).append(":").append(id).append(":").append(word);
        for(Node child: children) {
            output.append(" ");
            output.append(child.toSexp());
        }
        output.append(")");
        return output.toString();
    }

    public void setParent(Node parent) {
        if(this.parent != null) {
            this.parent.removeChild(this);
        }
        parent_id = 0;
        if(parent != null) parent.addChild(this);
        this.parent = parent;
    }
    public String toString() {
        StringBuffer output = new StringBuffer();
        output.append(id);
        output.append("\t");
        output.append(word);
        output.append("\t");
        output.append("_");
        output.append("\t");
        output.append(tag);
        output.append("\t");
        output.append("_");
        output.append("\t");
        output.append("_");
        output.append("\t");
        if(parent != null) output.append(parent.id);
        else output.append("-1");
        output.append("\t");
        output.append(label);
        output.append("\t");
        output.append("_");
        output.append("\t");
        output.append("_");
        return output.toString();
    }
    public int compareTo(Node o) {
        if(((Node)o).id < id) return 1;
        return -1;
    }
    public Vector<Node> collect() {
        Vector<Node> output = new Vector<Node>();
        if(id != 0) output.add(this);
        for(int i = 0; i < children.size(); i++) {
            output.addAll(children.get(i).collect());
        }
        Collections.sort(output);
        return output;
    }
    public Vector<Node> renumber() {
        return renumber(1);
    }
    public Vector<Node> renumber(int start) {
        Vector<Node> nodes = collect();
        Collections.sort(nodes);
        int max = 0;
        for(Node node: nodes) {
            if(max < node.id) max = node.id;
        }
        int mapping[] = new int[max + 1];
        for(int i = 0; i < nodes.size(); i++) {
            mapping[nodes.get(i).id] = start + i;
        }
        for(Node node: nodes) {
            node.id = mapping[node.id];
        }
        for(Node node: nodes) {
            if(node.parent != null) node.parent_id = node.parent.id;
            else node.parent_id = 0;
        }
        return nodes;
    }
    public void sortChildren() {
        Collections.sort(children);
        for(Node child: children) {
            child.sortChildren();
        }
    }
    public int size() {
        int size = 0;
        if(id != 0) size += 1;
        for(Node child: children) {
            size += child.size();
        }
        return size;
    }

    public void print(PrintStream output) {
        Vector<Node> nodes = renumber();
        Collections.sort(nodes);
        for(int i = 0; i < nodes.size(); i++) {
            output.println(nodes.get(i).toString());
        }
        output.println();
    }
    public boolean speechify() {
        if(word != null) word = word.toLowerCase();
        if(tag != null && tag.matches("^([.(),:]|--|''|``)$") && parent != null) return false; // drop punctuation
        Vector<Node> toRemove = new Vector<Node>();
        for(Node child: children) {
            if(child.speechify() == false) {
                toRemove.add(child);
            }
        }
        children.removeAll(toRemove);
        return true;
    }
    public static Node readNext(BufferedReader reader) throws IOException {
        String line;
        Vector<Node> nodes = new Vector<Node>();
        nodes.add(new Node(0));
        while(true) {
            line = reader.readLine();
            if(line == null) return null;
            if("".equals(line)) break;
            nodes.add(new Node(line));
        }
        for(int i = 1; i < nodes.size(); i++) {
            nodes.get(i).setParent(nodes.get(nodes.get(i).parent_id));
            nodes.get(i - 1).next = nodes.get(i);
        }
        for(int i = 0; i < nodes.size() - 1; i++) {
            nodes.get(i + 1).previous = nodes.get(i);
        }
        return nodes.get(0);
    }
    public boolean isProjective() {
        Vector<Node> nodes = collect();
        for(Node node: nodes) {
            for(Node peer: nodes) {
                if(node != peer) {
                    int a = node.id < node.parent.id ? node.id : node.parent.id;
                    int b = node.id < node.parent.id ? node.parent.id : node.id;
                    int c = peer.id < peer.parent.id ? peer.id : peer.parent.id;
                    int d = peer.id < peer.parent.id ? peer.parent.id : peer.id;
                    if(a < c && c < b && b < d) return false;
                    if(c < a && a < d && d < b) return false;
                }
            }
        }
        return true;
    }

    public static void main(String args[]) {
        try {
            BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
            Node tree;
            while(null != (tree = Node.readNext(input))) {
                tree.speechify();
                tree.print(System.out);
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
