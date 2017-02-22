package edu.berkeley.forum.model

import edu.berkeley.forum.data.NPDocumentPosition
import edu.berkeley.forum.data.LabeledNPDocument
import edu.berkeley.forum.data.Dataset.LabeledDocument
import scala.collection.mutable.ArrayBuffer
import edu.berkeley.nlp.futile.fig.basic.Indexer
import edu.berkeley.nlp.futile.util.Counter
import edu.berkeley.nlp.futile.util.IntCounter
import edu.berkeley.forum.data.Dataset.Document
import scala.collection.JavaConverters._
import edu.berkeley.nlp.futile.util.Logger


//takes token level features and re-creates them for first word in NP, last, head and parent
class ProductCRFFeaturizer(val wrappedFeaturizer: ProductFeaturizer,
                           val labelVocab: Seq[String] = Seq("O", "B")) extends Serializable {
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
  
  def extractFeaturesCached(ex: LabeledDocument, addToIndexer: Boolean): Array[Array[Array[Array[Int]]]] = {
    // Only featurize sentence-internal boundaries (sentence boundaries are trivially EDU segments)
    if (ex.cachedSentFeats == null) {
      ex.cachedSentFeats = Array.tabulate(ex.document.lines.size)(sentIdx => {
        Array.tabulate(ex.document.lines.get(sentIdx).size)(tokIdx => {
          extractFeatures(ex, sentIdx, tokIdx, addToIndexer)
        })
      })
    }
    ex.cachedSentFeats
  }
  
  def extractFeatures(ex: LabeledDocument, sentIdx: Int, tokIdx: Int, addToIndexer: Boolean): Array[Array[Int]] = {
    val wrappedFeats = wrappedFeaturizer.extractFeatures(new DocumentPosition(ex.document, sentIdx, tokIdx, false), addToIndexer)
    Array.tabulate(labelVocab.size)(labelIdx => {
      val feats = new ArrayBuffer[Int]
      for (feat <- wrappedFeats) {
        maybeAdd(feats, addToIndexer, labelVocab(labelIdx) + ":" + feat)
      }
      feats.toArray
    })
  }
  
}


