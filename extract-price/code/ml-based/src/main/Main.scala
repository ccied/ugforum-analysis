package main

import scala.collection.JavaConverters._
import data.Dataset
import edu.berkeley.nlp.futile.LightRunner
import edu.berkeley.nlp.futile.Util
import edu.berkeley.nlp.futile.fig.basic.IOUtils
import edu.berkeley.nlp.futile.fig.basic.Indexer
import edu.berkeley.nlp.futile.util.Counter
import edu.berkeley.nlp.futile.util.Logger
import model.DocumentPosition
import model.GeneralTrainer
import model.ProductComputer
import model.ProductPredictor
import model.ProductFeaturizer
import data.NPDocument
import data.NPDocumentPosition
import data.Dataset.LabeledDocument
import eval.EvalStats
//import model.MemorizingNPPredictor
import data.ProductClusterer
import scala.collection.mutable.HashMap
import java.util.Calendar
//import model.FrequencyBasedMemorizingNPPredictor
import data.LabeledNPDocument
//import model.FrequencyBasedPredictor
import model.Model
import model.ModelUtils
import model.FrequencyBasedTokenLevelPredictor
import model.FrequencyBasedMemorizingPredictor
import model.ProductDocLevelTokenComputer
import model.ProductDocLevelTokenPredictor
import model.ProductCRFFeaturizer
import model.ProductCRFComputer
import model.ProductCRFPredictor
import scala.collection.mutable.ArrayBuffer

object Main {

  // Note: all of these are command-line arguments. Pass them in with -<argName> <argValue> (argValue can be omitted if it's a boolean
  // that you're setting to true)
  val filePrefix = "0-initiator";
  val fileSuffix = ".txt";
  val fileSuffix_POS = ".txt.out.conll.parse";
  val annotator = "merged";
  
  // Hyperparameters for learning
  //how many times you iterate through the dataset before quitting
  //worth experimenting with (5 is the best), rest are not worth experimenting with
  val numItrs = 5
  //mutliply the gradient by eta. adagrad optimization - for every weight in the weight vector
  //it learns a scaling factor for how it updates that weight. 0.1 is another scaling factor
  //for the whole thing
  val eta = 0.1
  val reg = 1e-8
  //the # of examples you look at before taking the next gradient step
  val batchSize = 1
  
  // These are set for majority vote right now (normal HF = 2/3, HF test = 3/4)
  val hfAnnotThreshold = 2
  val hfTestAnnotThreshold = 2
  
  val doCds = true
  
  // Controls the mapping from token labels to NP labels
  //do the noun phrase system
  val doNps = false
  //what's the biggest noun phrase that has the labeled product token as its head word
  //so this is the max number of tokens - more, then you end up just grabbing the whole sentence as the noun phrase
  //bc of weirdness in the parser
  val maxNpLen = 7
  //allow verbs to be products
  val includeVerbs = true
  // Do majority-voting at the NP level rather than at the token level
  val useNpMajority = false
  //token vs. NP comparison
  val doComparison = false
  
  // Run the holistic document-level version of the system 
  val doDocumentLevel = false
  val prohibitNull = false
  
  val doCRF = false
  
  val doFrequencyBaseline = false
  val doDictionaryBaseline = false
  
  // The default is train on Darkode, test on both Darkode and Hackforum
  val trainOnHackforum = false
  val trainOnBothTestOnBoth = false
  
  // True if we should run on the blind test set to get final results
  val runOnTest = true
  
  val doSig = false
  
  // Controls the precision-recall tradeoff for the token-level system
  //increasing this improves recall, runs from 0 to infinity
  val recallErrorLossWeight = 1.0
  
  // Controls the precision-recall tradeoff for the NP system
  // 2.0 or 4.0 sometimes give higher F1
  //increasing this improves recall, runs from 0 to infinity
  //for hackforum 4.0 is better
  val npRecallErrorLossWeight = 1.0
  
  val singletonLossWeight = 2.0
  //set it to 5 for the experiments
  //puts a coefficient of 5 in front of every product that only occurs once
  //any gradient from that example will be upweighted by a factor of 5. making it 5 times more important in your loss function
  
  val doLearningCurve = false
  
