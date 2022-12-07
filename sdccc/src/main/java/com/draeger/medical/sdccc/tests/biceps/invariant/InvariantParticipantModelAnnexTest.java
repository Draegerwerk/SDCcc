/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.tests.biceps.invariant;

import static org.junit.jupiter.api.Assertions.assertNull;
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
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.somda.sdc.biceps.common.storage.PreprocessingException;
import org.somda.sdc.biceps.consumer.access.RemoteMdibAccess;
import org.somda.sdc.biceps.model.message.AbstractReport;
import org.somda.sdc.biceps.model.participant.AbstractOperationDescriptor;
import org.somda.sdc.biceps.model.participant.MdsDescriptor;
import org.somda.sdc.biceps.model.participant.MdsState;
import org.somda.sdc.biceps.model.participant.VmdDescriptor;
import org.somda.sdc.biceps.model.participant.VmdState;
import org.somda.sdc.glue.consumer.report.ReportProcessingException;

/**
 * Test for the normative Annex Participant Model of BICEPS.
 */
public class InvariantParticipantModelAnnexTest extends InjectorTestBase {

    private MessageStorage messageStorage;
    private MdibHistorianFactory mdibHistorianFactory;

    @BeforeEach
    void setup() {
        this.messageStorage = getInjector().getInstance(MessageStorage.class);
        final var riInjector = getInjector().getInstance(TestClient.class).getInjector();
        this.mdibHistorianFactory = riInjector.getInstance(MdibHistorianFactory.class);
    }

    @Test
    @DisplayName("A service control object to define remote control operations. Any "
            + "pm:AbstractOperationDescriptor/@OperationTarget within this SCO SHALL refer to any descriptor "
            + "within the containment subtree that has as its root the AbstractComplexDeviceComponentDescriptor "
            + "that contains the SCO.")
    @TestIdentifier(EnabledTestConfig.BICEPS_B_6_0)
    @TestDescription("Starting from the initially retrieved mdib, applies every episodic report to the mdib and"
            + " verifies that the OperationTarget of every AbstractOperationDescriptor is at any point set to the"
            + " descriptor of the parent of the sco or any child descriptor of that parent.")
    void testRequirementB6() throws NoTestData, IOException {
        final var mdibHistorian = mdibHistorianFactory.createMdibHistorian(
                messageStorage, getInjector().getInstance(TestRunObserver.class));

        final var acceptableSequenceSeen = new AtomicInteger(0);

        try (final Stream<String> sequenceIds = mdibHistorian.getKnownSequenceIds()) {
            sequenceIds.forEach(sequenceId -> {
                try (final var reports = mdibHistorian.getAllReports(sequenceId)) {
                    var first = mdibHistorian.createNewStorage(sequenceId);

                    for (final Iterator<AbstractReport> iterator = reports.iterator(); iterator.hasNext(); ) {
                        final AbstractReport report = iterator.next();

                        first = mdibHistorian.applyReportOnStorage(first, report);

                        final var operationEntities = first.findEntitiesByType(AbstractOperationDescriptor.class);

                        for (var entity : operationEntities) {
                            acceptableSequenceSeen.incrementAndGet();
                            final var parentHandle = entity.getParent().orElseThrow();
                            final var scoDescriptor =
                                    first.getEntity(parentHandle).orElseThrow();
                            final var scoParentHandle =
                                    scoDescriptor.getParent().orElseThrow();
                            final var scoParent =
                                    first.getEntity(scoParentHandle).orElseThrow();
                            final var possibleTargets =
                                    getAllChildrenHandles(first, scoParent.getHandle(), scoParent.getChildren());
                            final var target = entity.getDescriptor(AbstractOperationDescriptor.class)
                                    .orElseThrow()
                                    .getOperationTarget();
                            assertTrue(
                                    possibleTargets.contains(target),
                                    String.format(
                                            "%s is not a valid target, the valid targets are: %s",
                                            target, possibleTargets));
                        }
                    }
                } catch (PreprocessingException | ReportProcessingException e) {
                    fail(e);
                }
            });
        }
        assertTestData(
                acceptableSequenceSeen.get(), "No AbstractOperationDescriptors seen during test run, test failed.");
    }

