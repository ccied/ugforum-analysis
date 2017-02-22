package util;

import java.util.ArrayList;
import java.util.List;

public class a {
	
	// double int
	
	// vector
	
	public static int[] append(int[] vect1, int x) {
		int[] result = new int[vect1.length + 1];
		if (vect1.length > 0) System.arraycopy(vect1, 0, result, 0, vect1.length);
		result[result.length-1] = x;
		return result;
	}
	
	public static double[] append(double[] vect1, double x) {
		double[] result = new double[vect1.length + 1];
		if (vect1.length > 0) System.arraycopy(vect1, 0, result, 0, vect1.length);
		result[result.length-1] = x;
		return result;
	}
	
	public static float[] append(float[] vect1, float x) {
		float[] result = new float[vect1.length + 1];
		if (vect1.length > 0) System.arraycopy(vect1, 0, result, 0, vect1.length);
		result[result.length-1] = x;
		return result;
	}
	
	public static int[] append(int x, int[] vect1) {
		int[] result = new int[vect1.length + 1];
		if (vect1.length > 0) System.arraycopy(vect1, 0, result, 1, vect1.length);
		result[0] = x;
		return result;
	}
	
	public static double[] append(double x, double[] vect1) {
		double[] result = new double[vect1.length + 1];
		if (vect1.length > 0) System.arraycopy(vect1, 0, result, 1, vect1.length);
		result[0] = x;
		return result;
	}
	
	public static float[] append(float x, float[] vect1) {
		float[] result = new float[vect1.length + 1];
		if (vect1.length > 0) System.arraycopy(vect1, 0, result, 1, vect1.length);
		result[0] = x;
		return result;
	}
	
	public static int[] append(int[] vect1, int[] vect2) {
		int[] result = new int[vect1.length + vect2.length];
		if (vect1.length > 0) System.arraycopy(vect1, 0, result, 0, vect1.length);
		if (vect2.length > 0) System.arraycopy(vect2, 0, result, vect1.length, vect2.length);
		return result;
	}
	
	public static double[] append(double[] vect1, double[] vect2) {
		double[] result = new double[vect1.length + vect2.length];
		if (vect1.length > 0) System.arraycopy(vect1, 0, result, 0, vect1.length);
		if (vect2.length > 0) System.arraycopy(vect2, 0, result, vect1.length, vect2.length);
		return result;
	}
	
	public static float[] append(float[] vect1, float[] vect2) {
		float[] result = new float[vect1.length + vect2.length];
		if (vect1.length > 0) System.arraycopy(vect1, 0, result, 0, vect1.length);
		if (vect2.length > 0) System.arraycopy(vect2, 0, result, vect1.length, vect2.length);
		return result;
	}
	
	public static int find(int x, int[] vect) {
		for (int i=0; i<vect.length; ++i) {
			if (vect[i] == x) return i;
		}
		return -1;
	}
	
	public static int[] enumerate(int start, int end) {
		int[] result = null;
		if (start < end) {
			result = new int[end - start];
			for (int i=0; i<result.length; ++i) {
				result[i] = start+i;
			}
		} else {
			result = new int[start - end];
			for (int i=0; i<result.length; ++i) {
				result[i] = start-(i+1);
			}
		}
		return result;
	}
	
	public static double[] toDouble(float[] vect1) {
		double[] vect2 = new double[vect1.length];
		for (int i=0; i<vect1.length; ++i) {
			vect2[i] = (double) vect1[i];
		}
		return vect2;
	}
	
	public static int[] toIntArray(List<Integer> list) {
		int[] vect = new int[list.size()];
		for (int i=0; i<list.size(); ++i) {
			vect[i] = list.get(i);
		}
		return vect;
	}
	
	public static double[] toDoubleArray(List<Double> list) {
		double[] vect = new double[list.size()];
		for (int i=0; i<list.size(); ++i) {
			vect[i] = list.get(i);
		}
		return vect;
	}

	
	public static double[] copy(double[] vect) {
		double[] copy = new double[vect.length];
		System.arraycopy(vect, 0, copy, 0, vect.length);
		return copy;
	}
	
	public static int[] copy(int[] vect) {
		int[] copy = new int[vect.length];
		System.arraycopy(vect, 0, copy, 0, vect.length);
		return copy;
	}
	
	public static float[] copy(float[] vect) {
		float[] copy = new float[vect.length];
		System.arraycopy(vect, 0, copy, 0, vect.length);
		return copy;
	}
	
	public static void copyi(double[] vect, double[] copy) {
		System.arraycopy(vect, 0, copy, 0, vect.length);
	}
	
	public static void copyi(int[] vect, int[] copy) {
		System.arraycopy(vect, 0, copy, 0, vect.length);
	}
	
	public static void copyi(float[] vect, float[] copy) {
		System.arraycopy(vect, 0, copy, 0, vect.length);
	}
	
	public static List<Double> toList(double[] vect) {
		List<Double> list = new ArrayList<Double>();
		for (double x : vect) {
			list.add(x);
		}
		return list;
	}
	