  // Print more logs of specific errors, particularly relating to prediction of
  // products unseen in the training set
  val printDetailedErrors = true
  
  // Path to write trained model to; null if we shouldn't bother saving the model
  val modelPath = ""
  
  // Restricts the classifier to only look at the token under consideration
  val restrictToCurrent = false
  // Prevents the classifier from looking at the token under consideration
  val blockCurrent = false
//  val blockCurrent = true
  // Path to Brown clusters
  val useBrown = true
  val brownPath = "data/dhf-brown-50"
  //only use brown clusters for any words that occur at least 2 times
  val brownCutoff = 10
  val lexicalFeatThreshold = 5
  val useParentFeatures = true
  
  val useGazetteer = false
  val gazetteerThreshold = 4
  
  val doDomainAdaptationExperiment = false
  val adaptationScalingFactor = 1
  val useDomainFeatures = false
  
  // Neural network parameters; unused now that this is deprecated
  val doNeural = false
//  val word2vecPath = "data/dhf-vecs.txt"
//  val numHidden = 100
//  val numHiddenLayers = 1
  
  // Auxiliary files to load in time information
//  val timeFilesPath = ""
  val useTime = false
  val timeFilesPath = "data/darkode.csv,data/hackforums.csv"
  
  def main(args: Array[String]) {
    LightRunner.initializeOutput(Main.getClass())
    LightRunner.populateScala(Main.getClass(), args)
    
    
    val dataset = Dataset.loadDatasetNew(IndexedSeq("../data/darkode/raw/buy", "../data/darkode/raw/sell_unverified", "../data/darkode/raw/sell_verified").asJava,
                                         IndexedSeq("../greg_scratch/darkode-buy", "../greg_scratch/darkode-sell-unverified", "../greg_scratch/darkode-sell-verified").asJava,
                                         "../greg_scratch/price-train-annotations.txt", "../greg_scratch/price-dev-annotations.txt","../greg_scratch/price-test-annotations.txt", filePrefix, ".txt.tok", ".txt.tok", "darkode", annotator);
    val dataset2 = Dataset.loadDatasetNew(IndexedSeq("../greg_scratch/all-hack-2").asJava,
                                           IndexedSeq("../greg_scratch/hackforums-nocurr-noprem-parsed/", "../greg_scratch/hackforums-premium-parsed/").asJava,
                                         "../greg_scratch/hf-price-train-annotations.txt", "../greg_scratch/hf-price-dev-annotations.txt","../greg_scratch/hf-price-test-annotations.txt", filePrefix, ".txt.tok", ".txt.tok", "hackforums", annotator);
 
    
    
    //sort data
    val sorteddktrain = dataset.train.asScala.sortBy(labeledDoc => labeledDoc.document.documentId)
    val training = sorteddktrain.slice(0, 83)
    val extratraining = sorteddktrain.slice(83, 112)
    
     val (train, test) = if (runOnTest) {
      (training ++ dataset2.train.asScala, extratraining ++ dataset.dev.asScala ++ dataset.test.asScala)
    } else {
      (dataset.train.asScala, dataset.test.asScala)
    }
    
     Logger.logss("Training size->" + train.size)
     Logger.logss("Testing size->" + test.size)
     
     trainSystemDoErrorAnalysis(train, test, Some(train), Seq(test))
     
    LightRunner.finalizeOutput()
  }
  

  
  def trainSystemDoErrorAnalysis(train: Seq[LabeledDocument],
                                 test: Seq[LabeledDocument],
                                 auxTrain: Option[Seq[LabeledDocument]],
                                 auxTest: Seq[Seq[LabeledDocument]]) {
    // BASELINES
    if (doFrequencyBaseline) {
      
        val frequencyBaseline = new FrequencyBasedTokenLevelPredictor(true)
        for (testSet <- Seq(test) ++ auxTest) {
          evaluateTokenLevel(frequencyBaseline, testSet, doSig)
        }
      
    } else if (doDictionaryBaseline) {
      
      
        val dictionaryBaseline = FrequencyBasedMemorizingPredictor.extractPredictor(train, restrictToPickOne = false)
        for (testSet <- Seq(test) ++ auxTest) {
          evaluateTokenLevel(dictionaryBaseline, testSet, doSig)
        }
      
    }
    // DOCUMENT-LEVEL SYSTEM
    else if (doDocumentLevel) {
      
        val tokDocSystem = trainDocumentLevelTokenSystem(train, test)
        if (!auxTest.isEmpty) {
          tokDocSystem.adapt(auxTrain, None)
          for (auxTestSet <- auxTest) {
            evaluateTokenLevel(tokDocSystem, auxTestSet, doSig)
//            doDocumentLevelErrorAnalysis(train, auxTestSet, tokDocSystem)
          }
        }
      
    } else { // TOKEN-LEVEL SYSTEM
      if (doCRF) {
        val crfTokenSystem = trainCRFTokenSystem(train, test)
        for (auxTestSet <- auxTest) {
          evaluateTokenLevel(crfTokenSystem, auxTestSet, doSig)
        }
      } else {
        Logger.logss("****print statistics  ***");
    
        val tokenSystem = trainTokenSystem(train, test)
        for (auxTestSet <- auxTest) {
          evaluateTokenLevel(tokenSystem, auxTestSet, doSig)
          printStatistics(auxTestSet)
          doErrorAnalysis(train, test, tokenSystem)
        }
      }
    }
  }
  
