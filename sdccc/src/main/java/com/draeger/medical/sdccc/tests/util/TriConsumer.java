package com.draeger.medical.sdccc.tests.util;

import java.util.Objects;

@FunctionalInterface
public interface TriConsumer<A, B, C> {
    /**
     * Performs this operation on the given arguments.
     *
     * @param a the first input argument
     * @param b the second input argument
     * @param c the third input argument
     */
    void accept(A a, B b, C c);

    /**
     * Returns a composed {@code TriConsumer} that performs, in sequence, this operation followed by the
     * {@code after} operation. If performing either operation throws an exception, it is relayed to the
     * caller of the composed operation.
     *
     * @param after the operation to perform after this operation
     * @return a composed {@code TriConsumer} that performs in sequence this operation followed by the
     * {@code after} operation
     * @throws NullPointerException if {@code after} is null
     */
    default TriConsumer<A, B, C> andThen(TriConsumer<? super A, ? super B, ? super C> after) {
        Objects.requireNonNull(after);
        return (a, b, c) -> {
            accept(a, b, c);
            after.accept(a, b, c);
        };
    }
}
