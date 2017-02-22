from __future__ import print_function
from __future__ import division

import heapq
import logging
import math
import random
import sys

from constants import *
from data import *

class Extractor(object):
    def __init__(self):
        self.name = "Extractor"

    def train(self, data):
        pass

    def extract(self, text):
        return Annotation()

#########################

class Baseline_Fixed_Word_Order(Extractor):
    '''Deterministic extractor using regular expressions.

    First token to match a currency is the 'have' currency, second to match that is not the same as the first is the 'want' currency.
    First token to match a number is the 'have' amount and second is the 'want' amount.
    '''
    def __init__(self):
        super(Baseline_Fixed_Word_Order, self).__init__()
        self.name = "Fixed_Word_Order_Extractor"

    def extract(self, text):
        ans = Annotation()
        have_currency = None
        want_currency = None
        have_amount = None
        want_amount = None
        for l, line in enumerate(text):
            for w, word in enumerate(line):
                word = word.lower()
                if map_word(word) == CURRENCY:
                    if have_currency is None:
                        have_currency = word
                        ans.have.append((l, w))
                    elif want_currency is None and word != have_currency:
                        want_currency = word
                        ans.want.append((l, w))
                elif map_word(word) == NUMBER:
                    if have_amount is None:
                        have_amount = word
                        ans.have.append((l, w))
                    elif want_amount is None:
                        want_amount = word
                        ans.want.append((l, w))
        return ans

#########################

class Pattern_Extractor(Extractor):
    def __init__(self):
        super(Pattern_Extractor, self).__init__()
        self.patterns = {}
        self.name = "Pattern_Extractor"

    def train(self, data):
        # Note - Completely ignores all non-special words
        for datum in data:
            text = datum.text
            gold = datum.gold

            for l, line in enumerate(text):
                pattern = []
                marks = []
                use = False
                for i, word in enumerate(line):
                    mapped = map_word(word)
                    if mapped != UNK:
                        pattern.append(mapped)
                        if (l, i) in gold.inverse:
                            marks.append(gold.inverse[l, i])
                            use = True
                        else:
                            marks.append('')
                if use:
                    self.patterns[tuple(pattern)] = tuple(marks)

    def __repr__(self):
        ans = []
        for pattern in self.patterns:
            ans.append(str((pattern, self.patterns[pattern])))
        return '\n'.join(ans)

    def extract(self, text):
        ans = Annotation()
        for l, line in enumerate(text):
            pattern = []
            words = []
            for w, word in enumerate(line):
                mapped = map_word(word)
                if mapped != UNK:
                    pattern.append(mapped)
                    words.append(w)
            pattern = tuple(pattern)
            if pattern in self.patterns:
                marks = self.patterns[pattern]
                for w, mark in zip(words, marks):
                    for symbol in mark:
                        ans.add(symbol, (l, w))
        return ans

#########################

class Doc_With_Cache(Labeled_Doc):
    # gold is dict from label => list((int, int))
    def __init__(self, doc):
        super(Doc_With_Cache, self).__init__(doc.text, doc.gold, doc.filename)
        self.feats_cache = {}

def get_word(text, l, w, delta):
    assert(l >= 0 and w >= 0)
    for d in range(abs(delta)):
        if 0 <= l < len(text):
            if w < 0:
                if delta > 0:
                    w = 0
                else:
                    l -= 1
                    while l >= 0 and len(text[l]) == 0:
                        l -= 1
                    if l >= 0:
                        w = len(text[l]) - 1
            else:
                if delta < 0:
                    w -= 1
                else:
                    if w < len(text[l]) - 1:
                        w += 1
                    else:
                        l += 1
                        while l < len(text) and len(text[l]) == 0:
                            l += 1
                        w = -1

    if l < 0:
        return 'DOC_START'
    elif l >= len(text):
        return 'DOC_END'
    elif w < 0:
        return 'SENT_BOUNDARY'
    else:
        return text[l][w]

