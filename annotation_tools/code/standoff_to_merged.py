#!/usr/bin/env python3

from __future__ import print_function

import sys

# Read annotations, removing redundant cases along the way
annotations = {}
for line in sys.stdin:
  if 'Unknown' in line:
    continue
  fields = line.strip().split()
  post = int(fields[0])
  span = (int(fields[2]), int(fields[3]))
  flags = fields[4:]
  if post not in annotations:
    annotations[post] = set()
  annotations[post].add(span)

if len(annotations) == 0:
  print("Usgae:\n{} < standoff_annotations".format(sys.argv[0]))
  sys.exit(0)

# Merge
for post in annotations:
  spans = annotations[post]
  for spanA in spans:
    use = True
    for spanB in spans:
      if spanA[0] <= spanB[0] and spanB[1] <= spanA[1] and spanA != spanB:
        use = False
        break
    if use:
      print("{} merged {} {}".format(post, *spanA))
