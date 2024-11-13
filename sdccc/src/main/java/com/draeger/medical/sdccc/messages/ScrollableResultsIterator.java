package com.draeger.medical.sdccc.messages;

import org.hibernate.query.spi.CloseableIterator;
import org.hibernate.query.spi.ScrollableResultsImplementor;

class ScrollableResultsIterator<T> implements CloseableIterator<T> {
    private final ScrollableResultsImplementor scrollableResults;

    ScrollableResultsIterator(ScrollableResultsImplementor scrollableResults) {
        this.scrollableResults = scrollableResults;
    }

    @Override
    public void close() {
        scrollableResults.close();
    }

    @Override
    public boolean hasNext() {
        return !scrollableResults.isClosed() && scrollableResults.next();
    }

    @Override
    @SuppressWarnings("unchecked")
    public T next() {
        Object[] next = scrollableResults.get();
        if (next.length == 1) {
            return (T) next[0];
        } else {
            return (T) next;
        }
    }
}
