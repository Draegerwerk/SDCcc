/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.tests.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;

import com.draeger.medical.biceps.model.message.EpisodicContextReport;
import com.draeger.medical.biceps.model.participant.AlertActivation;
import com.draeger.medical.biceps.model.participant.Mdib;
import com.draeger.medical.biceps.model.participant.MetricAvailability;
import com.draeger.medical.biceps.model.participant.MetricCategory;
import com.draeger.medical.biceps.model.participant.OperatingMode;
import com.draeger.medical.biceps.model.participant.PatientContextState;
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
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
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
 * Unit tests for the {@linkplain com.draeger.medical.sdccc.tests.util.MdibHistorian}.
 */
public class MdibHistorianTest {

    private static final String VMD_HANDLE = "someVmd";
    private static final String CHANNEL_HANDLE = "someChannel";
    private static final String STRING_METRIC_HANDLE = "theIncredibleStringHandle";
    private static final String ALERT_SYSTEM_HANDLE = "ringDingDong";
    private static final String SYSTEM_CONTEXT_HANDLE = "syswow64";
    private static final String PATIENT_CONTEXT_HANDLE = "PATIENT_ZERO";
    private static final String PATIENT_CONTEXT_STATE_HANDLE = "He_is_dead_jim";
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
    void testOnlyMdibNoReport() throws PreprocessingException, ReportProcessingException, IOException, JAXBException {
        final var mdib = mdibBuilder.buildMinimalMdib();
        final var getMdibResponse = messageBuilder.buildGetMdibResponse(mdib.getSequenceId());
        getMdibResponse.setMdib(mdib);

        messageStorageUtil.addInboundSecureHttpMessage(
                storage,
                messageBuilder.createSoapMessageWithBody(
                        ActionConstants.getResponseAction(ActionConstants.ACTION_GET_MDIB), getMdibResponse));

        final var mockObserver = mock(TestRunObserver.class);
        final var historian = historianFactory.createMdibHistorian(storage, mockObserver);

        try (final var history = historian.episodicReportBasedHistory(MdibBuilder.DEFAULT_SEQUENCE_ID)) {
            // skip the first element, we should be at the end since there are no reports to apply
            assertNotNull(history.next());
            assertNull(history.next());
        }
    }

