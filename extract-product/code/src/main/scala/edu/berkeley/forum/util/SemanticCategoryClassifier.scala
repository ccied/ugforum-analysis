package edu.berkeley.forum.util

import java.nio.file.Paths
import java.nio.file.Files
import java.io.File
import edu.berkeley.forum.data.Dataset.LabeledDocument
import scala.collection.JavaConverters._
import edu.berkeley.forum.data.Dataset.Document
import edu.berkeley.forum.main.ForumDatasets
import edu.berkeley.forum.model.AdagradWeightVector
import edu.berkeley.forum.model.AbstractProductFeaturizer
import edu.berkeley.forum.model.LikelihoodAndGradientComputerSparse
import edu.berkeley.nlp.futile.util.Counter
import edu.berkeley.nlp.futile.util.IntCounter
import edu.berkeley.nlp.futile.fig.basic.Indexer
import scala.collection.mutable.ArrayBuffer
import edu.berkeley.forum.model.GeneralTrainer
import edu.berkeley.nlp.futile.fig.basic.IOUtils
import edu.berkeley.nlp.futile.util.Logger
import edu.berkeley.nlp.futile.Util
import edu.berkeley.nlp.futile.LightRunner
import edu.berkeley.forum.main.SystemRunner

case class SemanticCategoryExample(fileId: String,
                                   words: IndexedSeq[IndexedSeq[String]],
                                   semanticAutoHeadNoStem: String,
                                   semanticAutoHead: String,
                                   fineAutoCat: String,
                                   coarseAutoCat: String,
                                   semanticCatLabel: String,
                                   semanticCatService: String) {
  var cachedFeats: Array[Array[Int]] = null
  
}

object SemanticCategoryExample {
  
  def loadExs(csvLines: IndexedSeq[IndexedSeq[String]], docs: IndexedSeq[Document]): IndexedSeq[SemanticCategoryExample] = {
    val docsByIdMap = docs.map(doc => doc.documentId -> doc).toMap
    val csvLinesDocsJoined = csvLines.flatMap(line => docsByIdMap.get(line(0)).map(doc => doc -> line))
    csvLinesDocsJoined.map {
      case (doc, line) => new SemanticCategoryExample(line(0), doc.lines.asScala.toIndexedSeq.map(_.asScala.toIndexedSeq), line(2), line(4), // line(1) was here previously
                                                      line(5), line(6), if (line.size >= 8) line(7) else "", if (line.size >= 9) line(8) else "")
    }
  }
}

