package edu.berkeley.forum.model

import edu.berkeley.forum.data.NPDocument
import edu.berkeley.forum.data.LabeledNPDocument

trait NPDocLevelModel extends NPModel {
  def predict(datum: NPDocument): LabeledNPDocument;
}
