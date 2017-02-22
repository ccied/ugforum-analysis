package edu.berkeley.forum.data;

import java.io.BufferedReader;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.berkeley.forum.model.DocumentPosition;
import tuple.Pair;
import edu.berkeley.nlp.futile.fig.basic.IOUtils;
import edu.berkeley.nlp.futile.util.Counter;
import edu.berkeley.nlp.futile.util.Logger;
import fileio.f;

public class Dataset {
	
	public static class Document {
	  public String documentId;
	  public String forumName;
		public List<List<String>> lines;
		public List<List<List<String>>> linesConll;
		public List<List<String>> lines_pos;
		public Document(String documentId, String forumName, List<List<String>> lines, List<List<List<String>>> linesConll) {
		  this.documentId = documentId;
		  this.forumName = forumName;
			this.lines = lines;
			this.linesConll = linesConll;
			this.lines_pos = new ArrayList<List<String>>();
			for (List<List<String>> sentence : linesConll) {
			  for (List<String> word : sentence) {
			    lines_pos.add(word);
			  }
			}
		}
		
		public int getParentIdx(int sentIdx, int tokIdx) {
		  return Integer.parseInt(linesConll.get(sentIdx).get(tokIdx).get(6)) - 1;
    }
		
		private Counter<String> cachedDocumentNounsAndVerbs = null;
		
		public Counter<String> countDocumentNounsAndVerbs() {
		  if (cachedDocumentNounsAndVerbs == null) {
		    cachedDocumentNounsAndVerbs = new Counter<String>();
		    for (int sentIdx = 0; sentIdx < lines.size(); sentIdx++) {
		      for (int wordIdx = 0; wordIdx < lines.get(sentIdx).size(); wordIdx++) {
		        String tag = linesConll.get(sentIdx).get(wordIdx).get(4);
		        if (tag.startsWith("N") || tag.startsWith("V")) {
		          String stemmedLcWord = ProductClusterer.dropLongestSuffix(lines.get(sentIdx).get(wordIdx).toLowerCase());
		          cachedDocumentNounsAndVerbs.incrementCount(stemmedLcWord, 1.0);
		        }
		      }
		    }
		  }
		  return cachedDocumentNounsAndVerbs;
		}
	}
	
	public static class LabeledDocument {
		public Document document;
		public List<Pair<Integer,Integer>> positiveLabels;
		// Used to track annotator disagreement and consensus
		public Counter<Pair<Integer,Integer>> rawPositiveLabelCounts;
		
		// Hack used for caching in document-level model
		public int[] cachedNoneFeatures = null;
		public DocumentPosition[][] documentPositions;
		public int[][][][] cachedSentFeats;

    public LabeledDocument(Document document, List<Pair<Integer,Integer>> positiveLabels) {
      this.document = document;
      this.positiveLabels = positiveLabels;
      Counter<Pair<Integer,Integer>> labelCounts = new Counter<Pair<Integer,Integer>>();
      for (Pair<Integer,Integer> label : positiveLabels) {
        labelCounts.setCount(label, 1.0);
      }
      initializeDocPositions();
    }
		
		public LabeledDocument(Document document, List<Pair<Integer,Integer>> positiveLabels, Counter<Pair<Integer,Integer>> rawPositiveLabelCounts) {
			this.document = document;
			this.positiveLabels = positiveLabels;
			this.rawPositiveLabelCounts = rawPositiveLabelCounts;
			initializeDocPositions();
		}
		
		private void initializeDocPositions() {
      this.documentPositions = new DocumentPosition[document.lines.size()][];
      for (int i = 0; i < document.lines.size(); i++) {
        this.documentPositions[i] = new DocumentPosition[document.lines.get(i).size()];
        for (int j = 0; j < document.lines.get(i).size(); j++) {
          this.documentPositions[i][j] = new DocumentPosition(document, i, j, false);
        }
      }
		}
		
