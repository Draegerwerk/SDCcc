/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.tests.biceps.invariant;

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
import com.draeger.medical.biceps.model.participant.CodedValue;
import com.draeger.medical.biceps.model.participant.ComponentActivation;
import com.draeger.medical.biceps.model.participant.ContextAssociation;
import com.draeger.medical.biceps.model.participant.LocationContextDescriptor;
import com.draeger.medical.biceps.model.participant.LocationContextState;
import com.draeger.medical.biceps.model.participant.MdsDescriptor;
import com.draeger.medical.biceps.model.participant.MdsState;
import com.draeger.medical.biceps.model.participant.MetricAvailability;
import com.draeger.medical.biceps.model.participant.MetricCategory;
import com.draeger.medical.biceps.model.participant.NumericMetricValue;
import com.draeger.medical.biceps.model.participant.OperatingMode;
import com.draeger.medical.biceps.model.participant.OperatorContextDescriptor;
import com.draeger.medical.biceps.model.participant.OperatorContextState;
import com.draeger.medical.biceps.model.participant.PatientContextDescriptor;
import com.draeger.medical.biceps.model.participant.PatientContextState;
import com.draeger.medical.biceps.model.participant.StringMetricValue;
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
import jakarta.xml.bind.JAXBException;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nullable;
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
 * Unit test for the BICEPS {@linkplain InvariantMessageModelAnnexTest}.
 */
public class InvariantMessageModelAnnexTestTest {

    private static final String VMD_HANDLE = "someVmd";
    private static final String MDS_ALERT_SYSTEM_HANDLE = "someMdsAlertSystem";
    private static final String MDS_ALERT_CONDITION_HANDLE = "someMdsAlertCondition";
    private static final String MDS_ALERT_SIGNAL_HANDLE = "someMdsAlertSignal";
    private static final String MDS_SECOND_ALERT_CONDITION_HANDLE = "someOtherMdsAlertCondition";
    private static final String MDS_SECOND_ALERT_SIGNAL_HANDLE = "someOtherMdsAlertSignal";
    private static final String VMD_ALERT_SYSTEM_HANDLE = "someVmdAlertSystem";
    private static final String VMD_ALERT_CONDITION_HANDLE = "someVmdAlertCondition";
    private static final String VMD_ALERT_SIGNAL_HANDLE = "someVmdAlertSignal";
    private static final String BATTERY_HANDLE = "someBattery";
    private static final String SCO_HANDLE = "someSco";
    private static final String SET_STRING_OPERATION_HANDLE = "someSetStringOperation";
    private static final String ACTIVATE_OPERATION_HANDLE = "someActivateOperation";
    private static final String SYSTEM_CONTEXT_HANDLE = "someSystemContext";
    private static final String PATIENT_CONTEXT_DESCRIPTOR_HANDLE = "somePatientContext";
    private static final String PATIENT_CONTEXT_STATE_HANDLE = "somePatientContextState";
    private static final String LOCATION_CONTEXT_DESCRIPTOR_HANDLE = "someLocationContext";
    private static final String LOCATION_CONTEXT_STATE_HANDLE = "someLocationContextState";
    private static final String OPERATOR_CONTEXT_DESCRIPTOR_HANDLE = "someOperatorContext";
    private static final String OPERATOR_CONTEXT_STATE_HANDLE = "someOperatorContextState";
    private static final String NEW_OPERATOR_CONTEXT_STATE_HANDLE = "toBeCreatedOperatorContextState";
    private static final String CHANNEL_HANDLE = "someChannel";
    private static final String STRING_METRIC_HANDLE = "someStringMetric";
    private static final String NUMERIC_METRIC_HANDLE = "someNumericMetric";
    private static final String SEQUENCE_ID = MdibBuilder.DEFAULT_SEQUENCE_ID;
    private static final String SEQUENCE_ID2 = "123457";

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    private static MessageStorageUtil messageStorageUtil;
    private static MdibBuilder mdibBuilder;
    private static MessageBuilder messageBuilder;
    private InvariantMessageModelAnnexTest testClass;
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
        final TestClient mockClient = mock(TestClient.class);
        when(mockClient.isClientRunning()).thenReturn(true);

        final Injector injector = InjectorUtil.setupInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(TestClient.class).toInstance(mockClient);
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

