/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.tests.util;

import com.draeger.medical.sdccc.tests.annotations.TestIdentifier;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Names;
import java.util.function.Predicate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.engine.descriptor.MethodBasedTestDescriptor;
import org.junit.platform.engine.FilterResult;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.launcher.PostDiscoveryFilter;

/**
 * Filters discovered test cases based on whether they're enabled.
 *
 * @see com.draeger.medical.sdccc.configuration.EnabledTestConfig
 */
public class TestEnabledFilter implements PostDiscoveryFilter {
    private static final Logger LOG = LogManager.getLogger(TestEnabledFilter.class);
    private final Injector injector;

    @Inject
    TestEnabledFilter(final Injector injector) {
        this.injector = injector;
    }

    @Override
    public FilterResult apply(final TestDescriptor object) {
        final FilterResult result;

        // only filter tests
        if (!object.isTest()) {
            result = FilterResult.included("Class");
        } else if (!(object instanceof MethodBasedTestDescriptor)) {
            result = FilterResult.included("Only filtering methods");
        } else {
            final var method = (MethodBasedTestDescriptor) object;
            final TestIdentifier testIdentifier;
            testIdentifier = method.getTestMethod().getAnnotation(TestIdentifier.class);

            if (testIdentifier == null) {
                LOG.error(
                        "Test {} does not have a TestIdentifier tag, this is not allowed!",
                        method.getTestMethod().getName());
                result = FilterResult.excluded("Test does not have a TestDescription tag, this is not allowed!");
            } else {

                result = getFilterResultFromTestIdentifier(testIdentifier);
            }
        }

        return result;
    }

    private FilterResult getFilterResultFromTestIdentifier(final TestIdentifier testIdentifier) {
        final FilterResult result;
        final var actualIdentifier = testIdentifier.value();

        LOG.info("Found test identifier {}", actualIdentifier);

        // Disabled check
        final boolean enabled = injector.getInstance(Key.get(Boolean.class, Names.named(actualIdentifier)));

        if (!enabled) {
            LOG.info("Test {} is not enabled", actualIdentifier);
            result = FilterResult.excluded(String.format("Test %s is not enabled", actualIdentifier));
        } else {

            result = FilterResult.included("Not disabled");
        }
        return result;
    }

    @Override
    public Predicate<TestDescriptor> toPredicate() {
        return testDescriptor -> apply(testDescriptor).included();
    }
}
