/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2023 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.tests.glue.invariant;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.draeger.medical.biceps.model.participant.AbstractAlertState;
import com.draeger.medical.biceps.model.participant.AbstractContextState;
import com.draeger.medical.biceps.model.participant.AbstractMetricState;
import com.draeger.medical.biceps.model.participant.AbstractMetricValue;
import com.draeger.medical.biceps.model.participant.AlertActivation;
import com.draeger.medical.biceps.model.participant.AlertConditionKind;
import com.draeger.medical.biceps.model.participant.AlertConditionPriority;
import com.draeger.medical.biceps.model.participant.AlertConditionState;
import com.draeger.medical.biceps.model.participant.CodedValue;
import com.draeger.medical.biceps.model.participant.ContextAssociation;
import com.draeger.medical.biceps.model.participant.LocationContextState;
import com.draeger.medical.biceps.model.participant.MeasurementValidity;
import com.draeger.medical.biceps.model.participant.MetricAvailability;
import com.draeger.medical.biceps.model.participant.MetricCategory;
import com.draeger.medical.biceps.model.participant.PatientContextState;
import com.draeger.medical.biceps.model.participant.StringMetricDescriptor;
import com.draeger.medical.biceps.model.participant.StringMetricState;
import com.draeger.medical.biceps.model.participant.StringMetricValue;
import com.draeger.medical.dpws.soap.model.Envelope;
import com.draeger.medical.sdccc.configuration.TestSuiteConfig;
import com.draeger.medical.sdccc.marshalling.MarshallingUtil;
import com.draeger.medical.sdccc.messages.MessageStorage;
import com.draeger.medical.sdccc.sdcri.testclient.TestClient;
import com.draeger.medical.sdccc.sdcri.testclient.TestClientUtil;
import com.draeger.medical.sdccc.tests.InjectorTestBase;
import com.draeger.medical.sdccc.tests.test_util.InjectorUtil;
import com.draeger.medical.sdccc.tests.util.NoTestData;
import com.draeger.medical.sdccc.util.MdibBuilder;
import com.draeger.medical.sdccc.util.MessageBuilder;
import com.draeger.medical.sdccc.util.MessageStorageUtil;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Names;
import jakarta.xml.bind.JAXBException;
import java.io.IOException;
import java.math.BigInteger;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nullable;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;
import org.somda.sdc.dpws.helper.JaxbMarshalling;
import org.somda.sdc.dpws.soap.SoapMarshalling;
import org.somda.sdc.glue.common.ActionConstants;

/**
 * Unit test for the GLUE {@linkplain InvariantNonFunctionalQualityAttributesTest}.
 */
public class InvariantNonFunctionalQualityAttributesTestTest {
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    private static final String ALERT_SYSTEM_HANDLE = "alertSystemHandle";
    private static final String ALERT_CONDITION_HANDLE = "alertConditionHandle";
    private static final String VMD_HANDLE = "vmdHandle";
    private static final String SYSTEM_CONTEXT_HANDLE = "systemContextHandle";
    private static final String PATIENT_CONTEXT_HANDLE = "patientHandle";
    private static final String LOCATION_CONTEXT_HANDLE = "locationHandle";
    private static final String PATIENT_CONTEXT_STATE_HANDLE = "patientStateHandle";
    private static final String LOCATION_CONTEXT_STATE_HANDLE = "locationStateHandle";
    private static final String METRIC_HANDLE = "metric";
    private static final String CLOCK_HANDLE = "clockHandle";
    private static final String SECOND_CLOCK_HANDLE = "secondClockHandle";
    private static final String SECOND_MDS = "secondMds";

    private static MessageStorageUtil messageStorageUtil;
    private static MdibBuilder mdibBuilder;
    private static MessageBuilder messageBuilder;
    private InvariantNonFunctionalQualityAttributesTest testClass;
    private MessageStorage storage;
    private JaxbMarshalling baseMarshalling;
    private SoapMarshalling marshalling;

