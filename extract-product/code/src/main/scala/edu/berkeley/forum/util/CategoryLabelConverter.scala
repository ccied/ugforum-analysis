package edu.berkeley.forum.util

import scala.io.Source
import scala.collection.mutable.ArrayBuffer
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import scala.collection.mutable.HashMap

object CategoryLabelConverter {
  
  def readCsvLine(line: String): IndexedSeq[String] = {
    var inQuote = false
    var currTok = ""
    val entries = new ArrayBuffer[String]
    for (c <- line) {
      if (c == ',') {
        if (inQuote) {
          currTok += c
        } else {
          entries += currTok
          currTok = ""
        }
      } else if (c == '"') {
        inQuote = !inQuote
      } else {
        currTok += c
      }
    }
    entries :+ currTok
  }
  
  def loadCsv(file: String): IndexedSeq[IndexedSeq[String]] = {
//    val lines = Source.fromFile(file).getLines().toIndexedSeq.map(_.split(",").toIndexedSeq)
    val lines = Source.fromFile(file).getLines().toIndexedSeq.map(readCsvLine(_))
    for (line <- lines) {
      if (line.size != 7) {
        System.out.println("Anomalous line: " + line)
      }
    }
    lines
  }
  
  def augmentCsv(csvLines: IndexedSeq[IndexedSeq[String]], annotDirs: IndexedSeq[String]) = {
    val annots = loadFileAnnots(annotDirs)
    var numUsed = 0
    val augCsv = csvLines.map(line => {
      if (annots.contains(line(0))) {
        numUsed += 1
        line ++ annots(line(0)) 
      } else {
        line ++ IndexedSeq("", "")
      }
    })
    System.out.println(numUsed + " / " + annots.size)
    augCsv
  }
  
  // Loads from two-level directory structure
  def loadFileAnnots(dirs: IndexedSeq[String]) = {
    val map = new HashMap[String,IndexedSeq[String]]
    for (dir <- dirs) {
      for (subDir <- new File(dir).listFiles()) {
        for (file <- subDir.listFiles()) {
          val lines = scala.io.Source.fromFile(file).getLines()
          if (lines.hasNext) {
            val firstLine = lines.next
            if (firstLine.startsWith("#")) {
              val spaceIdx = firstLine.indexOf(" ")
              val label = firstLine.substring(0, if (spaceIdx == -1) firstLine.size else spaceIdx)
              val service = if (spaceIdx == -1) "" else firstLine.substring(spaceIdx + 1)
              map += file.getName().replace("0-initiator", "").replace(".txt.tok", "") -> IndexedSeq(label, service)
            }
          }
        }
      }
    }
    map
  }
}
