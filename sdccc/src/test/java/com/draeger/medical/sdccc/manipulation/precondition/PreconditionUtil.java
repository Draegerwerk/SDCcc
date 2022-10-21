/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.manipulation.precondition;

import com.google.inject.Injector;

import java.util.concurrent.Callable;

/**
 * Utilities for precondition testing.
 */
public class PreconditionUtil {

    /**
     * An implementation of {@linkplain SimplePrecondition} which allows adding custom
     * handlers for precondition testing, manipulation calls as well as constructor calls.
     */
    public static class MockPrecondition extends SimplePrecondition {

        private static PreconditionFunction<Injector> isPreconditionMet;
        private static ManipulationFunction<Injector> manipulationCall;
        private static Callable<Void> afterConstructorCall;

        MockPrecondition() throws Exception {
            super(isPreconditionMet, manipulationCall);
            afterConstructorCall.call();
        }

        /**
         * Resets the {@linkplain MockPrecondition} to it's initial state.
         */
        public static void reset() {
            setIsPreconditionMet(injector -> true);
            setManipulationCall(injector -> true);
            setAfterConstructorCall(() -> null);
        }

        /**
         * Sets the precondition verification call to a new function.
         *
         * @param isPreconditionMet function to call for precondition verification
         */
        public static void setIsPreconditionMet(final PreconditionFunction<Injector> isPreconditionMet) {
            MockPrecondition.isPreconditionMet = isPreconditionMet;
        }

        /**
         * Sets the manipulation call to a new function.
         *
         * @param manipulationCall function to call for manipulation
         */
        public static void setManipulationCall(final ManipulationFunction<Injector> manipulationCall) {
            MockPrecondition.manipulationCall = manipulationCall;
        }

        /**
         * Sets the after constructor call to a new function.
         *
         * <p>
         * This function is called as the last step of the constructor, allowing e.g. insertion of exceptions.
         *
         * @param afterConstructorCall function to call in the constructor
         */
        public static void setAfterConstructorCall(final Callable<Void> afterConstructorCall) {
            MockPrecondition.afterConstructorCall = afterConstructorCall;
        }
    }

    /**
     * An implementation of {@linkplain ManipulationPrecondition} which allows adding custom
     * handlers for manipulation and constructor calls.
     */
    public static class MockManipulation extends ManipulationPrecondition {
        private static ManipulationFunction<Injector> manipulationCall;
        private static Callable<Void> afterConstructorCall;

        MockManipulation() throws Exception {
            super(manipulationCall);
            afterConstructorCall.call();
        }

        /**
         * Resets the {@linkplain MockPrecondition} to it's initial state.
         */
        public static void reset() {
            setManipulationCall(injector -> true);
            setAfterConstructorCall(() -> null);
        }

        /**
         * Sets the manipulation call to a new function.
         *
         * @param manipulationCall function to call for manipulation
         */
        public static void setManipulationCall(final ManipulationFunction<Injector> manipulationCall) {
            MockManipulation.manipulationCall = manipulationCall;
        }

        /**
         * Sets the after constructor call to a new function.
         *
         * <p>
         * This function is called as the last step of the constructor, allowing e.g. insertion of exceptions.
         *
         * @param afterConstructorCall function to call in the constructor
         */
        public static void setAfterConstructorCall(final Callable<Void> afterConstructorCall) {
            MockManipulation.afterConstructorCall = afterConstructorCall;
        }
    }
}