    @BeforeAll
    static void setupMarshalling() {
        final Injector marshallingInjector = MarshallingUtil.createMarshallingTestInjector(true);
        messageStorageUtil = marshallingInjector.getInstance(MessageStorageUtil.class);
        mdibBuilder = marshallingInjector.getInstance(MdibBuilder.class);
        messageBuilder = marshallingInjector.getInstance(MessageBuilder.class);
    }

    @BeforeEach
    void setUp() throws IOException, TimeoutException {
        final TestClient mockClient = mock(TestClient.class);
        when(mockClient.isClientRunning()).thenReturn(true);

        final Injector injector = InjectorUtil.setupInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(TestClient.class).toInstance(mockClient);
                bind(Key.get(Boolean.class, Names.named(TestSuiteConfig.SUMMARIZE_MESSAGE_ENCODING_ERRORS)))
                        .toInstance(true);
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

        testClass = new InvariantNonFunctionalQualityAttributesTest();
        testClass.setup();
    }

    @AfterEach
    void tearDown() throws TimeoutException {
        baseMarshalling.stopAsync().awaitTerminated(DEFAULT_TIMEOUT);
        marshalling.stopAsync().awaitTerminated(DEFAULT_TIMEOUT);
        storage.close();
    }

    /**
     * Tests whether calling the test without any input data causes a failure.
     */
    @Test
    public void testRequirementR0010NoTestData() {
        assertThrows(NoTestData.class, testClass::testRequirementR0010);
    }

    /**
     * Tests whether the test passes when every mds has a clock descriptor and clock state.
     */
    @Test
    public void testRequirementR0010Good() throws Exception {
        final var initial = buildMdibForR0010(MdibBuilder.DEFAULT_SEQUENCE_ID, BigInteger.ZERO, true, false);

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);

