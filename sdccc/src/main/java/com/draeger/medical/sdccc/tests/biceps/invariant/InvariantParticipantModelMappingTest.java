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
import com.draeger.medical.sdccc.messages.MessageStorage;
import com.draeger.medical.sdccc.sdcri.testclient.TestClient;
import com.draeger.medical.sdccc.tests.InjectorTestBase;
import com.draeger.medical.sdccc.tests.annotations.TestDescription;
import com.draeger.medical.sdccc.tests.annotations.TestIdentifier;
import com.draeger.medical.sdccc.tests.util.MdibHistorian;
import com.draeger.medical.sdccc.tests.util.NoTestData;
import com.draeger.medical.sdccc.tests.util.guice.MdibHistorianFactory;
import com.draeger.medical.sdccc.util.TestRunObserver;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.somda.sdc.biceps.common.storage.PreprocessingException;
import org.somda.sdc.biceps.consumer.access.RemoteMdibAccess;
import org.somda.sdc.biceps.model.participant.AbstractState;
import org.somda.sdc.glue.consumer.report.ReportProcessingException;

/**
 * BICEPS participant model state mapping tests (ch. 5.4.2).
 */
public class InvariantParticipantModelMappingTest extends InjectorTestBase {
    private static final String STATE_SUFFIX = "State$";
    private static final String DESCRIPTOR_SUFFIX = "%sDescriptor";

    private MessageStorage messageStorage;
    private MdibHistorianFactory mdibHistorianFactory;

    @BeforeEach
    void setUp() {
        this.messageStorage = getInjector().getInstance(MessageStorage.class);
        final var riInjector = getInjector().getInstance(TestClient.class).getInjector();
        this.mdibHistorianFactory = riInjector.getInstance(MdibHistorianFactory.class);
    }

    @Test
    @TestIdentifier(EnabledTestConfig.BICEPS_R0023)
    @TestDescription("Starting from the initially retrieved mdib, applies every episodic report to the mdib and"
            + " verifies that every state references its descriptor with the descriptor handle attribute."
            + " The relationship between a state and its descriptor is further verified by checking the naming scheme."
            + " The existence of a descriptor for a state is implicitly tested in MdibHistorian"
            + " and is covered by a unittest.")
    void testRequirementR0023() throws NoTestData, IOException {
        final var mdibHistorian = mdibHistorianFactory.createMdibHistorian(
                messageStorage, getInjector().getInstance(TestRunObserver.class));

        final var statesSeen = new AtomicInteger(0);

        try (final Stream<String> sequenceIds = mdibHistorian.getKnownSequenceIds()) {
            sequenceIds.forEach(sequenceId -> {
                try (final MdibHistorian.HistorianResult history =
                        mdibHistorian.episodicReportBasedHistory(sequenceId)) {

                    RemoteMdibAccess first = history.next();
                    while (first != null) {
                        final var allStates = first.getStatesByType(AbstractState.class);
                        for (var state : allStates) {
                            statesSeen.incrementAndGet();
                            final var descriptor = first.getDescriptor(state.getDescriptorHandle())
                                    .orElseThrow();
                            final var stateName =
                                    state.getClass().getSimpleName().replaceAll(STATE_SUFFIX, "");
                            assertEquals(
                                    String.format(DESCRIPTOR_SUFFIX, stateName),
                                    descriptor.getClass().getSimpleName(),
                                    String.format(
                                            "Non matching naming scheme for handle: %s."
                                                    + " State is %s and Descriptor is %s.",
                                            state.getDescriptorHandle(),
                                            state.getClass().getSimpleName(),
                                            descriptor.getClass().getSimpleName()));
                        }
                        first = history.next();
                    }
                } catch (PreprocessingException | ReportProcessingException e) {
                    fail(e);
                }
            });
        }
        assertTestData(statesSeen.get(), "No Data to perform test on");
    }
}
