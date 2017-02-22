package edu.berkeley.forum.sig

import edu.berkeley.nlp.futile.fig.basic.IOUtils
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import edu.berkeley.nlp.futile.util.Logger

object BootstrapDriverProducts {
  
  def readProductsFile(file: String): HashMap[String,ArrayBuffer[Seq[Double]]] = {
    val linesItr = IOUtils.lineIterator(IOUtils.openInHard(file))
//    val allValues = new HashMap[String,Seq[Seq[Double]]]()
//    while (linesItr.hasNext) {
//      val line = linesItr.next
//      if (currLabel == "" && line.startsWith("EVAL")) {
//        currLabel = line.substring(0, line.indexOf(":"))
//      }
//      if (currLabel != "" && line.startsWith("EVAL")) {
//        currVals += line.substring(line.indexOf(":") + 2).split("\\s+").map(str => str.toDouble).toSeq
//      }
//      if (currLabel != "" && !line.startsWith("EVAL")) {
//        allValues += currLabel -> currVals
//        currVals = new ArrayBuffer[Seq[Double]]
//        currLabel = ""
//      }
//    }
    val allValues = new HashMap[String,ArrayBuffer[Seq[Double]]]()
    while (linesItr.hasNext) {
      val line = linesItr.next
      if (line.startsWith("EVAL")) {
        val currLabel = line.substring(0, line.indexOf(":"))
        val currVals = line.substring(line.indexOf(":") + 2).split("\\s+").map(str => str.toDouble).toSeq
        if (!allValues.contains(currLabel)) {
          allValues.put(currLabel, new ArrayBuffer[Seq[Double]])
        }
        allValues(currLabel) += currVals
      }
    }
    allValues
  }

  def main(args: Array[String]) {
    val worseFilePath = args(0);
    val betterFilePath = args(1);
    Logger.logss("Stat sig on " + worseFilePath + " " + betterFilePath)
    
    val worseValues = readProductsFile(worseFilePath)
    val betterValues = readProductsFile(betterFilePath)
    
    Logger.logss(worseValues.keySet)
    Logger.logss(betterValues.keySet)
    
    for (metricDatasetPair <- worseValues.keySet) {
      val comp = if (worseValues(metricDatasetPair).head.size == 3) new F1Computer(0, 1, 0, 2) else new AccuracyComputer
      Logger.logss(metricDatasetPair)
      require(worseValues(metricDatasetPair).size == betterValues(metricDatasetPair).size, metricDatasetPair + " " + worseValues(metricDatasetPair).size + " " + betterValues(metricDatasetPair).size)
      BootstrapDriver.printSimpleBootstrapPValue(worseValues(metricDatasetPair), betterValues(metricDatasetPair), comp)
    }
  }
}