@SerialVersionUID(1L)
class SemanticCategoryFeaturizer(val featIdx: Indexer[String],
                                 val labelIdx: Indexer[String],
                                 val surfaceFeatIdx: Indexer[String]) {
  
  val NeighborhoodSize = 3
  
  private def maybeAdd(feats: ArrayBuffer[Int], featIndexer: Indexer[String], addToIndexer: Boolean, feat: String) {
    if (addToIndexer) {
      feats += featIndexer.getIndex(feat)
    } else {
      val idx = featIndexer.indexOf(feat)
      if (idx != -1) {
        feats += idx
      }
    }
  }
  
  def extractFeaturesCached(ex: SemanticCategoryExample, addToIndexer: Boolean): Array[Array[Int]] = {
    if (ex.cachedFeats == null) {
      ex.cachedFeats = extractFeatures(ex, addToIndexer)
    }
    ex.cachedFeats
  }
  
  def extractFeatures(ex: SemanticCategoryExample, addToIndexer: Boolean): Array[Array[Int]] = {
    val surfaceFeats = extractSurfaceFeatures(ex, addToIndexer)
    Array.tabulate(labelIdx.getObjects.asScala.size)(i => {
      val label = labelIdx.getObjects.asScala(i)
      val thisLabelFeats = new ArrayBuffer[Int]()
      for (feat <- surfaceFeats) {
        maybeAdd(thisLabelFeats, featIdx, addToIndexer, label + ":" + feat)
      }
      thisLabelFeats.toArray
    })
  }
  
  def extractSurfaceFeatures(ex: SemanticCategoryExample, addToIndexer: Boolean): Array[Int] = {
    val feats = new ArrayBuffer[Int]()
    maybeAdd(feats, surfaceFeatIdx, addToIndexer, "AutoHead=" + ex.semanticAutoHead)
    maybeAdd(feats, surfaceFeatIdx, addToIndexer, "AutoHeadOneWord=" + ex.semanticAutoHeadNoStem.toLowerCase)
    maybeAdd(feats, surfaceFeatIdx, addToIndexer, "AutoHeadPlural=" + ex.semanticAutoHeadNoStem.endsWith("s"))
//    maybeAdd(feats, surfaceFeatIdx, addToIndexer, "FineCat=" + ex.fineAutoCat)
//    maybeAdd(feats, surfaceFeatIdx, addToIndexer, "CoarseCat=" + ex.coarseAutoCat)
    // NEARBY WORDS
//    val headNPWords = ex.semanticAutoHead.split(" ")
//    val headNPLocs = new ArrayBuffer[(Int,Int)]
//    // Find all occurrences of the NP in the post
//    for (sentIdx <- 0 until ex.words.size) yield {
//      for (wordIdx <- 0 to ex.words(sentIdx).size - headNPWords.size) {
//        if (ex.words(sentIdx).slice(wordIdx, wordIdx + headNPWords.size).reduce(_ + " " + _) == headNPWords.reduce(_ + " " + _)) {
//          headNPLocs += sentIdx -> wordIdx
//        }
//      }
//    }
//    if (headNPLocs.isEmpty) {
//      Logger.logss("WARNING: couldn't find NP " + headNPWords.toIndexedSeq + " in doc " + ex.fileId)
//      ex.words.foreach(line => Logger.logss(line))
//    }
//    headNPLocs.foreach {
//      case (sentIdx, wordIdx) => for (i <- Math.max(0, wordIdx - NeighborhoodSize) until Math.min(ex.words(sentIdx).size, wordIdx + NeighborhoodSize)) {
//        maybeAdd(feats, surfaceFeatIdx, addToIndexer, "NearbyWord" + (i - wordIdx) + "=" + ex.words(sentIdx)(i))
//      }
//    }
    // WORDS
//    for (sent <- ex.words) {
//      for (word <- sent) {
//        maybeAdd(feats, surfaceFeatIdx, addToIndexer, "Word=" + word)
//      }
//    }
    feats.toArray
  }
}


@SerialVersionUID(1L)
class SemanticCategoryComputer(val featurizer: SemanticCategoryFeaturizer) extends LikelihoodAndGradientComputerSparse[SemanticCategoryExample] with Serializable {
  
  def getInitialWeights(initialWeightsScale: Double): Array[Double] = Array.tabulate(featurizer.featIdx.size)(i => 0.0)
  
  def accumulateGradientAndComputeObjective(ex: SemanticCategoryExample, weights: AdagradWeightVector, gradient: IntCounter): Double = {
    val (pred, predScore) = decode(ex, weights, 1.0);
    if (pred != ex.semanticCatLabel) {
      val goldLabelIdx = featurizer.labelIdx.getIndex(ex.semanticCatLabel)
      val predFeats = ex.cachedFeats(featurizer.labelIdx.getIndex(pred))
      val goldFeats = ex.cachedFeats(goldLabelIdx)
      for (feat <- goldFeats) {
        gradient.incrementCount(feat, 1)
      }
      for (feat <- predFeats) {
        gradient.incrementCount(feat, -1)
      }
      Math.abs(predScore) - score(ex, weights, goldLabelIdx, 0.0)
    } else {
      // Do nothing and incur no loss
      0.0
    }
  }
  
  def computeObjective(ex: SemanticCategoryExample, weights: AdagradWeightVector): Double = accumulateGradientAndComputeObjective(ex, weights, new IntCounter)
  
