package data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class EditDistance {
  
  public static enum EditOp {
    INSERTION, DELETION, SUBSTITUTION, EQUALITY;
  }
  
  private static <T> double[][] getEditDistanceMat(List<T> src, List<T> trg, double insCost, double delCost, double substCost, boolean useEqualsMethod) {
    double[][] dpMat = new double[src.size() + 1][trg.size() + 1];
    for (int i = 1; i < dpMat.length; i++) {
      dpMat[i][0] = dpMat[i-1][0] + delCost;
    }
    for (int j = 1; j < dpMat[0].length; j++) {
      dpMat[0][j] = dpMat[0][j-1] + insCost;
    }
    for (int i = 1; i < src.size() + 1; i++) {
      for (int j = 1; j < trg.size() + 1; j++) {
        Object srcObj = src.get(i-1);
        Object trgObj = trg.get(j-1);
        boolean areEqual = (useEqualsMethod ? srcObj.equals(trgObj) : srcObj == trgObj);
        dpMat[i][j] = Math.min(Math.min(dpMat[i-1][j] + delCost, dpMat[i][j-1] + insCost), dpMat[i-1][j-1] + (areEqual ? 0.0 : substCost));
      }
    }
    return dpMat;
  }

  public static <T> double editDistance(List<T> src, List<T> trg) {
    return editDistance(src, trg, 1.0, 1.0, 1.0, true);
  }
  
  public static <T> double editDistance(List<T> src, List<T> trg, double insCost, double delCost, double substCost, boolean useEqualsMethod) {
    double[][] dpMat = getEditDistanceMat(src, trg, insCost, delCost, substCost, useEqualsMethod);
    return dpMat[src.size()][trg.size()];
  }

  public static <T> EditOp[] getEditDistanceOperations(List<T> src, List<T> trg) {
    return getEditDistanceOperations(src, trg, 1.0, 1.0, 1.0, true);
  }
  
  public static <T> EditOp[] getEditDistanceOperations(List<T> src, List<T> trg, double insCost, double delCost, double substCost, boolean useEqualsMethod) {
    double[][] dpMat = getEditDistanceMat(src, trg, insCost, delCost, substCost, useEqualsMethod);
    EditOp[][] optimalEditOps = new EditOp[src.size() + 1][trg.size() + 1];
    for (int i = 1; i < optimalEditOps.length; i++) {
      optimalEditOps[i][0] = EditOp.DELETION;
    }
    for (int j = 1; j < optimalEditOps[0].length; j++) {
      optimalEditOps[0][j] = EditOp.INSERTION;
    }
    for (int i = 1; i < src.size() + 1; i++) {
      for (int j = 1; j < trg.size() + 1; j++) {
        Object srcObj = src.get(i-1);
        Object trgObj = trg.get(j-1);
        boolean areEqual = (useEqualsMethod ? srcObj.equals(trgObj) : srcObj == trgObj);
        double delScore = dpMat[i-1][j] + delCost;
        double insScore = dpMat[i][j-1] + insCost;
        double substScore = dpMat[i-1][j-1] + (areEqual ? 0.0 : substCost);
        if (substScore <= insScore && substScore <= delScore) {
          optimalEditOps[i][j] = (areEqual ? EditOp.EQUALITY : EditOp.SUBSTITUTION);
        } else if (delScore <= substScore && delScore <= insScore) {
          optimalEditOps[i][j] = EditOp.DELETION;
        } else {   // (insScore <= substScore && insScore <= delScore)
          optimalEditOps[i][j] = EditOp.INSERTION;
        }
      }
    }
    List<EditOp> editList = new ArrayList<EditOp>();
    int currI = src.size();
    int currJ = trg.size();
    while (currI > 0 || currJ > 0) {
      EditOp op = optimalEditOps[currI][currJ];
      editList.add(0, op);
      if (op == EditOp.DELETION || op == EditOp.SUBSTITUTION || op == EditOp.EQUALITY) {
        currI--;
      }
      if (op == EditOp.INSERTION || op == EditOp.SUBSTITUTION || op == EditOp.EQUALITY) {
        currJ--;
      }
    }
    return editList.toArray(new EditOp[editList.size()]);
  }
  
  public static List<Character> convert(String str) {
    List<Character> charList = new ArrayList<Character>();
    for (int i = 0; i < str.length(); i++) {
      charList.add(str.charAt(i));
    }
    return charList;
  }
  
  public static void main(String[] args) {
    System.out.println(editDistance(convert("stuff"), convert("stuff2"), 1.0, 1.0, 1.0, true)); // 1
    System.out.println(editDistance(convert("stuff"), convert("stuff2"), 2.0, 1.0, 1.0, true)); // 2
    System.out.println(editDistance(convert("staff"), convert("stufs"), 2.0, 1.0, 1.0, true)); // 2
    
    System.out.println(Arrays.toString(getEditDistanceOperations(convert("stuff"), convert("stuff2"), 1.0, 1.0, 1.0, true))); // [EQUALITY, EQUALITY, EQUALITY, EQUALITY, EQUALITY, INSERTION]
    System.out.println(Arrays.toString(getEditDistanceOperations(convert("stuff"), convert("tff"), 2.0, 1.0, 1.0, true))); // [DELETION, EQUALITY, DELETION, EQUALITY, EQUALITY]
    System.out.println(Arrays.toString(getEditDistanceOperations(convert("staff"), convert("stufs"), 2.0, 1.0, 1.0, true))); // [EQUALITY, EQUALITY, SUBSTITUTION, EQUALITY, SUBSTITUTION]
  }
}
