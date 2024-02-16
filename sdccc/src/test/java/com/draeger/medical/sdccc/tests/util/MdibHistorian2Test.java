/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2023, 2024 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.tests.util;

import static org.mockito.Mockito.mock;

import com.draeger.medical.biceps.model.message.InvocationError;
import com.draeger.medical.biceps.model.message.InvocationInfo;
import com.draeger.medical.biceps.model.message.InvocationState;
import com.draeger.medical.biceps.model.message.OperationInvokedReport;
import com.draeger.medical.biceps.model.participant.AlertActivation;
import com.draeger.medical.biceps.model.participant.CodedValue;
import com.draeger.medical.biceps.model.participant.InstanceIdentifier;
import com.draeger.medical.biceps.model.participant.Mdib;
import com.draeger.medical.biceps.model.participant.MetricAvailability;
import com.draeger.medical.biceps.model.participant.MetricCategory;
import com.draeger.medical.biceps.model.participant.OperatingMode;
import com.draeger.medical.dpws.soap.model.Envelope;
import com.draeger.medical.sdccc.marshalling.MarshallingUtil;
import com.draeger.medical.sdccc.messages.MessageStorage;
import com.draeger.medical.sdccc.sdcri.testclient.TestClient;
import com.draeger.medical.sdccc.sdcri.testclient.TestClientUtil;
import com.draeger.medical.sdccc.tests.InjectorTestBase;
import com.draeger.medical.sdccc.tests.test_util.InjectorUtil;
import com.draeger.medical.sdccc.tests.util.guice.MdibHistorianFactory;
import com.draeger.medical.sdccc.util.MdibBuilder;
import com.draeger.medical.sdccc.util.MessageBuilder;
import com.draeger.medical.sdccc.util.MessageStorageUtil;
import com.draeger.medical.sdccc.util.TestRunObserver;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import jakarta.xml.bind.JAXBException;
import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import javax.annotation.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.somda.sdc.biceps.common.storage.PreprocessingException;
import org.somda.sdc.dpws.helper.JaxbMarshalling;
import org.somda.sdc.dpws.soap.SoapMarshalling;
import org.somda.sdc.glue.common.ActionConstants;
import org.somda.sdc.glue.consumer.report.ReportProcessingException;

/**
 * Unit tests for those method in {@linkplain MdibHistorian} that
 * use models from the com.draeger.medical package.
 */
public class MdibHistorian2Test {

    private static final String VMD_HANDLE = "someVmd";
    private static final String CHANNEL_HANDLE = "someChannel";
    private static final String STRING_METRIC_HANDLE = "theIncredibleStringHandle";
    private static final String ALERT_SYSTEM_HANDLE = "ringDingDong";
    private static final String SYSTEM_CONTEXT_HANDLE = "syswow64";
    private static final String PATIENT_CONTEXT_HANDLE = "PATIENT_ZERO";
    private static final String SCO_HANDLE = "sco_what?";
    private static final String SET_STRING_HANDLE = "sadString";

    private Injector historianInjector;
    private MdibHistorianFactory historianFactory;
    private Injector marshallingInjector;
    private MessageStorageUtil messageStorageUtil;
    private MessageBuilder messageBuilder;
    private MessageStorage storage;
    private MdibBuilder mdibBuilder;
    private SoapMarshalling soapMarshalling;
    private JaxbMarshalling jaxbMarshalling;

