package edu.berkeley.forum.model

import edu.berkeley.forum.data.LabeledNPDocument
import edu.berkeley.forum.data.NPDocument
import scala.collection.mutable.HashSet
import edu.berkeley.nlp.futile.util.Counter
import edu.berkeley.forum.data.ProductClusterer
import scala.collection.JavaConverters._
import edu.berkeley.forum.data.Dataset.LabeledDocument
import edu.berkeley.nlp.futile.util.Logger
import edu.berkeley.forum.data.Dataset.Document
import scala.collection.mutable.ArrayBuffer
import tuple.Pair

class FrequencyBasedMemorizingPredictor(val canonicalProductNames: Counter[String],
                                        val restrictToPickOne: Boolean) extends Model with Serializable {
  
  def predict(datum: Document): LabeledDocument = {
    val nsAndVsByCount = datum.countDocumentNounsAndVerbs()
    var chosenWord = ""
    // Pick most frequent thing that is a product
    val docPq = nsAndVsByCount.asPriorityQueue()
    while (chosenWord == "" && docPq.hasNext) {
      val word = docPq.next
      val prodNamesPq = canonicalProductNames.asPriorityQueue
      while (chosenWord == "" && prodNamesPq.hasNext) {
        val prodName = prodNamesPq.next
        if (ProductClusterer.areProductsSimilarOrEqual(word, prodName)) {
          chosenWord = word
        }
      }
    }
    // Label ALL occurrences
    val occurrences = new ArrayBuffer[(Int,Int)]
    val sentIndicesToCheck = if (datum.lines.size <= 10) 0 until datum.lines.size else ((0 until 10) ++ (datum.lines.size - 10 until datum.lines.size)).distinct
    for (sentIdx <- sentIndicesToCheck) {
      for (npIdx <- 0 until datum.lines.get(sentIdx).size) {
        val word = ProductClusterer.dropLongestSuffix(datum.lines.get(sentIdx).get(npIdx).toLowerCase)
        if (word == chosenWord) {
          occurrences += sentIdx -> npIdx
        }
      }
    }
//    if (firstOcc == (-1, -1) && chosenWord != "") {
//      Logger.logss("No NP for word " + chosenWord + " in document: " + datum.doc.lines)
//    }
//    val predictions = Array.tabulate(datum.nps.size)(i => {
//      Array.tabulate(datum.nps(i).size)(j => {
//        (i < 10 || i >= datum.nps.size - 10) && ((restrictToPickOne && !occurrences.isEmpty && occurrences.head == (i, j)) ||
//                                                 (!restrictToPickOne && occurrences.contains((i,j))))
//      })
//    })
    new LabeledDocument(datum, occurrences.map(pair => Pair.makePair(new java.lang.Integer(pair._1), new java.lang.Integer(pair._2))).asJava)
  }
  
  def adapt(newLabeledData: Option[Seq[LabeledNPDocument]], newUnlabeledData: Option[Seq[NPDocument]]) {
    // Do nothing
  }
}

object FrequencyBasedMemorizingPredictor {
  def extractPredictor(trainDocs: Seq[LabeledDocument], restrictToPickOne: Boolean): FrequencyBasedMemorizingPredictor = {
    val canonicalProductNames = ProductClusterer.extractCanonicalizedProductCounts(trainDocs)
    new FrequencyBasedMemorizingPredictor(canonicalProductNames, restrictToPickOne)
  }
}

class FrequencyBasedTokenLevelPredictor(restrictToPickOne: Boolean) extends Model with Serializable {
  
  def predict(datum: Document): LabeledDocument = {
    val nsAndVsByCount = datum.countDocumentNounsAndVerbs()
    var chosenWord = ""
    chosenWord = if (!nsAndVsByCount.isEmpty) nsAndVsByCount.asPriorityQueue().next() else ""
    // Find the first occurrence of the word
    val occurrences = new ArrayBuffer[(Int,Int)]
    val sentIndicesToCheck = if (datum.lines.size <= 10) 0 until datum.lines.size else ((0 until 10) ++ (datum.lines.size - 10 until datum.lines.size)).distinct
    for (sentIdx <- sentIndicesToCheck) {
      for (npIdx <- 0 until datum.lines.get(sentIdx).size) {
        val word = ProductClusterer.dropLongestSuffix(datum.lines.get(sentIdx).get(npIdx).toLowerCase)
        if (word == chosenWord) {
          occurrences += sentIdx -> npIdx
        }
      }
    }
    new LabeledDocument(datum, occurrences.map(pair => Pair.makePair(new java.lang.Integer(pair._1), new java.lang.Integer(pair._2))).asJava)
  }
  
