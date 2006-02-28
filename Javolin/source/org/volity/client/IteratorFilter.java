package org.volity.client;

import java.util.Iterator;
import java.util.List;

/**
 * IteratorFilter is a handy way to iterate over *some* items in a list or
 * other iterable.
 *
 * You create a subclass and define the matches() predicate. The result is
 * an Iterator that wraps an existing Iterator, and only lets the matching
 * objects show through.
 */
public abstract class IteratorFilter implements Iterator {
    Iterator mBaseIterator;
    Object nextObject;

    public IteratorFilter(Iterator iter) {
        mBaseIterator = iter;
        nextObject = null;
        nextMatching();
    }

    public IteratorFilter(List ls) {
        mBaseIterator = ls.iterator();
        nextObject = null;
        nextMatching();
    }

    private void nextMatching() {
        while (mBaseIterator.hasNext()) {
            Object obj = mBaseIterator.next();
            if (matches(obj)) {
                nextObject = obj;
                return;
            }
        }
        nextObject = null;
    }

    protected abstract boolean matches(Object obj);

    public boolean hasNext() {
        return (nextObject != null);
    }

    public Object next() {
        Object val = nextObject;
        nextMatching();
        return val;
    }

    public void remove() {
        throw new UnsupportedOperationException("IteratorFilter does not support remove");
    }
}
