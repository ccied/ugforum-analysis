package math;

import java.util.Arrays;
import java.util.LinkedList;

import util.a;

public class LBFGSMinimizer {
	
	private class MyLineSearcher extends BacktrackingLineSearcher {
		public void configureForIteration(int iteration) {
			if (iteration == 0)
				stepSizeMultiplier = initialStepSizeMultiplier;
			else
				stepSizeMultiplier = LBFGSMinimizer.this.stepSizeMultiplier;
			stepSize = 1.0;
		}
	}

	double EPS = 1e-10;
	int maxIterations = 20;
	int maxHistorySize = 5;
	LinkedList<double[]> inputDifferenceVectorList = new LinkedList<double[]>();
	LinkedList<double[]> derivativeDifferenceVectorList = new LinkedList<double[]>();
	int minIterations = -1;
	double initialStepSizeMultiplier = 0.01;
	double stepSizeMultiplier = 0.5;
	boolean convergedAndClearedHistories = false;
	int maxHistoryResets = Integer.MAX_VALUE;
	int numHistoryResets;
	boolean verbose = false;
	boolean finishOnFirstConverge = false;
	boolean checkEmpiricalGradient = false;
	boolean throwExceptionOnStepSizeUnderflow = false;
	
	public LBFGSMinimizer() {
	}

	public LBFGSMinimizer(int maxIterations) {
		this.maxIterations = maxIterations;
	}
	
	public double[] minimize(DifferentiableFunction function, double[] initial, double tolerance) {
		return minimize(function, initial, tolerance, false);
	}

	public double[] minimize(DifferentiableFunction function, double[] initial, double tolerance, boolean printProgress) {
		double[] guess = a.copy(initial);
		return minimize(function, guess, function.valueAt(guess), function.derivativeAt(guess), 0, tolerance, printProgress);
	}

	private double[] minimize(DifferentiableFunction function, double[] initial, double initialValue, double[] initialDerivative, int startIteration, double tolerance, boolean printProgress) {
		numHistoryResets = 0;
		BacktrackingLineSearcher lineSearcher = new MyLineSearcher();

		double[] guess = a.copy(initial);
		double value = initialValue;
		double[] derivative = initialDerivative;
		for (int iteration = startIteration; iteration < maxIterations; iteration++) {
			if (checkEmpiricalGradient) EmpiricalGradientTester.test(function, guess);
			assert derivative.length == function.dimension();
			double[] initialInverseHessianDiagonal = getInitialInverseHessianDiagonal(function);
			double[] direction = implicitMultiply(initialInverseHessianDiagonal, derivative);
			a.scalei(direction, -1.0);

			lineSearcher.configureForIteration(iteration);
			double[] nextGuess = lineSearcher.minimize(function, guess, direction);
			if (lineSearcher.stepSizeUnderflowed()) {

				// if step size underflow, clear histories and repeat this iteration
				if (clearHistories()) {
					--iteration;
					continue;
				} else {
					if (throwExceptionOnStepSizeUnderflow) {
						throw new StepSizeUnderflowException("Step size underflow", guess, derivative);
					} else
						break;
				}

			}
			double nextValue = function.valueAt(nextGuess);
			double[] nextDerivative = function.derivativeAt(nextGuess);

			if (printProgress) {
				printProgress(iteration, nextValue);
			}

			if (iteration >= minIterations && converged(value, nextValue, tolerance)) {
				if (!finishOnFirstConverge && !convergedAndClearedHistories) {
					clearHistories();
					convergedAndClearedHistories = true;
				} else {
					return nextGuess;
				}
			} else {
				convergedAndClearedHistories = false;
			}

			updateHistories(guess, nextGuess, derivative, nextDerivative);
			guess = nextGuess;
			value = nextValue;
			derivative = nextDerivative;
		}
		if (verbose) System.out.println("LBFGSMinimizer.minimize: Exceeded maxIterations without converging.");
		return guess;
	}

	public void setThrowExceptionOnStepSizeUnderflow(boolean exceptionOnStepSizeUnderflow) {
		this.throwExceptionOnStepSizeUnderflow = exceptionOnStepSizeUnderflow;
	}
	
	public void setCheckEmpiricalGradient(boolean checkEmpiricalGradient) {
		this.checkEmpiricalGradient = checkEmpiricalGradient;
	}

	public void setMinIterations(int minIterations) {
		this.minIterations = minIterations;
	}

	public void setFinishOnFirstConverge(boolean finishOnFirstConverge) {
		this.finishOnFirstConverge = finishOnFirstConverge;
	}

	public void setVerbose(boolean verbose) {
		this.verbose = verbose;
	}

	public boolean getVerbose() {
		return verbose;
	}

	public int getMaxHistoryResets() {
		return maxHistoryResets;
	}

	public void setMaxHistoryResets(int maxHistoryResets) {
		this.maxHistoryResets = maxHistoryResets;
	}

	public void setMaxIterations(int maxIterations) {
		this.maxIterations = maxIterations;
	}

