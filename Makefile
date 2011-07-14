CP=trove.jar:liblinear-1.7-with-deps.jar:.
lattice-parser.jar: LatticeParser.class
	mkdir -p tmp && cd tmp && cp ../*.class ../*.java . && jar xf ../trove.jar && jar xf ../liblinear-1.7-with-deps.jar && jar cmf ../manifest.txt ../lattice-parser.jar gnu liblinear org *.class *.java && cd .. && rm -rf tmp
LatticeParser.class: $(wildcard *.java)
	javac -g -cp $(CP) *.java
