package edu.berkeley.forum.model

import edu.berkeley.forum.data.Dataset.LabeledDocument
import edu.berkeley.forum.data.Dataset.Document
import scala.collection.mutable.ArrayBuffer
import edu.berkeley.nlp.futile.fig.basic.Indexer
import edu.berkeley.nlp.futile.ClassifyUtils
import scala.collection.JavaConverters._
import edu.berkeley.nlp.futile.util.Counter
import edu.berkeley.nlp.futile.util.IntCounter
import edu.berkeley.forum.data.NPDocument
import edu.berkeley.forum.data.LabeledNPDocument
import edu.berkeley.forum.data.ProductClusterer
import edu.berkeley.forum.data.NPDocumentPosition
import edu.berkeley.nlp.futile.util.Logger
import edu.berkeley.forum.main.Trainer

//takes token level features and re-creates them for first word in NP, last, head and parent
class NPProductFeaturizer(val wrappedFeaturizer: ProductFeaturizer,
                          val restrictToCurrent: Boolean = false,
                          val blockCurrent: Boolean = false) extends Serializable {
//  val featIdx = wrappedFeaturizer.featIdx
  val featIdx = new Indexer[String]
  
  def numFeats = featIdx.size
  
  private def maybeAdd(feats: ArrayBuffer[Int], addToIndexer: Boolean, feat: String) {
    if (addToIndexer) {
      feats += featIdx.getIndex(feat)
    } else {
      val idx = featIdx.indexOf(feat)
      if (idx != -1) {
        feats += idx
      }
    }
  }
  
  def unpackFeatureName(idx: Int) = {
    val name = featIdx.getObject(idx)
    if (name.contains("=")) {
      val wrappedFeatIdx = name.substring(name.indexOf("=") + 1).toInt
      val wrappedFeatName = wrappedFeaturizer.featIdx.getObject(wrappedFeatIdx)
      name.substring(0, name.indexOf("=") + 1) + wrappedFeatName
    } else {
      name
    }
  }
  
  def extractFeaturesCached(ex: NPDocumentPosition, addToIndexer: Boolean): Array[Int] = {
    // Only featurize sentence-internal boundaries (sentence boundaries are trivially EDU segments)
    if (ex.cachedFeatures == null) {
      ex.cachedFeatures = extractFeatures(ex, addToIndexer)
    }
    ex.cachedFeatures
  }
  
  def extractFeatures(ex: NPDocumentPosition, addToIndexer: Boolean): Array[Int] = {
    val allFeats = new ArrayBuffer[Int]
    val np = ex.doc.nps(ex.lineIdx)(ex.npIdx)
    val firstFeats = wrappedFeaturizer.extractFeatures(new DocumentPosition(ex.doc.doc, ex.lineIdx, np._1, false), addToIndexer)
////    if (!restrictToCurrent) {
    for (feat <- firstFeats) {
      maybeAdd(allFeats, addToIndexer, "First=" + feat)
    }
//    }
    val headFeats = wrappedFeaturizer.extractFeatures(new DocumentPosition(ex.doc.doc, ex.lineIdx, np._2, false), addToIndexer)
    for (feat <- headFeats) {
      maybeAdd(allFeats, addToIndexer, "Head=" + feat)
    }
    val lastFeats = wrappedFeaturizer.extractFeatures(new DocumentPosition(ex.doc.doc, ex.lineIdx, np._3 - 1, false), addToIndexer)
//    if (!restrictToCurrent) {
    for (feat <- lastFeats) {
      maybeAdd(allFeats, addToIndexer, "Last=" + feat)
    }
////    }
    // Span features
    maybeAdd(allFeats, addToIndexer, "Length=" + (np._3 - np._1))
    val npParentIdx = ex.doc.doc.getParentIdx(ex.lineIdx, np._2)
    if (npParentIdx == -1 || restrictToCurrent) {
      maybeAdd(allFeats, addToIndexer, "ROOT")
    } else {
      val parentFeats = wrappedFeaturizer.extractFeatures(new DocumentPosition(ex.doc.doc, ex.lineIdx, npParentIdx, false), addToIndexer)
      for (feat <- parentFeats) {
        maybeAdd(allFeats, addToIndexer, "Parent=" + feat)
      }
    }
    allFeats.toArray
  }
  
  // Extract features on the choice to say that the document contains no product
  def extractNoneFeaturesCached(ex: LabeledNPDocument, addToIndexer: Boolean): Array[Int] = {
    if (ex.cachedNoneFeatures == null) {
      ex.cachedNoneFeatures = extractNoneFeatures(ex, addToIndexer)
    }
    ex.cachedNoneFeatures
  }
  
  def extractNoneFeatures(ex: LabeledNPDocument, addToIndexer: Boolean): Array[Int] = {
    val allFeats = new ArrayBuffer[Int]
    maybeAdd(allFeats, addToIndexer, "NoneBias")
    maybeAdd(allFeats, addToIndexer, "NoneSentLen=" + ex.doc.doc.lines.size())
    allFeats.toArray
  }
}

