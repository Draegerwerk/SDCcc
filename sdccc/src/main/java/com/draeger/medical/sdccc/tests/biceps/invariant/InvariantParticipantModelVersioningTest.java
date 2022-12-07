/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.tests.biceps.invariant;

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
import com.draeger.medical.sdccc.tests.util.InitialImpliedValue;
import com.draeger.medical.sdccc.tests.util.InitialImpliedValueException;
import com.draeger.medical.sdccc.tests.util.MdibHistorian;
import com.draeger.medical.sdccc.tests.util.NoTestData;
import com.draeger.medical.sdccc.tests.util.guice.MdibHistorianFactory;
import com.draeger.medical.sdccc.util.TestRunObserver;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Collections;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.somda.sdc.biceps.common.MdibEntity;
import org.somda.sdc.biceps.common.access.MdibAccess;
import org.somda.sdc.biceps.common.storage.PreprocessingException;
import org.somda.sdc.biceps.consumer.access.RemoteMdibAccess;
import org.somda.sdc.biceps.model.participant.AbstractDescriptor;
import org.somda.sdc.biceps.model.participant.AbstractMultiState;
import org.somda.sdc.biceps.model.participant.AbstractState;
import org.somda.sdc.biceps.model.participant.AlertSystemDescriptor;
import org.somda.sdc.biceps.model.participant.ChannelDescriptor;
import org.somda.sdc.biceps.model.participant.MdsDescriptor;
import org.somda.sdc.biceps.model.participant.ScoDescriptor;
import org.somda.sdc.biceps.model.participant.SystemContextDescriptor;
import org.somda.sdc.biceps.model.participant.VmdDescriptor;
import org.somda.sdc.glue.consumer.report.ReportProcessingException;

/**
 * BICEPS participant model versioning tests (ch. 5.2.5).
 */
public class InvariantParticipantModelVersioningTest extends InjectorTestBase {
    public static final String IMPLIED_VALUE_ERROR_MESSAGE =
            "Handle %s has an implied value, but was already seen" + " without an implied value.";
    public static final String DECREMENTED_VERSION_ERROR_MESSAGE =
            "The version of %s has been decremented in" + " MdibVersion %s. It was %s and is now %s.";
    private MessageStorage messageStorage;
    private MdibHistorianFactory mdibHistorianFactory;

    @BeforeEach
    void setUp() {
        this.messageStorage = getInjector().getInstance(MessageStorage.class);
        final var riInjector = getInjector().getInstance(TestClient.class).getInjector();
        this.mdibHistorianFactory = riInjector.getInstance(MdibHistorianFactory.class);
    }

    @Test
    @TestIdentifier(EnabledTestConfig.BICEPS_R0033)
    @TestDescription("Starting from the initially retrieved mdib, applies every episodic report to the mdib and"
            + " verifies that descriptor versions are incremented whenever a child has changed.")
    @RequirePrecondition(simplePreconditions = {ConditionalPreconditions.DescriptionChangedPrecondition.class})
    void testRequirementR0033() throws NoTestData, IOException {
        final var mdibHistorian = mdibHistorianFactory.createMdibHistorian(
                messageStorage, getInjector().getInstance(TestRunObserver.class));

        final var descriptorChanges = new AtomicInteger(0);

        try (final Stream<String> sequenceIds = mdibHistorian.getKnownSequenceIds()) {
            sequenceIds.forEach(sequenceId -> {
                final var impliedValueMap = new InitialImpliedValue();
                try (final MdibHistorian.HistorianResult history =
                                mdibHistorian.episodicReportBasedHistory(sequenceId);
                        final MdibHistorian.HistorianResult historyNext =
                                mdibHistorian.episodicReportBasedHistory(sequenceId)) {

                    // skip the first entry so that history and historyNext are off by one entry
                    final var skippedElement = historyNext.next();
                    assertTestData(skippedElement != null, "Not enough input to compare mdib revisions");

                    RemoteMdibAccess first = history.next();
                    RemoteMdibAccess second = historyNext.next();
                    while (second != null) {
                        final var currentDescriptors = first.findEntitiesByType(AbstractDescriptor.class);
                        for (MdibEntity descriptor : currentDescriptors) {
                            final var nextDescriptorOpt = second.getEntity(descriptor.getHandle());
                            if (nextDescriptorOpt.isEmpty()) {
                                continue;
                            }
                            final var nextDescriptor = nextDescriptorOpt.orElseThrow();
                            // compare children of current and next descriptor one by one
                            final var childrenChanged =
                                    haveDescriptorChildrenDisOrReappeared(descriptor.getHandle(), first, second);
                            if (!childrenChanged) {
                                continue;
                            }

                            descriptorChanges.incrementAndGet();
                            assertTrue(
                                    isIncrementedVersion(
                                            ImpliedValueUtil.getDescriptorVersion(
                                                    descriptor.getDescriptor(), impliedValueMap),
                                            ImpliedValueUtil.getDescriptorVersion(
                                                    nextDescriptor.getDescriptor(), impliedValueMap)),
                                    "Descriptor version has not changed, but children have."
                                            + " MdibVersions " + first.getMdibVersion()
                                            + " and " + second.getMdibVersion()
                                            + ". Descriptor handle " + descriptor.getHandle()
                                            + ". Old children " + descriptor.getChildren()
                                            + " new children " + nextDescriptor.getChildren());
                        }
                        first = history.next();
                        second = historyNext.next();
                    }

                } catch (PreprocessingException
                        | ReportProcessingException
                        | InitialImpliedValueException
                        | NoTestData e) {
                    fail(e);
                }
            });
        }
        assertTestData(descriptorChanges.get(), "No descriptor changed during the test run.");
    }

