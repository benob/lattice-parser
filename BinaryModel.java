import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.nio.charset.*;
import java.util.*;
import gnu.trove.*;
//switched to mmap which is not faster

class BinaryModel {
    // only works for correct model files, and dense labels!
    static int MAGIC = 0xa7dcb043;
    static int VERSION = 0;
    static int LABEL_SECTION = 0;
    static int WEIGHT_SECTION = 1;
    static int TABLE_SECTION = 2;
    static int WEIGHT_TYPE_DOUBLE = 0;
    static int LABEL_TYPE_INT = 0;
    static int WEIGHT_ENCODING_SPARSE = 0;

    static public void convert(String featureMapper, String libLinearModel, String outputFile) throws IOException {
        Features features = new Features();
        features.loadDict(featureMapper);
        double weights[][] = null;
        BufferedReader input = new BufferedReader(new FileReader(libLinearModel));
        String line;
        boolean inWeightVector = false;
        int mapping[] = null;
        int numClasses = 0;
        int mappedNumLabels = 0;
        int numFeatures = 0;
        int featureId = 0;
        while(null != (line = input.readLine())) {
            if(inWeightVector) {
                String tokens[] = line.split(" ");
                if(featureId < numFeatures) {
                    for(int i = 0; i < numClasses; i++) {
                        weights[featureId][mapping[i]] = Double.parseDouble(tokens[i]);
                    }
                } else {
                    System.out.println("warning: too many weights");
                }
                featureId++;
            } else if(line.startsWith("nr_class ")) {
                String tokens[] = line.split(" ");
                numClasses = Integer.parseInt(tokens[1]);
                mapping = new int[numClasses];
            } else if(line.startsWith("label ")) {
                String tokens[] = line.split(" ");
                for(int i = 1; i < tokens.length; i++) {
                    mapping[i - 1] = Integer.parseInt(tokens[i]) - 1;
                    if(mapping[i - 1] + 1 > mappedNumLabels) mappedNumLabels = mapping[i - 1] + 1;
                }
            } else if(line.startsWith("nr_feature ")) {
                String tokens[] = line.split(" ");
                numFeatures = Integer.parseInt(tokens[1]) + 1;
                weights = new double[numFeatures][mappedNumLabels];
            } else if("w".equals(line)) {
                inWeightVector = true;
            }
        }
        numClasses = mappedNumLabels;

        RandomAccessFile output = new RandomAccessFile(outputFile, "rw");
        output.writeInt(MAGIC);
        output.writeInt(VERSION);
        output.writeInt(3); // num sections: labels, hash-table, weights

        int labelSectionLocation = (int) output.getFilePointer();
        output.writeInt(LABEL_SECTION);
        output.writeInt(0); // placeholder for next section location

        output.writeInt(features.numLabels()); // warning: this is different from the number of classes
        for(int i = 0; i < features.numLabels(); i++) {
            output.writeUTF(features.labelText(i));
        }

        int weightSectionLocation = (int) output.getFilePointer();
        output.writeInt(WEIGHT_SECTION);
        output.writeInt(0);
        output.writeInt(LABEL_TYPE_INT);
        output.writeInt(WEIGHT_TYPE_DOUBLE);
        output.writeInt(WEIGHT_ENCODING_SPARSE);

        TObjectIntHashMap<String> weightLocation = new TObjectIntHashMap<String>();
        TObjectIntIterator<String> iterator = features.featureDict.iterator();
        for (int i = 0; i < numFeatures; i++) {
            iterator.advance();
            String featureText = iterator.key();
            int id = iterator.value();
            int numNonNull = 0;
            for(int j = 0; j < numClasses; j++) {
                if(Math.abs(weights[id][j]) > 1e-6) numNonNull++;
            }
            if(numNonNull > 0) {
                weightLocation.put(featureText, (int) output.getFilePointer());
                output.writeInt(numNonNull);
                for(int j = 0; j < numClasses; j++) {
                    if(Math.abs(weights[id][j]) > 1e-6) {
                        output.writeInt(j);
                        output.writeDouble(weights[id][j]);
                    }
                }
            }
        }

        int tableSectionLocation = (int) output.getFilePointer();
        output.writeInt(TABLE_SECTION);
        output.writeInt(-1); // last section

        numFeatures = weightLocation.size();
        int lookupSize = numFeatures * 3;
        int startOfKeys = 4 * (lookupSize + 5); // section-type, next-section (x2) + num-features + lookup-size + 4 * lookup-size
        output.writeInt(numFeatures);
        output.writeInt(numClasses);
        output.writeInt(lookupSize);
        output.writeInt(startOfKeys);

        int lookupStart = (int) output.getFilePointer();
        for(int i = 0; i < lookupSize; i++) output.writeInt(0); // placeholder for hash table

        int locations[] = new int[lookupSize];
        Arrays.fill(locations, 0);
        iterator = weightLocation.iterator();
        for (int i = 0; i < numFeatures; i++) {
            iterator.advance();
            String featureText = iterator.key();
            int location = iterator.value();
            int hash = Math.abs(featureText.hashCode());
            while(locations[hash % lookupSize] != 0) hash++;
            locations[hash % lookupSize] = (int) output.getFilePointer();
            output.writeUTF(featureText);
            output.writeInt(location);
        }
        output.seek(lookupStart);
        for(int i = 0; i < lookupSize; i++) {
            output.writeInt(locations[i]);
        }

        output.seek(labelSectionLocation + 4);
        output.writeInt(weightSectionLocation);
        output.seek(weightSectionLocation + 4);
        output.writeInt(tableSectionLocation);

        output.close();
    }

