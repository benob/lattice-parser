/*This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; version 3 of the License.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.
*/

import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.nio.charset.*;
import java.util.*;
import gnu.trove.*;
//switched to mmap which is not faster

class BinaryModel {
    // only works for correct model files, and dense labels!
    static final int MAGIC = 0xa7dcb043;
    static final int VERSION = 2;
    static final int LABEL_SECTION = 0;
    static final int WEIGHT_SECTION = 1;
    static final int MAPPING_SECTION = 2;

    static final int LABEL_ENCODING = 0;

    static final int WEIGHT_TYPE_DOUBLE = 0;
    static final int WEIGHT_TYPE_FLOAT = 1;
    static final int WEIGHT_TYPE_INDEXED = 2; // not implemented

    static final int LABEL_TYPE_INT = 0;
    static final int LABEL_TYPE_SHORT = 1;
    static final int LABEL_TYPE_BYTE = 2;

    static final int WEIGHT_ENCODING_SPARSE = 0;
    static final int WEIGHT_ENCODING_DENSE = 1; // not implemented

    static final int MAPPING_TYPE_HASH = 0;
    static final int MAPPING_TYPE_TRIE = 1;

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
                    System.err.println("warning: too many weights");
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
        output.writeInt(3); // num sections: labels, mapping table, weights

        int labelSectionLocation = (int) output.getFilePointer();
        output.writeInt(LABEL_SECTION);
        output.writeInt(0); // placeholder for next section location
        output.writeInt(LABEL_ENCODING); // simple list of strings

        output.writeInt(features.numLabels()); // warning: this is different from the number of classes
        for(int i = 0; i < features.numLabels(); i++) {
            output.writeUTF(features.labelText(i));
        }

