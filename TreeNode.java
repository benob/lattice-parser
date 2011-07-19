/*    */ import java.io.PrintStream;
/*    */ import java.util.Collections;
/*    */ import java.util.Vector;
/*    */ 
/*    */ class TreeNode
/*    */   implements Comparable<TreeNode>
/*    */ {
/*  4 */   public static int numInstances = 0;
/*    */ 
/*  9 */   public TreeNode[] children = null;
/* 10 */   public String[] labels = null;
/* 11 */   public InputArc input = null;
/* 12 */   public String representation = null;
/* 13 */   int id = 0;
/*    */ 
/*    */   public int compareTo(TreeNode other)
/*    */   {
/*  6 */     if (other.input.id < this.input.id) return 1;
/*  7 */     return -1;
/*    */   }
/*    */ 
/*    */   public String toString()
/*    */   {
/* 15 */     if (this.representation != null) return this.representation;
/* 16 */     StringBuilder output = new StringBuilder("(");
/* 17 */     output.append(this.input.id);
/* 18 */     for (int i = 0; i < this.children.length; i++) {
/* 19 */       output.append(this.labels[i]);
/* 20 */       output.append(":");
/* 21 */       output.append(this.children[i].toString());
/*    */     }
/* 23 */     output.append(")");
/* 24 */     this.representation = output.toString();
/* 25 */     return this.representation;
/*    */   }
/*    */ 
/*    */   public TreeNode(TreeNode other) {
/* 29 */     numInstances += 1;
/* 30 */     this.children = ((TreeNode[])(TreeNode[])other.children.clone());
/* 31 */     this.labels = ((String[])(String[])other.labels.clone());
/* 32 */     this.input = other.input;
/* 33 */     this.representation = other.representation;
/*    */   }
/*    */   public TreeNode(TreeNode other, TreeNode child, String label) {
/* 36 */     numInstances += 1;
/* 37 */     this.children = new TreeNode[other.children.length + 1];
/* 38 */     this.labels = new String[other.labels.length + 1];
/* 39 */     if (other.children.length == 0) {
/* 40 */       this.children[0] = child;
/* 41 */       this.labels[0] = label;
/*    */     } else {
/* 43 */       boolean done = false;
/* 44 */       for (int i = 0; i < other.children.length; i++) {
/* 45 */         if (other.children[i].input.id < child.input.id) {
/* 46 */           this.children[i] = other.children[i];
/* 47 */           this.labels[i] = other.labels[i];
/*    */         } else {
/* 49 */           if (!done) {
/* 50 */             this.children[i] = child;
/* 51 */             this.labels[i] = label;
/* 52 */             done = true;
/*    */           }
/* 54 */           this.children[(i + 1)] = other.children[i];
/* 55 */           this.labels[(i + 1)] = other.labels[i];
/*    */         }
/*    */       }
/* 58 */       if (!done) {
/* 59 */         this.children[(this.children.length - 1)] = child;
/* 60 */         this.labels[(this.labels.length - 1)] = label;
/*    */       }
/*    */     }
/* 63 */     this.input = other.input;
/* 64 */     toString();
/*    */   }
/*    */   public TreeNode(InputArc input) {
/* 67 */     numInstances += 1;
/* 68 */     this.children = new TreeNode[0];
/* 69 */     this.labels = new String[0];
/* 70 */     this.input = input;
/* 71 */     toString();
/*    */   }
/*    */   public TreeNode addChild(TreeNode child, String label) {
/* 74 */     return new TreeNode(this, child, label);
/*    */   }
/*    */   public void print(int parent) {
/* 77 */     System.out.println(new StringBuilder().append(this.input.id).append(" ").append(this.input.word).append(" ").append(parent).toString());
/* 78 */     for (TreeNode child : this.children)
/* 79 */       child.print(this.input.id);
/*    */   }
/*    */ 
/*    */   public Vector<TreeNode> collect() {
/* 83 */     Vector output = new Vector();
/* 84 */     output.add(this);
/* 85 */     for (TreeNode child : this.children) {
/* 86 */       output.addAll(child.collect());
/*    */     }
/* 88 */     Collections.sort(output);
/* 89 */     return output;
/*    */   }
/*    */ }

/* Location:           /home/favre/work/lattice-parser/
 * Qualified Name:     TreeNode
 * JD-Core Version:    0.6.0
 */