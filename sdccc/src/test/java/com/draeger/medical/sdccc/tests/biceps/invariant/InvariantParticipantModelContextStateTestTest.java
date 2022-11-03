/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.tests.biceps.invariant;

import com.draeger.medical.biceps.model.participant.ContextAssociation;
import com.draeger.medical.biceps.model.participant.Mdib;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.somda.sdc.dpws.helper.JaxbMarshalling;
import org.somda.sdc.dpws.soap.SoapMarshalling;
import org.somda.sdc.glue.common.ActionConstants;

import javax.annotation.Nullable;
import java.io.IOException;
import java.math.BigInteger;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test for the BICEPS {@linkplain InvariantParticipantModelContextStateTest}.
 */
public class InvariantParticipantModelContextStateTestTest {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);
    private static final String VMD_HANDLE = "someVmd";
    private static final String SYSTEM_CONTEXT_HANDLE1 = "zeSystemContext";
    private static final String SYSTEM_CONTEXT_HANDLE2 = "zeOtherSystemContext";
    private static final String PATIENT_CONTEXT_HANDLE1 = "zePatient";
    private static final String PATIENT_CONTEXT_HANDLE2 = "zeOtherPatient";
    private static final String LOCATION_CONTEXT_HANDLE1 = "zePlaceToBe";
    private static final String LOCATION_CONTEXT_HANDLE2 = "zeOtherPlaceToBe";
    private static final String PATIENT_CONTEXT_STATE_HANDLE1 = "zePatientInfo1";
    private static final String PATIENT_CONTEXT_STATE_HANDLE2 = "zePatientInfo2";
    private static final String PATIENT_CONTEXT_STATE_HANDLE3 = "zePatientInfo3";
    private static final String LOCATION_CONTEXT_STATE_HANDLE1 = "zeCurrentPlaceOfZeWork1";
    private static final String LOCATION_CONTEXT_STATE_HANDLE2 = "zeCurrentPlaceOfZeWork2";
    private static final String LOCATION_CONTEXT_STATE_HANDLE3 = "zeCurrentPlaceOfZeWork3";
    private static final String LOCATION_CONTEXT_STATE_HANDLE4 = "zeCurrentPlaceOfZeWork4";
    private static final String MDS0_HANDLE = MdibBuilder.DEFAULT_MDS_HANDLE;
    private static final String MDS1_HANDLE = "mdsmdsmdsmds";
    private static final String SEQUENCE_ID = MdibBuilder.DEFAULT_SEQUENCE_ID;

    private static MessageStorageUtil messageStorageUtil;
    private static MdibBuilder mdibBuilder;
    private static MessageBuilder messageBuilder;
    private InvariantParticipantModelContextStateTest testClass;
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

        final Injector injector = InjectorUtil.setupInjector(
            new AbstractModule() {
                @Override
                protected void configure() {
                    bind(TestClient.class).toInstance(mockClient);
                }
            }
        );
        InjectorTestBase.setInjector(injector);

        final var riInjector = TestClientUtil.createClientInjector();
        when(mockClient.getInjector()).thenReturn(riInjector);

        baseMarshalling = riInjector.getInstance(JaxbMarshalling.class);
        baseMarshalling.startAsync().awaitRunning(DEFAULT_TIMEOUT);

        marshalling = riInjector.getInstance(SoapMarshalling.class);
        marshalling.startAsync().awaitRunning(DEFAULT_TIMEOUT);

        storage = injector.getInstance(MessageStorage.class);

        testClass = new InvariantParticipantModelContextStateTest();
        testClass.setUp();
    }

    @AfterEach
    void tearDown() throws TimeoutException {
        baseMarshalling.stopAsync().awaitTerminated(DEFAULT_TIMEOUT);
        marshalling.stopAsync().awaitTerminated(DEFAULT_TIMEOUT);
        storage.close();
    }

    /**
     * Tests whether calling the tests without any input data causes a failure.
     */
    @Test
    public void testRequirementR0124NoData() throws IOException, JAXBException {
        // no data
        assertThrows(NoTestData.class, testClass::testRequirementR0124);

        // no patient context
        final var initial = buildMultiMdsMdib(
            SEQUENCE_ID, false, false, false, true, BigInteger.ZERO
        );
        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);

        assertThrows(NoTestData.class, testClass::testRequirementR0124);
    }

    /**
     * Tests whether calling the tests without sufficient input data causes a failure.
     *
     * <p>
     * Sufficient means that there was at least one mdib sequence in which a single patient context descriptor had
     * two or more <em>different</em> states which were associated at some point.
     */
    @Test
    public void testRequirementR0124NoDataSecondSequence() throws IOException, JAXBException, NoTestData {
        // no data
        assertThrows(NoTestData.class, testClass::testRequirementR0124);

        // no patient context
        final var initial = buildMultiMdsMdib(
            SEQUENCE_ID, true, true, false, true, BigInteger.ZERO
        );

        // associate mds0 patient
        final var first = buildEpisodicContextReportPatient(
            SEQUENCE_ID, BigInteger.ONE, MDS0_HANDLE,
            PATIENT_CONTEXT_HANDLE1, PATIENT_CONTEXT_STATE_HANDLE1,
            ContextAssociation.ASSOC, BigInteger.ONE
        );
        // associate mds1 patient
        final var second = buildEpisodicContextReportPatient(
            SEQUENCE_ID, BigInteger.TWO, MDS1_HANDLE,
            PATIENT_CONTEXT_HANDLE2, PATIENT_CONTEXT_STATE_HANDLE2,
            ContextAssociation.ASSOC, BigInteger.ONE
        );
        // disassociate first mds0 patient
        final var third = buildEpisodicContextReportPatient(
            SEQUENCE_ID, BigInteger.valueOf(3), MDS1_HANDLE,
            PATIENT_CONTEXT_HANDLE1, PATIENT_CONTEXT_STATE_HANDLE1,
            ContextAssociation.DIS, BigInteger.TWO
        );
        // associate second mds0 patient
        final var fourth = buildEpisodicContextReportPatient(
            SEQUENCE_ID, BigInteger.valueOf(4), MDS1_HANDLE,
            PATIENT_CONTEXT_HANDLE1, PATIENT_CONTEXT_STATE_HANDLE3,
            ContextAssociation.ASSOC, BigInteger.ONE
        );

        final var initialSecondSequence = buildMultiMdsMdib(
            SEQUENCE_ID + "2", true, true, false, true, BigInteger.ZERO
        );

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, first);
        messageStorageUtil.addInboundSecureHttpMessage(storage, second);
        messageStorageUtil.addInboundSecureHttpMessage(storage, third);
        // throws because only one patient associated
        assertThrows(NoTestData.class, testClass::testRequirementR0124);

        // passes because second patient was present for first descriptor
        messageStorageUtil.addInboundSecureHttpMessage(storage, fourth);
        testClass.testRequirementR0124();

        // still passes because first sequence was acceptable
        messageStorageUtil.addInboundSecureHttpMessage(storage, initialSecondSequence);
        testClass.testRequirementR0124();
    }

    /**
     * Tests whether the test passes when multiple patient context states over more than one mds are associated.
     */
    @Test
    public void testRequirementR0124GoodMultiMds() throws IOException, JAXBException, NoTestData {
        final var initial = buildMultiMdsMdib(
            SEQUENCE_ID, true, true, true, true, BigInteger.ZERO
        );
        // associate mds0 patient
        final var first = buildEpisodicContextReportPatient(
            SEQUENCE_ID, BigInteger.ONE, MDS0_HANDLE,
            PATIENT_CONTEXT_HANDLE1, PATIENT_CONTEXT_STATE_HANDLE1,
            ContextAssociation.ASSOC, BigInteger.ONE
        );
        // associate mds1 patient
        final var second = buildEpisodicContextReportPatient(
            SEQUENCE_ID, BigInteger.TWO, MDS1_HANDLE,
            PATIENT_CONTEXT_HANDLE2, PATIENT_CONTEXT_STATE_HANDLE2,
            ContextAssociation.ASSOC, BigInteger.ONE
        );
        // disassociate mds1 patient
        final var third = buildEpisodicContextReportPatient(
            SEQUENCE_ID, BigInteger.valueOf(3), MDS1_HANDLE,
            PATIENT_CONTEXT_HANDLE2, PATIENT_CONTEXT_STATE_HANDLE2,
            ContextAssociation.DIS, BigInteger.TWO
        );
        // associate another mds1 patient
        final var fourth = buildEpisodicContextReportPatient(
            SEQUENCE_ID, BigInteger.valueOf(4), MDS1_HANDLE,
            PATIENT_CONTEXT_HANDLE2, PATIENT_CONTEXT_STATE_HANDLE3,
            ContextAssociation.ASSOC, BigInteger.ZERO
        );

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, first);
        messageStorageUtil.addInboundSecureHttpMessage(storage, second);
        messageStorageUtil.addInboundSecureHttpMessage(storage, third);
        messageStorageUtil.addInboundSecureHttpMessage(storage, fourth);

        testClass.testRequirementR0124();
    }

    /**
     * Tests whether the test fails when multiple patient context states in a single mds are associated.
     */
    @Test
    public void testRequirementR0124BadDuplicateStateMultiMds() throws IOException, JAXBException {
        final var initial = buildMultiMdsMdib(
            SEQUENCE_ID, true, true, true, true, BigInteger.ZERO
        );
        // associate mds0 patient
        final var first = buildEpisodicContextReportPatient(
            SEQUENCE_ID, BigInteger.ONE, MDS0_HANDLE,
            PATIENT_CONTEXT_HANDLE1, PATIENT_CONTEXT_STATE_HANDLE1,
            ContextAssociation.ASSOC, BigInteger.ONE
        );
        // associate mds1 patient
        final var second = buildEpisodicContextReportPatient(
            SEQUENCE_ID, BigInteger.TWO, MDS1_HANDLE,
            PATIENT_CONTEXT_HANDLE2, PATIENT_CONTEXT_STATE_HANDLE2,
            ContextAssociation.ASSOC, BigInteger.ONE
        );
        // associate another mds1 patient
        final var third = buildEpisodicContextReportPatient(
            SEQUENCE_ID, BigInteger.valueOf(3), MDS1_HANDLE,
            PATIENT_CONTEXT_HANDLE2, PATIENT_CONTEXT_STATE_HANDLE3,
            ContextAssociation.ASSOC, BigInteger.ZERO
        );

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, first);
        messageStorageUtil.addInboundSecureHttpMessage(storage, second);
        messageStorageUtil.addInboundSecureHttpMessage(storage, third);

        final var error = assertThrows(AssertionError.class, testClass::testRequirementR0124);
        assertTrue(error.getMessage().contains(PATIENT_CONTEXT_HANDLE2));
    }

    /**
     * Tests whether calling the tests without any input data causes a failure.
     */
    @Test
    public void testRequirementR0133NoData() throws IOException, JAXBException {
        // no data
        assertThrows(NoTestData.class, testClass::testRequirementR0133);

        // no location context
        final var initial = buildMultiMdsMdib(
            SEQUENCE_ID, false, false, false, true, BigInteger.ZERO
        );
        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);

        assertThrows(NoTestData.class, testClass::testRequirementR0133);
    }

    /**
     * Tests whether calling the tests without sufficient input data causes a failure.
     *
     * <p>
     * Sufficient means that there was at least one mdib sequence in which all location context descriptors had
     * two or more <em>different</em> states which were associated at some point.
     */
    @Test
    public void testRequirementR0133NoDataSecondSequence() throws IOException, JAXBException, NoTestData {
        // no data
        assertThrows(NoTestData.class, testClass::testRequirementR0133);

        // no location context
        final var initial = buildMultiMdsMdib(
            SEQUENCE_ID, false, false, true, true, BigInteger.ZERO
        );

        // associate mds0 location
        final var first = buildEpisodicContextReportLocation(
            SEQUENCE_ID, BigInteger.ONE, MDS0_HANDLE,
            LOCATION_CONTEXT_HANDLE1, LOCATION_CONTEXT_STATE_HANDLE1,
            ContextAssociation.ASSOC, BigInteger.ONE
        );
        // disassociate mds0 location
        final var second = buildEpisodicContextReportLocation(
            SEQUENCE_ID, BigInteger.TWO, MDS0_HANDLE,
            LOCATION_CONTEXT_HANDLE1, LOCATION_CONTEXT_STATE_HANDLE1,
            ContextAssociation.DIS, BigInteger.TWO
        );
        // associate another mds0 location
        final var third = buildEpisodicContextReportLocation(
            SEQUENCE_ID, BigInteger.valueOf(3), MDS0_HANDLE,
            LOCATION_CONTEXT_HANDLE1, LOCATION_CONTEXT_STATE_HANDLE4,
            ContextAssociation.ASSOC, BigInteger.ZERO
        );
        // associate mds1 location
        final var fourth = buildEpisodicContextReportLocation(
            SEQUENCE_ID, BigInteger.valueOf(4), MDS1_HANDLE,
            LOCATION_CONTEXT_HANDLE2, LOCATION_CONTEXT_STATE_HANDLE2,
            ContextAssociation.ASSOC, BigInteger.ONE
        );
        // disassociate mds1 location
        final var fifth = buildEpisodicContextReportLocation(
            SEQUENCE_ID, BigInteger.valueOf(5), MDS1_HANDLE,
            LOCATION_CONTEXT_HANDLE2, LOCATION_CONTEXT_STATE_HANDLE2,
            ContextAssociation.DIS, BigInteger.TWO
        );
        // associate another mds1 location
        final var sixth = buildEpisodicContextReportLocation(
            SEQUENCE_ID, BigInteger.valueOf(6), MDS1_HANDLE,
            LOCATION_CONTEXT_HANDLE2, LOCATION_CONTEXT_STATE_HANDLE3,
            ContextAssociation.ASSOC, BigInteger.ZERO
        );

        final var initialSecondSequence = buildMultiMdsMdib(
            SEQUENCE_ID + "2", false, true, true, true, BigInteger.ZERO
        );

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, first);
        messageStorageUtil.addInboundSecureHttpMessage(storage, second);
        messageStorageUtil.addInboundSecureHttpMessage(storage, third);
        messageStorageUtil.addInboundSecureHttpMessage(storage, fourth);
        messageStorageUtil.addInboundSecureHttpMessage(storage, fifth);
        // throws because only one location associated
        assertThrows(NoTestData.class, testClass::testRequirementR0133);

        // passes because second location was present for first descriptor
        messageStorageUtil.addInboundSecureHttpMessage(storage, sixth);
        testClass.testRequirementR0133();

        // still passes because first sequence was acceptable
        messageStorageUtil.addInboundSecureHttpMessage(storage, initialSecondSequence);
        testClass.testRequirementR0133();
    }

    /**
     * Tests whether the test passes when multiple location context states over more than one mds are associated.
     */
    @Test
    public void testRequirementR0133GoodMultiMds() throws IOException, JAXBException, NoTestData {
        final var initial = buildMultiMdsMdib(
            SEQUENCE_ID, true, true, true, true, BigInteger.ZERO
        );
        // associate mds0 location
        final var first = buildEpisodicContextReportLocation(
            SEQUENCE_ID, BigInteger.ONE, MDS0_HANDLE,
            LOCATION_CONTEXT_HANDLE1, LOCATION_CONTEXT_STATE_HANDLE1,
            ContextAssociation.ASSOC, BigInteger.ONE
        );
        // disassociate mds0 location
        final var second = buildEpisodicContextReportLocation(
            SEQUENCE_ID, BigInteger.TWO, MDS0_HANDLE,
            LOCATION_CONTEXT_HANDLE1, LOCATION_CONTEXT_STATE_HANDLE1,
            ContextAssociation.DIS, BigInteger.TWO
        );
        // associate another mds0 location
        final var third = buildEpisodicContextReportLocation(
            SEQUENCE_ID, BigInteger.valueOf(3), MDS0_HANDLE,
            LOCATION_CONTEXT_HANDLE1, LOCATION_CONTEXT_STATE_HANDLE4,
            ContextAssociation.ASSOC, BigInteger.ZERO
        );
        // associate mds1 location
        final var fourth = buildEpisodicContextReportLocation(
            SEQUENCE_ID, BigInteger.valueOf(4), MDS1_HANDLE,
            LOCATION_CONTEXT_HANDLE2, LOCATION_CONTEXT_STATE_HANDLE2,
            ContextAssociation.ASSOC, BigInteger.ONE
        );
        // disassociate mds1 location
        final var fifth = buildEpisodicContextReportLocation(
            SEQUENCE_ID, BigInteger.valueOf(5), MDS1_HANDLE,
            LOCATION_CONTEXT_HANDLE2, LOCATION_CONTEXT_STATE_HANDLE2,
            ContextAssociation.DIS, BigInteger.TWO
        );
        // associate another mds1 location
        final var sixth = buildEpisodicContextReportLocation(
            SEQUENCE_ID, BigInteger.valueOf(6), MDS1_HANDLE,
            LOCATION_CONTEXT_HANDLE2, LOCATION_CONTEXT_STATE_HANDLE3,
            ContextAssociation.ASSOC, BigInteger.ZERO
        );

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, first);
        messageStorageUtil.addInboundSecureHttpMessage(storage, second);
        messageStorageUtil.addInboundSecureHttpMessage(storage, third);
        messageStorageUtil.addInboundSecureHttpMessage(storage, fourth);
        messageStorageUtil.addInboundSecureHttpMessage(storage, fifth);
        messageStorageUtil.addInboundSecureHttpMessage(storage, sixth);

        testClass.testRequirementR0133();
    }

    /**
     * Tests whether the test fails when multiple location context states in a single mds are associated.
     */
    @Test
    public void testRequirementR0133BadDuplicateStateMultiMds() throws IOException, JAXBException {
        final var initial = buildMultiMdsMdib(
            SEQUENCE_ID, true, true, true, true, BigInteger.ZERO
        );
        // associate mds0 location
        final var first = buildEpisodicContextReportLocation(
            SEQUENCE_ID, BigInteger.ONE, MDS0_HANDLE,
            LOCATION_CONTEXT_HANDLE1, LOCATION_CONTEXT_STATE_HANDLE1,
            ContextAssociation.ASSOC, BigInteger.ONE
        );
        // associate mds1 location
        final var second = buildEpisodicContextReportLocation(
            SEQUENCE_ID, BigInteger.TWO, MDS1_HANDLE,
            LOCATION_CONTEXT_HANDLE2, LOCATION_CONTEXT_STATE_HANDLE2,
            ContextAssociation.ASSOC, BigInteger.ONE
        );
        // associate another mds1 location
        final var third = buildEpisodicContextReportLocation(
            SEQUENCE_ID, BigInteger.valueOf(3), MDS1_HANDLE,
            LOCATION_CONTEXT_HANDLE2, LOCATION_CONTEXT_STATE_HANDLE3,
            ContextAssociation.ASSOC, BigInteger.ZERO
        );

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, first);
        messageStorageUtil.addInboundSecureHttpMessage(storage, second);
        messageStorageUtil.addInboundSecureHttpMessage(storage, third);

        final var error = assertThrows(AssertionError.class, testClass::testRequirementR0133);
        assertTrue(error.getMessage().contains(LOCATION_CONTEXT_HANDLE2));
    }

    Envelope buildMultiMdsMdib(final String sequenceId,
                               final boolean includePatientContext,
                               final boolean includeSecondPatientContext,
                               final boolean includeLocationContext,
                               final boolean includeSecondLocationContext,
                               final @Nullable BigInteger mdsVersion) {
        final var mdib = buildBaseMdib(
            sequenceId, includePatientContext, includeLocationContext, mdsVersion
        );
        final var mdState = mdib.getMdState();

        // add second mds
        final var mds = mdibBuilder.buildMds(MDS1_HANDLE);
        mdib.getMdDescription().getMds().add(mds.getLeft());
        mdState.getState().add(mds.getRight());

        // add second system context
        final var systemContext = mdibBuilder.buildSystemContext(SYSTEM_CONTEXT_HANDLE2);
        mds.getLeft().setSystemContext(systemContext.getLeft());
        mdState.getState().add(systemContext.getRight());

        if (includeSecondPatientContext) {
            final var patientContext = mdibBuilder.buildPatientContext(
                PATIENT_CONTEXT_HANDLE2, PATIENT_CONTEXT_STATE_HANDLE2
            );

            systemContext.getLeft().setPatientContext(patientContext.getLeft());
            mdState.getState().add(patientContext.getRight());
        }

        if (includeSecondLocationContext) {
            final var locationContext = mdibBuilder.buildLocationContext(
                LOCATION_CONTEXT_HANDLE2, LOCATION_CONTEXT_STATE_HANDLE2
            );

            systemContext.getLeft().setLocationContext(locationContext.getLeft());
            mdState.getState().add(locationContext.getRight());
        }

        final var getMdibResponse = messageBuilder.buildGetMdibResponse(mdib.getSequenceId());
        getMdibResponse.setMdib(mdib);

        return messageBuilder.createSoapMessageWithBody(
            ActionConstants.getResponseAction(ActionConstants.ACTION_GET_MDIB),
            getMdibResponse
        );
    }

    Mdib buildBaseMdib(final String sequenceId,
                       final boolean includePatientContext,
                       final boolean includeLocationContext,
                       final @Nullable BigInteger mdsVersion) {
        final var mdib = mdibBuilder.buildMinimalMdib(sequenceId);

        final var mdState = mdib.getMdState();
        final var mdsDescriptor = mdib.getMdDescription().getMds().get(0);
        mdsDescriptor.setDescriptorVersion(mdsVersion);

        final var vmd = mdibBuilder.buildVmd(VMD_HANDLE);
        mdsDescriptor.getVmd().add(vmd.getLeft());
        mdState.getState().add(vmd.getRight());

        final var systemContext = mdibBuilder.buildSystemContext(SYSTEM_CONTEXT_HANDLE1);
        mdsDescriptor.setSystemContext(systemContext.getLeft());
        mdState.getState().add(systemContext.getRight());

        if (includePatientContext) {
            final var patientContext = mdibBuilder.buildPatientContext(
                PATIENT_CONTEXT_HANDLE1, PATIENT_CONTEXT_STATE_HANDLE1
            );

            systemContext.getLeft().setPatientContext(patientContext.getLeft());
            mdState.getState().add(patientContext.getRight());
        }

        if (includeLocationContext) {
            final var locationContext = mdibBuilder.buildLocationContext(
                LOCATION_CONTEXT_HANDLE1, LOCATION_CONTEXT_STATE_HANDLE1
            );

            systemContext.getLeft().setLocationContext(locationContext.getLeft());
            mdState.getState().add(locationContext.getRight());
        }

        return mdib;
    }

    Envelope buildEpisodicContextReportPatient(
        final String sequenceId,
        final @Nullable BigInteger mdibVersion,
        final @Nullable String sourceMds,
        final String patientContextDescriptorHandle,
        final String patientContextStateHandle,
        final ContextAssociation contextAssociation,
        final BigInteger stateVersion) {
        final var report = messageBuilder.buildEpisodicContextReport(sequenceId);

        final var patientContextState = mdibBuilder.buildPatientContextState(
            patientContextDescriptorHandle, patientContextStateHandle
        );
        patientContextState.setStateVersion(stateVersion);
        patientContextState.setContextAssociation(contextAssociation);

        final var reportPart = messageBuilder.buildAbstractContextReportReportPart();
        reportPart.getContextState().add(patientContextState);
        reportPart.setSourceMds(sourceMds);

        report.setMdibVersion(mdibVersion);
        report.getReportPart().add(reportPart);
        return messageBuilder.createSoapMessageWithBody(
            ActionConstants.ACTION_EPISODIC_CONTEXT_REPORT,
            report
        );
    }

    Envelope buildEpisodicContextReportLocation(
        final String sequenceId,
        final @Nullable BigInteger mdibVersion,
        final @Nullable String sourceMds,
        final String locationContextDescriptorHandle,
        final String locationContextStateHandle,
        final ContextAssociation contextAssociation,
        final BigInteger stateVersion) {
        final var report = messageBuilder.buildEpisodicContextReport(sequenceId);

        final var locationContextState = mdibBuilder.buildLocationContextState(
            locationContextDescriptorHandle, locationContextStateHandle
        );
        locationContextState.setStateVersion(stateVersion);
        locationContextState.setContextAssociation(contextAssociation);

        final var reportPart = messageBuilder.buildAbstractContextReportReportPart();
        reportPart.getContextState().add(locationContextState);
        reportPart.setSourceMds(sourceMds);

        report.setMdibVersion(mdibVersion);
        report.getReportPart().add(reportPart);
        return messageBuilder.createSoapMessageWithBody(
            ActionConstants.ACTION_EPISODIC_CONTEXT_REPORT,
            report
        );
    }
}
