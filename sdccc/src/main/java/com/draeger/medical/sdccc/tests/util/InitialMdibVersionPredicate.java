/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.tests.util;

import org.somda.sdc.biceps.model.message.AbstractReport;

import java.math.BigInteger;
import java.util.function.Predicate;

/**
 * Predicate to filter reports based on the provided initial mdib version.
 * All reports with a smaller version are filtered until a version larger than the initial version was seen.
 */
public class InitialMdibVersionPredicate implements Predicate<AbstractReport> {
    private final BigInteger initialMdibVersion;
    private boolean seenLargerVersion;

    InitialMdibVersionPredicate(final BigInteger initialValue) {
        initialMdibVersion = initialValue;
        seenLargerVersion = false;
    }

    @Override
    public boolean test(final AbstractReport report) {
        final var mdibVersion = ImpliedValueUtil.getReportMdibVersion(report);
        if (!seenLargerVersion) {
            if (mdibVersion.compareTo(initialMdibVersion) > 0) {
                seenLargerVersion = true;
            }
            return mdibVersion.compareTo(initialMdibVersion) >= 0;
        }
        return true;
    }
}
