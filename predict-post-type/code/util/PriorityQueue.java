package util;

import java.io.Serializable;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * A priority queue based on a binary heap. Note that this implementation does
 * not efficiently support containment, removal, or element promotion
 * (decreaseKey) -- these methods are therefore not yet implemented. It is a
 * maximum priority queue, so next() gives the highest-priority object.
 * 
 * @author Dan Klein
 */
public class PriorityQueue<E> implements Iterator<E>, Serializable, Cloneable, PriorityQueueInterface<E>
{
	private static final long serialVersionUID = 1L;

	int size;

	int capacity;

	E[] elements;

	double[] priorities;

	public static class MinPriorityQueue<E> implements PriorityQueueInterface<E>
	{
		private PriorityQueue<E> pq = new PriorityQueue<E>();

		public boolean hasNext() {
			return pq.hasNext();
		}

		public E next() {
			return pq.next();
		}

		public void remove() {
			pq.remove();
		}

		public E peek() {
			return pq.peek();
		}

		public double getPriority() {
			return -pq.getPriority();
		}

		public int size() {
			return pq.size();
		}

		public boolean isEmpty() {
			return pq.isEmpty();
		}

		public void put(E key, double priority) {
			pq.put(key, -priority);
		}

		public List<E> toSortedList() {
			return pq.toSortedList();
		}

	}

	protected void grow(int newCapacity) {
		//		E[] newElements = (E[]) new Object[newCapacity];
		//		double[] newPriorities = new double[newCapacity];
		//		if (size > 0) {
		//			System.arraycopy(elements, 0, newElements, 0, priorities.length);
		//			System.arraycopy(priorities, 0, newPriorities, 0, priorities.length);
		//		}
		elements = elements == null ? (E[]) new Object[newCapacity] : Arrays.copyOf(elements, newCapacity);
		priorities = priorities == null ? new double[newCapacity] : Arrays.copyOf(priorities, newCapacity);
		capacity = newCapacity;
	}

	private int parent(int loc) {
		return (loc - 1) / 2;
	}

	private int leftChild(int loc) {
		return 2 * loc + 1;
	}

	private int rightChild(int loc) {
		return 2 * loc + 2;
	}

	private void heapifyUp(int loc_) {
		if (loc_ == 0) return;
		int loc = loc_;
		int parent = parent(loc);
		while (loc != 0 && priorities[loc] > priorities[parent]) {
			swap(loc, parent);
			loc = parent;
			parent = parent(loc);
			//			heapifyUp(parent);
		}
	}

	private void heapifyDown(int loc_) {
		int loc = loc_;
		int max = loc;
		while (true) {
			int leftChild = leftChild(loc);
			if (leftChild < size()) {
				double priority = priorities[loc];
				double leftChildPriority = priorities[leftChild];
				if (leftChildPriority > priority) max = leftChild;
				int rightChild = rightChild(loc);
				if (rightChild < size()) {
					double rightChildPriority = priorities[rightChild(loc)];
					if (rightChildPriority > priority && rightChildPriority > leftChildPriority) max = rightChild;
				}
			}
			if (max == loc) break;
			swap(loc, max);
			loc = max;
		}
		//		heapifyDown(max);
	}

	private void swap(int loc1, int loc2) {
		double tempPriority = priorities[loc1];
		E tempElement = elements[loc1];
		priorities[loc1] = priorities[loc2];
		elements[loc1] = elements[loc2];
		priorities[loc2] = tempPriority;
		elements[loc2] = tempElement;
	}

