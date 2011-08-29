/*This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; version 2 of the License.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.
*/

import java.util.Vector;

class ParseContext {
    static public int numInstances = 0;
    public TreeNode stack;
    public InputArc[] input;
    public ParseContext(TreeNode stack, InputArc[] input) {
        numInstances++;
        this.stack = stack;
        this.input = input;
    }
}
