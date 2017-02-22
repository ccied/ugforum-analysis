package edu.berkeley.forum.main

import java.util.Calendar

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.asScalaSetConverter
import scala.collection.JavaConverters.bufferAsJavaListConverter
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.collection.mutable.ArrayBuffer

import edu.berkeley.forum.data.Dataset
import edu.berkeley.forum.data.Dataset.LabeledDocument
import edu.berkeley.forum.data.LabeledNPDocument
import edu.berkeley.forum.data.NPDocument
import edu.berkeley.forum.data.NPDocumentPosition
import edu.berkeley.forum.data.ProductClusterer
import edu.berkeley.nlp.futile.LightRunner
import edu.berkeley.nlp.futile.Util
import edu.berkeley.nlp.futile.fig.basic.IOUtils
import edu.berkeley.nlp.futile.fig.basic.Indexer
import edu.berkeley.nlp.futile.util.Counter
import edu.berkeley.nlp.futile.util.Logger
import edu.berkeley.forum.eval.EvalStats
import edu.berkeley.forum.model.DocumentPosition
import edu.berkeley.forum.model.FirstNPPredictor
import edu.berkeley.forum.model.FrequencyBasedMemorizingNPPredictor
import edu.berkeley.forum.model.FrequencyBasedMemorizingPredictor
import edu.berkeley.forum.model.FrequencyBasedPredictor
import edu.berkeley.forum.model.FrequencyBasedTokenLevelPredictor
import edu.berkeley.forum.model.GeneralTrainer
import edu.berkeley.forum.model.MemorizingNPPredictor
import edu.berkeley.forum.model.Model
import edu.berkeley.forum.model.ModelUtils
import edu.berkeley.forum.model.NPDocLevelModel
import edu.berkeley.forum.model.NPModel
import edu.berkeley.forum.model.NPProductFeaturizer
import edu.berkeley.forum.model.ProductCRFComputer
import edu.berkeley.forum.model.ProductCRFFeaturizer
import edu.berkeley.forum.model.ProductCRFPredictor
import edu.berkeley.forum.model.ProductComputer
import edu.berkeley.forum.model.ProductDocLevelNPComputer
import edu.berkeley.forum.model.ProductDocLevelNPPredictor
import edu.berkeley.forum.model.ProductDocLevelTokenComputer
import edu.berkeley.forum.model.ProductDocLevelTokenPredictor
import edu.berkeley.forum.model.ProductFeaturizer
import edu.berkeley.forum.model.ProductNPComputer
import edu.berkeley.forum.model.ProductNPPredictor
import edu.berkeley.forum.model.ProductPredictor

object Trainer {

  // Note: all of these are command-line arguments. Pass them in with -<argName> <argValue> (argValue can be omitted if it's a boolean
  // that you're setting to true)
  
  // Only train on files in the directories with this prefix
  val filePrefix = "0-initiator"
  // Only train on files in the directories with this suffix
  val fileSuffix = ".txt.tok"
  
  // Path to Darkode, Hackforums, and Blackhat data release
  val dataReleasePath: String = ""
  
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
  
  // Controls the mapping from token labels to NP labels
  //do the noun phrase system
  val doNps = true
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
  
  val doFirstBaseline = false
  val doFrequencyBaseline = false
  val doDictionaryBaseline = false
  
  // The default is train on Darkode, test on both Darkode and Hackforum
  val trainOnHackforum = false
  val trainOnBothTestOnBoth = false
  
  // True if we should run on the blind test set to get final results
  val runOnTest = false
  
  val doSig = false
  
  // Controls the precision-recall tradeoff for the token-level system
  //increasing this improves recall, runs from 0 to infinity
  val recallErrorLossWeight = 1.0
  
  // Controls the precision-recall tradeoff for the NP system
  // 2.0 or 4.0 sometimes give higher F1
  //increasing this improves recall, runs from 0 to infinity
  //for hackforum 4.0 is better
  val npRecallErrorLossWeight = 1.0
  
  val singletonLossWeight = 1.0
  //set it to 5 for the experiments
  //puts a coefficient of 5 in front of every product that only occurs once
  //any gradient from that example will be upweighted by a factor of 5. making it 5 times more important in your loss function
  
  val doLearningCurve = false
  
  // Print more logs of specific errors, particularly relating to prediction of
  // products unseen in the training set
  val printDetailedErrors = false
  
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
  val brownCutoff = 2
  val lexicalFeatThreshold = 5
  val useParentFeatures = true
  
  val useGazetteer = false
  val gazetteerThreshold = 4
  
  val doDomainAdaptationExperiment = false
  val adaptationScalingFactor = 1
  val useDomainFeatures = false
  
