/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2023 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.tests.util;

import java.math.BigInteger;
import java.util.function.Predicate;
import org.apache.commons.lang3.tuple.Pair;
import org.somda.sdc.biceps.model.message.AbstractReport;

/**
 * Predicate that does the same as InitialMdibVersionPredicate, but also passes the UUID Strings through.
 */
public class InitialMdibVersionPredicateWithUUID implements Predicate<Pair<AbstractReport, String>> {
    private final BigInteger initialMdibVersion;
    private boolean seenLargerVersion;

    InitialMdibVersionPredicateWithUUID(final BigInteger initialValue) {
        initialMdibVersion = initialValue;
        seenLargerVersion = false;
    }

    @Override
    public boolean test(final Pair<AbstractReport, String> pair) {
        final var report = pair.getLeft();
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
