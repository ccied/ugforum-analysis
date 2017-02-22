package util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HashMapIndexer<A> implements Indexer<A> {

	private boolean locked;
	private List<A> objectByIndex;
	private Map<A,Integer> indexByObject;

	public HashMapIndexer() {
		locked = false;
		objectByIndex = new ArrayList<A>();
		indexByObject = new HashMap<A,Integer>();
	}

	public void lock() {
		this.locked = true;
	}

	public boolean locked() {
		return locked;
	}
	
	public int size() {
		return objectByIndex.size();
	}

	public boolean contains(A object) {
		return indexByObject.containsKey(object);
	}

	public int getIndex(A object) {
		int index = index(objectByIndex, indexByObject, object, locked);
		if (index == -1) {
			throw new RuntimeException(String.format("Indexer locked, and object not in indexer: %s", object.toString()));
		}
		return index;
	}

	public A getObject(int index) {
		if (index >= objectByIndex.size()) {
			throw new RuntimeException(String.format("Index not in indexer: %d", index));
		}
		return objectByIndex.get(index);
	}

	public void index(A[] vect) {
		for (A x : vect) {
			getIndex(x);
		}
	}

	private static <A> int index(List<A> objectByIndex, Map<A,Integer> indexByObject, A object, boolean locked) {
		Integer index = indexByObject.get(object);
		if (index == null) {
			if (!locked) {
				index = objectByIndex.size();
				objectByIndex.add(object);
				indexByObject.put(object, index);
			} else {
				index = -1;
			}
		}
		return index;
	}

	public void forgetIndexLookup() {
		this.indexByObject = null;
	}

}
