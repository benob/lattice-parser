class InputArc {
    public String word;
    public String tag;
    public InputNode next;
    public int id;
    public InputArc(String word, String tag, InputNode next) {
        this.word = word;
        this.tag = tag;
        this.next = next;
    }
    public void number(int id) {
        this.id = id;
        next.number(id + 1);
    }
}
