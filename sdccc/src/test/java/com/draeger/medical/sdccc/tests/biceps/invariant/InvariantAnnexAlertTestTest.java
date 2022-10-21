/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.tests.biceps.invariant;

import com.draeger.medical.biceps.model.participant.AbstractAlertState;
import com.draeger.medical.biceps.model.participant.AlertActivation;
import com.draeger.medical.biceps.model.participant.AlertSignalDescriptor;
import com.draeger.medical.biceps.model.participant.AlertSignalManifestation;
import com.draeger.medical.biceps.model.participant.AlertSignalPrimaryLocation;
import com.draeger.medical.biceps.model.participant.AlertSignalState;
import com.draeger.medical.biceps.model.participant.ObjectFactory;
import com.draeger.medical.biceps.model.participant.SystemSignalActivation;
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
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.somda.sdc.dpws.helper.JaxbMarshalling;
import org.somda.sdc.dpws.soap.SoapMarshalling;
import org.somda.sdc.glue.common.ActionConstants;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test for the BICEPS {@linkplain InvariantAnnexAlertTest}.
 */
public class InvariantAnnexAlertTestTest {
    private static final String VMD_HANDLE = "someVmd";
    private static final String MDS_ALERT_SYSTEM_HANDLE = "someMdsAlertSystem";
    private static final String MDS_ALERT_SIGNAL_HANDLE = "mdsAlertSignalHandle";
    private static final String MDS_ALERT_SIGNAL_HANDLE2 = "mdsAlertSignalHandle2";
    private static final String MDS_ALERT_SIGNAL_HANDLE3 = "mdsAlertSignalHandle3";
    private static final String VMD_ALERT_SYSTEM_HANDLE = "someVmdAlertSystem";
    private static final String VMD_ALERT_SIGNAL_HANDLE = "vmdAlertSignalHandle";
    private static final String VMD_ALERT_SIGNAL_HANDLE2 = "vmdAlertSignalHandle2";
    private static final String VMD_ALERT_SIGNAL_HANDLE3 = "vmdAlertSignalHandle3";
    private static final String VMD_ALERT_SIGNAL_HANDLE4 = "vmdAlertSignalHandle4";
    private static final String VMD_ALERT_SIGNAL_HANDLE5 = "vmdAlertSignalHandle5";
    private static final String VMD_ALERT_SIGNAL_HANDLE_REM = "vmdAlertSignalHandleRem";
    private static final String SEQUENCE_ID = MdibBuilder.DEFAULT_SEQUENCE_ID;
    private static final String SEQUENCE_ID2 = "123457";

    private static MessageStorageUtil messageStorageUtil;
    private static MdibBuilder mdibBuilder;
    private static ObjectFactory participantModelFactory;
    private static MessageBuilder messageBuilder;
    private static final java.time.Duration DEFAULT_TIMEOUT = java.time.Duration.ofSeconds(10);

    private MessageStorage storage;
    private InvariantAnnexAlertTest testClass;
    private JaxbMarshalling baseMarshalling;
    private SoapMarshalling marshalling;

    @BeforeAll
    static void setupMarshalling() {
        final Injector marshallingInjector = MarshallingUtil.createMarshallingTestInjector(true);
        messageStorageUtil = marshallingInjector.getInstance(MessageStorageUtil.class);
        mdibBuilder = marshallingInjector.getInstance(MdibBuilder.class);
        participantModelFactory = marshallingInjector.getInstance(ObjectFactory.class);
        messageBuilder = marshallingInjector.getInstance(MessageBuilder.class);
    }