    @Test
    void testEpisodicReports() throws IOException, JAXBException, PreprocessingException, ReportProcessingException {

        final var componentReportMdibVersion = BigInteger.TEN;
        final var componentReportMdsStateVersion = BigInteger.TWO;
        final var componentReport = buildEpisodicComponentReport(
                MdibBuilder.DEFAULT_SEQUENCE_ID,
                MdibBuilder.DEFAULT_MDS_HANDLE,
                componentReportMdibVersion,
                componentReportMdsStateVersion);

        final var metricReportMdibVersion = componentReportMdibVersion.add(BigInteger.ONE);
        final var metricReportStateVersion = BigInteger.TWO;
        final var metricReport = buildEpisodicMetricReport(
                MdibBuilder.DEFAULT_SEQUENCE_ID, metricReportMdibVersion, metricReportStateVersion);

        final var alertReportMdibVersion = metricReportMdibVersion.add(BigInteger.ONE);
        final var alertReportStateVersion = BigInteger.TWO;
        final var alertReport = buildEpisodicAlertReport(
                MdibBuilder.DEFAULT_SEQUENCE_ID, alertReportMdibVersion, alertReportStateVersion);

        final var contextReportMdibVersion = alertReportMdibVersion.add(BigInteger.ONE);
        final var contextReportStateVersion = BigInteger.TWO;
        final var contextReport = buildEpisodicContextReport(
                MdibBuilder.DEFAULT_SEQUENCE_ID, contextReportMdibVersion, contextReportStateVersion);

        final var operationalStateReportMdibVersion = contextReportMdibVersion.add(BigInteger.ONE);
        final var operationStateReportStateVersion = BigInteger.TWO;
        final var operationStateReport = buildEpisodicOperationalStateReport(
                MdibBuilder.DEFAULT_SEQUENCE_ID, operationalStateReportMdibVersion, operationStateReportStateVersion);

        messageStorageUtil.addInboundSecureHttpMessage(
                storage, buildMdibEnvelope(MdibBuilder.DEFAULT_SEQUENCE_ID, null));
        messageStorageUtil.addInboundSecureHttpMessage(storage, componentReport);
        messageStorageUtil.addInboundSecureHttpMessage(storage, metricReport);
        messageStorageUtil.addInboundSecureHttpMessage(storage, alertReport);
        messageStorageUtil.addInboundSecureHttpMessage(storage, contextReport);
        messageStorageUtil.addInboundSecureHttpMessage(storage, operationStateReport);

        final var mockObserver = mock(TestRunObserver.class);
        final var historian = historianFactory.createMdibHistorian(storage, mockObserver);

        try (final var history = historian.episodicReportBasedHistory(MdibBuilder.DEFAULT_SEQUENCE_ID)) {
            assertNotNull(history.next());
            {
                final var componentReportMdib = history.next();
                assertEquals(
                        componentReportMdibVersion,
                        componentReportMdib.getMdibVersion().getVersion());
            }
            {
                final var metricReportMdib = history.next();
                assertEquals(
                        metricReportMdibVersion,
                        metricReportMdib.getMdibVersion().getVersion());
            }
            {
                final var alertReportMdib = history.next();
                assertEquals(
                        alertReportMdibVersion, alertReportMdib.getMdibVersion().getVersion());
            }
            {
                final var contextReportMdib = history.next();
                assertEquals(
                        contextReportMdibVersion,
                        contextReportMdib.getMdibVersion().getVersion());
            }
            {
                final var operationalStateReportMdib = history.next();
                assertEquals(
                        operationalStateReportMdibVersion,
                        operationalStateReportMdib.getMdibVersion().getVersion());
            }
            assertNull(history.next());
        }
    }

    @Test
    void testExceptionMissingDescriptor()
            throws IOException, JAXBException, PreprocessingException, ReportProcessingException {
        final var componentReportMdibVersion = BigInteger.TEN;
        final var componentReportMdsStateVersion = BigInteger.TWO;
        final var componentReport = buildEpisodicComponentReport(
                MdibBuilder.DEFAULT_SEQUENCE_ID,
                "InvalidHandleYo",
                componentReportMdibVersion,
                componentReportMdsStateVersion);

        messageStorageUtil.addInboundSecureHttpMessage(
                storage, buildMdibEnvelope(MdibBuilder.DEFAULT_SEQUENCE_ID, null));
        messageStorageUtil.addInboundSecureHttpMessage(storage, componentReport);

        final var mockObserver = mock(TestRunObserver.class);
        final var historian = historianFactory.createMdibHistorian(storage, mockObserver);

        try (final var history = historian.episodicReportBasedHistory(MdibBuilder.DEFAULT_SEQUENCE_ID)) {
            // skip the mdib
            history.next();
            final var error = assertThrows(AssertionError.class, history::next);
            assertTrue(error.getCause() instanceof RuntimeException);
        }
    }

