/*This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; version 2 of the License.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.
*/

import java.util.*;
import java.io.*;

// adapted from tree-merger/TreeMerger.java
class HypergraphOutput {
    public String orSymbol = "-OR-";
    public boolean expandFsm = false;
    public boolean needOutputStates = false;

    public void writeHypergraph(Vector<TreeNode> trees, PrintStream output) {
        Node forest = new Node(trees.get(0), "ROOT");
        for(int i = 1; i < trees.size(); i++) {
            Node current = new Node(trees.get(i), "ROOT");
            Vector<Node> nodes = getDifferences(forest, current);
            if(nodes.size() > 0) {
                Node common = commonParent(nodes);
                Node merged = mergeAt(forest, current, common);
                Node minimized = minimizeTree(merged); 
                forest = minimized;
            }
        }
        nextState = 1;
        printHyperGraph(forest, 0, output);
        output.println();
    }

    Vector<Node> expandOrChildren(Node tree) {
        Vector<Node> output = new Vector<Node>();
        for(Node child: tree.children) {
            if(child.isOrNode) output.addAll(expandOrChildren(child));
            else output.add(child);
        }
        return output;
    }

    public Node minimizeTree(Node tree) {
        if(tree.isOrNode) {
            // all directly accessible OR-nodes should be merged with this one
            Vector<Node> children = expandOrChildren(tree);
            Vector<Node> output = new Vector<Node>();
            for(int i = 0; i < children.size(); i++) {
                boolean keep = true;
                for(int j = 0; j < children.size(); j++) {
                    if(i != j && isRedundant(children.get(j), children.get(i))) {
                        // if red(a,b) and red(b,a): we have to keep one!
                        if(!(i < j && isRedundant(children.get(i), children.get(j)))) {
                            keep = false;
                            break;
                        }
                    }
                }
                if(keep) output.add(children.get(i));
            }
            tree.children = output;
            if(tree.children.size() == 1) return minimizeTree(tree.children.firstElement());
        }
        for(int i = 0; i < tree.children.size(); i++) {
            Node node = minimizeTree(tree.children.get(i));
            node.parent = tree;
            tree.children.set(i, node);
        }
        return tree;
    }

    public boolean isRedundant(Node tree1, Node tree2) { // tree2 is redundant given tree1 
        if(tree1.isOrNode && tree2.isOrNode) {
            for(Node child: tree2.children) {
                if(!isRedundant(tree1, child)) return false;
            }
            return true;
        } else if(tree2.isOrNode) {
            return false; // tree1 should be a or-node
        } else if(tree1.isOrNode) {
            for(Node child: tree1.children) {
                if(isRedundant(child, tree2)) return true;
            }
            return false;
        } else {
            if(!(tree1.id == tree2.id && tree1.label.equals(tree2.label) && tree1.children.size() == tree2.children.size())) return false;
            for(int i = 0; i < tree1.children.size(); i++) {
                if(!isRedundant(tree1.children.get(i), tree2.children.get(i))) return false;
            }
            return true;
        }
    }

    public Node mergeAt(Node tree1, Node tree2, Node mergePointFromTree1) {
        if(tree1 == mergePointFromTree1) {
            Node output = new Node();
            output.isOrNode = true;
            output.label = orSymbol;
            output.children.add(tree1);
            output.children.add(tree2);
            return output;
        } else {
            if(tree1.isOrNode) {
                for(int i = 0; i < tree1.children.size(); i++) {
                    Node node = mergeAt(tree1.children.get(i), tree2, mergePointFromTree1);
                    node.parent = tree1;
                    tree1.children.set(i, node);
                }
            } else {
                if(!(tree1.id == tree2.id && tree1.label.equals(tree2.label) && tree1.children.size() == tree2.children.size())) return tree1; // cut mismatches
                for(int i = 0; i < tree1.children.size(); i++) {
                    Node node = mergeAt(tree1.children.get(i), tree2.children.get(i), mergePointFromTree1);
                    node.parent = tree1;
                    tree1.children.set(i, node);
                }
            }
            return tree1;
        }
    }

    public Node commonParent(Vector<Node> nodes) {
        Node node = nodes.firstElement();
        for(int i = 1; i < nodes.size(); i++) {
            Node peer = nodes.get(i);
            Node parent1 = node;
            while(parent1 != null) {
                Node parent2 = peer;
                while(parent1 != parent2 && parent2 != null) {
                    parent2 = parent2.parent;
                }
                if(parent1 == parent2) break;
                parent1 = parent1.parent;
            }
            node = parent1;
        }
        return node;
    }

    public Vector<Node> getDifferences(Node tree1, Node tree2) {
        Vector<Node> output = new Vector<Node>();
        if(tree1.isOrNode) {
            Vector<Node> argmin = null;
            int min = 0;
            for(Node child: tree1.children) {
                Vector<Node> result = getDifferences(child, tree2);
                int size = 0;
                for(Node node: result) size += node.size();
                if(argmin == null || size < min) {
                    argmin = result;
                    min = size;
                }
            }
            output.addAll(argmin);
            return output;
        } else if(tree1.children.size() != tree2.children.size() || tree1.id != tree2.id || (tree1.label != null && !tree1.label.equals(tree2.label))) {
            output.add(tree1);
            return output;
        } else {
            for(int i = 0; i < tree1.children.size(); i++) {
                output.addAll(getDifferences(tree1.children.get(i), tree2.children.get(i)));
            }
        }
        return output;
    }

    public int nextState = 1;
    public HashMap<String, Integer> stateId = new HashMap<String, Integer>();
    public void printHyperGraph(Node node, int from, PrintStream output) {
        int state = 0;
        if(expandFsm || node.children.size() == 0) {
            state = nextState++;
            for(Node child: node.children) {
                printHyperGraph(child, state, output);
            }
        } else {
            String key = node.factorRepresentation();
            if(stateId.containsKey(key)) {
                state = stateId.get(key);
            } else {
                state = nextState++;
                stateId.put(key, state);
                for(Node child: node.children) {
                    printHyperGraph(child, state, output);
                }
            }
        }
        if(node.isOrNode) output.printf("%d %d %s\n", from, state, node.label); 
        else output.printf("%d %d %d:%s:%s %s\n", from, state, node.id, node.word, node.tag, node.label); 
        if(needOutputStates && node.children.size() == 0) System.out.printf("%d\n", state);
    }

}
