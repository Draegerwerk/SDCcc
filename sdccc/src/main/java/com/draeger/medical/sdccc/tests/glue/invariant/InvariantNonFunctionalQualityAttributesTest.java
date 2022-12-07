/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.tests.glue.invariant;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.draeger.medical.sdccc.configuration.EnabledTestConfig;
import com.draeger.medical.sdccc.messages.MessageStorage;
import com.draeger.medical.sdccc.sdcri.testclient.TestClient;
import com.draeger.medical.sdccc.tests.InjectorTestBase;
import com.draeger.medical.sdccc.tests.annotations.TestDescription;
import com.draeger.medical.sdccc.tests.annotations.TestIdentifier;
import com.draeger.medical.sdccc.tests.util.ImpliedValueUtil;
import com.draeger.medical.sdccc.tests.util.MdibHistorian;
import com.draeger.medical.sdccc.tests.util.NoTestData;
import com.draeger.medical.sdccc.tests.util.guice.MdibHistorianFactory;
import com.draeger.medical.sdccc.util.TestRunObserver;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.somda.sdc.biceps.common.storage.PreprocessingException;
import org.somda.sdc.biceps.consumer.access.RemoteMdibAccess;
import org.somda.sdc.biceps.model.participant.AbstractContextState;
import org.somda.sdc.biceps.model.participant.AbstractMetricDescriptor;
import org.somda.sdc.biceps.model.participant.AlertConditionState;
import org.somda.sdc.biceps.model.participant.ClockDescriptor;
import org.somda.sdc.biceps.model.participant.ClockState;
import org.somda.sdc.biceps.model.participant.DistributionSampleArrayMetricDescriptor;
import org.somda.sdc.biceps.model.participant.DistributionSampleArrayMetricState;
import org.somda.sdc.biceps.model.participant.EnumStringMetricDescriptor;
import org.somda.sdc.biceps.model.participant.EnumStringMetricState;
import org.somda.sdc.biceps.model.participant.MdsDescriptor;
import org.somda.sdc.biceps.model.participant.NumericMetricDescriptor;
import org.somda.sdc.biceps.model.participant.NumericMetricState;
import org.somda.sdc.biceps.model.participant.RealTimeSampleArrayMetricDescriptor;
import org.somda.sdc.biceps.model.participant.RealTimeSampleArrayMetricState;
import org.somda.sdc.biceps.model.participant.StringMetricDescriptor;
import org.somda.sdc.biceps.model.participant.StringMetricState;
import org.somda.sdc.glue.consumer.report.ReportProcessingException;

/**
 * Glue Non-functional quality attributes tests (ch. 10).
 */
public class InvariantNonFunctionalQualityAttributesTest extends InjectorTestBase {
    private MessageStorage messageStorage;
    private MdibHistorianFactory mdibHistorianFactory;

    @BeforeEach
    void setup() {
        this.messageStorage = getInjector().getInstance(MessageStorage.class);
        final var riInjector = getInjector().getInstance(TestClient.class).getInjector();
        this.mdibHistorianFactory = riInjector.getInstance(MdibHistorianFactory.class);
    }

