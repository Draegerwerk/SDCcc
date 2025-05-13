/*
 * This Source Code Form is subject to the terms of the "SDCcc non-commercial use license".
 *
 * Copyright (C) 2025 Draegerwerk AG & Co. KGaA
 */

package com.draeger.medical.sdccc.tests.util;

/**
 * Exception which is thrown when a test case has not received the test data
 * required to evaluate the requirement.
 */
public class NoTestData extends Exception {
    /**
     * Creates a new {@linkplain NoTestData} exception.
     *
     * @param message describing the error
     */
    public NoTestData(final String message) {
        super(message);
    }
}
