CP=trove.jar:liblinear-1.7-with-deps.jar:.
IcsiboostSegmenter.class: $(wildcard *.java)
	javac -g -cp $(CP) *.java
