/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2023-2024 Draegerwerk AG & Co. KGaA.
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
    public static final String SDCCC = "SDCcc.";
    public static final String CI_MODE = SDCCC + "CIMode";
    public static final String GRAPHICAL_POPUPS = SDCCC + "GraphicalPopups";
    public static final String TEST_EXECUTION_LOGGING = SDCCC + "TestExecutionLogging";
    public static final String SUMMARIZE_MESSAGE_ENCODING_ERRORS = SDCCC + "SummarizeMessageEncodingErrors";
    public static final String ENABLE_MESSAGE_ENCODING_CHECK = SDCCC + "EnableMessageEncodingCheck";
    public static final String OVER_RIDE = "Override";

    /*
     * TLS configuration
     */
    private static final String TLS = "TLS.";
    public static final String FILE_DIRECTORY = SDCCC + TLS + "FileDirectory";
    public static final String KEY_STORE_PASSWORD = SDCCC + TLS + "KeyStorePassword";
    public static final String TRUST_STORE_PASSWORD = SDCCC + TLS + "TrustStorePassword";
    public static final String PARTICIPANT_PRIVATE_PASSWORD = SDCCC + TLS + "ParticipantPrivatePassword";
    public static final String TLS_ENABLED_PROTOCOLS = SDCCC + TLS + "EnabledProtocols";
    public static final String TLS_ENABLED_CIPHERS = SDCCC + TLS + "EnabledCiphers";

    /*
     * Network configuration
     */
    private static final String NETWORK = "Network.";
    public static final String NETWORK_INTERFACE_ADDRESS = SDCCC + NETWORK + "InterfaceAddress";
    public static final String NETWORK_MAX_WAIT = SDCCC + NETWORK + "MaxWait";
    public static final String NETWORK_MULTICAST_TTL = SDCCC + NETWORK + "MulticastTTL"; // should be between 0 and 255

    /*
     * Consumer configuration
     */
    private static final String CONSUMER = "Consumer.";
    public static final String CONSUMER_DEVICE_EPR = SDCCC + CONSUMER + Constants.DEVICE_EPR_POSTFIX;
    public static final String CONSUMER_DEVICE_LOCATION_FACILITY = SDCCC + CONSUMER + "DeviceLocationFacility";
    public static final String CONSUMER_DEVICE_LOCATION_BUILDING = SDCCC + CONSUMER + "DeviceLocationBuilding";
    public static final String CONSUMER_DEVICE_LOCATION_POINT_OF_CARE = SDCCC + CONSUMER + "DeviceLocationPointOfCare";
    public static final String CONSUMER_DEVICE_LOCATION_FLOOR = SDCCC + CONSUMER + "DeviceLocationFloor";
    public static final String CONSUMER_DEVICE_LOCATION_ROOM = SDCCC + CONSUMER + "DeviceLocationRoom";
    public static final String CONSUMER_DEVICE_LOCATION_BED = SDCCC + CONSUMER + "DeviceLocationBed";

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
