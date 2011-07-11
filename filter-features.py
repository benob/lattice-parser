#!/usr/bin/python
import sys
if len(sys.argv) != 3:
    sys.stderr.write('usage: %s <cutoff> <input>')

cutoff = int(sys.argv[1])
count={}
examples = open(sys.argv[2])

for line in examples:
    tokens = line.strip().split()
    for feature, value in [x.split(":") for x in tokens[1:]]:
        if feature not in count:
            count[feature] = 1
        else:
            count[feature] += 1
examples.seek(0)
for line in examples:
    tokens = line.strip().split()
    output = list(tokens[0])
    for feature, value in [x.split(":") for x in tokens[1:]]:
        if count[feature] >= cutoff:
            output.append("%s:%s" % (feature, value))
    if len(output) > 1:
        sys.stdout.write(" ".join(output) + "\n")
