package edu.berkeley.forum.data

import edu.berkeley.nlp.futile.LightRunner
import scala.collection.JavaConverters._
import edu.berkeley.nlp.futile.util.Logger
import edu.berkeley.nlp.futile.util.Counter
import edu.berkeley.forum.data.Dataset.LabeledDocument

object ProductFrequencyAnalyzer {

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
    LightRunner.initializeOutput(ProductFrequencyAnalyzer.getClass())
    LightRunner.populateScala(ProductFrequencyAnalyzer.getClass(), args)
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
    
    analyzeProductRank(dataset.train.asScala)
    analyzeProductRank(hfTrainRaw.asScala)
    
    LightRunner.finalizeOutput()
  }
  
  def analyzeProductRank(allDocs: Seq[LabeledDocument]) {
    val canonicalProductNames = ProductClusterer.extractCanonicalizedProductCounts(allDocs)
    Logger.logss(canonicalProductNames)
    val allRanks = new Counter[Int]
    for (doc <- allDocs) {
      val npDoc = NPDocument.createFromLabeledDocument(doc, maxNpLen, includeVerbs, useNpMajority)
      val prodName = ProductClusterer.identifyCanonicalProduct(npDoc, canonicalProductNames)
//      val allProducts = npDoc.getAllTaggedProducts
//      val clusteredProducts = ProductClusterer.clusterProducts(allProducts.toSet)
//      // Try and extract a canonical name for the product in this document
//      var prodName = ""
//      val clustersBySize = clusteredProducts.toSeq.sortBy(- _.size)
//      for (cluster <- clustersBySize) {
//        if (prodName == "") {
//          val pq = canonicalProductNames.asPriorityQueue()
//          while (prodName == "" && pq.hasNext) {
//            val canonicalName = pq.next
//            for (prod <- cluster) {
//              if (ProductClusterer.areProductsOverlapping(prod, canonicalName)) {
//                prodName = canonicalName
//              }
//            }
//          }
//        }
//      }
      // Now see how frequently that word occurs in the document compared to other
      // N*, V*
      val nsAndVsByCount = new Counter[String]
      for (sentIdx <- 0 until doc.document.lines.size) {
        for (wordIdx <- 0 until doc.document.lines.get(sentIdx).size) {
          val tag = doc.document.linesConll.get(sentIdx).get(wordIdx).get(4)
          if (tag.startsWith("N") || tag.startsWith("V")) {
            val stemmedLcWord = ProductClusterer.dropLongestSuffix(doc.document.lines.get(sentIdx).get(wordIdx).toLowerCase)
            nsAndVsByCount.incrementCount(stemmedLcWord, 1.0)
          }
        }
      }
      var rank = 0
      val pq = nsAndVsByCount.asPriorityQueue()
      var done = false
      while (!done && pq.hasNext) {
        val word = pq.next
        if (ProductClusterer.areProductsSimilarOrEqual(prodName, word)) {
          done = true
        }
        rank += 1
      }
      allRanks.incrementCount(rank, 1.0)
      
    }
    for (i <- 0 until 20) {
      Logger.logss(i + ": " + allRanks.getCount(i))
    }
  }
}
