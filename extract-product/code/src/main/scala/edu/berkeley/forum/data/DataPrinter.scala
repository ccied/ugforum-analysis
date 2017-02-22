package edu.berkeley.forum.data

import edu.berkeley.nlp.futile.LightRunner
import scala.collection.JavaConverters._
import edu.berkeley.nlp.futile.util.Logger
import edu.berkeley.forum.data.Dataset.LabeledDocument
import edu.berkeley.nlp.futile.util.Counter
import java.io.File

object DataPrinter {

  val filePrefix = "0-initiator";
  val annotator = "merged";
  
  val maxNpLen = 7
  val includeVerbs = true
  
//  val doUnion = false
  val annotThreshold = 2
  val useNpMajority = false
  
  val printHf = false
  
  val analyzeMultipleLabels = true
  
  def main(args: Array[String]) {
    LightRunner.initializeOutput(DataPrinter.getClass())
    LightRunner.populateScala(DataPrinter.getClass(), args)
    val dataset = Dataset.loadDatasetNew(IndexedSeq("../data/darkode/raw/buy", "../data/darkode/raw/sell_unverified", "../data/darkode/raw/sell_verified", "../data/darkode/raw/other").asJava,
                                         IndexedSeq("../greg_scratch/darkode-buy", "../greg_scratch/darkode-sell-unverified", "../greg_scratch/darkode-sell-verified", "../greg_scratch/darkode-other").asJava,
                                         "../greg_scratch/train-annotations.txt", "../greg_scratch/dev-annotations.txt","../greg_scratch/test-annotations.txt", filePrefix, ".txt.tok", ".txt.tok", "darkode", annotator);
    val hfTrainRaw = Dataset.loadTestDatasetNew(IndexedSeq("../data/hackforum/auto/tok", "../greg_scratch/hackforums_tokenised/all-nocurr-noprem/", "../greg_scratch/hackforums_tokenised/premium_sellers_section/").asJava,
                                            IndexedSeq("../greg_scratch/hackforums", "../greg_scratch/hackforums-nocurr-noprem-parsed/", "../greg_scratch/hackforums-premium-parsed/").asJava,
//                                            "../data/hackforums/annotated/10_14_2015/all-annots-processed.txt", filePrefix, ".txt.tok", ".txt.tok", doUnion);
                                            "data/hackforum-train-all-annots-processed.txt", filePrefix, ".txt.tok", ".txt.tok", "hackforum", annotThreshold);
    val hfTestRaw = Dataset.loadTestDatasetNew(IndexedSeq("../data/hackforum/auto/tok", "../greg_scratch/hackforums_tokenised/all-nocurr-noprem/", "../greg_scratch/hackforums_tokenised/premium_sellers_section/").asJava,
                                            IndexedSeq("../greg_scratch/hackforums", "../greg_scratch/hackforums-nocurr-noprem-parsed/", "../greg_scratch/hackforums-premium-parsed/").asJava,
//                                            "../hackforum-temp-labels/all-annots-processed.txt", filePrefix, ".txt.tok", ".txt.tok", doUnion);
                                            "data/hackforum-test-all-annots-processed.txt", filePrefix, ".txt.tok", ".txt.tok", "hackforum", annotThreshold);
    
    if (printHf) {
      for (doc <- hfTrainRaw.asScala) {
        Logger.logss("==============")
        Logger.logss(doc.document.documentId)
        val npDoc = NPDocument.createFromLabeledDocument(doc, maxNpLen, includeVerbs, useNpMajority)
        for (sentIdx <- 0 until doc.document.lines.size) {
          if (npDoc.labels(sentIdx).foldLeft(false)(_ || _)) {
            Logger.logss(npDoc.renderSentence(sentIdx, true))
          }
        }
      }
    }
    
    if (analyzeMultipleLabels) {
//      Logger.logss("HACKFORUM")
//      analyzeMultipleProducts(hfTrainRaw.asScala)
      Logger.logss("DARKODE")
      analyzeMultipleProducts(dataset.train.asScala)
    }
    
    LightRunner.finalizeOutput()
  }
  
//  def fetchExample(docs: Seq[LabeledDocument]) {
//    
//  }
  
