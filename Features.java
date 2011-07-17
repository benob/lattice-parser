import java.util.*;
import java.io.*;
import gnu.trove.*;
import liblinear.*;

class Features {
    class FeatureNodeComparator implements Comparator<FeatureNode> {
        public int compare(FeatureNode n1, FeatureNode n2) {
            if(n1.index > n2.index) return 1;
            return -1;
        }
    }
    public TObjectIntHashMap<String> featureDict = new TObjectIntHashMap<String>();
    TObjectIntHashMap<String> labelDict = new TObjectIntHashMap<String>();
    Vector<String> labels = new Vector<String>();
    TObjectIntHashMap<String> counts = new TObjectIntHashMap<String>();

    public Features() {
    }

    public Features(String filename) throws IOException {
        if(filename != null) loadDict(filename);
    }

    public void loadDict(String filename) throws IOException {
        BufferedReader input = new BufferedReader(new FileReader(filename));
        String line;
        boolean pastLabels = false;
        while(null != (line = input.readLine())) {
            if("".equals(line.trim())) {
                pastLabels = true;
            } else {
                String tokens[] = line.trim().split(" ");
                if(pastLabels) featureDict.put(tokens[0], Integer.parseInt(tokens[1]));
                else {
                    labels.add(tokens[0]);
                    labelDict.put(tokens[0], Integer.parseInt(tokens[1]));
                }
            }
        }
        input.close();
    }
    public void saveDict(String filename) throws IOException {
        PrintWriter output = new PrintWriter(filename);
        for(int i = 0; i < labels.size(); i++) {
            output.println(labels.get(i) + " " + i);
        }
        output.println(); // empty line
        TObjectIntIterator iterator = featureDict.iterator();
        for (int i = featureDict.size(); i-- > 0;) {
            iterator.advance();
            output.println(iterator.key() + " " + iterator.value());
        }
        output.close();
    }

    public void count(Vector<String> features) {
        for(String feature: features) {
            counts.adjustOrPutValue(feature, 1, 1);
        }
    }

    public int[] map(Vector<String> features) {
        int output[] = new int[features.size()];
        for(int i = 0; i < output.length; i++) {
            output[i] = featureDict.adjustOrPutValue(features.get(i), 0, featureDict.size());
        }
        return output;
    }
    public String labelText(int label) {
        return labels.get(label);
    }
    public int mapLabel(String label) {
        if(labelDict.containsKey(label)) {
            return labelDict.get(label);
        } else {
            labels.add(label);
            labelDict.put(label, labels.size() - 1);
            return labels.size() - 1;
        }
    }
    public FeatureNode[] mapForLibLinear(Vector<String> features) {
        FeatureNode output[] = new FeatureNode[features.size()];
        for(int i = 0; i < output.length; i++) {
            output[i] = new FeatureNode(featureDict.adjustOrPutValue(features.get(i), 0, featureDict.size()) + 1, 1);
        }
        Arrays.sort(output, new FeatureNodeComparator());
        int previous = 0;
        for(int i = 0; i < output.length; i++) {
            if(output[i].index == previous) System.out.printf("ERROR: dupe (%d, %d)\n", output[i].index, previous);
            previous = output[i].index;
        }
        return output;
    }

    public int numFeatures() { return featureDict.size(); }
    public int numLabels() { return labelDict.size(); }

    static public String join(Vector<String> array, String delimitor) {
        StringBuilder output = new StringBuilder();
        if(array.size() == 1) return array.get(1);
        output.append(array.get(1));
        for(int i = 1; i < array.size(); i++) {
            output.append(delimitor);
            output.append(array.get(i));
        }
        return output.toString();
    }

    static String mix(int a, String b) { return new StringBuffer().append(a).append(':').append(b).toString(); }
    static String mix(int a, String b, String c) { return new StringBuffer().append(a).append(':').append(b).append(':').append(c).toString(); }
    static String mix(int a, String b, String c, String d) { return new StringBuffer().append(a).append(':').append(b).append(':').append(c).append(':').append(d).toString(); }
    static String mix(int a, String b, String c, String d, String e) { return new StringBuffer().append(a).append(':').append(b).append(':').append(c).append(':').append(d).append(':').append(e).toString(); }

    static public Vector<String> getBoundaryFeatures(Node current, Node next) {
        Vector<String> features = new Vector<String>();
        features.add("w1w2:" + current.word.replace(",", "<comma>") + "_" + next.word.replace(",", "<comma>"));
        features.add("w1:" + current.word.replace(",", "<comma>"));
        features.add("w2:" + next.word.replace(",", "<comma>"));
        features.add("t1t2:" + current.tag.replace(",", "<comma>") + "_" + next.tag.replace(",", "<comma>"));
        features.add("t1:" + current.tag.replace(",", "<comma>"));
        features.add("t2:" + next.tag.replace(",", "<comma>"));
        return features;
    }

