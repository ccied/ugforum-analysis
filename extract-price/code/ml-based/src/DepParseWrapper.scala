package edu.berkeley.nlp.depparse

import edu.stanford.nlp.util.StringUtils
import edu.stanford.nlp.parser.nndep.DependencyParser
import edu.stanford.nlp.io.RuntimeIOException
import java.io.PrintWriter
import edu.stanford.nlp.io.IOUtils
import edu.stanford.nlp.parser.nndep.Config
import java.io.IOException
import java.io.File
import edu.stanford.nlp.tagger.maxent.MaxentTagger
import edu.stanford.nlp.process.DocumentPreprocessor
import edu.stanford.nlp.ling.TaggedWord
import edu.stanford.nlp.util.Timing

object DepParseWrapper {
  
  
  def main(args: Array[String]) {
    val props = StringUtils.argsToProperties(args, new java.util.HashMap[String,java.lang.Integer]);
    val config = new Config(props);
    val encoding = config.tlp.getEncoding();
    val parser = new DependencyParser(props);
    // wsj and english appear to be very similar, same set of labels and only one difference
    // (PP attachment) on three sentences I looked at manually.
    parser.loadModelFile("edu/stanford/nlp/models/parser/nndep/wsj_SD.gz");
//    parser.loadModelFile("edu/stanford/nlp/models/parser/nndep/english_SD.gz");
//    parser.loadModelFile("edu/stanford/nlp/models/parser/nndep/PTB_CoNLL_params.txt.gz");
    
    val tagger = new MaxentTagger(config.tagger);
    val inDir = args(0)
    val outDir = args(1)
    val inDirFiles = new File(inDir).listFiles();
    for (file <- inDirFiles) {
      val fileName = file.getName()
      parseFile(config, tagger, parser, file.getAbsolutePath(), outDir + "/" + fileName)
    }
  }
  
  def parseFile(config: Config, tagger: MaxentTagger, parser: DependencyParser, inFile: String, outFile: String) {
    val input = IOUtils.readerFromString(inFile, config.tlp.getEncoding);
    val output = IOUtils.getPrintWriter(outFile, config.tlp.getEncoding);
    val preprocessor = new DocumentPreprocessor(input);
//    preprocessor.setSentenceFinalPuncWords(Array("\n"));
    preprocessor.setEscaper(config.escaper);
    // This does whatever's in config
    // This seems to let the parser decide
//    preprocessor.setSentenceDelimiter(null);
//    preprocessor.setSentenceFinalPuncWords(config.tlp.sentenceFinalPunctuationWords());
//    preprocessor.setSentenceDelimiter(config.sentenceDelimiter);
//    preprocessor.setTokenizerFactory(config.tlp.getTokenizerFactory());
    // N.B. This triggers newlines to be ends of sentences rather than letting the parser decide
    preprocessor.setSentenceFinalPuncWords(Array("\n"));
    preprocessor.setSentenceDelimiter("\n");
    preprocessor.setTokenizerFactory(null); // use whitespace tokenization since everything is pretokenized
    val tagged = new java.util.ArrayList[java.util.List[TaggedWord]]();
    val sentItr = preprocessor.iterator()
    val timer = new Timing();
    while (sentItr.hasNext) {
      tagged.add(tagger.tagSentence(sentItr.next))
    }
    System.err.println("Tagging completed in " + (timer.stop() / 1000.0) + " sec");
    timer.start();
    
    var numSentences = 0;
    val taggedSentenceItr = tagged.iterator();
    while (taggedSentenceItr.hasNext) {
      val sent = taggedSentenceItr.next
//      System.err.println(sent)
      val parse = parser.predict(sent);
      val deps = parse.typedDependencies();
      val depsItr = deps.iterator();
      // N.B. For standard (non-Stanford) dependencies, they always come out in order
      var i = 0;
      while (depsItr.hasNext) {
        val dep = depsItr.next
//        System.err.println(dep)
        val word = sent.get(i).word();
        val pos = sent.get(i).tag();
        require(!word.contains(" ") && !pos.contains(" "), "Bad word or POS, contains space: word=" + word + " tag=")
        output.println((i+1) + "\t" + word + "\t_\t_\t" + pos + "\t_\t" + dep.gov().index() + "\t" + dep.reln() + "\t_\t_")
        i += 1
      }
      output.println()
      numSentences += 1
    }
    val millis = timer.stop();
    val seconds = millis / 1000.0;
    System.err.println("Parsed " + numSentences + " sentences in " + seconds + " seconds (" + (numSentences / seconds) + " sentences/sec)")
  }

}