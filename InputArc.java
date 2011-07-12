class InputArc {
    public String word;
    public String tag;
    public InputNode next;
    public int id;
    public int hashCode() {
        return word.hashCode() ^ tag.hashCode() ^ id;
    }
    public InputArc(String word, String tag, InputNode next) {
        this.word = word;
        this.tag = tag;
        this.next = next;
    }
}