  def decode(ex: SemanticCategoryExample, weights: AdagradWeightVector): String = {
    decode(ex, weights, 0)._1
  }
  
  private def decode(ex: SemanticCategoryExample, weights: AdagradWeightVector, lossWeight: Double): (String, Double) = {
    val feats = featurizer.extractFeaturesCached(ex, false)
    val scores = (0 until featurizer.labelIdx.size).map(idx => {
      score(ex, weights, idx, lossWeight)
    })
    val bestScoreAndIdx = scores.zipWithIndex.maxBy(_._1)
    (featurizer.labelIdx.getObject(bestScoreAndIdx._2) -> bestScoreAndIdx._1)
  }
  
  private def score(ex: SemanticCategoryExample, weights: AdagradWeightVector, choice: String, lossWeight: Double): Double = {
    score(ex, weights, featurizer.labelIdx.getIndex(choice), lossWeight)
  }
  
  private def score(ex: SemanticCategoryExample, weights: AdagradWeightVector, choiceIdx: Int, lossWeight: Double): Double = {
    val feats = featurizer.extractFeaturesCached(ex, false)
    val goldLabelIdx = featurizer.labelIdx.getIndex(ex.semanticCatLabel)
    weights.score(feats(choiceIdx)) + (if (choiceIdx == goldLabelIdx) 0.0 else lossWeight)
  }
}

@SerialVersionUID(1L)
class SemanticCategoryPredictor(val computer: SemanticCategoryComputer,
                                val weights: Array[Double]) extends Serializable {
  val wrappedWeights = new AdagradWeightVector(weights, 0, 0)
  
  def getWrappedWeights = wrappedWeights
  
  def decode(ex: SemanticCategoryExample): String = {
    computer.decode(ex, wrappedWeights)
  }
}

object SemanticCategoryClassifier {
  
  def loadSemCatExsDarkode = {
    val darkodeDocs = ForumDatasets.loadDarkodeNoAnnots(dataReleasePath, filePrefix, fileSuffix).asScala.toIndexedSeq
    val csvLines = CategoryLabelConverter.loadCsv("/Users/gdurrett/Documents/code/security/forum-analy/product_extraction/expers/SystemRunner/darkode-extractions2.csv")
    val augmentedCsvLines = CategoryLabelConverter.augmentCsv(csvLines, IndexedSeq("/Users/gdurrett/Documents/code/security/forum-analy/Error-analysis/darkode-semcats/"))
    SemanticCategoryExample.loadExs(augmentedCsvLines, darkodeDocs).filter(_.semanticCatLabel != "")
  }
  
  def loadSemCatExsHackforums = {
    val hfDocs = ForumDatasets.loadHackforumsNoAnnots(hfAllProcessedPrefix, filePrefix, fileSuffix).asScala.toIndexedSeq
    val csvLines = CategoryLabelConverter.loadCsv("/Users/gdurrett/Documents/code/security/forum-analy/product_extraction/expers/SystemRunner/hackforums-extractions2.csv")
    val augmentedCsvLines = CategoryLabelConverter.augmentCsv(csvLines, IndexedSeq("/Users/gdurrett/Documents/code/security/forum-analy/Error-analysis/hackforums-semcats/"))
    SemanticCategoryExample.loadExs(augmentedCsvLines, hfDocs).filter(_.semanticCatLabel != "")
  }
  
