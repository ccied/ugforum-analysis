package edu.berkeley.forum.eval;


public class EvalStats {
  private float truePositive;
  private float falsePositive;
  private float falseNegative;

  public EvalStats(float truePositive, float falsePositive, float falseNegative) {
    this.truePositive = truePositive;
    this.falsePositive = falsePositive;
    this.falseNegative = falseNegative;
  }

  public void increment(EvalStats other) {
    this.truePositive += other.truePositive;
    this.falsePositive += other.falsePositive;
    this.falseNegative += other.falseNegative;
  }

  public float getPrecision() {
    return truePositive / (truePositive + falsePositive);
  }

  public float getRecall() {
    return truePositive / (truePositive + falseNegative);
  }

  public float getF1() {
    if (getPrecision() + getRecall() == 0) {
      return 0;
    }
    return 2.0f * getPrecision() * getRecall() / (getPrecision() + getRecall());
  }

  public float getAcc() {
    return truePositive / (truePositive + falseNegative + falsePositive);
  }

  public String toString() {
    return String.format("prec: %f, recall: %f, f1: %f, acc: %f", getPrecision(), getRecall(), getF1(), getAcc());
  }
  
  public static String fmtPositiveNumber(float number, int decimals) {
    String numStr = ("" + number);
    if (numStr.contains(".")) {
      return numStr.substring(0, Math.min(numStr.length(), numStr.indexOf(".") + 1 + decimals));
    } else {
      return numStr;
    }
  }

  public String renderForTex() {
    return fmtPositiveNumber(getPrecision() * 100, 1) + " & " + fmtPositiveNumber(getRecall() * 100, 1) + " & " + fmtPositiveNumber(getF1() * 100, 1);
  }
}
