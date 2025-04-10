/*
 * This Source Code Form is subject to the terms of the "SDCcc non-commercial use license".
 *
 * Copyright (C) 2025 Draegerwerk AG & Co. KGaA
 */

package com.draeger.medical.sdccc.manipulation.precondition;

import com.google.inject.Injector;
import java.util.Objects;

/**
 * A simple precondition which verifies whether a condition is fulfilled and executes a manipulation in case it isn't.
 */
public class SimplePrecondition implements Precondition {

    private final PreconditionFunction<Injector> preconditionCheck;
    private final ManipulationFunction<Injector> manipulationCall;

    /**
     * @param isPreconditionMet precondition check to verify whether executing manipulations is required
     * @param manipulationCall  function to call in case manipulations are required
     */
    public SimplePrecondition(
            final PreconditionFunction<Injector> isPreconditionMet,
            final ManipulationFunction<Injector> manipulationCall) {
        this.preconditionCheck = isPreconditionMet;
        this.manipulationCall = manipulationCall;
    }

    @Override
    public void verifyPrecondition(final Injector injector) throws PreconditionException {
        if (this.preconditionCheck.apply(injector)) {
            return;
        }
        manipulationCall.apply(injector);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final SimplePrecondition that = (SimplePrecondition) o;
        return preconditionCheck.equals(that.preconditionCheck) && manipulationCall.equals(that.manipulationCall);
    }

    @Override
    public int hashCode() {
        return Objects.hash(preconditionCheck, manipulationCall);
    }
}
