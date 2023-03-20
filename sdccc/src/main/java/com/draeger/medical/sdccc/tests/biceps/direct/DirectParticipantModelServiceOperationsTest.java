/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2023 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.tests.biceps.direct;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.draeger.medical.sdccc.configuration.EnabledTestConfig;
import com.draeger.medical.sdccc.sdcri.testclient.TestClient;
import com.draeger.medical.sdccc.tests.InjectorTestBase;
import com.draeger.medical.sdccc.tests.annotations.TestDescription;
import com.draeger.medical.sdccc.tests.annotations.TestIdentifier;
import com.draeger.medical.sdccc.tests.util.NoTestData;
import com.draeger.medical.sdccc.util.MessageGeneratingUtil;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.somda.sdc.biceps.model.message.GetContextStatesResponse;
import org.somda.sdc.biceps.model.message.GetMdStateResponse;
import org.somda.sdc.biceps.model.participant.AbstractContextState;
import org.somda.sdc.biceps.model.participant.AbstractMultiState;
import org.somda.sdc.biceps.model.participant.AbstractState;
import org.somda.sdc.biceps.model.participant.MdsDescriptor;

/**
 * BICEPS participant model Service Operations test.
 */
public class DirectParticipantModelServiceOperationsTest extends InjectorTestBase {

    private TestClient testClient;
    private MessageGeneratingUtil messageGeneratingUtil;

    @BeforeEach
    void setUp() {
        this.testClient = getInjector().getInstance(TestClient.class);
        assertTrue(testClient.isClientRunning());

        this.messageGeneratingUtil = getInjector().getInstance(MessageGeneratingUtil.class);
    }

    @Test
    @TestIdentifier(EnabledTestConfig.BICEPS_R5039)
    @TestDescription("Sends a get context states message with empty handle ref and verifies that the response contains"
            + " all context states of the mdib.")
    @SuppressFBWarnings(
            value = {"WMI_WRONG_MAP_ITERATOR"},
            justification = "HashMap should be sufficiently efficient.")
    void testRequirementR5039() throws NoTestData {
        final var allContextStates =
                testClient.getSdcRemoteDevice().getMdibAccess().getContextStates();

        assertTestData(allContextStates.size(), "No context states has been seen.");

        final Set<String> contextStateHandles =
                allContextStates.stream().map(AbstractMultiState::getHandle).collect(Collectors.toSet());

        final GetContextStatesResponse getContextStatesResponse = (GetContextStatesResponse) messageGeneratingUtil
                .getContextStates()
                .getOriginalEnvelope()
                .getBody()
                .getAny()
                .get(0);

        verifyStatesInResponse(contextStateHandles, getContextStatesResponse);
    }

    @Test
    @TestIdentifier(EnabledTestConfig.BICEPS_R5040)
    @TestDescription("Verifies that for every known context descriptor handle the "
            + "corresponding context states are returned.")
    @SuppressFBWarnings(
            value = {"WMI_WRONG_MAP_ITERATOR"},
            justification = "HashMap should be sufficiently efficient.")
    void testRequirementR5040() throws NoTestData {
        final List<AbstractContextState> allExpectedContextStates =
                testClient.getSdcRemoteDevice().getMdibAccess().getContextStates();
        final Map<String, Set<String>> descriptorHandleToHandleListMapping = new HashMap<>();

        for (var state : allExpectedContextStates) {
            if (!descriptorHandleToHandleListMapping.containsKey(state.getDescriptorHandle())) {
                descriptorHandleToHandleListMapping.put(state.getDescriptorHandle(), new HashSet<>());
            }
            descriptorHandleToHandleListMapping.get(state.getDescriptorHandle()).add(state.getHandle());
        }

        assertTestData(descriptorHandleToHandleListMapping.size(), "No context descriptor has been seen.");
        for (var descriptorHandle : descriptorHandleToHandleListMapping.keySet()) {
            assertTestData(
                    descriptorHandleToHandleListMapping.get(descriptorHandle).size(),
                    String.format("No states for the descriptor handle %s.", descriptorHandle));
        }

        for (var descriptorHandle : descriptorHandleToHandleListMapping.keySet()) {
            final GetContextStatesResponse getContextStatesResponse = (GetContextStatesResponse) messageGeneratingUtil
                    .getContextStates(List.of(descriptorHandle))
                    .getOriginalEnvelope()
                    .getBody()
                    .getAny()
                    .get(0);

            verifyStatesInResponse(descriptorHandleToHandleListMapping.get(descriptorHandle), getContextStatesResponse);
        }
    }

