
from constants import *

def eval_type(token, field):
    if field == 'rate':
            'rate'
    elif pattern_numbers_re.match(token) or token in number_words:
        return NUMBER
    else:
        return CURRENCY

def merge_multiword_currency(text, positions):
    # Two adjacent words are in the list, and form a single currency
    positions.sort()
    npos = []
    for i in range(len(positions) - 1):
        pos0 = positions[i]
        pos1 = positions[i+1]
        if pos0[0] == pos1[0] and pos1[1] - pos1[0] == 1:
            pair = text[pos0[0]][pos0[1]] +" "+ text[pos1[0]][pos1[1]]
            if pair.lower() in currency_dict:
                continue
        npos.append(positions[i])
    if len(positions) > 0:
        npos.append(positions[-1])

    # A position is in the list and the word after is part of the currency name
    # (in which case we should remap to that)
    npos2 = []
    for pos in npos:
        if len(text[pos[0]]) > pos[1] + 1:
            pair = text[pos[0]][pos[1]] +" "+ text[pos[0]][pos[1] + 1]
            if pair.lower() in currency_dict:
                npos2.append((pos[0], pos[1] + 1))
            else:
                npos2.append(pos)
        else:
            npos2.append(pos)
    return npos2

def score(guess, gold, text, unique = True, merge_two_word_currency = False):
    results = {
        'complete': 1,
        'have-want': 0, 'want-have': 0, 'gold-curr': 0, 'reversed': 0,
        'currency': {'missing': 0, 'extra': 0, 'match': 0},
        'amount': {'missing': 0, 'extra': 0, 'match': 0},
        'rate': {'missing': 0, 'extra': 0, 'match': 0},
    }

    gold_curr = {
        'want': [],
        'have': []
    }
    guess_curr = {
        'want': [],
        'have': []
    }

    for field in ['have', 'want', 'rate']:
        init_guess_vals = merge_multiword_currency(text, guess.get_by_name(field))
        init_gold_vals = merge_multiword_currency(text, gold.get_by_name(field))

        guess_vals = []
        for pos in init_guess_vals:
            info = get_type_and_canon(pos, text)
            category = eval_type(info[1], field)
            guess_vals.append((category, info[1], pos))
        gold_vals = []
        for pos in init_gold_vals:
            info = get_type_and_canon(pos, text)
            category = eval_type(info[1], field)
            gold_vals.append((category, info[1], pos))

        if unique:
            guess_vals = {(info[0], info[1]) for info in guess_vals}
            gold_vals = {(info[0], info[1]) for info in gold_vals}

        # Update reversed
        for v in gold_vals:
            if v[0] == CURRENCY:
                results['gold-curr'] += 1
                gold_curr[field].append(v[1])
        for v in guess_vals:
            if v[0] == CURRENCY:
                guess_curr[field].append(v[1])

        guess_summary = []
        for v in guess_vals:
            guess_summary.append("({} {})".format(v[0], v[1]))
        gold_summary = []
        for v in gold_vals:
            gold_summary.append("({} {})".format(v[0], v[1]))

        for val in gold_vals:
            result_set = results['amount']
            if field == 'rate':
                result_set = results['rate']
            elif val[0] == CURRENCY:
                result_set = results['currency']

            if val in guess_vals:
                result_set['match'] += 1
            else:
                result_set['missing'] += 1
                results['complete'] = 0
        for val in guess_vals:
            result_set = results['amount']
            if field == 'rate':
                result_set = results['rate']
            elif val[0] == CURRENCY:
                result_set = results['currency']

            if val not in gold_vals:
                result_set['extra'] += 1
                results['complete'] = 0

    both_dir = 0
    for val in guess_curr['want']:
        if val not in gold_curr['want']:
            if val in gold_curr['have']:
                results['want-have'] += 1
                both_dir = 1
    for val in guess_curr['have']:
        if val not in gold_curr['have']:
            if val in gold_curr['want']:
                results['have-want'] += 1
                if both_dir == 1:
                    both_dir = 2
    if both_dir == 2:
        results['reversed'] += 1
    return results