    @Test
    @DisplayName("R0010_0: For all of its MDSs an SDC SERVICE PROVIDER SHALL provide a"
            + " pm:ClockDescriptor and pm:ClockState.")
    @TestIdentifier(EnabledTestConfig.GLUE_R0010_0)
    @TestDescription("Starting from the initially retrieved mdib, applies each episodic report to the mdib and"
            + " verifies for each mds that a clock descriptor and a clock state are present.")
    void testRequirementR0010() throws NoTestData, IOException {
        final var mdibHistorian = mdibHistorianFactory.createMdibHistorian(
                messageStorage, getInjector().getInstance(TestRunObserver.class));

        final var acceptableSequenceSeen = new AtomicInteger(0);

        try (final Stream<String> sequenceIds = mdibHistorian.getKnownSequenceIds()) {
            sequenceIds.forEach(sequenceId -> {
                try (final MdibHistorian.HistorianResult history =
                        mdibHistorian.episodicReportBasedHistory(sequenceId)) {
                    RemoteMdibAccess remoteMdibAccess = history.next();

                    while (remoteMdibAccess != null) {
                        final var entities = remoteMdibAccess.findEntitiesByType(MdsDescriptor.class);

                        for (var entity : entities) {
                            acceptableSequenceSeen.incrementAndGet();
                            final var children = entity.getChildren();
                            final var clockDescriptorSeen = new AtomicBoolean(false);
                            for (var child : children) {
                                final var clockOpt = remoteMdibAccess.getDescriptor(child, ClockDescriptor.class);
                                if (clockOpt.isPresent()) {
                                    clockDescriptorSeen.set(true);
                                    final var clockStateOpt = remoteMdibAccess.getState(child, ClockState.class);
                                    assertTrue(
                                            clockStateOpt.isPresent(),
                                            String.format(
                                                    "No clock state present for mds with handle %s.",
                                                    entity.getHandle()));
                                }
                            }
                            assertTrue(
                                    clockDescriptorSeen.get(),
                                    String.format(
                                            "No clock descriptor present for mds with handle %s.", entity.getHandle()));
                        }
                        remoteMdibAccess = history.next();
                    }
                } catch (PreprocessingException | ReportProcessingException e) {
                    fail(e);
                }
            });
        }
        assertTestData(acceptableSequenceSeen.get(), "No mds seen during test run, test failed.");
    }

    @Test
    @TestIdentifier(EnabledTestConfig.GLUE_R0011)
    @TestDescription("Starting from the initially retrieved mdib, applies every episodic report to the mdib and "
            + "verifies for each metric that if a value is present, a timestamp is also present.")
    void testRequirementR0011() throws NoTestData, IOException {
        final var mdibHistorian = mdibHistorianFactory.createMdibHistorian(
                messageStorage, getInjector().getInstance(TestRunObserver.class));

        final var acceptableSequenceSeen = new AtomicInteger(0);

        try (final Stream<String> sequenceIds = mdibHistorian.getKnownSequenceIds()) {
            sequenceIds.forEach(sequenceId -> {
                try (final MdibHistorian.HistorianResult history =
                        mdibHistorian.episodicReportBasedHistory(sequenceId)) {

                    RemoteMdibAccess remoteMdibAccess = history.next();

                    while (remoteMdibAccess != null) {
                        final var entities = remoteMdibAccess.findEntitiesByType(AbstractMetricDescriptor.class);

                        for (var entity : entities) {

                            if (entity.getDescriptor() instanceof RealTimeSampleArrayMetricDescriptor) {
                                final var metricValue = entity.getFirstState(RealTimeSampleArrayMetricState.class)
                                        .orElseThrow()
                                        .getMetricValue();

                                if (metricValue != null
                                        && !metricValue.getSamples().isEmpty()) {
                                    acceptableSequenceSeen.incrementAndGet();

                                    assertNotNull(
                                            metricValue.getDeterminationTime(),
                                            String.format(
                                                    "No DeterminationTime for the metric with the handle %s "
                                                            + "even though it has a non-empty sample attribute.",
                                                    entity.getHandle()));
                                }
                            } else if (entity.getDescriptor() instanceof DistributionSampleArrayMetricDescriptor) {
                                final var metricValue = entity.getFirstState(DistributionSampleArrayMetricState.class)
                                        .orElseThrow()
                                        .getMetricValue();

                                if (metricValue != null
                                        && !metricValue.getSamples().isEmpty()) {
                                    acceptableSequenceSeen.incrementAndGet();

                                    assertNotNull(
                                            metricValue.getDeterminationTime(),
                                            String.format(
                                                    "No DeterminationTime for the metric with the handle %s "
                                                            + "even though it has a non-empty sample attribute.",
                                                    entity.getHandle()));
                                }
                            } else if (entity.getDescriptor() instanceof NumericMetricDescriptor) {
                                final var metricValue = entity.getFirstState(NumericMetricState.class)
                                        .orElseThrow()
                                        .getMetricValue();

                                if (metricValue != null && metricValue.getValue() != null) {
                                    acceptableSequenceSeen.incrementAndGet();

                                    assertNotNull(
                                            metricValue.getDeterminationTime(),
                                            String.format(
                                                    "No DeterminationTime for the metric with the handle %s "
                                                            + "even though it has a non-empty value attribute.",
                                                    entity.getHandle()));
                                }
                            } else if (entity.getDescriptor() instanceof EnumStringMetricDescriptor) {
                                final var metricValue = entity.getFirstState(EnumStringMetricState.class)
                                        .orElseThrow()
                                        .getMetricValue();

                                if (metricValue != null && metricValue.getValue() != null) {
                                    acceptableSequenceSeen.incrementAndGet();

                                    assertNotNull(
                                            metricValue.getDeterminationTime(),
                                            String.format(
                                                    "No DeterminationTime for the metric with the handle %s "
                                                            + "even though it has a non-empty value attribute.",
                                                    entity.getHandle()));
                                }
                            } else if (entity.getDescriptor() instanceof StringMetricDescriptor) {
                                final var metricValue = entity.getFirstState(StringMetricState.class)
                                        .orElseThrow()
                                        .getMetricValue();

                                if (metricValue != null && metricValue.getValue() != null) {
                                    acceptableSequenceSeen.incrementAndGet();

                                    assertNotNull(
                                            metricValue.getDeterminationTime(),
                                            String.format(
                                                    "No DeterminationTime for the metric with the handle %s "
                                                            + "even though it has a non-empty value attribute.",
                                                    entity.getHandle()));
                                }
                            } else {
                                fail(String.format(
                                        "Object of type %s is not supported by the test.",
                                        entity.getDescriptor().getClass()));
                            }
                        }

                        remoteMdibAccess = history.next();
                    }
                } catch (PreprocessingException | ReportProcessingException e) {
                    fail(e);
                }
            });
        }
        assertTestData(acceptableSequenceSeen.get(), "No metric with a value has been seen.");
    }

