#!/usr/bin/env python3
"""A collection of functions for handling standoff annotations."""

from __future__ import print_function

import heapq

flag_map = {
  'A': 'ADMIN',
  'D': 'DIFFICULT',
  'H': 'HARD',
  'M': 'MULTIPLE',
  'O': 'OTHER',
  'S': 'UNSURE',
  'U': 'UNSATISFIED',
  'W': 'WEIRD',
  'L': 'LAME',
  'G': 'GAMING',
}

tok_map = [
  ('[', '-LSB-'),
  (']', '-RSB-'),
  ('{', '-LCB-'),
  ('}', '-RCB-'),
  ('(', '-LSB-'),
  (')', '-RSB-'),
  ("'", '`'),
  ('"', "''"),
  ('"', "``"),
]

def align_text(raw, ann):
  '''Performs a uniform cost search to align annotations with raw data'''
  # States are (raw_index, ann_index) tuples
  start = (-1, -1)
  goal = (len(raw) - 1, len(ann) - 1)

  # Start with nothing aligned
  best = None
  expanded = set()
  # Search states are (coat, state, prev_search_state)
  fringe = [(0, start, None)]

  # Search
  pushes = 0
  pops = 0
  newpops = 0
  while len(fringe) > 0:
    cur = heapq.heappop(fringe)
    pops += 1
    if cur[1] in expanded:
      continue
    newpops += 1
    expanded.add(cur[1])
    if cur[1] == goal:
      best = cur
      break

    # Consider match
    npos = (cur[1][0] + 1, cur[1][1] + 1)
    if npos[0] < len(raw) and npos[1] < len(ann):
      cost = cur[0]
      if raw[npos[0]] == ann[npos[1]]:
        heapq.heappush(fringe, (cost, npos, cur))
        pushes += 1
      for atok, rtok in tok_map:
        if ann[npos[1]] == atok and len(raw) >= npos[0] + len(rtok) and raw[npos[0]:npos[0] + len(rtok)] == rtok:
          heapq.heappush(fringe, (cost, (npos[0] + len(rtok) - 1, npos[1]), cur))
          pushes += 1
    # Consider insert
    npos = (cur[1][0] + 1, cur[1][1])
    if npos[0] < len(raw):
      cost = cur[0] + 2
      if raw[npos[0]] in ' \n':
        cost -= 1
      heapq.heappush(fringe, (cost, npos, cur))
      pushes += 1
    # Consider skip
    npos = (cur[1][0], cur[1][1] + 1)
    if npos[1] < len(ann):
      cost = cur[0] + 2
      if ann[npos[1]] in ' \n':
        cost -= 1
      heapq.heappush(fringe, (cost, npos, cur))
      pushes += 1

  # Extract the points where annotations are present
  ans = []
  while best is not None:
    prev = best[2]
    if prev is None:
      break
    if prev[1][0] == best[1][0]:
      ans.append(best[1])
    best = prev
  return ans[::-1]

def standardise(chunks):
  ans = []
  for chunk in chunks:
    if chunk[-1] == '{B':
      ans.append((chunk[0], chunk[1], chunk[2], '['))
    elif chunk[-1] == '{S':
      ans.append((chunk[0], chunk[1], chunk[2], '{'))
    elif chunk[-1] == '}' and len(ans) > 0 and ans[-1][-1] == '[':
      ans.append((chunk[0], chunk[1], chunk[2], ']'))
    else:
      ans.append(chunk)
  return ans

def chunk_ann(locations, ann):
  chunks = []
  prev = (-2, -2)
  for rawp, annp in locations:
    if ann[annp] in ' \n()':
      pass
    elif rawp == prev[0] and annp == prev[1] + 1:
      chunks[-1] = (chunks[-1][0], chunks[-1][1], annp, chunks[-1][3] + ann[annp])
      prev = (rawp, annp)
    else:
      chunks.append((rawp, annp, annp, ann[annp]))
      prev = (rawp, annp)
  return standardise(chunks)

def extract(raw, ann):
  ann_locations = align_text(raw, ann)
  ann_flags = set()
  ann_brackets = []
  for raw_pos, ann_spos, ann_epos, text in chunk_ann(ann_locations, ann):
    if text in '{}[]':
      ann_brackets.append((raw_pos, text))
    else:
      ann_flags.add(text)

  ann_spans = set()
  incomplete = []

  for pos, bracket in ann_brackets:
    if bracket in '[{':
      incomplete.append((pos, bracket))
    elif bracket in ']}':
      if len(incomplete) == 0:
        print(ann)
        raise Exception("Unmatched close bracket")
      start = incomplete.pop()
      ann_spans.add((start[0] + 1, start[1], pos + 1, bracket))
  if len(incomplete) != 0:
    print(ann)
    raise Exception("Unmatched open bracket")

  return ann_spans, ann_flags

def insert(raw, ann_spans, ann_flags):
  ans = ''
  starts = {span[0][0]: span[1][0] for span in ann_spans}
  ends = {span[0][1]: span[1][1] for span in ann_spans}
  for i in range(len(raw)):
    if i in ends:
      ans += ends[i]
    if i in starts:
      ans += starts[i]
    ans += raw[i]
  ans += '\n'
  for flag in ann_flags:
    ans += flag + '\n'
  return ans
