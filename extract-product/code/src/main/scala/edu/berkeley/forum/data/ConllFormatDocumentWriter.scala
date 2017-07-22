package edu.berkeley.forum.data

import edu.berkeley.forum.data.Dataset.LabeledDocument
import java.io.PrintWriter

object ConllFormatDocumentWriter {

  def writeLabeledDocuments(predDocs: Seq[LabeledDocument], goldDocs: Seq[LabeledDocument], writer: PrintWriter) {
    for ((predDoc, goldDoc) <- predDocs.zip(goldDocs)) {
      writer.println("NEWFILE----" + predDoc.document.forumName + "/" + predDoc.document.documentId)
      writer.println()
      for (i <- 0 until predDoc.document.lines.size) {
        for (j <- 0 until predDoc.document.lines.get(i).size) {
          val word = predDoc.document.lines.get(i).get(j)
          require(word == goldDoc.document.lines.get(i).get(j), "Docs don't match!")
          writer.println(word + "\t" + bOrO(i, j, goldDoc.positiveLabels) + "\t" + bOrO(i, j, predDoc.positiveLabels))
        }
        writer.println()
      }
    }
    writer.close()
  }
  
  private def bOrO(i: Int, j: Int, positiveLabels: java.util.List[tuple.Pair[Integer,Integer]]) = {
    if (positiveLabels.contains(new tuple.Pair(new Integer(i), new Integer(j)))) "B" else "O"
  }
}