    @Test
    @DisplayName("R0012_0_0: When pm:AlertConditionState/@Presence changes, an SDC SERVICE PROVIDER"
            + " SHALL update pm:AlertConditionState/@DeterminationTime.")
    @TestIdentifier(EnabledTestConfig.GLUE_R0012_0_0)
    @TestDescription("Starting from the initially retrieved mdib, applies every episodic report to the mdib"
            + " and verifies for every alert condition state, that its @DeterminationTime is updated"
            + " whenever its @Presence changes.")
    void testRequirementR001200() throws NoTestData, IOException {
        final var mdibHistorian = mdibHistorianFactory.createMdibHistorian(
                messageStorage, getInjector().getInstance(TestRunObserver.class));

        final var acceptableSequenceSeen = new AtomicInteger(0);

        try (final Stream<String> sequenceIds = mdibHistorian.getKnownSequenceIds()) {
            sequenceIds.forEach(sequenceId -> {
                try (final MdibHistorian.HistorianResult history =
                                mdibHistorian.episodicReportBasedHistory(sequenceId);
                        final MdibHistorian.HistorianResult prevHistory =
                                mdibHistorian.episodicReportBasedHistory(sequenceId)) {
                    history.next(); // history must be one element ahead of prevHistory
                    RemoteMdibAccess last = prevHistory.next();
                    RemoteMdibAccess current = history.next();

                    while (current != null) {
                        final var currentAlertConditionStates = current.getStatesByType(AlertConditionState.class);

                        for (var currentAlertConditionState : currentAlertConditionStates) {
                            acceptableSequenceSeen.incrementAndGet();
                            final Optional<AlertConditionState> lastAlertConditionState = last.getState(
                                    currentAlertConditionState.getDescriptorHandle(), AlertConditionState.class);
                            if (lastAlertConditionState.isEmpty()) {
                                continue;
                            }

                            if (ImpliedValueUtil.isPresence(currentAlertConditionState)
                                    != ImpliedValueUtil.isPresence(lastAlertConditionState.orElseThrow())) {
                                assertNotEquals(
                                        currentAlertConditionState.getDeterminationTime(),
                                        lastAlertConditionState.orElseThrow().getDeterminationTime(),
                                        String.format(
                                                "The AlertConditionState with descriptor handle '%s' has changed "
                                                        + "its @Presence attribute from mdibVersion '%s' to mdibVersion '%s', "
                                                        + "but its @DeterminationTime was not updated ('%s' in both cases).",
                                                currentAlertConditionState.getDescriptorHandle(),
                                                ImpliedValueUtil.getMdibVersion(last.getMdibVersion())
                                                        .toString(),
                                                ImpliedValueUtil.getMdibVersion(current.getMdibVersion())
                                                        .toString(),
                                                currentAlertConditionState
                                                        .getDeterminationTime()
                                                        .toString()));
                            }
                        }
                        last = prevHistory.next();
                        current = history.next();
                    }

                } catch (PreprocessingException | ReportProcessingException e) {
                    fail(e);
                }
            });
        }
        assertTestData(acceptableSequenceSeen.get(), "No AlertConditionState seen during the test run, test failed.");
    }

