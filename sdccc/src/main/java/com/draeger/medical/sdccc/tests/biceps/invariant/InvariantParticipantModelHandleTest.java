/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.tests.biceps.invariant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.somda.sdc.biceps.common.MdibEntity;
import org.somda.sdc.biceps.common.storage.PreprocessingException;
import org.somda.sdc.biceps.consumer.access.RemoteMdibAccess;
import org.somda.sdc.biceps.model.participant.AbstractContextState;
import org.somda.sdc.biceps.model.participant.AbstractDescriptor;
import org.somda.sdc.biceps.model.participant.AbstractMultiState;
import org.somda.sdc.glue.consumer.report.ReportProcessingException;

/**
 * BICEPS participant model handle tests (ch. 5.2.2).
 */
public class InvariantParticipantModelHandleTest extends InjectorTestBase {

    private static final Pair<Integer, Integer> VALID_ASCII_RANGE = new ImmutablePair<>(0x21, 0x7E);

    private MessageStorage messageStorage;
    private MdibHistorianFactory mdibHistorianFactory;

    @BeforeEach
    void setUp() {
        this.messageStorage = getInjector().getInstance(MessageStorage.class);

        final var riInjector = getInjector().getInstance(TestClient.class).getInjector();
        this.mdibHistorianFactory = riInjector.getInstance(MdibHistorianFactory.class);
    }

    @Test
    @DisplayName("R0007_0: Within every MDIB version of a SERVICE PROVIDER, all HANDLEs SHALL be unique.")
    @TestIdentifier(EnabledTestConfig.BICEPS_R0007_0)
    @TestDescription("Starting from the initially retrieved mdib, ensures that for each mdib version, "
            + " all contained handles are unique.")
    void testRequirementR0007() throws NoTestData, IOException {
        // NOTE: MdibHistorian checks the uniqueness of Handles in all MdibVersions.
        //       However, its checks are missing duplicate handles introduced by ContextReports.
        //       Hence, we cannot fully rely on the MdibHistorian and have to check handle
        //       uniqueness ourselves.
        final var mdibHistorian = mdibHistorianFactory.createMdibHistorian(
                messageStorage, getInjector().getInstance(TestRunObserver.class));

        final AtomicInteger handlesSeen = new AtomicInteger();

        try (final Stream<String> sequenceIds = mdibHistorian.getKnownSequenceIds()) {
            sequenceIds.forEach(sequenceId -> {
                try (final MdibHistorian.HistorianResult history =
                        mdibHistorian.episodicReportBasedHistory(sequenceId)) {
                    var current = history.next();
                    while (current != null) {
                        final var allEntities = current.findEntitiesByType(AbstractDescriptor.class);

                        final List<String> entityHandles =
                                allEntities.stream().map(MdibEntity::getHandle).toList();
                        final HashSet<String> allHandles = new HashSet<>();
                        for (var handle : entityHandles) {
                            assertFalse(
                                    allHandles.contains(handle),
                                    "Handle '" + handle + "' is not unique in mdib version " + current.getMdibVersion()
                                            + ".");
                            allHandles.add(handle);
                        }

                        final List<AbstractContextState> contextStates =
                                current.findContextStatesByType(AbstractContextState.class);
                        final List<String> contextStateHandles = contextStates.stream()
                                .map(AbstractMultiState::getHandle)
                                .toList();
                        for (var cSHandle : contextStateHandles) {
                            assertFalse(
                                    allHandles.contains(cSHandle),
                                    "contextState handle '" + cSHandle + "' is not unique in Mdib version "
                                            + current.getMdibVersion() + ".");
                            allHandles.add(cSHandle);
                        }
                        handlesSeen.addAndGet(allHandles.size());
                        current = history.next();
                    }
                } catch (PreprocessingException | ReportProcessingException e) {
                    fail(e);
                }
            });
        }
        assertTestData(handlesSeen.get(), "No Data to perform test on");
    }

    @Test
    @DisplayName("A HANDLE SHALL only consist of characters that match ASCII character codes in the range of"
            + " [0x21, 0x7E].")
    @TestIdentifier(EnabledTestConfig.BICEPS_R0105_0)
    @TestDescription("Starting from the initially retrieved mdib, applies every episodic report to the mdib and"
            + " verifies that every descriptor and state handle present only contains valid ASCII characters within the"
            + " permitted range.")
    void testRequirementR0105() throws NoTestData, IOException {
        final var mdibHistorian = mdibHistorianFactory.createMdibHistorian(
                messageStorage, getInjector().getInstance(TestRunObserver.class));

        final var handlesSeen = new HashSet<String>();

        try (final Stream<String> sequenceIds = mdibHistorian.getKnownSequenceIds()) {
            sequenceIds.forEach(sequenceId -> {
                try (final MdibHistorian.HistorianResult history =
                        mdibHistorian.episodicReportBasedHistory(sequenceId)) {

                    RemoteMdibAccess first = history.next();
                    while (first != null) {
                        final var mdibVersion = first.getMdibVersion();
                        final var allEntities = first.findEntitiesByType(AbstractDescriptor.class);
                        for (MdibEntity entity : allEntities) {
                            // descriptor handle
                            assertTrue(
                                    isWithinPermittedASCIIRange(entity.getHandle()),
                                    String.format(
                                            "Invalid descriptor handle %s found in mdib version %s",
                                            entity.getHandle(), mdibVersion));
                            handlesSeen.add(entity.getHandle());

                            // state handles
                            entity.doIfMultiState(states -> states.forEach(state -> {
                                handlesSeen.add(state.getHandle());
                                assertTrue(
                                        isWithinPermittedASCIIRange(state.getHandle()),
                                        String.format(
                                                "Invalid multi state handle %s found in mdib version %s",
                                                state.getHandle(), mdibVersion));
                            }));
                        }
                        first = history.next();
                    }
                } catch (PreprocessingException | ReportProcessingException e) {
                    fail(e);
                }
            });
        }
        assertTestData(handlesSeen, "No Data to perform test on");
    }

    /**
     * Verifies whether a string only contains Characters in the range of 0x21 to 0x7E.
     *
     * @param data to verify
     * @return true if correct, false otherwise
     */
    boolean isWithinPermittedASCIIRange(final String data) {
        return data.codePoints().allMatch(c -> isInRange(c, VALID_ASCII_RANGE));
    }

    private boolean isInRange(final int codePoint, final Pair<Integer, Integer> range) {
        return codePoint >= range.getLeft() && codePoint <= range.getRight();
    }
}