  def removeTrailingPunc(word: String) = {
    var currWord = word;
    while (currWord.size > 0 && !Character.isLetterOrDigit(currWord.last)) {
      currWord = currWord.dropRight(1)
    }
    currWord
  }
  
  /**
   * Train the token version of the system (not over NPs)
   */
  def trainTokenSystem(train: Seq[LabeledDocument], dev: Seq[LabeledDocument]): ProductPredictor = {
    
    val trainWordCounts = new Counter[String]
    train.foreach(_.document.lines.asScala.foreach(_.asScala.foreach(word => trainWordCounts.incrementCount(word, 1.0))))
     val trainExsRaw = train.flatMap(ex => (0 until ex.document.lines.size).flatMap(lineIdx => {
      (0 until ex.document.lines.get(lineIdx).size).map(wordIdx => {
        val isGold = ex.positiveLabels.contains(tuple.Pair.makePair(lineIdx, wordIdx))
        new DocumentPosition(ex.document, lineIdx, wordIdx, isGold)
      })
    }))
    val trainExs = new scala.util.Random(0).shuffle(trainExsRaw)
    
    Logger.logss("Extracting training set features")
    val featIdx = new Indexer[String]
    val maybeBrownClusters = if (useBrown && brownPath != "") Some(ProductFeaturizer.loadBrownClusters(brownPath, brownCutoff)) else None
    val maybeGazetteer = if (useGazetteer) Some(buildGazetteer(train)) else None
    val featurizer = new ProductFeaturizer(featIdx, trainWordCounts, lexicalFeatThreshold, maybeBrownClusters = maybeBrownClusters, gazetteer = maybeGazetteer, useDomainFeatures = useDomainFeatures, useParentFeatures = useParentFeatures)
    trainExs.foreach(ex => featurizer.extractFeaturesCached(ex, true))
    val trainProductLcCounts = computeTrainProductLcCounts(train)
    
    val computer = new ProductComputer(featurizer, recallErrorLossWeight, trainProductLcCounts, singletonLossWeight)
    
    val initialWeights = computer.getInitialWeights(0)
    Logger.logss(initialWeights.size + " total features")
    val weights = new GeneralTrainer(true).trainAdagradSparse(trainExs, computer, eta, reg, batchSize, numItrs, initialWeights, verbose = true);
    val model = new ProductPredictor(computer, weights)
    if (modelPath != "") {
      IOUtils.writeObjFile(modelPath, model)
    }
    
    evaluateTokenLevel(model, dev, doSig)
    
    // FEATURES
//    val featCounter = new Counter[String]
//    for (i <- 0 until featIdx.size) {
//      featCounter.incrementCount(featIdx.getObject(i), weights(i))
//    }
//    featCounter.keepTopNKeysByAbsValue(100)
//    val pq = featCounter.asPriorityQueue()
//    Logger.logss("TOP FEATURES")
//    while (pq.hasNext()) {
//      val key = pq.next
//      Logger.logss(key + ": " + featCounter.getCount(key))
//    }
    
    model
  }
  