  def adapt(newLabeledData: Option[Seq[LabeledNPDocument]], newUnlabeledData: Option[Seq[NPDocument]]) {
    // Do nothing
  }
}

// NP-LEVEL BASELINES

class MemorizingNPPredictor(val headTokens: Set[String]) extends NPModel with Serializable {

  def predict(datum: NPDocument): LabeledNPDocument = {
    val predictions = Array.tabulate(datum.nps.size)(i => {
      Array.tabulate(datum.nps(i).size)(j => {
        (i < 10 || i >= datum.nps.size - 10) && headTokens.contains(datum.doc.lines.get(i).get(datum.nps(i)(j)._2))
      })
    })
    new LabeledNPDocument(datum, predictions)
  }
  
  def adapt(newLabeledData: Option[Seq[LabeledNPDocument]], newUnlabeledData: Option[Seq[NPDocument]]) {
    // Do nothing
  }
}

object MemorizingNPPredictor {
  
  def extractPredictor(trainDocs: Seq[LabeledNPDocument]): MemorizingNPPredictor = {
    val headTokens = new HashSet[String]
    for (doc <- trainDocs) {
      for (i <- 0 until doc.labels.size) {
        for (j <- 0 until doc.labels(i).size) {
          if (doc.labels(i)(j)) {
            headTokens += doc.doc.doc.lines.get(i).get(doc.doc.nps(i)(j)._2)
          }
        }
      }
    }
    new MemorizingNPPredictor(headTokens.toSet)
  }
  
}

class FrequencyBasedMemorizingNPPredictor(val canonicalProductNames: Counter[String],
                                          val restrictToPickOne: Boolean) extends NPDocLevelModel with Serializable {
  
  def predict(datum: NPDocument): LabeledNPDocument = {
    val nsAndVsByCount = datum.doc.countDocumentNounsAndVerbs()
    var chosenWord = ""
    // Pick most frequent thing that is a product
    val docPq = nsAndVsByCount.asPriorityQueue()
    while (chosenWord == "" && docPq.hasNext) {
      val word = docPq.next
      val prodNamesPq = canonicalProductNames.asPriorityQueue
      while (chosenWord == "" && prodNamesPq.hasNext) {
        val prodName = prodNamesPq.next
        if (ProductClusterer.areProductsSimilarOrEqual(word, prodName)) {
          chosenWord = word
        }
      }
    }
    // Pick most frequent product that shows up (flip the order of the two loops above)
//    val prodNamesPq = canonicalProductNames.asPriorityQueue
//    while (chosenWord == "" && prodNamesPq.hasNext) {
//      val prodName = prodNamesPq.next
//      val docPq = nsAndVsByCount.asPriorityQueue()
//      while (chosenWord == "" && docPq.hasNext) {
//        val word = docPq.next
//        if (ProductClusterer.areProductsSimilarOrEqual(word, prodName)) {
//          chosenWord = word
//        }
//      }
//    }
    // Pick the most frequent word
//    chosenWord = if (!nsAndVsByCount.isEmpty) nsAndVsByCount.asPriorityQueue().next() else ""
    // Find the first occurrence of the word
    // Label ALL occurrences
//    var firstOcc = (-1, -1)
    val occurrences = new ArrayBuffer[(Int,Int)]
    for (sentIdx <- 0 until datum.nps.size) {
      for (npIdx <- 0 until datum.nps(sentIdx).size) {
        val np = datum.nps(sentIdx)(npIdx)
        val npSpan = datum.doc.lines.get(sentIdx).asScala.slice(np._1, np._3).map(word => ProductClusterer.dropLongestSuffix(word.toLowerCase))
        if (npSpan.contains(chosenWord)) {
          occurrences += sentIdx -> npIdx
        }
      }
    }
//    if (firstOcc == (-1, -1) && chosenWord != "") {
//      Logger.logss("No NP for word " + chosenWord + " in document: " + datum.doc.lines)
//    }
    val predictions = Array.tabulate(datum.nps.size)(i => {
      Array.tabulate(datum.nps(i).size)(j => {
        (i < 10 || i >= datum.nps.size - 10) && ((restrictToPickOne && !occurrences.isEmpty && occurrences.head == (i, j)) ||
                                                 (!restrictToPickOne && occurrences.contains((i,j))))
      })
    })
    new LabeledNPDocument(datum, predictions)
  }
  
  def adapt(newLabeledData: Option[Seq[LabeledNPDocument]], newUnlabeledData: Option[Seq[NPDocument]]) {
    // Do nothing
  }
}