    public int numLabels = 0;
    public int numFeatures = 0;
    public int numClasses = 0;
    public Vector<String> labels;
    int lookupSize = 0;
    int labelSectionLocation = -1;
    int weightSectionLocation = -1;
    int tableSectionLocation = -1;
    MappedByteBuffer model;
    Charset utf8 = Charset.forName("utf-8");

    void loadModel(String filename) throws IOException {
        File file = new File(filename);
        model = new FileInputStream(file).getChannel().map(FileChannel.MapMode.READ_ONLY, 0, file.length());
        int magic = model.getInt(0);
        if(magic != MAGIC) {
            System.err.printf("error: bad magic 0x%x != 0x%x\n", magic, MAGIC);
            throw new IOException();
        }
        int version = model.getInt(4);
        if(version != VERSION) {
            System.err.printf("error: unsupported version %d != %d\n", version, VERSION);
            throw new IOException();
        }
        int numSections = model.getInt(8);
        int pointer = 12;
        for(int i = 0; i < numSections; i++) {
            int sectionType = model.getInt(pointer);
            if(sectionType == LABEL_SECTION) labelSectionLocation = pointer;
            else if(sectionType == WEIGHT_SECTION) weightSectionLocation = pointer;
            else if(sectionType == TABLE_SECTION) tableSectionLocation = pointer;
            pointer = model.getInt(pointer + 4);
        }

        numLabels = model.getInt(labelSectionLocation + 8);
        numFeatures = model.getInt(tableSectionLocation + 8);
        numClasses = model.getInt(tableSectionLocation + 12);

        int labelType = model.getInt(weightSectionLocation + 8);
        if(labelType != LABEL_TYPE_INT) {
            System.err.printf("error: unsupported label type %d != %d\n", labelType, LABEL_TYPE_INT);
            throw new IOException();
        }
        int weightType = model.getInt(weightSectionLocation + 12);
        if(weightType != WEIGHT_TYPE_DOUBLE) {
            System.err.printf("error: unsupported weight type %d != %d\n", weightType, WEIGHT_TYPE_DOUBLE);
            throw new IOException();
        }
        int encoding = model.getInt(weightSectionLocation + 16);
        if(encoding != WEIGHT_ENCODING_SPARSE) {
            System.err.printf("error: unsupported weight vector encoding %d != %d\n", encoding, WEIGHT_ENCODING_SPARSE);
            throw new IOException();
        }

        labels = new Vector<String>();
        pointer = labelSectionLocation + 12;
        while(labels.size() < numLabels) {
            short length = model.getShort(pointer);
            byte buffer[] = new byte[length];
            for(int j = 0; j < length; j++) {
                buffer[j] = model.get(j + pointer + 2);
            }
            labels.add(new String(buffer, utf8));
            pointer += length + 2;
        }
        lookupSize = model.getInt(tableSectionLocation + 16);
        cache = new String[100000];
        weightCache = new double[100000][numClasses];
        //System.err.printf("classes: %d, features: %d, lookup: %d, length:%d, ndeps:%d\n", numClasses, numFeatures, lookupSize, file.length(), labels.size());
    }

