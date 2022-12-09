/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.tests.biceps.invariant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.somda.sdc.biceps.common.storage.PreprocessingException;
import org.somda.sdc.biceps.consumer.access.RemoteMdibAccess;
import org.somda.sdc.biceps.model.participant.AlertActivation;
import org.somda.sdc.biceps.model.participant.AlertSignalDescriptor;
import org.somda.sdc.biceps.model.participant.AlertSignalManifestation;
import org.somda.sdc.biceps.model.participant.AlertSignalPrimaryLocation;
import org.somda.sdc.biceps.model.participant.AlertSignalState;
import org.somda.sdc.biceps.model.participant.AlertSystemState;
import org.somda.sdc.biceps.model.participant.SystemSignalActivation;
import org.somda.sdc.glue.consumer.report.ReportProcessingException;

/**
 * BICEPS Annex B alert tests (B.88 - B.128).
 */
public class InvariantAnnexAlertTest extends InjectorTestBase {
    private MessageStorage messageStorage;
    private MdibHistorianFactory mdibHistorianFactory;

    @BeforeEach
    void setUp() {
        final var injector = getInjector();
        this.messageStorage = injector.getInstance(MessageStorage.class);
        final var riInjector = getInjector().getInstance(TestClient.class).getInjector();
        this.mdibHistorianFactory = riInjector.getInstance(MdibHistorianFactory.class);
    }

    @Test
    @TestIdentifier(EnabledTestConfig.BICEPS_B_128)
    @TestDescription("Starting from the initially retrieved mdib, applies every episodic report to the mdib and"
            + " verifies that the activation state of every alert signal state is set according to the "
            + " system signal activation of the alert system, "
            + "if the alert signal is located within the same alert system"
            + " and the manifestation matches with the manifestation of the system signal activation.")
    @RequirePrecondition(
            manipulationPreconditions = {ManipulationPreconditions.SystemSignalActivationManipulation.class})
    void testRequirementB128() throws NoTestData, IOException {
        final var mdibHistorian = mdibHistorianFactory.createMdibHistorian(
                messageStorage, getInjector().getInstance(TestRunObserver.class));

        final var acceptableSequenceSeen = new AtomicInteger(0);

        try (final Stream<String> sequenceIds = mdibHistorian.getKnownSequenceIds()) {
            sequenceIds.forEach(sequenceId -> {
                try (final MdibHistorian.HistorianResult history =
                        mdibHistorian.episodicReportBasedHistory(sequenceId)) {
                    RemoteMdibAccess first = history.next();

                    while (first != null) {
                        final var alertSystemStates = first.getStatesByType(AlertSystemState.class);

                        for (var alertSystemState : alertSystemStates) {
                            final var manifestationAndAlertActivationsMap =
                                    createSystemSignalActivationMap(alertSystemState.getSystemSignalActivation());

                            final var childAlertSignals =
                                    getChildAlertSignals(first, alertSystemState.getDescriptorHandle());

                            for (var manifestationAndState : manifestationAndAlertActivationsMap.entrySet()) {
                                final var currentActivationStates = manifestationAndState.getValue();
                                final var onSeen = new AtomicBoolean(false);
                                final var allPsd = new AtomicInteger(0);
                                final var allOff = new AtomicInteger(0);
                                final var childrenWithSameManifestation = getChildrenWithSameManifestation(
                                        childAlertSignals, manifestationAndState.getKey());
                                for (var entry : childrenWithSameManifestation) {
                                    acceptableSequenceSeen.incrementAndGet();
                                    final var alertSignalActivationState = entry.getActivationState();
                                    for (var currentActivationState : currentActivationStates) {
                                        verifyAlertSignalActivationState(
                                                currentActivationState,
                                                alertSignalActivationState,
                                                entry.getDescriptorHandle());
                                    }

                                    switch (alertSignalActivationState) {
                                        case ON -> onSeen.set(true);
                                        case PSD -> allPsd.incrementAndGet();
                                        case OFF -> allOff.incrementAndGet();
                                        default -> {}
                                    }
                                }
                                if (onSeen.get()) {
                                    checkActivationState(manifestationAndState.getValue(), AlertActivation.ON);
                                } else if (!childrenWithSameManifestation.isEmpty()
                                        && allPsd.get() == childrenWithSameManifestation.size()) {
                                    checkActivationState(manifestationAndState.getValue(), AlertActivation.PSD);
                                } else if (!childrenWithSameManifestation.isEmpty()
                                        && allOff.get() == childrenWithSameManifestation.size()) {
                                    checkActivationState(manifestationAndState.getValue(), AlertActivation.OFF);
                                }
                            }
                        }
                        first = history.next();
                    }
                } catch (PreprocessingException | ReportProcessingException e) {
                    fail(e);
                }
            });
        }

        assertTestData(acceptableSequenceSeen.get(), "No acceptable sequence seen, test failed");
    }