	public static List<Integer> toList(int[] vect) {
		List<Integer> list = new ArrayList<Integer>();
		for (int x : vect) {
			list.add(x);
		}
		return list;
	}
	
	public static String toString(double[] vect) {
		StringBuffer buf = new StringBuffer();
		for (int i=0; i<vect.length; ++i) {
			buf.append(String.format("%f", vect[i]));
			if (i != vect.length-1) {
				buf.append("\t");
			}
		}
		return buf.toString();
	}
	
	public static String toString(int[] vect) {
		StringBuffer buf = new StringBuffer();
		for (int i=0; i<vect.length; ++i) {
			buf.append(String.format("%d", vect[i]));
			if (i != vect.length-1) {
				buf.append("\t");
			}
		}
		return buf.toString();
	}
	
	public static <A> String toString(A[] vect) {
		StringBuffer buf = new StringBuffer();
		for (int i=0; i<vect.length; ++i) {
			buf.append(vect[i].toString());
			if (i != vect.length-1) {
				buf.append("\t");
			}
		}
		return buf.toString();
	}
	
	public static int argmax(double[] vect) {
		int bestIndex = 0;
		double bestValue = vect[0];
		for (int i=0; i<vect.length; ++i) {
			double val = vect[i];
			if (val > bestValue) {
				bestValue = val;
				bestIndex = i;
			}
		}
		return bestIndex;
	}
	
	public static int argmin(double[] vect) {
		int bestIndex = 0;
		double bestValue = vect[0];
		for (int i=0; i<vect.length; ++i) {
			double val = vect[i];
			if (val < bestValue) {
				bestValue = val;
				bestIndex = i;
			}
		}
		return bestIndex;
	}
	
	public static int argmax(float[] vect) {
		int bestIndex = 0;
		float bestValue = vect[0];
		for (int i=0; i<vect.length; ++i) {
			float val = vect[i];
			if (val > bestValue) {
				bestValue = val;
				bestIndex = i;
			}
		}
		return bestIndex;
	}
	
	public static int argmin(float[] vect) {
		int bestIndex = 0;
		float bestValue = vect[0];
		for (int i=0; i<vect.length; ++i) {
			float val = vect[i];
			if (val < bestValue) {
				bestValue = val;
				bestIndex = i;
			}
		}
		return bestIndex;
	}
	
	public static int argmax(int[] vect) {
		int bestIndex = 0;
		int bestValue = vect[0];
		for (int i=0; i<vect.length; ++i) {
			int val = vect[i];
			if (val > bestValue) {
				bestValue = val;
				bestIndex = i;
			}
		}
		return bestIndex;
	}
	
	public static int argmin(int[] vect) {
		int bestIndex = 0;
		int bestValue = vect[0];
		for (int i=0; i<vect.length; ++i) {
			int val = vect[i];
			if (val < bestValue) {
				bestValue = val;
				bestIndex = i;
			}
		}
		return bestIndex;
	}
	
	public static double sum(double[] vect) {
		double sum = 0.0;
		for (double x : vect) {
			sum += x;
		}
		return sum;
	}
	
	public static double max(double[] vect) {
		double max = Double.NEGATIVE_INFINITY;
		for (double x : vect) {
			if (x > max) {
				max = x;
			}
		}
		return max;
	}
	
	public static double min(double[] vect) {
		double min = Double.POSITIVE_INFINITY;
		for (double x : vect) {
			if (x < min) {
				min = x;
			}
		}
		return min;
	}
	
	public static double maxabs(double[] vect) {
		double max = Double.NEGATIVE_INFINITY;
		for (double x : vect) {
			if (Math.abs(x) > max) {
				max = Math.abs(x);
			}
		}
		return max;
	}
	
	public static double minabs(double[] vect) {
		double min = Double.POSITIVE_INFINITY;
		for (double x : vect) {
			if (Math.abs(x) < min) {
				min = Math.abs(x);
			}
		}
		return min;
	}
	
	public static double[] exp(double[] vect1) {
		double[] vect2 = new double[vect1.length];
		for (int i=0; i<vect1.length; ++i) {
			vect2[i] = Math.exp(vect1[i]);
		}
		return vect2;
	}
	
	public static void expi(double[] vect) {
		for (int i=0; i<vect.length; ++i) {
			vect[i] = Math.exp(vect[i]);
		}
	}
	
	public static float[] exp(float[] vect1) {
		float[] vect2 = new float[vect1.length];
		for (int i=0; i<vect1.length; ++i) {
			vect2[i] = (float) Math.exp(vect1[i]);
		}
		return vect2;
	}
	
	public static void expi(float[] vect) {
		for (int i=0; i<vect.length; ++i) {
			vect[i] = (float) Math.exp(vect[i]);
		}
	}
	
