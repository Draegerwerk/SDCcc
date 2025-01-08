package com.draeger.medical.sdccc.tests.util;

import java.util.Objects;

/**
 * Represents an operation that accepts three input arguments and returns no result.
 *
 * @param <A> the type of the first argument to the operation
 * @param <B> the type of the second argument to the operation
 * @param <C> the type of the second argument to the operation
 *
 */
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
