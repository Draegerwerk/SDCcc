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
import com.draeger.medical.sdccc.manipulation.precondition.impl.ConditionalPreconditions;
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
import com.google.common.collect.Sets;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.somda.sdc.biceps.common.MdibEntity;
import org.somda.sdc.biceps.common.storage.PreprocessingException;
import org.somda.sdc.biceps.consumer.access.RemoteMdibAccess;
import org.somda.sdc.biceps.model.participant.AbstractContextDescriptor;
import org.somda.sdc.biceps.model.participant.AbstractContextState;
import org.somda.sdc.biceps.model.participant.AbstractDescriptor;
import org.somda.sdc.biceps.model.participant.ContextAssociation;
import org.somda.sdc.biceps.model.participant.EnsembleContextDescriptor;
import org.somda.sdc.biceps.model.participant.LocationContextDescriptor;
import org.somda.sdc.biceps.model.participant.MdibVersion;
import org.somda.sdc.biceps.model.participant.MeansContextDescriptor;
import org.somda.sdc.biceps.model.participant.OperatorContextDescriptor;
import org.somda.sdc.biceps.model.participant.PatientContextDescriptor;
import org.somda.sdc.biceps.model.participant.WorkflowContextDescriptor;
import org.somda.sdc.glue.consumer.report.ReportProcessingException;

/**
 * BICEPS participant model multistate tests (ch. 5.4.3).
 */
public class InvariantMultiStateTest extends InjectorTestBase {

    private static final List<Class<? extends AbstractContextDescriptor>> CONTEXT_DESCRIPTOR_CLASSES = List.of(
            PatientContextDescriptor.class,
            LocationContextDescriptor.class,
            EnsembleContextDescriptor.class,
            MeansContextDescriptor.class,
            OperatorContextDescriptor.class,
            WorkflowContextDescriptor.class);

    private MessageStorage messageStorage;
    private MdibHistorianFactory mdibHistorianFactory;

    @BeforeEach
    void setUp() {
        final var injector = getInjector();
        final var riInjector = getInjector().getInstance(TestClient.class).getInjector();
        this.messageStorage = injector.getInstance(MessageStorage.class);
        this.mdibHistorianFactory = riInjector.getInstance(MdibHistorianFactory.class);
    }

    @Test
    @TestIdentifier(EnabledTestConfig.BICEPS_R0097)
    @TestDescription("Starting from the initially retrieved mdib, applies every episodic report to the mdib and"
            + " verifies that the handles from any state derived from pm:AbstractMultiState are unique and disjunctive"
            + " to the handles from any descriptor derived from pm:AbstractDescriptor within one mdib sequence.")
    @RequirePrecondition(
            simplePreconditions = {ConditionalPreconditions.AllKindsOfContextStatesAssociatedPrecondition.class})
    void testRequirement0097() throws NoTestData, IOException {

        final var descriptorHandles = new HashSet<String>();
        final var multiStateHandles = new HashSet<String>();

        final var mdibHistorian = mdibHistorianFactory.createMdibHistorian(
                messageStorage, getInjector().getInstance(TestRunObserver.class));
        final var seenAcceptableSequence = new AtomicBoolean(false);

        try (final Stream<String> sequenceIds = mdibHistorian.getKnownSequenceIds()) {
            sequenceIds.forEach(sequenceId -> {
                try (final MdibHistorian.HistorianResult history =
                        mdibHistorian.episodicReportBasedHistory(sequenceId)) {
                    RemoteMdibAccess first = history.next();
                    final var seenMultiStatesMap = initMultiStateMap(first, CONTEXT_DESCRIPTOR_CLASSES);
                    while (first != null) {
                        final var entities = first.findEntitiesByType(AbstractDescriptor.class);
                        final var states = first.findContextStatesByType(AbstractContextState.class);
                        addAllDescriptorHandles(entities, descriptorHandles);
                        if (states.isEmpty()) {
                            first = history.next();
                            continue;
                        }
                        addAllMultiStateHandles(states, multiStateHandles, seenMultiStatesMap);
                        areMultiStatesHandlesUnique(states);
                        areHandlesDisjunctive(descriptorHandles, multiStateHandles, first.getMdibVersion());
                        first = history.next();
                    }
                    var acceptableSequence = true;
                    for (var value : seenMultiStatesMap.values()) {
                        acceptableSequence &= value.size() > 1;
                    }

                    if (acceptableSequence) {
                        seenAcceptableSequence.set(acceptableSequence);
                    }
                } catch (PreprocessingException | ReportProcessingException e) {
                    fail(e);
                }
                descriptorHandles.clear();
                multiStateHandles.clear();
            });
        }
        assertTestData(
                seenAcceptableSequence.get(),
                "No Sequence with two different context states for each" + " context descriptor associated seen.");
    }

    private HashMap<String, Set<String>> initMultiStateMap(
            final RemoteMdibAccess first, final List<Class<? extends AbstractContextDescriptor>> contextClasses) {
        final var multiStateMap = new HashMap<String, Set<String>>();
        for (var contextClass : contextClasses) {
            first.findEntitiesByType(contextClass)
                    .forEach(entity -> multiStateMap.put(entity.getHandle(), new HashSet<>()));
        }
        return multiStateMap;
    }

    private void areMultiStatesHandlesUnique(final List<AbstractContextState> states) {
        final var allHandles =
                states.stream().map(AbstractContextState::getHandle).collect(Collectors.toList());
        final var distinctHandles = allHandles.stream().distinct().collect(Collectors.toList());
        assertEquals(allHandles.size(), distinctHandles.size());
    }

    private void addAllDescriptorHandles(
            final Collection<MdibEntity> entities, final HashSet<String> descriptorHandles) {
        entities.forEach(entity -> descriptorHandles.add(entity.getHandle()));
    }

    private void addAllMultiStateHandles(
            final List<AbstractContextState> states,
            final HashSet<String> multiStateHandles,
            final HashMap<String, Set<String>> seenMultiStatesMap) {
        states.forEach(state -> {
            multiStateHandles.add(state.getHandle());
            if (ImpliedValueUtil.getContextAssociation(state) == ContextAssociation.ASSOC) {
                seenMultiStatesMap
                        .computeIfAbsent(state.getDescriptorHandle(), stateClass -> new HashSet<String>())
                        .add(state.getHandle());
            }
        });
    }

    private void areHandlesDisjunctive(
            final HashSet<String> setOne, final HashSet<String> setTwo, final MdibVersion mdibVersion) {
        final var intersection = Sets.intersection(setOne, setTwo);
        assertTrue(
                intersection.isEmpty(),
                String.format(
                        "Descriptor and state handles are not disjunctive."
                                + " Overlapping handles:%s. In MdibVersion: %s",
                        intersection, mdibVersion));
    }
}
