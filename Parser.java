import java.util.*;
import java.io.*;
import gnu.trove.*;
import liblinear.*;

// implementation of the arc normal parser
class Parser {
    public static final int SHIFT = 0;
    public static final int LEFT = 1;
    public static final int RIGHT = 2;
    public static final int EOS = 3;
    public static final int numActions = 4;
    public static final String actionText[] = {"shift", "left", "right", "eos"};

    public Features mapper = new Features();
    public Problem problem = new Problem();
    public Model model = new Model();
    public int numExamples = 0;

    public Node extractFeatures(Vector<Node> input, Vector<Integer> actions) {
        Vector<Node> stack = new Vector<Node>();
        Node root = new Node(0);
        if(input.size() == 0) return null;
        if(input.size() == 1) {
            Node node = new Node(input.get(0));
            node.setParent(root);
            return root;
        }
        stack.add(new Node(input.get(0)));
        stack.add(new Node(input.get(1)));
        int start = 2;
        for(int action: actions) {
            String label = null;
            Vector<String> features = mapper.getRegularFeatures(stack, input, start);
            if(problem.l < problem.x.length) {
                if(action < 0) System.out.println("ERROR: action < 0");
                problem.x[problem.l] = mapper.mapForLibLinear(features);
                problem.y[problem.l] = action + 1;
                problem.l++;
            } else {
                System.err.println("ERROR: too many training transitions " + problem.l);
                return null;
            }
            if(action == SHIFT) {
                if(start >= input.size()) {
                    System.err.printf("ERROR: impossible action \"%s\", start >= input.size()\n", actionText[action]);
                    return null;
                }
                stack.add(new Node(input.get(start)));
                start++;
            } else if(action == LEFT) {
                if(stack.size() < 2) {
                    System.err.printf("ERROR: impossible action \"%s\", stack.size() < 2\n", actionText[action]);
                    return null;
                }
                Node first = stack.get(stack.size() - 1);
                Node second = stack.get(stack.size() - 2);
                first.setParent(second);
                first.label = label;
                stack.set(stack.size() - 2, second);
                stack.remove(stack.size() - 1);
            } else if(action == RIGHT) {
                if(stack.size() < 2) {
                    System.err.printf("ERROR: impossible action \"%s\", stack.size() < 2\n", actionText[action]);
                    return null;
                }
                Node first = stack.get(stack.size() - 1);
                Node second = stack.get(stack.size() - 2);
                second.setParent(first);
                second.label = label;
                stack.set(stack.size() - 2, first);
                stack.remove(stack.size() - 1);
            }
        }
        Node node = stack.get(0);
        node.setParent(root);
        return root;
    }

    public Node predict(Vector<Node> input) {
        Vector<Node> stack = new Vector<Node>();
        Node root = new Node(0);
        if(input.size() == 0) return null;
        if(input.size() == 1) {
            Node node = new Node(input.get(0));
            node.setParent(root);
            return root;
        }
        stack.add(new Node(input.get(0)));
        stack.add(new Node(input.get(1)));
        int start = 2;
        double scores[] = new double[numActions + 1];
        while(stack.size() > 1 || start < input.size()) {
            Vector<String> features = Features.getRegularFeatures(stack, input, start);
            int action = Linear.predictValues(model, mapper.mapForLibLinear(features), scores) - 1;
            if(stack.size() < 2) {
                action = SHIFT;
            } else if(start >= input.size()) {
                for(int i = 0; i < numActions; i++) {
                    if(i != SHIFT && (action == -1 || action == SHIFT || scores[action + 1] < scores[i + 1])) {
                        action = i;
                    }
                }
            }
            String label = null; // need to predict label
            if(action == SHIFT) {
                stack.add(new Node(input.get(start)));
                start++;
            } else if(action == LEFT) {
                Node first = stack.get(stack.size() - 1);
                Node second = stack.get(stack.size() - 2);
                first.setParent(second);
                first.label = label;
                stack.set(stack.size() - 2, second);
                stack.remove(stack.size() - 1);
            } else if(action == RIGHT) {
                Node first = stack.get(stack.size() - 1);
                Node second = stack.get(stack.size() - 2);
                second.setParent(first);
                second.label = label;
                stack.set(stack.size() - 2, first);
                stack.remove(stack.size() - 1);
            }
        }
        Node node = stack.get(0);
        node.setParent(root);
        return root;
    }

