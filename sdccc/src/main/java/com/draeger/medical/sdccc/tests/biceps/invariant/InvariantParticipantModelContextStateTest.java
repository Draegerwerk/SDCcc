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
import com.google.common.collect.HashMultimap;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.somda.sdc.biceps.common.MdibEntity;
import org.somda.sdc.biceps.common.storage.PreprocessingException;
import org.somda.sdc.biceps.consumer.access.RemoteMdibAccess;
import org.somda.sdc.biceps.model.participant.AbstractMultiState;
import org.somda.sdc.biceps.model.participant.ContextAssociation;
import org.somda.sdc.biceps.model.participant.LocationContextDescriptor;
import org.somda.sdc.biceps.model.participant.LocationContextState;
import org.somda.sdc.biceps.model.participant.PatientContextDescriptor;
import org.somda.sdc.biceps.model.participant.PatientContextState;
import org.somda.sdc.glue.consumer.report.ReportProcessingException;

/**
 * BICEPS participant model context state tests (ch. 5.4.4).
 */
public class InvariantParticipantModelContextStateTest extends InjectorTestBase {

    private static final Logger LOG = LogManager.getLogger();

    private MessageStorage messageStorage;
    private MdibHistorianFactory mdibHistorianFactory;

    @BeforeEach
    void setUp() {
        this.messageStorage = getInjector().getInstance(MessageStorage.class);
        final var riInjector = getInjector().getInstance(TestClient.class).getInjector();
        this.mdibHistorianFactory = riInjector.getInstance(MdibHistorianFactory.class);
    }

    @Test
    @TestIdentifier(EnabledTestConfig.BICEPS_R0124)
    @TestDescription("Starting from the initially retrieved mdib, applies every episodic report to the mdib and"
            + " verifies that at no point a patient context descriptor has more than one associated state. Verifies that"
            + " during every sequence there were at least two different associated patient context states.")
    @RequirePrecondition(manipulationPreconditions = {ManipulationPreconditions.AssociatePatientsManipulation.class})
    void testRequirementR0124() throws NoTestData, IOException {
        final var mdibHistorian = mdibHistorianFactory.createMdibHistorian(
                messageStorage, getInjector().getInstance(TestRunObserver.class));

        final var acceptableSequenceIdsSeen = new AtomicBoolean(false);

        try (final Stream<String> sequenceIds = mdibHistorian.getKnownSequenceIds()) {
            sequenceIds.forEach(sequenceId -> {
                final var associationCounterMap = HashMultimap.<String, String>create();
                try (final MdibHistorian.HistorianResult history =
                        mdibHistorian.episodicReportBasedHistory(sequenceId)) {

                    RemoteMdibAccess mdibAccess = history.next();
                    while (mdibAccess != null) {

                        final var patientContextEntities =
                                mdibAccess.findEntitiesByType(PatientContextDescriptor.class);

                        for (MdibEntity patientContextEntity : patientContextEntities) {

                            final var associatedPatients = patientContextEntity.getStates().stream()
                                    .map(state -> (PatientContextState) state)
                                    .filter(state -> ContextAssociation.ASSOC.equals(
                                            ImpliedValueUtil.getContextAssociation(state)))
                                    .toList();

                            if (!associatedPatients.isEmpty()) {
                                final var associatedStateHandles = associatedPatients.stream()
                                        .map(AbstractMultiState::getHandle)
                                        .collect(Collectors.toList());

                                assertEquals(
                                        1,
                                        associatedPatients.size(),
                                        String.format(
                                                "More than one PatientContextState was associated for the"
                                                        + " handle %s, associated state handles were %s, mdib version %s",
                                                patientContextEntity.getHandle(),
                                                String.join(", ", associatedStateHandles),
                                                mdibAccess.getMdibVersion()));

                                // only add handles if the check above passed
                                LOG.debug(
                                        "Adding desc {} state {}",
                                        patientContextEntity.getHandle(),
                                        associatedPatients.get(0).getHandle());
                                associationCounterMap.put(
                                        patientContextEntity.getHandle(),
                                        associatedPatients.get(0).getHandle());
                            }
                        }
                        mdibAccess = history.next();
                    }
                } catch (PreprocessingException | ReportProcessingException e) {
                    fail(e);
                }
                // determine if any context descriptor had 2+ associated states
                final var hadSufficientContexts =
                        associationCounterMap.asMap().values().stream().anyMatch(values -> values.size() >= 2);

                if (hadSufficientContexts) {
                    // the check passed, this sequence is acceptable
                    acceptableSequenceIdsSeen.set(true);
                } else {
                    LOG.warn(
                            "No or not enough patients were associated during the test run in sequence {}"
                                    + " - at least two different associated patient context states are required for a single patient"
                                    + " context descriptor.",
                            sequenceId);
                }
            });
        }
        assertTestData(
                acceptableSequenceIdsSeen.get(),
                "No or not enough patients were associated during the test run."
                        + " At least two different associated patient context states are required for a single patient"
                        + " context descriptor in a single sequence.");
    }