    @Test
    void testFilterForMdibVersion()
            throws IOException, JAXBException, PreprocessingException, ReportProcessingException {

        final var componentReportMdibVersion = BigInteger.ONE;
        final var componentReportMdsStateVersion = BigInteger.TWO;
        final var componentReport = buildEpisodicComponentReport(
                MdibBuilder.DEFAULT_SEQUENCE_ID,
                MdibBuilder.DEFAULT_MDS_HANDLE,
                componentReportMdibVersion,
                componentReportMdsStateVersion);

        final var metricReportMdibVersion = BigInteger.TWO;
        final var metricReportStateVersion = BigInteger.TWO;
        final var metricReport = buildEpisodicMetricReport(
                MdibBuilder.DEFAULT_SEQUENCE_ID, metricReportMdibVersion, metricReportStateVersion);

        final var alertReportMdibVersion = BigInteger.valueOf(3);
        final var alertReportStateVersion = BigInteger.TWO;
        final var alertReport = buildEpisodicAlertReport(
                MdibBuilder.DEFAULT_SEQUENCE_ID, alertReportMdibVersion, alertReportStateVersion);

        final var contextReportMdibVersion = BigInteger.valueOf(4);
        final var contextReportStateVersion = BigInteger.TWO;
        final var contextReport = buildEpisodicContextReport(
                MdibBuilder.DEFAULT_SEQUENCE_ID, contextReportMdibVersion, contextReportStateVersion);

        final var operationalStateReportMdibVersion = BigInteger.ONE;
        final var operationStateReportStateVersion = BigInteger.TWO;
        final var operationStateReport = buildEpisodicOperationalStateReport(
                MdibBuilder.DEFAULT_SEQUENCE_ID, operationalStateReportMdibVersion, operationStateReportStateVersion);

        messageStorageUtil.addInboundSecureHttpMessage(
                storage, buildMdibEnvelope(MdibBuilder.DEFAULT_SEQUENCE_ID, BigInteger.TWO));
        // should be filtered, since mdibVersion is smaller than initial get mdib response
        messageStorageUtil.addInboundSecureHttpMessage(storage, componentReport);

        messageStorageUtil.addInboundSecureHttpMessage(storage, metricReport);
        messageStorageUtil.addInboundSecureHttpMessage(storage, alertReport);
        messageStorageUtil.addInboundSecureHttpMessage(storage, contextReport);
        // should not be filtered, since reports with mdib version larger than initial mdib version was seen before
        messageStorageUtil.addInboundSecureHttpMessage(storage, operationStateReport);

        final var mockObserver = mock(TestRunObserver.class);
        final var historian = historianFactory.createMdibHistorian(storage, mockObserver);

        try (final var history = historian.episodicReportBasedHistory(MdibBuilder.DEFAULT_SEQUENCE_ID)) {
            history.next();
            history.next();
            history.next();
            history.next();
            history.next();
            assertNull(history.next()); // history end
        }
    }

