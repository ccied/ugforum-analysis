package edu.berkeley.forum.data

import edu.berkeley.forum.data.Dataset.LabeledDocument
import scala.collection.JavaConverters._
import edu.berkeley.forum.data.Dataset.Document
import scala.collection.mutable.HashSet
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import edu.berkeley.nlp.futile.util.Logger
import edu.berkeley.nlp.futile.util.Counter

class NPDocument(val doc: Document,
                 val nps: Array[Seq[(Int,Int,Int)]]) {
  
  def getTaggedProduct(sentIdx: Int, npIdx: Int): TaggedProduct = {
    val np = nps(sentIdx)(npIdx)
    getTaggedProduct(sentIdx, np._1, np._3)
  }
  
  def getTaggedProduct(sentIdx: Int, startIdx: Int, endIdx: Int): TaggedProduct = {
    val prodWords = doc.lines.get(sentIdx).asScala.slice(startIdx, endIdx).map(_.toLowerCase)
    val prodTags = doc.linesConll.get(sentIdx).asScala.slice(startIdx, endIdx).map(_.get(4))
    new TaggedProduct(prodWords, prodTags)
  }
}

class NPDocumentPosition(val doc: NPDocument,
                         val lineIdx: Int,
                         val npIdx: Int,
                         val isProduct: Boolean) {
  var cachedFeatures: Array[Int] = null;
  var cachedDenseWordInputs: Array[Int] = null;
  var isSingleton: Option[Boolean] = None
}

// Labels are indexed by (sentence, NP index)
class LabeledNPDocument(val doc: NPDocument,
                        val labels: Array[Array[Boolean]],
                        val maybeLabelCounts: Option[Array[Array[Int]]] = None,
                        val maybeRawTokLabels: Option[Seq[(Int,Int)]] = None) {
  
  // N.B. This stuff is used primarily for the document-level NP model
  val npDocumentPositions = Array.tabulate(doc.nps.size)(i => Array.tabulate(doc.nps(i).size)(j => new NPDocumentPosition(doc, i, j, labels(i)(j))))
  var cachedNoneFeatures: Array[Int] = null
  
  def getPositiveLabels = (0 until labels.size).flatMap(i => (0 until labels(i).size).flatMap(j => if (labels(i)(j)) Seq(i -> j) else Seq()))
  
  def getAllTaggedProducts: Seq[TaggedProduct] = getPositiveLabels.map(pair => doc.getTaggedProduct(pair._1, pair._2))
  
  def getLabelCount(sentIdx: Int, npIdx: Int) = if (maybeLabelCounts.isDefined) maybeLabelCounts.get(sentIdx)(npIdx) else -1
  
  def renderSentence(idx: Int, onlyDrawBracketsForGolds: Boolean = false) = {
    val npsToUse = if (onlyDrawBracketsForGolds) {
      (0 until doc.nps(idx).size).filter(labels(idx)(_)).map(doc.nps(idx)(_))
    } else {
      doc.nps(idx)
    }
    NPDocument.renderSentence(doc.doc.lines.get(idx).asScala,
                              npsToUse,
                              if (maybeRawTokLabels.isDefined) maybeRawTokLabels.get.filter(_._1 == idx).map(_._2) else Seq[Int](),
                              None)
  }
}

object NPDocument {
  
  def createFromDocument(doc: Document, maxNpLen: Int, includeVerbs: Boolean): NPDocument = {
    new NPDocument(doc, NPDocument.extractNPs(doc, maxNpLen, includeVerbs))
  }

  def createFromLabeledDocument(labeledDoc: LabeledDocument, maxNpLen: Int, includeVerbs: Boolean, useNpLevelVoting: Boolean) = {
    val npDoc = createFromDocument(labeledDoc.document, maxNpLen, includeVerbs)
    val rawLabels = labeledDoc.positiveLabels.asScala.map(intPair => intPair.getFirst.intValue -> intPair.getSecond.intValue)
    val npLabels = labelNps(labeledDoc, npDoc.nps, useNpLevelVoting)
    new LabeledNPDocument(npDoc, npLabels._1, npLabels._2, Some(rawLabels))
  }
  
  var contained = 0
  var total = 0
  