@SerialVersionUID(1L)
class ProductCRFComputer(val featurizer: ProductCRFFeaturizer,
                         val recallErrorLossWeight: Double,
                         val trainProductLcCounts: Counter[String],
                         val singletonLossWeight: Double) extends LikelihoodAndGradientComputerSparse[LabeledDocument] with Serializable {
  
  val trainProductsLc = trainProductLcCounts.keySet.asScala.toSet
  
  val transitionFeatures = Array(Array("Trans=B-B", "Trans=B-O"),
                                 Array("Trans=O-B", "Trans=O-O"))
  val indexedTransitionFeatures = transitionFeatures.map(_.map(feat => Array(featurizer.featIdx.getIndex(feat))))
  
  val MaxSeqLen = 500
  val NumStates = 2
  var cachedForwardLogProbs = Array.fill(MaxSeqLen, NumStates)(Double.NegativeInfinity)
  var tempLogProbVector = Array.fill(NumStates)(Double.NegativeInfinity)
  
  def getInitialWeights(initialWeightsScale: Double): Array[Double] = Array.tabulate(featurizer.numFeats)(i => 0.0)
  
  def accumulateGradientAndComputeObjective(ex: LabeledDocument, weights: AdagradWeightVector, gradient: IntCounter): Double = {
    var overallScore = 0.0
    val feats = featurizer.extractFeaturesCached(ex, false)
    for (sentIdx <- 0 until ex.document.lines.size) {
      val gold = (0 until ex.document.lines.get(sentIdx).size).map(i => if (ex.positiveLabels.contains(tuple.Pair.makePair(new Integer(sentIdx), new Integer(i)))) 1 else 0)
      val goldScore = score(gold.toArray, feats(sentIdx), weights)
      val (pred, predScore) = decodeSentence(ex, sentIdx, weights, 1.0);
      incrementActiveFeats(gold.toArray, feats(sentIdx), gradient, 1.0)
      incrementActiveFeats(pred, feats(sentIdx), gradient, -1.0)
//      Logger.logss("PRED SCORE: " + predScore + "; GOLD SCORE: " + goldScore + "\n" + pred.toSeq + "\n" + gold.toSeq) 
      overallScore += predScore - goldScore
    }
    overallScore
  }
  
  def computeObjective(ex: LabeledDocument, weights: AdagradWeightVector): Double = accumulateGradientAndComputeObjective(ex, weights, new IntCounter)
  
  def decode(ex: LabeledDocument, weights: AdagradWeightVector): LabeledDocument = {
    val posLabels = new ArrayBuffer[(Int,Int)]
    for (sentIdx <- 0 until ex.document.lines.size) {
      val (output, score) = decodeSentence(ex, sentIdx, weights, 0)
      val activeIndices = (0 until output.size).filter(i => output(i) == 1)
      posLabels ++= activeIndices.map(activeIdx => (sentIdx -> activeIdx))
    }
    new LabeledDocument(ex.document, posLabels.map(pair => tuple.Pair.makePair(new Integer(pair._1), new Integer(pair._2))).asJava)
  }
  
  private def decodeSentence(ex: LabeledDocument, sentIdx: Int, weights: AdagradWeightVector, lossWeight: Double): (Array[Int], Double) = {
//    val feats = featurizer.extractFeaturesCached(ex, false)
    var aggregateScore = 0.0
    val feats = featurizer.extractFeaturesCached(ex, false)(sentIdx)
    val goldSeq = (0 until ex.document.lines.get(sentIdx).size).map(i => if (ex.positiveLabels.contains(tuple.Pair.makePair(new Integer(sentIdx), new Integer(i)))) 1 else 0)
    decode(feats, goldSeq, lossWeight, weights)
  }
  
  private def decode(feats: Array[Array[Array[Int]]], golds: Seq[Int], lossWeight: Double, weights: AdagradWeightVector): (Array[Int], Double) = {
    val numTokens = feats.size
    val transitionLogProbs = indexedTransitionFeatures.map(_.map(feats => weights.score(feats)))
    for (i <- 0 until numTokens) {
      for (j <- 0 until NumStates) {
        val loss = if (j == 1 && golds(i) == 0) 1.0 else if (j == 0 && golds(i) == 1) recallErrorLossWeight else 0.0
        val emissionScore = weights.score(feats(i)(j)) + loss
        if (i == 0) {
          cachedForwardLogProbs(i)(j) = emissionScore
        } else {
          var bestForwardProb = Double.NegativeInfinity
          var k = 0
          while (k < NumStates) {
            bestForwardProb = Math.max(bestForwardProb, cachedForwardLogProbs(i-1)(k) + emissionScore + transitionLogProbs(k)(j))
            k += 1
          }
          cachedForwardLogProbs(i)(j) = bestForwardProb
        }
      }
    }
    var finalScore = Double.NegativeInfinity
    val prediction = Array.fill(numTokens)(-1)
    for (i <- numTokens - 1 to 0 by -1) {
      var bestScore = Double.NegativeInfinity
      for (j <- 0 until NumStates) {
        val score = if (i == numTokens - 1) {
          finalScore = Math.max(finalScore, cachedForwardLogProbs(i)(j))
          cachedForwardLogProbs(i)(j)
        } else {
          cachedForwardLogProbs(i)(j) + transitionLogProbs(j)(prediction(i+1));
        }
        if (score > bestScore) {
          prediction(i) = j;
          bestScore = score;
        }
      }
    }
    prediction -> finalScore;
  }
  
  private def incrementActiveFeats(labels: Array[Int], feats: Array[Array[Array[Int]]], gradient: IntCounter, scale: Double) {
    for (i <- 0 until labels.size) {
      if (i < labels.size - 1) {
        for (feat <- indexedTransitionFeatures(labels(i))(labels(i+1))) {
          gradient.incrementCount(feat, scale)
        }
      }
      for (feat <- feats(i)(labels(i))) {
        gradient.incrementCount(feat, scale)
      }
    }
  }
  
  private def score(labels: Array[Int], feats: Array[Array[Array[Int]]], weights: AdagradWeightVector) = {
    var overallScore = 0.0
    for (i <- 0 until labels.size) {
      if (i < labels.size - 1) {
        overallScore += weights.score(indexedTransitionFeatures(labels(i))(labels(i+1)))
      }
      overallScore += weights.score(feats(i)(labels(i)))
    }
    overallScore
  }
  
//  private def score(ex: DocumentPosition, weights: AdagradWeightVector, choice: Boolean, lossWeight: Double) = {
//    val feats = featurizer.extractFeaturesCached(ex, false)
//    val isGold = ex.isProduct
//    weights.score(feats) + (if (isGold) (-lossWeight * recallErrorLossWeight) else lossWeight)
//  }
}

@SerialVersionUID(1L)
class ProductCRFPredictor(val computer: ProductCRFComputer,
                          val weights: Array[Double]) extends Model with Serializable {
  val wrappedWeights = new AdagradWeightVector(weights, 0.0, 0.0)
  
  def predict(datum: Document): LabeledDocument = {
    computer.decode(new LabeledDocument(datum, Seq().asJava), wrappedWeights)
  }
}