	public static double[] log(double[] vect1) {
		double[] vect2 = new double[vect1.length];
		for (int i=0; i<vect1.length; ++i) {
			vect2[i] = Math.log(vect1[i]);
		}
		return vect2;
	}
	
	public static void logi(double[] vect) {
		for (int i=0; i<vect.length; ++i) {
			vect[i] = Math.log(vect[i]);
		}
	}
	
	public static double[] add(double[] vect1, double x) {
		double[] vect2 = new double[vect1.length];
		for (int i=0; i<vect1.length; ++i) {
			vect2[i] = vect1[i] + x;
		}
		return vect2;
	}
	
	public static void addi(double[] vect, double x) {
		for (int i=0; i<vect.length; ++i) {
			vect[i] += x;
		}
	}
	
	public static double[] scale(double[] vect1, double x) {
		double[] vect2 = new double[vect1.length];
		for (int i=0; i<vect1.length; ++i) {
			vect2[i] = vect1[i] * x;
		}
		return vect2;
	}
	
	public static void scalei(double[] vect, double x) {
		for (int i=0; i<vect.length; ++i) {
			vect[i] *= x;
		}
	}
	
	public static double[] comb(double[] vect1, double x1, double[] vect2, double x2) {
		double[] vect3 = new double[vect1.length];
		for (int i=0; i<vect1.length; ++i) {
			vect3[i] = x1 * vect1[i] + x2 * vect2[i];
		}
		return vect3;
	}
	
	public static double[] pointwiseMult(double[] vect1, double[] vect2) {
		double[] vect3 = new double[vect1.length];
		for (int i=0; i<vect1.length; ++i) {
			vect3[i] = vect1[i] * vect2[i];
		}
		return vect3;
	}
	
	public static float[] pointwiseMult(float[] vect1, float[] vect2) {
		float[] vect3 = new float[vect1.length];
		for (int i=0; i<vect1.length; ++i) {
			vect3[i] = vect1[i] * vect2[i];
		}
		return vect3;
	}
	
	public static double innerProd(double[] vect1, double[] vect2) {
		double result = 0.0;
		for (int i=0; i<vect1.length; ++i) {
			result +=  vect1[i] * vect2[i];
		}
		return result;
	}
	
	public static float innerProd(float[] vect1, float[] vect2) {
		float result = 0.0f;
		for (int i=0; i<vect1.length; ++i) {
			result +=  vect1[i] * vect2[i];
		}
		return result;
	}
	
	public static void combi(double[] vect1, double x1, double[] vect2, double x2) {
		for (int i=0; i<vect1.length; ++i) {
			vect1[i] = x1 * vect1[i] + x2 * vect2[i];
		}
	}
	
	public static double[] normalize(double[] vect1) {
		double[] vect2 = new double[vect1.length];
		double norm = sum(vect1);
		if (norm == 0.0) {
			addi(vect2, 1.0 / vect1.length);
		} else {
			for (int i=0; i<vect1.length; ++i) {
				vect2[i] = vect1[i] / norm;
			}
		}
		return vect2;
	}
	
	public static void normalizei(double[] vect) {
		double norm = sum(vect);
		if (norm == 0.0) {
			addi(vect, 1.0 / vect.length);
		} else {
			for (int i=0; i<vect.length; ++i) {
				vect[i] /= norm;
			}
		}
	}
	
	// matrix
	
	public static double[][] toDouble(float[][] mat1) {
		double[][] mat2 = new double[mat1.length][];
		for (int i=0; i<mat1.length; ++i) {
			mat2[i] = new double[mat1[i].length];
			for (int j=0; j<mat1[i].length; ++j) {
				mat2[i][j] = (double) mat1[i][j];
			}
		}
		return mat2;
	}
	
	public static double[][] toDoubleArrays(List<List<Double>> lists) {
		double[][] mat = new double[lists.size()][];
		for (int i=0; i<lists.size(); ++i) {
			mat[i] = toDoubleArray(lists.get(i));
		}
		return mat;
	}
	
	public static int[][] toIntArrays(List<List<Integer>> lists) {
		int[][] mat = new int[lists.size()][];
		for (int i=0; i<lists.size(); ++i) {
			mat[i] = toIntArray(lists.get(i));
		}
		return mat;
	}
	
	public static double[][] copy(double[][] mat) {
		double[][] copy = new double[mat.length][];
		for (int i=0; i<copy.length; ++i) {
			copy[i] = a.copy(mat[i]);
		}
		return copy;
	}
	
	public static int[][] copy(int[][] mat) {
		int[][] copy = new int[mat.length][];
		for (int i=0; i<copy.length; ++i) {
			copy[i] = a.copy(mat[i]);
		}
		return copy;
	}
	
	public static float[][] copy(float[][] mat) {
		float[][] copy = new float[mat.length][];
		for (int i=0; i<copy.length; ++i) {
			copy[i] = a.copy(mat[i]);
		}
		return copy;
	}
	
	public static void copyi(double[][] mat, double[][] copy) {
		for (int i=0; i<copy.length; ++i) {
			copy[i] = a.copy(mat[i]);
		}
	}
	
