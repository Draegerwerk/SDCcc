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

import com.draeger.medical.biceps.model.message.GetMdibResponse;
import com.draeger.medical.biceps.model.participant.AbstractMetricDescriptor;
import com.draeger.medical.biceps.model.participant.AbstractMetricState;
import com.draeger.medical.biceps.model.participant.AbstractState;
import com.draeger.medical.biceps.model.participant.AlertActivation;
import com.draeger.medical.biceps.model.participant.AlertConditionDescriptor;
import com.draeger.medical.biceps.model.participant.AlertConditionKind;
import com.draeger.medical.biceps.model.participant.AlertConditionPriority;
import com.draeger.medical.biceps.model.participant.AlertConditionState;
import com.draeger.medical.biceps.model.participant.AlertSystemDescriptor;
import com.draeger.medical.biceps.model.participant.AlertSystemState;
import com.draeger.medical.biceps.model.participant.ChannelDescriptor;
import com.draeger.medical.biceps.model.participant.ChannelState;
import com.draeger.medical.biceps.model.participant.CodedValue;
import com.draeger.medical.biceps.model.participant.ComponentActivation;
import com.draeger.medical.biceps.model.participant.Mdib;
import com.draeger.medical.biceps.model.participant.MdsDescriptor;
import com.draeger.medical.biceps.model.participant.MdsState;
import com.draeger.medical.biceps.model.participant.MetricAvailability;
import com.draeger.medical.biceps.model.participant.MetricCategory;
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
import java.math.BigInteger;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.somda.sdc.dpws.helper.JaxbMarshalling;
import org.somda.sdc.dpws.soap.SoapMarshalling;
import org.somda.sdc.glue.common.ActionConstants;

/**
 * Unit test for the BICEPS {@linkplain InvariantDeviceComponentStateTest}.
 */
public class InvariantDeviceComponentStateTestTest {
    private static final String VMD_HANDLE = "someVmd";
    private static final String SEQUENCE_ID = MdibBuilder.DEFAULT_SEQUENCE_ID;

    private static final String MDS_HANDLE = "mds";
    private static final String METRIC_HANDLE = "s_metric";
    private static final String CHANNEL_HANDLE = "channel";
    private static final String ALERT_SYSTEM_HANDLE = "alertSystem";
    private static final String ALERT_CONDITION_HANDLE = "alertConditionHandle";

    private static MessageStorageUtil messageStorageUtil;
    private static MdibBuilder mdibBuilder;
    private static MessageBuilder messageBuilder;
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    private MessageStorage storage;
    private InvariantDeviceComponentStateTest testClass;
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

        testClass = new InvariantDeviceComponentStateTest();
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
    public void testRequirementR00250NoTestData() {
        assertThrows(NoTestData.class, testClass::testRequirementR00250);
    }

