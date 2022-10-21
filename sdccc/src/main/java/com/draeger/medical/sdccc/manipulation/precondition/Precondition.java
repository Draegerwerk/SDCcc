/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.manipulation.precondition;

import com.google.inject.Injector;

/**
 * Describes a precondition to be checked before running invariant tests.
 *
 * <p>
 * Preconditions check whether a certain condition is fulfilled, and if it isn't, calls one or multiple manipulations
 * to reach a state where the precondition is fulfilled. Because this requires a connection to the DUT, they must be
 * run before the actual invariant test cases.
 */
public interface Precondition {
    /**
     * Calls the precondition to verify whether it is fulfilled, and executes necessary measures to become fulfilled.
     *
     * @param injector for access to the test run environment
     * @throws PreconditionException if an error occurs during either validation of the precondition or execution
     *                               of the manipulation
     */
    void verifyPrecondition(Injector injector) throws PreconditionException;
}
