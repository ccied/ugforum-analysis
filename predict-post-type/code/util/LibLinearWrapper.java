package util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.bwaldvogel.liblinear.Feature;
import de.bwaldvogel.liblinear.FeatureNode;
import de.bwaldvogel.liblinear.Linear;
import de.bwaldvogel.liblinear.Model;
import de.bwaldvogel.liblinear.Parameter;
import de.bwaldvogel.liblinear.Problem;
import de.bwaldvogel.liblinear.SolverType;

public class LibLinearWrapper implements Classifier {
	
	SolverType solverType;
	double C;
	double eps;
	Model model;
	
	public LibLinearWrapper(SolverType solverType, double C, double eps) {
		this.solverType = solverType;
		this.C = C;
		this.eps = eps;
	}
	
	public void train(List<Pair<CounterInterface<Integer>,Integer>> trainSet) {
		Problem problem = new Problem();
		FeatureNode[][] x = new FeatureNode[trainSet.size()][];
		double[] y = new double[trainSet.size()];
		int maxFeature = 0;
		for (int i=0; i<x.length; ++i) {
			CounterInterface<Integer> features = trainSet.get(i).getFirst();
			for (Map.Entry<Integer, Double> feat : features.entries()) {
				maxFeature = Math.max(feat.getKey()+1, maxFeature);
			}
			x[i] = convertToFeatureNodes(features);
			y[i] = trainSet.get(i).getSecond();
		}
		
		problem.l = trainSet.size();
		problem.n = maxFeature;
		problem.x = x;
		problem.y = y;
		problem.bias = 0.0;
		
		Parameter parameter = new Parameter(solverType, C, eps);
		model = Linear.train(problem, parameter);
	}
	
	public Map<Integer,CounterInterface<Integer>> getWeights() {
		Map<Integer,CounterInterface<Integer>> weights = new HashMap<Integer,CounterInterface<Integer>>();
		int numLabels = model.getNrClass();
		double[] flatWeights = model.getFeatureWeights();
		if (numLabels > 2 || solverType == SolverType.MCSVM_CS) {
			for (int l : model.getLabels()) weights.put(l, new IntCounter());
			int i=0;
			int f=0;
			while (i < flatWeights.length) {
				for (int l : model.getLabels()) {
					if (flatWeights[i] != 0.0) weights.get(l).setCount(f, flatWeights[i]);
					i++;
				}
				f++;
			}
		} else {
			CounterInterface<Integer> labelWeights = new IntCounter();
			for (int f=0; f<flatWeights.length; ++f) {
				if (flatWeights[f] != 0.0) labelWeights.setCount(f, flatWeights[f]);
			}
			weights.put(model.getLabels()[0], labelWeights);
			weights.put(model.getLabels()[1], new IntCounter());
		}
		return weights;
	}
	
	public Integer predict(CounterInterface<Integer> toPredict) {
		return (int) Linear.predict(model, convertToFeatureNodes(toPredict));
	}
	
	private FeatureNode[] convertToFeatureNodes(CounterInterface<Integer> features) {
		FeatureNode[] x = new FeatureNode[features.size()];
		int j=0;
		for (Map.Entry<Integer, Double> feat : features.entries()) {
			x[j] = new FeatureNode(feat.getKey()+1, feat.getValue());
			j++;
		}
		Arrays.sort(x, new Comparator<FeatureNode>() {
			public int compare(FeatureNode o1, FeatureNode o2) {
				if (o1.index > o2.index) {
					return 1;
				} else if (o1.index < o2.index) {
					return -1;
				} else {
					return 0;
				}
			}
		});
		return x;
	}
	
	
	
	public static void main(String[] args) {
		System.out.println("TEST LIBLINEAR API:");
		
		Problem problem = new Problem();
		problem.l = 3; // number of training examples
		problem.n = 3; // number of features
		problem.x = new FeatureNode[][] {
				{new FeatureNode(1, 1.0)},
				{new FeatureNode(2, 1.0)},
				{new FeatureNode(3, 1.0)}}; // feature nodes
		problem.y = new double[] {0.0, 1.0, 1.0}; // target values
		problem.bias = 0.0;
		
		SolverType solver = SolverType.MCSVM_CS; // -s 0
		double C = 100.0;    // cost of constraints violation
		double eps = 0.001; // stopping criteria

		Parameter parameter = new Parameter(solver, C, eps);
		Model model = Linear.train(problem, parameter);
		
		System.out.println("nr class: " + model.getNrClass());
		System.out.println("nr feature: " + model.getNrFeature());
		System.out.println("nr weights: " + model.getFeatureWeights().length);
		
		Feature[] instance = { new FeatureNode(3, 1.5)};
		double prediction = Linear.predict(model, instance);
		System.out.println(prediction);
		System.out.println("feature weights: "+Arrays.toString(model.getFeatureWeights()));
		System.out.println("labels: "+Arrays.toString(model.getLabels()));
		
		System.out.println();
		System.out.println();
		System.out.println();
		System.out.println();
		System.out.println("TEST LIBLINEAR WRAPPER:");
		
		Classifier classifier = new LibLinearWrapper(SolverType.L1R_L2LOSS_SVC, 1.0, 1e-1);
		int[][] trainFeatIDs = new int[][] {{0}, {1}, {3}};
		double[][] trainFeatVals = new double[][] {{1.0}, {1.0}, {1.0}};
		int[] trainLabels = new int[] {0, 1, 3};
		
		List<Pair<CounterInterface<Integer>,Integer>> trainSet = new ArrayList<Pair<CounterInterface<Integer>,Integer>>();
		for (int i=0; i<trainFeatIDs.length; ++i) {
			int[] featIds = trainFeatIDs[i];
			double[] featVals = trainFeatVals[i];
			int label = trainLabels[i];
			CounterInterface<Integer> feats = new IntCounter();
			for (int j=0; j<featIds.length; ++j) {
				feats.setCount(featIds[j], featVals[j]);
			}
			trainSet.add(Pair.makePair(feats, label));
		}
		
		classifier.train(trainSet);
		
		Map<Integer,CounterInterface<Integer>> weights = classifier.getWeights();
		for (int l : weights.keySet()) {
			System.out.println("label: "+l);
			System.out.println(weights.get(l));
		}
	}

}
