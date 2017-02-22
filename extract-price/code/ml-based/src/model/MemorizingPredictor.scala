package model

import data.LabeledNPDocument
import data.NPDocument
import scala.collection.mutable.HashSet
import edu.berkeley.nlp.futile.util.Counter
import data.ProductClusterer
import scala.collection.JavaConverters._
import data.Dataset.LabeledDocument
import edu.berkeley.nlp.futile.util.Logger
import scala.collection.mutable.ArrayBuffer
import data.Dataset.Document
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
