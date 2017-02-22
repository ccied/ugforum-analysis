#!/usr/bin/env python3

from __future__ import print_function

import sys
import glob
import string
from standoff_annotations import extract

DESCRIPTION = '''Usage:
        
    {} <raw_file_directory> [-<username>] <annotated_file> <annotated_file> ...

If a username is not specified, it is taken from the filename as followes:

    <username>/.../<unique_name>

Where the '...' could have multiple levels, and the 'unique_name' is common
across annotators. For example, we could have:

    annotator0/foo/bar/file8843.tok

We assume that the filenames in the raw directory are the same as are provided
from annotators. For example, in the case above we would expect the raw
directory to contain a file name 'file8843.tok'.

There are some internal program option:
    REMOVE_INITIAL_DIGITS
        Whether to cut out of annotations the first word in a line if the word
        is actually a number (this can be introduced by the assign.py script)
    MAX_LENGTH
        The alignment can be slow, so if the file is longer than this value, we
        don't do it (ie. this program gives up).
'''.format(sys.argv[0])

REMOVE_INITIAL_DIGITS = False
MAX_LENGTH = 10000

if len(sys.argv) < 3:
    print(DESCRIPTION)
    sys.exit(0)

def starts_with_num(line):
    if len(line) < 1:
        return False
    for char in line[0]:
        if char not in string.digits:
            return False
    return True

# Read the annotated files and store their contents
annotations = {}
for filename in sys.argv[2:]:
  if filename[0] == '-':
    continue
  name = filename.split('/')[-1]
  user = filename.split('/')[0]
  if sys.argv[2][0] == '-':
    user = sys.argv[2][1:]
  if name not in annotations:
    annotations[name] = []
  text = []
  for line in open(filename):
    line = line.strip().split()
    if REMOVE_INITIAL_DIGITS and starts_with_num(line):
      line = line[1:]
    text.append(' '.join(line))
  annotations[name].append(['\n'.join(text), user])

# Read the raw files and work out the alignments
raw_template = sys.argv[1] + "/{}"
for name in annotations:
  files = glob.glob(raw_template.format(name))
  raw = open(files[0]).read()
  if len(raw) < MAX_LENGTH:
    for ann_text, user in annotations[name]:
      print(len(raw), name)
      brackets, labels = extract(raw, ann_text)
      for start, ssym, end, esym in brackets:
        print(name, user, start, ssym, end, esym, ' '.join(sorted(labels)))
    sys.stdout.flush()

