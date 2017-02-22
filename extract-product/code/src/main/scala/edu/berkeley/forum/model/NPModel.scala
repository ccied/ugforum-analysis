package edu.berkeley.forum.model

import edu.berkeley.forum.data.NPDocument
import edu.berkeley.forum.data.LabeledNPDocument
import edu.berkeley.forum.eval.EvalStats
import edu.berkeley.nlp.futile.ClassifyUtils
import edu.berkeley.nlp.futile.util.Logger
import scala.collection.mutable.HashSet
import edu.berkeley.nlp.futile.util.Counter
import edu.berkeley.nlp.futile.Util
import scala.collection.JavaConverters._
import edu.berkeley.forum.data.ProductClusterer
import edu.berkeley.forum.data.TaggedProduct

trait NPModel {
  
  def predict(datum: NPDocument): LabeledNPDocument;
  
  def adapt(newLabeledData: Option[Seq[LabeledNPDocument]], newUnlabeledData: Option[Seq[NPDocument]]);
  
  def evaluate(data: Seq[LabeledNPDocument], writeOutputForSig: Boolean): EvalStats = {
    evaluate(data, data.map(doc => predict(doc.doc)), writeOutputForSig)
  }
  
  def evaluate(data: Seq[LabeledNPDocument], predictions: Seq[LabeledNPDocument], writeOutputForSig: Boolean): EvalStats = {
    var tp = 0.0F
    var fp = 0.0F
    var fn = 0.0F
    for (i <- 0 until data.size) {
      var thisCorr = 0.0f
      var thisPred = 0.0f
      var thisGold = 0.0f
      val datum = data(i)
      val prediction = predictions(i)
      for (i <- 0 until prediction.labels.size) {
        for (j <- 0 until prediction.labels(i).size) {
          if (prediction.labels(i)(j) && datum.labels(i)(j)) {
            tp += 1
            thisCorr += 1
          } else if (prediction.labels(i)(j)) {
            fp += 1
          } else if (datum.labels(i)(j)) {
            fn += 1
          }
          if (prediction.labels(i)(j)) {
            thisPred += 1
          }
          if (datum.labels(i)(j)) {
            thisGold += 1
          }
        }
      }
      if (writeOutputForSig) {
        Logger.logss("EVALTOK-" + datum.doc.doc.forumName + ": " + thisCorr + " " + thisPred + " " + thisGold)
      }
    }
    new EvalStats(tp, fp, fn)
  }
  
  def evaluateDocumentLevel(data: Seq[LabeledNPDocument], predictions: Seq[LabeledNPDocument], writeOutputForSig: Boolean): (Double, String) = {
    var correct = 0
    var numFalseNulls = 0
    var numTrueNulls = 0
    for (i <- 0 until data.size) {
      val datum = data(i)
      val prediction = predictions(i)
      // Things that predict multiple products just end up with the first product
      val rawPredictedLabels = prediction.getPositiveLabels
      val predictedLabels = if (rawPredictedLabels.size > 1) rawPredictedLabels.slice(0, 1) else rawPredictedLabels
//      require(predictedLabels.size <= 1, "Too many predicted labels!")
      val goldLabels = datum.getPositiveLabels
//      if (!goldLabels.contains(new FirstNPPredictor().predict(datum.doc).getPositiveLabels.headOption.getOrElse((-1, -1)))) {
//        System.out.println("ERROR")
//        System.out.println(goldLabels)
//        System.out.println(datum.renderSentence(0, false)) 
//      }
      if (goldLabels.size == 0) {
        numTrueNulls += 1
      }
//      if (predictedLabels.size == 1) {
//        val np = predictedLabels(0)
//        val sentIndices = datum.doc.nps(np._1)(np._2)
//        val predNP = datum.doc.doc.lines.get(np._1).asScala.slice(sentIndices._1, sentIndices._3)
//        Logger.logss(datum.doc.doc.documentId + ": " + predNP.foldLeft("")(_ + " " + _))
//      } else {
//        Logger.logss(datum.doc.doc.documentId + ": NULL")
//      }
      val isCorrect = (predictedLabels.size == 0 && goldLabels.size == 0) || (goldLabels.toSet & predictedLabels.toSet).size >= 1;
      if (isCorrect) {
        correct += 1
      } else {
        if (predictedLabels.size == 0) {
          numFalseNulls += 1
        }
      }
      if (writeOutputForSig) {
        Logger.logss("EVALDOC-" + datum.doc.doc.forumName + ": " + (if (isCorrect) 1 else 0) + " 1")
      }
    }
    (correct.toDouble / data.size, "Correct: " + Util.renderNumerDenom(correct, data.size) + "; predicted " + numFalseNulls + " false nulls and the dataset actually contained " + numTrueNulls)
  }
  