    @Test
    @DisplayName("A SERVICE PROVIDER SHALL increment pm:AbstractDescriptor/@DescriptorVersion by 1 if an ATTRIBUTE"
            + " or the content of the descriptor ELEMENT have changed excluding changes to children of any TYPE that"
            + " extends pm:AbstractDescriptor.")
    @TestIdentifier(EnabledTestConfig.BICEPS_R0034_0)
    @TestDescription("Starting from the initially retrieved mdib, applies every episodic report to the mdib and"
            + " verifies that descriptor versions are incremented whenever the descriptors attributes or content changes,\n"
            + " except for changes to children of any Type that extends AbstractDescriptor.")
    @RequirePrecondition(simplePreconditions = {ConditionalPreconditions.DescriptionChangedPrecondition.class})
    void testRequirementR0034() throws NoTestData, IOException {
        final var mdibHistorian = mdibHistorianFactory.createMdibHistorian(
                messageStorage, getInjector().getInstance(TestRunObserver.class));

        final var descriptorChanges = new AtomicInteger(0);

        try (final Stream<String> sequenceIds = mdibHistorian.getKnownSequenceIds()) {
            sequenceIds.forEach(sequenceId -> {
                final var impliedValueMap = new InitialImpliedValue();
                try (final MdibHistorian.HistorianResult history =
                                mdibHistorian.episodicReportBasedHistory(sequenceId);
                        final MdibHistorian.HistorianResult historyNext =
                                mdibHistorian.episodicReportBasedHistory(sequenceId)) {

                    // skip the first entry so that history and historyNext are off by one entry
                    final var skippedElement = historyNext.next();
                    assertTestData(skippedElement != null, "Not enough input to compare mdib revisions");

                    RemoteMdibAccess first = history.next();
                    RemoteMdibAccess second = historyNext.next();
                    while (second != null) {
                        final var currentDescriptors = first.findEntitiesByType(AbstractDescriptor.class);
                        for (MdibEntity descriptor : currentDescriptors) {
                            final var nextDescriptorOpt = second.getEntity(descriptor.getHandle());
                            if (nextDescriptorOpt.isEmpty()) {
                                continue;
                            }
                            final var nextDescriptor = nextDescriptorOpt.orElseThrow();
                            // compare children of current and next descriptor one by one
                            final var descriptorChanged =
                                    hasDescriptorChanged(descriptor.getDescriptor(), nextDescriptor.getDescriptor());
                            if (!descriptorChanged) {
                                continue;
                            }

                            descriptorChanges.incrementAndGet();
                            assertTrue(
                                    isIncrementedVersion(
                                            ImpliedValueUtil.getDescriptorVersion(
                                                    descriptor.getDescriptor(), impliedValueMap),
                                            ImpliedValueUtil.getDescriptorVersion(
                                                    nextDescriptor.getDescriptor(), impliedValueMap)),
                                    "Descriptor version has not been incremented by one, but descriptor has changed."
                                            + " MdibVersions " + first.getMdibVersion()
                                            + " and " + second.getMdibVersion()
                                            + ". Descriptor handle " + descriptor.getHandle()
                                            + ". Old Descriptor " + descriptor.getDescriptor()
                                            + " New Descriptor " + nextDescriptor.getDescriptor());
                        }
                        first = history.next();
                        second = historyNext.next();
                    }

                } catch (PreprocessingException
                        | ReportProcessingException
                        | InitialImpliedValueException
                        | NoTestData e) {
                    fail(e);
                }
            });
        }
        assertTestData(descriptorChanges.get(), "No descriptor changed during the test run.");
    }

