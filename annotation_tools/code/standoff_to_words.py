#!/usr/bin/env python3

from __future__ import print_function

import sys
import glob
from standoff_annotations import extract, insert

if len(sys.argv) < 2:
  print("Usgae:\n{} <raw_file_directory> [<file_prefix>] < standoff_annotations".format(sys.argv[0]))
  print("If the file prefix is not included, output is printed to stdout")
  sys.exit(0)

# Read annotations
annotations = {}
for line in sys.stdin:
  if 'Unknown' in line:
    continue
  fields = line.strip().split()
  name = fields[0]
  user = fields[1]
  span = (int(fields[2]), int(fields[4]))
  stype = '[]'
  flags = []
  if len(fields) > 4:
    # To handle the newer cases where we also annotate
    # buy/sell by using different symbols
    span = (int(fields[2]), int(fields[4]))
    stype = fields[3] + fields[5]
    flags = fields[6:]
  if name not in annotations:
    annotations[name] = {}
  if user not in annotations[name]:
    annotations[name][user] = ([], flags)
  annotations[name][user][0].append((span, stype))

# Read raw text and print out combined form
output_prefix = '' if len(sys.argv) == 2 else sys.argv[2]
raw_template = sys.argv[1] + "/*/{}"
for name in annotations:
  fname = name
  if not name.endswith(".tok"):
    fname = name + ".tok"
  files = glob.glob(raw_template.format(fname))
  raw = open(files[0]).read()
  for user in annotations[name]:
    ans = ''
    ann_spans, ann_flags = annotations[name][user]
    starts = {span[0][0]: span[1][0] for span in ann_spans}
    ends = {span[0][1]: span[1][1] for span in ann_spans}
    inside = False
    for i in range(len(raw)):
      if i in ends:
        inside = False
        if len(ans) > 0:
          print(ans.strip())
        ans = ''
      if i in starts:
        inside = True
      if inside:
        ans += raw[i]