  /**
   * Train the CRF version of the token system (not over NPs)
   */
  def trainCRFTokenSystem(train: Seq[LabeledDocument], dev: Seq[LabeledDocument]): ProductCRFPredictor = {
    val trainWordCounts = new Counter[String]
    train.foreach(_.document.lines.asScala.foreach(_.asScala.foreach(word => trainWordCounts.incrementCount(word, 1.0))))
    val trainExs = new scala.util.Random(0).shuffle(train)
    
    Logger.logss("Extracting training set features")
    val featIdx = new Indexer[String]
    val maybeBrownClusters = if (useBrown && brownPath != "") Some(ProductFeaturizer.loadBrownClusters(brownPath, brownCutoff)) else None
    val maybeGazetteer = if (useGazetteer) Some(buildGazetteer(train)) else None
    val featurizer = new ProductFeaturizer(featIdx, trainWordCounts, lexicalFeatThreshold, maybeBrownClusters = maybeBrownClusters, gazetteer = maybeGazetteer, useDomainFeatures = useDomainFeatures, useParentFeatures = useParentFeatures)
    val wrappedFeaturizer = new ProductCRFFeaturizer(featurizer)
    trainExs.foreach(ex => wrappedFeaturizer.extractFeaturesCached(ex, true))
    val trainProductLcCounts = computeTrainProductLcCounts(train)
    
    val computer = new ProductCRFComputer(wrappedFeaturizer, recallErrorLossWeight, trainProductLcCounts, singletonLossWeight)
    
    val initialWeights = computer.getInitialWeights(0)
    Logger.logss(initialWeights.size + " total features")
    val weights = new GeneralTrainer(true).trainAdagradSparse(trainExs, computer, eta, reg, batchSize, numItrs, initialWeights, verbose = true);
    val model = new ProductCRFPredictor(computer, weights)
    if (modelPath != "") {
      IOUtils.writeObjFile(modelPath, model)
    }
    
    evaluateTokenLevel(model, dev, doSig)
    
    model
  }

  
  
  ////////////////////////////
  // DOCUMENT LEVEL SYSTEMS //
  ////////////////////////////
  
  def trainDocumentLevelTokenSystem(train: Seq[LabeledDocument], dev: Seq[LabeledDocument]): ProductDocLevelTokenPredictor = {
    trainDocumentLevelTokenSystem(train, dev, computeTrainProductLcCounts(train))
  }
  