@SerialVersionUID(1L)
class ProductNPComputer(val featurizer: NPProductFeaturizer,
                        val recallErrorLossWeight: Double,
                        val trainProductLcCounts: Counter[String],
                        val singletonLossWeight: Double) extends LikelihoodAndGradientComputerSparse[NPDocumentPosition] with Serializable {
  
  val trainProductsLc = trainProductLcCounts.keySet.asScala.toSet
  
  def getInitialWeights(initialWeightsScale: Double): Array[Double] = Array.tabulate(featurizer.featIdx.size)(i => 0.0)
  
  def accumulateGradientAndComputeObjective(ex: NPDocumentPosition, weights: AdagradWeightVector, gradient: IntCounter): Double = {
    val (pred, predScore) = decode(ex, weights, 1.0);
    if (pred != ex.isProduct) {
      val feats = ex.cachedFeatures
      if (ex.isProduct) {
        // Update positive
        for (feat <- feats) {
          gradient.incrementCount(feat, 1)
        }
      } else {
        for (feat <- feats) {
          gradient.incrementCount(feat, -1)
        }
      }
      Math.abs(predScore)
    } else {
      // Do nothing and incur no loss
      0.0
    }
  }
  
  def computeObjective(ex: NPDocumentPosition, weights: AdagradWeightVector): Double = accumulateGradientAndComputeObjective(ex, weights, new IntCounter)
  
  def decode(ex: NPDocumentPosition, weights: AdagradWeightVector): Boolean = {
    decode(ex, weights, 0)._1
  }
  
  private def decode(ex: NPDocumentPosition, weights: AdagradWeightVector, lossWeight: Double): (Boolean, Double) = {
    val feats = featurizer.extractFeaturesCached(ex, false)
    val isGold = ex.isProduct
    val singletonLossTerm = if (singletonLossWeight != 1.0) {
      if (!ex.isSingleton.isDefined) {
        val exHead = ex.doc.doc.lines.get(ex.lineIdx).get(ex.doc.nps(ex.lineIdx)(ex.npIdx)._2)
        ex.isSingleton = Some(ProductClusterer.hasProductBeenSeenBefore(trainProductsLc, exHead.toLowerCase))
      }
      if (ex.isSingleton.get) singletonLossWeight else 1.0
    } else {
      1.0
    }
    val modLossWeight = lossWeight * singletonLossTerm
    val score = weights.score(feats) + (if (isGold) (-modLossWeight * recallErrorLossWeight) else modLossWeight)
    (score > 0) -> score
  }
}

@SerialVersionUID(1L)
class ProductNPPredictor(val computer: ProductNPComputer,
                         val weights: Array[Double]) extends NPModel with Serializable {
  val wrappedWeights = new AdagradWeightVector(weights, 0, 0)
  
  def getWrappedWeights = wrappedWeights
  
  def decode(ex: NPDocumentPosition) = {
    computer.decode(ex, wrappedWeights)
  }
  
  def predict(datum: NPDocument): LabeledNPDocument = {
    val predictions = Array.tabulate(datum.nps.size)(i => {
      Array.tabulate(datum.nps(i).size)(j => {
        val npDP = new NPDocumentPosition(datum, i, j, false)
        decode(npDP)
      })
    })
    new LabeledNPDocument(datum, predictions)
  }
  
  def adapt(newLabeledData: Option[Seq[LabeledNPDocument]], newUnlabeledData: Option[Seq[NPDocument]]) {
    if (newLabeledData.isDefined && computer.featurizer.wrappedFeaturizer.gazetteer.isDefined) {
//      val newGazetteer = ProductClusterer.extractCanonicalizedProductCountsFromNPs(newLabeledData.get)
      val newGazetteer = Trainer.buildGazetteerFromNPs(newLabeledData.get)
      Logger.logss("Adapting to new gazetteer: " + computer.featurizer.wrappedFeaturizer.gazetteer.get.keySet.size + " products => " + newGazetteer.keySet.size + " products")
      computer.featurizer.wrappedFeaturizer.gazetteer = Some(newGazetteer)
    }
  }
  
  def getActiveFeatures(datum: NPDocument, lineIdx: Int, npIdx: Int): Counter[String] = {
    val ex = new NPDocumentPosition(datum, lineIdx, npIdx, false)
    val feats = computer.featurizer.extractFeaturesCached(ex, false)
    val counter = new Counter[String]
    for (feat <- feats) {
      val featName = computer.featurizer.featIdx.getObject(feat)
      if (featName.contains("=")) {
        val featPrefix = featName.substring(0, featName.indexOf("="))
        val finalFeatName = if (featPrefix == "First" || featPrefix == "Head" || featPrefix == "Last" || featPrefix == "Parent") {
          val wrappedFeatIdx = featName.substring(featName.indexOf("=") + 1).toInt
          featPrefix + "=" + computer.featurizer.wrappedFeaturizer.featIdx.getObject(wrappedFeatIdx)
        } else {
          featName
        }
        counter.incrementCount(finalFeatName, weights(feat))
      } else {
        counter.incrementCount(featName, weights(feat))
      }
    }
    counter
  }
}