    static public Vector<String> getRegularFeatures(Vector<Node> stack, Vector<Node> input, int start) {
        Vector<String> features = new Vector<String>();
        String q0w = null, q1w = null, q2w = null; // input words: i, i+1, i+2
        String q0t = null, q1t = null, q2t = null; // input tags: i, i+1, i+2
        String qp1w = null, qp2w = null; // input words: i-1, i-2
        String qp1t = null, qp2t = null; // input tags: i-1, i-2
        String s0w = null, s1w = null, s2w = null; // stack words: j-1, j-2, j-3
        String s0t = null, s1t = null, s2t = null; // stack tags: j -1, j-2, j-3
        String s0lc = null, s1lc = null, s0rc = null, s1rc = null; // stack left/right child tag (j-1, j-2)
        String s0lcl = null, s1lcl = null, s0rcl = null, s1rcl = null; // stack left/right label (j-1, j-2)
        int dist = 0;
        if(start > 0) { qp1w = input.get(start - 1).word; qp1t = input.get(start - 1).tag; }
        if(start > 1) { qp2w = input.get(start - 2).word; qp2t = input.get(start - 2).tag; }
        if(start < input.size()) { q0w = input.get(start).word; q0t = input.get(start).tag; }
        if(start < input.size() - 1) { q1w = input.get(start + 1).word; q1t = input.get(start + 1).tag; }
        if(start < input.size() - 2) { q2w = input.get(start + 2).word; q2t = input.get(start + 2).tag; }
        if(stack.size() > 0) {
            Node s0 = stack.get(stack.size() - 1);
            s0w = s0.word;
            s0t = s0.tag;
            if(s0.leftMostChild != null) {
                s0lc = s0.leftMostChild.tag;
                s0lcl = s0.leftMostChild.label;
            }
            if(s0.rightMostChild != null) {
                s0rc = s0.rightMostChild.tag;
                s0rcl = s0.rightMostChild.label;
            }
        }
        if(stack.size() > 1) { 
            Node s1 = stack.get(stack.size() - 2);
            s1w = s1.word;
            s1t = s1.tag;
            if(s1.leftMostChild != null) {
                s1lc = s1.leftMostChild.tag;
                s1lcl = s1.leftMostChild.label;
            }
            if(s1.rightMostChild != null) {
                s1rc = s1.rightMostChild.tag;
                s1rcl = s1.rightMostChild.label;
            }
            dist = Math.abs(stack.get(stack.size() - 1).id - s1.id);
        }
        if(stack.size() > 2) {
            Node s2 = stack.get(stack.size() - 3);
            s2w = s2.word;
            s2t = s2.tag;
        }
        int id = 0;
        features.add(mix(id++, s0w));
        features.add(mix(id++, s1w));
        features.add(mix(id++, s0t));
        features.add(mix(id++, s1t));
        features.add(mix(id++, q0w));
        features.add(mix(id++, q0t));
        features.add(mix(id++, q1w));
        features.add(mix(id++, q1t));
        features.add(mix(id++, s0w, s0t));
        features.add(mix(id++, s1w, s1t));
        features.add(mix(id++, q0w, q0t));
        features.add(mix(id++, s0w, s1w));
        features.add(mix(id++, s0t, s1t));
        features.add(mix(id++, s0t, q0t));
        features.add(mix(id++, s0t, s1w, s1t));
        features.add(mix(id++, s0w, s0t, s1w));
        features.add(mix(id++, s0w, s0t, s1t));
        features.add(mix(id++, s0w, s1w, s1t));
        features.add(mix(id++, s0w, s0t, s1w, s1t));
        features.add(mix(id++, s0t, q0t, q1t));
        features.add(mix(id++, s0w, q0t, q1t));
        features.add(mix(id++, s1t, s0t, q0t));
        features.add(mix(id++, s1t, s0w, q0t));
        features.add(mix(id++, s1t, s1lc, s0t));
        features.add(mix(id++, s1t, s0t, s0rc));
        features.add(mix(id++, s1t, s1rc, s0w));
        features.add(mix(id++, s1t, s1rc, s0t));
        features.add(mix(id++, s1t, s1lc, s0t));
        features.add(mix(id++, s1t, s0w, s0lc));
        features.add(mix(id++, s0t, s0lcl));
        features.add(mix(id++, s0t, s0lcl, s0lc));
        features.add(mix(id++, s0t, s0rcl));
        features.add(mix(id++, s0t, s0rcl, s0rc));
        features.add(mix(id++, s1t, s1lcl));
        features.add(mix(id++, s1t, s1lcl, s1lc));
        features.add(mix(id++, s1t, s1rcl));
        features.add(mix(id++, s1t, s1rcl, s1rc));
        return features;
    }