  def main(args: Array[String]) {
    LightRunner.initializeOutput(Trainer.getClass())
    LightRunner.populateScala(Trainer.getClass(), args)
    
    val dataset = ForumDatasets.loadDarkode(dataReleasePath, filePrefix, fileSuffix)
    val hfTrainDevRaw = ForumDatasets.loadHackforumsTrainDev(dataReleasePath, filePrefix, fileSuffix, hfAnnotThreshold);
    val hfTrainDevRawSorted = hfTrainDevRaw.asScala.sortBy(labeledDoc => labeledDoc.document.documentId)
    val hfTrainRaw = hfTrainDevRawSorted.slice(0, (0.8 * hfTrainDevRawSorted.size).toInt).asJava
    val hfDevRaw = hfTrainDevRawSorted.slice((0.8 * hfTrainDevRawSorted.size).toInt, hfTrainDevRawSorted.size).asJava
    val hfTestRaw = ForumDatasets.loadHackforumsTest(dataReleasePath, filePrefix, fileSuffix, hfTestAnnotThreshold)
    
    val blackhatTestRaw = ForumDatasets.loadBlackhatTest(dataReleasePath, filePrefix, fileSuffix)
    
    val (darkodeTrain, darkodeTest) = if (runOnTest) {
      (dataset.train.asScala ++ dataset.dev.asScala, dataset.test.asScala)
    } else {
      (dataset.train.asScala, dataset.dev.asScala)
    }
    val (hfTrain, hfTest) = if (runOnTest) {
      (hfTrainDevRaw, hfTestRaw)
    } else {
      (hfTrainRaw, hfDevRaw)
    }
    val blackhatTest = if (runOnTest) {
      blackhatTestRaw
    } else {
      Seq[LabeledDocument]()
    }

    if (doDomainAdaptationExperiment) {
      Logger.logss("HF -> DARKODE")
//      trainSystemDoErrorAnalysis(dataset.train.asScala.slice(0, 100), dataset.dev.asScala, None, None)
      domainAdaptationExperiment(hfTrain.asScala, darkodeTrain, hfTest.asScala, darkodeTest)
      Logger.logss("DARKODE -> HF")
//      trainSystemDoErrorAnalysis(hfTrain.asScala.slice(0, 100), hfTest.asScala, None, None)
      domainAdaptationExperiment(dataset.train.asScala, hfTrain.asScala, darkodeTest, hfTest.asScala)
    } else if (trainOnHackforum) {
      trainSystemDoErrorAnalysis(hfTrain.asScala, hfTest.asScala, Some(darkodeTrain), Seq(darkodeTest, blackhatTestRaw))
    } else if (trainOnBothTestOnBoth) {
//      trainSystemDoErrorAnalysis(hfTrain.asScala ++ dataset.train.asScala, hfTest.asScala ++ dataset.dev.asScala, None, None)
      Logger.logss("DARKODE EVAL")
      trainSystemDoErrorAnalysis(hfTrain.asScala ++ darkodeTrain, darkodeTest, None, Seq())
      Logger.logss("HACKFORUMS EVAL")
      trainSystemDoErrorAnalysis(hfTrain.asScala ++ darkodeTrain, hfTest.asScala, None, Seq())
    } 
    //deprecated, compares NP head to token (prev system)
    else if (doComparison) {
      val npSystem = trainNPSystem(darkodeTrain, darkodeTest)
      val tokenSystem = trainTokenSystem(darkodeTrain, darkodeTest)
      compareAndAnalyze(dataset, tokenSystem, npSystem)
    } else {
      trainSystemDoErrorAnalysis(darkodeTrain, darkodeTest, Some(hfTrain.asScala), Seq(hfTest.asScala, blackhatTestRaw))
    }
    LightRunner.finalizeOutput()
  }
  
  def domainAdaptationExperiment(trainDomain1: Seq[LabeledDocument],
                                 trainDomain2: Seq[LabeledDocument],
                                 testDomain1: Seq[LabeledDocument],
                                 testDomain2: Seq[LabeledDocument]) {
    val shufTrain2 = new scala.util.Random(0).shuffle(trainDomain2)
    for (numOodExs <- Seq(0, 10, 20, 40, 80, 160)) {
      // Duplicate it
      val trainSet = new ArrayBuffer[LabeledDocument]
      trainSet ++= trainDomain1
      for (i <- 0 until adaptationScalingFactor) {
        trainSet ++= shufTrain2.slice(0, numOodExs)
      }
      val trainedSystem = trainSystemDoErrorAnalysis(trainSet, testDomain2, None, Seq())
    }
  }
  
