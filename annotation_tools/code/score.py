#!/usr/bin/env python3

from __future__ import print_function

import sys, glob
from standoff_annotations import extract

def exact_spans(spans):
    return spans

def span_no_nested(spans):
    to_remove = set()
    for span in spans:
        for ospan in spans:
            if span[0] <= ospan[0] and ospan[1] <= span[1] and ospan != span:
                to_remove.add(ospan)
    return spans.difference(to_remove)

def span_no_outside(spans):
    to_remove = set()
    for span in spans:
        for ospan in spans:
            if span[0] <= ospan[0] and ospan[1] <= span[1] and ospan != span:
                to_remove.add(span)
    return spans.difference(to_remove)

lines = sys.stdin.readlines()
if len(lines) == 0:
    print("Usgae:\n{} [labels to ignore] [all | any, filter criteria] < <standoff_file>".format(sys.argv[0]))
    sys.exit(0)

# Input
annotation_counts = {}
annotations = {}
labels = {}
to_ignore = [v for v in sys.argv[1:] if len(v) == 1]
ignore_criteria = 'none'
if 'any' in sys.argv:
    ignore_criteria = 'any'
elif 'all' in sys.argv:
    ignore_criteria = 'all'
users = set()

def is_allowed(post):
    if ignore_criteria == 'none':
        return True
    cur_labels = labels[post]
    for label in to_ignore:
        if label in cur_labels:
            if ignore_criteria == 'any':
                return False
            if len(annotations[post]) == len(cur_labels[label]):
                return False
    return True

for line in lines:
    if 'Unknown' in line:
        continue
    fields = line.strip().split()
    post = fields[0]
    user = fields[1]
    users.add(user)
    span = (int(fields[2]), int(fields[4]), fields[3] + fields[5])
    flags = fields[6:]
    if post not in annotations:
        annotations[post] = {}
        labels[post] = {}
        annotation_counts[post] = {}
    if user not in annotations[post]:
        annotations[post][user] = ([], flags)
    for flag in flags:
        if flag in to_ignore:
            if flag not in labels[post]:
                labels[post][flag] = set()
            labels[post][flag].add(user)
    annotations[post][user][0].append(span)
    if span not in annotation_counts[post]:
        annotation_counts[post][span] = 0
    annotation_counts[post][span] += 1

# Calculate the number of people who annotated a given span
count_distribution = {}
total = {}
total_unique = {}
for post in annotation_counts:
    if is_allowed(post):
        annotators = len(annotations[post])
        for span in annotation_counts[post]:
            count = annotation_counts[post][span]
            if annotators not in count_distribution:
                count_distribution[annotators] = {}
                total[annotators] = 0
                total_unique[annotators] = 0
            if count not in count_distribution[annotators]:
                count_distribution[annotators][count] = 0
            count_distribution[annotators][count] += 1
            total[annotators] += count
            total_unique[annotators] += 1
for annotators in count_distribution:
    for count in count_distribution[annotators]:
        val = count_distribution[annotators][count]
        score1 = float(val * count * 100) / total[annotators]
        score2 = float(val * 100) / total_unique[annotators]
        print("{} / {}  {:<5} {:.1f}% of all {:.1f}% of unique".format(count, annotators, val, score1, score2))

# Calculate counts for labels
pattern_counts = {}
for post in labels:
    pattern = [(label, len(labels[post][label])) for label in labels[post]]
    pattern.sort()
    pattern = (' '.join([l * c for l, c in pattern]), len(annotations[post]))
    if pattern not in pattern_counts:
        pattern_counts[pattern] = 0
    pattern_counts[pattern] += 1
pattern_to_print = []
for pattern in pattern_counts:
    pattern_to_print.append((pattern[1], pattern_counts[pattern], pattern[0]))
pattern_to_print.sort()
for annotators, count, pattern in pattern_to_print:
    print(count, pattern, '[' + str(annotators) +' ann]')

# Scorepairs of people relative to each other
# Note - at the moment we don't handle the case of a post where one person annotated something and no one else annotated anything at all
scores = {}
for post in annotations:
    if len(annotations[post]) > 1 and is_allowed(post):
        for userA in annotations[post]:
            spansA, flagsA = annotations[post][userA]
            spansA = set(spansA)
            for userB in annotations[post]:
                if userA < userB:
                    spansB, flagsB = annotations[post][userB]
                    spansB = set(spansB)
                    if (userA, userB) not in scores:
                        scores[userA, userB] = {
                            'post': [0, 0],
                            'span': [0, 0]
                        }
                    union = spansA.union(spansB)
                    intersection = spansA.intersection(spansB)
                    scores[userA, userB]['post'][0] += 1
                    if len(union) == len(intersection):
                        scores[userA, userB]['post'][1] += 1
                    scores[userA, userB]['span'][0] += len(union)
                    scores[userA, userB]['span'][1] += len(intersection)
for pair in scores:
    userA, userB = pair
    for name in scores[userA, userB]:
        total, val = scores[userA, userB][name]
        print("{:<5.2f}  {:<5} {:<5}  {}    {:>8} - {:>8}".format(100 * val / total, total, val, name, userA, userB))

# Calculate p/r/f relative to the majority
sample_disagree = {user: [] for user in users}
for user in users:
    total_all = 0
    total_user = 0
    total_agree = 0
    for post in annotations:
        if is_allowed(post) and user in annotations[post]:
            cutoff = len(annotations[post]) // 2
            cur_all = 0
            cur_user = 0
            cur_agree = 0
            for span in annotation_counts[post]:
                if annotation_counts[post][span] > cutoff:
                    cur_all += 1
            for span in annotations[post][user][0]:
                cur_user += 1
                if annotation_counts[post][span] > cutoff:
                    cur_agree += 1
            total_all += cur_all
            total_user += cur_user
            total_agree += cur_agree
            p = float(cur_agree) / float(cur_user) if cur_user > 0 else 1.0
            r = float(cur_agree) / float(cur_all) if cur_all > 0 else 1.0
            f = p * r * 2 / (p + r) if p + r > 0 else 1.0
            sample_disagree[user].append((f, post))

    p = 100 * float(total_agree) / float(total_user) if total_user > 0 else 1.0
    r = 100 * float(total_agree) / float(total_all) if total_all > 0 else 1.0
    f = p * r * 2 / (p + r) if p + r > 0 else 100.0
    print("{:<10}  PRF {:.0f} {:.0f} {:.0f}    {} {} {}".format(user, p, r, f, total_all, total_user, total_agree))

for user in sample_disagree:
    posts = sample_disagree[user]
    posts.sort()
    for f, post in posts[:30]:
        print(user, post, f)

