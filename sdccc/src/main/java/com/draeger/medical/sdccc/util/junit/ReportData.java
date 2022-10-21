/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.util.junit;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.reporting.ReportEntry;
import org.junit.platform.launcher.TestIdentifier;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Container used for JUnit-like report format.
 */
// TODO: Transform to record for java 17 (https://github.com/Draegerwerk/SDCcc/issues/7)
public class ReportData {

    private final TestIdentifier testIdentifier;
    private final Duration testDuration;

    private final TestExecutionResult testExecutionResult;

    private final List<ReportEntry> reportEntries;

    /**
     * Initializes a new ReportData container.
     *
     * @param testIdentifier of the test case
     * @param testDuration runtime of the test case
     * @param testExecutionResult result of the test case
     * @param reportEntries report entries of the test case
     */
    public ReportData(
        final TestIdentifier testIdentifier,
        final @Nullable Duration testDuration,
        final TestExecutionResult testExecutionResult,
        final List<ReportEntry> reportEntries
    ) {
        this.testIdentifier = testIdentifier;
        this.testDuration = testDuration;
        this.testExecutionResult = testExecutionResult;
        this.reportEntries = reportEntries;
    }


    public TestIdentifier getTestIdentifier() {
        return testIdentifier;
    }

    public @Nullable Duration getTestDuration() {
        return testDuration;
    }

    public TestExecutionResult getTestExecutionResult() {
        return testExecutionResult;
    }

    public List<ReportEntry> getReportEntries() {
        return new ArrayList<>(reportEntries);
    }
}