  def exploreSemCats(exs: IndexedSeq[SemanticCategoryExample]) {
    System.out.println("SEMANTIC CATEGORY COUNTS")
    val semCatCounts = new Counter[String]
    for (semCat <- exs.map(_.semanticCatLabel)) {
      semCatCounts.incrementCount(semCat, 1.0)
    }
    displayCounter(semCatCounts)
    Logger.logss("SEMANTIC CATEGORIES BY AUTO CATEGORY")
    val allAutoCats = exs.map(_.fineAutoCat).toSet.toIndexedSeq.sorted
    for (autoCat <- allAutoCats) {
      val semCats = new Counter[String]
      for (ex <- exs.filter(_.fineAutoCat == autoCat)) {
        semCats.incrementCount(ex.semanticCatLabel, 1.0)
      }
      System.out.println(autoCat + ": " + semCats.totalCount + " " + semCats.toString)
    }
    System.out.println("AUTO CATEGORIES BY SEMANTIC CATEGORY")
    val allSemCats = exs.map(_.semanticCatLabel).toSet.toIndexedSeq.sorted
    for (semCat <- allSemCats) {
      val autoCats = new Counter[String]
      for (ex <- exs.filter(_.semanticCatLabel == semCat)) {
        autoCats.incrementCount(ex.fineAutoCat, 1.0)
      }
      System.out.println(semCat + ": " + autoCats.totalCount + " " + autoCats.toString)
    }
  }
  
  def coarsenSemCatLabels(label: String) = {
    val labelToUse = if (stripBuySell && label.indexOf("_") >= 0) {
      "#" + label.substring(label.indexOf("_") + 1)
    } else {
      label
    }
    coarsenMapping.get(labelToUse).getOrElse(labelToUse)
  }
  
  def getLabelsToUse: IndexedSeq[String] = {
//    if (useManualCoarsening) {
//      coarsenMapping.values.toSet.toIndexedSeq.sorted
//    } else {
    labelsToUse.toIndexedSeq
//    }
  }
  
  def displayCounter(counts: Counter[String]) {
    val keys = counts.asPriorityQueue()
    while (keys.hasNext) {
      val key = keys.next
      System.out.println(key + ": " + counts.getCount(key))
    }
  }
  
  def coarsenSemLabelsTryRuleBased(exs: IndexedSeq[SemanticCategoryExample]) {
    val coarsenedExs = exs.map(ex => ex.copy(semanticCatLabel = coarsenSemCatLabels(ex.semanticCatLabel)))
    val predExs = coarsenedExs.filter(ex => ex.fineAutoCat == "bulk account" || ex.fineAutoCat == "hacked account" || ex.fineAutoCat == "account").map(_.fileId).toSet
    val goldExs = coarsenedExs.filter(ex => ex.semanticCatLabel == "#ACCT").map(_.fileId).toSet
    Logger.logss(renderPRF1((predExs & goldExs).size, predExs.size, goldExs.size))
  }
  
  def renderPRF1(corr: Int, precDenom: Int, recDenom: Int) = {
    "Prec = " + Util.renderStandardPercentage(corr, precDenom) + ", Rec = " +
                Util.renderStandardPercentage(corr, recDenom) + ", F1 = " +
                Util.fmtTwoDigitNumber(Util.computeF1(corr, precDenom, recDenom) * 100); 
  }
  
  def makeTrainTestSets(semCatExsPairedWithProductExs: IndexedSeq[(IndexedSeq[SemanticCategoryExample], IndexedSeq[LabeledDocument])]): (IndexedSeq[SemanticCategoryExample], IndexedSeq[SemanticCategoryExample]) = {
    val mustBeInTrain = new ArrayBuffer[SemanticCategoryExample]
    val canBeEither = new ArrayBuffer[SemanticCategoryExample]
    semCatExsPairedWithProductExs.foreach(_ match {
      case (semCatExsTmp, productExs) => {
        val semCatExsOfInterest = semCatExsTmp.map(ex => ex.copy(semanticCatLabel = coarsenSemCatLabels(ex.semanticCatLabel))).filter(ex => getLabelsToUse.contains(ex.semanticCatLabel))
        val semCatSet = semCatExsOfInterest.map(_.fileId)
        val trainSet = productExs.map(_.document.documentId)
        val overlap = semCatSet.toSet & trainSet.toSet
        Logger.logss(semCatSet.size + " sem cat exs, " + trainSet.size + " train exs, " + overlap.size + " overlap")
        mustBeInTrain ++= semCatExsOfInterest.filter(ex => overlap.contains(ex.fileId))
        canBeEither ++= semCatExsOfInterest.filterNot(ex => overlap.contains(ex.fileId))
      }
      case _ => ()
    })
    val rng = new scala.util.Random(0)
    val canBeEitherShuffled = rng.shuffle(canBeEither)
    val numTrainExs = ((mustBeInTrain.size + canBeEitherShuffled.size) * 0.8).toInt
    val train = rng.shuffle(mustBeInTrain ++ canBeEitherShuffled.take(numTrainExs - mustBeInTrain.size))
    val test = rng.shuffle(canBeEitherShuffled.drop(numTrainExs - mustBeInTrain.size))
    Logger.logss(train.size + " train exs (" + mustBeInTrain.size + " were forced to be in train), " + test.size + " test exs")
    train -> test 
  }
  
