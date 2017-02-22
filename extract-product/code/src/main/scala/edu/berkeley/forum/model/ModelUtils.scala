package edu.berkeley.forum.model

import edu.berkeley.forum.data.Dataset.LabeledDocument
import edu.berkeley.forum.eval.EvalStats
import scala.collection.JavaConverters._
import edu.berkeley.forum.data.ProductClusterer
import edu.berkeley.forum.data.LabeledNPDocument
import scala.collection.mutable.HashSet
import edu.berkeley.nlp.futile.util.Logger
import edu.berkeley.nlp.futile.Util
import edu.berkeley.forum.data.TaggedProduct
import edu.berkeley.forum.data.Dataset.Document

object ModelUtils {
  
  def evaluateTokenLevel(data: Seq[LabeledDocument], predictions: Seq[LabeledDocument], writeOutputForSig: Boolean): EvalStats = {
    var tp = 0.0F
    var fp = 0.0F
    var fn = 0.0F
    for (i <- 0 until data.size) {
      var thisCorr = 0.0f
      var thisPred = 0.0f
      var thisGold = 0.0f
      val goldLabels = data(i).positiveLabels.asScala.toSet
      val predLabels = predictions(i).positiveLabels.asScala.toSet
      val thisTp = (goldLabels & predLabels).size
      val thisFp = predLabels.size - thisTp
      val thisFn = goldLabels.size - thisTp
      thisCorr += thisTp
      thisPred += predLabels.size
      thisGold += goldLabels.size
      if (writeOutputForSig) {
        Logger.logss("EVALTOK-" + data(i).document.forumName + ": " + thisCorr + " " + thisPred + " " + thisGold)
      }
      tp += thisTp
      fp += thisFp
      fn += thisFn
    }
    new EvalStats(tp, fp, fn)
  }
  
  def evaluateDocumentLevel(data: Seq[LabeledDocument], predictions: Seq[LabeledDocument], writeOutputForSig: Boolean): (Double, String) = {
    var correct = 0
    var numFalseNulls = 0
    var numTrueNulls = 0
    for (i <- 0 until data.size) {
      val datum = data(i)
      val prediction = predictions(i)
      // Things that predict multiple products just end up with the first product
      val rawPredictedLabels = prediction.positiveLabels.asScala
      val predictedLabels = if (rawPredictedLabels.size > 1) rawPredictedLabels.slice(0, 1) else rawPredictedLabels
//      require(predictedLabels.size <= 1, "Too many predicted labels!")
      val goldLabels = datum.positiveLabels.asScala
      if (goldLabels.size == 0) {
        numTrueNulls += 1
      }
//      if (predictedLabels.size == 1) {
//        val predWord = datum.document.lines.get(predictedLabels(0).getFirst().intValue).get(predictedLabels(0).getSecond().intValue)
//        Logger.logss(datum.document.documentId + ": " + predWord)
//      } else {
//        Logger.logss(datum.document.documentId + ": NULL")
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
        Logger.logss("EVALDOC-" + data(i).document.forumName + ": " + (if (isCorrect) 1 else 0) + " 1")
      }
    }
    (correct.toDouble / data.size, "Correct: " + Util.renderNumerDenom(correct, data.size) + "; predicted " + numFalseNulls + " false nulls and the dataset actually contained " + numTrueNulls)
  }
  
  def getTaggedProduct(doc: Document, sentIdx: Int, tokIdx: Int) = {
    new TaggedProduct(Seq(doc.lines.get(sentIdx).get(tokIdx).toLowerCase),
                      Seq(doc.linesConll.get(sentIdx).get(tokIdx).get(4)))
  }
  
  def evaluateDocumentLevelSemanticProducts(data: Seq[LabeledDocument], predictions: Seq[LabeledDocument], writeOutputForSig: Boolean): EvalStats = {
    var tp = 0.0F
    var fp = 0.0F
    var fn = 0.0F
    for (i <- 0 until data.size) {
      val datum = data(i)
      val prediction = predictions(i)
      val goldProducts = new HashSet[TaggedProduct]
      val predProducts = new HashSet[TaggedProduct]
      for (label <- datum.positiveLabels.asScala) {
        goldProducts += getTaggedProduct(datum.document, label.getFirst().intValue, label.getSecond().intValue)
      }
      for (label <- prediction.positiveLabels.asScala) {
        predProducts += getTaggedProduct(datum.document, label.getFirst().intValue, label.getSecond().intValue)
      }
      val goldClusters = ProductClusterer.clusterProducts(goldProducts.toSet).map(_.map(_.words))
      val predClusters = ProductClusterer.clusterProducts(predProducts.toSet).map(_.map(_.words))
//      Logger.logss(goldClusters + " " + predClusters)
      var correctPredictions = 0
      for (predCluster <- predClusters) {
        if (doesOverlapStrings(predCluster, goldClusters)) {
          correctPredictions += 1
        }
      }
      var duplicateCorrectPredictions = 0
      for (goldCluster <- goldClusters) {
        if (doesOverlapStrings(goldCluster, predClusters)) {
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
        Logger.logss("EVALTYPE-" + datum.document.forumName + ": " + correctPredictions + " " + predClusters.size + " " + goldClusters.size)
      }
    }
    new EvalStats(tp, fp, fn)
  }
  
  def doesOverlap(cluster: HashSet[TaggedProduct], clustering: Seq[HashSet[TaggedProduct]]) = {
    var overlap = false
    for (item <- cluster) {
      for (otherCluster <- clustering) {
        if (!overlap && otherCluster.contains(item)) {
          overlap = true
        }
      }
    }
    overlap
  }
  
  def doesOverlapStrings(cluster: HashSet[Seq[String]], clustering: Seq[HashSet[Seq[String]]]) = {
    var overlap = false
    for (item <- cluster) {
      for (otherCluster <- clustering) {
        if (!overlap && otherCluster.contains(item)) {
          overlap = true
        }
      }
    }
    overlap
  }
}
