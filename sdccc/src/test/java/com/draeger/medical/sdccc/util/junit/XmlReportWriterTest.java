/*
 * This Source Code Form is subject to the terms of the "SDCcc non-commercial use license".
 *
 * Copyright (C) 2025 Draegerwerk AG & Co. KGaA
 */

package com.draeger.medical.sdccc.util.junit;

import static com.draeger.medical.sdccc.util.junit.XmlReportWriter.INVALID_TEST_RUN_TEST_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.w3c.dom.Node.ELEMENT_NODE;

import com.draeger.medical.sdccc.messages.MessageStorage;
import com.draeger.medical.sdccc.tests.annotations.TestDescription;
import com.draeger.medical.sdccc.util.TestRunObserver;
import com.draeger.medical.sdccc.util.XPathExtractor;
import com.draeger.medical.sdccc.util.junit.util.ClassUtil;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.TestTag;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.reporting.ReportEntry;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestIdentifier;
import org.opentest4j.AssertionFailedError;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Unit tests for the customized JUnit {@linkplain XmlReportWriter}.
 */
public class XmlReportWriterTest {

    private static final String DISPLAY_NAME = "display-name";
    private static final String TEST_DESCRIPTION = "test-description";
    private static final String TEST_IDENTIFIER = "test-identifier";
    private static final String UNIQUE_ID = "unique-id";
    private static final String TEST_PACKAGE = "all.of.my.mock";
    private static final String TEST_CASE_PREFIX = "test";
    protected static final String UNIQUE_ID_PREFIX = "[class:" + TEST_PACKAGE + "." + TEST_CASE_PREFIX;
    private static final String DEFAULT_DISPLAY_NAME = "Ω≈ç√∫˜µ≤≥÷";
    private static final String DEFAULT_LEGACY_REPORTING_NAME = "the_legacy_reporting_name";
    private static final String MOCK_METHOD_DESCRIPTION = "Mock method test description";
    private static final String MOCK_METHOD_IDENTIFIER = "SWPDM.R1234";

    private static final String DEFAULT_REPORT_ENTRY_KEY = "Name of an album";
    private static final String DEFAULT_REPORT_ENTRY_VALUE = "⋋≒ℵ≓⋌ ⋶ℵ⊖ʂ⊖µɲɖɾѦςҟ ⌊∤þɾѦɾџ ・∴・ 1";

    private List<ReportData> data = new ArrayList<>();

    private Map<String, TestDescriptor> stringToIdentifier;
    private ClassUtil classUtil;

    // uniqueId, reason
    private Map<String, TestExecutionResult> failedTests;
    private Map<String, TestExecutionResult> errorTests;

    protected static TestDescriptor createMockedTestDescriptor(
            final String uniqueId,
            final String displayName,
            @Nullable final TestSource source,
            final Set<TestTag> tags,
            final TestDescriptor.Type type,
            @Nullable final TestDescriptor parent,
            final String legacyReportingName) {

        final Optional<TestSource> src = Optional.ofNullable(source);
        final Optional<TestDescriptor> parentId = Optional.ofNullable(parent);

        final TestDescriptor descriptor = mock(TestDescriptor.class);

        when(descriptor.getUniqueId()).thenReturn(UniqueId.parse(uniqueId));
        when(descriptor.getDisplayName()).thenReturn(displayName);
        when(descriptor.getSource()).thenReturn(src);
        when(descriptor.getTags()).thenReturn(tags);
        when(descriptor.getParent()).thenReturn(parentId);
        when(descriptor.getLegacyReportingName()).thenReturn(legacyReportingName);
        when(descriptor.getType()).thenReturn(type);

        return descriptor;
    }

    TestExecutionResult createFailedTest(final TestDescriptor test) {
        final var result = TestExecutionResult.failed(new AssertionError(
                String.format("Assertion for %s failed", test.getUniqueId().toString())));
        failedTests.put(test.getUniqueId().toString(), result);
        return result;
    }

    TestExecutionResult createFailedTestWithBadCharSequence(final TestDescriptor test) {
        final var result = TestExecutionResult.failed(new AssertionFailedError(String.format(
                "Assertion for %s failed. The sequence ']]>' may be included in the content only if it marks the end"
                        + " of a CDATA section. Oh no, now I wrote ]]> and the CDATA section is not finished yet."
                        + " I hope my XML parser can handle it and does not produce any errors or throws exceptions,"
                        + " since ]]> usually marks the end of a CDATA section, as I mentioned.",
                test.getUniqueId().toString())));
        failedTests.put(test.getUniqueId().toString(), result);
        return result;
    }