		public String renderLabeledSentence(int sentIdx) {
		  StringBuffer str = new StringBuffer("");
		  for (int i = 0; i < document.lines.get(sentIdx).size(); i++) {
		    String currWord = document.lines.get(sentIdx).get(i);
		    if (positiveLabels.contains(Pair.makePair(new Integer(sentIdx), new Integer(i)))) {
		      str.append("[").append(currWord).append("]");
		    } else {
		      str.append(currWord);
		    }
		    str.append(" ");
		  }
		  return str.toString().trim();
		}
	}
	
	//public Pair<List<LabeledDocument>,List<LabeledDocument>> train;
	public List<LabeledDocument> train;
	public List<LabeledDocument> dev;
	public List<LabeledDocument> test;
	
	private static int numNotMatched = 0;
	private static int totalAnnots = 0;
	
	public Dataset(List<LabeledDocument> train, List<LabeledDocument> dev, List<LabeledDocument> test) {
	  this.train = train;
	  this.dev = dev;
	  this.test = test;
	}
	
//	public static Dataset loadDataset(String dataPath, String dataPath_POS, String filePrefix, String fileSuffix, String labelsPath, 
//	                                  String annotator, int numTrain, double devFrac, double testFrac, String fileSuffix_POS) {
//	  Map<String,List<Pair<Integer,Integer>>> documentIdToPositiveCharSpans = readAnnotations(labelsPath, annotator);
//	  List<LabeledDocument> labeledData = assembleDocuments(Collections.singletonList(dataPath), Collections.singletonList(dataPath_POS), filePrefix, fileSuffix, fileSuffix_POS, documentIdToPositiveCharSpans);
//
//	  Collections.shuffle(labeledData, new Random(2));
//
//	  //train = labeledData.subList(0, (int) Math.floor((1.0 - (devFrac+testFrac)) * labeledData.size()));
//	  List<LabeledDocument> train = labeledData.subList(0, (int) Math.floor((1.0 - (devFrac+testFrac))* labeledData.size()));
//
//	  //train = labeledData.subList(0, Math.min(numTrain, (int) Math.floor((1.0 - (devFrac+testFrac)) * labeledData.size())));
//	  List<LabeledDocument> dev = labeledData.subList((int) Math.floor((1.0 - (devFrac+testFrac)) * labeledData.size()), (int) Math.floor((1.0 - (testFrac)) * labeledData.size()));
//	  List<LabeledDocument> test = labeledData.subList((int) Math.floor((1.0 - (testFrac)) * labeledData.size()), labeledData.size());
//
//	  System.out.println("Train:");
//	  printDatasetStats(train);
//	  System.out.println("Dev:");
//	  printDatasetStats(dev);
//	  System.out.println("Test:");
//	  printDatasetStats(test);
//	  return new Dataset(train, dev, test);
//	}

  
  public static Dataset loadDatasetNew(List<String> dataPaths, List<String> dataPathsPOS, String trainAnnotsPath, String devAnnotsPath, String testAnnotsPath, String filePrefix,
                                       String fileSuffix, String fileSuffix_POS, String forumName, String annotator) {
    Map<String,Counter<Pair<Integer,Integer>>> trainAnnots = readAnnotations(trainAnnotsPath, annotator);
    Map<String,Counter<Pair<Integer,Integer>>> devAnnots = readAnnotations(devAnnotsPath, annotator);
    Map<String,Counter<Pair<Integer,Integer>>> testAnnots = readAnnotations(testAnnotsPath, annotator);

//    List<LabeledDocument> train = assembleDocuments(dataPaths, dataPathsPOS, filePrefix, fileSuffix, fileSuffix_POS, forumName, trainAnnots, true);
//    List<LabeledDocument> dev = assembleDocuments(dataPaths, dataPathsPOS, filePrefix, fileSuffix, fileSuffix_POS, forumName, devAnnots, true);
//    List<LabeledDocument> test = assembleDocuments(dataPaths, dataPathsPOS, filePrefix, fileSuffix, fileSuffix_POS, forumName, testAnnots, true);

    // The reason we use a threshold of 1 here is that majority voting is already pre-done
    List<LabeledDocument> train = assembleDocuments(dataPaths, dataPathsPOS, filePrefix, fileSuffix, fileSuffix_POS, forumName, trainAnnots, 1);
    List<LabeledDocument> dev = assembleDocuments(dataPaths, dataPathsPOS, filePrefix, fileSuffix, fileSuffix_POS, forumName, devAnnots, 1);
    List<LabeledDocument> test = assembleDocuments(dataPaths, dataPathsPOS, filePrefix, fileSuffix, fileSuffix_POS, forumName, testAnnots, 1);

    
    System.out.println("Train:");
    printDatasetStats(train);
    System.out.println("Dev:");
    printDatasetStats(dev);
    System.out.println("Test:");
    printDatasetStats(test);
    return new Dataset(train, dev, test);
  }
  
