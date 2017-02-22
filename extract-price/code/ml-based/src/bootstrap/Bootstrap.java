package bootstrap;

import java.util.Random;

import tuple.Pair;
import arrays.a;
import fileio.f;

public class Bootstrap {

	public static void main(String[] args) {
		
		double[] vals1 = f.readDoubleVector(args[0]);
		double[] vals2 = f.readDoubleVector(args[1]);
		
		int samplesPerGroup = Integer.parseInt(args[2]);
		int bootstrapSize = Integer.parseInt(args[3]);
		
		System.out.println(args[0] + " mean:");
		System.out.println(a.sum(vals1) / vals1.length);

		System.out.println(args[1] + " mean:");
		System.out.println(a.sum(vals2) / vals2.length);
		
		double delta = getDelta(vals1, vals2);

		assert vals1.length == vals2.length;
		assert vals1.length % samplesPerGroup == 0;
		assert delta >= 0;
		
		Random rand = new Random(0);
		
		double s = 0;
		for (int i=0; i<bootstrapSize; ++i) {
			Pair<int[],int[]> alignment = getBootstrapAlignment(rand, vals1.length, samplesPerGroup);
			double bdelta = getDelta(getValuesFromAlignment(vals1, alignment.getFirst()), getValuesFromAlignment(vals2, alignment.getSecond()));
			if (bdelta > 2*delta) s++;
		}
		
		System.out.println("p-value: " + s / bootstrapSize);
	}
	
	private static Pair<int[],int[]> getBootstrapAlignment(Random rand, int numSamples, int samplesPerGroup) {
		int numGroups = numSamples / samplesPerGroup;
		int[] a1 = new int[numSamples];
		int[] a2 = new int[numSamples];
		for (int g=0; g<numGroups; ++g) {
			int bg = rand.nextInt(numGroups);
			for (int s=0; s<samplesPerGroup; ++s) {
				int bs1 = rand.nextInt(samplesPerGroup);
				int bs2 = rand.nextInt(samplesPerGroup);
				a1[g*samplesPerGroup + s] = bg*samplesPerGroup + bs1;
				a2[g*samplesPerGroup + s] = bg*samplesPerGroup + bs2;
			}
		}
		return Pair.makePair(a1, a2);
	}
	
	private static double[] getValuesFromAlignment(double[] vals, int[] alignment) {
		assert vals.length == alignment.length;
		double[] result = new double[vals.length];
		for (int i=0; i<vals.length; ++i) {
			result[i] = vals[alignment[i]];
		}
		return result;
	}
	
	private static double getDelta(double[] vals1, double[] vals2) {
		return (a.sum(vals1) - a.sum(vals2)) / vals1.length;
	}
}
