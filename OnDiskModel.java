import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.nio.charset.*;
import java.util.*;
import gnu.trove.*;
//switched to mmap which is not faster

class OnDiskModel {
    // only works for correct model files, and dense labels!
    static public void convert(String featureMapper, String libLinearModel, String outputFile) throws IOException {
        Features features = new Features();
        features.loadDict(featureMapper);
        BufferedReader input = new BufferedReader(new FileReader(libLinearModel));
        RandomAccessFile output = new RandomAccessFile(outputFile, "rw");
        String line;
        boolean inWeightVector = false;
        int mapping[] = null;
        double weights[] = null;
        int numLabels = 0;
        int mappedNumLabels = 0;
        int numFeatures = 0;
        int numWritten = 0;
        while(null != (line = input.readLine())) {
            if(inWeightVector) {
                String tokens[] = line.split(" ");
                for(int i = 0; i < numLabels; i++) {
                    weights[mapping[i]] = Double.parseDouble(tokens[i]);
                }
                for(int i = 0; i < mappedNumLabels; i++) {
                    output.writeDouble(weights[i]);
                }
                numWritten++;
                if(numWritten % 1000 == 0) System.err.printf("\rweights: %d%%", 100 * numWritten / numFeatures);
                if(numWritten == numFeatures) break;
            } else if(line.startsWith("nr_class ")) {
                String tokens[] = line.split(" ");
                numLabels = Integer.parseInt(tokens[1]);
                mapping = new int[numLabels];
            } else if(line.startsWith("label ")) {
                String tokens[] = line.split(" ");
                for(int i = 1; i < tokens.length; i++) {
                    mapping[i - 1] = Integer.parseInt(tokens[i]) - 1;
                    if(mapping[i - 1] + 1 > mappedNumLabels) mappedNumLabels = mapping[i - 1] + 1;
                }
                weights = new double[mappedNumLabels];
                output.writeInt(mappedNumLabels);
            } else if(line.startsWith("nr_feature ")) {
                String tokens[] = line.split(" ");
                numFeatures = Integer.parseInt(tokens[1]);
                output.writeInt(numFeatures);
                output.writeInt(0); // placeholder for start of weights
                for(int i = 0; i < features.numLabels(); i++) {
                    output.writeUTF(features.labelText(i));
                }
                long startOfWeights = output.getFilePointer();
                output.seek(8); // after numFeatures and numLabels
                output.writeInt((int) startOfWeights);
                output.seek(startOfWeights);
            } else if("w".equals(line)) {
                inWeightVector = true;
            }
        }
        System.err.printf("\rweights: %d%%\n", 100 * numWritten / numFeatures);
        int lookupSize = numFeatures * 2;
        long locations[] = new long[lookupSize];
        output.writeInt(lookupSize);
        long startOfLookup = output.getFilePointer();
        numWritten = 0;
        for(int i = 0; i < lookupSize; i++) {
            output.writeLong(0);
            numWritten++;
            if(numWritten % 1000 == 0) System.err.printf("\rpre-filling: %d%%", 100 * numWritten / lookupSize);
        }
        System.err.printf("\rpre-filling: %d%%\n", 100 * numWritten / lookupSize);
        TObjectIntIterator<String> iterator = features.featureDict.iterator();
        numWritten = 0;
        for (int i = numFeatures; i-- > 0;) {
            iterator.advance();
            String key = iterator.key();
            int value = iterator.value();
            int hash = Math.abs(key.hashCode());
            while(locations[hash % lookupSize] != 0) hash++;
            locations[hash % lookupSize] = output.getFilePointer();
            output.writeUTF(key);
            output.writeInt(value);
            numWritten++;
            if(numWritten % 1000 == 0) System.err.printf("\rkeys: %d%%", 100 * numWritten / numFeatures);
        }
        System.err.printf("\rkeys: %d%%\n", 100 * numWritten / numFeatures);
        output.seek(startOfLookup);
        numWritten = 0;
        for(int i = 0; i < lookupSize; i++) {
            output.writeLong(locations[i]);
            numWritten++;
            if(numWritten % 1000 == 0) System.err.printf("\rtable: %d%%", 100 * numWritten / lookupSize);
        }
        System.err.printf("\rtable: %d%%\n", 100 * numWritten / lookupSize);
        output.close();
    }