  /**
   * Right now extract maximal NPs and then filter if they're over length.
   * We could also cut off projection if it exceeds a certain length
   */
  def extractHeadedNPs(pos: Seq[String], parents: Seq[Int], maxNpLen: Int, includeVerbs: Boolean): Seq[(Int,Int,Int)] = {
    val parentChildMap = new HashMap[Int,ArrayBuffer[Int]]
    for (i <- 0 until parents.size) {
      if (parents(i) != -1) {
        if (!parentChildMap.contains(parents(i))) {
          parentChildMap.put(parents(i), new ArrayBuffer[Int])
        }
        parentChildMap(parents(i)) += i
      }
    }
    val maximalProjections = new HashSet[Int]
    val npHeadedSpans = new HashSet[(Int,Int,Int)]
    val spansEachWord = (0 until parents.size).map(i => findSpan(i, parentChildMap))
    for (i <- 0 until parents.size) {
      // Find the set of maximal projections of N* tags, subject to the constraint
      // that they're shorter than maxNpLen
      if (pos(i).startsWith("N")) {
        var currNode = i
        if (spansEachWord(currNode)._2 - spansEachWord(currNode)._1 > maxNpLen) {
          npHeadedSpans += new Tuple3(currNode, currNode, currNode + 1)
        } else {
          while (parents(currNode) != -1 && pos(parents(currNode)).startsWith("N") && spansEachWord(parents(currNode))._2 - spansEachWord(parents(currNode))._1 <= maxNpLen) {
            currNode = parents(currNode)
          }
          val head = currNode
          val span = spansEachWord(head)
          npHeadedSpans += new Tuple3(span._1, head, span._2)
          if (span._2 - span._1 > maxNpLen) {
            Logger.logss("PROBLEM")
            Logger.logss(i + " " + head + " " + span)
          }
        }
      }
    }
    if (includeVerbs) {
      for (i <- 0 until parents.size) {
        if (pos(i).startsWith("V")) {
          npHeadedSpans += new Tuple3(i, i, i+1)
        }
      }
    }
    npHeadedSpans.toSeq.sortBy(_._1)
  }
  
  private def findSpan(idx: Int, parentChildMap: HashMap[Int,ArrayBuffer[Int]]): (Int, Int) = {
    if (!parentChildMap.contains(idx)) {
      (idx, idx+1)
    } else {
      var begin = idx
      var end = idx + 1
      for (child <- parentChildMap(idx)) {
        val childSpan = findSpan(child, parentChildMap)
        begin = Math.min(begin, childSpan._1)
        end = Math.max(end, childSpan._2)
      }
      (begin, end)
    }
  }
  
  private def doesOverlap(np1: (Int,Int,Int), np2: (Int,Int,Int)) = {
//    (np1._1 <= np2._1 && np1._2 > np2._1) || (np1._1 < np2._2 && np1._2 >= np2._2) ||
//      isContained(np1, np2) || isContained(np2, np1)
    (np1._1 <= np2._1 && np1._3 > np2._1) || (np1._1 < np2._3 && np1._3 >= np2._3) ||
      isContained(np1, np2) || isContained(np2, np1)
  }
  
  private def isContained(npInner: (Int,Int,Int), npOuter: (Int,Int,Int)) = {
//    npInner._1 >= npOuter._1 && npInner._2 <= npOuter._2
    npInner._1 >= npOuter._1 && npInner._3 <= npOuter._3
  }
  
  private def renderNp(np: (Int,Int,Int), words: Seq[String]) = {
    var str =""
    for (i <- np._1 until np._3) {
      str += (if (i == np._2) "[" + words(i) + "]" else words(i)) + " "
    }
    str.trim
  }
  
  def renderSentence(words: Seq[String], nps: Seq[(Int,Int,Int)], goldToks: Seq[Int], goldToks2: Option[Seq[Int]] = None): String = {
    var str = ""
    for (i <- 0 until words.size) {
      val numStarting = nps.filter(_._1 == i).size
      val numHeading = nps.filter(_._2 == i).size
      val numEnding = nps.filter(_._3 - 1 == i).size
      for (j <- 0 until numStarting) {
        str += "("
      }
      for (j <- 0 until numHeading) {
        str += "["
      }
      if (goldToks2.isDefined && goldToks2.get.contains(i)) {
        str += "@"
      }
      if (goldToks.contains(i)) {
        str += "*"
      }
      str += words(i)
      if (goldToks.contains(i)) {
        str += "*"
      }
      if (goldToks2.isDefined && goldToks2.get.contains(i)) {
        str += "@"
      }
      for (j <- 0 until numHeading) {
        str += "]"
      }
      for (j <- 0 until numEnding) {
        str += ")"
      }
      str += " "
    }
    str.trim
  }
  
  def renderSentenceWithGoldPredNps(words: Seq[String], goldNps: Seq[(Int,Int,Int)], predNps: Seq[(Int,Int,Int)]): String = {
    var str = ""
    for (i <- 0 until words.size) {
      val numGoldStarting = goldNps.filter(_._1 == i).size
      val numGoldHeading = goldNps.filter(_._2 == i).size
      val numGoldEnding = goldNps.filter(_._3 - 1 == i).size
      val numPredStarting = predNps.filter(_._1 == i).size
      val numPredHeading = predNps.filter(_._2 == i).size
      val numPredEnding = predNps.filter(_._3 - 1 == i).size
      for (j <- 0 until numGoldStarting) {
        str += "(G("
      }
      for (j <- 0 until numPredStarting) {
        str += "(P("
      }
      for (j <- 0 until numGoldHeading) {
        str += "["
      }
      for (j <- 0 until numPredHeading) {
        str += "["
      }
      str += words(i)
      for (j <- 0 until numGoldHeading) {
        str += "]"
      }
      for (j <- 0 until numPredHeading) {
        str += "]"
      }
      for (j <- 0 until numPredEnding) {
        str += ")P)"
      }
      for (j <- 0 until numGoldEnding) {
        str += ")G)"
      }
      str += " "
    }
    str.trim
  }
  