	public static void copyi(int[][] mat, int[][] copy) {
		for (int i=0; i<copy.length; ++i) {
			copy[i] = a.copy(mat[i]);
		}
	}
	
	public static void copyi(float[][] mat, float[][] copy) {
		for (int i=0; i<copy.length; ++i) {
			copy[i] = a.copy(mat[i]);
		}
	}
	
	public static List<List<Double>> toLists(double[][] mat) {
		List<List<Double>> lists = new ArrayList<List<Double>>();
		for (double[] vect : mat) {
			lists.add(toList(vect));
		}
		return lists;
	}
	
	public static List<List<Integer>> toLists(int[][] mat) {
		List<List<Integer>> lists = new ArrayList<List<Integer>>();
		for (int[] vect : mat) {
			lists.add(toList(vect));
		}
		return lists;
	}
	
	public static String toString(double[][] mat) {
		StringBuffer buf = new StringBuffer();
		for (int i=0; i<mat.length; ++i) {
			buf.append(toString(mat[i])+"\n");
		}
		return buf.toString();
	}
	
	public static String toString(int[][] mat) {
		StringBuffer buf = new StringBuffer();
		for (int i=0; i<mat.length; ++i) {
			buf.append(toString(mat[i])+"\n");
		}
		return buf.toString();
	}
	
	public static <A> String toString(A[][] mat) {
		StringBuffer buf = new StringBuffer();
		for (int i=0; i<mat.length; ++i) {
			buf.append(toString(mat[i])+"\n");
		}
		return buf.toString();
	}
	
	public static double[] sum(double[][] mat) {
		double[] sum = new double[mat.length];
		for (int i=0; i<mat.length; ++i) {
			sum[i] = sum(mat[i]);
		}
		return sum;
	}
	
	public static double[] max(double[][] mat) {
		double[] max = new double[mat.length];
		for (int i=0; i<mat.length; ++i) {
			max[i] = max(mat[i]);
		}
		return max;
	}
	
	public static double[] min(double[][] mat) {
		double[] min = new double[mat.length];
		for (int i=0; i<mat.length; ++i) {
			min[i] = min(mat[i]);
		}
		return min;
	}
	
	public static double[] maxabs(double[][] mat) {
		double[] maxabs = new double[mat.length];
		for (int i=0; i<mat.length; ++i) {
			maxabs[i] = maxabs(mat[i]);
		}
		return maxabs;
	}
	
	public static double[] minabs(double[][] mat) {
		double[] minabs = new double[mat.length];
		for (int i=0; i<mat.length; ++i) {
			minabs[i] = minabs(mat[i]);
		}
		return minabs;
	}
	
	public static double[][] log(double[][] mat1) {
		double[][] mat2 = new double[mat1.length][];
		for (int i=0; i<mat1.length; ++i) {
			mat2[i] = log(mat1[i]);
		}
		return mat2;
	}
	
	public static void logi(double[][] mat) {
		for (double[] vect : mat) {
			logi(vect);
		}
	}
	
	public static double[][] add(double[][] mat1, double x) {
		double[][] mat2 = new double[mat1.length][];
		for (int i=0; i<mat1.length; ++i) {
			mat2[i] = add(mat1[i], x);
		}
		return mat2;
	}
	
	public static void addi(double[][] mat, double x) {
		for (double[] vect : mat) {
			addi(vect, x);
		}
	}
	
	public static double[][] scale(double[][] mat1, double x) {
		double[][] mat2 = new double[mat1.length][];
		for (int i=0; i<mat1.length; ++i) {
			mat2[i] = scale(mat1[i], x);
		}
		return mat2;
	}
	
	public static void scalei(double[][] mat, double x) {
		for (double[] vect : mat) {
			scalei(vect, x);
		}
	}
	
	public static double[][] comb(double[][] mat1, double x1, double[][] mat2, double x2) {
		double[][] mat3 = new double[mat1.length][];
		for (int i=0; i<mat1.length; ++i) {
			mat3[i] = comb(mat1[i], x1, mat2[i], x2);
		}
		return mat3;
	}
	
	public static void combi(double[][] mat1, double x1, double[][] mat2, double x2) {
		for (int i=0; i<mat1.length; ++i) {
			combi(mat1[i], x1, mat2[i], x2);
		}
	}
	
	public static double[][] normalizecol(double[][] mat1) {
		double[][] mat2 = new double[mat1.length][];
		for (int i=0; i<mat1.length; ++i) {
			mat2[i] = normalize(mat1[i]);
		}
		return mat2;
	}

	public static void normalizecoli(double[][] mat) {
		for (double[] vect : mat) {
			normalizei(vect);
		}
	}
	
	// tensor
	