    @Test
    void testCorrectNumberOfReportsAfterFilter()
            throws IOException, JAXBException, PreprocessingException, ReportProcessingException {

        final var componentReportMdibVersion = BigInteger.ONE;
        final var componentReportMdsStateVersion = BigInteger.TWO;
        final var componentReport = buildEpisodicComponentReport(
                MdibBuilder.DEFAULT_SEQUENCE_ID,
                MdibBuilder.DEFAULT_MDS_HANDLE,
                componentReportMdibVersion,
                componentReportMdsStateVersion);

        final var metricReportMdibVersion = BigInteger.TWO;
        final var metricReportStateVersion = BigInteger.TWO;
        final var metricReport = buildEpisodicMetricReport(
                MdibBuilder.DEFAULT_SEQUENCE_ID, metricReportMdibVersion, metricReportStateVersion);

        final var metricReportMdibVersion2 = BigInteger.valueOf(3);
        final var metricReport2 = buildEpisodicMetricReport(
                MdibBuilder.DEFAULT_SEQUENCE_ID, metricReportMdibVersion2, metricReportStateVersion);

        final var alertReportMdibVersion = BigInteger.valueOf(3);
        final var alertReportStateVersion = BigInteger.TWO;
        final var alertReport = buildEpisodicAlertReport(
                MdibBuilder.DEFAULT_SEQUENCE_ID, alertReportMdibVersion, alertReportStateVersion);

        final var contextReportMdibVersion = BigInteger.valueOf(4);
        final var contextReportStateVersion = BigInteger.TWO;
        final var contextReport = buildEpisodicContextReport(
                MdibBuilder.DEFAULT_SEQUENCE_ID, contextReportMdibVersion, contextReportStateVersion);

        final var operationalStateReportMdibVersion = BigInteger.valueOf(5);
        final var operationStateReportStateVersion = BigInteger.TWO;
        final var operationStateReport = buildEpisodicOperationalStateReport(
                MdibBuilder.DEFAULT_SEQUENCE_ID, operationalStateReportMdibVersion, operationStateReportStateVersion);

        final var initialMdibVersion = BigInteger.valueOf(3);
        messageStorageUtil.addInboundSecureHttpMessage(
                storage, buildMdibEnvelope(MdibBuilder.DEFAULT_SEQUENCE_ID, initialMdibVersion));
        // should be filtered, since mdibVersion is smaller than initial get mdib response
        messageStorageUtil.addInboundSecureHttpMessage(storage, componentReport);
        messageStorageUtil.addInboundSecureHttpMessage(storage, metricReport);
        // should not be filtered and be part of history
        messageStorageUtil.addInboundSecureHttpMessage(storage, metricReport2);
        messageStorageUtil.addInboundSecureHttpMessage(storage, alertReport);
        messageStorageUtil.addInboundSecureHttpMessage(storage, contextReport);
        messageStorageUtil.addInboundSecureHttpMessage(storage, operationStateReport);

        // initial getMdibResponse and the metricReport2, alertReport, contextReport and operationStateReport
        final var expectedNumberOfEntries = 5;
        final var mockObserver = mock(TestRunObserver.class);
        final var historian = historianFactory.createMdibHistorian(storage, mockObserver);
        try (final var history = historian.episodicReportBasedHistory(MdibBuilder.DEFAULT_SEQUENCE_ID)) {
            var access = history.next();
            final var numberOfEntries = new AtomicInteger(0);
            while (access != null) {
                numberOfEntries.incrementAndGet();
                access = history.next();
            }
            assertEquals(expectedNumberOfEntries, numberOfEntries.get());
        }
    }

    /**
     * Verifies that sequence ids are properly detected and their histories are available.
     */
    @Test
    void testMultipleSequenceIds() throws IOException, JAXBException {
        final var sequenceIds = List.of("Sequenceid1", "Sequenceid2", "Sequenceid3");

        {
            final var componentReportMdibVersion = BigInteger.TEN;
            final var componentReportMdsStateVersion = BigInteger.TWO;
            final var componentReport = buildEpisodicComponentReport(
                    sequenceIds.get(0),
                    MdibBuilder.DEFAULT_MDS_HANDLE,
                    componentReportMdibVersion,
                    componentReportMdsStateVersion);
            messageStorageUtil.addInboundSecureHttpMessage(storage, buildMdibEnvelope(sequenceIds.get(0), null));
            messageStorageUtil.addInboundSecureHttpMessage(storage, componentReport);
        }
        {
            final var metricReportMdibVersion = BigInteger.ONE;
            final var metricReportStateVersion = BigInteger.TWO;
            final var metricReport =
                    buildEpisodicMetricReport(sequenceIds.get(1), metricReportMdibVersion, metricReportStateVersion);
            messageStorageUtil.addInboundSecureHttpMessage(storage, buildMdibEnvelope(sequenceIds.get(1), null));
            messageStorageUtil.addInboundSecureHttpMessage(storage, metricReport);
        }
        {
            final var alertReportMdibVersion = BigInteger.ONE;
            final var alertReportStateVersion = BigInteger.TWO;
            final var alertReport =
                    buildEpisodicAlertReport(sequenceIds.get(2), alertReportMdibVersion, alertReportStateVersion);
            messageStorageUtil.addInboundSecureHttpMessage(storage, buildMdibEnvelope(sequenceIds.get(2), null));
            messageStorageUtil.addInboundSecureHttpMessage(storage, alertReport);
        }

        final var mockObserver = mock(TestRunObserver.class);
        final var historian = historianFactory.createMdibHistorian(storage, mockObserver);

        try (final Stream<String> sequenceIdStream = historian.getKnownSequenceIds()) {
            assertEquals(Set.copyOf(sequenceIds), Set.copyOf(sequenceIdStream.toList()));
        }

        // ensure only correct sequence id elements occur in the mdib
        for (final String sequence : sequenceIds) {
            try (final var history = historian.episodicReportBasedHistory(sequence)) {
                var mdib = history.next();
                assertNotNull(mdib);
                while (mdib != null) {
                    assertEquals(sequence, mdib.getMdibVersion().getSequenceId());
                    mdib = history.next();
                }
            } catch (PreprocessingException | ReportProcessingException e) {
                fail(e);
            }
        }
    }

