/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2023 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.tests.biceps.invariant;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.draeger.medical.biceps.model.message.DescriptionModificationType;
import com.draeger.medical.biceps.model.participant.AbstractDescriptor;
import com.draeger.medical.biceps.model.participant.AbstractState;
import com.draeger.medical.biceps.model.participant.AlertActivation;
import com.draeger.medical.biceps.model.participant.AlertConditionKind;
import com.draeger.medical.biceps.model.participant.AlertConditionPriority;
import com.draeger.medical.biceps.model.participant.AlertSignalManifestation;
import com.draeger.medical.biceps.model.participant.ApprovedJurisdictions;
import com.draeger.medical.biceps.model.participant.ComponentActivation;
import com.draeger.medical.biceps.model.participant.ContextAssociation;
import com.draeger.medical.biceps.model.participant.LocationContextDescriptor;
import com.draeger.medical.biceps.model.participant.LocationContextState;
import com.draeger.medical.biceps.model.participant.MdsState;
import com.draeger.medical.biceps.model.participant.MetricAvailability;
import com.draeger.medical.biceps.model.participant.MetricCategory;
import com.draeger.medical.biceps.model.participant.ObjectFactory;
import com.draeger.medical.biceps.model.participant.OperatingJurisdiction;
import com.draeger.medical.biceps.model.participant.OperatingMode;
import com.draeger.medical.biceps.model.participant.PatientContextDescriptor;
import com.draeger.medical.biceps.model.participant.PatientContextState;
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
import java.util.List;
import java.util.Objects;
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
 * Unit test for the BICEPS {@linkplain InvariantParticipantModelAnnexTest}.
 */
public class InvariantParticipantModelAnnexTestTest {

    private static final String VMD_HANDLE = "someVmd";
    private static final String MDS_ALERT_SYSTEM_HANDLE = "someMdsAlertSystem";
    private static final String MDS_ALERT_CONDITION_HANDLE = "someMdsAlertCondition";
    private static final String MDS_ALERT_SIGNAL_HANDLE = "someMdsAlertSignal";
    private static final String VMD_ALERT_SYSTEM_HANDLE = "someVmdAlertSystem";
    private static final String VMD_ALERT_CONDITION_HANDLE = "someVmdAlertCondition";
    private static final String VMD_ALERT_SIGNAL_HANDLE = "someVmdAlertSignal";
    private static final String BATTERY_HANDLE = "someBattery";
    private static final String SCO_HANDLE = "someSco";
    private static final String SET_STRING_OPERATION_HANDLE = "someSetStringOperation";
    private static final String ACTIVATE_OPERATION_HANDLE = "someActivateOperation";
    private static final String SECOND_SCO_HANDLE = "someOtherSco";
    private static final String SECOND_SET_STRING_OPERATION_HANDLE = "someOtherSetStringOperation";
    private static final String SECOND_ACTIVATE_OPERATION_HANDLE = "someOtherActivateOperation";
    private static final String SYSTEM_CONTEXT_HANDLE = "someSystemContext";
    private static final String PATIENT_CONTEXT_DESCRIPTOR_HANDLE = "somePatientContext";
    private static final String PATIENT_CONTEXT_STATE_HANDLE = "somePatientContextState";
    private static final String LOCATION_CONTEXT_DESCRIPTOR_HANDLE = "someLocationContext";
    private static final String LOCATION_CONTEXT_STATE_HANDLE = "someLocationContextState";
    private static final String CHANNEL_HANDLE = "someChannel";
    private static final String STRING_METRIC_HANDLE = "someStringMetric";
    private static final String NUMERIC_METRIC_HANDLE = "someNumericMetric";
    private static final String SEQUENCE_ID = MdibBuilder.DEFAULT_SEQUENCE_ID;

    private static final String SECOND_MDS_HANDLE = "someOtherMds";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    private static MessageStorageUtil messageStorageUtil;
    private static MdibBuilder mdibBuilder;
    private static MessageBuilder messageBuilder;
    private InvariantParticipantModelAnnexTest testClass;
    private MessageStorage storage;
    private JaxbMarshalling baseMarshalling;
    private SoapMarshalling marshalling;
    private ObjectFactory participantFactory;

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
        participantFactory = injector.getInstance(ObjectFactory.class);