    String cache[];
    double weightCache[][];

    public void getWeights(String feature, double[] weights) throws IOException {
        Arrays.fill(weights, 0);
        int hash = Math.abs(feature.hashCode());
        int cacheCode = hash % cache.length;
        if(feature.equals(cache[cacheCode])) {
            //for(int i = 0; i < numClasses; i++) weights[i] = weightCache[cacheCode][i];
            System.arraycopy(weightCache[cacheCode], 0, weights, 0, numClasses);
        }
        for(;; hash++) {
            int where = tableSectionLocation + 24 + 4 * (hash % lookupSize);
            //System.out.println(feature + " " + where);
            int keyLocation = model.getInt(where);
            //System.out.println(keyLocation);
            if(keyLocation == 0) { // not found
                return;
            }
            int length = model.getShort(keyLocation);
            //System.out.println(length);
            byte buffer[] = new byte[length];
            for(int i = 0; i < buffer.length; i++) {
                buffer[i] = model.get(keyLocation + 2 + i);
                //byte b[] = new byte[1]; b[0] = buffer[i];
                //System.out.println(buffer[i] + " " + new String(b));
            }
            String key = new String(buffer, utf8); //model.readUTF();
            //System.out.println(key);
            if(key.equals(feature)) {
                int weightLocation = model.getInt(keyLocation + 2 + length);
                int numWeights = model.getInt(weightLocation);
                for(int i = 0; i < numWeights; i++) {
                    int id = model.getInt(weightLocation + 4 + i * 12);
                    weights[id] = model.getDouble(weightLocation + 4 + i * 12 + 4);
                    weightCache[cacheCode][id] = weights[id];
                }
                //System.out.printf("%s %d : %f %f %f\n", feature, weightId, weights[0], weights[1], weights[2]);
                cache[cacheCode] = feature;
            }
        } 
    }
    public void predict(Vector<String> features, double[] scores) throws IOException {
        double weights[] = new double[numClasses];
        Arrays.fill(scores, 0);
        for(String feature: features) {
            getWeights(feature, weights);
            for(int i = 0; i < numClasses; i++) {
                scores[i] += weights[i];
            }
        }
    }

    static public void main(String args[]) {
        try {
            if(args.length == 3) {
                convert(args[0], args[1], args[2]);
            } else if(args.length == 1) {
                BinaryModel model = new BinaryModel();
                model.loadModel(args[0]);
                String line;
                BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
                int num=1;
                boolean inLabels = true;
                while(null != (line = input.readLine())) {
                    if(line.trim().equals("")) {
                        inLabels = false;
                    } else if(!inLabels) {
                        String tokens[] = line.trim().split(" ");
                        if(tokens.length != 2) continue;
                        double scores[] = new double[model.numClasses];
                        model.getWeights(tokens[0], scores);
                        //model.getWeights("__notfound__" + tokens[0], scores);
                        System.out.print(tokens[0]);
                        for(int i = 0; i < scores.length; i++) {
                            System.out.print(" " + scores[i]);
                        }
                        System.out.println();
                        num++;
                    }
                }
            } else {
                StackTraceElement[] stack = Thread.currentThread ().getStackTrace ();
                System.out.printf("convert: java %s <feature-dict> <liblinear-model> <output>\n", stack[stack.length - 1].getClassName());
                System.out.printf("test: echo feature-name | java %s <binary-model>\n", stack[stack.length - 1].getClassName());
                System.exit(1);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
