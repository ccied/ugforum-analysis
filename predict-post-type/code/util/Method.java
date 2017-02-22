package util;

/**
 * A function wrapping interface.
 * @author John DeNero
 */
public interface Method<I, O> {
	public O call(I obj);
}
