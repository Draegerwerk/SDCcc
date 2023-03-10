/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2023 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.configuration;

import org.somda.sdc.common.guice.AbstractConfigurationModule;

/**
 * Provides default configuration parameters for SDCcc.
 *
 * @see TestSuiteConfig
 */
public class DefaultTestSuiteConfig extends AbstractConfigurationModule {

    private static final int BUFFER_SIZE = 100;

    @Override
    protected void defaultConfigure() {
        configureTestSuite();
        configureTLS();
        configureNetwork();
        configureProvider();
        configureGRpc();
        configureInternalSettings();
        configureCommlogSettings();
    }

    void configureTestSuite() {
        bind(TestSuiteConfig.CI_MODE, Boolean.class, false);

        bind(TestSuiteConfig.GRAPHICAL_POPUPS, Boolean.class, true);
    }

    void configureTLS() {
        bind(TestSuiteConfig.FILE_DIRECTORY, String.class, "");
        bind(TestSuiteConfig.KEY_STORE_PASSWORD, String.class, "");
        bind(TestSuiteConfig.TRUST_STORE_PASSWORD, String.class, "");
        bind(TestSuiteConfig.PARTICIPANT_PRIVATE_PASSWORD, String.class, "");
        bind(TestSuiteConfig.TLS_ENABLED_PROTOCOLS, String[].class, new String[] {"TLSv1.2", "TLSv1.3"});
    }

    void configureNetwork() {
        bind(TestSuiteConfig.NETWORK_INTERFACE_ADDRESS, String.class, "127.0.0.1");
    }

    void configureProvider() {
        bind(TestSuiteConfig.PROVIDER_ENABLE, Boolean.class, false);
    }

    void configureGRpc() {
        bind(TestSuiteConfig.GRPC_SERVER_ADDRESS, String.class, "localhost:50051");
    }

    void configureInternalSettings() {
        bind(TestSuiteConfig.SDC_TEST_DIRECTORIES, String[].class, new String[] {
            "com.draeger.medical.sdccc.tests.biceps",
            "com.draeger.medical.sdccc.tests.mdpws",
            "com.draeger.medical.sdccc.tests.dpws",
            "com.draeger.medical.sdccc.tests.glue",
        });
    }

    protected void configureCommlogSettings() {
        bind(TestSuiteConfig.COMMLOG_MESSAGE_BUFFER_SIZE, int.class, BUFFER_SIZE);
    }
}
