/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2023 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.tests.biceps.invariant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.draeger.medical.biceps.model.participant.ComponentActivation;
import com.draeger.medical.biceps.model.participant.MetricAvailability;
import com.draeger.medical.biceps.model.participant.MetricCategory;
import com.draeger.medical.dpws.soap.model.Envelope;
import com.draeger.medical.sdccc.configuration.TestSuiteConfig;
import com.draeger.medical.sdccc.marshalling.MarshallingUtil;
import com.draeger.medical.sdccc.messages.Message;
import com.draeger.medical.sdccc.messages.MessageStorage;
import com.draeger.medical.sdccc.sdcri.testclient.TestClient;
import com.draeger.medical.sdccc.sdcri.testclient.TestClientUtil;
import com.draeger.medical.sdccc.tests.InjectorTestBase;
import com.draeger.medical.sdccc.tests.test_util.InjectorUtil;
import com.draeger.medical.sdccc.tests.util.ManipulationParameterUtil;
import com.draeger.medical.sdccc.tests.util.NoTestData;
import com.draeger.medical.sdccc.util.CertificateUtil;
import com.draeger.medical.sdccc.util.Constants;
import com.draeger.medical.sdccc.util.MdibBuilder;
import com.draeger.medical.sdccc.util.MessageBuilder;
import com.draeger.medical.sdccc.util.MessageStorageUtil;
import com.draeger.medical.t2iapi.ResponseTypes;
import com.google.common.collect.ArrayListMultimap;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Names;
import jakarta.xml.bind.JAXBElement;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.security.cert.CertificateException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.somda.sdc.common.guice.AbstractConfigurationModule;
import org.somda.sdc.dpws.CommunicationLog;
import org.somda.sdc.dpws.helper.JaxbMarshalling;
import org.somda.sdc.dpws.soap.CommunicationContext;
import org.somda.sdc.dpws.soap.HttpApplicationInfo;
import org.somda.sdc.dpws.soap.SoapMarshalling;
import org.somda.sdc.dpws.soap.TransportInfo;
import org.somda.sdc.glue.common.ActionConstants;

/**
 * Unit test for the BICEPS {@linkplain InvariantParticipantModelStatePartTest}.
 */
public class InvariantParticipantModelStatePartTestTest {

    private static final String VMD_HANDLE = "someVmd";
    private static final String CHANNEL_HANDLE = "someChannel";
    private static final String MSRMT_METRIC_HANDLE = "someMsrmtStringMetric";
    private static final String MSRMT_METRIC_HANDLE2 = "someMsrmtStringMetric2";
    private static final String RTSA_METRIC_HANDLE = "someRealTimeSampleArrayMetric";
    private static final String RTSA_METRIC_HANDLE2 = "someRealTimeSampleArrayMetric2";
    private static final String SET_METRIC_HANDLE = "someSetStringMetric";
    private static final String SET_METRIC_HANDLE2 = "someSetStringMetric2";
    private static final String CLC_METRIC_HANDLE = "someClcStringMetric";
    private static final String CLC_METRIC_HANDLE2 = "someClcStringMetric2";
    private static final String SOME_NON_EXISTENT_HANDLE = "someNonExistentHandle";
    private static final String SEQUENCE_ID = MdibBuilder.DEFAULT_SEQUENCE_ID;

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    private static final long TIMESTAMP_START = 1000;
    private static final long TIMESTAMP_START2 = 1200;
    private static final long TIMESTAMP_FINISH = 2000;
    private static final long TIMESTAMP_FINISH2 = 1800;
    private static final long TIMESTAMP_IN_INTERVAL = 1500;
    private static final long TIMESTAMP_IN_INTERVAL2 = 1550;
    private static MessageStorageUtil messageStorageUtil;
    private static MdibBuilder mdibBuilder;
    private static MessageBuilder messageBuilder;
    private static com.draeger.medical.sdccc.marshalling.SoapMarshalling marshalling2;
    private MessageStorage storage;
    private InvariantParticipantModelStatePartTest testClass;
    private JaxbMarshalling baseMarshalling;
    private SoapMarshalling marshalling;
    private DatatypeFactory datatypeFactory;

    private long buffer;

    @BeforeAll
    static void setupMarshalling() {
        final Injector marshallingInjector = MarshallingUtil.createMarshallingTestInjector(true);
        messageStorageUtil = marshallingInjector.getInstance(MessageStorageUtil.class);
        mdibBuilder = marshallingInjector.getInstance(MdibBuilder.class);
        messageBuilder = marshallingInjector.getInstance(MessageBuilder.class);
        marshalling2 = marshallingInjector.getInstance(com.draeger.medical.sdccc.marshalling.SoapMarshalling.class);
    }

