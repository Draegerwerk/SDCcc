/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2023 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.tests.biceps.invariant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.draeger.medical.biceps.model.participant.AbstractAlertState;
import com.draeger.medical.biceps.model.participant.AlertActivation;
import com.draeger.medical.biceps.model.participant.AlertConditionDescriptor;
import com.draeger.medical.biceps.model.participant.AlertConditionKind;
import com.draeger.medical.biceps.model.participant.AlertConditionPriority;
import com.draeger.medical.biceps.model.participant.AlertSignalDescriptor;
import com.draeger.medical.biceps.model.participant.AlertSignalManifestation;
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
import java.math.BigInteger;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.somda.sdc.dpws.helper.JaxbMarshalling;
import org.somda.sdc.dpws.soap.SoapMarshalling;
import org.somda.sdc.glue.common.ActionConstants;

/**
 * Unit test for the BICEPS {@linkplain InvariantAlertStateTest}.
 */
public class InvariantAlertStateTestTest {
    private static final String VMD_HANDLE = "someVmd";
    private static final String MDS_ALERT_SYSTEM_HANDLE = "someMdsAlertSystem";
    private static final String MDS_ALERT_CONDITION_HANDLE = "someMdsAlertConditionHandle";
    private static final String MDS_ALERT_SIGNAL_HANDLE = "someMdsAlertSignalHandle";
    private static final String MDS_SECOND_ALERT_CONDITION_HANDLE = "someOtherMdsAlertConditionHandle";
    private static final String MDS_SECOND_ALERT_SIGNAL_HANDLE = "someOtherMdsAlertSignalHandle";
    private static final String VMD_ALERT_SYSTEM_HANDLE = "someVmdAlertSystem";
    private static final String VMD_ALERT_CONDITION_HANDLE = "someVmdAlertConditionHandle";
    private static final String VMD_ALERT_SIGNAL_HANDLE = "someVmdAlertSignalHandle";
    private static final String SEQUENCE_ID = MdibBuilder.DEFAULT_SEQUENCE_ID;
    private static final String SEQUENCE_ID2 = "123457";