	public void setInitialStepSizeMultiplier(double initialStepSizeMultiplier) {
		this.initialStepSizeMultiplier = initialStepSizeMultiplier;
	}

	public void setStepSizeMultiplier(double stepSizeMultiplier) {
		this.stepSizeMultiplier = stepSizeMultiplier;
	}

	public double[] getSearchDirection(int dimension, double[] derivative) {
		double[] initialInverseHessianDiagonal = getInitialInverseHessianDiagonal(dimension);
		double[] direction = implicitMultiply(initialInverseHessianDiagonal, derivative);
		return direction;
	}

	protected double[] getInitialInverseHessianDiagonal(int dimension) {
		double scale = 1.0;
		if (derivativeDifferenceVectorList.size() >= 1) {
			double[] lastDerivativeDifference = getLastDerivativeDifference();
			double[] lastInputDifference = getLastInputDifference();
			double num = a.innerProd(lastDerivativeDifference, lastInputDifference);
			double den = a.innerProd(lastDerivativeDifference, lastDerivativeDifference);
			scale = num / den;
		}
		double[] result = new double[dimension];
		Arrays.fill(result, scale);
		return result;
	}

	private void printProgress(int iteration, double nextValue) {
		System.out.println(String.format("[LBFGSMinimizer.minimize] Iteration %d ended with value %.6f", iteration, nextValue));
	}

	protected boolean converged(double value, double nextValue, double tolerance) {
		if (value == nextValue) return true;
		double valueChange = Math.abs(nextValue - value);
		double valueAverage = Math.abs(nextValue + value + EPS) / 2.0;
		if (valueChange / valueAverage < tolerance) return true;
		return false;
	}

	protected void updateHistories(double[] guess, double[] nextGuess, double[] derivative, double[] nextDerivative) {
		double[] guessChange = a.comb(nextGuess, 1.0, guess, -1.0);
		double[] derivativeChange = a.comb(nextDerivative, 1.0, derivative, -1.0);
		pushOntoList(guessChange, inputDifferenceVectorList);
		pushOntoList(derivativeChange, derivativeDifferenceVectorList);
	}

	private void pushOntoList(double[] vector, LinkedList<double[]> vectorList) {
		vectorList.addFirst(vector);
		if (vectorList.size() > maxHistorySize) vectorList.removeLast();
	}

	public boolean clearHistories() {
		if (numHistoryResets < maxHistoryResets) {
			if (verbose) System.out.println("LBFGS cleared history.");
			inputDifferenceVectorList.clear();
			derivativeDifferenceVectorList.clear();
			numHistoryResets++;
			return true;
		}
		return false;
	}

	private int historySize() {
		return inputDifferenceVectorList.size();
	}

	public void setMaxHistorySize(int maxHistorySize) {
		this.maxHistorySize = maxHistorySize;
	}

	private double[] getInputDifference(int num) {
		// 0 is previous, 1 is the one before that
		return inputDifferenceVectorList.get(num);
	}

	private double[] getDerivativeDifference(int num) {
		return derivativeDifferenceVectorList.get(num);
	}

	private double[] getLastDerivativeDifference() {
		return derivativeDifferenceVectorList.getFirst();
	}

	private double[] getLastInputDifference() {
		return inputDifferenceVectorList.getFirst();
	}

	private double[] implicitMultiply(double[] initialInverseHessianDiagonal, double[] derivative) {
		double[] rho = new double[historySize()];
		double[] alpha = new double[historySize()];
		double[] right = a.copy(derivative);
		// loop last backward
		for (int i = historySize() - 1; i >= 0; i--) {
			double[] inputDifference = getInputDifference(i);
			double[] derivativeDifference = getDerivativeDifference(i);
			rho[i] = a.innerProd(inputDifference, derivativeDifference);
			if (rho[i] == 0.0) throw new RuntimeException("LBFGSMinimizer.implicitMultiply: Curvature problem.");
			alpha[i] = a.innerProd(inputDifference, right) / rho[i];
			right = a.comb(right, 1.0, derivativeDifference, -1.0 * alpha[i]);
		}
		double[] left = a.pointwiseMult(initialInverseHessianDiagonal, right);
		for (int i = 0; i < historySize(); i++) {
			double[] inputDifference = getInputDifference(i);
			double[] derivativeDifference = getDerivativeDifference(i);
			double beta = a.innerProd(derivativeDifference, left) / rho[i];
			left = a.comb(left, 1.0, inputDifference, alpha[i] - beta);
		}
		return left;
	}

	private double[] getInitialInverseHessianDiagonal(DifferentiableFunction function) {
		double scale = 1.0;
		if (derivativeDifferenceVectorList.size() >= 1) {
			double[] lastDerivativeDifference = getLastDerivativeDifference();
			double[] lastInputDifference = getLastInputDifference();
			double num = a.innerProd(lastDerivativeDifference, lastInputDifference);
			double den = a.innerProd(lastDerivativeDifference, lastDerivativeDifference);
			scale = num / den;
		}
		double[] result = new double[function.dimension()];
		Arrays.fill(result, scale);
		return result;	
	}

}
