import java.util.*;
import java.io.*;

class Trie implements Comparable<Trie> {
    byte[] match;
    int payload;
    ArrayList<Trie> children;

    public Trie() {
        match = null;
        payload = 0;
        children = new ArrayList<Trie>();
    }
    public Trie(byte[] key, ArrayList<Trie> children, int payload) {
        this.children = children;
        this.payload = payload;
        match = key;
    }
    public Trie(byte[] key, int payload) {
        children = new ArrayList<Trie>();
        this.payload = payload;
        match = key;
    }

    public int compareTo(Trie other)
    {
        if (other.match[0] < this.match[0]) return 1;
        return -1;
    }


    public String toString() {
        StringBuffer output = new StringBuffer();
        if(match != null) {
            output.append("(").append(match);
        }
        output.append(":");
        output.append(payload);
        for(Trie child: children) {
            output.append(child.toString());
        }
        if(match != null) output.append(")");
        return output.toString();
    }

    public boolean find(String key) throws UnsupportedEncodingException {
        return find(key.getBytes("UTF-8"), 0);
    }

    public boolean find(byte[] key, int index) {
        if(match == null) {
            for(Trie child: children) {
                if(child.find(key, 0) == true) return true;
            }
            return false;
        }
        int where = 0;
        while(where < match.length && where + index < key.length && match[where] == key[where + index]) {
            where++;
        }
        if(where == match.length) {
            if(where + index == key.length) return true;
            for(Trie child: children) {
                if(child.find(key, index + where)) return true;
            }
        }
        return false;
    }

    public boolean insert(String key, int payload) throws UnsupportedEncodingException {
        return insert(key.getBytes("UTF-8"), 0, payload);
    }

    public boolean insert(byte key[], int index, int payload) {
        if(match == null) {
            for(Trie child: children) {
                if(child.insert(key, index, payload)) return true;
            }
            children.add(new Trie(key, payload));
            return true;
        }
        int where = 0;
        while(where < match.length && where + index < key.length && match[where] == key[where + index]) {
            where++;
        }
        if(where == 0) return false;
        if(where == match.length) {
            if(key.length - index > match.length) {
                for(Trie child: children) {
                    if(child.insert(key, index + where, payload)) return true;
                }
                children.add(new Trie(Arrays.copyOfRange(key, index + where, key.length), payload));
            } else {
                if(this.payload != -1) System.err.println("WARNING: duplicate in trie [" + new String(key) + "]");
                this.payload = payload;
                return true; // nothing to do, node already present
            }
        } else {
            ArrayList<Trie> newChildren = new ArrayList<Trie>();
            newChildren.add(new Trie(Arrays.copyOfRange(match, where, match.length), children, this.payload));
            if(key.length - index > where || key.length - index > match.length) newChildren.add(new Trie(Arrays.copyOfRange(key, index + where, key.length), payload));
            children = newChildren;
            this.payload = -1; // this node becomes non-final
            match = Arrays.copyOfRange(match, 0, where);
        }
        return true;
    }

    public int writeToDisk(RandomAccessFile output) throws IOException {
        return writeToDisk(output, new HashMap<String, Integer>());
    }

    public int writeToDisk(RandomAccessFile output, HashMap<String, Integer> alreadyOutput) throws IOException {
        String repr = toString();
        if(alreadyOutput.containsKey(repr)) return alreadyOutput.get(repr);
        int location = (int) output.getFilePointer();
        alreadyOutput.put(repr, location);
        output.writeInt(payload);
        if(match != null) {
            output.writeByte(match.length);
            for(byte item: match) output.writeByte(item);
        } else {
            output.writeByte(0);
        }
        output.writeByte(children.size());
        ArrayList<Integer> childrenLocation = new ArrayList<Integer>();
        Collections.sort(children);
        long beforeChildren = output.getFilePointer();
        for(Trie child: children) {
            output.writeInt(0); // reserve space
        }
        for(Trie child: children) {
            childrenLocation.add(child.writeToDisk(output, alreadyOutput));
        }
        long afterChildren = output.getFilePointer();
        output.seek(beforeChildren);
        for(Integer childLocation: childrenLocation) output.writeInt(childLocation);
        output.seek(afterChildren);
        return location;
    }
}
