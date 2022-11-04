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
import java.util.List;


/**
 * Container used for JUnit-like report format.
 *
 * @param testIdentifier      of the test case
 * @param testDuration        runtime of the test case
 * @param testExecutionResult result of the test case
 * @param reportEntries       report entries of the test case
 */
public record ReportData(
        TestIdentifier testIdentifier,
        @Nullable Duration testDuration,
        TestExecutionResult testExecutionResult,
        List<ReportEntry> reportEntries) {
}
