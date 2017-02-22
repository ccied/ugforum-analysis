package edu.berkeley.forum.main

import edu.berkeley.forum.model.NPModel
import edu.berkeley.nlp.futile.LightRunner
import edu.berkeley.nlp.futile.fig.basic.IOUtils

object SystemEvaluator {
  
  val dataReleasePath: String = ""
  // Only train on files in the directories with this prefix
  val filePrefix = "0-initiator"
  // Only train on files in the directories with this suffix
  val fileSuffix = ".txt.tok"
  val modelPath = "models/bothmodel-doclevel.ser.gz"
  
  def main(args: Array[String]) {
    LightRunner.initializeOutput(SystemEvaluator.getClass())
    LightRunner.populateScala(SystemEvaluator.getClass(), args)
    
    val system = IOUtils.readObjFileHard(modelPath).asInstanceOf[NPModel]
    
    
    val data = ForumDatasets.loadNulledTest(dataReleasePath, filePrefix, fileSuffix)
//    val data = ForumDatasets.loadBlackhatTest(dataReleasePath, filePrefix, fileSuffix)
//    val data = ForumDatasets.loadHellTest(dataReleasePath, filePrefix, fileSuffix)
    Trainer.evaluate(system, data, false)
    
    LightRunner.finalizeOutput()
  }
}