  def trainSystemDoErrorAnalysis(train: Seq[LabeledDocument],
                                 test: Seq[LabeledDocument],
                                 auxTrain: Option[Seq[LabeledDocument]],
                                 auxTest: Seq[Seq[LabeledDocument]]) {
    // BASELINES
    if (doFirstBaseline) {
      if (doNps) {
        val firstBaseline = new FirstNPPredictor()
        for (testSet <- Seq(test) ++ auxTest) {
          evaluate(firstBaseline, testSet, doSig)
        }
      } else {
        throw new RuntimeException("Unimplemented")
      }
    } else if (doFrequencyBaseline) {
      if (doNps) {
        val frequencyBaseline = new FrequencyBasedPredictor(true)
        for (testSet <- Seq(test) ++ auxTest) {
          evaluate(frequencyBaseline, testSet, doSig)
        }
      } else {
        val frequencyBaseline = new FrequencyBasedTokenLevelPredictor(true)
        for (testSet <- Seq(test) ++ auxTest) {
          evaluateTokenLevel(frequencyBaseline, testSet, doSig)
        }
      }
    } else if (doDictionaryBaseline) {
      if (doNps) {
        val trainNPDocs = train.map(ex => NPDocument.createFromLabeledDocument(ex, maxNpLen, includeVerbs, useNpMajority))
        val dictionaryBaseline = FrequencyBasedMemorizingNPPredictor.extractPredictorFromNPs(trainNPDocs, restrictToPickOne = false)
        for (testSet <- Seq(test) ++ auxTest) {
          evaluate(dictionaryBaseline, testSet, doSig)
        }
      } else {
        val dictionaryBaseline = FrequencyBasedMemorizingPredictor.extractPredictor(train, restrictToPickOne = false)
        for (testSet <- Seq(test) ++ auxTest) {
          evaluateTokenLevel(dictionaryBaseline, testSet, doSig)
        }
      }
    }
    // DOCUMENT-LEVEL SYSTEM
    else if (doDocumentLevel) {
      if (doNps) {
        val (docLevelSystem, frequencyPredictorSystem) = trainDocumentLevelNPSystem(train, test)
        doDocumentLevelErrorAnalysis(train, test, docLevelSystem)
        if (!auxTest.isEmpty) {
          docLevelSystem.adapt(Some(auxTrain.get.map(doc => NPDocument.createFromLabeledDocument(doc, maxNpLen, includeVerbs, useNpMajority))), None)
          for (auxTestSet <- auxTest) {
            evaluate(docLevelSystem, auxTestSet, doSig)
            doDocumentLevelErrorAnalysis(train, auxTestSet, docLevelSystem)
          }
        }
      } else {
        val tokDocSystem = trainDocumentLevelTokenSystem(train, test)
        if (!auxTest.isEmpty) {
          tokDocSystem.adapt(auxTrain, None)
          for (auxTestSet <- auxTest) {
            evaluateTokenLevel(tokDocSystem, auxTestSet, doSig)
//            doDocumentLevelErrorAnalysis(train, auxTestSet, tokDocSystem)
          }
        }
      }
    } else if (doNps) { // NP SYSTEM
      val npSystem = trainNPSystem(train, test)
      if (doLearningCurve) {
        for (divider <- Seq(2, 4, 8, 16)) {
          trainNPSystem(train.slice(0, train.size/divider), test)
        }
      }
      doErrorAnalysis(train, test, npSystem)
      if (!auxTest.isEmpty) {
        npSystem.adapt(Some(auxTrain.get.map(doc => NPDocument.createFromLabeledDocument(doc, maxNpLen, includeVerbs, useNpMajority))), None)
        for (auxTestSet <- auxTest) {
          evaluate(npSystem, auxTestSet, doSig)
          val auxNPDocs = auxTestSet.map(ex => NPDocument.createFromLabeledDocument(ex, maxNpLen, includeVerbs, useNpMajority))
          npSystem.evaluateTypeLevelPrintResults(auxNPDocs)
          printStatistics(auxTestSet)
          doErrorAnalysis(train, auxTestSet, npSystem)
        }
      }
    } else { // TOKEN-LEVEL SYSTEM
      if (doCRF) {
        val crfTokenSystem = trainCRFTokenSystem(train, test)
        for (auxTestSet <- auxTest) {
          evaluateTokenLevel(crfTokenSystem, auxTestSet, doSig)
        }
      } else {
        val tokenSystem = trainTokenSystem(train, test)
        for (auxTestSet <- auxTest) {
          evaluateTokenLevel(tokenSystem, auxTestSet, doSig)
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
//    val trainEval = model.evaluate(train.asJava);
//    Logger.logss("train: " + trainEval);
//    val devEval = model.evaluate(dev.asJava);
//    Logger.logss("dev: (" + dev.size + " docs, " + dev.map(_.positiveLabels.size).foldLeft(0)(_ + _) + " products) => " + devEval);
    
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
  
  /**
   * Primary training method: takes training and test (but it's called dev) sets of LabeledDocuments
   * and trains an NP product predictor.
   */
  def trainNPSystem(train: Seq[LabeledDocument], dev: Seq[LabeledDocument]): NPModel = {
    // Count the words in the training set. These counts are used in the featurizer to decide when to fire features
    // (we don't fire features on words if they're too rare)
    val trainWordCounts = new Counter[String]
    train.foreach(_.document.lines.asScala.foreach(_.asScala.foreach(word => trainWordCounts.incrementCount(word, 1.0))))
    // Turns the LabeledDocuments (which are labeled at the token level) into NP-labeled documents. This mapping has
    // some free parameters; it isn't a trivial procedure.
    val trainNPDocs = train.map(ex => NPDocument.createFromLabeledDocument(ex, maxNpLen, includeVerbs, useNpMajority))
    var totalGoldNPs = 0
    // Extract individual examples from each document
    val trainNPExsRaw = trainNPDocs.flatMap(npDoc => (0 until npDoc.doc.nps.size).flatMap(lineIdx => {
      (0 until npDoc.doc.nps(lineIdx).size).map(npIdx => {
         val isGold = npDoc.labels(lineIdx)(npIdx)
         if (isGold) {
           totalGoldNPs += 1
         }
         new NPDocumentPosition(npDoc.doc, lineIdx, npIdx, isGold)
      })
    }))
    // Shuffle the training data; this tends to improve performance.
    val trainNPExs = new scala.util.Random(0).shuffle(trainNPExsRaw)
    
    Logger.logss(totalGoldNPs + " gold NPs extracted from " + train.map(_.positiveLabels.size).foldLeft(0)(_ + _) + " positive token labels (and some tokens may be in the same NP)")
    Logger.logss("Extracting training set features")
    // Build the featurizer and cache features in the training data
    val featIdx = new Indexer[String]
    val maybeBrownClusters = if (useBrown && brownPath != "") Some(ProductFeaturizer.loadBrownClusters(brownPath, brownCutoff)) else None
    
    val maybeGazetteer = if (useGazetteer) Some(buildGazetteer(train)) else None
    val wrappedFeaturizer = new ProductFeaturizer(featIdx, trainWordCounts, lexicalFeatThreshold, restrictToCurrent = restrictToCurrent,
                                                  blockCurrent = blockCurrent, maybeBrownClusters = maybeBrownClusters,
                                                  gazetteer = maybeGazetteer, useDomainFeatures = useDomainFeatures)
    val featurizer = new NPProductFeaturizer(wrappedFeaturizer, restrictToCurrent, blockCurrent)
    trainNPExs.foreach(ex => featurizer.extractFeaturesCached(ex, true))

    // Actually train the model
    val trainProductLcCounts = computeTrainProductLcCounts(train)
    val computer = new ProductNPComputer(featurizer, npRecallErrorLossWeight, trainProductLcCounts, singletonLossWeight)
    val initialWeights = computer.getInitialWeights(0)
    Logger.logss(initialWeights.size + " total features")
    val weights = new GeneralTrainer(true).trainAdagradSparse(trainNPExs, computer, eta, reg, batchSize, numItrs, initialWeights, verbose = true);
    val model = new ProductNPPredictor(computer, weights)
    if (modelPath != "") {
      IOUtils.writeObjFile(modelPath, model)
    }
    // Run on the development set and report results
    evaluate(model, dev, doSig)
//    val trainEval = model.evaluate(trainNPDocs);
//    Logger.logss("train: " + trainEval);
//    val predictions = devNPDocs.map(doc => model.predict(doc.doc))
//    val devEval = model.evaluate(devNPDocs, predictions);
//    Logger.logss("TOKEN LEVEL RESULTS: dev: (" + devNPDocs.size + " docs, " + devNPDocs.map(_.labels.map(sentLabels => sentLabels.filter(label => label).size).foldLeft(0)(_ + _)).foldLeft(0)(_ + _) + " products) => " + devEval);
//    val devDocumentLevelEval = model.evaluateDocumentLevel(devNPDocs, predictions);
//    Logger.logss("DOCUMENT LEVEL RESULTS: " + devDocumentLevelEval)
//    val devSemanticProductEval = model.evaluateDocumentLevelSemanticProducts(devNPDocs, predictions);
//    Logger.logss("DOCUMENT LEVEL SEMANTIC PRODUCT RESULTS: " + devSemanticProductEval)
    
    // Runs a simple baseline too
    val devNPDocs = dev.map(ex => NPDocument.createFromLabeledDocument(ex, maxNpLen, includeVerbs, useNpMajority))
    val baseline = MemorizingNPPredictor.extractPredictor(trainNPDocs)
    val baselineEval = baseline.evaluate(devNPDocs, false);
    Logger.logss("baseline: (" + devNPDocs.size + " docs, " + devNPDocs.map(_.labels.map(sentLabels => sentLabels.filter(label => label).size).foldLeft(0)(_ + _)).foldLeft(0)(_ + _) + " products) => " + baselineEval);
    model.evaluateTypeLevelPrintResults(devNPDocs)
    //baseline.evaluateTypeLevelPrintResults(devNPDocs)
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
  
  
    
  /**
   * Holistic version of the system which picks out an NP from a document that is likely a representative product
   */
  def trainDocumentLevelNPSystem(trainRaw: Seq[LabeledDocument], dev: Seq[LabeledDocument]): (NPDocLevelModel, FrequencyBasedMemorizingNPPredictor) = {
    val trainNPDocsRaw = trainRaw.map(ex => NPDocument.createFromLabeledDocument(ex, maxNpLen, includeVerbs, useNpMajority))
    val devNPDocs = dev.map(ex => NPDocument.createFromLabeledDocument(ex, maxNpLen, includeVerbs, useNpMajority))
    trainDocumentLevelNPSystemFromNPs(trainNPDocsRaw, devNPDocs, computeTrainProductLcCounts(trainRaw))
  }
  
  def trainDocumentLevelNPSystemFromNPs(trainNPDocsRaw: Seq[LabeledNPDocument], devNPDocs: Seq[LabeledNPDocument]): (NPDocLevelModel, FrequencyBasedMemorizingNPPredictor) = {
    trainDocumentLevelNPSystemFromNPs(trainNPDocsRaw, devNPDocs, computeTrainProductLcCountsFromNPs(trainNPDocsRaw))
  }
  
  def trainDocumentLevelNPSystemFromNPs(trainNPDocsRaw: Seq[LabeledNPDocument], devNPDocs: Seq[LabeledNPDocument], trainProductCounts: Counter[String]): (NPDocLevelModel, FrequencyBasedMemorizingNPPredictor) = {
    val trainNPDocsPreShuf = if (prohibitNull) {
      trainNPDocsRaw.filter(_.getPositiveLabels.size > 0)
    } else {
      trainNPDocsRaw
    }
    // Count the words in the training set. These counts are used in the featurizer to decide when to fire features
    // (we don't fire features on words if they're too rare)
    val trainWordCounts = new Counter[String]
    trainNPDocsPreShuf.foreach(_.doc.doc.lines.asScala.foreach(_.asScala.foreach(word => trainWordCounts.incrementCount(word, 1.0))))
    // Turns the LabeledDocuments (which are labeled at the token level) into NP-labeled documents. This mapping has
    // some free parameters; it isn't a trivial procedure.
    val trainNPDocs = new scala.util.Random(0).shuffle(trainNPDocsPreShuf)
    
    Logger.logss("Extracting training set features")
    // Build the featurizer and cache features in the training data
    val featIdx = new Indexer[String]
    val maybeBrownClusters = if (useBrown && brownPath != "") Some(ProductFeaturizer.loadBrownClusters(brownPath, brownCutoff)) else None
    
    val maybeGazetteer = if (useGazetteer) Some(buildGazetteerFromNPs(trainNPDocsRaw)) else None
    val wrappedFeaturizer = new ProductFeaturizer(featIdx, trainWordCounts, lexicalFeatThreshold, restrictToCurrent = restrictToCurrent,
                                                  blockCurrent = blockCurrent, maybeBrownClusters = maybeBrownClusters,
                                                  gazetteer = maybeGazetteer, useDomainFeatures = useDomainFeatures)
    val featurizer = new NPProductFeaturizer(wrappedFeaturizer, restrictToCurrent, blockCurrent)
    for (ex <- trainNPDocs) {
      featurizer.extractNoneFeaturesCached(ex, true)
      for (i <- 0 until ex.npDocumentPositions.size) {
        for (j <- 0 until ex.npDocumentPositions(i).size) {
          featurizer.extractFeaturesCached(ex.npDocumentPositions(i)(j), true)
        }
      }
    }

    // Actually train the model.
    val model = {
      val computer = new ProductDocLevelNPComputer(featurizer, npRecallErrorLossWeight, prohibitNull, trainProductCounts, singletonLossWeight)
      val initialWeights = computer.getInitialWeights(0)
      Logger.logss(initialWeights.size + " total features")
      val weights = new GeneralTrainer(true).trainAdagradSparse(trainNPDocs, computer, eta, reg, batchSize, numItrs, initialWeights, verbose = true);
      new ProductDocLevelNPPredictor(computer, weights)
    }
    if (modelPath != "") {
      IOUtils.writeObjFile(modelPath, model)
    }
    if (useGazetteer) {
      model.displayGazetteerFeats
    }
    // Run on the development set and report results
    evaluateNPs(model, devNPDocs, doSig)
    
    // Runs a simple baseline too
    val baseline = MemorizingNPPredictor.extractPredictor(trainNPDocs)
    val baselineEval = baseline.evaluate(devNPDocs, false);
    Logger.logss("baseline: (" + devNPDocs.size + " docs, " + devNPDocs.map(_.labels.map(sentLabels => sentLabels.filter(label => label).size).foldLeft(0)(_ + _)).foldLeft(0)(_ + _) + " products) => " + baselineEval);
    val frequencyBasedMemorizingPredictor = FrequencyBasedMemorizingNPPredictor.extractPredictorFromNPs(trainNPDocs, restrictToPickOne = false)
//    Logger.logss("FREQUENCY-BASED MEMORIZING PREDICTOR")
//    evaluateNPs(frequencyBasedMemorizingPredictor, devNPDocs, false)
    
    model -> frequencyBasedMemorizingPredictor
  }
  
//  def trainDocumentLevelNPSystem(trainRaw: Seq[LabeledDocument], dev: Seq[LabeledDocument]): (NPDocLevelModel, FrequencyBasedMemorizingNPPredictor) = {
//    val train = if (prohibitNull) {
//      trainRaw.filter(_.positiveLabels.size > 0)
//    } else {
//      trainRaw
//    }
//    // Count the words in the training set. These counts are used in the featurizer to decide when to fire features
//    // (we don't fire features on words if they're too rare)
//    val trainWordCounts = new Counter[String]
//    train.foreach(_.document.lines.asScala.foreach(_.asScala.foreach(word => trainWordCounts.incrementCount(word, 1.0))))
//    // Turns the LabeledDocuments (which are labeled at the token level) into NP-labeled documents. This mapping has
//    // some free parameters; it isn't a trivial procedure.
//    val trainNPDocsRaw = train.map(ex => NPDocument.createFromLabeledDocument(ex, maxNpLen, includeVerbs, useNpMajority))
//    val trainNPDocs = new scala.util.Random(0).shuffle(trainNPDocsRaw)
//    
//    Logger.logss("Extracting training set features")
//    // Build the featurizer and cache features in the training data
//    val featIdx = new Indexer[String]
//    val maybeBrownClusters = if (brownPath != "") Some(ProductFeaturizer.loadBrownClusters(brownPath, brownCutoff)) else None
//    val maybeTimes = if (useTime && timeFilesPath != "") Some(TimeLoader.loadAllTimes(timeFilesPath.split(","))) else None
//    
//    val wrappedFeaturizer = new ProductFeaturizer(featIdx, trainWordCounts, restrictToCurrent = restrictToCurrent, blockCurrent = blockCurrent, maybeBrownClusters = maybeBrownClusters, maybeTimes = maybeTimes)
//    val featurizer = new NPProductFeaturizer(wrappedFeaturizer, restrictToCurrent, blockCurrent)
//    for (ex <- trainNPDocs) {
//      featurizer.extractNoneFeaturesCached(ex, true)
//      for (i <- 0 until ex.npDocumentPositions.size) {
//        for (j <- 0 until ex.npDocumentPositions(i).size) {
//          featurizer.extractFeaturesCached(ex.npDocumentPositions(i)(j), true)
//        }
//      }
//    }
//
//    // Actually train the model.
//    require(!doNeural)
//    val model = {
//      val trainProductLcCounts = computeTrainProductLcCounts(train)
//      val computer = new ProductDocLevelNPComputer(featurizer, npRecallErrorLossWeight, prohibitNull, trainProductLcCounts, singletonLossWeight)
//      val initialWeights = computer.getInitialWeights(0)
//      Logger.logss(initialWeights.size + " total features")
//      val weights = new GeneralTrainer(true).trainAdagradSparse(trainNPDocs, computer, eta, reg, batchSize, numItrs, initialWeights, verbose = true);
//      new ProductDocLevelNPPredictor(computer, weights)
//    }
//    if (modelPath != "") {
//      IOUtils.writeObjFile(modelPath, model)
//    }
//    // Run on the development set and report results
//    evaluate(model, dev)
//    
//    // Runs a simple baseline too
//    val devNPDocs = dev.map(ex => NPDocument.createFromLabeledDocument(ex, maxNpLen, includeVerbs, useNpMajority))
//    val baseline = MemorizingNPPredictor.extractPredictor(trainNPDocs)
//    val baselineEval = baseline.evaluate(devNPDocs);
//    Logger.logss("baseline: (" + devNPDocs.size + " docs, " + devNPDocs.map(_.labels.map(sentLabels => sentLabels.filter(label => label).size).foldLeft(0)(_ + _)).foldLeft(0)(_ + _) + " products) => " + baselineEval);
//    val frequencyBasedMemorizingPredictor = FrequencyBasedMemorizingNPPredictor.extractPredictorFromNPs(trainNPDocs)
//    Logger.logss("FREQUENCY-BASED MEMORIZING PREDICTOR")
//    evaluate(frequencyBasedMemorizingPredictor, dev)
//    
//    model -> frequencyBasedMemorizingPredictor
//  }
  
  def evaluateTokenLevel(model: Model, dev: Seq[LabeledDocument], writeOutputForSig: Boolean) {
    val predictions = dev.map(doc => model.predict(doc.document))
    val devEval = ModelUtils.evaluateTokenLevel(dev, predictions, writeOutputForSig)
    Logger.logss("TOKEN LEVEL RESULTS: dev: (" + dev.size + " docs, " + dev.map(_.positiveLabels.size).foldLeft(0)(_ + _) + " products) => " + devEval);
    val (devDocumentLevelEval, devDocumentLevelEvalStr) = ModelUtils.evaluateDocumentLevel(dev, predictions, writeOutputForSig);
    Logger.logss("DOCUMENT LEVEL RESULTS: " + devDocumentLevelEvalStr)
    val devSemanticProductEval = ModelUtils.evaluateDocumentLevelSemanticProducts(dev, predictions, writeOutputForSig);
    Logger.logss("DOCUMENT LEVEL SEMANTIC PRODUCT RESULTS: " + devSemanticProductEval)
    Logger.logss("CUMULATIVE EVAL: "+ devEval.renderForTex() + " & " + devSemanticProductEval.renderForTex() + " & " + EvalStats.fmtPositiveNumber(devDocumentLevelEval.toFloat * 100, 1))
  }
  
  def evaluate(model: NPModel, dev: Seq[LabeledDocument], writeOutputForSig: Boolean) {
    val devNPDocs = dev.map(ex => NPDocument.createFromLabeledDocument(ex, maxNpLen, includeVerbs, useNpMajority))
    evaluateNPs(model, devNPDocs, writeOutputForSig)
  }
  
  def evaluateNPs(model: NPModel, devNPDocs: Seq[LabeledNPDocument], writeOutputForSig: Boolean) {
    val predictions = devNPDocs.map(doc => model.predict(doc.doc))
    val devEval = model.evaluate(devNPDocs, predictions, writeOutputForSig);
    Logger.logss("TOKEN LEVEL RESULTS: dev: (" + devNPDocs.size + " docs, " + devNPDocs.map(_.labels.map(sentLabels => sentLabels.filter(label => label).size).foldLeft(0)(_ + _)).foldLeft(0)(_ + _) + " products) => " + devEval);
    val (devDocumentLevelEval, devDocumentLevelEvalStr) = model.evaluateDocumentLevel(devNPDocs, predictions, writeOutputForSig);
    Logger.logss("DOCUMENT LEVEL RESULTS: " + devDocumentLevelEvalStr)
    val devSemanticProductEval = model.evaluateDocumentLevelSemanticProducts(devNPDocs, predictions, writeOutputForSig);
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
  
  def computeTrainProductLcCountsFromNPs(trainDocuments: Seq[LabeledNPDocument]) = {
    val trainProductLcCounts = new Counter[String]
    for (trainDoc <- trainDocuments) {
      val positiveLabels = trainDoc.getPositiveLabels
      for (positiveLabel <- positiveLabels) {
        // Pull out the head word
        val word = trainDoc.doc.doc.lines.get(positiveLabel._1.intValue).get(trainDoc.doc.nps(positiveLabel._1)(positiveLabel._2)._2)
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
  
  def buildGazetteerFromNPs(trainDocuments: Seq[LabeledNPDocument]) = {
    val gazetteer = ProductClusterer.extractCanonicalizedProductCountsFromNPs(trainDocuments)
    gazetteer.pruneKeysBelowThreshold(gazetteerThreshold)
    gazetteer
  }
  
  /**
   * Compares the token system and the NP model; not really necessary anymore.
   */
  def compareAndAnalyze(dataset: Dataset, tokenSystem: ProductPredictor, npSystem: NPModel) {
    for (doc <- dataset.dev.asScala) {
      val npDoc = NPDocument.createFromLabeledDocument(doc, maxNpLen, includeVerbs, useNpMajority)
      val tokenPredictions = tokenSystem.predict(doc.document)
      val npPredictions = npSystem.predict(npDoc.doc)
      Logger.logss("===============================")
      Logger.logss(doc.document.documentId)
      val lastAnnotatedTokenLine = doc.positiveLabels.asScala.map(_.getFirst.intValue()).foldLeft(0)(Math.max(_, _))
      val lastPredTokenLine = tokenPredictions.positiveLabels.asScala.map(_.getFirst.intValue()).foldLeft(0)(Math.max(_, _))
      val npPredictionLines = (0 until npDoc.doc.nps.size).filter(sentIdx => npPredictions.labels(sentIdx).foldLeft(false)(_ || _))
      val lastNPPredictionLine = if (npPredictionLines.isEmpty) 0 else npPredictionLines.last
      val lastInterestingLine = Math.max(Math.max(lastAnnotatedTokenLine, lastPredTokenLine), lastNPPredictionLine)
      for (sentIdx <- 0 to lastInterestingLine) {
        val goldTokens = doc.positiveLabels.asScala.filter(_.getFirst.intValue() == sentIdx).map(_.getSecond().intValue)
        val predictedTokens = tokenPredictions.positiveLabels.asScala.filter(_.getFirst.intValue() == sentIdx).map(_.getSecond().intValue)
        val positiveNPIndices = (0 until npDoc.doc.nps(sentIdx).size).filter(npIdx => npPredictions.labels(sentIdx)(npIdx))
        val predictedNPSpans = positiveNPIndices.map(npIdx => npDoc.doc.nps(sentIdx)(npIdx))
        Logger.logss(NPDocument.renderSentence(doc.document.lines.get(sentIdx).asScala, predictedNPSpans, goldTokens, Some(predictedTokens)))
      }
      val omittedLines = npDoc.doc.nps.size - lastInterestingLine
      if (omittedLines > 0) {
        Logger.logss("...omitted " + omittedLines + " lines")
      }
    }
  }
  
//  def doErrorAnalysis(dataset: Dataset, tokenSystem: ProductPredictor) {
//    val trainProducts = dataset.train.asScala.flatMap(doc => {
//      val positiveLabels = doc.positiveLabels.asScala
//      positiveLabels.map(pair => doc.document.lines.get(pair.getFirst().intValue).get(pair.getSecond().intValue)).toSet
//    })
//    val trainProductsLc = trainProducts.map(_.toLowerCase)
//    var numRecalledSeenInTrain = 0
//    var numSeenInTrain = 0
//    var recallNumer = 0
//    var recallDenom = 0
//    for (dev <- dataset.dev.asScala) {
//      val decoded = tokenSystem.predict(dev.document)
//      val precErrors = decoded.positiveLabels.asScala.toSet -- dev.positiveLabels.asScala.toSet
//      val recErrors = dev.positiveLabels.asScala.toSet -- decoded.positiveLabels.asScala.toSet
//      for (error <- precErrors ++ recErrors) {
//        val sentCopy = dev.document.lines.get(error.getFirst().intValue).asScala.toArray
//        sentCopy(error.getSecond().intValue) = "**" + sentCopy(error.getSecond().intValue) + "**"
//        if (sentCopy.size > 0) {
//          val str = if (recErrors.contains(error)) "--UNRECALLED: " else "##IMPRECISE: " 
//          Logger.logss(dev.document.documentId + ": " + str + sentCopy.reduce(_ + " " + _))
//        }
//      }
//      val recalled = decoded.positiveLabels.asScala.toSet & dev.positiveLabels.asScala.toSet
//      for (label <- recalled) {
//        val word = dev.document.lines.get(label.getFirst().intValue).get(label.getSecond().intValue)
//        val preprocWord = removeTrailingPunc(word)
//        var seenInTrain = false
//        for (trainProduct <- trainProducts) {
//          seenInTrain = seenInTrain || trainProduct.contains(preprocWord.toLowerCase)
//        }
//        if (seenInTrain) {
//          numRecalledSeenInTrain += 1;
//        }
//      }
//      for (label <- dev.positiveLabels.asScala) {
//        val word = dev.document.lines.get(label.getFirst().intValue).get(label.getSecond().intValue)
//        val preprocWord = removeTrailingPunc(word)
//        var seenInTrain = false
//        // seenInTrain = trainProducts.contains(word)
//        for (trainProduct <- trainProducts) {
//          seenInTrain = seenInTrain || trainProduct.contains(preprocWord.toLowerCase)
//        }
//        if (seenInTrain) {
//          Logger.logss("##SEEN: " + word)
//          numSeenInTrain += 1
//        } else {
//          Logger.logss("--UNSEEN: " + word)
//        }
//      }
//      recallNumer += recalled.size
//      recallDenom += dev.positiveLabels.size
//    }
//    Logger.logss("Fraction seen in train that were recalled: " + Util.renderNumerDenom(numRecalledSeenInTrain, numSeenInTrain))
//    Logger.logss("Fraction not seen in train that were recalled: " + Util.renderNumerDenom(recallNumer - numRecalledSeenInTrain, recallDenom - numSeenInTrain))
//    Logger.logss("Overall recall: " + Util.renderNumerDenom(recallNumer, recallDenom))
//    
//  }
  
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
  
  def doErrorAnalysis(trainSet: Seq[LabeledDocument], devSet: Seq[LabeledDocument], npSystem: NPModel) {
    val trainNPDocs = trainSet.map(ex => NPDocument.createFromLabeledDocument(ex, maxNpLen, includeVerbs, useNpMajority))
    val devNPDocs = devSet.map(ex => NPDocument.createFromLabeledDocument(ex, maxNpLen, includeVerbs, useNpMajority))
    
//    val trainProducts = trainSet.flatMap(doc => {
//      val positiveLabels = doc.positiveLabels.asScala
//      positiveLabels.map(pair => doc.document.lines.get(pair.getFirst().intValue).get(pair.getSecond().intValue)).toSet
//    }).toSet
//    val trainProductsLc = trainProducts.map(_.toLowerCase)
    val trainProductsLc = computeTrainProductLcCounts(trainSet).keySet.asScala.toSet
//    Logger.logss(trainProductsLc.size + " lowercased products")
//    ProductClusterer.clusterProducts(trainProductsLc.toSeq)
    var numCorrectSeenInTrain = 0
    var numGoldSeenInTrain = 0
    var numCorrect = 0
    var recallDenom = 0
    
    var numPredictionsSeenInTrain = 0
    var precNumer = 0
    var precDenom = 0
    
    var recallUnanimousNumer = 0
    var recallUnanimousDenom = 0
    var recallAmbiguousNumer = 0
    var recallAmbiguousDenom = 0
    
    var numExactMatch = 0
    val maxProductCount = 4
    val numExactMatchByCount = Array.fill(maxProductCount+1)(0)
    val numDocsWithNProducts = Array.fill(maxProductCount+1)(0)
    
    val timeMapToNumProducts = new Counter[String]
    val timeMapToNumPosts = new Counter[String]
    
    for (dev <- devNPDocs) {
      val decoded = npSystem.predict(dev.doc)
      val precErrors = decoded.getPositiveLabels.toSet -- dev.getPositiveLabels.toSet
      val recErrors = dev.getPositiveLabels.toSet -- decoded.getPositiveLabels.toSet
      for (error <- precErrors ++ recErrors) {
        val sentCopy = dev.doc.doc.lines.get(error._1.intValue).asScala.toArray
        val np = dev.doc.nps(error._1)(error._2)
        sentCopy(np._1) = "**[" + sentCopy(np._1)
        sentCopy(np._3 - 1) = sentCopy(np._3 - 1) + "]**"
        if (printDetailedErrors && sentCopy.size > 0) {
          val str = if (recErrors.contains(error)) "--UNRECALLED: " else "##IMPRECISE: " 
          Logger.logss(dev.doc.doc.documentId + ": " + str + sentCopy.reduce(_ + " " + _))
        }
      }
      val recalled = decoded.getPositiveLabels.toSet & dev.getPositiveLabels.toSet
      for (label <- recalled) {
//        val word = dev.doc.doc.lines.get(label.getFirst().intValue).get(label.getSecond().intValue)
        val word = dev.doc.doc.lines.get(label._1).get(dev.doc.nps(label._1)(label._2)._2)
        val preprocWord = removeTrailingPunc(word)
//        var seenInTrain = false
//        for (trainProduct <- trainProducts) {
//          seenInTrain = seenInTrain || trainProduct.contains(preprocWord.toLowerCase)
//        }
        val seenInTrain = ProductClusterer.hasProductBeenSeenBefore(trainProductsLc, preprocWord.toLowerCase)
        if (seenInTrain) {
          numCorrectSeenInTrain += 1;
        }
      }
      
      // ITERATE OVER GOLDS
      for (label <- dev.getPositiveLabels) {
        val word = dev.doc.doc.lines.get(label._1).get(dev.doc.nps(label._1)(label._2)._2)
        val preprocWord = removeTrailingPunc(word)
        val seenInTrain = ProductClusterer.hasProductBeenSeenBefore(trainProductsLc, preprocWord.toLowerCase)
        val isCorrect = decoded.getPositiveLabels.contains(label)
        if (seenInTrain) {
//          Logger.logss("##SEEN: " + word)
          numGoldSeenInTrain += 1
        } else {
          if (printDetailedErrors) {
            Logger.logss("--UNSEEN " + (if (isCorrect) "HIT" else "MISSED") + " GOLD: " + word)
            if (npSystem.isInstanceOf[ProductNPPredictor]) {
              val feats = npSystem.asInstanceOf[ProductNPPredictor].getActiveFeatures(dev.doc, label._1, label._2)
              feats.keepTopNKeysByAbsValue(10)
              Logger.logss("Features: " + feats)
            }
          }
        }
      }
      
      // ITERATE OVER PREDICTIONS
      for (label <- decoded.getPositiveLabels) {
        val word = dev.doc.doc.lines.get(label._1).get(dev.doc.nps(label._1)(label._2)._2)
        val preprocWord = removeTrailingPunc(word)
        val seenInTrain = ProductClusterer.hasProductBeenSeenBefore(trainProductsLc, preprocWord.toLowerCase)
        val isCorrect = dev.getPositiveLabels.contains(label)
        if (seenInTrain) {
          numPredictionsSeenInTrain += 1
        } else {
          if (!isCorrect) {
            if (printDetailedErrors) {
              Logger.logss("--UNSEEN BAD PREDICTION: " + word)
              // Only write incorrect because 
              if (npSystem.isInstanceOf[ProductNPPredictor]) {
                val feats = npSystem.asInstanceOf[ProductNPPredictor].getActiveFeatures(dev.doc, label._1, label._2)
                feats.keepTopNKeysByAbsValue(10)
                Logger.logss("Features: " + feats)
              }
            }
          }
        }
      }
      
      numCorrect += recalled.size
      recallDenom += dev.getPositiveLabels.size
      
      precDenom += decoded.getPositiveLabels.size
      
      // LOOK AT PREDICTIONS ON UNANIMOUS VS. AMBIGUOUS ANNOTATIONS
      for (label <- dev.getPositiveLabels) {
        val isCorrect = decoded.getPositiveLabels.contains(label)
        val isUnanimous = dev.getLabelCount(label._1, label._2) > 2
        if (isUnanimous) {
          if (isCorrect) recallUnanimousNumer += 1
          recallUnanimousDenom += 1
        } else {
          if (isCorrect) recallAmbiguousNumer += 1
          recallAmbiguousDenom += 1
        }
      }
      
      // EXACT MATCH
      val isExactMatch = recalled.size == dev.getPositiveLabels.size
      numDocsWithNProducts(Math.min(dev.getPositiveLabels.size, maxProductCount)) += 1
      if (isExactMatch) {
        numExactMatch += 1
        numExactMatchByCount(Math.min(recalled.size, maxProductCount)) += 1
      }
      
    }
    Logger.logss("Fraction seen in train that were recalled: " + Util.renderNumerDenom(numCorrectSeenInTrain, numGoldSeenInTrain))
    Logger.logss("Fraction not seen in train that were recalled: " + Util.renderNumerDenom(numCorrect - numCorrectSeenInTrain, recallDenom - numGoldSeenInTrain))
    Logger.logss("Overall recall: " + Util.renderNumerDenom(numCorrect, recallDenom))
    Logger.logss("Fraction predictions seen in train: " + Util.renderNumerDenom(numPredictionsSeenInTrain, precDenom))
    Logger.logss("Overall precision: " + Util.renderNumerDenom(precNumer, precDenom))
    Logger.logss("Recall of unanimous things: " + Util.renderNumerDenom(recallUnanimousNumer, recallUnanimousDenom))
    Logger.logss("Recall of ambiguous things: " + Util.renderNumerDenom(recallAmbiguousNumer, recallAmbiguousDenom))
    Logger.logss("Exact match: " + Util.renderNumerDenom(numExactMatch, devNPDocs.size))
    for (i <- 0 to maxProductCount) {
      Logger.logss("Exact match on docs with " + i + " products: " + Util.renderNumerDenom(numExactMatchByCount(i), numDocsWithNProducts(i)))
    }
  }
  
  def doDocumentLevelErrorAnalysis(trainSet: Seq[LabeledDocument], devSet: Seq[LabeledDocument], docLevelSystem: NPDocLevelModel) {
    val trainNPDocs = trainSet.map(ex => NPDocument.createFromLabeledDocument(ex, maxNpLen, includeVerbs, useNpMajority))
    val devNPDocs = devSet.map(ex => NPDocument.createFromLabeledDocument(ex, maxNpLen, includeVerbs, useNpMajority))
    
    // For correct products, did we pick the 1st/2nd/3rd/etc. one in the document?
    val productPositionsChosen = new Counter[String]
    for (dev <- devNPDocs) {
      val decoded = docLevelSystem.predict(dev.doc)
      val isCorrect = (dev.getPositiveLabels.size == 0 && decoded.getPositiveLabels.size == 0) ||
                      (decoded.getPositiveLabels.size == 1 && dev.getPositiveLabels.contains(decoded.getPositiveLabels(0)))
      if (isCorrect) {
        if (decoded.getPositiveLabels.size == 0) {
          productPositionsChosen.incrementCount("0/0", 1.0)
        } else {
          val totalProds = dev.getPositiveLabels.size
          val prodPosn = dev.getPositiveLabels.indexOf(decoded.getPositiveLabels(0))
          productPositionsChosen.incrementCount("Total products = " + Math.min(totalProds, 4) + ", Chose index = " + (Math.min(prodPosn, 3) + 1), 1.0)
        }
      } else {
        Logger.logss(dev.doc.doc.documentId)
        val activeSents = (Set(0, 1) ++ dev.getPositiveLabels.map(_._1).toSet ++ decoded.getPositiveLabels.map(_._1).toSet).toSeq.sorted 
        for (sentIdx <- activeSents) {
          if (sentIdx < dev.doc.doc.lines.size) {
            val goldNps = dev.getPositiveLabels.filter(_._1 == sentIdx).map(posLabel => dev.doc.nps(sentIdx)(posLabel._2))
            val predNps = decoded.getPositiveLabels.filter(_._1 == sentIdx).map(posLabel => dev.doc.nps(sentIdx)(posLabel._2))
            Logger.logss(NPDocument.renderSentenceWithGoldPredNps(dev.doc.doc.lines.get(sentIdx).asScala, goldNps, predNps))
          }
        }
      }
    }
    Logger.logss("Analysis of chosen positions in correct documents")
    Logger.logss(devNPDocs.size + " total docs, " + productPositionsChosen.totalCount + " correct predictions")
    for (key <- productPositionsChosen.keySet.asScala.toSeq.sorted) {
      Logger.logss(key + ": " + productPositionsChosen.getCount(key))
    }
  }
}
