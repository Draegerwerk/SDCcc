/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2023 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.configuration;

import com.draeger.medical.sdccc.util.Constants;

/**
 * Configuration of the SDCcc package.
 *
 * @see DefaultTestSuiteConfig
 */
public final class TestSuiteConfig {

    /*
     * General configuration
     */
    private static final String SDCCC = "SDCcc.";
    public static final String CI_MODE = SDCCC + "CIMode";
    public static final String GRAPHICAL_POPUPS = SDCCC + "GraphicalPopups";
    public static final String TEST_EXECUTION_LOGGING = SDCCC + "TestExecutionLogging";

    /*
     * TLS configuration
     */
    private static final String TLS = "TLS.";
    public static final String FILE_DIRECTORY = SDCCC + TLS + "FileDirectory";
    public static final String KEY_STORE_PASSWORD = SDCCC + TLS + "KeyStorePassword";
    public static final String TRUST_STORE_PASSWORD = SDCCC + TLS + "TrustStorePassword";
    public static final String PARTICIPANT_PRIVATE_PASSWORD = SDCCC + TLS + "ParticipantPrivatePassword";
    public static final String TLS_ENABLED_PROTOCOLS = SDCCC + TLS + "EnabledProtocols";

    /*
     * Network configuration
     */
    private static final String NETWORK = "Network.";
    public static final String NETWORK_INTERFACE_ADDRESS = SDCCC + NETWORK + "InterfaceAddress";

    /*
     * Consumer configuration
     */
    private static final String CONSUMER = "Consumer.";
    public static final String CONSUMER_ENABLE = SDCCC + CONSUMER + Constants.ENABLE_SETTING_POSTFIX;
    public static final String CONSUMER_DEVICE_EPR = SDCCC + CONSUMER + Constants.DEVICE_EPR_POSTFIX;

    /*
     * Provider configuration
     */
    private static final String PROVIDER = "Provider.";
    public static final String PROVIDER_ENABLE = SDCCC + PROVIDER + Constants.ENABLE_SETTING_POSTFIX;
    public static final String PROVIDER_DEVICE_EPR = SDCCC + PROVIDER + Constants.DEVICE_EPR_POSTFIX;

    /*
     * GRPC configuration
     */
    private static final String GRPC = "gRPC.";
    public static final String GRPC_SERVER_ADDRESS = SDCCC + GRPC + "ServerAddress";

    /*
     * Commlog configuration
     */
    private static final String COMMLOG = "Commlog.";
    // note, that the actual size will be (this * (thread count + 1))
    public static final String COMMLOG_MESSAGE_BUFFER_SIZE = SDCCC + COMMLOG + "BufferSize";

    /*
     * Internal settings which should not be overwritten by a user
     */
    private static final String INTERNAL = "Internal.";

    public static final String SDC_TEST_DIRECTORIES = SDCCC + INTERNAL + "SdcTestDirectories";

    private TestSuiteConfig() {}
}
