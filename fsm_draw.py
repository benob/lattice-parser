import pygraphviz, re, sys, os

graph = pygraphviz.AGraph(strict=False,directed=True,rankdir='LR', rank='min')
for line in sys.stdin:
    tokens = line.strip().split()
    if len(tokens) > 2:
        graph.add_edge(tokens[0], tokens[1], label=tokens[2], fontsize=32)
    elif len(tokens) == 1:
        graph.add_node(tokens[0], shape='doublecircle')
        graph.get_node(tokens[0]).attr['shape'] = 'doublecircle'

graph.layout('dot')
graph.draw('file.pdf')
os.system('evince file.pdf')
