#!/usr/bin/env python3

from __future__ import print_function
from __future__ import division

import argparse
import logging
import pickle
import sys

from data import *
from constants import *
from data import *
from eval import *
from extractor import *

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description='Currency exchange extraction.\nData files should contain one line per file, with the filename of the raw version followed by whitespace and the filename of the annotated version.')
    parser.add_argument('--train_data', help='Data for training')
    parser.add_argument('--eval_data', help='Data for evaluation')
    parser.add_argument('--run_data', help='Data to run on')
    parser.add_argument('--train_model', help='Model type to train', choices=["fixed-order", "pattern", "classifier", "global-single", "global-multi", "all"])
    parser.add_argument('--output_prefix', help='Prefix for output files')
    parser.add_argument('--save_prefix', help='Prefix for model files to be saved')
    parser.add_argument('--load_model', help='Filename of model to load')
    args = parser.parse_args()

    if args.output_prefix is not None:
        logging.basicConfig(filename=args.output_prefix +".out", level=logging.DEBUG)
    else:
        logging.basicConfig(level=logging.DEBUG)

    train_data = []
    if args.train_data is not None:
        for raw_file, gold_file in get_filenames(args.train_data):
            train_data.append(read(raw_file, gold_file))
    eval_data = []
    if args.eval_data is not None:
        for raw_file, gold_file in get_filenames(args.eval_data):
            eval_data.append(read(raw_file, gold_file))
    blank_data = []
    if args.run_data is not None:
        for raw_file, gold_file in get_filenames(args.run_data, False):
            blank_data.append(read(raw_file, gold_file))

    # Training

    systems = []
    if args.train_model in ["all", "fixed-order"]:
        systems.append(Baseline_Fixed_Word_Order())
    if args.train_model in ["all", "pattern"]:
        systems.append(Pattern_Extractor())
    if args.train_model in ["all", "classifier"]:
        systems.append(Classifier_Extractor())
    if args.train_model in ["all", "global-single"]:
        systems.append(Global_Extractor())
    if args.train_model in ["all", "global-multi"]:
        systems.append(Linked_Global_Extractor())

    if args.load_model is not None:
        data = open(args.load_model, "rb")
        systems.append(pickle.load(data))
        data.close()

    if len(train_data) > 0:
        for system in systems:
            logging.info("Training %s", system.name)
            system.train(train_data)
            if args.save_prefix is not None:
                out = open(args.save_prefix +"."+ system.name, "wb")
                pickle.dump(system, out)
                out.close()

    # Eval

    eval_out = sys.stdout
    if args.output_prefix is not None:
        eval_out = open(args.output_prefix +".eval", "w")

    if len(eval_data) > 0:
        log_details = True

        overall_scores = {} 
        for field in ['all', 'currency', 'amount', 'rate']:
            for unique in [True, False]:
                for system in systems:
                    overall_scores[system.name, field, unique] = {
                        'missing': 0,
                        'extra': 0,
                        'match': 0,
                        'count': 0,
                        'complete': 0,
                        'reversed': 0,
                        'have-want': 0,
                        'want-have': 0,
                        'gold-curr': 0,
                    }

        for datum in eval_data:
            text = datum.text
            gold = datum.gold
            if log_details:
                to_print = ["==============================================="]
                prev_blank = False
                for tokens in text:
                    line = ' '.join(tokens)
                    if line.strip() == "" and prev_blank:
                        continue
                    to_print.append(line.strip())
                    if line.strip() == "":
                        prev_blank = True
                    else:
                        prev_blank = False
                logging.info("\n".join(to_print) +"\n")
            logging.info("{:<30} {}".format("Gold", gold.word_repr(text)))
            for index, system in enumerate(systems):
                guess = system.extract(text)
                for unique in [True, False]:
                    scores = score(guess, gold, text, unique)
                    complete_scores = overall_scores[system.name, 'all', unique]
                    for field in ['complete', 'have-want', 'want-have', 'gold-curr', 'reversed']:
                        complete_scores[field] += scores[field]
                    complete_scores['count'] += 1
                    curr_complete = True
                    for field in ['currency', 'amount', 'rate']:
                        overall_score_set = overall_scores[system.name, field, unique]
                        overall_score_set['count'] += 1
                        for count in ['missing', 'extra', 'match']:
                            if field == 'currency' and count != 'match' and scores[field][count] > 0:
                                curr_complete = False
                            overall_score_set[count] += scores[field][count]
                            overall_scores[system.name, 'all', unique][count] += scores[field][count]
                    if curr_complete:
                        overall_scores[system.name, 'currency', unique]['complete'] += 1
                if log_details:
                    logging.info("{:<30} {}".format(system.name, guess.word_repr(text)))
            logging.info("")

        summary_fmt = "{:>8} {:>5} {:>30} | {:>3} {:>3} {:>3} | {:>5.1f} {:>5.1f} {:>5.1f} | {:>5.1f} | {:>5.1f} | {:>5.1f}"
        heading_fmt = "{:>8} {:>5} {:>30} | {:>3} {:>3} {:>3} | {:>5} {:>5} {:>5} | {:>5} | {:>5} | {:>5}"
        print(heading_fmt.format("field", "uni", "system", "-", "+", "=", "P", "R", "F", "Rev", "Re2", "Complete"), file=eval_out)
        summaries = []
        for system_name, field, unique in overall_scores:
            scores = overall_scores[system_name, field, unique]
            p, r, f = 0, 0, 0
            if scores['match'] + scores['extra'] > 0:
                p = 100 * scores['match'] / (scores['match'] + scores['extra'])
            if scores['missing'] + scores['match'] > 0:
                r = 100 * scores['match'] / (scores['match'] + scores['missing'])
            if p + r > 0:
                f = 2 * p * r / (p + r)
            complete = 100 * scores['complete'] / scores['count']
            rev = 100
            if scores['gold-curr'] > 0:
                rev = 100 * (scores['have-want'] + scores['want-have']) / scores['gold-curr']
            rev2 = 100 * scores['reversed'] / scores['count']
            summary = summary_fmt.format(field, str(unique), system_name, scores['missing'], scores['extra'], scores['match'], p, r, f, rev, rev2, complete)
            summaries.append(summary)
        summaries.sort()
        for summary in summaries:
            print(summary, file=eval_out)

    # Run

    out = sys.stdout
    if args.output_prefix is not None:
        out = open(args.output_prefix +".labeled", "w")
    if len(blank_data) > 0:
        print("filename,system,output", file=out)
        for datum in blank_data:
            for system in systems:
                info = system.extract(datum.text)
                to_print = [
                    datum.filename, system.name, info.word_repr(datum.text)
                ]
                print(",".join(to_print), file=out)
    out.close()
