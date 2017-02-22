package edu.berkeley.forum.util

import edu.berkeley.forum.main.ForumDatasets
import scala.collection.JavaConverters._
import edu.berkeley.nlp.futile.util.Counter
import edu.berkeley.forum.main.SystemRunner
import edu.berkeley.forum.data.ProductClusterer
import edu.berkeley.forum.data.Dataset.LabeledDocument
import edu.berkeley.forum.data.Dataset.Document
import edu.berkeley.nlp.futile.LightRunner
import edu.berkeley.nlp.futile.util.Logger

object ForumDistributionRenderer {
  
  val filePrefix = "0-initiator"
  val fileSuffix = ".txt.tok"
  
  val dataReleasePrefix: String = "/Users/gdurrett/n/security/forum-analy/forum-data/"
  val hfProcessedPrefix: String = "/Users/gdurrett/Documents/code/security/forum-analy/greg_scratch/hackforums-all-nocurr-parsed"
  
  val darkodeCsvPath = "/Users/gdurrett/n/security/forum-analy/product_extraction/expers/SystemRunner/darkode-extractions2.csv"
  val hackforumsCsvPath = "/Users/gdurrett/n/security/forum-analy/product_extraction/expers/SystemRunner/hackforums-extractions2.csv"

  def main(args: Array[String]) {
    LightRunner.initializeOutput(ForumDistributionRenderer.getClass())
    LightRunner.populateScala(ForumDistributionRenderer.getClass(), args)
    System.out.println("Darkode")
    val darkodeProductExs = ForumDatasets.loadDarkodeNoAnnots(dataReleasePrefix, filePrefix, fileSuffix).asScala.toIndexedSeq
    val darkodeCsv = CategoryLabelConverter.loadCsv(darkodeCsvPath)
    val (dc1, dc2) = render(darkodeProductExs, darkodeCsv, true)
    System.out.println("\n\n\n")
    System.out.println("HF")
    val hackforumsProductExs = ForumDatasets.loadHackforumsNoAnnots(hfProcessedPrefix, filePrefix, fileSuffix).asScala.toIndexedSeq
    val hackforumsCsv = CategoryLabelConverter.loadCsv(hackforumsCsvPath)
    val (hc1, hc2) = render(hackforumsProductExs, hackforumsCsv, true)
    val (_, hcnp2) = render(hackforumsProductExs, hackforumsCsv, false)
    
    val dc1Pq = dc1.asPriorityQueue()
    val dc2Pq = dc2.asPriorityQueue()
    val hc1Pq = hc1.asPriorityQueue()
    val hc2Pq = hc2.asPriorityQueue()
    val hcnp2Pq = hcnp2.asPriorityQueue()
    for (i <- 0 until 10) {
      Logger.logss(dc1Pq.next + " & " + hc1Pq.next + " & " + dc2Pq.next + " & " + hc2Pq.next + " & " + hcnp2Pq.next + " \\\\")
    }
    LightRunner.finalizeOutput()
  }
  
  def render(exs: IndexedSeq[Document], csvLines: IndexedSeq[IndexedSeq[String]], useHeadWord: Boolean) = {
    val productHeadDist = new Counter[String]()
    // Look at either 3 (stemmed head) or 4 (full NP)
    def destem(str: String) = if (str == "servic") "service" else str
    for (line <- csvLines) {
      productHeadDist.incrementCount(if (useHeadWord) destem(line(3)) else line(4), 1.0)
    }
    
    val nounVerbDist = new Counter[String]
    for (ex <- exs) {
      for (conllSent <- ex.linesConll.asScala) {
        for (conllWord <- conllSent.asScala) {
          val pos = conllWord.get(4)
          val word = conllWord.get(1)
//          if (pos.startsWith("N") || pos.startsWith("V")) {
          if (pos.startsWith("N") && word.forall(c => Character.isLetterOrDigit(c))) {
            nounVerbDist.incrementCount(ProductClusterer.dropLongestSuffix(word.toLowerCase()), 1.0)
          }
        }
      }
    }
    System.out.println("PRODUCT EXTRACTOR")
    SystemRunner.renderTopN(productHeadDist, 20)
    System.out.println("NOUNS/VERBS")
    SystemRunner.renderTopN(nounVerbDist, 20)
    nounVerbDist -> productHeadDist 
  }
}