///////////////////////////////
// DOCUMENT-LEVEL PREDICTION //
///////////////////////////////

@SerialVersionUID(1L)
class ProductDocLevelNPComputer(val featurizer: NPProductFeaturizer,
                                val recallErrorLossWeight: Double,
                                val prohibitNull: Boolean,
                                val trainProductLcCounts: Counter[String],
                                val singletonLossWeight: Double) extends LikelihoodAndGradientComputerSparse[LabeledNPDocument] with Serializable {
  
  val trainProductsLc = trainProductLcCounts.keySet.asScala.toSet
  
  def getInitialWeights(initialWeightsScale: Double): Array[Double] = Array.tabulate(featurizer.featIdx.size)(i => 0.0)
  
  def accumulateGradientAndComputeObjective(ex: LabeledNPDocument, weights: AdagradWeightVector, gradient: IntCounter): Double = {
    val (pred, predScore) = decode(ex, weights, 1.0);
    val gold = ex.getPositiveLabels
    if ((!pred.isDefined && gold.isEmpty) || (pred.isDefined && gold.contains(pred.get))) {
      // Make no update and incur no loss
      0.0
    } else {
      val predFeats = if (!pred.isDefined) ex.cachedNoneFeatures else ex.npDocumentPositions(pred.get._1)(pred.get._2).cachedFeatures
      val (goldFeats, goldScore) = if (gold.isEmpty) {
        ex.cachedNoneFeatures -> weights.score(ex.cachedNoneFeatures)
      } else {
        // Compute the best gold
        var bestGoldChoice: (Int,Int) = null
        var bestGoldScore = Double.NegativeInfinity
        for (goldChoice <- gold) {
          // TODO: This might be weird with singletonLossWeight...
          val goldChoiceScore = weights.score(ex.npDocumentPositions(goldChoice._1)(goldChoice._2).cachedFeatures)
          if (goldChoiceScore > bestGoldScore) {
            bestGoldChoice = goldChoice
            bestGoldScore = goldChoiceScore
          }
        }
        val bestGoldFeats = ex.npDocumentPositions(bestGoldChoice._1)(bestGoldChoice._2).cachedFeatures
        bestGoldFeats -> weights.score(bestGoldFeats)
      }
      for (feat <- goldFeats) {
        gradient.incrementCount(feat, 1)
      }
      for (feat <- predFeats) {
        gradient.incrementCount(feat, -1)
      }
      Math.abs(predScore - goldScore)
    }
  }
  
  def computeObjective(ex: LabeledNPDocument, weights: AdagradWeightVector): Double = accumulateGradientAndComputeObjective(ex, weights, new IntCounter)
  
  def decode(ex: LabeledNPDocument, weights: AdagradWeightVector): Option[(Int,Int)] = {
    decode(ex, weights, 0)._1
  }
  
  private def decode(ex: LabeledNPDocument, weights: AdagradWeightVector, lossWeight: Double): (Option[(Int,Int)], Double) = {
    val golds = ex.getPositiveLabels
    val noneFeats = featurizer.extractNoneFeaturesCached(ex, false)
    val noneScore = if (prohibitNull) {
      -100000
    } else {
      weights.score(noneFeats) + (if (!golds.isEmpty) lossWeight else 0.0)
    }
    var highestScore = noneScore
    var highestChoice: Option[(Int,Int)] = None
    for (i <- 0 until ex.doc.nps.size) {
      for (j <- 0 until ex.doc.nps(i).size) {
        val npDocPosn = ex.npDocumentPositions(i)(j)
        if (!npDocPosn.isSingleton.isDefined) {
          val exHead = ex.doc.doc.lines.get(npDocPosn.lineIdx).get(ex.doc.nps(npDocPosn.lineIdx)(npDocPosn.npIdx)._2)
          npDocPosn.isSingleton = Some(ProductClusterer.hasProductBeenSeenBefore(trainProductsLc, exHead.toLowerCase))
        }
      }
    }
    val isAnySingleton = ex.getPositiveLabels.map(posLabelIdx => ex.npDocumentPositions(posLabelIdx._1)(posLabelIdx._2).isSingleton.get).foldLeft(false)(_ || _)
    val singletonLossTerm = if (isAnySingleton) singletonLossWeight else 1.0
    for (i <- 0 until ex.doc.nps.size) {
      for (j <- 0 until ex.doc.nps(i).size) {
        val npDocPosn = ex.npDocumentPositions(i)(j)
        val feats = featurizer.extractFeaturesCached(npDocPosn, false)
        // TODO: Don't do this until we figure out how it impacts the latent stuff
//        val singletonLossTerm = if (singletonLossWeight != 1.0) {
//          if (npDocPosn.isSingleton.get) singletonLossWeight else 1.0
//        } else {
//          1.0
//        }
//        val modLossWeight = lossWeight * singletonLossTerm
        
        val modLossWeight = lossWeight * singletonLossTerm
        val score = weights.score(feats) + (if (!golds.contains((i, j))) recallErrorLossWeight * modLossWeight else 0.0) 
        if (score > highestScore) {
          highestScore = score
          highestChoice = Some((i, j))
        }
      }
    }
    highestChoice -> highestScore
  }
}

