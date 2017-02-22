package util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class NaiveBayesClassifier implements Classifier {
	
	double smoothing;
	int maxLabel;
	int maxFeature;
	double[][][] logProbs;
	
	public NaiveBayesClassifier(double smoothing) {
		this.smoothing = smoothing;
	}

	public void train(List<Pair<CounterInterface<Integer>, Integer>> trainSet) {
		this.maxLabel = -1;
		this.maxFeature = -1;
		for (Pair<CounterInterface<Integer>, Integer> datum : trainSet) {
			int label = datum.getSecond();
			maxLabel = Math.max(label, maxLabel);
			for (Entry<Integer,Double> entry : datum.getFirst().entries()) {
				int feature = entry.getKey();
				maxFeature = Math.max(feature, maxFeature);
			}
		}
		
		this.logProbs = new double[maxLabel+1][maxFeature+1][2];
		
		for (Pair<CounterInterface<Integer>, Integer> datum : trainSet) {
			int label = datum.getSecond();
			for (int f=0; f<=maxFeature; ++f) logProbs[label][f][0] += 1.0;
			for (Entry<Integer,Double> entry : datum.getFirst().entries()) {
				int f = entry.getKey();
				if (entry.getValue() > 0) {
					logProbs[label][f][0] -= 1.0;
					logProbs[label][f][1] += 1.0;
				}
			}
		}
	
		a.addi(this.logProbs, smoothing);
		a.normalizecoli(this.logProbs);
		a.logi(this.logProbs);
	}

	public Map<Integer,CounterInterface<Integer>> getWeights() {
		Map<Integer,CounterInterface<Integer>> weights = new HashMap<Integer,CounterInterface<Integer>>();
		
		for (int label=0; label<=maxLabel; ++label) {
			CounterInterface<Integer> labelWeights = new Counter<Integer>();
			for (int f=0; f<=maxFeature; ++f) {
				labelWeights.setCount(f, logProbs[label][f][0] - logProbs[label][f][1]);
			}
			weights.put(label, labelWeights);
		}
		
		return weights;
	}

	public Integer predict(CounterInterface<Integer> testInstance) {
		double bestScore = Double.NEGATIVE_INFINITY;
		int bestLabel = -1;
		for (int label=0; label<=maxLabel; ++label) {
			double score = 0.0;
			for (int f=0; f<=maxFeature; ++f) score += logProbs[label][f][0];
			for (Entry<Integer,Double> entry : testInstance.entries()) {
				int f = entry.getKey();
				if (f <= maxFeature) {
					if (entry.getValue() > 0) {
						score -= logProbs[label][f][0];
						score += logProbs[label][f][1];
					}
				} else {
					score += Math.log(0.5);
				}
			}
			if (score > bestScore) {
				bestLabel = label;
				bestScore = score;
			}
		}
		return bestLabel;
	}

}
