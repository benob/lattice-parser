/*   */ public class StackElement
/*   */ {
/* 2 */   public static int numInstances = 0;
/*   */   public StackElement next;
/*   */   public TreeNode tree;
/*   */ 
/*   */   public StackElement(StackElement next, TreeNode tree)
/*   */   {
/* 6 */     numInstances += 1;
/* 7 */     this.next = next;
/* 8 */     this.tree = tree;
/*   */   }
/*   */ }

/* Location:           /home/favre/work/lattice-parser/
 * Qualified Name:     StackElement
 * JD-Core Version:    0.6.0
 */