  public static List<LabeledDocument> loadTestDatasetNew(List<String> dataPaths, List<String> dataPathsPOS, String annotsPath, String filePrefix,
                                                         String fileSuffix, String fileSuffix_POS, String forumName, int annotThreshold) {
    Map<String,Counter<Pair<Integer,Integer>>> annots = readAndMergeAnnotationsNew(annotsPath);
    List<LabeledDocument> test = assembleDocuments(dataPaths, dataPathsPOS, filePrefix, fileSuffix, fileSuffix_POS, forumName, annots, annotThreshold);
    System.out.println("Test:");
    printDatasetStats(test);
    return test;
  }
  
  public static List<Document> loadDatasetNoAnnots(String dataPath, String dataPathPOS, String filePrefix, String fileSuffix, String fileSuffix_POS, String forumName) {
    return loadDatasetNoAnnots(Collections.singletonList(dataPath), Collections.singletonList(dataPathPOS), filePrefix, fileSuffix, fileSuffix_POS, forumName);
  }
  
  public static List<Document> loadDatasetNoAnnots(List<String> dataPaths, List<String> dataPathsPOS, String filePrefix, String fileSuffix, String fileSuffix_POS, String forumName) {
    List<LabeledDocument> test = new ArrayList<LabeledDocument>();
    for (String dataPath: dataPaths) {
      Logger.logss(dataPath);
      try {
        new File(dataPath).listFiles();
      } catch (Exception e) {
        throw new RuntimeException("Problem with " + dataPath + ": " + e.toString());
      }
      for (File file: new File(dataPath).listFiles()) {
        String name = file.getName();
        if (name.startsWith(filePrefix) && name.endsWith(fileSuffix)) {
          String id = name.substring(filePrefix.length(), name.length() - fileSuffix.length());
          String docPath = dataPath + "/"+filePrefix+id+fileSuffix;
          String docPathPOS = "";
          for (String dataPathPOS: dataPathsPOS) {
            String tmpPath = dataPathPOS + "/"+filePrefix+id+fileSuffix_POS;
            if (new File(tmpPath).exists()) {
              docPathPOS = tmpPath;
            }
          }
          test.add(buildLabeledDocument(id, forumName, docPath, docPathPOS, new Counter<Pair<Integer,Integer>>(), 0));
        }
      }
    }
//    Map<String,Counter<Pair<Integer,Integer>>> nullAnnots = new HashMap<String,Counter<Pair<Integer,Integer>>>();
//    List<LabeledDocument> test = assembleDocuments(dataPaths, dataPathsPOS, filePrefix, fileSuffix, fileSuffix_POS, nullAnnots, false);
    List<Document> documents = new ArrayList<Document>();
    for (LabeledDocument testDoc: test) {
      documents.add(testDoc.document);
    }
    System.out.println("Unlabeled documents:");
    printDatasetStats(test);
    return documents;
  }
  
  /**
   * Load from just CoNLL-formatted files (no raw text)
   * @param dataPath
   * @param dataPathPOS
   * @param filePrefix
   * @param fileSuffix
   * @param fileSuffix_POS
   * @param forumName
   * @return
   */
  public static List<Document> loadDatasetNoAnnots(String dataPathPOS, String filePrefix, String fileSuffix_POS, String forumName) {
    return loadDatasetNoAnnots(Collections.singletonList(dataPathPOS), filePrefix, fileSuffix_POS, forumName);
  }
  
