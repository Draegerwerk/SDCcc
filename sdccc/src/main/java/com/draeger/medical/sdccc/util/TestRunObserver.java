/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2023 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.util;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Utility which is used to register unexpected failures during a test run to mark runs as invalid.
 */
@Singleton
public class TestRunObserver {
    private static final Logger LOG = LogManager.getLogger(TestRunObserver.class);

    private long totalNumberOfTestsRun;

    private boolean isInvalid;
    private final Set<String> reasons;

    @Inject
    TestRunObserver() {
        isInvalid = false;
        reasons = new HashSet<>();
    }

    /**
     * Invalidates the test run using the given reason.
     *
     * @param reason the test run failed
     */
    public synchronized void invalidateTestRun(final String reason) {
        if (reasons.add(reason)) {
            LOG.error("Test run has been marked as invalid. Reason: {}", reason);
            isInvalid = true;
        }
    }

    /**
     * Invalidates the test run using the given exception.
     *
     * @param exception which caused the test run failure
     */
    public synchronized void invalidateTestRun(final Throwable exception) {
        if (reasons.add(exception.getMessage())) {
            LOG.error("Test run has been marked as invalid, because an unexpected exception occurred.", exception);
            isInvalid = true;
        }
    }

    /**
     * Invalidates the test run using the given reason and exception.
     *
     * @param reason    the test run failed
     * @param exception which caused the test run failure
     */
    public synchronized void invalidateTestRun(final String reason, final Throwable exception) {
        if (reasons.add(reason)) {
            LOG.error("Test run has been marked as invalid. Reason: {}", reason, exception);
            isInvalid = true;
        }
    }

    /**
     * Has the test run been marked as invalid.
     *
     * @return true if invalid, false otherwise
     */
    public synchronized boolean isInvalid() {
        return isInvalid;
    }

    /**
     * Lists all reasons for invalidation which have been collected over the test run.
     *
     * @return reasons for invalidation
     */
    public synchronized List<String> getReasons() {
        return new ArrayList<>(reasons);
    }

    public long getTotalNumberOfTestsRun() {
        return totalNumberOfTestsRun;
    }

    /**
     * Sets the totalNumberOfTestRuns.
     * @param totalNumberOfTestsRun the new value.
     */
    public void setTotalNumberOfTestsRun(final long totalNumberOfTestsRun) {
        this.totalNumberOfTestsRun = totalNumberOfTestsRun;
    }
}
