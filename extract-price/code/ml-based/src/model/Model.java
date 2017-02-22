package model;

import java.util.List;

import tuple.Pair;
import data.Dataset.Document;
import data.Dataset.LabeledDocument;
import eval.EvalStats;

public abstract class Model {
  public abstract LabeledDocument predict(Document datum);
  
  public EvalStats evaluate(List<LabeledDocument> data) {
    float tp = 0.0f;
    float fp = 0.0f;
    float fn = 0.0f;
    for (LabeledDocument datum : data) {
      LabeledDocument predDatum = predict(datum.document);
      for (Pair<Integer,Integer> predPos : predDatum.positiveLabels) {
        boolean found = false;
        for (Pair<Integer,Integer> goldPos : datum.positiveLabels) {
          if (predPos.equals(goldPos)) {
            tp++;
            found = true;
            break;
          }
        }
        if (!found) fp++;
      }
      for (Pair<Integer,Integer> goldPos : datum.positiveLabels) {
        boolean found = false;
        for (Pair<Integer,Integer> predPos : predDatum.positiveLabels) {
          if (goldPos.equals(predPos)) {
            found = true;
            break;
          }
        }
        if (!found) fn++;
      }
    }
    return new EvalStats(tp, fp, fn);
  }
}
