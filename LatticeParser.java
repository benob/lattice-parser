import java.util.*;
import java.io.*;
import gnu.trove.*;
import liblinear.*;

// implementation of the arc normal parser
class LatticeParser {
    public static final int SHIFT = 0;
    public static final int LEFT = 1;
    public static final int RIGHT = 2;
    public static final int numActions = 3;
    public static final String actionText[] = {"shift", "left", "right"};

    public Features mapper = new Features();
    public Problem problem = new Problem();
    public Model model = null;
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
            //for(String feature: features) { System.out.printf("%s ", feature); } System.out.println();
            if(model == null) {
                if(problem.l < problem.x.length) {
                    if(action < 0) System.out.println("ERROR: action < 0");
                    problem.x[problem.l] = mapper.mapForLibLinear(features);
                    problem.y[problem.l] = action + 1;
                    problem.l++;
                } else {
                    System.err.println("ERROR: too many training transitions " + problem.l);
                    return null;
                }
            }
            //System.out.println(actionText[action]);
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

    public Vector<StackNode> latticePredict(InputNode inputStart) {
        Vector<StackNode> output = new Vector<StackNode>();
        Vector<ParseContext> openParses = new Vector<ParseContext>();
        // init openParses with start contexts (assumes at least one arc)
        InputArc arcTrigram[] = new InputArc[3];
        for(InputArc arc: inputStart.outgoing) {
            arcTrigram[0] = arc;
            if(arc.next != null) {
                for(InputArc arc2: arc.next.outgoing) {
                    arcTrigram[1] = arc2;
                    if(arc2.next != null) {
                        for(InputArc arc3: arc2.next.outgoing) {
                            arcTrigram[2] = arc3;
                            openParses.add(new ParseContext(null, arcTrigram.clone()));
                        }
                    } else {
                        arcTrigram[2] = null;
                        openParses.add(new ParseContext(null, arcTrigram.clone()));
                    }
                }
            } else {
                arcTrigram[1] = null;
                openParses.add(new ParseContext(null, arcTrigram.clone()));
            }
        }

        double scores[] = new double[numActions + 1];

        while(openParses.size() > 0) {
            Vector<ParseContext> newOpenParses = new Vector<ParseContext>();
            for(ParseContext context: openParses) {
                //System.out.printf("(%s %s %s)\n", context.input[0] != null ? context.input[0].word : "null", context.input[1] != null ? context.input[1].word : "null", context.input[2] != null ? context.input[2].word : "null");
                Vector<String> features = Features.getLatticeFeatures(context);
                //for(String feature: features) { System.out.printf("%s ", feature); } System.out.println();
                int action = Linear.predictValues(model, mapper.mapForLibLinear(features), scores) - 1;
                boolean available[] = new boolean[numActions + 1];
                Arrays.fill(available, true);
                if(context.input[0] == null) available[SHIFT] = false;
                if(context.stack == null || context.stack.next == null) {
                    available[LEFT] = false;
                    available[RIGHT] = false;
                }
                action = -1; // recompute argmax with constraints
                //System.out.printf("available=[%s %s %s]\n", available[0], available[1], available[2]);
                for(int candidateAction = 0; candidateAction < numActions; candidateAction ++) {
                    if(available[candidateAction] && (action == -1 || scores[action] < scores[candidateAction])) 
                        action = candidateAction;
                }
                if(action == 1) action = 2; // why why why
                else if(action == 2) action = 1;
                //if(action != -1) System.out.println(actionText[action]);
                if(action == SHIFT) {
                    //System.out.println("shift " + context.input[0].word);
                    StackNode top = new StackNode(context.stack, context.input[0]);
                    context.input[0] = context.input[1];
                    context.input[1] = context.input[2];
                    if(context.input[1] != null && context.input[1].next != null && context.input[1].next.outgoing.size() > 0) { // a bit tricky
                        for(InputArc arc: context.input[1].next.outgoing) {
                            context.input[2] = arc;
                            newOpenParses.add(new ParseContext(top, context.input.clone()));
                        }
                    } else {
                        context.input[2] = null;
                        context.stack = top;
                        newOpenParses.add(context);
                    }
                } else if(action == LEFT) {
                    //System.out.println("left " + context.stack.next.input.word + " <- " + context.stack.input.word);
                    StackNode top = new StackNode(context.stack.next.next, context.stack.next.children, context.stack.next.input);
                    top.children.add(context.stack);
                    Collections.sort(top.children);
                    context.stack = top;
                    newOpenParses.add(context);
                } else if(action == RIGHT) {
                    //System.out.println("right " + context.stack.next.input.word + " -> " + context.stack.input.word);
                    StackNode top = new StackNode(context.stack.next.next, context.stack.children, context.stack.input);
                    top.children.add(context.stack.next);
                    Collections.sort(top.children);
                    context.stack = top;
                    newOpenParses.add(context);
                } else {
                    //System.out.println("COMPL");
                    output.add(context.stack); // generated a complete tree
                }
            }
            openParses = newOpenParses;
        }
        //System.out.println();
        return output;
    }