def context_to_str_feats_nonnull(text, l, i, label):
    # Actual words
    words = []
    for d in range(-6, 0):
        name = 'p' * abs(d) + 'w'
        feat = get_word(text, l, i, d).lower()
        words.append((name, feat)) 
    words.append(('cw', get_word(text, l, i, 0).lower()))
    for d in range(1, 7):
        name = 'n' * abs(d) + 'w'
        feat = get_word(text, l, i, d).lower()
        words.append((name, feat))

    # Trading words
    # get preceding and following trading words on this line
    trading_before = UNK
    for before_pos in range(i):
        word = text[l][before_pos].lower()
        if word in trading_dict:
            trading_before = trading_dict[word]
    trading_after = UNK
    for after_pos in range(i, len(text[l])):
        word = text[l][after_pos].lower()
        if word in trading_dict and trading_after == UNK:
            trading_after = trading_dict[word]

    # Pairs of mapped words
    mapped = [(name + '_map', map_word(word)) for name, word in words]
    mapcomb = []
    for w1 in range(len(words)):
        for w2 in range(w1):
            subwords = [mapped[w1], mapped[w2]]
            name = '_'.join([v[0] for v in subwords])
            feat = '_'.join([v[1] for v in subwords])
            mapcomb.append((name, feat))

    # Ngrams of mapped words
    for w in range(len(words)):
        for d in range(2, 5):
            if w + d < len(words):
                subwords = mapped[w:w+d+1]
                name = '_'.join([v[0] for v in subwords])
                feat = '_'.join([v[1] for v in subwords])
                mapcomb.append((name, feat))

    # Filtered mapped words
    mapped2 = [v for v in mapped if v[1] != UNK]
    for w in range(len(words)):
        for d in range(2, 5):
            if w + d < len(words):
                subwords = mapped2[w:w+d+1]
                name = "filter_"+ '_'.join([v[0] for v in subwords])
                feat = '_'.join([v[1] for v in subwords])
                mapcomb.append((name, feat))

    feats = []
    for name, feat in words:
        feats.append(name +"_"+ feat +"_"+ label)
    for name, feat in mapped:
        feats.append(name +"_"+ feat +"_"+ label)
    for name, feat in mapcomb:
        feats.append(name +"_"+ feat +"_"+ label)
    feats.append("TRADING_"+ trading_before +"_"+ trading_after)

    # Positional features
    feats.append("LinePos=" + (repr(l) if l < 4 else "Large") +"_"+ label)
    feats.append("WordPos=" + (repr(i) if i < 4 else "Large") +"_"+ label)
    nl = len(text) - l
    feats.append("NegLinePos=" + (repr(nl) if nl < 4 else "Large") +"_"+ label)
    ni = len(text[l]) - i
    feats.append("NegWordPos=" + (repr(ni) if ni < 4 else "Large") +"_"+ label)

    return feats

def context_to_str_feats(text, l, i, label):
    if l < 0 or i < 0:
        return ["NULL_" + label]
    else:
        return context_to_str_feats_nonnull(text, l, i, label)