    @BeforeEach
    void setUp() throws IOException {
        historianInjector = TestClientUtil.createClientInjector();
        historianFactory = historianInjector.getInstance(MdibHistorianFactory.class);

        marshallingInjector = MarshallingUtil.createMarshallingTestInjector(true);
        messageStorageUtil = marshallingInjector.getInstance(MessageStorageUtil.class);
        messageBuilder = marshallingInjector.getInstance(MessageBuilder.class);
        mdibBuilder = marshallingInjector.getInstance(MdibBuilder.class);

        soapMarshalling = historianInjector.getInstance(SoapMarshalling.class);
        soapMarshalling.startAsync().awaitRunning();

        jaxbMarshalling = historianInjector.getInstance(JaxbMarshalling.class);
        jaxbMarshalling.startAsync().awaitRunning();

        final var mockClient = mock(TestClient.class);
        final Injector storageInjector = InjectorUtil.setupInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(TestClient.class).toInstance(mockClient);
            }
        });

        InjectorTestBase.setInjector(historianInjector);
        storage = storageInjector.getInstance(MessageStorage.class);
    }

    @AfterEach
    void tearDown() {
        storage.close();
        soapMarshalling.stopAsync().awaitTerminated();
        jaxbMarshalling.stopAsync().awaitTerminated();
    }

    @Test
    void testEpisodicReportBasedHistoryRegressionWithOperationInvokedReport()
            throws ReportProcessingException, JAXBException, PreprocessingException, IOException {

        // given
        final BigInteger numericMdibVersion = BigInteger.ZERO;
        final String sequenceId = "abc";
        final var report = new OperationInvokedReport();
        final com.draeger.medical.biceps.model.message.OperationInvokedReport.ReportPart part = createOIRReportPart(
                createInvocationInfo(InvocationError.OTH, InvocationState.FAIL, 123),
                "opTarget",
                "opHandle",
                createInstanceIdentifier(createCodedValue("33", "JOCS", "2.0"), "rotName", "extName"),
                "sourceMds");
        report.getReportPart().add(part);
        report.setMdibVersion(numericMdibVersion);
        report.setSequenceId(sequenceId);
        final Envelope soapMessage =
                messageBuilder.createSoapMessageWithBody(ActionConstants.ACTION_OPERATION_INVOKED_REPORT, report);

        testEpisodicReportBasedHistory(soapMessage, sequenceId);
    }

    @SuppressWarnings({"EmptyBlock"})
    void testEpisodicReportBasedHistory(final Envelope report, final String sequenceId)
            throws ReportProcessingException, PreprocessingException, JAXBException, IOException {
        final var mockObserver = mock(TestRunObserver.class);
        final var historianUnderTest = historianFactory.createMdibHistorian(storage, mockObserver);

        messageStorageUtil.addInboundSecureHttpMessage(storage, buildMdibEnvelope(sequenceId, BigInteger.ZERO));
        messageStorageUtil.addInboundSecureHttpMessage(storage, report);

        try (MdibHistorian.HistorianResult historianResult =
                historianUnderTest.episodicReportBasedHistory(sequenceId)) {
            while (historianResult.next() != null) {
                // query all of them
                final var ignored = false; // make Checkstyle happy
            }
        }
    }

    @Test
    void testUniqueEpisodicReportBasedHistoryUntilTimestampRegressionWithOperationInvokedReport()
            throws ReportProcessingException, JAXBException, PreprocessingException, IOException {

        final BigInteger numericMdibVersion = BigInteger.ZERO;
        final String sequenceId = "abc";
        final var report = new OperationInvokedReport();
        final com.draeger.medical.biceps.model.message.OperationInvokedReport.ReportPart part = createOIRReportPart(
                createInvocationInfo(InvocationError.OTH, InvocationState.FAIL, 123),
                "opTarget",
                "opHandle",
                createInstanceIdentifier(createCodedValue("33", "JOCS", "2.0"), "rotName", "extName"),
                "sourceMds");
        report.getReportPart().add(part);
        report.setMdibVersion(numericMdibVersion);
        report.setSequenceId(sequenceId);
        final Envelope soapMessage =
                messageBuilder.createSoapMessageWithBody(ActionConstants.ACTION_OPERATION_INVOKED_REPORT, report);

        testUniqueEpisodicReportBasedHistoryUntilTimestamp(sequenceId, soapMessage);
    }

    @SuppressWarnings({"EmptyBlock"})
    private void testUniqueEpisodicReportBasedHistoryUntilTimestamp(final String sequenceId, final Envelope report)
            throws ReportProcessingException, PreprocessingException, JAXBException, IOException {
        final var mockObserver = mock(TestRunObserver.class);
        final var historianUnderTest = historianFactory.createMdibHistorian(storage, mockObserver);

        messageStorageUtil.addInboundSecureHttpMessage(storage, buildMdibEnvelope(sequenceId, BigInteger.ZERO));
        messageStorageUtil.addInboundSecureHttpMessage(storage, report);

        final long timestamp = System.nanoTime();

        try (var result = historianUnderTest.uniqueEpisodicReportBasedHistoryUntilTimestamp(sequenceId, timestamp)) {
            while (result.next() != null) {
                // query them all
                final var ignored = false; // make Checkstyle happy
            }
        }
    }

    private static InvocationInfo createInvocationInfo(
            final InvocationError error, final InvocationState state, final long transactionId) {
        final InvocationInfo info = new InvocationInfo();
        info.setInvocationError(error);
        info.setInvocationState(state);
        info.setTransactionId(transactionId);

        return info;
    }

    private static com.draeger.medical.biceps.model.message.OperationInvokedReport.ReportPart createOIRReportPart(
            final InvocationInfo invocationInfo,
            final String opTarget,
            final String opHandleRef,
            final InstanceIdentifier invocationSource,
            final String sourceMds) {
        final com.draeger.medical.biceps.model.message.OperationInvokedReport.ReportPart part =
                new com.draeger.medical.biceps.model.message.OperationInvokedReport.ReportPart();
        part.setInvocationInfo(invocationInfo);
        part.setOperationTarget(opTarget);
        part.setOperationHandleRef(opHandleRef);
        part.setInvocationSource(invocationSource);
        part.setSourceMds(sourceMds);
        return part;
    }

    private static InstanceIdentifier createInstanceIdentifier(
            final CodedValue type, final String rootName, final String extensionName) {
        final InstanceIdentifier result = new InstanceIdentifier();
        result.setType(type);
        result.setRootName(rootName);
        result.setExtensionName(extensionName);
        return result;
    }

    private static CodedValue createCodedValue(
            final String code, final String codingSystem, final String codingSystemVersion) {
        final CodedValue value = new CodedValue();
        value.setCode(code);
        value.setCodingSystem(codingSystem);
        value.setCodingSystemVersion(codingSystemVersion);
        return value;
    }

    Envelope buildMdibEnvelope(final String sequenceId, @Nullable final BigInteger mdibVersion) {
        final var mdib = buildMdib(sequenceId);
        mdib.setMdibVersion(mdibVersion);
        final var report = messageBuilder.buildGetMdibResponse(sequenceId);
        report.setMdib(mdib);
        return messageBuilder.createSoapMessageWithBody(
                ActionConstants.getResponseAction(ActionConstants.ACTION_GET_MDIB), report);
    }

    private Mdib buildMdib(final String sequenceId) {
        final var mdib = mdibBuilder.buildMinimalMdib(sequenceId);

        final var mdState = mdib.getMdState();
        final var mdsDescriptor = mdib.getMdDescription().getMds().get(0);
        mdsDescriptor.setDescriptorVersion(null);

        final var vmd = mdibBuilder.buildVmd(VMD_HANDLE);
        vmd.getLeft().setDescriptorVersion(BigInteger.ZERO);
        vmd.getRight().setDescriptorVersion(BigInteger.ZERO);
        mdsDescriptor.getVmd().add(vmd.getLeft());
        mdState.getState().add(vmd.getRight());

        final var channel = mdibBuilder.buildChannel(CHANNEL_HANDLE);
        vmd.getLeft().getChannel().add(channel.getLeft());
        mdState.getState().add(channel.getRight());

        final var metric = mdibBuilder.buildStringMetric(
                STRING_METRIC_HANDLE, MetricCategory.CLC, MetricAvailability.INTR, mdibBuilder.buildCodedValue("abc"));
        channel.getLeft().getMetric().add(metric.getLeft());
        mdState.getState().add(metric.getRight());

        final var alertSystem = mdibBuilder.buildAlertSystem(ALERT_SYSTEM_HANDLE, AlertActivation.OFF);
        mdsDescriptor.setAlertSystem(alertSystem.getLeft());
        mdState.getState().add(alertSystem.getRight());

        final var systemContext = mdibBuilder.buildSystemContext(SYSTEM_CONTEXT_HANDLE);

        final var operator1 = mdibBuilder.buildOperatorContextDescriptor("opDescriptor1");
        final var operator2 = mdibBuilder.buildOperatorContextDescriptor("opDescriptor2");
        systemContext.getLeft().getOperatorContext().addAll(List.of(operator1, operator2));

        mdsDescriptor.setSystemContext(systemContext.getLeft());
        mdState.getState().add(systemContext.getRight());

        final var patientContextDescriptor = mdibBuilder.buildPatientContextDescriptor(PATIENT_CONTEXT_HANDLE);
        systemContext.getLeft().setPatientContext(patientContextDescriptor);

        final var sco = mdibBuilder.buildSco(SCO_HANDLE);
        mdsDescriptor.setSco(sco.getLeft());
        mdState.getState().add(sco.getRight());

        final var setString =
                mdibBuilder.buildSetStringOperation(SET_STRING_HANDLE, SYSTEM_CONTEXT_HANDLE, OperatingMode.EN);
        sco.getLeft().getOperation().add(setString.getLeft());
        mdState.getState().add(setString.getRight());
        return mdib;
    }
}