    public Node predict(Vector<Node> input, Vector<Integer> actions) {
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
        int currentAction = 0;
        double scores[] = new double[numActions + 1];
        while(stack.size() > 1 || start < input.size()) {
            Vector<String> features = Features.getRegularFeatures(stack, input, start);
            //for(String feature: features) { System.out.printf("%s ", feature); } System.out.println();
            FeatureNode[] featureIds = mapper.mapForLibLinear(features);
            int action = Linear.predictValues(model, mapper.mapForLibLinear(features), scores) - 1;
            boolean available[] = new boolean[numActions + 1];
            Arrays.fill(available, true);
            if(start >= input.size()) available[SHIFT] = false;
            if(stack.size() < 2) {
                available[LEFT] = false;
                available[RIGHT] = false;
            }
            action = -1; // recompute argmax with constraints
            //System.out.printf("available=[%s %s %s]\n", available[0], available[1], available[2]);
            for(int candidateAction = 0; candidateAction < numActions; candidateAction ++) {
                if(available[candidateAction] && (action == -1 || scores[action] < scores[candidateAction])) 
                    action = candidateAction;
            }
            if(action == 1) action = 2;
            else if(action == 2) action = 1;
            if(actions != null && actions.size() > currentAction) {
                System.out.printf("%d a=%d o=%d [%f %f %f %f]", currentAction, action, actions.get(currentAction), scores[0], scores[1], scores[2], scores[3]);
                System.out.print(action + 1);
                for(int i = 0; i < featureIds.length; i++) {
                    System.out.printf(" %d:%d", featureIds[i].index, (int) featureIds[i].value);
                }
                System.out.println();
            }
            currentAction++;
            String label = null; // need to predict label
            if(action == SHIFT) {
                //System.out.println("shift " + input.get(start).word);
                stack.add(new Node(input.get(start)));
                start++;
            } else if(action == LEFT) {
                Node first = stack.get(stack.size() - 1);
                Node second = stack.get(stack.size() - 2);
                //System.out.println("left " + second.word + " <- " + first.word);
                first.setParent(second);
                first.label = label;
                stack.set(stack.size() - 2, second);
                stack.remove(stack.size() - 1);
            } else if(action == RIGHT) {
                Node first = stack.get(stack.size() - 1);
                Node second = stack.get(stack.size() - 2);
                //System.out.println("right " + second.word + " -> " + first.word);
                second.setParent(first);
                second.label = label;
                stack.set(stack.size() - 2, first);
                stack.remove(stack.size() - 1);
            }
        }
        Node node = stack.get(0);
        node.setParent(root);
        System.out.println();
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
                System.out.println("shift " + input.get(start).word);
                stack.add(new Node(input.get(start)));
                start++;
            } else if(action == LEFT) {
                if(stack.size() < 2) {
                    System.err.printf("ERROR: impossible action \"%s\", stack.size() < 2\n", actionText[action]);
                    return null;
                }
                Node first = stack.get(stack.size() - 1);
                Node second = stack.get(stack.size() - 2);
                System.out.println("left " + second.word + " <- " + first.word);
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
                System.out.println("right " + second.word + " -> " + first.word);
                second.setParent(first);
                second.label = label;
                stack.set(stack.size() - 2, first);
                stack.remove(stack.size() - 1);
            }
        }
        Node node = stack.get(0);
        node.setParent(root);
        System.out.println();
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
        PrintWriter featureFile = new PrintWriter("all-examples.txt");
        for(int i = 0; i < problem.l; i++) {
            featureFile.print(problem.y[i]);
            for(int j = 0; j < problem.x[i].length; j++) {
                featureFile.printf(" %d:1", problem.x[i][j].index);//, problem.x[i][j].value);
            }
            featureFile.println();
        }
        featureFile.close();
        System.exit(0);
        model = Linear.train(problem, new Parameter(SolverType.MCSVM_CS, 1, 0.01));
        model.save(new File("model.txt"));
        mapper.saveDict("model.features");
    }

    public void testLattice(String filename) throws IOException {
        System.out.print("loading model: ");
        mapper.loadDict("model.features");
        model = Model.load(new File("all-examples.model"));
        System.out.println("ok.");
        BufferedReader input = new BufferedReader(new FileReader(filename));
        String line;
        Vector<InputNode> nodes = new Vector<InputNode>();
        while(null != (line = input.readLine())) {
            line = line.trim();
            if("".equals(line)) {
                nodes.firstElement().number(1);
                Vector<StackNode> forest = latticePredict(nodes.firstElement());
                for(StackNode tree: forest) {
                    tree.setParentId();
                    for(StackNode node: tree.collect()) {
                        System.out.printf("%d %s %s %d\n", node.input.id, node.input.word, node.input.tag, node.parentId);
                    }
                    System.out.println();
                }
                System.out.println();
            } else {
                String tokens[] = line.split(" ");
                if(tokens.length == 1) continue;
                int startNode = Integer.parseInt(tokens[0]);
                int endNode = Integer.parseInt(tokens[1]);
                String word = tokens[2];
                String tag = tokens[3];
                while(nodes.size() < startNode + 1) nodes.add(new InputNode(null));
                while(nodes.size() < endNode + 1) nodes.add(new InputNode(null));
                nodes.get(startNode).outgoing.add(new InputArc(word, tag, nodes.get(endNode)));
            }
        }
        if(nodes.size() > 0) {
            nodes.firstElement().number(1);
            Vector<StackNode> forest = latticePredict(nodes.firstElement());
            for(StackNode tree: forest) {
                tree.setParentId();
                for(StackNode node: tree.collect()) {
                    System.out.printf("%d %s %s %d\n", node.input.id, node.input.word, node.input.tag, node.parentId);
                }
                System.out.println();
            }
            System.out.println();
        }
    }

    public void test(String filename) throws IOException {
        System.out.print("loading model: ");
        mapper.loadDict("model.features");
        model = Model.load(new File("all-examples.model"));
        System.out.println("ok.");
        Node current;
        BufferedReader input = new BufferedReader(new FileReader(filename));
        int num = 0, errors = 0;
        while(null != (current = Node.readNext(input))) {
            Vector<Node> reference = current.collect();
            InputNode start = new InputNode(null);
            InputNode inputCurrent = start;
            for(Node node: reference) {
                InputArc arc = new InputArc(node.word, node.tag, null);
                inputCurrent.outgoing = new Vector<InputArc>();
                inputCurrent.outgoing.add(arc);
                arc.next = new InputNode(null);
                inputCurrent = arc.next;
            }
            start.number(1);
            Vector<StackNode> forest = latticePredict(start);
            if(forest.size() != 0) {
            forest.firstElement().setParentId();
            Vector<StackNode> hypothesis = forest.firstElement().collect();
            //extractFeatures(reference, oracle(reference));
            //Node predicted = predict(reference, null);//oracle(reference));

            //Vector<Node> hypothesis = predicted.collect();
            for(int i = 0; i < hypothesis.size(); i++) {
                StackNode node = hypothesis.get(i);
                //System.out.printf("%d %s %d %d\n", node.input.id, node.input.word, node.parentId, reference.get(i).parent.id);
                if(node.parentId != reference.get(i).parent.id) errors++;
                num++;
            }
            } else {
                System.out.println("\nWARNING: empty output (size=" + reference.size() + ")");
            }
            System.out.printf("\rUAS: %.2f", 100 - 100.0 * errors / num);
        }
        System.out.printf("\rUAS: %.2f\n", 100 - 100.0 * errors / num);
    }

    public static void main(String args[]) {
        try {
            LatticeParser parser = new LatticeParser();
            //parser.train(args[0]);
            //parser.test(args[1]);
            parser.testLattice(args[0]);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}