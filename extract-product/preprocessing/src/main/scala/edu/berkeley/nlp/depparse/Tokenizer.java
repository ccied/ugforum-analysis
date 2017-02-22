package edu.berkeley.nlp.depparse;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;

import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;

public class Tokenizer {
  public static void main(String[] args) throws IOException {
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize, ssplit");
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

    Charset encoding = Charset.forName("UTF-8");
    File dirToHandle = new File(args[0]);
    String outputDir = args[1];
    for (File file: dirToHandle.listFiles()) {
      try {
        PrintWriter writer = new PrintWriter(outputDir + "/" + file.getName(), "UTF-8");
        System.err.println(file.getName());
        for (String line : Files.readAllLines(Paths.get(file.getAbsolutePath()), encoding)) {
          Annotation document = new Annotation(line);
          pipeline.annotate(document);
          List<CoreMap> sentences = document.get(SentencesAnnotation.class);
          for(CoreMap sentence: sentences) {
            for (CoreLabel token: sentence.get(TokensAnnotation.class)) {
              writer.print(token.get(TextAnnotation.class));
              writer.print(" ");
            }
            writer.println();
          }
          writer.println();
        }
        writer.close();
      } catch (IOException e) {
        System.out.println(e);
      }
    }
  }
}