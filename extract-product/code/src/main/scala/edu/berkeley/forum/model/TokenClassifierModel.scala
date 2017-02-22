package edu.berkeley.forum.model

import edu.berkeley.forum.data.Dataset.LabeledDocument
import edu.berkeley.forum.data.Dataset.Document
import scala.collection.mutable.ArrayBuffer
import edu.berkeley.nlp.futile.fig.basic.Indexer
import scala.collection.JavaConverters._
import edu.berkeley.nlp.futile.util.Counter
import edu.berkeley.nlp.futile.util.IntCounter
import edu.berkeley.nlp.futile.util.Logger
import edu.berkeley.nlp.futile.fig.basic.IOUtils
import scala.collection.mutable.HashMap
import java.util.Calendar
import edu.berkeley.forum.data.ProductClusterer
import edu.berkeley.forum.data.NPDocument
import edu.berkeley.forum.main.Trainer
import edu.berkeley.forum.data.NPDocumentPosition

trait AbstractProductFeaturizer {
  def numFeats: Int;
  def extractFeaturesCached(ex: DocumentPosition, addToIndexer: Boolean): Array[Int]
}

@SerialVersionUID(1L)
class ProductFeaturizer(val featIdx: Indexer[String],
                        val trainWordCounts: Counter[String],
                        val lexicalFeatThreshold: Int,
                        val restrictToCurrent: Boolean = false,
                        val blockCurrent: Boolean = false,
                        val maybeBrownClusters: Option[Map[String,String]] = None,
                        var gazetteer: Option[Counter[String]] = None,
                        val useDomainFeatures: Boolean = false,
                        val useParentFeatures: Boolean = false) extends AbstractProductFeaturizer with Serializable {
  
  val trainWordCountsLc = new Counter[String]
  for (key <- trainWordCounts.keySet.asScala) {
    trainWordCountsLc.incrementCount(key, trainWordCounts.getCount(key))
  }
  
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
  
  def extractFeaturesCached(ex: DocumentPosition, addToIndexer: Boolean): Array[Int] = {
    // Only featurize sentence-internal boundaries (sentence boundaries are trivially EDU segments)
    if (ex.cachedFeatures == null) {
      ex.cachedFeatures = extractFeatures(ex, addToIndexer)
    }
    ex.cachedFeatures
  }
  
  def extractFeatures(ex: DocumentPosition, addToIndexer: Boolean): Array[Int] = {
    // BASIC FEATURE REPRESENTATION
    if (!useParentFeatures) {
      extractTokenFeatures(ex, addToIndexer)
    } else {
      // N.B. This is a more complicated featurization scheme that extracts token features on the
      // current token and syntactic parent
      // Features on preceding and following tokens aren't useful
      val allFeats = new ArrayBuffer[Int]
      val mainFeats = extractTokenFeatures(new DocumentPosition(ex.doc, ex.lineIdx, ex.wordIdx, false), addToIndexer)
      for (feat <- mainFeats) {
        maybeAdd(allFeats, addToIndexer, "Main=" + feat)
      }
      if (useParentFeatures) {
        val parentIdx = ex.doc.getParentIdx(ex.lineIdx, ex.wordIdx)
        if (parentIdx == -1) {
          maybeAdd(allFeats, addToIndexer, "Parent=ROOT")
        } else {
          val parentFeats = extractTokenFeatures(new DocumentPosition(ex.doc, ex.lineIdx, parentIdx, false), addToIndexer)
          for (feat <- parentFeats) {
            maybeAdd(allFeats, addToIndexer, "Parent=" + feat)
          }
        }
      }
      allFeats.toArray
    }
  }
  
  def extractTokenFeatures(ex: DocumentPosition, addToIndexer: Boolean): Array[Int] = {
    val doc = ex.doc
    val lineIdx = ex.lineIdx
    val lineWords = doc.lines.get(lineIdx).asScala
    val linePos = new ArrayBuffer[String]
    val lineDepRel = new ArrayBuffer[String]
    for (word <- lineWords) {
      val wordLc = word.toLowerCase
      var found = false
      var idx = 0
      while (!found && idx < doc.lines_pos.size) {
        val line = doc.lines_pos.get(idx)
        if (line.size > 1 && wordLc.contains(line.get(1).toLowerCase)) {
          linePos += line.get(4)
          lineDepRel += line.get(7)
          found = true
        } else {
          ""
        }
        idx += 1
      }
      if (!found) {
        linePos += "NOTAG"
        lineDepRel += "NOTAG"
      }
    }
    
    val wordIdx = ex.wordIdx
    val feats = new ArrayBuffer[Int]
    def add(feat: String) = {
      maybeAdd(feats, addToIndexer, feat)
      if (useDomainFeatures) {
        maybeAdd(feats, addToIndexer, feat + "-" + ex.doc.forumName)
      }
    }
    // Some positional features based on the indices
    add("LinePosnFromStart=" + bucket(lineIdx))
    add("LinePosnFromEnd=" + bucket(ex.doc.lines.size - 1 - lineIdx))
    add("WordPosn=" + bucket(wordIdx))
//    for (i <- -2 to 2) {
    // Word and POS features on the current token and context
    val tokenOffsetsToConsider = if (restrictToCurrent) Seq(0) else if (blockCurrent) Seq(-3, -2, -1, 1, 2, 3) else -3 to 3
    for (i <- tokenOffsetsToConsider) {
      add("POS" + i + "=" + access(linePos, wordIdx + i))
      add("DepRel" + i + "=" + access(lineDepRel, wordIdx + i))
      // Doesn't seem to help
//      if (i < 2) {
//        add("POSBigram" + i + "=" + access(linePos, wordIdx + i) + "-" + access(linePos, wordIdx + i + 1))
//      }
//      val word = access(lineWords, wordIdx + i).toLowerCase
      val rawWord = access(lineWords, wordIdx + i).toLowerCase
      val word = ProductClusterer.dropLongestSuffix(rawWord)
      
      if (word == "<s>" || word == "</s>" || trainWordCounts.getCount(word) >= lexicalFeatThreshold) {
        add("Word" + i + "=" + word)
      } else {
        add("Word" + i + "=UNK")
      }
      
      if (maybeBrownClusters.isDefined && (maybeBrownClusters.get.contains(rawWord) || maybeBrownClusters.get.contains(word))) {
        val bc = if (maybeBrownClusters.get.contains(rawWord)) {
          maybeBrownClusters.get(rawWord)
        } else {
          maybeBrownClusters.get(word)
        }
        add("Brown2Prefix" + i + "=" + bc.substring(0, Math.min(bc.length, 2)))
        add("Brown4Prefix" + i + "=" + bc.substring(0, Math.min(bc.length, 4)))
        add("Brown6Prefix" + i + "=" + bc.substring(0, Math.min(bc.length, 6)))
      } else {
        if (word != "<s>" && word != "</s>") {
//          Logger.logss("Missed word! " + word)
        }
      }
      
      if (gazetteer.isDefined) {
//        add("Gazetteer" + i + "=" + (gazetteer.get.getCount(ProductClusterer.dropLongestSuffix(word.toLowerCase())) > 0.5))
        add("Gazetteer" + i + "=" + (gazetteer.get.getCount(word) > 0.5))
      }
      // Compute word shape
      // Doesn't seem to help
//      if (word != "<s>" && word != "</s>") {
//        add("WordShape" + i + "=" + shapeFor(word))
//      }
    }
    // Frequency-based features: these seem to be a mixed bag. Don't seem to convincingly help
//    val nsAndVsByCount = ex.doc.countDocumentNounsAndVerbs()
//    val stemmedLcWord = ProductClusterer.dropLongestSuffix(access(lineWords, wordIdx).toLowerCase());
//    add("WordFreq=" + bucket(nsAndVsByCount.getCount(stemmedLcWord).toInt))
//    var rank = 0
//    var done = false;
//    val pq = nsAndVsByCount.asPriorityQueue()
//    while (!done && pq.hasNext) {
//      if (pq.next == stemmedLcWord) {
//        done = true
//      }
//      rank += 1
//    }
//    add("WordRank=" + bucket(rank))
    
    // Char n-grams from word and context
    // Only use current word, context seems to lead to overfitting
//    for (i <- Seq(-1, 0, 1)) {
    
    if (!blockCurrent) {
      for (i <- Seq(0)) {
        val word = access(lineWords, wordIdx + i)
        for (j <- -3 until word.size) {
          if (j >= -1) {
            add("Substr" + i + "=" + accessChars(word, j, j+2))
          }
          add("Substr" + i + "=" + accessChars(word, j, j+4))
        }
      }
    }
    feats.toArray
  }
  
  def extractNoneFeaturesCached(ex: LabeledDocument, addToIndexer: Boolean): Array[Int] = {
    if (ex.cachedNoneFeatures == null) {
      ex.cachedNoneFeatures = extractNoneFeatures(ex, addToIndexer)
    }
    ex.cachedNoneFeatures
  }
  
  def extractNoneFeatures(ex: LabeledDocument, addToIndexer: Boolean): Array[Int] = {
    val allFeats = new ArrayBuffer[Int]
    maybeAdd(allFeats, addToIndexer, "NoneBias")
    maybeAdd(allFeats, addToIndexer, "NoneSentLen=" + ex.document.lines.size())
    allFeats.toArray
  }
  
  private def accessChars(str: String, start: Int, end: Int) = {
     (start until end).map(accessChar(str, _)).foldLeft("")(_ + _)
  }
  
  private def accessChar(str: String, idx: Int) = {
     if (idx < 0) "[" else if (idx >= str.size) "]" else str.charAt(idx) + ""
  }
  
  private def access(strs: Seq[String], idx: Int) = {
    if (idx < 0) "<s>" else if (idx >= strs.size) "</s>" else strs(idx) 
  }
  
  private def bucket(n: Int) = {
    if (n <= 4) n else if (n <= 8) "5-8" else if (n <= 16) "9-16" else if (n <= 32) "17-32" else "33+"  
  }
  
  private def shapeFor(word: String) = {
    val result = new StringBuilder(word.length);
    var i = 0;
    while (i < word.length) {
      val c = word(i);
      val x = if (c.isLetter && c.isUpper) 'X' else if (c.isLetter) 'x' else if (c.isDigit) 'd' else c;
      if (result.length > 1 && (result.last == x) && result(result.length - 2) == x) {
        result += 'e'
      } else if (result.length > 1 && result.last == 'e' && result(result.length - 2) == x) {
        // nothing
      } else {
        result += x;
      }
      i += 1;
    }
    result.toString
  }
}

