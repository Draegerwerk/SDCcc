/*
 * This Source Code Form is subject to the terms of the "SDCcc non-commercial use license".
 *
 * Copyright (C) 2025 Draegerwerk AG & Co. KGaA
 */

package com.draeger.medical.sdccc.tests.glue.invariant;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.draeger.medical.biceps.model.message.AbstractAlertReport;
import com.draeger.medical.biceps.model.message.AbstractComponentReport;
import com.draeger.medical.biceps.model.message.AbstractContextReport;
import com.draeger.medical.biceps.model.message.AbstractMetricReport;
import com.draeger.medical.biceps.model.message.AbstractOperationalStateReport;
import com.draeger.medical.biceps.model.message.DescriptionModificationReport;
import com.draeger.medical.biceps.model.message.DescriptionModificationType;
import com.draeger.medical.biceps.model.message.ObservedValueStream;
import com.draeger.medical.biceps.model.participant.AbstractAlertState;
import com.draeger.medical.biceps.model.participant.AbstractContextState;
import com.draeger.medical.biceps.model.participant.AbstractDescriptor;
import com.draeger.medical.biceps.model.participant.AbstractDeviceComponentState;
import com.draeger.medical.biceps.model.participant.AbstractMetricState;
import com.draeger.medical.biceps.model.participant.AbstractOperationState;
import com.draeger.medical.biceps.model.participant.AbstractState;
import com.draeger.medical.biceps.model.participant.AlertActivation;
import com.draeger.medical.biceps.model.participant.AlertConditionKind;
import com.draeger.medical.biceps.model.participant.AlertConditionPriority;
import com.draeger.medical.biceps.model.participant.AlertSignalManifestation;
import com.draeger.medical.biceps.model.participant.MetricAvailability;
import com.draeger.medical.biceps.model.participant.MetricCategory;
import com.draeger.medical.biceps.model.participant.OperatingMode;
import com.draeger.medical.biceps.model.participant.RealTimeSampleArrayMetricState;
import com.draeger.medical.dpws.soap.model.Envelope;
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
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeoutException;
import javax.xml.datatype.DatatypeFactory;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.somda.sdc.dpws.helper.JaxbMarshalling;
import org.somda.sdc.dpws.soap.SoapMarshalling;
import org.somda.sdc.glue.common.ActionConstants;

/**
 * Unit test for Glue {@linkplain InvariantSubscriptionHandlingTest}.
 */
