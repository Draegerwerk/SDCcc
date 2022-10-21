/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.tests.biceps.invariant;

import com.draeger.medical.biceps.model.message.DescriptionModificationReport;
import com.draeger.medical.biceps.model.message.DescriptionModificationType;
import com.draeger.medical.biceps.model.message.EpisodicContextReport;
import com.draeger.medical.biceps.model.message.EpisodicMetricReport;
import com.draeger.medical.biceps.model.participant.AbstractAlertState;
import com.draeger.medical.biceps.model.participant.AbstractDescriptor;
import com.draeger.medical.biceps.model.participant.AbstractState;
import com.draeger.medical.biceps.model.participant.ChannelState;
import com.draeger.medical.biceps.model.participant.ComponentActivation;
import com.draeger.medical.biceps.model.participant.ContextAssociation;
import com.draeger.medical.biceps.model.participant.MdsDescriptor;
import com.draeger.medical.biceps.model.participant.MetricAvailability;
import com.draeger.medical.biceps.model.participant.MetricCategory;
import com.draeger.medical.biceps.model.participant.PatientContextState;
import com.draeger.medical.biceps.model.participant.SafetyClassification;
import com.draeger.medical.biceps.model.participant.StringMetricState;
import com.draeger.medical.biceps.model.participant.VmdDescriptor;
import com.draeger.medical.dpws.soap.model.Envelope;
import com.draeger.medical.sdccc.marshalling.MarshallingUtil;
import com.draeger.medical.sdccc.messages.MessageStorage;
import com.draeger.medical.sdccc.sdcri.testclient.TestClient;
import com.draeger.medical.sdccc.sdcri.testclient.TestClientUtil;
import com.draeger.medical.sdccc.tests.InjectorTestBase;
import com.draeger.medical.sdccc.tests.test_util.InjectorUtil;
import com.draeger.medical.sdccc.tests.util.InitialImpliedValueException;
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
import org.opentest4j.AssertionFailedError;
import org.somda.sdc.biceps.model.participant.ActivateOperationDescriptor;
import org.somda.sdc.biceps.model.participant.AlertConditionDescriptor;
import org.somda.sdc.biceps.model.participant.AlertSystemDescriptor;
import org.somda.sdc.biceps.model.participant.BatteryDescriptor;
import org.somda.sdc.biceps.model.participant.ChannelDescriptor;
import org.somda.sdc.biceps.model.participant.ClockDescriptor;
import org.somda.sdc.biceps.model.participant.DistributionSampleArrayMetricDescriptor;
import org.somda.sdc.biceps.model.participant.EnsembleContextDescriptor;
import org.somda.sdc.biceps.model.participant.EnumStringMetricDescriptor;
import org.somda.sdc.biceps.model.participant.LimitAlertConditionDescriptor;
import org.somda.sdc.biceps.model.participant.LocationContextDescriptor;
import org.somda.sdc.biceps.model.participant.MeansContextDescriptor;
import org.somda.sdc.biceps.model.participant.NumericMetricDescriptor;
import org.somda.sdc.biceps.model.participant.OperatorContextDescriptor;
import org.somda.sdc.biceps.model.participant.PatientContextDescriptor;
import org.somda.sdc.biceps.model.participant.RealTimeSampleArrayMetricDescriptor;
import org.somda.sdc.biceps.model.participant.ScoDescriptor;
import org.somda.sdc.biceps.model.participant.SetAlertStateOperationDescriptor;
import org.somda.sdc.biceps.model.participant.SetComponentStateOperationDescriptor;
import org.somda.sdc.biceps.model.participant.SetContextStateOperationDescriptor;
import org.somda.sdc.biceps.model.participant.SetMetricStateOperationDescriptor;
import org.somda.sdc.biceps.model.participant.SetStringOperationDescriptor;
import org.somda.sdc.biceps.model.participant.SetValueOperationDescriptor;
import org.somda.sdc.biceps.model.participant.StringMetricDescriptor;
import org.somda.sdc.biceps.model.participant.SystemContextDescriptor;
import org.somda.sdc.biceps.model.participant.WorkflowContextDescriptor;
import org.somda.sdc.dpws.helper.JaxbMarshalling;
import org.somda.sdc.dpws.soap.SoapMarshalling;
import org.somda.sdc.glue.common.ActionConstants;

import javax.annotation.Nullable;
import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.math.BigInteger;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test for the BICEPS {@linkplain InvariantParticipantModelVersioningTest}.
 */
public class InvariantParticipantModelVersioningTestTest {

