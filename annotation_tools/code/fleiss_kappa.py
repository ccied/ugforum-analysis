#!/usr/bin/env python3

from __future__ import print_function

import sys, glob

DESCRIPTION = '''Usage:

    {} <raw_file_dir> <low_annotation> <high_annotation> < <standoff_annotations>

Calculate Fleiss' Kappa for annotated data. low_annotation and high_annotation
refer to how many people were annotating files, for example it may be that all
K annotators labeled some, and only pairs of annotators labeled others. In that
case the values would be 2 and K.

The data redirected in (< <standoff_annotations>) is assumed to contain only
lines with annotations, no other data.
'''.format(sys.argv[0])

if len(sys.argv) < 2:
  print(DESCRIPTION))
  sys.exit(1)

raw_template = sys.argv[1] + "/*/{}"  # Requires tweaking depending on dataset
low_annotation = int(sys.argv[2])
high_annotation = int(sys.argv[3])

posts = {}
for line in sys.stdin:
    fields = line.strip().split()
    post = fields[0]
    user = fields[1]
    span = (fields[2], fields[4])
    # Get the number of tokens in the raw post (all unlabeled tokens are
    # considered labeled not-product)
    if post not in posts:
      files = glob.glob(raw_template.format(post))
      length = len(open(files[0]).read().split())
      posts[post] = ({}, length)
    # Record this annotation
    if user not in posts[post][0]:
      posts[post][0][user] = set()
    posts[post][0][user].add(span)

# Calculate the intermediate quantities needed
all_counts = {}
for post in posts:
  label_set, length = posts[post]

  # Work out which scenario this falls into
  nusers = len(label_set)
  if nusers <= low_annotation:
    nusers = low_annotation
  else:
    nusers = high_annotation
  if nusers not in all_counts:
    all_counts[nusers] = {
      "sum n marked": 0,
      "sum n blank": 0,
      "sum n^2": 0,
      "N": 0,
      "n": nusers,
    }
  counts = all_counts[nusers]

  # Update the count of total tokens considered for annotation
  counts["N"] += length

  # Update for base rate of the two labels
  counts["sum n blank"] += length * (nusers - len(label_set))
  for user in label_set:
    labels = label_set[user]
    counts["sum n marked"] += len(labels)
    counts["sum n blank"] += (length - len(labels))

  # Determine counts for each label, and use it to calculate agreement on words
  # annotated by someone
  label_counts = {}
  for user in label_set:
    labels = label_set[user]
    for span in labels:
      if span not in label_counts:
        label_counts[span] = 0
      label_counts[span] += 1
  for label in label_counts:
    num = label_counts[label]
    counts["sum n^2"] += num * num
    counts["sum n^2"] += (nusers - num) * (nusers - num)
  # Perfect agreement on the rest
  counts["sum n^2"] += (length - len(label_counts)) * (nusers * nusers)
  
# Calculate final quantities and produce output
for nusers in all_counts:
  counts = all_counts[nusers]
  N = counts["N"]
  n = counts["n"]
  print("users", nusers, "N", N, "n", n)
  if nusers > 1:
    p_marked = counts["sum n marked"] / (N * n)
    p_blank = counts["sum n blank"] / (N * n)
    print("p_marked", p_marked, "p_blank", p_blank)

    P_e = (p_marked * p_marked) + (p_blank * p_blank)
    P_bar = (counts["sum n^2"] - N * n) / (N * n * (n - 1))
    print("P_e", P_e, "P_bar", P_bar)

    kappa = (P_bar - P_e) / (1 - P_e)
    print("kappa", kappa)

