/*This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; version 3 of the License.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.
*/

import java.io.PrintStream;
import java.util.Collections;
import java.util.Vector;

class TreeNode implements Comparable<TreeNode> {
     public static int numInstances = 0;

     public ChildNode children = null;
     public InputArc input = null;
     public String representation = null;
     public TreeNode next; // next node in the stack
     int id = 0;
     int parentId = 0;

     public int compareTo(TreeNode other)
     {
         if (other.input.id < this.input.id) return 1;
         return -1;
     }

     public String toString()
     {
         if (this.representation != null) return this.representation;
         StringBuilder output = new StringBuilder();
         //output.append(input.id);
         output.append(input.toString());
         if(children != null) {
             output.append("(");
             for (ChildNode child: children) {
                 output.append(child.label + "/" + child.node.toString());
                 output.append(",");
             }
             output.append(")");
         }
         this.representation = output.toString();
         return this.representation;
     }

     public TreeNode(TreeNode other) {
         numInstances += 1;
         this.children = other.children;
         this.input = other.input;
         this.representation = other.representation;
     }

     public TreeNode(TreeNode other, TreeNode child, String label) {
         numInstances += 1;
         this.input = other.input;
         this.children = new ChildNode(child, label, other.children);
     }

     public TreeNode(InputArc input) {
         numInstances += 1;
         this.children = null;
         this.input = input;
     }

     public TreeNode addChild(TreeNode child, String label) {
         return new TreeNode(this, child, label);
     }

     public void print(int parent) {
         System.out.println(new StringBuilder().append(input.id).append(" ").append(input.word).append(" ").append(parent).toString());
         for (ChildNode child : children)
             child.node.print(input.id);
     }

     public Vector<TreeNode> collect() {
         Vector<TreeNode> output = new Vector<TreeNode>();
         output.add(this);
         if(children != null) {
             for (ChildNode child: children) {
                 output.addAll(child.node.collect());
             }
         }
         Collections.sort(output);
         return output;
     }

     public void setParentId(int id) {
         parentId = id;
         if(children != null) {
             for(ChildNode child: children) {
                 child.node.setParentId(this.input.id);
             }
         }
     }

     public ChildNode rightMostChild() {
         int max = 0;
         ChildNode argmax = null;
         for(ChildNode child: children) {
             if(argmax == null || child.node.input.id > max) {
                 max = child.node.input.id;
                 argmax = child;
             }
         }
         return argmax;
     }

     public ChildNode leftMostChild() {
         int min = 0;
         ChildNode argmin = null;
         for(ChildNode child: children) {
             if(argmin == null || child.node.input.id < min) {
                 min = child.node.input.id;
                 argmin = child;
             }
         }
         return argmin;
     }
     public int numChildren() {
         if(children == null) return 0;
         int num = 0;
         for(ChildNode child: children) {
             num++;
         }
         return num;
     }
}

