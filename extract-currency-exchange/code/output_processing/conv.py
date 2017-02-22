#!/usr/bin/env python3

import sys, re

from collections import defaultdict

from gazateers import currency_dict, trading_dict, common_dict

def get_currency(token):
    if token.lower() in currency_dict:
        return currency_dict[token.lower()]
    else:
        return None

MISSING = "Missing"
def get_currencies(field):
    ans = []
    for part in field[3:-1].split(','):
        curr = get_currency(part)
        if curr is not None:
            ans.append(curr)
    if len(ans) == 0:
        ans.append(MISSING)
    return ans

pattern_numbers = re.compile('^[,.0-9]*[0-9][,.0-9]*$')
def get_ratio(fields):
    ans = []
    parts = fields[2][3:-1].split(',')
    for part in parts:
        try:
            if len(part) > 0:
                if ':' in part:
                    have = part.split(':')[0]
                    want = part.split(':')[1]
                    ans.append(float(want) / float(have))
                elif pattern_numbers.match(part):
                    if '%' in parts and len(parts) == 2:
                        ans.append(1 + float(part)/100)
                        print(parts)
                    else:
                        ans.append(float(part))
        except:
            pass
    have = None
    for part in fields[0][3:-1].split(','):
        if pattern_numbers.match(part):
            have = part
    want = None
    for part in fields[1][3:-1].split(','):
        if pattern_numbers.match(part):
            want = part
    if have is not None and want is not None:
        try:
            ans.append(float(want) / float(have))
        except:
            pass
    modans = []
    for val in ans:
        if val < -10:
            modans.append(-10)
        elif val > 10:
            modans.append(10)
        else:
            modans.append(val)
    ans = modans
    return '['+ ' '.join(["{:.1f}".format(val) for val in ans]) +']'

for line in sys.stdin:
    if line.startswith("data/raw"):
        source = line.split('/')[2]
        info = line.split("Linked_Global_Extractor")[1][1:].split()
        have = get_currencies(info[0])
        want = get_currencies(info[1])
        rate = get_ratio(info)
        value = 1 / (len(have) * len(want))
        if len(have) == 1 and len(want) == 1 and have[0] == want[0] == MISSING:
            continue
        print(line.split(',')[0], ' '.join(["H:"+ tok for tok in have]), ' '.join(["W:"+ tok for tok in want]), rate)

