/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.util.junit;

import com.draeger.medical.sdccc.util.junit.guice.XmlReportFactory;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.xml.stream.XMLStreamException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.reporting.ReportEntry;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

/**
 * A {@linkplain TestExecutionListener} which collects information for a JUnit-like XML report.
 */
public class XmlReportListener implements TestExecutionListener {

    private static final Logger LOG = LogManager.getLogger(XmlReportListener.class);

    private final Path reportsDir;
    private final String xmlReportName;
    private final XmlReportFactory xmlReportFactory;

    private List<ReportData> results;

    private Map<String, Instant> testStartTime;
    private Map<String, Instant> testEndTime;

    private Map<String, List<ReportEntry>> reportEntries;

    @AssistedInject
    XmlReportListener(
            @Assisted final Path reportsDir,
            @Assisted final String xmlReportName,
            final XmlReportFactory xmlReportFactory) {
        this.reportsDir = reportsDir;
        this.xmlReportName = xmlReportName;
        this.xmlReportFactory = xmlReportFactory;
    }

    @Override
    public void testPlanExecutionStarted(final TestPlan testPlan) {
        results = new ArrayList<>();
        testStartTime = new ConcurrentHashMap<>();
        testEndTime = new ConcurrentHashMap<>();
        reportEntries = new ConcurrentHashMap<>();
    }

    @Override
    public void testPlanExecutionFinished(final TestPlan testPlan) {
        results = null;
        testStartTime = null;
        testEndTime = null;
        reportEntries = null;
    }

    @Override
    public void dynamicTestRegistered(final TestIdentifier testIdentifier) {
        // doesn't matter
    }

    @Override
    public void executionSkipped(final TestIdentifier testIdentifier, final String reason) {
        if (testIdentifier.isTest()) {
            final var now = Instant.now();
            testStartTime.put(testIdentifier.getUniqueId(), now);
            testEndTime.put(testIdentifier.getUniqueId(), now);
            results.add(new ReportData(
                    testIdentifier,
                    null,
                    TestExecutionResult.failed(new Exception("Skipped")),
                    this.reportEntries.getOrDefault(testIdentifier.getUniqueId(), Collections.emptyList())));
        }
    }

    @Override
    public void executionStarted(final TestIdentifier testIdentifier) {
        testStartTime.put(testIdentifier.getUniqueId(), Instant.now());
    }

    @Override
    public void executionFinished(final TestIdentifier testIdentifier, final TestExecutionResult testExecutionResult) {
        testEndTime.put(testIdentifier.getUniqueId(), Instant.now());
        if (testIdentifier.isTest()) {
            results.add(new ReportData(
                    testIdentifier,
                    getDurationForUniqueId(testIdentifier.getUniqueId()),
                    testExecutionResult,
                    this.reportEntries.getOrDefault(testIdentifier.getUniqueId(), Collections.emptyList())));
        }

        if (testIdentifier.getParentId().isEmpty()) {
            // write report
            writeXmlReport(testIdentifier);
        }
    }

    private void writeXmlReport(final TestIdentifier testIdentifier) {
        final var writer = xmlReportFactory.createXmlReportWriter(results);
        try {
            writer.writeXmlReport(reportsDir, xmlReportName, getDurationForUniqueId(testIdentifier.getUniqueId()));
        } catch (final XMLStreamException | IOException e) {
            LOG.error("Could not write XML Report", e);
        }
    }

    @Override
    public void reportingEntryPublished(final TestIdentifier testIdentifier, final ReportEntry entry) {
        final List<ReportEntry> entries =
                this.reportEntries.computeIfAbsent(testIdentifier.getUniqueId(), key -> new ArrayList<>());
        entries.add(entry);
    }

    private Duration getDurationForUniqueId(final String uniqueId) {
        return Duration.between(testStartTime.get(uniqueId), testEndTime.get(uniqueId));
    }
}
