/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.tests.biceps.invariant;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.draeger.medical.biceps.model.message.DescriptionModificationReport;
import com.draeger.medical.biceps.model.message.DescriptionModificationType;
import com.draeger.medical.biceps.model.message.EpisodicContextReport;
import com.draeger.medical.biceps.model.participant.ContextAssociation;
import com.draeger.medical.biceps.model.participant.LocationContextDescriptor;
import com.draeger.medical.biceps.model.participant.LocationContextState;
import com.draeger.medical.biceps.model.participant.MdsDescriptor;
import com.draeger.medical.biceps.model.participant.MdsState;
import com.draeger.medical.biceps.model.participant.OperatorContextDescriptor;
import com.draeger.medical.biceps.model.participant.OperatorContextState;
import com.draeger.medical.biceps.model.participant.PatientContextDescriptor;
import com.draeger.medical.biceps.model.participant.PatientContextState;
import com.draeger.medical.biceps.model.participant.SystemContextDescriptor;
import com.draeger.medical.biceps.model.participant.SystemContextState;
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
import java.io.IOException;
import java.math.BigInteger;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nullable;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.somda.sdc.biceps.provider.preprocessing.HandleDuplicatedException;
import org.somda.sdc.dpws.helper.JaxbMarshalling;
import org.somda.sdc.dpws.soap.SoapMarshalling;
import org.somda.sdc.glue.common.ActionConstants;

/**
 * Unit test for the BICEPS {@linkplain InvariantMultiStateTest}.
 */
public class InvariantMultiStateTestTest {
    private static final String SYSTEM_CONTEXT_HANDLE = "someVmd";
    private static final String PATIENT_CONTEXT_DESCRIPTOR_HANDLE = "someHandle";
    private static final String PATIENT_CONTEXT_STATE_HANDLE = "someContextState";
    private static final String PATIENT_CONTEXT_STATE_HANDLE2 = "someContextState2";
    private static final String LOCATION_CONTEXT_DESCRIPTOR_HANDLE = "someLocationContextDescriptor";
    private static final String LOCATION_CONTEXT_STATE_HANDLE = "someLocationState";
    private static final String LOCATION_CONTEXT_STATE_HANDLE2 = "someLocationState2";

    private static final String OPERATOR_CONTEXT_DESCRIPTOR_HANDLE = "opDescriptor1";
    private static final String OPERATOR_CONTEXT_DESCRIPTOR_HANDLE2 = "opDescriptor2";
    private static final String OPERATOR_CONTEXT_STATE_HANDLE = "someOperatorState";
    private static final String OPERATOR_CONTEXT_STATE_HANDLE2 = "someOperatorState2";
    private static final String OPERATOR_CONTEXT_STATE_HANDLE3 = "someOperatorState3";
    private static final String OPERATOR_CONTEXT_STATE_HANDLE4 = "someOperatorState4";

    private static final String MDS_HANDLE = MdibBuilder.DEFAULT_MDS_HANDLE;
    private static final String SEQUENCE_ID = MdibBuilder.DEFAULT_SEQUENCE_ID;
    private static final String SEQUENCE_ID2 = "123457";