    private static MessageStorageUtil messageStorageUtil;
    private static MdibBuilder mdibBuilder;
    private static MessageBuilder messageBuilder;
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    private MessageStorage storage;
    private InvariantAlertStateTest testClass;
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
    void setup() throws Exception {
        final TestClient mockClient = mock(TestClient.class);
        when(mockClient.isClientRunning()).thenReturn(true);

        final Injector injector;
        injector = InjectorUtil.setupInjector(new AbstractModule() {
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

        testClass = new InvariantAlertStateTest();
        testClass.setup();
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
    public void testRequirementR0029NoTestData() {
        assertThrows(NoTestData.class, testClass::testRequirementR00290);
    }

    /**
     * Tests whether setting the presence attribute of an alert condition state to "true" and the activation states of
     * the same alert condition state and the alert system state to "On" causes the test to pass.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR0029Good() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID, BigInteger.ZERO, BigInteger.ZERO, false);

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

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, first);
        messageStorageUtil.addInboundSecureHttpMessage(storage, second);

        testClass.testRequirementR00290();
    }

    /**
     * Tests whether setting the presence attribute of an alert condition state to "true" and the activation states of
     * the same alert condition state and the alert system state to "On" causes the test to pass.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR0029GoodMultipleSequences() throws Exception {

        final var initial = buildMdib(SEQUENCE_ID, BigInteger.ZERO, BigInteger.ZERO, false);

        final var update = buildEpisodicAlertReport(
                SEQUENCE_ID,
                BigInteger.ONE,
                buildAlertConditionState(VMD_ALERT_CONDITION_HANDLE, AlertActivation.PSD, false),
                buildAlertConditionState(MDS_ALERT_CONDITION_HANDLE, AlertActivation.PSD, false),
                buildAlertConditionState(MDS_SECOND_ALERT_CONDITION_HANDLE, AlertActivation.PSD, false));

        final var initialSecondSequence = buildMdib(SEQUENCE_ID2, BigInteger.ZERO, BigInteger.ZERO, false);

        final var first = buildEpisodicAlertReport(
                SEQUENCE_ID2,
                BigInteger.ONE,
                buildAlertConditionState(VMD_ALERT_CONDITION_HANDLE, AlertActivation.PSD, false),
                buildAlertConditionState(MDS_ALERT_CONDITION_HANDLE, AlertActivation.PSD, false),
                buildAlertConditionState(MDS_SECOND_ALERT_CONDITION_HANDLE, AlertActivation.PSD, false));

        final var second = buildEpisodicAlertReport(
                SEQUENCE_ID2,
                BigInteger.TWO,
                buildAlertConditionState(VMD_ALERT_CONDITION_HANDLE, AlertActivation.ON, true),
                buildAlertConditionState(MDS_ALERT_CONDITION_HANDLE, AlertActivation.ON, true),
                buildAlertConditionState(MDS_SECOND_ALERT_CONDITION_HANDLE, AlertActivation.ON, true));

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, update);

        // new sequence
        messageStorageUtil.addInboundSecureHttpMessage(storage, initialSecondSequence);
        messageStorageUtil.addInboundSecureHttpMessage(storage, first);
        messageStorageUtil.addInboundSecureHttpMessage(storage, second);

        testClass.testRequirementR00290();
    }

    /**
     * Tests whether setting the presence attribute of an alert condition state to "true" and the activation states of
     * the same alert condition state and the alert system state to "On", while having multiple alert condition states
     * causes the test to pass.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR0029GoodMultipleAlertConditionStates() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID, BigInteger.ZERO, BigInteger.ZERO, false);

        final var first = buildEpisodicAlertReport(
                SEQUENCE_ID,
                BigInteger.ONE,
                buildAlertConditionState(MDS_ALERT_CONDITION_HANDLE, AlertActivation.ON, true),
                buildAlertConditionState(MDS_SECOND_ALERT_CONDITION_HANDLE, AlertActivation.PSD, false));

        final var second = buildEpisodicAlertReport(
                SEQUENCE_ID,
                BigInteger.TWO,
                buildAlertSystemState(MDS_ALERT_SYSTEM_HANDLE, AlertActivation.OFF),
                buildAlertConditionState(MDS_ALERT_CONDITION_HANDLE, AlertActivation.OFF, false),
                buildAlertConditionState(MDS_SECOND_ALERT_CONDITION_HANDLE, AlertActivation.OFF, false));

        final var third = buildEpisodicAlertReport(
                SEQUENCE_ID,
                BigInteger.valueOf(3),
                buildAlertSystemState(MDS_ALERT_SYSTEM_HANDLE, AlertActivation.ON),
                buildAlertConditionState(MDS_ALERT_CONDITION_HANDLE, AlertActivation.ON, true),
                buildAlertConditionState(MDS_SECOND_ALERT_CONDITION_HANDLE, AlertActivation.ON, true));

        final var fourth = buildEpisodicAlertReport(
                SEQUENCE_ID,
                BigInteger.valueOf(4),
                buildAlertConditionState(VMD_ALERT_CONDITION_HANDLE, AlertActivation.ON, true));

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, first);
        messageStorageUtil.addInboundSecureHttpMessage(storage, second);
        messageStorageUtil.addInboundSecureHttpMessage(storage, third);
        messageStorageUtil.addInboundSecureHttpMessage(storage, fourth);
        testClass.testRequirementR00290();
    }

    /**
     * Tests whether setting the presence attribute of an alert condition state to "true" and the activation states of
     * the same alert condition state and the alert system state to "On", while having multiple alert system states
     * with their own alert condition states, causes the test to pass.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR0029GoodDifferentAlertSystems() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID, BigInteger.ZERO, BigInteger.ZERO, false);

        final var firstUpdate = buildEpisodicAlertReport(
                SEQUENCE_ID,
                BigInteger.ONE,
                buildAlertSystemState(MDS_ALERT_SYSTEM_HANDLE, AlertActivation.PSD),
                buildAlertConditionState(VMD_ALERT_CONDITION_HANDLE, AlertActivation.ON, true),
                buildAlertConditionState(MDS_ALERT_CONDITION_HANDLE, AlertActivation.PSD, false));

        final var secondUpdate = buildEpisodicAlertReport(
                SEQUENCE_ID,
                BigInteger.TWO,
                buildAlertSystemState(MDS_ALERT_SYSTEM_HANDLE, AlertActivation.ON),
                buildAlertConditionState(MDS_ALERT_CONDITION_HANDLE, AlertActivation.ON, true),
                buildAlertConditionState(MDS_SECOND_ALERT_CONDITION_HANDLE, AlertActivation.ON, true));

        final var thirdUpdate = buildEpisodicAlertReport(
                SEQUENCE_ID,
                BigInteger.valueOf(3),
                buildAlertSystemState(MDS_ALERT_SYSTEM_HANDLE, AlertActivation.OFF),
                buildAlertSystemState(VMD_ALERT_SYSTEM_HANDLE, AlertActivation.OFF),
                buildAlertConditionState(MDS_ALERT_CONDITION_HANDLE, AlertActivation.OFF, false),
                buildAlertConditionState(MDS_SECOND_ALERT_CONDITION_HANDLE, AlertActivation.OFF, false),
                buildAlertConditionState(VMD_ALERT_CONDITION_HANDLE, AlertActivation.OFF, false));

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, firstUpdate);
        messageStorageUtil.addInboundSecureHttpMessage(storage, secondUpdate);
        messageStorageUtil.addInboundSecureHttpMessage(storage, thirdUpdate);
        testClass.testRequirementR00290();
    }

    /**
     * Tests whether setting the presence attribute of an alert condition state to "true" while the activation state of
     * either the alert condition state or the alert system state is not set to "On" causes the test to fail.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR0029Bad() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID, BigInteger.ZERO, BigInteger.ZERO, false);

        final var first = buildEpisodicAlertReport(
                SEQUENCE_ID,
                BigInteger.ONE,
                buildAlertSystemState(VMD_ALERT_SYSTEM_HANDLE, AlertActivation.OFF),
                buildAlertConditionState(VMD_ALERT_CONDITION_HANDLE, AlertActivation.ON, true));

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, first);

        assertThrows(AssertionError.class, () -> testClass.testRequirementR00290());

        final var second = buildEpisodicAlertReport(
                SEQUENCE_ID,
                BigInteger.TWO,
                buildAlertSystemState(VMD_ALERT_SYSTEM_HANDLE, AlertActivation.ON),
                buildAlertConditionState(VMD_ALERT_CONDITION_HANDLE, AlertActivation.OFF, true));

        messageStorageUtil.addInboundSecureHttpMessage(storage, second);

        assertThrows(AssertionError.class, () -> testClass.testRequirementR00290());
    }

    /**
     * Tests whether calling the tests without any input data causes the test to fail.
     */
    @Test
    public void testRequirementR0116NoTestData() {
        final var error = assertThrows(NoTestData.class, testClass::testRequirementR0116);
        assertEquals(InvariantAlertStateTest.NO_ACCEPTABLE_SEQUENCE_SEEN, error.getMessage());
    }

    /**
     * Tests whether setting the activation state of an alert system state and the activation states of an alert
     * condition state and an alert signal state to expected combinations causes the test to pass.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR0116Good() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID, BigInteger.ZERO, BigInteger.ZERO, false, false, true);

        final var first = buildEpisodicAlertReport(
                SEQUENCE_ID,
                BigInteger.ONE,
                buildAlertConditionState(VMD_ALERT_CONDITION_HANDLE, AlertActivation.ON, true),
                buildAlertSignalState(VMD_ALERT_SIGNAL_HANDLE, AlertActivation.OFF));

        final var second = buildEpisodicAlertReport(
                SEQUENCE_ID,
                BigInteger.TWO,
                buildAlertSystemState(VMD_ALERT_SYSTEM_HANDLE, AlertActivation.OFF),
                buildAlertConditionState(VMD_ALERT_CONDITION_HANDLE, AlertActivation.OFF, false));

        final var third = buildEpisodicAlertReport(
                SEQUENCE_ID,
                BigInteger.valueOf(3),
                buildAlertSystemState(VMD_ALERT_SYSTEM_HANDLE, AlertActivation.PSD),
                buildAlertConditionState(VMD_ALERT_CONDITION_HANDLE, AlertActivation.PSD, false),
                buildAlertSignalState(VMD_ALERT_SIGNAL_HANDLE, AlertActivation.PSD));

        final var fourth = buildEpisodicAlertReport(
                SEQUENCE_ID,
                BigInteger.valueOf(4),
                buildAlertSystemState(VMD_ALERT_SYSTEM_HANDLE, AlertActivation.ON),
                buildAlertConditionState(VMD_ALERT_CONDITION_HANDLE, AlertActivation.OFF, false));

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, first);
        messageStorageUtil.addInboundSecureHttpMessage(storage, second);
        messageStorageUtil.addInboundSecureHttpMessage(storage, third);
        messageStorageUtil.addInboundSecureHttpMessage(storage, fourth);
        testClass.testRequirementR0116();
    }

    /**
     * Tests whether seeing the activation state of an alert system state and the activation states of an alert
     * condition state and an alert signal state to expected combinations during one sequence causes the test to pass.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR0116GoodMultipleSequences() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID, BigInteger.ZERO, BigInteger.ZERO, false, false, true);

        final var update = buildEpisodicAlertReport(
                SEQUENCE_ID,
                BigInteger.ONE,
                buildAlertConditionState(VMD_ALERT_CONDITION_HANDLE, AlertActivation.ON, true),
                buildAlertSignalState(VMD_ALERT_SIGNAL_HANDLE, AlertActivation.OFF));

        final var initialSecondSequence = buildMdib(SEQUENCE_ID2, BigInteger.ZERO, BigInteger.ZERO, false, false, true);

        final var first = buildEpisodicAlertReport(
                SEQUENCE_ID2,
                BigInteger.ONE,
                buildAlertConditionState(VMD_ALERT_CONDITION_HANDLE, AlertActivation.ON, true),
                buildAlertSignalState(VMD_ALERT_SIGNAL_HANDLE, AlertActivation.OFF));

        final var second = buildEpisodicAlertReport(
                SEQUENCE_ID2,
                BigInteger.TWO,
                buildAlertSystemState(VMD_ALERT_SYSTEM_HANDLE, AlertActivation.OFF),
                buildAlertConditionState(VMD_ALERT_CONDITION_HANDLE, AlertActivation.OFF, false));

        final var third = buildEpisodicAlertReport(
                SEQUENCE_ID2,
                BigInteger.valueOf(3),
                buildAlertSystemState(VMD_ALERT_SYSTEM_HANDLE, AlertActivation.PSD),
                buildAlertConditionState(VMD_ALERT_CONDITION_HANDLE, AlertActivation.PSD, false),
                buildAlertSignalState(VMD_ALERT_SIGNAL_HANDLE, AlertActivation.PSD));

        final var fourth = buildEpisodicAlertReport(
                SEQUENCE_ID2,
                BigInteger.valueOf(4),
                buildAlertSystemState(VMD_ALERT_SYSTEM_HANDLE, AlertActivation.ON),
                buildAlertConditionState(VMD_ALERT_CONDITION_HANDLE, AlertActivation.OFF, false));

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, update);

        // second sequence
        messageStorageUtil.addInboundSecureHttpMessage(storage, initialSecondSequence);
        messageStorageUtil.addInboundSecureHttpMessage(storage, first);
        messageStorageUtil.addInboundSecureHttpMessage(storage, second);
        messageStorageUtil.addInboundSecureHttpMessage(storage, third);
        messageStorageUtil.addInboundSecureHttpMessage(storage, fourth);
        testClass.testRequirementR0116();
    }

    /**
     * Tests whether setting the activation state of an alert system state and the activation states of multiple alert
     * condition states and multiple alert signal states to expected combinations causes the test to pass.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR0116GoodMultipleAlertConditionsAndSignals() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID, BigInteger.ZERO, BigInteger.ZERO);

        final var first = buildEpisodicAlertReport(
                SEQUENCE_ID,
                BigInteger.ONE,
                buildAlertConditionState(MDS_ALERT_CONDITION_HANDLE, AlertActivation.ON, true),
                buildAlertConditionState(MDS_SECOND_ALERT_CONDITION_HANDLE, AlertActivation.PSD, false));

        final var second = buildEpisodicAlertReport(
                SEQUENCE_ID,
                BigInteger.TWO,
                buildAlertSystemState(MDS_ALERT_SYSTEM_HANDLE, AlertActivation.OFF),
                buildAlertConditionState(MDS_ALERT_CONDITION_HANDLE, AlertActivation.OFF, false),
                buildAlertConditionState(MDS_SECOND_ALERT_CONDITION_HANDLE, AlertActivation.OFF, false));

        final var third = buildEpisodicAlertReport(
                SEQUENCE_ID,
                BigInteger.valueOf(3),
                buildAlertSystemState(MDS_ALERT_SYSTEM_HANDLE, AlertActivation.PSD),
                buildAlertConditionState(MDS_ALERT_CONDITION_HANDLE, AlertActivation.PSD, false),
                buildAlertSignalState(MDS_ALERT_SIGNAL_HANDLE, AlertActivation.PSD),
                buildAlertConditionState(MDS_SECOND_ALERT_CONDITION_HANDLE, AlertActivation.PSD, false),
                buildAlertSignalState(MDS_SECOND_ALERT_SIGNAL_HANDLE, AlertActivation.PSD),
                buildAlertSystemState(VMD_ALERT_SYSTEM_HANDLE, AlertActivation.PSD),
                buildAlertConditionState(VMD_ALERT_CONDITION_HANDLE, AlertActivation.PSD, false),
                buildAlertSignalState(VMD_ALERT_SIGNAL_HANDLE, AlertActivation.PSD));

        final var fourth = buildEpisodicAlertReport(
                SEQUENCE_ID,
                BigInteger.valueOf(4),
                buildAlertSystemState(VMD_ALERT_SYSTEM_HANDLE, AlertActivation.OFF),
                buildAlertConditionState(VMD_ALERT_CONDITION_HANDLE, AlertActivation.OFF, false),
                buildAlertSignalState(VMD_ALERT_SIGNAL_HANDLE, AlertActivation.OFF));

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, first);
        messageStorageUtil.addInboundSecureHttpMessage(storage, second);
        messageStorageUtil.addInboundSecureHttpMessage(storage, third);
        messageStorageUtil.addInboundSecureHttpMessage(storage, fourth);
        testClass.testRequirementR0116();
    }

    /**
     * Tests whether setting the activation state of multiple alert system states and the activation states of alert
     * condition states and alert signal states to expected combinations causes the test to pass.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR0116GoodMultipleAlertSystems() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID, BigInteger.ZERO, BigInteger.ZERO, true, false, true);

        final var first = buildEpisodicAlertReport(
                SEQUENCE_ID,
                BigInteger.ONE,
                buildAlertSystemState(MDS_ALERT_SYSTEM_HANDLE, AlertActivation.PSD),
                buildAlertConditionState(MDS_ALERT_CONDITION_HANDLE, AlertActivation.PSD, false),
                buildAlertSignalState(MDS_ALERT_SIGNAL_HANDLE, AlertActivation.PSD),
                buildAlertConditionState(VMD_ALERT_CONDITION_HANDLE, AlertActivation.ON, true),
                buildAlertSignalState(VMD_ALERT_SIGNAL_HANDLE, AlertActivation.OFF));

        final var second = buildEpisodicAlertReport(
                SEQUENCE_ID,
                BigInteger.TWO,
                buildAlertSystemState(MDS_ALERT_SYSTEM_HANDLE, AlertActivation.OFF),
                buildAlertConditionState(MDS_ALERT_CONDITION_HANDLE, AlertActivation.OFF, false),
                buildAlertSignalState(MDS_ALERT_SIGNAL_HANDLE, AlertActivation.OFF),
                buildAlertSystemState(VMD_ALERT_SYSTEM_HANDLE, AlertActivation.PSD),
                buildAlertConditionState(VMD_ALERT_CONDITION_HANDLE, AlertActivation.PSD, false),
                buildAlertSignalState(VMD_ALERT_SIGNAL_HANDLE, AlertActivation.PSD));

        final var third = buildEpisodicAlertReport(
                SEQUENCE_ID,
                BigInteger.valueOf(3),
                buildAlertSystemState(VMD_ALERT_SYSTEM_HANDLE, AlertActivation.OFF),
                buildAlertConditionState(VMD_ALERT_CONDITION_HANDLE, AlertActivation.OFF, false),
                buildAlertSignalState(VMD_ALERT_SIGNAL_HANDLE, AlertActivation.OFF));

        final var fourth = buildEpisodicAlertReport(
                SEQUENCE_ID,
                BigInteger.valueOf(4),
                buildAlertSystemState(MDS_ALERT_SYSTEM_HANDLE, AlertActivation.OFF),
                buildAlertConditionState(MDS_ALERT_CONDITION_HANDLE, AlertActivation.OFF, false),
                buildAlertSignalState(MDS_ALERT_SIGNAL_HANDLE, AlertActivation.OFF));

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, first);
        messageStorageUtil.addInboundSecureHttpMessage(storage, second);
        messageStorageUtil.addInboundSecureHttpMessage(storage, third);
        messageStorageUtil.addInboundSecureHttpMessage(storage, fourth);
        testClass.testRequirementR0116();
    }

    /**
     * Tests whether setting the activation states of an alert system state to 'Psd', but the activation states of
     * the alert condition state and alert signal state to 'Off' causes the test to fail.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR0116Bad() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID, BigInteger.ZERO, BigInteger.ZERO);

        final var first = buildEpisodicAlertReport(
                SEQUENCE_ID, BigInteger.ONE, buildAlertSystemState(VMD_ALERT_SYSTEM_HANDLE, AlertActivation.PSD));

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, first);
        assertThrows(AssertionError.class, () -> testClass.testRequirementR0116());
    }

    /**
     * Tests whether never seeing the activation state of an alert system set to 'Psd' causes the test to fail.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR0116BadNoPsdState() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID, BigInteger.ZERO, BigInteger.ZERO);

        final var first = buildEpisodicAlertReport(
                SEQUENCE_ID,
                BigInteger.ONE,
                buildAlertSystemState(VMD_ALERT_SYSTEM_HANDLE, AlertActivation.OFF),
                buildAlertConditionState(VMD_ALERT_CONDITION_HANDLE, AlertActivation.OFF, false),
                buildAlertSignalState(VMD_ALERT_SIGNAL_HANDLE, AlertActivation.OFF),
                buildAlertSystemState(MDS_ALERT_SYSTEM_HANDLE, AlertActivation.OFF),
                buildAlertConditionState(MDS_ALERT_CONDITION_HANDLE, AlertActivation.OFF, false),
                buildAlertSignalState(MDS_ALERT_SIGNAL_HANDLE, AlertActivation.OFF),
                buildAlertConditionState(MDS_SECOND_ALERT_CONDITION_HANDLE, AlertActivation.OFF, false),
                buildAlertSignalState(MDS_SECOND_ALERT_SIGNAL_HANDLE, AlertActivation.OFF));

        final var second = buildEpisodicAlertReport(
                SEQUENCE_ID,
                BigInteger.ONE,
                buildAlertSystemState(MDS_ALERT_SYSTEM_HANDLE, AlertActivation.PSD),
                buildAlertConditionState(MDS_ALERT_CONDITION_HANDLE, AlertActivation.PSD, false),
                buildAlertSignalState(MDS_ALERT_SIGNAL_HANDLE, AlertActivation.PSD),
                buildAlertConditionState(MDS_SECOND_ALERT_CONDITION_HANDLE, AlertActivation.PSD, false),
                buildAlertSignalState(MDS_SECOND_ALERT_SIGNAL_HANDLE, AlertActivation.PSD));

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, first);
        messageStorageUtil.addInboundSecureHttpMessage(storage, second);

        assertThrows(NoTestData.class, testClass::testRequirementR0116);
    }

    /**
     * Tests whether never seeing the activation state of an alert system set to 'Off' causes the test to fail.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR0116BadNoOffState() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID, BigInteger.ZERO, BigInteger.ZERO);

        final var first = buildEpisodicAlertReport(
                SEQUENCE_ID,
                BigInteger.ONE,
                buildAlertSystemState(VMD_ALERT_SYSTEM_HANDLE, AlertActivation.PSD),
                buildAlertConditionState(VMD_ALERT_CONDITION_HANDLE, AlertActivation.PSD, false),
                buildAlertSignalState(VMD_ALERT_SIGNAL_HANDLE, AlertActivation.PSD),
                buildAlertSystemState(MDS_ALERT_SYSTEM_HANDLE, AlertActivation.PSD),
                buildAlertConditionState(MDS_ALERT_CONDITION_HANDLE, AlertActivation.PSD, false),
                buildAlertSignalState(MDS_ALERT_SIGNAL_HANDLE, AlertActivation.PSD),
                buildAlertConditionState(MDS_SECOND_ALERT_CONDITION_HANDLE, AlertActivation.PSD, false),
                buildAlertSignalState(MDS_SECOND_ALERT_SIGNAL_HANDLE, AlertActivation.PSD));

        final var second = buildEpisodicAlertReport(
                SEQUENCE_ID,
                BigInteger.ONE,
                buildAlertSystemState(MDS_ALERT_SYSTEM_HANDLE, AlertActivation.OFF),
                buildAlertConditionState(MDS_ALERT_CONDITION_HANDLE, AlertActivation.OFF, false),
                buildAlertSignalState(MDS_ALERT_SIGNAL_HANDLE, AlertActivation.OFF),
                buildAlertConditionState(MDS_SECOND_ALERT_CONDITION_HANDLE, AlertActivation.OFF, false),
                buildAlertSignalState(MDS_SECOND_ALERT_SIGNAL_HANDLE, AlertActivation.OFF));

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, first);
        messageStorageUtil.addInboundSecureHttpMessage(storage, second);

        assertThrows(NoTestData.class, testClass::testRequirementR0116);
    }

    /**
     * Tests whether setting the activation state of an alert system state with multiple alert condition states to
     * 'Off', but not all activation states of the alert condition states to 'Off' causes the test to fail.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR0116BadMultipleAlertConditionsAndSignals() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID, BigInteger.ZERO, BigInteger.ZERO);

        final var first = buildEpisodicAlertReport(
                SEQUENCE_ID,
                BigInteger.ONE,
                buildAlertConditionState(MDS_ALERT_CONDITION_HANDLE, AlertActivation.ON, true),
                buildAlertConditionState(MDS_SECOND_ALERT_CONDITION_HANDLE, AlertActivation.PSD, false));

        final var second = buildEpisodicAlertReport(
                SEQUENCE_ID,
                BigInteger.TWO,
                buildAlertSystemState(MDS_ALERT_SYSTEM_HANDLE, AlertActivation.OFF),
                buildAlertConditionState(MDS_ALERT_CONDITION_HANDLE, AlertActivation.OFF, false));

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, first);
        messageStorageUtil.addInboundSecureHttpMessage(storage, second);
        assertThrows(AssertionError.class, () -> testClass.testRequirementR0116());
    }

    /**
     * Tests whether setting the activation state of an alert system state to 'Off', but the activation states of
     * the alert condition state and alert signal state to 'Psd', when multiple alert systems are present,
     * causes the test to fail.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR0116BadMultipleAlertSystems() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID, BigInteger.ZERO, BigInteger.ZERO);

        final var first = buildEpisodicAlertReport(
                SEQUENCE_ID,
                BigInteger.ONE,
                buildAlertSystemState(VMD_ALERT_SYSTEM_HANDLE, AlertActivation.PSD),
                buildAlertSystemState(MDS_ALERT_SYSTEM_HANDLE, AlertActivation.OFF),
                buildAlertConditionState(VMD_ALERT_CONDITION_HANDLE, AlertActivation.PSD, false),
                buildAlertSignalState(VMD_ALERT_SIGNAL_HANDLE, AlertActivation.PSD),
                buildAlertConditionState(MDS_ALERT_CONDITION_HANDLE, AlertActivation.PSD, false),
                buildAlertSignalState(MDS_ALERT_SIGNAL_HANDLE, AlertActivation.PSD));

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, first);
        assertThrows(AssertionError.class, () -> testClass.testRequirementR0116());
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

    private Envelope buildMdib(
            final String sequenceId, final @Nullable BigInteger vmdVersion, final @Nullable BigInteger mdsVersion) {
        return buildMdib(sequenceId, vmdVersion, mdsVersion, true, true, true);
    }

    private Envelope buildMdib(
            final String sequenceId,
            final @Nullable BigInteger vmdVersion,
            final @Nullable BigInteger mdsVersion,
            final boolean presence) {
        return buildMdib(sequenceId, vmdVersion, mdsVersion, true, true, presence);
    }

    private Envelope buildMdib(
            final String sequenceId,
            final @Nullable BigInteger vmdVersion,
            final @Nullable BigInteger mdsVersion,
            final boolean multipleAlertSystems,
            final boolean multipleAlertConditionsAndSignals,
            final boolean presence) {
        final var mdib = mdibBuilder.buildMinimalMdib(sequenceId);

        final var mdState = mdib.getMdState();
        final var mdsDescriptor = mdib.getMdDescription().getMds().get(0);
        mdsDescriptor.setDescriptorVersion(mdsVersion);

        final var vmdAlertCondition = mdibBuilder.buildAlertCondition(
                VMD_ALERT_CONDITION_HANDLE, AlertConditionKind.OTH, AlertConditionPriority.ME, AlertActivation.ON);
        vmdAlertCondition.getRight().setPresence(presence);
        final var vmdAlertSignal = mdibBuilder.buildAlertSignal(
                VMD_ALERT_SIGNAL_HANDLE, AlertSignalManifestation.OTH, false, AlertActivation.OFF);

        final var vmdAlertSystem = mdibBuilder.buildAlertSystem(VMD_ALERT_SYSTEM_HANDLE, AlertActivation.ON);
        vmdAlertCondition.getLeft().setDescriptorVersion(null);
        vmdAlertSignal.getLeft().setDescriptorVersion(null);
        vmdAlertSystem.getLeft().setDescriptorVersion(null);
        vmdAlertSystem.getLeft().getAlertCondition().clear();
        vmdAlertSystem.getLeft().getAlertCondition().addAll(List.of(vmdAlertCondition.getLeft()));
        vmdAlertSystem.getLeft().getAlertSignal().clear();
        vmdAlertSystem.getLeft().getAlertSignal().addAll(List.of(vmdAlertSignal.getLeft()));

        final var vmd = mdibBuilder.buildVmd(VMD_HANDLE);
        vmd.getLeft().setDescriptorVersion(vmdVersion);
        vmd.getRight().setDescriptorVersion(vmdVersion);
        vmd.getLeft().setAlertSystem(vmdAlertSystem.getLeft());
        mdState.getState()
                .addAll(List.of(vmdAlertSystem.getRight(), vmdAlertCondition.getRight(), vmdAlertSignal.getRight()));

        mdsDescriptor.getVmd().add(vmd.getLeft());
        mdState.getState().add(vmd.getRight());

        if (multipleAlertSystems) {
            final var mdsAlertSystem = mdibBuilder.buildAlertSystem(MDS_ALERT_SYSTEM_HANDLE, AlertActivation.ON);
            final var listOfAlertConditions = new ArrayList<AlertConditionDescriptor>();
            final var listOfAlertSignals = new ArrayList<AlertSignalDescriptor>();
            final var mdsAlertCondition = mdibBuilder.buildAlertCondition(
                    MDS_ALERT_CONDITION_HANDLE, AlertConditionKind.OTH, AlertConditionPriority.ME, AlertActivation.ON);
            mdsAlertCondition.getRight().setPresence(presence);
            listOfAlertConditions.add(mdsAlertCondition.getLeft());
            final var mdsAlertSignal = mdibBuilder.buildAlertSignal(
                    MDS_ALERT_SIGNAL_HANDLE, AlertSignalManifestation.OTH, false, AlertActivation.OFF);
            listOfAlertSignals.add(mdsAlertSignal.getLeft());
            if (multipleAlertConditionsAndSignals) {
                final var mdsSecondAlertCondition = mdibBuilder.buildAlertCondition(
                        MDS_SECOND_ALERT_CONDITION_HANDLE,
                        AlertConditionKind.TEC,
                        AlertConditionPriority.HI,
                        AlertActivation.ON);
                mdsSecondAlertCondition.getRight().setPresence(presence);
                listOfAlertConditions.add(mdsSecondAlertCondition.getLeft());
                final var mdsSecondAlertSignal = mdibBuilder.buildAlertSignal(
                        MDS_SECOND_ALERT_SIGNAL_HANDLE, AlertSignalManifestation.VIS, true, AlertActivation.OFF);
                listOfAlertSignals.add(mdsSecondAlertSignal.getLeft());
                mdState.getState().addAll(List.of(mdsSecondAlertCondition.getRight(), mdsSecondAlertSignal.getRight()));
            }

            mdsAlertSystem.getLeft().getAlertCondition().clear();
            mdsAlertSystem.getLeft().getAlertCondition().addAll(listOfAlertConditions);
            mdsAlertSystem.getLeft().getAlertSignal().clear();
            mdsAlertSystem.getLeft().getAlertSignal().addAll(listOfAlertSignals);

            mdsDescriptor.setAlertSystem(mdsAlertSystem.getLeft());
            mdState.getState()
                    .addAll(List.of(
                            mdsAlertSystem.getRight(), mdsAlertCondition.getRight(), mdsAlertSignal.getRight()));
        }
        final var getMdibResponse = messageBuilder.buildGetMdibResponse(mdib.getSequenceId());
        getMdibResponse.setMdib(mdib);

        return messageBuilder.createSoapMessageWithBody(
                ActionConstants.getResponseAction(ActionConstants.ACTION_GET_MDIB), getMdibResponse);
    }

    private Envelope buildEpisodicAlertReport(
            final String sequenceId, final @Nullable BigInteger mdibVersion, final AbstractAlertState... alertStates) {
        final var report = messageBuilder.buildEpisodicAlertReport(sequenceId);

        final var reportPart = messageBuilder.buildAbstractAlertReportReportPart();
        for (var alertState : alertStates) {
            reportPart.getAlertState().add(alertState);
        }

        report.setMdibVersion(mdibVersion);
        report.getReportPart().add(reportPart);
        return messageBuilder.createSoapMessageWithBody(ActionConstants.ACTION_EPISODIC_ALERT_REPORT, report);
    }
}