    @Test
    @DisplayName("OperatingJurisdiction SHALL NOT be present if there is no pm:MdsDescriptor/pm:ApprovedJurisdictions"
            + " list present.")
    @TestIdentifier(EnabledTestConfig.BICEPS_B_284_0)
    @TestDescription("Based on the initially retrieved mdib, applies each episodic report to the mdib and verifies that"
            + " no OperatingJurisdiction is set for an MdsState at any time if the corresponding MdsDescriptor does not"
            + " maintain an ApprovedJurisdiction list.")
    void testRequirementB284() throws NoTestData, IOException {
        final var mdibHistorian = mdibHistorianFactory.createMdibHistorian(
                messageStorage, getInjector().getInstance(TestRunObserver.class));

        final var acceptableSequenceSeen = new AtomicInteger(0);

        try (final Stream<String> sequenceIds = mdibHistorian.getKnownSequenceIds()) {
            sequenceIds.forEach(sequenceId -> {
                try (final MdibHistorian.HistorianResult history =
                        mdibHistorian.episodicReportBasedHistory(sequenceId)) {
                    RemoteMdibAccess first = history.next();

                    while (first != null) {
                        final var mdsEntities = first.findEntitiesByType(MdsDescriptor.class);
                        for (var mdsEntity : mdsEntities) {
                            final var descriptor =
                                    mdsEntity.getDescriptor(MdsDescriptor.class).orElseThrow();
                            final var state = mdsEntity.getStates(MdsState.class);
                            final var approvedJurisdictions = descriptor.getApprovedJurisdictions();
                            if (approvedJurisdictions == null) {
                                acceptableSequenceSeen.incrementAndGet();
                                assertNull(
                                        state.get(0).getOperatingJurisdiction(),
                                        String.format(
                                                "OperatingJurisdiction for %s is set, although ApprovedJurisdictions is missing.",
                                                state.get(0).getDescriptorHandle()));
                            }
                        }
                        first = history.next();
                    }
                } catch (PreprocessingException | ReportProcessingException e) {
                    fail(e);
                }
            });
        }
        assertTestData(acceptableSequenceSeen.get(), "No acceptable sequence seen, test failed.");
    }

    @Test
    @DisplayName("OperatingJurisdiction SHALL NOT be present if there is no pm:VmdDescriptor/pm:ApprovedJurisdictions"
            + " list present.")
    @TestIdentifier(EnabledTestConfig.BICEPS_B_402_0)
    @TestDescription("Based on the initially retrieved mdib, applies each episodic report to the mdib and verifies that"
            + " no OperatingJurisdiction is set for an VmdState at any time if the corresponding VmdDescriptor does not"
            + " maintain an ApprovedJurisdiction list.")
    void testRequirementB402() throws NoTestData, IOException {
        final var mdibHistorian = mdibHistorianFactory.createMdibHistorian(
                messageStorage, getInjector().getInstance(TestRunObserver.class));

        final var acceptableSequenceSeen = new AtomicInteger(0);

        try (final Stream<String> sequenceIds = mdibHistorian.getKnownSequenceIds()) {
            sequenceIds.forEach(sequenceId -> {
                try (final MdibHistorian.HistorianResult history =
                        mdibHistorian.episodicReportBasedHistory(sequenceId)) {
                    RemoteMdibAccess first = history.next();

                    while (first != null) {
                        final var vmdEntities = first.findEntitiesByType(VmdDescriptor.class);
                        for (var vmd : vmdEntities) {
                            final var descriptor = vmd.getDescriptor(VmdDescriptor.class);
                            final var state = vmd.getFirstState(VmdState.class);
                            final var approvedJurisdiction =
                                    descriptor.orElseThrow().getApprovedJurisdictions();
                            if (approvedJurisdiction == null) {
                                acceptableSequenceSeen.incrementAndGet();
                                assertNull(
                                        state.orElseThrow().getOperatingJurisdiction(),
                                        String.format(
                                                "OperatingJurisdiction should not be present, because ApprovedJurisdiction is not"
                                                        + " present for vmd with handle %s",
                                                descriptor.orElseThrow().getHandle()));
                            }
                        }
                        first = history.next();
                    }
                } catch (PreprocessingException | ReportProcessingException e) {
                    fail(e);
                }
            });
        }
        assertTestData(
                acceptableSequenceSeen.get(),
                "No vmd descriptor without approved jurisdiction seen," + " during test run, test failed.");
    }

    private Set<String> getAllChildrenHandles(
            final RemoteMdibAccess first, final String handle, final List<String> children) {
        final var handles = new HashSet<String>();
        handles.add(handle);
        for (var child : children) {
            final var descriptor = first.getDescriptor(child).orElseThrow();
            if (descriptor instanceof AbstractOperationDescriptor) {
                continue;
            }
            final var entity = first.getEntity(child).orElseThrow();
            handles.addAll(getAllChildrenHandles(first, child, entity.getChildren()));
        }
        return handles;
    }
}
