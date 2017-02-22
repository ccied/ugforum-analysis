package data

import edu.berkeley.nlp.futile.fig.basic.IOUtils
import scala.collection.JavaConverters._
import edu.berkeley.nlp.futile.util.Logger
import data.EditDistance.EditOp

/**
 * Converts token-level annotations on untokenized text to token-level annotations on tokenized text.
 */
object AnnotationPorter {

  def main(args: Array[String]) {
    
    val annots = "../greg_scratch/blackhat-annotations.txt"
    val dir = "../data/blackhat/raw/pub-threads-first/"
    val outAnnots = "../greg_scratch/blackhat-annotations-ported-to-tok.txt"
    
    val writer = IOUtils.openOutHard(outAnnots)
    for (line <- IOUtils.readLinesHard(annots).asScala) {
      if (line.trim.isEmpty) {
        writer.println(line)
      } else {
        val lineFields = line.split("\\s+")
        
        val fileName = lineFields(0)
        
        val untokFileSausage = IOUtils.readLinesHard(dir + fileName).asScala.reduce(_ + "\n" + _)
        val tokFileSausage = IOUtils.readLinesHard(dir + fileName + ".tok").asScala.reduce(_ + "\n" + _)
        
        // Recomputes this for each annotation, but it's fast anyway...
        val edOps = EditDistance.getEditDistanceOperations(untokFileSausage.toCharArray().toSeq.map(_ + "").asJava, tokFileSausage.toCharArray().toSeq.map(_ + "").asJava)
        val start = lineFields(2).toInt
        val end = lineFields(4).toInt
        
        var srcIdx = 0
        var endSet = false
        var trgIdx = 0
        var edIdx = 0
        while (edIdx < edOps.size) {
          if (start == srcIdx) {
            lineFields(2) = trgIdx + ""
          }
          if (end == srcIdx && !endSet) {
            lineFields(4) = trgIdx + ""
            endSet = true
          }
          edOps(edIdx) match {
            case EditOp.INSERTION => {
              trgIdx += 1
            }
            case EditOp.DELETION => {
              srcIdx += 1
            }
            case EditOp.EQUALITY => {
              srcIdx += 1
              trgIdx += 1
            }
            case EditOp.SUBSTITUTION => {
//              Logger.logss("SUBSTITUTION: " + untokFileSausage(srcIdx) + " " + tokFileSausage(trgIdx))
              srcIdx += 1
              trgIdx += 1
            }
          }
          edIdx += 1
        }
        if (end == srcIdx) {
          lineFields(4) = trgIdx + ""
        }
        val oldWord = untokFileSausage.slice(start, end)
        val newWord = tokFileSausage.slice(lineFields(2).toInt, lineFields(4).toInt)
        if (oldWord != newWord) {
          Logger.logss(":" + oldWord + ": -> :" + newWord + ":")
          if (oldWord.toLowerCase == "eu-c.com") {
            lineFields(4) = (lineFields(4).toInt - 6) + ""
            Logger.logss("New word: " + tokFileSausage.slice(lineFields(2).toInt, lineFields(4).toInt))
          }
        }
        writer.println(lineFields.reduce(_ + " " + _))
      }
    }
    writer.close()
//    blackhat/annotated/2014_09_25/standoff.txt
    
//    blackhat/raw/pub-threads-first/
  }
}