    /**
     * Tests whether a sequence without an mdib throws.
     *
     * @throws Exception on any exception
     */
    @Test
    void testSequenceWithoutMdib() throws Exception {
        final var sequenceIds = List.of("Sequenceid1", "Sequenceid2");

        {
            final var componentReportMdibVersion = BigInteger.TEN;
            final var componentReportMdsStateVersion = BigInteger.TWO;
            final var componentReport = buildEpisodicComponentReport(
                    sequenceIds.get(0),
                    MdibBuilder.DEFAULT_MDS_HANDLE,
                    componentReportMdibVersion,
                    componentReportMdsStateVersion);
            messageStorageUtil.addInboundSecureHttpMessage(storage, buildMdibEnvelope(sequenceIds.get(0), null));
            messageStorageUtil.addInboundSecureHttpMessage(storage, componentReport);
        }
        {
            final var metricReportMdibVersion = BigInteger.ONE;
            final var metricReportStateVersion = BigInteger.TWO;
            final var metricReport =
                    buildEpisodicMetricReport(sequenceIds.get(1), metricReportMdibVersion, metricReportStateVersion);
            messageStorageUtil.addInboundSecureHttpMessage(storage, metricReport);
        }

        final var mockObserver = mock(TestRunObserver.class);
        final var historian = historianFactory.createMdibHistorian(storage, mockObserver);

        try (final Stream<String> sequenceIdStream = historian.getKnownSequenceIds()) {
            assertEquals(Set.copyOf(sequenceIds), Set.copyOf(sequenceIdStream.toList()));
        }

        {
            // ensure only correct sequence id elements occur in the mdib
            final var sequence = sequenceIds.get(0);
            try (final var history = historian.episodicReportBasedHistory(sequence)) {
                var mdib = history.next();
                assertNotNull(mdib);
                while (mdib != null) {
                    assertEquals(sequence, mdib.getMdibVersion().getSequenceId());
                    mdib = history.next();
                }
            } catch (PreprocessingException | ReportProcessingException e) {
                fail(e);
            }
        }
        {
            final var sequence = sequenceIds.get(1);

            final var error = assertThrows(AssertionError.class, () -> {
                try (final var history = historian.episodicReportBasedHistory(sequence)) {
                    fail("Unreachable");
                }
            });
            assertTrue(error.getMessage().contains(MdibHistorian.NO_MDIB_ERROR));
        }
    }