  def analyzeMultipleProducts(docs: Seq[LabeledDocument]) {
    var numOnlyOneHead = 0
    var numOnlyOneRealHead = 0
    var numWithAllOverlapping = 0
    var numWithMultipleProducts = 0
    var numWithMultipleSemanticProducts = 0
    var numPrinted = 0
    for (docIdx <- 0 until docs.size) {
      val doc = docs(docIdx)
      val labeledDoc = NPDocument.createFromLabeledDocument(doc, maxNpLen, includeVerbs, useNpMajority)
      val observedRawProducts = new Counter[TaggedProduct]
      val observedHeads = new Counter[TaggedProduct]
      for (sentIdx <- 0 until labeledDoc.labels.size) {
        for (npIdx <- 0 until labeledDoc.labels(sentIdx).size) {
          if (labeledDoc.labels(sentIdx)(npIdx)) {
            val prod = labeledDoc.doc.getTaggedProduct(sentIdx, npIdx)
            val np = labeledDoc.doc.nps(sentIdx)(npIdx)
//            val prodWords = labeledDoc.doc.doc.lines.get(sentIdx).asScala.slice(np._1, np._3).map(_.toLowerCase)
//            val prodTags = labeledDoc.doc.doc.linesConll.get(sentIdx).asScala.slice(np._1, np._3).map(_.get(4))
//            val prod = new TaggedProduct(prodWords, prodTags)
            observedRawProducts.incrementCount(prod, 1.0)
            observedHeads.incrementCount(prod.slice(np._2 - np._1, np._2 - np._1 + 1), 1.0)
          }
        }
      }
      if (observedRawProducts.totalCount > 1) {
        numWithMultipleProducts += 1
//        Logger.logss(observedProducts + "          " + observedHeads)
        if (observedHeads.keySet.size == 1) {
          numOnlyOneHead += 1
        } else {
          // Print the thing
          val clusteredProducts = ProductClusterer.clusterProducts(observedRawProducts.keySet.asScala.toSet)
          Logger.logss("==============")
          Logger.logss(numPrinted + " DOC " + labeledDoc.doc.doc.documentId + " -- " + clusteredProducts.size + " PRODUCT" + (if (clusteredProducts.size > 1) "S" else ""))
          numPrinted += 1
          val highestLabeledSentence = labeledDoc.getPositiveLabels.map(_._1).foldLeft(0)(Math.max(_, _))
          for (sentIdx <- 0 to highestLabeledSentence) {
            val goldNPs = (0 until labeledDoc.doc.nps(sentIdx).size).filter(i => labeledDoc.labels(sentIdx)(i)).map(i => labeledDoc.doc.nps(sentIdx)(i))
            if (goldNPs.size > 0) {
              val goldProducts = goldNPs.map(np => labeledDoc.doc.getTaggedProduct(sentIdx, np._1, np._3))
              val goldClusterIndices = goldProducts.map(product => clusteredProducts.zipWithIndex.filter(_._1.contains(product)).head._2)
              Logger.logss(NPDocument.renderSentenceWithNPClustering(labeledDoc.doc.doc.lines.get(sentIdx).asScala, goldNPs, goldClusterIndices))
            } else {
              Logger.logss(labeledDoc.doc.doc.lines.get(sentIdx).asScala.reduce(_ + " " + _))
            }
          }
          if (ProductClusterer.doProductsOverlapAndCollapseToOne(observedHeads.keySet.asScala.toSet)) {
            numOnlyOneRealHead += 1
//            Logger.logss("ONLY ONE REAL HEAD:")
//            Logger.logss("          " + observedRawProducts)
          } else if (ProductClusterer.doProductsOverlapAndCollapseToOne(observedRawProducts.keySet.asScala.toSet)) {
            numWithAllOverlapping += 1
//            Logger.logss("ONLY ONE SEMANTIC PRODUCT:")
//            Logger.logss("          " + observedRawProducts)
          } else {
            val semProds = ProductClusterer.clusterProducts(observedRawProducts.keySet.asScala.toSet)
            numWithMultipleSemanticProducts += 1
            Logger.logss("MULTIPLE PRODUCTS: " + doc.document.documentId)
//            Logger.logss("MULTIPLE:")
//            for (semProd <- semProds) {
//              Logger.logss("          " + semProd)
//            }
          }
        }
      }
      Logger.logss("RAW LABELS")
      for (sentIdx <- 0 until labeledDoc.labels.size) {
        Logger.logss(doc.renderLabeledSentence(sentIdx))
      }
    }
    Logger.logss(numOnlyOneHead + " with one head, " + numOnlyOneRealHead + " with >1 head but one semantic head, " + numWithAllOverlapping +
                 " with >1 semantic head but all overlapping, " + numWithMultipleSemanticProducts + " with multiple semantic products / " +
                 numWithMultipleProducts + " with multiple products (" + docs.size + " total docs)")
  }
}
