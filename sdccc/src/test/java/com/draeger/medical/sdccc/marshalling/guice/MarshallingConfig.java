/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2023 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.marshalling.guice;

/**
 * Configuration for Soap Marshalling.
 */
public final class MarshallingConfig {

    /**
     * Enables XML schema validation in the marshaller.
     * <ul>
     * <li>Data type: {@linkplain Boolean}
     * <li>Use: optional
     * </ul>
     */
    public static final String VALIDATE_SOAP_MESSAGES = "ValidateSoapMessages";

    public static final String PACKAGES = "Packages";
    public static final String SCHEMAS = "Schemas";

    private MarshallingConfig() {}
}