    /**
     * Tests whether modifications with the same state version are applied.
     *
     * @throws Exception on any exception
     */
    @Test
    void testModificationsWithSameStateVersion() throws Exception {
        final var contextReportMdibVersion = BigInteger.ONE;
        final var contextReportStateVersion = BigInteger.ONE;
        final var firstCategory = mdibBuilder.buildCodedValue("1");
        final var contextReport = buildEpisodicContextReport(
                MdibBuilder.DEFAULT_SEQUENCE_ID, contextReportMdibVersion, contextReportStateVersion);
        final var firstReport =
                (EpisodicContextReport) contextReport.getBody().getAny().get(0);
        final var contextState = (PatientContextState)
                firstReport.getReportPart().get(0).getContextState().get(0);
        contextState.setCategory(firstCategory);

        final var secondContextReportMdibVersion = contextReportMdibVersion.add(BigInteger.ONE);
        final var secondCategory = mdibBuilder.buildCodedValue("2");
        final var secondContextReport = buildEpisodicContextReport(
                MdibBuilder.DEFAULT_SEQUENCE_ID, secondContextReportMdibVersion, contextReportStateVersion);
        final var secondReport =
                (EpisodicContextReport) secondContextReport.getBody().getAny().get(0);
        final var secondContextState = (PatientContextState)
                secondReport.getReportPart().get(0).getContextState().get(0);
        secondContextState.setCategory(secondCategory);

        messageStorageUtil.addInboundSecureHttpMessage(
                storage, buildMdibEnvelope(MdibBuilder.DEFAULT_SEQUENCE_ID, null));
        messageStorageUtil.addInboundSecureHttpMessage(storage, contextReport);
        messageStorageUtil.addInboundSecureHttpMessage(storage, secondContextReport);

        final var mockObserver = mock(TestRunObserver.class);
        final var historian = historianFactory.createMdibHistorian(storage, mockObserver);

        try (final var history = historian.episodicReportBasedHistory(MdibBuilder.DEFAULT_SEQUENCE_ID)) {
            assertNotNull(history.next());
            {
                final var contextReportMdib = history.next();
                assertEquals(
                        contextReportMdibVersion,
                        contextReportMdib.getMdibVersion().getVersion());
                assertEquals(
                        firstCategory.getCode(),
                        contextReportMdib
                                .getContextStates()
                                .get(0)
                                .getCategory()
                                .getCode());
            }
            {
                final var contextReportMdib = history.next();
                assertEquals(
                        secondContextReportMdibVersion,
                        contextReportMdib.getMdibVersion().getVersion());
                assertEquals(
                        secondCategory.getCode(),
                        contextReportMdib
                                .getContextStates()
                                .get(0)
                                .getCategory()
                                .getCode());
            }
            assertNull(history.next());
        }
    }