    public int numLabels = 0;
    public int numFeatures = 0;
    public Vector<String> labels;
    int lookupSize = 0;
    int startOfWeights = 0;
    MappedByteBuffer model;
    void loadModel(String filename) throws IOException {
        File file = new File(filename);
        model = new FileInputStream(file).getChannel().map(FileChannel.MapMode.READ_ONLY, 0, file.length());
        numLabels = model.getInt(0);
        numFeatures = model.getInt(4);
        startOfWeights = model.getInt(8);
        labels = new Vector<String>();
        int i = 12;
        while(i < startOfWeights) {
            short length = model.getShort(i);
            byte buffer[] = new byte[length];
            for(int j = 0; j < length; j++) {
                buffer[j] = model.get(j + i + 2);
            }
            labels.add(new String(buffer, Charset.forName("UTF-8")));
            i += length + 2;
        }
        lookupSize = model.getInt(startOfWeights + 8 * (numLabels * numFeatures));
        cache = new String[100000];
        weightCache = new double[100000][numLabels];
        idCache = new int[100000];
        System.err.printf("labels: %d, features: %d, lookup: %d, length:%d, ndeps:%d, startofweights:%d\n", numLabels, numFeatures, lookupSize, file.length(), labels.size(), startOfWeights);
    }
    String cache[];
    int idCache[];
    double weightCache[][];
    public int getWeights(String feature, double[] weights) throws IOException {
        int hash = Math.abs(feature.hashCode());
        int cacheCode = hash % cache.length;
        if(feature.equals(cache[cacheCode])) {
            for(int i = 0; i < numLabels; i++) weights[i] = weightCache[cacheCode][i];
            return idCache[cacheCode];
        }
        for(;; hash++) {
            int where = startOfWeights + 4 + 8 * numLabels * numFeatures + 8 * (hash % lookupSize);
            //System.out.println(feature + " " + where);
            int keyLocation = (int) model.getLong(where);
            //System.out.println(keyLocation);
            if(keyLocation == 0) {
                return -1;
            }
            int length = model.getShort(keyLocation);
            //System.out.println(length);
            byte buffer[] = new byte[length];
            for(int i = 0; i < buffer.length; i++) {
                buffer[i] = model.get(keyLocation + 2 + i);
                //byte b[] = new byte[1]; b[0] = buffer[i];
                //System.out.println(buffer[i] + " " + new String(b));
            }
            String key = new String(buffer, Charset.forName("UTF-8")); //model.readUTF();
            //System.out.println(key);
            if(key.equals(feature)) {
                int weightId = model.getInt(keyLocation + 2 + length);
                if(weightId < 0) return -2; // this would be alarming
                int weightLocation = startOfWeights + weightId * numLabels * 8;
                for(int i = 0; i < numLabels; i++) {
                    weights[i] = model.getDouble(weightLocation + i * 8);
                    weightCache[cacheCode][i] = weights[i];
                }
                //System.out.printf("%s %d : %f %f %f\n", feature, weightId, weights[0], weights[1], weights[2]);
                cache[cacheCode] = feature;
                idCache[cacheCode] = weightId;
                return idCache[cacheCode];
            }
        } 
    }
    public void predict(Vector<String> features, double[] scores) throws IOException {
        double weights[] = new double[numLabels];
        Arrays.fill(scores, 0);
        for(String feature: features) {
            int result = getWeights(feature, weights);
            if(result >= 0) {
                for(int i = 0; i < numLabels; i++) {
                    scores[i] += weights[i];
                }
            } else {
                //System.err.println("not found[" + feature + "]");
            }
        }
    }

    static public void main(String args[]) {
        try {
            if(args.length == 3) {
                convert(args[0], args[1], args[2]);
            } else if(args.length == 1) {
                OnDiskModel model = new OnDiskModel();
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
                        double scores[] = new double[model.numLabels];
                        int id = model.getWeights(tokens[0], scores);
                        int id2 = model.getWeights("__notfound__" + tokens[0], scores);
                        System.out.print(tokens[0] + " " + id + " " + id2);
                        for(int i = 0; i < scores.length; i++) {
                            System.out.print(" " + scores[i]);
                        }
                        System.out.println();
                        if(Integer.parseInt(tokens[1]) != id) {
                            System.err.printf("ERROR: %s %s != %d\n", tokens[0], tokens[1], id);
                        } else if(id2 != -1) {
                            System.err.printf("ERROR: __notfound__%s = %d\n", tokens[0], id2);
                        }
                        System.err.print("\r" + num);
                        num++;
                    }
                }
                System.err.println("\r" + num);
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
