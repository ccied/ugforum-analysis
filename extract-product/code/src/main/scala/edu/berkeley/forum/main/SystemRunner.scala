package edu.berkeley.forum.main

import scala.collection.JavaConverters._
import edu.berkeley.nlp.futile.fig.basic.IOUtils
import edu.berkeley.forum.model.NPModel
import edu.berkeley.forum.data.Dataset
import edu.berkeley.forum.data.NPDocument
import edu.berkeley.nlp.futile.util.Logger
import edu.berkeley.forum.data.Dataset.LabeledDocument
import edu.berkeley.forum.data.Dataset.Document
import edu.berkeley.forum.data.ProductClusterer
import edu.berkeley.nlp.futile.util.Counter
import edu.berkeley.nlp.futile.LightRunner
import edu.berkeley.forum.dicts.ProductToClusterMap
import scala.collection.mutable.HashMap
import scala.collection.mutable.ArrayBuffer
import java.io.File
import java.io.PrintWriter

object SystemRunner {
  
  val modelPath = "models/bothmodel-doclevel.ser.gz"
  
  val maxNpLen = 7
  val includeVerbs = true
  val useNpMajority = false
  
  val inputDir = "../sample-data"
  val outputDir = "output/"
  
  val filePrefix = ""
  val fileSuffix = ""
  val doUnion = false
  val annotator = "merged"
  
  def main(args: Array[String]) {
    val outDir = new File(outputDir)
    if (outDir.mkdir()) {
      Logger.logss(outputDir + " created successfully")
    } else {
      Logger.logss(outputDir + " exists, overwriting")
    }
    LightRunner.initializeOutput(SystemRunner.getClass())
    LightRunner.populateScala(SystemRunner.getClass(), args)
    val system = IOUtils.readObjFileHard(modelPath).asInstanceOf[NPModel]
    val dataset = loadDatasetStreaming(inputDir, filePrefix, fileSuffix, "")
    runOnNewData(system, dataset)
    
    LightRunner.finalizeOutput()
  }
  
  val productsToSubdivide = Set("account", "accounts", "service", "email", "install", "bot")
  
  def runOnNewData(system: NPModel, data: Iterator[Document]) {
    val canonicalizedHeadCounts = new Counter[String]
    val canonicalizedFullProductCounts = new Counter[String]
    val csvWriter = IOUtils.openOutHard(outputDir + "/products.csv")
    while (data.hasNext) {
      val datum = data.next
      Logger.logss("Processing " + datum.documentId)
      val doc = NPDocument.createFromDocument(datum, maxNpLen, includeVerbs)
      val labeledDoc = system.predict(doc)
      for (predictedNp <- labeledDoc.getPositiveLabels) {
        val sentIdx = predictedNp._1
        val sent = labeledDoc.doc.doc.lines.get(sentIdx).asScala
        val positiveNp = labeledDoc.doc.nps(sentIdx)(predictedNp._2)
        val npFullText = sent.slice(positiveNp._1, positiveNp._3).foldLeft("")(_ + " " + _).trim.replace("\"", "")
        val npHeadText = sent(positiveNp._2).replace("\"", "")
        val canonicalizedHeadText = ProductClusterer.dropLongestSuffix(npHeadText.toLowerCase())
        val fullSemanticProduct = if (productsToSubdivide.contains(canonicalizedHeadText)) {
          val lcPreModifier = if (positiveNp._2 > 0) sent(positiveNp._2 - 1).toLowerCase else ""
          (lcPreModifier + " " + canonicalizedHeadText).trim
        } else {
          canonicalizedHeadText
        }
        canonicalizedHeadCounts.incrementCount(canonicalizedHeadText, 1.0)
        canonicalizedFullProductCounts.incrementCount(fullSemanticProduct, 1.0)
        val clusteredProductTypes = ProductToClusterMap.canonicalize(npFullText, fullSemanticProduct, canonicalizedHeadText)
        csvWriter.println(doc.doc.documentId + ",\"" + npFullText + "\",\"" + npHeadText + "\"," + canonicalizedHeadText + "," + fullSemanticProduct + "," + clusteredProductTypes._1 + "," + clusteredProductTypes._2)
      }
    }
    csvWriter.close()
    
    val headsWriter = IOUtils.openOutHard(outputDir + "/heads.txt")
    renderTopN(canonicalizedHeadCounts, 10000, headsWriter)
    val total = canonicalizedHeadCounts.totalCount
    headsWriter.println("Types of products: " + canonicalizedHeadCounts.keySet.size)
    headsWriter.close()
    
    // Display these to show to people for classification purposes
    val refinedHeadsWriter = IOUtils.openOutHard(outputDir + "/refined-heads.txt")
    val headToSemProductMap = new HashMap[String,ArrayBuffer[String]]
    for (key <- canonicalizedFullProductCounts.keySet.asScala) {
      if (key.contains(" ")) {
        val lastWord = key.substring(key.lastIndexOf(" ") + 1)
        if (!headToSemProductMap.contains(lastWord)) {
          headToSemProductMap += lastWord -> new ArrayBuffer[String]
        }
        headToSemProductMap(lastWord) += key
      }
    }
    val pq = canonicalizedHeadCounts.asPriorityQueue()
    while (pq.hasNext) {
      val headKey = pq.next
      refinedHeadsWriter.println(headKey + ": " + canonicalizedHeadCounts.getCount(headKey))
      if (headToSemProductMap.contains(headKey)) {
        for (subKey <- headToSemProductMap(headKey)) {
          refinedHeadsWriter.println("  " + subKey + ": " + canonicalizedFullProductCounts.getCount(subKey))
        } 
      }
    }
    refinedHeadsWriter.close()
  }
  
