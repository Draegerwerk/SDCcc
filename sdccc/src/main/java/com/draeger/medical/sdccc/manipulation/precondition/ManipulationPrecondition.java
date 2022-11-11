/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.manipulation.precondition;

import com.google.inject.Injector;
import java.util.Objects;

/**
 * A simple precondition which always executes its manipulation.
 */
public class ManipulationPrecondition implements Precondition {

    private final ManipulationFunction<Injector> manipulationCall;

    /**
     * @param manipulationCall  function to call in case manipulations are required
     */
    public ManipulationPrecondition(final ManipulationFunction<Injector> manipulationCall) {
        this.manipulationCall = manipulationCall;
    }

    @Override
    public void verifyPrecondition(final Injector injector) throws PreconditionException {
        manipulationCall.apply(injector);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final ManipulationPrecondition that = (ManipulationPrecondition) o;
        return manipulationCall.equals(that.manipulationCall);
    }

    @Override
    public int hashCode() {
        return Objects.hash(manipulationCall);
    }
}