class Classifier_Extractor(Extractor):
    def __init__(self):
        super(Classifier_Extractor, self).__init__()
        self.name = "Classifier_Extractor"
        self.feat_to_id = {}
        self.weights = []
        self.labels = ['have', 'want', 'have-want', 'rate', 'IGNORE']

        # Learning params
        self.gradient = {}
        self.gradient_sq = []
        self.gradient_last = []
        self.loss_weight_fn = 1.0
        self.loss_weight_fp = 1.0
        self.loss_weight_fd = 1.0
        self.batch_size = 1
        self.perceptron = False
        self.nupdates = 0
        self.step = 0.01 # Higher improves loss but doesn't work better
        self.reg = 0.00001
        self.fraction = 1.0
        self.num_train_itrs = 6 # 6 is the best out of {5, 6, 8, 10, 15}

    def print_weights(self):
        for feat in self.feat_to_id:
            feat_num = self.feat_to_id[feat]
            print(feat, self.weights[feat_num])

    def get_feat(self, feature):
        if feature not in self.feat_to_id:
            self.feat_to_id[feature] = len(self.weights)
            self.weights.append(0.0)
            self.gradient_sq.append(1e-6)
            self.gradient_last.append(self.nupdates)
        return self.feat_to_id[feature]
    
    def get_weight(self, feat_id):
        weight = self.weights[feat_id]
        # Lazy update
        if not self.perceptron:
            dt = self.nupdates - self.gradient_last[feat_id]
            if dt > 0:
                q = math.sqrt(self.gradient_sq[feat_id])
                weight *= pow(q / (self.step * self.reg + q), dt)
                self.weights[feat_id] = weight
                self.gradient_last[feat_id] = self.nupdates
        return weight

    def get_feat_and_weight(self, feature):
        feat_id = self.get_feat(feature)
        return (feat_id, self.get_weight(feat_id))
    
    def score_feats(self, feats):
        score = 0.0
        for feat in feats:
            score += self.get_weight(feat)
        return score
    
    def context_to_feats(self, text, l, i, label):
        string_feats = context_to_str_feats(text, l, i, label)
        int_feats = []
        for feat in string_feats:
            int_feats.append(self.get_feat(feat))
        return int_feats

    def update_gradient(self, feats, sign):
        for feat in feats:
            if feat not in self.gradient:
                self.gradient[feat] = 0.0
            self.gradient[feat] += sign

    def take_step(self):
        for feat in self.gradient:
            g = self.gradient[feat]
            w = self.weights[feat]
            if self.perceptron:
                self.weights[feat] -= g
            else:
                self.gradient_sq[feat] += g * g
                self.gradient_last[feat] = self.nupdates
                q = math.sqrt(self.gradient_sq[feat])
                self.weights[feat] = (w * q - self.step * g) / (self.step * self.reg + q)
                self.gradient_last[feat] = self.nupdates
        self.gradient = {}

    def train(self, data):
        self.nupdates = 0

        order = [i for i in range(int(math.floor(self.fraction * len(data))))]
        for i in range(self.num_train_itrs):
            metric = [0, 0, 0, 0]
            random.seed(i) # determinize training to make debugging easier
            random.shuffle(order)
            objective = 0.0
            for pos in order:
                # Data setup
                # gold is dict from label => list((int, int))
                datum = Doc_With_Cache(data[order[pos]])
                marked = datum.gold.inverse

                for cline in range(len(datum.text)):
                    for cword in range(len(datum.text[cline])):
                        # Classify
                        correct = self.labels[-1]
                        if (cline, cword) in marked:
                            correct_set = marked[cline, cword]
                            if 'have' in correct_set:
                                if 'want' in correct_set:
                                    correct = 'have-want'
                                else:
                                    correct = 'have'
                            elif 'want' in correct_set:
                                correct = 'want'
                            else:
                                correct = 'rate'
                        cscores = []
                        correct_feats = None
                        for label in self.labels:
                            key = (cline,cword,label)
                            if key not in datum.feats_cache:
                                datum.feats_cache[key] = self.context_to_feats(datum.text, cline, cword, label)
                            feats = datum.feats_cache[key]
                            total = self.score_feats(feats)
                            if label == correct:
                                correct_feats = feats
                            elif not self.perceptron:
                                if correct == self.labels[-1]:
                                    total += self.loss_weight_fp
                                elif label == self.labels[-1]:
                                    total += self.loss_weight_fn
                                else:
                                    total += self.loss_weight_fd
                            cscores.append((total, label, feats))
                        cscores.sort()

                        # Update metric
                        metric[1] += 1
                        if correct != self.labels[-1]:
                            metric[3] += 1
                        if cscores[-1][1] != correct:
                            metric[0] += 1
                            if correct != self.labels[-1]:
                                metric[2] += 1

                        # Update gradient
                        if cscores[-1][1] != correct:
                            self.update_gradient(cscores[-1][2], 1.0)
                            self.update_gradient(correct_feats, -1.0)

                # Update weights
                if (pos + 1) % self.batch_size == 0:
                    self.nupdates += 1
                    self.take_step()

            logging.info("Iteration {} objectives: {} {}".format(i, metric[0] / metric[1], metric[2] / metric[3]))
            sys.stdout.flush()

    def __repr__(self):
        return ''

    def extract(self, text):
        ans = Annotation()
        for l in range(len(text)):
            for i in range(len(text[l])):
                cscores = []
                for label in self.labels:
                    feats = self.context_to_feats(text, l, i, label)
                    total = self.score_feats(feats)
                    cscores.append((total, label, feats))
                cscores.sort()

                to_apply = cscores[-1][1]
                if to_apply != "IGNORE":
                    if to_apply == 'have-want':
                        ans.add('have', (l, i))
                        ans.add('want', (l, i))
                    else:
                        ans.add(to_apply, (l, i))
        return ans
    