  val useManualCoarsening = true
  
//  val labels = CategoryLabelConverter.catMapping.keySet.toIndexedSeq.sorted
  
//  val coarsenLevel = 1
//  val labelsToUse = Set("#ACCT_HACKED", "#ACCT_BULK", "#ACCT_OGNAME", "#ACCT_STAT", "#SW_ACCT_CTRL", "#ACCT_EMAIL", "#OSN_LINK", "#LIST_EMAIL")
//  val coarsenLevel = 2
//  val labelsToUse = Set("#ACCT_BULK", "#ACCT_STAT", "#SW_ACCT_CTRL", "#ACCT_EMAIL", "#OSN_LINK", "#LIST_EMAIL")
//  val labelsToUse = Set("#ACCT_BULK", "#ACCT_STAT")
//  val coarsenLevel = 3
//  val labelsToUse = Set("#ACCT_STAT", "#SW_ACCT_CTRL", "#ACCT_EMAIL", "#OSN_LINK", "#LIST_EMAIL")
  
//  val coarsenLevel = 4
//  val labelsToUse = Set("#ACCT_BULK", "#ACCT_STAT", "#SW_ACCT_CTRL", "#ACCT_EMAIL", "#OSN_LINK", "#LIST_EMAIL")
//  val labelsToUse = Set("#ACCT_BULK", "#ACCT_STAT")
  
//  val stripBuySell = false
//  val coarsenMapping = Map("#WANT_ACCT_HACKED" -> "#WANT_ACCT",
//                           "#WANT_ACCT_BULK" -> "#WANT_ACCT",
//                           "#WANT_ACCT_STAT" -> "#WANT_ACCT",
//                           "#SELL_ACCT_HACKED" -> "#SELL_ACCT",
//                           "#SELL_ACCT_BULK" -> "#SELL_ACCT",
//                           "#SELL_ACCT_STAT" -> "#SELL_ACCT")
  
//  val labelsToUse = Set("#ACCT_BULK", "#ACCT_STAT")
//  val stripBuySell = true
//  val coarsenMapping = Map("#ACCT_OGNAME" -> "#ACCT_STAT",
//                           "#ACCT_HACKED" -> "#ACCT_BULK")
                           
  val labelsToUse = Set("#ACCT_BULK", "#ACCT_STAT", "#ACCT_HACKED")
  val stripBuySell = true
  val coarsenMapping = Map("#ACCT_OGNAME" -> "#ACCT_STAT")
  
  val filePrefix = "0-initiator"
  val fileSuffix = ".txt.tok"
  
  val dataReleasePath: String = ""
  
  val hfAllProcessedPrefix: String = "/Users/gdurrett/Documents/code/security/forum-analy/greg_scratch/hackforums-all-nocurr-parsed"
  
  val numItrs = 5
  val eta = 0.1
  val reg = 1e-8
  val batchSize = 1
  
  val modelPath = ""
  
//  val serviceGlossary = Set("steam", "youtube", "instagram", "league of legends", "lol", "kik", "hitleap", "clash of clans",
//                            "twitter", "twitch", "uk", "netflix","paypal", "hotmail", "facebook", "minecraft")
    
  val serviceGlossary = Set("steam", "youtube", "instagram", "league-of-legends", "kik", "hitleap", "clash-of-clans",
                            "twitter", "twitch", "uk", "netflix","paypal", "hotmail", "facebook", "minecraft")
                            
