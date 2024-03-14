/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2023 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package it.com.draeger.medical.sdccc.test_util.testprovider.guice;

import com.google.inject.assistedinject.Assisted;
import it.com.draeger.medical.sdccc.test_util.testprovider.TestProviderImpl;
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
