/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2023, 2024 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package it.com.draeger.medical.sdccc.test_util.testprovider;

import com.draeger.medical.sdccc.configuration.DefaultTestSuiteConfig;
import com.draeger.medical.sdccc.configuration.TestSuiteConfig;
import com.draeger.medical.sdccc.util.Constants;

/**
 * Configuration of the SDCcc package.
 *
 * @see DefaultTestSuiteConfig
 */
public final class TestProviderConfig {

    /*
     * Provider configuration
     */
    private static final String PROVIDER = "Provider.";
    public static final String PROVIDER_DEVICE_EPR = TestSuiteConfig.SDCCC + PROVIDER + Constants.DEVICE_EPR_POSTFIX;

    private TestProviderConfig() {}
}