  def renderSentenceWithNPClustering(words: Seq[String], goldNps: Seq[(Int,Int,Int)], clusters: Seq[Int]): String = {
    var str = ""
    for (i <- 0 until words.size) {
      val numStarting = goldNps.filter(_._1 == i).size
      // Shortest ones should have their ends printed first, so we sort by -start index
      val npsEnding = goldNps.filter(_._3 - 1 == i).sortBy(- _._1)
      for (j <- 0 until numStarting) {
        str += "["
      }
      str += words(i)
      for (npEnding <- npsEnding) {
        str += "]_" + clusters(goldNps.indexOf(npEnding))
      }
      str += " "
    }
    str.trim
  }
  
  def extractNPs(doc: Document, maxNpLen: Int, includeVerbs: Boolean): Array[Seq[(Int,Int,Int)]] = {
    // Convert the List<List<List>> to scala's Seq[Seq[Seq]]
    val conllSents = doc.linesConll.asScala.map(_.asScala.map(_.asScala))
    Array.tabulate(conllSents.size)(i => {
      val conllSent = conllSents(i)
      val words = conllSent.map(_(1))
      val pos = conllSent.map(_(4))
      // Subtract 1 so that the parent indices are 0-based and -1 is the root
      val parents = conllSent.map(_(6).toInt - 1)
      val npsWithHeads = extractHeadedNPs(pos, parents, maxNpLen, includeVerbs)
      for (targetNp <- npsWithHeads) {
        require(npsWithHeads.filter(_._2 == targetNp._2).size == 1, "Two with same head!")
        val overlaps = npsWithHeads.filter(np => doesOverlap(targetNp, np) && targetNp != np)
      }
      npsWithHeads
    })
  }
  
  def labelNps(labeledDoc: LabeledDocument, nps: Array[Seq[(Int,Int,Int)]], useNpLevelVoting: Boolean): (Array[Array[Boolean]], Option[Array[Array[Int]]]) = {
    val positiveLabels = labeledDoc.positiveLabels.asScala
    val npLabels = new ArrayBuffer[Array[Boolean]]
    val npLabelCounts = new ArrayBuffer[Array[Int]]
    for (sentIdx <- 0 until nps.size) {
      require(!useNpLevelVoting, "NP-level voting is no longer implemented")
      val thisSentNpPositiveIndices = new ArrayBuffer[Int]
      for (positiveIndexThisSentence <- positiveLabels.filter(sentAndPosn => sentAndPosn.getFirst.intValue == sentIdx).map(_.getSecond.intValue)) {
        val maybeSmallestNp = nps(sentIdx).filter(np => np._1 <= positiveIndexThisSentence && positiveIndexThisSentence < np._3).sortBy(np => np._3 - np._1).headOption
        for (smallestNp <- maybeSmallestNp) {
          thisSentNpPositiveIndices += nps(sentIdx).indexOf(smallestNp)
        }
      }
      val thisSentNpLabels = (0 until nps(sentIdx).size).map(i => thisSentNpPositiveIndices.contains(i))
      npLabels += thisSentNpLabels.toArray
      val thisSentNpLabelCounts = new ArrayBuffer[Int]()
      npLabelCounts += thisSentNpLabelCounts.toArray
    }
    npLabels.toArray -> (if (useNpLevelVoting) Some(npLabelCounts.toArray) else None)
    
//    var thisContained = 0
//    val thisTotal = positiveLabels.size
//    for (positiveLabel <- positiveLabels) {
//      val i = positiveLabel.getFirst
//      val idx = positiveLabel.getSecond
//      val goldNpsThisSent = (0 until nps(i).size).filter(npIdx => npLabels(i)(npIdx)).map(nps(i)(_))
//      if (goldNpsThisSent.filter(np => np._1 <= idx && idx < np._3).size > 0) {
//        thisContained += 1
//      }
//    } 
//    Logger.logss("====================")
//    Logger.logss(labeledDoc.document.documentId)
//    for (i <- 0 until nps.size) {
//      val npsThisSent = nps(i)
//      Logger.logss(renderSentence(labeledDoc.document.lines.get(i).asScala, npsThisSent, positiveLabels.filter(_.getFirst == i).map(_.getSecond.intValue)))
//    }
//    contained += thisContained
//    total += thisTotal
//    Logger.logss("THIS: " + thisContained + " / " + thisTotal)
//    Logger.logss("CUMULATIVE: " + contained + " / " + total)
//    npLabels
  }
}
