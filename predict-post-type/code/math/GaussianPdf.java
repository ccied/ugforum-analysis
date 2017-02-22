package math;


public class GaussianPdf {

  public static double gaussianProb(double distSqr, double stdDev) {
    return Math.exp(logGaussianProb(distSqr, stdDev));
  }

  public static double logGaussianProb(double distSqr, double stdDev) {
    return Math.log(1.0 / (stdDev * Math.sqrt(2.0 * Math.PI))) + -(1.0/(2.0*stdDev*stdDev) * distSqr);
  }
}
