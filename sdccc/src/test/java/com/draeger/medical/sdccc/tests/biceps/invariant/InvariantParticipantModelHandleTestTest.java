/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2023 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.tests.biceps.invariant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.draeger.medical.biceps.model.message.AbstractContextReport;
import com.draeger.medical.biceps.model.message.DescriptionModificationReport;
import com.draeger.medical.biceps.model.message.DescriptionModificationType;
import com.draeger.medical.biceps.model.participant.AbstractDescriptor;
import com.draeger.medical.biceps.model.participant.AlertSignalManifestation;
import com.draeger.medical.biceps.model.participant.LocationContextState;
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
import java.math.BigInteger;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;
import org.somda.sdc.biceps.common.storage.PreprocessingException;
import org.somda.sdc.biceps.provider.preprocessing.HandleDuplicatedException;
import org.somda.sdc.dpws.helper.JaxbMarshalling;
import org.somda.sdc.dpws.soap.SoapMarshalling;
import org.somda.sdc.glue.common.ActionConstants;

/**
 * Unit test for the BICEPS {@linkplain InvariantParticipantModelHandleTest}.
 */
public class InvariantParticipantModelHandleTestTest {

    private static final String VMD_HANDLE = "someVmd";
    private static final String CHANNEL_HANDLE = "someChannel";
    private static final String MDS_HANDLE = MdibBuilder.DEFAULT_MDS_HANDLE;
    private static final String SYSTEM_CONTEXT_HANDLE = "systemContext123";
    private static final String SEQUENCE_ID = MdibBuilder.DEFAULT_SEQUENCE_ID;
    private static final String LOCATION_STATE_HANDLE = "locationStateHandle";
    private static final String LOCATION_DESC_HANDLE = "locationDescHandle";
    private static final String NON_UNIQUE_HANDLE = "non-uniqueHandle";

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);
    private static final int LOWER_BOUND = 0x21;
    private static final int UPPER_BOUND = 0x7E;
    private static final String ERROR_MESSAGE_HANDLE_IS_NOT_UNIQUE =
            "contextState handle '%s' is " + "not unique in Mdib version MdibVersion";
    private static MessageStorageUtil messageStorageUtil;
    private static MdibBuilder mdibBuilder;
    private static MessageBuilder messageBuilder;
    private InvariantParticipantModelHandleTest testClass;
    private JaxbMarshalling riBaseMarshalling;
    private SoapMarshalling riMarshalling;
    private MessageStorage storage;

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

        riBaseMarshalling = riInjector.getInstance(JaxbMarshalling.class);
        riBaseMarshalling.startAsync().awaitRunning(DEFAULT_TIMEOUT);

        riMarshalling = riInjector.getInstance(SoapMarshalling.class);
        riMarshalling.startAsync().awaitRunning(DEFAULT_TIMEOUT);

        storage = injector.getInstance(MessageStorage.class);

        testClass = new InvariantParticipantModelHandleTest();
        testClass.setUp();
    }

    @AfterEach
    void tearDown() throws TimeoutException {
        riBaseMarshalling.stopAsync().awaitTerminated(DEFAULT_TIMEOUT);
        riMarshalling.stopAsync().awaitTerminated(DEFAULT_TIMEOUT);
        storage.close();
    }

    /**
     * Tests whether no test data fails the test.
     */
    @Test
    public void testRequirementR0105NoTestData() {
        assertThrows(NoTestData.class, testClass::testRequirementR0105);
    }

    /**
     * Tests whether handles with characters within the permitted range are passing.
     */
    @Test
    public void testValidASCIICharacters() throws Exception {
        // build handle with every allowed ascii character
        final var locationHandleBuilder = new StringBuilder();
        for (int i = LOWER_BOUND; i <= UPPER_BOUND; i++) {
            locationHandleBuilder.append(Character.toChars(i));
        }
        final var locationDescHandle = locationHandleBuilder.toString();
        final var locationStateHandle = locationHandleBuilder.reverse().toString();

        final var message = buildMdib(
                MDS_HANDLE, VMD_HANDLE, CHANNEL_HANDLE, SYSTEM_CONTEXT_HANDLE, locationDescHandle, locationStateHandle);
        messageStorageUtil.addInboundSecureHttpMessage(storage, message);
        testClass.testRequirementR0105();
    }

    /**
     * Tests whether descriptor handles with characters which are not allowed fail the test.
     */
    @Test
    public void testInvalidASCIICharactersDescriptor() throws Exception {
        final var locationDescHandle = "l̶̲̤ͅo̥̖c͟a̟̙͚t͍̮i̺̫o͕̝̤̯̼n͓̹̘̯͎ͅs̶̭̟͇͖͈̞";

        final var message = buildMdib(
                MDS_HANDLE,
                VMD_HANDLE,
                CHANNEL_HANDLE,
                SYSTEM_CONTEXT_HANDLE,
                locationDescHandle,
                LOCATION_STATE_HANDLE);
        messageStorageUtil.addInboundSecureHttpMessage(storage, message);
        assertThrows(AssertionError.class, testClass::testRequirementR0105);
    }

    /**
     * Tests whether state handles with characters which are not allowed fail the test.
     */
    @Test
    public void testInvalidASCIICharactersState() throws Exception {
        final var locationStateHandle = "loçat͞io̴n͟1̸";

        final var message = buildMdib(
                MDS_HANDLE,
                VMD_HANDLE,
                CHANNEL_HANDLE,
                SYSTEM_CONTEXT_HANDLE,
                LOCATION_DESC_HANDLE,
                locationStateHandle);
        messageStorageUtil.addInboundSecureHttpMessage(storage, message);
        assertThrows(AssertionError.class, testClass::testRequirementR0105);
    }

    /**
     * Tests the ASCII Character validation method, ensures only characters in the range of 0x21 to 0x7E
     * are accepted.
     */
    @Test
    public void testPermittedASCIIRange() {
        // below lower bound
        for (int i = 0; i < LOWER_BOUND; i++) {
            assertFalse(testClass.isWithinPermittedASCIIRange(new String(Character.toChars(i))));
        }
        // lower boundaries
        final int outOfLowerBound = 0x20;
        assertFalse(testClass.isWithinPermittedASCIIRange(new String(Character.toChars(outOfLowerBound))));
        assertTrue(testClass.isWithinPermittedASCIIRange(new String(Character.toChars(LOWER_BOUND))));
        // valid
        for (int i = LOWER_BOUND; i <= UPPER_BOUND; i++) {
            assertTrue(testClass.isWithinPermittedASCIIRange(new String(Character.toChars(i))));
        }
        // upper boundaries
        final int outOfUpperBound = 0x7F;
        assertTrue(testClass.isWithinPermittedASCIIRange(new String(Character.toChars(UPPER_BOUND))));
        assertFalse(testClass.isWithinPermittedASCIIRange(new String(Character.toChars(outOfUpperBound))));
    }

    /**
     * Tests whether no test data fails the test.
     */
    @Test
    public void testRequirementR0007NoTestData() {
        assertThrows(NoTestData.class, testClass::testRequirementR0007);
    }

    /**
     * Tests whether mdib versions with unique handles are passing.
     */
    @Test
    public void testR0007UniqueHandles() throws NoTestData, IOException, JAXBException {

        final var mdib = buildMdib(
                MDS_HANDLE,
                VMD_HANDLE,
                CHANNEL_HANDLE,
                SYSTEM_CONTEXT_HANDLE,
                LOCATION_DESC_HANDLE,
                LOCATION_STATE_HANDLE);

        messageStorageUtil.addInboundSecureHttpMessage(storage, mdib);
        testClass.testRequirementR0007();
    }

    /**
     * Tests whether DescriptionModificationReports introducing non-unique handles fail the test.
     */
    @Test
    public void testR0007DescriptionModificationReportsIntroducingDuplicateHandles() throws IOException, JAXBException {
        final var mdib = buildMdib(
                MDS_HANDLE,
                VMD_HANDLE,
                CHANNEL_HANDLE,
                SYSTEM_CONTEXT_HANDLE,
                LOCATION_DESC_HANDLE,
                LOCATION_STATE_HANDLE);
        final var deleteOp = buildDescriptionModificationReport(
                BigInteger.ONE, BigInteger.ONE, BigInteger.ZERO, true, BigInteger.ONE, BigInteger.ZERO);
        final var insertOp = buildDescriptionModificationReport(
                BigInteger.TWO, BigInteger.TWO, BigInteger.ONE, false, BigInteger.TWO, BigInteger.ONE);
        messageStorageUtil.addInboundSecureHttpMessage(storage, mdib);
        messageStorageUtil.addInboundSecureHttpMessage(storage, deleteOp);
        messageStorageUtil.addInboundSecureHttpMessage(storage, insertOp);

        final AssertionFailedError afe = assertThrows(AssertionFailedError.class, testClass::testRequirementR0007);
        assertTrue(afe.getCause() instanceof RuntimeException);
        final RuntimeException rte = (RuntimeException) afe.getCause();
        assertTrue(rte.getCause() instanceof PreprocessingException);
        assertTrue(rte.getMessage().contains(MDS_HANDLE));
    }

    /**
     * Tests whether ContextReports introducing non-unique handles fail the test.
     */
    @Test
    public void testR0007ContextReportsIntroducingDuplicateHandles() throws IOException, JAXBException {
        final var initialMdib = buildMdib(
                MDS_HANDLE,
                VMD_HANDLE,
                CHANNEL_HANDLE,
                SYSTEM_CONTEXT_HANDLE,
                LOCATION_DESC_HANDLE,
                LOCATION_STATE_HANDLE);
        final var firstReport = buildContextReport(VMD_HANDLE);
        messageStorageUtil.addInboundSecureHttpMessage(storage, initialMdib);
        messageStorageUtil.addInboundSecureHttpMessage(storage, firstReport);

        final AssertionFailedError afe = assertThrows(AssertionFailedError.class, testClass::testRequirementR0007);
        assertTrue(afe.getMessage().startsWith(String.format(ERROR_MESSAGE_HANDLE_IS_NOT_UNIQUE, "someVmd")));
    }

    /**
     * Tests whether mdib versions with non-unique handles are failing. (channel+systemContext)
     */
    @Test
    public void testR0007NonUniqueHandles() throws IOException, JAXBException {

        // channelHandle and systemHandle are the same
        final var mdib = buildMdib(
                MDS_HANDLE,
                VMD_HANDLE,
                NON_UNIQUE_HANDLE,
                NON_UNIQUE_HANDLE,
                LOCATION_DESC_HANDLE,
                LOCATION_STATE_HANDLE);

        messageStorageUtil.addInboundSecureHttpMessage(storage, mdib);

        final RuntimeException exception = assertThrows(RuntimeException.class, testClass::testRequirementR0007);
        assertTrue(exception.getMessage().contains(NON_UNIQUE_HANDLE));
        assertTrue(
                exception.getCause() instanceof HandleDuplicatedException,
                "Wrong kind of Exception: " + exception.getCause());
    }

    /**
     * Tests whether mdib versions with non-unique handles are failing (locationDescriptor+locationState).
     */
    @Test
    public void testR0007NonUniqueHandles2() throws IOException, JAXBException {

        // locationDescHandle and locationStateHandle are the same
        final var mdib = buildMdib(
                MDS_HANDLE, VMD_HANDLE, CHANNEL_HANDLE, SYSTEM_CONTEXT_HANDLE, NON_UNIQUE_HANDLE, NON_UNIQUE_HANDLE);

        messageStorageUtil.addInboundSecureHttpMessage(storage, mdib);

        final RuntimeException rte = assertThrows(RuntimeException.class, testClass::testRequirementR0007);
        assertTrue(rte.getMessage().contains(NON_UNIQUE_HANDLE));
        assertTrue(rte.getCause() instanceof HandleDuplicatedException, "Wrong kind of Exception: " + rte.getCause());
    }

    /**
     * Tests whether no test data fails the test.
     */
    @Test
    public void testRequirementR00980NoTestData() {
        assertThrows(NoTestData.class, testClass::testRequirementR00980);
    }

    /**
     * Tests whether the deletion and reinsertion of an descriptor during the same MDIB sequence having the same handle
     * and type causes the test to pass.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR00980Good() throws Exception {
        final var descriptor = mdibBuilder.buildVmdDescriptor(VMD_HANDLE);
        final var deletePart = buildDescriptionModificationReportPart(DescriptionModificationType.DEL, descriptor);
        final var report = buildDescriptionModificationReport(SEQUENCE_ID, BigInteger.ONE, deletePart);
        messageStorageUtil.addInboundSecureHttpMessage(storage, report);

        final var createPart = buildDescriptionModificationReportPart(DescriptionModificationType.CRT, descriptor);
        final var secondReport = buildDescriptionModificationReport(SEQUENCE_ID, BigInteger.TWO, createPart);
        messageStorageUtil.addInboundSecureHttpMessage(storage, secondReport);

        testClass.testRequirementR00980();
    }

    /**
     * Tests whether reusing the same handle for a different type of descriptor during another sequence id is not
     * failing the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR00980GoodWrongTypeOtherSequence() throws Exception {
        final var descriptor = mdibBuilder.buildVmdDescriptor(NON_UNIQUE_HANDLE);
        final var deletePart = buildDescriptionModificationReportPart(DescriptionModificationType.DEL, descriptor);
        final var report = buildDescriptionModificationReport(SEQUENCE_ID, BigInteger.ONE, deletePart);
        messageStorageUtil.addInboundSecureHttpMessage(storage, report);

        final var createPart = buildDescriptionModificationReportPart(DescriptionModificationType.CRT, descriptor);
        final var secondReport = buildDescriptionModificationReport(SEQUENCE_ID, BigInteger.TWO, createPart);
        messageStorageUtil.addInboundSecureHttpMessage(storage, secondReport);

        final var deleteAgainReport =
                buildDescriptionModificationReport(SEQUENCE_ID, BigInteger.valueOf(3), deletePart);
        messageStorageUtil.addInboundSecureHttpMessage(storage, deleteAgainReport);

        final var otherDescriptor =
                mdibBuilder.buildAlertSignalDescriptor(NON_UNIQUE_HANDLE, AlertSignalManifestation.VIS, true);
        final var createOtherPart =
                buildDescriptionModificationReportPart(DescriptionModificationType.CRT, otherDescriptor);
        final var createSomethingNewReport =
                buildDescriptionModificationReport("123457", BigInteger.valueOf(4), createOtherPart);
        messageStorageUtil.addInboundSecureHttpMessage(storage, createSomethingNewReport);

        testClass.testRequirementR00980();
    }

    /**
     * Tests whether reusing a handle from a previously deleted descriptor for a different type causes the test to fail.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR00980BadWrongType() throws Exception {
        final var descriptor = mdibBuilder.buildVmdDescriptor(NON_UNIQUE_HANDLE);
        final var deletePart = buildDescriptionModificationReportPart(DescriptionModificationType.DEL, descriptor);
        final var report = buildDescriptionModificationReport(SEQUENCE_ID, BigInteger.ONE, deletePart);
        messageStorageUtil.addInboundSecureHttpMessage(storage, report);

        final var otherDescriptor =
                mdibBuilder.buildAlertSignalDescriptor(NON_UNIQUE_HANDLE, AlertSignalManifestation.VIS, true);
        final var createPart = buildDescriptionModificationReportPart(DescriptionModificationType.CRT, otherDescriptor);
        final var secondReport = buildDescriptionModificationReport(SEQUENCE_ID, BigInteger.TWO, createPart);
        messageStorageUtil.addInboundSecureHttpMessage(storage, secondReport);

        assertThrows(AssertionError.class, testClass::testRequirementR00980);
    }

    /*
     * build mdib with configurable handles
     */
    Envelope buildMdib(
            final String mdsHandle,
            final String vmdHandle,
            final String channelHandle,
            final String systemContextHandle,
            final String locationDescriptorHandle,
            final String locationStateHandle) {
        final var mds = mdibBuilder.buildMds(mdsHandle);

        final var mdDescription = mdibBuilder.buildMdDescription();
        final var mdState = mdibBuilder.buildMdState();

        mdDescription.getMds().add(mds.getLeft());
        mdState.getState().add(mds.getRight());

        final var mdib = mdibBuilder.buildMdib(InvariantParticipantModelHandleTestTest.SEQUENCE_ID);
        mdib.setMdDescription(mdDescription);
        mdib.setMdState(mdState);

        final var mdsDescriptor = mdib.getMdDescription().getMds().get(0);
        mdsDescriptor.setDescriptorVersion(BigInteger.ZERO);

        final var systemContext = mdibBuilder.buildSystemContext(systemContextHandle);
        mdsDescriptor.setSystemContext(systemContext.getLeft());
        mdState.getState().add(systemContext.getRight());

        final var locationContext = mdibBuilder.buildLocationContext(locationDescriptorHandle, locationStateHandle);
        systemContext.getLeft().setLocationContext(locationContext.getLeft());
        mdState.getState().add(locationContext.getRight());

        final var vmd = mdibBuilder.buildVmd(vmdHandle);
        vmd.getLeft().setDescriptorVersion(BigInteger.ZERO);
        vmd.getRight().setDescriptorVersion(BigInteger.ZERO);
        mdsDescriptor.getVmd().add(vmd.getLeft());
        mdState.getState().add(vmd.getRight());

        final var channel = mdibBuilder.buildChannel(channelHandle);
        vmd.getLeft().getChannel().add(channel.getLeft());
        mdState.getState().add(channel.getRight());

        final var getMdibResponse = messageBuilder.buildGetMdibResponse(mdib.getSequenceId());
        getMdibResponse.setMdib(mdib);

        return messageBuilder.createSoapMessageWithBody(
                ActionConstants.getResponseAction(ActionConstants.ACTION_GET_MDIB), getMdibResponse);
    }

    final DescriptionModificationReport.ReportPart buildDescriptionModificationReportPart(
            final DescriptionModificationType modificationType, final AbstractDescriptor... modifications) {
        final var reportPart = messageBuilder.buildDescriptionModificationReportReportPart();
        reportPart.setModificationType(modificationType);
        for (var modification : modifications) {
            reportPart.getDescriptor().add(modification);
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

    Envelope buildDescriptionModificationReport(
            final @Nullable BigInteger mdibVersion,
            final @Nullable BigInteger vmdVersion,
            final BigInteger channelVersion,
            final boolean deleteChannel,
            final @Nullable BigInteger vmdStateVersion,
            final @Nullable BigInteger channelStateVersion) {
        final var reportPart = messageBuilder.buildDescriptionModificationReportReportPart();
        reportPart.setModificationType(DescriptionModificationType.UPT);
        final DescriptionModificationReport.ReportPart modificationPart;
        final var channel = mdibBuilder.buildChannel(CHANNEL_HANDLE);
        channel.getLeft().setDescriptorVersion(channelVersion);
        channel.getRight().setDescriptorVersion(channelVersion);
        channel.getRight().setStateVersion(channelStateVersion);
        modificationPart = messageBuilder.buildDescriptionModificationReportReportPart();
        if (deleteChannel) {
            modificationPart.setModificationType(DescriptionModificationType.DEL);
        } else {
            // create if not delete with collision handle
            channel.getLeft().setHandle(MDS_HANDLE);
            channel.getRight().setDescriptorHandle(MDS_HANDLE);
            modificationPart.setModificationType(DescriptionModificationType.CRT);
            modificationPart.setParentDescriptor(VMD_HANDLE);
        }
        modificationPart.getDescriptor().add(channel.getLeft());
        modificationPart.getState().add(channel.getRight());
        if (vmdVersion != null) {
            final var vmd = mdibBuilder.buildVmd(VMD_HANDLE);
            vmd.getLeft().setDescriptorVersion(vmdVersion);
            vmd.getRight().setDescriptorVersion(vmdVersion);
            vmd.getRight().setStateVersion(vmdStateVersion);
            reportPart.getDescriptor().add(vmd.getLeft());
            reportPart.getState().add(vmd.getRight());
        }
        final List<DescriptionModificationReport.ReportPart> parts;
        parts = List.of(reportPart, modificationPart);
        final var report = messageBuilder.buildDescriptionModificationReport(SEQUENCE_ID, parts);
        report.setMdibVersion(mdibVersion);
        return messageBuilder.createSoapMessageWithBody(ActionConstants.ACTION_DESCRIPTION_MODIFICATION_REPORT, report);
    }

    Envelope buildContextReport(final String handle) {
        final var reportPart = messageBuilder.buildAbstractContextReportReportPart();
        final LocationContextState locationContextState = new LocationContextState();
        locationContextState.setHandle(handle);
        locationContextState.setDescriptorHandle(LOCATION_DESC_HANDLE);
        reportPart.getContextState().add(locationContextState);

        final List<AbstractContextReport.ReportPart> parts = List.of(reportPart);
        final var report = messageBuilder.buildEpisodicContextReport(SEQUENCE_ID);
        report.setMdibVersion(BigInteger.TWO);
        report.getReportPart().addAll(parts);
        return messageBuilder.createSoapMessageWithBody(ActionConstants.ACTION_EPISODIC_CONTEXT_REPORT, report);
    }
}