        testClass = new InvariantParticipantModelAnnexTest();
        testClass.setup();
    }

    @AfterEach
    void testDown() throws TimeoutException {
        baseMarshalling.stopAsync().awaitTerminated(DEFAULT_TIMEOUT);
        marshalling.stopAsync().awaitTerminated(DEFAULT_TIMEOUT);
        storage.close();
    }

    /**
     * Tests whether calling the tests without any input data causes the test to fail.
     */
    @Test
    public void testRequirementB6NoTestData() {
        assertThrows(NoTestData.class, testClass::testRequirementB6);
    }

    /**
     * Tests whether the operation target of an AbstractOperationDescriptor referencing only the parent descriptor of
     * the sco or any child descriptor from that parent, passes the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementB6Good() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID);

        final var first = buildDescriptionModificationReport(
                SEQUENCE_ID,
                BigInteger.ONE,
                null,
                mdibBuilder.buildSetStringOperation(SET_STRING_OPERATION_HANDLE, VMD_HANDLE, OperatingMode.EN));

        final var second = buildDescriptionModificationReport(
                SEQUENCE_ID,
                BigInteger.TWO,
                null,
                mdibBuilder.buildActivateOperation(ACTIVATE_OPERATION_HANDLE, CHANNEL_HANDLE, OperatingMode.DIS),
                mdibBuilder.buildSetStringOperation(
                        SET_STRING_OPERATION_HANDLE, VMD_ALERT_CONDITION_HANDLE, OperatingMode.NA));

        final var third = buildDescriptionModificationReport(
                SEQUENCE_ID,
                BigInteger.valueOf(3),
                null,
                mdibBuilder.buildActivateOperation(SECOND_ACTIVATE_OPERATION_HANDLE, BATTERY_HANDLE, OperatingMode.EN),
                mdibBuilder.buildSetStringOperation(
                        SET_STRING_OPERATION_HANDLE, VMD_ALERT_SIGNAL_HANDLE, OperatingMode.NA));

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, first);
        messageStorageUtil.addInboundSecureHttpMessage(storage, second);
        messageStorageUtil.addInboundSecureHttpMessage(storage, third);

        testClass.testRequirementB6();
    }

    /**
     * Tests whether the operation target of an AbstractOperationDescriptor referencing a non existing handle causes
     * the test to fail.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementB6BadTargetUnknownHandle() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID);

        final var first = buildDescriptionModificationReport(
                SEQUENCE_ID,
                BigInteger.ONE,
                null,
                mdibBuilder.buildSetStringOperation(SET_STRING_OPERATION_HANDLE, "someOtherTarget", OperatingMode.EN));

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, first);

        assertThrows(AssertionError.class, testClass::testRequirementB6);
    }

    /**
     * Tests whether the operation target of an AbstractOperationDescriptor referencing a descriptor that is not the
     * parent of the sco or a descriptor of any child of that parent, causes the test to fail.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementB6BadTargetNotPartOfParentOrSubtree() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID);

        final var first = buildDescriptionModificationReport(
                SEQUENCE_ID,
                BigInteger.ONE,
                null,
                mdibBuilder.buildSetStringOperation(
                        SET_STRING_OPERATION_HANDLE, MDS_ALERT_SYSTEM_HANDLE, OperatingMode.EN));

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, first);

        assertThrows(AssertionError.class, testClass::testRequirementB6);
    }

    /**
     * Tests whether calling the tests without any input data causes a failure.
     */
    @Test
    public void testRequirementB284NoTestData() {
        assertThrows(NoTestData.class, testClass::testRequirementB284);
    }

    /**
     * Tests whether operatingJurisdiction is never set when no approvedJurisdiction is present passes the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementB284Good() throws Exception {
        final var initial = buildMdib(MdibBuilder.DEFAULT_SEQUENCE_ID);

        final var firstUpdate = buildDescriptionModificationReport(
                MdibBuilder.DEFAULT_SEQUENCE_ID,
                BigInteger.ONE,
                null,
                buildMdsPair(MdibBuilder.DEFAULT_MDS_HANDLE, BigInteger.ONE, null, null));

        final var approvedJurisdiction = participantFactory.createApprovedJurisdictions();
        final var operatingJurisdiction = participantFactory.createOperatingJurisdiction();
        final var secondUpdate = buildDescriptionModificationReport(
                MdibBuilder.DEFAULT_SEQUENCE_ID,
                BigInteger.TWO,
                null,
                buildMdsPair(
                        MdibBuilder.DEFAULT_MDS_HANDLE, BigInteger.TWO, approvedJurisdiction, operatingJurisdiction));

        final var thirdUpdate = buildDescriptionModificationReport(
                MdibBuilder.DEFAULT_SEQUENCE_ID,
                BigInteger.valueOf(3),
                DescriptionModificationType.CRT,
                buildMdsPair(SECOND_MDS_HANDLE, BigInteger.valueOf(3), null, null));

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, firstUpdate);
        messageStorageUtil.addInboundSecureHttpMessage(storage, secondUpdate);
        messageStorageUtil.addInboundSecureHttpMessage(storage, thirdUpdate);

        testClass.testRequirementB284();
    }

    /**
     * Tests whether operatingJurisdiction is set when no approvedJurisdiction is present fails the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementB284Bad() throws Exception {
        final var initial = buildMdib(MdibBuilder.DEFAULT_SEQUENCE_ID);

        final var operatingJurisdiction = participantFactory.createOperatingJurisdiction();
        final var firstUpdate = buildDescriptionModificationReport(
                MdibBuilder.DEFAULT_SEQUENCE_ID,
                BigInteger.TWO,
                null,
                buildMdsPair(MdibBuilder.DEFAULT_MDS_HANDLE, BigInteger.TWO, null, operatingJurisdiction));

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, firstUpdate);

        assertThrows(AssertionError.class, testClass::testRequirementB284);
    }

    /**
     * Tests whether calling the tests without any input data causes a failure.
     */
    @Test
    public void testRequirementB402NoTestData() {
        assertThrows(NoTestData.class, testClass::testRequirementB402);
    }

    /**
     * Tests whether operatingJurisdiction is never set for any vmdstate when no approvedJurisdiction is present in the
     * corresponding descriptor passes the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementB402Good() throws Exception {
        final var initial = buildMdib(MdibBuilder.DEFAULT_SEQUENCE_ID);

        final var firstUpdate = buildDescriptionModificationReport(
                MdibBuilder.DEFAULT_SEQUENCE_ID, BigInteger.ONE, null, buildVmdPair(VMD_HANDLE, null, null));

        final var approvedJurisdiction = participantFactory.createApprovedJurisdictions();
        final var operatingJurisdiction = participantFactory.createOperatingJurisdiction();
        final var secondUpdate = buildDescriptionModificationReport(
                MdibBuilder.DEFAULT_SEQUENCE_ID,
                BigInteger.TWO,
                null,
                buildVmdPair(VMD_HANDLE, approvedJurisdiction, operatingJurisdiction));

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, firstUpdate);
        messageStorageUtil.addInboundSecureHttpMessage(storage, secondUpdate);

        testClass.testRequirementB402();
    }

    /**
     * Tests whether operatingJurisdiction is set for any vmdstate when no approvedJurisdiction is present in the
     * corresponding descriptor fails the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementB402Bad() throws Exception {
        final var initial = buildMdib(MdibBuilder.DEFAULT_SEQUENCE_ID);

        final var operatingJurisdiction = participantFactory.createOperatingJurisdiction();
        final var firstUpdate = buildDescriptionModificationReport(
                MdibBuilder.DEFAULT_SEQUENCE_ID,
                BigInteger.TWO,
                null,
                buildVmdPair(VMD_HANDLE, null, operatingJurisdiction));

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, firstUpdate);

        assertThrows(AssertionError.class, testClass::testRequirementB402);
    }

    private Pair<AbstractDescriptor, AbstractState> buildMdsPair(
            final String handle,
            final BigInteger version,
            @Nullable final ApprovedJurisdictions approvedJurisdictions,
            @Nullable final OperatingJurisdiction operatingJurisdiction) {
        final var desc = participantFactory.createMdsDescriptor();
        desc.setHandle(handle);
        desc.setDescriptorVersion(version);
        desc.setApprovedJurisdictions(approvedJurisdictions);
        final var state = participantFactory.createMdsState();
        state.setDescriptorHandle(handle);
        state.setDescriptorVersion(version);
        state.setOperatingJurisdiction(operatingJurisdiction);
        return new ImmutablePair<>(desc, state);
    }

    private Pair<AbstractDescriptor, AbstractState> buildVmdPair(
            final String handle,
            @Nullable final ApprovedJurisdictions approvedJurisdictions,
            @Nullable final OperatingJurisdiction operatingJurisdiction) {
        final var desc = participantFactory.createVmdDescriptor();
        desc.setHandle(handle);
        desc.setApprovedJurisdictions(approvedJurisdictions);
        final var state = participantFactory.createVmdState();
        state.setDescriptorHandle(handle);
        state.setOperatingJurisdiction(operatingJurisdiction);
        return new ImmutablePair<>(desc, state);
    }

    private Envelope buildMdib(final String sequenceId) {
        final var mdib = mdibBuilder.buildMinimalMdib(sequenceId);

        final var mdState = mdib.getMdState();
        final var mdsDescriptor = mdib.getMdDescription().getMds().get(0);
        mdsDescriptor.setDescriptorVersion(BigInteger.ZERO);

        final var mdsAlertSystem = mdibBuilder.buildAlertSystem(MDS_ALERT_SYSTEM_HANDLE, AlertActivation.ON);

        final var mdsAlertCondition = mdibBuilder.buildAlertCondition(
                MDS_ALERT_CONDITION_HANDLE, AlertConditionKind.OTH, AlertConditionPriority.ME, AlertActivation.ON);
        mdsAlertCondition.getRight().setPresence(false);
        final var mdsAlertSignal = mdibBuilder.buildAlertSignal(
                MDS_ALERT_SIGNAL_HANDLE, AlertSignalManifestation.OTH, false, AlertActivation.OFF);

        mdsAlertSystem.getLeft().getAlertCondition().clear();
        mdsAlertSystem.getLeft().getAlertCondition().add(mdsAlertCondition.getLeft());
        mdsAlertSystem.getLeft().getAlertSignal().clear();
        mdsAlertSystem.getLeft().getAlertSignal().addAll(List.of(mdsAlertSignal.getLeft()));

        final var vmdAlertCondition = mdibBuilder.buildAlertCondition(
                VMD_ALERT_CONDITION_HANDLE, AlertConditionKind.OTH, AlertConditionPriority.ME, AlertActivation.ON);
        vmdAlertCondition.getRight().setPresence(false);
        final var vmdAlertSignal = mdibBuilder.buildAlertSignal(
                VMD_ALERT_SIGNAL_HANDLE, AlertSignalManifestation.OTH, false, AlertActivation.OFF);

        final var vmdAlertSystem = mdibBuilder.buildAlertSystem(VMD_ALERT_SYSTEM_HANDLE, AlertActivation.ON);

        vmdAlertSystem.getLeft().getAlertCondition().clear();
        vmdAlertSystem.getLeft().getAlertCondition().addAll(List.of(vmdAlertCondition.getLeft()));
        vmdAlertSystem.getLeft().getAlertSignal().clear();
        vmdAlertSystem.getLeft().getAlertSignal().addAll(List.of(vmdAlertSignal.getLeft()));

        final var vmd = mdibBuilder.buildVmd(VMD_HANDLE);
        final var vmdOperatingJurisdiction = participantFactory.createOperatingJurisdiction();
        vmd.getRight().setOperatingJurisdiction(vmdOperatingJurisdiction);
        final var vmdApprovedJurisdictions = participantFactory.createApprovedJurisdictions();
        final var vmdApprovedJurisdiction = participantFactory.createInstanceIdentifier();
        vmdApprovedJurisdictions.getApprovedJurisdiction().clear();
        vmdApprovedJurisdictions.getApprovedJurisdiction().addAll(List.of(vmdApprovedJurisdiction));
        vmd.getLeft().setApprovedJurisdictions(vmdApprovedJurisdictions);
        vmd.getLeft().setAlertSystem(vmdAlertSystem.getLeft());
        mdState.getState()
                .addAll(List.of(
                        mdsAlertSystem.getRight(),
                        mdsAlertCondition.getRight(),
                        mdsAlertSignal.getRight(),
                        vmd.getRight(),
                        vmdAlertSystem.getRight(),
                        vmdAlertCondition.getRight(),
                        vmdAlertSignal.getRight()));

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

        final Pair<PatientContextDescriptor, PatientContextState> patientContext;
        patientContext =
                mdibBuilder.buildPatientContext(PATIENT_CONTEXT_DESCRIPTOR_HANDLE, PATIENT_CONTEXT_STATE_HANDLE);
        patientContext.getRight().setContextAssociation(ContextAssociation.ASSOC);
        patientContext.getRight().setCategory(mdibBuilder.buildCodedValue("initial"));
        final Pair<LocationContextDescriptor, LocationContextState> locationContext =
                mdibBuilder.buildLocationContext(LOCATION_CONTEXT_DESCRIPTOR_HANDLE, LOCATION_CONTEXT_STATE_HANDLE);
        locationContext.getRight().setContextAssociation(ContextAssociation.ASSOC);
        locationContext.getRight().setCategory(mdibBuilder.buildCodedValue("initial"));
        mdState.getState().addAll(List.of(patientContext.getRight(), locationContext.getRight()));

        systemContext.getLeft().setPatientContext(patientContext.getLeft());
        systemContext.getLeft().setLocationContext(locationContext.getLeft());

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

        final var setStringOperation = mdibBuilder.buildSetStringOperation(
                SET_STRING_OPERATION_HANDLE, VMD_ALERT_SIGNAL_HANDLE, OperatingMode.EN);
        final var activateOperation =
                mdibBuilder.buildActivateOperation(ACTIVATE_OPERATION_HANDLE, VMD_HANDLE, OperatingMode.EN);
        final var sco = mdibBuilder.buildSco(SCO_HANDLE);
        sco.getLeft().getOperation().clear();
        sco.getLeft().getOperation().addAll(List.of(setStringOperation.getLeft(), activateOperation.getLeft()));
        sco.getRight().setActivationState(ComponentActivation.ON);
        mdState.getState().addAll(List.of(sco.getRight(), setStringOperation.getRight(), activateOperation.getRight()));
        vmd.getLeft().setSco(sco.getLeft());

        final var setStringOperation2 = mdibBuilder.buildSetStringOperation(
                SECOND_SET_STRING_OPERATION_HANDLE, MDS_ALERT_SIGNAL_HANDLE, OperatingMode.DIS);
        final var activateOperation2 = mdibBuilder.buildActivateOperation(
                SECOND_ACTIVATE_OPERATION_HANDLE, MdibBuilder.DEFAULT_MDS_HANDLE, OperatingMode.DIS);
        final var sco2 = mdibBuilder.buildSco(SECOND_SCO_HANDLE);
        sco2.getLeft().getOperation().clear();
        sco2.getLeft().getOperation().addAll(List.of(setStringOperation2.getLeft(), activateOperation2.getLeft()));
        sco2.getRight().setActivationState(ComponentActivation.ON);
        mdState.getState()
                .addAll(List.of(sco2.getRight(), setStringOperation2.getRight(), activateOperation2.getRight()));
        mdsDescriptor.setSco(sco2.getLeft());

        mdsDescriptor.setSystemContext(systemContext.getLeft());
        mdsDescriptor.getVmd().add(vmd.getLeft());
        mdsDescriptor.setAlertSystem(mdsAlertSystem.getLeft());
        mdsDescriptor.getBattery().add(battery.getLeft());
        final var mdsStateOpt = mdState.getState().stream()
                .filter(state -> state.getDescriptorHandle().equals(mdsDescriptor.getHandle()))
                .findFirst();
        final var mdsState = (MdsState) mdsStateOpt.orElseThrow();

        final var operatingJurisdiction = participantFactory.createOperatingJurisdiction();
        mdsState.setOperatingJurisdiction(operatingJurisdiction);
        final var approvedJurisdictions = participantFactory.createApprovedJurisdictions();
        final var approvedJurisdiction = participantFactory.createInstanceIdentifier();
        approvedJurisdictions.getApprovedJurisdiction().clear();
        approvedJurisdictions.getApprovedJurisdiction().add(approvedJurisdiction);
        mdsDescriptor.setApprovedJurisdictions(approvedJurisdictions);

        final var getMdibResponse = messageBuilder.buildGetMdibResponse(mdib.getSequenceId());
        getMdibResponse.setMdib(mdib);

        return messageBuilder.createSoapMessageWithBody(
                ActionConstants.getResponseAction(ActionConstants.ACTION_GET_MDIB), getMdibResponse);
    }

    @SafeVarargs
    final Envelope buildDescriptionModificationReport(
            final String sequenceId,
            final @Nullable BigInteger mdibVersion,
            final @Nullable DescriptionModificationType modificationType,
            final Pair<? extends AbstractDescriptor, ? extends AbstractState>... modifications) {
        final var reportPart = messageBuilder.buildDescriptionModificationReportReportPart();
        reportPart.setModificationType(Objects.requireNonNullElse(modificationType, DescriptionModificationType.UPT));

        for (var modification : modifications) {
            reportPart.getDescriptor().add(modification.getLeft());
            reportPart.getState().add(modification.getRight());
        }

        final var report = messageBuilder.buildDescriptionModificationReport(sequenceId, List.of(reportPart));
        report.setMdibVersion(mdibVersion);
        return messageBuilder.createSoapMessageWithBody(ActionConstants.ACTION_DESCRIPTION_MODIFICATION_REPORT, report);
    }
}
