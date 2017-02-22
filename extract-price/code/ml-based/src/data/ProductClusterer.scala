package data

import scala.collection.JavaConverters._
import edu.berkeley.nlp.futile.util.Logger
import scala.collection.mutable.HashSet
import scala.collection.mutable.ArrayBuffer
import data.Dataset.LabeledDocument
import edu.berkeley.nlp.futile.util.Counter
import data.Dataset.Document

case class TaggedProduct(val words: Seq[String],
                         val tags: Seq[String]) {
  require(words.size == tags.size, words + " " + tags)
  
  def slice(startIdx: Int, endIdx: Int) = new TaggedProduct(words.slice(startIdx, endIdx), tags.slice(startIdx, endIdx)) 
  
  override def equals(other: Any): Boolean = {
    other != null && other.isInstanceOf[TaggedProduct] &&
      this.words.sameElements(other.asInstanceOf[TaggedProduct].words) &&
      this.tags.sameElements(other.asInstanceOf[TaggedProduct].tags)
  }
  
  override def hashCode(): Int = {
    words.hashCode() * 113612367 + tags.hashCode
  }
  
  override def toString(): String = {
    "Product(" + words.zip(tags).map(wordTag => wordTag._1 + "-" + wordTag._2).reduce(_ + " " + _) + ")"
  }
}

object ProductClusterer {
  
  def hasProductBeenSeenBefore(products: Set[String], newProduct: String) = {
    var seenBefore = false
    for (product <- products) {
      if (!seenBefore) {
        seenBefore = seenBefore || product == newProduct || areProductsSimilarNotEqual(product, newProduct)
      }
    }
    seenBefore
  }
  
  val suffixes = Seq("ing", "ed", "er", "ers", "s", "es")
  // Whitelist "accounts" so that we don't stem it
  val whitelist = Seq("crypter", "server", "downloader", "access", "ddos", "passport", "pass", "need", "rdps", "coder", "site", "vps", "accounts")
  
  def dropLongestSuffix(p1: String): String = {
    var whitelistResult = ""
    for (item <- whitelist) {
      if (p1.startsWith(item)) {
        whitelistResult = item
      }
    }
    if (whitelistResult != "") {
      whitelistResult
    } else {
      var longestSuffix = ""
      for (suffix <- suffixes) {
        if (p1.endsWith(suffix) && suffix.size > longestSuffix.size) {
          longestSuffix = suffix
        }
      }
      p1.substring(0, p1.size - longestSuffix.size)
    }
  }
  
  def areProductsSimilarOrEqual(p1: String, p2: String) = {
    p1 == p2 || areProductsSimilarNotEqual(p1, p2)
  }
  
  def areProductsSimilarNotEqual(p1: String, p2: String) = {
    if (p1 == p2) {
      false
    } else {
      val p1LcStemmed = dropLongestSuffix(p1.toLowerCase)
      val p2LcStemmed = dropLongestSuffix(p2.toLowerCase)
      if (p1LcStemmed == p2LcStemmed) {
//        Logger.logss(p1 + " " + p2)
        true
      } else {
//        val ed = EditDistance.editDistance(p1.toSeq.asJava, p2.toSeq.asJava)
//        (p1.size >= 5 && p2.size >= 5 && ed <= 1) || (p1.size >= 8 && p2.size >= 8 && ed <= 2)
        val ed = EditDistance.editDistance(p1LcStemmed.toSeq.asJava, p2LcStemmed.toSeq.asJava)
        (p1LcStemmed.size >= 5 && p2LcStemmed.size >= 5 && ed <= 1) || (p1LcStemmed.size >= 8 && p2LcStemmed.size >= 8 && ed <= 2)
      }
    }
  }
  
  def areProductsOverlapping(p1: TaggedProduct, p2: String): Boolean = {
    areProductsOverlapping(p1, new TaggedProduct(Seq(p2), Seq("N")))
  }
  
  def areProductsOverlapping(p1: TaggedProduct, p2: TaggedProduct): Boolean = {
    val p1Words = p1.words
    val p1Tags = p1.tags
    val p2Words = p2.words
    val p2Tags = p2.tags 
    var overlapping = false
    for (idx1 <- 0 until p1Words.size) {
      val word1 = p1Words(idx1)
      val tag1 = p1Tags(idx1)
//      if (p2.contains(word1)) {
//        overlapping = true
//      }
      if (!overlapping && (tag1.startsWith("N") || tag1.startsWith("V"))) {
        for (idx2 <- 0 until p2Words.size) {
          val word2 = p2Words(idx2)
          val tag2 = p2Tags(idx2)
          if (!overlapping && (tag2.startsWith("N") || tag2.startsWith("V")) && (word1 == word2 || areProductsSimilarNotEqual(word1, word2))) {
            overlapping = true
          }
        }
      }
    }
    overlapping
  } 

//  def clusterProducts(products: Seq[String]) {
//    for (p1 <- products) {
//      for (p2 <- products) {
//        if (p1 != p2) {
//          if (areProductsSimilarNotEqual(p1, p2)) {
//            Logger.logss("MATCH: " + p1  + " " + p2)
//          }
//        }
//      }
//    }
//  }
  