        testClass = new InvariantMessageModelAnnexTest();
        testClass.setup();
    }

    @AfterEach
    void testDown() throws TimeoutException {
        baseMarshalling.stopAsync().awaitTerminated(DEFAULT_TIMEOUT);
        marshalling.stopAsync().awaitTerminated(DEFAULT_TIMEOUT);
        storage.close();
    }

    /**
     * Tests whether calling the test without any input data causes a failure.
     */
    @Test
    public void testRequirementC5NoTestData() {
        assertThrows(NoTestData.class, testClass::testRequirementC5);
    }

    /**
     * Checks whether a sequence of DescriptionModificationReports,
     * in which each DescriptionModificationReport
     * contains only AbstractDescriptors that have been inserted or deleted or updated by changing
     * at least one child or attribute,
     * passes the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementC5Good() throws Exception {
        final Envelope initial = buildMdib(SEQUENCE_ID, BigInteger.ZERO);
        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);

        final MdsDescriptor mdsDescriptor = mdibBuilder.buildMdsDescriptor(MdibBuilder.DEFAULT_MDS_HANDLE);
        mdsDescriptor.setDescriptorVersion(BigInteger.TEN);
        final MdsState mdsState = mdibBuilder.buildMdsState(MdibBuilder.DEFAULT_MDS_HANDLE);

        final Envelope first = buildDescriptionModificationReport(
                SEQUENCE_ID,
                BigInteger.ONE,
                buildDescriptionModificationReportPart(
                        DescriptionModificationType.UPT, new ImmutablePair<>(mdsDescriptor, mdsState)));
        messageStorageUtil.addInboundSecureHttpMessage(storage, first);

        final Envelope second = buildDescriptionModificationReport(
                SEQUENCE_ID,
                BigInteger.TWO,
                buildDescriptionModificationReportPart(
                        DescriptionModificationType.CRT,
                        mdibBuilder.buildChannel("second channel"),
                        mdibBuilder.buildVmd("second vmd")));
        messageStorageUtil.addInboundSecureHttpMessage(storage, second);

        final Envelope third = buildDescriptionModificationReport(
                SEQUENCE_ID,
                BigInteger.valueOf(3),
                buildDescriptionModificationReportPart(
                        DescriptionModificationType.DEL,
                        mdibBuilder.buildSystemContext(SYSTEM_CONTEXT_HANDLE),
                        mdibBuilder.buildSco(SCO_HANDLE)));
        messageStorageUtil.addInboundSecureHttpMessage(storage, third);

        testClass.testRequirementC5();
    }

    /**
     * Checks whether a DescriptionModificationReport
     * containing an AbstractDescriptor without any change,
     * fails the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementC5Bad() throws Exception {
        final Envelope initial = buildMdib(SEQUENCE_ID, BigInteger.ZERO);
        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);

        final MdsDescriptor mdsDescriptor = mdibBuilder.buildMdsDescriptor(MdibBuilder.DEFAULT_MDS_HANDLE);
        mdsDescriptor.setDescriptorVersion(BigInteger.ZERO);
        final MdsState mdsState = mdibBuilder.buildMdsState(MdibBuilder.DEFAULT_MDS_HANDLE);

        final Envelope first = buildDescriptionModificationReport(
                SEQUENCE_ID,
                BigInteger.ONE,
                buildDescriptionModificationReportPart(
                        DescriptionModificationType.UPT, new ImmutablePair<>(mdsDescriptor, mdsState)));
        messageStorageUtil.addInboundSecureHttpMessage(storage, first);

        assertThrows(AssertionError.class, testClass::testRequirementC5);
    }

    /**
     * Tests whether calling the test without any input data causes a failure.
     */
    @Test
    public void testRequirementR5024NoTestData() {
        assertThrows(NoTestData.class, testClass::testRequirementR5024);
    }

    /**
     * Checks whether DescriptionModificationReports which do not contain nested descriptors pass the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR5024Good() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID, BigInteger.ZERO);
        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);

        final var first = buildDescriptionModificationReport(
                SEQUENCE_ID,
                BigInteger.ONE,
                buildDescriptionModificationReportPart(
                        DescriptionModificationType.UPT,
                        mdibBuilder.buildMds(MdibBuilder.DEFAULT_MDS_HANDLE),
                        mdibBuilder.buildAlertSystem(MDS_ALERT_SYSTEM_HANDLE, AlertActivation.ON)));
        messageStorageUtil.addInboundSecureHttpMessage(storage, first);

        final var second = buildDescriptionModificationReport(
                SEQUENCE_ID,
                BigInteger.TWO,
                buildDescriptionModificationReportPart(
                        DescriptionModificationType.CRT,
                        mdibBuilder.buildChannel("second channel"),
                        mdibBuilder.buildVmd("second vmd")));
        messageStorageUtil.addInboundSecureHttpMessage(storage, second);

        final var third = buildDescriptionModificationReport(
                SEQUENCE_ID,
                BigInteger.valueOf(3),
                buildDescriptionModificationReportPart(
                        DescriptionModificationType.DEL,
                        mdibBuilder.buildSystemContext(SYSTEM_CONTEXT_HANDLE),
                        mdibBuilder.buildSco(SCO_HANDLE)));
        messageStorageUtil.addInboundSecureHttpMessage(storage, third);

        testClass.testRequirementR5024();
    }

    /**
     * Checks whether DescriptionModificationReports which contain nested descriptors fail the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR5024Bad() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID, BigInteger.ZERO);
        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);

        final var mdsAlertSystem = mdibBuilder.buildAlertSystem(MDS_ALERT_SYSTEM_HANDLE, AlertActivation.ON);
        final var mdsAlertCondition = mdibBuilder.buildAlertCondition(
                MDS_ALERT_CONDITION_HANDLE, AlertConditionKind.OTH, AlertConditionPriority.ME, AlertActivation.ON);
        mdsAlertSystem.getLeft().getAlertCondition().clear();
        mdsAlertSystem.getLeft().getAlertCondition().add(mdsAlertCondition.getLeft());
        final var first = buildDescriptionModificationReport(
                SEQUENCE_ID,
                BigInteger.ONE,
                buildDescriptionModificationReportPart(DescriptionModificationType.UPT, mdsAlertSystem));
        messageStorageUtil.addInboundSecureHttpMessage(storage, first);

        assertThrows(AssertionError.class, testClass::testRequirementR5024);
    }

    /**
     * Tests whether calling the test without any input data causes a failure.
     */
    @Test
    public void testRequirementR5025NoTestData() {
        assertThrows(NoTestData.class, testClass::testRequirementR5025);
    }

    /**
     * Checks whether DescriptionModificationReports with properly ordered update modification record parts pass
     * the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR5025GoodWithUpdates() throws Exception {
        testRequirementR5025Good(DescriptionModificationType.UPT);
    }

    /**
     * Checks whether DescriptionModificationReports with properly ordered insert modification record parts pass
     * the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR5025GoodWithInserts() throws Exception {
        testRequirementR5025Good(DescriptionModificationType.CRT);
    }

    /**
     * Checks whether DescriptionModificationReports with properly ordered removal modification record parts pass
     * the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR5025GoodWithRemovals() throws Exception {
        testRequirementR5025Good(DescriptionModificationType.DEL);
    }

    private void testRequirementR5025Good(final DescriptionModificationType modificationType) throws Exception {
        final var initial = buildMdib(SEQUENCE_ID, BigInteger.ZERO);
        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);

        final var parentDescriptor = "parent1";
        final String firstChannelHandle = "first channel";
        final var first = buildDescriptionModificationReport(
                SEQUENCE_ID,
                BigInteger.ONE,
                buildDescriptionModificationReportPart(
                        modificationType, "someParentHandle", mdibBuilder.buildVmd(parentDescriptor)),
                buildDescriptionModificationReportPart(
                        modificationType,
                        parentDescriptor,
                        mdibBuilder.buildChannel(firstChannelHandle),
                        mdibBuilder.buildChannel("second channel")),
                buildDescriptionModificationReportPart(
                        modificationType,
                        firstChannelHandle,
                        mdibBuilder.buildSystemContext(SYSTEM_CONTEXT_HANDLE),
                        mdibBuilder.buildNumericMetric(
                                "firstChannelfirstMetric",
                                MetricCategory.CLC,
                                MetricAvailability.INTR,
                                mdibBuilder.buildCodedValue("Millibar"),
                                BigDecimal.ONE),
                        mdibBuilder.buildSco(SCO_HANDLE)));
        messageStorageUtil.addInboundSecureHttpMessage(storage, first);

        testClass.testRequirementR5025();
    }

    /**
     * Checks whether a DescriptionModificationReport with an Empty ReportPart passes
     * the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR5025GoodWithEmptyReportPart() throws Exception {
        final var modificationType = DescriptionModificationType.UPT;
        final var initial = buildMdib(SEQUENCE_ID, BigInteger.ZERO);
        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);

        final var parentDescriptor = "parent1";
        final String firstChannelHandle = "first channel";
        final var first = buildDescriptionModificationReport(
                SEQUENCE_ID,
                BigInteger.ONE,
                buildDescriptionModificationReportPart(
                        modificationType, "someParentHandle", mdibBuilder.buildVmd(parentDescriptor)),
                // Empty Report Part
                buildDescriptionModificationReportPart(modificationType, parentDescriptor),
                buildDescriptionModificationReportPart(
                        modificationType,
                        parentDescriptor,
                        mdibBuilder.buildChannel(firstChannelHandle),
                        mdibBuilder.buildChannel("third channel")));
        messageStorageUtil.addInboundSecureHttpMessage(storage, first);

        testClass.testRequirementR5025();
    }

    /**
     * Checks whether DescriptionModificationReports with incorrectly ordered update modification record parts
     * fail the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR5025BadWithUpdates() throws Exception {
        testRequirementR5025Bad(DescriptionModificationType.UPT);
    }

    /**
     * Checks whether DescriptionModificationReports with incorrectly ordered insert modification record parts
     * fail the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR5025BadWithInserts() throws Exception {
        testRequirementR5025Bad(DescriptionModificationType.CRT);
    }

    /**
     * Checks whether DescriptionModificationReports with incorrectly ordered removal modification record parts
     * fail the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR5025BadWithRemovals() throws Exception {
        testRequirementR5025Bad(DescriptionModificationType.DEL);
    }

    private void testRequirementR5025Bad(final DescriptionModificationType modificationType)
            throws JAXBException, IOException {
        final var initial = buildMdib(SEQUENCE_ID, BigInteger.ZERO);
        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);

        final var parentDescriptor = "parent1";
        final String firstChannelHandle = "first channel";
        final var first = buildDescriptionModificationReport(
                SEQUENCE_ID,
                BigInteger.ONE,
                buildDescriptionModificationReportPart(
                        modificationType, "someParentHandle", mdibBuilder.buildVmd(parentDescriptor)),
                buildDescriptionModificationReportPart(
                        modificationType,
                        firstChannelHandle,
                        mdibBuilder.buildSystemContext(SYSTEM_CONTEXT_HANDLE),
                        mdibBuilder.buildNumericMetric(
                                "firstChannelfirstMetric",
                                MetricCategory.CLC,
                                MetricAvailability.INTR,
                                mdibBuilder.buildCodedValue("Millibar"),
                                BigDecimal.ONE),
                        mdibBuilder.buildSco(SCO_HANDLE)),
                buildDescriptionModificationReportPart(
                        modificationType,
                        parentDescriptor,
                        mdibBuilder.buildChannel(firstChannelHandle),
                        mdibBuilder.buildChannel("second channel")));
        messageStorageUtil.addInboundSecureHttpMessage(storage, first);

        assertThrows(AssertionError.class, testClass::testRequirementR5025);
    }

    /**
     * Checks whether DescriptionModificationReports with incorrectly ordered update modification record parts
     * fail the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR5025Bad2WithUpdates() throws Exception {
        testRequirementR5025Bad2(DescriptionModificationType.UPT);
    }

    /**
     * Checks whether DescriptionModificationReports with incorrectly ordered insert modification record parts
     * fail the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR5025Bad2WithInserts() throws Exception {
        testRequirementR5025Bad2(DescriptionModificationType.CRT);
    }

    /**
     * Checks whether DescriptionModificationReports with incorrectly ordered removal modification record parts
     * fail the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR5025Bad2WithRemovals() throws Exception {
        testRequirementR5025Bad2(DescriptionModificationType.DEL);
    }

    private void testRequirementR5025Bad2(final DescriptionModificationType modificationType)
            throws JAXBException, IOException {
        final var initial = buildMdib(SEQUENCE_ID, BigInteger.ZERO);
        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);

        final var parentDescriptor = "parent1";
        final String firstChannelHandle = "first channel";
        final var first = buildDescriptionModificationReport(
                SEQUENCE_ID,
                BigInteger.ONE,
                buildDescriptionModificationReportPart(
                        modificationType,
                        parentDescriptor,
                        mdibBuilder.buildChannel(firstChannelHandle),
                        mdibBuilder.buildChannel("second channel")),
                buildDescriptionModificationReportPart(
                        modificationType, "someParentHandle", mdibBuilder.buildVmd(parentDescriptor)),
                buildDescriptionModificationReportPart(
                        modificationType,
                        firstChannelHandle,
                        mdibBuilder.buildSystemContext(SYSTEM_CONTEXT_HANDLE),
                        mdibBuilder.buildNumericMetric(
                                "firstChannelfirstMetric",
                                MetricCategory.CLC,
                                MetricAvailability.INTR,
                                mdibBuilder.buildCodedValue("Millibar"),
                                BigDecimal.ONE),
                        mdibBuilder.buildSco(SCO_HANDLE)));
        messageStorageUtil.addInboundSecureHttpMessage(storage, first);

        assertThrows(AssertionError.class, testClass::testRequirementR5025);
    }

    /**
     * Tests whether calling the test without any input data causes a failure.
     */
    @Test
    public void testRequirementR5051NoTestData() {
        assertThrows(NoTestData.class, testClass::testRequirementR5051);
    }

    /**
     * Tests whether description modification reports with report parts with modification type crt, containing only
     * states whose descriptor version matches the version of the respective descriptor, passes the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR5051Good() throws Exception {
        final var alertCondition = mdibBuilder.buildAlertCondition(
                MDS_ALERT_CONDITION_HANDLE, AlertConditionKind.OTH, AlertConditionPriority.ME, AlertActivation.ON);
        alertCondition.getLeft().setDescriptorVersion(BigInteger.ONE);
        alertCondition.getRight().setDescriptorVersion(BigInteger.ONE);
        final var first = buildDescriptionModificationReport(
                SEQUENCE_ID,
                BigInteger.ONE,
                buildDescriptionModificationReportPart(DescriptionModificationType.CRT, alertCondition));
        messageStorageUtil.addInboundSecureHttpMessage(storage, first);

        // wrong modification type, should be ignored
        alertCondition.getLeft().setDescriptorVersion(BigInteger.TWO);
        final var second = buildDescriptionModificationReport(
                SEQUENCE_ID,
                BigInteger.TWO,
                buildDescriptionModificationReportPart(DescriptionModificationType.UPT, alertCondition));
        messageStorageUtil.addInboundSecureHttpMessage(storage, second);

        testClass.testRequirementR5051();
    }

    /**
     * Tests whether description modification reports with report parts with modification type crt, containing only
     * states whose descriptor version matches the version of the respective descriptor and implied values are used,
     * passes the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR5051GoodImpliedValue() throws Exception {
        final var alertCondition = mdibBuilder.buildAlertCondition(
                MDS_ALERT_CONDITION_HANDLE, AlertConditionKind.OTH, AlertConditionPriority.ME, AlertActivation.ON);
        alertCondition.getLeft().setDescriptorVersion(BigInteger.ZERO);
        final var first = buildDescriptionModificationReport(
                SEQUENCE_ID,
                BigInteger.ONE,
                buildDescriptionModificationReportPart(DescriptionModificationType.CRT, alertCondition));
        messageStorageUtil.addInboundSecureHttpMessage(storage, first);

        testClass.testRequirementR5051();
    }

    /**
     * Tests whether description modification reports with report parts with modification type crt, containing only
     * states whose descriptor version matches the version of the respective descriptor and implied values are used,
     * passes the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR5051BadImpliedValue() throws Exception {
        final var alertCondition = mdibBuilder.buildAlertCondition(
                MDS_ALERT_CONDITION_HANDLE, AlertConditionKind.OTH, AlertConditionPriority.ME, AlertActivation.ON);
        alertCondition.getLeft().setDescriptorVersion(BigInteger.ZERO);
        final var first = buildDescriptionModificationReport(
                SEQUENCE_ID,
                BigInteger.ZERO,
                buildDescriptionModificationReportPart(DescriptionModificationType.CRT, alertCondition));

        final var secondAlertCondition = mdibBuilder.buildAlertCondition(
                MDS_ALERT_CONDITION_HANDLE, AlertConditionKind.OTH, AlertConditionPriority.ME, AlertActivation.ON);
        secondAlertCondition.getLeft().setDescriptorVersion(BigInteger.ONE);
        secondAlertCondition.getRight().setDescriptorVersion(BigInteger.ONE);
        final var second = buildDescriptionModificationReport(
                SEQUENCE_ID,
                BigInteger.ONE,
                buildDescriptionModificationReportPart(DescriptionModificationType.CRT, secondAlertCondition));

        final var thirdAlertCondition = mdibBuilder.buildAlertCondition(
                MDS_ALERT_CONDITION_HANDLE, AlertConditionKind.OTH, AlertConditionPriority.ME, AlertActivation.ON);
        thirdAlertCondition.getLeft().setDescriptorVersion(BigInteger.ONE);
        final var third = buildDescriptionModificationReport(
                SEQUENCE_ID,
                BigInteger.TWO,
                buildDescriptionModificationReportPart(DescriptionModificationType.CRT, thirdAlertCondition));

        messageStorageUtil.addInboundSecureHttpMessage(storage, first);
        messageStorageUtil.addInboundSecureHttpMessage(storage, second);
        messageStorageUtil.addInboundSecureHttpMessage(storage, third);

        assertThrows(AssertionError.class, () -> testClass.testRequirementR5051());
    }

    /**
     * Tests whether description modification reports with a report part with modification type crt containing states
     * whose descriptor version does not match the version of the respective descriptor, cause the test to fail.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR5051BadVersionMismatch() throws Exception {
        final var alertCondition = mdibBuilder.buildAlertCondition(
                MDS_ALERT_CONDITION_HANDLE, AlertConditionKind.OTH, AlertConditionPriority.ME, AlertActivation.ON);
        alertCondition.getLeft().setDescriptorVersion(BigInteger.ONE);
        alertCondition.getRight().setDescriptorVersion(BigInteger.TWO);
        final var first = buildDescriptionModificationReport(
                SEQUENCE_ID,
                BigInteger.ONE,
                buildDescriptionModificationReportPart(DescriptionModificationType.CRT, alertCondition));

        messageStorageUtil.addInboundSecureHttpMessage(storage, first);

        assertThrows(AssertionError.class, testClass::testRequirementR5051);
    }

    /**
     * Tests whether description modification reports with a report part with modification type crt, containing states
     * but not the respective descriptors, cause the test to fail.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR5051BadMissingDescriptor() throws Exception {
        final var alertConditionState =
                mdibBuilder.buildAlertConditionState(MDS_ALERT_CONDITION_HANDLE, AlertActivation.ON);
        alertConditionState.setDescriptorVersion(BigInteger.TWO);
        final var reportPart = messageBuilder.buildDescriptionModificationReportReportPart();
        reportPart.setModificationType(DescriptionModificationType.CRT);
        reportPart.getState().add(alertConditionState);

        final var first = buildDescriptionModificationReport(SEQUENCE_ID, BigInteger.ONE, reportPart);

        messageStorageUtil.addInboundSecureHttpMessage(storage, first);

        assertThrows(AssertionError.class, testClass::testRequirementR5051);
    }

    /**
     * Tests whether calling the test without any input data causes a failure.
     */
    @Test
    public void testRequirementR5052NoTestData() {
        assertThrows(NoTestData.class, testClass::testRequirementR5052);
    }

    /**
     * Tests whether description modification reports with report parts with modification type upt, containing only
     * states whose descriptor version matches the version of the respective descriptor, passes the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR5052Good() throws Exception {
        final var alertCondition = mdibBuilder.buildAlertCondition(
                MDS_ALERT_CONDITION_HANDLE, AlertConditionKind.OTH, AlertConditionPriority.ME, AlertActivation.ON);
        alertCondition.getLeft().setDescriptorVersion(BigInteger.ONE);
        alertCondition.getRight().setDescriptorVersion(BigInteger.ONE);
        final var first = buildDescriptionModificationReport(
                SEQUENCE_ID,
                BigInteger.ONE,
                buildDescriptionModificationReportPart(DescriptionModificationType.UPT, alertCondition));
        messageStorageUtil.addInboundSecureHttpMessage(storage, first);

        // wrong modification type, should be ignored
        alertCondition.getLeft().setDescriptorVersion(BigInteger.TWO);
        final var second = buildDescriptionModificationReport(
                SEQUENCE_ID,
                BigInteger.TWO,
                buildDescriptionModificationReportPart(DescriptionModificationType.CRT, alertCondition));
        messageStorageUtil.addInboundSecureHttpMessage(storage, second);

        testClass.testRequirementR5052();
    }

    /**
     * Tests whether description modification reports with report parts with modification type upt, containing only
     * states whose descriptor version matches the version of the respective descriptor and implied values are used,
     * passes the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR5052GoodImpliedValue() throws Exception {
        final var alertCondition = mdibBuilder.buildAlertCondition(
                MDS_ALERT_CONDITION_HANDLE, AlertConditionKind.OTH, AlertConditionPriority.ME, AlertActivation.ON);
        alertCondition.getLeft().setDescriptorVersion(BigInteger.ZERO);
        final var first = buildDescriptionModificationReport(
                SEQUENCE_ID,
                BigInteger.ONE,
                buildDescriptionModificationReportPart(DescriptionModificationType.UPT, alertCondition));
        messageStorageUtil.addInboundSecureHttpMessage(storage, first);

        testClass.testRequirementR5052();
    }

    /**
     * Tests whether description modification reports with a report part with modification type upt containing states
     * whose descriptor version does not match the version of the respective descriptor, cause the test to fail.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR5052BadVersionMismatch() throws Exception {
        final var alertCondition = mdibBuilder.buildAlertCondition(
                MDS_ALERT_CONDITION_HANDLE, AlertConditionKind.OTH, AlertConditionPriority.ME, AlertActivation.ON);
        alertCondition.getLeft().setDescriptorVersion(BigInteger.ONE);
        alertCondition.getRight().setDescriptorVersion(BigInteger.TWO);
        final var first = buildDescriptionModificationReport(
                SEQUENCE_ID,
                BigInteger.ONE,
                buildDescriptionModificationReportPart(DescriptionModificationType.UPT, alertCondition));

        messageStorageUtil.addInboundSecureHttpMessage(storage, first);

        assertThrows(AssertionError.class, testClass::testRequirementR5052);
    }

    /**
     * Tests whether description modification reports with a report part with modification type upt, containing states
     * but not the respective descriptors, cause the test to fail.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR5052BadMissingDescriptor() throws Exception {
        final var alertConditionState =
                mdibBuilder.buildAlertConditionState(MDS_ALERT_CONDITION_HANDLE, AlertActivation.ON);
        alertConditionState.setDescriptorVersion(BigInteger.TWO);
        final var reportPart = messageBuilder.buildDescriptionModificationReportReportPart();
        reportPart.setModificationType(DescriptionModificationType.UPT);
        reportPart.getState().add(alertConditionState);

        final var first = buildDescriptionModificationReport(SEQUENCE_ID, BigInteger.ONE, reportPart);

        messageStorageUtil.addInboundSecureHttpMessage(storage, first);

        assertThrows(AssertionError.class, testClass::testRequirementR5052);
    }

    /**
     * Tests whether calling the test without any input data causes a failure.
     */
    @Test
    public void testRequirementC11NoTestData() {
        assertThrows(NoTestData.class, testClass::testRequirementC11);
    }

    /**
     * Tests whether EpisodicAlertReports which contain only AbstractAlertStates with at least one changed child
     * element or attribute passes the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementC11Good() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID, BigInteger.ZERO);

        final var first = buildEpisodicAlertReport(
                SEQUENCE_ID,
                BigInteger.ONE,
                buildAlertConditionState(VMD_ALERT_CONDITION_HANDLE, AlertActivation.PSD, false),
                buildAlertConditionState(MDS_ALERT_CONDITION_HANDLE, AlertActivation.PSD, false),
                buildAlertConditionState(MDS_SECOND_ALERT_CONDITION_HANDLE, AlertActivation.PSD, false));

        final var second = buildEpisodicAlertReport(
                SEQUENCE_ID,
                BigInteger.TWO,
                buildAlertConditionState(VMD_ALERT_CONDITION_HANDLE, AlertActivation.ON, true),
                buildAlertConditionState(MDS_ALERT_CONDITION_HANDLE, AlertActivation.ON, true),
                buildAlertConditionState(MDS_SECOND_ALERT_CONDITION_HANDLE, AlertActivation.ON, true));

        final var third = buildEpisodicAlertReport(
                SEQUENCE_ID,
                BigInteger.valueOf(3),
                buildAlertSystemState(VMD_ALERT_SYSTEM_HANDLE, AlertActivation.PSD),
                buildAlertSignalState(VMD_ALERT_SIGNAL_HANDLE, AlertActivation.PSD),
                buildAlertConditionState(VMD_ALERT_CONDITION_HANDLE, AlertActivation.PSD, false));

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, first);
        messageStorageUtil.addInboundSecureHttpMessage(storage, second);
        messageStorageUtil.addInboundSecureHttpMessage(storage, third);

        testClass.testRequirementC11();
    }

    /**
     * Tests whether seeing one acceptable sequence is sufficient to pass the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementC11GoodMultipleSequences() throws Exception {
        final var initialFirstSequence = buildMdib(SEQUENCE_ID, BigInteger.ZERO);
        final var firstMessageFirstSequence = buildEpisodicMetricReport(SEQUENCE_ID, BigInteger.ONE);
        final var secondMessageFirstSequence = buildEpisodicComponentReport(SEQUENCE_ID, BigInteger.TWO);
        final var thirdMessageFirstSequence = buildEpisodicContextReport(SEQUENCE_ID, BigInteger.valueOf(3));
        final var fourthMessageFirstSequence = buildEpisodicOperationalStateReport(SEQUENCE_ID, BigInteger.valueOf(4));

        final var initialSecondSequence = buildMdib(SEQUENCE_ID2, BigInteger.ZERO);
        final var firstMessageSecondSequence = buildEpisodicAlertReport(
                SEQUENCE_ID2,
                BigInteger.ONE,
                buildAlertConditionState(VMD_ALERT_CONDITION_HANDLE, AlertActivation.PSD, false),
                buildAlertConditionState(MDS_ALERT_CONDITION_HANDLE, AlertActivation.PSD, false),
                buildAlertConditionState(MDS_SECOND_ALERT_CONDITION_HANDLE, AlertActivation.PSD, false));

        messageStorageUtil.addInboundSecureHttpMessage(storage, initialFirstSequence);
        messageStorageUtil.addInboundSecureHttpMessage(storage, firstMessageFirstSequence);
        messageStorageUtil.addInboundSecureHttpMessage(storage, secondMessageFirstSequence);
        messageStorageUtil.addInboundSecureHttpMessage(storage, thirdMessageFirstSequence);
        messageStorageUtil.addInboundSecureHttpMessage(storage, fourthMessageFirstSequence);

        messageStorageUtil.addInboundSecureHttpMessage(storage, initialSecondSequence);
        messageStorageUtil.addInboundSecureHttpMessage(storage, firstMessageSecondSequence);

        testClass.testRequirementC11();
    }

    /**
     * Tests whether EpisodicAlertReports which contain one AbstractAlertState where no child
     * element or attribute has changed fails the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementC11Bad() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID, BigInteger.ZERO);

        final var first = buildEpisodicAlertReport(
                SEQUENCE_ID, BigInteger.ONE, buildAlertSignalState(VMD_ALERT_SIGNAL_HANDLE, AlertActivation.OFF));

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, first);

        assertThrows(AssertionError.class, testClass::testRequirementC11);
    }

    /**
     * Tests whether EpisodicAlertReports with multiple report parts where one part contain only
     * AbstractAlertStates without changed child elements or attributes fails the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementC11BadMultipleReportParts() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID, BigInteger.ZERO);

        final var reportPartGood = buildAbstractAlertReportPart(
                buildAlertConditionState(VMD_ALERT_CONDITION_HANDLE, AlertActivation.PSD, false),
                buildAlertConditionState(MDS_ALERT_CONDITION_HANDLE, AlertActivation.PSD, false),
                buildAlertConditionState(MDS_SECOND_ALERT_CONDITION_HANDLE, AlertActivation.PSD, false));
        final var reportPartBad =
                buildAbstractAlertReportPart(buildAlertSignalState(MDS_ALERT_SIGNAL_HANDLE, AlertActivation.OFF));
        final var first = buildEpisodicAlertReportWithParts(SEQUENCE_ID, BigInteger.ONE, reportPartGood, reportPartBad);

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, first);

        assertThrows(AssertionError.class, testClass::testRequirementC11);
    }

    /**
     * Tests whether calling the test without any input data causes a failure.
     */
    @Test
    public void testRequirementC12NoTestData() {
        assertThrows(NoTestData.class, testClass::testRequirementC12);
    }

    /**
     * Tests whether EpisodicComponentReports which contain only AbstractComponentState with at least one changed child
     * element or attribute passes the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementC12Good() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID, BigInteger.ZERO);

        final var first = buildEpisodicComponentReport(
                SEQUENCE_ID,
                BigInteger.ONE,
                buildBatteryState(BATTERY_HANDLE, ComponentActivation.ON, 101L),
                buildSystemContextState(SYSTEM_CONTEXT_HANDLE, ComponentActivation.STND_BY),
                buildScoState(SCO_HANDLE, ComponentActivation.SHTDN));

        final var second = buildEpisodicComponentReport(
                SEQUENCE_ID,
                BigInteger.TWO,
                buildBatteryState(BATTERY_HANDLE, ComponentActivation.OFF, 101L),
                buildSystemContextState(SYSTEM_CONTEXT_HANDLE, ComponentActivation.ON),
                buildScoState(SCO_HANDLE, ComponentActivation.OFF));

        final var third = buildEpisodicComponentReport(
                SEQUENCE_ID, BigInteger.valueOf(3), buildBatteryState(BATTERY_HANDLE, ComponentActivation.NOT_RDY, 0L));

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, first);
        messageStorageUtil.addInboundSecureHttpMessage(storage, second);
        messageStorageUtil.addInboundSecureHttpMessage(storage, third);

        testClass.testRequirementC12();
    }

    /**
     * Tests whether seeing one acceptable sequence is sufficient to pass the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementC12GoodMultipleSequences() throws Exception {
        final var initialFirstSequence = buildMdib(SEQUENCE_ID, BigInteger.ZERO);
        final var firstMessageFirstSequence = buildEpisodicMetricReport(SEQUENCE_ID, BigInteger.ONE);
        final var secondMessageFirstSequence = buildEpisodicAlertReport(SEQUENCE_ID, BigInteger.TWO);
        final var thirdMessageFirstSequence = buildEpisodicContextReport(SEQUENCE_ID, BigInteger.valueOf(3));
        final var fourthMessageFirstSequence = buildEpisodicOperationalStateReport(SEQUENCE_ID, BigInteger.valueOf(4));

        final var initialSecondSequence = buildMdib(SEQUENCE_ID2, BigInteger.ZERO);
        final var firstMessageSecondSequence = buildEpisodicComponentReport(
                SEQUENCE_ID2,
                BigInteger.ONE,
                buildBatteryState(BATTERY_HANDLE, ComponentActivation.ON, 101L),
                buildSystemContextState(SYSTEM_CONTEXT_HANDLE, ComponentActivation.STND_BY),
                buildScoState(SCO_HANDLE, ComponentActivation.SHTDN));

        messageStorageUtil.addInboundSecureHttpMessage(storage, initialFirstSequence);
        messageStorageUtil.addInboundSecureHttpMessage(storage, firstMessageFirstSequence);
        messageStorageUtil.addInboundSecureHttpMessage(storage, secondMessageFirstSequence);
        messageStorageUtil.addInboundSecureHttpMessage(storage, thirdMessageFirstSequence);
        messageStorageUtil.addInboundSecureHttpMessage(storage, fourthMessageFirstSequence);

        messageStorageUtil.addInboundSecureHttpMessage(storage, initialSecondSequence);
        messageStorageUtil.addInboundSecureHttpMessage(storage, firstMessageSecondSequence);

        testClass.testRequirementC12();
    }

    /**
     * Tests whether EpisodicComponentReports which contain one AbstractComponentState where no child
     * element or attribute has changed fails the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementC12Bad() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID, BigInteger.ZERO);

        final var first = buildEpisodicComponentReport(
                SEQUENCE_ID, BigInteger.ONE, buildBatteryState(BATTERY_HANDLE, ComponentActivation.ON, 100L));

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, first);

        assertThrows(AssertionError.class, testClass::testRequirementC12);
    }

    /**
     * Tests whether EpisodicComponentReports with multiple report parts where one part contain only
     * AbstractComponentStates without changed child elements or attributes fails the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementC12BadMultipleReportParts() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID, BigInteger.ZERO);

        final var reportPartGood = buildAbstractComponentReportPart(
                buildSystemContextState(SYSTEM_CONTEXT_HANDLE, ComponentActivation.STND_BY),
                buildScoState(SCO_HANDLE, ComponentActivation.SHTDN));
        final var reportPartBad =
                buildAbstractComponentReportPart(buildBatteryState(BATTERY_HANDLE, ComponentActivation.ON, 100L));
        final var first =
                buildEpisodicComponentReportWithParts(SEQUENCE_ID, BigInteger.ONE, reportPartBad, reportPartGood);

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, first);

        assertThrows(AssertionError.class, testClass::testRequirementC12);
    }

    /**
     * Tests whether calling the test without any input data causes a failure.
     */
    @Test
    public void testRequirementC13NoTestData() {
        assertThrows(NoTestData.class, testClass::testRequirementC13);
    }

    /**
     * Tests whether EpisodicContextReports which contain only AbstractContextStates with at least one changed child
     * element or attribute passes the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementC13Good() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID, BigInteger.ZERO);

        final var first = buildEpisodicContextReport(
                SEQUENCE_ID,
                BigInteger.ONE,
                buildPatientContextState(
                        PATIENT_CONTEXT_DESCRIPTOR_HANDLE,
                        PATIENT_CONTEXT_STATE_HANDLE,
                        ContextAssociation.ASSOC,
                        mdibBuilder.buildCodedValue("newCodedValue")));

        final var second = buildEpisodicContextReport(
                SEQUENCE_ID,
                BigInteger.TWO,
                buildLocationContextState(
                        LOCATION_CONTEXT_DESCRIPTOR_HANDLE,
                        LOCATION_CONTEXT_STATE_HANDLE,
                        ContextAssociation.DIS,
                        mdibBuilder.buildCodedValue("initial")));

        final var third = buildEpisodicContextReport(
                SEQUENCE_ID,
                BigInteger.valueOf(3),
                buildPatientContextState(
                        PATIENT_CONTEXT_DESCRIPTOR_HANDLE,
                        PATIENT_CONTEXT_STATE_HANDLE,
                        ContextAssociation.ASSOC,
                        mdibBuilder.buildCodedValue("otherValue")),
                buildLocationContextState(
                        LOCATION_CONTEXT_DESCRIPTOR_HANDLE,
                        LOCATION_CONTEXT_STATE_HANDLE,
                        ContextAssociation.PRE,
                        mdibBuilder.buildCodedValue("newValue")));

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, first);
        messageStorageUtil.addInboundSecureHttpMessage(storage, second);
        messageStorageUtil.addInboundSecureHttpMessage(storage, third);

        testClass.testRequirementC13();
    }

    /**
     * Tests whether EpisodicContextReports which contain a new AbstractContextState is not causing the test to fail.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementC13GoodContextStateCreated() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID, BigInteger.ZERO);

        final var first = buildEpisodicContextReport(
                SEQUENCE_ID,
                BigInteger.ONE,
                buildOperatorContextState(
                        OPERATOR_CONTEXT_DESCRIPTOR_HANDLE,
                        NEW_OPERATOR_CONTEXT_STATE_HANDLE,
                        ContextAssociation.ASSOC,
                        mdibBuilder.buildCodedValue("newCodedValue")));

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, first);

        testClass.testRequirementC13();
    }

    /**
     * Tests whether seeing one acceptable sequence is sufficient to pass the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementC13GoodMultipleSequences() throws Exception {
        final var initialFirstSequence = buildMdib(SEQUENCE_ID, BigInteger.ZERO);
        final var firstMessageFirstSequence = buildEpisodicMetricReport(SEQUENCE_ID, BigInteger.ONE);
        final var secondMessageFirstSequence = buildEpisodicComponentReport(SEQUENCE_ID, BigInteger.TWO);
        final var thirdMessageFirstSequence = buildEpisodicAlertReport(SEQUENCE_ID, BigInteger.valueOf(3));
        final var fourthMessageFirstSequence = buildEpisodicOperationalStateReport(SEQUENCE_ID, BigInteger.valueOf(4));

        final var initialSecondSequence = buildMdib(SEQUENCE_ID2, BigInteger.ZERO);
        final var firstMessageSecondSequence = buildEpisodicContextReport(
                SEQUENCE_ID2,
                BigInteger.ONE,
                buildPatientContextState(
                        PATIENT_CONTEXT_DESCRIPTOR_HANDLE,
                        PATIENT_CONTEXT_STATE_HANDLE,
                        ContextAssociation.ASSOC,
                        mdibBuilder.buildCodedValue("newCodedValue")));

        messageStorageUtil.addInboundSecureHttpMessage(storage, initialFirstSequence);
        messageStorageUtil.addInboundSecureHttpMessage(storage, firstMessageFirstSequence);
        messageStorageUtil.addInboundSecureHttpMessage(storage, secondMessageFirstSequence);
        messageStorageUtil.addInboundSecureHttpMessage(storage, thirdMessageFirstSequence);
        messageStorageUtil.addInboundSecureHttpMessage(storage, fourthMessageFirstSequence);

        messageStorageUtil.addInboundSecureHttpMessage(storage, initialSecondSequence);
        messageStorageUtil.addInboundSecureHttpMessage(storage, firstMessageSecondSequence);

        testClass.testRequirementC13();
    }

    /**
     * Tests whether EpisodicContextReports which contain one AbstractContextState where no child
     * element or attribute has changed fails the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementC13Bad() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID, BigInteger.ZERO);

        final var first = buildEpisodicContextReport(
                SEQUENCE_ID,
                BigInteger.ONE,
                buildPatientContextState(
                        PATIENT_CONTEXT_DESCRIPTOR_HANDLE,
                        PATIENT_CONTEXT_STATE_HANDLE,
                        ContextAssociation.ASSOC,
                        mdibBuilder.buildCodedValue("initial")));

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, first);

        assertThrows(AssertionError.class, testClass::testRequirementC13);
    }

    /**
     * Tests whether EpisodicContextReports with multiple report parts where one part contain only
     * AbstractContextStates without changed child elements or attributes fails the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementC13BadMultipleReportParts() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID, BigInteger.ZERO);

        final var reportPartGood = buildAbstractContextReportPart(buildLocationContextState(
                LOCATION_CONTEXT_DESCRIPTOR_HANDLE,
                LOCATION_CONTEXT_STATE_HANDLE,
                ContextAssociation.DIS,
                mdibBuilder.buildCodedValue("initial")));
        final var reportPartBad = buildAbstractContextReportPart(buildPatientContextState(
                PATIENT_CONTEXT_DESCRIPTOR_HANDLE,
                PATIENT_CONTEXT_STATE_HANDLE,
                ContextAssociation.ASSOC,
                mdibBuilder.buildCodedValue("initial")));
        final var first =
                buildEpisodicContextReportWithParts(SEQUENCE_ID, BigInteger.ONE, reportPartGood, reportPartBad);

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, first);

        assertThrows(AssertionError.class, testClass::testRequirementC13);
    }

    /**
     * Tests whether calling the test without any input data causes a failure.
     */
    @Test
    public void testRequirementC14NoTestData() {
        assertThrows(NoTestData.class, testClass::testRequirementC14);
    }

    /**
     * Tests whether EpisodicMetricReports which contain only AbstractMetricStates with at least one changed child
     * element or attribute passes the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementC14Good() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID, BigInteger.ZERO);

        final var first = buildEpisodicMetricReport(
                SEQUENCE_ID,
                BigInteger.ONE,
                buildNumericMetricState(
                        NUMERIC_METRIC_HANDLE,
                        mdibBuilder.buildNumericMetricValue(BigDecimal.TEN),
                        ComponentActivation.NOT_RDY));

        final var second = buildEpisodicMetricReport(
                SEQUENCE_ID,
                BigInteger.TWO,
                buildStringMetricState(
                        STRING_METRIC_HANDLE,
                        mdibBuilder.buildStringMetricValue("otherValue"),
                        ComponentActivation.SHTDN));

        final var third = buildEpisodicMetricReport(
                SEQUENCE_ID,
                BigInteger.valueOf(3),
                buildNumericMetricState(
                        NUMERIC_METRIC_HANDLE,
                        mdibBuilder.buildNumericMetricValue(BigDecimal.valueOf(11L)),
                        ComponentActivation.ON),
                buildStringMetricState(
                        STRING_METRIC_HANDLE,
                        mdibBuilder.buildStringMetricValue("changedValue"),
                        ComponentActivation.ON));

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, first);
        messageStorageUtil.addInboundSecureHttpMessage(storage, second);
        messageStorageUtil.addInboundSecureHttpMessage(storage, third);

        testClass.testRequirementC14();
    }

    /**
     * Tests whether seeing one acceptable sequence is sufficient to pass the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementC14GoodMultipleSequences() throws Exception {
        final var initialFirstSequence = buildMdib(SEQUENCE_ID, BigInteger.ZERO);
        final var firstMessageFirstSequence = buildEpisodicContextReport(SEQUENCE_ID, BigInteger.ONE);
        final var secondMessageFirstSequence = buildEpisodicComponentReport(SEQUENCE_ID, BigInteger.TWO);
        final var thirdMessageFirstSequence = buildEpisodicAlertReport(SEQUENCE_ID, BigInteger.valueOf(3));
        final var fourthMessageFirstSequence = buildEpisodicOperationalStateReport(SEQUENCE_ID, BigInteger.valueOf(4));

        final var initialSecondSequence = buildMdib(SEQUENCE_ID2, BigInteger.ZERO);
        final var firstMessageSecondSequence = buildEpisodicMetricReport(
                SEQUENCE_ID2,
                BigInteger.ONE,
                buildNumericMetricState(
                        NUMERIC_METRIC_HANDLE,
                        mdibBuilder.buildNumericMetricValue(BigDecimal.TEN),
                        ComponentActivation.SHTDN));

        messageStorageUtil.addInboundSecureHttpMessage(storage, initialFirstSequence);
        messageStorageUtil.addInboundSecureHttpMessage(storage, firstMessageFirstSequence);
        messageStorageUtil.addInboundSecureHttpMessage(storage, secondMessageFirstSequence);
        messageStorageUtil.addInboundSecureHttpMessage(storage, thirdMessageFirstSequence);
        messageStorageUtil.addInboundSecureHttpMessage(storage, fourthMessageFirstSequence);

        messageStorageUtil.addInboundSecureHttpMessage(storage, initialSecondSequence);
        messageStorageUtil.addInboundSecureHttpMessage(storage, firstMessageSecondSequence);

        testClass.testRequirementC14();
    }

    /**
     * Tests whether EpisodicMetricReports which contain one AbstractMetricState where no child
     * element or attribute has changed fails the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementC14Bad() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID, BigInteger.ZERO);

        final var first = buildEpisodicMetricReport(
                SEQUENCE_ID,
                BigInteger.ONE,
                buildNumericMetricState(
                        NUMERIC_METRIC_HANDLE,
                        mdibBuilder.buildNumericMetricValue(BigDecimal.ONE),
                        ComponentActivation.OFF));

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, first);

        assertThrows(AssertionError.class, testClass::testRequirementC14);
    }

    /**
     * Tests whether EpisodicMetricReports with multiple report parts where one part contain only
     * AbstractMetricStates without changed child elements or attributes fails the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementC14BadMultipleReportParts() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID, BigInteger.ZERO);

        final var reportPartGood = buildAbstractMetricReportPart(buildStringMetricState(
                STRING_METRIC_HANDLE, mdibBuilder.buildStringMetricValue("otherValue"), ComponentActivation.SHTDN));
        final var reportPartBad = buildAbstractMetricReportPart(buildNumericMetricState(
                NUMERIC_METRIC_HANDLE, mdibBuilder.buildNumericMetricValue(BigDecimal.ONE), ComponentActivation.OFF));
        final var first =
                buildEpisodicMetricReportWithParts(SEQUENCE_ID, BigInteger.ONE, reportPartGood, reportPartBad);

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, first);

        assertThrows(AssertionError.class, testClass::testRequirementC14);
    }

    /**
     * Tests whether calling the test without any input data causes a failure.
     */
    @Test
    public void testRequirementC15NoTestData() {
        assertThrows(NoTestData.class, testClass::testRequirementC15);
    }

    /**
     * Tests whether EpisodicOperationalStateReport which contain only AbstractOperationState with at least one changed
     * child element or attribute passes the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementC15Good() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID, BigInteger.ZERO);

        final var first = buildEpisodicOperationalStateReport(
                SEQUENCE_ID,
                BigInteger.ONE,
                buildSetStringOperationState(SET_STRING_OPERATION_HANDLE, OperatingMode.DIS));

        final var second = buildEpisodicOperationalStateReport(
                SEQUENCE_ID, BigInteger.TWO, buildActivateOperationState(ACTIVATE_OPERATION_HANDLE, OperatingMode.NA));

        final var third = buildEpisodicOperationalStateReport(
                SEQUENCE_ID,
                BigInteger.valueOf(3),
                buildSetStringOperationState(SET_STRING_OPERATION_HANDLE, OperatingMode.EN),
                buildActivateOperationState(ACTIVATE_OPERATION_HANDLE, OperatingMode.EN));

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, first);
        messageStorageUtil.addInboundSecureHttpMessage(storage, second);
        messageStorageUtil.addInboundSecureHttpMessage(storage, third);

        testClass.testRequirementC15();
    }

    /**
     * Tests whether seeing one acceptable sequence is sufficient to pass the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementC15GoodMultipleSequences() throws Exception {
        final var initialFirstSequence = buildMdib(SEQUENCE_ID, BigInteger.ZERO);
        final var firstMessageFirstSequence = buildEpisodicContextReport(SEQUENCE_ID, BigInteger.ONE);
        final var secondMessageFirstSequence = buildEpisodicComponentReport(SEQUENCE_ID, BigInteger.TWO);
        final var thirdMessageFirstSequence = buildEpisodicAlertReport(SEQUENCE_ID, BigInteger.valueOf(3));
        final var fourthMessageFirstSequence = buildEpisodicMetricReport(SEQUENCE_ID, BigInteger.valueOf(4));

        final var initialSecondSequence = buildMdib(SEQUENCE_ID2, BigInteger.ZERO);
        final var firstMessageSecondSequence = buildEpisodicOperationalStateReport(
                SEQUENCE_ID2,
                BigInteger.ONE,
                buildSetStringOperationState(SET_STRING_OPERATION_HANDLE, OperatingMode.DIS));

        messageStorageUtil.addInboundSecureHttpMessage(storage, initialFirstSequence);
        messageStorageUtil.addInboundSecureHttpMessage(storage, firstMessageFirstSequence);
        messageStorageUtil.addInboundSecureHttpMessage(storage, secondMessageFirstSequence);
        messageStorageUtil.addInboundSecureHttpMessage(storage, thirdMessageFirstSequence);
        messageStorageUtil.addInboundSecureHttpMessage(storage, fourthMessageFirstSequence);

        messageStorageUtil.addInboundSecureHttpMessage(storage, initialSecondSequence);
        messageStorageUtil.addInboundSecureHttpMessage(storage, firstMessageSecondSequence);

        testClass.testRequirementC15();
    }

    /**
     * Tests whether EpisodicOperationalStateReport which contain one AbstractOperationState where no child
     * element or attribute has changed fails the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementC15Bad() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID, BigInteger.ZERO);

        final var first = buildEpisodicOperationalStateReport(
                SEQUENCE_ID,
                BigInteger.ONE,
                buildSetStringOperationState(SET_STRING_OPERATION_HANDLE, OperatingMode.EN));

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, first);

        assertThrows(AssertionError.class, testClass::testRequirementC15);
    }

    /**
     * Tests whether EpisodicOperationalStateReport with multiple report parts where one part contain only
     * AbstractOperationStates without changed child elements or attributes fails the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementC15BadMultipleReportParts() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID, BigInteger.ZERO);

        final var reportPartGood = buildAbstractOperationalStateReportPart(
                buildActivateOperationState(ACTIVATE_OPERATION_HANDLE, OperatingMode.NA));
        final var reportPartBad = buildAbstractOperationalStateReportPart(
                buildSetStringOperationState(SET_STRING_OPERATION_HANDLE, OperatingMode.EN));
        final var first = buildEpisodicOperationalStateReportWithParts(
                SEQUENCE_ID, BigInteger.ONE, reportPartGood, reportPartBad);

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, first);

        assertThrows(AssertionError.class, testClass::testRequirementC15);
    }

    private AbstractAlertState buildAlertSystemState(final String handle, final AlertActivation activation) {
        return mdibBuilder.buildAlertSystemState(handle, activation);
    }

    private AbstractAlertState buildAlertConditionState(
            final String handle, final AlertActivation activation, final boolean presence) {
        final var alertConditionState = mdibBuilder.buildAlertConditionState(handle, activation);
        alertConditionState.setPresence(presence);
        return alertConditionState;
    }

    private AbstractAlertState buildAlertSignalState(final String handle, final AlertActivation activation) {
        return mdibBuilder.buildAlertSignalState(handle, activation);
    }

    private AbstractDeviceComponentState buildBatteryState(
            final String handle, final ComponentActivation activation, final long operatingHours) {
        final var batteryState = mdibBuilder.buildBatteryState(handle);
        batteryState.setActivationState(activation);
        batteryState.setOperatingHours(operatingHours);
        return batteryState;
    }

    private AbstractDeviceComponentState buildSystemContextState(
            final String handle, final ComponentActivation activation) {
        final var systemContextState = mdibBuilder.buildSystemContextState(handle);
        systemContextState.setActivationState(activation);
        return systemContextState;
    }

    private AbstractDeviceComponentState buildScoState(final String handle, final ComponentActivation activation) {
        final var scoState = mdibBuilder.buildScoState(handle);
        scoState.setActivationState(activation);
        return scoState;
    }

    private PatientContextState buildPatientContextState(
            final String patientContextHandle,
            final String patientContextStateHandle,
            final ContextAssociation association,
            final CodedValue category) {
        final var patientContextState =
                mdibBuilder.buildPatientContextState(patientContextHandle, patientContextStateHandle);
        patientContextState.setContextAssociation(association);
        patientContextState.setCategory(category);
        return patientContextState;
    }

    private LocationContextState buildLocationContextState(
            final String locationContextHandle,
            final String locationContextStateHandle,
            final ContextAssociation association,
            final CodedValue category) {
        final var locationContextState =
                mdibBuilder.buildLocationContextState(locationContextHandle, locationContextStateHandle);
        locationContextState.setContextAssociation(association);
        locationContextState.setCategory(category);
        return locationContextState;
    }

    private OperatorContextState buildOperatorContextState(
            final String operatorContextHandle,
            final String operatorContextStateHandle,
            final ContextAssociation association,
            final CodedValue category) {
        final var operatorContextState =
                mdibBuilder.buildOperatorContextState(operatorContextHandle, operatorContextStateHandle);
        operatorContextState.setContextAssociation(association);
        operatorContextState.setCategory(category);
        return operatorContextState;
    }

    private AbstractMetricState buildStringMetricState(
            final String metricHandle, final StringMetricValue metricValue, final ComponentActivation activation) {
        final var metricState = mdibBuilder.buildStringMetricState(metricHandle);
        metricState.setActivationState(activation);
        metricState.setMetricValue(metricValue);
        return metricState;
    }

    private AbstractMetricState buildNumericMetricState(
            final String metricHandle, final NumericMetricValue metricValue, final ComponentActivation activation) {
        final var metricState = mdibBuilder.buildNumericMetricState(metricHandle);
        metricState.setActivationState(activation);
        metricState.setMetricValue(metricValue);
        return metricState;
    }

    private AbstractOperationState buildSetStringOperationState(
            final String handle, final OperatingMode operatingMode) {
        return mdibBuilder.buildSetStringOperationState(handle, operatingMode);
    }

    private AbstractOperationState buildActivateOperationState(final String handle, final OperatingMode operatingMode) {
        return mdibBuilder.buildActivateOperationState(handle, operatingMode);
    }

    private Envelope buildMdib(final String sequenceId, final @Nullable BigInteger mdsVersion) {
        final var mdib = mdibBuilder.buildMinimalMdib(sequenceId);

        final var mdState = mdib.getMdState();
        final var mdsDescriptor = mdib.getMdDescription().getMds().get(0);
        mdsDescriptor.setDescriptorVersion(mdsVersion);

        // alerts for c.11
        final var mdsAlertSystem = mdibBuilder.buildAlertSystem(MDS_ALERT_SYSTEM_HANDLE, AlertActivation.ON);

        final var mdsAlertCondition = mdibBuilder.buildAlertCondition(
                MDS_ALERT_CONDITION_HANDLE, AlertConditionKind.OTH, AlertConditionPriority.ME, AlertActivation.ON);
        mdsAlertCondition.getRight().setPresence(false);
        final var mdsAlertSignal = mdibBuilder.buildAlertSignal(
                MDS_ALERT_SIGNAL_HANDLE, AlertSignalManifestation.OTH, false, AlertActivation.OFF);

        final var mdsSecondAlertCondition = mdibBuilder.buildAlertCondition(
                MDS_SECOND_ALERT_CONDITION_HANDLE,
                AlertConditionKind.TEC,
                AlertConditionPriority.HI,
                AlertActivation.ON);
        mdsSecondAlertCondition.getRight().setPresence(false);
        final var mdsSecondAlertSignal = mdibBuilder.buildAlertSignal(
                MDS_SECOND_ALERT_SIGNAL_HANDLE, AlertSignalManifestation.VIS, true, AlertActivation.OFF);

        mdsAlertSystem.getLeft().getAlertCondition().clear();
        mdsAlertSystem
                .getLeft()
                .getAlertCondition()
                .addAll(List.of(mdsAlertCondition.getLeft(), mdsSecondAlertCondition.getLeft()));
        mdsAlertSystem.getLeft().getAlertSignal().clear();
        mdsAlertSystem
                .getLeft()
                .getAlertSignal()
                .addAll(List.of(mdsAlertSignal.getLeft(), mdsSecondAlertSignal.getLeft()));

        final var vmdAlertCondition = mdibBuilder.buildAlertCondition(
                VMD_ALERT_CONDITION_HANDLE, AlertConditionKind.OTH, AlertConditionPriority.ME, AlertActivation.ON);
        vmdAlertCondition.getRight().setPresence(false);
        final var vmdAlertSignal = mdibBuilder.buildAlertSignal(
                VMD_ALERT_SIGNAL_HANDLE, AlertSignalManifestation.OTH, false, AlertActivation.OFF);

        final var vmdAlertSystem = mdibBuilder.buildAlertSystem(VMD_ALERT_SYSTEM_HANDLE, AlertActivation.ON);

        vmdAlertSystem.getLeft().getAlertCondition().clear();
        vmdAlertSystem.getLeft().getAlertCondition().add(vmdAlertCondition.getLeft());
        vmdAlertSystem.getLeft().getAlertSignal().clear();
        vmdAlertSystem.getLeft().getAlertSignal().add(vmdAlertSignal.getLeft());

        final var vmd = mdibBuilder.buildVmd(VMD_HANDLE);
        vmd.getLeft().setAlertSystem(vmdAlertSystem.getLeft());
        mdState.getState()
                .addAll(List.of(
                        mdsAlertSystem.getRight(),
                        mdsAlertCondition.getRight(),
                        mdsAlertSignal.getRight(),
                        mdsSecondAlertCondition.getRight(),
                        mdsSecondAlertSignal.getRight(),
                        vmd.getRight(),
                        vmdAlertSystem.getRight(),
                        vmdAlertCondition.getRight(),
                        vmdAlertSignal.getRight()));

        // component for c.12
        final var systemContext = mdibBuilder.buildSystemContext(SYSTEM_CONTEXT_HANDLE);
        systemContext.getRight().setActivationState(ComponentActivation.ON);

        final var battery = mdibBuilder.buildBattery(BATTERY_HANDLE);
        battery.getRight().setActivationState(ComponentActivation.ON);
        final var initialOperatingHours = 100L;
        battery.getRight().setOperatingHours(initialOperatingHours);

        final var channel = mdibBuilder.buildChannel(CHANNEL_HANDLE);
        channel.getRight().setOperatingHours(0L);
        vmd.getLeft().getChannel().add(channel.getLeft());
        mdState.getState().addAll(List.of(systemContext.getRight(), channel.getRight(), battery.getRight()));

        // contexts for c.13
        final Pair<PatientContextDescriptor, PatientContextState> patientContext;
        patientContext =
                mdibBuilder.buildPatientContext(PATIENT_CONTEXT_DESCRIPTOR_HANDLE, PATIENT_CONTEXT_STATE_HANDLE);
        patientContext.getRight().setContextAssociation(ContextAssociation.ASSOC);
        patientContext.getRight().setCategory(mdibBuilder.buildCodedValue("initial"));
        final Pair<LocationContextDescriptor, LocationContextState> locationContext =
                mdibBuilder.buildLocationContext(LOCATION_CONTEXT_DESCRIPTOR_HANDLE, LOCATION_CONTEXT_STATE_HANDLE);
        locationContext.getRight().setContextAssociation(ContextAssociation.ASSOC);
        locationContext.getRight().setCategory(mdibBuilder.buildCodedValue("initial"));
        final Pair<OperatorContextDescriptor, OperatorContextState> operatorContext =
                mdibBuilder.buildOperatorContext(OPERATOR_CONTEXT_DESCRIPTOR_HANDLE, OPERATOR_CONTEXT_STATE_HANDLE);
        operatorContext.getRight().setContextAssociation(ContextAssociation.ASSOC);
        operatorContext.getRight().setCategory(mdibBuilder.buildCodedValue("initial"));
        mdState.getState()
                .addAll(List.of(patientContext.getRight(), locationContext.getRight(), operatorContext.getRight()));

        systemContext.getLeft().setPatientContext(patientContext.getLeft());
        systemContext.getLeft().setLocationContext(locationContext.getLeft());
        systemContext.getLeft().getOperatorContext().clear();
        systemContext.getLeft().getOperatorContext().add(operatorContext.getLeft());

        // metrics for c.14
        final var metric = mdibBuilder.buildStringMetric(
                STRING_METRIC_HANDLE, MetricCategory.CLC, MetricAvailability.INTR, mdibBuilder.buildCodedValue("abc"));
        metric.getRight().setActivationState(ComponentActivation.OFF);
        metric.getRight().setMetricValue(mdibBuilder.buildStringMetricValue("value"));

        final var numericMetric = mdibBuilder.buildNumericMetric(
                NUMERIC_METRIC_HANDLE,
                MetricCategory.RCMM,
                MetricAvailability.CONT,
                mdibBuilder.buildCodedValue("abc"),
                BigDecimal.ONE);
        numericMetric.getRight().setActivationState(ComponentActivation.OFF);
        numericMetric.getRight().setMetricValue(mdibBuilder.buildNumericMetricValue(BigDecimal.ONE));

        channel.getLeft().getMetric().clear();
        channel.getLeft().getMetric().addAll(List.of(metric.getLeft(), numericMetric.getLeft()));
        mdState.getState().addAll(List.of(metric.getRight(), numericMetric.getRight()));

        // operations for c.15
        final var setStringOperation =
                mdibBuilder.buildSetStringOperation(SET_STRING_OPERATION_HANDLE, "someTarget", OperatingMode.EN);
        final var activateOperation =
                mdibBuilder.buildActivateOperation(ACTIVATE_OPERATION_HANDLE, "someTarget", OperatingMode.EN);
        final var sco = mdibBuilder.buildSco(SCO_HANDLE);
        sco.getLeft().getOperation().clear();
        sco.getLeft().getOperation().addAll(List.of(setStringOperation.getLeft(), activateOperation.getLeft()));
        sco.getRight().setActivationState(ComponentActivation.ON);
        mdState.getState().addAll(List.of(sco.getRight(), setStringOperation.getRight(), activateOperation.getRight()));

        mdsDescriptor.setSystemContext(systemContext.getLeft());
        mdsDescriptor.getVmd().add(vmd.getLeft());
        mdsDescriptor.setSco(sco.getLeft());
        mdsDescriptor.setAlertSystem(mdsAlertSystem.getLeft());
        mdsDescriptor.getBattery().add(battery.getLeft());

        final var getMdibResponse = messageBuilder.buildGetMdibResponse(mdib.getSequenceId());
        getMdibResponse.setMdib(mdib);

        return messageBuilder.createSoapMessageWithBody(
                ActionConstants.getResponseAction(ActionConstants.ACTION_GET_MDIB), getMdibResponse);
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
            final @Nullable BigInteger mdibVersion,
            final AbstractAlertReport.ReportPart... reportParts) {
        final var report = messageBuilder.buildEpisodicAlertReport(sequenceId);
        report.setMdibVersion(mdibVersion);
        for (var reportPart : reportParts) {
            report.getReportPart().add(reportPart);
        }
        return messageBuilder.createSoapMessageWithBody(ActionConstants.ACTION_EPISODIC_ALERT_REPORT, report);
    }

    Envelope buildEpisodicAlertReport(
            final String sequenceId, final @Nullable BigInteger mdibVersion, final AbstractAlertState... alertStates) {
        final var reportPart = messageBuilder.buildAbstractAlertReportReportPart();
        for (var alertState : alertStates) {
            reportPart.getAlertState().add(alertState);
        }

        return buildEpisodicAlertReportWithParts(sequenceId, mdibVersion, reportPart);
    }

    AbstractComponentReport.ReportPart buildAbstractComponentReportPart(
            final AbstractDeviceComponentState... componentStates) {
        final var reportPart = messageBuilder.buildAbstractComponentReportReportPart();
        for (var componentState : componentStates) {
            reportPart.getComponentState().add(componentState);
        }
        return reportPart;
    }

    Envelope buildEpisodicComponentReportWithParts(
            final String sequenceId,
            final @Nullable BigInteger mdibVersion,
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
            final @Nullable BigInteger mdibVersion,
            final AbstractDeviceComponentState... componentStates) {
        final var reportPart = messageBuilder.buildAbstractComponentReportReportPart();
        for (var componentState : componentStates) {
            reportPart.getComponentState().add(componentState);
        }
        return buildEpisodicComponentReportWithParts(sequenceId, mdibVersion, reportPart);
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
            final @Nullable BigInteger mdibVersion,
            final AbstractContextReport.ReportPart... reportParts) {
        final var report = messageBuilder.buildEpisodicContextReport(sequenceId);
        report.setMdibVersion(mdibVersion);
        for (var reportPart : reportParts) {
            report.getReportPart().add(reportPart);
        }
        return messageBuilder.createSoapMessageWithBody(ActionConstants.ACTION_EPISODIC_CONTEXT_REPORT, report);
    }

    Envelope buildEpisodicContextReport(
            final String sequenceId,
            final @Nullable BigInteger mdibVersion,
            final AbstractContextState... contextStates) {
        final var reportPart = messageBuilder.buildAbstractContextReportReportPart();
        for (var contextState : contextStates) {
            reportPart.getContextState().add(contextState);
        }
        return buildEpisodicContextReportWithParts(sequenceId, mdibVersion, reportPart);
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
            final @Nullable BigInteger mdibVersion,
            final AbstractMetricReport.ReportPart... reportParts) {
        final var report = messageBuilder.buildEpisodicMetricReport(sequenceId);
        report.setMdibVersion(mdibVersion);
        for (var reportPart : reportParts) {
            report.getReportPart().add(reportPart);
        }
        return messageBuilder.createSoapMessageWithBody(ActionConstants.ACTION_EPISODIC_METRIC_REPORT, report);
    }

    Envelope buildEpisodicMetricReport(
            final String sequenceId,
            final @Nullable BigInteger mdibVersion,
            final AbstractMetricState... metricStates) {
        final var reportPart = messageBuilder.buildAbstractMetricReportReportPart();
        for (var metricState : metricStates) {
            reportPart.getMetricState().add(metricState);
        }
        return buildEpisodicMetricReportWithParts(sequenceId, mdibVersion, reportPart);
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
            final @Nullable BigInteger mdibVersion,
            final AbstractOperationalStateReport.ReportPart... reportParts) {
        final var report = messageBuilder.buildEpisodicOperationalStateReport(sequenceId);
        report.setMdibVersion(mdibVersion);
        for (var reportPart : reportParts) {
            report.getReportPart().add(reportPart);
        }
        return messageBuilder.createSoapMessageWithBody(
                ActionConstants.ACTION_EPISODIC_OPERATIONAL_STATE_REPORT, report);
    }

    Envelope buildEpisodicOperationalStateReport(
            final String sequenceId,
            final @Nullable BigInteger mdibVersion,
            final AbstractOperationState... operationStates) {
        final var reportPart = messageBuilder.buildAbstractOperationalStateReportReportPart();
        for (var operationState : operationStates) {
            reportPart.getOperationState().add(operationState);
        }
        return buildEpisodicOperationalStateReportWithParts(sequenceId, mdibVersion, reportPart);
    }

    @SafeVarargs
    final DescriptionModificationReport.ReportPart buildDescriptionModificationReportPart(
            final DescriptionModificationType modificationType,
            final Pair<? extends AbstractDescriptor, ? extends AbstractState>... modifications) {
        return buildDescriptionModificationReportPart(modificationType, null, modifications);
    }

    @SafeVarargs
    final DescriptionModificationReport.ReportPart buildDescriptionModificationReportPart(
            final DescriptionModificationType modificationType,
            final @Nullable String parentDescriptor,
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
            final @Nullable BigInteger mdibVersion,
            final DescriptionModificationReport.ReportPart... reportParts) {
        final var report = messageBuilder.buildDescriptionModificationReport(sequenceId, List.of(reportParts));
        report.setMdibVersion(mdibVersion);
        return messageBuilder.createSoapMessageWithBody(ActionConstants.ACTION_DESCRIPTION_MODIFICATION_REPORT, report);
    }
}
