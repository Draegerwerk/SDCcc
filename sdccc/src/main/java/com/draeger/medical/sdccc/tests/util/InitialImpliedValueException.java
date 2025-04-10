/*
 * This Source Code Form is subject to the terms of the "SDCcc non-commercial use license".
 *
 * Copyright (C) 2025 Draegerwerk AG & Co. KGaA
 */

package com.draeger.medical.sdccc.tests.util;

/**
 * Indicates a failure when retrieving the implied value of a AbstractDescriptor/@Version, AbstractState/@StateVersion,
 * or AbstractState/@DescriptorVersion.
 */
public class InitialImpliedValueException extends Exception {

    /**
     * Constructs a new InitialImpliedValueException with the specified message.
     *
     * @param message the Exception's message
     */
    public InitialImpliedValueException(final String message) {
        super(message);
    }
}
