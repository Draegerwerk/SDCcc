/*
 * This Source Code Form is subject to the terms of the "SDCcc non-commercial use license".
 *
 * Copyright (C) 2025 Draegerwerk AG & Co. KGaA
 */

package com.draeger.medical.sdccc.tests.glue.invariant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

import com.draeger.medical.sdccc.configuration.EnabledTestConfig;
import com.draeger.medical.sdccc.manipulation.precondition.impl.ConditionalPreconditions;
import com.draeger.medical.sdccc.messages.MessageStorage;
import com.draeger.medical.sdccc.messages.mapping.MessageContent;
import com.draeger.medical.sdccc.sdcri.testclient.TestClient;
import com.draeger.medical.sdccc.tests.InjectorTestBase;
import com.draeger.medical.sdccc.tests.annotations.RequirePrecondition;
import com.draeger.medical.sdccc.tests.annotations.TestDescription;
import com.draeger.medical.sdccc.tests.annotations.TestIdentifier;
import com.draeger.medical.sdccc.tests.util.ImpliedValueUtil;
import com.draeger.medical.sdccc.tests.util.InitialImpliedValue;
import com.draeger.medical.sdccc.tests.util.InitialImpliedValueException;
import com.draeger.medical.sdccc.tests.util.MdibHistorian;
import com.draeger.medical.sdccc.tests.util.NoTestData;
import com.draeger.medical.sdccc.tests.util.guice.MdibHistorianFactory;
import com.draeger.medical.sdccc.util.Constants;
import com.draeger.medical.sdccc.util.TestRunObserver;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.xml.namespace.QName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.somda.sdc.biceps.model.message.AbstractReport;
import org.somda.sdc.biceps.model.message.DescriptionModificationReport;
import org.somda.sdc.biceps.model.message.DescriptionModificationType;
import org.somda.sdc.biceps.model.message.EpisodicAlertReport;
import org.somda.sdc.biceps.model.message.EpisodicComponentReport;
import org.somda.sdc.biceps.model.message.EpisodicContextReport;
import org.somda.sdc.biceps.model.message.EpisodicMetricReport;
import org.somda.sdc.biceps.model.message.EpisodicOperationalStateReport;
import org.somda.sdc.biceps.model.message.ObservedValueStream;
import org.somda.sdc.biceps.model.message.WaveformStream;
import org.somda.sdc.biceps.model.participant.AbstractState;
import org.somda.sdc.biceps.model.participant.DistributionSampleArrayMetricState;
import org.somda.sdc.biceps.model.participant.RealTimeSampleArrayMetricState;
import org.somda.sdc.biceps.model.participant.SampleArrayValue;
import org.somda.sdc.dpws.soap.MarshallingService;
import org.somda.sdc.dpws.soap.SoapMessage;
import org.somda.sdc.dpws.soap.SoapUtil;
import org.somda.sdc.dpws.soap.exception.MarshallingException;

/**
 * Glue subscription handling tests.
 */
public class InvariantSubscriptionHandlingTest extends InjectorTestBase {

    private MessageStorage messageStorage;
    private MdibHistorianFactory historianFactory;
    private MarshallingService marshalling;
    private SoapUtil soapUtil;

    @BeforeEach
    void setup() {
        this.messageStorage = getInjector().getInstance(MessageStorage.class);
        final var riInjector = getInjector().getInstance(TestClient.class).getInjector();
        this.historianFactory = riInjector.getInstance(MdibHistorianFactory.class);
        this.marshalling = riInjector.getInstance(MarshallingService.class);
        this.soapUtil = riInjector.getInstance(SoapUtil.class);
    }

    @Test
    @TestIdentifier(EnabledTestConfig.GLUE_R0056)
    @TestDescription("Retrieves each DescriptionModificationReport seen during the test run and verifies that no"
            + " EpisodicAlertReport, EpisodicComponentReport, EpisodicMetricReport, EpisodicOperationalStateReport,"
            + " WaveformStream, ObservedValueStream, or EpisodicContextReport containing the same states were sent before"
            + " the DescriptionModificationReport. "
            + " Note: For this test it is necessary that the order of the messages is assured.")
    @RequirePrecondition(simplePreconditions = {ConditionalPreconditions.DescriptionModificationUptPrecondition.class})
    void testRequirementR0056() throws NoTestData, IOException {
        final var historian = historianFactory.createMdibHistorian(
                messageStorage, getInjector().getInstance(TestRunObserver.class));
        final var acceptableSequenceSeen = new AtomicBoolean(false);

        try (final var sequenceIds = messageStorage.getUniqueSequenceIds().filter(Objects::nonNull)) {
            sequenceIds.forEach(sequenceId -> {
                final var impliedValueMap = new InitialImpliedValue();
                try (final var messages = messageStorage.getInboundMessagesByBodyTypeAndSequenceId(
                        sequenceId, Constants.MSG_DESCRIPTION_MODIFICATION_REPORT)) {

                    messages.getStream()
                            .map(this::getDescriptionModificationReportFromMessageContent)
                            .forEach(report -> {
                                final var reportParts = report.orElseThrow().getReportPart();
                                final var mdibVersion = ImpliedValueUtil.getReportMdibVersion(report.orElseThrow());
                                for (var part : reportParts) {
                                    if (ImpliedValueUtil.getModificationType(part) != DescriptionModificationType.DEL
                                            && !part.getState().isEmpty()) {
                                        acceptableSequenceSeen.set(true);
                                        checkReportsBeforeDescriptionModification(
                                                historian, sequenceId, mdibVersion, part, impliedValueMap);
                                    }
                                }
                            });
                } catch (IOException e) {
                    fail(e);
                    // unreachable, silence warnings
                    throw new RuntimeException(e);
                }
            });
        }

        assertTestData(
                acceptableSequenceSeen.get(),
                "No DescriptionModificationReports seen during the test run, test failed.");
    }