    public Node replay(Vector<Node> input, Vector<Integer> actions) {
        Vector<Node> stack = new Vector<Node>();
        Node root = new Node(0);
        if(input.size() == 0) return null;
        if(input.size() == 1) {
            Node node = new Node(input.get(0));
            node.setParent(root);
            return root;
        }
        stack.add(new Node(input.get(0)));
        stack.add(new Node(input.get(1)));
        int start = 2;
        for(int action: actions) {
            String label = null;
            if(action == SHIFT) {
                if(start >= input.size()) {
                    System.err.printf("ERROR: impossible action \"%s\", start >= input.size()\n", actionText[action]);
                    return null;
                }
                stack.add(new Node(input.get(start)));
                start++;
            } else if(action == LEFT) {
                if(stack.size() < 2) {
                    System.err.printf("ERROR: impossible action \"%s\", stack.size() < 2\n", actionText[action]);
                    return null;
                }
                Node first = stack.get(stack.size() - 1);
                Node second = stack.get(stack.size() - 2);
                first.setParent(second);
                first.label = label;
                stack.set(stack.size() - 2, second);
                stack.remove(stack.size() - 1);
            } else if(action == RIGHT) {
                if(stack.size() < 2) {
                    System.err.printf("ERROR: impossible action \"%s\", stack.size() < 2\n", actionText[action]);
                    return null;
                }
                Node first = stack.get(stack.size() - 1);
                Node second = stack.get(stack.size() - 2);
                second.setParent(first);
                second.label = label;
                stack.set(stack.size() - 2, first);
                stack.remove(stack.size() - 1);
            }
        }
        Node node = stack.get(0);
        node.setParent(root);
        return root;
    }

    public Vector<Integer> oracle(Vector<Node> input) {
        Vector<Integer> actions = new Vector<Integer>();
        Vector<Node> stack = new Vector<Node>();
        if(input.size() < 2) return actions;
        stack.add(input.get(0));
        stack.add(input.get(1));
        int start = 2;
        while(stack.size() > 1 || start < input.size()) {
            if(stack.size() < 2 && start < input.size()) {
                actions.add(SHIFT);
                stack.add(input.get(start));
                start++;
            } else {
                Node first = stack.get(stack.size() - 1);
                Node second = stack.get(stack.size() - 2);
                // there is a link and there are no children in the remaining input
                if(first.parent == second && (first.rightMostChild == null || start >= input.size() || first.rightMostChild.id < input.get(start).id)) {
                    actions.add(LEFT);
                    stack.set(stack.size() - 2, second);
                    stack.remove(stack.size() - 1);
                } else if(second.parent == first && (second.rightMostChild == null || start >= input.size() || second.rightMostChild.id < input.get(start).id)) {
                    actions.add(RIGHT);
                    stack.set(stack.size() - 2, first);
                    stack.remove(stack.size() - 1);
                } else if(start < input.size()) {
                    actions.add(SHIFT);
                    stack.add(input.get(start));
                    start++;
                } else {
                    //System.err.println("ERROR: dead end!");
                    return null;
                }
            }
        }
        return actions;
    }

    public void train(String filename) throws IOException {
        Node current;
        BufferedReader input = new BufferedReader(new FileReader(filename));
        Vector<Node> sentences = new Vector<Node>();
        numExamples = 0;
        while(null != (current = Node.readNext(input))) {
            /*current.speechify();
            current.renumber();*/
            sentences.add(current);
            if(current.size() != 1) 
                numExamples += current.size() * 2 - 3; // there will be twice as many actions minus 2 shifts at the begining and the root at the end
        }
        System.out.printf("%d examples\n", numExamples);
        problem.x = new FeatureNode[numExamples][];
        problem.y = new int[numExamples];
        problem.l = 0;
        for(Node sentence: sentences) {
            Vector<Node> nodes = sentence.collect();
            Vector<Integer> actions = oracle(nodes);
            if(actions != null) {
                extractFeatures(nodes, actions);
            } else {
                System.err.println("ERROR: found a non-projective tree");
                return;
            }
            System.out.printf("\rfeatures: %.2f%%", 100.0 * problem.l / numExamples);
        }
        System.out.printf("\rfeatures: %.2f%%\n", 100.0 * problem.l / numExamples);
        problem.n = mapper.numFeatures();
        model = Linear.train(problem, new Parameter(SolverType.MCSVM_CS, 0.3, 0.01));
    }

    public void test(String filename) throws IOException {
        Node current;
        BufferedReader input = new BufferedReader(new FileReader(filename));
        int num = 0, errors = 0;
        while(null != (current = Node.readNext(input))) {
            /*current.speechify();
            current.renumber();*/
            Vector<Node> reference = current.collect();
            Node predicted = predict(reference);
            Vector<Node> hypothesis = predicted.collect();
            for(int i = 0; i < hypothesis.size(); i++) {
                if(hypothesis.get(i).parent.id != reference.get(i).parent.id) errors++;
                num++;
            }
            System.out.printf("\rerror rate: %.2f", 100.0 * errors / num);
        }
        System.out.printf("\rerror rate: %.2f\n", 100.0 * errors / num);
    }

    public static void main(String args[]) {
        try {
            Parser parser = new Parser();
            parser.train(args[0]);
            parser.test(args[1]);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