  def trainDocumentLevelTokenSystem(trainDocsRaw: Seq[LabeledDocument], devDocs: Seq[LabeledDocument], trainProductCounts: Counter[String]): ProductDocLevelTokenPredictor = {
    val trainDocsPreShuf = if (prohibitNull) {
      trainDocsRaw.filter(_.positiveLabels.size > 0)
    } else {
      trainDocsRaw
    }
    // Count the words in the training set. These counts are used in the featurizer to decide when to fire features
    // (we don't fire features on words if they're too rare)
    val trainWordCounts = new Counter[String]
    trainDocsPreShuf.foreach(_.document.lines.asScala.foreach(_.asScala.foreach(word => trainWordCounts.incrementCount(word, 1.0))))
    // Turns the LabeledDocuments (which are labeled at the token level) into NP-labeled documents. This mapping has
    // some free parameters; it isn't a trivial procedure.
    val trainDocs = new scala.util.Random(0).shuffle(trainDocsPreShuf)
    
    Logger.logss("Extracting training set features")
    // Build the featurizer and cache features in the training data
    val featIdx = new Indexer[String]
    val maybeBrownClusters = if (useBrown && brownPath != "") Some(ProductFeaturizer.loadBrownClusters(brownPath, brownCutoff)) else None
    
    val maybeGazetteer = if (useGazetteer) Some(buildGazetteer(trainDocsRaw)) else None
    val featurizer = new ProductFeaturizer(featIdx, trainWordCounts, lexicalFeatThreshold, restrictToCurrent = restrictToCurrent,
                                           blockCurrent = blockCurrent, maybeBrownClusters = maybeBrownClusters,
                                           gazetteer = maybeGazetteer, useDomainFeatures = useDomainFeatures)
    for (ex <- trainDocs) {
      featurizer.extractNoneFeaturesCached(ex, true)
      for (i <- 0 until ex.documentPositions.size) {
        for (j <- 0 until ex.documentPositions(i).size) {
          featurizer.extractFeaturesCached(ex.documentPositions(i)(j), true)
        }
      }
    }

    // Actually train the model.
    val model = {
      val computer = new ProductDocLevelTokenComputer(featurizer, npRecallErrorLossWeight, prohibitNull, trainProductCounts, singletonLossWeight)
      val initialWeights = computer.getInitialWeights(0)
      Logger.logss(initialWeights.size + " total features")
      val weights = new GeneralTrainer(true).trainAdagradSparse(trainDocs, computer, eta, reg, batchSize, numItrs, initialWeights, verbose = true);
      new ProductDocLevelTokenPredictor(computer, weights)
    }
    if (modelPath != "") {
      IOUtils.writeObjFile(modelPath, model)
    }
    if (useGazetteer) {
      model.displayGazetteerFeats
    }
    // Run on the development set and report results
    evaluateTokenLevel(model, devDocs, doSig)
    
    // Runs a simple baseline too
//    Logger.logss("FREQUENCY-BASED MEMORIZING PREDICTOR")
//    evaluateNPs(frequencyBasedMemorizingPredictor, devNPDocs, false)
    
    model
  }

  
  def evaluateTokenLevel(model: Model, dev: Seq[LabeledDocument], writeOutputForSig: Boolean) {
    Logger.logss("****Evaluating Token Level ***");
    
    val predictions = dev.map(doc => model.predict(doc.document))
    val devEval = ModelUtils.evaluateTokenLevel(dev, predictions, writeOutputForSig)
    Logger.logss("TOKEN LEVEL RESULTS: dev: (" + dev.size + " docs, " + dev.map(_.positiveLabels.size).foldLeft(0)(_ + _) + " products) => " + devEval);
    val (devDocumentLevelEval, devDocumentLevelEvalStr) = ModelUtils.evaluateDocumentLevel(dev, predictions, writeOutputForSig);
    Logger.logss("DOCUMENT LEVEL RESULTS: " + devDocumentLevelEvalStr)
    val devSemanticProductEval = ModelUtils.evaluateDocumentLevelSemanticProducts(dev, predictions, writeOutputForSig);
    Logger.logss("DOCUMENT LEVEL SEMANTIC PRODUCT RESULTS: " + devSemanticProductEval)
    Logger.logss("CUMULATIVE EVAL: "+ devEval.renderForTex() + " & " + devSemanticProductEval.renderForTex() + " & " + EvalStats.fmtPositiveNumber(devDocumentLevelEval.toFloat * 100, 1))
  }
  