  val serviceGlossaryMap = Set("steam", "youtube", "instagram", "league-of-legends", "kik", "hitleap", "clash-of-clans",
                            "twitter", "twitch", "uk", "netflix","paypal", "hotmail", "facebook", "minecraft").map(word => word -> word).toMap ++ 
                           Map("lol" -> "league-of-legends", "league of legends" -> "league-of-legends", "clash of clans" -> "clash-of-clans",
                               "fb" -> "facebook", "yt" -> "youtube")
  
  def dataDisplay(darkodeExs: IndexedSeq[SemanticCategoryExample], hackforumsExs: IndexedSeq[SemanticCategoryExample]) {
    val services = new Counter[String]
    val allExs = darkodeExs ++ hackforumsExs
//    System.out.println("ORIG")
//    exploreSemCats(allExs)
//    System.out.println("\n\n\n\n\nCOARSENED")
//    exploreSemCats(allExs.map(ex => ex.copy(semanticCatLabel = coarsenSemCatLabelsLevelX(ex.semanticCatLabel, 1))))
//    System.out.println("\n\n\n\n\nFURTHER COARSENED")
//    exploreSemCats(allExs.map(ex => ex.copy(semanticCatLabel = coarsenSemCatLabelsLevelX(ex.semanticCatLabel, 2))))
//    System.out.println("\n\n\n\n\nEVEN FURTHER COARSENED")
//    exploreSemCats(allExs.map(ex => ex.copy(semanticCatLabel = coarsenSemCatLabelsLevelX(ex.semanticCatLabel, 3))))
    
    
    allExs.foreach(ex => services.incrementCount(ex.semanticCatService, 1.0))
    SystemRunner.renderTopN(services, 100)
    val numWithSome = (darkodeExs ++ hackforumsExs).filter(ex => {
      val wordSausage = ex.words.reduce(_ ++ _).reduce(_ + " " + _).toLowerCase
//      val matchingWords = serviceGlossary.filter(service => wordSausage.contains(service))
      val matchingWords = serviceGlossaryMap.keySet.filter(service => wordSausage.contains(service))
//      val words = ex.words.reduce(_ ++ _).map(_.toLowerCase())
//      val matchingWords = serviceGlossary.filter(service => words.contains(service))
//      (words.toSet & serviceGlossary).size > 0
      matchingWords.size > 0
    }).size
    val numCorrect = (darkodeExs ++ hackforumsExs).filter(ex => {
      val wordSausage = ex.words.reduce(_ ++ _).reduce(_ + " " + _).toLowerCase
//      val matchingWords = serviceGlossary.filter(service => wordSausage.contains(service))
      val matchingWords = serviceGlossaryMap.keySet.filter(service => wordSausage.contains(service))
//      val words = ex.words.reduce(_ ++ _).map(_.toLowerCase())
//      val matchingWords = serviceGlossary.filter(service => words.contains(service))
      matchingWords.size == 1 && serviceGlossaryMap(matchingWords.head) == ex.semanticCatService
    }).size
    System.out.println(numWithSome + " had some service, " + numCorrect + " were correct out of " + allExs.filter(_.semanticCatService != "").size + " with a gold service")
    
    val coarsenedExs = (darkodeExs ++ hackforumsExs).map(ex => ex.copy(semanticCatLabel = coarsenSemCatLabels(ex.semanticCatLabel)))
    val exsSubset = coarsenedExs.filter(ex => ex.semanticCatLabel == "#ACCT_HACKED" || ex.semanticCatLabel == "#ACCT_BULK" || ex.semanticCatLabel == "#ACCT_STAT")
    for (ex <- exsSubset) {
      Logger.logss("========================")
      Logger.logss(ex.semanticCatLabel + " " + ex.semanticCatService)
      ex.words.foreach(sent => Logger.logss(sent.foldLeft("")(_ + " " + _).trim))
    }
    
    LightRunner.finalizeOutput()
    System.exit(0)
  }
  