    @Test
    @TestIdentifier(EnabledTestConfig.BICEPS_R0133)
    @TestDescription("Starting from the initially retrieved mdib, applies every episodic report to the mdib and"
            + " verifies that at no point a location context descriptor has more than one associated state. Verifies that"
            + " there were at least two different associated location context states during at least one sequence.")
    @RequirePrecondition(manipulationPreconditions = {ManipulationPreconditions.AssociateLocationsManipulation.class})
    void testRequirementR0133() throws NoTestData, IOException {
        final var mdibHistorian = mdibHistorianFactory.createMdibHistorian(
                messageStorage, getInjector().getInstance(TestRunObserver.class));

        final var acceptableSequenceIdsSeen = new AtomicBoolean(false);

        try (final Stream<String> sequenceIds = mdibHistorian.getKnownSequenceIds()) {
            sequenceIds.forEach(sequenceId -> {
                final var associationCounterMap = HashMultimap.<String, String>create();
                try (final MdibHistorian.HistorianResult history =
                        mdibHistorian.episodicReportBasedHistory(sequenceId)) {

                    RemoteMdibAccess mdibAccess = history.next();
                    while (mdibAccess != null) {

                        final var locationContextEntities =
                                mdibAccess.findEntitiesByType(LocationContextDescriptor.class);

                        for (MdibEntity locationContextEntity : locationContextEntities) {

                            final var associatedLocations = locationContextEntity.getStates().stream()
                                    .map(state -> (LocationContextState) state)
                                    .filter(state -> ContextAssociation.ASSOC.equals(
                                            ImpliedValueUtil.getContextAssociation(state)))
                                    .toList();

                            if (!associatedLocations.isEmpty()) {
                                final var associatedStateHandles = associatedLocations.stream()
                                        .map(AbstractMultiState::getHandle)
                                        .collect(Collectors.toList());

                                assertEquals(
                                        1,
                                        associatedLocations.size(),
                                        String.format(
                                                "More than one LocationContextState was associated for the"
                                                        + " handle %s, associated state handles were %s, mdib version %s",
                                                locationContextEntity.getHandle(),
                                                String.join(", ", associatedStateHandles),
                                                mdibAccess.getMdibVersion()));

                                // only add handles if the check above passed
                                LOG.debug(
                                        "Adding desc {} state {}",
                                        locationContextEntity.getHandle(),
                                        associatedLocations.get(0).getHandle());
                                associationCounterMap.put(
                                        locationContextEntity.getHandle(),
                                        associatedLocations.get(0).getHandle());
                            }
                        }
                        mdibAccess = history.next();
                    }
                } catch (PreprocessingException | ReportProcessingException e) {
                    fail(e);
                }
                // determine if any context descriptor had 2+ associated states
                final var hadSufficientContexts = !associationCounterMap.isEmpty()
                        && associationCounterMap.asMap().values().stream().allMatch(values -> values.size() >= 2);

                if (hadSufficientContexts) {
                    // the check passed, this sequence is acceptable
                    acceptableSequenceIdsSeen.set(true);
                } else {
                    LOG.warn(
                            "No or not enough locations were associated during the test run in sequence {}"
                                    + " - at least two different associated location context states are required for a single"
                                    + " location context descriptor.",
                            sequenceId);
                }
            });
        }
        assertTestData(
                acceptableSequenceIdsSeen.get(),
                "No or not enough locations were associated during the test run."
                        + " At least two different associated location context states are required for a single location"
                        + " context descriptor in a single sequence.");
    }
}
