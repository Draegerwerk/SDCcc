/*
 * This Source Code Form is subject to the terms of the "SDCcc non-commercial use license".
 *
 * Copyright (C) 2025 Draegerwerk AG & Co. KGaA
 */

package com.draeger.medical.sdccc.tests.glue.invariant;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.draeger.medical.biceps.model.message.DescriptionModificationType;
import com.draeger.medical.biceps.model.participant.AbstractDescriptor;
import com.draeger.medical.biceps.model.participant.AbstractState;
import com.draeger.medical.biceps.model.participant.AlertActivation;
import com.draeger.medical.biceps.model.participant.AlertConditionDescriptor;
import com.draeger.medical.biceps.model.participant.AlertConditionKind;
import com.draeger.medical.biceps.model.participant.AlertConditionPriority;
import com.draeger.medical.biceps.model.participant.AlertConditionState;
import com.draeger.medical.biceps.model.participant.AlertSignalManifestation;
import com.draeger.medical.biceps.model.participant.ChannelDescriptor;
import com.draeger.medical.biceps.model.participant.ChannelState;
import com.draeger.medical.biceps.model.participant.CodedValue;
import com.draeger.medical.biceps.model.participant.MetricAvailability;
import com.draeger.medical.biceps.model.participant.MetricCategory;
import com.draeger.medical.biceps.model.participant.OperatingMode;
import com.draeger.medical.biceps.model.participant.SetStringOperationDescriptor;
import com.draeger.medical.biceps.model.participant.SetStringOperationState;
import com.draeger.medical.biceps.model.participant.StringMetricDescriptor;
import com.draeger.medical.biceps.model.participant.StringMetricState;
import com.draeger.medical.biceps.model.participant.VmdDescriptor;
import com.draeger.medical.biceps.model.participant.VmdState;
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
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nullable;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.somda.sdc.dpws.helper.JaxbMarshalling;
import org.somda.sdc.dpws.soap.SoapMarshalling;
import org.somda.sdc.glue.common.ActionConstants;

/**
 * Unit test for the GLUE {@linkplain InvariantSdcParticipantModelBindingTest}.
 */
public class InvariantSdcParticipantModelBindingTestTest {
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    private static final String VMD_HANDLE = "vmdHandle";
    private static final String VMD_ALERT_SYSTEM_HANDLE = "vmdAlertSystemHandle";
    private static final String VMD_ALERT_CONDITION_HANDLE = "vmdAlertConditionHandle";
    private static final String VMD_ALERT_SIGNAL_HANDLE = "vmdAlertSignalHandle";
    private static final String CHANNEL_HANDLE = "channelHandle";
    private static final String STRING_METRIC_HANDLE = "stringMetricHandle";
    private static final String SET_STRING_OPERATION_HANDLE = "setStringOperationHandle";

    private static MessageStorageUtil messageStorageUtil;
    private static MdibBuilder mdibBuilder;
    private static MessageBuilder messageBuilder;
    private InvariantSdcParticipantModelBindingTest testClass;
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

        testClass = new InvariantSdcParticipantModelBindingTest();
        testClass.setup();
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
    public void testRequirementR0080NoTestData() {
        assertThrows(NoTestData.class, testClass::testRequirementR0080);
    }

