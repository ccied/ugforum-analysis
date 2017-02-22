#!/usr/bin/env python3

from __future__ import print_function

import sys
import itertools
import random
import subprocess

DESCRIPTION = '''Usage:
    
    {} <comma_separated_usernames> <file_of_files_to_use> [<items>:<group_size>]+

Assigns the files to annotators and copies them into directories. For example, this command:

    {} user0,user1,user2 filenames.txt 30:2 20:3

Would lead to the creation of this set of directories

    todo/
      -- user0/
            -- 0/<20 files>
            -- 1/<20 files>
      -- user1/
            -- 0/<20 files>
            -- 1/<20 files>
      -- user2/
            -- 0/<20 files>
            -- 1/<20 files>

This idea of the subdirectories is to break the data down into convenient
chunks.

Options in the code:
    FILES_PER_SUBDIR - Change the number of files per subdirectory created.
    PRINT_LINE_COUNTS - Change the files to have numbers counting the number of non-blank lines.

'''.format(sys.argv[0], sys.argv[0])

PRINT_LINE_COUNTS = False
FILES_PER_SUBDIR = 20

if len(sys.argv) < 4:
    print(DESCRIPTION)
    sys.exit(0)

# The names of annotators
names = sys.argv[1].split(',')

# The names of files to be assigned
filenames = [line.strip() for line in open(sys.argv[2]).readlines()]

# Create the groups that will files will be assigned to
assignment_groups = []
for config in sys.argv[3:]:
    fields = config.split(":")
    count = int(fields[0])
    size = int(fields[1])
    assignment_groups.append((count, size))

# Assign files
# Note - the division will only be appxoimately equal. Depending on group sizes
# it may not be possible to get a perfectly even assignment.
assignments = {name: [] for name in names}
for count, size in assignment_groups:
    done = 0
    while done < count:
        groups = itertools.combinations(names, size)
        for group in groups:
            if done == count:
                break
            filename = filenames.pop(random.randint(0, len(filenames) - 1))
            for person in group:
                assignments[person].append(filename)
            done += 1

# Make directories and copy the files in.
mkdir = 'mkdir -p todo/{}'
cp_start = "cat"
if PRINT_LINE_COUNTS:
    cp_start = "awk '{ if (NF > 0) a += 1 ; print a, $0}'"
cp_end = " {} > todo/{}/{}"
for name in names:
    print(len(assignments[name]), name)
    # Shuffle so that the order within sub-directories varies across
    # annotators.
    random.shuffle(assignments[name])
    cdir = -1
    for i in range(len(assignments[name])):
        if i % FILES_PER_SUBDIR == 0:
            cdir += 1
            subprocess.call(mkdir.format(name + "/" + str(cdir)), shell=True)
        cmd = cp_start + cp_end.format(assignments[name][i], name + "/" + str(cdir), assignments[name][i].split('/')[-1])
        subprocess.call(cmd, shell=True)

