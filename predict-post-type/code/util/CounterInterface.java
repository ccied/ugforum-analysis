package util;

import java.util.Map;

public interface CounterInterface<E> {

	public double dotProduct(CounterInterface<E> c);

	public Iterable<Map.Entry<E, Double>> entries();

	public double incrementCount(E key, double d);

	public <T extends E> void incrementAll(CounterInterface<T> c, double d);

	public <T extends E> void incrementAll(CounterInterface<T> newWeights);

	public void scale(double d);

	public double getCount(E k);
	
	public void setCount(E k, double d);
	
	public double totalCount();

	public int size();

	public Iterable<E> keySet();

}