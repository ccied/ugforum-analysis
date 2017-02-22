package math;

import java.util.Arrays;

import util.Pair;
import util.a;

public abstract class CachingDifferentiableFunction implements DifferentiableFunction {
	double[] lastX;
	double[] lastGradient;
	double lastValue;
	
	protected abstract Pair<Double, double[]> calculate(double[] x);
		
	private void ensureCache(double[] x) {
		if (!isCached(x)) {
			Pair<Double, double[]> result = calculate(x);			
			lastValue = result.getFirst();
			lastX = a.copy(x);
			lastGradient = a.copy(result.getSecond());			
		}
	}
	
	private boolean isCached(double[] x) {
		if (lastX == null) {
			return false;
		}
		return Arrays.equals(x, lastX);
	}
	
	public void clearCache()
	{
		lastX = null;
		lastGradient = null;
	}
	

	public double[] derivativeAt(double[] x) {
		ensureCache(x);
		return lastGradient;
	}
	
	public double valueAt(double[] x) {
		ensureCache(x);
		return lastValue;
	}
	
	public abstract int dimension() ;
}
