/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.util;

/**
 * Indicates a Message Sending Failure.
 */
public class MessagingException extends Exception {

    /**
     * Constructs a MessagingException.
     * @param message the Exception's message
     * @param cause the Exception's cause
     */
    public MessagingException(final String message, final Exception cause) {
        super(message, cause);
    }
}