    private static final Duration UNIT_TEST_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);
    private static MessageStorageUtil messageStorageUtil;
    private static MdibBuilder mdibBuilder;
    private static MessageBuilder messageBuilder;
    private MessageStorage storage;
    private InvariantMultiStateTest testClass;
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

        testClass = new InvariantMultiStateTest();
        testClass.setUp();
    }

    @AfterEach
    void tearDown() throws TimeoutException {
        baseMarshalling.stopAsync().awaitTerminated(DEFAULT_TIMEOUT);
        marshalling.stopAsync().awaitTerminated(DEFAULT_TIMEOUT);
        storage.close();
    }

    /**
     * Tests whether no test data fails the test.
     */
    @Test
    public void testRequirementR0097NoTestData() {
        assertThrows(NoTestData.class, testClass::testRequirement0097);
    }

    /**
     * Tests whether disjunctive unique handles for multistates and descriptors passes the test.
     */
    @Test
    public void testRequirementR0097Good() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID, false);

        final var firstUpdate = buildDescriptionModificationReport(
                SEQUENCE_ID,
                BigInteger.ONE,
                buildMds(MDS_HANDLE, BigInteger.ONE),
                buildSystemContext(SYSTEM_CONTEXT_HANDLE, BigInteger.ONE),
                buildPatientContext(PATIENT_CONTEXT_DESCRIPTOR_HANDLE, PATIENT_CONTEXT_STATE_HANDLE, BigInteger.ONE),
                null,
                null);

        final var secondUpdate = buildDescriptionModificationReport(
                SEQUENCE_ID,
                BigInteger.TWO,
                buildMds(MDS_HANDLE, BigInteger.TWO),
                buildSystemContext(SYSTEM_CONTEXT_HANDLE, BigInteger.TWO),
                buildPatientContext(PATIENT_CONTEXT_DESCRIPTOR_HANDLE, PATIENT_CONTEXT_STATE_HANDLE2, BigInteger.TWO),
                null,
                null);

        final var three = BigInteger.valueOf(3);
        final var thirdUpdate = buildDescriptionModificationReport(
                SEQUENCE_ID,
                three,
                buildMds(MDS_HANDLE, three),
                buildSystemContext(SYSTEM_CONTEXT_HANDLE, three),
                null,
                buildLocationContext(
                        LOCATION_CONTEXT_DESCRIPTOR_HANDLE, LOCATION_CONTEXT_STATE_HANDLE2, BigInteger.TWO),
                null);

        final var fourth = BigInteger.valueOf(4);
        final var fourthUpdate = buildDescriptionModificationReport(
                SEQUENCE_ID,
                fourth,
                buildMds(MDS_HANDLE, fourth),
                buildSystemContext(SYSTEM_CONTEXT_HANDLE, fourth),
                null,
                null,
                buildOperatorContext(
                        OPERATOR_CONTEXT_DESCRIPTOR_HANDLE, OPERATOR_CONTEXT_STATE_HANDLE, BigInteger.TWO));

        final var fifth = BigInteger.valueOf(4);
        final var fifthUpdate = buildDescriptionModificationReport(
                SEQUENCE_ID,
                fifth,
                buildMds(MDS_HANDLE, fifth),
                buildSystemContext(SYSTEM_CONTEXT_HANDLE, fifth),
                null,
                null,
                buildOperatorContext(
                        OPERATOR_CONTEXT_DESCRIPTOR_HANDLE2, OPERATOR_CONTEXT_STATE_HANDLE2, BigInteger.TWO));

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, firstUpdate);
        messageStorageUtil.addInboundSecureHttpMessage(storage, secondUpdate);
        messageStorageUtil.addInboundSecureHttpMessage(storage, thirdUpdate);
        messageStorageUtil.addInboundSecureHttpMessage(storage, fourthUpdate);
        messageStorageUtil.addInboundSecureHttpMessage(storage, fifthUpdate);

        testClass.testRequirement0097();
    }

    /**
     * Tests whether having one acceptable sequence is enough to pass the test.
     */
    @Test
    public void testRequirementR0097GoodOneAcceptableSequenceSeen() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID, false);

        final var patientUpdate = buildDescriptionModificationReport(
                SEQUENCE_ID,
                BigInteger.ONE,
                buildMds(MDS_HANDLE, BigInteger.ONE),
                buildSystemContext(SYSTEM_CONTEXT_HANDLE, BigInteger.ONE),
                buildPatientContext(PATIENT_CONTEXT_DESCRIPTOR_HANDLE, PATIENT_CONTEXT_STATE_HANDLE2, BigInteger.ONE),
                null,
                null);

        final var newSequenceInitial = buildMdib(SEQUENCE_ID2, false);

        final var firstUpdate = buildDescriptionModificationReport(
                SEQUENCE_ID2,
                BigInteger.ONE,
                buildMds(MDS_HANDLE, BigInteger.ONE),
                buildSystemContext(SYSTEM_CONTEXT_HANDLE, BigInteger.ONE),
                buildPatientContext(PATIENT_CONTEXT_DESCRIPTOR_HANDLE, PATIENT_CONTEXT_STATE_HANDLE2, BigInteger.ONE),
                null,
                null);

        final var secondUpdate = buildDescriptionModificationReport(
                SEQUENCE_ID2,
                BigInteger.TWO,
                buildMds(MDS_HANDLE, BigInteger.TWO),
                buildSystemContext(SYSTEM_CONTEXT_HANDLE, BigInteger.TWO),
                buildPatientContext(PATIENT_CONTEXT_DESCRIPTOR_HANDLE, PATIENT_CONTEXT_STATE_HANDLE2, BigInteger.TWO),
                null,
                null);

        final var three = BigInteger.valueOf(3);
        final var thirdUpdate = buildDescriptionModificationReport(
                SEQUENCE_ID2,
                three,
                buildMds(MDS_HANDLE, three),
                buildSystemContext(SYSTEM_CONTEXT_HANDLE, three),
                null,
                buildLocationContext(
                        LOCATION_CONTEXT_DESCRIPTOR_HANDLE, LOCATION_CONTEXT_STATE_HANDLE2, BigInteger.TWO),
                null);

        final var fourth = BigInteger.valueOf(4);
        final var fourthUpdate = buildDescriptionModificationReport(
                SEQUENCE_ID2,
                fourth,
                buildMds(MDS_HANDLE, fourth),
                buildSystemContext(SYSTEM_CONTEXT_HANDLE, fourth),
                null,
                null,
                buildOperatorContext(
                        OPERATOR_CONTEXT_DESCRIPTOR_HANDLE, OPERATOR_CONTEXT_STATE_HANDLE, BigInteger.TWO));

        final var fifth = BigInteger.valueOf(5);
        final var fifthUpdate = buildDescriptionModificationReport(
                SEQUENCE_ID2,
                fifth,
                buildMds(MDS_HANDLE, fifth),
                buildSystemContext(SYSTEM_CONTEXT_HANDLE, fifth),
                null,
                null,
                buildOperatorContext(
                        OPERATOR_CONTEXT_DESCRIPTOR_HANDLE, OPERATOR_CONTEXT_STATE_HANDLE2, BigInteger.TWO));

        final var sixth = BigInteger.valueOf(5);
        final var sixthUpdate = buildDescriptionModificationReport(
                SEQUENCE_ID2,
                sixth,
                buildMds(MDS_HANDLE, sixth),
                buildSystemContext(SYSTEM_CONTEXT_HANDLE, sixth),
                null,
                null,
                buildOperatorContext(
                        OPERATOR_CONTEXT_DESCRIPTOR_HANDLE2, OPERATOR_CONTEXT_STATE_HANDLE2 + "a", BigInteger.TWO));

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, patientUpdate);

        messageStorageUtil.addInboundSecureHttpMessage(storage, newSequenceInitial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, firstUpdate);
        messageStorageUtil.addInboundSecureHttpMessage(storage, secondUpdate);
        messageStorageUtil.addInboundSecureHttpMessage(storage, thirdUpdate);
        messageStorageUtil.addInboundSecureHttpMessage(storage, fourthUpdate);
        messageStorageUtil.addInboundSecureHttpMessage(storage, fifthUpdate);
        messageStorageUtil.addInboundSecureHttpMessage(storage, sixthUpdate);

        testClass.testRequirement0097();
    }

    /**
     * Tests whether having no context states at some point is not causing the test to be stuck or fail.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR0097GoodNoContextStates() throws Exception {
        final var initial = buildMdibWithoutContextStates(SEQUENCE_ID);

        final var firstUpdate = buildDescriptionModificationReport(
                SEQUENCE_ID,
                BigInteger.ONE,
                buildMds(MDS_HANDLE, BigInteger.ONE),
                buildSystemContext(SYSTEM_CONTEXT_HANDLE, BigInteger.ONE),
                buildPatientContext(PATIENT_CONTEXT_DESCRIPTOR_HANDLE, PATIENT_CONTEXT_STATE_HANDLE, BigInteger.ONE),
                buildLocationContext(
                        LOCATION_CONTEXT_DESCRIPTOR_HANDLE, LOCATION_CONTEXT_STATE_HANDLE, BigInteger.ONE));

        final var secondUpdate = buildDescriptionModificationReport(
                SEQUENCE_ID,
                BigInteger.TWO,
                buildMds(MDS_HANDLE, BigInteger.TWO),
                buildSystemContext(SYSTEM_CONTEXT_HANDLE, BigInteger.TWO),
                buildPatientContext(PATIENT_CONTEXT_DESCRIPTOR_HANDLE, PATIENT_CONTEXT_STATE_HANDLE2, BigInteger.TWO),
                buildLocationContext(
                        LOCATION_CONTEXT_DESCRIPTOR_HANDLE, LOCATION_CONTEXT_STATE_HANDLE2, BigInteger.TWO));

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, firstUpdate);
        messageStorageUtil.addInboundSecureHttpMessage(storage, secondUpdate);

        assertTimeoutPreemptively(UNIT_TEST_TIMEOUT, testClass::testRequirement0097);
    }

    /**
     * Tests whether having less than two context states associated for one context descriptor fails the test.
     */
    @Test
    public void testRequirementR0097BadNotEnoughDifferentContextStatesSeen() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID, false);

        final var firstUpdate = buildDescriptionModificationReport(
                SEQUENCE_ID,
                BigInteger.ONE,
                buildMds(MDS_HANDLE, BigInteger.ONE),
                buildSystemContext(SYSTEM_CONTEXT_HANDLE, BigInteger.ONE),
                buildPatientContext(PATIENT_CONTEXT_DESCRIPTOR_HANDLE, PATIENT_CONTEXT_STATE_HANDLE2, BigInteger.ONE),
                null,
                null);

        final var secondUpdate = buildDescriptionModificationReport(
                SEQUENCE_ID,
                BigInteger.TWO,
                buildMds(MDS_HANDLE, BigInteger.TWO),
                buildSystemContext(SYSTEM_CONTEXT_HANDLE, BigInteger.TWO),
                null,
                buildLocationContext(
                        LOCATION_CONTEXT_DESCRIPTOR_HANDLE, LOCATION_CONTEXT_STATE_HANDLE2, BigInteger.TWO),
                null);

        final var three = BigInteger.valueOf(3);
        final var thirdUpdate = buildDescriptionModificationReport(
                SEQUENCE_ID,
                three,
                buildMds(MDS_HANDLE, three),
                buildSystemContext(SYSTEM_CONTEXT_HANDLE, three),
                null,
                null,
                buildOperatorContext(
                        OPERATOR_CONTEXT_DESCRIPTOR_HANDLE, OPERATOR_CONTEXT_STATE_HANDLE, BigInteger.TWO));

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, firstUpdate);
        messageStorageUtil.addInboundSecureHttpMessage(storage, secondUpdate);
        messageStorageUtil.addInboundSecureHttpMessage(storage, thirdUpdate);

        assertThrows(NoTestData.class, testClass::testRequirement0097);
    }

    /**
     * Tests whether duplicate handles for multistates and descriptors fails the test.
     */
    @Test
    public void testRequirementR0097BadStart() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID, true);

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);

        final var error = assertThrows(RuntimeException.class, () -> testClass.testRequirement0097());
        assertTrue(error.getCause() instanceof HandleDuplicatedException);
    }

    /**
     * Tests whether modification to create a duplicate handles for multistates and descriptors fails the test.
     */
    @Test
    public void testRequirementR0097BadModification() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID, false);

        final var firstUpdate = buildEpisodicContextReport(
                SEQUENCE_ID,
                BigInteger.ONE,
                LOCATION_CONTEXT_DESCRIPTOR_HANDLE,
                PATIENT_CONTEXT_DESCRIPTOR_HANDLE,
                BigInteger.ONE);

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, firstUpdate);

        assertThrows(AssertionError.class, () -> testClass.testRequirement0097());
    }

    /**
     * Tests whether adding two context states with the same handle to two different descriptors causes the
     * test to fail.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR0097BadDuplicateContextStateHandle() throws Exception {
        final var operator1 = mdibBuilder.buildOperatorContextState(
                OPERATOR_CONTEXT_DESCRIPTOR_HANDLE, OPERATOR_CONTEXT_STATE_HANDLE);
        final var operator2 = mdibBuilder.buildOperatorContextState(
                OPERATOR_CONTEXT_DESCRIPTOR_HANDLE2, OPERATOR_CONTEXT_STATE_HANDLE);

        final var initial = buildMdib(SEQUENCE_ID, false);

        final var firstUpdate = buildEpisodicContextReport(
                SEQUENCE_ID,
                BigInteger.ONE,
                PATIENT_CONTEXT_DESCRIPTOR_HANDLE,
                PATIENT_CONTEXT_STATE_HANDLE,
                BigInteger.ONE);

        final var firstMod =
                (EpisodicContextReport) firstUpdate.getBody().getAny().get(0);
        firstMod.getReportPart().get(0).getContextState().add(operator1);
        firstMod.getReportPart().get(0).getContextState().add(operator2);

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, firstUpdate);

        assertThrows(AssertionError.class, () -> testClass.testRequirement0097());
    }

    /*
     * build mdib with configurable handles
     */
    Envelope buildMdib(final String sequenceId, final boolean broken) {
        final var mdib = mdibBuilder.buildMinimalMdib(sequenceId);

        final var mdState = mdib.getMdState();
        final var mdsDescriptor = mdib.getMdDescription().getMds().get(0);

        final Pair<PatientContextDescriptor, PatientContextState> patientContext;
        if (broken) {
            patientContext = mdibBuilder.buildPatientContext(
                    PATIENT_CONTEXT_DESCRIPTOR_HANDLE, PATIENT_CONTEXT_DESCRIPTOR_HANDLE);
        } else {
            patientContext =
                    mdibBuilder.buildPatientContext(PATIENT_CONTEXT_DESCRIPTOR_HANDLE, PATIENT_CONTEXT_STATE_HANDLE);
        }
        patientContext.getRight().setContextAssociation(ContextAssociation.ASSOC);

        final Pair<LocationContextDescriptor, LocationContextState> locationContext =
                mdibBuilder.buildLocationContext(LOCATION_CONTEXT_DESCRIPTOR_HANDLE, LOCATION_CONTEXT_STATE_HANDLE);
        locationContext.getRight().setContextAssociation(ContextAssociation.ASSOC);

        final var operator1 =
                mdibBuilder.buildOperatorContext(OPERATOR_CONTEXT_DESCRIPTOR_HANDLE, OPERATOR_CONTEXT_STATE_HANDLE3);
        operator1.getRight().setContextAssociation(ContextAssociation.ASSOC);
        final var operator2 =
                mdibBuilder.buildOperatorContext(OPERATOR_CONTEXT_DESCRIPTOR_HANDLE2, OPERATOR_CONTEXT_STATE_HANDLE4);
        operator2.getRight().setContextAssociation(ContextAssociation.ASSOC);

        final var systemContext = mdibBuilder.buildSystemContext(SYSTEM_CONTEXT_HANDLE);
        systemContext.getLeft().setPatientContext(patientContext.getLeft());
        systemContext.getLeft().setLocationContext(locationContext.getLeft());
        systemContext.getLeft().getOperatorContext().clear();
        systemContext.getLeft().getOperatorContext().addAll(List.of(operator1.getLeft(), operator2.getLeft()));

        mdsDescriptor.setSystemContext(systemContext.getLeft());
        mdState.getState()
                .addAll(List.of(
                        systemContext.getRight(),
                        patientContext.getRight(),
                        locationContext.getRight(),
                        operator1.getRight(),
                        operator2.getRight()));

        final var getMdibResponse = messageBuilder.buildGetMdibResponse(mdib.getSequenceId());
        getMdibResponse.setMdib(mdib);

        return messageBuilder.createSoapMessageWithBody(
                ActionConstants.getResponseAction(ActionConstants.ACTION_GET_MDIB), getMdibResponse);
    }

    /*
     * build mdib with configurable handles
     */
    Envelope buildMdibWithoutContextStates(final String sequenceId) {

        final var mdib = mdibBuilder.buildMinimalMdib(sequenceId);

        final var mdState = mdib.getMdState();
        final var mdsDescriptor = mdib.getMdDescription().getMds().get(0);

        final var patientContextDescriptor =
                mdibBuilder.buildPatientContextDescriptor(PATIENT_CONTEXT_DESCRIPTOR_HANDLE);

        final var locationContextDescriptor =
                mdibBuilder.buildLocationContextDescriptor(LOCATION_CONTEXT_DESCRIPTOR_HANDLE);

        final var systemContext = mdibBuilder.buildSystemContext(SYSTEM_CONTEXT_HANDLE);
        systemContext.getLeft().setPatientContext(patientContextDescriptor);
        systemContext.getLeft().setLocationContext(locationContextDescriptor);

        mdsDescriptor.setSystemContext(systemContext.getLeft());
        mdState.getState().addAll(List.of(systemContext.getRight()));

        final var getMdibResponse = messageBuilder.buildGetMdibResponse(mdib.getSequenceId());
        getMdibResponse.setMdib(mdib);

        return messageBuilder.createSoapMessageWithBody(
                ActionConstants.getResponseAction(ActionConstants.ACTION_GET_MDIB), getMdibResponse);
    }

    Envelope buildDescriptionModificationReport(
            final String sequenceId,
            final @Nullable BigInteger mdibVersion,
            final @Nullable Pair<MdsDescriptor, MdsState> mds,
            final @Nullable Pair<SystemContextDescriptor, SystemContextState> systemContext,
            final @Nullable Pair<PatientContextDescriptor, PatientContextState> patientContext,
            final @Nullable Pair<LocationContextDescriptor, LocationContextState> locationContext,
            final @Nullable Pair<OperatorContextDescriptor, OperatorContextState> operatorContext) {
        final var reportPart = messageBuilder.buildDescriptionModificationReportReportPart();
        reportPart.setModificationType(DescriptionModificationType.UPT);

        if (patientContext != null) {
            reportPart.getDescriptor().add(patientContext.getLeft());
            reportPart.getState().add(patientContext.getRight());
        }

        if (locationContext != null) {
            reportPart.getDescriptor().add(locationContext.getLeft());
            reportPart.getState().add(locationContext.getRight());
        }

        if (operatorContext != null) {
            reportPart.getDescriptor().add(operatorContext.getLeft());
            reportPart.getState().add(operatorContext.getRight());
        }

        if (systemContext != null && patientContext != null) {
            systemContext.getLeft().setPatientContext(patientContext.getLeft());
            reportPart.getDescriptor().add(systemContext.getLeft());
            reportPart.getState().add(systemContext.getRight());
        } else if (systemContext != null) {
            reportPart.getDescriptor().add(systemContext.getLeft());
            reportPart.getState().add(systemContext.getRight());
        }

        if (mds != null) {
            reportPart.getDescriptor().add(mds.getLeft());
            reportPart.getState().add(mds.getRight());
        }

        final List<DescriptionModificationReport.ReportPart> parts;
        parts = List.of(reportPart);
        final var report = messageBuilder.buildDescriptionModificationReport(sequenceId, parts);
        report.setMdibVersion(mdibVersion);
        return messageBuilder.createSoapMessageWithBody(ActionConstants.ACTION_DESCRIPTION_MODIFICATION_REPORT, report);
    }

    Envelope buildDescriptionModificationReport(
            final String sequenceId,
            final BigInteger mdibVersion,
            final Pair<MdsDescriptor, MdsState> mds,
            final Pair<SystemContextDescriptor, SystemContextState> systemContext,
            final Pair<PatientContextDescriptor, PatientContextState> patientContext,
            final Pair<LocationContextDescriptor, LocationContextState> locationContext) {
        final var reportPart = messageBuilder.buildDescriptionModificationReportReportPart();
        reportPart.setModificationType(DescriptionModificationType.UPT);

        systemContext.getLeft().setPatientContext(patientContext.getLeft());
        systemContext.getLeft().setLocationContext(locationContext.getLeft());
        mds.getLeft().setSystemContext(systemContext.getLeft());

        reportPart
                .getDescriptor()
                .addAll(List.of(
                        patientContext.getLeft(), locationContext.getLeft(), systemContext.getLeft(), mds.getLeft()));
        reportPart
                .getState()
                .addAll(List.of(
                        systemContext.getRight(),
                        patientContext.getRight(),
                        locationContext.getRight(),
                        mds.getRight()));

        final List<DescriptionModificationReport.ReportPart> parts;
        parts = List.of(reportPart);
        final var report = messageBuilder.buildDescriptionModificationReport(sequenceId, parts);
        report.setMdibVersion(mdibVersion);
        return messageBuilder.createSoapMessageWithBody(ActionConstants.ACTION_DESCRIPTION_MODIFICATION_REPORT, report);
    }

    Envelope buildEpisodicContextReport(
            final String sequenceId,
            final @Nullable BigInteger mdibVersion,
            final String descriptorHandle,
            final String stateHandle,
            final @Nullable BigInteger stateVersion) {
        final var report = messageBuilder.buildEpisodicContextReport(sequenceId);

        final var locationContextState = mdibBuilder.buildLocationContextState(descriptorHandle, stateHandle);
        locationContextState.setStateVersion(stateVersion);

        final var reportPart = messageBuilder.buildAbstractContextReportReportPart();
        reportPart.getContextState().add(locationContextState);

        report.setMdibVersion(mdibVersion);
        report.getReportPart().add(reportPart);
        return messageBuilder.createSoapMessageWithBody(ActionConstants.ACTION_EPISODIC_CONTEXT_REPORT, report);
    }

    Pair<PatientContextDescriptor, PatientContextState> buildPatientContext(
            final String patientContextHandle,
            final String patientContextStateHandle,
            final BigInteger patientContextVersion) {
        final var patientContext = mdibBuilder.buildPatientContext(patientContextHandle, patientContextStateHandle);
        patientContext.getLeft().setDescriptorVersion(patientContextVersion);
        patientContext.getRight().setDescriptorVersion(patientContextVersion);
        patientContext.getRight().setContextAssociation(ContextAssociation.ASSOC);
        return patientContext;
    }

    Pair<LocationContextDescriptor, LocationContextState> buildLocationContext(
            final String locationContextHandle,
            final String locationContextStateHandle,
            final BigInteger locationContextVersion) {
        final var locationContext = mdibBuilder.buildLocationContext(locationContextHandle, locationContextStateHandle);
        locationContext.getLeft().setDescriptorVersion(locationContextVersion);
        locationContext.getRight().setDescriptorVersion(locationContextVersion);
        locationContext.getRight().setContextAssociation(ContextAssociation.ASSOC);
        return locationContext;
    }

    Pair<OperatorContextDescriptor, OperatorContextState> buildOperatorContext(
            final String operatorContextHandle,
            final String operatorContextStateHandle,
            final BigInteger operatorContextVersion) {
        final var operatorContext = mdibBuilder.buildOperatorContext(operatorContextHandle, operatorContextStateHandle);
        operatorContext.getLeft().setDescriptorVersion(operatorContextVersion);
        operatorContext.getRight().setDescriptorVersion(operatorContextVersion);
        operatorContext.getRight().setContextAssociation(ContextAssociation.ASSOC);
        return operatorContext;
    }

    Pair<SystemContextDescriptor, SystemContextState> buildSystemContext(
            final String systemContextHandle, final BigInteger systemContextVersion) {
        final var systemContext = mdibBuilder.buildSystemContext(systemContextHandle);
        systemContext.getLeft().setDescriptorVersion(systemContextVersion);
        systemContext.getRight().setDescriptorVersion(systemContextVersion);
        return systemContext;
    }

    Pair<MdsDescriptor, MdsState> buildMds(final String mdsHandle, final BigInteger mdsVersion) {
        final var mds = mdibBuilder.buildMds(mdsHandle);
        mds.getLeft().setDescriptorVersion(mdsVersion);
        mds.getRight().setDescriptorVersion(mdsVersion);
        return mds;
    }
}