        testClass.testRequirementR0010();
    }

    /**
     * Tests whether the test passes when every mds has a clock descriptor and clock state.
     */
    @Test
    public void testRequirementR0010GoodMultipleMds() throws Exception {
        final var initial = buildMdibForR0010(MdibBuilder.DEFAULT_SEQUENCE_ID, BigInteger.ZERO, true, true);

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);

        testClass.testRequirementR0010();
    }

    /**
     * Tests whether the test fails when a mds has no clock descriptor and clock state.
     */
    @Test
    public void testRequirementR0010Bad() throws Exception {
        final var initial = buildMdibForR0010(MdibBuilder.DEFAULT_SEQUENCE_ID, BigInteger.ZERO, false, false);

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);

        assertThrows(AssertionError.class, testClass::testRequirementR0010);
    }

    /**
     * Tests whether the test fails when at least one mds has no clock descriptor and clock state.
     */
    @Test
    public void testRequirementR0010BadMultipleMds() throws Exception {
        final var initial = buildMdibForR0010(MdibBuilder.DEFAULT_SEQUENCE_ID, BigInteger.ZERO, false, true);

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);

        assertThrows(AssertionError.class, testClass::testRequirementR0010);
    }

    Envelope buildMdibForR0010(
            final String sequenceId,
            final @Nullable BigInteger mdsVersion,
            final boolean clockPresent,
            final boolean multipleMds) {
        final var mdib = mdibBuilder.buildMinimalMdib(sequenceId);

        final var mdState = mdib.getMdState();
        final var mdsDescriptor = mdib.getMdDescription().getMds().get(0);
        mdsDescriptor.setDescriptorVersion(mdsVersion);

        if (clockPresent) {
            final var clock = mdibBuilder.buildClock(CLOCK_HANDLE);
            mdsDescriptor.setClock(clock.getLeft());
            mdState.getState().add(clock.getRight());
        }

        if (multipleMds) {
            final var mds = mdibBuilder.buildMds(SECOND_MDS);
            final var clock = mdibBuilder.buildClock(SECOND_CLOCK_HANDLE);
            mds.getLeft().setClock(clock.getLeft());
            mdState.getState().add(clock.getRight());
            mdState.getState().add(mds.getRight());
            mdib.getMdDescription().getMds().add(mds.getLeft());
        }

        final var getMdibResponse = messageBuilder.buildGetMdibResponse(mdib.getSequenceId());
        getMdibResponse.setMdib(mdib);

        return messageBuilder.createSoapMessageWithBody(
                ActionConstants.getResponseAction(ActionConstants.ACTION_GET_MDIB), getMdibResponse);
    }

    /**
     * Tests whether calling the test without any input data causes a failure.
     */
    @Test
    public void testRequirementR0011NoTestData() {
        assertThrows(NoTestData.class, testClass::testRequirementR0011);
    }

    /**
     * Tests whether the test fails when a metric update with a value but without a timestamp is send.
     */
    @Test
    public void testRequirementR0011Bad() throws IOException, JAXBException {
        final var initial = buildMdib(MdibBuilder.DEFAULT_SEQUENCE_ID, BigInteger.ZERO);
        final var wrong = buildWrongEpisodicMetricReport();

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, wrong);

        assertThrows(AssertionError.class, testClass::testRequirementR0011);
    }

    /**
     * Tests whether the test passes when no update is send and the initial MDIB contains a
     * metric with a value and timestamp.
     */
    @Test
    public void testRequirementR0011Good() throws IOException, JAXBException, NoTestData {
        final var initial = buildMdib(MdibBuilder.DEFAULT_SEQUENCE_ID, BigInteger.ZERO);

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);

        testClass.testRequirementR0011();
    }

    /**
     * Tests whether the test does not fail when a metric state has no metric value.
     */
    @Test
    public void testRequirementR0011GoodMetricWithoutValue() throws IOException, JAXBException, NoTestData {
        final var initial = buildMdib(MdibBuilder.DEFAULT_SEQUENCE_ID, BigInteger.ZERO);
        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);

        final var first = buildEpisodicMetricReport(
                MdibBuilder.DEFAULT_SEQUENCE_ID, BigInteger.ONE, mdibBuilder.buildStringMetricState(METRIC_HANDLE));
        messageStorageUtil.addInboundSecureHttpMessage(storage, first);

        testClass.testRequirementR0011();
    }

    /**
     * Tests whether calling the test without any input data causes a failure.
     */
    @Test
    public void testRequirementR001200NoTestData() {
        assertThrows(NoTestData.class, testClass::testRequirementR001200);
    }

    /**
     * Tests whether the test passes when @DeterminationTime
     *    is updated whenever @Presence changes for all AlertConditionStates.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR001200Good() throws Exception {
        final var initial = buildMdib(MdibBuilder.DEFAULT_SEQUENCE_ID, BigInteger.ZERO);
        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);

        final var first = buildEpisodicAlertReport(
                MdibBuilder.DEFAULT_SEQUENCE_ID,
                BigInteger.valueOf(1),
                buildAlertConditionState(ALERT_CONDITION_HANDLE, AlertActivation.OFF, false, BigInteger.valueOf(1)));
        messageStorageUtil.addInboundSecureHttpMessage(storage, first);

        final var second = buildEpisodicAlertReport(
                MdibBuilder.DEFAULT_SEQUENCE_ID,
                BigInteger.valueOf(2),
                buildAlertConditionState(ALERT_CONDITION_HANDLE, AlertActivation.OFF, true, BigInteger.valueOf(2)));
        messageStorageUtil.addInboundSecureHttpMessage(storage, second);

        final var third = buildEpisodicAlertReport(
                MdibBuilder.DEFAULT_SEQUENCE_ID,
                BigInteger.valueOf(3),
                buildAlertConditionState(ALERT_CONDITION_HANDLE, AlertActivation.OFF, true, BigInteger.valueOf(2)));
        messageStorageUtil.addInboundSecureHttpMessage(storage, third);

        final var fourth = buildEpisodicAlertReport(
                MdibBuilder.DEFAULT_SEQUENCE_ID,
                BigInteger.valueOf(4),
                buildAlertConditionState(ALERT_CONDITION_HANDLE, AlertActivation.OFF, false, BigInteger.valueOf(3)));
        messageStorageUtil.addInboundSecureHttpMessage(storage, fourth);

        testClass.testRequirementR001200();
    }

    /**
     * Tests whether the test fails when @DeterminationTime
     *    is not updated, but @Presence changes for an AlertConditionState.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR001200BadDeterminationTimeNotUpdated() throws Exception {
        final var initial = buildMdib(MdibBuilder.DEFAULT_SEQUENCE_ID, BigInteger.ZERO);
        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);

        final var first = buildEpisodicAlertReport(
                MdibBuilder.DEFAULT_SEQUENCE_ID,
                BigInteger.valueOf(1),
                buildAlertConditionState(ALERT_CONDITION_HANDLE, AlertActivation.OFF, false, BigInteger.ONE));
        messageStorageUtil.addInboundSecureHttpMessage(storage, first);

        final var second = buildEpisodicAlertReport(
                MdibBuilder.DEFAULT_SEQUENCE_ID,
                BigInteger.valueOf(2),
                buildAlertConditionState(ALERT_CONDITION_HANDLE, AlertActivation.OFF, true, BigInteger.ONE));
        messageStorageUtil.addInboundSecureHttpMessage(storage, second);

        assertThrows(AssertionFailedError.class, testClass::testRequirementR001200);
    }

    /**
     * Tests whether calling the tests without any input data causes a failure.
     */
    @Test
    public void testRequirementR0013NoTestData() {
        assertThrows(NoTestData.class, testClass::testRequirementR0013);
    }

    /**
     * Tests whether the test passes, if the BindingStartTime of a ContextState is present when the BindingMidbVersion
     * is present. Also checks if the test passes when both attributes are absent or only the BindingStartTime is
     * present.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR0013Good() throws Exception {
        final var initial = buildMdib(MdibBuilder.DEFAULT_SEQUENCE_ID, BigInteger.ZERO);
        // BindingMdibVersion and BindingStartTime present
        final var patientContext =
                buildPatientContext(PATIENT_CONTEXT_HANDLE, PATIENT_CONTEXT_STATE_HANDLE, BigInteger.ONE);
        patientContext.setBindingMdibVersion(BigInteger.ONE);
        patientContext.setBindingStartTime(BigInteger.ONE);
        final var locationContext =
                buildLocationContext(LOCATION_CONTEXT_HANDLE, LOCATION_CONTEXT_STATE_HANDLE, BigInteger.ONE);
        locationContext.setBindingMdibVersion(BigInteger.ONE);
        locationContext.setBindingStartTime(BigInteger.ONE);
        final var firstUpdate = buildEpisodicContextReport(
                MdibBuilder.DEFAULT_SEQUENCE_ID, BigInteger.ONE, patientContext, locationContext);
        // BindingMdibVersion and BindingStartTime absent
        final var secondLocationContext =
                buildLocationContext(LOCATION_CONTEXT_HANDLE, LOCATION_CONTEXT_STATE_HANDLE, BigInteger.TWO);
        final var secondUpdate =
                buildEpisodicContextReport(MdibBuilder.DEFAULT_SEQUENCE_ID, BigInteger.TWO, secondLocationContext);
        // BindingMdibVersion absent and BindingStartTime present
        final var thirdLocationContext =
                buildLocationContext(LOCATION_CONTEXT_HANDLE, LOCATION_CONTEXT_STATE_HANDLE, BigInteger.valueOf(3));
        final var bindingStartTime = BigInteger.valueOf(3);
        thirdLocationContext.setBindingStartTime(bindingStartTime);
        final var thirdUpdate = buildEpisodicContextReport(
                MdibBuilder.DEFAULT_SEQUENCE_ID, BigInteger.valueOf(3), thirdLocationContext);

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, firstUpdate);
        messageStorageUtil.addInboundSecureHttpMessage(storage, secondUpdate);
        messageStorageUtil.addInboundSecureHttpMessage(storage, thirdUpdate);

        testClass.testRequirementR0013();
    }

    /**
     * Tests whether the test fails, if the BindingStartTime of a ContextState is absent, when the BindingMidbVersion is
     * present.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR0013Bad() throws Exception {
        final var initial = buildMdib(MdibBuilder.DEFAULT_SEQUENCE_ID, BigInteger.ZERO);
        // BindingMdibVersion present and BindingStartTime absent
        final var patientContext =
                buildPatientContext(PATIENT_CONTEXT_HANDLE, PATIENT_CONTEXT_STATE_HANDLE, BigInteger.ONE);
        patientContext.setBindingMdibVersion(BigInteger.ONE);
        final var firstUpdate =
                buildEpisodicContextReport(MdibBuilder.DEFAULT_SEQUENCE_ID, BigInteger.ONE, patientContext);

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, firstUpdate);

        assertThrows(AssertionError.class, testClass::testRequirementR0013);
    }

    /**
     * Tests whether calling the tests without any input data causes a failure.
     */
    @Test
    public void testRequirementR0072NoTestData() {
        assertThrows(NoTestData.class, testClass::testRequirementR0072);
    }

    /**
     * Tests whether the test passes, if the BindingEndTime of a ContextState is present when the UnbindingMidbVersion
     * is present. Also checks if the test passes when both attributes are absent or only the BindingEndTime is
     * present.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR0072Good() throws Exception {
        final var initial = buildMdib(MdibBuilder.DEFAULT_SEQUENCE_ID, BigInteger.ZERO);
        // UnbindingMdibVersion and BindingEndTime present
        final var patientContext =
                buildPatientContext(PATIENT_CONTEXT_HANDLE, PATIENT_CONTEXT_STATE_HANDLE, BigInteger.ONE);
        patientContext.setUnbindingMdibVersion(BigInteger.ONE);
        patientContext.setBindingEndTime(BigInteger.ONE);
        final var locationContext =
                buildLocationContext(LOCATION_CONTEXT_HANDLE, LOCATION_CONTEXT_STATE_HANDLE, BigInteger.ONE);
        locationContext.setUnbindingMdibVersion(BigInteger.ONE);
        locationContext.setBindingEndTime(BigInteger.ONE);
        final var firstUpdate = buildEpisodicContextReport(
                MdibBuilder.DEFAULT_SEQUENCE_ID, BigInteger.ONE, patientContext, locationContext);
        // UnbindingMdibVersion and BindingEndTime absent
        final var locationContext2 =
                buildLocationContext(LOCATION_CONTEXT_HANDLE, LOCATION_CONTEXT_STATE_HANDLE, BigInteger.TWO);
        final var secondUpdate =
                buildEpisodicContextReport(MdibBuilder.DEFAULT_SEQUENCE_ID, BigInteger.TWO, locationContext2);
        // UnbindingMdibVersion absent and BindingEndTime present
        final var locationContext3 =
                buildLocationContext(LOCATION_CONTEXT_HANDLE, LOCATION_CONTEXT_STATE_HANDLE, BigInteger.valueOf(3));
        final var bindingEndTime = BigInteger.valueOf(3);
        locationContext3.setBindingEndTime(bindingEndTime);
        final var thirdUpdate =
                buildEpisodicContextReport(MdibBuilder.DEFAULT_SEQUENCE_ID, BigInteger.valueOf(3), locationContext3);

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, firstUpdate);
        messageStorageUtil.addInboundSecureHttpMessage(storage, secondUpdate);
        messageStorageUtil.addInboundSecureHttpMessage(storage, thirdUpdate);

        testClass.testRequirementR0072();
    }

    /**
     * Tests whether the test fails, if the BindingEndTime of a ContextState is absent, when the UnbindingMidbVersion is
     * present.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR0072Bad() throws Exception {
        final var initial = buildMdib(MdibBuilder.DEFAULT_SEQUENCE_ID, BigInteger.ZERO);
        // UnbindingMdibVersion present and BindingEndTime absent
        final var patientContext =
                buildPatientContext(PATIENT_CONTEXT_HANDLE, PATIENT_CONTEXT_STATE_HANDLE, BigInteger.ONE);
        patientContext.setUnbindingMdibVersion(BigInteger.ONE);
        final var firstUpdate =
                buildEpisodicContextReport(MdibBuilder.DEFAULT_SEQUENCE_ID, BigInteger.ONE, patientContext);

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, firstUpdate);

        assertThrows(AssertionError.class, testClass::testRequirementR0072);
    }

    Envelope buildMdib(final String sequenceId, final @Nullable BigInteger mdsVersion) {
        final var mdib = mdibBuilder.buildMinimalMdib(sequenceId);

        final var mdState = mdib.getMdState();
        final var mdsDescriptor = mdib.getMdDescription().getMds().get(0);
        mdsDescriptor.setDescriptorVersion(mdsVersion);

        final var alertSystem = mdibBuilder.buildAlertSystem(ALERT_SYSTEM_HANDLE, AlertActivation.ON);
        alertSystem.getRight().setLastSelfCheck(BigInteger.ZERO);
        final var alertCondition = mdibBuilder.buildAlertCondition(
                ALERT_CONDITION_HANDLE, AlertConditionKind.PHY, AlertConditionPriority.HI, AlertActivation.ON);
        alertCondition.getRight().setDeterminationTime(BigInteger.ZERO);
        alertSystem.getLeft().getAlertCondition().add(alertCondition.getLeft());
        mdsDescriptor.setAlertSystem(alertSystem.getLeft());
        mdState.getState().addAll(List.of(alertSystem.getRight(), alertCondition.getRight()));

        final var vmd = mdibBuilder.buildVmd(VMD_HANDLE);
        mdsDescriptor.getVmd().add(vmd.getLeft());
        mdState.getState().add(vmd.getRight());
        final var channel = mdibBuilder.buildChannel("channel");
        vmd.getLeft().getChannel().add(channel.getLeft());
        mdState.getState().add(channel.getRight());

        final Pair<StringMetricDescriptor, StringMetricState> metric = getStringMetricStatePair(true);
        channel.getLeft().getMetric().add(metric.getLeft());
        mdState.getState().add(metric.getRight());

        final var systemContext = mdibBuilder.buildSystemContext(SYSTEM_CONTEXT_HANDLE);
        mdsDescriptor.setSystemContext(systemContext.getLeft());
        mdState.getState().add(systemContext.getRight());

        final var patientContext =
                mdibBuilder.buildPatientContext(PATIENT_CONTEXT_HANDLE, PATIENT_CONTEXT_STATE_HANDLE);

        systemContext.getLeft().setPatientContext(patientContext.getLeft());
        mdState.getState().add(patientContext.getRight());

        final var locationContext =
                mdibBuilder.buildLocationContext(LOCATION_CONTEXT_HANDLE, LOCATION_CONTEXT_STATE_HANDLE);

        systemContext.getLeft().setLocationContext(locationContext.getLeft());
        mdState.getState().add(locationContext.getRight());

        final var getMdibResponse = messageBuilder.buildGetMdibResponse(mdib.getSequenceId());
        getMdibResponse.setMdib(mdib);

        return messageBuilder.createSoapMessageWithBody(
                ActionConstants.getResponseAction(ActionConstants.ACTION_GET_MDIB), getMdibResponse);
    }

    private Pair<StringMetricDescriptor, StringMetricState> getStringMetricStatePair(final boolean correct) {
        final var codedValue = new CodedValue();
        codedValue.setCode("1");
        final var metric =
                mdibBuilder.buildStringMetric(METRIC_HANDLE, MetricCategory.MSRMT, MetricAvailability.CONT, codedValue);
        final var metricValue = new StringMetricValue();
        metricValue.setValue("0");
        final var metricQuality = new AbstractMetricValue.MetricQuality();
        metricQuality.setValidity(MeasurementValidity.VLD);
        metricValue.setMetricQuality(metricQuality);
        if (correct) {
            metricValue.setDeterminationTime(BigInteger.ZERO);
        }
        metric.getRight().setMetricValue(metricValue);
        return metric;
    }

    Envelope buildEpisodicAlertReport(
            final String sequenceId, final @Nullable BigInteger mdibVersion, final AbstractAlertState... states) {
        final var report = messageBuilder.buildEpisodicAlertReport(sequenceId);
        final var reportPart = messageBuilder.buildAbstractAlertReportReportPart();
        reportPart.getAlertState().addAll(Arrays.asList(states));
        report.setMdibVersion(mdibVersion);
        report.getReportPart().add(reportPart);
        return messageBuilder.createSoapMessageWithBody(ActionConstants.ACTION_EPISODIC_ALERT_REPORT, report);
    }

    Envelope buildEpisodicMetricReport(
            final String sequenceId, final @Nullable BigInteger mdibVersion, final AbstractMetricState... states) {
        final var report = messageBuilder.buildEpisodicMetricReport(sequenceId);

        final var reportPart = messageBuilder.buildAbstractMetricReportReportPart();

        for (var state : states) {
            reportPart.getMetricState().add(state);
        }
        report.setMdibVersion(mdibVersion);
        report.getReportPart().add(reportPart);

        return messageBuilder.createSoapMessageWithBody(ActionConstants.ACTION_EPISODIC_METRIC_REPORT, report);
    }

    Envelope buildWrongEpisodicMetricReport() {
        final var report = messageBuilder.buildEpisodicMetricReport(MdibBuilder.DEFAULT_SEQUENCE_ID);

        final var reportPart = messageBuilder.buildAbstractMetricReportReportPart();

        reportPart.getMetricState().add(getStringMetricStatePair(false).getRight());
        report.setMdibVersion(BigInteger.ONE);
        report.getReportPart().add(reportPart);

        return messageBuilder.createSoapMessageWithBody(ActionConstants.ACTION_EPISODIC_METRIC_REPORT, report);
    }

    Envelope buildEpisodicContextReport(
            final String sequenceId,
            final @Nullable BigInteger mdibVersion,
            final AbstractContextState... contextStates) {
        final var report = messageBuilder.buildEpisodicContextReport(sequenceId);

        final var reportPart = messageBuilder.buildAbstractContextReportReportPart();

        for (var contextState : contextStates) {
            reportPart.getContextState().add(contextState);
        }

        report.setMdibVersion(mdibVersion);
        report.getReportPart().add(reportPart);
        return messageBuilder.createSoapMessageWithBody(ActionConstants.ACTION_EPISODIC_CONTEXT_REPORT, report);
    }

    PatientContextState buildPatientContext(
            final String patientContextHandle,
            final String patientContextStateHandle,
            final BigInteger patientContextVersion) {
        final var patientContext =
                mdibBuilder.buildPatientContextState(patientContextHandle, patientContextStateHandle);
        patientContext.setDescriptorVersion(patientContextVersion);
        patientContext.setContextAssociation(ContextAssociation.ASSOC);
        return patientContext;
    }

    LocationContextState buildLocationContext(
            final String locationContextHandle,
            final String locationContextStateHandle,
            final BigInteger locationContextVersion) {
        final var locationContext =
                mdibBuilder.buildLocationContextState(locationContextHandle, locationContextStateHandle);
        locationContext.setDescriptorVersion(locationContextVersion);
        locationContext.setContextAssociation(ContextAssociation.ASSOC);
        return locationContext;
    }

    AlertConditionState buildAlertConditionState(
            final String handle,
            final AlertActivation activation,
            final boolean presence,
            @Nullable final BigInteger determinationTime) {
        final var state = mdibBuilder.buildAlertConditionState(handle, activation);
        state.setDeterminationTime(determinationTime);
        state.setPresence(presence);
        return state;
    }

    AbstractAlertState buildAlertSystemState(
            final String handle, final AlertActivation activation, @Nullable final BigInteger lastSelfCheck) {
        final var state = mdibBuilder.buildAlertSystemState(handle, activation);
        state.setLastSelfCheck(lastSelfCheck);
        return state;
    }
}
