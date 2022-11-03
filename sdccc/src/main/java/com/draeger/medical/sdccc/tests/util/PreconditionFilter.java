/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.tests.util;

import com.draeger.medical.sdccc.manipulation.precondition.ManipulationPrecondition;
import com.draeger.medical.sdccc.manipulation.precondition.PreconditionRegistry;
import com.draeger.medical.sdccc.manipulation.precondition.SimplePrecondition;
import com.draeger.medical.sdccc.tests.annotations.RequirePrecondition;
import com.google.inject.Inject;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.junit.jupiter.engine.descriptor.MethodBasedTestDescriptor;
import org.junit.platform.engine.FilterResult;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.launcher.PostDiscoveryFilter;

/**
 * A {@linkplain PostDiscoveryFilter} for invariant tests which registers
 * all preconditions with the {@linkplain PreconditionRegistry}.
 */
public class PreconditionFilter implements PostDiscoveryFilter {

    private final PreconditionRegistry preconditionRegistry;

    @Inject
    PreconditionFilter(final PreconditionRegistry preconditionRegistry) {
        this.preconditionRegistry = preconditionRegistry;
    }

    @Override
    @SuppressFBWarnings(value = {"DCN_NULLPOINTER_EXCEPTION"},
            justification = "intentional due to not all tests having a precondition")
    public FilterResult apply(final TestDescriptor object) {
        final FilterResult result;

        // only filter tests
        if (!object.isTest()) {
            result = FilterResult.included("Class");
        } else if (!(object instanceof MethodBasedTestDescriptor)) {
            result = FilterResult.included("Only filtering methods");
        } else {
            final var method = (MethodBasedTestDescriptor) object;
            RequirePrecondition requiredInteraction;
            try {
                requiredInteraction = method.getTestMethod().getAnnotation(RequirePrecondition.class);
            } catch (final NullPointerException e) {
                requiredInteraction = null;
            }

            if (requiredInteraction == null) {
                result = FilterResult.included("No interaction found");
            } else {

                for (final Class<? extends SimplePrecondition> complexInteraction : requiredInteraction
                    .simplePreconditions()) {
                    preconditionRegistry.registerSimplePrecondition(complexInteraction);
                }

                for (final Class<? extends ManipulationPrecondition> manipulationPrecondition : requiredInteraction
                    .manipulationPreconditions()) {
                    preconditionRegistry.registerManipulationPrecondition(manipulationPrecondition);
                }

                result = FilterResult.included("Filter only used for metadata collection");
            }
        }

        return result;
    }
}