  public static List<Document> loadDatasetNoAnnots(List<String> dataPathsPOS, String filePrefix, String fileSuffix_POS, String forumName) {
    List<Document> documents = new ArrayList<Document>();
    for (String dataPathPOS: dataPathsPOS) {
      Logger.logss(dataPathPOS);
      try {
        new File(dataPathPOS).listFiles();
      } catch (Exception e) {
        throw new RuntimeException("Problem with " + dataPathPOS + ": " + e.toString());
      }
      for (File file: new File(dataPathPOS).listFiles()) {
        String name = file.getName();
        if (name.startsWith(filePrefix) && name.endsWith(fileSuffix_POS)) {
          String id = name.substring(filePrefix.length(), name.length() - fileSuffix_POS.length());
          String docPathPOS = dataPathPOS + "/"+filePrefix+id+fileSuffix_POS;
          documents.add(buildUnlabeledDocument(id, forumName, docPathPOS));
        }
      }
    }
    return documents;
  }
  
  private static Map<String,Counter<Pair<Integer,Integer>>> readAnnotations(String annotsPath, String annotator) {
    List<String> labelsLines = f.readLines(annotsPath);
    Map<String,Counter<Pair<Integer,Integer>>> documentIdToPositiveCharSpans = new HashMap<String,Counter<Pair<Integer,Integer>>>();
    for (String labelLine : labelsLines) {
      String[] labelSplit = labelLine.split("\\s+");
      String labelDocumentId = labelSplit[0];
      String labelAnnotator = labelSplit[1];
      int labelStartCharIndex = Integer.parseInt(labelSplit[2]);
      int labelStopCharIndex = Integer.parseInt(labelSplit[3]);
      if (labelAnnotator.equals(annotator)) {
        Counter<Pair<Integer,Integer>> positiveCharSpans = documentIdToPositiveCharSpans.get(labelDocumentId);
        if (positiveCharSpans == null) {
          positiveCharSpans = new Counter<Pair<Integer,Integer>>();
          documentIdToPositiveCharSpans.put(labelDocumentId, positiveCharSpans);
        }
        positiveCharSpans.incrementCount(Pair.makePair(labelStartCharIndex, labelStopCharIndex), 1.0);
      }
    }
    return documentIdToPositiveCharSpans;
  }
  
  private static Map<String,Counter<Pair<Integer,Integer>>> readAndMergeAnnotationsNew(String annotsPath) {
    List<String> labelsLines = f.readLines(annotsPath);
    Map<String,Counter<Pair<Integer,Integer>>> documentIdToPositiveCharSpans = new HashMap<String,Counter<Pair<Integer,Integer>>>();
    for (String labelLine : labelsLines) {
      String[] labelSplit = labelLine.split("\\s+");
      String rawDocumentName = labelSplit[0];
      String labelDocumentId = rawDocumentName.replace("0-initiator", "").replace(".txt.tok", "");
      int labelStartCharIndex = -1, labelStopCharIndex = -1;
      if (labelSplit.length == 4) { 
        labelStartCharIndex = Integer.parseInt(labelSplit[2]);
        labelStopCharIndex = Integer.parseInt(labelSplit[3]);
      } else if (labelSplit.length >= 6) {
        labelStartCharIndex = Integer.parseInt(labelSplit[2]);
        labelStopCharIndex = Integer.parseInt(labelSplit[4]);
      } else {
        throw new RuntimeException("Bad line in annotations file:\n" + labelLine + "\nhas " + labelSplit.length + " fields");
      }
      Counter<Pair<Integer,Integer>> positiveCharSpans = documentIdToPositiveCharSpans.get(labelDocumentId);
      if (positiveCharSpans == null) {
        positiveCharSpans = new Counter<Pair<Integer,Integer>>();
        documentIdToPositiveCharSpans.put(labelDocumentId, positiveCharSpans);
      }
      positiveCharSpans.incrementCount(Pair.makePair(labelStartCharIndex, labelStopCharIndex), 1.0);
    }
    // Now add everything that is agreed on by at least 2 annotators
    return documentIdToPositiveCharSpans;
//    Map<String,List<Pair<Integer,Integer>>> documentIdToPositiveCharSpansFinal = new HashMap<String,List<Pair<Integer,Integer>>>();
//    for (String docId : documentIdToPositiveCharSpans.keySet()) {
//      Counter<Pair<Integer,Integer>> charSpans = documentIdToPositiveCharSpans.get(docId);
//      List<Pair<Integer,Integer>> finalList = new ArrayList<Pair<Integer,Integer>>();
//      for (Pair<Integer,Integer> uniqueSpan : charSpans.keySet()) {
//        if (doUnion || charSpans.getCount(uniqueSpan) >= 2) {
////          Logger.logss("Keeping " + docId + " " + uniqueSpan + " with count " + count);
//          finalList.add(uniqueSpan);
//        }
//      }
//      Collections.sort(finalList, new Comparator<Pair<Integer,Integer>>() {
//        @Override
//        public int compare(Pair<Integer, Integer> o1, Pair<Integer, Integer> o2) {
//          int first = o1.getFirst() - o2.getFirst();
//          if (first == 0) {
//            return o1.getSecond() - o2.getSecond();
//          } else {
//            return first;
//          }
//        }
//      });
//      documentIdToPositiveCharSpansFinal.put(docId, finalList);
//    }
//    return documentIdToPositiveCharSpansFinal;
  }
  
