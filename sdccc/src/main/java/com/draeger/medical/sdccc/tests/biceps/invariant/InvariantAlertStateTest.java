/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.tests.biceps.invariant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import com.draeger.medical.sdccc.configuration.EnabledTestConfig;
import com.draeger.medical.sdccc.manipulation.precondition.impl.ManipulationPreconditions;
import com.draeger.medical.sdccc.messages.MessageStorage;
import com.draeger.medical.sdccc.sdcri.testclient.TestClient;
import com.draeger.medical.sdccc.tests.InjectorTestBase;
import com.draeger.medical.sdccc.tests.annotations.RequirePrecondition;
import com.draeger.medical.sdccc.tests.annotations.TestDescription;
import com.draeger.medical.sdccc.tests.annotations.TestIdentifier;
import com.draeger.medical.sdccc.tests.util.ImpliedValueUtil;
import com.draeger.medical.sdccc.tests.util.MdibHistorian;
import com.draeger.medical.sdccc.tests.util.NoTestData;
import com.draeger.medical.sdccc.tests.util.guice.MdibHistorianFactory;
import com.draeger.medical.sdccc.util.TestRunObserver;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.somda.sdc.biceps.common.storage.PreprocessingException;
import org.somda.sdc.biceps.consumer.access.RemoteMdibAccess;
import org.somda.sdc.biceps.model.participant.AbstractAlertState;
import org.somda.sdc.biceps.model.participant.AlertActivation;
import org.somda.sdc.biceps.model.participant.AlertConditionState;
import org.somda.sdc.biceps.model.participant.AlertSystemState;
import org.somda.sdc.glue.consumer.report.ReportProcessingException;

/**
 * BICEPS alert state tests (ch. 5.4.9).
 */
public class InvariantAlertStateTest extends InjectorTestBase {
    public static final String NO_PRESENCE_TRUE = "AlertConditionState/@Presence true was never seen, test failed.";
    public static final String NO_ACCEPTABLE_SEQUENCE_SEEN = "Not enough AlertSystemStates or children seen with the"
            + " AlertSystemState set to PSD or OFF during test run, test failed.";

    private MessageStorage messageStorage;
    private MdibHistorianFactory mdibHistorianFactory;

    @BeforeEach
    void setup() {
        this.messageStorage = getInjector().getInstance(MessageStorage.class);
        final var riInjector = getInjector().getInstance(TestClient.class).getInjector();
        this.mdibHistorianFactory = riInjector.getInstance(MdibHistorianFactory.class);
    }

    @Test
    @DisplayName("For each pm:AlertConditionState, while @Presence = true, a SERVICE PROVIDER SHALL set this"
            + " pm:AlertConditionState/@ActivationState = On and the pm:AlertSystemState/@ActivationState = On of the"
            + " ALERT SYSTEM that detected this ALERT CONDITION.")
    @TestIdentifier(EnabledTestConfig.BICEPS_R0029_0)
    @TestDescription("Starting from the initially retrieved mdib, applies every episodic report to the mdib and"
            + " verifies that the activation state of an alert condition state and the activation state of the parent"
            + " alert system state are both set to 'On', when the presence attribute of the alert condition state is"
            + " set to 'true'.")
    @RequirePrecondition(
            manipulationPreconditions = {ManipulationPreconditions.AlertConditionPresenceManipulation.class})
    void testRequirementR00290() throws NoTestData, IOException {
        final var mdibHistorian = mdibHistorianFactory.createMdibHistorian(
                messageStorage, getInjector().getInstance(TestRunObserver.class));

        final var presenceOnSeen = new AtomicInteger(0);
        try (final Stream<String> sequenceIds = mdibHistorian.getKnownSequenceIds()) {
            sequenceIds.forEach(sequenceId -> {
                try (final MdibHistorian.HistorianResult history =
                        mdibHistorian.episodicReportBasedHistory(sequenceId)) {

                    RemoteMdibAccess mdibAccess = history.next();

                    while (mdibAccess != null) {
                        final var alertConditionStates = mdibAccess.getStatesByType(AlertConditionState.class);
                        for (var alertConditionState : alertConditionStates) {
                            final var isPresence = ImpliedValueUtil.isPresence(alertConditionState);
                            if (isPresence) {
                                presenceOnSeen.incrementAndGet();
                                final var descriptorHandle = alertConditionState.getDescriptorHandle();
                                final var alertSystemStateHandle = mdibAccess
                                        .getEntity(descriptorHandle)
                                        .orElseThrow()
                                        .getParent()
                                        .orElseThrow();
                                final var alertSystemState = mdibAccess
                                        .getState(alertSystemStateHandle, AlertSystemState.class)
                                        .orElseThrow();

                                assertEquals(
                                        AlertActivation.ON,
                                        alertConditionState.getActivationState(),
                                        String.format(
                                                "AlertConditionState/@Presence is true, for AlertConditionState with handle %s."
                                                        + "The AlertConditionState/@Activation state should be 'On' but is '%s'",
                                                descriptorHandle,
                                                alertConditionState
                                                        .getActivationState()
                                                        .value()));
                                assertEquals(
                                        AlertActivation.ON,
                                        alertSystemState.getActivationState(),
                                        String.format(
                                                "AlertConditionState/@Presence is true, for AlertConditionState with handle %s."
                                                        + " The AlertSystemState/@Activation "
                                                        + "state for AlertSystemState with handle %s"
                                                        + " should be 'On' but is '%s'",
                                                descriptorHandle,
                                                alertSystemStateHandle,
                                                alertSystemState
                                                        .getActivationState()
                                                        .value()));
                            }
                        }
                        mdibAccess = history.next();
                    }
                } catch (PreprocessingException | ReportProcessingException e) {
                    fail(e);
                }
            });
        }
        assertTestData(presenceOnSeen.get(), NO_PRESENCE_TRUE);
    }