    static public Vector<String> getLatticeFeatures(ParseContext context) {
        Vector<String> features = new Vector<String>();
        String q0w = null, q1w = null, q2w = null; // input words: i, i+1, i+2
        String q0t = null, q1t = null, q2t = null; // input tags: i, i+1, i+2
        String s0w = null, s1w = null, s2w = null; // stack words: j-1, j-2, j-3
        String s0t = null, s1t = null, s2t = null; // stack tags: j -1, j-2, j-3
        String s0lc = null, s1lc = null, s0rc = null, s1rc = null; // stack left/right child tag (j-1, j-2)
        String s0lcl = null, s1lcl = null, s0rcl = null, s1rcl = null; // stack left/right label (j-1, j-2)
        if(context.input[0] != null) { q0w = context.input[0].word; q0t = context.input[0].tag; }
        if(context.input[1] != null) { q1w = context.input[1].word; q1t = context.input[1].tag; }
        if(context.input[2] != null) { q2w = context.input[2].word; q2t = context.input[2].tag; }
        // TODO: dependency-label-based features
        if(context.stack != null) {
            s0w = context.stack.tree.input.word;
            s0t = context.stack.tree.input.tag;
            if(context.stack.tree.children.length > 0) {
                s0lc = context.stack.tree.children[0].input.tag;
                s0lcl = context.stack.tree.labels[0];
                s0rc = context.stack.tree.children[context.stack.tree.children.length - 1].input.tag;
                s0rcl = context.stack.tree.labels[context.stack.tree.labels.length - 1];
            }
            if(context.stack.next != null) {
                s1w = context.stack.next.tree.input.word;
                s1t = context.stack.next.tree.input.tag;
                if(context.stack.next.tree.children.length > 0) {
                    s1lc = context.stack.next.tree.children[0].input.tag;
                    s1lcl = context.stack.next.tree.labels[0];
                    s1rc = context.stack.next.tree.children[context.stack.next.tree.children.length - 1].input.tag;
                    s1rcl = context.stack.next.tree.labels[context.stack.next.tree.labels.length - 1];
                }
                if(context.stack.next.next != null) {
                    s2w = context.stack.next.next.tree.input.word;
                    s2t = context.stack.next.next.tree.input.tag;
                }
            }
        }
        int id = 0;
        features.add(mix(id++, s0w));
        features.add(mix(id++, s1w));
        features.add(mix(id++, s0t));
        features.add(mix(id++, s1t));
        features.add(mix(id++, q0w));
        features.add(mix(id++, q0t));
        features.add(mix(id++, q1w));
        features.add(mix(id++, q1t));
        features.add(mix(id++, s0w, s0t));
        features.add(mix(id++, s1w, s1t));
        features.add(mix(id++, q0w, q0t));
        features.add(mix(id++, s0w, s1w));
        features.add(mix(id++, s0t, s1t));
        features.add(mix(id++, s0t, q0t));
        features.add(mix(id++, s0t, s1w, s1t));
        features.add(mix(id++, s0w, s0t, s1w));
        features.add(mix(id++, s0w, s0t, s1t));
        features.add(mix(id++, s0w, s1w, s1t));
        features.add(mix(id++, s0w, s0t, s1w, s1t));
        features.add(mix(id++, s0t, q0t, q1t));
        features.add(mix(id++, s0w, q0t, q1t));
        features.add(mix(id++, s1t, s0t, q0t));
        features.add(mix(id++, s1t, s0w, q0t));
        features.add(mix(id++, s1t, s1lc, s0t));
        features.add(mix(id++, s1t, s0t, s0rc));
        features.add(mix(id++, s1t, s1rc, s0w));
        features.add(mix(id++, s1t, s1rc, s0t));
        features.add(mix(id++, s1t, s1lc, s0t));
        features.add(mix(id++, s1t, s0w, s0lc));
        features.add(mix(id++, s0t, s0lcl));
        features.add(mix(id++, s0t, s0lcl, s0lc));
        features.add(mix(id++, s0t, s0rcl));
        features.add(mix(id++, s0t, s0rcl, s0rc));
        features.add(mix(id++, s1t, s1lcl));
        features.add(mix(id++, s1t, s1lcl, s1lc));
        features.add(mix(id++, s1t, s1rcl));
        features.add(mix(id++, s1t, s1rcl, s1rc));
        return features;
    }
}
