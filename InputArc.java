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
