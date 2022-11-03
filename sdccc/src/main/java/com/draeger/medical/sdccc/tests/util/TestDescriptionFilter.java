/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.tests.util;

import com.draeger.medical.sdccc.tests.annotations.TestDescription;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.engine.descriptor.MethodBasedTestDescriptor;
import org.junit.platform.engine.FilterResult;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.launcher.PostDiscoveryFilter;

import java.util.function.Predicate;

/**
 * Filters test cases which do not have a {@linkplain TestDescription} annotation.
 */
public class TestDescriptionFilter implements PostDiscoveryFilter {
    private static final Logger LOG = LogManager.getLogger(TestDescriptionFilter.class);


    @Override
    @SuppressFBWarnings(value = {"DCN_NULLPOINTER_EXCEPTION"},
            justification = "intentional due to not all tests having a TestDescription")
    public FilterResult apply(final TestDescriptor object) {
        final FilterResult result;

        // only filter tests
        if (!object.isTest()) {
            result = FilterResult.included("Class");
        } else if (!(object instanceof MethodBasedTestDescriptor)) {
            result = FilterResult.included("Only filtering methods");
        } else {
            final var method = (MethodBasedTestDescriptor) object;
            TestDescription testDescription;
            try {
                testDescription = method.getTestMethod().getAnnotation(TestDescription.class);
            } catch (final NullPointerException e) {
                testDescription = null;
            }

            if (testDescription == null) {
                LOG.error(
                    "Test {} does not have a TestDescription tag, this is not allowed!",
                    method.getTestMethod().getName()
                );
                result = FilterResult.excluded("Test does not have a TestDescription tag, this is not allowed!");
            } else {

                LOG.debug("Found test description for test {}: {}", method.getTestMethod().getName(),
                    testDescription.value());
                result = FilterResult.included("Valid");
            }
        }

        return result;
    }

    @Override
    public Predicate<TestDescriptor> toPredicate() {
        return null;
    }
}