  def evaluateDocumentLevelSemanticProducts(data: Seq[LabeledNPDocument], predictions: Seq[LabeledNPDocument], writeOutputForSig: Boolean): EvalStats = {
    var tp = 0.0F
    var fp = 0.0F
    var fn = 0.0F
    for (i <- 0 until data.size) {
      val datum = data(i)
      val prediction = predictions(i)
      val goldProducts = new HashSet[TaggedProduct]
      val predProducts = new HashSet[TaggedProduct]
      for (sentIdx <- 0 until datum.labels.size) {
        for (npIdx <- 0 until datum.labels(sentIdx).size) {
          val taggedProduct = datum.doc.getTaggedProduct(sentIdx, npIdx)
          if (datum.labels(sentIdx)(npIdx)) {
            goldProducts += taggedProduct
          }
          if (prediction.labels(sentIdx)(npIdx)) {
            predProducts += taggedProduct
          }
        }
      }
      val goldClusters = ProductClusterer.clusterProducts(goldProducts.toSet)
      val predClusters = ProductClusterer.clusterProducts(predProducts.toSet)
//      if (prediction.labels(i)(j) && datum.labels(i)(j)) {
//        tp += 1
//      } else if (prediction.labels(i)(j)) {
//        fp += 1
//      } else if (datum.labels(i)(j)) {
//        fn += 1
//      }
      var correctPredictions = 0
      for (predCluster <- predClusters) {
        if (ModelUtils.doesOverlap(predCluster, goldClusters)) {
          correctPredictions += 1
        }
      }
      var duplicateCorrectPredictions = 0
      for (goldCluster <- goldClusters) {
        if (ModelUtils.doesOverlap(goldCluster, predClusters)) {
          duplicateCorrectPredictions += 1
        }
      }
      if (duplicateCorrectPredictions != correctPredictions) {
        Logger.logss("MISMATCH IN EVALUATION: gold -> pred and pred -> gold return inconsistent numbers of semantic products")
        Logger.logss("gold -> pred: " + duplicateCorrectPredictions + ", pred -> gold: " + correctPredictions)
        Logger.logss("PRED: " + predClusters)
        Logger.logss("GOLD: " + goldClusters)
      }
      tp += correctPredictions
      fp += predClusters.size - correctPredictions
      fn += goldClusters.size - correctPredictions
      if (writeOutputForSig) {
        Logger.logss("EVALTYPE-" + datum.doc.doc.forumName + ": " + correctPredictions + " " + predClusters.size + " " + goldClusters.size)
      }
    }
    new EvalStats(tp, fp, fn)
  }
  
  def evaluateTypeLevelPrintResults(data: Seq[LabeledNPDocument]) {
    var numWithOneCorrect = 0
    var numHeadsCorrect = 0
    var totalNumHeadsPred = 0
    var totalNumHeadsGold = 0
    var totalGoldLabels = 0
    val topMissedHeads = new Counter[String]
    val goldHeads = new Counter[String]
    val predHeads = new Counter[String]
    for (datum <- data) {
      val prediction = predict(datum.doc)
      var isOneRight = false
      val allPredHeads = new HashSet[String]
      val allGoldHeads = new HashSet[String]
      for (i <- 0 until prediction.labels.size) {
        for (j <- 0 until prediction.labels(i).size) {
          val np = datum.doc.nps(i)(j)
          val head = datum.doc.doc.lines.get(i).get(np._2)
          if (prediction.labels(i)(j) && datum.labels(i)(j)) {
            isOneRight = true
          }
          if (prediction.labels(i)(j)) {
            allPredHeads += head
            predHeads.incrementCount(head, 1.0)
          }
          if (datum.labels(i)(j)) {
            allGoldHeads += head
            goldHeads.incrementCount(head, 1.0)
            totalGoldLabels += 1
          }
        }
      }
      if (isOneRight) numWithOneCorrect += 1
      numHeadsCorrect += (allPredHeads & allGoldHeads).size
      totalNumHeadsPred += allPredHeads.size
      totalNumHeadsGold += allGoldHeads.size
      for (head <- (allGoldHeads -- allPredHeads)) {
        topMissedHeads.incrementCount(head, 1.0)
      }
    }
    Logger.logss("Number of posts with at least one right: " + ClassifyUtils.renderNumerDenom(numWithOneCorrect, data.size))
    Logger.logss("Average products per post: " + ClassifyUtils.renderNumerDenom(totalGoldLabels, data.size))
    Logger.logss("Type-level precision/recall/F1 on heads: " + ClassifyUtils.renderPRF1(numHeadsCorrect.toInt, totalNumHeadsPred.toInt, totalNumHeadsGold.toInt))
    Logger.logss("Top missed heads:")
    val pq = topMissedHeads.asPriorityQueue()
    var numPrinted = 0
    while (pq.hasNext && numPrinted < 10) {
      val key = pq.next()
      Logger.logss(key + ": " + topMissedHeads.getCount(key))
      numPrinted += 1
    }
    goldHeads.pruneKeysBelowThreshold(2.0)
    predHeads.pruneKeysBelowThreshold(2.0)
    val goldOrderStatistics = new Counter[String]
    val goldItr = goldHeads.asPriorityQueue
    var i = 1
    while (goldItr.hasNext) {
      goldOrderStatistics.setCount(goldItr.next, i)
      i += 1
    }
    val predOrderStatistics = new Counter[String]
    val predItr = predHeads.asPriorityQueue
    i = 1
    while (predItr.hasNext) {
      predOrderStatistics.setCount(predItr.next, i)
      i += 1
    }
    
    def render(count: Double, os: Double) = if (count == 0.0) "-" else count + " (" + os + ")" 
    Logger.logss("GOLD HEADS")
    val goldItr2 = goldHeads.asPriorityQueue
    while (goldItr2.hasNext) {
      val head = goldItr2.next
      Logger.logss(head + ": gold: " + render(goldHeads.getCount(head), goldOrderStatistics.getCount(head)) +
                          "; pred: " + render(predHeads.getCount(head), predOrderStatistics.getCount(head)))
    }
    Logger.logss("PRED HEADS")
    val predItr2 = predHeads.asPriorityQueue
    while (predItr2.hasNext) {
      val head = predItr2.next
      Logger.logss(head + ": gold: " + render(goldHeads.getCount(head), goldOrderStatistics.getCount(head)) +
                   "); pred: " + render(predHeads.getCount(head), predOrderStatistics.getCount(head)))
    }
  }
}