    @BeforeEach
    void setUp() throws IOException, TimeoutException, DatatypeConfigurationException {
        final TestClient mockClient = mock(TestClient.class);
        when(mockClient.isClientRunning()).thenReturn(true);

        final Injector injector = InjectorUtil.setupInjector(
                new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(TestClient.class).toInstance(mockClient);
                    }
                },
                new AbstractConfigurationModule() {
                    @Override
                    protected void defaultConfigure() {
                        bind(TestSuiteConfig.TEST_BICEPS_547_TIME_INTERVAL, long.class, 2L);
                    }
                });
        InjectorTestBase.setInjector(injector);

        final var riInjector = TestClientUtil.createClientInjector();
        when(mockClient.getInjector()).thenReturn(riInjector);

        baseMarshalling = riInjector.getInstance(JaxbMarshalling.class);
        baseMarshalling.startAsync().awaitRunning(DEFAULT_TIMEOUT);

        marshalling = riInjector.getInstance(SoapMarshalling.class);
        marshalling.startAsync().awaitRunning(DEFAULT_TIMEOUT);

        storage = injector.getInstance(MessageStorage.class);

        datatypeFactory = DatatypeFactory.newInstance();

        buffer = TimeUnit.NANOSECONDS.convert(
                injector.getInstance(Key.get(long.class, Names.named(TestSuiteConfig.TEST_BICEPS_547_TIME_INTERVAL))),
                TimeUnit.SECONDS);
        testClass = new InvariantParticipantModelStatePartTest();
        testClass.setUp();
    }

    @AfterEach
    void tearDown() throws TimeoutException {
        baseMarshalling.stopAsync().awaitTerminated(DEFAULT_TIMEOUT);
        marshalling.stopAsync().awaitTerminated(DEFAULT_TIMEOUT);
        storage.close();
    }

    /**
     * This test ensures that the tests for biceps 547 do not ignore manipulation parameters again and lead to
     * false positive test results.
     * This test has erroneously not triggered any exception before.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement547NotIgnoringParameters() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID);
        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);

        final var parameters = ManipulationParameterUtil.buildMetricStatusManipulationParameterData(
                MdibBuilder.DEFAULT_SEQUENCE_ID,
                MSRMT_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.MSRMT,
                // Component activation should be ON to be relevant for testRequirement54700.
                org.somda.sdc.biceps.model.participant.ComponentActivation.OFF);
        messageStorageUtil.addManipulation(
                storage,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                ResponseTypes.Result.RESULT_SUCCESS,
                "", // not used by test
                Constants.MANIPULATION_NAME_SET_METRIC_STATUS,
                parameters);

        final var metricReport = buildMetricReport(
                SEQUENCE_ID, BigInteger.ONE, BigInteger.ONE, MSRMT_METRIC_HANDLE, ComponentActivation.ON);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport));

        final var error = assertThrows(NoTestData.class, testClass::testRequirement54700);
        assertEquals(
                error.getMessage(),
                String.format(
                        InvariantParticipantModelStatePartTest.NO_SET_METRIC_STATUS_MANIPULATION,
                        MetricCategory.MSRMT));
    }

    /**
     * Tests whether no test data fails the test.
     */
    @Test
    public void testRequirement54700NoTestData() {
        assertThrows(NoTestData.class, testClass::testRequirement54700);
    }

    /**
     * Tests whether the test fails when no manipulation data with ResponseTypes.Result.RESULT_SUCCESS is in storage.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54700NoSuccessfulManipulation() throws Exception {
        requirement547SetUp(
                MSRMT_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.MSRMT,
                org.somda.sdc.biceps.model.participant.ComponentActivation.ON,
                // no manipulation with result success
                ResponseTypes.Result.RESULT_FAIL,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                MSRMT_METRIC_HANDLE,
                ComponentActivation.ON,
                TIMESTAMP_IN_INTERVAL);

        final var error = assertThrows(NoTestData.class, testClass::testRequirement54700);
        assertTrue(error.getMessage().contains(InvariantParticipantModelStatePartTest.NO_SUCCESSFUL_MANIPULATION));
    }

    /**
     * Test whether the test fails, when no manipulation data with category 'Msrmt' is in storage.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54700WrongMetricCategory() throws Exception {
        requirement547SetUp(
                MSRMT_METRIC_HANDLE,
                // the test expects manipulations with metric category msrmt
                org.somda.sdc.biceps.model.participant.MetricCategory.SET,
                org.somda.sdc.biceps.model.participant.ComponentActivation.ON,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                MSRMT_METRIC_HANDLE,
                ComponentActivation.ON,
                TIMESTAMP_IN_INTERVAL);

        final var error = assertThrows(NoTestData.class, testClass::testRequirement54700);
        assertTrue(error.getMessage()
                .contains(String.format(
                        InvariantParticipantModelStatePartTest.NO_SET_METRIC_STATUS_MANIPULATION,
                        MetricCategory.MSRMT)));
    }

    /**
     * Tests whether the test fails, when no metric report is present with a timestamp less than the manipulation
     * end timestamp + buffer.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54700NoReportUntilEndTimestamp() throws Exception {
        requirement547SetUp(
                MSRMT_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.MSRMT,
                org.somda.sdc.biceps.model.participant.ComponentActivation.ON,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                MSRMT_METRIC_HANDLE,
                ComponentActivation.ON,
                // the timestamp of the report is not < manipulation end timestamp + buffer
                TIMESTAMP_FINISH + buffer);
        final var error = assertThrows(AssertionError.class, testClass::testRequirement54700);
        assertTrue(error.getMessage()
                .contains(String.format(
                        InvariantParticipantModelStatePartTest.NO_REPORT_IN_TIME, TIMESTAMP_FINISH + buffer)));
    }

    /**
     * Tests whether the test fails, when the handle in the manipulation data parameters is unknown.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54700NoMetricWithExpectedHandle() throws Exception {
        requirement547SetUp(
                // this handle is not present in mdib
                SOME_NON_EXISTENT_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.MSRMT,
                org.somda.sdc.biceps.model.participant.ComponentActivation.ON,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                MSRMT_METRIC_HANDLE,
                ComponentActivation.ON,
                TIMESTAMP_IN_INTERVAL);
        final var error = assertThrows(AssertionError.class, testClass::testRequirement54700);
        assertTrue(error.getMessage()
                .contains(String.format(
                        InvariantParticipantModelStatePartTest.NO_METRIC_WITH_EXPECTED_HANDLE,
                        SOME_NON_EXISTENT_HANDLE)));
    }

    /**
     * Tests whether the test fails, when the metric has not the expected activation state after the manipulation.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54700BadWrongActivation() throws Exception {
        requirement547SetUp(
                MSRMT_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.MSRMT,
                org.somda.sdc.biceps.model.participant.ComponentActivation.ON,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                MSRMT_METRIC_HANDLE,
                // the metric has the wrong activation state, ComponentActivation.ON is expected
                ComponentActivation.OFF,
                TIMESTAMP_IN_INTERVAL);
        final var error = assertThrows(AssertionError.class, testClass::testRequirement54700);
        assertTrue(error.getMessage()
                .contains(String.format(
                        InvariantParticipantModelStatePartTest.WRONG_ACTIVATION_STATE,
                        MSRMT_METRIC_HANDLE,
                        ComponentActivation.ON,
                        ComponentActivation.OFF)));
    }

    /**
     * Tests whether the test passes, when for each manipulation data for 'setMetricStatus' manipulations and metrics
     * with category 'Msrmt' a metric with the expected activation state exists and is in the time interval of the manipulation data.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54700Good() throws Exception {
        requirement547SetUp(
                MSRMT_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.MSRMT,
                org.somda.sdc.biceps.model.participant.ComponentActivation.ON,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                MSRMT_METRIC_HANDLE,
                ComponentActivation.ON,
                TIMESTAMP_IN_INTERVAL);
        testClass.testRequirement54700();
    }

    /**
     * Tests whether the test correctly checks the metric with the handle from the manipulation data parameter.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54700GoodOverlappingTimeInterval() throws Exception {
        requirement547SetUp(
                MSRMT_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.MSRMT,
                org.somda.sdc.biceps.model.participant.ComponentActivation.ON,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                MSRMT_METRIC_HANDLE,
                ComponentActivation.ON,
                TIMESTAMP_IN_INTERVAL);

        final var parameters2 = ManipulationParameterUtil.buildMetricStatusManipulationParameterData(
                MdibBuilder.DEFAULT_SEQUENCE_ID,
                MSRMT_METRIC_HANDLE2,
                org.somda.sdc.biceps.model.participant.MetricCategory.MSRMT,
                org.somda.sdc.biceps.model.participant.ComponentActivation.ON);
        messageStorageUtil.addManipulation(
                storage,
                TIMESTAMP_START2,
                TIMESTAMP_FINISH2,
                ResponseTypes.Result.RESULT_SUCCESS,
                "", // not used by test
                Constants.MANIPULATION_NAME_SET_METRIC_STATUS,
                parameters2);
        final var metricReport2 = buildMetricReport(
                SEQUENCE_ID, BigInteger.ONE, BigInteger.ONE, MSRMT_METRIC_HANDLE2, ComponentActivation.ON);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL2, metricReport2));

        testClass.testRequirement54700();
    }

    /**
     * Tests whether the test passes when the last update of the activation state of the metric until the end timestamp
     * is as expected.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54700GoodMultipleReportsInInterval() throws Exception {
        requirement547SetUp(
                MSRMT_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.MSRMT,
                org.somda.sdc.biceps.model.participant.ComponentActivation.ON,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                MSRMT_METRIC_HANDLE,
                ComponentActivation.ON,
                TIMESTAMP_IN_INTERVAL);
        // another report for the same metric with the wrong activation state but a smaller mdib version should not
        // fail the test
        final var metricReport2 = buildMetricReport(
                SEQUENCE_ID, BigInteger.ONE, BigInteger.ONE, MSRMT_METRIC_HANDLE, ComponentActivation.OFF);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport2));

        testClass.testRequirement54700();
    }

    /**
     * Tests whether the test fails when the last update of the activation state of the metric until the end timestamp
     * is not as expected.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54700BadMultipleReportsInInterval() throws Exception {
        requirement547SetUp(
                MSRMT_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.MSRMT,
                org.somda.sdc.biceps.model.participant.ComponentActivation.ON,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                MSRMT_METRIC_HANDLE,
                ComponentActivation.ON,
                TIMESTAMP_IN_INTERVAL);
        // another report for the same metric with the wrong activation state but a bigger mdib version should
        // fail the test
        final var metricReport2 = buildMetricReport(
                SEQUENCE_ID, BigInteger.valueOf(10), BigInteger.ONE, MSRMT_METRIC_HANDLE, ComponentActivation.OFF);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport2));

        assertThrows(AssertionError.class, testClass::testRequirement54700);
    }

    /**
     * Tests whether no test data fails the test.
     */
    @Test
    public void testRequirement5471NoTestData() {
        assertThrows(NoTestData.class, testClass::testRequirement5471);
    }

    /**
     * Tests whether the test fails when no manipulation data with ResponseTypes.Result.RESULT_SUCCESS is in storage.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5471NoSuccessfulManipulation() throws Exception {
        requirement547SetUp(
                MSRMT_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.MSRMT,
                org.somda.sdc.biceps.model.participant.ComponentActivation.NOT_RDY,
                // no manipulation with result success
                ResponseTypes.Result.RESULT_FAIL,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                MSRMT_METRIC_HANDLE,
                ComponentActivation.NOT_RDY,
                TIMESTAMP_IN_INTERVAL);

        final var error = assertThrows(NoTestData.class, testClass::testRequirement5471);
        assertTrue(error.getMessage().contains(InvariantParticipantModelStatePartTest.NO_SUCCESSFUL_MANIPULATION));
    }

    /**
     * Test whether the test fails, when no manipulation data with category 'Msrmt' is in storage.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5471WrongMetricCategory() throws Exception {
        requirement547SetUp(
                MSRMT_METRIC_HANDLE,
                // the test expects manipulations with metric category msrmt
                org.somda.sdc.biceps.model.participant.MetricCategory.SET,
                org.somda.sdc.biceps.model.participant.ComponentActivation.NOT_RDY,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                MSRMT_METRIC_HANDLE,
                ComponentActivation.NOT_RDY,
                TIMESTAMP_IN_INTERVAL);

        final var error = assertThrows(NoTestData.class, testClass::testRequirement5471);
        assertTrue(error.getMessage()
                .contains(String.format(
                        InvariantParticipantModelStatePartTest.NO_SET_METRIC_STATUS_MANIPULATION,
                        MetricCategory.MSRMT)));
    }

    /**
     * Tests whether the test fails, when no metric report is present with timestamp less than the manipulation
     * end timestamp + buffer.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5471NoReportUntilEndTimestamp() throws Exception {
        requirement547SetUp(
                MSRMT_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.MSRMT,
                org.somda.sdc.biceps.model.participant.ComponentActivation.NOT_RDY,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                MSRMT_METRIC_HANDLE,
                ComponentActivation.NOT_RDY,
                // the timestamp of the report is not < manipulation end timestamp + buffer
                TIMESTAMP_FINISH + buffer);
        final var error = assertThrows(AssertionError.class, testClass::testRequirement5471);
        assertTrue(error.getMessage()
                .contains(String.format(
                        InvariantParticipantModelStatePartTest.NO_REPORT_IN_TIME, TIMESTAMP_FINISH + buffer)));
    }

    /**
     * Tests whether the test fails, when the handle in the manipulation data parameters is unknown.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5471NoMetricWithExpectedHandle() throws Exception {
        requirement547SetUp(
                // this handle is not present in mdib
                SOME_NON_EXISTENT_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.MSRMT,
                org.somda.sdc.biceps.model.participant.ComponentActivation.NOT_RDY,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                MSRMT_METRIC_HANDLE,
                ComponentActivation.NOT_RDY,
                TIMESTAMP_IN_INTERVAL);
        final var error = assertThrows(AssertionError.class, testClass::testRequirement5471);
        assertTrue(error.getMessage()
                .contains(String.format(
                        InvariantParticipantModelStatePartTest.NO_METRIC_WITH_EXPECTED_HANDLE,
                        SOME_NON_EXISTENT_HANDLE)));
    }

    /**
     * Tests whether the test fails, when the metric has not the expected activation state after the manipulation.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5471BadWrongActivation() throws Exception {
        requirement547SetUp(
                MSRMT_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.MSRMT,
                org.somda.sdc.biceps.model.participant.ComponentActivation.NOT_RDY,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                MSRMT_METRIC_HANDLE,
                // the metric has the wrong activation state, ComponentActivation.NOT_RDY is expected
                ComponentActivation.OFF,
                TIMESTAMP_IN_INTERVAL);
        final var error = assertThrows(AssertionError.class, testClass::testRequirement5471);
        assertTrue(error.getMessage()
                .contains(String.format(
                        InvariantParticipantModelStatePartTest.WRONG_ACTIVATION_STATE,
                        MSRMT_METRIC_HANDLE,
                        ComponentActivation.NOT_RDY,
                        ComponentActivation.OFF)));
    }

    /**
     * Tests whether the test passes, when for each manipulation data for 'setMetricStatus' manipulations and metrics
     * with category 'Msrmt' a metric with the expected activation state exists and is in the time interval of the manipulation data.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5471Good() throws Exception {
        requirement547SetUp(
                MSRMT_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.MSRMT,
                org.somda.sdc.biceps.model.participant.ComponentActivation.NOT_RDY,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                MSRMT_METRIC_HANDLE,
                ComponentActivation.NOT_RDY,
                TIMESTAMP_IN_INTERVAL);
        testClass.testRequirement5471();
    }

    /**
     * Tests whether the test correctly checks the metric with the handle from the manipulation data parameter.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5471GoodOverlappingTimeInterval() throws Exception {
        requirement547SetUp(
                MSRMT_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.MSRMT,
                org.somda.sdc.biceps.model.participant.ComponentActivation.NOT_RDY,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                MSRMT_METRIC_HANDLE,
                ComponentActivation.NOT_RDY,
                TIMESTAMP_IN_INTERVAL);

        final var parameters2 = ManipulationParameterUtil.buildMetricStatusManipulationParameterData(
                MdibBuilder.DEFAULT_SEQUENCE_ID,
                MSRMT_METRIC_HANDLE2,
                org.somda.sdc.biceps.model.participant.MetricCategory.MSRMT,
                org.somda.sdc.biceps.model.participant.ComponentActivation.NOT_RDY);
        messageStorageUtil.addManipulation(
                storage,
                TIMESTAMP_START2,
                TIMESTAMP_FINISH2,
                ResponseTypes.Result.RESULT_SUCCESS,
                "", // not used by test
                Constants.MANIPULATION_NAME_SET_METRIC_STATUS,
                parameters2);
        final var metricReport2 = buildMetricReport(
                SEQUENCE_ID, BigInteger.ONE, BigInteger.ONE, MSRMT_METRIC_HANDLE2, ComponentActivation.NOT_RDY);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL2, metricReport2));

        testClass.testRequirement5471();
    }

    /**
     * Tests whether the test passes when the last update of the activation state of the metric until the end timestamp
     * is as expected.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5471GoodMultipleReportsInInterval() throws Exception {
        requirement547SetUp(
                MSRMT_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.MSRMT,
                org.somda.sdc.biceps.model.participant.ComponentActivation.NOT_RDY,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                MSRMT_METRIC_HANDLE,
                ComponentActivation.NOT_RDY,
                TIMESTAMP_IN_INTERVAL);
        // add another report for the same metric handle with the wrong activation state
        final var metricReport2 = buildMetricReport(
                SEQUENCE_ID, BigInteger.ONE, BigInteger.ONE, MSRMT_METRIC_HANDLE, ComponentActivation.OFF);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport2));

        testClass.testRequirement5471();
    }

    /**
     * Tests whether the test fails when the last update of the activation state of the metric until the end timestamp
     * is not as expected.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5471BadMultipleReportsInInterval() throws Exception {
        requirement547SetUp(
                MSRMT_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.MSRMT,
                org.somda.sdc.biceps.model.participant.ComponentActivation.NOT_RDY,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                MSRMT_METRIC_HANDLE,
                ComponentActivation.NOT_RDY,
                TIMESTAMP_IN_INTERVAL);
        // add another report for the same metric handle with the wrong activation state
        final var metricReport2 = buildMetricReport(
                SEQUENCE_ID, BigInteger.valueOf(10), BigInteger.ONE, MSRMT_METRIC_HANDLE, ComponentActivation.OFF);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport2));

        assertThrows(AssertionError.class, testClass::testRequirement5471);
    }

    /**
     * Tests whether no test data fails the test.
     */
    @Test
    public void testRequirement5472NoTestData() {
        assertThrows(NoTestData.class, testClass::testRequirement5472);
    }

    /**
     * Tests whether the test fails when no manipulation data with ResponseTypes.Result.RESULT_SUCCESS is in storage.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5472NoSuccessfulManipulation() throws Exception {
        requirement547SetUp(
                MSRMT_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.MSRMT,
                org.somda.sdc.biceps.model.participant.ComponentActivation.STND_BY,
                // no manipulation with result success
                ResponseTypes.Result.RESULT_FAIL,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                MSRMT_METRIC_HANDLE,
                ComponentActivation.STND_BY,
                TIMESTAMP_IN_INTERVAL);

        final var error = assertThrows(NoTestData.class, testClass::testRequirement5472);
        assertTrue(error.getMessage().contains(InvariantParticipantModelStatePartTest.NO_SUCCESSFUL_MANIPULATION));
    }

    /**
     * Test whether the test fails, when no manipulation data with category 'Msrmt' is in storage.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5472WrongMetricCategory() throws Exception {
        requirement547SetUp(
                MSRMT_METRIC_HANDLE,
                // the test expects manipulations with metric category msrmt
                org.somda.sdc.biceps.model.participant.MetricCategory.SET,
                org.somda.sdc.biceps.model.participant.ComponentActivation.STND_BY,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                MSRMT_METRIC_HANDLE,
                ComponentActivation.STND_BY,
                TIMESTAMP_IN_INTERVAL);

        final var error = assertThrows(NoTestData.class, testClass::testRequirement5472);
        assertTrue(error.getMessage()
                .contains(String.format(
                        InvariantParticipantModelStatePartTest.NO_SET_METRIC_STATUS_MANIPULATION,
                        MetricCategory.MSRMT)));
    }

    /**
     * Tests whether the test fails, when no metric report is present with timestamp less than the manipulation end timestamp + buffer.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5472NoReportUntilEndTimestamp() throws Exception {
        requirement547SetUp(
                MSRMT_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.MSRMT,
                org.somda.sdc.biceps.model.participant.ComponentActivation.STND_BY,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                MSRMT_METRIC_HANDLE,
                ComponentActivation.STND_BY,
                // the timestamp of the report is not < manipulation end timestamp + buffer
                TIMESTAMP_FINISH + buffer);
        final var error = assertThrows(AssertionError.class, testClass::testRequirement5472);
        assertTrue(error.getMessage()
                .contains(String.format(
                        InvariantParticipantModelStatePartTest.NO_REPORT_IN_TIME, TIMESTAMP_FINISH + buffer)));
    }

    /**
     * Tests whether the test fails, when the handle in the manipulation data parameters is unknown.
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5472NoMetricWithExpectedHandle() throws Exception {
        requirement547SetUp(
                // this handle is not present in mdib
                SOME_NON_EXISTENT_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.MSRMT,
                org.somda.sdc.biceps.model.participant.ComponentActivation.STND_BY,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                MSRMT_METRIC_HANDLE,
                ComponentActivation.STND_BY,
                TIMESTAMP_IN_INTERVAL);
        final var error = assertThrows(AssertionError.class, testClass::testRequirement5472);
        assertTrue(error.getMessage()
                .contains(String.format(
                        InvariantParticipantModelStatePartTest.NO_METRIC_WITH_EXPECTED_HANDLE,
                        SOME_NON_EXISTENT_HANDLE)));
    }

    /**
     * Tests whether the test fails, when the metric has not the expected activation state after the manipulation.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5472BadWrongActivation() throws Exception {
        requirement547SetUp(
                MSRMT_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.MSRMT,
                org.somda.sdc.biceps.model.participant.ComponentActivation.STND_BY,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                MSRMT_METRIC_HANDLE,
                // the metric has the wrong activation state, ComponentActivation.STND_BY is expected
                ComponentActivation.OFF,
                TIMESTAMP_IN_INTERVAL);
        final var error = assertThrows(AssertionError.class, testClass::testRequirement5472);
        assertTrue(error.getMessage()
                .contains(String.format(
                        InvariantParticipantModelStatePartTest.WRONG_ACTIVATION_STATE,
                        MSRMT_METRIC_HANDLE,
                        ComponentActivation.STND_BY,
                        ComponentActivation.OFF)));
    }

    /**
     * Tests whether the test passes, when for each manipulation data for 'setMetricStatus' manipulations and metrics
     * with category 'Msrmt' a metric with the expected activation state exists and is in the time interval of the manipulation data.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5472Good() throws Exception {
        requirement547SetUp(
                MSRMT_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.MSRMT,
                org.somda.sdc.biceps.model.participant.ComponentActivation.STND_BY,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                MSRMT_METRIC_HANDLE,
                ComponentActivation.STND_BY,
                TIMESTAMP_IN_INTERVAL);
        testClass.testRequirement5472();
    }

    /**
     * Tests whether the test correctly checks the metric with the handle from the manipulation data parameter.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5472GoodOverlappingTimeInterval() throws Exception {
        requirement547SetUp(
                MSRMT_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.MSRMT,
                org.somda.sdc.biceps.model.participant.ComponentActivation.STND_BY,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                MSRMT_METRIC_HANDLE,
                ComponentActivation.STND_BY,
                TIMESTAMP_IN_INTERVAL);

        final var parameters2 = ManipulationParameterUtil.buildMetricStatusManipulationParameterData(
                MdibBuilder.DEFAULT_SEQUENCE_ID,
                MSRMT_METRIC_HANDLE2,
                org.somda.sdc.biceps.model.participant.MetricCategory.MSRMT,
                org.somda.sdc.biceps.model.participant.ComponentActivation.STND_BY);
        messageStorageUtil.addManipulation(
                storage,
                TIMESTAMP_START2,
                TIMESTAMP_FINISH2,
                ResponseTypes.Result.RESULT_SUCCESS,
                "", // not used by test
                Constants.MANIPULATION_NAME_SET_METRIC_STATUS,
                parameters2);
        final var metricReport2 = buildMetricReport(
                SEQUENCE_ID, BigInteger.ONE, BigInteger.ONE, MSRMT_METRIC_HANDLE2, ComponentActivation.STND_BY);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL2, metricReport2));

        testClass.testRequirement5472();
    }

    /**
     * Tests whether the test passes when the last update of the activation state of the metric until the end timestamp is as expected.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5472GoodMultipleReportsInInterval() throws Exception {
        requirement547SetUp(
                MSRMT_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.MSRMT,
                org.somda.sdc.biceps.model.participant.ComponentActivation.STND_BY,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                MSRMT_METRIC_HANDLE,
                ComponentActivation.STND_BY,
                TIMESTAMP_IN_INTERVAL);
        // add another report for the same metric handle with the wrong activation state
        final var metricReport2 = buildMetricReport(
                SEQUENCE_ID, BigInteger.ONE, BigInteger.ONE, MSRMT_METRIC_HANDLE, ComponentActivation.OFF);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport2));

        testClass.testRequirement5472();
    }

    /**
     * Tests whether the test fails when the last update of the activation state of the metric until the end timestamp is not as expected.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5472BadMultipleReportsInInterval() throws Exception {
        requirement547SetUp(
                MSRMT_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.MSRMT,
                org.somda.sdc.biceps.model.participant.ComponentActivation.STND_BY,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                SET_METRIC_HANDLE,
                ComponentActivation.STND_BY,
                TIMESTAMP_IN_INTERVAL);
        // add another report for the same metric handle with the wrong activation state
        final var metricReport2 = buildMetricReport(
                SEQUENCE_ID, BigInteger.valueOf(10), BigInteger.ONE, MSRMT_METRIC_HANDLE, ComponentActivation.OFF);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport2));

        assertThrows(AssertionError.class, testClass::testRequirement5472);
    }

    /**
     * Tests whether no test data fails the test.
     */
    @Test
    public void testRequirement5473NoTestData() {
        assertThrows(NoTestData.class, testClass::testRequirement5473);
    }

    /**
     * Tests whether the test fails when no manipulation data with ResponseTypes.Result.RESULT_SUCCESS is in storage.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5473NoSuccessfulManipulation() throws Exception {
        requirement547SetUp(
                MSRMT_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.MSRMT,
                org.somda.sdc.biceps.model.participant.ComponentActivation.SHTDN,
                // no manipulation with result success
                ResponseTypes.Result.RESULT_FAIL,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                MSRMT_METRIC_HANDLE,
                ComponentActivation.SHTDN,
                TIMESTAMP_IN_INTERVAL);

        final var error = assertThrows(NoTestData.class, testClass::testRequirement5473);
        assertTrue(error.getMessage().contains(InvariantParticipantModelStatePartTest.NO_SUCCESSFUL_MANIPULATION));
    }

    /**
     * Test whether the test fails, when no manipulation data with category 'Msrmt' is in storage.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5473WrongMetricCategory() throws Exception {
        requirement547SetUp(
                MSRMT_METRIC_HANDLE,
                // the test expects manipulations with metric category msrmt
                org.somda.sdc.biceps.model.participant.MetricCategory.SET,
                org.somda.sdc.biceps.model.participant.ComponentActivation.SHTDN,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                MSRMT_METRIC_HANDLE,
                ComponentActivation.SHTDN,
                TIMESTAMP_IN_INTERVAL);

        final var error = assertThrows(NoTestData.class, testClass::testRequirement5473);
        assertTrue(error.getMessage()
                .contains(String.format(
                        InvariantParticipantModelStatePartTest.NO_SET_METRIC_STATUS_MANIPULATION,
                        MetricCategory.MSRMT)));
    }

    /**
     * Tests whether the test fails, when no metric report is present with timestamp less than the manipulation end timestamp + buffer.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5473NoReportUntilEndTimestamp() throws Exception {
        requirement547SetUp(
                MSRMT_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.MSRMT,
                org.somda.sdc.biceps.model.participant.ComponentActivation.SHTDN,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                MSRMT_METRIC_HANDLE,
                ComponentActivation.SHTDN,
                // the timestamp of the report is not < manipulation end timestamp + buffer
                TIMESTAMP_FINISH + buffer);
        final var error = assertThrows(AssertionError.class, testClass::testRequirement5473);
        assertTrue(error.getMessage()
                .contains(String.format(
                        InvariantParticipantModelStatePartTest.NO_REPORT_IN_TIME, TIMESTAMP_FINISH + buffer)));
    }

    /**
     * Tests whether the test fails, when the handle in the manipulation data parameters is unknown.
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5473NoMetricWithExpectedHandle() throws Exception {
        requirement547SetUp(
                // this handle is not present in mdib
                SOME_NON_EXISTENT_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.MSRMT,
                org.somda.sdc.biceps.model.participant.ComponentActivation.SHTDN,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                MSRMT_METRIC_HANDLE,
                ComponentActivation.SHTDN,
                TIMESTAMP_IN_INTERVAL);
        final var error = assertThrows(AssertionError.class, testClass::testRequirement5473);
        assertTrue(error.getMessage()
                .contains(String.format(
                        InvariantParticipantModelStatePartTest.NO_METRIC_WITH_EXPECTED_HANDLE,
                        SOME_NON_EXISTENT_HANDLE)));
    }

    /**
     * Tests whether the test fails, when the metric has not the expected activation state after the manipulation.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5473BadWrongActivation() throws Exception {
        requirement547SetUp(
                MSRMT_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.MSRMT,
                org.somda.sdc.biceps.model.participant.ComponentActivation.SHTDN,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                MSRMT_METRIC_HANDLE,
                // the metric has the wrong activation state, ComponentActivation.SHTDN is expected
                ComponentActivation.OFF,
                TIMESTAMP_IN_INTERVAL);
        final var error = assertThrows(AssertionError.class, testClass::testRequirement5473);
        assertTrue(error.getMessage()
                .contains(String.format(
                        InvariantParticipantModelStatePartTest.WRONG_ACTIVATION_STATE,
                        MSRMT_METRIC_HANDLE,
                        ComponentActivation.SHTDN,
                        ComponentActivation.OFF)));
    }

    /**
     * Tests whether the test passes, when for each manipulation data for 'setMetricStatus' manipulations and metrics
     * with category 'Msrmt' a metric with the expected activation state exists and is in the time interval of the manipulation data.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5473Good() throws Exception {
        requirement547SetUp(
                MSRMT_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.MSRMT,
                org.somda.sdc.biceps.model.participant.ComponentActivation.SHTDN,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                MSRMT_METRIC_HANDLE,
                ComponentActivation.SHTDN,
                TIMESTAMP_IN_INTERVAL);
        testClass.testRequirement5473();
    }

    /**
     * Tests whether the test correctly checks the metric with the handle from the manipulation data parameter.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5473GoodOverlappingTimeInterval() throws Exception {
        requirement547SetUp(
                MSRMT_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.MSRMT,
                org.somda.sdc.biceps.model.participant.ComponentActivation.SHTDN,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                MSRMT_METRIC_HANDLE,
                ComponentActivation.SHTDN,
                TIMESTAMP_IN_INTERVAL);

        final var parameters2 = ManipulationParameterUtil.buildMetricStatusManipulationParameterData(
                MdibBuilder.DEFAULT_SEQUENCE_ID,
                MSRMT_METRIC_HANDLE2,
                org.somda.sdc.biceps.model.participant.MetricCategory.MSRMT,
                org.somda.sdc.biceps.model.participant.ComponentActivation.SHTDN);
        messageStorageUtil.addManipulation(
                storage,
                TIMESTAMP_START2,
                TIMESTAMP_FINISH2,
                ResponseTypes.Result.RESULT_SUCCESS,
                "", // not used by test
                Constants.MANIPULATION_NAME_SET_METRIC_STATUS,
                parameters2);
        final var metricReport2 = buildMetricReport(
                SEQUENCE_ID, BigInteger.ONE, BigInteger.ONE, MSRMT_METRIC_HANDLE2, ComponentActivation.SHTDN);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL2, metricReport2));

        testClass.testRequirement5473();
    }

    /**
     * Tests whether the test passes when the last update of the activation state of the metric until the end timestamp is as expected.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5473GoodMultipleReportsInInterval() throws Exception {
        requirement547SetUp(
                MSRMT_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.MSRMT,
                org.somda.sdc.biceps.model.participant.ComponentActivation.SHTDN,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                MSRMT_METRIC_HANDLE,
                ComponentActivation.SHTDN,
                TIMESTAMP_IN_INTERVAL);
        // add another report for the same metric handle with the wrong activation state
        final var metricReport2 = buildMetricReport(
                SEQUENCE_ID, BigInteger.ONE, BigInteger.ONE, MSRMT_METRIC_HANDLE, ComponentActivation.OFF);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport2));

        testClass.testRequirement5473();
    }

    /**
     * Tests whether the test fails when the last update of the activation state of the metric until the end timestamp is not as expected.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5473BadMultipleReportsInInterval() throws Exception {
        requirement547SetUp(
                MSRMT_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.MSRMT,
                org.somda.sdc.biceps.model.participant.ComponentActivation.SHTDN,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                MSRMT_METRIC_HANDLE,
                ComponentActivation.SHTDN,
                TIMESTAMP_IN_INTERVAL);
        // add another report for the same metric handle with the wrong activation state
        final var metricReport2 = buildMetricReport(
                SEQUENCE_ID, BigInteger.valueOf(10), BigInteger.ONE, MSRMT_METRIC_HANDLE, ComponentActivation.OFF);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport2));

        assertThrows(AssertionError.class, testClass::testRequirement5473);
    }

    /**
     * Tests whether no test data fails the test.
     */
    @Test
    public void testRequirement5474NoTestData() {
        assertThrows(NoTestData.class, testClass::testRequirement5474);
    }

    /**
     * Tests whether the test fails when no manipulation data with ResponseTypes.Result.RESULT_SUCCESS is in storage.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5474NoSuccessfulManipulation() throws Exception {
        requirement547SetUp(
                MSRMT_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.MSRMT,
                org.somda.sdc.biceps.model.participant.ComponentActivation.OFF,
                // no manipulation with result success
                ResponseTypes.Result.RESULT_FAIL,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                MSRMT_METRIC_HANDLE,
                ComponentActivation.OFF,
                TIMESTAMP_IN_INTERVAL);

        final var error = assertThrows(NoTestData.class, testClass::testRequirement5474);
        assertTrue(error.getMessage().contains(InvariantParticipantModelStatePartTest.NO_SUCCESSFUL_MANIPULATION));
    }

    /**
     * Test whether the test fails, when no manipulation data with category 'Msrmt' is in storage.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5474WrongMetricCategory() throws Exception {
        requirement547SetUp(
                MSRMT_METRIC_HANDLE,
                // the test expects manipulations with metric category msrmt
                org.somda.sdc.biceps.model.participant.MetricCategory.SET,
                org.somda.sdc.biceps.model.participant.ComponentActivation.OFF,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                MSRMT_METRIC_HANDLE,
                ComponentActivation.OFF,
                TIMESTAMP_IN_INTERVAL);

        final var error = assertThrows(NoTestData.class, testClass::testRequirement5474);
        assertTrue(error.getMessage()
                .contains(String.format(
                        InvariantParticipantModelStatePartTest.NO_SET_METRIC_STATUS_MANIPULATION,
                        MetricCategory.MSRMT)));
    }

    /**
     * Tests whether the test fails, when no metric report is present with timestamp less than the manipulation end timestamp + buffer.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5474NoReportUntilEndTimestamp() throws Exception {
        requirement547SetUp(
                MSRMT_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.MSRMT,
                org.somda.sdc.biceps.model.participant.ComponentActivation.OFF,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                MSRMT_METRIC_HANDLE,
                ComponentActivation.OFF,
                // the timestamp of the report is not < manipulation end timestamp + buffer
                TIMESTAMP_FINISH + buffer);
        final var error = assertThrows(AssertionError.class, testClass::testRequirement5474);
        assertTrue(error.getMessage()
                .contains(String.format(
                        InvariantParticipantModelStatePartTest.NO_REPORT_IN_TIME, TIMESTAMP_FINISH + buffer)));
    }

    /**
     * Tests whether the test fails, when the handle in the manipulation data parameters is unknown.
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5474NoMetricWithExpectedHandle() throws Exception {
        requirement547SetUp(
                // this handle is not present in mdib
                SOME_NON_EXISTENT_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.MSRMT,
                org.somda.sdc.biceps.model.participant.ComponentActivation.OFF,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                MSRMT_METRIC_HANDLE,
                ComponentActivation.OFF,
                TIMESTAMP_IN_INTERVAL);
        final var error = assertThrows(AssertionError.class, testClass::testRequirement5474);
        assertTrue(error.getMessage()
                .contains(String.format(
                        InvariantParticipantModelStatePartTest.NO_METRIC_WITH_EXPECTED_HANDLE,
                        SOME_NON_EXISTENT_HANDLE)));
    }

    /**
     * Tests whether the test fails, when the metric has not the expected activation state after the manipulation.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5474BadWrongActivation() throws Exception {
        requirement547SetUp(
                MSRMT_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.MSRMT,
                org.somda.sdc.biceps.model.participant.ComponentActivation.OFF,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                MSRMT_METRIC_HANDLE,
                // the metric has the wrong activation state, ComponentActivation.OFF is expected
                ComponentActivation.ON,
                TIMESTAMP_IN_INTERVAL);
        final var error = assertThrows(AssertionError.class, testClass::testRequirement5474);
        assertTrue(error.getMessage()
                .contains(String.format(
                        InvariantParticipantModelStatePartTest.WRONG_ACTIVATION_STATE,
                        MSRMT_METRIC_HANDLE,
                        ComponentActivation.OFF,
                        ComponentActivation.ON)));
    }

    /**
     * Tests whether the test passes, when for each manipulation data for 'setMetricStatus' manipulations and metrics
     * with category 'Msrmt' a metric with the expected activation state exists and is in the time interval of the manipulation data.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5474Good() throws Exception {
        requirement547SetUp(
                MSRMT_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.MSRMT,
                org.somda.sdc.biceps.model.participant.ComponentActivation.OFF,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                MSRMT_METRIC_HANDLE,
                ComponentActivation.OFF,
                TIMESTAMP_IN_INTERVAL);
        testClass.testRequirement5474();
    }

    /**
     * Tests whether the test correctly checks the metric with the handle from the manipulation data parameter.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5474GoodOverlappingTimeInterval() throws Exception {
        requirement547SetUp(
                MSRMT_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.MSRMT,
                org.somda.sdc.biceps.model.participant.ComponentActivation.OFF,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                MSRMT_METRIC_HANDLE,
                ComponentActivation.OFF,
                TIMESTAMP_IN_INTERVAL);

        final var parameters2 = ManipulationParameterUtil.buildMetricStatusManipulationParameterData(
                MdibBuilder.DEFAULT_SEQUENCE_ID,
                MSRMT_METRIC_HANDLE2,
                org.somda.sdc.biceps.model.participant.MetricCategory.MSRMT,
                org.somda.sdc.biceps.model.participant.ComponentActivation.OFF);
        messageStorageUtil.addManipulation(
                storage,
                TIMESTAMP_START2,
                TIMESTAMP_FINISH2,
                ResponseTypes.Result.RESULT_SUCCESS,
                "", // not used by test
                Constants.MANIPULATION_NAME_SET_METRIC_STATUS,
                parameters2);
        final var metricReport2 = buildMetricReport(
                SEQUENCE_ID, BigInteger.ONE, BigInteger.ONE, MSRMT_METRIC_HANDLE2, ComponentActivation.OFF);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL2, metricReport2));

        testClass.testRequirement5474();
    }

    /**
     * Tests whether the test passes when the last update of the activation state of the metric until the end timestamp is as expected.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5474GoodMultipleReportsInInterval() throws Exception {
        requirement547SetUp(
                MSRMT_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.MSRMT,
                org.somda.sdc.biceps.model.participant.ComponentActivation.OFF,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                MSRMT_METRIC_HANDLE,
                ComponentActivation.OFF,
                TIMESTAMP_IN_INTERVAL);
        // add another report for the same metric handle with the wrong activation state
        final var metricReport2 = buildMetricReport(
                SEQUENCE_ID, BigInteger.ONE, BigInteger.ONE, MSRMT_METRIC_HANDLE, ComponentActivation.ON);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport2));

        testClass.testRequirement5474();
    }

    /**
     * Tests whether the test fails when the last update of the activation state of the metric until the end timestamp is not as expected.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5474BadMultipleReportsInInterval() throws Exception {
        requirement547SetUp(
                MSRMT_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.MSRMT,
                org.somda.sdc.biceps.model.participant.ComponentActivation.OFF,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                MSRMT_METRIC_HANDLE,
                ComponentActivation.OFF,
                TIMESTAMP_IN_INTERVAL);
        // add another report for the same metric handle with the wrong activation state
        final var metricReport2 = buildMetricReport(
                SEQUENCE_ID, BigInteger.valueOf(10), BigInteger.ONE, MSRMT_METRIC_HANDLE, ComponentActivation.ON);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport2));

        assertThrows(AssertionError.class, testClass::testRequirement5474);
    }

    /**
     * Tests whether no test data fails the test.
     */
    @Test
    public void testRequirement5475NoTestData() {
        assertThrows(NoTestData.class, testClass::testRequirement5475);
    }

    /**
     * Tests whether the test fails when no manipulation data with ResponseTypes.Result.RESULT_SUCCESS is in storage.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5475NoSuccessfulManipulation() throws Exception {
        requirement547SetUp(
                MSRMT_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.MSRMT,
                org.somda.sdc.biceps.model.participant.ComponentActivation.FAIL,
                // no manipulation with result success
                ResponseTypes.Result.RESULT_FAIL,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                MSRMT_METRIC_HANDLE,
                ComponentActivation.FAIL,
                TIMESTAMP_IN_INTERVAL);

        final var error = assertThrows(NoTestData.class, testClass::testRequirement5475);
        assertTrue(error.getMessage().contains(InvariantParticipantModelStatePartTest.NO_SUCCESSFUL_MANIPULATION));
    }

    /**
     * Test whether the test fails, when no manipulation data with category 'Msrmt' is in storage.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5475WrongMetricCategory() throws Exception {
        requirement547SetUp(
                MSRMT_METRIC_HANDLE,
                // the test expects manipulations with metric category msrmt
                org.somda.sdc.biceps.model.participant.MetricCategory.SET,
                org.somda.sdc.biceps.model.participant.ComponentActivation.FAIL,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                MSRMT_METRIC_HANDLE,
                ComponentActivation.FAIL,
                TIMESTAMP_IN_INTERVAL);

        final var error = assertThrows(NoTestData.class, testClass::testRequirement5475);
        assertTrue(error.getMessage()
                .contains(String.format(
                        InvariantParticipantModelStatePartTest.NO_SET_METRIC_STATUS_MANIPULATION,
                        MetricCategory.MSRMT)));
    }

    /**
     * Tests whether the test fails, when no metric report is present with timestamp less than the manipulation end timestamp + buffer.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5475NoReportUntilEndTimestamp() throws Exception {
        requirement547SetUp(
                MSRMT_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.MSRMT,
                org.somda.sdc.biceps.model.participant.ComponentActivation.FAIL,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                MSRMT_METRIC_HANDLE,
                ComponentActivation.FAIL,
                // the timestamp of the report is not < manipulation end timestamp + buffer
                TIMESTAMP_FINISH + buffer);
        final var error = assertThrows(AssertionError.class, testClass::testRequirement5475);
        assertTrue(error.getMessage()
                .contains(String.format(
                        InvariantParticipantModelStatePartTest.NO_REPORT_IN_TIME, TIMESTAMP_FINISH + buffer)));
    }

    /**
     * Tests whether the test fails, when the handle in the manipulation data parameters is unknown.
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5475NoMetricWithExpectedHandle() throws Exception {
        requirement547SetUp(
                // this handle is not present in mdib
                SOME_NON_EXISTENT_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.MSRMT,
                org.somda.sdc.biceps.model.participant.ComponentActivation.FAIL,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                MSRMT_METRIC_HANDLE,
                ComponentActivation.FAIL,
                TIMESTAMP_IN_INTERVAL);
        final var error = assertThrows(AssertionError.class, testClass::testRequirement5475);
        assertTrue(error.getMessage()
                .contains(String.format(
                        InvariantParticipantModelStatePartTest.NO_METRIC_WITH_EXPECTED_HANDLE,
                        SOME_NON_EXISTENT_HANDLE)));
    }

    /**
     * Tests whether the test fails, when the metric has not the expected activation state after the manipulation.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5475BadWrongActivation() throws Exception {
        requirement547SetUp(
                MSRMT_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.MSRMT,
                org.somda.sdc.biceps.model.participant.ComponentActivation.FAIL,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                MSRMT_METRIC_HANDLE,
                // the metric has the wrong activation state, ComponentActivation.FAIL is expected
                ComponentActivation.OFF,
                TIMESTAMP_IN_INTERVAL);
        final var error = assertThrows(AssertionError.class, testClass::testRequirement5475);
        assertTrue(error.getMessage()
                .contains(String.format(
                        InvariantParticipantModelStatePartTest.WRONG_ACTIVATION_STATE,
                        MSRMT_METRIC_HANDLE,
                        ComponentActivation.FAIL,
                        ComponentActivation.OFF)));
    }

    /**
     * Tests whether the test passes, when for each manipulation data for 'setMetricStatus' manipulations and metrics
     * with category 'Msrmt' a metric with the expected activation state exists and is in the time interval of the manipulation data.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5475Good() throws Exception {
        requirement547SetUp(
                MSRMT_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.MSRMT,
                org.somda.sdc.biceps.model.participant.ComponentActivation.FAIL,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                MSRMT_METRIC_HANDLE,
                ComponentActivation.FAIL,
                TIMESTAMP_IN_INTERVAL);
        testClass.testRequirement5475();
    }

    /**
     * Tests whether the test correctly checks the metric with the handle from the manipulation data parameter.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5475GoodOverlappingTimeInterval() throws Exception {
        requirement547SetUp(
                MSRMT_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.MSRMT,
                org.somda.sdc.biceps.model.participant.ComponentActivation.FAIL,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                MSRMT_METRIC_HANDLE,
                ComponentActivation.FAIL,
                TIMESTAMP_IN_INTERVAL);

        final var parameters2 = ManipulationParameterUtil.buildMetricStatusManipulationParameterData(
                MdibBuilder.DEFAULT_SEQUENCE_ID,
                MSRMT_METRIC_HANDLE2,
                org.somda.sdc.biceps.model.participant.MetricCategory.MSRMT,
                org.somda.sdc.biceps.model.participant.ComponentActivation.FAIL);
        messageStorageUtil.addManipulation(
                storage,
                TIMESTAMP_START2,
                TIMESTAMP_FINISH2,
                ResponseTypes.Result.RESULT_SUCCESS,
                "", // not used by test
                Constants.MANIPULATION_NAME_SET_METRIC_STATUS,
                parameters2);
        final var metricReport2 = buildMetricReport(
                SEQUENCE_ID, BigInteger.ONE, BigInteger.ONE, MSRMT_METRIC_HANDLE2, ComponentActivation.FAIL);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL2, metricReport2));

        testClass.testRequirement5475();
    }

    /**
     * Tests whether the test passes when the last update of the activation state of the metric until the end timestamp is as expected.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5475GoodMultipleReportsInInterval() throws Exception {
        requirement547SetUp(
                MSRMT_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.MSRMT,
                org.somda.sdc.biceps.model.participant.ComponentActivation.FAIL,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                MSRMT_METRIC_HANDLE,
                ComponentActivation.FAIL,
                TIMESTAMP_IN_INTERVAL);
        // add another report for the same metric handle with the wrong activation state
        final var metricReport2 = buildMetricReport(
                SEQUENCE_ID, BigInteger.ONE, BigInteger.ONE, MSRMT_METRIC_HANDLE, ComponentActivation.OFF);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport2));

        testClass.testRequirement5475();
    }

    /**
     * Tests whether the test fails when the last update of the activation state of the metric until the end timestamp is not as expected.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5475BadMultipleReportsInInterval() throws Exception {
        requirement547SetUp(
                MSRMT_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.MSRMT,
                org.somda.sdc.biceps.model.participant.ComponentActivation.FAIL,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                MSRMT_METRIC_HANDLE,
                ComponentActivation.FAIL,
                TIMESTAMP_IN_INTERVAL);
        // add another report for the same metric handle with the wrong activation state
        final var metricReport2 = buildMetricReport(
                SEQUENCE_ID, BigInteger.valueOf(10), BigInteger.ONE, MSRMT_METRIC_HANDLE, ComponentActivation.OFF);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport2));

        assertThrows(AssertionError.class, testClass::testRequirement5475);
    }

    /**
     * Tests whether no test data fails the test.
     */
    @Test
    public void testRequirement54760NoTestData() {
        assertThrows(NoTestData.class, testClass::testRequirement54760);
    }

    /**
     * Tests whether the test fails when no manipulation data with ResponseTypes.Result.RESULT_SUCCESS is in storage.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54760NoSuccessfulManipulation() throws Exception {
        requirement547SetUp(
                SET_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.SET,
                org.somda.sdc.biceps.model.participant.ComponentActivation.ON,
                // no manipulation with result success
                ResponseTypes.Result.RESULT_FAIL,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                SET_METRIC_HANDLE,
                ComponentActivation.ON,
                TIMESTAMP_IN_INTERVAL);

        final var error = assertThrows(NoTestData.class, testClass::testRequirement54760);
        assertTrue(error.getMessage().contains(InvariantParticipantModelStatePartTest.NO_SUCCESSFUL_MANIPULATION));
    }

    /**
     * Test whether the test fails, when no manipulation data with category 'SET' is in storage.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54760WrongMetricCategory() throws Exception {
        requirement547SetUp(
                SET_METRIC_HANDLE,
                // the test expects manipulations with metric category SET
                org.somda.sdc.biceps.model.participant.MetricCategory.MSRMT,
                org.somda.sdc.biceps.model.participant.ComponentActivation.ON,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                SET_METRIC_HANDLE,
                ComponentActivation.ON,
                TIMESTAMP_IN_INTERVAL);

        final var error = assertThrows(NoTestData.class, testClass::testRequirement54760);
        assertTrue(error.getMessage()
                .contains(String.format(
                        InvariantParticipantModelStatePartTest.NO_SET_METRIC_STATUS_MANIPULATION, MetricCategory.SET)));
    }

    /**
     * Tests whether the test fails, when no metric report is present with timestamp less than the manipulation end timestamp + buffer.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54760NoReportUntilEndTimestamp() throws Exception {
        requirement547SetUp(
                SET_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.SET,
                org.somda.sdc.biceps.model.participant.ComponentActivation.ON,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                SET_METRIC_HANDLE,
                ComponentActivation.ON,
                // the timestamp of the report is not < manipulation end timestamp + buffer
                TIMESTAMP_FINISH + buffer);
        final var error = assertThrows(AssertionError.class, testClass::testRequirement54760);
        assertTrue(error.getMessage()
                .contains(String.format(
                        InvariantParticipantModelStatePartTest.NO_REPORT_IN_TIME, TIMESTAMP_FINISH + buffer)));
    }

    /**
     * Tests whether the test fails, when the handle in the manipulation data parameters is unknown.
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54760NoMetricWithExpectedHandle() throws Exception {
        requirement547SetUp(
                // this handle is not present in mdib
                SOME_NON_EXISTENT_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.SET,
                org.somda.sdc.biceps.model.participant.ComponentActivation.ON,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                SET_METRIC_HANDLE,
                ComponentActivation.ON,
                TIMESTAMP_IN_INTERVAL);
        final var error = assertThrows(AssertionError.class, testClass::testRequirement54760);
        assertTrue(error.getMessage()
                .contains(String.format(
                        InvariantParticipantModelStatePartTest.NO_METRIC_WITH_EXPECTED_HANDLE,
                        SOME_NON_EXISTENT_HANDLE)));
    }

    /**
     * Tests whether the test fails, when the metric has not the expected activation state after the manipulation.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54760BadWrongActivation() throws Exception {
        requirement547SetUp(
                SET_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.SET,
                org.somda.sdc.biceps.model.participant.ComponentActivation.ON,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                SET_METRIC_HANDLE,
                // the metric has the wrong activation state, ComponentActivation.ON is expected
                ComponentActivation.OFF,
                TIMESTAMP_IN_INTERVAL);
        final var error = assertThrows(AssertionError.class, testClass::testRequirement54760);
        assertTrue(error.getMessage()
                .contains(String.format(
                        InvariantParticipantModelStatePartTest.WRONG_ACTIVATION_STATE,
                        SET_METRIC_HANDLE,
                        ComponentActivation.ON,
                        ComponentActivation.OFF)));
    }

    /**
     * Tests whether the test passes, when for each manipulation data for 'setMetricStatus' manipulations and metrics
     * with category 'SET' a metric with the expected activation state exists and is in the time interval of the manipulation data.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54760Good() throws Exception {
        requirement547SetUp(
                SET_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.SET,
                org.somda.sdc.biceps.model.participant.ComponentActivation.ON,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                SET_METRIC_HANDLE,
                ComponentActivation.ON,
                TIMESTAMP_IN_INTERVAL);
        testClass.testRequirement54760();
    }

    /**
     * Tests whether the test correctly checks the metric with the handle from the manipulation data parameter.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54760GoodOverlappingTimeInterval() throws Exception {
        requirement547SetUp(
                SET_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.SET,
                org.somda.sdc.biceps.model.participant.ComponentActivation.ON,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                SET_METRIC_HANDLE,
                ComponentActivation.ON,
                TIMESTAMP_IN_INTERVAL);

        final var parameters2 = ManipulationParameterUtil.buildMetricStatusManipulationParameterData(
                MdibBuilder.DEFAULT_SEQUENCE_ID,
                SET_METRIC_HANDLE2,
                org.somda.sdc.biceps.model.participant.MetricCategory.SET,
                org.somda.sdc.biceps.model.participant.ComponentActivation.ON);
        messageStorageUtil.addManipulation(
                storage,
                TIMESTAMP_START2,
                TIMESTAMP_FINISH2,
                ResponseTypes.Result.RESULT_SUCCESS,
                "", // not used by test
                Constants.MANIPULATION_NAME_SET_METRIC_STATUS,
                parameters2);
        final var metricReport2 = buildMetricReport(
                SEQUENCE_ID, BigInteger.ONE, BigInteger.ONE, SET_METRIC_HANDLE2, ComponentActivation.ON);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL2, metricReport2));

        testClass.testRequirement54760();
    }

    /**
     * Tests whether the test passes when the last update of the activation state of the metric until the end timestamp is as expected.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54760GoodMultipleReportsInInterval() throws Exception {
        requirement547SetUp(
                SET_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.SET,
                org.somda.sdc.biceps.model.participant.ComponentActivation.ON,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                SET_METRIC_HANDLE,
                ComponentActivation.ON,
                TIMESTAMP_IN_INTERVAL);
        // add another report for the same metric handle with the wrong activation state
        final var metricReport2 = buildMetricReport(
                SEQUENCE_ID, BigInteger.ONE, BigInteger.ONE, SET_METRIC_HANDLE, ComponentActivation.OFF);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport2));

        testClass.testRequirement54760();
    }

    /**
     * Tests whether the test fails when the last update of the activation state of the metric until the end timestamp is not as expected.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54760BadMultipleReportsInInterval() throws Exception {
        requirement547SetUp(
                SET_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.SET,
                org.somda.sdc.biceps.model.participant.ComponentActivation.ON,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                SET_METRIC_HANDLE,
                ComponentActivation.ON,
                TIMESTAMP_IN_INTERVAL);
        // add another report for the same metric handle with the wrong activation state
        final var metricReport2 = buildMetricReport(
                SEQUENCE_ID, BigInteger.valueOf(10), BigInteger.ONE, SET_METRIC_HANDLE, ComponentActivation.OFF);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport2));

        assertThrows(AssertionError.class, testClass::testRequirement54760);
    }

    /**
     * Tests whether no test data fails the test.
     */
    @Test
    public void testRequirement5477NoTestData() {
        assertThrows(NoTestData.class, testClass::testRequirement5477);
    }

    /**
     * Tests whether the test fails when no manipulation data with ResponseTypes.Result.RESULT_SUCCESS is in storage.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5477NoSuccessfulManipulation() throws Exception {
        requirement547SetUp(
                SET_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.SET,
                org.somda.sdc.biceps.model.participant.ComponentActivation.NOT_RDY,
                // no manipulation with result success
                ResponseTypes.Result.RESULT_FAIL,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                SET_METRIC_HANDLE,
                ComponentActivation.NOT_RDY,
                TIMESTAMP_IN_INTERVAL);

        final var error = assertThrows(NoTestData.class, testClass::testRequirement5477);
        assertTrue(error.getMessage().contains(InvariantParticipantModelStatePartTest.NO_SUCCESSFUL_MANIPULATION));
    }

    /**
     * Test whether the test fails, when no manipulation data with category 'SET' is in storage.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5477WrongMetricCategory() throws Exception {
        requirement547SetUp(
                SET_METRIC_HANDLE,
                // the test expects manipulations with metric category SET
                org.somda.sdc.biceps.model.participant.MetricCategory.CLC,
                org.somda.sdc.biceps.model.participant.ComponentActivation.NOT_RDY,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                SET_METRIC_HANDLE,
                ComponentActivation.NOT_RDY,
                TIMESTAMP_IN_INTERVAL);

        final var error = assertThrows(NoTestData.class, testClass::testRequirement5477);
        assertTrue(error.getMessage()
                .contains(String.format(
                        InvariantParticipantModelStatePartTest.NO_SET_METRIC_STATUS_MANIPULATION, MetricCategory.SET)));
    }

    /**
     * Tests whether the test fails, when no metric report is present with timestamp less than the manipulation end timestamp + buffer.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5477NoReportUntilEndTimestamp() throws Exception {
        requirement547SetUp(
                SET_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.SET,
                org.somda.sdc.biceps.model.participant.ComponentActivation.NOT_RDY,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                SET_METRIC_HANDLE,
                ComponentActivation.NOT_RDY,
                // the timestamp of the report is not < manipulation end timestamp + buffer
                TIMESTAMP_FINISH + buffer);
        final var error = assertThrows(AssertionError.class, testClass::testRequirement5477);
        assertTrue(error.getMessage()
                .contains(String.format(
                        InvariantParticipantModelStatePartTest.NO_REPORT_IN_TIME, TIMESTAMP_FINISH + buffer)));
    }

    /**
     * Tests whether the test fails, when the handle in the manipulation data parameters is unknown.
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5477NoMetricWithExpectedHandle() throws Exception {
        requirement547SetUp(
                // this handle is not present in mdib
                SOME_NON_EXISTENT_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.SET,
                org.somda.sdc.biceps.model.participant.ComponentActivation.NOT_RDY,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                SET_METRIC_HANDLE,
                ComponentActivation.NOT_RDY,
                TIMESTAMP_IN_INTERVAL);
        final var error = assertThrows(AssertionError.class, testClass::testRequirement5477);
        assertTrue(error.getMessage()
                .contains(String.format(
                        InvariantParticipantModelStatePartTest.NO_METRIC_WITH_EXPECTED_HANDLE,
                        SOME_NON_EXISTENT_HANDLE)));
    }

    /**
     * Tests whether the test fails, when the metric has not the expected activation state after the manipulation.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5477BadWrongActivation() throws Exception {
        requirement547SetUp(
                SET_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.SET,
                org.somda.sdc.biceps.model.participant.ComponentActivation.NOT_RDY,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                SET_METRIC_HANDLE,
                // the metric has the wrong activation state, ComponentActivation.NOT_RDY is expected
                ComponentActivation.OFF,
                TIMESTAMP_IN_INTERVAL);
        final var error = assertThrows(AssertionError.class, testClass::testRequirement5477);
        assertTrue(error.getMessage()
                .contains(String.format(
                        InvariantParticipantModelStatePartTest.WRONG_ACTIVATION_STATE,
                        SET_METRIC_HANDLE,
                        ComponentActivation.NOT_RDY,
                        ComponentActivation.OFF)));
    }

    /**
     * Tests whether the test passes, when for each manipulation data for 'setMetricStatus' manipulations and metrics
     * with category 'SET' a metric with the expected activation state exists and is in the time interval of the manipulation data.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5477Good() throws Exception {
        requirement547SetUp(
                SET_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.SET,
                org.somda.sdc.biceps.model.participant.ComponentActivation.NOT_RDY,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                SET_METRIC_HANDLE,
                ComponentActivation.NOT_RDY,
                TIMESTAMP_IN_INTERVAL);
        testClass.testRequirement5477();
    }

    /**
     * Tests whether the test correctly checks the metric with the handle from the manipulation data parameter.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5477GoodOverlappingTimeInterval() throws Exception {
        requirement547SetUp(
                SET_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.SET,
                org.somda.sdc.biceps.model.participant.ComponentActivation.NOT_RDY,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                SET_METRIC_HANDLE,
                ComponentActivation.NOT_RDY,
                TIMESTAMP_IN_INTERVAL);

        final var parameters2 = ManipulationParameterUtil.buildMetricStatusManipulationParameterData(
                MdibBuilder.DEFAULT_SEQUENCE_ID,
                SET_METRIC_HANDLE2,
                org.somda.sdc.biceps.model.participant.MetricCategory.SET,
                org.somda.sdc.biceps.model.participant.ComponentActivation.NOT_RDY);
        messageStorageUtil.addManipulation(
                storage,
                TIMESTAMP_START2,
                TIMESTAMP_FINISH2,
                ResponseTypes.Result.RESULT_SUCCESS,
                "", // not used by test
                Constants.MANIPULATION_NAME_SET_METRIC_STATUS,
                parameters2);
        final var metricReport2 = buildMetricReport(
                SEQUENCE_ID, BigInteger.ONE, BigInteger.ONE, SET_METRIC_HANDLE2, ComponentActivation.NOT_RDY);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL2, metricReport2));

        testClass.testRequirement5477();
    }

    /**
     * Tests whether the test passes when the last update of the activation state of the metric until the end timestamp is as expected.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5477GoodMultipleReportsInInterval() throws Exception {
        requirement547SetUp(
                SET_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.SET,
                org.somda.sdc.biceps.model.participant.ComponentActivation.NOT_RDY,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                SET_METRIC_HANDLE,
                ComponentActivation.NOT_RDY,
                TIMESTAMP_IN_INTERVAL);
        // add another report for the same metric handle with the wrong activation state
        final var metricReport2 = buildMetricReport(
                SEQUENCE_ID, BigInteger.ONE, BigInteger.ONE, SET_METRIC_HANDLE, ComponentActivation.OFF);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport2));

        testClass.testRequirement5477();
    }

    /**
     * Tests whether the test fails when the last update of the activation state of the metric until the end timestamp is not as expected.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5477BadMultipleReportsInInterval() throws Exception {
        requirement547SetUp(
                SET_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.SET,
                org.somda.sdc.biceps.model.participant.ComponentActivation.NOT_RDY,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                SET_METRIC_HANDLE,
                ComponentActivation.NOT_RDY,
                TIMESTAMP_IN_INTERVAL);
        // add another report for the same metric handle with the wrong activation state
        final var metricReport2 = buildMetricReport(
                SEQUENCE_ID, BigInteger.valueOf(10), BigInteger.ONE, SET_METRIC_HANDLE, ComponentActivation.OFF);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport2));

        assertThrows(AssertionError.class, testClass::testRequirement5477);
    }

    /**
     * Tests whether no test data fails the test.
     */
    @Test
    public void testRequirement5478NoTestData() {
        assertThrows(NoTestData.class, testClass::testRequirement5478);
    }

    /**
     * Tests whether the test fails when no manipulation data with ResponseTypes.Result.RESULT_SUCCESS is in storage.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5478NoSuccessfulManipulation() throws Exception {
        requirement547SetUp(
                SET_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.SET,
                org.somda.sdc.biceps.model.participant.ComponentActivation.STND_BY,
                // no manipulation with result success
                ResponseTypes.Result.RESULT_FAIL,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                SET_METRIC_HANDLE,
                ComponentActivation.STND_BY,
                TIMESTAMP_IN_INTERVAL);

        final var error = assertThrows(NoTestData.class, testClass::testRequirement5478);
        assertTrue(error.getMessage().contains(InvariantParticipantModelStatePartTest.NO_SUCCESSFUL_MANIPULATION));
    }

    /**
     * Test whether the test fails, when no manipulation data with category 'SET' is in storage.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5478WrongMetricCategory() throws Exception {
        requirement547SetUp(
                SET_METRIC_HANDLE,
                // the test expects manipulations with metric category SET
                org.somda.sdc.biceps.model.participant.MetricCategory.CLC,
                org.somda.sdc.biceps.model.participant.ComponentActivation.STND_BY,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                SET_METRIC_HANDLE,
                ComponentActivation.STND_BY,
                TIMESTAMP_IN_INTERVAL);

        final var error = assertThrows(NoTestData.class, testClass::testRequirement5478);
        assertTrue(error.getMessage()
                .contains(String.format(
                        InvariantParticipantModelStatePartTest.NO_SET_METRIC_STATUS_MANIPULATION, MetricCategory.SET)));
    }

    /**
     * Tests whether the test fails, when no metric report is present with timestamp less than the manipulation end timestamp + buffer.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5478NoReportUntilEndTimestamp() throws Exception {
        requirement547SetUp(
                SET_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.SET,
                org.somda.sdc.biceps.model.participant.ComponentActivation.STND_BY,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                SET_METRIC_HANDLE,
                ComponentActivation.STND_BY,
                // the timestamp of the report is not < manipulation end timestamp + buffer
                TIMESTAMP_FINISH + buffer);
        final var error = assertThrows(AssertionError.class, testClass::testRequirement5478);
        assertTrue(error.getMessage()
                .contains(String.format(
                        InvariantParticipantModelStatePartTest.NO_REPORT_IN_TIME, TIMESTAMP_FINISH + buffer)));
    }

    /**
     * Tests whether the test fails, when the handle in the manipulation data parameters is unknown.
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5478NoMetricWithExpectedHandle() throws Exception {
        requirement547SetUp(
                // this handle is not present in mdib
                SOME_NON_EXISTENT_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.SET,
                org.somda.sdc.biceps.model.participant.ComponentActivation.STND_BY,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                SET_METRIC_HANDLE,
                ComponentActivation.STND_BY,
                TIMESTAMP_IN_INTERVAL);
        final var error = assertThrows(AssertionError.class, testClass::testRequirement5478);
        assertTrue(error.getMessage()
                .contains(String.format(
                        InvariantParticipantModelStatePartTest.NO_METRIC_WITH_EXPECTED_HANDLE,
                        SOME_NON_EXISTENT_HANDLE)));
    }

    /**
     * Tests whether the test fails, when the metric has not the expected activation state after the manipulation.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5478BadWrongActivation() throws Exception {
        requirement547SetUp(
                SET_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.SET,
                org.somda.sdc.biceps.model.participant.ComponentActivation.STND_BY,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                SET_METRIC_HANDLE,
                // the metric has the wrong activation state, ComponentActivation.STND_BY is expected
                ComponentActivation.OFF,
                TIMESTAMP_IN_INTERVAL);
        final var error = assertThrows(AssertionError.class, testClass::testRequirement5478);
        assertTrue(error.getMessage()
                .contains(String.format(
                        InvariantParticipantModelStatePartTest.WRONG_ACTIVATION_STATE,
                        SET_METRIC_HANDLE,
                        ComponentActivation.STND_BY,
                        ComponentActivation.OFF)));
    }

    /**
     * Tests whether the test passes, when for each manipulation data for 'setMetricStatus' manipulations and metrics
     * with category 'SET' a metric with the expected activation state exists and is in the time interval of the manipulation data.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5478Good() throws Exception {
        requirement547SetUp(
                SET_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.SET,
                org.somda.sdc.biceps.model.participant.ComponentActivation.STND_BY,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                SET_METRIC_HANDLE,
                ComponentActivation.STND_BY,
                TIMESTAMP_IN_INTERVAL);
        testClass.testRequirement5478();
    }

    /**
     * Tests whether the test correctly checks the metric with the handle from the manipulation data parameter.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5478GoodOverlappingTimeInterval() throws Exception {
        requirement547SetUp(
                SET_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.SET,
                org.somda.sdc.biceps.model.participant.ComponentActivation.STND_BY,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                SET_METRIC_HANDLE,
                ComponentActivation.STND_BY,
                TIMESTAMP_IN_INTERVAL);

        final var parameters2 = ManipulationParameterUtil.buildMetricStatusManipulationParameterData(
                MdibBuilder.DEFAULT_SEQUENCE_ID,
                SET_METRIC_HANDLE2,
                org.somda.sdc.biceps.model.participant.MetricCategory.SET,
                org.somda.sdc.biceps.model.participant.ComponentActivation.STND_BY);
        messageStorageUtil.addManipulation(
                storage,
                TIMESTAMP_START2,
                TIMESTAMP_FINISH2,
                ResponseTypes.Result.RESULT_SUCCESS,
                "", // not used by test
                Constants.MANIPULATION_NAME_SET_METRIC_STATUS,
                parameters2);
        final var metricReport2 = buildMetricReport(
                SEQUENCE_ID, BigInteger.ONE, BigInteger.ONE, SET_METRIC_HANDLE2, ComponentActivation.STND_BY);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL2, metricReport2));

        testClass.testRequirement5478();
    }

    /**
     * Tests whether the test passes when the last update of the activation state of the metric until the end timestamp is as expected.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5478GoodMultipleReportsInInterval() throws Exception {
        requirement547SetUp(
                SET_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.SET,
                org.somda.sdc.biceps.model.participant.ComponentActivation.STND_BY,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                SET_METRIC_HANDLE,
                ComponentActivation.STND_BY,
                TIMESTAMP_IN_INTERVAL);
        // add another report for the same metric handle with the wrong activation state
        final var metricReport2 = buildMetricReport(
                SEQUENCE_ID, BigInteger.ONE, BigInteger.ONE, SET_METRIC_HANDLE, ComponentActivation.OFF);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport2));

        testClass.testRequirement5478();
    }

    /**
     * Tests whether the test fails when the last update of the activation state of the metric until the end timestamp is not as expected.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5478BadMultipleReportsInInterval() throws Exception {
        requirement547SetUp(
                SET_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.SET,
                org.somda.sdc.biceps.model.participant.ComponentActivation.STND_BY,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                SET_METRIC_HANDLE,
                ComponentActivation.STND_BY,
                TIMESTAMP_IN_INTERVAL);
        // add another report for the same metric handle with the wrong activation state
        final var metricReport2 = buildMetricReport(
                SEQUENCE_ID, BigInteger.valueOf(10), BigInteger.ONE, SET_METRIC_HANDLE, ComponentActivation.OFF);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport2));

        assertThrows(AssertionError.class, testClass::testRequirement5478);
    }

    /**
     * Tests whether no test data fails the test.
     */
    @Test
    public void testRequirement5479NoTestData() {
        assertThrows(NoTestData.class, testClass::testRequirement5479);
    }

    /**
     * Tests whether the test fails when no manipulation data with ResponseTypes.Result.RESULT_SUCCESS is in storage.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5479NoSuccessfulManipulation() throws Exception {
        requirement547SetUp(
                SET_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.SET,
                org.somda.sdc.biceps.model.participant.ComponentActivation.SHTDN,
                // no manipulation with result success
                ResponseTypes.Result.RESULT_FAIL,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                SET_METRIC_HANDLE,
                ComponentActivation.SHTDN,
                TIMESTAMP_IN_INTERVAL);

        final var error = assertThrows(NoTestData.class, testClass::testRequirement5479);
        assertTrue(error.getMessage().contains(InvariantParticipantModelStatePartTest.NO_SUCCESSFUL_MANIPULATION));
    }

    /**
     * Test whether the test fails, when no manipulation data with category 'SET' is in storage.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5479WrongMetricCategory() throws Exception {
        requirement547SetUp(
                SET_METRIC_HANDLE,
                // the test expects manipulations with metric category SET
                org.somda.sdc.biceps.model.participant.MetricCategory.MSRMT,
                org.somda.sdc.biceps.model.participant.ComponentActivation.SHTDN,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                SET_METRIC_HANDLE,
                ComponentActivation.SHTDN,
                TIMESTAMP_IN_INTERVAL);

        final var error = assertThrows(NoTestData.class, testClass::testRequirement5479);
        assertTrue(error.getMessage()
                .contains(String.format(
                        InvariantParticipantModelStatePartTest.NO_SET_METRIC_STATUS_MANIPULATION, MetricCategory.SET)));
    }

    /**
     * Tests whether the test fails, when no metric report is present with timestamp less than the manipulation end timestamp + buffer.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5479NoReportUntilEndTimestamp() throws Exception {
        requirement547SetUp(
                SET_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.SET,
                org.somda.sdc.biceps.model.participant.ComponentActivation.SHTDN,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                SET_METRIC_HANDLE,
                ComponentActivation.SHTDN,
                // the timestamp of the report is not < manipulation end timestamp + buffer
                TIMESTAMP_FINISH + buffer);
        final var error = assertThrows(AssertionError.class, testClass::testRequirement5479);
        assertTrue(error.getMessage()
                .contains(String.format(
                        InvariantParticipantModelStatePartTest.NO_REPORT_IN_TIME, TIMESTAMP_FINISH + buffer)));
    }

    /**
     * Tests whether the test fails, when the handle in the manipulation data parameters is unknown.
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5479NoMetricWithExpectedHandle() throws Exception {
        requirement547SetUp(
                // this handle is not present in mdib
                SOME_NON_EXISTENT_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.SET,
                org.somda.sdc.biceps.model.participant.ComponentActivation.SHTDN,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                SET_METRIC_HANDLE,
                ComponentActivation.SHTDN,
                TIMESTAMP_IN_INTERVAL);
        final var error = assertThrows(AssertionError.class, testClass::testRequirement5479);
        assertTrue(error.getMessage()
                .contains(String.format(
                        InvariantParticipantModelStatePartTest.NO_METRIC_WITH_EXPECTED_HANDLE,
                        SOME_NON_EXISTENT_HANDLE)));
    }

    /**
     * Tests whether the test fails, when the metric has not the expected activation state after the manipulation.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5479BadWrongActivation() throws Exception {
        requirement547SetUp(
                SET_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.SET,
                org.somda.sdc.biceps.model.participant.ComponentActivation.SHTDN,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                SET_METRIC_HANDLE,
                // the metric has the wrong activation state, ComponentActivation.SHTDN is expected
                ComponentActivation.OFF,
                TIMESTAMP_IN_INTERVAL);
        final var error = assertThrows(AssertionError.class, testClass::testRequirement5479);
        assertTrue(error.getMessage()
                .contains(String.format(
                        InvariantParticipantModelStatePartTest.WRONG_ACTIVATION_STATE,
                        SET_METRIC_HANDLE,
                        ComponentActivation.SHTDN,
                        ComponentActivation.OFF)));
    }

    /**
     * Tests whether the test passes, when for each manipulation data for 'setMetricStatus' manipulations and metrics
     * with category 'SET' a metric with the expected activation state exists and is in the time interval of the manipulation data.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5479Good() throws Exception {
        requirement547SetUp(
                SET_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.SET,
                org.somda.sdc.biceps.model.participant.ComponentActivation.SHTDN,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                SET_METRIC_HANDLE,
                ComponentActivation.SHTDN,
                TIMESTAMP_IN_INTERVAL);
        testClass.testRequirement5479();
    }

    /**
     * Tests whether the test correctly checks the metric with the handle from the manipulation data parameter.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5479GoodOverlappingTimeInterval() throws Exception {
        requirement547SetUp(
                SET_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.SET,
                org.somda.sdc.biceps.model.participant.ComponentActivation.SHTDN,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                SET_METRIC_HANDLE,
                ComponentActivation.SHTDN,
                TIMESTAMP_IN_INTERVAL);

        final var parameters2 = ManipulationParameterUtil.buildMetricStatusManipulationParameterData(
                MdibBuilder.DEFAULT_SEQUENCE_ID,
                SET_METRIC_HANDLE2,
                org.somda.sdc.biceps.model.participant.MetricCategory.SET,
                org.somda.sdc.biceps.model.participant.ComponentActivation.SHTDN);
        messageStorageUtil.addManipulation(
                storage,
                TIMESTAMP_START2,
                TIMESTAMP_FINISH2,
                ResponseTypes.Result.RESULT_SUCCESS,
                "", // not used by test
                Constants.MANIPULATION_NAME_SET_METRIC_STATUS,
                parameters2);
        final var metricReport2 = buildMetricReport(
                SEQUENCE_ID, BigInteger.ONE, BigInteger.ONE, SET_METRIC_HANDLE2, ComponentActivation.SHTDN);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL2, metricReport2));

        testClass.testRequirement5479();
    }

    /**
     * Tests whether the test passes when the last update of the activation state of the metric until the end timestamp is as expected.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5479GoodMultipleReportsInInterval() throws Exception {
        requirement547SetUp(
                SET_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.SET,
                org.somda.sdc.biceps.model.participant.ComponentActivation.SHTDN,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                SET_METRIC_HANDLE,
                ComponentActivation.SHTDN,
                TIMESTAMP_IN_INTERVAL);
        // add another report for the same metric handle with the wrong activation state
        final var metricReport2 = buildMetricReport(
                SEQUENCE_ID, BigInteger.ONE, BigInteger.ONE, SET_METRIC_HANDLE, ComponentActivation.OFF);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport2));

        testClass.testRequirement5479();
    }

    /**
     * Tests whether the test fails when the last update of the activation state of the metric until the end timestamp is not as expected.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5479BadMultipleReportsInInterval() throws Exception {
        requirement547SetUp(
                SET_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.SET,
                org.somda.sdc.biceps.model.participant.ComponentActivation.SHTDN,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                SET_METRIC_HANDLE,
                ComponentActivation.SHTDN,
                TIMESTAMP_IN_INTERVAL);
        // add another report for the same metric handle with the wrong activation state
        final var metricReport2 = buildMetricReport(
                SEQUENCE_ID, BigInteger.valueOf(10), BigInteger.ONE, SET_METRIC_HANDLE, ComponentActivation.OFF);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport2));

        assertThrows(AssertionError.class, testClass::testRequirement5479);
    }

    /**
     * Tests whether no test data fails the test.
     */
    @Test
    public void testRequirement54710NoTestData() {
        assertThrows(NoTestData.class, testClass::testRequirement54710);
    }

    /**
     * Tests whether the test fails when no manipulation data with ResponseTypes.Result.RESULT_SUCCESS is in storage.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54710NoSuccessfulManipulation() throws Exception {
        requirement547SetUp(
                SET_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.SET,
                org.somda.sdc.biceps.model.participant.ComponentActivation.OFF,
                // no manipulation with result success
                ResponseTypes.Result.RESULT_FAIL,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                SET_METRIC_HANDLE,
                ComponentActivation.OFF,
                TIMESTAMP_IN_INTERVAL);

        final var error = assertThrows(NoTestData.class, testClass::testRequirement54710);
        assertTrue(error.getMessage().contains(InvariantParticipantModelStatePartTest.NO_SUCCESSFUL_MANIPULATION));
    }

    /**
     * Test whether the test fails, when no manipulation data with category 'SET' is in storage.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54710WrongMetricCategory() throws Exception {
        requirement547SetUp(
                SET_METRIC_HANDLE,
                // the test expects manipulations with metric category SET
                org.somda.sdc.biceps.model.participant.MetricCategory.MSRMT,
                org.somda.sdc.biceps.model.participant.ComponentActivation.OFF,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                SET_METRIC_HANDLE,
                ComponentActivation.OFF,
                TIMESTAMP_IN_INTERVAL);

        final var error = assertThrows(NoTestData.class, testClass::testRequirement54710);
        assertTrue(error.getMessage()
                .contains(String.format(
                        InvariantParticipantModelStatePartTest.NO_SET_METRIC_STATUS_MANIPULATION, MetricCategory.SET)));
    }

    /**
     * Tests whether the test fails, when no metric report is present with timestamp less than the manipulation end timestamp + buffer.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54710NoReportUntilEndTimestamp() throws Exception {
        requirement547SetUp(
                SET_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.SET,
                org.somda.sdc.biceps.model.participant.ComponentActivation.OFF,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                SET_METRIC_HANDLE,
                ComponentActivation.OFF,
                // the timestamp of the report is not < manipulation end timestamp + buffer
                TIMESTAMP_FINISH + buffer);
        final var error = assertThrows(AssertionError.class, testClass::testRequirement54710);
        assertTrue(error.getMessage()
                .contains(String.format(
                        InvariantParticipantModelStatePartTest.NO_REPORT_IN_TIME, TIMESTAMP_FINISH + buffer)));
    }

    /**
     * Tests whether the test fails, when the handle in the manipulation data parameters is unknown.
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54710NoMetricWithExpectedHandle() throws Exception {
        requirement547SetUp(
                // this handle is not present in mdib
                SOME_NON_EXISTENT_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.SET,
                org.somda.sdc.biceps.model.participant.ComponentActivation.OFF,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                SET_METRIC_HANDLE,
                ComponentActivation.OFF,
                TIMESTAMP_IN_INTERVAL);
        final var error = assertThrows(AssertionError.class, testClass::testRequirement54710);
        assertTrue(error.getMessage()
                .contains(String.format(
                        InvariantParticipantModelStatePartTest.NO_METRIC_WITH_EXPECTED_HANDLE,
                        SOME_NON_EXISTENT_HANDLE)));
    }

    /**
     * Tests whether the test fails, when the metric has not the expected activation state after the manipulation.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54710BadWrongActivation() throws Exception {
        requirement547SetUp(
                SET_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.SET,
                org.somda.sdc.biceps.model.participant.ComponentActivation.OFF,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                SET_METRIC_HANDLE,
                // the metric has the wrong activation state, ComponentActivation.OFF is expected
                ComponentActivation.ON,
                TIMESTAMP_IN_INTERVAL);
        final var error = assertThrows(AssertionError.class, testClass::testRequirement54710);
        assertTrue(error.getMessage()
                .contains(String.format(
                        InvariantParticipantModelStatePartTest.WRONG_ACTIVATION_STATE,
                        SET_METRIC_HANDLE,
                        ComponentActivation.OFF,
                        ComponentActivation.ON)));
    }

    /**
     * Tests whether the test passes, when for each manipulation data for 'setMetricStatus' manipulations and metrics
     * with category 'SET' a metric with the expected activation state exists and is in the time interval of the manipulation data.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54710Good() throws Exception {
        requirement547SetUp(
                SET_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.SET,
                org.somda.sdc.biceps.model.participant.ComponentActivation.OFF,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                SET_METRIC_HANDLE,
                ComponentActivation.OFF,
                TIMESTAMP_IN_INTERVAL);
        testClass.testRequirement54710();
    }

    /**
     * Tests whether the test correctly checks the metric with the handle from the manipulation data parameter.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54710GoodOverlappingTimeInterval() throws Exception {
        requirement547SetUp(
                SET_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.SET,
                org.somda.sdc.biceps.model.participant.ComponentActivation.OFF,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                SET_METRIC_HANDLE,
                ComponentActivation.OFF,
                TIMESTAMP_IN_INTERVAL);

        final var parameters2 = ManipulationParameterUtil.buildMetricStatusManipulationParameterData(
                MdibBuilder.DEFAULT_SEQUENCE_ID,
                SET_METRIC_HANDLE2,
                org.somda.sdc.biceps.model.participant.MetricCategory.SET,
                org.somda.sdc.biceps.model.participant.ComponentActivation.OFF);
        messageStorageUtil.addManipulation(
                storage,
                TIMESTAMP_START2,
                TIMESTAMP_FINISH2,
                ResponseTypes.Result.RESULT_SUCCESS,
                "", // not used by test
                Constants.MANIPULATION_NAME_SET_METRIC_STATUS,
                parameters2);
        final var metricReport2 = buildMetricReport(
                SEQUENCE_ID, BigInteger.ONE, BigInteger.ONE, SET_METRIC_HANDLE2, ComponentActivation.OFF);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL2, metricReport2));

        testClass.testRequirement54710();
    }

    /**
     * Tests whether the test passes when the last update of the activation state of the metric until the end timestamp is as expected.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54710GoodMultipleReportsInInterval() throws Exception {
        requirement547SetUp(
                SET_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.SET,
                org.somda.sdc.biceps.model.participant.ComponentActivation.OFF,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                SET_METRIC_HANDLE,
                ComponentActivation.OFF,
                TIMESTAMP_IN_INTERVAL);
        // add another report for the same metric handle with the wrong activation state
        final var metricReport2 = buildMetricReport(
                SEQUENCE_ID, BigInteger.ONE, BigInteger.ONE, SET_METRIC_HANDLE, ComponentActivation.ON);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport2));

        testClass.testRequirement54710();
    }

    /**
     * Tests whether the test fails when the last update of the activation state of the metric until the end timestamp is not as expected.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54710BadMultipleReportsInInterval() throws Exception {
        requirement547SetUp(
                SET_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.SET,
                org.somda.sdc.biceps.model.participant.ComponentActivation.OFF,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                SET_METRIC_HANDLE,
                ComponentActivation.OFF,
                TIMESTAMP_IN_INTERVAL);
        // add another report for the same metric handle with the wrong activation state
        final var metricReport2 = buildMetricReport(
                SEQUENCE_ID, BigInteger.valueOf(10), BigInteger.ONE, SET_METRIC_HANDLE, ComponentActivation.ON);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport2));

        assertThrows(AssertionError.class, testClass::testRequirement54710);
    }

    /**
     * Tests whether no test data fails the test.
     */
    @Test
    public void testRequirement54711NoTestData() {
        assertThrows(NoTestData.class, testClass::testRequirement54711);
    }

    /**
     * Tests whether the test fails when no manipulation data with ResponseTypes.Result.RESULT_SUCCESS is in storage.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54711NoSuccessfulManipulation() throws Exception {
        requirement547SetUp(
                SET_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.SET,
                org.somda.sdc.biceps.model.participant.ComponentActivation.FAIL,
                // no manipulation with result success
                ResponseTypes.Result.RESULT_FAIL,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                SET_METRIC_HANDLE,
                ComponentActivation.FAIL,
                TIMESTAMP_IN_INTERVAL);

        final var error = assertThrows(NoTestData.class, testClass::testRequirement54711);
        assertTrue(error.getMessage().contains(InvariantParticipantModelStatePartTest.NO_SUCCESSFUL_MANIPULATION));
    }

    /**
     * Test whether the test fails, when no manipulation data with category 'SET' is in storage.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54711WrongMetricCategory() throws Exception {
        requirement547SetUp(
                SET_METRIC_HANDLE,
                // the test expects manipulations with metric category SET
                org.somda.sdc.biceps.model.participant.MetricCategory.MSRMT,
                org.somda.sdc.biceps.model.participant.ComponentActivation.FAIL,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                SET_METRIC_HANDLE,
                ComponentActivation.FAIL,
                TIMESTAMP_IN_INTERVAL);

        final var error = assertThrows(NoTestData.class, testClass::testRequirement54711);
        assertTrue(error.getMessage()
                .contains(String.format(
                        InvariantParticipantModelStatePartTest.NO_SET_METRIC_STATUS_MANIPULATION, MetricCategory.SET)));
    }

    /**
     * Tests whether the test fails, when no metric report is present with timestamp less than the manipulation end timestamp + buffer.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54711NoReportUntilEndTimestamp() throws Exception {
        requirement547SetUp(
                SET_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.SET,
                org.somda.sdc.biceps.model.participant.ComponentActivation.FAIL,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                SET_METRIC_HANDLE,
                ComponentActivation.FAIL,
                // the timestamp of the report is not < manipulation end timestamp + buffer
                TIMESTAMP_FINISH + buffer);
        final var error = assertThrows(AssertionError.class, testClass::testRequirement54711);
        assertTrue(error.getMessage()
                .contains(String.format(
                        InvariantParticipantModelStatePartTest.NO_REPORT_IN_TIME, TIMESTAMP_FINISH + buffer)));
    }

    /**
     * Tests whether the test fails, when the handle in the manipulation data parameters is unknown.
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54711NoMetricWithExpectedHandle() throws Exception {
        requirement547SetUp(
                // this handle is not present in mdib
                SOME_NON_EXISTENT_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.SET,
                org.somda.sdc.biceps.model.participant.ComponentActivation.FAIL,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                SET_METRIC_HANDLE,
                ComponentActivation.FAIL,
                TIMESTAMP_IN_INTERVAL);
        final var error = assertThrows(AssertionError.class, testClass::testRequirement54711);
        assertTrue(error.getMessage()
                .contains(String.format(
                        InvariantParticipantModelStatePartTest.NO_METRIC_WITH_EXPECTED_HANDLE,
                        SOME_NON_EXISTENT_HANDLE)));
    }

    /**
     * Tests whether the test fails, when the metric has not the expected activation state after the manipulation.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54711BadWrongActivation() throws Exception {
        requirement547SetUp(
                SET_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.SET,
                org.somda.sdc.biceps.model.participant.ComponentActivation.FAIL,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                SET_METRIC_HANDLE,
                // the metric has the wrong activation state, ComponentActivation.FAIL is expected
                ComponentActivation.OFF,
                TIMESTAMP_IN_INTERVAL);
        final var error = assertThrows(AssertionError.class, testClass::testRequirement54711);
        assertTrue(error.getMessage()
                .contains(String.format(
                        InvariantParticipantModelStatePartTest.WRONG_ACTIVATION_STATE,
                        SET_METRIC_HANDLE,
                        ComponentActivation.FAIL,
                        ComponentActivation.OFF)));
    }

    /**
     * Tests whether the test passes, when for each manipulation data for 'setMetricStatus' manipulations and metrics
     * with category 'SET' a metric with the expected activation state exists and is in the time interval of the manipulation data.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54711Good() throws Exception {
        requirement547SetUp(
                SET_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.SET,
                org.somda.sdc.biceps.model.participant.ComponentActivation.FAIL,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                SET_METRIC_HANDLE,
                ComponentActivation.FAIL,
                TIMESTAMP_IN_INTERVAL);
        testClass.testRequirement54711();
    }

    /**
     * Tests whether the test correctly checks the metric with the handle from the manipulation data parameter.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54711GoodOverlappingTimeInterval() throws Exception {
        requirement547SetUp(
                SET_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.SET,
                org.somda.sdc.biceps.model.participant.ComponentActivation.FAIL,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                SET_METRIC_HANDLE,
                ComponentActivation.FAIL,
                TIMESTAMP_IN_INTERVAL);

        final var parameters2 = ManipulationParameterUtil.buildMetricStatusManipulationParameterData(
                MdibBuilder.DEFAULT_SEQUENCE_ID,
                SET_METRIC_HANDLE2,
                org.somda.sdc.biceps.model.participant.MetricCategory.SET,
                org.somda.sdc.biceps.model.participant.ComponentActivation.FAIL);
        messageStorageUtil.addManipulation(
                storage,
                TIMESTAMP_START2,
                TIMESTAMP_FINISH2,
                ResponseTypes.Result.RESULT_SUCCESS,
                "", // not used by test
                Constants.MANIPULATION_NAME_SET_METRIC_STATUS,
                parameters2);
        final var metricReport2 = buildMetricReport(
                SEQUENCE_ID, BigInteger.ONE, BigInteger.ONE, SET_METRIC_HANDLE2, ComponentActivation.FAIL);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL2, metricReport2));

        testClass.testRequirement54711();
    }

    /**
     * Tests whether the test passes when the last update of the activation state of the metric until the end timestamp is as expected.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54711GoodMultipleReportsInInterval() throws Exception {
        requirement547SetUp(
                SET_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.SET,
                org.somda.sdc.biceps.model.participant.ComponentActivation.FAIL,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                SET_METRIC_HANDLE,
                ComponentActivation.FAIL,
                TIMESTAMP_IN_INTERVAL);
        // add another report for the same metric handle with the wrong activation state
        final var metricReport2 = buildMetricReport(
                SEQUENCE_ID, BigInteger.ONE, BigInteger.ONE, SET_METRIC_HANDLE, ComponentActivation.OFF);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport2));

        testClass.testRequirement54711();
    }

    /**
     * Tests whether the test fails when the last update of the activation state of the metric until the end timestamp is not as expected.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54711BadMultipleReportsInInterval() throws Exception {
        requirement547SetUp(
                SET_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.SET,
                org.somda.sdc.biceps.model.participant.ComponentActivation.FAIL,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                SET_METRIC_HANDLE,
                ComponentActivation.FAIL,
                TIMESTAMP_IN_INTERVAL);
        // add another report for the same metric handle with the wrong activation state
        final var metricReport2 = buildMetricReport(
                SEQUENCE_ID, BigInteger.valueOf(10), BigInteger.ONE, SET_METRIC_HANDLE, ComponentActivation.OFF);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport2));

        assertThrows(AssertionError.class, testClass::testRequirement54711);
    }

    /**
     * Tests whether no test data fails the test.
     */
    @Test
    public void testRequirement547120NoTestData() {
        assertThrows(NoTestData.class, testClass::testRequirement547120);
    }

    /**
     * Tests whether the test fails when no manipulation data with ResponseTypes.Result.RESULT_SUCCESS is in storage.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement547120NoSuccessfulManipulation() throws Exception {
        requirement547SetUp(
                CLC_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.CLC,
                org.somda.sdc.biceps.model.participant.ComponentActivation.ON,
                // no manipulation with result success
                ResponseTypes.Result.RESULT_FAIL,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                CLC_METRIC_HANDLE,
                ComponentActivation.ON,
                TIMESTAMP_IN_INTERVAL);

        final var error = assertThrows(NoTestData.class, testClass::testRequirement547120);
        assertTrue(error.getMessage().contains(InvariantParticipantModelStatePartTest.NO_SUCCESSFUL_MANIPULATION));
    }

    /**
     * Test whether the test fails, when no manipulation data with category 'CLC' is in storage.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement547120WrongMetricCategory() throws Exception {
        requirement547SetUp(
                CLC_METRIC_HANDLE,
                // the test expects manipulations with metric category CLC
                org.somda.sdc.biceps.model.participant.MetricCategory.SET,
                org.somda.sdc.biceps.model.participant.ComponentActivation.ON,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                CLC_METRIC_HANDLE,
                ComponentActivation.ON,
                TIMESTAMP_IN_INTERVAL);

        final var error = assertThrows(NoTestData.class, testClass::testRequirement547120);
        assertTrue(error.getMessage()
                .contains(String.format(
                        InvariantParticipantModelStatePartTest.NO_SET_METRIC_STATUS_MANIPULATION, MetricCategory.CLC)));
    }

    /**
     * Tests whether the test fails, when no metric report is present with timestamp less than the manipulation end timestamp + buffer.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement547120NoReportUntilEndTimestamp() throws Exception {
        requirement547SetUp(
                CLC_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.CLC,
                org.somda.sdc.biceps.model.participant.ComponentActivation.ON,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                CLC_METRIC_HANDLE,
                ComponentActivation.ON,
                // the timestamp of the report is not < manipulation end timestamp + buffer
                TIMESTAMP_FINISH + buffer);
        final var error = assertThrows(AssertionError.class, testClass::testRequirement547120);
        assertTrue(error.getMessage()
                .contains(String.format(
                        InvariantParticipantModelStatePartTest.NO_REPORT_IN_TIME, TIMESTAMP_FINISH + buffer)));
    }

    /**
     * Tests whether the test fails, when the handle in the manipulation data parameters is unknown.
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement547120NoMetricWithExpectedHandle() throws Exception {
        requirement547SetUp(
                // this handle is not present in mdib
                SOME_NON_EXISTENT_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.CLC,
                org.somda.sdc.biceps.model.participant.ComponentActivation.ON,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                CLC_METRIC_HANDLE,
                ComponentActivation.ON,
                TIMESTAMP_IN_INTERVAL);
        final var error = assertThrows(AssertionError.class, testClass::testRequirement547120);
        assertTrue(error.getMessage()
                .contains(String.format(
                        InvariantParticipantModelStatePartTest.NO_METRIC_WITH_EXPECTED_HANDLE,
                        SOME_NON_EXISTENT_HANDLE)));
    }

    /**
     * Tests whether the test fails, when the metric has not the expected activation state after the manipulation.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement547120BadWrongActivation() throws Exception {
        requirement547SetUp(
                CLC_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.CLC,
                org.somda.sdc.biceps.model.participant.ComponentActivation.ON,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                CLC_METRIC_HANDLE,
                // the metric has the wrong activation state, ComponentActivation.ON is expected
                ComponentActivation.OFF,
                TIMESTAMP_IN_INTERVAL);
        final var error = assertThrows(AssertionError.class, testClass::testRequirement547120);
        assertTrue(error.getMessage()
                .contains(String.format(
                        InvariantParticipantModelStatePartTest.WRONG_ACTIVATION_STATE,
                        CLC_METRIC_HANDLE,
                        ComponentActivation.ON,
                        ComponentActivation.OFF)));
    }

    /**
     * Tests whether the test passes, when for each manipulation data for 'setMetricStatus' manipulations and metrics
     * with category 'CLC' a metric with the expected activation state exists and is in the time interval of the manipulation data.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement547120Good() throws Exception {
        requirement547SetUp(
                CLC_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.CLC,
                org.somda.sdc.biceps.model.participant.ComponentActivation.ON,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                CLC_METRIC_HANDLE,
                ComponentActivation.ON,
                TIMESTAMP_IN_INTERVAL);
        testClass.testRequirement547120();
    }

    /**
     * Tests whether the test correctly checks the metric with the handle from the manipulation data parameter.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement547120GoodOverlappingTimeInterval() throws Exception {
        requirement547SetUp(
                CLC_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.CLC,
                org.somda.sdc.biceps.model.participant.ComponentActivation.ON,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                CLC_METRIC_HANDLE,
                ComponentActivation.ON,
                TIMESTAMP_IN_INTERVAL);

        final var parameters2 = ManipulationParameterUtil.buildMetricStatusManipulationParameterData(
                MdibBuilder.DEFAULT_SEQUENCE_ID,
                CLC_METRIC_HANDLE2,
                org.somda.sdc.biceps.model.participant.MetricCategory.CLC,
                org.somda.sdc.biceps.model.participant.ComponentActivation.ON);
        messageStorageUtil.addManipulation(
                storage,
                TIMESTAMP_START2,
                TIMESTAMP_FINISH2,
                ResponseTypes.Result.RESULT_SUCCESS,
                "", // not used by test
                Constants.MANIPULATION_NAME_SET_METRIC_STATUS,
                parameters2);
        final var metricReport2 = buildMetricReport(
                SEQUENCE_ID, BigInteger.ONE, BigInteger.ONE, CLC_METRIC_HANDLE2, ComponentActivation.ON);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL2, metricReport2));

        testClass.testRequirement547120();
    }

    /**
     * Tests whether the test passes when the last update of the activation state of the metric until the end timestamp is as expected.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement547120GoodMultipleReportsInInterval() throws Exception {
        requirement547SetUp(
                CLC_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.CLC,
                org.somda.sdc.biceps.model.participant.ComponentActivation.ON,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                CLC_METRIC_HANDLE,
                ComponentActivation.ON,
                TIMESTAMP_IN_INTERVAL);
        // add another report for the same metric handle with the wrong activation state
        final var metricReport2 = buildMetricReport(
                SEQUENCE_ID, BigInteger.ONE, BigInteger.ONE, CLC_METRIC_HANDLE, ComponentActivation.OFF);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport2));

        testClass.testRequirement547120();
    }

    /**
     * Tests whether the test fails when the last update of the activation state of the metric until the end timestamp is not as expected.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement547120BadMultipleReportsInInterval() throws Exception {
        requirement547SetUp(
                CLC_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.CLC,
                org.somda.sdc.biceps.model.participant.ComponentActivation.ON,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                CLC_METRIC_HANDLE,
                ComponentActivation.ON,
                TIMESTAMP_IN_INTERVAL);
        // add another report for the same metric handle with the wrong activation state
        final var metricReport2 = buildMetricReport(
                SEQUENCE_ID, BigInteger.valueOf(10), BigInteger.ONE, CLC_METRIC_HANDLE, ComponentActivation.OFF);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport2));

        assertThrows(AssertionError.class, testClass::testRequirement547120);
    }

    /**
     * Tests whether no test data fails the test.
     */
    @Test
    public void testRequirement54713NoTestData() {
        assertThrows(NoTestData.class, testClass::testRequirement54713);
    }

    /**
     * Tests whether the test fails when no manipulation data with ResponseTypes.Result.RESULT_SUCCESS is in storage.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54713NoSuccessfulManipulation() throws Exception {
        requirement547SetUp(
                CLC_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.CLC,
                org.somda.sdc.biceps.model.participant.ComponentActivation.NOT_RDY,
                // no manipulation with result success
                ResponseTypes.Result.RESULT_FAIL,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                CLC_METRIC_HANDLE,
                ComponentActivation.NOT_RDY,
                TIMESTAMP_IN_INTERVAL);

        final var error = assertThrows(NoTestData.class, testClass::testRequirement54713);
        assertTrue(error.getMessage().contains(InvariantParticipantModelStatePartTest.NO_SUCCESSFUL_MANIPULATION));
    }

    /**
     * Test whether the test fails, when no manipulation data with category 'CLC' is in storage.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54713WrongMetricCategory() throws Exception {
        requirement547SetUp(
                CLC_METRIC_HANDLE,
                // the test expects manipulations with metric category CLC
                org.somda.sdc.biceps.model.participant.MetricCategory.SET,
                org.somda.sdc.biceps.model.participant.ComponentActivation.NOT_RDY,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                CLC_METRIC_HANDLE,
                ComponentActivation.NOT_RDY,
                TIMESTAMP_IN_INTERVAL);

        final var error = assertThrows(NoTestData.class, testClass::testRequirement54713);
        assertTrue(error.getMessage()
                .contains(String.format(
                        InvariantParticipantModelStatePartTest.NO_SET_METRIC_STATUS_MANIPULATION, MetricCategory.CLC)));
    }

    /**
     * Tests whether the test fails, when no metric report is present with timestamp less than the manipulation end timestamp + buffer.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54713NoReportUntilEndTimestamp() throws Exception {
        requirement547SetUp(
                CLC_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.CLC,
                org.somda.sdc.biceps.model.participant.ComponentActivation.NOT_RDY,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                CLC_METRIC_HANDLE,
                ComponentActivation.NOT_RDY,
                // the timestamp of the report is not < manipulation end timestamp + buffer
                TIMESTAMP_FINISH + buffer);
        final var error = assertThrows(AssertionError.class, testClass::testRequirement54713);
        assertTrue(error.getMessage()
                .contains(String.format(
                        InvariantParticipantModelStatePartTest.NO_REPORT_IN_TIME, TIMESTAMP_FINISH + buffer)));
    }

    /**
     * Tests whether the test fails, when the handle in the manipulation data parameters is unknown.
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54713NoMetricWithExpectedHandle() throws Exception {
        requirement547SetUp(
                // this handle is not present in mdib
                SOME_NON_EXISTENT_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.CLC,
                org.somda.sdc.biceps.model.participant.ComponentActivation.NOT_RDY,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                CLC_METRIC_HANDLE,
                ComponentActivation.NOT_RDY,
                TIMESTAMP_IN_INTERVAL);
        final var error = assertThrows(AssertionError.class, testClass::testRequirement54713);
        assertTrue(error.getMessage()
                .contains(String.format(
                        InvariantParticipantModelStatePartTest.NO_METRIC_WITH_EXPECTED_HANDLE,
                        SOME_NON_EXISTENT_HANDLE)));
    }

    /**
     * Tests whether the test fails, when the metric has not the expected activation state after the manipulation.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54713BadWrongActivation() throws Exception {
        requirement547SetUp(
                CLC_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.CLC,
                org.somda.sdc.biceps.model.participant.ComponentActivation.NOT_RDY,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                CLC_METRIC_HANDLE,
                // the metric has the wrong activation state, ComponentActivation.NOT_RDY is expected
                ComponentActivation.OFF,
                TIMESTAMP_IN_INTERVAL);
        final var error = assertThrows(AssertionError.class, testClass::testRequirement54713);
        assertTrue(error.getMessage()
                .contains(String.format(
                        InvariantParticipantModelStatePartTest.WRONG_ACTIVATION_STATE,
                        CLC_METRIC_HANDLE,
                        ComponentActivation.NOT_RDY,
                        ComponentActivation.OFF)));
    }

    /**
     * Tests whether the test passes, when for each manipulation data for 'setMetricStatus' manipulations and metrics
     * with category 'CLC' a metric with the expected activation state exists and is in the time interval of the manipulation data.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54713Good() throws Exception {
        requirement547SetUp(
                CLC_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.CLC,
                org.somda.sdc.biceps.model.participant.ComponentActivation.NOT_RDY,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                CLC_METRIC_HANDLE,
                ComponentActivation.NOT_RDY,
                TIMESTAMP_IN_INTERVAL);
        testClass.testRequirement54713();
    }

    /**
     * Tests whether the test correctly checks the metric with the handle from the manipulation data parameter.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54713GoodOverlappingTimeInterval() throws Exception {
        requirement547SetUp(
                CLC_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.CLC,
                org.somda.sdc.biceps.model.participant.ComponentActivation.NOT_RDY,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                CLC_METRIC_HANDLE,
                ComponentActivation.NOT_RDY,
                TIMESTAMP_IN_INTERVAL);

        final var parameters2 = ManipulationParameterUtil.buildMetricStatusManipulationParameterData(
                MdibBuilder.DEFAULT_SEQUENCE_ID,
                CLC_METRIC_HANDLE2,
                org.somda.sdc.biceps.model.participant.MetricCategory.CLC,
                org.somda.sdc.biceps.model.participant.ComponentActivation.NOT_RDY);
        messageStorageUtil.addManipulation(
                storage,
                TIMESTAMP_START2,
                TIMESTAMP_FINISH2,
                ResponseTypes.Result.RESULT_SUCCESS,
                "", // not used by test
                Constants.MANIPULATION_NAME_SET_METRIC_STATUS,
                parameters2);
        final var metricReport2 = buildMetricReport(
                SEQUENCE_ID, BigInteger.ONE, BigInteger.ONE, CLC_METRIC_HANDLE2, ComponentActivation.NOT_RDY);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL2, metricReport2));

        testClass.testRequirement54713();
    }

    /**
     * Tests whether the test passes when the last update of the activation state of the metric until the end timestamp is as expected.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54713GoodMultipleReportsInInterval() throws Exception {
        requirement547SetUp(
                CLC_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.CLC,
                org.somda.sdc.biceps.model.participant.ComponentActivation.NOT_RDY,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                CLC_METRIC_HANDLE,
                ComponentActivation.NOT_RDY,
                TIMESTAMP_IN_INTERVAL);
        // add another report for the same metric handle with the wrong activation state
        final var metricReport2 = buildMetricReport(
                SEQUENCE_ID, BigInteger.ONE, BigInteger.ONE, CLC_METRIC_HANDLE, ComponentActivation.OFF);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport2));

        testClass.testRequirement54713();
    }

    /**
     * Tests whether the test fails when the last update of the activation state of the metric until the end timestamp is not as expected.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54713BadMultipleReportsInInterval() throws Exception {
        requirement547SetUp(
                CLC_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.CLC,
                org.somda.sdc.biceps.model.participant.ComponentActivation.NOT_RDY,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                CLC_METRIC_HANDLE,
                ComponentActivation.NOT_RDY,
                TIMESTAMP_IN_INTERVAL);
        // add another report for the same metric handle with the wrong activation state
        final var metricReport2 = buildMetricReport(
                SEQUENCE_ID, BigInteger.valueOf(10), BigInteger.ONE, CLC_METRIC_HANDLE, ComponentActivation.OFF);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport2));

        assertThrows(AssertionError.class, testClass::testRequirement54713);
    }

    /**
     * Tests whether no test data fails the test.
     */
    @Test
    public void testRequirement54714NoTestData() {
        assertThrows(NoTestData.class, testClass::testRequirement54714);
    }

    /**
     * Tests whether the test fails when no manipulation data with ResponseTypes.Result.RESULT_SUCCESS is in storage.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54714NoSuccessfulManipulation() throws Exception {
        requirement547SetUp(
                CLC_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.CLC,
                org.somda.sdc.biceps.model.participant.ComponentActivation.STND_BY,
                // no manipulation with result success
                ResponseTypes.Result.RESULT_FAIL,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                CLC_METRIC_HANDLE,
                ComponentActivation.STND_BY,
                TIMESTAMP_IN_INTERVAL);

        final var error = assertThrows(NoTestData.class, testClass::testRequirement54714);
        assertTrue(error.getMessage().contains(InvariantParticipantModelStatePartTest.NO_SUCCESSFUL_MANIPULATION));
    }

    /**
     * Test whether the test fails, when no manipulation data with category 'CLC' is in storage.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54714WrongMetricCategory() throws Exception {
        requirement547SetUp(
                CLC_METRIC_HANDLE,
                // the test expects manipulations with metric category CLC
                org.somda.sdc.biceps.model.participant.MetricCategory.SET,
                org.somda.sdc.biceps.model.participant.ComponentActivation.STND_BY,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                CLC_METRIC_HANDLE,
                ComponentActivation.STND_BY,
                TIMESTAMP_IN_INTERVAL);

        final var error = assertThrows(NoTestData.class, testClass::testRequirement54714);
        assertTrue(error.getMessage()
                .contains(String.format(
                        InvariantParticipantModelStatePartTest.NO_SET_METRIC_STATUS_MANIPULATION, MetricCategory.CLC)));
    }

    /**
     * Tests whether the test fails, when no metric report is present with timestamp less than the manipulation end timestamp + buffer.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54714NoReportUntilEndTimestamp() throws Exception {
        requirement547SetUp(
                CLC_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.CLC,
                org.somda.sdc.biceps.model.participant.ComponentActivation.STND_BY,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                CLC_METRIC_HANDLE,
                ComponentActivation.STND_BY,
                // the timestamp of the report is not < manipulation end timestamp + buffer
                TIMESTAMP_FINISH + buffer);
        final var error = assertThrows(AssertionError.class, testClass::testRequirement54714);
        assertTrue(error.getMessage()
                .contains(String.format(
                        InvariantParticipantModelStatePartTest.NO_REPORT_IN_TIME, TIMESTAMP_FINISH + buffer)));
    }

    /**
     * Tests whether the test fails, when the handle in the manipulation data parameters is unknown.
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54714NoMetricWithExpectedHandle() throws Exception {
        requirement547SetUp(
                // this handle is not present in mdib
                SOME_NON_EXISTENT_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.CLC,
                org.somda.sdc.biceps.model.participant.ComponentActivation.STND_BY,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                CLC_METRIC_HANDLE,
                ComponentActivation.STND_BY,
                TIMESTAMP_IN_INTERVAL);
        final var error = assertThrows(AssertionError.class, testClass::testRequirement54714);
        assertTrue(error.getMessage()
                .contains(String.format(
                        InvariantParticipantModelStatePartTest.NO_METRIC_WITH_EXPECTED_HANDLE,
                        SOME_NON_EXISTENT_HANDLE)));
    }

    /**
     * Tests whether the test fails, when the metric has not the expected activation state after the manipulation.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54714BadWrongActivation() throws Exception {
        requirement547SetUp(
                CLC_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.CLC,
                org.somda.sdc.biceps.model.participant.ComponentActivation.STND_BY,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                CLC_METRIC_HANDLE,
                // the metric has the wrong activation state, ComponentActivation.STND_BY is expected
                ComponentActivation.OFF,
                TIMESTAMP_IN_INTERVAL);
        final var error = assertThrows(AssertionError.class, testClass::testRequirement54714);
        assertTrue(error.getMessage()
                .contains(String.format(
                        InvariantParticipantModelStatePartTest.WRONG_ACTIVATION_STATE,
                        CLC_METRIC_HANDLE,
                        ComponentActivation.STND_BY,
                        ComponentActivation.OFF)));
    }

    /**
     * Tests whether the test passes, when for each manipulation data for 'setMetricStatus' manipulations and metrics
     * with category 'CLC' a metric with the expected activation state exists and is in the time interval of the manipulation data.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54714Good() throws Exception {
        requirement547SetUp(
                CLC_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.CLC,
                org.somda.sdc.biceps.model.participant.ComponentActivation.STND_BY,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                CLC_METRIC_HANDLE,
                ComponentActivation.STND_BY,
                TIMESTAMP_IN_INTERVAL);
        testClass.testRequirement54714();
    }

    /**
     * Tests whether the test correctly checks the metric with the handle from the manipulation data parameter.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54714GoodOverlappingTimeInterval() throws Exception {
        requirement547SetUp(
                CLC_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.CLC,
                org.somda.sdc.biceps.model.participant.ComponentActivation.STND_BY,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                CLC_METRIC_HANDLE,
                ComponentActivation.STND_BY,
                TIMESTAMP_IN_INTERVAL);

        final var parameters2 = ManipulationParameterUtil.buildMetricStatusManipulationParameterData(
                MdibBuilder.DEFAULT_SEQUENCE_ID,
                CLC_METRIC_HANDLE2,
                org.somda.sdc.biceps.model.participant.MetricCategory.CLC,
                org.somda.sdc.biceps.model.participant.ComponentActivation.STND_BY);
        messageStorageUtil.addManipulation(
                storage,
                TIMESTAMP_START2,
                TIMESTAMP_FINISH2,
                ResponseTypes.Result.RESULT_SUCCESS,
                "", // not used by test
                Constants.MANIPULATION_NAME_SET_METRIC_STATUS,
                parameters2);
        final var metricReport2 = buildMetricReport(
                SEQUENCE_ID, BigInteger.ONE, BigInteger.ONE, CLC_METRIC_HANDLE2, ComponentActivation.STND_BY);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL2, metricReport2));

        testClass.testRequirement54714();
    }

    /**
     * Tests whether the test passes when the last update of the activation state of the metric until the end timestamp is as expected.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54714GoodMultipleReportsInInterval() throws Exception {
        requirement547SetUp(
                CLC_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.CLC,
                org.somda.sdc.biceps.model.participant.ComponentActivation.STND_BY,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                CLC_METRIC_HANDLE,
                ComponentActivation.STND_BY,
                TIMESTAMP_IN_INTERVAL);
        // add another report for the same metric handle with the wrong activation state
        final var metricReport2 = buildMetricReport(
                SEQUENCE_ID, BigInteger.ONE, BigInteger.ONE, CLC_METRIC_HANDLE, ComponentActivation.OFF);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport2));

        testClass.testRequirement54714();
    }

    /**
     * Tests whether the test fails when the last update of the activation state of the metric until the end timestamp is not as expected.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54714BadMultipleReportsInInterval() throws Exception {
        requirement547SetUp(
                CLC_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.CLC,
                org.somda.sdc.biceps.model.participant.ComponentActivation.STND_BY,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                SET_METRIC_HANDLE,
                ComponentActivation.STND_BY,
                TIMESTAMP_IN_INTERVAL);
        // add another report for the same metric handle with the wrong activation state
        final var metricReport2 = buildMetricReport(
                SEQUENCE_ID, BigInteger.valueOf(10), BigInteger.ONE, CLC_METRIC_HANDLE, ComponentActivation.OFF);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport2));

        assertThrows(AssertionError.class, testClass::testRequirement54714);
    }

    /**
     * Tests whether no test data fails the test.
     */
    @Test
    public void testRequirement54715NoTestData() {
        assertThrows(NoTestData.class, testClass::testRequirement54715);
    }

    /**
     * Tests whether the test fails when no manipulation data with ResponseTypes.Result.RESULT_SUCCESS is in storage.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54715NoSuccessfulManipulation() throws Exception {
        requirement547SetUp(
                CLC_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.CLC,
                org.somda.sdc.biceps.model.participant.ComponentActivation.SHTDN,
                // no manipulation with result success
                ResponseTypes.Result.RESULT_FAIL,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                CLC_METRIC_HANDLE,
                ComponentActivation.SHTDN,
                TIMESTAMP_IN_INTERVAL);

        final var error = assertThrows(NoTestData.class, testClass::testRequirement54715);
        assertTrue(error.getMessage().contains(InvariantParticipantModelStatePartTest.NO_SUCCESSFUL_MANIPULATION));
    }

    /**
     * Test whether the test fails, when no manipulation data with category 'CLC' is in storage.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54715WrongMetricCategory() throws Exception {
        requirement547SetUp(
                CLC_METRIC_HANDLE,
                // the test expects manipulations with metric category CLC
                org.somda.sdc.biceps.model.participant.MetricCategory.SET,
                org.somda.sdc.biceps.model.participant.ComponentActivation.SHTDN,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                CLC_METRIC_HANDLE,
                ComponentActivation.SHTDN,
                TIMESTAMP_IN_INTERVAL);

        final var error = assertThrows(NoTestData.class, testClass::testRequirement54715);
        assertTrue(error.getMessage()
                .contains(String.format(
                        InvariantParticipantModelStatePartTest.NO_SET_METRIC_STATUS_MANIPULATION, MetricCategory.CLC)));
    }

    /**
     * Tests whether the test fails, when no metric report is present with timestamp less than the manipulation end timestamp + buffer.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54715NoReportUntilEndTimestamp() throws Exception {
        requirement547SetUp(
                CLC_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.CLC,
                org.somda.sdc.biceps.model.participant.ComponentActivation.SHTDN,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                CLC_METRIC_HANDLE,
                ComponentActivation.SHTDN,
                // the timestamp of the report is not < manipulation end timestamp + buffer
                TIMESTAMP_FINISH + buffer);
        final var error = assertThrows(AssertionError.class, testClass::testRequirement54715);
        assertTrue(error.getMessage()
                .contains(String.format(
                        InvariantParticipantModelStatePartTest.NO_REPORT_IN_TIME, TIMESTAMP_FINISH + buffer)));
    }

    /**
     * Tests whether the test fails, when the handle in the manipulation data parameters is unknown.
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54715NoMetricWithExpectedHandle() throws Exception {
        requirement547SetUp(
                // this handle is not present in mdib
                SOME_NON_EXISTENT_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.CLC,
                org.somda.sdc.biceps.model.participant.ComponentActivation.SHTDN,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                CLC_METRIC_HANDLE,
                ComponentActivation.SHTDN,
                TIMESTAMP_IN_INTERVAL);
        final var error = assertThrows(AssertionError.class, testClass::testRequirement54715);
        assertTrue(error.getMessage()
                .contains(String.format(
                        InvariantParticipantModelStatePartTest.NO_METRIC_WITH_EXPECTED_HANDLE,
                        SOME_NON_EXISTENT_HANDLE)));
    }

    /**
     * Tests whether the test fails, when the metric has not the expected activation state after the manipulation.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54715BadWrongActivation() throws Exception {
        requirement547SetUp(
                CLC_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.CLC,
                org.somda.sdc.biceps.model.participant.ComponentActivation.SHTDN,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                CLC_METRIC_HANDLE,
                // the metric has the wrong activation state, ComponentActivation.SHTDN is expected
                ComponentActivation.OFF,
                TIMESTAMP_IN_INTERVAL);
        final var error = assertThrows(AssertionError.class, testClass::testRequirement54715);
        assertTrue(error.getMessage()
                .contains(String.format(
                        InvariantParticipantModelStatePartTest.WRONG_ACTIVATION_STATE,
                        CLC_METRIC_HANDLE,
                        ComponentActivation.SHTDN,
                        ComponentActivation.OFF)));
    }

    /**
     * Tests whether the test passes, when for each manipulation data for 'setMetricStatus' manipulations and metrics
     * with category 'CLC' a metric with the expected activation state exists and is in the time interval of the manipulation data.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54715Good() throws Exception {
        requirement547SetUp(
                CLC_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.CLC,
                org.somda.sdc.biceps.model.participant.ComponentActivation.SHTDN,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                CLC_METRIC_HANDLE,
                ComponentActivation.SHTDN,
                TIMESTAMP_IN_INTERVAL);
        testClass.testRequirement54715();
    }

    /**
     * Tests whether the test correctly checks the metric with the handle from the manipulation data parameter.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54715GoodOverlappingTimeInterval() throws Exception {
        requirement547SetUp(
                CLC_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.CLC,
                org.somda.sdc.biceps.model.participant.ComponentActivation.SHTDN,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                CLC_METRIC_HANDLE,
                ComponentActivation.SHTDN,
                TIMESTAMP_IN_INTERVAL);

        final var parameters2 = ManipulationParameterUtil.buildMetricStatusManipulationParameterData(
                MdibBuilder.DEFAULT_SEQUENCE_ID,
                CLC_METRIC_HANDLE2,
                org.somda.sdc.biceps.model.participant.MetricCategory.CLC,
                org.somda.sdc.biceps.model.participant.ComponentActivation.SHTDN);
        messageStorageUtil.addManipulation(
                storage,
                TIMESTAMP_START2,
                TIMESTAMP_FINISH2,
                ResponseTypes.Result.RESULT_SUCCESS,
                "", // not used by test
                Constants.MANIPULATION_NAME_SET_METRIC_STATUS,
                parameters2);
        final var metricReport2 = buildMetricReport(
                SEQUENCE_ID, BigInteger.ONE, BigInteger.ONE, CLC_METRIC_HANDLE2, ComponentActivation.SHTDN);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL2, metricReport2));

        testClass.testRequirement54715();
    }

    /**
     * Tests whether the test passes when the last update of the activation state of the metric until the end timestamp is as expected.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54715GoodMultipleReportsInInterval() throws Exception {
        requirement547SetUp(
                CLC_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.CLC,
                org.somda.sdc.biceps.model.participant.ComponentActivation.SHTDN,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                CLC_METRIC_HANDLE,
                ComponentActivation.SHTDN,
                TIMESTAMP_IN_INTERVAL);
        // add another report for the same metric handle with the wrong activation state
        final var metricReport2 = buildMetricReport(
                SEQUENCE_ID, BigInteger.ONE, BigInteger.ONE, CLC_METRIC_HANDLE, ComponentActivation.OFF);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport2));

        testClass.testRequirement54715();
    }

    /**
     * Tests whether the test fails when the last update of the activation state of the metric until the end timestamp is not as expected.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54715BadMultipleReportsInInterval() throws Exception {
        requirement547SetUp(
                CLC_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.CLC,
                org.somda.sdc.biceps.model.participant.ComponentActivation.SHTDN,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                CLC_METRIC_HANDLE,
                ComponentActivation.SHTDN,
                TIMESTAMP_IN_INTERVAL);
        // add another report for the same metric handle with the wrong activation state
        final var metricReport2 = buildMetricReport(
                SEQUENCE_ID, BigInteger.valueOf(10), BigInteger.ONE, CLC_METRIC_HANDLE, ComponentActivation.OFF);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport2));

        assertThrows(AssertionError.class, testClass::testRequirement54715);
    }

    /**
     * Tests whether no test data fails the test.
     */
    @Test
    public void testRequirement54716NoTestData() {
        assertThrows(NoTestData.class, testClass::testRequirement54716);
    }

    /**
     * Tests whether the test fails when no manipulation data with ResponseTypes.Result.RESULT_SUCCESS is in storage.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54716NoSuccessfulManipulation() throws Exception {
        requirement547SetUp(
                CLC_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.CLC,
                org.somda.sdc.biceps.model.participant.ComponentActivation.OFF,
                // no manipulation with result success
                ResponseTypes.Result.RESULT_FAIL,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                CLC_METRIC_HANDLE,
                ComponentActivation.OFF,
                TIMESTAMP_IN_INTERVAL);

        final var error = assertThrows(NoTestData.class, testClass::testRequirement54716);
        assertTrue(error.getMessage().contains(InvariantParticipantModelStatePartTest.NO_SUCCESSFUL_MANIPULATION));
    }

    /**
     * Test whether the test fails, when no manipulation data with category 'CLC' is in storage.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54716WrongMetricCategory() throws Exception {
        requirement547SetUp(
                CLC_METRIC_HANDLE,
                // the test expects manipulations with metric category CLC
                org.somda.sdc.biceps.model.participant.MetricCategory.SET,
                org.somda.sdc.biceps.model.participant.ComponentActivation.OFF,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                CLC_METRIC_HANDLE,
                ComponentActivation.OFF,
                TIMESTAMP_IN_INTERVAL);

        final var error = assertThrows(NoTestData.class, testClass::testRequirement54716);
        assertTrue(error.getMessage()
                .contains(String.format(
                        InvariantParticipantModelStatePartTest.NO_SET_METRIC_STATUS_MANIPULATION, MetricCategory.CLC)));
    }

    /**
     * Tests whether the test fails, when no metric report is present with timestamp less than the manipulation end timestamp + buffer.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54716NoReportUntilEndTimestamp() throws Exception {
        requirement547SetUp(
                CLC_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.CLC,
                org.somda.sdc.biceps.model.participant.ComponentActivation.OFF,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                CLC_METRIC_HANDLE,
                ComponentActivation.OFF,
                // the timestamp of the report is not < manipulation end timestamp + buffer
                TIMESTAMP_FINISH + buffer);
        final var error = assertThrows(AssertionError.class, testClass::testRequirement54716);
        assertTrue(error.getMessage()
                .contains(String.format(
                        InvariantParticipantModelStatePartTest.NO_REPORT_IN_TIME, TIMESTAMP_FINISH + buffer)));
    }

    /**
     * Tests whether the test fails, when the handle in the manipulation data parameters is unknown.
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54716NoMetricWithExpectedHandle() throws Exception {
        requirement547SetUp(
                // this handle is not present in mdib
                SOME_NON_EXISTENT_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.CLC,
                org.somda.sdc.biceps.model.participant.ComponentActivation.OFF,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                CLC_METRIC_HANDLE,
                ComponentActivation.OFF,
                TIMESTAMP_IN_INTERVAL);
        final var error = assertThrows(AssertionError.class, testClass::testRequirement54716);
        assertTrue(error.getMessage()
                .contains(String.format(
                        InvariantParticipantModelStatePartTest.NO_METRIC_WITH_EXPECTED_HANDLE,
                        SOME_NON_EXISTENT_HANDLE)));
    }

    /**
     * Tests whether the test fails, when the metric has not the expected activation state after the manipulation.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54716BadWrongActivation() throws Exception {
        requirement547SetUp(
                CLC_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.CLC,
                org.somda.sdc.biceps.model.participant.ComponentActivation.OFF,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                CLC_METRIC_HANDLE,
                // the metric has the wrong activation state, ComponentActivation.OFF is expected
                ComponentActivation.ON,
                TIMESTAMP_IN_INTERVAL);
        final var error = assertThrows(AssertionError.class, testClass::testRequirement54716);
        assertTrue(error.getMessage()
                .contains(String.format(
                        InvariantParticipantModelStatePartTest.WRONG_ACTIVATION_STATE,
                        CLC_METRIC_HANDLE,
                        ComponentActivation.OFF,
                        ComponentActivation.ON)));
    }

    /**
     * Tests whether the test passes, when for each manipulation data for 'setMetricStatus' manipulations and metrics
     * with category 'CLC' a metric with the expected activation state exists and is in the time interval of the manipulation data.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54716Good() throws Exception {
        requirement547SetUp(
                CLC_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.CLC,
                org.somda.sdc.biceps.model.participant.ComponentActivation.OFF,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                CLC_METRIC_HANDLE,
                ComponentActivation.OFF,
                TIMESTAMP_IN_INTERVAL);
        testClass.testRequirement54716();
    }

    /**
     * Tests whether the test correctly checks the metric with the handle from the manipulation data parameter.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54716GoodOverlappingTimeInterval() throws Exception {
        requirement547SetUp(
                CLC_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.CLC,
                org.somda.sdc.biceps.model.participant.ComponentActivation.OFF,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                CLC_METRIC_HANDLE,
                ComponentActivation.OFF,
                TIMESTAMP_IN_INTERVAL);

        final var parameters2 = ManipulationParameterUtil.buildMetricStatusManipulationParameterData(
                MdibBuilder.DEFAULT_SEQUENCE_ID,
                CLC_METRIC_HANDLE2,
                org.somda.sdc.biceps.model.participant.MetricCategory.CLC,
                org.somda.sdc.biceps.model.participant.ComponentActivation.OFF);
        messageStorageUtil.addManipulation(
                storage,
                TIMESTAMP_START2,
                TIMESTAMP_FINISH2,
                ResponseTypes.Result.RESULT_SUCCESS,
                "", // not used by test
                Constants.MANIPULATION_NAME_SET_METRIC_STATUS,
                parameters2);
        final var metricReport2 = buildMetricReport(
                SEQUENCE_ID, BigInteger.ONE, BigInteger.ONE, CLC_METRIC_HANDLE2, ComponentActivation.OFF);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL2, metricReport2));

        testClass.testRequirement54716();
    }

    /**
     * Tests whether the test passes when the last update of the activation state of the metric until the end timestamp is as expected.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54716GoodMultipleReportsInInterval() throws Exception {
        requirement547SetUp(
                CLC_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.CLC,
                org.somda.sdc.biceps.model.participant.ComponentActivation.OFF,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                CLC_METRIC_HANDLE,
                ComponentActivation.OFF,
                TIMESTAMP_IN_INTERVAL);
        // add another report for the same metric handle with the wrong activation state
        final var metricReport2 = buildMetricReport(
                SEQUENCE_ID, BigInteger.ONE, BigInteger.ONE, CLC_METRIC_HANDLE, ComponentActivation.ON);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport2));

        testClass.testRequirement54716();
    }

    /**
     * Tests whether the test fails when the last update of the activation state of the metric until the end timestamp is not as expected.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54716BadMultipleReportsInInterval() throws Exception {
        requirement547SetUp(
                CLC_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.CLC,
                org.somda.sdc.biceps.model.participant.ComponentActivation.OFF,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                CLC_METRIC_HANDLE,
                ComponentActivation.OFF,
                TIMESTAMP_IN_INTERVAL);
        // add another report for the same metric handle with the wrong activation state
        final var metricReport2 = buildMetricReport(
                SEQUENCE_ID, BigInteger.valueOf(10), BigInteger.ONE, CLC_METRIC_HANDLE, ComponentActivation.ON);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport2));

        assertThrows(AssertionError.class, testClass::testRequirement54716);
    }

    /**
     * Tests whether no test data fails the test.
     */
    @Test
    public void testRequirement54717NoTestData() {
        assertThrows(NoTestData.class, testClass::testRequirement54717);
    }

    /**
     * Tests whether the test fails when no manipulation data with ResponseTypes.Result.RESULT_SUCCESS is in storage.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54717NoSuccessfulManipulation() throws Exception {
        requirement547SetUp(
                CLC_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.CLC,
                org.somda.sdc.biceps.model.participant.ComponentActivation.FAIL,
                // no manipulation with result success
                ResponseTypes.Result.RESULT_FAIL,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                CLC_METRIC_HANDLE,
                ComponentActivation.FAIL,
                TIMESTAMP_IN_INTERVAL);

        final var error = assertThrows(NoTestData.class, testClass::testRequirement54717);
        assertTrue(error.getMessage().contains(InvariantParticipantModelStatePartTest.NO_SUCCESSFUL_MANIPULATION));
    }

    /**
     * Test whether the test fails, when no manipulation data with category 'CLC' is in storage.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54717WrongMetricCategory() throws Exception {
        requirement547SetUp(
                CLC_METRIC_HANDLE,
                // the test expects manipulations with metric category CLC
                org.somda.sdc.biceps.model.participant.MetricCategory.SET,
                org.somda.sdc.biceps.model.participant.ComponentActivation.FAIL,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                CLC_METRIC_HANDLE,
                ComponentActivation.FAIL,
                TIMESTAMP_IN_INTERVAL);

        final var error = assertThrows(NoTestData.class, testClass::testRequirement54717);
        assertTrue(error.getMessage()
                .contains(String.format(
                        InvariantParticipantModelStatePartTest.NO_SET_METRIC_STATUS_MANIPULATION, MetricCategory.CLC)));
    }

    /**
     * Tests whether the test fails, when no metric report is present with timestamp less than the manipulation end timestamp + buffer.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54717NoReportUntilEndTimestamp() throws Exception {
        requirement547SetUp(
                CLC_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.CLC,
                org.somda.sdc.biceps.model.participant.ComponentActivation.FAIL,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                CLC_METRIC_HANDLE,
                ComponentActivation.FAIL,
                // the timestamp of the report is not < manipulation end timestamp + buffer
                TIMESTAMP_FINISH + buffer);
        final var error = assertThrows(AssertionError.class, testClass::testRequirement54717);
        assertTrue(error.getMessage()
                .contains(String.format(
                        InvariantParticipantModelStatePartTest.NO_REPORT_IN_TIME, TIMESTAMP_FINISH + buffer)));
    }

    /**
     * Tests whether the test fails, when the handle in the manipulation data parameters is unknown.
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54717NoMetricWithExpectedHandle() throws Exception {
        requirement547SetUp(
                // this handle is not present in mdib
                SOME_NON_EXISTENT_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.CLC,
                org.somda.sdc.biceps.model.participant.ComponentActivation.FAIL,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                CLC_METRIC_HANDLE,
                ComponentActivation.FAIL,
                TIMESTAMP_IN_INTERVAL);
        final var error = assertThrows(AssertionError.class, testClass::testRequirement54717);
        assertTrue(error.getMessage()
                .contains(String.format(
                        InvariantParticipantModelStatePartTest.NO_METRIC_WITH_EXPECTED_HANDLE,
                        SOME_NON_EXISTENT_HANDLE)));
    }

    /**
     * Tests whether the test fails, when the metric has not the expected activation state after the manipulation.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54717BadWrongActivation() throws Exception {
        requirement547SetUp(
                CLC_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.CLC,
                org.somda.sdc.biceps.model.participant.ComponentActivation.FAIL,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                CLC_METRIC_HANDLE,
                // the metric has the wrong activation state, ComponentActivation.FAIL is expected
                ComponentActivation.OFF,
                TIMESTAMP_IN_INTERVAL);
        final var error = assertThrows(AssertionError.class, testClass::testRequirement54717);
        assertTrue(error.getMessage()
                .contains(String.format(
                        InvariantParticipantModelStatePartTest.WRONG_ACTIVATION_STATE,
                        CLC_METRIC_HANDLE,
                        ComponentActivation.FAIL,
                        ComponentActivation.OFF)));
    }

    /**
     * Tests whether the test passes, when for each manipulation data for 'setMetricStatus' manipulations and metrics
     * with category 'CLC' a metric with the expected activation state exists and is in the time interval of the manipulation data.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54717Good() throws Exception {
        requirement547SetUp(
                CLC_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.CLC,
                org.somda.sdc.biceps.model.participant.ComponentActivation.FAIL,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                CLC_METRIC_HANDLE,
                ComponentActivation.FAIL,
                TIMESTAMP_IN_INTERVAL);
        testClass.testRequirement54717();
    }

    /**
     * Tests whether the test correctly checks the metric with the handle from the manipulation data parameter.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54717GoodOverlappingTimeInterval() throws Exception {
        requirement547SetUp(
                CLC_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.CLC,
                org.somda.sdc.biceps.model.participant.ComponentActivation.FAIL,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                CLC_METRIC_HANDLE,
                ComponentActivation.FAIL,
                TIMESTAMP_IN_INTERVAL);

        final var parameters2 = ManipulationParameterUtil.buildMetricStatusManipulationParameterData(
                MdibBuilder.DEFAULT_SEQUENCE_ID,
                CLC_METRIC_HANDLE2,
                org.somda.sdc.biceps.model.participant.MetricCategory.CLC,
                org.somda.sdc.biceps.model.participant.ComponentActivation.FAIL);
        messageStorageUtil.addManipulation(
                storage,
                TIMESTAMP_START2,
                TIMESTAMP_FINISH2,
                ResponseTypes.Result.RESULT_SUCCESS,
                "", // not used by test
                Constants.MANIPULATION_NAME_SET_METRIC_STATUS,
                parameters2);
        final var metricReport2 = buildMetricReport(
                SEQUENCE_ID, BigInteger.ONE, BigInteger.ONE, CLC_METRIC_HANDLE2, ComponentActivation.FAIL);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL2, metricReport2));

        testClass.testRequirement54717();
    }

    /**
     * Tests whether the test passes when the last update of the activation state of the metric until the end timestamp is as expected.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54717GoodMultipleReportsInInterval() throws Exception {
        requirement547SetUp(
                CLC_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.CLC,
                org.somda.sdc.biceps.model.participant.ComponentActivation.FAIL,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                CLC_METRIC_HANDLE,
                ComponentActivation.FAIL,
                TIMESTAMP_IN_INTERVAL);
        // add another report for the same metric handle with the wrong activation state
        final var metricReport2 = buildMetricReport(
                SEQUENCE_ID, BigInteger.ONE, BigInteger.ONE, CLC_METRIC_HANDLE, ComponentActivation.OFF);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport2));

        testClass.testRequirement54717();
    }

    /**
     * Tests whether the test fails when the last update of the activation state of the metric until the end timestamp is not as expected.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54717BadMultipleReportsInInterval() throws Exception {
        requirement547SetUp(
                CLC_METRIC_HANDLE,
                org.somda.sdc.biceps.model.participant.MetricCategory.CLC,
                org.somda.sdc.biceps.model.participant.ComponentActivation.FAIL,
                ResponseTypes.Result.RESULT_SUCCESS,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                CLC_METRIC_HANDLE,
                ComponentActivation.FAIL,
                TIMESTAMP_IN_INTERVAL);
        // add another report for the same metric handle with the wrong activation state
        final var metricReport2 = buildMetricReport(
                SEQUENCE_ID, BigInteger.valueOf(10), BigInteger.ONE, CLC_METRIC_HANDLE, ComponentActivation.OFF);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport2));

        assertThrows(AssertionError.class, testClass::testRequirement54717);
    }

    private Message buildTestMessage(final long timestamp, final Envelope envelope) throws Exception {
        final var message = mock(Message.class);
        when(message.getDirection()).thenReturn(CommunicationLog.Direction.INBOUND);
        when(message.getNanoTimestamp()).thenReturn(timestamp);
        when(message.getMessageType()).thenReturn(CommunicationLog.MessageType.RESPONSE);
        when(message.getFinalMemory()).thenReturn(getMessageBytes(envelope));
        when(message.getCommunicationContext()).thenReturn(createCommunicationContext());
        return message;
    }

    private CommunicationContext createCommunicationContext() throws CertificateException, IOException {
        return new CommunicationContext(
                new HttpApplicationInfo(ArrayListMultimap.create(), "", ""),
                new TransportInfo("https", null, null, null, null, List.of(CertificateUtil.getDummyCert())),
                null);
    }

    private byte[] getMessageBytes(final Envelope message) throws Exception {
        final JAXBElement<Envelope> envelope = messageBuilder.buildEnvelope(message);
        try (final var messageStream = new ByteArrayOutputStream()) {
            marshalling2.marshal(envelope, messageStream);
            return new ByteArrayInputStream(messageStream.toByteArray()).readAllBytes();
        }
    }

    private Envelope buildMdib(final String sequenceId) {
        final var mdib = mdibBuilder.buildMinimalMdib(sequenceId);

        final var mdState = mdib.getMdState();
        mdState.setStateVersion(BigInteger.ZERO);
        final var mdsDescriptor = mdib.getMdDescription().getMds().get(0);
        mdsDescriptor.setDescriptorVersion(BigInteger.ZERO);

        final var vmd = mdibBuilder.buildVmd(VMD_HANDLE);
        vmd.getLeft().setDescriptorVersion(BigInteger.ZERO);
        vmd.getRight().setDescriptorVersion(BigInteger.ZERO);
        mdsDescriptor.getVmd().add(vmd.getLeft());
        mdState.getState().add(vmd.getRight());

        final var channel = mdibBuilder.buildChannel(CHANNEL_HANDLE);
        channel.getRight().setOperatingHours(0L);
        vmd.getLeft().getChannel().add(channel.getLeft());
        mdState.getState().add(channel.getRight());

        final var msrmtMetric = mdibBuilder.buildStringMetric(
                MSRMT_METRIC_HANDLE, MetricCategory.MSRMT, MetricAvailability.INTR, mdibBuilder.buildCodedValue("abc"));
        msrmtMetric.getRight().setActivationState(ComponentActivation.OFF);
        msrmtMetric.getRight().setMetricValue(mdibBuilder.buildStringMetricValue("msrmtOne"));

        final var msrmtMetric2 = mdibBuilder.buildStringMetric(
                MSRMT_METRIC_HANDLE2,
                MetricCategory.MSRMT,
                MetricAvailability.CONT,
                mdibBuilder.buildCodedValue("def"));
        msrmtMetric2.getRight().setActivationState(ComponentActivation.OFF);
        msrmtMetric2.getRight().setMetricValue(mdibBuilder.buildStringMetricValue("msrmtTwo"));

        final var rtsaMetric = mdibBuilder.buildRealTimeSampleArrayMetric(
                RTSA_METRIC_HANDLE,
                MetricCategory.MSRMT,
                MetricAvailability.CONT,
                mdibBuilder.buildCodedValue("def"),
                BigDecimal.ONE,
                datatypeFactory.newDuration("P0DT0H0M30S"));
        rtsaMetric.getRight().setActivationState(ComponentActivation.OFF);
        rtsaMetric
                .getRight()
                .setMetricValue(
                        mdibBuilder.buildSampleArrayValue(List.of(BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.TEN)));

        final var rtsaMetric2 = mdibBuilder.buildRealTimeSampleArrayMetric(
                RTSA_METRIC_HANDLE2,
                MetricCategory.MSRMT,
                MetricAvailability.INTR,
                mdibBuilder.buildCodedValue("abc"),
                BigDecimal.ONE,
                datatypeFactory.newDuration("P0DT0H2M35S"));
        rtsaMetric2.getRight().setActivationState(ComponentActivation.OFF);
        rtsaMetric2
                .getRight()
                .setMetricValue(mdibBuilder.buildSampleArrayValue(
                        List.of(BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE)));

        final var setMetric = mdibBuilder.buildStringMetric(
                SET_METRIC_HANDLE, MetricCategory.SET, MetricAvailability.INTR, mdibBuilder.buildCodedValue("abc"));
        setMetric.getRight().setActivationState(ComponentActivation.OFF);
        setMetric.getRight().setMetricValue(mdibBuilder.buildStringMetricValue("setOne"));

        final var setMetric2 = mdibBuilder.buildStringMetric(
                SET_METRIC_HANDLE2, MetricCategory.SET, MetricAvailability.CONT, mdibBuilder.buildCodedValue("def"));
        setMetric2.getRight().setActivationState(ComponentActivation.OFF);
        setMetric2.getRight().setMetricValue(mdibBuilder.buildStringMetricValue("setTwo"));

        final var clcMetric = mdibBuilder.buildStringMetric(
                CLC_METRIC_HANDLE, MetricCategory.CLC, MetricAvailability.INTR, mdibBuilder.buildCodedValue("abc"));
        clcMetric.getRight().setActivationState(ComponentActivation.OFF);
        clcMetric.getRight().setMetricValue(mdibBuilder.buildStringMetricValue("clcOne"));

        final var clcMetric2 = mdibBuilder.buildStringMetric(
                CLC_METRIC_HANDLE2, MetricCategory.CLC, MetricAvailability.CONT, mdibBuilder.buildCodedValue("def"));
        clcMetric2.getRight().setActivationState(ComponentActivation.OFF);
        clcMetric2.getRight().setMetricValue(mdibBuilder.buildStringMetricValue("clcTwo"));

        channel.getLeft()
                .getMetric()
                .addAll(List.of(
                        msrmtMetric.getLeft(),
                        msrmtMetric2.getLeft(),
                        rtsaMetric.getLeft(),
                        rtsaMetric2.getLeft(),
                        setMetric.getLeft(),
                        setMetric2.getLeft(),
                        clcMetric.getLeft(),
                        clcMetric2.getLeft()));
        mdState.getState()
                .addAll(List.of(
                        msrmtMetric.getRight(),
                        msrmtMetric2.getRight(),
                        rtsaMetric.getRight(),
                        rtsaMetric2.getRight(),
                        setMetric.getRight(),
                        setMetric2.getRight(),
                        clcMetric.getRight(),
                        clcMetric2.getRight()));

        final var getMdibResponse = messageBuilder.buildGetMdibResponse(mdib.getSequenceId());
        getMdibResponse.setMdib(mdib);

        return messageBuilder.createSoapMessageWithBody(
                ActionConstants.getResponseAction(ActionConstants.ACTION_GET_MDIB), getMdibResponse);
    }

    private Envelope buildMetricReport(
            final String sequenceId,
            final BigInteger mdibVersion,
            final BigInteger metricVersion,
            final String metricHandle,
            final ComponentActivation activation) {
        final var report = messageBuilder.buildEpisodicMetricReport(sequenceId);

        final var metricState = mdibBuilder.buildStringMetricState(metricHandle);
        metricState.setStateVersion(metricVersion);
        metricState.setActivationState(activation);

        final var reportPart = messageBuilder.buildAbstractMetricReportReportPart();
        reportPart.getMetricState().add(metricState);

        report.setMdibVersion(mdibVersion);
        report.getReportPart().add(reportPart);
        return messageBuilder.createSoapMessageWithBody(ActionConstants.ACTION_EPISODIC_METRIC_REPORT, report);
    }

    private void noSuccessfulManipulationBiceps547Setup(
            final String manipulationHandle,
            final org.somda.sdc.biceps.model.participant.MetricCategory metricCategory,
            final org.somda.sdc.biceps.model.participant.ComponentActivation activation,
            final ComponentActivation reportActivation)
            throws Exception {
        requirement547SetUp(
                manipulationHandle,
                metricCategory,
                activation,
                ResponseTypes.Result.RESULT_FAIL,
                TIMESTAMP_START,
                TIMESTAMP_FINISH,
                manipulationHandle,
                reportActivation,
                TIMESTAMP_IN_INTERVAL);
    }

    private void wrongMetricCategorySetup(
            final String manipulationHandle,
            final org.somda.sdc.biceps.model.participant.MetricCategory wrongMetricCategory,
            final org.somda.sdc.biceps.model.participant.ComponentActivation manipulationActivation,
            final ComponentActivation expectedActivation)
            throws Exception {
        final var initial = buildMdib(SEQUENCE_ID);
        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);

        final var result = ResponseTypes.Result.RESULT_SUCCESS;
        final var methodName = Constants.MANIPULATION_NAME_SET_METRIC_STATUS;
        final var parameters = ManipulationParameterUtil.buildMetricStatusManipulationParameterData(
                SEQUENCE_ID, manipulationHandle, wrongMetricCategory, manipulationActivation);
        messageStorageUtil.addManipulation(
                storage, TIMESTAMP_START, TIMESTAMP_FINISH, result, "", methodName, parameters);

        final var metricReport =
                buildMetricReport(SEQUENCE_ID, BigInteger.ONE, BigInteger.ONE, manipulationHandle, expectedActivation);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport));
    }

    private void requirement547SetUp(
            final String manipulationParameterHandle,
            final org.somda.sdc.biceps.model.participant.MetricCategory manipulationParameterCategory,
            final org.somda.sdc.biceps.model.participant.ComponentActivation manipulationParameterActivation,
            final ResponseTypes.Result manipulationResult,
            final long manipulationStartTimestamp,
            final long manipulationEndTimestamp,
            final String metricReportHandle,
            final ComponentActivation metricReportActivation,
            final long metricReportTimestamp)
            throws Exception {
        final var initial = buildMdib(SEQUENCE_ID);
        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);

        final var methodName = Constants.MANIPULATION_NAME_SET_METRIC_STATUS;
        final var parameters = ManipulationParameterUtil.buildMetricStatusManipulationParameterData(
                MdibBuilder.DEFAULT_SEQUENCE_ID,
                manipulationParameterHandle,
                manipulationParameterCategory,
                manipulationParameterActivation);
        messageStorageUtil.addManipulation(
                storage,
                manipulationStartTimestamp,
                manipulationEndTimestamp,
                manipulationResult,
                "", // not used by test
                methodName,
                parameters);

        // activation state should be on
        final var metricReport = buildMetricReport(
                SEQUENCE_ID, BigInteger.TWO, BigInteger.ONE, metricReportHandle, metricReportActivation);
        messageStorageUtil.addMessage(storage, buildTestMessage(metricReportTimestamp, metricReport));
    }
}
