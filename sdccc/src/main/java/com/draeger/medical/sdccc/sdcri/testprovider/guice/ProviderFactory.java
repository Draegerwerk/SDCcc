/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.sdcri.testprovider.guice;

import com.draeger.medical.sdccc.sdcri.testprovider.TestProviderImpl;
import com.google.inject.assistedinject.Assisted;

import java.io.InputStream;

/**
 * Provider factory.
 */
public interface ProviderFactory {

    /**
     * Create a provider using the given mdib.
     * @param mdibAsStream to start provider with
     * @return new provider instance
     */
    TestProviderImpl createProvider(@Assisted InputStream mdibAsStream);
}