object ProductFeaturizer {
  
   def loadBrownClusters(path: String, cutoff: Int): Map[String,String] = {
    val wordsToClusters = new HashMap[String,String];
    val iterator = IOUtils.lineIterator(path);
    while (iterator.hasNext) {
      val nextLine = iterator.next;
      val fields = nextLine.split("\\s+");
      if (fields.size == 3 && fields(fields.size - 1).toInt >= cutoff) {
        wordsToClusters.put(fields(1), fields(0));
      }
    }
    Logger.logss(wordsToClusters.size + " Brown cluster definitions read in");
    wordsToClusters.toMap;
  }
}

@SerialVersionUID(1L)
class ProductComputer(val featurizer: AbstractProductFeaturizer,
                      val recallErrorLossWeight: Double,
                      val trainProductLcCounts: Counter[String],
                      val singletonLossWeight: Double) extends LikelihoodAndGradientComputerSparse[DocumentPosition] with Serializable {
  
  val trainProductsLc = trainProductLcCounts.keySet.asScala.toSet
  
  def getInitialWeights(initialWeightsScale: Double): Array[Double] = Array.tabulate(featurizer.numFeats)(i => 0.0)
  
  def accumulateGradientAndComputeObjective(ex: DocumentPosition, weights: AdagradWeightVector, gradient: IntCounter): Double = {
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
  
  def computeObjective(ex: DocumentPosition, weights: AdagradWeightVector): Double = accumulateGradientAndComputeObjective(ex, weights, new IntCounter)
  
  def decode(ex: DocumentPosition, weights: AdagradWeightVector): Boolean = {
    decode(ex, weights, 0)._1
  }
  
  private def decode(ex: DocumentPosition, weights: AdagradWeightVector, lossWeight: Double): (Boolean, Double) = {
    val feats = featurizer.extractFeaturesCached(ex, false)
    val isGold = ex.isProduct
    val singletonLossTerm = if (singletonLossWeight != 1.0) {
      if (!ex.isSingleton.isDefined) {
        val exHead = ex.doc.lines.get(ex.lineIdx).get(ex.wordIdx)
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
  
  private def score(ex: DocumentPosition, weights: AdagradWeightVector, choice: Boolean, lossWeight: Double) = {
    val feats = featurizer.extractFeaturesCached(ex, false)
    val isGold = ex.isProduct
    weights.score(feats) + (if (isGold) (-lossWeight * recallErrorLossWeight) else lossWeight)
  }
}

class DocumentPosition(val doc: Document,
                       val lineIdx: Int,
                       val wordIdx: Int,
                       val isProduct: Boolean) {
  var cachedFeatures: Array[Int] = null;
  var isSingleton: Option[Boolean] = None
}

@SerialVersionUID(1L)
class ProductPredictor(val computer: ProductComputer,
                       val weights: Array[Double]) extends Model with Serializable {
  val wrappedWeights = new AdagradWeightVector(weights, 0.0, 0.0)
  
  def decode(ex: DocumentPosition) = computer.decode(ex, wrappedWeights)
  
  def predict(datum: Document): LabeledDocument = {
    val positiveLabels = new java.util.ArrayList[tuple.Pair[java.lang.Integer,java.lang.Integer]]();
    for (lineIdx <- 0 until datum.lines.size) {
      for (wordIdx <- 0 until datum.lines.get(lineIdx).size) {
        val predLabel = decode(new DocumentPosition(datum, lineIdx, wordIdx, false));
        if (predLabel) {
          positiveLabels.add(tuple.Pair.makePair(lineIdx, wordIdx));
        }
      }
    }
    return new LabeledDocument(datum, positiveLabels);
  }
}


////////////////////
// DOCUMENT-LEVEL //
////////////////////

@SerialVersionUID(1L)
class ProductDocLevelTokenComputer(val featurizer: ProductFeaturizer,
                                   val recallErrorLossWeight: Double,
                                   val prohibitNull: Boolean,
                                   val trainProductLcCounts: Counter[String],
                                   val singletonLossWeight: Double) extends LikelihoodAndGradientComputerSparse[LabeledDocument] with Serializable {
  
  val trainProductsLc = trainProductLcCounts.keySet.asScala.toSet
  
  def getInitialWeights(initialWeightsScale: Double): Array[Double] = Array.tabulate(featurizer.featIdx.size)(i => 0.0)
  
  def accumulateGradientAndComputeObjective(ex: LabeledDocument, weights: AdagradWeightVector, gradient: IntCounter): Double = {
    val (pred, predScore) = decode(ex, weights, 1.0);
    val gold = ex.positiveLabels.asScala
    if ((!pred.isDefined && gold.isEmpty) || (pred.isDefined && gold.contains(pred.get))) {
      // Make no update and incur no loss
      0.0
    } else {
      val predFeats = if (!pred.isDefined) ex.cachedNoneFeatures else ex.documentPositions(pred.get._1)(pred.get._2).cachedFeatures
      val (goldFeats, goldScore) = if (gold.isEmpty) {
        ex.cachedNoneFeatures -> weights.score(ex.cachedNoneFeatures)
      } else {
        // Compute the best gold
        var bestGoldChoice: (Int,Int) = null
        var bestGoldScore = Double.NegativeInfinity
        for (goldChoice <- gold) {
          val convGoldChoice = goldChoice.getFirst().intValue -> goldChoice.getSecond.intValue
          // TODO: This might be weird with singletonLossWeight...
          val goldChoiceScore = weights.score(ex.documentPositions(convGoldChoice._1)(convGoldChoice._2).cachedFeatures)
          if (goldChoiceScore > bestGoldScore) {
            bestGoldChoice = convGoldChoice
            bestGoldScore = goldChoiceScore
          }
        }
        val bestGoldFeats = ex.documentPositions(bestGoldChoice._1)(bestGoldChoice._2).cachedFeatures
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
  
  def computeObjective(ex: LabeledDocument, weights: AdagradWeightVector): Double = accumulateGradientAndComputeObjective(ex, weights, new IntCounter)
  
  def decode(ex: LabeledDocument, weights: AdagradWeightVector): Option[(Int,Int)] = {
    decode(ex, weights, 0)._1
  }
  
  private def decode(ex: LabeledDocument, weights: AdagradWeightVector, lossWeight: Double): (Option[(Int,Int)], Double) = {
    val golds = ex.positiveLabels
    val noneFeats = featurizer.extractNoneFeaturesCached(ex, false)
    val noneScore = if (prohibitNull) {
      -100000
    } else {
      weights.score(noneFeats) + (if (!golds.isEmpty) lossWeight else 0.0)
    }
    var highestScore = noneScore
    var highestChoice: Option[(Int,Int)] = None
    for (i <- 0 until ex.document.lines.size) {
      for (j <- 0 until ex.document.lines.get(i).size) {
        val docPosn = ex.documentPositions(i)(j)
        if (!docPosn.isSingleton.isDefined) {
          val exHead = ex.document.lines.get(i).get(j)
          docPosn.isSingleton = Some(ProductClusterer.hasProductBeenSeenBefore(trainProductsLc, exHead.toLowerCase))
        }
      }
    }
    val isAnySingleton = ex.positiveLabels.asScala.map(posLabel => ex.documentPositions(posLabel.getFirst.intValue)(posLabel.getSecond.intValue).isSingleton.get).foldLeft(false)(_ || _)
    val singletonLossTerm = if (isAnySingleton) singletonLossWeight else 1.0
    for (i <- 0 until ex.document.lines.size) {
      for (j <- 0 until ex.document.lines.get(i).size) {
        val docPosn = ex.documentPositions(i)(j)
        val feats = featurizer.extractFeaturesCached(docPosn, false)
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
class ProductDocLevelTokenPredictor(val computer: ProductDocLevelTokenComputer,
                                    val weights: Array[Double]) extends Model with Serializable {
  val wrappedWeights = new AdagradWeightVector(weights, 0, 0)
  
  def getWrappedWeights = wrappedWeights
  
  def decode(ex: LabeledDocument) = {
    computer.decode(ex, wrappedWeights)
  }
  
  def predict(datum: Document): LabeledDocument = {
    val maybeSelectedIndex = decode(new LabeledDocument(datum, Seq[tuple.Pair[Integer,Integer]]().asJava))
    new LabeledDocument(datum, if (maybeSelectedIndex.isDefined) Seq(tuple.Pair.makePair(new Integer(maybeSelectedIndex.get._1), new Integer(maybeSelectedIndex.get._2))).asJava else Seq().asJava)
  }
  
  def adapt(newLabeledData: Option[Seq[LabeledDocument]], newUnlabeledData: Option[Seq[Document]]) {
    if (newLabeledData.isDefined && computer.featurizer.gazetteer.isDefined) {
      val newGazetteer = Trainer.buildGazetteer(newLabeledData.get)
      Logger.logss("Adapting to new gazetteer: " + computer.featurizer.gazetteer.get.keySet.size + " products => " + newGazetteer.keySet.size + " products")
      computer.featurizer.gazetteer = Some(newGazetteer)
    }
  }
  
  def displayGazetteerFeats {
    for (i <- 0 until computer.featurizer.featIdx.size) {
      val featName = computer.featurizer.featIdx.get(i)
      if (featName.contains("Gazetteer")) {
        Logger.logss(featName + ": " + weights(i))
      }
    }
  }
  
  def getActiveFeatures(datum: Document, lineIdx: Int, npIdx: Int): Counter[String] = {
    throw new RuntimeException("Unimplemented")
//    val ex = new DocumentPosition(datum, lineIdx, npIdx, false)
//    val feats = computer.featurizer.extractFeaturesCached(ex, false)
//    val counter = new Counter[String]
//    for (feat <- feats) {
//      val featName = computer.featurizer.featIdx.getObject(feat)
//      if (featName.contains("=")) {
//        val featPrefix = featName.substring(0, featName.indexOf("="))
//        val finalFeatName = if (featPrefix == "First" || featPrefix == "Head" || featPrefix == "Last" || featPrefix == "Parent") {
//          val wrappedFeatIdx = featName.substring(featName.indexOf("=") + 1).toInt
//          featPrefix + "=" + computer.featurizer.wrappedFeaturizer.featIdx.getObject(wrappedFeatIdx)
//        } else {
//          featName
//        }
//        counter.incrementCount(finalFeatName, weights(feat))
//      } else {
//        counter.incrementCount(featName, weights(feat))
//      }
//    }
//    counter
  }
}
