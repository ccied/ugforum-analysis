package edu.berkeley.forum.main

import scala.IndexedSeq
import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.seqAsJavaListConverter
import edu.berkeley.forum.data.Dataset
import edu.berkeley.forum.data.Dataset.Document
import edu.berkeley.forum.data.Dataset.LabeledDocument
import edu.berkeley.nlp.futile.util.Logger
import edu.berkeley.forum.model.FrequencyBasedPredictor
import edu.berkeley.forum.data.NPDocument
import edu.berkeley.forum.model.FrequencyBasedMemorizingNPPredictor
import edu.berkeley.forum.model.NPDocLevelModel
import edu.berkeley.nlp.futile.LightRunner

object UnsupervisedRunner {
  
  val filePrefix = "0-initiator";
  val annotator = "merged";
  
  val maxNpLen = 7
  val includeVerbs = true
  
  val doUnion = false
  val useNpMajority = false

  def main(args: Array[String]) {
    LightRunner.initializeOutput(UnsupervisedRunner.getClass())
    LightRunner.populateScala(UnsupervisedRunner.getClass(), args)
    val unlabeledTrain = Dataset.loadDatasetNoAnnots(IndexedSeq("../data/darkode/raw/buy", "../data/darkode/raw/sell_unverified", "../data/darkode/raw/sell_verified", "../data/darkode/raw/other").asJava,
                                                     IndexedSeq("../greg_scratch/darkode-buy", "../greg_scratch/darkode-sell-unverified", "../greg_scratch/darkode-sell-verified", "../greg_scratch/darkode-other").asJava,
                                                     "0-initiator", ".txt.tok", ".txt.tok", "darkode")
    val trainDevTest = Dataset.loadDatasetNew(IndexedSeq("../data/darkode/raw/buy", "../data/darkode/raw/sell_unverified", "../data/darkode/raw/sell_verified", "../data/darkode/raw/other").asJava,
                                         IndexedSeq("../greg_scratch/darkode-buy", "../greg_scratch/darkode-sell-unverified", "../greg_scratch/darkode-sell-verified", "../greg_scratch/darkode-other").asJava,
                                         "../greg_scratch/train-annotations.txt", "../greg_scratch/dev-annotations.txt","../greg_scratch/test-annotations.txt", filePrefix, ".txt.tok", ".txt.tok", "darkode", annotator);
    val labeledTrain = trainDevTest.train
    val test = trainDevTest.dev

//    val unlabeledTrain = Dataset.loadDatasetNoAnnots(IndexedSeq("../greg_scratch/hackforums_tokenised/all-nocurr-noprem/", "../greg_scratch/hackforums_tokenised/premium_sellers_section/").asJava,
//                                                       IndexedSeq("../greg_scratch/hackforums-nocurr-noprem-parsed/", "../greg_scratch/hackforums-premium-parsed/").asJava,
//                                                       "0-initiator", ".txt.tok", ".txt.tok")
//    val labeledTrain = Dataset.loadTestDatasetNew(IndexedSeq("../greg_scratch/hackforums_tokenised/all-nocurr-noprem/", "../greg_scratch/hackforums_tokenised/premium_sellers_section/").asJava,
//                                            IndexedSeq("../greg_scratch/hackforums-nocurr-noprem-parsed/", "../greg_scratch/hackforums-premium-parsed/").asJava,
//                                            "data/hackforum-train-all-annots-processed.txt", filePrefix, ".txt.tok", ".txt.tok", doUnion);
////                                            "../data/hackforums/annotated/10_14_2015/all-annots-processed.txt", filePrefix, ".txt.tok", ".txt.tok", doUnion);
//    val test = Dataset.loadTestDatasetNew(IndexedSeq("../greg_scratch/hackforums_tokenised/all-nocurr-noprem/", "../greg_scratch/hackforums_tokenised/premium_sellers_section/").asJava,
//                                            IndexedSeq("../greg_scratch/hackforums-nocurr-noprem-parsed/", "../greg_scratch/hackforums-premium-parsed/").asJava,
////                                            "../hackforum-temp-labels/all-annots-processed.txt", filePrefix, ".txt.tok", ".txt.tok", doUnion);
//                                            "data/hackforum-test-all-annots-processed.txt", filePrefix, ".txt.tok", ".txt.tok", doUnion);
    
    
//    val model = FrequencyBasedMemorizingNPPredictor.extractPredictor(labeledTrain.asScala)
    val model = FrequencyBasedMemorizingNPPredictor.extractPredictorFromNPs(labeledTrain.asScala.map(doc => NPDocument.createFromLabeledDocument(doc, maxNpLen, includeVerbs, useNpMajority)),
                                                                            restrictToPickOne = false)
    trainEval(unlabeledTrain.asScala, test.asScala, model)
    LightRunner.finalizeOutput()
  }
  
  def trainEval(unlabeledData: Seq[Document], testSet: Seq[LabeledDocument], labelingModel: NPDocLevelModel) {
    Logger.logss(unlabeledData.size + " train documents")
    val trainNPDocs = unlabeledData.map(doc => NPDocument.createFromDocument(doc, maxNpLen, includeVerbs))
//    val frequencyPredictor = new FrequencyBasedPredictor
//    val labeledTrainNPDocs = trainNPDocs.map(doc => frequencyPredictor.predict(doc))
    val labeledTrainNPDocs = trainNPDocs.map(doc => labelingModel.predict(doc))
    val testSetNPDocs = testSet.map(ex => NPDocument.createFromLabeledDocument(ex, maxNpLen, includeVerbs, useNpMajority)) 
    Trainer.trainDocumentLevelNPSystemFromNPs(labeledTrainNPDocs, testSetNPDocs)
    Logger.logss("Raw labeling model's performance on test set")
    Trainer.evaluateNPs(labelingModel, testSetNPDocs, true)
  }
}
