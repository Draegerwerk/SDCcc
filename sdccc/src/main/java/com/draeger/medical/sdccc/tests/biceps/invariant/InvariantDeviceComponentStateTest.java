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
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.somda.sdc.biceps.common.MdibEntity;
import org.somda.sdc.biceps.common.storage.PreprocessingException;
import org.somda.sdc.biceps.consumer.access.RemoteMdibAccess;
import org.somda.sdc.biceps.model.participant.AbstractAlertDescriptor;
import org.somda.sdc.biceps.model.participant.AbstractAlertState;
import org.somda.sdc.biceps.model.participant.AbstractDescriptor;
import org.somda.sdc.biceps.model.participant.AbstractDeviceComponentDescriptor;
import org.somda.sdc.biceps.model.participant.AbstractDeviceComponentState;
import org.somda.sdc.biceps.model.participant.AbstractMetricDescriptor;
import org.somda.sdc.biceps.model.participant.AbstractMetricState;
import org.somda.sdc.biceps.model.participant.AbstractOperationDescriptor;
import org.somda.sdc.biceps.model.participant.AbstractOperationState;
import org.somda.sdc.biceps.model.participant.AlertActivation;
import org.somda.sdc.biceps.model.participant.ComponentActivation;
import org.somda.sdc.biceps.model.participant.OperatingMode;
import org.somda.sdc.glue.consumer.report.ReportProcessingException;

/**
 * BICEPS tests for chapter 5.4.6 .
 */
public class InvariantDeviceComponentStateTest extends InjectorTestBase {
    private MessageStorage messageStorage;
    private MdibHistorianFactory mdibHistorianFactory;

    @BeforeEach
    void setup() {
        this.messageStorage = getInjector().getInstance(MessageStorage.class);
        final var riInjector = getInjector().getInstance(TestClient.class).getInjector();
        this.mdibHistorianFactory = riInjector.getInstance(MdibHistorianFactory.class);
    }

    @Test
    @DisplayName("R0025_0: A SERVICE PROVIDER's pm:AbstractDeviceComponentState/@ActivationState SHALL only have "
            + "the value \"Off\" when every activation state of the component’s descendants is inactive. "
            + "Inactive means that every pm:AbstractDeviceComponentState/@ActivationState is \"Off\", "
            + "every pm:AbstractAlertState/@ActivationState is \"Off\", "
            + "every pm:AbstractMetricState/@ActivationState is \"Off\", and "
            + "every pm:AbstractOperationState/@OperatingMode is \"NA\" (not available).")
    @TestIdentifier(EnabledTestConfig.BICEPS_R0025_0)
    @TestDescription("Starting from the initially retrieved mdib, applies every episodic report to the mdib and "
            + "verifies that if a pm:AbstractDeviceComponentState has @ActivationState=OFF "
            + "all of it’s descendants are inactive.")
    @RequirePrecondition(
            manipulationPreconditions = {ManipulationPreconditions.AbstractDeviceComponentStateOFFManipulation.class})
    void testRequirementR00250() throws NoTestData, IOException {
        final var mdibHistorian = mdibHistorianFactory.createMdibHistorian(
                messageStorage, getInjector().getInstance(TestRunObserver.class));

        final var acceptableSequenceSeen = new AtomicInteger(0);

        try (final Stream<String> sequenceIds = mdibHistorian.getKnownSequenceIds()) {
            sequenceIds.forEach(sequenceId -> {
                try (final MdibHistorian.HistorianResult history =
                        mdibHistorian.episodicReportBasedHistory(sequenceId)) {

                    RemoteMdibAccess remoteMdibAccess = history.next();

                    while (remoteMdibAccess != null) {
                        final var entities =
                                remoteMdibAccess.findEntitiesByType(AbstractDeviceComponentDescriptor.class);

                        for (var entity : entities) {
                            if (ImpliedValueUtil.getComponentActivation(
                                            entity.getFirstState(AbstractDeviceComponentState.class)
                                                    .orElseThrow())
                                    == ComponentActivation.OFF) {

                                final var abstractDeviceComponentDescendants = getDescendantsByType(
                                        remoteMdibAccess, entity.getHandle(), AbstractDeviceComponentDescriptor.class);
                                for (var descendant : abstractDeviceComponentDescendants) {
                                    final var descendantState = descendant
                                            .getFirstState(AbstractDeviceComponentState.class)
                                            .orElseThrow();
                                    assertEquals(
                                            ComponentActivation.OFF,
                                            ImpliedValueUtil.getComponentActivation(descendantState),
                                            String.format(
                                                    "The ComponentActivation OFF was not "
                                                            + "set for the descendant with handle %s",
                                                    descendantState.getDescriptorHandle()));
                                }

                                final var abstractAlertDescendants = getDescendantsByType(
                                        remoteMdibAccess, entity.getHandle(), AbstractAlertDescriptor.class);
                                for (var descendant : abstractAlertDescendants) {
                                    final var descendantState = descendant
                                            .getFirstState(AbstractAlertState.class)
                                            .orElseThrow();
                                    assertEquals(
                                            AlertActivation.OFF,
                                            descendantState.getActivationState(),
                                            String.format(
                                                    "The AlertActivation OFF was not "
                                                            + "set for the descendant with handle %s",
                                                    descendantState.getDescriptorHandle()));
                                }

                                final var abstractMetricDescendants = getDescendantsByType(
                                        remoteMdibAccess, entity.getHandle(), AbstractMetricDescriptor.class);
                                for (var descendant : abstractMetricDescendants) {
                                    final var descendantState = descendant
                                            .getFirstState(AbstractMetricState.class)
                                            .orElseThrow();
                                    assertEquals(
                                            ComponentActivation.OFF,
                                            ImpliedValueUtil.getMetricActivation(descendantState),
                                            String.format(
                                                    "The ComponentActivation OFF was not "
                                                            + "set for the descendant with handle %s",
                                                    descendantState.getDescriptorHandle()));
                                }

                                final var abstractOperationDescendants = getDescendantsByType(
                                        remoteMdibAccess, entity.getHandle(), AbstractOperationDescriptor.class);
                                for (var descendant : abstractOperationDescendants) {
                                    final var descendantState = descendant
                                            .getFirstState(AbstractOperationState.class)
                                            .orElseThrow();
                                    assertEquals(
                                            OperatingMode.NA,
                                            descendantState.getOperatingMode(),
                                            String.format(
                                                    "The OperatingMode NA was not "
                                                            + "set for the descendant with handle %s",
                                                    descendantState.getDescriptorHandle()));
                                }

                                acceptableSequenceSeen.incrementAndGet();
                            }
                        }

                        remoteMdibAccess = history.next();
                    }
                } catch (PreprocessingException | ReportProcessingException e) {
                    fail(e);
                }
            });
        }
        assertTestData(
                acceptableSequenceSeen.get(), "No pm:AbstractDeviceComponentState/@ActivationState had the value OFF.");
    }

    private List<MdibEntity> getDescendantsByType(
            final RemoteMdibAccess remoteMdibAccess,
            final String entityHandle,
            final Class<? extends AbstractDescriptor> type) {

        final List<MdibEntity> result = new ArrayList<>();
        for (MdibEntity child : remoteMdibAccess.getChildrenByType(entityHandle, type)) {
            result.add(child);
            result.addAll(getDescendantsByType(remoteMdibAccess, child.getHandle(), type));
        }
        return result;
    }
}
