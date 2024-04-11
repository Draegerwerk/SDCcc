/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2023 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.tests;

import com.draeger.medical.sdccc.tests.util.NoTestData;
import com.google.inject.Injector;
import java.util.Collection;
import javax.annotation.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Base class for SDCcc requirement tests.
 */
public class InjectorTestBase {
    private static final Logger LOG = LogManager.getLogger(InjectorTestBase.class);
    private static Injector injector;

    /**
     * @return the SDCcc guice injector
     */
    public static Injector getInjector() {
        if (injector == null) {
            throw new RuntimeException("Injector has not been set up");
        }
        return injector;
    }

    /**
     * Set the guice {@linkplain Injector} for all SDCcc requirement tests.
     *
     * @param injector a guice injector.
     */
    public static void setInjector(final Injector injector) {
        if (InjectorTestBase.injector != null) {
            LOG.warn("Injector has already been set up");
        }
        InjectorTestBase.injector = injector;
    }

    /**
     * Asserts whether the provided collection is not empty.
     *
     * @param data    a collection with data
     * @param message the message to display on failure
     * @throws NoTestData thrown if collection is empty
     */
    public void assertTestData(final Collection<?> data, final String message) throws NoTestData {
        if (data.isEmpty()) {
            LOG.error(message);
            throw new NoTestData(message);
        }
    }

    /**
     * Asserts whether the provided value is greater than zero.
     *
     * @param data    an integer representing the number of test items
     * @param message the message to display on failure
     * @throws NoTestData thrown if collection is empty
     */
    public void assertTestData(final int data, final String message) throws NoTestData {
        if (data == 0) {
            LOG.error(message);
            throw new NoTestData(message);
        }
    }

    /**
     * Asserts whether the provided value is true.
     *
     * @param data    a boolean representing, if there are test items
     * @param message the message to display on failure
     * @throws NoTestData thrown if collection is empty
     */
    public void assertTestData(final boolean data, final String message) throws NoTestData {
        if (!data) {
            LOG.error(message);
            throw new NoTestData(message);
        }
    }

    /**
     * Asserts whether the provided value is not null.
     *
     * @param data    an object representing the value to check for null
     * @param message the message to display on failure
     * @throws NoTestData thrown if the provided value is null
     */
    public void assertTestDataNotNull(@Nullable final Object data, final String message) throws NoTestData {
        if (data == null) {
            LOG.error(message);
            throw new NoTestData(message);
        }
    }
}
