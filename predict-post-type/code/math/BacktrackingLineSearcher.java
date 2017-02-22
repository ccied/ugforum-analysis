package math;

import util.a;

public class BacktrackingLineSearcher {
	private double EPS = 1e-10;
	public double stepSizeMultiplier = 0.5;
	public double sufficientDecreaseConstant = 1e-4;
	public double initialStepSize = 1.0;
	double stepSize;
	boolean stepSizeUnderflow = false;
	int maxIterations = Integer.MAX_VALUE;

	public double[] minimize(DifferentiableFunction function, double[] initial, double[] direction) {
		stepSizeUnderflow = false;
		stepSize = initialStepSize;
		double initialFunctionValue = function.valueAt(initial);
		final double[] derivative = function.derivativeAt(initial);
		double initialDirectionalDerivative = a.innerProd(derivative, direction);
		double derivMax = a.maxabs(derivative);
		double[] guess = null;
		double guessValue = 0.0;
		boolean sufficientDecreaseObtained = false;
		int iter = 0;
		while (!sufficientDecreaseObtained) {
			guess = a.comb(initial, 1.0, direction, stepSize);
			guessValue = function.valueAt(guess);
			double sufficientDecreaseValue = initialFunctionValue + sufficientDecreaseConstant * initialDirectionalDerivative * stepSize;
			sufficientDecreaseObtained = (guessValue <= sufficientDecreaseValue + EPS);
			if (!sufficientDecreaseObtained) {
				if (stepSize < EPS && stepSize * derivMax < EPS) {
					System.out.printf("BacktrackingSearcher.minimize: stepSize underflow: %.15f, %.15f, %.15f, %.15f, %.15f, %.15f\n", stepSize, initialDirectionalDerivative, derivMax, guessValue, sufficientDecreaseValue, initialFunctionValue);
					stepSizeUnderflow = true;
					return initial;
				}
				stepSize *= stepSizeMultiplier;
			}
			if (iter++ > maxIterations) return initial;
		}
		return guess;
	}

	public double getFinalStepSize() {
		return stepSize;
	}

	public static void main(String[] args) {
		DifferentiableFunction function = new DifferentiableFunction() {
			public int dimension() {
				return 1;
			}
			public double valueAt(double[] x) {
				return x[0] * (x[0] - 0.01);
			}
			public double[] derivativeAt(double[] x) {
				return new double[] { 2 * x[0] - 0.01 };
			}
		};
		BacktrackingLineSearcher lineSearcher = new BacktrackingLineSearcher();
		lineSearcher.minimize(function, new double[] { 0 }, new double[] { 1 });
	}

	public void setMaxIterations(int i) {
		this.maxIterations = i;
	}

	public void configureForIteration(int iteration) {

	}

	public boolean stepSizeUnderflowed() {
		return stepSizeUnderflow;
	}
}
