/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.util.junit;

import com.draeger.medical.sdccc.tests.annotations.TestDescription;
import com.draeger.medical.sdccc.util.TestRunObserver;
import com.draeger.medical.sdccc.util.junit.util.ClassUtil;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.reporting.ReportEntry;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestIdentifier;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.IOException;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Writer which produces a minimal JUnit compatible output XML.
 */
public class XmlReportWriter {

    public static final String MISSING_ANNOTATION_TEXT = "Test case %s did not present a valid %s annotation";
    public static final String INVALID_TEST_RUN_TEST_NAME = "SDCccInvalidTestRun";
    public static final String INVALID_TEST_RUN_CLASS_NAME = "com.draeger.medical.sdccc.TestSuite";

    private static final Logger LOG = LogManager.getLogger(XmlReportWriter.class);

    private final List<ReportData> reportData;
    private final TestRunObserver testRunObserver;
    private final ClassUtil classUtil;

    /**
     * Initializes an XmlReportWriter.
     *
     * @param reportData list of results representing test cases
     * @param classUtil utility
     * @param testRunObserver observer which contains information on validity of test run
     */
    @AssistedInject
    XmlReportWriter(@Assisted final List<ReportData> reportData,
                    final ClassUtil classUtil,
                    final TestRunObserver testRunObserver) {
        this.reportData = reportData;
        this.testRunObserver = testRunObserver;
        this.classUtil = classUtil;
    }


    protected void writeXmlReport(final Path reportsDir, final String xmlReportName, final Duration duration)
        throws XMLStreamException, IOException {

        final Path xmlFile = reportsDir.resolve("TEST-" + xmlReportName + ".xml");

        try (final Writer fileWriter = Files.newBufferedWriter(xmlFile)) {
            final XMLOutputFactory factory = XMLOutputFactory.newInstance();
            final XMLStreamWriter xmlWriter = factory.createXMLStreamWriter(fileWriter);
            writeXmlReport(xmlWriter, duration);
        }
    }

    protected void writeXmlReport(final XMLStreamWriter xmlWriter, final Duration duration)
        throws XMLStreamException {

        xmlWriter.writeStartDocument("UTF-8", "1.0");
        writeNewLine(xmlWriter);

        writeTestSuite(xmlWriter, duration);
        writeProperties(xmlWriter);

        writeTestResults(xmlWriter);
        writeInvalidTestRunTestCase(xmlWriter);

        xmlWriter.writeEndElement();
        writeNewLine(xmlWriter);
        xmlWriter.writeEndDocument();
    }

    private void writeTestSuite(final XMLStreamWriter xmlWriter, final Duration duration)
        throws XMLStreamException {

        xmlWriter.writeStartElement("testsuite");
        xmlWriter.writeAttribute("name", "SDCcc Test Run");
        xmlWriter.writeAttribute("tests", String.valueOf(reportData.size()));
        xmlWriter.writeAttribute("skipped", "0"); // skips are always failures
        xmlWriter.writeAttribute("failures", String.valueOf(countFailures()));
        xmlWriter.writeAttribute("errors", String.valueOf(countErrors()));
        xmlWriter.writeAttribute("time", formatDuration(duration));
        writeNewLine(xmlWriter);
    }

    private static String formatDuration(final Duration duration) {
        return String.format("%s.%s", duration.toSeconds(), duration.toMillisPart());
    }

    private long countFailures() {
        return reportData.stream()
            .filter(it -> it.getTestExecutionResult().getStatus() == TestExecutionResult.Status.FAILED)
            .filter(XmlReportWriter::isFailure)
            .count();
    }

    private long countErrors() {
        return reportData.stream()
            .filter(it -> it.getTestExecutionResult().getStatus() == TestExecutionResult.Status.FAILED)
            .filter(it -> !isFailure(it))
            .count()
            + reportData.stream()
            .filter(it -> it.getTestExecutionResult().getStatus() == TestExecutionResult.Status.ABORTED)
            .count();
    }

    private static void writeNewLine(final XMLStreamWriter xmlWriter) throws XMLStreamException {
        xmlWriter.writeCharacters("\n");
    }