public class InvariantSubscriptionHandlingTestTest {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);
    private static final String ALERT_HANDLE = "alertHandle";
    private static final String NUMERIC_HANDLE = "numericMetric";
    private static final String PARENT_HANDLE = "parentDescriptor";
    private static final String RTSA_HANDLE = "rtsaMetric";
    private static final String STRING_HANDLE = "stringMetric";
    private static MessageStorageUtil messageStorageUtil;
    private static MdibBuilder mdibBuilder;
    private static MessageBuilder messageBuilder;
    private DatatypeFactory datatypeFactory;
    private InvariantSubscriptionHandlingTest testClass;
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
    void setUp() throws Exception {
        final TestClient mockTestClient = mock(TestClient.class);
        when(mockTestClient.isClientRunning()).thenReturn(true);

        final Injector injector = InjectorUtil.setupInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(TestClient.class).toInstance(mockTestClient);
            }
        });

        InjectorTestBase.setInjector(injector);

        final var riInjector = TestClientUtil.createClientInjector();
        when(mockTestClient.getInjector()).thenReturn(riInjector);

        baseMarshalling = riInjector.getInstance(JaxbMarshalling.class);
        baseMarshalling.startAsync().awaitRunning(DEFAULT_TIMEOUT);

        marshalling = riInjector.getInstance(SoapMarshalling.class);
        marshalling.startAsync().awaitRunning(DEFAULT_TIMEOUT);

        storage = injector.getInstance(MessageStorage.class);

        datatypeFactory = DatatypeFactory.newInstance();

        testClass = new InvariantSubscriptionHandlingTest();
        testClass.setup();
    }

    @AfterEach
    void testDown() throws TimeoutException {
        baseMarshalling.stopAsync().awaitTerminated(DEFAULT_TIMEOUT);
        marshalling.stopAsync().awaitTerminated(DEFAULT_TIMEOUT);
        storage.close();
    }

    /**
     * Test whether calling the test without any input data causes the test to fail.
     */
    @Test
    public void testRequirementR0056NoTestData() {
        assertThrows(NoTestData.class, testClass::testRequirementR0056);
    }

    /**
     * Checks if the test fails with a NoTestData exception if only description modification reports with
     * modification type delete were seen during the test run.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR0056NoTestData2() throws Exception {
        final var metric = mdibBuilder.buildNumericMetric(
                NUMERIC_HANDLE,
                MetricCategory.CLC,
                MetricAvailability.CONT,
                mdibBuilder.buildCodedValue("Millibar"),
                BigDecimal.ONE);
        final var part = buildDescriptionModificationReportPart(
                DescriptionModificationType.DEL,
                PARENT_HANDLE,
                new ImmutablePair<>(metric.getLeft(), metric.getRight()));
        final var report = buildDescriptionModificationReport(MdibBuilder.DEFAULT_SEQUENCE_ID, BigInteger.ZERO, part);

        messageStorageUtil.addInboundSecureHttpMessage(storage, report);
        assertThrows(NoTestData.class, testClass::testRequirementR0056);
    }

    /**
     * Checks if the test fails with a NoTestData exception if only description modification reports without
     * states were seen during the test run.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR0056NoTestData3() throws Exception {
        final var metric = mdibBuilder.buildNumericMetric(
                NUMERIC_HANDLE,
                MetricCategory.CLC,
                MetricAvailability.CONT,
                mdibBuilder.buildCodedValue("Millibar"),
                BigDecimal.ONE);
        final var part = buildDescriptionModificationReportPart(
                DescriptionModificationType.CRT,
                PARENT_HANDLE,
                new ImmutablePair<>(metric.getLeft(), metric.getRight()));

        // clear states from report part
        part.getState().clear();

        final var report = buildDescriptionModificationReport(MdibBuilder.DEFAULT_SEQUENCE_ID, BigInteger.ZERO, part);

        messageStorageUtil.addInboundSecureHttpMessage(storage, report);
        assertThrows(NoTestData.class, testClass::testRequirementR0056);
    }

    /**
     * Tests whether the test passes when the description modification report were sent before any report
     * containing the changed states.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR0056Good() throws Exception {
        final var metric = mdibBuilder.buildNumericMetric(
                NUMERIC_HANDLE,
                MetricCategory.CLC,
                MetricAvailability.CONT,
                mdibBuilder.buildCodedValue("Millibar"),
                BigDecimal.ONE);
        final var alert = mdibBuilder.buildAlertCondition(
                ALERT_HANDLE, AlertConditionKind.PHY, AlertConditionPriority.HI, AlertActivation.ON);
        final var operational =
                mdibBuilder.buildActivateOperation("operationHandle", "operationTarget", OperatingMode.EN);
        final var crtPart = buildDescriptionModificationReportPart(
                DescriptionModificationType.CRT,
                PARENT_HANDLE,
                new ImmutablePair<>(metric.getLeft(), metric.getRight()),
                new ImmutablePair<>(alert.getLeft(), alert.getRight()));
        final var delPart = buildDescriptionModificationReportPart(
                DescriptionModificationType.DEL,
                "otherParent",
                new ImmutablePair<>(operational.getLeft(), operational.getRight()));
        final var report =
                buildDescriptionModificationReport(MdibBuilder.DEFAULT_SEQUENCE_ID, BigInteger.ONE, crtPart, delPart);
        messageStorageUtil.addInboundSecureHttpMessage(storage, report);

        final var rtsaMetric = mdibBuilder.buildRealTimeSampleArrayMetric(
                RTSA_HANDLE,
                MetricCategory.CLC,
                MetricAvailability.CONT,
                mdibBuilder.buildCodedValue("rtsaCodedValue"),
                BigDecimal.TEN,
                datatypeFactory.newDuration("P0DT0H2M35S"));
        final var stringMetric = mdibBuilder.buildStringMetric(
                STRING_HANDLE,
                MetricCategory.CLC,
                MetricAvailability.CONT,
                mdibBuilder.buildCodedValue("someCodedValue"));
        final var uptPart = buildDescriptionModificationReportPart(
                DescriptionModificationType.UPT,
                PARENT_HANDLE,
                new ImmutablePair<>(stringMetric.getLeft(), stringMetric.getRight()));
        final var crtPart2 = buildDescriptionModificationReportPart(
                DescriptionModificationType.CRT,
                "otherParent",
                new ImmutablePair<>(rtsaMetric.getLeft(), rtsaMetric.getRight()));
        final var report2 =
                buildDescriptionModificationReport(MdibBuilder.DEFAULT_SEQUENCE_ID, BigInteger.TWO, uptPart, crtPart2);
        messageStorageUtil.addInboundSecureHttpMessage(storage, report2);

        // same string metric state but after description modification
        final var metricReportPart = buildAbstractMetricReportPart(stringMetric.getRight());
        final var metricReport = buildEpisodicMetricReportWithParts(
                MdibBuilder.DEFAULT_SEQUENCE_ID, BigInteger.valueOf(5), metricReportPart);
        messageStorageUtil.addInboundSecureHttpMessage(storage, metricReport);

        final var waveform =
                buildWaveformStream(MdibBuilder.DEFAULT_SEQUENCE_ID, BigInteger.TEN, rtsaMetric.getRight());
        messageStorageUtil.addInboundSecureHttpMessage(storage, waveform);

        testClass.testRequirementR0056();
    }

    /**
     * Tests whether the test passes when the description modification report and the report
     * containing the changed state have the same mdib version.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR0056GoodSameMdibVersion() throws Exception {
        final var sameMdibVersion = BigInteger.ZERO;
        final var operational =
                mdibBuilder.buildActivateOperation("operationHandle", "operationTarget", OperatingMode.EN);
        final var uptPart = buildDescriptionModificationReportPart(
                DescriptionModificationType.UPT,
                "otherParent",
                new ImmutablePair<>(operational.getLeft(), operational.getRight()));
        final var report =
                buildDescriptionModificationReport(MdibBuilder.DEFAULT_SEQUENCE_ID, sameMdibVersion, uptPart);
        messageStorageUtil.addInboundSecureHttpMessage(storage, report);

        final var operationalStateReportPart = buildAbstractOperationalStateReportPart(operational.getRight());
        final var operationalStateReport = buildEpisodicOperationalStateReportWithParts(
                MdibBuilder.DEFAULT_SEQUENCE_ID, sameMdibVersion, operationalStateReportPart);
        messageStorageUtil.addInboundSecureHttpMessage(storage, operationalStateReport);

        testClass.testRequirementR0056();
    }

    /**
     * Tests whether the test fails when the description modification report was send after the alert report
     * containing the changed state.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR0056BadAlertReport() throws Exception {
        final var alertSignal =
                mdibBuilder.buildAlertSignal("alertSignal", AlertSignalManifestation.TAN, true, AlertActivation.ON);
        final var part = buildDescriptionModificationReportPart(
                DescriptionModificationType.UPT,
                PARENT_HANDLE,
                new ImmutablePair<>(alertSignal.getLeft(), alertSignal.getRight()));
        final var descriptionModification =
                buildDescriptionModificationReport(MdibBuilder.DEFAULT_SEQUENCE_ID, BigInteger.TEN, part);
        messageStorageUtil.addInboundSecureHttpMessage(storage, descriptionModification);

        final var alertReport =
                buildEpisodicAlertReport(MdibBuilder.DEFAULT_SEQUENCE_ID, BigInteger.TWO, alertSignal.getRight());
        messageStorageUtil.addInboundSecureHttpMessage(storage, alertReport);
        assertThrows(AssertionError.class, testClass::testRequirementR0056);
    }

    /**
     * Tests whether the test fails when the description modification report was send after the component report
     * containing the changed state.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR0056BadComponentReport() throws Exception {
        final var component = mdibBuilder.buildSco("sco");
        final var part = buildDescriptionModificationReportPart(
                DescriptionModificationType.UPT,
                PARENT_HANDLE,
                new ImmutablePair<>(component.getLeft(), component.getRight()));
        final var descriptionModification =
                buildDescriptionModificationReport(MdibBuilder.DEFAULT_SEQUENCE_ID, BigInteger.TEN, part);
        messageStorageUtil.addInboundSecureHttpMessage(storage, descriptionModification);

        final var componentReport =
                buildEpisodicComponentReport(MdibBuilder.DEFAULT_SEQUENCE_ID, BigInteger.TWO, component.getRight());
        messageStorageUtil.addInboundSecureHttpMessage(storage, componentReport);
        assertThrows(AssertionError.class, testClass::testRequirementR0056);
    }

    /**
     * Tests whether the test fails when the description modification report was send after the metric report
     * containing the changed state.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR0056BadMetricReport() throws Exception {
        final var metric = mdibBuilder.buildNumericMetric(
                NUMERIC_HANDLE,
                MetricCategory.CLC,
                MetricAvailability.CONT,
                mdibBuilder.buildCodedValue("Millibar"),
                BigDecimal.ONE);
        final var part = buildDescriptionModificationReportPart(
                DescriptionModificationType.CRT,
                PARENT_HANDLE,
                new ImmutablePair<>(metric.getLeft(), metric.getRight()));
        final var descriptionModification =
                buildDescriptionModificationReport(MdibBuilder.DEFAULT_SEQUENCE_ID, BigInteger.TEN, part);
        messageStorageUtil.addInboundSecureHttpMessage(storage, descriptionModification);

        final var metricReport = buildEpisodicMetricReportWithParts(
                MdibBuilder.DEFAULT_SEQUENCE_ID, BigInteger.TWO, buildAbstractMetricReportPart(metric.getRight()));
        messageStorageUtil.addInboundSecureHttpMessage(storage, metricReport);
        assertThrows(AssertionError.class, testClass::testRequirementR0056);
    }

    /**
     * Tests whether the test fails when the description modification report was send after the operational state report
     * containing the changed state.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR0056BadOperationalStateReport() throws Exception {
        final var operational =
                mdibBuilder.buildActivateOperation("operationHandle", "operationTarget", OperatingMode.EN);
        final var uptPart = buildDescriptionModificationReportPart(
                DescriptionModificationType.UPT,
                "otherParent",
                new ImmutablePair<>(operational.getLeft(), operational.getRight()));
        final var report = buildDescriptionModificationReport(MdibBuilder.DEFAULT_SEQUENCE_ID, BigInteger.TEN, uptPart);
        messageStorageUtil.addInboundSecureHttpMessage(storage, report);

        final var operationalStateReportPart = buildAbstractOperationalStateReportPart(operational.getRight());
        final var operationalStateReport = buildEpisodicOperationalStateReportWithParts(
                MdibBuilder.DEFAULT_SEQUENCE_ID, BigInteger.TWO, operationalStateReportPart);
        messageStorageUtil.addInboundSecureHttpMessage(storage, operationalStateReport);

        assertThrows(AssertionError.class, testClass::testRequirementR0056);
    }

    /**
     * Tests whether the test fails when the description modification report was send after the waveform stream
     * containing the changed state.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR0056BadWaveformStream() throws Exception {
        final var rtsaMetric = mdibBuilder.buildRealTimeSampleArrayMetric(
                RTSA_HANDLE,
                MetricCategory.CLC,
                MetricAvailability.CONT,
                mdibBuilder.buildCodedValue("rtsaCodedValue"),
                BigDecimal.TEN,
                datatypeFactory.newDuration("P0DT0H2M35S"));
        final var crtPart = buildDescriptionModificationReportPart(
                DescriptionModificationType.CRT,
                "otherParent",
                new ImmutablePair<>(rtsaMetric.getLeft(), rtsaMetric.getRight()));
        final var report = buildDescriptionModificationReport(MdibBuilder.DEFAULT_SEQUENCE_ID, BigInteger.TEN, crtPart);
        messageStorageUtil.addInboundSecureHttpMessage(storage, report);

        final var waveformStream =
                buildWaveformStream(MdibBuilder.DEFAULT_SEQUENCE_ID, BigInteger.TWO, rtsaMetric.getRight());
        messageStorageUtil.addInboundSecureHttpMessage(storage, waveformStream);

        assertThrows(AssertionError.class, testClass::testRequirementR0056);
    }

    /**
     * Tests whether the test fails when the description modification report was send after the observed value stream
     * containing the changed state.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR0056BadObservedValueStream() throws Exception {
        final var rtsaMetric = mdibBuilder.buildRealTimeSampleArrayMetric(
                RTSA_HANDLE,
                MetricCategory.CLC,
                MetricAvailability.CONT,
                mdibBuilder.buildCodedValue("rtsaCodedValue"),
                BigDecimal.TEN,
                datatypeFactory.newDuration("P0DT0H2M35S"));
        rtsaMetric.getRight().setStateVersion(BigInteger.TWO);
        final var sampleArrayValue = mdibBuilder.buildSampleArrayValue(List.of(BigDecimal.ONE, BigDecimal.TEN));
        rtsaMetric.getRight().setMetricValue(sampleArrayValue);
        final var desc = mdibBuilder.buildRealTimeSampleArrayMetricDescriptor(
                RTSA_HANDLE,
                MetricCategory.CLC,
                MetricAvailability.CONT,
                mdibBuilder.buildCodedValue("rtsaCodedValue"),
                BigDecimal.TEN,
                datatypeFactory.newDuration("P0DT0H2M35S"));
        final var state = mdibBuilder.buildRealTimeSampleArrayMetricState(RTSA_HANDLE);
        state.setStateVersion(BigInteger.TWO);
        state.setMetricValue(sampleArrayValue);

        final var crtPart = buildDescriptionModificationReportPart(
                DescriptionModificationType.CRT, "otherParent", new ImmutablePair<>(desc, state));
        final var report = buildDescriptionModificationReport(MdibBuilder.DEFAULT_SEQUENCE_ID, BigInteger.TEN, crtPart);
        messageStorageUtil.addInboundSecureHttpMessage(storage, report);

        final var observedValueValue = messageBuilder.buildObservedValue(RTSA_HANDLE, BigInteger.TWO, sampleArrayValue);
        final var observedValueStream =
                buildObservedValueStream(MdibBuilder.DEFAULT_SEQUENCE_ID, BigInteger.TWO, observedValueValue);
        messageStorageUtil.addInboundSecureHttpMessage(storage, observedValueStream);

        assertThrows(AssertionError.class, testClass::testRequirementR0056);
    }

    /**
     * Tests whether the test fails when the description modification report was send after the context report
     * containing the changed state.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR0056BadContextReport() throws Exception {
        final var locationContext =
                mdibBuilder.buildLocationContext("locationContextHandle", "locationContextStateHandle");

        final var crtPart = buildDescriptionModificationReportPart(
                DescriptionModificationType.CRT,
                PARENT_HANDLE,
                new ImmutablePair<>(locationContext.getLeft(), locationContext.getRight()));
        final var descriptionModification =
                buildDescriptionModificationReport(MdibBuilder.DEFAULT_SEQUENCE_ID, BigInteger.TEN, crtPart);
        messageStorageUtil.addInboundSecureHttpMessage(storage, descriptionModification);

        final var reportPart = buildAbstractContextReportPart(locationContext.getRight());
        final var contextReport =
                buildEpisodicContextReportWithParts(MdibBuilder.DEFAULT_SEQUENCE_ID, BigInteger.TWO, reportPart);
        messageStorageUtil.addInboundSecureHttpMessage(storage, contextReport);

        assertThrows(AssertionError.class, testClass::testRequirementR0056);
    }

    /**
     * Tests whether the test fails when a report with multiple report parts where just one part contains the same state
     * as the description modification report was send before the description modification report.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR0056Bad2() throws Exception {
        final var metric = mdibBuilder.buildNumericMetric(
                NUMERIC_HANDLE,
                MetricCategory.CLC,
                MetricAvailability.CONT,
                mdibBuilder.buildCodedValue("Millibar"),
                BigDecimal.ONE);
        final var stringMetric = mdibBuilder.buildStringMetric(
                STRING_HANDLE,
                MetricCategory.CLC,
                MetricAvailability.CONT,
                mdibBuilder.buildCodedValue("someCodedValue"));
        final var operational =
                mdibBuilder.buildActivateOperation("operationHandle", "operationTarget", OperatingMode.EN);
        final var crtPart = buildDescriptionModificationReportPart(
                DescriptionModificationType.CRT,
                PARENT_HANDLE,
                new ImmutablePair<>(metric.getLeft(), metric.getRight()),
                new ImmutablePair<>(stringMetric.getLeft(), stringMetric.getRight()));
        final var delPart = buildDescriptionModificationReportPart(
                DescriptionModificationType.DEL,
                "otherParent",
                new ImmutablePair<>(operational.getLeft(), operational.getRight()));
        final var descriptionModificationReport =
                buildDescriptionModificationReport(MdibBuilder.DEFAULT_SEQUENCE_ID, BigInteger.ZERO, crtPart, delPart);
        messageStorageUtil.addInboundSecureHttpMessage(storage, descriptionModificationReport);

        final var alertCondition = mdibBuilder.buildAlertCondition(
                "alertCondition", AlertConditionKind.PHY, AlertConditionPriority.HI, AlertActivation.ON);
        final var alertSignal =
                mdibBuilder.buildAlertSignal("alertSignal", AlertSignalManifestation.TAN, true, AlertActivation.ON);
        final var alertSystem = mdibBuilder.buildAlertSystem("alertSystem", AlertActivation.ON);

        final var crtPart2 = buildDescriptionModificationReportPart(
                DescriptionModificationType.CRT,
                "alertSystem",
                new ImmutablePair<>(alertCondition.getLeft(), alertCondition.getRight()),
                new ImmutablePair<>(alertSignal.getLeft(), alertSignal.getRight()));
        final var uptPart2 = buildDescriptionModificationReportPart(
                DescriptionModificationType.UPT,
                PARENT_HANDLE,
                new ImmutablePair<>(alertSystem.getLeft(), alertSystem.getRight()));

        final var descriptionModificationReport2 =
                buildDescriptionModificationReport(MdibBuilder.DEFAULT_SEQUENCE_ID, BigInteger.ONE, crtPart2, uptPart2);
        messageStorageUtil.addInboundSecureHttpMessage(storage, descriptionModificationReport2);

        final var unrelatedAlertSignal = mdibBuilder.buildAlertSignalState("unrelatedAlertSignal", AlertActivation.OFF);
        final var alertPartOne = buildAbstractAlertReportPart(unrelatedAlertSignal);
        final var alertPartTwo = buildAbstractAlertReportPart(alertSignal.getRight());
        final var alertReportBefore = buildEpisodicAlertReportWithParts(
                MdibBuilder.DEFAULT_SEQUENCE_ID, BigInteger.ZERO, alertPartOne, alertPartTwo);
        messageStorageUtil.addInboundSecureHttpMessage(storage, alertReportBefore);

        assertThrows(AssertionError.class, testClass::testRequirementR0056);
    }

    @SafeVarargs
    final DescriptionModificationReport.ReportPart buildDescriptionModificationReportPart(
            final DescriptionModificationType modificationType,
            final String parentDescriptor,
            final Pair<? extends AbstractDescriptor, ? extends AbstractState>... modifications) {
        final var reportPart = messageBuilder.buildDescriptionModificationReportReportPart();
        reportPart.setModificationType(modificationType);
        reportPart.setParentDescriptor(parentDescriptor);
        for (var modification : modifications) {
            reportPart.getDescriptor().add(modification.getLeft());
            if (modificationType != DescriptionModificationType.DEL) {
                reportPart.getState().add(modification.getRight());
            }
        }
        return reportPart;
    }

    Envelope buildDescriptionModificationReport(
            final String sequenceId,
            final BigInteger mdibVersion,
            final DescriptionModificationReport.ReportPart... reportParts) {
        final var report = messageBuilder.buildDescriptionModificationReport(sequenceId, List.of(reportParts));
        report.setMdibVersion(mdibVersion);
        return messageBuilder.createSoapMessageWithBody(ActionConstants.ACTION_DESCRIPTION_MODIFICATION_REPORT, report);
    }

    AbstractMetricReport.ReportPart buildAbstractMetricReportPart(final AbstractMetricState... metricStates) {
        final var reportPart = messageBuilder.buildAbstractMetricReportReportPart();
        for (var metricState : metricStates) {
            reportPart.getMetricState().add(metricState);
        }
        return reportPart;
    }

    Envelope buildEpisodicMetricReportWithParts(
            final String sequenceId,
            final BigInteger mdibVersion,
            final AbstractMetricReport.ReportPart... reportParts) {
        final var report = messageBuilder.buildEpisodicMetricReport(sequenceId);
        report.setMdibVersion(mdibVersion);
        for (var reportPart : reportParts) {
            report.getReportPart().add(reportPart);
        }
        return messageBuilder.createSoapMessageWithBody(ActionConstants.ACTION_EPISODIC_METRIC_REPORT, report);
    }

    AbstractOperationalStateReport.ReportPart buildAbstractOperationalStateReportPart(
            final AbstractOperationState... operationStates) {
        final var reportPart = messageBuilder.buildAbstractOperationalStateReportReportPart();
        for (var operationState : operationStates) {
            reportPart.getOperationState().add(operationState);
        }
        return reportPart;
    }

    Envelope buildEpisodicOperationalStateReportWithParts(
            final String sequenceId,
            final BigInteger mdibVersion,
            final AbstractOperationalStateReport.ReportPart... reportParts) {
        final var report = messageBuilder.buildEpisodicOperationalStateReport(sequenceId);
        report.setMdibVersion(mdibVersion);
        for (var reportPart : reportParts) {
            report.getReportPart().add(reportPart);
        }
        return messageBuilder.createSoapMessageWithBody(
                ActionConstants.ACTION_EPISODIC_OPERATIONAL_STATE_REPORT, report);
    }

    AbstractContextReport.ReportPart buildAbstractContextReportPart(final AbstractContextState... contextStates) {
        final var reportPart = messageBuilder.buildAbstractContextReportReportPart();
        for (var contextState : contextStates) {
            reportPart.getContextState().add(contextState);
        }
        return reportPart;
    }

    Envelope buildEpisodicContextReportWithParts(
            final String sequenceId,
            final BigInteger mdibVersion,
            final AbstractContextReport.ReportPart... reportParts) {
        final var report = messageBuilder.buildEpisodicContextReport(sequenceId);
        report.setMdibVersion(mdibVersion);
        for (var reportPart : reportParts) {
            report.getReportPart().add(reportPart);
        }
        return messageBuilder.createSoapMessageWithBody(ActionConstants.ACTION_EPISODIC_CONTEXT_REPORT, report);
    }

    AbstractAlertReport.ReportPart buildAbstractAlertReportPart(final AbstractAlertState... alertStates) {
        final var reportPart = messageBuilder.buildAbstractAlertReportReportPart();
        for (var alertState : alertStates) {
            reportPart.getAlertState().add(alertState);
        }
        return reportPart;
    }

    Envelope buildEpisodicAlertReportWithParts(
            final String sequenceId,
            final BigInteger mdibVersion,
            final AbstractAlertReport.ReportPart... reportParts) {
        final var report = messageBuilder.buildEpisodicAlertReport(sequenceId);
        report.setMdibVersion(mdibVersion);
        for (var reportPart : reportParts) {
            report.getReportPart().add(reportPart);
        }
        return messageBuilder.createSoapMessageWithBody(ActionConstants.ACTION_EPISODIC_ALERT_REPORT, report);
    }

    Envelope buildEpisodicAlertReport(
            final String sequenceId, final BigInteger mdibVersion, final AbstractAlertState... alertStates) {
        final var reportPart = messageBuilder.buildAbstractAlertReportReportPart();
        for (var alertState : alertStates) {
            reportPart.getAlertState().add(alertState);
        }

        return buildEpisodicAlertReportWithParts(sequenceId, mdibVersion, reportPart);
    }

    Envelope buildEpisodicComponentReportWithParts(
            final String sequenceId,
            final BigInteger mdibVersion,
            final AbstractComponentReport.ReportPart... reportParts) {
        final var report = messageBuilder.buildEpisodicComponentReport(sequenceId);
        report.setMdibVersion(mdibVersion);
        for (var reportPart : reportParts) {
            report.getReportPart().add(reportPart);
        }
        return messageBuilder.createSoapMessageWithBody(ActionConstants.ACTION_EPISODIC_COMPONENT_REPORT, report);
    }

    Envelope buildEpisodicComponentReport(
            final String sequenceId,
            final BigInteger mdibVersion,
            final AbstractDeviceComponentState... componentStates) {
        final var reportPart = messageBuilder.buildAbstractComponentReportReportPart();
        for (var componentState : componentStates) {
            reportPart.getComponentState().add(componentState);
        }
        return buildEpisodicComponentReportWithParts(sequenceId, mdibVersion, reportPart);
    }

    private Envelope buildWaveformStream(
            final String sequenceId, final BigInteger mdibVersion, final RealTimeSampleArrayMetricState... states) {

        final var waveform = messageBuilder.buildWaveformStream(
                sequenceId, Arrays.stream(states).toList());
        waveform.setMdibVersion(mdibVersion);
        return messageBuilder.createSoapMessageWithBody(ActionConstants.ACTION_WAVEFORM_STREAM, waveform);
    }

    private Envelope buildObservedValueStream(
            final String sequenceId, final BigInteger mdibVersion, final ObservedValueStream.Value... values) {
        final var observedValueStream = messageBuilder.buildObservedValueStream(
                sequenceId, Arrays.stream(values).toList());
        observedValueStream.setMdibVersion(mdibVersion);
        return messageBuilder.createSoapMessageWithBody(
                ActionConstants.ACTION_OBSERVED_VALUE_STREAM, observedValueStream);
    }
}