	private void removeFirst() {
		if (size < 1) return;
		swap(0, size - 1);
		size--;
		heapifyDown(0);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.berkeley.nlp.util.PriorityQueueInterface#hasNext()
	 */
	public boolean hasNext() {
		return !isEmpty();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.berkeley.nlp.util.PriorityQueueInterface#next()
	 */
	public E next() {
		E first = peek();
		removeFirst();
		return first;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.berkeley.nlp.util.PriorityQueueInterface#remove()
	 */
	public void remove() {
		throw new UnsupportedOperationException();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.berkeley.nlp.util.PriorityQueueInterface#peek()
	 */
	public E peek() {
		if (size() > 0) return elements[0];
		throw new NoSuchElementException();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.berkeley.nlp.util.PriorityQueueInterface#getPriority()
	 */
	public double getPriority() {
		if (size() > 0) return priorities[0];
		throw new NoSuchElementException();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.berkeley.nlp.util.PriorityQueueInterface#size()
	 */
	public int size() {
		return size;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.berkeley.nlp.util.PriorityQueueInterface#isEmpty()
	 */
	public boolean isEmpty() {
		return size == 0;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.berkeley.nlp.util.PriorityQueueInterface#add(E, double)
	 */
	public boolean add(E key, double priority) {
		if (size == capacity) {
			grow(2 * capacity + 1);
		}
		elements[size] = key;
		priorities[size] = priority;
		heapifyUp(size);
		size++;
		return true;
	}

	public void put(E key, double priority) {
		add(key, priority);
	}

	/**
	 * Returns a representation of the queue in decreasing priority order.
	 */
	@Override
	public String toString() {
		return toString(size(), false);
	}

	/**
	 * Returns a representation of the queue in decreasing priority order,
	 * displaying at most maxKeysToPrint elements and optionally printing one
	 * element per line.
	 * 
	 * @param maxKeysToPrint
	 * @param multiline
	 *            TODO
	 */
	public String toString(int maxKeysToPrint, boolean multiline) {
		PriorityQueue<E> pq = clone();
		StringBuilder sb = new StringBuilder(multiline ? "" : "[");
		int numKeysPrinted = 0;
		NumberFormat f = NumberFormat.getInstance();
		f.setMaximumFractionDigits(5);
		while (numKeysPrinted < maxKeysToPrint && pq.hasNext()) {
			double priority = pq.getPriority();
			E element = pq.next();
			sb.append(element == null ? "null" : element.toString());
			sb.append(" : ");
			sb.append(f.format(priority));
			if (numKeysPrinted < size() - 1) sb.append(multiline ? "\n" : ", ");
			numKeysPrinted++;
		}
		if (numKeysPrinted < size()) sb.append("...");
		if (!multiline) sb.append("]");
		return sb.toString();
	}

	/**
	 * Returns a clone of this priority queue. Modifications to one will not
	 * affect modifications to the other.
	 */
	@Override
	public PriorityQueue<E> clone() {
		PriorityQueue<E> clonePQ = new PriorityQueue<E>();
		clonePQ.size = size;
		clonePQ.capacity = capacity;
		clonePQ.elements = elements == null ? null : Arrays.copyOf(elements, capacity);
		clonePQ.priorities = priorities == null ? null : Arrays.copyOf(priorities, capacity);
		//		if (size() > 0) {
		//			clonePQ.elements.addAll(elements);
		//			System.arraycopy(priorities, 0, clonePQ.priorities, 0, size());
		//		}
		return clonePQ;
	}

	public PriorityQueue() {
		this(15);
	}

	public PriorityQueue(int capacity) {
		int legalCapacity = 0;
		while (legalCapacity < capacity) {
			legalCapacity = 2 * legalCapacity + 1;
		}
		grow(legalCapacity);
	}

	public static void main(String[] args) {
		PriorityQueue<String> pq = new PriorityQueue<String>();
		System.out.println(pq);
		pq.put("one", 1);
		System.out.println(pq);
		pq.put("three", 3);
		System.out.println(pq);
		pq.put("one", 1.1);
		System.out.println(pq);
		pq.put("two", 2);
		System.out.println(pq);
		System.out.println(pq.toString(2, false));
		while (pq.hasNext()) {
			System.out.println(pq.next());
		}
	}

	public List<E> toSortedList() {
		List<E> l = new ArrayList<E>(size());
		PriorityQueue<E> pq = clone();
		while (pq.hasNext()) {
			l.add(pq.next());
		}
		return l;
	}

	public void clear() {
		this.size = 0;
	}
}