    @Test
    @DisplayName("A SERVICE PROVIDER SHALL increment pm:AbstractState/@StateVersion by 1 if an ATTRIBUTE or the content"
            + " of the state ELEMENT have changed.")
    @TestIdentifier(EnabledTestConfig.BICEPS_R0038_0)
    @TestDescription("Starting from the initially retrieved mdib, applies every episodic report to the mdib and"
            + " verifies that state versions are incremented whenever the state attributes or the content of the state"
            + " changed.")
    @RequirePrecondition(simplePreconditions = {ConditionalPreconditions.StateChangedPrecondition.class})
    void testRequirementR0038() throws NoTestData, IOException {
        final var mdibHistorian = mdibHistorianFactory.createMdibHistorian(
                messageStorage, getInjector().getInstance(TestRunObserver.class));

        final var stateChanges = new AtomicInteger(0);

        try (final Stream<String> sequenceIds = mdibHistorian.getKnownSequenceIds()) {
            sequenceIds.forEach(sequenceId -> {
                final var impliedValueMap = new InitialImpliedValue();
                final var removedStatesMap = new HashMap<String, AbstractState>();
                try (final MdibHistorian.HistorianResult history =
                                mdibHistorian.episodicReportBasedHistory(sequenceId);
                        final MdibHistorian.HistorianResult historyNext =
                                mdibHistorian.episodicReportBasedHistory(sequenceId)) {
                    // skip the first entry so that history and historyNext are off by one entry
                    final var skippedElement = historyNext.next();
                    assertTestData(skippedElement != null, "Not enough input to compare mdib revisions");

                    RemoteMdibAccess first = history.next();
                    RemoteMdibAccess second = historyNext.next();

                    while (second != null) {
                        final var states = first.getStatesByType(AbstractState.class);
                        for (var state : states) {

                            final String stateHandle;
                            if (state instanceof AbstractMultiState) {
                                stateHandle = ((AbstractMultiState) state).getHandle();
                            } else {
                                stateHandle = state.getDescriptorHandle();
                            }
                            final Optional<AbstractState> nextStateOpt = second.getState(stateHandle);

                            if (removedStatesMap.containsKey(stateHandle)) {
                                final var removedState = removedStatesMap.get(stateHandle);
                                if (!removedState.equals(state)) {
                                    assertTrue(
                                            isIncrementedVersion(
                                                    ImpliedValueUtil.getStateVersion(removedState, impliedValueMap),
                                                    ImpliedValueUtil.getStateVersion(state, impliedValueMap)),
                                            "State version has not been incremented by one, but reinserted state"
                                                    + " has changed. MdibVersions " + first.getMdibVersion()
                                                    + " and " + second.getMdibVersion()
                                                    + ". State handle " + stateHandle
                                                    + ". Old State " + removedState
                                                    + " New State " + state);
                                }
                                removedStatesMap.remove(stateHandle);
                            }

                            if (nextStateOpt.isEmpty()) {
                                // state will be removed
                                removedStatesMap.put(stateHandle, state);
                                continue;
                            }

                            final var nextState = nextStateOpt.orElseThrow();

                            if (state.equals(nextState)) {
                                continue;
                            }

                            stateChanges.incrementAndGet();
                            assertTrue(
                                    isIncrementedVersion(
                                            ImpliedValueUtil.getStateVersion(state, impliedValueMap),
                                            ImpliedValueUtil.getStateVersion(nextState, impliedValueMap)),
                                    "State version has not been incremented by one, but state has changed."
                                            + " MdibVersions " + first.getMdibVersion()
                                            + " and " + second.getMdibVersion()
                                            + ". State handle " + stateHandle
                                            + ". Old State " + state
                                            + " New State " + nextState);
                        }
                        first = history.next();
                        second = historyNext.next();
                    }

                } catch (PreprocessingException
                        | ReportProcessingException
                        | InitialImpliedValueException
                        | NoTestData e) {
                    fail(e);
                }
                removedStatesMap.clear();
            });
        }
        assertTestData(stateChanges.get(), "No state changed during the test run.");
    }

