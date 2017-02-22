package util;

public interface Indexer<A> {
	
	public boolean locked();

	public void lock();

	public int size();

	public boolean contains(A object);

	public int getIndex(A object);

	public A getObject(int index);

	public void index(A[] vect);
	
	public void forgetIndexLookup();

}