  private static List<LabeledDocument> assembleDocuments(List<String> dataPaths, List<String> dataPathsPOS, String filePrefix, String fileSuffix, String fileSuffix_POS, String forumName, Map<String,Counter<Pair<Integer,Integer>>> annotations, int annotThreshold) {
    List<LabeledDocument> labeledData = new ArrayList<LabeledDocument>();
    //List<LabeledDocument> labeledData_POS = new ArrayList<LabeledDocument>(); 
    for (String documentId : annotations.keySet()) {
      String docPath = "";
      String docPathPOS = "";
      for (String dataPath: dataPaths) {
        String docPathTmp = dataPath + "/"+filePrefix+documentId+fileSuffix;
//        System.out.println(docPathTmp);
        if (new File(docPathTmp).exists()) {
          docPath = docPathTmp;
        }
      }
      for (String dataPathPOS: dataPathsPOS) {
        String docPathPOSTmp = dataPathPOS + "/"+filePrefix+documentId+fileSuffix_POS;
//        System.out.println(docPathPOSTmp);
        if (new File(docPathPOSTmp).exists()) {
          docPathPOS = docPathPOSTmp;
        }
      }
//      System.out.println(docPath);
//      System.out.println(docPathPOS);
      if (!docPath.isEmpty() && !docPathPOS.isEmpty()) {
        labeledData.add(buildLabeledDocument(documentId, forumName, docPath, docPathPOS, annotations.get(documentId), annotThreshold));
      } else {
        System.out.println("Couldn't find data for document: " + documentId);
      }
    }
    return labeledData;
  }
	
	
	private static void printDatasetStats(List<LabeledDocument> labeledData) {
		double numPos = 0.0;
		double numLines = 0.0;
		for (LabeledDocument labeledDoc : labeledData) {
			numPos += labeledDoc.positiveLabels.size();
			numLines += labeledDoc.document.lines.size();
//			System.out.println("pos words:");
//			for (Pair<Integer,Integer> positiveLabel : labeledDoc.positiveLabels) {
//				System.out.println(labeledDoc.document.lines.get(positiveLabel.getFirst()).get(positiveLabel.getSecond()));
//			}
		}
		System.out.println("Num documents: "+labeledData.size());
		System.out.printf("Avg lines per doc: %.1f%n",numLines/((double) labeledData.size()));
		System.out.printf("Avg pos words per doc: %.1f%n",numPos/((double) labeledData.size()));
		
	}
	  