    @BeforeEach
    void setup() throws Exception {
        final TestClient mockClient = mock(TestClient.class);
        when(mockClient.isClientRunning()).thenReturn(true);

        final Injector injector;
        injector = InjectorUtil.setupInjector(
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

        testClass = new InvariantAnnexAlertTest();
        testClass.setUp();
    }

    @AfterEach
    void tearDown() throws TimeoutException {
        baseMarshalling.stopAsync().awaitTerminated(DEFAULT_TIMEOUT);
        marshalling.stopAsync().awaitTerminated(DEFAULT_TIMEOUT);
        storage.close();
    }

    /**
     * Tests whether calling the tests without any input data causes the test to fail.
     */
    @Test
    public void testRequirementB128NoTestData() {
        assertThrows(NoTestData.class, testClass::testRequirementB128);
    }

    /**
     * Tests whether setting the alert activation states of alert signals according to the alert activation states of
     * the system signal activations of the alert system passes the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementB128Good() throws Exception {
        final var initialSystemSignalActivations = List.of(
            createSystemSignalActivation(AlertSignalManifestation.AUD, AlertActivation.OFF),
            createSystemSignalActivation(AlertSignalManifestation.VIS, AlertActivation.OFF),
            createSystemSignalActivation(AlertSignalManifestation.TAN, AlertActivation.OFF),
            createSystemSignalActivation(AlertSignalManifestation.OTH, AlertActivation.OFF));

        final var alertSignals = List.of(
            buildAlertSignal(VMD_ALERT_SIGNAL_HANDLE, AlertSignalManifestation.AUD, AlertActivation.OFF,
                AlertSignalPrimaryLocation.LOC),
            buildAlertSignal(VMD_ALERT_SIGNAL_HANDLE2, AlertSignalManifestation.VIS, AlertActivation.OFF,
                AlertSignalPrimaryLocation.LOC),
            buildAlertSignal(VMD_ALERT_SIGNAL_HANDLE3, AlertSignalManifestation.TAN, AlertActivation.OFF,
                AlertSignalPrimaryLocation.LOC),
            buildAlertSignal(VMD_ALERT_SIGNAL_HANDLE4, AlertSignalManifestation.OTH, AlertActivation.OFF,
                AlertSignalPrimaryLocation.LOC),
            buildAlertSignal(VMD_ALERT_SIGNAL_HANDLE5, AlertSignalManifestation.VIS, AlertActivation.OFF,
                AlertSignalPrimaryLocation.LOC),
            buildAlertSignal(VMD_ALERT_SIGNAL_HANDLE_REM, AlertSignalManifestation.AUD, AlertActivation.OFF,
                AlertSignalPrimaryLocation.REM));

        final var initial = buildMdib(SEQUENCE_ID, BigInteger.ZERO, BigInteger.ZERO, true,
            initialSystemSignalActivations, alertSignals);

        // set alert activation for manifestation AUD to PSD
        final var firstSystemSignalActivations = List.of(
            createSystemSignalActivation(AlertSignalManifestation.AUD, AlertActivation.PSD),
            createSystemSignalActivation(AlertSignalManifestation.VIS, AlertActivation.OFF),
            createSystemSignalActivation(AlertSignalManifestation.TAN, AlertActivation.OFF),
            createSystemSignalActivation(AlertSignalManifestation.OTH, AlertActivation.OFF));
        // set AUD alert signals to PSD
        final var firstUpdate = buildEpisodicAlertReport(SEQUENCE_ID, BigInteger.ONE,
            buildAlertSystemState(VMD_ALERT_SYSTEM_HANDLE, AlertActivation.ON, firstSystemSignalActivations),
            buildAlertSignalState(VMD_ALERT_SIGNAL_HANDLE, AlertActivation.PSD, AlertSignalPrimaryLocation.LOC));

        // set alert activation for manifestation VIS to ON
        final var secondSystemSignalActivations = List.of(
            createSystemSignalActivation(AlertSignalManifestation.AUD, AlertActivation.PSD),
            createSystemSignalActivation(AlertSignalManifestation.VIS, AlertActivation.ON),
            createSystemSignalActivation(AlertSignalManifestation.TAN, AlertActivation.OFF),
            createSystemSignalActivation(AlertSignalManifestation.OTH, AlertActivation.OFF));
        // set both VIS alert signals to PSD or OFF
        final var secondUpdate = buildEpisodicAlertReport(SEQUENCE_ID, BigInteger.TWO,
            buildAlertSystemState(VMD_ALERT_SYSTEM_HANDLE, AlertActivation.ON, secondSystemSignalActivations),
            buildAlertSignalState(VMD_ALERT_SIGNAL_HANDLE2, AlertActivation.PSD, AlertSignalPrimaryLocation.LOC),
            buildAlertSignalState(VMD_ALERT_SIGNAL_HANDLE5, AlertActivation.OFF, AlertSignalPrimaryLocation.LOC));

        // set alert activation for manifestation AUD to OFF and for OTH to PSD
        final var thirdSystemSignalActivations = List.of(
            createSystemSignalActivation(AlertSignalManifestation.AUD, AlertActivation.OFF),
            createSystemSignalActivation(AlertSignalManifestation.VIS, AlertActivation.ON),
            createSystemSignalActivation(AlertSignalManifestation.TAN, AlertActivation.OFF),
            createSystemSignalActivation(AlertSignalManifestation.OTH, AlertActivation.PSD));
        // set AUD alert signals to OFF and OTH alert signals to PSD
        final var thirdUpdate = buildEpisodicAlertReport(SEQUENCE_ID, BigInteger.valueOf(3),
            buildAlertSystemState(VMD_ALERT_SYSTEM_HANDLE, AlertActivation.ON, thirdSystemSignalActivations),
            buildAlertSignalState(VMD_ALERT_SIGNAL_HANDLE, AlertActivation.OFF, AlertSignalPrimaryLocation.LOC),
            buildAlertSignalState(VMD_ALERT_SIGNAL_HANDLE4, AlertActivation.PSD, AlertSignalPrimaryLocation.LOC));

        // set alert activation for AUD to PSD, VIS to PSD, TAN to PSD and OTH to OFF
        final var fourthSystemSignalActivations = List.of(
            createSystemSignalActivation(AlertSignalManifestation.AUD, AlertActivation.PSD),
            createSystemSignalActivation(AlertSignalManifestation.VIS, AlertActivation.PSD),
            createSystemSignalActivation(AlertSignalManifestation.TAN, AlertActivation.PSD),
            createSystemSignalActivation(AlertSignalManifestation.OTH, AlertActivation.OFF));
        // set alert signals with manifestation AUD to PSD, VIS to OFF or PSD, TAN to PSD and OTH to OFF
        final var fourthUpdate = buildEpisodicAlertReport(SEQUENCE_ID, BigInteger.valueOf(4),
            buildAlertSystemState(VMD_ALERT_SYSTEM_HANDLE, AlertActivation.ON, fourthSystemSignalActivations),
            buildAlertSignalState(VMD_ALERT_SIGNAL_HANDLE, AlertActivation.PSD, AlertSignalPrimaryLocation.LOC),
            buildAlertSignalState(VMD_ALERT_SIGNAL_HANDLE2, AlertActivation.OFF, AlertSignalPrimaryLocation.LOC),
            buildAlertSignalState(VMD_ALERT_SIGNAL_HANDLE3, AlertActivation.PSD, AlertSignalPrimaryLocation.LOC),
            buildAlertSignalState(VMD_ALERT_SIGNAL_HANDLE4, AlertActivation.OFF, AlertSignalPrimaryLocation.LOC),
            buildAlertSignalState(VMD_ALERT_SIGNAL_HANDLE5, AlertActivation.PSD, AlertSignalPrimaryLocation.LOC));

        // set alert activation for AUD to ON for the system signal activations of the mds alert system
        final var fifthSystemSignalActivations = List.of(
            createSystemSignalActivation(AlertSignalManifestation.AUD, AlertActivation.ON));
        // set alert signals with manifestation AUD to ON
        final var fifthUpdate = buildEpisodicAlertReport(SEQUENCE_ID, BigInteger.valueOf(5),
            buildAlertSystemState(MDS_ALERT_SYSTEM_HANDLE, AlertActivation.ON, fifthSystemSignalActivations),
            buildAlertSignalState(MDS_ALERT_SIGNAL_HANDLE3, AlertActivation.ON, AlertSignalPrimaryLocation.LOC));

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, firstUpdate);
        messageStorageUtil.addInboundSecureHttpMessage(storage, secondUpdate);
        messageStorageUtil.addInboundSecureHttpMessage(storage, thirdUpdate);
        messageStorageUtil.addInboundSecureHttpMessage(storage, fourthUpdate);
        messageStorageUtil.addInboundSecureHttpMessage(storage, fifthUpdate);

        testClass.testRequirementB128();
    }

    /**
     * Tests whether setting the alert activation states of alert signals, with implied values for location,
     * according to the alert activation states of the system signal activations of the alert system passes the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementB128GoodImpliedValue() throws Exception {
        final var initialSystemSignalActivations = List.of(
            createSystemSignalActivation(AlertSignalManifestation.AUD, AlertActivation.OFF),
            createSystemSignalActivation(AlertSignalManifestation.VIS, AlertActivation.OFF),
            createSystemSignalActivation(AlertSignalManifestation.TAN, AlertActivation.OFF),
            createSystemSignalActivation(AlertSignalManifestation.OTH, AlertActivation.OFF));

        final var alertSignals = List.of(
            mdibBuilder.buildAlertSignal(
                VMD_ALERT_SIGNAL_HANDLE, AlertSignalManifestation.AUD, false, AlertActivation.OFF),
            buildAlertSignal(VMD_ALERT_SIGNAL_HANDLE2, AlertSignalManifestation.VIS, AlertActivation.OFF,
                AlertSignalPrimaryLocation.LOC),
            buildAlertSignal(VMD_ALERT_SIGNAL_HANDLE_REM, AlertSignalManifestation.AUD, AlertActivation.OFF,
                AlertSignalPrimaryLocation.REM));

        final var initial = buildMdib(SEQUENCE_ID, BigInteger.ZERO, BigInteger.ZERO, true,
            initialSystemSignalActivations, alertSignals);

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);

        testClass.testRequirementB128();
    }

    /**
     * Tests whether seeing one acceptable sequence is enough to pass the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementB128GoodMultipleSequences() throws Exception {

        final var initialSystemSignalActivationsFirstSequence = List.of(
            createSystemSignalActivation(AlertSignalManifestation.AUD, AlertActivation.OFF));

        final var alertSignalsFirstSequence = List.of(
            buildAlertSignal(VMD_ALERT_SIGNAL_HANDLE, AlertSignalManifestation.AUD, AlertActivation.OFF,
                AlertSignalPrimaryLocation.REM));

        final var initialUnacceptableSequence = buildMdib(SEQUENCE_ID2, BigInteger.ZERO, BigInteger.ZERO, false,
            initialSystemSignalActivationsFirstSequence, alertSignalsFirstSequence);

        // set alert activation for manifestation AUD to PSD
        final var firstSystemSignalActivationsUnacceptableSequence = List.of(
            createSystemSignalActivation(AlertSignalManifestation.AUD, AlertActivation.PSD));
        // set AUD alert signals to PSD
        final var firstUpdateUnacceptableSequence = buildEpisodicAlertReport(SEQUENCE_ID2, BigInteger.ONE,
            buildAlertSystemState(VMD_ALERT_SYSTEM_HANDLE, AlertActivation.ON,
                firstSystemSignalActivationsUnacceptableSequence),
            buildAlertSignalState(VMD_ALERT_SIGNAL_HANDLE, AlertActivation.PSD, AlertSignalPrimaryLocation.REM));

        final var initialSystemSignalActivations = List.of(
            createSystemSignalActivation(AlertSignalManifestation.AUD, AlertActivation.OFF),
            createSystemSignalActivation(AlertSignalManifestation.VIS, AlertActivation.OFF));

        final var alertSignals = List.of(
            buildAlertSignal(VMD_ALERT_SIGNAL_HANDLE, AlertSignalManifestation.AUD, AlertActivation.OFF,
                AlertSignalPrimaryLocation.LOC),
            buildAlertSignal(VMD_ALERT_SIGNAL_HANDLE2, AlertSignalManifestation.VIS, AlertActivation.OFF,
                AlertSignalPrimaryLocation.LOC),
            buildAlertSignal(VMD_ALERT_SIGNAL_HANDLE5, AlertSignalManifestation.VIS, AlertActivation.OFF,
                AlertSignalPrimaryLocation.LOC),
            buildAlertSignal(VMD_ALERT_SIGNAL_HANDLE_REM, AlertSignalManifestation.AUD, AlertActivation.OFF,
                AlertSignalPrimaryLocation.REM));

        final var initial = buildMdib(SEQUENCE_ID, BigInteger.ZERO, BigInteger.ZERO, false,
            initialSystemSignalActivations, alertSignals);

        // set alert activation for manifestation AUD to PSD
        final var firstSystemSignalActivations = List.of(
            createSystemSignalActivation(AlertSignalManifestation.AUD, AlertActivation.PSD),
            createSystemSignalActivation(AlertSignalManifestation.VIS, AlertActivation.OFF));
        // set AUD alert signals to PSD
        final var firstUpdate = buildEpisodicAlertReport(SEQUENCE_ID, BigInteger.ONE,
            buildAlertSystemState(VMD_ALERT_SYSTEM_HANDLE, AlertActivation.ON, firstSystemSignalActivations),
            buildAlertSignalState(VMD_ALERT_SIGNAL_HANDLE, AlertActivation.PSD, AlertSignalPrimaryLocation.LOC));

        messageStorageUtil.addInboundSecureHttpMessage(storage, initialUnacceptableSequence);
        messageStorageUtil.addInboundSecureHttpMessage(storage, firstUpdateUnacceptableSequence);

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, firstUpdate);

        testClass.testRequirementB128();
    }

    /**
     * Tests whether setting multiple system signal activations for the same manifestation, without violating the
     * restrictions of the requirement passes the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementB128GoodMultipleSystemSignalActivationsForSameManifestation() throws Exception {
        final var systemSignalActivations = List.of(
            createSystemSignalActivation(AlertSignalManifestation.AUD, AlertActivation.PSD));

        final var alertSignals = List.of(
            buildAlertSignal(VMD_ALERT_SIGNAL_HANDLE, AlertSignalManifestation.AUD, AlertActivation.PSD,
                AlertSignalPrimaryLocation.LOC),
            buildAlertSignal(VMD_ALERT_SIGNAL_HANDLE2, AlertSignalManifestation.AUD, AlertActivation.OFF,
                AlertSignalPrimaryLocation.LOC),
            buildAlertSignal(VMD_ALERT_SIGNAL_HANDLE3, AlertSignalManifestation.AUD, AlertActivation.PSD,
                AlertSignalPrimaryLocation.LOC),
            buildAlertSignal(VMD_ALERT_SIGNAL_HANDLE4, AlertSignalManifestation.AUD, AlertActivation.PSD,
                AlertSignalPrimaryLocation.LOC),
            buildAlertSignal(VMD_ALERT_SIGNAL_HANDLE_REM, AlertSignalManifestation.AUD, AlertActivation.OFF,
                AlertSignalPrimaryLocation.LOC));

        final var initial = buildMdib(SEQUENCE_ID, BigInteger.ZERO, BigInteger.ZERO, false,
            systemSignalActivations, alertSignals);

        // update with two system signal activations for alert signal manifestation AUD
        final var firstUpdate = buildEpisodicAlertReport(SEQUENCE_ID, BigInteger.ONE,
            buildAlertSystemState(VMD_ALERT_SYSTEM_HANDLE, AlertActivation.ON, List.of(
                createSystemSignalActivation(AlertSignalManifestation.AUD, AlertActivation.PSD),
                createSystemSignalActivation(AlertSignalManifestation.AUD, AlertActivation.ON)
            )));

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, firstUpdate);

        testClass.testRequirementB128();

    }

    /**
     * Tests whether setting the activation state of an non local alert signal different from the system signal
     * activation still passes the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementB128GoodSettingRemoteAlertSignal() throws Exception {
        final var systemSignalActivations = List.of(
            createSystemSignalActivation(AlertSignalManifestation.AUD, AlertActivation.OFF));

        final var alertSignals = List.of(
            buildAlertSignal(VMD_ALERT_SIGNAL_HANDLE, AlertSignalManifestation.AUD, AlertActivation.OFF,
                AlertSignalPrimaryLocation.REM),
            buildAlertSignal(VMD_ALERT_SIGNAL_HANDLE2, AlertSignalManifestation.AUD, AlertActivation.OFF,
                AlertSignalPrimaryLocation.REM));

        final var initial = buildMdib(SEQUENCE_ID, BigInteger.ZERO, BigInteger.ZERO, true,
            systemSignalActivations, alertSignals);

        // update the system signal activations for remote alert signals
        final var firstUpdate = buildEpisodicAlertReport(SEQUENCE_ID, BigInteger.ONE,
            buildAlertSignalState(VMD_ALERT_SIGNAL_HANDLE, AlertActivation.ON, AlertSignalPrimaryLocation.REM),
            buildAlertSignalState(VMD_ALERT_SIGNAL_HANDLE2, AlertActivation.PSD, AlertSignalPrimaryLocation.REM));

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, firstUpdate);

        testClass.testRequirementB128();
    }

    /**
     * Tests whether setting multiple system signal activations for the same manifestation, which are violating the
     * restrictions of the requirement fails the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementB128BadMultipleSystemSignalActivationForSameManifestation() throws Exception {
        final var systemSignalActivations = List.of(
            createSystemSignalActivation(AlertSignalManifestation.AUD, AlertActivation.PSD));

        final var alertSignals = List.of(
            buildAlertSignal(VMD_ALERT_SIGNAL_HANDLE, AlertSignalManifestation.AUD, AlertActivation.PSD,
                AlertSignalPrimaryLocation.LOC),
            buildAlertSignal(VMD_ALERT_SIGNAL_HANDLE2, AlertSignalManifestation.AUD, AlertActivation.PSD,
                AlertSignalPrimaryLocation.LOC),
            buildAlertSignal(VMD_ALERT_SIGNAL_HANDLE3, AlertSignalManifestation.AUD, AlertActivation.PSD,
                AlertSignalPrimaryLocation.LOC),
            buildAlertSignal(VMD_ALERT_SIGNAL_HANDLE4, AlertSignalManifestation.AUD, AlertActivation.PSD,
                AlertSignalPrimaryLocation.LOC),
            buildAlertSignal(VMD_ALERT_SIGNAL_HANDLE_REM, AlertSignalManifestation.AUD, AlertActivation.PSD,
                AlertSignalPrimaryLocation.LOC));

        final var initial = buildMdib(SEQUENCE_ID, BigInteger.ZERO, BigInteger.ZERO, false,
            systemSignalActivations, alertSignals);

        // update with two system signal activations for alert signal manifestation AUD
        final var firstUpdate = buildEpisodicAlertReport(SEQUENCE_ID, BigInteger.ONE,
            buildAlertSystemState(VMD_ALERT_SYSTEM_HANDLE, AlertActivation.ON, List.of(
                createSystemSignalActivation(AlertSignalManifestation.AUD, AlertActivation.PSD),
                createSystemSignalActivation(AlertSignalManifestation.AUD, AlertActivation.ON)
            )));

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, firstUpdate);

        assertThrows(AssertionError.class, testClass::testRequirementB128, "Should have failed, since all"
            + " alert signal alert activation states are set to PSD.");

    }

    /**
     * Tests whether setting the alert activation state of an alert signal to a state contradicting the alert
     * activation state of the system signal activation of the alert system fails the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementB128Bad() throws Exception {
        final var systemSignalActivations = List.of(
            createSystemSignalActivation(AlertSignalManifestation.VIS, AlertActivation.OFF)
        );

        final var alertSignals = List.of(
            buildAlertSignal(VMD_ALERT_SIGNAL_HANDLE, AlertSignalManifestation.VIS,
                AlertActivation.OFF, AlertSignalPrimaryLocation.LOC));

        final var initial = buildMdib(SEQUENCE_ID, BigInteger.ZERO, BigInteger.ZERO, false,
            systemSignalActivations, alertSignals);

        final var firstUpdate = buildEpisodicAlertReport(SEQUENCE_ID, BigInteger.ONE,
            buildAlertSignalState(VMD_ALERT_SIGNAL_HANDLE, AlertActivation.ON, AlertSignalPrimaryLocation.LOC));

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, firstUpdate);

        final var error = assertThrows(AssertionError.class, testClass::testRequirementB128,
            "Should have failed, alert activation of alert system state is OFF, alert activation of alert"
                + " signal should be OFF");
        assertTrue(error.getMessage().contains(VMD_ALERT_SIGNAL_HANDLE));

    }

    private AbstractAlertState buildAlertSystemState(final String handle,
                                                     final AlertActivation activation,
                                                     final List<SystemSignalActivation> systemSignalActivations) {
        final var alertSystemState = mdibBuilder.buildAlertSystemState(handle, activation);
        alertSystemState.setSystemSignalActivation(systemSignalActivations);
        return alertSystemState;
    }

    private AbstractAlertState buildAlertSignalState(final String handle,
                                                     final AlertActivation activation,
                                                     final AlertSignalPrimaryLocation location) {
        final var alertSignalState = mdibBuilder.buildAlertSignalState(handle, activation);
        alertSignalState.setLocation(location);
        return alertSignalState;
    }

    private Pair<AlertSignalDescriptor, AlertSignalState> buildAlertSignal(final String handle,
                                                                           final AlertSignalManifestation manifestation,
                                                                           final AlertActivation alertActivation,
                                                                           final AlertSignalPrimaryLocation location) {
        final var alertSignal = mdibBuilder.buildAlertSignal(
            handle, manifestation, false, alertActivation);
        alertSignal.getRight().setLocation(location);
        return alertSignal;
    }

    private SystemSignalActivation createSystemSignalActivation(final AlertSignalManifestation manifestation,
                                                                final AlertActivation alertActivation) {
        final var systemSignalActivation = participantModelFactory.createSystemSignalActivation();
        systemSignalActivation.setManifestation(manifestation);
        systemSignalActivation.setState(alertActivation);
        return systemSignalActivation;
    }

    private Envelope buildMdib(
        final String sequenceId,
        final @Nullable BigInteger vmdVersion,
        final @Nullable BigInteger mdsVersion,
        final boolean multipleAlertSystems,
        final List<SystemSignalActivation> systemSignalActivations,
        final List<Pair<AlertSignalDescriptor, AlertSignalState>> alertSignals) {
        final var mdib = mdibBuilder.buildMinimalMdib(sequenceId);

        final var mdState = mdib.getMdState();
        final var mdsDescriptor = mdib.getMdDescription().getMds().get(0);
        mdsDescriptor.setDescriptorVersion(mdsVersion);

        final var vmdAlertSystem = mdibBuilder.buildAlertSystem(VMD_ALERT_SYSTEM_HANDLE, AlertActivation.ON);

        vmdAlertSystem.getRight().setSystemSignalActivation(systemSignalActivations);
        vmdAlertSystem.getLeft().setAlertSignal(alertSignals.stream().map(Pair::getLeft).collect(Collectors.toList()));

        final var vmd = mdibBuilder.buildVmd(VMD_HANDLE);
        vmd.getLeft().setDescriptorVersion(vmdVersion);
        vmd.getRight().setDescriptorVersion(vmdVersion);
        vmd.getLeft().setAlertSystem(vmdAlertSystem.getLeft());
        mdState.getState().add(vmdAlertSystem.getRight());
        mdState.getState().addAll(alertSignals.stream().map(Pair::getRight).collect(Collectors.toList()));

        mdsDescriptor.getVmd().add(vmd.getLeft());
        mdState.getState().add(vmd.getRight());

        if (multipleAlertSystems) {
            final var mdsAlertSystem = mdibBuilder.buildAlertSystem(MDS_ALERT_SYSTEM_HANDLE, AlertActivation.OFF);
            final var mdsAlertSignal = mdibBuilder.buildAlertSignal(
                MDS_ALERT_SIGNAL_HANDLE,
                AlertSignalManifestation.OTH,
                false,
                AlertActivation.OFF);
            mdsAlertSignal.getRight().setLocation(AlertSignalPrimaryLocation.REM);

            final var mdsSecondAlertSignal = mdibBuilder.buildAlertSignal(
                MDS_ALERT_SIGNAL_HANDLE2,
                AlertSignalManifestation.AUD,
                true,
                AlertActivation.OFF);
            mdsSecondAlertSignal.getRight().setLocation(AlertSignalPrimaryLocation.REM);

            final var mdsThirdAlertSignal = mdibBuilder.buildAlertSignal(
                MDS_ALERT_SIGNAL_HANDLE3,
                AlertSignalManifestation.AUD,
                true,
                AlertActivation.OFF);
            mdsThirdAlertSignal.getRight().setLocation(AlertSignalPrimaryLocation.LOC);

            mdsAlertSystem.getLeft().setAlertSignal(List.of(
                mdsAlertSignal.getLeft(),
                mdsSecondAlertSignal.getLeft(),
                mdsThirdAlertSignal.getLeft()));
            mdsAlertSystem.getRight().setSystemSignalActivation(systemSignalActivations);
            mdsDescriptor.setAlertSystem(mdsAlertSystem.getLeft());
            mdState.getState().addAll(List.of(
                mdsAlertSystem.getRight(),
                mdsAlertSignal.getRight(),
                mdsSecondAlertSignal.getRight(),
                mdsThirdAlertSignal.getRight()));

        }
        final var getMdibResponse = messageBuilder.buildGetMdibResponse(mdib.getSequenceId());
        getMdibResponse.setMdib(mdib);

        return messageBuilder.createSoapMessageWithBody(
            ActionConstants.getResponseAction(ActionConstants.ACTION_GET_MDIB),
            getMdibResponse
        );
    }

    private Envelope buildEpisodicAlertReport(
        final String sequenceId,
        final @Nullable BigInteger mdibVersion,
        final AbstractAlertState... alertStates
    ) {
        final var report = messageBuilder.buildEpisodicAlertReport(sequenceId);

        final var reportPart = messageBuilder.buildAbstractAlertReportReportPart();
        for (var alertState : alertStates) {
            reportPart.getAlertState().add(alertState);
        }

        report.setMdibVersion(mdibVersion);
        report.getReportPart().add(reportPart);
        return messageBuilder.createSoapMessageWithBody(
            ActionConstants.ACTION_EPISODIC_ALERT_REPORT,
            report
        );
    }
}