    TestExecutionResult createErrorTest(final TestDescriptor test) {
        final var result = TestExecutionResult.failed(new RuntimeException("Other exception occurred"));
        errorTests.put(test.getUniqueId().toString(), result);
        return result;
    }

    @BeforeEach
    void setUp() throws NoSuchMethodException, ClassNotFoundException {
        classUtil = mock(ClassUtil.class);

        data = new ArrayList<>();

        stringToIdentifier = new HashMap<>();
        failedTests = new HashMap<>();
        errorTests = new HashMap<>();

        // create some test cases
        createTestCases();

        assertFalse(failedTests.isEmpty());
        assertFalse(errorTests.isEmpty());
    }

    private void createTestCases() throws NoSuchMethodException, ClassNotFoundException {
        for (int i = 0; i < 10; i++) {
            final String uniqueId = UNIQUE_ID_PREFIX + i + "]";

            final TestSource src = MethodSource.from(TEST_PACKAGE, TEST_CASE_PREFIX + i);

            final Set<TestTag> tags = new HashSet<>();
            final TestDescriptor child = createMockedTestDescriptor(
                    uniqueId,
                    DEFAULT_DISPLAY_NAME + " " + i,
                    src,
                    tags,
                    TestDescriptor.Type.TEST,
                    null,
                    DEFAULT_LEGACY_REPORTING_NAME + i);

            stringToIdentifier.put(uniqueId, child);

            final var mockMethod = this.getClass().getDeclaredMethod("mockMethod");
            doReturn(mockMethod).when(classUtil).getMethod(TEST_PACKAGE, TEST_CASE_PREFIX + i);

            // create each kind of test (successful, failed, error)
            final var testType = i % 3;
            final TestExecutionResult testExecutionResult;
            switch (testType) {
                case 0:
                    // success
                    testExecutionResult = TestExecutionResult.successful();
                    break;
                case 1:
                    // fail
                    testExecutionResult = createFailedTest(child);
                    break;
                case 2:
                    // error
                    testExecutionResult = createErrorTest(child);
                    break;
                default:
                    throw new RuntimeException("Unhandled test type");
            }

            data.add(new ReportData(
                    TestIdentifier.from(child),
                    Duration.ofSeconds(i),
                    testExecutionResult,
                    List.of(ReportEntry.from(DEFAULT_REPORT_ENTRY_KEY, DEFAULT_REPORT_ENTRY_VALUE))));
        }
    }

    private void setupBadCharSequenceTest() throws Exception {
        // reset old setup
        data.clear();
        stringToIdentifier = new HashMap<>();
        failedTests = new HashMap<>();
        errorTests = new HashMap<>();

        // build and add report data
        final String uniqueId = UNIQUE_ID_PREFIX + "badCharSequence" + "]";
        final TestSource src = MethodSource.from(TEST_PACKAGE, TEST_CASE_PREFIX + "badCharSequence");
        final Set<TestTag> tags = new HashSet<>();

        final TestDescriptor testDescriptor = createMockedTestDescriptor(
                uniqueId,
                DEFAULT_DISPLAY_NAME + " " + "badCharSequence",
                src,
                tags,
                TestDescriptor.Type.TEST,
                null,
                DEFAULT_LEGACY_REPORTING_NAME + "badCharSequence");

        data.add(new ReportData(
                TestIdentifier.from(testDescriptor),
                Duration.ofSeconds(11),
                createFailedTestWithBadCharSequence(testDescriptor),
                List.of(ReportEntry.from(DEFAULT_REPORT_ENTRY_KEY, DEFAULT_REPORT_ENTRY_VALUE))));

        final var mockMethod = this.getClass().getNestHost().getDeclaredMethod("mockMethod");
        doReturn(mockMethod).when(classUtil).getMethod(TEST_PACKAGE, TEST_CASE_PREFIX + "badCharSequence");
        stringToIdentifier.put(uniqueId, testDescriptor);
    }