	public static double[][][] toDouble(float[][][] tens1) {
		double[][][] tens2 = new double[tens1.length][][];
		for (int i=0; i<tens1.length; ++i) {
			tens2[i] = new double[tens1[i].length][];
			for (int j=0; j<tens1[i].length; ++j) {
				tens2[i][j] = new double[tens1[i][j].length];
				for (int k=0; k<tens1[i][j].length; ++k) {
					tens2[i][j][k] = (double) tens1[i][j][k];
				}
			}
		}
		return tens2;
	}
	
	public static double[][][] toDoubleArrayss(List<List<List<Double>>> listss) {
		double[][][] tens = new double[listss.size()][][];
		for (int i=0; i<listss.size(); ++i) {
			tens[i] = toDoubleArrays(listss.get(i));
		}
		return tens;
	}
	
	public static int[][][] toIntArrayss(List<List<List<Integer>>> listss) {
		int[][][] tens = new int[listss.size()][][];
		for (int i=0; i<listss.size(); ++i) {
			tens[i] = toIntArrays(listss.get(i));
		}
		return tens;
	}
	
	public static List<List<List<Double>>> toListss(double[][][] tens) {
		List<List<List<Double>>> listss = new ArrayList<List<List<Double>>>();
		for (double[][] mat : tens) {
			listss.add(toLists(mat));
		}
		return listss;
	}
	
	public static List<List<List<Integer>>> toListss(int[][][] tens) {
		List<List<List<Integer>>> listss = new ArrayList<List<List<Integer>>>();
		for (int[][] mat : tens) {
			listss.add(toLists(mat));
		}
		return listss;
	}
	
	public static double[][][] copy(double[][][] tens) {
		double[][][] copy = new double[tens.length][][];
		for (int i=0; i<copy.length; ++i) {
			copy[i] = a.copy(tens[i]);
		}
		return copy;
	}
	
	public static int[][][] copy(int[][][] tens) {
		int[][][] copy = new int[tens.length][][];
		for (int i=0; i<copy.length; ++i) {
			copy[i] = a.copy(tens[i]);
		}
		return copy;
	}
	
	public static float[][][] copy(float[][][] tens) {
		float[][][] copy = new float[tens.length][][];
		for (int i=0; i<copy.length; ++i) {
			copy[i] = a.copy(tens[i]);
		}
		return copy;
	}
	
	public static void copy(double[][][] tens, double[][][] copy) {
		for (int i=0; i<copy.length; ++i) {
			copy[i] = a.copy(tens[i]);
		}
	}
	
	public static void copy(int[][][] tens, int[][][] copy) {
		for (int i=0; i<copy.length; ++i) {
			copy[i] = a.copy(tens[i]);
		}
	}
	
	public static void copy(float[][][] tens, float[][][] copy) {
		for (int i=0; i<copy.length; ++i) {
			copy[i] = a.copy(tens[i]);
		}
	}
	
	public static String toString(double[][][] tens) {
		StringBuffer buf = new StringBuffer();
		for (int i=0; i<tens.length; ++i) {
			buf.append(toString(tens[i])+"\n");
		}
		return buf.toString();
	}
	
	public static String toString(int[][][] tens) {
		StringBuffer buf = new StringBuffer();
		for (int i=0; i<tens.length; ++i) {
			buf.append(toString(tens[i])+"\n");
		}
		return buf.toString();
	}
	
	public static <A> String toString(A[][][] tens) {
		StringBuffer buf = new StringBuffer();
		for (int i=0; i<tens.length; ++i) {
			buf.append(toString(tens[i])+"\n");
		}
		return buf.toString();
	}
	
	public static double[][] sum(double[][][] tens) {
		double[][] sum = new double[tens.length][];
		for (int i=0; i<tens.length; ++i) {
			sum[i] = sum(tens[i]);
		}
		return sum;
	}
	
	public static double[][] max(double[][][] tens) {
		double[][] max = new double[tens.length][];
		for (int i=0; i<tens.length; ++i) {
			max[i] = max(tens[i]);
		}
		return max;
	}
	
	public static double[][] min(double[][][] tens) {
		double[][] min = new double[tens.length][];
		for (int i=0; i<tens.length; ++i) {
			min[i] = min(tens[i]);
		}
		return min;
	}
	
	public static double[][] maxabs(double[][][] tens) {
		double[][] maxabs = new double[tens.length][];
		for (int i=0; i<tens.length; ++i) {
			maxabs[i] = maxabs(tens[i]);
		}
		return maxabs;
	}
	
	public static double[][] minabs(double[][][] tens) {
		double[][] minabs = new double[tens.length][];
		for (int i=0; i<tens.length; ++i) {
			minabs[i] = minabs(tens[i]);
		}
		return minabs;
	}
	
	public static double[][][] log(double[][][] tens1) {
		double[][][] tens2 = new double[tens1.length][][];
		for (int i=0; i<tens1.length; ++i) {
			tens2[i] = log(tens1[i]);
		}
		return tens2;
	}
	
	public static void logi(double[][][] tens) {
		for (double[][] mat : tens) {
			logi(mat);
		}
	}
	