  def main(args: Array[String]) {
    LightRunner.initializeOutput(SemanticCategoryClassifier.getClass())
    LightRunner.populateScala(SemanticCategoryClassifier.getClass(), args)
    val darkodeExs = loadSemCatExsDarkode
    val darkodeProductExs = ForumDatasets.loadDarkode(dataReleasePath, filePrefix, fileSuffix).train.asScala.toIndexedSeq
    val hackforumsExs = loadSemCatExsHackforums
    val hackforumsProductExs = ForumDatasets.loadHackforumsTrainDev(dataReleasePath, filePrefix, fileSuffix).asScala.toIndexedSeq
    System.out.println(darkodeExs.size + hackforumsExs.size)
    
    var numPos = 0
    for (ex <- hackforumsProductExs ++ darkodeProductExs) {
      if (ex.positiveLabels.size > 0) {
        numPos += 1
      }
    }
    System.out.println(numPos + "/" + (hackforumsProductExs ++ darkodeProductExs).size)
    System.exit(0)
    
    val (trainSet, testSet) = makeTrainTestSets(IndexedSeq((darkodeExs, darkodeProductExs), (hackforumsExs, hackforumsProductExs)))
    
    dataDisplay(darkodeExs, hackforumsExs)
    
    // Build featurizer
    val featIdx = new Indexer[String]
    val labelIdx = new Indexer[String]
    val labels = (trainSet ++ testSet).map(_.semanticCatLabel).toSet.toIndexedSeq.sorted
    for (label <- labels) {
      labelIdx.add(label)
    }
    Logger.logss("Label indices: " + labelIdx)
    val surfaceFeatIdx = new Indexer[String]
    val featurizer = new SemanticCategoryFeaturizer(featIdx, labelIdx, surfaceFeatIdx)
    for (ex <- trainSet) {
      featurizer.extractFeaturesCached(ex, true)
    }
    Logger.logss(surfaceFeatIdx)
    
    val model = {
      val computer = new SemanticCategoryComputer(featurizer)
      val initialWeights = computer.getInitialWeights(0)
      Logger.logss(initialWeights.size + " total features")
      val weights = new GeneralTrainer(true).trainAdagradSparse(trainSet, computer, eta, reg, batchSize, numItrs, initialWeights, verbose = true);
      new SemanticCategoryPredictor(computer, weights)
    }
    if (modelPath != "") {
      IOUtils.writeObjFile(modelPath, model)
    }
    Logger.logss("TRAIN EVALUATION")
    evaluate(model, trainSet)
    Logger.logss("\n\n\nTEST EVALUATION")
    evaluate(model, testSet)
  }
  
  def evaluate(model: SemanticCategoryPredictor, testSet: IndexedSeq[SemanticCategoryExample]) {
    val labelIdx = model.computer.featurizer.labelIdx
    // Evaluate
    val confusionMatrix = Array.tabulate(labelIdx.size, labelIdx.size)((i, j) => 0)
    var acc = 0.0
    for (ex <- testSet) {
      val pred = model.decode(ex)
      if (pred == ex.semanticCatLabel) {
        acc += 1.0
      }
      confusionMatrix(labelIdx.indexOf(pred))(labelIdx.indexOf(ex.semanticCatLabel)) += 1
    }
    Logger.logss("TEST RESULTS: " + Util.renderNumerDenom(acc, testSet.size))
    Logger.logss("CONFUSION: Ordering = " + labelIdx)
    for (predIdx <- 0 until labelIdx.size) {
      Logger.logss(confusionMatrix(predIdx).foldLeft("")(_ + " " + _).trim)
//      for (goldIdx <- 0 until labelIdx.size) {
//        Logger.logss(labelIdx.getObject(predIdx) + " / " + labelIdx.getObject(goldIdx) + " = " + confusionMatrix(predIdx)(goldIdx))
//      }
    }
    LightRunner.finalizeOutput()
  }
}