        int weightSectionLocation = (int) output.getFilePointer();
        output.writeInt(WEIGHT_SECTION);
        output.writeInt(0);
        int labelType = LABEL_TYPE_INT;
        int weightType = WEIGHT_TYPE_FLOAT;
        if(numClasses < 128) labelType = LABEL_TYPE_BYTE;
        else if(numClasses < 32768) labelType = LABEL_TYPE_SHORT;
        output.writeInt(labelType);
        output.writeInt(weightType);
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
                if(labelType == LABEL_TYPE_BYTE) output.writeByte(numNonNull);
                else if(labelType == LABEL_TYPE_SHORT) output.writeShort(numNonNull);
                else output.writeInt(numNonNull);
                for(int j = 0; j < numClasses; j++) {
                    if(Math.abs(weights[id][j]) > 1e-6) {
                        if(labelType == LABEL_TYPE_BYTE) output.writeByte(j);
                        else if(labelType == LABEL_TYPE_SHORT) output.writeShort(j);
                        else if(labelType == LABEL_TYPE_INT) output.writeInt(j);
                        if(weightType == WEIGHT_TYPE_FLOAT) output.writeFloat((float) weights[id][j]);
                        else if(weightType == WEIGHT_TYPE_DOUBLE) output.writeDouble(weights[id][j]);
                    }
                }
            }
        }

        int tableSectionLocation = (int) output.getFilePointer();
        output.writeInt(MAPPING_SECTION);
        output.writeInt(-1); // last section

        int mappingType = MAPPING_TYPE_TRIE;
        numFeatures = weightLocation.size();
        output.writeInt(numFeatures);
        output.writeInt(numClasses);
        output.writeInt(mappingType);
        if(mappingType == MAPPING_TYPE_HASH) {
            int lookupSize = numFeatures * 3;
            int startOfKeys = 4 * (lookupSize + 5); // section-type, next-section (x2) + num-features + lookup-size + 4 * lookup-size
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
        } else if(mappingType == MAPPING_TYPE_TRIE) {
            Trie trie = new Trie();
            iterator = weightLocation.iterator();
            for(int i = 0; i < numFeatures; i++) {
                iterator.advance();
                trie.insert(iterator.key(), iterator.value());
            }
            trie.writeToDisk(output);
        } else {
            System.err.println("ERROR: unsupported mapping " + mappingType);
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
    public int modelVersion = 0;
    int labelType;
    int labelSize[] = new int[]{4,2,1};
    int weightType;
    int weightSize[] = new int[]{8,4};
    int mappingType;
    int weightEncoding;

    void loadModel(String filename) throws IOException {
        File file = new File(filename);
        model = new FileInputStream(file).getChannel().map(FileChannel.MapMode.READ_ONLY, 0, file.length());
        int magic = model.getInt(0);
        if(magic != MAGIC) {
            System.err.printf("error: bad magic 0x%x != 0x%x\n", magic, MAGIC);
            throw new IOException();
        }
        modelVersion = model.getInt(4);
        if(modelVersion != 2) {
            System.err.printf("error: unsupported version %d\n", modelVersion);
            throw new IOException();
        }
        int numSections = model.getInt(8);
        int pointer = 12;
        for(int i = 0; i < numSections; i++) {
            int sectionType = model.getInt(pointer);
            if(sectionType == LABEL_SECTION) labelSectionLocation = pointer;
            else if(sectionType == WEIGHT_SECTION) weightSectionLocation = pointer;
            else if(sectionType == MAPPING_SECTION) tableSectionLocation = pointer;
            pointer = model.getInt(pointer + 4);
        }

        numLabels = model.getInt(labelSectionLocation + 12);
        numFeatures = model.getInt(tableSectionLocation + 8);
        numClasses = model.getInt(tableSectionLocation + 12);

        labelType = model.getInt(weightSectionLocation + 8);
        weightType = model.getInt(weightSectionLocation + 12);
        weightEncoding = model.getInt(weightSectionLocation + 16);
        mappingType = model.getInt(tableSectionLocation + 16);

        labels = new Vector<String>();
        pointer = labelSectionLocation + 16;
        while(labels.size() < numLabels) {
            short length = model.getShort(pointer);
            byte buffer[] = new byte[length];
            for(int j = 0; j < length; j++) {
                buffer[j] = model.get(j + pointer + 2);
            }
            labels.add(new String(buffer, utf8));
            pointer += length + 2;
        }
        if(mappingType == MAPPING_TYPE_HASH) lookupSize = model.getInt(tableSectionLocation + 20);
        cache = new String[100000];
        weightCache = new double[100000][numClasses];
        //System.err.printf("classes: %d, features: %d, lookup: %d, length:%d, ndeps:%d\n", numClasses, numFeatures, lookupSize, file.length(), labels.size());
    }

    String cache[];
    double weightCache[][];

    void readWeightVector(int weightLocation, double[] weights) {
        int numWeights = 0;
        if(labelType == LABEL_TYPE_BYTE) numWeights = model.get(weightLocation);
        else if(labelType == LABEL_TYPE_SHORT) numWeights = model.getShort(weightLocation);
        else if(labelType == LABEL_TYPE_INT) numWeights = model.getInt(weightLocation);
        //System.out.println("num-weights: " + numWeights + " / " + labelType + " " + weightType);
        for(int i = 0; i < numWeights; i++) {
            int id = 0;
            if(labelType == LABEL_TYPE_BYTE) id = model.get(weightLocation + labelSize[labelType] + i * (labelSize[labelType] + weightSize[weightType]));
            else if(labelType == LABEL_TYPE_SHORT) id =model.getShort(weightLocation + labelSize[labelType] + i * (labelSize[labelType] + weightSize[weightType]));
            else if(labelType == LABEL_TYPE_INT) id =model.getInt(weightLocation + labelSize[labelType] + i * (labelSize[labelType] + weightSize[weightType]));
            //System.out.println("id: " + id);
            if(weightType == WEIGHT_TYPE_FLOAT) weights[id] = model.getFloat(weightLocation + labelSize[labelType] + i * (labelSize[labelType] + weightSize[weightType]) + labelSize[labelType]);
            else if(weightType == WEIGHT_TYPE_DOUBLE) weights[id] = model.getDouble(weightLocation + labelSize[labelType] + i * (labelSize[labelType] + weightSize[weightType]) + labelSize[labelType]);
        }
    }

    public void getWeightsHashTable(String feature, double[] weights) throws IOException {
        int hash = Math.abs(feature.hashCode());
        int cacheCode = hash % cache.length;
        if(feature.equals(cache[cacheCode])) {
            //for(int i = 0; i < numClasses; i++) weights[i] = weightCache[cacheCode][i];
            System.arraycopy(weightCache[cacheCode], 0, weights, 0, numClasses);
        }
        for(;; hash++) {
            int where = tableSectionLocation + 28 + 4 * (hash % lookupSize);
            //System.out.println(feature + " " + where);
            int keyLocation = model.getInt(where);
            //System.out.println(keyLocation);
            if(keyLocation == 0) { // not found
                Arrays.fill(weights, 0);
                break;
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
                if(weightLocation != -1) {
                    readWeightVector(weightLocation, weights);
                } else { // should not happend
                    Arrays.fill(weights, 0);
                }
                break;
            }
        } 
        System.arraycopy(weights, 0, weightCache[cacheCode], 0, numClasses);
        cache[cacheCode] = feature;
    }

    public int findInTrie(byte[] key, int index, int nodeLocation) {
        int matchLength = model.get(nodeLocation + 4);
        byte match[] = new byte[matchLength];
        for(int i = 0; i < matchLength; i++) {
            match[i] = model.get(nodeLocation + 5 + i);
        }
        int where = 0;
        while(where < match.length && where + index < key.length && match[where] == key[where + index]) {
            where++;
        }
        //System.out.println("" + new String(key).substring(index) + " [" + new String(match) + "] " + where);
        if(where == match.length) {
            if(where + index == key.length) {
                int weightLocation = model.getInt(nodeLocation);
                //System.out.println("weight=" + weightLocation);
                if(weightLocation != -1) {
                    return weightLocation;
                }
                return -2;
            }
            int numChildren = model.get(nodeLocation + 5 + matchLength);
            //System.out.println("num-children=" + numChildren);
            for(int i = 0; i < numChildren; i++) {
                int childLocation = model.getInt(nodeLocation + 6 + matchLength + i * 4);
                //System.out.println(numChildren + " child=" + childLocation);
                int result = findInTrie(key, index + where, childLocation);
                if(result != -1) return result;
            }
            //System.out.println("not-found");
        }
        return -1;
    }

    public void getWeightsTrie(String feature, double[] weights) throws IOException, UnsupportedEncodingException {
        int hash = Math.abs(feature.hashCode());
        int cacheCode = hash % cache.length;
        if(feature.equals(cache[cacheCode])) {
            //for(int i = 0; i < numClasses; i++) weights[i] = weightCache[cacheCode][i];
            System.arraycopy(weightCache[cacheCode], 0, weights, 0, numClasses);
            return;
        }
        int rootLocation = tableSectionLocation + 20; // root node
        byte key[] = feature.getBytes("UTF-8");
        int weightLocation = findInTrie(key, 0, rootLocation);
        if(weightLocation >= 0) {
            readWeightVector(weightLocation, weights);
        } else {
            Arrays.fill(weights, 0);
        }
        System.arraycopy(weights, 0, weightCache[cacheCode], 0, numClasses);
        cache[cacheCode] = feature;
    }

    public void predict(Vector<String> features, double[] scores) throws IOException {
        double weights[] = new double[numClasses];
        Arrays.fill(scores, 0);
        for(String feature: features) {
            if(mappingType == MAPPING_TYPE_HASH) getWeightsHashTable(feature, weights);
            else if(mappingType == MAPPING_TYPE_TRIE) getWeightsTrie(feature, weights);
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
                Vector<String> features = new Vector<String>();
                while(null != (line = input.readLine())) {
                        String tokens[] = line.trim().split(" ");
                        features.clear();
                        features.add(tokens[0]);
                        double scores[] = new double[model.numClasses];
                        model.predict(features, scores);
                        //model.getWeights("__notfound__" + tokens[0], scores);
                        System.out.print(tokens[0]);
                        for(int i = 0; i < scores.length; i++) {
                            System.out.print(" " + scores[i]);
                        }
                        System.out.println();
                        num++;
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
