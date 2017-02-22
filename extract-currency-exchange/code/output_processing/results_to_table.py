#!/usr/bin/env python3

import sys

from collections import defaultdict

from gazateers import currency_dict, trading_dict, common_dict

ratio = 0.6
top_offset = 0.1

# small
###cutoff = 4
###do_labels = False
###axis_offset = -2.5
###cutoff = 12
###do_labels = True
###axis_offset = -2.7
###col_label_offset = -0.05
###row_label_offset = -0.05

# Big
###cutoff = 21
cutoff = 6
axis_offset = -2.7
do_labels = True
col_label_offset = -0.1
row_label_offset = -0.1

gap = 1.8
forums = ['hackforums', 'darkode', 'nulled']
grid_bottom = 0
grid_top = ratio * (cutoff + 1)
grid_left = [
    0,
    ratio * (cutoff + 1) + gap,
    ratio * (cutoff + 1) * 2 + gap * 2
]
grid_right = [
    ratio * (cutoff + 1),
    ratio * (cutoff + 1) * 2 + gap,
    ratio * (cutoff + 1) * 3 + gap * 2
]

def get_closest_fraction(num):
    closest = (1, 9, abs(num - 1 / 9))
###    for numerator in range(1, 10):
    for numerator in range(1, 2):
        for denominator in range(2, 10):
            distance = abs(num - (numerator / denominator))
            if distance < closest[2]:
                closest = (numerator, denominator, distance)
    return closest
    pass

def get_currency(token):
    if token.lower() in currency_dict:
        return currency_dict[token.lower()]
    else:
        return None

def render_start(forum):
    forum = titlecase(forum)
    print("\\begin{tikzpicture}")
    print("\\draw[step={}cm,gray,very thin] ({:.2f},{:.2f}) grid ({:.2f},{:.2f});".format(ratio, grid_left[0], grid_bottom, grid_right[0], grid_top))
    print("\\node [above] at ({:.2f}, {})".format((grid_left[0] + grid_right[0]) / 2, grid_top + top_offset) + "{\\Large "+ forum +"};\n")
    if do_labels:
        print("\\node [rotate=90] at ({}, {:.2f})".format(axis_offset, grid_top / 2) + "{\\Large Have};\n")
        print("\\node at ({:.2f}, {})".format((grid_left[0] + grid_right[0]) / 2, axis_offset) + "{\\Large Want};\n")

def render_end():
    print("\\end{tikzpicture}")

def titlecase(words):
    changed = []
    for word in words.split(" "):
        changed.append(word[0].upper() + word[1:].lower())
    return ' '.join(changed)

def render_new_row(row, name):
    name = titlecase(name)
    ans = "\\node [left] at ({}, {:.2f}) ".format(row_label_offset, 0.25 + row * ratio) + "{"+ name +"};\n"
    print(ans)

def render_new_col(col, name, count):
    name = titlecase(name)
    ans = "\\node [left,rotate=90] at ({:.2f}, {}) ".format(0.25 + col * ratio + grid_left[count], col_label_offset) + "{"+ name +"};\n"
    print(ans)

def render_cell(row, col, num, count):
    if num == 0 or num < 0.05:
        return
    colour = "yellow"
    if num > 1000:
        colour = "red"
        num = "{:.1f}k".format(num / 1000)
    else:
        if num > 100:
            colour = "orange"
        if 0 < num < 0.5:
            num = "\\textasciitilde"
            colour = "white"
        else:
            num = "{:.0f}".format(num)
    ans = ""
    ans += "\\filldraw[fill={}, draw=black] ({:.2f},{:.2f}) rectangle ({:.2f},{:.2f});\n".format(colour, grid_left[count] + row * ratio, col * ratio, grid_left[count] + row * ratio + ratio, col * ratio + ratio)
    ans += "\\node at ({:.2f}, {:.2f})".format(grid_left[count] + ratio / 2 + row * ratio, ratio / 2 + col * ratio) + "{"+ str(num) + "};\n"
    print(ans)

MISSING = "\\it (Missing)"
def get_currencies(field):
    ans = []
    for part in field[3:-1].split(','):
        curr = get_currency(part)
        if curr is not None:
            ans.append(curr)
    if len(ans) == 0:
        ans.append(MISSING)
    return ans

trades = defaultdict(lambda: 0.0)
currencies = defaultdict(lambda: 0.0)

# get trades
forum = None
for line in sys.stdin:
    if line.startswith("data/raw"):
        source = line.split('/')[2]
        info = line.split("Linked_Global_Extractor")[1][1:].split()
        have = get_currencies(info[0])
        want = get_currencies(info[1])
        value = 1 / (len(have) * len(want))
        if len(have) == 1 and len(want) == 1 and have[0] == want[0] == MISSING:
            continue
        for c0 in have:
            for c1 in want:
                forum = source
                trades[source, c0, c1] += value
                currencies[c0] += value
                currencies[c1] += value

# Order currencies
ordered = []
for curr in currencies:
    ordered.append((currencies[curr], curr))
ordered.sort(reverse=True)
ordered = [v[1] for v in ordered]
top = ordered[:cutoff]
top.append('Other ({})'.format(len(ordered) - len(top)))

to_add = []
for source, c0, c1 in trades:
    if c0 in top and c1 in top:
        continue
    num = trades[source, c0, c1]
    if c0 in top:
        to_add.append((source, c0, top[-1], num))
    elif c1 in top:
        to_add.append((source, top[-1], c1, num))
    else:
        to_add.append((source, top[-1], top[-1], num))

for source, c0, c1, num in to_add:
    trades[source, c0, c1] += num

# Render
render_start(forum)
for pos0, c0 in enumerate(top):
    render_new_row(pos0, c0)
    for pos1, c1 in enumerate(top):
        count = 0
        if pos0 == 0:
            render_new_col(pos1, c1, count)
        num = 0
        if (forum, c0, c1) in trades:
            num = trades[forum, c0, c1]
        render_cell(pos0, pos1, num, count)
render_end()
