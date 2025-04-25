/*
 * This Source Code Form is subject to the terms of the "SDCcc non-commercial use license".
 *
 * Copyright (C) 2025 Draegerwerk AG & Co. KGaA
 */

package com.draeger.medical.sdccc.manipulation.precondition;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;

import com.google.inject.Injector;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the {@linkplain SimplePrecondition}.
 */
public class SimplePreconditionTest {

    /**
     * Tests whether manipulation is not called when precondition is met.
     *
     * @throws PreconditionException on exceptions during precondition verification
     */
    @Test
    @DisplayName("Precondition is met, manipulation not called")
    public void testPreconditionMet() throws PreconditionException {
        final PreconditionFunction<Injector> preconditionMet = injector -> true;
        final ManipulationFunction<Injector> manipulationFail = injector -> fail("Manipulation called");

        final var injector = mock(Injector.class);

        final var precondition = new SimplePrecondition(preconditionMet, manipulationFail);
        precondition.verifyPrecondition(injector);
    }

    /**
     * Tests whether manipulation is called when precondition is not met.
     *
     * @throws PreconditionException on exceptions during precondition verification
     */
    @Test
    @DisplayName("Precondition is not met, manipulation called")
    public void testPreconditionNotMet() throws PreconditionException {
        final var manipulationCalled = new AtomicBoolean(false);
        final PreconditionFunction<Injector> preconditionMet = injector -> false;
        final ManipulationFunction<Injector> manipulationHandler = injector -> manipulationCalled.getAndSet(true);

        final var injector = mock(Injector.class);

        final var precondition = new SimplePrecondition(preconditionMet, manipulationHandler);
        precondition.verifyPrecondition(injector);

        assertTrue(manipulationCalled.get());
    }
}