    private Map<AlertSignalManifestation, List<AlertActivation>> createSystemSignalActivationMap(
            final List<SystemSignalActivation> systemSignalActivations) {
        final var map = new EnumMap<AlertSignalManifestation, List<AlertActivation>>(AlertSignalManifestation.class);
        for (var systemSignalActivation : systemSignalActivations) {
            if (map.containsKey(systemSignalActivation.getManifestation())) {
                map.get(systemSignalActivation.getManifestation()).add(systemSignalActivation.getState());
            } else {
                final var alertActivationStates = new ArrayList<AlertActivation>();
                alertActivationStates.add(systemSignalActivation.getState());
                map.put(systemSignalActivation.getManifestation(), alertActivationStates);
            }
        }
        return map;
    }

    private Map<AlertSignalDescriptor, AlertSignalState> getChildAlertSignals(
            final RemoteMdibAccess first, final String descriptorHandle) {
        final var childAlertSignals = new HashMap<AlertSignalDescriptor, AlertSignalState>();
        final var children = first.getEntity(descriptorHandle).orElseThrow().getChildren();
        for (var child : children) {
            final var entity = first.getEntity(child).orElseThrow();
            if (entity.getDescriptorClass().equals(AlertSignalDescriptor.class)) {
                final var alertSignalState =
                        entity.getStates(AlertSignalState.class).get(0);
                if (ImpliedValueUtil.getLocation(alertSignalState).equals(AlertSignalPrimaryLocation.LOC)) {
                    childAlertSignals.put(
                            entity.getDescriptor(AlertSignalDescriptor.class).orElseThrow(), alertSignalState);
                }
            }
        }
        return childAlertSignals;
    }

    private List<AlertSignalState> getChildrenWithSameManifestation(
            final Map<AlertSignalDescriptor, AlertSignalState> childAlertSignals,
            final AlertSignalManifestation currentManifestation) {
        final var relevantChildren = new ArrayList<AlertSignalState>();
        for (var entry : childAlertSignals.entrySet()) {
            if (entry.getKey().getManifestation().equals(currentManifestation)) {
                relevantChildren.add(entry.getValue());
            }
        }
        return relevantChildren;
    }

    private void verifyAlertSignalActivationState(
            final AlertActivation currentActivationState,
            final AlertActivation alertSignalActivationState,
            final String descriptorHandle) {
        switch (currentActivationState) {
            case PSD:
                assertTrue(
                        alertSignalActivationState.equals(AlertActivation.PSD)
                                || alertSignalActivationState.equals(AlertActivation.OFF),
                        String.format(
                                "The activation state of the alert signal %s is %s, but should be OFF or PSD since"
                                        + " the alert system activation state is PSD",
                                descriptorHandle, alertSignalActivationState));
                break;
            case OFF:
                assertEquals(
                        AlertActivation.OFF,
                        alertSignalActivationState,
                        String.format(
                                "The activation state of the alert signal %s is %s, but should be OFF since the"
                                        + " alert system activation state is OFF",
                                descriptorHandle, alertSignalActivationState));
                break;
            default:
        }
    }

    private void checkActivationState(final List<AlertActivation> alertActivations, final AlertActivation activation) {
        for (var alertActivation : alertActivations) {
            assertEquals(
                    activation,
                    alertActivation,
                    String.format("Expected activation state was %s, but actual is %s", activation, alertActivation));
        }
    }
}