  def computeTrainProductLcCounts(trainDocuments: Seq[LabeledDocument]) = {
    val trainProductLcCounts = new Counter[String]
    for (trainDoc <- trainDocuments) {
      val positiveLabels = trainDoc.positiveLabels.asScala
      for (positiveLabel <- positiveLabels) {
        val word = trainDoc.document.lines.get(positiveLabel.getFirst().intValue).get(positiveLabel.getSecond().intValue)
//        trainProductLcCounts.incrementCount(word.toLowerCase, 1.0)
        trainProductLcCounts.incrementCount(ProductClusterer.dropLongestSuffix(word.toLowerCase), 1.0)
      }
    }
    var numSingletons = 0
    for (product <- trainProductLcCounts.keySet().asScala) {
      // If it's count 1
      if (trainProductLcCounts.getCount(product) < 1.5) {
        numSingletons += 1
      }
    }
    Logger.logss(numSingletons + " singletons out of " + trainProductLcCounts.size +
                 " total product types, " + trainProductLcCounts.totalCount() + " total products")
    trainProductLcCounts
  }
  

  
  def buildGazetteer(trainDocuments: Seq[LabeledDocument]) = {
    val gazetteer = ProductClusterer.extractCanonicalizedProductCounts(trainDocuments)
    gazetteer.pruneKeysBelowThreshold(gazetteerThreshold)
    gazetteer
  }
  

  
  def doErrorAnalysis(train: Seq[LabeledDocument], devSet: Seq[LabeledDocument], tokenSystem: ProductPredictor) {
    val trainProducts = train.flatMap(doc => {
      val positiveLabels = doc.positiveLabels.asScala
      positiveLabels.map(pair => doc.document.lines.get(pair.getFirst().intValue).get(pair.getSecond().intValue)).toSet
    })
    val trainProductsLc = trainProducts.map(_.toLowerCase)
    var numRecalledSeenInTrain = 0
    var numSeenInTrain = 0
    var recallNumer = 0
    var recallDenom = 0
    for (dev <- devSet) {
      val decoded = tokenSystem.predict(dev.document)
      val precErrors = decoded.positiveLabels.asScala.toSet -- dev.positiveLabels.asScala.toSet
      val recErrors = dev.positiveLabels.asScala.toSet -- decoded.positiveLabels.asScala.toSet
      for (error <- precErrors ++ recErrors) {
        val sentCopy = dev.document.lines.get(error.getFirst().intValue).asScala.toArray
        sentCopy(error.getSecond().intValue) = "**" + sentCopy(error.getSecond().intValue) + "**"
        if (sentCopy.size > 0) {
          val str = if (recErrors.contains(error)) "--UNRECALLED: " else "##IMPRECISE: " 
          Logger.logss(dev.document.documentId + ": " + str + sentCopy.reduce(_ + " " + _))
        }
      }
      val recalled = decoded.positiveLabels.asScala.toSet & dev.positiveLabels.asScala.toSet
      for (label <- recalled) {
        val word = dev.document.lines.get(label.getFirst().intValue).get(label.getSecond().intValue)
        val preprocWord = removeTrailingPunc(word)
        var seenInTrain = false
        for (trainProduct <- trainProducts) {
          seenInTrain = seenInTrain || trainProduct.contains(preprocWord.toLowerCase)
        }
        if (seenInTrain) {
          numRecalledSeenInTrain += 1;
        }
      }
      for (label <- dev.positiveLabels.asScala) {
        val word = dev.document.lines.get(label.getFirst().intValue).get(label.getSecond().intValue)
        val preprocWord = removeTrailingPunc(word)
        var seenInTrain = false
        // seenInTrain = trainProducts.contains(word)
        for (trainProduct <- trainProducts) {
          seenInTrain = seenInTrain || trainProduct.contains(preprocWord.toLowerCase)
        }
        if (seenInTrain) {
          Logger.logss("##SEEN: " + word)
          numSeenInTrain += 1
        } else {
          Logger.logss("--UNSEEN: " + word)
        }
      }
      recallNumer += recalled.size
      recallDenom += dev.positiveLabels.size
    }
    Logger.logss("Fraction seen in train that were recalled: " + Util.renderNumerDenom(numRecalledSeenInTrain, numSeenInTrain))
    Logger.logss("Fraction not seen in train that were recalled: " + Util.renderNumerDenom(recallNumer - numRecalledSeenInTrain, recallDenom - numSeenInTrain))
    Logger.logss("Overall recall: " + Util.renderNumerDenom(recallNumer, recallDenom))
    
  }
  
  def printStatistics(docs: Seq[LabeledDocument]) {
    val beginLimit = 10
    val endLimit = 10
    val beginLabels = Array.fill(beginLimit)(0.0)
    val endLabels = Array.fill(endLimit)(0.0)
    for (doc <- docs) {
      for (i <- 0 until Math.min(beginLimit, doc.document.lines.size)) {
        beginLabels(i) += doc.positiveLabels.asScala.filter(_.getFirst().intValue == i).size.toFloat / docs.size
      }
      if (doc.document.lines.size >= beginLimit + endLimit) {
        for (i <- doc.document.lines.size - endLimit until doc.document.lines.size) {
          endLabels(doc.document.lines.size - 1 - i) += doc.positiveLabels.asScala.filter(_.getFirst().intValue == i).size.toFloat / docs.size
        }
      }
    }
    Logger.logss("Starting sentence histogram: " + beginLabels.toSeq)
    Logger.logss("  Ending sentence histogram: " + endLabels.toSeq)
    Logger.logss("  (positive label counts, normalized by num docs)")
  }
  


}