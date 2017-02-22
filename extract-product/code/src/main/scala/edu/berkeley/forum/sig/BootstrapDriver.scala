package edu.berkeley.forum.sig

import scala.io.Source
import scala.collection.mutable.ArrayBuffer
import scala.util.Random
import edu.berkeley.nlp.futile.util.Logger

object BootstrapDriver {
  
  def printAggregatedPValue(worseSuffStats: Seq[Seq[Seq[Double]]], betterSuffStats: Seq[Seq[Seq[Double]]], metricComputer: MetricComputer, stratified: Boolean): (Double, Double) = {
    val rng = new Random(0);
    val experOffsets = new Array[Int](worseSuffStats.size);
    for (i <- 0 until worseSuffStats.size) {
      experOffsets(i) = (if (i == 0) 0 else experOffsets(i-1) + worseSuffStats(i-1).size);
    }
    val allWorseSuffStats = worseSuffStats.flatMap(lst => lst);
    val allBetterSuffStats = betterSuffStats.flatMap(lst => lst);
//    var numBetter = 0;
//    var numEq = 0;
//    for (i <- 0 until allWorseSuffStats.size) {
//      if (metricComputer.computeMetric(Seq(allBetterSuffStats(i))) > metricComputer.computeMetric(Seq(allWorseSuffStats(i)))) {
//        numBetter += 1;
//      }
//      if (metricComputer.computeMetric(Seq(allBetterSuffStats(i))) == metricComputer.computeMetric(Seq(allWorseSuffStats(i)))) {
//        numEq += 1;
//      }
//    }
//    println("Num better: " + numBetter + ", num eq: " + numEq + " (/ " + allWorseSuffStats.size + ")");
    var numSigDifferences = 0;
    val NumTrials = 1000;
    for (j <- 0 until NumTrials) {
//      if (j % 1000 == 0) {
//        println(j);
//      }
      val allResampledIndices = if (stratified) {
        val resampled = new ArrayBuffer[Int];
        for (i <- 0 until worseSuffStats.size) {
          resampled ++= resample(worseSuffStats(i).size, rng).map(idx => idx + experOffsets(i));
        }
        resampled;
      } else {
        resample(allWorseSuffStats.size, rng);
      }
      if (metricComputer.isSigDifference(allWorseSuffStats, allBetterSuffStats, allResampledIndices)) {
        numSigDifferences += 1;
      }
    }
    val baselineFull = metricComputer.computeMetricFull(allWorseSuffStats);
    val improvedFull = metricComputer.computeMetricFull(allBetterSuffStats);
    val fracSig = (numSigDifferences.toDouble/NumTrials.toDouble);
    println("Overall (stratified = " + stratified + "): fraction sig = " + fracSig);
    val dag = if (fracSig > 0.95) "\\ddag" else if (fracSig > 0.9) "\\dag" else "";
    val baselineStrList = baselineFull.map(entry => MetricComputer.fmtTwoDigitNumber(entry, 2));
    val improvedStrList = improvedFull.map(entry => MetricComputer.fmtTwoDigitNumber(entry, 2));
    val improvedStrListMutable = new ArrayBuffer[String]();
    improvedStrListMutable ++= improvedStrList;
    improvedStrListMutable(improvedStrListMutable.size-1) = dag + improvedStrListMutable(improvedStrListMutable.size-1);
//    println(baselineStrList.foldLeft("")((str, entry) => str + " & " + entry));
//    println(improvedStrListMutable.foldLeft("")((str, entry) => str + " & " + entry));
    (baselineFull(baselineFull.size - 1), improvedFull(improvedFull.size - 1))
  }
  
  def printSimpleBootstrapPValue(worseSuffStats: Seq[Seq[Double]], betterSuffStats: Seq[Seq[Double]], metricComputer: MetricComputer) {
    val rng = new Random(0);
    var numSigDifferences = 0;
    val NumTrials = 1000;
    for (j <- 0 until NumTrials) {
//      if (j % 1000 == 0) {
//        println(j);
//      }
      val resampledIndices = resample(worseSuffStats.size, rng);
      if (metricComputer.isSigDifference(worseSuffStats, betterSuffStats, resampledIndices)) {
        numSigDifferences += 1;
      }
    }
    val baselineFull = metricComputer.computeMetricFull(worseSuffStats);
    val improvedFull = metricComputer.computeMetricFull(betterSuffStats);
    val fracSig = (numSigDifferences.toDouble/NumTrials.toDouble);
    Logger.logss("1-p = " + fracSig);
    // Print the last metric value since it's usually F1 or whatever
    Logger.logss("Results: " + baselineFull(baselineFull.size - 1) + " " + improvedFull(improvedFull.size - 1));
  }
  
  def fmtMetricValues(suffStats: Seq[Double]): String = {
    MetricComputer.fmtMetricValues(suffStats);
  }
  
  def resample(size: Int, rng: Random): Seq[Int] = {
    (0 until size).map(i => rng.nextInt(size));
  }
}
