import java.util.*;
import java.io.*;
import gnu.trove.*;
import liblinear.*;

// implementation of the arc normal parser
class LatticeParser {
    final int UNLABELED = 0;
    final int LABELED = 1;
    int mode = LABELED;
    public static final int SHIFT = 0;
    public static final int LEFT = 1;
    public static final int RIGHT = 2;
    public static int numActions = 3;

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
            if(action == SHIFT) {
                if(start >= input.size()) {
                    System.err.printf("ERROR: impossible action \"SHIFT\", start >= input.size()\n");
                    return null;
                }
                stack.add(new Node(input.get(start)));
                start++;
            } else if((action - 1) % 2 + 1 == LEFT) {
                if(stack.size() < 2) {
                    System.err.printf("ERROR: impossible action \"LEFT\", stack.size() < 2\n");
                    return null;
                }
                Node first = stack.get(stack.size() - 1);
                Node second = stack.get(stack.size() - 2);
                first.setParent(second);
                if(mode == LABELED) label = mapper.labelText((action - 1) / 2);
                first.label = label;
                stack.set(stack.size() - 2, second);
                stack.remove(stack.size() - 1);
            } else if((action - 1) % 2 + 1 == RIGHT) {
                if(stack.size() < 2) {
                    System.err.printf("ERROR: impossible action \"RIGHT\", stack.size() < 2\n");
                    return null;
                }
                Node first = stack.get(stack.size() - 1);
                Node second = stack.get(stack.size() - 2);
                second.setParent(first);
                if(mode == LABELED) label = mapper.labelText((action - 1) / 2);
                second.label = label;
                stack.set(stack.size() - 2, first);
                stack.remove(stack.size() - 1);
            }
        }
        Node node = stack.get(0);
        node.setParent(root);
        return root;
    }

    public Vector<TreeNode> latticePredict(InputNode inputStart) throws IOException {
        THashMap<String, TreeNode> subtrees = new THashMap<String, TreeNode>();
        Vector<TreeNode> output = new Vector<TreeNode>();
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

        double scores[] = new double[model2.numClasses];

        while(openParses.size() > 0) {
            Vector<ParseContext> newOpenParses = new Vector<ParseContext>();
            for(ParseContext context: openParses) {
                //System.out.printf("(%s %s %s)\n", context.input[0] != null ? context.input[0].word : "null", context.input[1] != null ? context.input[1].word : "null", context.input[2] != null ? context.input[2].word : "null");
                Vector<String> features = Features.getLatticeFeatures(context);
                //for(String feature: features) { System.out.printf("%s ", feature); } System.out.println();
                //Linear.predictValues(model, mapper.mapForLibLinear(features), scores);
                model2.predict(features, scores);
                boolean available[] = new boolean[3];
                Arrays.fill(available, true);
                if(context.input[0] == null) available[SHIFT] = false;
                if(context.stack == null || context.stack.next == null) {
                    available[LEFT] = false;
                    available[RIGHT] = false;
                }
                int action = -1; // recompute argmax with constraints
                //System.out.printf("available=[%s %s %s]\n", available[0], available[1], available[2]);
                for(int candidateAction = 0; candidateAction < model2.numClasses; candidateAction ++) {
                    if(available[(candidateAction - 1) % 2 + 1] && (action == -1 || scores[action] < scores[candidateAction])) 
                        action = candidateAction;
                }
                //System.out.printf("label=%d action=%d dep=%d\n", action, (action - 1) % 2 + 1, (action - 1) / 2);
                //if(action == 1) action = 2; // why why why
                //else if(action == 2) action = 1;
                if(action == -1) {
                    output.add(context.stack.tree); // generated a complete tree
                } else if(action == SHIFT) {
                    //System.out.println("shift " + context.input[0].word);
                    TreeNode top = new TreeNode(context.input[0]);
                    if(subtrees.containsKey(top.toString())) {
                        top = subtrees.get(top.toString());
                    } else {
                        subtrees.put(top.toString(), top);
                    }
                    context.input[0] = context.input[1];
                    context.input[1] = context.input[2];
                    StackElement stack = new StackElement(context.stack, top);
                    if(context.input[1] != null && context.input[1].next != null && context.input[1].next.outgoing.size() > 0) { // a bit tricky
                        for(InputArc arc: context.input[1].next.outgoing) {
                            context.input[2] = arc;
                            newOpenParses.add(new ParseContext(stack, context.input.clone()));
                        }
                    } else {
                        context.input[2] = null;
                        context.stack = stack;
                        newOpenParses.add(context);
                    }
                } else if((action - 1) % 2 + 1 == LEFT) {
                    //System.out.println("left " + context.stack.next.input.word + " <-(" + model2.labels.get((action - 1) / 2) + ") " + context.stack.input.word);
                    TreeNode top = context.stack.next.tree.addChild(context.stack.tree, model2.labels.get((action - 1) / 2));
                    if(subtrees.containsKey(top.toString())) {
                        top = subtrees.get(top.toString());
                    } else {
                        subtrees.put(top.toString(), top);
                    }
                    StackElement stack = new StackElement(context.stack.next.next, top);
                    context.stack = stack;
                    newOpenParses.add(context);
                } else if((action - 1) % 2 + 1 == RIGHT) {
                    //System.out.println("right " + context.stack.next.input.word + " -> " + context.stack.input.word);
                    TreeNode top = context.stack.tree.addChild(context.stack.next.tree, model2.labels.get((action - 1) / 2));
                    if(subtrees.containsKey(top.toString())) {
                        top = subtrees.get(top.toString());
                    } else {
                        subtrees.put(top.toString(), top);
                    }
                    StackElement stack = new StackElement(context.stack.next.next, top);
                    context.stack = stack;
                    newOpenParses.add(context);
                } else {
                    System.err.println("ERROR: unexpected action " + action);
                }
            }
            openParses = newOpenParses;
        }
        System.err.println("subtrees: " + subtrees.size());
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
                    System.err.printf("ERROR: impossible action \"SHIFT\", start >= input.size()\n");
                    return null;
                }
                System.out.println("shift " + input.get(start).word);
                stack.add(new Node(input.get(start)));
                start++;
            } else if(action == LEFT) {
                if(stack.size() < 2) {
                    System.err.printf("ERROR: impossible action \"LEFT\", stack.size() < 2\n");
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
                    System.err.printf("ERROR: impossible action \"RIGHT\", stack.size() < 2\n");
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

    public void outputForest(Vector<TreeNode> forest, PrintStream output) {
        for(TreeNode node: forest) {
            //System.out.println(node.toString());
        }
        THashMap<String, TreeNode> alreadyOutput = new THashMap<String, TreeNode>();
        Vector<TreeNode> waitingList = new Vector<TreeNode>();
        int nextId = 1;
        for(TreeNode node: forest) {
            node.id = nextId;
            nextId++;
            output.printf("0 %d %d:%s:%s %s\n", node.id, node.input.id, node.input.word, node.input.tag, "ROOT");
            for(int i = 0; i < node.children.length; i++) {
                TreeNode child = node.children[i];
                if(!alreadyOutput.containsKey(child.toString())) {
                    alreadyOutput.put(child.toString(), child);
                    child.id = nextId;
                    nextId++;
                    waitingList.add(child);
                }
                else child = alreadyOutput.get(child.toString());
                output.printf("%d %d %d:%s:%s %s\n", node.id, child.id, child.input.id, child.input.word, child.input.tag, node.labels[i]);
            }
            if(node.children.length == 0) output.println(node.id);
        }
        while(waitingList.size() != 0) {
            Vector<TreeNode> newList = new Vector<TreeNode>();
            for(TreeNode node: waitingList) {
                for(int i = 0; i < node.children.length; i++) {
                    TreeNode child = node.children[i];
                    if(!alreadyOutput.containsKey(child.toString())) {
                        alreadyOutput.put(child.toString(), child);
                        child.id = nextId;
                        nextId++;
                        newList.add(child);
                    }
                    else child = alreadyOutput.get(child.toString());
                    output.printf("%d %d %d:%s:%s %s\n", node.id, child.id, child.input.id, child.input.word, child.input.tag, node.labels[i]);
                }
                if(node.children.length == 0) output.println(node.id);
            }
            waitingList = newList;
        }
        System.err.printf("arcs: %d, contexts: %d, stackelement: %d, treenodes: %d, paths: %d\n", InputArc.numInstances, ParseContext.numInstances, StackElement.numInstances, TreeNode.numInstances, forest.size());
        InputArc.numInstances = 0;
        ParseContext.numInstances = 0;
        TreeNode.numInstances = 0;
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
                    if(mode == UNLABELED) actions.add(LEFT);
                    else if(mode == LABELED) actions.add(LEFT + 2 * mapper.mapLabel(first.label));
                    stack.set(stack.size() - 2, second);
                    stack.remove(stack.size() - 1);
                } else if(second.parent == first && (second.rightMostChild == null || start >= input.size() || second.rightMostChild.id < input.get(start).id)) {
                    if(mode == UNLABELED) actions.add(RIGHT);
                    else if(mode == LABELED) actions.add(RIGHT + 2 * mapper.mapLabel(second.label));
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
        numActions = mapper.numLabels() * 2 + 3;
        return actions;
    }

    public void train(String filename, String modelFileName) throws IOException {
        Node current;
        BufferedReader input = new BufferedReader(new FileReader(filename));
        Vector<Node> sentences = new Vector<Node>();
        numExamples = 0;
        while(null != (current = Node.readNext(input))) {
            /*current.speechify();
            current.renumber();*/
            Vector<Node> nodes = current.collect();
            boolean invalid = false;
            for(Node node: nodes) {
                if(node.label.equals("missinghead")) {
                    invalid = true;
                    break;
                }
            }
            if(invalid) {
                System.err.println("WARNING: skipping sentence with missing heads");
            } else {
                sentences.add(current);
                if(current.size() != 1) 
                    numExamples += current.size() * 2 - 3; // there will be twice as many actions minus 2 shifts at the begining and the root at the end
            }
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
                System.err.printf("ERROR: found a non-projective tree");
                sentence.print(System.err);
                return;
            }
            System.out.printf("\rfeatures: %.2f%%", 100.0 * problem.l / numExamples);
        }
        System.out.printf("\rfeatures: %.2f%%\n", 100.0 * problem.l / numExamples);
        problem.n = mapper.numFeatures();
        PrintWriter featureFile = new PrintWriter(modelFileName + ".examples");
        for(int i = 0; i < problem.l; i++) {
            featureFile.print(problem.y[i]);
            for(int j = 0; j < problem.x[i].length; j++) {
                featureFile.printf(" %d:1", problem.x[i][j].index);//, problem.x[i][j].value);
            }
            featureFile.println();
        }
        featureFile.close();
        model = Linear.train(problem, new Parameter(SolverType.MCSVM_CS, 1, 0.01));
        model.save(new File(modelFileName));
        mapper.saveDict(modelFileName + ".features");
        System.out.println("creating binary model");
        OnDiskModel2.convert(modelFileName + ".features", modelFileName, modelFileName + ".binary");
    }

    OnDiskModel2 model2 = new OnDiskModel2();
    public void testLattice(String modelFilename) throws IOException {
        //System.err.print("loading model: ");
        /*mapper.loadDict("model.features");
        model = Model.load(new File("all-examples.model"));*/
        model2 = new OnDiskModel2();
        model2.loadModel(modelFilename);
        //System.err.println("ok.");
        BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
        String line;
        Vector<InputNode> nodes = new Vector<InputNode>();
        while(null != (line = input.readLine())) {
            line = line.trim();
            if("".equals(line)) {
                nodes.firstElement().number(1);
                Vector<TreeNode> forest = latticePredict(nodes.firstElement());
                outputForest(forest, System.out);
                System.out.println();
                nodes.clear();
            } else {
                String tokens[] = line.split("\\s+");
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
            Vector<TreeNode> forest = latticePredict(nodes.firstElement());
            outputForest(forest, System.out);
            System.out.println();
        }
    }

    public void test(String filename) throws IOException {
        /*System.out.print("loading model: ");
        mapper.loadDict("model.features");
        model = Model.load(new File("all-examples.model"));
        System.out.println("ok.");*/
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
            Vector<TreeNode> forest = latticePredict(start);
            if(forest.size() != 0) {
            //forest.firstElement().setParentId();
            Vector<TreeNode> hypothesis = forest.firstElement().collect();
            //extractFeatures(reference, oracle(reference));
            //Node predicted = predict(reference, null);//oracle(reference));

            //Vector<Node> hypothesis = predicted.collect();
            for(int i = 0; i < hypothesis.size(); i++) {
                TreeNode node = hypothesis.get(i);
                //System.out.printf("%d %s %d %d\n", node.input.id, node.input.word, node.parentId, reference.get(i).parent.id);
                //if(node.parentId != reference.get(i).parent.id) errors++;
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
            //parser.test(args[1]);
            if(args.length == 1) {
                parser.testLattice(args[0]);
            } else if(args.length == 2) {
                parser.train(args[0], args[1]);
            } else {
                System.err.println("train: java LatticeParser projective-trees-conll05 model");
                System.err.println("predict: java LatticeParser model < input");
                System.exit(1);
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