    @Test
    @TestIdentifier(EnabledTestConfig.BICEPS_R0116)
    @TestDescription("Starting from the initially retrieved mdib, applies every episodic report to the mdib and"
            + " verifies that the activation states of "
            + "pm:AlertConditionState and pm:AlertSignalState are set according to"
            + " the following: AlertSystemState/@ActivationState "
            + "'Off' implies AlertConditionState/@ActivationState 'Off'"
            + " and AlertSignalState/@ActivationState 'Off'. AlertSystemState/@ActivationState 'Psd' implies"
            + " AlertConditionState/@ActivationState 'Psd' and AlertSignalState/@ActivationState 'Psd'."
            + " The case when AlertSystemState/@ActivationState is 'On' does not need to be checked, because then any"
            + " value is permitted for AlertConditionState/@ActivationState and AlertSignalState/@ActivationState.")
    @RequirePrecondition(
            manipulationPreconditions = {ManipulationPreconditions.AlertSystemActivationStateManipulation.class})
    void testRequirementR0116() throws NoTestData, IOException {
        final var mdibHistorian = mdibHistorianFactory.createMdibHistorian(
                messageStorage, getInjector().getInstance(TestRunObserver.class));

        final var acceptableSequenceSeen = new AtomicInteger(0);

        try (final Stream<String> sequenceIds = mdibHistorian.getKnownSequenceIds()) {
            sequenceIds.forEach(sequenceId -> {
                try (final MdibHistorian.HistorianResult history =
                        mdibHistorian.episodicReportBasedHistory(sequenceId)) {
                    RemoteMdibAccess first = history.next();

                    final var alertActivationStateOffSeen =
                            initAlertSystemStateMap(first.getStatesByType(AlertSystemState.class));
                    final var alertActivationStatePsdSeen =
                            initAlertSystemStateMap(first.getStatesByType(AlertSystemState.class));

                    while (first != null) {
                        final var alertSystemStates = first.getStatesByType(AlertSystemState.class);

                        for (var alertSystemState : alertSystemStates) {
                            final var activationState = alertSystemState.getActivationState();
                            final var descriptorHandle = alertSystemState.getDescriptorHandle();

                            final var children = first.getEntity(descriptorHandle)
                                    .orElseThrow()
                                    .getChildren();
                            final List<AbstractAlertState> abstractAlertStates = new ArrayList<>();
                            for (var child : children) {
                                abstractAlertStates.addAll(
                                        first.getEntity(child).orElseThrow().getStates(AbstractAlertState.class));
                            }
                            if (activationState.equals(AlertActivation.OFF)
                                    || activationState.equals(AlertActivation.PSD)) {
                                if (activationState.equals(AlertActivation.OFF)) {
                                    alertActivationStateOffSeen
                                            .computeIfAbsent(descriptorHandle, handle -> new AtomicInteger(0))
                                            .incrementAndGet();
                                } else {
                                    alertActivationStatePsdSeen
                                            .computeIfAbsent(descriptorHandle, handle -> new AtomicInteger(0))
                                            .incrementAndGet();
                                }
                                for (var state : abstractAlertStates) {
                                    assertEquals(
                                            activationState,
                                            state.getActivationState(),
                                            String.format(
                                                    "The activation state of %s should be: %s but is: %s.",
                                                    state.getDescriptorHandle(),
                                                    activationState,
                                                    state.getActivationState()));
                                }
                            }
                        }
                        first = history.next();
                    }
                    if (verifyActivationStatesWereSeen(alertActivationStatePsdSeen, AlertActivation.PSD)
                            && verifyActivationStatesWereSeen(alertActivationStateOffSeen, AlertActivation.OFF)) {
                        acceptableSequenceSeen.incrementAndGet();
                    }
                } catch (PreprocessingException | ReportProcessingException | NoTestData e) {
                    fail(e);
                }
            });
        }
        assertTestData(acceptableSequenceSeen.get(), NO_ACCEPTABLE_SEQUENCE_SEEN);
    }

    private Map<String, AtomicInteger> initAlertSystemStateMap(final List<AlertSystemState> alertSystemStates) {
        final var map = new HashMap<String, AtomicInteger>();
        for (var state : alertSystemStates) {
            if (!map.containsKey(state.getDescriptorHandle())) {
                map.put(state.getDescriptorHandle(), new AtomicInteger(0));
            }
        }
        return map;
    }

    private boolean verifyActivationStatesWereSeen(
            final Map<String, AtomicInteger> seenMap, @Nullable final AlertActivation state) throws NoTestData {
        if (state != null) {
            for (var seen : seenMap.entrySet()) {
                if (seen.getValue().get() <= 0) {
                    return false;
                }
            }
        }
        return seenMap.size() > 0;
    }
}
