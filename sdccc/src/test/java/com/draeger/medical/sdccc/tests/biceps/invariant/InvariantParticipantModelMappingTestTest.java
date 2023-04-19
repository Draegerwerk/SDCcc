/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2023 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.tests.biceps.invariant;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.draeger.medical.biceps.model.message.DescriptionModificationReport;
import com.draeger.medical.biceps.model.message.DescriptionModificationType;
import com.draeger.medical.biceps.model.participant.ChannelDescriptor;
import com.draeger.medical.biceps.model.participant.ChannelState;
import com.draeger.medical.biceps.model.participant.MdsDescriptor;
import com.draeger.medical.biceps.model.participant.MdsState;
import com.draeger.medical.biceps.model.participant.VmdDescriptor;
import com.draeger.medical.biceps.model.participant.VmdState;
import com.draeger.medical.dpws.soap.model.Envelope;
import com.draeger.medical.sdccc.configuration.TestSuiteConfig;
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
import com.google.inject.Key;
import com.google.inject.name.Names;
import java.io.IOException;
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
 * Unit test for the BICEPS {@linkplain InvariantParticipantModelMappingTest}.
 */
public class InvariantParticipantModelMappingTestTest {
    private static final String VMD_HANDLE = "someVmd";
    private static final String CHANNEL_HANDLE = "someChannel";
    private static final String NONEXISTENT_CHANNEL_HANDLE = "someNonexistentChannel";
    private static final String MDS_HANDLE = MdibBuilder.DEFAULT_MDS_HANDLE;
    private static final String SEQUENCE_ID = MdibBuilder.DEFAULT_SEQUENCE_ID;
    private static final String PATIENT_CONTEXT_DESCRIPTOR_HANDLE = "somePatientDescriptor";

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);
    private static MessageStorageUtil messageStorageUtil;
    private static MdibBuilder mdibBuilder;
    private static MessageBuilder messageBuilder;
    private MessageStorage storage;
    private InvariantParticipantModelMappingTest testClass;
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

        final Injector injector = InjectorUtil.setupInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(TestClient.class).toInstance(mockClient);
                bind(Key.get(Boolean.class, Names.named(TestSuiteConfig.SUMMARIZE_MESSAGE_ENCODING_ERRORS)))
                        .toInstance(true);
            }
        });
        InjectorTestBase.setInjector(injector);

        riInjector = TestClientUtil.createClientInjector();
        when(mockClient.getInjector()).thenReturn(riInjector);

        baseMarshalling = riInjector.getInstance(JaxbMarshalling.class);
        baseMarshalling.startAsync().awaitRunning(DEFAULT_TIMEOUT);

        marshalling = riInjector.getInstance(SoapMarshalling.class);
        marshalling.startAsync().awaitRunning(DEFAULT_TIMEOUT);

        storage = injector.getInstance(MessageStorage.class);

        testClass = new InvariantParticipantModelMappingTest();
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
    public void testRequirementR0023NoTestData() {
        assertThrows(NoTestData.class, testClass::testRequirementR0023);
    }

    /**
     * Tests if org.somda.sdc.biceps.common.CommonConfig.ALLOW_STATES_WITHOUT_DESCRIPTORS is set to false,
     * causing this test to fail.
     *
     * @throws Exception on any Exception
     */
    @Test
    public void testStatesWithoutDescriptorFails() throws Exception {
        final var patHandle1 = "pat1";

        final var initial = buildMdib(true, null, BigInteger.ZERO);

        final var firstUpdate = buildEpisodicContextReport(
                SEQUENCE_ID, BigInteger.ONE, PATIENT_CONTEXT_DESCRIPTOR_HANDLE, patHandle1, null);

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, firstUpdate);

        final var error = assertThrows(AssertionError.class, () -> testClass.testRequirementR0023());
        assertTrue(error.getCause() instanceof RuntimeException);
    }

    /**
     * Tests whether states which reference their descriptors passes the test.
     *
     * @throws Exception on any Exception
     */
    @Test
    public void testRequirementR0023Good() throws Exception {
        final var initial = buildMdib(true, null, BigInteger.ZERO);

        final var firstUpdate = buildDescriptionModificationReport(
                SEQUENCE_ID,
                BigInteger.ONE,
                buildMds(MDS_HANDLE, BigInteger.ONE),
                buildVmd(VMD_HANDLE, BigInteger.ONE),
                buildChannel(CHANNEL_HANDLE, BigInteger.ONE),
                null);
        final var secondUpdate = buildDescriptionModificationReport(
                SEQUENCE_ID,
                BigInteger.TWO,
                buildMds(MDS_HANDLE, BigInteger.TWO),
                buildVmd(VMD_HANDLE, BigInteger.TWO),
                buildChannel(CHANNEL_HANDLE, BigInteger.ONE),
                null,
                true,
                false);
        final var three = BigInteger.valueOf(3);
        final var thirdUpdate = buildDescriptionModificationReport(
                SEQUENCE_ID,
                three,
                buildMds(MDS_HANDLE, three),
                buildVmd(VMD_HANDLE, three),
                buildChannel(CHANNEL_HANDLE, BigInteger.TWO),
                null);

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, firstUpdate);
        messageStorageUtil.addInboundSecureHttpMessage(storage, secondUpdate);
        messageStorageUtil.addInboundSecureHttpMessage(storage, thirdUpdate);

        testClass.testRequirementR0023();
    }

    /**
     * Tests whether states which reference their descriptors violating the naming scheme fails the test.
     *
     * @throws Exception on any Exception
     */
    @Test
    public void testRequirementR0023BadNamingScheme() throws Exception {
        final var initial = buildMdib(true, null, BigInteger.ZERO);

        final var firstUpdate = buildDescriptionModificationReport(
                SEQUENCE_ID,
                BigInteger.ONE,
                buildMds(MDS_HANDLE, BigInteger.ONE),
                buildVmd(VMD_HANDLE, BigInteger.ONE),
                buildChannel(CHANNEL_HANDLE, BigInteger.ONE),
                null,
                false,
                true);

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, firstUpdate);

        assertThrows(AssertionError.class, () -> testClass.testRequirementR0023());
    }

    /**
     * Tests whether states which reference nonexistent descriptors fails the test.
     *
     * @throws Exception on any Exception
     */
    @Test
    public void testRequirementR0023BadWrongDescriptorHandle() throws Exception {
        final var initial = buildMdib(true, null, BigInteger.ZERO);

        final var firstUpdate = buildDescriptionModificationReport(
                SEQUENCE_ID,
                BigInteger.ONE,
                buildMds(MDS_HANDLE, BigInteger.ONE),
                buildVmd(VMD_HANDLE, BigInteger.ONE),
                buildBrokenDescriptorHandleChannel(CHANNEL_HANDLE, NONEXISTENT_CHANNEL_HANDLE, BigInteger.ONE),
                null);

        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);
        messageStorageUtil.addInboundSecureHttpMessage(storage, firstUpdate);

        final var error = assertThrows(AssertionError.class, () -> testClass.testRequirementR0023());
        assertTrue(error.getCause().getMessage().contains(NONEXISTENT_CHANNEL_HANDLE));
    }

    Envelope buildMdib(
            final boolean includeChannel,
            final @Nullable BigInteger vmdVersion,
            final @Nullable BigInteger mdsVersion) {
        return buildMdib(MdibBuilder.DEFAULT_SEQUENCE_ID, includeChannel, vmdVersion, mdsVersion);
    }

    /*
     * build mdib with configurable handles
     */
    Envelope buildMdib(
            final String sequenceId,
            final boolean includeChannel,
            final @Nullable BigInteger vmdVersion,
            final @Nullable BigInteger mdsVersion) {
        final var mdib = mdibBuilder.buildMinimalMdib(sequenceId);

        final var mdState = mdib.getMdState();
        final var mdsDescriptor = mdib.getMdDescription().getMds().get(0);
        mdsDescriptor.setDescriptorVersion(mdsVersion);

        final var vmd = mdibBuilder.buildVmd(VMD_HANDLE);
        vmd.getLeft().setDescriptorVersion(vmdVersion);
        vmd.getRight().setDescriptorVersion(vmdVersion);
        mdsDescriptor.getVmd().add(vmd.getLeft());
        mdState.getState().add(vmd.getRight());

        if (includeChannel) {
            final var channel = mdibBuilder.buildChannel(CHANNEL_HANDLE);
            vmd.getLeft().getChannel().add(channel.getLeft());
            mdState.getState().add(channel.getRight());
        }

        final var getMdibResponse = messageBuilder.buildGetMdibResponse(mdib.getSequenceId());
        getMdibResponse.setMdib(mdib);

        return messageBuilder.createSoapMessageWithBody(
                ActionConstants.getResponseAction(ActionConstants.ACTION_GET_MDIB), getMdibResponse);
    }

    Envelope buildDescriptionModificationReport(
            final String sequenceId,
            final @Nullable BigInteger mdibVersion,
            final @Nullable Pair<MdsDescriptor, MdsState> mds,
            final @Nullable Pair<VmdDescriptor, VmdState> vmd,
            final @Nullable Pair<ChannelDescriptor, ChannelState> channel,
            final @Nullable DescriptionModificationType modificationType) {
        return buildDescriptionModificationReport(
                sequenceId, mdibVersion, mds, vmd, channel, modificationType, false, false);
    }

    Envelope buildDescriptionModificationReport(
            final String sequenceId,
            final @Nullable BigInteger mdibVersion,
            final @Nullable Pair<MdsDescriptor, MdsState> mds,
            final @Nullable Pair<VmdDescriptor, VmdState> vmd,
            final @Nullable Pair<ChannelDescriptor, ChannelState> channel,
            final @Nullable DescriptionModificationType modificationType,
            final boolean deleteChannel,
            final boolean brokenChannel) {
        final var reportPart = messageBuilder.buildDescriptionModificationReportReportPart();
        reportPart.setModificationType(Objects.requireNonNullElse(modificationType, DescriptionModificationType.UPT));

        DescriptionModificationReport.ReportPart deletePart = null;

        if (channel != null && brokenChannel) {
            final var channelBrokenNameScheme = buildBrokenNameSchemaChannel(
                    CHANNEL_HANDLE, channel.getLeft().getDescriptorVersion());
            if (deleteChannel) {
                deletePart = messageBuilder.buildDescriptionModificationReportReportPart();
                deletePart.setModificationType(DescriptionModificationType.DEL);

                deletePart.getDescriptor().add(channelBrokenNameScheme.getLeft());
                deletePart.getState().add(channelBrokenNameScheme.getRight());
            } else {
                reportPart.getDescriptor().add(channelBrokenNameScheme.getLeft());
                reportPart.getState().add(channelBrokenNameScheme.getRight());
            }
        } else if (channel != null) {
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

        if (vmd != null) {
            reportPart.getDescriptor().add(vmd.getLeft());
            reportPart.getState().add(vmd.getRight());
        }

        if (mds != null) {
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
        return messageBuilder.createSoapMessageWithBody(ActionConstants.ACTION_DESCRIPTION_MODIFICATION_REPORT, report);
    }

    Pair<VmdDescriptor, ChannelState> buildBrokenNameSchemaChannel(
            final String channelHandel, final BigInteger channelVersion) {
        final var vmdDescriptor = mdibBuilder.buildVmdDescriptor(channelHandel);
        final var channelState = mdibBuilder.buildChannelState(channelHandel);
        final Pair<VmdDescriptor, ChannelState> channel = new ImmutablePair<>(vmdDescriptor, channelState);
        channel.getLeft().setDescriptorVersion(channelVersion);
        channel.getRight().setDescriptorVersion(channelVersion);
        return channel;
    }

    Pair<ChannelDescriptor, ChannelState> buildBrokenDescriptorHandleChannel(
            final String channelDescriptorHandel, final String channelStateHandle, final BigInteger channelVersion) {
        final var channelDescriptor = mdibBuilder.buildChannelDescriptor(channelDescriptorHandel);
        final var channelState = mdibBuilder.buildChannelState(channelStateHandle);
        final Pair<ChannelDescriptor, ChannelState> channel = new ImmutablePair<>(channelDescriptor, channelState);
        channel.getLeft().setDescriptorVersion(channelVersion);
        channel.getRight().setDescriptorVersion(channelVersion);
        return channel;
    }

    Pair<ChannelDescriptor, ChannelState> buildChannel(final String channelHandle, final BigInteger channelVersion) {
        final var channel = mdibBuilder.buildChannel(channelHandle);
        channel.getLeft().setDescriptorVersion(channelVersion);
        channel.getRight().setDescriptorVersion(channelVersion);
        return channel;
    }

    Pair<VmdDescriptor, VmdState> buildVmd(final String vmdHandle, final BigInteger vmdVersion) {
        final var vmd = mdibBuilder.buildVmd(vmdHandle);
        vmd.getLeft().setDescriptorVersion(vmdVersion);
        vmd.getRight().setDescriptorVersion(vmdVersion);
        return vmd;
    }

    Pair<MdsDescriptor, MdsState> buildMds(final String mdsHandle, final BigInteger mdsVersion) {
        final var mds = mdibBuilder.buildMds(mdsHandle);
        mds.getLeft().setDescriptorVersion(mdsVersion);
        mds.getRight().setDescriptorVersion(mdsVersion);
        return mds;
    }

    Envelope buildEpisodicContextReport(
            final String sequenceId,
            final @Nullable BigInteger mdibVersion,
            final String descriptorHandle,
            final String stateHandle,
            final @Nullable BigInteger stateVersion) {
        final var report = messageBuilder.buildEpisodicContextReport(sequenceId);

        final var patientContextState = mdibBuilder.buildPatientContextState(descriptorHandle, stateHandle);
        patientContextState.setStateVersion(stateVersion);

        final var reportPart = messageBuilder.buildAbstractContextReportReportPart();
        reportPart.getContextState().add(patientContextState);

        report.setMdibVersion(mdibVersion);
        report.getReportPart().add(reportPart);
        return messageBuilder.createSoapMessageWithBody(ActionConstants.ACTION_EPISODIC_CONTEXT_REPORT, report);
    }
}