  def loadDatasetStreaming(dataPathPOS: String, filePrefix: String, fileSuffix_POS: String, forumName: String): Iterator[Document] = {
    new File(dataPathPOS).listFiles.toIterator.flatMap(file => {
      val name = file.getName
      if (name.startsWith(filePrefix) && name.endsWith(fileSuffix_POS)) {
        val id = name.substring(filePrefix.length(), name.length() - fileSuffix_POS.length())
        val docPathPOS = dataPathPOS + "/"+filePrefix+id+fileSuffix_POS
        Option(Dataset.buildUnlabeledDocument(id, forumName, docPathPOS))
      } else {
        None
      }
    })
  }
  
  def renderTopN(counter: Counter[String], n: Int) {
    renderTopN(counter, n, new PrintWriter(System.out))
  }
  
  def renderTopN(counter: Counter[String], n: Int, printWriter: PrintWriter) {
    val pq = counter.asPriorityQueue()
    for (i <- 0 until n) {
      if (pq.hasNext) {
        val item = pq.next
        printWriter.println(item + ": " + counter.getCount(item))
      }
    }
  }
  
  
  def evaluateBlackhat(system: NPModel, darkode: Dataset, hfTrainRaw: Seq[LabeledDocument]) {
    val trainSet = darkode.train.asScala ++ hfTrainRaw
    val trainProducts = trainSet.flatMap(doc => {
      val positiveLabels = doc.positiveLabels.asScala
      positiveLabels.map(pair => doc.document.lines.get(pair.getFirst().intValue).get(pair.getSecond().intValue)).toSet
    })
    val trainProductsLc = trainProducts.map(_.toLowerCase)
    
    
    val blackhatData = Dataset.loadDatasetNoAnnots("../data/blackhat/raw/pub-threads-all-separate/", "../greg_scratch/blackhat/",
                                                   "0-initiator", ".txt.tok", ".txt.tok", "blackhat")
    var numWithAtLeastOnePrediction = 0
    var totalNumPredictions = 0
    var totalNumNovelPredictions = 0
    for (datum <- blackhatData.asScala) {
      val doc = NPDocument.createFromDocument(datum, maxNpLen, includeVerbs)
      val labeledDoc = system.predict(doc)
      Logger.logss("=====================")
      val numPredictions = labeledDoc.labels.map(_.map(if (_) 1 else 0).foldLeft(0)(_ + _)).foldLeft(0)(_ + _)
      Logger.logss(doc.doc.documentId + " -- " + numPredictions + " PREDICTIONS")
      for (i <- 0 until doc.doc.lines.size()) {
        for (labelIdx <- 0 until labeledDoc.labels(i).size) {
          val prediction = labeledDoc.labels(i)(labelIdx)
          val head = labeledDoc.doc.doc.lines.get(i).get(labeledDoc.doc.nps(i)(labelIdx)._2)
          if (prediction) {
            totalNumPredictions += 1
            if (!trainProductsLc.contains(head.toLowerCase)) {
              totalNumNovelPredictions += 1
            }
          }
        }
        Logger.logss(labeledDoc.renderSentence(i, true))
      }
      if (numPredictions >= 1) {
        numWithAtLeastOnePrediction += 1
      }
    }
    Logger.logss("Predicted on " + blackhatData.size + " exes")
    Logger.logss(numWithAtLeastOnePrediction + " with at least one")
    Logger.logss(totalNumNovelPredictions + " / " + totalNumPredictions + " novel predictions")
  }
}