#########################

class Global_Extractor(Classifier_Extractor):
    # Note: Currently only returns one exchange.
    def __init__(self):
        super(Global_Extractor, self).__init__()
        self.name = "Global_Extractor"
        self.labels = ['have', 'want', 'rate']
        self.search_steps = 100
        self.search_steps_max = 1000
        
    def calculate_score(self, scores, positions, text, negate=False):
        score = 0.0
        score += scores['have', 'amount'][positions[0]][0]
        score += scores['have', 'currency'][positions[1]][0]
        score += scores['want', 'amount'][positions[2]][0]
        score += scores['want', 'currency'][positions[3]][0]
        score += scores['rate', 'amount'][positions[4]][0]

        # Score with features that consider interactions
        info = [
            ('have_amount', scores['have', 'amount'][positions[0]][1]),
            ('have_currency', scores['have', 'currency'][positions[1]][1]),
            ('want_amount', scores['want', 'amount'][positions[2]][1]),
            ('want_currency', scores['want', 'currency'][positions[3]][1]),
            ('rate', scores['rate', 'amount'][positions[4]][1])
        ]

        feats = []
        # Pairwise cases
        for name0, pos0 in info:
            for name1, pos1 in info:
                if name0 == name1:
                    break
                # Types and the simplified tokens
                w0 = UNK
                w1 = UNK
                if pos0[0] >= 0:
                    w0 = text[pos0[0]][pos0[1]].lower()
                if pos1[0] >= 0:
                    w1 = text[pos1[0]][pos1[1]].lower()
                w0_mapped = map_word(w0)
                w1_mapped = map_word(w1)
                feat = 'PAIR_ww'+ name0 +"_"+ name1 +"_"+ w0 +"_"+ w1
                feats.append(self.get_feat(feat))
                feat = 'PAIR_wmwm'+ name0 +"_"+ name1 +"_"+ w0_mapped +"_"+ w1_mapped
                feats.append(self.get_feat(feat))

                # Trading word between
                if pos0[0] == pos1[0]:
                    trading_between = False
                    for i in range(min(pos0[1], pos1[1]), max(pos0[1], pos1[1])):
                        if text[pos0[0]][i].lower() in trading_dict:
                            trading_between = True
                    feat = 'PAIR_trading'+ name0 +"_"+ name1 +"_"+ str(trading_between)
                    feats.append(self.get_feat(feat))

                # Distance between types
                distance = "NA"
                if pos0[0] == pos1[0]:
                    distance = "toks="+ str(pos0[1] - pos1[1])
                elif pos0[0] >= 0 and pos1[0] >= 0:
                    distance = "sents="+ str(pos0[0] - pos1[0])
                feat = 'PAIR'+ name0 +"_"+ name1 +"_"+ distance
                feats.append(self.get_feat(feat))

        # Ordering of all the types
        order = [(v[1], v[0]) for v in info if v[1][0] >= 0]
        order.sort()
        feats.append(self.get_feat('ORDER'+ '_'.join([v[1] for v in order])))
        score += self.score_feats(feats)
        if negate:
            return (-score, feats)
        else:
            return (score, feats)

    def next_options(self, scores, positions):
        opts = []
        if len(scores['have', 'amount']) > positions[0] + 1:
            opts.append((positions[0] + 1, positions[1], positions[2], positions[3], positions[4]))
        if len(scores['have', 'currency']) > positions[1] + 1:
            opts.append((positions[0], positions[1] + 1, positions[2], positions[3], positions[4]))
        if len(scores['want', 'amount']) > positions[2] + 1:
            opts.append((positions[0], positions[1], positions[2] + 1, positions[3], positions[4]))
        if len(scores['want', 'currency']) > positions[3] + 1:
            opts.append((positions[0], positions[1], positions[2], positions[3] + 1, positions[4]))
        if len(scores['rate', 'amount']) > positions[4] + 1:
            opts.append((positions[0], positions[1], positions[2], positions[3], positions[4] + 1))
        return opts

    def decode(self, datum, restrict_to_gold, loss_augmentation):
        # 1 - For each label + [cur / amt], get a list of scored positions
        scores = {
            ('have', 'amount'): [],
            ('have', 'currency'): [],
            ('want', 'amount'): [],
            ('want', 'currency'): [],
            ('rate', 'amount'): []
        }

        gold_pairs = {} if datum.gold is None else datum.gold.inverse

        for label in self.labels:
            gold_exists = set()
            for cline in range(len(datum.text)):
                for cword in range(len(datum.text[cline])):
                    is_gold = label in gold_pairs.get((cline, cword), set())
                    if (not restrict_to_gold) or is_gold:
                        mapped_word = map_word(datum.text[cline][cword])
                        key = (cline,cword,label)
                        if key not in datum.feats_cache:
                            datum.feats_cache[key] = self.context_to_feats(datum.text, cline, cword, label)
                        feats = datum.feats_cache[key]
                        feats_score = self.score_feats(feats)
                        loss = 0 if is_gold or self.perceptron else loss_augmentation
                        if mapped_word == CURRENCY and label != 'rate':
                            scores[label, 'currency'].append((feats_score + loss, key))
                            if is_gold:
                                gold_exists.add('currency')
                        else:
                            scores[label, 'amount'].append((feats_score + loss, key))
                            if is_gold:
                                gold_exists.add('amount')

            # Explicitly have a 'no choice' option
            for option in scores:
                if option[0] == label and (len(scores[option]) == 0 or (not restrict_to_gold)):
                    subtype = option[1]
                    key = (-1, -1, label) if subtype == 'currency' else (-2, -2, label)
                    if key not in datum.feats_cache:
                        datum.feats_cache[key] = [self.get_feat("BIAS_{}_{}".format(label, subtype))]
                    feats = datum.feats_cache[key]
                    feats_score = self.score_feats(feats)
                    loss = loss_augmentation
                    if (not self.perceptron) and (subtype not in gold_exists):
                        loss = 0
                    scores[option].append((feats_score + loss, key))

        # 2 - Sort lists
        for option in scores:
            scores[option].sort(reverse=True)

        # 3 - Explore the space using UCS, stopping after N steps in which the
        # best does not change.
        start = (0, 0, 0, 0, 0)
        added = {start}
        best = (self.calculate_score(scores, start, datum.text, True), start)
        fringe = [best]
        steps_since_change = 0
        total_steps = 0
        while steps_since_change < self.search_steps and total_steps < self.search_steps_max and len(fringe) > 0:
            total_steps += 1
            steps_since_change += 1
            cur = heapq.heappop(fringe)
            if cur[0][0] < best[0][0]:
                best = cur
                steps_since_change = 0
            for opt in self.next_options(scores, cur[1]):
                if opt not in added:
                    added.add(opt)
                    score = self.calculate_score(scores, opt, datum.text, True)
                    heapq.heappush(fringe, (score, opt))

        ans = []
        chosen = best[1]
        ans.append(scores['have', 'amount'][chosen[0]][1])
        ans.append(scores['have', 'currency'][chosen[1]][1])
        ans.append(scores['want', 'amount'][chosen[2]][1])
        ans.append(scores['want', 'currency'][chosen[3]][1])
        ans.append(scores['rate', 'amount'][chosen[4]][1])
        best_score = self.calculate_score(scores, chosen, datum.text)
        feats = [best_score[1]]
        for key in ans:
            feats.append(datum.feats_cache[key])
        return (ans, best_score[0], feats)

    def train(self, data):
        self.nupdates = 0

        order = [i for i in range(math.floor(self.fraction * len(data)))]
        for i in range(self.num_train_itrs):
            metric = [0, 0, 0, 0]
            random.seed(i) # determinize training to make debugging easier
            random.shuffle(order)
            objective = 0.0
            for pos in order:
                # Data setup
                # gold is dict, label => ((int, int), word)
                datum = Doc_With_Cache(data[order[pos]])

                # Get the best gold and best prediction
                gold_labels, gold_score, gold_feats = self.decode(datum, True, 0)
                pred_labels, pred_score, pred_feats = self.decode(datum, False, 1)
                for key in datum.gold.inverse:
                    raw_word = datum
                    word = datum.text[key[0]][key[1]]
                    mapped_word = map_word(word)
                
                # Objective value to minimize
                objective += pred_score - gold_score

                # Update the metric
                metric[1] += 1
                metric[3] += 1
                if gold_labels != pred_labels:
                    metric[0] += 1
                    metric[2] += 1
                
                # Update the gradient
                if gold_labels != pred_labels:
                    for feats in gold_feats:
                        self.update_gradient(feats, -1.0)
                    for feats in pred_feats:
                        self.update_gradient(feats, 1.0)

                # Update weights
                if (not self.perceptron) and (pos + 1) % self.batch_size == 0:
                    self.nupdates += 1
                    self.take_step()
            logging.info("Iteration {} objectives: {} {}".format(i, repr(objective), metric[0] / metric[1], metric[2] / metric[3]))
            sys.stdout.flush()

    def extract(self, text):
        ans = Annotation()
        datum = Doc_With_Cache(Labeled_Doc(text, None, None))
        keys, score, feats = self.decode(datum, False, 0)
        for key in keys:
            line, word, label = key
            if word >= 0:
                ans.add(label, (line, word))
        return ans

