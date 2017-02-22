'''Data structures for storing files and annotations.'''

import re

class Annotation:
    def __init__(self):
        self.have = []
        self.want = []
        self.rate = []
        self.inverse = {}

    def add(self, name, pos):
        if pos not in self.inverse:
            self.inverse[pos] = set()
        self.inverse[pos].add(name)
        if name == 'have':
            self.have.append(pos)
        elif name == 'want':
            self.want.append(pos)
        elif name == 'rate':
            self.rate.append(pos)

    def get_by_name(self, name):
        if name == 'have': return self.have
        if name == 'want': return self.want
        if name == 'rate': return self.rate

    def list_to_string(self, name, data):
        if len(data) == 0:
            return name + ":_"
        elif len(data) == 1:
            return name + ":" + ''.join(str(data[0]).split())
        else:
            return name + ":(" + ','.join([''.join(str(val).split()) for val in data]) +")"

    def word_repr(self, text):
        have_text = [text[v[0]][v[1]] for v in self.have]
        want_text = [text[v[0]][v[1]] for v in self.want]
        rate_text = [text[v[0]][v[1]] for v in self.rate]
        return \
            self.list_to_string('H', have_text) +" "+ \
            self.list_to_string('W', want_text) +" "+ \
            self.list_to_string('R', rate_text)

    def __repr__(self):
        return \
            self.list_to_string('H', self.have) +" "+ \
            self.list_to_string('W', self.want) +" "+ \
            self.list_to_string('R', self.rate)

class Labeled_Doc(object):
    # gold is dict from label => list((int, int))
    def __init__(self, text, gold, filename):
        self.text = text
        self.gold = gold
        self.filename = filename

    def get_golds(self, label):
        # List is not empty
        if (self.gold is not None and self.gold[label]):
            return [pair[0] for pair in self.gold[label]]
        else:
            return [(-1, -1)]

    def __repr__(self):
        ans = []
        for cline_no, cline in enumerate(datum.text):
            line = [str(cline_no) +":"]
            for cword in cline:
                line.append(cword)
            ans.append(' '.join(line))
        '\n'.join(ans)

annotations_re = re.compile('^[{[|][^]|}]+[]}]?[]}|]$')
def extract_gold(text, data):
    ans = Annotation()
    for l, line in enumerate(data):
        i = -1
        for fullword in line.strip().split():
            match = annotations_re.match(fullword)
            if match is None:
                i += len(split_word(fullword))
            else:
                subword = fullword.lower()
                while len(subword) > 1 and subword[0] in '{[|' and subword[-1] in '}]|':
                    subword = subword[1:-1]
                for word in split_word(subword):
                    i += 1
                    # Currently ignoring annotation of $
                    if '$' in fullword and len(fullword) < 4:
                        continue
                    if '{' in fullword and '}' in fullword:
                        ans.add('have', (l, i))
                    if '[' in fullword and ']' in fullword:
                        ans.add('want', (l, i))
                    if len(fullword.split('|')) > 2:
                        ans.add('rate', (l, i))
    return ans

compound_word = re.compile("^([0-9.]+)([A-Za-z]+)$")
def split_word(word):
    match = compound_word.match(word)
    if match is None:
        return word.split('/')
    else:
        return [match.group(1), match.group(2)]

def read(raw_file, gold_file = None):
    text = []
    for line in open(raw_file).readlines():
        to_add = []
        for word in line.split():
            # Split words in cases lik '50lr', specifically, numbers followed by
            # letters
            for subword in split_word(word):
                to_add.append(subword)
        text.append(to_add)
    if gold_file is not None:
        annotated = open(gold_file).readlines()
        gold = extract_gold(text, annotated)
        return Labeled_Doc(text, gold, raw_file)
    else:
        return Labeled_Doc(text, None, raw_file)

def get_filenames(filenames_file, get_gold=True):
    for line in open(filenames_file):
        line = line.strip()
        parts = line.split()
        if get_gold:
            yield (parts[0], parts[1])
        else:
            yield (parts[0], None)