    /**
     * Tests whether the XmlReportWriter can process the string sequence that marks the end of a CDATA section
     * if it also occurs in the content.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testBadCharSequence() throws Exception {
        setupBadCharSequenceTest();

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final var observer = mock(TestRunObserver.class);
        final var messageStorage = mock(MessageStorage.class);
        try (final OutputStreamWriter outputStreamWriter = new OutputStreamWriter(baos, StandardCharsets.UTF_8)) {
            final var factory = XMLOutputFactory.newInstance();
            final var xmlWriter = factory.createXMLStreamWriter(outputStreamWriter);

            final var writer = new XmlReportWriter(data, classUtil, observer, messageStorage);
            writer.writeXmlReport(xmlWriter, Duration.ofSeconds(1));
        }

        // now look whether we find our test cases and their descriptions
        final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(baos.toByteArray());

        final DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        final DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        final Document report = dBuilder.parse(byteArrayInputStream);

        // schema verify the report
        performSchemaValidation(report);

        final Node root = report.getChildNodes().item(0);

        final Map<String, TestContainer> parsingResult = parseReportToTestCases(root);
        parsingResult.forEach((key, value) -> {
            assertNotNull(value.uniqueId);

            final var testCaseData = data.stream()
                    .filter(it -> value.uniqueId.equals(it.testIdentifier().getUniqueId()))
                    .findFirst()
                    .orElseThrow();

            assertNotNull(value.testDescription);
            assertFalse(value.testDescription.isBlank());
            assertEquals(MOCK_METHOD_DESCRIPTION, value.testDescription);

            assertNotNull(value.testIdentifier);
            assertFalse(value.testIdentifier.isBlank());
            assertEquals(MOCK_METHOD_DESCRIPTION, value.testDescription);

            assertEquals(testCaseData.testIdentifier().getDisplayName(), value.displayName());
            assertEquals(testCaseData.testIdentifier().getUniqueId(), value.uniqueId());
            assertEquals(testCaseData.testIdentifier().getLegacyReportingName(), value.name());

            for (ReportEntry reportEntry : testCaseData.reportEntries()) {
                assertTrue(
                        value.systemOut().contains(reportEntry.toString()),
                        String.format("%s does not occur in %s", reportEntry, value.message()));
            }

            assertNotNull(testCaseData.testDuration());
            assertEquals(
                    String.valueOf(testCaseData.testDuration().toSeconds()),
                    value.time().split("\\.")[0]);
            assertEquals(
                    String.valueOf(testCaseData.testDuration().toMillisPart()),
                    value.time().split("\\.")[1]);

            // verify remaining attributes match
            final var identifier = stringToIdentifier.get(key);
            assertEquals(identifier.getDisplayName(), value.displayName);
            assertEquals(identifier.getUniqueId().toString(), value.uniqueId);

            if (failedTests.containsKey(value.uniqueId)) {
                assertEquals("failure", value.failureType);
                assertEquals(
                        failedTests
                                .get(value.uniqueId)
                                .getThrowable()
                                .orElseThrow()
                                .getMessage(),
                        value.message);
            }
            if (errorTests.containsKey(value.uniqueId)) {
                assertEquals("error", value.failureType);
                assertEquals(
                        errorTests
                                .get(value.uniqueId)
                                .getThrowable()
                                .orElseThrow()
                                .getMessage(),
                        value.message);
            }
        });
    }

    /**
     * Tests whether the expected elements as well as additional elements provided by SDCcc are written into the xml
     * report.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testAdditionalTags() throws Exception {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final var observer = mock(TestRunObserver.class);
        final var messageStorage = mock(MessageStorage.class);
        try (final OutputStreamWriter outputStreamWriter = new OutputStreamWriter(baos, StandardCharsets.UTF_8)) {
            final var factory = XMLOutputFactory.newInstance();
            final var xmlWriter = factory.createXMLStreamWriter(outputStreamWriter);

            final var writer = new XmlReportWriter(data, classUtil, observer, messageStorage);
            writer.writeXmlReport(xmlWriter, Duration.ofSeconds(1));
        }

        // now look whether we find our test cases and their descriptions
        final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(baos.toByteArray());

        final DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        final DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        final Document report = dBuilder.parse(byteArrayInputStream);

        // schema verify the report
        performSchemaValidation(report);

        final Node root = report.getChildNodes().item(0);

        final Map<String, TestContainer> parsingResult = parseReportToTestCases(root);
        parsingResult.forEach((key, value) -> {
            assertNotNull(value.uniqueId);

            final var testCaseData = data.stream()
                    .filter(it -> value.uniqueId.equals(it.testIdentifier().getUniqueId()))
                    .findFirst()
                    .orElseThrow();

            assertNotNull(value.testDescription);
            assertFalse(value.testDescription.isBlank());
            assertEquals(MOCK_METHOD_DESCRIPTION, value.testDescription);

            assertNotNull(value.testIdentifier);
            assertFalse(value.testIdentifier.isBlank());
            assertEquals(MOCK_METHOD_DESCRIPTION, value.testDescription);

            assertEquals(testCaseData.testIdentifier().getDisplayName(), value.displayName());
            assertEquals(testCaseData.testIdentifier().getUniqueId(), value.uniqueId());
            assertEquals(testCaseData.testIdentifier().getLegacyReportingName(), value.name());

            for (ReportEntry reportEntry : testCaseData.reportEntries()) {
                assertTrue(
                        value.systemOut().contains(reportEntry.toString()),
                        String.format("%s does not occur in %s", reportEntry, value.message()));
            }

            assertNotNull(testCaseData.testDuration());
            assertEquals(
                    String.valueOf(testCaseData.testDuration().toSeconds()),
                    value.time().split("\\.")[0]);
            assertEquals(
                    String.valueOf(testCaseData.testDuration().toMillisPart()),
                    value.time().split("\\.")[1]);

            // verify remaining attributes match
            final var identifier = stringToIdentifier.get(key);
            assertEquals(identifier.getDisplayName(), value.displayName);
            assertEquals(identifier.getUniqueId().toString(), value.uniqueId);

            if (failedTests.containsKey(value.uniqueId)) {
                assertEquals("failure", value.failureType);
                assertEquals(
                        failedTests
                                .get(value.uniqueId)
                                .getThrowable()
                                .orElseThrow()
                                .getMessage(),
                        value.message);
            }
            if (errorTests.containsKey(value.uniqueId)) {
                assertEquals("error", value.failureType);
                assertEquals(
                        errorTests
                                .get(value.uniqueId)
                                .getThrowable()
                                .orElseThrow()
                                .getMessage(),
                        value.message);
            }
        });
    }

    /**
     * Tests whether a missing {@linkplain TestDescription} causes the writer to fail.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testMissingTestDescription() throws Exception {
        final var annotationClass = TestDescription.class;
        // add an invalid test case
        final String testName = "MissingDescription";
        final String uniqueId = UNIQUE_ID_PREFIX + testName + "]";

        final TestSource src = MethodSource.from(TEST_PACKAGE, TEST_CASE_PREFIX + testName);

        final TestDescriptor child = createMockedTestDescriptor(
                uniqueId,
                DEFAULT_DISPLAY_NAME + " " + testName,
                src,
                Collections.emptySet(),
                TestDescriptor.Type.TEST,
                null,
                DEFAULT_LEGACY_REPORTING_NAME + testName);

        stringToIdentifier.put(uniqueId, child);
        final var mockMethod = this.getClass().getDeclaredMethod("mockMethodNoDescription");
        doReturn(mockMethod).when(classUtil).getMethod(TEST_PACKAGE, TEST_CASE_PREFIX + testName);

        data.add(new ReportData(
                TestIdentifier.from(child),
                Duration.ofSeconds(1),
                TestExecutionResult.successful(),
                Collections.emptyList()));

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (final OutputStreamWriter outputStreamWriter = new OutputStreamWriter(baos, StandardCharsets.UTF_8)) {
            final var factory = XMLOutputFactory.newInstance();
            final var xmlWriter = factory.createXMLStreamWriter(outputStreamWriter);

            final var writer =
                    new XmlReportWriter(data, classUtil, mock(TestRunObserver.class), mock(MessageStorage.class));
            final var error = assertThrows(
                    XMLStreamException.class,
                    () -> writer.writeXmlReport(xmlWriter, Duration.ofSeconds(1)),
                    "XmlReportWriter accepted test case without TestDescription annotation");
            final var expectedMessage =
                    String.format(XmlReportWriter.MISSING_ANNOTATION_TEXT, uniqueId, annotationClass.getSimpleName());
            assertTrue(error.getMessage().contains(expectedMessage));
        }
    }

    /**
     * Tests whether a missing {@linkplain com.draeger.medical.sdccc.tests.annotations.TestIdentifier}
     * causes the writer to fail.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testMissingTestIdentifier() throws Exception {
        final var annotationClass = com.draeger.medical.sdccc.tests.annotations.TestIdentifier.class;
        // add an invalid test case
        final String testName = "MissingIdentifier";
        final String uniqueId = UNIQUE_ID_PREFIX + testName + "]";

        final TestSource src = MethodSource.from(TEST_PACKAGE, TEST_CASE_PREFIX + testName);

        final TestDescriptor child = createMockedTestDescriptor(
                uniqueId,
                DEFAULT_DISPLAY_NAME + " " + testName,
                src,
                Collections.emptySet(),
                TestDescriptor.Type.TEST,
                null,
                DEFAULT_LEGACY_REPORTING_NAME + testName);

        stringToIdentifier.put(uniqueId, child);

        data.add(new ReportData(
                TestIdentifier.from(child),
                Duration.ofSeconds(1),
                TestExecutionResult.successful(),
                Collections.emptyList()));

        final var mockMethod = this.getClass().getDeclaredMethod("mockMethodNoIdentifier");
        doReturn(mockMethod).when(classUtil).getMethod(TEST_PACKAGE, TEST_CASE_PREFIX + testName);

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (final OutputStreamWriter outputStreamWriter = new OutputStreamWriter(baos, StandardCharsets.UTF_8)) {
            final var factory = XMLOutputFactory.newInstance();
            final var xmlWriter = factory.createXMLStreamWriter(outputStreamWriter);

            final var writer =
                    new XmlReportWriter(data, classUtil, mock(TestRunObserver.class), mock(MessageStorage.class));
            final var error = assertThrows(
                    XMLStreamException.class,
                    () -> writer.writeXmlReport(xmlWriter, Duration.ofSeconds(1)),
                    "XmlReportWriter accepted test case without TestIdentifier annotation");
            final var expectedMessage =
                    String.format(XmlReportWriter.MISSING_ANNOTATION_TEXT, uniqueId, annotationClass.getSimpleName());
            assertTrue(error.getMessage().contains(expectedMessage));
        }
    }

    /**
     * Tests whether an invalid test run has the additional test result marking it as invalid in the report.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testInvalidRunHasAdditionalFailedTestCase() throws Exception {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final OutputStreamWriter outputStreamWriter = new OutputStreamWriter(baos, StandardCharsets.UTF_8);
        final var observer = mock(TestRunObserver.class);
        when(observer.isInvalid()).thenReturn(true);

        final var factory = XMLOutputFactory.newInstance();
        final var xmlWriter = factory.createXMLStreamWriter(outputStreamWriter);

        final var writer = new XmlReportWriter(data, classUtil, observer, mock(MessageStorage.class));
        writer.writeXmlReport(xmlWriter, Duration.ofSeconds(1));

        outputStreamWriter.close();

        // now look whether we find our test cases and their descriptions
        final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(baos.toByteArray());

        final DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        final DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        final Document report = dBuilder.parse(byteArrayInputStream);

        // schema verify the report
        performSchemaValidation(report);

        final Node root = report.getChildNodes().item(0);

        final var testSuiteExtractor = new XPathExtractor("//testsuite");

        final var suites = testSuiteExtractor.extractFrom(root);
        assertEquals(1, suites.size());

        // find invalidated test
        final var testCaseExtractor = new XPathExtractor("//testcase");
        final var tests =
                testCaseExtractor.extractFrom(suites.stream().findFirst().orElseThrow());

        final var invalidTestRunTest = tests.stream()
                .filter(it -> INVALID_TEST_RUN_TEST_NAME.equals(
                        it.getAttributes().getNamedItem("name").getNodeValue()))
                .findFirst();

        assertTrue(invalidTestRunTest.isPresent());
    }

    /**
     * Tests whether a valid test run has the attribute marking it as valid in the report.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testValidRunHasPositiveAttribute() throws Exception {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final OutputStreamWriter outputStreamWriter = new OutputStreamWriter(baos, StandardCharsets.UTF_8);
        final var observer = mock(TestRunObserver.class);
        when(observer.isInvalid()).thenReturn(false);

        final var factory = XMLOutputFactory.newInstance();
        final var xmlWriter = factory.createXMLStreamWriter(outputStreamWriter);

        final var writer = new XmlReportWriter(data, classUtil, observer, mock(MessageStorage.class));
        writer.writeXmlReport(xmlWriter, Duration.ofSeconds(1));

        outputStreamWriter.close();

        // now look whether we find our test cases and their descriptions
        final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(baos.toByteArray());

        final DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        final DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        final Document report = dBuilder.parse(byteArrayInputStream);

        // schema verify the report
        performSchemaValidation(report);

        final Node root = report.getChildNodes().item(0);

        final var testSuiteExtractor = new XPathExtractor("//testsuite");

        final var suites = testSuiteExtractor.extractFrom(root);
        assertEquals(1, suites.size());

        // find invalidated test
        final var testCaseExtractor = new XPathExtractor("//testcase");
        final var tests =
                testCaseExtractor.extractFrom(suites.stream().findFirst().orElseThrow());

        final var invalidTestRunTest = tests.stream()
                .filter(it -> INVALID_TEST_RUN_TEST_NAME.equals(
                        it.getAttributes().getNamedItem("name").getNodeValue()))
                .findFirst();

        assertTrue(invalidTestRunTest.isEmpty());
    }

    Map<String, TestContainer> parseReportToTestCases(final Node root) {

        final Map<String, TestContainer> testCases = new HashMap<>();

        final NodeList children = root.getChildNodes();
        Node child;
        for (int i = 0; i < children.getLength(); i++) {

            child = children.item(i);
            // match testcase and see if we care about it
            if (child.getNodeType() == 1 && "testcase".equals(child.getNodeName())) {

                createTestCaseContainer(testCases, child);
            }
        }

        return testCases;
    }

    private void createTestCaseContainer(final Map<String, TestContainer> testCases, final Node child) {
        String systemOut = "";
        String displayName = "";
        String uniqueId = "";
        String message = "";
        String textContent = "";
        final String tags = "";
        String testDescription = "";
        String failureType = null;
        String testIdentifier = "";
        final String name = child.getAttributes().getNamedItem("name").getNodeValue();
        final String time = child.getAttributes().getNamedItem("time").getNodeValue();

        for (int j = 0; j < child.getChildNodes().getLength(); j++) {

            final Node nephew = child.getChildNodes().item(j);

            final String childNodeName;

            if (nephew.getNodeType() == ELEMENT_NODE) {
                childNodeName = nephew.getNodeName();
            } else {
                continue;
            }

            if ("system-out".equals(childNodeName)) {
                systemOut = nephew.getTextContent();
            } else if (DISPLAY_NAME.equals(childNodeName)) {
                displayName = nephew.getTextContent();
            } else if (TEST_DESCRIPTION.equals(childNodeName)) {
                testDescription = nephew.getTextContent();
            } else if (TEST_IDENTIFIER.equals(childNodeName)) {
                testIdentifier = nephew.getTextContent();
            } else if (UNIQUE_ID.equals(childNodeName)) {
                uniqueId = nephew.getTextContent();
            } else if ("skipped".equals(childNodeName)
                    || "error".equals(childNodeName)
                    || "failure".equals(childNodeName)) {
                failureType = childNodeName;
                textContent = nephew.getTextContent();
                final var messageAttribute = nephew.getAttributes().getNamedItem("message");
                if (messageAttribute != null) {
                    message = messageAttribute.getTextContent();
                }
            }
        }

        final TestContainer tc = new TestContainer(
                systemOut,
                displayName,
                uniqueId,
                message,
                textContent,
                tags,
                testDescription,
                failureType,
                testIdentifier,
                name,
                time);
        testCases.put(uniqueId, tc);
    }

    void performSchemaValidation(final Document document) throws SAXException, IOException {

        // create a SchemaFactory capable of understanding xml schemas
        final SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);

        // load a schema, represented by a Schema instance
        final Source schemaFile = new StreamSource(getClass().getResourceAsStream("sdccc_schema.xsd"));
        final Schema schema = factory.newSchema(schemaFile);

        // validate the DOM tree
        final Validator validator = schema.newValidator();
        validator.validate(new DOMSource(document));
    }

    // these methods are used for mocking purposes, ignore
    // this is primarily because {@linkplain Method} is not mockable, as it is declared final
    @TestDescription(MOCK_METHOD_DESCRIPTION)
    @com.draeger.medical.sdccc.tests.annotations.TestIdentifier(MOCK_METHOD_IDENTIFIER)
    void mockMethod() {}

    @com.draeger.medical.sdccc.tests.annotations.TestIdentifier(MOCK_METHOD_IDENTIFIER)
    void mockMethodNoDescription() {}

    @TestDescription(MOCK_METHOD_DESCRIPTION)
    void mockMethodNoIdentifier() {}

    record TestContainer(
            String systemOut,
            String displayName,
            String uniqueId,
            String message,
            String textContent,
            String tags,
            String testDescription,
            @Nullable String failureType,
            String testIdentifier,
            String name,
            String time) {}
}