	public static double[][][] add(double[][][] tens1, double x) {
		double[][][] tens2 = new double[tens1.length][][];
		for (int i=0; i<tens1.length; ++i) {
			tens2[i] = add(tens1[i], x);
		}
		return tens2;
	}
	
	public static void addi(double[][][] tens, double x) {
		for (double[][] mat : tens) {
			addi(mat, x);
		}
	}
	
	public static double[][][] scale(double[][][] tens1, double x) {
		double[][][] tens2 = new double[tens1.length][][];
		for (int i=0; i<tens1.length; ++i) {
			tens2[i] = scale(tens1[i], x);
		}
		return tens2;
	}
	
	public static void scalei(double[][][] tens, double x) {
		for (double[][] mat : tens) {
			scalei(mat, x);
		}
	}
	
	public static double[][][] comb(double[][][] tens1, double x1, double[][][] tens2, double x2) {
		double[][][] tens3 = new double[tens1.length][][];
		for (int i=0; i<tens1.length; ++i) {
			tens3[i] = comb(tens1[i], x1, tens2[i], x2);
		}
		return tens3;
	}
	
	public static void combi(double[][][] tens1, double x1, double[][][] tens2, double x2) {
		for (int i=0; i<tens1.length; ++i) {
			combi(tens1[i], x1, tens2[i], x2);
		}
	}
	
	public static double[][][] normalizecol(double[][][] tens1) {
		double[][][] tens2 = new double[tens1.length][][];
		for (int i=0; i<tens1.length; ++i) {
			tens2[i] = normalizecol(tens1[i]);
		}
		return tens2;
	}

	public static void normalizecoli(double[][][] tens) {
		for (double[][] mat : tens) {
			normalizecoli(mat);
		}
	}
	
	
	// float
	
	// vector
	
	public static float[] toFloat(double[] vect1) {
		float[] vect2 = new float[vect1.length];
		for (int i=0; i<vect1.length; ++i) {
			vect2[i] = (float) vect1[i];
		}
		return vect2;
	}
	
	public static float[] toFloatArray(List<Float> list) {
		float[] vect = new float[list.size()];
		for (int i=0; i<list.size(); ++i) {
			vect[i] = list.get(i);
		}
		return vect;
	}

	public static List<Float> toList(float[] vect) {
		List<Float> list = new ArrayList<Float>();
		for (float x : vect) {
			list.add(x);
		}
		return list;
	}
	
	public static String toString(float[] vect) {
		StringBuffer buf = new StringBuffer();
		for (int i=0; i<vect.length; ++i) {
			buf.append(String.format("%f", vect[i]));
			if (i != vect.length-1) {
				buf.append("\t");
			}
		}
		return buf.toString();
	}
	
	public static float sum(float[] vect) {
		float sum = 0.0f;
		for (float x : vect) {
			sum += x;
		}
		return sum;
	}
	
	public static float max(float[] vect) {
		float max = Float.NEGATIVE_INFINITY;
		for (float x : vect) {
			if (x > max) {
				max = x;
			}
		}
		return max;
	}
	
	public static float min(float[] vect) {
		float min = Float.POSITIVE_INFINITY;
		for (float x : vect) {
			if (x < min) {
				min = x;
			}
		}
		return min;
	}
	
	public static float maxabs(float[] vect) {
		float max = Float.NEGATIVE_INFINITY;
		for (float x : vect) {
			if (Math.abs(x) > max) {
				max = Math.abs(x);
			}
		}
		return max;
	}
	
	public static float minabs(float[] vect) {
		float min = Float.POSITIVE_INFINITY;
		for (float x : vect) {
			if (Math.abs(x) < min) {
				min = Math.abs(x);
			}
		}
		return min;
	}
	
	public static float[] log(float[] vect1) {
		float[] vect2 = new float[vect1.length];
		for (int i=0; i<vect1.length; ++i) {
			vect2[i] = (float) Math.log(vect1[i]);
		}
		return vect2;
	}
	
	public static void logi(float[] vect) {
		for (int i=0; i<vect.length; ++i) {
			vect[i] = (float) Math.log(vect[i]);
		}
	}
	
	public static float[] add(float[] vect1, float x) {
		float[] vect2 = new float[vect1.length];
		for (int i=0; i<vect1.length; ++i) {
			vect2[i] = vect1[i] + x;
		}
		return vect2;
	}
	
	public static void addi(float[] vect, float x) {
		for (int i=0; i<vect.length; ++i) {
			vect[i] += x;
		}
	}
	
	public static float[] scale(float[] vect1, float x) {
		float[] vect2 = new float[vect1.length];
		for (int i=0; i<vect1.length; ++i) {
			vect2[i] = vect1[i] * x;
		}
		return vect2;
	}
	
	public static void scalei(float[] vect, float x) {
		for (int i=0; i<vect.length; ++i) {
			vect[i] *= x;
		}
	}
	
