/*This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; version 3 of the License.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.
*/

class InputArc {
    public static int numInstances = 0;
    public String word;
    public String tag;
    public InputNode next;
    public int id;
    public int hashCode() {
        return word.hashCode() ^ tag.hashCode() ^ id;
    }
    public InputArc(String word, String tag, InputNode next) {
        numInstances++;
        this.word = word;
        this.tag = tag;
        this.next = next;
    }
    public String toString() {
        return "" + id + ":" + word + ":" + tag;
    }
}