    /**
     * Verifies that two context descriptors can not have the same context state.
     *
     * @throws Exception on any exception.
     */
    @Test
    void testMultipleContextStates() throws Exception {
        final var operator1 = mdibBuilder.buildOperatorContextState("opDescriptor1", "opState");
        final var operator2 = mdibBuilder.buildOperatorContextState("opDescriptor2", "opState");

        final var contextReportMdibVersion = BigInteger.ONE;
        final var contextReportStateVersion = BigInteger.TWO;
        final var contextReport = buildEpisodicContextReport(
                MdibBuilder.DEFAULT_SEQUENCE_ID, contextReportMdibVersion, contextReportStateVersion);

        final var firstMod =
                (EpisodicContextReport) contextReport.getBody().getAny().get(0);
        firstMod.getReportPart().get(0).getContextState().add(operator1);
        firstMod.getReportPart().get(0).getContextState().add(operator2);

        messageStorageUtil.addInboundSecureHttpMessage(
                storage, buildMdibEnvelope(MdibBuilder.DEFAULT_SEQUENCE_ID, null));

        messageStorageUtil.addInboundSecureHttpMessage(storage, contextReport);

        final var mockObserver = mock(TestRunObserver.class);
        final var historian = historianFactory.createMdibHistorian(storage, mockObserver);

        try (final var history = historian.episodicReportBasedHistory(MdibBuilder.DEFAULT_SEQUENCE_ID)) {
            assertNotNull(history.next());
            {
                final var error = assertThrows(AssertionError.class, () -> history.next());
                assertEquals(RuntimeException.class, error.getCause().getClass());
            }
        }
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

    Envelope buildEpisodicComponentReport(
            final String sequenceId,
            final String handle,
            final @Nullable BigInteger mdibVersion,
            final BigInteger mdsVersion) {
        final var report = messageBuilder.buildEpisodicComponentReport(sequenceId);

        final var mdsState = mdibBuilder.buildMdsState(handle);
        mdsState.setStateVersion(mdsVersion);

        final var reportPart = messageBuilder.buildAbstractComponentReportReportPart();
        reportPart.getComponentState().add(mdsState);

        report.setMdibVersion(mdibVersion);
        report.getReportPart().add(reportPart);
        return messageBuilder.createSoapMessageWithBody(ActionConstants.ACTION_EPISODIC_COMPONENT_REPORT, report);
    }

    Envelope buildEpisodicMetricReport(
            final String sequenceId, final @Nullable BigInteger mdibVersion, final BigInteger metricVersion) {
        final var report = messageBuilder.buildEpisodicMetricReport(sequenceId);

        final var metricState = mdibBuilder.buildStringMetricState(STRING_METRIC_HANDLE);
        metricState.setStateVersion(metricVersion);

        final var reportPart = messageBuilder.buildAbstractMetricReportReportPart();
        reportPart.getMetricState().add(metricState);

        report.setMdibVersion(mdibVersion);
        report.getReportPart().add(reportPart);
        return messageBuilder.createSoapMessageWithBody(ActionConstants.ACTION_EPISODIC_METRIC_REPORT, report);
    }

    Envelope buildEpisodicAlertReport(
            final String sequenceId, final @Nullable BigInteger mdibVersion, final BigInteger stateVersion) {
        final var report = messageBuilder.buildEpisodicAlertReport(sequenceId);

        final var alertSystemState = mdibBuilder.buildAlertSystemState(ALERT_SYSTEM_HANDLE, AlertActivation.OFF);
        alertSystemState.setStateVersion(stateVersion);

        final var reportPart = messageBuilder.buildAbstractAlertReportReportPart();
        reportPart.getAlertState().add(alertSystemState);

        report.setMdibVersion(mdibVersion);
        report.getReportPart().add(reportPart);
        return messageBuilder.createSoapMessageWithBody(ActionConstants.ACTION_EPISODIC_ALERT_REPORT, report);
    }

    Envelope buildEpisodicContextReport(
            final String sequenceId, final @Nullable BigInteger mdibVersion, final BigInteger stateVersion) {
        final var report = messageBuilder.buildEpisodicContextReport(sequenceId);

        final var patientContextState =
                mdibBuilder.buildPatientContextState(PATIENT_CONTEXT_HANDLE, PATIENT_CONTEXT_STATE_HANDLE);
        patientContextState.setStateVersion(stateVersion);

        final var reportPart = messageBuilder.buildAbstractContextReportReportPart();
        reportPart.getContextState().add(patientContextState);

        report.setMdibVersion(mdibVersion);
        report.getReportPart().add(reportPart);
        return messageBuilder.createSoapMessageWithBody(ActionConstants.ACTION_EPISODIC_CONTEXT_REPORT, report);
    }

    Envelope buildEpisodicOperationalStateReport(
            final String sequenceId, final @Nullable BigInteger mdibVersion, final BigInteger stateVersion) {
        final var report = messageBuilder.buildEpisodicOperationalStateReport(sequenceId);

        final var setString = mdibBuilder.buildSetStringOperationState(SET_STRING_HANDLE, OperatingMode.EN);
        setString.setStateVersion(stateVersion);

        final var reportPart = messageBuilder.buildAbstractOperationalStateReportReportPart();
        reportPart.getOperationState().add(setString);

        report.setMdibVersion(mdibVersion);
        report.getReportPart().add(reportPart);
        return messageBuilder.createSoapMessageWithBody(
                ActionConstants.ACTION_EPISODIC_OPERATIONAL_STATE_REPORT, report);
    }
}
