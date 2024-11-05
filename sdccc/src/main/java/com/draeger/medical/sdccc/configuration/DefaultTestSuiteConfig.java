/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2023-2024 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.configuration;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.somda.sdc.common.guice.AbstractConfigurationModule;

/**
 * Provides default configuration parameters for SDCcc.
 *
 * @see TestSuiteConfig
 */
public class DefaultTestSuiteConfig extends AbstractConfigurationModule {

    @SuppressFBWarnings(
            value = {"MS_MUTABLE_ARRAY"},
            justification = "In case this is wrong there will be an error (for test cases that couldn't be found)"
                    + " and thus this being wrong due to modification will always be noticed.")
    public static final String[] DEFAULT_DIRECTORIES = {
        "com.draeger.medical.sdccc.tests.biceps",
        "com.draeger.medical.sdccc.tests.mdpws",
        "com.draeger.medical.sdccc.tests.dpws",
        "com.draeger.medical.sdccc.tests.glue",
    };

    private static final int BUFFER_SIZE = 100;

    @Override
    protected void defaultConfigure() {
        configureTestSuite();
        configureTLS();
        configureNetwork();
        configureGRpc();
        configureInternalSettings();
        configureCommlogSettings();
    }

    void configureTestSuite() {
        bind(TestSuiteConfig.CI_MODE, Boolean.class, false);

        bind(TestSuiteConfig.GRAPHICAL_POPUPS, Boolean.class, true);

        bind(TestSuiteConfig.TEST_EXECUTION_LOGGING, Boolean.class, false);

        bind(TestSuiteConfig.ENABLE_MESSAGE_ENCODING_CHECK, Boolean.class, true);
        bind(TestSuiteConfig.SUMMARIZE_MESSAGE_ENCODING_ERRORS, Boolean.class, true);
    }

    void configureTLS() {
        bind(TestSuiteConfig.FILE_DIRECTORY, String.class, "");
        bind(TestSuiteConfig.KEY_STORE_PASSWORD, String.class, "");
        bind(TestSuiteConfig.TRUST_STORE_PASSWORD, String.class, "");
        bind(TestSuiteConfig.PARTICIPANT_PRIVATE_PASSWORD, String.class, "");
        bind(TestSuiteConfig.TLS_ENABLED_PROTOCOLS, String[].class, new String[] {"TLSv1.2", "TLSv1.3"});
        bind(TestSuiteConfig.TLS_ENABLED_CIPHERS, String[].class, new String[] {
            // TLS 1.2
            "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
            "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
            "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
            "TLS_DHE_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_DHE_RSA_WITH_AES_256_GCM_SHA384",
            // TLS 1.3
            "TLS_AES_128_GCM_SHA256",
            "TLS_AES_256_GCM_SHA384",
        });
    }

    void configureNetwork() {
        bind(TestSuiteConfig.NETWORK_INTERFACE_ADDRESS, String.class, "127.0.0.1");
        bind(TestSuiteConfig.NETWORK_MAX_WAIT, long.class, 10L);
        bind(TestSuiteConfig.NETWORK_MULTICAST_TTL, long.class, 128L);
        bind(TestSuiteConfig.NETWORK_RECONNECT_TRIES, long.class, 3L);
        bind(TestSuiteConfig.NETWORK_RECONNECT_WAIT, long.class, 5L);
    }

    void configureGRpc() {
        bind(TestSuiteConfig.GRPC_SERVER_ADDRESS, String.class, "localhost:50051");
    }

    void configureInternalSettings() {
        bind(TestSuiteConfig.SDC_TEST_DIRECTORIES, String[].class, DEFAULT_DIRECTORIES);
    }

    protected void configureCommlogSettings() {
        bind(TestSuiteConfig.COMMLOG_MESSAGE_BUFFER_SIZE, int.class, BUFFER_SIZE);
    }
}