	private static LabeledDocument buildLabeledDocument(String documentId, String forumName, String filePath, String filePathPOS, Counter<Pair<Integer,Integer>> positiveCharSpans, int annotThreshold) {
	  List<List<String>> lines = new ArrayList<>();
		List<String> rawLines = f.readLines(filePath);
    ///////////////////////
    // READ RAW FILE AND CONLL
		List<List<String>> rawSplitStrings = new ArrayList<List<String>>();
		List<Integer> spareTrailingWhitespace = new ArrayList<Integer>();
		for (String rawLine : rawLines) {
      List<String> lineWords = new ArrayList<String>();
		  if (rawLine.trim().isEmpty()) {
//		    lines.add(new ArrayList<String>());
//		    rawSplitStrings.add(new ArrayList<String>());
//		    spareTrailingWhitespace.add(rawLine.length() + 1);
		    // Add to the last line
		    if (spareTrailingWhitespace.size() == 0) {
		      throw new RuntimeException("ERROR: Document can't start with a blank line!");
		    }
		    spareTrailingWhitespace.set(spareTrailingWhitespace.size() - 1, spareTrailingWhitespace.get(spareTrailingWhitespace.size() - 1) + rawLine.length() + 1);
		  } else {
//        lineWords.addAll(Arrays.asList(rawLine.split("\\s+")));
		    lineWords.addAll(Arrays.asList(rawLine.split("(\\s|" + (char)160 + ")+")));
        lines.add(lineWords);
        // Remove an empty initial string (which can happen if the line starts with whitespace)
		    if (lineWords.get(0).isEmpty()) {
		      lineWords.remove(0);
		    }
		    List<String> rawSplitString = new ArrayList<String>();
		    int offset = 0;
		    for (int i = 0; i < lineWords.size() - 1; i++) {
		      int nextWordStart = rawLine.indexOf(lineWords.get(i+1), offset + lineWords.get(i).length());
		      String rawString = rawLine.substring(offset, nextWordStart);
//		      if (i == 0) {
//		        rawString = precedingWhitespace + rawString;
//		      }
		      offset = nextWordStart;
		      rawSplitString.add(rawString);
		    }
		    rawSplitString.add(rawLine.substring(offset));
		    if (rawSplitString.size() != lineWords.size()) {
		      throw new RuntimeException("Mismatched lengths! " + rawSplitString.size() + " " + lineWords.size());
		    }
		    int totalLen = 0;
		    for (int i = 0; i < rawSplitString.size(); i++) {
		      totalLen += rawSplitString.get(i).length();
		    }
		    if (totalLen != rawLine.length()) {
          throw new RuntimeException("Mismatched total line lengths! " + totalLen + " " + rawLine.length() + "\nLINE: " + rawLine + "\nWORDS: " + rawSplitString.toString());
        }
		    rawSplitStrings.add(rawSplitString);
		    spareTrailingWhitespace.add(1);
		  }
		}
		
		List<List<List<String>>> linesConll = new ArrayList<>();
		List<String> rawLines_POS = f.readLines(filePathPOS);
		List<List<String>> linesThisSentence = new ArrayList<List<String>>();
		for (String rawLine : rawLines_POS) {
		  if (rawLine.trim().isEmpty()) {
		    linesConll.add(linesThisSentence);
		    linesThisSentence = new ArrayList<List<String>>();
		  } else {
			  List<String> line = Arrays.asList(rawLine.split("\\s+"));
			  linesThisSentence.add(line);
		  }
		}
		if (linesThisSentence.size() > 0) {
		  linesConll.add(linesThisSentence);
		}
		
		// Check that the CoNLL tokenization is in sync with the raw tokenization
		if (lines.size() != linesConll.size()) {
		  throw new RuntimeException("Mismatched number of lines! " + documentId + " " + lines.size() + " " + linesConll.size());
		}
		for (int i = 0; i < lines.size(); i++) {
		  if (lines.get(i).size() != linesConll.get(i).size()) {
		    System.out.println("Mismatched line lengths! " + documentId + " " + lines.get(i).size() + " " + linesConll.get(i).size());
		    List<String> newWords = new ArrayList<String>();
		    for (int j = 0; j < linesConll.get(i).size(); j++) {
		      newWords.add(linesConll.get(i).get(j).get(1));
		    }
		    lines.set(i, newWords);
		    assert(lines.get(i).size() != linesConll.get(i).size());
		  }
		}

    ///////////////////////
    // READ ANNOTATIONS
		Counter<Pair<Integer,Integer>> positiveLabels = new Counter<Pair<Integer,Integer>>();
		if (!positiveCharSpans.isEmpty()) {
		  int charIndex = 0;
		  totalAnnots += positiveCharSpans.size();
		  for (int lineIndex=0; lineIndex<lines.size(); ++lineIndex) {
		    List<String> line = lines.get(lineIndex);
		    for (int wordIndex=0; wordIndex<line.size(); ++wordIndex) {
		      for (Pair<Integer,Integer> charSpan : positiveCharSpans.keySet()) {
		        if (charIndex >= charSpan.getFirst() && charIndex < charSpan.getSecond()) {
		          positiveLabels.setCount(Pair.makePair(lineIndex, wordIndex), positiveCharSpans.getCount(charSpan));
		          String goldWord = viewFile(filePath, charSpan.getFirst(), charSpan.getSecond(), false);
		          String word = lines.get(lineIndex).get(wordIndex);
		          // Contains is generally right because this might contain punctuation
		          if (!word.contains(goldWord.trim())) {
		            numNotMatched += 1;
		            //	            System.out.println(goldWord + " -- " + word);
		          }
		        }
		      }
		      charIndex += rawSplitStrings.get(lineIndex).get(wordIndex).length();
		    }
		    charIndex += spareTrailingWhitespace.get(lineIndex).intValue();
		  }
		}
		
		///////////////////////
		// FILTER POST LINES
		// We remove all empty lines, lines within vouches (<blockquote>), and lines that aren't
		// in the first 10 or last 10 nonempty lines in the document
		boolean[] linesToDelete = new boolean[lines.size()];
		int[] cumLinesToDelete = new int[lines.size()];
		boolean isInBlockQuote = false;
		int nonemptyLineIndex = 0;
		int totalNonemptyLines = 0;
		Set<Integer> posSents = new HashSet<Integer>();
		for (Pair<Integer,Integer> label : positiveLabels.keySet()) {
		  posSents.add(label.getFirst());
		}
		for (int i = 0; i < lines.size(); i++) {
		  if (!lines.get(i).isEmpty()) {
		    totalNonemptyLines += 1;
		  }
		}
		for (int i = 0; i < lines.size(); i++) {
		  if (lines.get(i).contains("<blockquote>")) {
		    isInBlockQuote = true;
		  }
		  boolean isLineEmpty = lines.get(i).isEmpty();
		  linesToDelete[i] = isLineEmpty || isInBlockQuote || (nonemptyLineIndex >= 10 && nonemptyLineIndex < totalNonemptyLines - 10);
//		  if (linesToDelete[i] && posSents.contains(i)) {
//		    System.out.println("WARNING2: Removed sentence with positive label! " + i + " " + nonemptyLineIndex + " " + totalNonemptyLines);
//		  }
		  if (!isLineEmpty) {
		    nonemptyLineIndex += 1;
		  }
		  cumLinesToDelete[i] = (i == 0 ? 0 : cumLinesToDelete[i-1]) + (linesToDelete[i] ? 1 : 0);
		  // Still deletes this line
      if (lines.get(i).contains("</blockquote>")) {
        isInBlockQuote = false;
      }
		}
//    for (int i = 0; i < lines.size(); i++) {
//      Logger.logss("Old line " + i + ": " + lines.get(i));
//    }
//    Logger.logss("Old: " + positiveLabels);
		int currIdx = 0;
//		int numDeleted = 0;
		for (int i = 0; i < linesToDelete.length; i++) {
		  if (linesToDelete[i]) {
		    lines.remove(currIdx);
		    linesConll.remove(currIdx);
//		    numDeleted += 1;
		  } else {
		    currIdx += 1;
		  }
		}
//		if (numDeleted != cumLinesToDelete[cumLinesToDelete.length - 1]) {
//		  Logger.logss("MISMATCH! " + numDeleted + " " + cumLinesToDelete[cumLinesToDelete.length - 1]);
//		}
//		List<Pair<Integer,Integer>> newPositiveLabels = new ArrayList<Pair<Integer,Integer>>();
		Counter<Pair<Integer,Integer>> newPositiveLabels = new Counter<Pair<Integer,Integer>>();
		for (Pair<Integer,Integer> label : positiveLabels.keySet()) {
		  int sentIdx = label.getFirst().intValue();
		  if (linesToDelete[sentIdx]) {
//		    System.out.println("WARNING: Removed sentence with positive label! " + sentIdx + " " + totalNonemptyLines);
		  } else {
		    newPositiveLabels.setCount(Pair.makePair(sentIdx - cumLinesToDelete[sentIdx], label.getSecond()), positiveLabels.getCount(label));
		  }
		}
//		for (int i = 0; i < lines.size(); i++) {
//      Logger.logss("New line " + i + ": " + lines.get(i));
//    }
//		Logger.logss("New: " + newPositiveLabels);
		
//		System.out.println("Filtered " + cumLinesToDelete[cumLinesToDelete.length - 1] + " lines");
		
//		System.out.println("------------------------");
//		System.out.println(lines.size() + " lines, " + lines_POS.size() + " pos lines");
//		for (List<String> line: lines) {
//		  System.out.println(line);
//		}
//		for (List<String> linePos: lines_POS) {
//		  System.out.println("POS: " + linePos.toString());
//    }
//		System.out.println(positiveLabels);
		
		// ORDER THE LIST
		List<Pair<Integer,Integer>> finalList = new ArrayList<Pair<Integer,Integer>>();
		for (Pair<Integer,Integer> uniqueSpan : newPositiveLabels.keySet()) {
		  if (newPositiveLabels.getCount(uniqueSpan) >= annotThreshold) {
		    //      Logger.logss("Keeping " + docId + " " + uniqueSpan + " with count " + count);
		    finalList.add(uniqueSpan);
		  }
		}
		Collections.sort(finalList, new Comparator<Pair<Integer,Integer>>() {
		  @Override
		  public int compare(Pair<Integer, Integer> o1, Pair<Integer, Integer> o2) {
		    int first = o1.getFirst() - o2.getFirst();
		    if (first == 0) {
		      return o1.getSecond() - o2.getSecond();
		    } else {
		      return first;
		    }
		  }
		});
		return new LabeledDocument(new Document(documentId, forumName, lines, linesConll), finalList, newPositiveLabels);
	}