class Linked_Global_Extractor(Classifier_Extractor):
    def __init__(self):
        super(Linked_Global_Extractor, self).__init__()
        self.name = "Linked_Global_Extractor"
        self.search_steps = 5
        self.search_steps_max = 100
        
    def calculate_score(self, scores, positions, text, groups, negate=False):
        score = 0.0
        for group, position in positions:
            score += scores[group][position][0]
        if negate:
            return (-score, [])
        else:
            return (score, [])

    def next_options(self, scores, positions):
        opts = []
        for group, position in positions:
            npos = position + 1
            if npos < len(scores[group]):
                option = list(positions)
                for i, pair in enumerate(option):
                    if pair[0] == group:
                        option[i] = (group, npos)
                opts.append(tuple(option))
        return opts

    def decode(self, datum, restrict_to_gold, loss_augmentation):
        # 0 - Form groups to be classified
        groups = {}
        for cline in range(len(datum.text)):
            for cword in range(len(datum.text[cline])):
                raw_word = datum.text[cline][cword]
                key = (cline,cword)
                word_type, canon_word = get_type_and_canon(key, datum.text)
                groups.setdefault(canon_word, []).append(key)

        # 1 - For each group, get a list of scored labels
        scores = {}
        gold_pairs = {} if datum.gold is None else datum.gold.inverse
        for group in groups:
            any_gold = False
            for label in self.labels:
                score = 0.0
                has_gold = False
                for cline, cword in groups[group]:
                    # Update gold tracking
                    is_gold = label in gold_pairs.get((cline, cword), set())
                    if is_gold:
                        any_gold = True
                    elif label == self.labels[-1]:
                        is_gold = not any_gold
                    if is_gold:
                        has_gold = True

                    key = (cline,cword,label)
                    if key not in datum.feats_cache:
                        datum.feats_cache[key] = self.context_to_feats(datum.text, cline, cword, label)
                    feats = datum.feats_cache[key]
                    feats_score = self.score_feats(feats)

                    # Calculate loss
                    loss = 0 if is_gold or self.perceptron else loss_augmentation

                    # Update overall score
                    score += feats_score + loss

                # Add this as a label for this group
                if has_gold or (not restrict_to_gold):
                    scores.setdefault(group, []).append((score, label))

        # 2 - Sort lists
        for group in scores:
            scores[group].sort(reverse=True)

        # 3 - Explore the space using UCS, stopping after N steps in which the
        # best does not change.
        added = {}
        start = tuple([(group, 0) for group in groups])
        best = (self.calculate_score(scores, start, datum.text, groups, True), start)
        added = {start}
        fringe = [best]
        steps_since_change = 0
        total_steps = 0
        while steps_since_change < self.search_steps and total_steps < self.search_steps_max and len(fringe) > 0:
            total_steps += 1
            steps_since_change += 1
            cur = heapq.heappop(fringe)
            if cur[0][0] < best[0][0]:
                best = cur
                steps_since_change = 0
            for opt in self.next_options(scores, cur[1]):
                if opt not in added:
                    added.add(opt)
                    score = self.calculate_score(scores, opt, datum.text, groups, True)
                    heapq.heappush(fringe, (score, opt))

        ans = []
        chosen = best[1]
        best_score = -best[0][0]
        for group, pos in chosen:
            score, label = scores[group][pos]
            if label != self.labels[-1]:
                for key in groups[group]:
                    ans.append((key[0], key[1], label))
        feats = [best[0][1]]
        for key in ans:
            feats.append(datum.feats_cache[key])
        return (ans, best_score, feats)

    def train(self, data):
        self.nupdates = 0

        order = [i for i in range(math.floor(self.fraction * len(data)))]
        for i in range(self.num_train_itrs):
            metric = [0, 0, 0, 0]
            random.seed(i) # determinize training to make debugging easier
            random.shuffle(order)
            objective = 0.0
            for pos in order:
                # Data setup
                # gold is dict, label => ((int, int), word)
                datum = Doc_With_Cache(data[order[pos]])

                # Get the best gold and best prediction
                gold_labels, gold_score, gold_feats = self.decode(datum, True, 0)
                pred_labels, pred_score, pred_feats = self.decode(datum, False, 1)
                
                # Objective value to minimize
                objective += pred_score - gold_score

                # Update the metric
                metric[1] += 1
                if gold_labels != pred_labels:
                    metric[0] += 1
                
                # Update the gradient
                if gold_labels != pred_labels:
                    for feats in gold_feats:
                        self.update_gradient(feats, -1.0)
                    for feats in pred_feats:
                        self.update_gradient(feats, 1.0)

                # Update weights
                if (not self.perceptron) and (pos + 1) % self.batch_size == 0:
                    self.nupdates += 1
                    self.take_step()
            logging.info("Iteration {} objectives: {}".format(i, objective, metric[0] / metric[1]))
            sys.stdout.flush()

    def extract(self, text):
        ans = Annotation()
        datum = Doc_With_Cache(Labeled_Doc(text, None, None))
        keys, score, feats = self.decode(datum, False, 0)
        for key in keys:
            line, word, label = key
            if word >= 0:
                ans.add(label, (line, word))
        return ans

