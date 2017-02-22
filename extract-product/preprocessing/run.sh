#!/bin/bash

corenlpversion=stanford-english-corenlp-2016-10-31-models.jar
if [ ! -f $corenlpversion ]; then
  wget http://nlp.stanford.edu/software/stanford-english-corenlp-2016-10-31-models.jar
fi
java -cp target/scala-2.11/extract-product-preprocessing-assembly-1.jar edu.berkeley.nlp.depparse.Tokenizer input-raw output-tokenized
java -cp target/scala-2.11/extract-product-preprocessing-assembly-1.jar:stanford-english-corenlp-2016-10-31-models.jar edu.berkeley.nlp.depparse.DepParseWrapper output-tokenized output-parsed
