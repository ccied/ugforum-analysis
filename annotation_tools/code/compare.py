#!/usr/bin/env python3

from __future__ import print_function

import sys

DESCRIPTION = '''Usage:
        
    {} <filename> <filename> ...

Expects filenames to be of the format:

    <user>/.../<unique_name>

Where the '...' could have multiple levels, and the 'unique_name' is common
across annotators. For example, we could have:

    annotator0/foo/bar/file8843.tok
    annotator1/baz/file8843.tok

And in this case these two files would be compared and differences printed.
'''.format(sys.argv[0])

if len(sys.argv) < 2:
    print(DESCRIPTION)
    sys.exit(0)

# Read in all of the data, only retaining files that contain at least one
# annotation.
annotations = {}
for filename in sys.argv[1:]:
    name = filename.split('/')[-1]
    user = filename.split('/')[0]
    if name not in annotations:
        annotations[name] = []
    data = open(filename).read()
    if '{' in data or '[' in data:
        annotations[name].append((data, user))

# Print comparison
print("{} files with annotations".format(len(annotations)))
total_printed = 0
for filename in annotations:
    if len(annotations[filename]) > 1:
        lines = []
        for text, user in annotations[filename]:
            text = [part.strip() for part in text.split('\n')]
            if len(lines) == 0:
                lines = [{line: 1} for line in text]
            else:
                for i in range(len(text)):
                    if i < len(lines):
                        if text[i] in lines[i]:
                            lines[i][text[i]] += 1
                        else:
                            lines[i][text[i]] = 1
                    else:
                        lines.append({text[i]: 1})
        printed = False
        for line in lines:
            if len(line) > 1:
                for ann in line:
                    if len(ann.strip().split()) > 1:
                        print('{} {}   {}'.format(line[ann], filename, ann))
                        printed = True
        if printed:
            total_printed += 1
            print(' ')

print("{} files had disagreements".format(total_printed))