    private static boolean isFailure(final ReportData reportDatum) {
        return reportDatum.getTestExecutionResult().getStatus() == TestExecutionResult.Status.FAILED
            && reportDatum.getTestExecutionResult().getThrowable().isPresent()
            && reportDatum.getTestExecutionResult().getThrowable().orElseThrow() instanceof AssertionError;
    }

    private void writeProperties(final XMLStreamWriter xmlWriter)
        throws XMLStreamException {

        xmlWriter.writeStartElement("properties");

        final var properties = System.getProperties();
        for (String stringPropertyName : properties.stringPropertyNames()) {
            xmlWriter.writeEmptyElement("property");
            xmlWriter.writeAttribute("name", stringPropertyName);
            xmlWriter.writeAttribute("value", properties.getProperty(stringPropertyName));
            writeNewLine(xmlWriter);
        }
        xmlWriter.writeEndElement();
        writeNewLine(xmlWriter);
    }


    private void writeTestResults(final XMLStreamWriter xmlWriter)
        throws XMLStreamException {

        for (final ReportData reportDatum : this.reportData) {
            xmlWriter.writeStartElement("testcase");
            xmlWriter.writeAttribute("name", getTestName(reportDatum));
            xmlWriter.writeAttribute("classname", getTestClassName(reportDatum));
            if (reportDatum.getTestDuration() != null) {
                xmlWriter.writeAttribute("time", formatDuration(reportDatum.getTestDuration()));
            }
            writeNewLine(xmlWriter);

            // if failure or error, write it
            writeFailureOrError(xmlWriter, reportDatum);

            if (!reportDatum.getReportEntries().isEmpty()) {
                xmlWriter.writeStartElement("system-out");

                final var transformedData = reportDatum.getReportEntries()
                    .stream()
                    .map(ReportEntry::toString)
                    .collect(Collectors.joining("\n"));

                xmlWriter.writeCData(transformedData);
                xmlWriter.writeEndElement();
                writeNewLine(xmlWriter);
            }

            xmlWriter.writeStartElement("display-name");
            xmlWriter.writeCData(reportDatum.getTestIdentifier().getDisplayName());
            xmlWriter.writeEndElement();
            writeNewLine(xmlWriter);

            xmlWriter.writeStartElement("test-description");
            xmlWriter.writeCData(getTestDescription(reportDatum));
            xmlWriter.writeEndElement();
            writeNewLine(xmlWriter);

            xmlWriter.writeStartElement("unique-id");
            xmlWriter.writeCData(reportDatum.getTestIdentifier().getUniqueId());
            xmlWriter.writeEndElement();
            writeNewLine(xmlWriter);

            xmlWriter.writeStartElement("test-identifier");
            xmlWriter.writeCData(getSDCccTestIdentifier(reportDatum));
            xmlWriter.writeEndElement();
            writeNewLine(xmlWriter);

            xmlWriter.writeEndElement();
            writeNewLine(xmlWriter);
        }
    }

    /**
     * Writes an additional test case into the test result if the test run was marked as invalid.
     *
     * @param xmlWriter to write the test case into
     * @throws XMLStreamException on xml writing errors
     */
    private void writeInvalidTestRunTestCase(final XMLStreamWriter xmlWriter)
        throws XMLStreamException {
        if (testRunObserver.isInvalid()) {
            xmlWriter.writeStartElement("testcase");
            xmlWriter.writeAttribute("name", INVALID_TEST_RUN_TEST_NAME);
            xmlWriter.writeAttribute("classname", INVALID_TEST_RUN_CLASS_NAME);
            xmlWriter.writeAttribute("time", "0");
            writeNewLine(xmlWriter);

            xmlWriter.writeStartElement("error");
            xmlWriter.writeAttribute("message", "SDCcc test run was marked as invalid");
            xmlWriter.writeAttribute("type", "InvalidTestRun");
            xmlWriter.writeCData(String.format(
                "SDCcc test run was marked as invalid for the following reasons:%s",
                String.join("\n- ", testRunObserver.getReasons())
            ));
            xmlWriter.writeEndElement();
            writeNewLine(xmlWriter);

            xmlWriter.writeStartElement("display-name");
            xmlWriter.writeCData("SDCcc Test Run Validity");
            xmlWriter.writeEndElement();
            writeNewLine(xmlWriter);

            xmlWriter.writeStartElement("test-description");
            xmlWriter.writeCData("SDCcc Test Run Validity");
            xmlWriter.writeEndElement();
            writeNewLine(xmlWriter);

            xmlWriter.writeStartElement("unique-id");
            xmlWriter.writeCData("[class:SDCccTestRunValidity]");
            xmlWriter.writeEndElement();
            writeNewLine(xmlWriter);

            xmlWriter.writeStartElement("test-identifier");
            xmlWriter.writeCData("SDCccTestRunValidity");
            xmlWriter.writeEndElement();
            writeNewLine(xmlWriter);

            xmlWriter.writeEndElement();
            writeNewLine(xmlWriter);
        }
    }

