package math;

import util.a;

public class EmpiricalGradientTester {
	static final double EPS = 1e-6;
	static final double REL_EPS = 1e-3;
	static final double DEL_INITIAL = 1e-5;
	static final double DEL_MIN = 1e-10;

	public static void test(DifferentiableFunction func, double[] x) {
		double[] nextX = a.copy(x);
		double baseVal = func.valueAt(x);
		double[] grad = func.derivativeAt(x);
		for (int i=0; i<x.length; ++i) {
			double delta = DEL_INITIAL;
			boolean ok = false;
			double empDeriv = 0.0;
			while (delta > DEL_MIN && !ok) {
				nextX[i] += delta;
				double nextVal = func.valueAt(nextX);
				empDeriv = (nextVal - baseVal) / delta;
				if (close(empDeriv, grad[i])) {
//					System.out.printf("Gradient ok for dim %d, delta %f, calculated %f, empirical: %f\n", i, delta, grad[i], empDeriv);
					ok = true;
				}
				nextX[i] -= delta;
				if (!ok) delta /= 1.1;
			}
			if (!ok) System.out.printf("Empirical gradient step-size underflow dim %d, delta %f, calculated %f, empirical: %f\n", i, delta, grad[i], empDeriv);
		}
	}

	public static boolean close(double x, double y) {
		if (Math.abs(x - y) < EPS) return true;
		double avgMag = (Math.abs(x) + Math.abs(y)) / 2.0;
		return Math.abs(x - y) / avgMag < REL_EPS;
	}
	
}