    /**
     * Tests whether having a MDS with ON that contains only OFF and NA passes the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR00250Good() throws Exception {
        final var initial = buildMdib();

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, buildPassReport());

        testClass.testRequirementR00250();
    }

    /**
     * Tests whether having a CHANNEL with ON and all others being OFF or NA fails the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR00250Bad() throws Exception {
        final var initial = buildMdib();

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, buildFailureReport());

        assertThrows(AssertionError.class, testClass::testRequirementR00250);
    }

    /**
     * Tests whether having a METRIC with ON in a MDS with OFF fails the test even when the ActivationStates
     * in between are not set.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR00250BadDescendants() throws Exception {
        final var initial = buildMdibWithoutActivationStates();
        setActivationStates(
                initial,
                ComponentActivation.OFF,
                ComponentActivation.OFF,
                ComponentActivation.OFF,
                ComponentActivation.OFF,
                AlertActivation.OFF,
                AlertActivation.ON);
        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, buildPassReport());

        assertThrows(AssertionError.class, testClass::testRequirementR00250);
    }

    private Envelope buildMdib() {
        final Envelope result = buildMdibWithoutActivationStates();
        setActivationStates(
                result,
                ComponentActivation.OFF,
                ComponentActivation.OFF,
                ComponentActivation.OFF,
                ComponentActivation.OFF,
                AlertActivation.OFF,
                AlertActivation.OFF);
        return result;
    }

    private void setActivationStates(
            final Envelope result,
            final @Nullable ComponentActivation mdsActivationState,
            final @Nullable ComponentActivation vmdActivationState,
            final @Nullable ComponentActivation channelActivationState,
            final @Nullable ComponentActivation metricActivationState,
            final @Nullable AlertActivation alertSystemActivationState,
            final @Nullable AlertActivation alertConditionActivationState) {
        final GetMdibResponse getMdibResponse =
                (GetMdibResponse) result.getBody().getAny().get(0);
        final Mdib mdib = getMdibResponse.getMdib();

        final MdsDescriptor mdsDesc = mdib.getMdDescription().getMds().get(0);
        final AlertSystemDescriptor alertSystemDesc = mdsDesc.getAlertSystem();
        final AlertConditionDescriptor alertConditionDesc =
                alertSystemDesc.getAlertCondition().get(0);
        final VmdDescriptor vmdDesc = mdsDesc.getVmd().get(0);
        final ChannelDescriptor channelDesc = vmdDesc.getChannel().get(0);
        final AbstractMetricDescriptor metricDesc = channelDesc.getMetric().get(0);

        final MdsState mdsState = getState(mdib, mdsDesc.getHandle(), MdsState.class);
        final AlertSystemState alertSystemState = getState(mdib, alertSystemDesc.getHandle(), AlertSystemState.class);
        final AlertConditionState alertConditionState =
                getState(mdib, alertConditionDesc.getHandle(), AlertConditionState.class);
        final VmdState vmdState = getState(mdib, vmdDesc.getHandle(), VmdState.class);
        final ChannelState channelState = getState(mdib, channelDesc.getHandle(), ChannelState.class);
        final AbstractMetricState metricState = getState(mdib, metricDesc.getHandle(), AbstractMetricState.class);

        if (mdsActivationState != null) {
            mdsState.setActivationState(mdsActivationState);
        }
        if (vmdActivationState != null) {
            vmdState.setActivationState(vmdActivationState);
        }
        if (channelActivationState != null) {
            channelState.setActivationState(channelActivationState);
        }
        if (metricActivationState != null) {
            metricState.setActivationState(metricActivationState);
        }
        if (alertSystemActivationState != null) {
            alertSystemState.setActivationState(alertSystemActivationState);
        }
        if (alertConditionActivationState != null) {
            alertConditionState.setActivationState(alertConditionActivationState);
        }
    }

    private <T> T getState(final Mdib mdib, final String handle, final Class<T> type) {
        final AbstractState state = mdib.getMdState().getState().stream()
                .filter(e -> handle.equals(e.getDescriptorHandle()))
                .collect(Collectors.toList())
                .get(0);
        return (T) state;
    }

    private Envelope buildMdibWithoutActivationStates() {

        final AlertSystemDescriptor alertSystem = mdibBuilder.buildAlertSystemDescriptor(ALERT_SYSTEM_HANDLE);
        final AlertSystemState alertSystemState =
                mdibBuilder.buildAlertSystemState(ALERT_SYSTEM_HANDLE, AlertActivation.ON);

        final AlertConditionDescriptor alertCondition = mdibBuilder.buildAlertConditionDescriptor(
                ALERT_CONDITION_HANDLE, AlertConditionKind.PHY, AlertConditionPriority.HI);
        final AlertConditionState alertConditionState =
                mdibBuilder.buildAlertConditionState(ALERT_CONDITION_HANDLE, AlertActivation.ON);
        alertSystem.getAlertCondition().clear();
        alertSystem.getAlertCondition().add(alertCondition);

        final var mds = mdibBuilder.buildMds(MDS_HANDLE);
        mds.getLeft().setAlertSystem(alertSystem);

        final var mdDescription = mdibBuilder.buildMdDescription();
        final var mdState = mdibBuilder.buildMdState();

        mdDescription.getMds().clear();
        mdDescription.getMds().add(mds.getLeft());
        mdState.getState().clear();
        mdState.getState().addAll(List.of(mds.getRight(), alertSystemState, alertConditionState));

        final var mdib = mdibBuilder.buildMdib(SEQUENCE_ID);
        mdib.setMdDescription(mdDescription);
        mdib.setMdState(mdState);

        mds.getLeft().setDescriptorVersion(BigInteger.ZERO);

        final var unit = new CodedValue();
        unit.setCode("1");
        final var metric =
                mdibBuilder.buildStringMetric(METRIC_HANDLE, MetricCategory.SET, MetricAvailability.INTR, unit);
        final var channel = mdibBuilder.buildChannel(CHANNEL_HANDLE);
        final var vmd = mdibBuilder.buildVmd(VMD_HANDLE);
        vmd.getLeft().setDescriptorVersion(BigInteger.ZERO);
        vmd.getRight().setDescriptorVersion(BigInteger.ZERO);
        channel.getLeft().getMetric().clear();
        channel.getLeft().getMetric().add(metric.getLeft());
        mds.getLeft().getVmd().add(vmd.getLeft());
        vmd.getLeft().getChannel().clear();
        vmd.getLeft().getChannel().add(channel.getLeft());
        mdState.getState().add(vmd.getRight());
        mdState.getState().add(metric.getRight());
        mdState.getState().add(channel.getRight());

        final var getMdibResponse = messageBuilder.buildGetMdibResponse(mdib.getSequenceId());
        getMdibResponse.setMdib(mdib);

        return messageBuilder.createSoapMessageWithBody(
                ActionConstants.getResponseAction(ActionConstants.ACTION_GET_MDIB), getMdibResponse);
    }

    private Envelope buildFailureReport() {
        final var report = messageBuilder.buildEpisodicComponentReport(SEQUENCE_ID);

        final var reportPart = messageBuilder.buildAbstractComponentReportReportPart();

        final var channel = mdibBuilder.buildChannel(CHANNEL_HANDLE);
        channel.getRight().setActivationState(ComponentActivation.ON);
        channel.getRight().setStateVersion(BigInteger.ONE);
        reportPart.getComponentState().add(channel.getRight());

        report.setMdibVersion(BigInteger.ONE);
        report.getReportPart().add(reportPart);
        return messageBuilder.createSoapMessageWithBody(ActionConstants.ACTION_EPISODIC_COMPONENT_REPORT, report);
    }

    private Envelope buildPassReport() {
        final var report = messageBuilder.buildEpisodicComponentReport(SEQUENCE_ID);

        final var reportPart = messageBuilder.buildAbstractComponentReportReportPart();

        final var mds = mdibBuilder.buildMds(MDS_HANDLE);
        mds.getRight().setStateVersion(BigInteger.ONE);
        mds.getRight().setActivationState(ComponentActivation.ON);
        reportPart.getComponentState().add(mds.getRight());

        report.setMdibVersion(BigInteger.ONE);
        report.getReportPart().add(reportPart);
        return messageBuilder.createSoapMessageWithBody(ActionConstants.ACTION_EPISODIC_COMPONENT_REPORT, report);
    }
}