	public static float[] comb(float[] vect1, float x1, float[] vect2, float x2) {
		float[] vect3 = new float[vect1.length];
		for (int i=0; i<vect1.length; ++i) {
			vect3[i] = x1 * vect1[i] + x2 * vect2[i];
		}
		return vect3;
	}
	
	public static void combi(float[] vect1, float x1, float[] vect2, float x2) {
		for (int i=0; i<vect1.length; ++i) {
			vect1[i] = x1 * vect1[i] + x2 * vect2[i];
		}
	}
	
	public static float[] normalize(float[] vect1) {
		float[] vect2 = new float[vect1.length];
		float norm = sum(vect1);
		if (norm == 0.0f) {
			addi(vect2, 1.0f / vect1.length);
		} else {
			for (int i=0; i<vect1.length; ++i) {
				vect2[i] = vect1[i] / norm;
			}
		}
		return vect2;
	}
	
	public static void normalizei(float[] vect) {
		float norm = sum(vect);
		if (norm == 0.0f) {
			addi(vect, 1.0f / vect.length);
		} else {
			for (int i=0; i<vect.length; ++i) {
				vect[i] /= norm;
			}
		}
	}
	
	// matrix
	
	public static float[][] toFloat(double[][] mat1) {
		float[][] mat2 = new float[mat1.length][];
		for (int i=0; i<mat1.length; ++i) {
			mat2[i] = new float[mat1[i].length];
			for (int j=0; j<mat1[i].length; ++j) {
				mat2[i][j] = (float) mat1[i][j];
			}
		}
		return mat2;
	}
	
	public static float[][] toFloatArrays(List<List<Float>> lists) {
		float[][] mat = new float[lists.size()][];
		for (int i=0; i<lists.size(); ++i) {
			mat[i] = toFloatArray(lists.get(i));
		}
		return mat;
	}
	
	public static List<List<Float>> toLists(float[][] mat) {
		List<List<Float>> lists = new ArrayList<List<Float>>();
		for (float[] vect : mat) {
			lists.add(toList(vect));
		}
		return lists;
	}
	
	public static String toString(float[][] mat) {
		StringBuffer buf = new StringBuffer();
		for (int i=0; i<mat.length; ++i) {
			buf.append(toString(mat[i])+"\n");
		}
		return buf.toString();
	}
	
	public static float[] sum(float[][] mat) {
		float[] sum = new float[mat.length];
		for (int i=0; i<mat.length; ++i) {
			sum[i] = sum(mat[i]);
		}
		return sum;
	}
	
	public static float[] max(float[][] mat) {
		float[] max = new float[mat.length];
		for (int i=0; i<mat.length; ++i) {
			max[i] = max(mat[i]);
		}
		return max;
	}
	
	public static float[] min(float[][] mat) {
		float[] min = new float[mat.length];
		for (int i=0; i<mat.length; ++i) {
			min[i] = min(mat[i]);
		}
		return min;
	}
	
	public static float[] maxabs(float[][] mat) {
		float[] maxabs = new float[mat.length];
		for (int i=0; i<mat.length; ++i) {
			maxabs[i] = maxabs(mat[i]);
		}
		return maxabs;
	}
	
	public static float[] minabs(float[][] mat) {
		float[] minabs = new float[mat.length];
		for (int i=0; i<mat.length; ++i) {
			minabs[i] = minabs(mat[i]);
		}
		return minabs;
	}
	
	public static float[][] log(float[][] mat1) {
		float[][] mat2 = new float[mat1.length][];
		for (int i=0; i<mat1.length; ++i) {
			mat2[i] = log(mat1[i]);
		}
		return mat2;
	}
	
	public static void logi(float[][] mat) {
		for (float[] vect : mat) {
			logi(vect);
		}
	}
	
	public static float[][] add(float[][] mat1, float x) {
		float[][] mat2 = new float[mat1.length][];
		for (int i=0; i<mat1.length; ++i) {
			mat2[i] = add(mat1[i], x);
		}
		return mat2;
	}
	
	public static void addi(float[][] mat, float x) {
		for (float[] vect : mat) {
			addi(vect, x);
		}
	}
	
	public static float[][] scale(float[][] mat1, float x) {
		float[][] mat2 = new float[mat1.length][];
		for (int i=0; i<mat1.length; ++i) {
			mat2[i] = scale(mat1[i], x);
		}
		return mat2;
	}
	
	public static void scalei(float[][] mat, float x) {
		for (float[] vect : mat) {
			scalei(vect, x);
		}
	}
	
	public static float[][] comb(float[][] mat1, float x1, float[][] mat2, float x2) {
		float[][] mat3 = new float[mat1.length][];
		for (int i=0; i<mat1.length; ++i) {
			mat3[i] = comb(mat1[i], x1, mat2[i], x2);
		}
		return mat3;
	}
	
	public static void combi(float[][] mat1, float x1, float[][] mat2, float x2) {
		for (int i=0; i<mat1.length; ++i) {
			combi(mat1[i], x1, mat2[i], x2);
		}
	}
	