    private static final String VMD_HANDLE = "someVmd";
    private static final String CHANNEL_HANDLE = "someChannel";
    private static final String STRING_METRIC_HANDLE = "someStringMetric";
    private static final String MDS_HANDLE = MdibBuilder.DEFAULT_MDS_HANDLE;
    private static final String PATIENT_CONTEXT_DESCRIPTOR_HANDLE = "somePatientDescriptor";
    private static final String SYSTEM_CONTEXT_HANDLE = "systemContext123";
    private static final String SEQUENCE_ID = MdibBuilder.DEFAULT_SEQUENCE_ID;


    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);
    private static MessageStorageUtil messageStorageUtil;
    private static MdibBuilder mdibBuilder;
    private static MessageBuilder messageBuilder;
    private MessageStorage storage;
    private InvariantParticipantModelVersioningTest testClass;
    private Injector riInjector;
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

        riInjector = TestClientUtil.createClientInjector(
        );
        when(mockClient.getInjector()).thenReturn(riInjector);

        baseMarshalling = riInjector.getInstance(JaxbMarshalling.class);
        baseMarshalling.startAsync().awaitRunning(DEFAULT_TIMEOUT);

        marshalling = riInjector.getInstance(SoapMarshalling.class);
        marshalling.startAsync().awaitRunning(DEFAULT_TIMEOUT);

        storage = injector.getInstance(MessageStorage.class);

        testClass = new InvariantParticipantModelVersioningTest();
        testClass.setUp();
    }

    @AfterEach
    void tearDown() throws TimeoutException {
        baseMarshalling.stopAsync().awaitTerminated(DEFAULT_TIMEOUT);
        marshalling.stopAsync().awaitTerminated(DEFAULT_TIMEOUT);
        storage.close();
    }

    /**
     * Tests whether removing and inserting a child with incremented descriptor version passes the test.
     */
    @Test
    public void testRequirementR0033Good() throws NoTestData, IOException, JAXBException {
        final var initial = buildMdib(null, BigInteger.ZERO);

        final var firstUpdate = buildDescriptionModificationReport(
            SEQUENCE_ID, BigInteger.ONE, BigInteger.ONE, BigInteger.ONE, BigInteger.ONE, null
        );
        final var secondUpdate = buildDescriptionModificationReport(
            SEQUENCE_ID, BigInteger.TWO, BigInteger.TWO, BigInteger.TWO, BigInteger.ONE, null, true
        );
        final var three = BigInteger.valueOf(3);
        final var thirdUpdate = buildDescriptionModificationReport(
            SEQUENCE_ID, three, three, null, null, null
        );

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, firstUpdate);
        messageStorageUtil.addInboundSecureHttpMessage(storage, secondUpdate);
        messageStorageUtil.addInboundSecureHttpMessage(storage, thirdUpdate);

        testClass.testRequirementR0033();
    }

    /**
     * Tests if only the initial value of a DescriptorVersion is interpreted as implied value.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR0033BadImpliedValue() throws Exception {
        final var initial = buildMdib(null, BigInteger.ZERO);

        final var firstUpdate = buildDescriptionModificationReportWithParts(SEQUENCE_ID, BigInteger.ONE,
            buildDescriptionModificationReportPart(DescriptionModificationType.UPT,
                buildMds(MDS_HANDLE, BigInteger.ONE, BigInteger.ONE),
                buildVmd(VMD_HANDLE, BigInteger.ONE, BigInteger.ONE),
                buildChannel(CHANNEL_HANDLE, BigInteger.ONE, BigInteger.ONE)));

        final var secondUpdate = buildDescriptionModificationReportWithParts(SEQUENCE_ID, BigInteger.TWO,
            buildDescriptionModificationReportPart(DescriptionModificationType.UPT,
                buildMds(MDS_HANDLE, BigInteger.TWO, BigInteger.TWO),
                buildVmd(VMD_HANDLE, null, BigInteger.TWO)),
            buildDescriptionModificationReportPart(DescriptionModificationType.DEL,
                buildChannel(CHANNEL_HANDLE, BigInteger.ONE, BigInteger.ONE)));

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, firstUpdate);
        messageStorageUtil.addInboundSecureHttpMessage(storage, secondUpdate);
        final var throwable = assertThrows(AssertionFailedError.class, () -> testClass.testRequirementR0033());
        assertTrue(throwable.getCause() instanceof InitialImpliedValueException);
    }

    /**
     * Tests whether removing a channel without updating the vmd descriptor version fails the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR0033Bad() throws Exception {
        final var initial = buildMdib(null, BigInteger.ZERO);

        final var firstUpdate = buildDescriptionModificationReport(
            SEQUENCE_ID, BigInteger.ONE, BigInteger.ONE, BigInteger.ONE, BigInteger.ONE, null
        );
        final var secondUpdate = buildDescriptionModificationReport(
            SEQUENCE_ID, BigInteger.TWO, BigInteger.TWO, BigInteger.ONE, BigInteger.ONE, null, true
        );
        final var three = BigInteger.valueOf(3);
        final var thirdUpdate = buildDescriptionModificationReport(
            SEQUENCE_ID, three, three, null, null, null
        );

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, firstUpdate);
        messageStorageUtil.addInboundSecureHttpMessage(storage, secondUpdate);
        messageStorageUtil.addInboundSecureHttpMessage(storage, thirdUpdate);

        assertThrows(AssertionError.class, testClass::testRequirementR0033);
    }

    /**
     * Tests whether removing a channel without updating the vmd descriptor but changing the sequence id passes.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR0033BadButSequenceIdChanged() throws Exception {
        final var initial = buildMdib(null, BigInteger.ZERO);

        final var firstUpdate = buildDescriptionModificationReport(
            SEQUENCE_ID, BigInteger.ONE, BigInteger.ONE, BigInteger.ONE, BigInteger.ONE, null
        );
        final var secondSequence = "brabrablubb";
        final var secondInitial = buildMdib(secondSequence, null, BigInteger.ZERO);

        // remove the channel but don't update the vmd descriptor version
        final var secondUpdate = buildDescriptionModificationReport(
            secondSequence, BigInteger.TWO, BigInteger.TWO, BigInteger.ONE, BigInteger.ONE, null, true
        );
        final var three = BigInteger.valueOf(3);
        final var thirdUpdate = buildDescriptionModificationReport(
            secondSequence, three, three, null, null, null
        );

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, firstUpdate);
        messageStorageUtil.addInboundSecureHttpMessage(storage, secondUpdate);
        messageStorageUtil.addInboundSecureHttpMessage(storage, thirdUpdate);
        messageStorageUtil.addInboundSecureHttpMessage(storage, secondInitial);

        assertDoesNotThrow(testClass::testRequirementR0033);
    }

    /**
     * Tests whether no and insufficient test data fails the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR0033BadNoData() throws Exception {
        final var initialMdib = buildMdib(null, null);
        messageStorageUtil.addInboundSecureHttpMessage(storage, initialMdib);
        assertThrows(NoTestData.class, testClass::testRequirementR0033);

        final var firstUpdate = buildDescriptionModificationReport(
            SEQUENCE_ID, BigInteger.ONE, BigInteger.ONE, BigInteger.ONE, BigInteger.ONE, null
        );
        messageStorageUtil.addInboundSecureHttpMessage(storage, firstUpdate);
        assertThrows(NoTestData.class, testClass::testRequirementR0033);
    }

    /**
     * Test whether the method hasDescriptorChanged() returns false when the descriptor did not change.
     */
    @Test
    public void testHasDescriptorChangedGoodAllSimpleDescriptors() {
        testHasDescriptorChangedGoodSimple(new AlertConditionDescriptor());
        testHasDescriptorChangedGoodSimple(new LimitAlertConditionDescriptor());
        testHasDescriptorChangedGoodSimple(new EnsembleContextDescriptor());
        testHasDescriptorChangedGoodSimple(new LocationContextDescriptor());
        testHasDescriptorChangedGoodSimple(new MeansContextDescriptor());
        testHasDescriptorChangedGoodSimple(new OperatorContextDescriptor());
        testHasDescriptorChangedGoodSimple(new PatientContextDescriptor());
        testHasDescriptorChangedGoodSimple(new WorkflowContextDescriptor());

        testHasDescriptorChangedGoodSimple(new BatteryDescriptor());
        testHasDescriptorChangedGoodSimple(new ClockDescriptor());

        testHasDescriptorChangedGoodSimple(new NumericMetricDescriptor());
        testHasDescriptorChangedGoodSimple(new EnumStringMetricDescriptor());
        testHasDescriptorChangedGoodSimple(new DistributionSampleArrayMetricDescriptor());
        testHasDescriptorChangedGoodSimple(new RealTimeSampleArrayMetricDescriptor());
        testHasDescriptorChangedGoodSimple(new StringMetricDescriptor());

        testHasDescriptorChangedGoodSimple(new ActivateOperationDescriptor());
        testHasDescriptorChangedGoodSimple(new SetAlertStateOperationDescriptor());
        testHasDescriptorChangedGoodSimple(new SetComponentStateOperationDescriptor());
        testHasDescriptorChangedGoodSimple(new SetContextStateOperationDescriptor());
        testHasDescriptorChangedGoodSimple(new SetMetricStateOperationDescriptor());
        testHasDescriptorChangedGoodSimple(new SetStringOperationDescriptor());
        testHasDescriptorChangedGoodSimple(new SetValueOperationDescriptor());
    }

    /**
     * Test whether the method hasDescriptorChanged() returns false when the descriptor did not change.
     */
    @Test
    public void testHasDescriptorChangedGoodAllComplexDescriptors() {
        // MdsDescriptor
        testHasDescriptorChangedGoodComplex(createMdsDescriptor(),
            (d, v) -> d.getSco().setHandle(v),
            "oldHandle", "newHandle");
        testHasDescriptorChangedGoodComplex(createMdsDescriptor(),
            (d, v) -> d.getSystemContext().setHandle(v),
            "oldHandle", "newHandle");
        testHasDescriptorChangedGoodComplex(createMdsDescriptor(),
            (d, v) -> d.getVmd().get(0).setHandle(v),
            "oldHandle", "newHandle");
        testHasDescriptorChangedGoodComplex(createMdsDescriptor(),
            (d, v) -> d.getBattery().get(0).setHandle(v),
            "oldHandle", "newHandle");
        testHasDescriptorChangedGoodComplex(createMdsDescriptor(),
            (d, v) -> d.getAlertSystem().setHandle(v),
            "oldHandle", "newHandle");
        testHasDescriptorChangedGoodComplex(createMdsDescriptor(),
            (d, v) -> d.getClock().setHandle(v),
            "oldHandle", "newHandle");

        // VmdDescriptor
        testHasDescriptorChangedGoodComplex(createVmdDescriptor(),
            (d, v) -> d.getSco().setHandle(v),
            "oldHandle", "newHandle");
        testHasDescriptorChangedGoodComplex(createVmdDescriptor(),
            (d, v) -> d.getChannel().get(0).setHandle(v),
            "oldHandle", "newHandle");
        testHasDescriptorChangedGoodComplex(createVmdDescriptor(),
            (d, v) -> d.getAlertSystem().setHandle(v),
            "oldHandle", "newHandle");

        // ChannelDescriptor
        testHasDescriptorChangedGoodComplex(createChannelDescriptor(),
            (d, v) -> d.getMetric().get(0).setHandle(v),
            "oldHandle", "newHandle");

        // SystemContextDescriptor
        testHasDescriptorChangedGoodComplex(createSystemContextDescriptor(),
            (d, v) -> d.getOperatorContext().get(0).setHandle(v),
            "oldHandle", "newHandle");
        testHasDescriptorChangedGoodComplex(createSystemContextDescriptor(),
            (d, v) -> d.getMeansContext().get(0).setHandle(v),
            "oldHandle", "newHandle");
        testHasDescriptorChangedGoodComplex(createSystemContextDescriptor(),
            (d, v) -> d.getLocationContext().setHandle(v),
            "oldHandle", "newHandle");
        testHasDescriptorChangedGoodComplex(createSystemContextDescriptor(),
            (d, v) -> d.getEnsembleContext().get(0).setHandle(v),
            "oldHandle", "newHandle");
        testHasDescriptorChangedGoodComplex(createSystemContextDescriptor(),
            (d, v) -> d.getPatientContext().setHandle(v),
            "oldHandle", "newHandle");
        testHasDescriptorChangedGoodComplex(createSystemContextDescriptor(),
            (d, v) -> d.getWorkflowContext().get(0).setHandle(v),
            "oldHandle", "newHandle");

        testHasDescriptorChangedGoodComplex(createScoDescriptor(),
            (d, v) -> d.getOperation().get(0).setHandle(v),
            "oldHandle", "newHandle");
    }

    private ScoDescriptor createScoDescriptor() {
        final ScoDescriptor sco = new ScoDescriptor();
        sco.setOperation(List.of(new SetValueOperationDescriptor()));
        return sco;
    }

    private SystemContextDescriptor createSystemContextDescriptor() {
        final SystemContextDescriptor sc = new SystemContextDescriptor();
        sc.setOperatorContext(List.of(new OperatorContextDescriptor()));
        sc.setMeansContext(List.of(new MeansContextDescriptor()));
        sc.setLocationContext(new LocationContextDescriptor());
        sc.setEnsembleContext(List.of(new EnsembleContextDescriptor()));
        sc.setPatientContext(new PatientContextDescriptor());
        sc.setWorkflowContext(List.of(new WorkflowContextDescriptor()));
        return sc;
    }

    private ChannelDescriptor createChannelDescriptor() {
        final ChannelDescriptor channel = new ChannelDescriptor();
        channel.setMetric(List.of(new EnumStringMetricDescriptor()));
        return channel;
    }

    private org.somda.sdc.biceps.model.participant.VmdDescriptor createVmdDescriptor() {
        final org.somda.sdc.biceps.model.participant.VmdDescriptor vmd =
            new org.somda.sdc.biceps.model.participant.VmdDescriptor();
        vmd.setSco(new ScoDescriptor());
        vmd.setChannel(List.of(new ChannelDescriptor()));
        vmd.setAlertSystem(new AlertSystemDescriptor());
        return vmd;
    }

    private org.somda.sdc.biceps.model.participant.MdsDescriptor createMdsDescriptor() {
        final org.somda.sdc.biceps.model.participant.MdsDescriptor mds =
            new org.somda.sdc.biceps.model.participant.MdsDescriptor();
        mds.setSco(new ScoDescriptor());
        mds.setSystemContext(new SystemContextDescriptor());
        mds.setVmd(List.of(new org.somda.sdc.biceps.model.participant.VmdDescriptor()));
        mds.setBattery(List.of(new BatteryDescriptor()));
        mds.setAlertSystem(new AlertSystemDescriptor());
        mds.setClock(new ClockDescriptor());
        return mds;
    }


    /**
     * Test whether the method hasDescriptorChanged() returns false when the descriptor did not change.
     * @param a - the descriptor.
     */
    private void testHasDescriptorChangedGoodSimple(final org.somda.sdc.biceps.model.participant.AbstractDescriptor a) {
        testHasDescriptorChanged(a,
            d -> { },
            false);
    }

    /**
     * Test whether the method hasDescriptorChanged() returns false when the descriptor did not change.
     * @param a - the descriptor.
     */
    private <T extends org.somda.sdc.biceps.model.participant.AbstractDescriptor, V>
        void testHasDescriptorChangedGoodComplex(
        final T a,
        final DescriptorModificationWithValue<T, V> childModification,
        final V firstValue, final V secondValue) {
        testHasDescriptorChanged(a,
            d -> { },
            false);

        childModification.modify(a, firstValue);
        // NOTE: changes to children of types inheriting from AbstractDescriptor are excepted
        testHasDescriptorChanged(a,
            d -> childModification.modify(d, secondValue),
            false);

    }

    /**
     * Test whether the method hasDescriptorChanged() returns true when the descriptor did change.
     */
    @Test
    public void testHasDescriptorChangedBadAlertConditionDescriptor() {
        final var a = new AlertConditionDescriptor();
        a.setSafetyClassification(org.somda.sdc.biceps.model.participant.SafetyClassification.INF);
        a.setHandle("oldHandle");

        testHasDescriptorChanged(a,
            d -> d.setSafetyClassification(org.somda.sdc.biceps.model.participant.SafetyClassification.MED_B),
            true);
        testHasDescriptorChanged(a,
            d -> d.setHandle("newHandle"),
            true);
    }

    /**
     * Test whether the method hasDescriptorChanged() returns false when the descriptor did not change.
     */
    @Test
    public void testHasDescriptorChangedGoodAlertSystemDescriptor() {
        final var cond = new AlertConditionDescriptor();
        final var a = new AlertSystemDescriptor();
        a.setAlertCondition(List.of(cond));
        a.setSafetyClassification(org.somda.sdc.biceps.model.participant.SafetyClassification.INF);

        testHasDescriptorChanged(a,
            d -> { },
            false);
        // NOTE: changes to children of types inheriting from AbstractDescriptor are excepted
        testHasDescriptorChanged(a,
            d -> d.getAlertCondition()
                .get(0).setHandle("someNewConditionHandle"),
            false);
    }

    /**
     * Test whether the method hasDescriptorChanged() returns true when the descriptor did change.
     */
    @Test
    public void testHasDescriptorChangedBadAlertSystemDescriptor() {
        final var a = new AlertSystemDescriptor();
        a.setSafetyClassification(org.somda.sdc.biceps.model.participant.SafetyClassification.INF);
        a.setHandle("oldHandle");

        testHasDescriptorChanged(a,
            d -> d.setSafetyClassification(org.somda.sdc.biceps.model.participant.SafetyClassification.MED_B),
            true);
        testHasDescriptorChanged(a,
            d -> d.setHandle("newHandle"),
            true);
    }

    private <T extends org.somda.sdc.biceps.model.participant.AbstractDescriptor>
        void testHasDescriptorChanged(final T a,
                                  final DescriptorModification<T> modification,
                                  final boolean expectedResult) {
        final T b
            = (T) a.clone();
        modification.modify(b);

        assertEquals(expectedResult, testClass.hasDescriptorChanged(a, b));
    }


    /**
     * Tests whether updating an elements attributes and incrementing the descriptor version passes the test.
     */
    @Test
    public void testRequirementR0034Good() throws NoTestData, IOException, JAXBException {
        final var initial = buildMdib(null, BigInteger.ZERO);

        final var firstUpdate = buildDescriptionModificationReport(
            SEQUENCE_ID, BigInteger.ONE, BigInteger.ONE, BigInteger.ONE, BigInteger.ONE, null
        );

        final var secondUpdate = buildDescriptionModificationReport(
            SEQUENCE_ID, BigInteger.TWO, null, BigInteger.TWO, null, null, true
        );
        // update an attribute, test must pass as version is bumped, and parents aren't updated
        final var secondMod = (DescriptionModificationReport) secondUpdate.getBody().getAny().get(0);
        final VmdDescriptor secondVmd = (VmdDescriptor) secondMod.getReportPart().get(0).getDescriptor().get(0);
        secondVmd.setSafetyClassification(SafetyClassification.INF);

        final var thirdUpdate = buildDescriptionModificationReport(
            SEQUENCE_ID, BigInteger.valueOf(3), BigInteger.TWO, null, null, null
        );
        // update an attribute, test must pass as version is bumped
        final var thirdMod = (DescriptionModificationReport) thirdUpdate.getBody().getAny().get(0);
        final MdsDescriptor thirdMds = (MdsDescriptor) thirdMod.getReportPart().get(0).getDescriptor().get(0);
        thirdMds.setSafetyClassification(SafetyClassification.INF);

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, firstUpdate);
        messageStorageUtil.addInboundSecureHttpMessage(storage, secondUpdate);
        messageStorageUtil.addInboundSecureHttpMessage(storage, thirdUpdate);

        testClass.testRequirementR0034();
    }

    /**
     * Tests whether updating a descriptor without incrementing its version fails the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR0034Bad() throws Exception {
        final var initial = buildMdib(null, BigInteger.ZERO);

        final var firstUpdate = buildDescriptionModificationReport(
            SEQUENCE_ID, BigInteger.ONE, BigInteger.ONE, BigInteger.ONE, BigInteger.ONE, null
        );
        final var secondUpdate = buildDescriptionModificationReport(
            SEQUENCE_ID, BigInteger.TWO, BigInteger.ONE, BigInteger.ONE, BigInteger.ONE, null, true
        );

        // change attribute of mds descriptor, but do not bump version
        final var secondMod = (DescriptionModificationReport) secondUpdate.getBody().getAny().get(0);
        final MdsDescriptor secondMds = (MdsDescriptor) secondMod.getReportPart().get(0).getDescriptor().get(1);
        secondMds.setSafetyClassification(SafetyClassification.INF);

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, firstUpdate);
        messageStorageUtil.addInboundSecureHttpMessage(storage, secondUpdate);

        assertThrows(AssertionError.class, testClass::testRequirementR0034);
    }

    /**
     * Tests whether insufficient test data fails the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR0034BadNoData() throws Exception {
        final var initialMdib = buildMdib(null, null);
        messageStorageUtil.addInboundSecureHttpMessage(storage, initialMdib);
        assertThrows(NoTestData.class, testClass::testRequirementR0034);

        // should no longer throw
        final var firstUpdate = buildDescriptionModificationReport(
            SEQUENCE_ID, BigInteger.ONE, BigInteger.ONE, BigInteger.ONE, BigInteger.ONE, null
        );
        messageStorageUtil.addInboundSecureHttpMessage(storage, firstUpdate);
        assertDoesNotThrow(testClass::testRequirementR0034);
    }

    /**
     * Tests whether insufficient test data fails the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR0038NoTestData() throws Exception {
        assertThrows(NoTestData.class, testClass::testRequirementR0038);

        final var initialMdib = buildMdib(null, null);
        messageStorageUtil.addInboundSecureHttpMessage(storage, initialMdib);
        assertThrows(NoTestData.class, testClass::testRequirementR0038);

        // should no longer throw
        final var firstUpdate = buildDescriptionModificationReport(
            SEQUENCE_ID, BigInteger.ONE, BigInteger.ONE, BigInteger.ONE, BigInteger.ONE, null, false,
            BigInteger.ONE, BigInteger.ONE, BigInteger.ONE
        );
        messageStorageUtil.addInboundSecureHttpMessage(storage, firstUpdate);
        assertDoesNotThrow(testClass::testRequirementR0038);
    }

    /**
     * Tests whether updating a state's attribute or child and incrementing the state version passes the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR0038Good() throws Exception {
        final var initial = buildMdib(null, BigInteger.ZERO);

        // DescriptionModificationReports
        final var firstUpdate = buildDescriptionModificationReport(
            SEQUENCE_ID, BigInteger.ONE, BigInteger.ONE, BigInteger.ONE, BigInteger.ONE, null, false,
            BigInteger.ONE, BigInteger.ONE, BigInteger.ONE
        );

        final var secondUpdate = buildDescriptionModificationReport(
            SEQUENCE_ID, BigInteger.TWO, BigInteger.TWO, BigInteger.TWO, BigInteger.TWO, null, false,
            BigInteger.TWO, BigInteger.TWO, BigInteger.TWO
        );

        // update attribute
        final var secondReport = (DescriptionModificationReport) secondUpdate.getBody().getAny().get(0);
        final ChannelState secondChannel = (ChannelState) secondReport.getReportPart().get(0).getState().get(0);
        secondChannel.setOperatingHours(1L);

        final var thirdUpdate = buildDescriptionModificationReport(SEQUENCE_ID, BigInteger.valueOf(3),
            BigInteger.valueOf(3), BigInteger.valueOf(3), BigInteger.valueOf(3), null, false,
            BigInteger.valueOf(3), BigInteger.valueOf(3), BigInteger.valueOf(3)
        );

        final var physicalConnectorInfo = mdibBuilder.buildPhysicalConnectorInfo();
        physicalConnectorInfo.setNumber(1);

        // update child
        final var thirdReport = (DescriptionModificationReport) thirdUpdate.getBody().getAny().get(0);
        final ChannelState thirdChannel = (ChannelState) thirdReport.getReportPart().get(0).getState().get(0);
        thirdChannel.setPhysicalConnector(physicalConnectorInfo);

        //EpisodicContextReports
        final var patHandle1 = "pat1";

        final var fourthUpdate = buildEpisodicContextReport(
            SEQUENCE_ID, BigInteger.valueOf(4), PATIENT_CONTEXT_DESCRIPTOR_HANDLE, patHandle1, null
        );

        final var fifthUpdate = buildEpisodicContextReport(
            SEQUENCE_ID, BigInteger.valueOf(5), PATIENT_CONTEXT_DESCRIPTOR_HANDLE, patHandle1, BigInteger.ONE
        );

        // update attribute
        final var fifthReport = (EpisodicContextReport) fifthUpdate.getBody().getAny().get(0);
        final PatientContextState fifthPatientContextState = (PatientContextState) fifthReport.getReportPart().get(0)
            .getContextState().get(0);
        fifthPatientContextState.setContextAssociation(ContextAssociation.ASSOC);

        final var sixthUpdate = buildEpisodicContextReport(
            SEQUENCE_ID, BigInteger.valueOf(6), PATIENT_CONTEXT_DESCRIPTOR_HANDLE, patHandle1, BigInteger.TWO
        );

        // update child
        final var sixthReport = (EpisodicContextReport) sixthUpdate.getBody().getAny().get(0);
        final PatientContextState sixthPatientContextState = (PatientContextState) sixthReport.getReportPart().get(0)
            .getContextState().get(0);
        sixthPatientContextState.setCategory(mdibBuilder.buildCodedValue("1"));

        // EpisodicMetricReports
        final var seventhUpdate = buildMetricReport(SEQUENCE_ID, BigInteger.valueOf(7),
            BigInteger.ONE, STRING_METRIC_HANDLE);

        final var eighthUpdate = buildMetricReport(SEQUENCE_ID, BigInteger.valueOf(8),
            BigInteger.TWO, STRING_METRIC_HANDLE);

        // update a child
        final var eighthReport = (EpisodicMetricReport) eighthUpdate.getBody().getAny().get(0);
        final StringMetricState eighthStringMetricState = (StringMetricState) eighthReport.getReportPart().get(0)
            .getMetricState().get(0);
        eighthStringMetricState.setMetricValue(mdibBuilder.buildStringMetricValue("newValue"));

        final var ninthUpdate = buildMetricReport(SEQUENCE_ID, BigInteger.valueOf(9), BigInteger.valueOf(3),
            STRING_METRIC_HANDLE);

        // update an attribute
        final var ninthReport = (EpisodicMetricReport) ninthUpdate.getBody().getAny().get(0);
        final StringMetricState ninthStringMetricState = (StringMetricState) ninthReport.getReportPart().get(0)
            .getMetricState().get(0);
        ninthStringMetricState.setActivationState(ComponentActivation.ON);

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, firstUpdate);
        messageStorageUtil.addInboundSecureHttpMessage(storage, secondUpdate);
        messageStorageUtil.addInboundSecureHttpMessage(storage, thirdUpdate);
        messageStorageUtil.addInboundSecureHttpMessage(storage, fourthUpdate);
        messageStorageUtil.addInboundSecureHttpMessage(storage, fifthUpdate);
        messageStorageUtil.addInboundSecureHttpMessage(storage, sixthUpdate);
        messageStorageUtil.addInboundSecureHttpMessage(storage, seventhUpdate);
        messageStorageUtil.addInboundSecureHttpMessage(storage, eighthUpdate);
        messageStorageUtil.addInboundSecureHttpMessage(storage, ninthUpdate);

        testClass.testRequirementR0038();
    }

    /**
     * Tests whether updating a state's attribute and not incrementing the state version fails the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR0038BadAttributeChanged() throws Exception {
        final var initial = buildMdib(null, BigInteger.ZERO);

        final var firstUpdate = buildDescriptionModificationReport(
            SEQUENCE_ID, BigInteger.ONE, BigInteger.ONE, BigInteger.ONE, BigInteger.ONE, null, false,
            BigInteger.ONE, BigInteger.ONE, BigInteger.ONE
        );

        final var firstReport = (DescriptionModificationReport) firstUpdate.getBody().getAny().get(0);
        final ChannelState firstChannel = (ChannelState) firstReport.getReportPart().get(0).getState().get(0);
        firstChannel.setOperatingHours(1L);

        final var secondUpdate = buildDescriptionModificationReport(
            SEQUENCE_ID, BigInteger.TWO, BigInteger.ONE, BigInteger.ONE, BigInteger.ONE, null, false,
            BigInteger.ONE, BigInteger.ONE, BigInteger.ONE
        );

        final var secondReport = (DescriptionModificationReport) secondUpdate.getBody().getAny().get(0);
        final ChannelState secondChannel = (ChannelState) secondReport.getReportPart().get(0).getState().get(0);
        secondChannel.setOperatingHours(2L);

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, firstUpdate);
        messageStorageUtil.addInboundSecureHttpMessage(storage, secondUpdate);

        assertThrows(AssertionError.class, () -> testClass.testRequirementR0038());
    }

    /**
     * Tests whether updating a state's child and not incrementing the state version fails the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR0038BadChildChanged() throws Exception {
        final var initial = buildMdib(null, BigInteger.ZERO);

        final var physicalConnectorInfo = mdibBuilder.buildPhysicalConnectorInfo();
        physicalConnectorInfo.setNumber(1);

        final var firstUpdate = buildDescriptionModificationReport(
            SEQUENCE_ID, BigInteger.ONE, BigInteger.ONE, BigInteger.ONE, BigInteger.ONE, null, false,
            BigInteger.ONE, BigInteger.ONE, BigInteger.ONE
        );

        final var firstReport = (DescriptionModificationReport) firstUpdate.getBody().getAny().get(0);
        final ChannelState firstChannel = (ChannelState) firstReport.getReportPart().get(0).getState().get(0);
        firstChannel.setPhysicalConnector(physicalConnectorInfo);

        final var physicalConnectorInfo2 = mdibBuilder.buildPhysicalConnectorInfo();
        physicalConnectorInfo2.setNumber(2);

        final var secondUpdate = buildDescriptionModificationReport(
            SEQUENCE_ID, BigInteger.TWO, BigInteger.ONE, BigInteger.ONE, BigInteger.ONE, null, false,
            BigInteger.ONE, BigInteger.ONE, BigInteger.ONE
        );
        final var secondReport = (DescriptionModificationReport) secondUpdate.getBody().getAny().get(0);
        final ChannelState secondChannel = (ChannelState) secondReport.getReportPart().get(0).getState().get(0);
        secondChannel.setPhysicalConnector(physicalConnectorInfo2);

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, firstUpdate);
        messageStorageUtil.addInboundSecureHttpMessage(storage, secondUpdate);

        assertThrows(AssertionError.class, () -> testClass.testRequirementR0038());
    }

    /**
     * Tests whether updating a context state's attribute and not incrementing the state version fails the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR0038BadContextStateAttributeChanged() throws Exception {
        final var patHandle1 = "pat1";
        final var initial = buildMdib(null, BigInteger.ZERO);

        final var firstUpdate = buildEpisodicContextReport(
            SEQUENCE_ID, BigInteger.ONE, PATIENT_CONTEXT_DESCRIPTOR_HANDLE, patHandle1, BigInteger.ZERO
        );
        final var firstReport = (EpisodicContextReport) firstUpdate.getBody().getAny().get(0);
        final PatientContextState firstPatientContextState = (PatientContextState) firstReport.getReportPart().get(0)
            .getContextState().get(0);
        firstPatientContextState.setContextAssociation(ContextAssociation.ASSOC);

        final var secondUpdate = buildEpisodicContextReport(
            SEQUENCE_ID, BigInteger.TWO, PATIENT_CONTEXT_DESCRIPTOR_HANDLE, patHandle1, BigInteger.ONE
        );
        final var secondReport = (EpisodicContextReport) secondUpdate.getBody().getAny().get(0);
        final PatientContextState secondPatientContextState = (PatientContextState) secondReport.getReportPart().get(0)
            .getContextState().get(0);
        secondPatientContextState.setContextAssociation(ContextAssociation.PRE);

        final var thirdUpdate = buildEpisodicContextReport(
            SEQUENCE_ID, BigInteger.valueOf(3), PATIENT_CONTEXT_DESCRIPTOR_HANDLE, patHandle1, BigInteger.ONE
        );
        final var thirdReport = (EpisodicContextReport) secondUpdate.getBody().getAny().get(0);
        final PatientContextState thirdPatientContextState = (PatientContextState) thirdReport.getReportPart().get(0)
            .getContextState().get(0);
        thirdPatientContextState.setContextAssociation(ContextAssociation.DIS);

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, firstUpdate);
        messageStorageUtil.addInboundSecureHttpMessage(storage, secondUpdate);
        messageStorageUtil.addInboundSecureHttpMessage(storage, thirdUpdate);

        assertThrows(AssertionError.class, () -> testClass.testRequirementR0038());
    }

    /**
     * Tests whether updating a context state's child and not incrementing the state version fails the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR0038BadContextStateChildChanged() throws Exception {
        final var patHandle1 = "pat1";
        final var initial = buildMdib(null, BigInteger.ZERO);

        final var firstUpdate = buildEpisodicContextReport(
            SEQUENCE_ID, BigInteger.ONE, PATIENT_CONTEXT_DESCRIPTOR_HANDLE, patHandle1, BigInteger.ZERO
        );
        final var firstReport = (EpisodicContextReport) firstUpdate.getBody().getAny().get(0);
        final PatientContextState firstPatientContextState = (PatientContextState) firstReport.getReportPart().get(0)
            .getContextState().get(0);
        firstPatientContextState.setCategory(mdibBuilder.buildCodedValue("1"));

        final var secondUpdate = buildEpisodicContextReport(
            SEQUENCE_ID, BigInteger.TWO, PATIENT_CONTEXT_DESCRIPTOR_HANDLE, patHandle1, BigInteger.ONE
        );
        final var secondReport = (EpisodicContextReport) secondUpdate.getBody().getAny().get(0);
        final PatientContextState secondPatientContextState = (PatientContextState) secondReport.getReportPart().get(0)
            .getContextState().get(0);
        secondPatientContextState.setCategory(mdibBuilder.buildCodedValue("2"));

        final var thirdUpdate = buildEpisodicContextReport(
            SEQUENCE_ID, BigInteger.valueOf(3), PATIENT_CONTEXT_DESCRIPTOR_HANDLE, patHandle1, BigInteger.ONE
        );
        final var thirdReport = (EpisodicContextReport) secondUpdate.getBody().getAny().get(0);
        final PatientContextState thirdPatientContextState = (PatientContextState) thirdReport.getReportPart().get(0)
            .getContextState().get(0);
        thirdPatientContextState.setCategory(mdibBuilder.buildCodedValue("3"));

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, firstUpdate);
        messageStorageUtil.addInboundSecureHttpMessage(storage, secondUpdate);
        messageStorageUtil.addInboundSecureHttpMessage(storage, thirdUpdate);

        assertThrows(AssertionError.class, () -> testClass.testRequirementR0038());
    }

    /**
     * Tests whether updating a metric state's attribute and not incrementing the state version fails the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR0038BadMetricAttributeChanged() throws Exception {
        final var initial = buildMdib(null, BigInteger.ZERO);

        final var firstUpdate = buildMetricReport(SEQUENCE_ID, BigInteger.ONE, BigInteger.ONE, STRING_METRIC_HANDLE);
        final var firstReport = (EpisodicMetricReport) firstUpdate.getBody().getAny().get(0);
        final StringMetricState firstStringMetricState = (StringMetricState) firstReport.getReportPart().get(0)
            .getMetricState().get(0);
        firstStringMetricState.setActivationState(ComponentActivation.NOT_RDY);

        final var secondUpdate = buildMetricReport(SEQUENCE_ID, BigInteger.TWO, BigInteger.ONE, STRING_METRIC_HANDLE);
        final var secondReport = (EpisodicMetricReport) secondUpdate.getBody().getAny().get(0);
        final StringMetricState secondStringMetricState = (StringMetricState) secondReport.getReportPart().get(0)
            .getMetricState().get(0);
        secondStringMetricState.setActivationState(ComponentActivation.ON);

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, firstUpdate);
        messageStorageUtil.addInboundSecureHttpMessage(storage, secondUpdate);

        assertThrows(AssertionError.class, () -> testClass.testRequirementR0038());
    }

    /**
     * Tests whether updating a metric state's child and not incrementing the state version fails the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR0038BadMetricStateChildChanged() throws Exception {
        final var initial = buildMdib(null, BigInteger.ZERO);

        final var firstUpdate = buildMetricReport(SEQUENCE_ID, BigInteger.ONE, BigInteger.ONE, STRING_METRIC_HANDLE);
        final var firstReport = (EpisodicMetricReport) firstUpdate.getBody().getAny().get(0);
        final StringMetricState firstStringMetricState = (StringMetricState) firstReport.getReportPart().get(0)
            .getMetricState().get(0);
        firstStringMetricState.setMetricValue(mdibBuilder.buildStringMetricValue("newValue"));

        final var secondUpdate = buildMetricReport(SEQUENCE_ID, BigInteger.TWO, BigInteger.ONE, STRING_METRIC_HANDLE);
        final var secondReport = (EpisodicMetricReport) secondUpdate.getBody().getAny().get(0);
        final StringMetricState secondStringMetricState = (StringMetricState) secondReport.getReportPart().get(0)
            .getMetricState().get(0);
        secondStringMetricState.setMetricValue(mdibBuilder.buildStringMetricValue("newerValue"));

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, firstUpdate);
        messageStorageUtil.addInboundSecureHttpMessage(storage, secondUpdate);

        assertThrows(AssertionError.class, () -> testClass.testRequirementR0038());
    }

    /**
     * Tests whether updating a metric state's child and not incrementing the state version and the mdibversion
     * fails the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR0038BadMetricStateChildChanged2()
        throws Exception {
        final var initial = buildMdib(null, BigInteger.ZERO);

        final var firstUpdate = buildMetricReport(SEQUENCE_ID, BigInteger.ONE, BigInteger.ONE, STRING_METRIC_HANDLE);
        final var firstReport = (EpisodicMetricReport) firstUpdate.getBody().getAny().get(0);
        final StringMetricState firstStringMetricState = (StringMetricState) firstReport.getReportPart().get(0)
            .getMetricState().get(0);
        firstStringMetricState.setMetricValue(mdibBuilder.buildStringMetricValue("newValue"));

        final var secondUpdate = buildMetricReport(SEQUENCE_ID, BigInteger.ONE, BigInteger.ONE, STRING_METRIC_HANDLE);
        final var secondReport = (EpisodicMetricReport) secondUpdate.getBody().getAny().get(0);
        final StringMetricState secondStringMetricState = (StringMetricState) secondReport.getReportPart().get(0)
            .getMetricState().get(0);
        secondStringMetricState.setMetricValue(mdibBuilder.buildStringMetricValue("newerValue"));

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, firstUpdate);
        messageStorageUtil.addInboundSecureHttpMessage(storage, secondUpdate);

        assertThrows(AssertionError.class, () -> testClass.testRequirementR0038());
    }

    /**
     * Tests whether deleting, reinserting with wrong state version and deleting an element again causes the test
     * to fail.
     * The test ensures, that a state does not get ignored, when reinserted and only present during one mdib version.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR0038BadReinsertForOneMdibVersion() throws Exception {
        final var initial = buildMdib(null, BigInteger.ZERO);

        final var firstUpdate = buildDescriptionModificationReport(
            SEQUENCE_ID, BigInteger.ONE, BigInteger.ONE, BigInteger.ONE, BigInteger.ONE, null, false,
            BigInteger.ONE, BigInteger.ONE, BigInteger.ONE
        );

        final var secondUpdate = buildDescriptionModificationReport(
            SEQUENCE_ID, BigInteger.TWO, BigInteger.TWO, BigInteger.TWO, BigInteger.TWO, null, true,
            BigInteger.TWO, BigInteger.TWO, BigInteger.TWO
        );

        final var thirdUpdate = buildDescriptionModificationReport(
            SEQUENCE_ID, BigInteger.valueOf(3), BigInteger.valueOf(3), BigInteger.valueOf(3), BigInteger.ONE,
            DescriptionModificationType.CRT, false, BigInteger.valueOf(3), BigInteger.valueOf(3), BigInteger.ONE
        );

        final var fourthUpdate = buildDescriptionModificationReport(
            SEQUENCE_ID, BigInteger.valueOf(4), BigInteger.valueOf(4), BigInteger.valueOf(4), BigInteger.TWO,
            null, true, BigInteger.valueOf(4), BigInteger.valueOf(4), BigInteger.TWO
        );

        // reinsert the state again with the wrong state version
        final var fifthUpdate = buildDescriptionModificationReport(
            SEQUENCE_ID, BigInteger.valueOf(5), BigInteger.valueOf(5), BigInteger.valueOf(5), BigInteger.TWO,
            DescriptionModificationType.CRT, false, BigInteger.valueOf(5), BigInteger.valueOf(5), BigInteger.ZERO
        );

        // delete the state
        final var sixthUpdate = buildDescriptionModificationReport(
            SEQUENCE_ID, BigInteger.valueOf(6), BigInteger.valueOf(6), BigInteger.valueOf(6), BigInteger.TWO,
            null, true, BigInteger.valueOf(6), BigInteger.valueOf(6), BigInteger.TWO
        );

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, firstUpdate);
        messageStorageUtil.addInboundSecureHttpMessage(storage, secondUpdate);
        messageStorageUtil.addInboundSecureHttpMessage(storage, thirdUpdate);
        messageStorageUtil.addInboundSecureHttpMessage(storage, fourthUpdate);
        messageStorageUtil.addInboundSecureHttpMessage(storage, fifthUpdate);
        messageStorageUtil.addInboundSecureHttpMessage(storage, sixthUpdate);

        assertThrows(AssertionError.class, () -> testClass.testRequirementR0038());
    }

    /**
     * Tests whether insufficient test data fails the test.
     */
    @Test
    public void testRequirementR5003NoTestData() {
        assertThrows(NoTestData.class, testClass::testRequirementR5003);
    }

    /**
     * Tests whether versions which are only incremented causes the test to pass.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR5003Good() throws Exception {

        final var initial = buildMdib(null, BigInteger.ZERO);

        final var firstUpdate = buildDescriptionModificationReportWithParts(SEQUENCE_ID, BigInteger.ONE,
            buildDescriptionModificationReportPart(DescriptionModificationType.UPT,
                buildMds(MDS_HANDLE, BigInteger.ONE, BigInteger.ONE),
                buildVmd(VMD_HANDLE, BigInteger.ONE, BigInteger.ONE),
                buildChannel(CHANNEL_HANDLE, BigInteger.ONE, BigInteger.ONE)));

        final var secondUpdate = buildDescriptionModificationReportWithParts(SEQUENCE_ID, BigInteger.TWO,
            buildDescriptionModificationReportPart(DescriptionModificationType.UPT,
                buildMds(MDS_HANDLE, BigInteger.TWO, BigInteger.TWO),
                buildVmd(VMD_HANDLE, BigInteger.TWO, BigInteger.TWO),
                buildChannel(CHANNEL_HANDLE, BigInteger.TWO, BigInteger.TWO)));

        final var thirdUpdate = buildDescriptionModificationReportWithParts(SEQUENCE_ID, BigInteger.valueOf(3),
            buildDescriptionModificationReportPart(DescriptionModificationType.UPT,
                buildMds(MDS_HANDLE, BigInteger.valueOf(3), BigInteger.valueOf(3)),
                buildVmd(VMD_HANDLE, BigInteger.valueOf(3), BigInteger.valueOf(3))),
            buildDescriptionModificationReportPart(DescriptionModificationType.DEL,
                buildChannel(CHANNEL_HANDLE, BigInteger.TWO, BigInteger.TWO)));

        final var fourthUpdate = buildDescriptionModificationReportWithParts(SEQUENCE_ID, BigInteger.valueOf(4),
            buildDescriptionModificationReportPart(DescriptionModificationType.UPT,
                buildMds(MDS_HANDLE, BigInteger.valueOf(4), BigInteger.valueOf(4)),
                buildVmd(VMD_HANDLE, BigInteger.valueOf(4), BigInteger.valueOf(4))),
            buildDescriptionModificationReportPart(DescriptionModificationType.CRT,
                buildChannel(CHANNEL_HANDLE, BigInteger.TWO, BigInteger.TWO)));

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, firstUpdate);
        messageStorageUtil.addInboundSecureHttpMessage(storage, secondUpdate);
        messageStorageUtil.addInboundSecureHttpMessage(storage, thirdUpdate);
        messageStorageUtil.addInboundSecureHttpMessage(storage, fourthUpdate);

        testClass.testRequirementR5003();
    }

    /**
     * Tests whether just the initial value of the descriptor version is implied.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR5003BadImpliedValue() throws Exception {
        final var initial = buildMdib(null, BigInteger.ZERO);

        final var firstUpdate = buildDescriptionModificationReportWithParts(SEQUENCE_ID, BigInteger.ONE,
            buildDescriptionModificationReportPart(DescriptionModificationType.UPT,
                buildMds(MDS_HANDLE, BigInteger.ONE, BigInteger.ONE),
                buildVmd(VMD_HANDLE, null, BigInteger.ZERO)));

        final var secondUpdate = buildDescriptionModificationReportWithParts(SEQUENCE_ID, BigInteger.TWO,
            buildDescriptionModificationReportPart(DescriptionModificationType.UPT,
                buildMds(MDS_HANDLE, BigInteger.TWO, BigInteger.TWO),
                buildVmd(VMD_HANDLE, BigInteger.ONE, BigInteger.ONE)));

        final var thirdUpdate = buildDescriptionModificationReportWithParts(SEQUENCE_ID, BigInteger.valueOf(3),
            buildDescriptionModificationReportPart(DescriptionModificationType.UPT,
                buildMds(MDS_HANDLE, BigInteger.valueOf(3), BigInteger.valueOf(3)),
                buildVmd(VMD_HANDLE, null, BigInteger.valueOf(3))));

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, firstUpdate);
        messageStorageUtil.addInboundSecureHttpMessage(storage, secondUpdate);
        messageStorageUtil.addInboundSecureHttpMessage(storage, thirdUpdate);

        final var throwable = assertThrows(AssertionError.class, () -> testClass.testRequirementR5003());
        assertTrue(throwable.getCause() instanceof InitialImpliedValueException);
    }

    /**
     * Checks if the implied value of descriptor and state versions are tracked separately and correctly.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR5003GoodImpliedValueStateAndDescriptorVersion() throws Exception {
        final var initial = buildMdib(null, BigInteger.ZERO);

        final var firstUpdate = buildDescriptionModificationReportWithParts(SEQUENCE_ID, BigInteger.ONE,
            buildDescriptionModificationReportPart(DescriptionModificationType.UPT,
                buildMds(MDS_HANDLE, BigInteger.ONE, BigInteger.ONE),
                buildVmd(VMD_HANDLE, null, null),
                buildChannel(CHANNEL_HANDLE, BigInteger.ONE, BigInteger.ONE)));

        final var secondUpdate = buildDescriptionModificationReportWithParts(SEQUENCE_ID, BigInteger.TWO,
            buildDescriptionModificationReportPart(DescriptionModificationType.UPT,
                buildMds(MDS_HANDLE, BigInteger.TWO, BigInteger.TWO),
                buildVmd(VMD_HANDLE, BigInteger.ZERO, null),
                buildChannel(CHANNEL_HANDLE, BigInteger.TWO, BigInteger.TWO)));

        final var thirdUpdate = buildDescriptionModificationReportWithParts(SEQUENCE_ID, BigInteger.valueOf(3),
            buildDescriptionModificationReportPart(DescriptionModificationType.UPT,
                buildMds(MDS_HANDLE, BigInteger.valueOf(3), BigInteger.valueOf(3)),
                buildVmd(VMD_HANDLE, BigInteger.ONE, BigInteger.ONE)),
            buildDescriptionModificationReportPart(DescriptionModificationType.DEL,
                buildChannel(CHANNEL_HANDLE, BigInteger.TWO, BigInteger.TWO)));

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, firstUpdate);
        messageStorageUtil.addInboundSecureHttpMessage(storage, secondUpdate);
        messageStorageUtil.addInboundSecureHttpMessage(storage, thirdUpdate);

        testClass.testRequirementR5003();
    }

    /**
     * Checks if the implied value of descriptor that was previously not implied fails the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR5003BadImpliedValueStateAndDescriptorVersion() throws Exception {
        final var initial = buildMdib(null, BigInteger.ZERO);

        final var firstUpdate = buildDescriptionModificationReportWithParts(SEQUENCE_ID, BigInteger.ONE,
            buildDescriptionModificationReportPart(DescriptionModificationType.UPT,
                buildMds(MDS_HANDLE, BigInteger.ONE, BigInteger.ONE),
                buildVmd(VMD_HANDLE, null, BigInteger.ZERO),
                buildChannel(CHANNEL_HANDLE, BigInteger.ONE, BigInteger.ONE)));

        final var secondUpdate = buildDescriptionModificationReportWithParts(SEQUENCE_ID, BigInteger.TWO,
            buildDescriptionModificationReportPart(DescriptionModificationType.UPT,
                buildMds(MDS_HANDLE, BigInteger.TWO, BigInteger.TWO),
                buildVmd(VMD_HANDLE, BigInteger.ZERO, BigInteger.ZERO),
                buildChannel(CHANNEL_HANDLE, BigInteger.TWO, BigInteger.TWO)));

        final var thirdUpdate = buildDescriptionModificationReportWithParts(SEQUENCE_ID, BigInteger.valueOf(3),
            buildDescriptionModificationReportPart(DescriptionModificationType.UPT,
                buildMds(MDS_HANDLE, BigInteger.valueOf(3), BigInteger.valueOf(3)),
                buildVmd(VMD_HANDLE, null, BigInteger.ZERO)),
            buildDescriptionModificationReportPart(DescriptionModificationType.DEL,
                buildChannel(CHANNEL_HANDLE, BigInteger.TWO, BigInteger.TWO)));

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, firstUpdate);
        messageStorageUtil.addInboundSecureHttpMessage(storage, secondUpdate);
        messageStorageUtil.addInboundSecureHttpMessage(storage, thirdUpdate);

        final var throwable = assertThrows(AssertionError.class, testClass::testRequirementR5003);
        assertTrue(throwable.getCause() instanceof InitialImpliedValueException);
    }

    /**
     * Tests whether a descriptor which was deleted and reinserted with decremented versions causes the test to fail.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR5003BadReinsertReset() throws Exception {

        final var initial = buildMdib(null, BigInteger.ZERO);

        final var firstUpdate = buildDescriptionModificationReportWithParts(SEQUENCE_ID, BigInteger.ONE,
            buildDescriptionModificationReportPart(DescriptionModificationType.UPT,
                buildMds(MDS_HANDLE, BigInteger.ONE, BigInteger.ONE),
                buildVmd(VMD_HANDLE, BigInteger.ONE, BigInteger.ONE),
                buildChannel(CHANNEL_HANDLE, BigInteger.ONE, BigInteger.ONE)));

        final var secondUpdate = buildDescriptionModificationReportWithParts(SEQUENCE_ID, BigInteger.TWO,
            buildDescriptionModificationReportPart(DescriptionModificationType.UPT,
                buildMds(MDS_HANDLE, BigInteger.TWO, BigInteger.TWO),
                buildVmd(VMD_HANDLE, BigInteger.TWO, BigInteger.TWO),
                buildChannel(CHANNEL_HANDLE, BigInteger.TWO, BigInteger.TWO)));

        final var thirdUpdate = buildDescriptionModificationReportWithParts(SEQUENCE_ID, BigInteger.valueOf(3),
            buildDescriptionModificationReportPart(DescriptionModificationType.UPT,
                buildMds(MDS_HANDLE, BigInteger.valueOf(3), BigInteger.valueOf(3)),
                buildVmd(VMD_HANDLE, BigInteger.valueOf(3), BigInteger.valueOf(3))),
            buildDescriptionModificationReportPart(DescriptionModificationType.DEL,
                buildChannel(CHANNEL_HANDLE, BigInteger.TWO, BigInteger.TWO)));

        final var fourthUpdate = buildDescriptionModificationReportWithParts(SEQUENCE_ID, BigInteger.valueOf(4),
            buildDescriptionModificationReportPart(DescriptionModificationType.UPT,
                buildMds(MDS_HANDLE, BigInteger.valueOf(4), BigInteger.valueOf(4)),
                buildVmd(VMD_HANDLE, BigInteger.valueOf(4), BigInteger.valueOf(4))),
            buildDescriptionModificationReportPart(DescriptionModificationType.CRT,
                buildChannel(CHANNEL_HANDLE, BigInteger.ZERO, BigInteger.ZERO)));

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, firstUpdate);
        messageStorageUtil.addInboundSecureHttpMessage(storage, secondUpdate);
        messageStorageUtil.addInboundSecureHttpMessage(storage, thirdUpdate);
        messageStorageUtil.addInboundSecureHttpMessage(storage, fourthUpdate);

        assertThrows(AssertionError.class, testClass::testRequirementR5003);
    }

    /**
     * Tests whether a version which is decremented causes the test to fail.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR5003BadDecrementMdibVersion() throws Exception {
        final var initial = buildMdib(null, BigInteger.ZERO);

        final var firstUpdate = buildDescriptionModificationReportWithParts(SEQUENCE_ID, BigInteger.ONE,
            buildDescriptionModificationReportPart(DescriptionModificationType.UPT,
                buildMds(MDS_HANDLE, BigInteger.ONE, BigInteger.ONE)));

        // decrement mdib version
        final var secondUpdate = buildDescriptionModificationReportWithParts(SEQUENCE_ID, BigInteger.ZERO,
            buildDescriptionModificationReportPart(DescriptionModificationType.UPT,
                buildMds(MDS_HANDLE, BigInteger.TWO, BigInteger.TWO)));

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, firstUpdate);
        messageStorageUtil.addInboundSecureHttpMessage(storage, secondUpdate);

        assertThrows(AssertionError.class, testClass::testRequirementR5003);
    }

    /**
     * Tests whether a descriptor version which is decremented causes the test to fail.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR5003BadDecrementDescriptorVersion() throws Exception {
        final var initial = buildMdib(null, BigInteger.ZERO);

        final var firstUpdate = buildDescriptionModificationReportWithParts(SEQUENCE_ID, BigInteger.ONE,
            buildDescriptionModificationReportPart(DescriptionModificationType.UPT,
                buildMds(MDS_HANDLE, BigInteger.ONE, BigInteger.ONE)));

        // decrement descriptor version
        final var secondUpdate = buildDescriptionModificationReportWithParts(SEQUENCE_ID, BigInteger.TWO,
            buildDescriptionModificationReportPart(DescriptionModificationType.UPT,
                buildMds(MDS_HANDLE, BigInteger.ZERO, BigInteger.TWO)));

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, firstUpdate);
        messageStorageUtil.addInboundSecureHttpMessage(storage, secondUpdate);

        assertThrows(AssertionError.class, testClass::testRequirementR5003);
    }

    /**
     * Tests whether a state version which is decremented causes the test to fail.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR5003BadDecrementStateVersion() throws Exception {
        final var initial = buildMdib(null, BigInteger.ZERO);

        final var firstUpdate = buildDescriptionModificationReportWithParts(SEQUENCE_ID, BigInteger.ONE,
            buildDescriptionModificationReportPart(DescriptionModificationType.UPT,
                buildMds(MDS_HANDLE, BigInteger.ONE, BigInteger.ONE)));

        // decrement state version
        final var secondUpdate = buildDescriptionModificationReportWithParts(SEQUENCE_ID, BigInteger.TWO,
            buildDescriptionModificationReportPart(DescriptionModificationType.UPT,
                buildMds(MDS_HANDLE, BigInteger.TWO, BigInteger.ZERO)));

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, firstUpdate);
        messageStorageUtil.addInboundSecureHttpMessage(storage, secondUpdate);

        assertThrows(AssertionError.class, testClass::testRequirementR5003);
    }

    Envelope buildMdib(
        final @Nullable BigInteger vmdVersion,
        final @Nullable BigInteger mdsVersion) {
        return buildMdib(MdibBuilder.DEFAULT_SEQUENCE_ID, vmdVersion, mdsVersion);
    }

    /*
     * build mdib with configurable handles
     */
    Envelope buildMdib(
        final String sequenceId,
        final @Nullable BigInteger vmdVersion,
        final @Nullable BigInteger mdsVersion) {
        final var mdib = mdibBuilder.buildMinimalMdib(sequenceId);

        final var mdState = mdib.getMdState();
        mdState.setStateVersion(mdsVersion);
        final var mdsDescriptor = mdib.getMdDescription().getMds().get(0);
        mdsDescriptor.setDescriptorVersion(mdsVersion);

        final var vmd = mdibBuilder.buildVmd(VMD_HANDLE);
        vmd.getLeft().setDescriptorVersion(vmdVersion);
        vmd.getRight().setDescriptorVersion(vmdVersion);
        mdsDescriptor.getVmd().add(vmd.getLeft());
        mdState.getState().add(vmd.getRight());

        final var channel = mdibBuilder.buildChannel(CHANNEL_HANDLE);
        channel.getRight().setOperatingHours(0L);
        vmd.getLeft().getChannel().add(channel.getLeft());
        mdState.getState().add(channel.getRight());

        final var metric = mdibBuilder.buildStringMetric(STRING_METRIC_HANDLE,
            MetricCategory.CLC, MetricAvailability.INTR, mdibBuilder.buildCodedValue("abc"));
        metric.getRight().setActivationState(ComponentActivation.OFF);
        metric.getRight().setMetricValue(mdibBuilder.buildStringMetricValue("value"));
        channel.getLeft().setMetric(List.of(metric.getLeft()));
        mdState.getState().add(metric.getRight());

        final var systemContext = mdibBuilder.buildSystemContext(SYSTEM_CONTEXT_HANDLE);
        mdsDescriptor.setSystemContext(systemContext.getLeft());
        mdState.getState().add(systemContext.getRight());

        final var patConDescriptor = mdibBuilder.buildPatientContextDescriptor(PATIENT_CONTEXT_DESCRIPTOR_HANDLE);
        mdsDescriptor.getSystemContext().setPatientContext(patConDescriptor);

        final var getMdibResponse = messageBuilder.buildGetMdibResponse(mdib.getSequenceId());
        getMdibResponse.setMdib(mdib);

        return messageBuilder.createSoapMessageWithBody(
            ActionConstants.getResponseAction(ActionConstants.ACTION_GET_MDIB),
            getMdibResponse
        );
    }

    Pair<? extends AbstractDescriptor, ? extends AbstractState> buildMds(final String handle,
                                                                         @Nullable final BigInteger descriptorVersion,
                                                                         @Nullable final BigInteger stateVersion) {
        final var mds = mdibBuilder.buildMds(handle);
        mds.getLeft().setDescriptorVersion(descriptorVersion);
        mds.getRight().setDescriptorVersion(descriptorVersion);
        if (stateVersion != null) {
            mds.getRight().setStateVersion(stateVersion);
        }
        return mds;
    }

    Pair<? extends AbstractDescriptor, ? extends AbstractState> buildVmd(final String handle,
                                                                         @Nullable final BigInteger descriptorVersion,
                                                                         @Nullable final BigInteger stateVersion) {
        final var vmd = mdibBuilder.buildVmd(handle);
        vmd.getLeft().setDescriptorVersion(descriptorVersion);
        vmd.getRight().setDescriptorVersion(descriptorVersion);
        if (stateVersion != null) {
            vmd.getRight().setStateVersion(stateVersion);
        }
        return vmd;
    }

    Pair<? extends AbstractDescriptor, ? extends AbstractState> buildChannel(final String handle,
                                                                             @Nullable
                                                                             final BigInteger descriptorVersion,
                                                                             @Nullable final BigInteger stateVersion) {
        final var channel = mdibBuilder.buildChannel(handle);
        channel.getLeft().setDescriptorVersion(descriptorVersion);
        channel.getRight().setDescriptorVersion(descriptorVersion);
        if (stateVersion != null) {
            channel.getRight().setStateVersion(stateVersion);
        }
        return channel;
    }

    @SafeVarargs
    final DescriptionModificationReport.ReportPart buildDescriptionModificationReportPart(
        final DescriptionModificationType modificationType,
        final Pair<? extends AbstractDescriptor, ? extends AbstractState>... modifications) {
        final var reportPart = messageBuilder.buildDescriptionModificationReportReportPart();
        reportPart.setModificationType(modificationType);

        for (var modification : modifications) {
            reportPart.getDescriptor().add(modification.getLeft());
            reportPart.getState().add(modification.getRight());
        }
        return reportPart;
    }

    Envelope buildDescriptionModificationReportWithParts(final String sequenceId,
                                                         final @Nullable BigInteger mdibVersion,
                                                         final DescriptionModificationReport.ReportPart... parts) {
        final var report = messageBuilder.buildDescriptionModificationReport(sequenceId, List.of(parts));
        report.setMdibVersion(mdibVersion);
        return messageBuilder.createSoapMessageWithBody(
            ActionConstants.ACTION_DESCRIPTION_MODIFICATION_REPORT,
            report
        );
    }

    Envelope buildDescriptionModificationReport(final String sequenceId,
                                                final @Nullable BigInteger mdibVersion,
                                                final @Nullable BigInteger mdsVersion,
                                                final @Nullable BigInteger vmdVersion,
                                                final @Nullable BigInteger channelVersion,
                                                final @Nullable DescriptionModificationType modificationType) {
        return buildDescriptionModificationReport(
            sequenceId, mdibVersion, mdsVersion, vmdVersion,
            channelVersion, modificationType, false, null, null, null
        );
    }

    Envelope buildDescriptionModificationReport(final String sequenceId,
                                                final @Nullable BigInteger mdibVersion,
                                                final @Nullable BigInteger mdsVersion,
                                                final @Nullable BigInteger vmdVersion,
                                                final @Nullable BigInteger channelVersion,
                                                final @Nullable DescriptionModificationType modificationType,
                                                final boolean deleteChannel) {
        return buildDescriptionModificationReport(sequenceId, mdibVersion, mdsVersion, vmdVersion, channelVersion,
            modificationType, deleteChannel, null, null, null);
    }

    // CHECKSTYLE.OFF: ParameterNumber
    Envelope buildDescriptionModificationReport(final String sequenceId,
                                                final @Nullable BigInteger mdibVersion,
                                                final @Nullable BigInteger mdsVersion,
                                                final @Nullable BigInteger vmdVersion,
                                                final @Nullable BigInteger channelVersion,
                                                final @Nullable DescriptionModificationType modificationType,
                                                final boolean deleteChannel,
                                                final @Nullable BigInteger mdsStateVersion,
                                                final @Nullable BigInteger vmdStateVersion,
                                                final @Nullable BigInteger channelStateVersion) {
        final var reportPart = messageBuilder.buildDescriptionModificationReportReportPart();
        reportPart.setModificationType(Objects.requireNonNullElse(modificationType, DescriptionModificationType.UPT));

        DescriptionModificationReport.ReportPart deletePart = null;

        if (channelVersion != null) {
            final var channel = mdibBuilder.buildChannel(CHANNEL_HANDLE);
            channel.getLeft().setDescriptorVersion(channelVersion);
            channel.getRight().setDescriptorVersion(channelVersion);
            if (channelStateVersion != null) {
                channel.getRight().setStateVersion(channelStateVersion);
            }
            if (deleteChannel) {
                deletePart = messageBuilder.buildDescriptionModificationReportReportPart();
                deletePart.setModificationType(DescriptionModificationType.DEL);

                deletePart.getDescriptor().add(channel.getLeft());
                deletePart.getState().add(channel.getRight());
            } else {
                reportPart.getDescriptor().add(channel.getLeft());
                reportPart.getState().add(channel.getRight());
            }
        }

        if (vmdVersion != null) {
            final var vmd = mdibBuilder.buildVmd(VMD_HANDLE);
            vmd.getLeft().setDescriptorVersion(vmdVersion);
            vmd.getRight().setDescriptorVersion(vmdVersion);
            if (vmdStateVersion != null) {
                vmd.getRight().setStateVersion(vmdStateVersion);
            }
            reportPart.getDescriptor().add(vmd.getLeft());
            reportPart.getState().add(vmd.getRight());
        }

        if (mdsVersion != null) {
            final var mds = mdibBuilder.buildMds(MDS_HANDLE);
            mds.getLeft().setDescriptorVersion(mdsVersion);
            mds.getRight().setDescriptorVersion(mdsVersion);
            if (mdsStateVersion != null) {
                mds.getRight().setStateVersion(mdsStateVersion);
            }
            reportPart.getDescriptor().add(mds.getLeft());
            reportPart.getState().add(mds.getRight());
        }

        final List<DescriptionModificationReport.ReportPart> parts;
        if (deletePart != null) {
            parts = List.of(reportPart, deletePart);
        } else {
            parts = List.of(reportPart);
        }
        final var report = messageBuilder.buildDescriptionModificationReport(sequenceId, parts);
        report.setMdibVersion(mdibVersion);
        return messageBuilder.createSoapMessageWithBody(
            ActionConstants.ACTION_DESCRIPTION_MODIFICATION_REPORT,
            report
        );
    }
    // CHECKSTYLE.ON: ParameterNumber

    Envelope buildEpisodicContextReport(
        final String sequenceId,
        final @Nullable BigInteger mdibVersion,
        final String descriptorHandle,
        final String stateHandle,
        final @Nullable BigInteger stateVersion
    ) {
        final var report = messageBuilder.buildEpisodicContextReport(sequenceId);

        final var patientContextState = mdibBuilder.buildPatientContextState(descriptorHandle, stateHandle);
        patientContextState.setStateVersion(stateVersion);

        final var reportPart = messageBuilder.buildAbstractContextReportReportPart();
        reportPart.getContextState().add(patientContextState);

        report.setMdibVersion(mdibVersion);
        report.getReportPart().add(reportPart);
        return messageBuilder.createSoapMessageWithBody(
            ActionConstants.ACTION_EPISODIC_CONTEXT_REPORT,
            report
        );
    }

    Envelope buildMetricReport(
        final String sequenceId,
        final @Nullable BigInteger mdibVersion,
        final @Nullable BigInteger metricVersion,
        final String metricHandle
    ) {
        final var report = messageBuilder.buildEpisodicMetricReport(sequenceId);

        final var metricState = mdibBuilder.buildStringMetricState(metricHandle);
        metricState.setStateVersion(metricVersion);

        final var reportPart = messageBuilder.buildAbstractMetricReportReportPart();
        reportPart.getMetricState().add(metricState);

        report.setMdibVersion(mdibVersion);
        report.getReportPart().add(reportPart);
        return messageBuilder.createSoapMessageWithBody(
            ActionConstants.ACTION_EPISODIC_METRIC_REPORT,
            report
        );
    }

    Envelope buildEpisodicAlertReport(
        final String sequenceId,
        final @Nullable BigInteger mdibVersion,
        final @Nullable BigInteger stateVersion,
        final AbstractAlertState... alertStates
    ) {
        final var report = messageBuilder.buildEpisodicAlertReport(sequenceId);

        final var reportPart = messageBuilder.buildAbstractAlertReportReportPart();
        for (var alertState : alertStates) {
            alertState.setStateVersion(stateVersion);
            reportPart.getAlertState().add(alertState);
        }

        report.setMdibVersion(mdibVersion);
        report.getReportPart().add(reportPart);
        return messageBuilder.createSoapMessageWithBody(
            ActionConstants.ACTION_EPISODIC_ALERT_REPORT,
            report
        );
    }

    interface DescriptorModification<T> {
        void modify(T a);
    }

    interface DescriptorModificationWithValue<T, V> {
        void modify(T a, V v);
    }
}


