package util;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IntArrayIndexer implements Indexer<int[]>, Serializable {
	private static final long serialVersionUID = 1L;

	private boolean locked;
	private List<List<Integer>> objectByIndex;
	private Map<List<Integer>,Integer> indexByObject;

	public IntArrayIndexer() {
		locked = false;
		objectByIndex = new ArrayList<List<Integer>>();
		indexByObject = new HashMap<List<Integer>,Integer>();
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

	public boolean contains(int[] object) {
		return indexByObject.containsKey(a.toList(object));
	}

	public int getIndex(int[] object) {
		int index = index(objectByIndex, indexByObject, a.toList(object), locked);
		if (index == -1) {
			throw new RuntimeException(String.format("Indexer locked, and object not in indexer: %s", object.toString()));
		}
		return index;
	}

	public int[] getObject(int index) {
		if (index >= objectByIndex.size()) {
			throw new RuntimeException(String.format("Index not in indexer: %d", index));
		}
		return a.toIntArray(objectByIndex.get(index));
	}

	public void index(int[][] vect) {
		for (int[] x : vect) {
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