    @Test
    @TestIdentifier(EnabledTestConfig.BICEPS_R5003)
    @TestDescription("Starting from the initially retrieved mdib, applies every episodic report to the mdib and"
            + " verifies that no version counter other than MdibVersion is decremented, "
            + "by comparing their value before and after applying each"
            + " report. This also applies to the deletion and re-insertion of descriptors or states."
            + "MdibVersion is excluded from this, because it is used for ordering the reports that have been received.")
    void testRequirementR5003() throws IOException, NoTestData {
        final var mdibHistorian = mdibHistorianFactory.createMdibHistorian(
                messageStorage, getInjector().getInstance(TestRunObserver.class));

        final AtomicInteger stateVersionsSeen = new AtomicInteger(0);

        try (final Stream<String> sequenceIds = mdibHistorian.getKnownSequenceIds()) {
            sequenceIds.forEach(sequenceId -> {
                final var impliedValueMap = new InitialImpliedValue();
                final var previousDescriptorVersionMap = new HashMap<String, BigInteger>();
                final var previousStateVersionMap = new HashMap<String, BigInteger>();
                var previousMdDescriptionVersion = BigInteger.valueOf(-1);
                var previousMdStateVersion = BigInteger.valueOf(-1);
                var previousMdibVersion = BigInteger.valueOf(-1);

                try (final MdibHistorian.HistorianResult history =
                        mdibHistorian.episodicReportBasedHistory(sequenceId)) {
                    RemoteMdibAccess current = history.next();
                    while (current != null) {
                        final var currentMdibVersion = ImpliedValueUtil.getMdibVersion(current.getMdibVersion());
                        for (var entity : current.findEntitiesByType(AbstractDescriptor.class)) {
                            final var handle = entity.getHandle();
                            final var currentDescriptorVersion =
                                    ImpliedValueUtil.getDescriptorVersion(entity.getDescriptor(), impliedValueMap);
                            if (previousDescriptorVersionMap.containsKey(handle)) {
                                assertTrue(
                                        isNotDecrementedVersion(
                                                previousDescriptorVersionMap.get(handle), currentDescriptorVersion),
                                        String.format(
                                                DECREMENTED_VERSION_ERROR_MESSAGE,
                                                handle,
                                                currentMdibVersion,
                                                previousDescriptorVersionMap.get(handle),
                                                currentDescriptorVersion));
                            }
                            previousDescriptorVersionMap.put(handle, currentDescriptorVersion);
                        }
                        final var states = current.getStatesByType(AbstractState.class);
                        for (var state : states) {
                            final String stateHandle;
                            final var currentStateVersion = ImpliedValueUtil.getStateVersion(state, impliedValueMap);
                            if (state instanceof AbstractMultiState) {
                                stateHandle = ((AbstractMultiState) state).getHandle();
                            } else {
                                stateHandle = state.getDescriptorHandle();
                            }
                            if (previousStateVersionMap.containsKey(stateHandle)) {
                                assertTrue(
                                        isNotDecrementedVersion(
                                                previousStateVersionMap.get(stateHandle), currentStateVersion),
                                        String.format(
                                                DECREMENTED_VERSION_ERROR_MESSAGE,
                                                stateHandle,
                                                currentMdibVersion,
                                                previousStateVersionMap.get(stateHandle),
                                                currentStateVersion));
                            }
                            previousStateVersionMap.put(stateHandle, currentStateVersion);
                            stateVersionsSeen.incrementAndGet();
                        }
                        final var currentMdDescriptionVersion =
                                ImpliedValueUtil.getMdibAccessDescriptionVersion(current);
                        final var currentMdStateVersion = ImpliedValueUtil.getMdibAccessMdStateVersion(current);
                        assertTrue(
                                isNotDecrementedVersion(previousMdDescriptionVersion, currentMdDescriptionVersion),
                                String.format(
                                        DECREMENTED_VERSION_ERROR_MESSAGE,
                                        "MdDescription",
                                        currentMdibVersion,
                                        previousMdDescriptionVersion,
                                        currentMdDescriptionVersion));
                        assertTrue(
                                isNotDecrementedVersion(previousMdStateVersion, currentMdStateVersion),
                                String.format(
                                        DECREMENTED_VERSION_ERROR_MESSAGE,
                                        "MdState",
                                        currentMdibVersion,
                                        previousMdStateVersion,
                                        currentMdStateVersion));
                        assertTrue(
                                isNotDecrementedVersion(previousMdibVersion, currentMdibVersion),
                                String.format(
                                        "The mdib version has been decremented. It was %s and is now %s.",
                                        previousMdibVersion, currentMdibVersion));
                        previousMdDescriptionVersion = currentMdDescriptionVersion;
                        previousMdStateVersion = currentMdStateVersion;
                        previousMdibVersion = currentMdibVersion;
                        current = history.next();
                    }
                } catch (PreprocessingException | ReportProcessingException | InitialImpliedValueException e) {
                    fail(e);
                }
            });
        }
        assertTestData(stateVersionsSeen.get(), "No state versions have been verified.");
    }