	public static float[][] normalizecol(float[][] mat1) {
		float[][] mat2 = new float[mat1.length][];
		for (int i=0; i<mat1.length; ++i) {
			mat2[i] = normalize(mat1[i]);
		}
		return mat2;
	}

	public static void normalizecoli(float[][] mat) {
		for (float[] vect : mat) {
			normalizei(vect);
		}
	}
	
	// tensor
	
	public static float[][][] toFloat(double[][][] tens1) {
		float[][][] tens2 = new float[tens1.length][][];
		for (int i=0; i<tens1.length; ++i) {
			tens2[i] = new float[tens1[i].length][];
			for (int j=0; j<tens1[i].length; ++j) {
				tens2[i][j] = new float[tens1[i][j].length];
				for (int k=0; k<tens1[i][j].length; ++k) {
					tens2[i][j][k] = (float) tens1[i][j][k];
				}
			}
		}
		return tens2;
	}
	
	public static float[][][] toFloatArrayss(List<List<List<Float>>> listss) {
		float[][][] tens = new float[listss.size()][][];
		for (int i=0; i<listss.size(); ++i) {
			tens[i] = toFloatArrays(listss.get(i));
		}
		return tens;
	}
	
	public static List<List<List<Float>>> toListss(float[][][] tens) {
		List<List<List<Float>>> listss = new ArrayList<List<List<Float>>>();
		for (float[][] mat : tens) {
			listss.add(toLists(mat));
		}
		return listss;
	}
	
	public static String toString(float[][][] tens) {
		StringBuffer buf = new StringBuffer();
		for (int i=0; i<tens.length; ++i) {
			buf.append(toString(tens[i])+"\n");
		}
		return buf.toString();
	}
	
	public static float[][] sum(float[][][] tens) {
		float[][] sum = new float[tens.length][];
		for (int i=0; i<tens.length; ++i) {
			sum[i] = sum(tens[i]);
		}
		return sum;
	}
	
	public static float[][] max(float[][][] tens) {
		float[][] max = new float[tens.length][];
		for (int i=0; i<tens.length; ++i) {
			max[i] = max(tens[i]);
		}
		return max;
	}
	
	public static float[][] min(float[][][] tens) {
		float[][] min = new float[tens.length][];
		for (int i=0; i<tens.length; ++i) {
			min[i] = min(tens[i]);
		}
		return min;
	}
	
	public static float[][] maxabs(float[][][] tens) {
		float[][] maxabs = new float[tens.length][];
		for (int i=0; i<tens.length; ++i) {
			maxabs[i] = maxabs(tens[i]);
		}
		return maxabs;
	}
	
	public static float[][] minabs(float[][][] tens) {
		float[][] minabs = new float[tens.length][];
		for (int i=0; i<tens.length; ++i) {
			minabs[i] = minabs(tens[i]);
		}
		return minabs;
	}
	
	public static float[][][] log(float[][][] tens1) {
		float[][][] tens2 = new float[tens1.length][][];
		for (int i=0; i<tens1.length; ++i) {
			tens2[i] = log(tens1[i]);
		}
		return tens2;
	}
	
	public static void logi(float[][][] tens) {
		for (float[][] mat : tens) {
			logi(mat);
		}
	}
	
	public static float[][][] add(float[][][] tens1, float x) {
		float[][][] tens2 = new float[tens1.length][][];
		for (int i=0; i<tens1.length; ++i) {
			tens2[i] = add(tens1[i], x);
		}
		return tens2;
	}
	
	public static void addi(float[][][] tens, float x) {
		for (float[][] mat : tens) {
			addi(mat, x);
		}
	}
	
	public static float[][][] scale(float[][][] tens1, float x) {
		float[][][] tens2 = new float[tens1.length][][];
		for (int i=0; i<tens1.length; ++i) {
			tens2[i] = scale(tens1[i], x);
		}
		return tens2;
	}
	
	public static void scalei(float[][][] tens, float x) {
		for (float[][] mat : tens) {
			scalei(mat, x);
		}
	}
	
	public static float[][][] comb(float[][][] tens1, float x1, float[][][] tens2, float x2) {
		float[][][] tens3 = new float[tens1.length][][];
		for (int i=0; i<tens1.length; ++i) {
			tens3[i] = comb(tens1[i], x1, tens2[i], x2);
		}
		return tens3;
	}
	
	public static void combi(float[][][] tens1, float x1, float[][][] tens2, float x2) {
		for (int i=0; i<tens1.length; ++i) {
			combi(tens1[i], x1, tens2[i], x2);
		}
	}
	
	public static float[][][] normalizecol(float[][][] tens1) {
		float[][][] tens2 = new float[tens1.length][][];
		for (int i=0; i<tens1.length; ++i) {
			tens2[i] = normalizecol(tens1[i]);
		}
		return tens2;
	}

	public static void normalizecoli(float[][][] tens) {
		for (float[][] mat : tens) {
			normalizecoli(mat);
		}
	}
	
}