object FrequencyBasedMemorizingNPPredictor {
 
  def extractPredictor(trainDocs: Seq[LabeledDocument], restrictToPickOne: Boolean): FrequencyBasedMemorizingNPPredictor = {
    val canonicalProductNames = ProductClusterer.extractCanonicalizedProductCounts(trainDocs)
    new FrequencyBasedMemorizingNPPredictor(canonicalProductNames, restrictToPickOne)
  }
  
  def extractPredictorFromNPs(trainDocs: Seq[LabeledNPDocument], restrictToPickOne: Boolean): FrequencyBasedMemorizingNPPredictor = {
    val canonicalProductNames = ProductClusterer.extractCanonicalizedProductCountsFromNPs(trainDocs)
    new FrequencyBasedMemorizingNPPredictor(canonicalProductNames, restrictToPickOne)
  }
}

class FirstNPPredictor() extends NPDocLevelModel with Serializable {
  def predict(datum: NPDocument): LabeledNPDocument = {
    var firstIdxPair = (-1, -1)
    for (i <- 0 until datum.nps.size) {
      for (j <- 0 until datum.nps(i).size) {
        if (firstIdxPair == (-1, -1)) {
          val startIdx =  datum.nps(i)(j)._1
          val isVerb = (datum.nps(i)(j)._3 == startIdx + 1) && datum.doc.linesConll.get(i).get(startIdx).get(4).startsWith("V")
          if (!isVerb) {
            firstIdxPair = (i, j)
          }
        }
      }
    }
    val predictions = Array.tabulate(datum.nps.size)(i => {
      Array.tabulate(datum.nps(i).size)(j => (i, j) == firstIdxPair)
    })
    new LabeledNPDocument(datum, predictions)
  }
  
  def adapt(newLabeledData: Option[Seq[LabeledNPDocument]], newUnlabeledData: Option[Seq[NPDocument]]) {
    // Do nothing
  }
}

class FrequencyBasedPredictor(restrictToPickOne: Boolean) extends NPDocLevelModel with Serializable {
  
  def predict(datum: NPDocument): LabeledNPDocument = {
    val nsAndVsByCount = datum.doc.countDocumentNounsAndVerbs()
    var chosenWord = ""
    chosenWord = if (!nsAndVsByCount.isEmpty) nsAndVsByCount.asPriorityQueue().next() else ""
    // Find the first occurrence of the word
    val occurrences = new ArrayBuffer[(Int,Int)]
    for (sentIdx <- 0 until datum.nps.size) {
      for (npIdx <- 0 until datum.nps(sentIdx).size) {
        val np = datum.nps(sentIdx)(npIdx)
        val npSpan = datum.doc.lines.get(sentIdx).asScala.slice(np._1, np._3).map(word => ProductClusterer.dropLongestSuffix(word.toLowerCase))
        if (npSpan.contains(chosenWord)) {
          occurrences += sentIdx -> npIdx
        }
      }
    }
    val predictions = Array.tabulate(datum.nps.size)(i => {
      Array.tabulate(datum.nps(i).size)(j => {
        (i < 10 || i >= datum.nps.size - 10) && ((restrictToPickOne && !occurrences.isEmpty && occurrences.head == (i, j)) ||
                                                 (!restrictToPickOne && occurrences.contains((i,j))))
      })
    })
//    var firstOcc = (-1, -1)
//    for (sentIdx <- 0 until datum.nps.size) {
//      for (npIdx <- 0 until datum.nps(sentIdx).size) {
//        val np = datum.nps(sentIdx)(npIdx)
//        val npSpan = datum.doc.lines.get(sentIdx).asScala.slice(np._1, np._3).map(word => ProductClusterer.dropLongestSuffix(word.toLowerCase))
//        if (firstOcc == (-1, -1) && npSpan.contains(chosenWord)) {
//          firstOcc = (sentIdx, npIdx)
//        }
//      }
//    }
//    val predictions = Array.tabulate(datum.nps.size)(i => {
//      Array.tabulate(datum.nps(i).size)(j => {
//        (i < 10 || i >= datum.nps.size - 10) && (i, j) == firstOcc
//      })
//    })
    new LabeledNPDocument(datum, predictions)
  }
  
  def adapt(newLabeledData: Option[Seq[LabeledNPDocument]], newUnlabeledData: Option[Seq[NPDocument]]) {
    // Do nothing
  }
}