    /**
     * Tests whether every AbstractComplexDeviceComponentDescriptor, ChannelDescriptor, AbstractOperationDescriptor,
     * AlertConditionDescriptor and AbstractMetricDescriptor having a type attribute causes the test to pass.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR0080Good() throws Exception {
        final var initial = buildMdib(MdibBuilder.DEFAULT_SEQUENCE_ID, BigInteger.ZERO);
        final var first = buildDescriptionModificationReport(
                MdibBuilder.DEFAULT_SEQUENCE_ID,
                BigInteger.ONE,
                DescriptionModificationType.UPT,
                buildAlertCondition(VMD_ALERT_CONDITION_HANDLE, mdibBuilder.buildCodedValue("1")),
                buildStringOperation(
                        SET_STRING_OPERATION_HANDLE,
                        STRING_METRIC_HANDLE,
                        OperatingMode.EN,
                        mdibBuilder.buildCodedValue("2")),
                buildStringMetric(
                        STRING_METRIC_HANDLE,
                        MetricCategory.MSRMT,
                        MetricAvailability.CONT,
                        mdibBuilder.buildCodedValue("stringMetricValue"),
                        mdibBuilder.buildCodedValue("3")),
                buildChannel(CHANNEL_HANDLE, mdibBuilder.buildCodedValue("4")),
                buildVmd(VMD_HANDLE, mdibBuilder.buildCodedValue("5")));

        final var second = buildDescriptionModificationReport(
                MdibBuilder.DEFAULT_SEQUENCE_ID,
                BigInteger.TWO,
                DescriptionModificationType.UPT,
                buildAlertCondition(VMD_ALERT_CONDITION_HANDLE, mdibBuilder.buildCodedValue("vmdCodedValue")));

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, first);
        messageStorageUtil.addInboundSecureHttpMessage(storage, second);

        testClass.testRequirementR0080();
    }

    /**
     * Tests whether one AbstractComplexDeviceComponentDescriptor, ChannelDescriptor, AbstractOperationDescriptor,
     * AlertConditionDescriptor or AbstractMetricDescriptor missing a type attribute causes the test to fail.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR0080Bad() throws Exception {
        final var initial = buildMdib(MdibBuilder.DEFAULT_SEQUENCE_ID, BigInteger.ZERO);
        final var first = buildDescriptionModificationReport(
                MdibBuilder.DEFAULT_SEQUENCE_ID,
                BigInteger.ONE,
                DescriptionModificationType.UPT,
                buildAlertCondition(VMD_ALERT_CONDITION_HANDLE, null));

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, first);

        assertThrows(AssertionError.class, testClass::testRequirementR0080);
    }

    private Pair<VmdDescriptor, VmdState> buildVmd(final String handle, @Nullable final CodedValue type) {
        final var vmd = mdibBuilder.buildVmd(handle);
        vmd.getLeft().setType(type);
        return vmd;
    }

    private Pair<ChannelDescriptor, ChannelState> buildChannel(final String handle, @Nullable final CodedValue type) {
        final var channel = mdibBuilder.buildChannel(handle);
        channel.getLeft().setType(type);
        return channel;
    }

    private Pair<SetStringOperationDescriptor, SetStringOperationState> buildStringOperation(
            final String handle,
            final String target,
            final OperatingMode operatingMode,
            @Nullable final CodedValue type) {
        final var operation = mdibBuilder.buildSetStringOperation(handle, target, operatingMode);
        operation.getLeft().setType(type);
        return operation;
    }

    private Pair<AlertConditionDescriptor, AlertConditionState> buildAlertCondition(
            final String handle, @Nullable final CodedValue type) {
        final var alertCondition = mdibBuilder.buildAlertCondition(
                handle, AlertConditionKind.PHY, AlertConditionPriority.HI, AlertActivation.ON);
        alertCondition.getLeft().setType(type);
        return alertCondition;
    }

    private Pair<StringMetricDescriptor, StringMetricState> buildStringMetric(
            final String handle,
            final MetricCategory category,
            final MetricAvailability availability,
            final CodedValue unit,
            @Nullable final CodedValue codedValue) {
        final var metric = mdibBuilder.buildStringMetric(handle, category, availability, unit);
        metric.getLeft().setType(codedValue);
        return metric;
    }

    Envelope buildMdib(final String sequenceId, final @Nullable BigInteger mdsVersion) {
        final var mdib = mdibBuilder.buildMinimalMdib(sequenceId);

        final var mdState = mdib.getMdState();
        final var mdsDescriptor = mdib.getMdDescription().getMds().get(0);
        mdsDescriptor.setDescriptorVersion(mdsVersion);
        mdsDescriptor.setType(mdibBuilder.buildCodedValue("mdsCodedValue"));

        final var vmdAlertCondition = mdibBuilder.buildAlertCondition(
                VMD_ALERT_CONDITION_HANDLE, AlertConditionKind.OTH, AlertConditionPriority.ME, AlertActivation.ON);
        final var vmdAlertSignal = mdibBuilder.buildAlertSignal(
                VMD_ALERT_SIGNAL_HANDLE, AlertSignalManifestation.OTH, false, AlertActivation.OFF);

        final var vmdAlertSystem = mdibBuilder.buildAlertSystem(VMD_ALERT_SYSTEM_HANDLE, AlertActivation.ON);
        vmdAlertCondition.getLeft().setType(mdibBuilder.buildCodedValue("alertConditionCodedValue"));
        vmdAlertSignal.getLeft().setType(mdibBuilder.buildCodedValue("alertSignalCodedValue"));
        vmdAlertSystem.getLeft().getAlertCondition().add(vmdAlertCondition.getLeft());
        vmdAlertSystem.getLeft().getAlertSignal().add(vmdAlertSignal.getLeft());
        mdState.getState()
                .addAll(List.of(vmdAlertSystem.getRight(), vmdAlertCondition.getRight(), vmdAlertSignal.getRight()));

        final var vmd = mdibBuilder.buildVmd(VMD_HANDLE);
        vmd.getLeft().setAlertSystem(vmdAlertSystem.getLeft());
        vmd.getLeft().setType(mdibBuilder.buildCodedValue("vmdCodedValue"));
        mdsDescriptor.getVmd().add(vmd.getLeft());
        mdState.getState().add(vmd.getRight());

        final var channel = mdibBuilder.buildChannel(CHANNEL_HANDLE);

        final var metric = mdibBuilder.buildStringMetric(
                STRING_METRIC_HANDLE,
                MetricCategory.MSRMT,
                MetricAvailability.CONT,
                mdibBuilder.buildCodedValue("stringCodedValue"));
        channel.getLeft().getMetric().add(metric.getLeft());
        mdState.getState().addAll(List.of(channel.getRight(), metric.getRight()));

        final var sco = mdibBuilder.buildSco("someSco");
        vmd.getLeft().setSco(sco.getLeft());
        mdState.getState().add(sco.getRight());

        final var setStringOperation = mdibBuilder.buildSetStringOperation(
                SET_STRING_OPERATION_HANDLE, STRING_METRIC_HANDLE, OperatingMode.DIS);
        setStringOperation.getLeft().setType(mdibBuilder.buildCodedValue("operationCodedValue"));
        sco.getLeft().getOperation().add(setStringOperation.getLeft());
        mdState.getState().add(setStringOperation.getRight());

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