    @Test
    @TestIdentifier(EnabledTestConfig.BICEPS_R5041)
    @TestDescription("Verifies that for every known context state handle the corresponding context state is returned.")
    @SuppressFBWarnings(
            value = {"WMI_WRONG_MAP_ITERATOR"},
            justification = "HashMap should be sufficiently efficient.")
    void testRequirementR5041() throws NoTestData {

        final List<AbstractContextState> allExpectedContextStates =
                testClient.getSdcRemoteDevice().getMdibAccess().getContextStates();
        final Map<String, Set<String>> descriptorHandleToHandleListMapping = new HashMap<>();

        for (var state : allExpectedContextStates) {
            if (!descriptorHandleToHandleListMapping.containsKey(state.getDescriptorHandle())) {
                descriptorHandleToHandleListMapping.put(state.getDescriptorHandle(), new HashSet<>());
            }
            descriptorHandleToHandleListMapping.get(state.getDescriptorHandle()).add(state.getHandle());
        }

        assertTestData(descriptorHandleToHandleListMapping.size(), "No context descriptor has been seen.");
        for (var descriptorHandle : descriptorHandleToHandleListMapping.keySet()) {
            assertTestData(
                    descriptorHandleToHandleListMapping.get(descriptorHandle).size(),
                    String.format("No states for the descriptor handle %s.", descriptorHandle));
        }

        for (var descriptorHandle : descriptorHandleToHandleListMapping.keySet()) {
            for (var stateHandle : descriptorHandleToHandleListMapping.get(descriptorHandle)) {
                final GetContextStatesResponse getContextStatesResponse =
                        (GetContextStatesResponse) messageGeneratingUtil
                                .getContextStates(List.of(stateHandle))
                                .getOriginalEnvelope()
                                .getBody()
                                .getAny()
                                .get(0);

                verifyStatesInResponse(Set.of(stateHandle), getContextStatesResponse);
            }
        }
    }

    @Test
    @TestIdentifier(EnabledTestConfig.BICEPS_R5042)
    @TestDescription("Verifies that only one Mds descriptor is present and then does a "
            + "GetContextStates request with only the Mds descriptor's handle and "
            + "verifies that all known states are returned.")
    void testRequirementR5042() throws NoTestData {

        final List<MdsDescriptor> mdsDescriptors =
                testClient.getSdcRemoteDevice().getMdibAccess().findEntitiesByType(MdsDescriptor.class).stream()
                        .map(mdibEntity -> (MdsDescriptor) mdibEntity.getDescriptor())
                        .collect(Collectors.toList());

        assertTestData(mdsDescriptors, "No Mds descriptor present.");
        assertEquals(1, mdsDescriptors.size(), "Too many Mds descriptors.");

        final Set<String> allExpectedContextStateHandles =
                testClient.getSdcRemoteDevice().getMdibAccess().getContextStates().stream()
                        .map(AbstractMultiState::getHandle)
                        .collect(Collectors.toSet());

        assertTestData(allExpectedContextStateHandles.size(), "No context states.");

        final GetContextStatesResponse getContextStatesResponse = (GetContextStatesResponse) messageGeneratingUtil
                .getContextStates(List.of(mdsDescriptors.get(0).getHandle()))
                .getOriginalEnvelope()
                .getBody()
                .getAny()
                .get(0);

        verifyStatesInResponse(allExpectedContextStateHandles, getContextStatesResponse);
    }

    @Test
    @TestIdentifier(EnabledTestConfig.BICEPS_C_62)
    @TestDescription("1. Request with GetMdState that does have an empty ref list."
            + "2. Request with GetMdState that contains 3 DescriptorHandles."
            + "Context state descriptor handles are excluded, "
            + "since they can be omitted by the provider and should be requested by GetContextStates instead.")
    void testRequirementC62() {

        final List<AbstractState> allStates =
                testClient.getSdcRemoteDevice().getMdibAccess().getStatesByType(AbstractState.class);

        final List<AbstractContextState> allContextStates =
                testClient.getSdcRemoteDevice().getMdibAccess().getContextStates();

        final Set<String> allExpectedDescriptorHandles =
                allStates.stream().map(AbstractState::getDescriptorHandle).collect(Collectors.toSet());

        final Set<String> allContextDescriptorHandles = allContextStates.stream()
                .map(AbstractState::getDescriptorHandle)
                .collect(Collectors.toSet());

        allExpectedDescriptorHandles.removeAll(allContextDescriptorHandles);

        // Empty ref list

        final GetMdStateResponse emptyRefListResponse = (GetMdStateResponse) messageGeneratingUtil
                .getMdState(List.of())
                .getOriginalEnvelope()
                .getBody()
                .getAny()
                .get(0);

        verifyStatesInResponse(allExpectedDescriptorHandles, emptyRefListResponse);

        // Descriptor handles
        final ArrayList<String> specificRefs = new ArrayList<>(3);

        for (var ref : allExpectedDescriptorHandles) {
            specificRefs.add(ref);

            if (specificRefs.size() > 2) break;
        }

        final GetMdStateResponse specificRefListResponse = (GetMdStateResponse) messageGeneratingUtil
                .getMdState(specificRefs)
                .getOriginalEnvelope()
                .getBody()
                .getAny()
                .get(0);

        verifyStatesInResponse(new HashSet<>(specificRefs), specificRefListResponse);
    }

    private void verifyStatesInResponse(
            final Set<String> expectedContextStateHandles, final GetContextStatesResponse response) {
        final Set<String> contextStateHandles = response.getContextState().stream()
                .map(AbstractContextState::getHandle)
                .collect(Collectors.toSet());

        for (var expectedContextStateHandle : expectedContextStateHandles) {
            assertTrue(
                    contextStateHandles.contains(expectedContextStateHandle),
                    String.format("State handle %s has not been seen in response.", expectedContextStateHandle));
        }
    }

    private void verifyStatesInResponse(
            final Set<String> expectedDescriptorHandles, final GetMdStateResponse response) {
        final Set<String> descriptorHandles = response.getMdState().getState().stream()
                .map(AbstractState::getDescriptorHandle)
                .collect(Collectors.toSet());

        for (var expectedDescriptorHandle : expectedDescriptorHandles) {
            assertTrue(
                    descriptorHandles.contains(expectedDescriptorHandle),
                    String.format("Descriptor handle %s has not been seen in response.", expectedDescriptorHandle));
        }
    }
}
