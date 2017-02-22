package util;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class f {
	
	public static interface StringProcessor {
		public String process(String str);
	}
	
	public static class NullStringProcessor implements StringProcessor {
		public String process(String str) {
			return str;
		}
	}

	public static double[] readAndIndexStringToDoubleVector(String path, Indexer<String> indexer, double defaultValue) {
		return readAndIndexStringToDoubleVector(path, indexer, defaultValue, new NullStringProcessor(), Integer.MAX_VALUE);
	}
	
	public static double[] readAndIndexStringToDoubleVector(String path, Indexer<String> indexer, double defaultValue, StringProcessor stringProcessor, int maxLinesToRead) {
		List<Double> result = new ArrayList<Double>();
		for (int i=0; i<indexer.size(); ++i) {
			result.add(defaultValue);
		}
		try {
			BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(path), "UTF-8"));
			int numLines = 0;
			while (in.ready()) {
				if (numLines >= maxLinesToRead) {
					break;
				}
				String line = in.readLine();
				String[] split = line.trim().split("\\s+");
				String s = stringProcessor.process(split[0]);
				if (s == null) continue;
				double d = Double.parseDouble(split[1]);
				int i = indexer.getIndex(s);
				if (i == result.size()) {
					result.add(defaultValue);
				}
				result.set(i, d);
				numLines++;
			}
			in.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return a.toDoubleArray(result);
	}
	
	private static <A> void growMatrix(List<List<A>> mat, A defaultValue) {
		List<A> row = new ArrayList<A>();
		for (int j=0; j<mat.get(0).size(); ++j) {
			row.add(defaultValue);
		}
		mat.add(row);
		for (int i=0; i<mat.size(); ++i) {
			mat.get(i).add(defaultValue);
		}
	}
	
	public static double[] readDoubleVector(String path) {
		List<Double> result = new ArrayList<Double>();
		try {
			BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(path), "UTF-8"));
			while (in.ready()) {
				String line = in.readLine();
				String[] split = line.trim().split("\\s+");
				for (String s : split) {
					double d = Double.parseDouble(s);
					result.add(d);
				}
			}
			in.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return a.toDoubleArray(result);
	}

  public static List<String> readLinesHard(String path) {
    return readLinesHard(path, Integer.MAX_VALUE);
  }
	
	public static List<String> readLinesHard(String path, int numLines) {
	  List<String> lines = new ArrayList<String>();
	  try {
	    BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(path), "UTF-8"));
      while (in.ready()) {
        if (lines.size() >= numLines) {
          break;
        }
        lines.add(in.readLine());
      }
	  } catch (IOException e) {
	    throw new RuntimeException(e);
	  }
	  return lines;
	}
	
	public static double[][] readAndIndexStringToDoubleMatrix(String path, Indexer<String> indexer, double defaultValue) {
		return readAndIndexStringToDoubleMatrix(path, indexer, defaultValue, new NullStringProcessor(), Integer.MAX_VALUE);
	}

	public static double[][] readAndIndexStringToDoubleMatrix(String path, Indexer<String> indexer, double defaultValue, StringProcessor stringProcessor, int maxLinesToRead) {
		List<List<Double>> result = new ArrayList<List<Double>>();
		for (int i=0; i<indexer.size(); ++i) {
			result.add(new ArrayList<Double>());
			for (int j=0; j<indexer.size(); ++j) {
				result.get(i).add(defaultValue);
			}
		}
		try {
			BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(path), "UTF-8"));
			int numLines = 0;
			while (in.ready()) {
				String line = in.readLine();
				numLines++;
				String[] split = line.trim().split("\\s+");
				String s1 = stringProcessor.process(split[0]);
				if (s1 == null) continue;
				String s2 = stringProcessor.process(split[1]);
				if (s2 == null) continue;
				double d = Double.parseDouble(split[2]);
				int i1 = indexer.getIndex(s1);
				if (i1 == result.size()) {
					growMatrix(result, defaultValue);
				}
				int i2 = indexer.getIndex(s2);
				if (i2 == result.size()) {
					growMatrix(result, defaultValue);
				}
				result.get(i1).set(i2, d);
				if (numLines >= maxLinesToRead) {
					break;
				}
			}
			in.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return a.toDoubleArrays(result);
	}
	
	public static double[][] readDoubleMatrix(String path) {
		List<List<Double>> result = new ArrayList<List<Double>>();
		try {
			BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(path), "UTF-8"));
			while (in.ready()) {
				String line = in.readLine();
				String[] split = line.trim().split("\\s+");
				List<Double> row = new ArrayList<Double>();
				for (String s : split) {
					double d = Double.parseDouble(s);
					row.add(d);
				}
				result.add(row);
			}
			in.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return a.toDoubleArrays(result);
	}
	
	private static <A> void growTensor(List<List<List<A>>> tens, A defaultValue) {
		List<List<A>> mat = new ArrayList<List<A>>();
		for (int i=0; i<tens.get(0).size(); ++i) {
			mat.add(new ArrayList<A>());
			for (int j=0; j<tens.get(0).get(0).size(); ++j) {
				mat.get(i).add(defaultValue);
			}
		}
		tens.add(mat);
		for (int i=0; i<tens.size(); ++i) {
			growMatrix(tens.get(i), defaultValue);
		}
	}
	
	public static double[][][] readAndIndexStringToDoubleTensor(String path, Indexer<String> indexer, double defaultValue) {
		return readAndIndexStringToDoubleTensor(path, indexer, defaultValue, new NullStringProcessor(), Integer.MAX_VALUE);
	}
	
	public static double[][][] readAndIndexStringToDoubleTensor(String path, Indexer<String> indexer, double defaultValue, StringProcessor stringProcessor, int maxLinesToRead) {
		List<List<List<Double>>> result = new ArrayList<List<List<Double>>>();
		for (int i=0; i<indexer.size(); ++i) {
			result.add(new ArrayList<List<Double>>());
			for (int j=0; j<indexer.size(); ++j) {
				result.get(i).add(new ArrayList<Double>());
				for (int k=0; k<indexer.size(); ++k) { 
					
				}
			}
		}
		try {
			BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(path), "UTF-8"));
			int numLines = 0;
			while (in.ready()) {
				String line = in.readLine();
				numLines++;
				String[] split = line.trim().split("\\s+");
				String s1 = stringProcessor.process(split[0]);
				if (s1 == null) continue;
				String s2 = stringProcessor.process(split[1]);
				if (s2 == null) continue;
				String s3 = stringProcessor.process(split[2]);
				if (s3 == null) continue;
				double d = Double.parseDouble(split[3]);
				int i1 = indexer.getIndex(s1);
				if (i1 == result.size()) {
					growTensor(result, defaultValue);
				}
				int i2 = indexer.getIndex(s2);
				if (i2 == result.size()) {
					growTensor(result, defaultValue);
				}
				int i3 = indexer.getIndex(s2);
				if (i3 == result.size()) {
					growTensor(result, defaultValue);
				}
				result.get(i1).get(i2).set(i3, d);
				if (numLines >= maxLinesToRead) {
					break;
				}
			}
			in.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return a.toDoubleArrayss(result);
	}
	
	public static double[][][] readDoubleTensor(String path) {
		List<List<List<Double>>> result = new ArrayList<List<List<Double>>>();
		try {
			BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(path), "UTF-8"));
			List<List<Double>> currentMat = new ArrayList<List<Double>>();
			while (in.ready()) {
				String line = in.readLine();
				if (line.trim().equals("")) {
					result.add(currentMat);
					currentMat = new ArrayList<List<Double>>();
					continue;
				}
				String[] split = line.trim().split("\\s+");
				List<Double> row = new ArrayList<Double>();
				for (String s : split) {
					double d = Double.parseDouble(s);
					row.add(d);
				}
				currentMat.add(row);
			}
			in.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return a.toDoubleArrayss(result);
	}
	
	public static int[][] readAndIndexDocument(String path, Indexer<String> indexer) {
		return readAndIndexDocument(path, indexer, new NullStringProcessor(), Integer.MAX_VALUE);
	}
	
	public static int[][] readAndIndexDocument(String path, Indexer<String> indexer, int maxLinesToRead) {
		return readAndIndexDocument(path, indexer, new NullStringProcessor(), maxLinesToRead);
	}
	
	public static int[][] readAndIndexDocument(String path, Indexer<String> indexer, StringProcessor stringProcessor, int maxLinesToRead) {
		List<List<Integer>> result = new ArrayList<List<Integer>>();
		try {
			BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(path), "UTF-8"));
			int numLines=0;
			while (in.ready()) {
				if (numLines >= maxLinesToRead) {
					break;
				}
				String line = in.readLine();
				String[] split = line.trim().split("\\s+");
				List<Integer> row = new ArrayList<Integer>();
				for (String s : split) {
					s = stringProcessor.process(s);
					if (s == null) continue;
					int i = indexer.getIndex(s);
					row.add(i);
				}
				if (!row.isEmpty()) {
					result.add(row);
					numLines++;
				}
			}
			in.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return a.toIntArrays(result);
	}
  
  public static String[][] readDocumentByCharacter(String path) {
    return readDocumentByCharacter(path, Integer.MAX_VALUE);
  }
  
  public static String[][] readDocumentByCharacter(String path, int maxLinesToRead) {
    return readDocumentByCharacter(path, new NullStringProcessor(), maxLinesToRead);
  }
  
  public static String[][] readDocumentByCharacter(String path, StringProcessor stringProcessor, int maxLinesToRead) {
    List<List<String>> result = new ArrayList<List<String>>();
    try {
      BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(path), "UTF-8"));
      int numLines=0;
      while (in.ready()) {
        if (numLines >= maxLinesToRead) {
          break;
        }
        String line = in.readLine();
        List<String> row = new ArrayList<String>();
        for (int t=0; t<line.length(); ++t) {
          String c = line.substring(t,t+1);
          c = stringProcessor.process(c);
          if (c == null) continue;
          row.add(c);
        }
        if (!row.isEmpty()) {
          result.add(row);
          numLines++;
        }
      }
      in.close();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    String[][] arrs = new String[result.size()][];
    for (int i=0; i<arrs.length; ++i) {
      arrs[i] = result.get(i).toArray(new String[result.get(i).size()]);
    }
    return arrs;
  }
	
	public static int[][] readAndIndexDocumentByCharacter(String path, Indexer<String> indexer) {
		return readAndIndexDocumentByCharacter(path, indexer, new NullStringProcessor(), Integer.MAX_VALUE);
	}
	
	public static int[][] readAndIndexDocumentByCharacter(String path, Indexer<String> indexer, int maxLinesToRead) {
		return readAndIndexDocumentByCharacter(path, indexer, new NullStringProcessor(), maxLinesToRead);
	}
	
	public static int[][] readAndIndexDocumentByCharacter(String path, Indexer<String> indexer, StringProcessor stringProcessor, int maxLinesToRead) {
	  String[][] textUnindexed = readDocumentByCharacter(path, stringProcessor, maxLinesToRead);
	  return indexDocument(textUnindexed, indexer);
	}
	
	public static int[][] indexDocument(String[][] textUnindexed, Indexer<String> charIndexer) {
	  final int[][] text = new int[textUnindexed.length][];
    for (int i=0; i<textUnindexed.length; ++i) {
      text[i] = new int[textUnindexed[i].length];
      for (int j=0; j<textUnindexed[i].length; ++j) {
        int idx = -1;
        if (!charIndexer.locked() || charIndexer.contains(textUnindexed[i][j])) {
          idx = charIndexer.getIndex(textUnindexed[i][j]);
        }
        text[i][j] = idx;
      }
    }
    return text;
	}
	
	public static Set<String> getCharacterVocabulary(String[][] textUnindexed) {
	  Set<String> vocabulary = new HashSet<String>();
    for (int i=0; i<textUnindexed.length; ++i) {
      for (int j=0; j<textUnindexed[i].length; ++j) {
        vocabulary.add(textUnindexed[i][j]);
      }
    }
    return vocabulary;
	}
}