  public static Document buildUnlabeledDocument(String documentId, String forumName, String filePathPOS) {
    List<List<List<String>>> linesConll = new ArrayList<>();
    List<String> rawLines_POS = f.readLines(filePathPOS);
    List<List<String>> linesThisSentence = new ArrayList<List<String>>();
    for (String rawLine : rawLines_POS) {
      if (rawLine.trim().isEmpty()) {
        linesConll.add(linesThisSentence);
        linesThisSentence = new ArrayList<List<String>>();
      } else {
        List<String> line = Arrays.asList(rawLine.split("\\s+"));
        linesThisSentence.add(line);
      }
    }
    if (linesThisSentence.size() > 0) {
      linesConll.add(linesThisSentence);
    }
    // Pull out the words
    List<List<String>> lines = new ArrayList<List<String>>();
    for (List<List<String>> sentence : linesConll) {
      List<String> sent = new ArrayList<String>();
      for (List<String> line : sentence) {
        sent.add(line.get(1));
      }
      lines.add(sent);
    }
    return new Document(documentId, forumName, lines, linesConll);
  }
	
	public static String viewFile(String path, int startCharIdx, int endCharIdx, boolean formatForOutput) {
    String fileStr = "";
    try {
      BufferedReader reader = IOUtils.openInHard(path);
      List<String> lines = IOUtils.readLines(path);
      for (String line: lines) {
        fileStr += line + "\n";
      }
      reader.close();
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
    if (formatForOutput) {
      return "(" + startCharIdx + ", " + endCharIdx + "): " + fileStr.substring(startCharIdx, endCharIdx);
    } else {
      return fileStr.substring(startCharIdx, endCharIdx);
    }
	}
	
	public static String renderMatches() {
	  return numNotMatched + " / " + totalAnnots + " spans unmatched";
	}

	
	public static void main(String[] args) {
	  String problem = "580" + ((char)160) + "800";
	  System.out.println(Arrays.asList(problem.split("\\s+")));
	  System.out.println(Arrays.asList(problem.split("(\\s|" + (char)160 + ")+")));
	}
}