  def doProductsOverlapAndCollapseToOne(products: Set[TaggedProduct], verbose: Boolean = false): Boolean = {
    var mergedSet = new HashSet[TaggedProduct]
    val productsSeq = products.toSeq
    mergedSet += productsSeq.head
    var someChangeMade = true
    while (someChangeMade && mergedSet.size < products.size) {
      val newMergedSet = new HashSet[TaggedProduct] ++ mergedSet
      for (product <- productsSeq) {
        if (!newMergedSet.contains(product)) {
          for (mergedProduct <- mergedSet) {
            if (areProductsOverlapping(product, mergedProduct)) {
//              if (verbose) Logger.logss("Overlap: " + product + " || " + mergedProduct)
              newMergedSet += product
            }
          }
        }
      }
      if (newMergedSet.size > mergedSet.size) {
        mergedSet = newMergedSet
        someChangeMade = true
      } else {
        someChangeMade = false
      }
    }
    if (verbose) Logger.logs(mergedSet + " " + products)
    mergedSet.size == products.size
  }
  
  def clusterProducts(products: Set[TaggedProduct]): Seq[HashSet[TaggedProduct]] = {
    val productSeq = products.toSeq
    val itemsToClusters = Array.tabulate(productSeq.size)(i => -1)
    var unusedClusterIdx = 0
    for (productIdx <- 0 until products.size) {
      itemsToClusters(productIdx) = unusedClusterIdx
      unusedClusterIdx += 1
      var addedToSomething = false
      for (otherProductIdx <- 0 until productIdx) {
        if (areProductsOverlapping(productSeq(productIdx), productSeq(otherProductIdx))) {
          val c1 = itemsToClusters(productIdx)
          val c2 = itemsToClusters(otherProductIdx)
          for (i <- 0 until itemsToClusters.size) {
            if (itemsToClusters(i) == c2) {
              itemsToClusters(i) = c1
            }
          }
        }
      }
    }
    val finalClusters = new ArrayBuffer[HashSet[TaggedProduct]]
    for (clusterIdx <- 0 until unusedClusterIdx) {
      if (itemsToClusters.contains(clusterIdx)) {
        // Pull out the item indices in this cluster
        val thisCluster = new HashSet[TaggedProduct] ++ (0 until productSeq.size).filter(i => itemsToClusters(i) == clusterIdx).map(itemIdx => productSeq(itemIdx))
        finalClusters += thisCluster
      }
    }
    finalClusters
  }
  
  def doProductsCollapseToOne(products: Set[String]): Boolean = {
    val productsSeq = products.toSeq
    var doCollapse = false
    for (centroid <- productsSeq) {
      var isGoodCentroid = true
      for (product <- products) {
        if (product != centroid && !areProductsSimilarNotEqual(product, centroid)) {
          isGoodCentroid = false
        }
      }
      doCollapse = doCollapse || isGoodCentroid
    }
    doCollapse
  }
  
  def extractCanonicalizedProductCounts(trainingSet: Seq[LabeledDocument]): Counter[String] = {
    val canonicalProducts = new Counter[String]
    for (doc <- trainingSet) {
      for (posLabel <- doc.positiveLabels.asScala) {
        val word = doc.document.lines.get(posLabel.getFirst()).get(posLabel.getSecond())
        canonicalProducts.incrementCount(dropLongestSuffix(word.toLowerCase()), 1.0)
      }
    }
    // TODO: Additional merging/clustering step?
    canonicalProducts
  }
  
  def extractCanonicalizedProductCountsFromNPs(trainingSet: Seq[LabeledNPDocument]): Counter[String] = {
    val canonicalProducts = new Counter[String]
    for (doc <- trainingSet) {
      for (posLabel <- doc.getPositiveLabels) {
        val headIdx = doc.doc.nps(posLabel._1)(posLabel._2)._2
        val headWord = doc.doc.doc.lines.get(posLabel._1).get(headIdx)
        canonicalProducts.incrementCount(dropLongestSuffix(headWord.toLowerCase()), 1.0)
      }
    }
    canonicalProducts
  }
  
  def identifyCanonicalProduct(npDoc: LabeledNPDocument, canonicalProductNames: Counter[String]) = {
    val allProducts = npDoc.getAllTaggedProducts
    val clusteredProducts = ProductClusterer.clusterProducts(allProducts.toSet)
      // Try and extract a canonical name for the product in this document
    var prodName = ""
    val clustersBySize = clusteredProducts.toSeq.sortBy(- _.size)
    for (cluster <- clustersBySize) {
      if (prodName == "") {
        val pq = canonicalProductNames.asPriorityQueue()
        while (prodName == "" && pq.hasNext) {
          val canonicalName = pq.next
          for (prod <- cluster) {
            if (ProductClusterer.areProductsOverlapping(prod, canonicalName)) {
              prodName = canonicalName
            }
          }
        }
      }
    }
//    Logger.logss("Clusters: " + clusteredProducts)
//    Logger.logss("Canonical name: " + prodName)
    prodName
  }
}