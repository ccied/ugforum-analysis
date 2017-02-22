package edu.berkeley.forum.main

import edu.berkeley.forum.data.Dataset
import scala.collection.JavaConverters._

object ForumDatasets {
  
  def darkodeRawDirs(dataReleasePrefix: String) =
    IndexedSeq(dataReleasePrefix + "/darkode/tokenized/buy",
               dataReleasePrefix + "/darkode/tokenized/sell_unverified",
               dataReleasePrefix + "/darkode/tokenized/sell_verified",
               dataReleasePrefix + "/darkode/tokenized/other").asJava
               
  def darkodeParsedDirs(dataReleasePrefix: String) =
    IndexedSeq(dataReleasePrefix + "/darkode/parsed/buy",
               dataReleasePrefix + "/darkode/parsed/sell_unverified",
               dataReleasePrefix + "/darkode/parsed/sell_verified",
               dataReleasePrefix + "/darkode/parsed/other").asJava
  
  def loadDarkode(dataReleasePrefix: String,
                  filePrefix: String,
                  fileSuffix: String) = {
    Dataset.loadDatasetNew(darkodeRawDirs(dataReleasePrefix),
                           darkodeParsedDirs(dataReleasePrefix),
                           dataReleasePrefix + "/darkode/annotations/train-annotations.txt",
                           dataReleasePrefix + "/darkode/annotations/dev-annotations.txt",
                           dataReleasePrefix + "/darkode/annotations/test-annotations.txt",
                           filePrefix, fileSuffix, fileSuffix, "darkode", "merged");
  }
  
  def loadDarkodeNoAnnots(dataReleasePrefix: String,
                          filePrefix: String,
                          fileSuffix: String) = {
    Dataset.loadDatasetNoAnnots(Seq(dataReleasePrefix + "/darkode/parsed/buy",
                                    dataReleasePrefix + "/darkode/parsed/sell_verified",
                                    dataReleasePrefix + "/darkode/parsed/sell_unverified").asJava,
                                filePrefix, fileSuffix, "darkode")
  }
  
  def hfRawDirs(dataReleasePrefix: String) =
    IndexedSeq(dataReleasePrefix + "/hackforums/tokenized/all_nocurr_noprem/",
               dataReleasePrefix + "/hackforums/tokenized/premium_sellers_section/").asJava
  def hfParsedDirs(dataReleasePrefix: String) =
    IndexedSeq(dataReleasePrefix + "/hackforums/parsed/all_nocurr_noprem/",
               dataReleasePrefix + "/hackforums/parsed/premium_sellers_section/").asJava
  
  def loadHackforumsTrainDev(dataReleasePrefix: String,
                             filePrefix: String,
                             fileSuffix: String,
                             hfAnnotThreshold: Int = 2) = {
    Dataset.loadTestDatasetNew(hfRawDirs(dataReleasePrefix), hfParsedDirs(dataReleasePrefix),
                               dataReleasePrefix + "/hackforums/annotations/train-annotations.txt",
                               filePrefix, fileSuffix, fileSuffix, "hackforum", hfAnnotThreshold);
  }
  
  def loadHackforumsTest(dataReleasePrefix: String,
                         filePrefix: String,
                         fileSuffix: String,
                         hfTestAnnotThreshold: Int = 2) = {
    Dataset.loadTestDatasetNew(hfRawDirs(dataReleasePrefix), hfParsedDirs(dataReleasePrefix),
                               dataReleasePrefix + "/hackforums/annotations/test-annotations.txt",
                               filePrefix, fileSuffix, fileSuffix, "hackforum", hfTestAnnotThreshold);
  }
  
  def loadHackforumsNoAnnots(hackforumsAllProcessedPath: String,
                             filePrefix: String,
                             fileSuffix: String) = {
    Dataset.loadDatasetNoAnnots(hackforumsAllProcessedPath, filePrefix, fileSuffix, "hackforums")
  }
  
  def loadBlackhatTest(dataReleasePrefix: String,
                       filePrefix: String,
                       fileSuffix: String) = {
    Dataset.loadTestDatasetNew(Seq(dataReleasePrefix + "/blackhat/tokenized/pub_threads").asJava,
                               Seq(dataReleasePrefix + "/blackhat/parsed/pub_threads").asJava,
                               dataReleasePrefix + "/blackhat/annotations/test-annotations.txt", filePrefix, fileSuffix, fileSuffix, "blackhat", 2).asScala;
  }
  
  def loadNulledTest(dataReleasePrefix: String,
                     filePrefix: String,
                     fileSuffix: String) = {
    Dataset.loadTestDatasetNew(Seq(dataReleasePrefix + "/nulled/tokenized").asJava,
                               Seq(dataReleasePrefix + "/nulled/parsed").asJava,
                               dataReleasePrefix + "/nulled/annotations/test-annotations.txt", filePrefix, fileSuffix, fileSuffix, "nulled", 1).asScala;
  }
  
  def loadHellTest(dataReleasePrefix: String,
                   filePrefix: String,
                   fileSuffix: String) = {
    Dataset.loadTestDatasetNew(Seq(dataReleasePrefix + "/hell/tokenized").asJava,
                               Seq(dataReleasePrefix + "/hell/parsed").asJava,
                               dataReleasePrefix + "/hell/annotations/test-annotations.txt", filePrefix, fileSuffix, fileSuffix, "hell", 1).asScala;
  }
}