    private Optional<DescriptionModificationReport> getDescriptionModificationReportFromMessageContent(
            final MessageContent content) {
        final var body = content.getBody();

        final SoapMessage message;
        try {
            message = marshalling.unmarshal(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
        } catch (MarshallingException e) {
            fail("Could not unmarshal message", e);
            // unreachable, silence warnings
            throw new RuntimeException(e);
        }
        return soapUtil.getBody(message, DescriptionModificationReport.class);
    }

    private void checkReportsBeforeDescriptionModification(
            final MdibHistorian historian,
            final String sequenceId,
            final BigInteger mdibVersion,
            final DescriptionModificationReport.ReportPart descriptionModificationReportPart,
            final InitialImpliedValue impliedValueMap) {

        final var relevantReportTypes = List.of(
                Constants.MSG_EPISODIC_ALERT_REPORT,
                Constants.MSG_EPISODIC_COMPONENT_REPORT,
                Constants.MSG_EPISODIC_METRIC_REPORT,
                Constants.MSG_EPISODIC_OPERATIONAL_STATE_REPORT,
                Constants.MSG_WAVEFORM_STREAM,
                Constants.MSG_OBSERVED_VALUE_STREAM,
                Constants.MSG_EPISODIC_CONTEXT_REPORT);

        try (final var relevantReports = historian.getAllReportsWithLowerMdibVersion(
                sequenceId, mdibVersion, relevantReportTypes.toArray(QName[]::new))) {
            relevantReports.forEach(report -> {
                final var statesFromDescriptionModification = descriptionModificationReportPart.getState();

                if (report instanceof ObservedValueStream observedValueStream) {
                    for (var value : observedValueStream.getValue()) {
                        final var handle = value.getMetric();
                        final var stateVersion = ImpliedValueUtil.getValueStateVersion(value);
                        final var sampleArrayValue = value.getValue();
                        try {
                            assertFalse(
                                    compareDescriptionModificationWithSampleArrayValue(
                                            handle,
                                            stateVersion,
                                            sampleArrayValue,
                                            statesFromDescriptionModification,
                                            impliedValueMap),
                                    String.format(
                                            "The description modification report with mdib version %s containing the changed states"
                                                    + " should be send before the observed value stream with mdib version %s",
                                            mdibVersion, ImpliedValueUtil.getReportMdibVersion(report)));
                        } catch (InitialImpliedValueException e) {
                            fail(e);
                            // unreachable, silence warnings
                            throw new RuntimeException(e);
                        }
                    }
                } else {
                    final var statesFromReport = getStatesFromRelevantReports(report);
                    assertFalse(
                            compareStates(statesFromReport, statesFromDescriptionModification),
                            String.format(
                                    "The description modification report with mdib version %s containing the changed states"
                                            + " should be send before the %s with mdib version %s",
                                    mdibVersion,
                                    report.getClass().getSimpleName(),
                                    ImpliedValueUtil.getReportMdibVersion(report)));
                }
            });
        }
    }

    private List<AbstractState> getStatesFromRelevantReports(final AbstractReport report) {
        final List<AbstractState> states = new ArrayList<>();
        if (report instanceof EpisodicAlertReport alertReport) {
            for (var part : alertReport.getReportPart()) {
                states.addAll(part.getAlertState());
            }
        }
        if (report instanceof EpisodicComponentReport componentReport) {
            for (var part : componentReport.getReportPart()) {
                states.addAll(part.getComponentState());
            }
        }
        if (report instanceof EpisodicMetricReport metricReport) {
            for (var part : metricReport.getReportPart()) {
                states.addAll(part.getMetricState());
            }
        }
        if (report instanceof EpisodicOperationalStateReport operationalStateReport) {
            for (var part : operationalStateReport.getReportPart()) {
                states.addAll(part.getOperationState());
            }
        }
        if (report instanceof WaveformStream waveformStream) {
            states.addAll(waveformStream.getState());
        }
        if (report instanceof EpisodicContextReport contextReport) {
            for (var part : contextReport.getReportPart()) {
                states.addAll(part.getContextState());
            }
        }
        return states;
    }

    private boolean compareDescriptionModificationWithSampleArrayValue(
            final String handle,
            final BigInteger stateVersion,
            final SampleArrayValue sampleArrayValue,
            final List<AbstractState> statesFromDescriptionModification,
            final InitialImpliedValue impliedValueMap)
            throws InitialImpliedValueException {
        boolean foundState = false;
        for (var state : statesFromDescriptionModification) {
            if (state.getDescriptorHandle().equals(handle)
                    && ImpliedValueUtil.getStateVersion(state, impliedValueMap).equals(stateVersion)
                    && ((state instanceof RealTimeSampleArrayMetricState rtsaMetric
                                    && Optional.ofNullable(rtsaMetric.getMetricValue())
                                            .map(it -> it.equals(sampleArrayValue))
                                            .orElse(false))
                            || (state instanceof DistributionSampleArrayMetricState dsaMetric
                                    && Optional.ofNullable(dsaMetric.getMetricValue())
                                            .map(it -> it.equals(sampleArrayValue))
                                            .orElse(false)))) {
                foundState = true;
            }
        }
        return foundState;
    }

    private boolean compareStates(
            final List<AbstractState> statesFromReport, final List<AbstractState> statesFromDescriptionModification) {
        return statesFromReport.stream().anyMatch(statesFromDescriptionModification::contains);
    }
}
