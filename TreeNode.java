import java.io.PrintStream;
import java.util.Collections;
import java.util.Vector;

class TreeNode implements Comparable<TreeNode> {
     public static int numInstances = 0;

     public TreeNode[] children = null;
     public String[] labels = null;
     public InputArc input = null;
     public String representation = null;
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
         StringBuilder output = new StringBuilder("(");
         output.append(this.input.id);
         for (int i = 0; i < this.children.length; i++) {
             output.append(this.labels[i]);
             output.append(":");
             output.append(this.children[i].toString());
         }
         output.append(")");
         this.representation = output.toString();
         return this.representation;
     }

     public TreeNode(TreeNode other) {
         numInstances += 1;
         this.children = ((TreeNode[])(TreeNode[])other.children.clone());
         this.labels = ((String[])(String[])other.labels.clone());
         this.input = other.input;
         this.representation = other.representation;
     }

     public TreeNode(TreeNode other, TreeNode child, String label) {
         numInstances += 1;
         this.children = new TreeNode[other.children.length + 1];
         this.labels = new String[other.labels.length + 1];
         if (other.children.length == 0) {
             this.children[0] = child;
             this.labels[0] = label;
         } else {
             boolean done = false;
             for (int i = 0; i < other.children.length; i++) {
                 if (other.children[i].input.id < child.input.id) {
                     this.children[i] = other.children[i];
                     this.labels[i] = other.labels[i];
                 } else {
                     if (!done) {
                         this.children[i] = child;
                         this.labels[i] = label;
                         done = true;
                     }
                     this.children[(i + 1)] = other.children[i];
                     this.labels[(i + 1)] = other.labels[i];
                 }
             }
             if (!done) {
                 this.children[(this.children.length - 1)] = child;
                 this.labels[(this.labels.length - 1)] = label;
             }
         }
         this.input = other.input;
         toString();
     }

     public TreeNode(InputArc input) {
         numInstances += 1;
         this.children = new TreeNode[0];
         this.labels = new String[0];
         this.input = input;
         toString();
     }

     public TreeNode addChild(TreeNode child, String label) {
         return new TreeNode(this, child, label);
     }

     public void print(int parent) {
         System.out.println(new StringBuilder().append(this.input.id).append(" ").append(this.input.word).append(" ").append(parent).toString());
         for (TreeNode child : this.children)
             child.print(this.input.id);
     }

     public Vector<TreeNode> collect() {
         Vector<TreeNode> output = new Vector<TreeNode>();
         output.add(this);
         for (TreeNode child : this.children) {
             output.addAll(child.collect());
         }
         Collections.sort(output);
         return output;
     }

     public void setParentId(int id) {
         parentId = id;
         for(TreeNode child: children) {
             child.setParentId(this.input.id);
         }
     }

}