    private static void writeFailureOrError(final XMLStreamWriter xmlWriter, final ReportData reportDatum)
        throws XMLStreamException {
        if (reportDatum.getTestExecutionResult().getStatus() == TestExecutionResult.Status.SUCCESSFUL) {
            return;
        }

        xmlWriter.writeStartElement(isFailure(reportDatum) ? "failure" : "error");
        if (reportDatum.getTestExecutionResult().getThrowable().isPresent()) {
            final var err = reportDatum.getTestExecutionResult().getThrowable().orElseThrow();
            if (err.getMessage() != null) {
                xmlWriter.writeAttribute("message", err.getMessage());
            }
            xmlWriter.writeAttribute("type", err.getClass().getCanonicalName());
            xmlWriter.writeCData(ExceptionUtils.getStackTrace(err));
        }
        xmlWriter.writeEndElement();
        writeNewLine(xmlWriter);
    }

    private static String getTestName(final ReportData reportDatum) {
        return reportDatum.getTestIdentifier().getLegacyReportingName();
    }

    private static String getTestClassName(final ReportData reportDatum) throws XMLStreamException {
        // add content from TestIdentifier annotation if present
        final var testIdentifier = reportDatum.getTestIdentifier();
        if (testIdentifier.getSource().isPresent()) {
            final var src = testIdentifier.getSource().orElseThrow();
            if (src instanceof MethodSource) {
                return ((MethodSource) src).getClassName();
            }
        }
        throw new XMLStreamException(
            String.format("Could not determine class name for %s", testIdentifier.getUniqueId())
        );
    }

    private String getTestDescription(final ReportData reportDatum) throws XMLStreamException {
        return getAnnotationValue(TestDescription.class, reportDatum).value();
    }

    private String getSDCccTestIdentifier(final ReportData reportDatum) throws XMLStreamException {
        return getAnnotationValue(
            com.draeger.medical.sdccc.tests.annotations.TestIdentifier.class,
            reportDatum
        ).value();
    }

    private <T extends Annotation> T getAnnotationValue(
        final Class<T> annotationClass,
        final ReportData reportDatum
    ) throws XMLStreamException {
        final var testIdentifier = reportDatum.getTestIdentifier();
        if (testIdentifier.getSource().isPresent()) {
            final var src = testIdentifier.getSource().orElseThrow();
            if (src instanceof MethodSource) {
                return getAnnotationFromMethodSource(annotationClass, (MethodSource) src, testIdentifier);
            }
        }
        throw new XMLStreamException(String.format(
            MISSING_ANNOTATION_TEXT, testIdentifier.getUniqueId(), annotationClass.getSimpleName()
        ));
    }

    private <T extends Annotation> T getAnnotationFromMethodSource(
        final Class<T> annotation,
        final MethodSource src,
        final TestIdentifier testIdentifier
    ) throws XMLStreamException {
        final var className = src.getClassName();
        final var methodName = src.getMethodName();
        try {
            final var method = classUtil.getMethod(className, methodName);
            final var description = method.getAnnotation(annotation);
            if (description != null) {
                return description;
            }
        } catch (final ClassNotFoundException | NoSuchMethodException e) {
            LOG.error(
                "Error while retrieving {} annotation content from method {}",
                annotation.getSimpleName(),
                methodName, e
            );
            throw new XMLStreamException(e);
        }
        throw new XMLStreamException(String.format(
            MISSING_ANNOTATION_TEXT,
            testIdentifier.getUniqueId(), annotation.getSimpleName()
        ));
    }
}