@SerialVersionUID(1L)
class ProductDocLevelNPPredictor(val computer: ProductDocLevelNPComputer,
                                 val weights: Array[Double]) extends NPDocLevelModel with Serializable {
  val wrappedWeights = new AdagradWeightVector(weights, 0, 0)
  
  def getWrappedWeights = wrappedWeights
  
  def decode(ex: LabeledNPDocument) = {
    computer.decode(ex, wrappedWeights)
  }
  
  def predict(datum: NPDocument): LabeledNPDocument = {
    val maybeSelectedIndex = decode(new LabeledNPDocument(datum, Array.tabulate(datum.nps.size)(i => Array.tabulate(datum.nps(i).size)(j => false)), None, None))
    new LabeledNPDocument(datum, Array.tabulate(datum.nps.size)(i => Array.tabulate(datum.nps(i).size)(j => maybeSelectedIndex.isDefined && maybeSelectedIndex.get == (i,j))), None, None)
  }
  
  def adapt(newLabeledData: Option[Seq[LabeledNPDocument]], newUnlabeledData: Option[Seq[NPDocument]]) {
    if (newLabeledData.isDefined && computer.featurizer.wrappedFeaturizer.gazetteer.isDefined) {
//      val newGazetteer = ProductClusterer.extractCanonicalizedProductCountsFromNPs(newLabeledData.get)
      val newGazetteer = Trainer.buildGazetteerFromNPs(newLabeledData.get)
      Logger.logss("Adapting to new gazetteer: " + computer.featurizer.wrappedFeaturizer.gazetteer.get.keySet.size + " products => " + newGazetteer.keySet.size + " products")
      computer.featurizer.wrappedFeaturizer.gazetteer = Some(newGazetteer)
    }
  }
  
  def displayGazetteerFeats {
    for (i <- 0 until computer.featurizer.featIdx.size) {
      val wrappedFeatName = computer.featurizer.unpackFeatureName(i)
      if (wrappedFeatName.contains("Gazetteer")) {
        Logger.logss(wrappedFeatName + ": " + weights(i))
      }
    }
  }
  
  def getActiveFeatures(datum: NPDocument, lineIdx: Int, npIdx: Int): Counter[String] = {
    val ex = new NPDocumentPosition(datum, lineIdx, npIdx, false)
    val feats = computer.featurizer.extractFeaturesCached(ex, false)
    val counter = new Counter[String]
    for (feat <- feats) {
      val featName = computer.featurizer.featIdx.getObject(feat)
      if (featName.contains("=")) {
        val featPrefix = featName.substring(0, featName.indexOf("="))
        val finalFeatName = if (featPrefix == "First" || featPrefix == "Head" || featPrefix == "Last" || featPrefix == "Parent") {
          val wrappedFeatIdx = featName.substring(featName.indexOf("=") + 1).toInt
          featPrefix + "=" + computer.featurizer.wrappedFeaturizer.featIdx.getObject(wrappedFeatIdx)
        } else {
          featName
        }
        counter.incrementCount(finalFeatName, weights(feat))
      } else {
        counter.incrementCount(featName, weights(feat))
      }
    }
    counter
  }
}