    /**
     * Determines whether any elements (i.e. attributes and children which are not {@linkplain AbstractDescriptor}s)
     * have changed for a descriptor.
     *
     * @param currentDescriptor current descriptor
     * @param nextDescriptor    next descriptor to compare to
     * @return true if descriptor has changed, false otherwise
     */
    protected boolean hasDescriptorChanged(
            final AbstractDescriptor currentDescriptor, final AbstractDescriptor nextDescriptor) {
        // set all fields to null we don't want to compare
        final var currentWithoutDescriptors = nullDescriptorChildren(currentDescriptor);
        final var nextWithoutDescriptors = nullDescriptorChildren(nextDescriptor);
        return !currentWithoutDescriptors.equals(nextWithoutDescriptors);
    }

    @SuppressFBWarnings(
            value = {"NP_NONNULL_PARAM_VIOLATION"},
            justification = "These are allowed to be null, just not marked as such. Null is the value representing"
                    + " an empty field.")
    protected AbstractDescriptor nullDescriptorChildren(final AbstractDescriptor descriptor) {
        final var descr = (AbstractDescriptor) descriptor.clone();
        if (descr instanceof AlertSystemDescriptor) {
            final var desc = (AlertSystemDescriptor) descr;
            desc.setAlertCondition(Collections.emptyList());
            desc.setAlertSignal(Collections.emptyList());
        }
        if (descr instanceof ChannelDescriptor) {
            ((ChannelDescriptor) descr).setMetric(Collections.emptyList());
        }
        if (descr instanceof ScoDescriptor) {
            ((ScoDescriptor) descr).setOperation(Collections.emptyList());
        }
        if (descr instanceof SystemContextDescriptor) {
            final var desc = (SystemContextDescriptor) descr;
            desc.setPatientContext(null);
            desc.setLocationContext(null);
            desc.setEnsembleContext(Collections.emptyList());
            desc.setOperatorContext(Collections.emptyList());
            desc.setWorkflowContext(Collections.emptyList());
            desc.setMeansContext(Collections.emptyList());
        }
        if (descr instanceof MdsDescriptor) {
            final var desc = (MdsDescriptor) descr;
            desc.setSco(null);
            desc.setAlertSystem(null);
            desc.setSystemContext(null);
            desc.setClock(null);
            desc.setBattery(Collections.emptyList());
            desc.setVmd(Collections.emptyList());
        }
        if (descr instanceof VmdDescriptor) {
            final var desc = (VmdDescriptor) descr;
            desc.setChannel(Collections.emptyList());
            desc.setSco(null);
            desc.setAlertSystem(null);
        }

        return descr;
    }

    /**
     * Determines whether any child descriptors for a given descriptor handle have been removed
     * or inserted, determined using their handles.
     *
     * @param handle         to check children for
     * @param currentStorage current mdib
     * @param nextStorage    next mdib
     * @return true if descriptors have been inserted or removed, false otherwise
     */
    private boolean haveDescriptorChildrenDisOrReappeared(
            final String handle, final MdibAccess currentStorage, final MdibAccess nextStorage) {
        final var currentDescriptorChildren =
                currentStorage.getChildrenByType(handle, AbstractDescriptor.class).stream()
                        .map(MdibEntity::getDescriptor)
                        .map(AbstractDescriptor::getHandle)
                        .collect(Collectors.toSet());

        final var nextDescriptorChildren = nextStorage.getChildrenByType(handle, AbstractDescriptor.class).stream()
                .map(MdibEntity::getDescriptor)
                .map(AbstractDescriptor::getHandle)
                .collect(Collectors.toSet());

        return !nextDescriptorChildren.equals(currentDescriptorChildren);
    }

    private boolean isNotDecrementedVersion(final BigInteger version, final BigInteger nextVersion) {
        return -1 != nextVersion.compareTo(version);
    }

    private boolean isIncrementedVersion(final BigInteger version, final BigInteger nextVersion) {
        return nextVersion.equals(version.add(BigInteger.ONE));
    }
}