    @Test
    @TestIdentifier(EnabledTestConfig.GLUE_R0013)
    @TestDescription("Starting from the initially retrieved mdib, applies every episodic report to the mdib and"
            + " verifies for every context state, that the BindingStartTime is set, when the BindingMdibVersion is"
            + " present.")
    void testRequirementR0013() throws NoTestData, IOException {
        final var mdibHistorian = mdibHistorianFactory.createMdibHistorian(
                messageStorage, getInjector().getInstance(TestRunObserver.class));

        final var acceptableSequenceSeen = new AtomicInteger(0);

        try (final Stream<String> sequenceIds = mdibHistorian.getKnownSequenceIds()) {
            sequenceIds.forEach(sequenceId -> {
                try (final MdibHistorian.HistorianResult history =
                        mdibHistorian.episodicReportBasedHistory(sequenceId)) {

                    RemoteMdibAccess first = history.next();

                    while (first != null) {
                        final var contextStates = first.getStatesByType(AbstractContextState.class);
                        for (var contextState : contextStates) {
                            final var bindingMdibVersion = contextState.getBindingMdibVersion();
                            if (bindingMdibVersion != null) {
                                final var bindingStartTime = contextState.getBindingStartTime();
                                assertNotNull(
                                        bindingStartTime,
                                        String.format(
                                                "The binding start time should not be null for state %s.",
                                                contextState.getHandle()));
                                acceptableSequenceSeen.incrementAndGet();
                            }
                        }
                        first = history.next();
                    }

                } catch (PreprocessingException | ReportProcessingException e) {
                    fail(e);
                }
            });
        }
        assertTestData(acceptableSequenceSeen.get(), "No suitable context states seen, test failed.");
    }

    @Test
    @TestIdentifier(EnabledTestConfig.GLUE_R0072)
    @TestDescription("Starting from the initially retrieved mdib, applies every episodic report to the mdib and"
            + " verifies for every context state, that the BindingEndTime is set, when the UnbindingMdibVersion is"
            + " present.")
    void testRequirementR0072() throws NoTestData, IOException {
        final var mdibHistorian = mdibHistorianFactory.createMdibHistorian(
                messageStorage, getInjector().getInstance(TestRunObserver.class));

        final var acceptableSequenceSeen = new AtomicInteger(0);

        try (final Stream<String> sequenceIds = mdibHistorian.getKnownSequenceIds()) {
            sequenceIds.forEach(sequenceId -> {
                try (final MdibHistorian.HistorianResult history =
                        mdibHistorian.episodicReportBasedHistory(sequenceId)) {

                    RemoteMdibAccess first = history.next();

                    while (first != null) {
                        final var contextStates = first.getStatesByType(AbstractContextState.class);
                        for (var contextState : contextStates) {
                            final var unbindingMdibVersion = contextState.getUnbindingMdibVersion();
                            if (unbindingMdibVersion != null) {
                                final var bindingEndTime = contextState.getBindingEndTime();
                                assertNotNull(
                                        bindingEndTime,
                                        String.format(
                                                "The binding end time should not be null for state %s.",
                                                contextState.getHandle()));
                                acceptableSequenceSeen.incrementAndGet();
                            }
                        }
                        first = history.next();
                    }

                } catch (PreprocessingException | ReportProcessingException e) {
                    fail(e);
                }
            });
        }
        assertTestData(acceptableSequenceSeen.get(), "No suitable context states seen, test failed.");
    }
}
