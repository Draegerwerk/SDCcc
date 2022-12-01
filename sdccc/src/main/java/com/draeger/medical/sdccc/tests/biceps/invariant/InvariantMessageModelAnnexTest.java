/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.tests.biceps.invariant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import com.draeger.medical.sdccc.tests.util.NoTestData;
import com.draeger.medical.sdccc.tests.util.guice.MdibHistorianFactory;
import com.draeger.medical.sdccc.util.Constants;
import com.draeger.medical.sdccc.util.TestRunObserver;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.somda.sdc.biceps.common.storage.PreprocessingException;
import org.somda.sdc.biceps.model.message.AbstractAlertReport;
import org.somda.sdc.biceps.model.message.AbstractComponentReport;
import org.somda.sdc.biceps.model.message.AbstractContextReport;
import org.somda.sdc.biceps.model.message.AbstractReport;
import org.somda.sdc.biceps.model.message.DescriptionModificationReport;
import org.somda.sdc.biceps.model.message.DescriptionModificationType;
import org.somda.sdc.biceps.model.message.EpisodicAlertReport;
import org.somda.sdc.biceps.model.message.EpisodicComponentReport;
import org.somda.sdc.biceps.model.message.EpisodicContextReport;
import org.somda.sdc.biceps.model.message.EpisodicMetricReport;
import org.somda.sdc.biceps.model.message.EpisodicOperationalStateReport;
import org.somda.sdc.biceps.model.participant.AbstractAlertState;
import org.somda.sdc.biceps.model.participant.AbstractContextState;
import org.somda.sdc.biceps.model.participant.AbstractDescriptor;
import org.somda.sdc.biceps.model.participant.AbstractDeviceComponentState;
import org.somda.sdc.biceps.model.participant.AbstractMetricState;
import org.somda.sdc.biceps.model.participant.AbstractOperationState;
import org.somda.sdc.biceps.model.participant.AlertSystemDescriptor;
import org.somda.sdc.biceps.model.participant.ChannelDescriptor;
import org.somda.sdc.biceps.model.participant.MdsDescriptor;
import org.somda.sdc.biceps.model.participant.ScoDescriptor;
import org.somda.sdc.biceps.model.participant.SystemContextDescriptor;
import org.somda.sdc.biceps.model.participant.VmdDescriptor;
import org.somda.sdc.dpws.soap.MarshallingService;
import org.somda.sdc.dpws.soap.SoapUtil;
import org.somda.sdc.dpws.soap.exception.MarshallingException;
import org.somda.sdc.glue.consumer.report.ReportProcessingException;

/**
 * Test for the normative Annex Message Model of BICEPS.
 */
public class InvariantMessageModelAnnexTest extends InjectorTestBase {
    private static final String STATE_ABSENT = "The state with handle %s is not present";
    private static final String STATE_UNCHANGED = "The state with the handle %s from the report has not changed";
    private MarshallingService marshalling;
    private SoapUtil soapUtil;
    private MessageStorage messageStorage;
    private MdibHistorianFactory mdibHistorianFactory;

    @BeforeEach
    void setup() {
        this.messageStorage = getInjector().getInstance(MessageStorage.class);
        final var riInjector = getInjector().getInstance(TestClient.class).getInjector();
        this.mdibHistorianFactory = riInjector.getInstance(MdibHistorianFactory.class);
        this.marshalling = riInjector.getInstance(MarshallingService.class);
        this.soapUtil = riInjector.getInstance(SoapUtil.class);
    }

    @Test
    @TestIdentifier(EnabledTestConfig.BICEPS_C5)
    @TestDescription("Starting from the initially retrieved mdib, applies each episodic report to the mdib and checks"
            + " for each AbstractDescriptor contained in a DescriptionModificationReport "
            + "that it was inserted or deleted or udpdated by changing"
            + "at least one child or attribute.")
    void testRequirementC5() throws NoTestData, IOException {
        final var mdibHistorian = mdibHistorianFactory.createMdibHistorian(
                messageStorage, getInjector().getInstance(TestRunObserver.class));

        final var acceptableSequenceSeen = new AtomicInteger(0);

        try (final Stream<String> sequenceIds = mdibHistorian.getKnownSequenceIds()) {
            sequenceIds.forEach(sequenceId -> {

                // get relevant reports
                try (final var reports = mdibHistorian.getAllReports(sequenceId)) {
                    var first = mdibHistorian.createNewStorage(sequenceId);
                    var second = mdibHistorian.createNewStorage(sequenceId);

                    for (final Iterator<AbstractReport> iterator = reports.iterator(); iterator.hasNext(); ) {
                        final AbstractReport report = iterator.next();

                        if (report instanceof DescriptionModificationReport) {
                            acceptableSequenceSeen.incrementAndGet();
                            second = mdibHistorian.applyReportOnStorage(second, report);

                            for (var reportPart : ((DescriptionModificationReport) report).getReportPart()) {
                                for (var modifiedDescriptor : reportPart.getDescriptor()) {

                                    final var descriptorBeforeReportOpt =
                                            first.getDescriptor(modifiedDescriptor.getHandle());
                                    final var descriptorAfterReportOpt =
                                            second.getDescriptor(modifiedDescriptor.getHandle());

                                    final var modificationType = ImpliedValueUtil.getModificationType(reportPart);
                                    if (modificationType.equals(DescriptionModificationType.UPT)) {
                                        assertTrue(
                                                descriptorBeforeReportOpt.isPresent()
                                                        && descriptorAfterReportOpt.isPresent(),
                                                String.format(
                                                        "The descriptor with handle %s is not present",
                                                        modifiedDescriptor.getHandle()));

                                        assertNotEquals(
                                                descriptorAfterReportOpt.orElseThrow(),
                                                descriptorBeforeReportOpt.orElseThrow(),
                                                String.format(
                                                        "The descriptor with the handle %s from the report has not changed",
                                                        modifiedDescriptor.getHandle()));

                                    } else if (modificationType.equals(DescriptionModificationType.CRT)) {
                                        assertTrue(
                                                descriptorBeforeReportOpt.isEmpty()
                                                        && descriptorAfterReportOpt.isPresent(),
                                                String.format(
                                                        "The descriptor with handle %s is missing before applying the report:"
                                                                + " %s and is present after applying the report: %s,"
                                                                + " for modification type create",
                                                        modifiedDescriptor.getHandle(),
                                                        descriptorBeforeReportOpt.isEmpty(),
                                                        descriptorAfterReportOpt.isPresent()));
                                    } else {
                                        assertTrue(
                                                descriptorBeforeReportOpt.isPresent()
                                                        && descriptorAfterReportOpt.isEmpty(),
                                                String.format(
                                                        "The descriptor with handle %s is present before applying the report:"
                                                                + " %s and is missing after applying the report: %s,"
                                                                + " for modification type delete",
                                                        modifiedDescriptor.getHandle(),
                                                        descriptorBeforeReportOpt.isPresent(),
                                                        descriptorAfterReportOpt.isEmpty()));
                                    }
                                }
                            }
                            first = mdibHistorian.applyReportOnStorage(first, report);
                        } else {
                            first = mdibHistorian.applyReportOnStorage(first, report);
                            second = mdibHistorian.applyReportOnStorage(second, report);
                        }
                    }
                } catch (PreprocessingException | ReportProcessingException e) {
                    fail(e);
                }
            });
        }
        assertTestData(
                acceptableSequenceSeen.get(), "No DescriptionModificationReport seen during test run, test failed.");
    }

    @Test
    @TestIdentifier(EnabledTestConfig.BICEPS_R5024)
    @TestDescription("Retrieves each report part from each description modification report seen during the test run and"
            + " checks that each descriptor does not contain nested descriptors.")
    void testRequirementR5024() throws NoTestData, IOException {
        try (final var messages =
                messageStorage.getInboundMessagesByBodyType(Constants.MSG_DESCRIPTION_MODIFICATION_REPORT)) {
            final var descriptorsSeen = new AtomicInteger(0);

            messages.getStream().forEach(messageContent -> {
                try {
                    final var soapMessage = marshalling.unmarshal(
                            new ByteArrayInputStream(messageContent.getBody().getBytes(StandardCharsets.UTF_8)));
                    final var reportOpt = soapUtil.getBody(soapMessage, DescriptionModificationReport.class);
                    if (reportOpt.isPresent()) {
                        for (var part : reportOpt.orElseThrow().getReportPart()) {
                            for (var descriptor : part.getDescriptor()) {
                                descriptorsSeen.incrementAndGet();
                                checkNestedDescriptors(descriptor);
                            }
                        }
                    }
                } catch (MarshallingException e) {
                    fail("Error unmarshalling MessageContent " + e);
                }
            });

            assertTestData(
                    descriptorsSeen.get(),
                    "No Descriptors in DescriptionModificationReports seen during test run, test failed.");
        }
    }

    @Test
    @DisplayName("Biceps:R5025_0: A SERVICE PROVIDER shall order msg:DescriptionModificationReport/msg:ReportPart"
            + " elements in a way the report parts containing parent descriptors appear before report parts containing"
            + " their child descriptors.")
    @TestIdentifier(EnabledTestConfig.BICEPS_R5025_0)
    @TestDescription("For every DescriptionModificationReport received from the DUT, and for all parent-child"
            + " relationships between the elements contained in the report, checks that the reportPart containing"
            + " the parent comes before the reportPart containing the child.")
    @RequirePrecondition(simplePreconditions = ConditionalPreconditions.DescriptionChangedPrecondition.class)
    void testRequirementR5025() throws NoTestData, IOException {
        try (final var messages =
                messageStorage.getInboundMessagesByBodyType(Constants.MSG_DESCRIPTION_MODIFICATION_REPORT)) {
            final var descriptorsSeen = new AtomicInteger(0);

            messages.getStream().forEach(messageContent -> {
                try {
                    final var soapMessage = marshalling.unmarshal(
                            new ByteArrayInputStream(messageContent.getBody().getBytes(StandardCharsets.UTF_8)));
                    final var reportOpt = soapUtil.getBody(soapMessage, DescriptionModificationReport.class);
                    reportOpt.ifPresent(descriptionModificationReport ->
                            checkOrderOfReportParts(descriptionModificationReport, descriptorsSeen));
                } catch (MarshallingException e) {
                    fail("Error unmarshalling MessageContent " + e);
                }
            });

            assertTestData(
                    descriptorsSeen.get(),
                    "No DescriptionModificationReports with Parent-Child Relationships between Descriptors "
                            + " seen during test run, test failed.");
        }
    }

    private void checkOrderOfReportParts(
            final DescriptionModificationReport report, final AtomicInteger descriptorsSeen) {
        final var modifiedParentHandles = new HashMap<String, Pair<Integer, String>>();
        final List<DescriptionModificationReport.ReportPart> reportParts = report.getReportPart();
        for (var i = 0; i < reportParts.size(); i++) {
            final var part = reportParts.get(i);
            final String key = part.getParentDescriptor();
            final List<AbstractDescriptor> descriptors = part.getDescriptor();
            if (!modifiedParentHandles.containsKey(key) && !descriptors.isEmpty()) {
                final String firstChildDescriptorHandle = descriptors.get(0).getHandle();
                modifiedParentHandles.put(key, Pair.of(i, firstChildDescriptorHandle));
            }
        }
        for (var indexOfParentUpdate = 0; indexOfParentUpdate < reportParts.size(); indexOfParentUpdate++) {
            final var part = reportParts.get(indexOfParentUpdate);
            for (var descriptor : part.getDescriptor()) {
                final String key = descriptor.getHandle();

                if (modifiedParentHandles.containsKey(key)) {
                    // we found a parent-child relationship within the updated descriptors
                    // -> check if the parent update came first
                    final Pair<Integer, String> pair = modifiedParentHandles.get(key);
                    final Integer indexOfFirstChildUpdate = pair.getLeft();
                    final String childHandle = pair.getRight();
                    assertTrue(
                            indexOfParentUpdate < indexOfFirstChildUpdate,
                            String.format(
                                    "reportPart containing child descriptor '%s' is listed before the reportPart"
                                            + " containing its parent descriptor '%s' in a DescriptionModificationReport.",
                                    childHandle, key));
                    descriptorsSeen.incrementAndGet();
                }
            }
        }
    }

    private void checkNestedDescriptors(final AbstractDescriptor descriptor) {
        final var handle = descriptor.getHandle();
        final String errorMsg = "%s with handle %s should not have nested descriptor %s";
        if (descriptor instanceof AlertSystemDescriptor) {
            // verify that the alarm system has no alarm signals or alarm conditions
            final var alertSystem = (AlertSystemDescriptor) descriptor;
            assertTrue(
                    alertSystem.getAlertCondition().isEmpty(),
                    String.format(errorMsg, descriptor.getClass(), handle, alertSystem.getAlertCondition()));
            assertTrue(
                    alertSystem.getAlertSignal().isEmpty(),
                    String.format(errorMsg, descriptor.getClass(), handle, alertSystem.getAlertSignal()));
        } else if (descriptor instanceof ChannelDescriptor) {
            // verify that the channel has no metrics
            final var channel = (ChannelDescriptor) descriptor;
            assertTrue(
                    channel.getMetric().isEmpty(),
                    String.format(errorMsg, descriptor.getClass(), handle, channel.getMetric()));
        } else if (descriptor instanceof MdsDescriptor) {
            // verify that the mds has no alert system, sco, system context, clock, batteries or vmds
            final var mds = (MdsDescriptor) descriptor;
            assertNull(
                    mds.getAlertSystem(), String.format(errorMsg, descriptor.getClass(), handle, mds.getAlertSystem()));
            assertNull(mds.getSco(), String.format(errorMsg, descriptor.getClass(), handle, mds.getSco()));
            assertNull(
                    mds.getSystemContext(),
                    String.format(errorMsg, descriptor.getClass(), handle, mds.getSystemContext()));
            assertNull(mds.getClock(), String.format(errorMsg, descriptor.getClass(), handle, mds.getClock()));
            assertTrue(
                    mds.getBattery().isEmpty(),
                    String.format(errorMsg, descriptor.getClass(), handle, mds.getBattery()));
            assertTrue(mds.getVmd().isEmpty(), String.format(errorMsg, descriptor.getClass(), handle, mds.getVmd()));
        } else if (descriptor instanceof ScoDescriptor) {
            // verify that the sco has no operations
            final var sco = (ScoDescriptor) descriptor;
            assertTrue(
                    sco.getOperation().isEmpty(),
                    String.format(errorMsg, descriptor.getClass(), handle, sco.getOperation()));
        } else if (descriptor instanceof SystemContextDescriptor) {
            // verify that the system context has no patient and location context and no ensemble, operator
            // workflow and mean contexts
            final var systemContext = (SystemContextDescriptor) descriptor;
            assertNull(
                    systemContext.getPatientContext(),
                    String.format(errorMsg, descriptor.getClass(), handle, systemContext.getPatientContext()));
            assertNull(
                    systemContext.getLocationContext(),
                    String.format(errorMsg, descriptor.getClass(), handle, systemContext.getLocationContext()));
            assertTrue(
                    systemContext.getEnsembleContext().isEmpty(),
                    String.format(errorMsg, descriptor.getClass(), handle, systemContext.getEnsembleContext()));
            assertTrue(
                    systemContext.getOperatorContext().isEmpty(),
                    String.format(errorMsg, descriptor.getClass(), handle, systemContext.getOperatorContext()));
            assertTrue(
                    systemContext.getWorkflowContext().isEmpty(),
                    String.format(errorMsg, descriptor.getClass(), handle, systemContext.getWorkflowContext()));
            assertTrue(
                    systemContext.getMeansContext().isEmpty(),
                    String.format(errorMsg, descriptor.getClass(), handle, systemContext.getMeansContext()));
        } else if (descriptor instanceof VmdDescriptor) {
            // verify that the vmd has no channels, vmd and sco
            final var vmd = (VmdDescriptor) descriptor;
            assertTrue(
                    vmd.getChannel().isEmpty(),
                    String.format(errorMsg, descriptor.getClass(), handle, vmd.getChannel()));
            assertNull(
                    vmd.getAlertSystem(), String.format(errorMsg, descriptor.getClass(), handle, vmd.getAlertSystem()));
            assertNull(vmd.getSco(), String.format(errorMsg, descriptor.getClass(), handle, vmd.getSco()));
        }
    }

    @Test
    @TestIdentifier(EnabledTestConfig.BICEPS_R5051)
    @TestDescription("Retrieves every report part with modification type crt from every description modification report"
            + " seen during the test run and compares the descriptor version from each state with the descriptor version"
            + " of their descriptor.")
    @RequirePrecondition(simplePreconditions = {ConditionalPreconditions.DescriptionModificationCrtPrecondition.class})
    void testRequirementR5051() throws NoTestData, IOException {
        try (final var messages =
                messageStorage.getInboundMessagesByBodyType(Constants.MSG_DESCRIPTION_MODIFICATION_REPORT)) {

            final var acceptableSequenceSeen = new AtomicInteger(0);
            final var impliedValueMap = new HashMap<String, InitialImpliedValue>();
            messages.getStream().forEach(messageContent -> {
                try {
                    final var soapMessage = marshalling.unmarshal(
                            new ByteArrayInputStream(messageContent.getBody().getBytes(StandardCharsets.UTF_8)));
                    final var reportOpt = soapUtil.getBody(soapMessage, DescriptionModificationReport.class);
                    final var crtReportParts = reportOpt.orElseThrow().getReportPart().stream()
                            .filter(part ->
                                    ImpliedValueUtil.getModificationType(part).equals(DescriptionModificationType.CRT))
                            .collect(Collectors.toList());
                    for (var part : crtReportParts) {
                        for (var state : part.getState()) {
                            acceptableSequenceSeen.incrementAndGet();
                            final var handle = state.getDescriptorHandle();
                            final var descriptorVersion = ImpliedValueUtil.getStateDescriptorVersion(
                                    state,
                                    impliedValueMap.computeIfAbsent(
                                            reportOpt.orElseThrow().getSequenceId(), k -> new InitialImpliedValue()));
                            final var descriptor = part.getDescriptor().stream()
                                    .filter(abstractDescriptor ->
                                            abstractDescriptor.getHandle().equals(handle))
                                    .findFirst();
                            assertTrue(
                                    descriptor.isPresent(),
                                    String.format("No matching descriptor for handle %s found.", handle));
                            final var descriptorsDescriptorVersion = ImpliedValueUtil.getDescriptorVersion(
                                    descriptor.orElseThrow(),
                                    impliedValueMap.computeIfAbsent(
                                            reportOpt.orElseThrow().getSequenceId(), k -> new InitialImpliedValue()));
                            assertEquals(
                                    descriptorsDescriptorVersion,
                                    descriptorVersion,
                                    String.format(
                                            "The descriptor version of state is %s, but should be %s, for %s.",
                                            descriptorVersion, descriptorsDescriptorVersion, handle));
                        }
                    }
                } catch (MarshallingException e) {
                    fail("Error unmarshalling MessageContent " + e);
                } catch (InitialImpliedValueException e) {
                    fail(
                            "The descriptor version was an implied value, but was not allowed to be one."
                                    + " It occurred previously without an implied value.",
                            e);
                }
            });

            assertTestData(
                    acceptableSequenceSeen.get(),
                    "No report parts with description modification type crt" + " seen during test run, test failed");
        }
    }

    @Test
    @TestIdentifier(EnabledTestConfig.BICEPS_R5052)
    @TestDescription("Retrieves every report part with modification type upt from every description modification report"
            + " seen during the test run and compares the descriptor version from each state with the descriptor version"
            + " of their descriptor.")
    @RequirePrecondition(simplePreconditions = {ConditionalPreconditions.DescriptionModificationUptPrecondition.class})
    void testRequirementR5052() throws NoTestData, IOException {
        try (final var messages =
                messageStorage.getInboundMessagesByBodyType(Constants.MSG_DESCRIPTION_MODIFICATION_REPORT)) {
            final var acceptableSequenceSeen = new AtomicInteger(0);

            final var impliedValueMap = new HashMap<Object, InitialImpliedValue>();
            messages.getStream().forEach(messageContent -> {
                try {
                    final var soapMessage = marshalling.unmarshal(
                            new ByteArrayInputStream(messageContent.getBody().getBytes(StandardCharsets.UTF_8)));
                    final var reportOpt = soapUtil.getBody(soapMessage, DescriptionModificationReport.class);
                    final var uptReportParts = reportOpt.orElseThrow().getReportPart().stream()
                            .filter(part ->
                                    ImpliedValueUtil.getModificationType(part).equals(DescriptionModificationType.UPT))
                            .collect(Collectors.toList());
                    for (var part : uptReportParts) {
                        acceptableSequenceSeen.incrementAndGet();
                        for (var state : part.getState()) {
                            final var handle = state.getDescriptorHandle();
                            final var descriptorVersion = ImpliedValueUtil.getStateDescriptorVersion(
                                    state,
                                    impliedValueMap.computeIfAbsent(
                                            reportOpt.orElseThrow().getSequenceId(), k -> new InitialImpliedValue()));
                            final var descriptor = part.getDescriptor().stream()
                                    .filter(abstractDescriptor ->
                                            abstractDescriptor.getHandle().equals(handle))
                                    .findFirst();
                            assertTrue(
                                    descriptor.isPresent(),
                                    String.format("No matching descriptor for handle %s found.", handle));
                            final var descriptorsDescriptorVersion = ImpliedValueUtil.getDescriptorVersion(
                                    descriptor.orElseThrow(),
                                    impliedValueMap.computeIfAbsent(
                                            reportOpt.orElseThrow().getSequenceId(), k -> new InitialImpliedValue()));
                            assertEquals(
                                    descriptorsDescriptorVersion,
                                    descriptorVersion,
                                    String.format(
                                            "The descriptor version of state is %s, but should be %s, for %s.",
                                            descriptorVersion,
                                            ImpliedValueUtil.getDescriptorVersion(
                                                    descriptor.orElseThrow(),
                                                    impliedValueMap.computeIfAbsent(
                                                            reportOpt
                                                                    .orElseThrow()
                                                                    .getSequenceId(),
                                                            k -> new InitialImpliedValue())),
                                            handle));
                        }
                    }
                } catch (MarshallingException | InitialImpliedValueException e) {
                    fail("Error unmarshalling MessageContent " + e);
                }
            });

            assertTestData(
                    acceptableSequenceSeen.get(),
                    "No report parts with description modification type upt" + " seen during test run, test failed");
        }
    }

    @Test
    @TestIdentifier(EnabledTestConfig.BICEPS_C11)
    @TestDescription("Starting from the initially retrieved mdib, applies each episodic report to the mdib and checks"
            + " whether at least one child or attribute has changed for each AbstractAlertState contained in an"
            + " EpisodicAlertReport.")
    @RequirePrecondition(simplePreconditions = {ConditionalPreconditions.TriggerEpisodicAlertReportPrecondition.class})
    void testRequirementC11() throws NoTestData, IOException {
        final var mdibHistorian = mdibHistorianFactory.createMdibHistorian(
                messageStorage, getInjector().getInstance(TestRunObserver.class));

        final var acceptableSequenceSeen = new AtomicInteger(0);

        try (final Stream<String> sequenceIds = mdibHistorian.getKnownSequenceIds()) {
            sequenceIds.forEach(sequenceId -> {

                // get relevant reports
                try (final var reports = mdibHistorian.getAllReports(sequenceId)) {

                    var first = mdibHistorian.createNewStorage(sequenceId);
                    var second = mdibHistorian.createNewStorage(sequenceId);

                    for (final Iterator<AbstractReport> iterator = reports.iterator(); iterator.hasNext(); ) {
                        final AbstractReport report = iterator.next();

                        if (report instanceof EpisodicAlertReport) {
                            acceptableSequenceSeen.incrementAndGet();
                            second = mdibHistorian.applyReportOnStorage(second, report);
                            for (var reportPart : ((AbstractAlertReport) report).getReportPart()) {
                                for (var alertState : reportPart.getAlertState()) {
                                    final var stateBeforeReport =
                                            first.getState(alertState.getDescriptorHandle(), AbstractAlertState.class);
                                    final var stateAfterReport =
                                            second.getState(alertState.getDescriptorHandle(), AbstractAlertState.class);
                                    assertTrue(
                                            stateBeforeReport.isPresent() && stateAfterReport.isPresent(),
                                            String.format(STATE_ABSENT, alertState.getDescriptorHandle()));
                                    assertNotEquals(
                                            stateAfterReport.orElseThrow(),
                                            stateBeforeReport.orElseThrow(),
                                            String.format(STATE_UNCHANGED, alertState.getDescriptorHandle()));
                                }
                            }
                            first = mdibHistorian.applyReportOnStorage(first, report);
                        } else {
                            first = mdibHistorian.applyReportOnStorage(first, report);
                            second = mdibHistorian.applyReportOnStorage(second, report);
                        }
                    }
                } catch (PreprocessingException | ReportProcessingException e) {
                    fail(e);
                }
            });
        }
        assertTestData(acceptableSequenceSeen.get(), "No AlertReports seen during test run, test failed.");
    }

    @Test
    @TestIdentifier(EnabledTestConfig.BICEPS_C12)
    @TestDescription("Starting from the initially retrieved mdib, applies each episodic report to the mdib and checks"
            + " whether at least one child or attribute has changed for each AbstractComponentState contained in an"
            + " EpisodicComponentReport.")
    @RequirePrecondition(
            simplePreconditions = {ConditionalPreconditions.TriggerEpisodicComponentReportPrecondition.class})
    void testRequirementC12() throws NoTestData, PreprocessingException, ReportProcessingException, IOException {
        final var mdibHistorian = mdibHistorianFactory.createMdibHistorian(
                messageStorage, getInjector().getInstance(TestRunObserver.class));

        final var acceptableSequenceSeen = new AtomicInteger(0);

        try (final Stream<String> sequenceIds = mdibHistorian.getKnownSequenceIds()) {
            sequenceIds.forEach(sequenceId -> {

                // get relevant reports
                try (final var reports = mdibHistorian.getAllReports(sequenceId)) {

                    var first = mdibHistorian.createNewStorage(sequenceId);
                    var second = mdibHistorian.createNewStorage(sequenceId);

                    for (final Iterator<AbstractReport> iterator = reports.iterator(); iterator.hasNext(); ) {
                        final AbstractReport report = iterator.next();

                        if (report instanceof EpisodicComponentReport) {
                            acceptableSequenceSeen.incrementAndGet();
                            second = mdibHistorian.applyReportOnStorage(second, report);
                            for (var reportPart : ((AbstractComponentReport) report).getReportPart()) {
                                for (var componentState : reportPart.getComponentState()) {
                                    final var stateBeforeReport = first.getState(
                                            componentState.getDescriptorHandle(), AbstractDeviceComponentState.class);
                                    final var stateAfterReport = second.getState(
                                            componentState.getDescriptorHandle(), AbstractDeviceComponentState.class);
                                    assertTrue(
                                            stateBeforeReport.isPresent() && stateAfterReport.isPresent(),
                                            String.format(STATE_ABSENT, componentState.getDescriptorHandle()));
                                    assertNotEquals(
                                            stateAfterReport.orElseThrow(),
                                            stateBeforeReport.orElseThrow(),
                                            String.format(STATE_UNCHANGED, componentState.getDescriptorHandle()));
                                }
                            }
                            first = mdibHistorian.applyReportOnStorage(first, report);
                        } else {
                            first = mdibHistorian.applyReportOnStorage(first, report);
                            second = mdibHistorian.applyReportOnStorage(second, report);
                        }
                    }
                } catch (PreprocessingException | ReportProcessingException e) {
                    fail(e);
                }
            });
        }
        assertTestData(acceptableSequenceSeen.get(), "No ComponentReports seen during test run, test failed.");
    }

    @Test
    @TestIdentifier(EnabledTestConfig.BICEPS_C13)
    @TestDescription("Starting from the initially retrieved mdib, applies each episodic report to the mdib and checks"
            + " whether at least one child or attribute has changed for each AbstractContextState contained in an"
            + " EpisodicContextReport.")
    @RequirePrecondition(
            simplePreconditions = {ConditionalPreconditions.TriggerEpisodicContextReportPrecondition.class})
    void testRequirementC13() throws NoTestData, PreprocessingException, ReportProcessingException, IOException {
        final var mdibHistorian = mdibHistorianFactory.createMdibHistorian(
                messageStorage, getInjector().getInstance(TestRunObserver.class));

        final var acceptableSequenceSeen = new AtomicInteger(0);

        try (final Stream<String> sequenceIds = mdibHistorian.getKnownSequenceIds()) {
            sequenceIds.forEach(sequenceId -> {

                // get relevant reports
                try (final var reports = mdibHistorian.getAllReports(sequenceId)) {

                    var first = mdibHistorian.createNewStorage(sequenceId);
                    var second = mdibHistorian.createNewStorage(sequenceId);

                    for (final Iterator<AbstractReport> iterator = reports.iterator(); iterator.hasNext(); ) {
                        final AbstractReport report = iterator.next();

                        if (report instanceof EpisodicContextReport) {
                            acceptableSequenceSeen.incrementAndGet();
                            second = mdibHistorian.applyReportOnStorage(second, report);

                            for (var reportPart : ((AbstractContextReport) report).getReportPart()) {
                                for (var contextState : reportPart.getContextState()) {
                                    final var beforeReport = first.getEntity(contextState.getDescriptorHandle());
                                    final var afterReport = second.getEntity(contextState.getDescriptorHandle());
                                    final var stateBeforeReport =
                                            beforeReport.orElseThrow().getStates(AbstractContextState.class).stream()
                                                    .filter(state ->
                                                            state.getHandle().equals(contextState.getHandle()))
                                                    .findFirst();
                                    final var stateAfterReport =
                                            afterReport.orElseThrow().getStates(AbstractContextState.class).stream()
                                                    .filter(state ->
                                                            state.getHandle().equals(contextState.getHandle()))
                                                    .findFirst();
                                    if (stateBeforeReport.isPresent() && stateAfterReport.isPresent()) {
                                        assertNotEquals(
                                                stateAfterReport.orElseThrow(),
                                                stateBeforeReport.orElseThrow(),
                                                String.format(STATE_UNCHANGED, contextState.getDescriptorHandle()));
                                    } else {
                                        assertTrue(
                                                stateBeforeReport.isEmpty() && stateAfterReport.isPresent(),
                                                String.format(STATE_ABSENT, contextState.getDescriptorHandle()));
                                    }
                                }
                            }
                            first = mdibHistorian.applyReportOnStorage(first, report);
                        } else {
                            first = mdibHistorian.applyReportOnStorage(first, report);
                            second = mdibHistorian.applyReportOnStorage(second, report);
                        }
                    }
                } catch (PreprocessingException | ReportProcessingException e) {
                    fail(e);
                }
            });
        }
        assertTestData(acceptableSequenceSeen.get(), "No ContextReports seen during test run, test failed.");
    }

    @Test
    @TestIdentifier(EnabledTestConfig.BICEPS_C14)
    @TestDescription("Starting from the initially retrieved mdib, applies each episodic report to the mdib and checks"
            + " whether at least one child or attribute has changed for each AbstractMetricState contained in an"
            + " EpisodicMetricReport.")
    @RequirePrecondition(simplePreconditions = {ConditionalPreconditions.TriggerEpisodicMetricReportPrecondition.class})
    void testRequirementC14() throws NoTestData, PreprocessingException, ReportProcessingException, IOException {
        final var mdibHistorian = mdibHistorianFactory.createMdibHistorian(
                messageStorage, getInjector().getInstance(TestRunObserver.class));

        final var acceptableSequenceSeen = new AtomicInteger(0);

        try (final Stream<String> sequenceIds = mdibHistorian.getKnownSequenceIds()) {
            sequenceIds.forEach(sequenceId -> {

                // get relevant reports
                try (final var reports = mdibHistorian.getAllReports(sequenceId)) {

                    var first = mdibHistorian.createNewStorage(sequenceId);
                    var second = mdibHistorian.createNewStorage(sequenceId);

                    for (final Iterator<AbstractReport> iterator = reports.iterator(); iterator.hasNext(); ) {
                        final AbstractReport report = iterator.next();

                        if (report instanceof EpisodicMetricReport) {
                            acceptableSequenceSeen.incrementAndGet();
                            second = mdibHistorian.applyReportOnStorage(second, report);

                            for (var reportPart : ((EpisodicMetricReport) report).getReportPart()) {
                                for (var metricState : reportPart.getMetricState()) {
                                    final var stateBeforeReport = first.getState(
                                            metricState.getDescriptorHandle(), AbstractMetricState.class);
                                    final var stateAfterReport = second.getState(
                                            metricState.getDescriptorHandle(), AbstractMetricState.class);
                                    assertTrue(
                                            stateBeforeReport.isPresent() && stateAfterReport.isPresent(),
                                            String.format(STATE_ABSENT, metricState.getDescriptorHandle()));
                                    assertNotEquals(
                                            stateAfterReport.orElseThrow(),
                                            stateBeforeReport.orElseThrow(),
                                            String.format(STATE_UNCHANGED, metricState.getDescriptorHandle()));
                                }
                            }
                            first = mdibHistorian.applyReportOnStorage(first, report);
                        } else {
                            first = mdibHistorian.applyReportOnStorage(first, report);
                            second = mdibHistorian.applyReportOnStorage(second, report);
                        }
                    }
                } catch (PreprocessingException | ReportProcessingException e) {
                    fail(e);
                }
            });
        }
        assertTestData(acceptableSequenceSeen.get(), "No MetricReports seen during test run, test failed.");
    }

    @Test
    @TestIdentifier(EnabledTestConfig.BICEPS_C15)
    @TestDescription("Starting from the initially retrieved mdib, applies each episodic report to the mdib and checks"
            + " whether at least one child or attribute has changed for each AbstractOperationState contained in an"
            + " EpisodicOperationalStateReport.")
    @RequirePrecondition(
            simplePreconditions = {ConditionalPreconditions.TriggerEpisodicOperationalStateReportPrecondition.class})
    void testRequirementC15() throws NoTestData, IOException {
        final var mdibHistorian = mdibHistorianFactory.createMdibHistorian(
                messageStorage, getInjector().getInstance(TestRunObserver.class));

        final var acceptableSequenceSeen = new AtomicInteger(0);

        try (final Stream<String> sequenceIds = mdibHistorian.getKnownSequenceIds()) {
            sequenceIds.forEach(sequenceId -> {

                // get relevant reports
                try (final var reports = mdibHistorian.getAllReports(sequenceId)) {

                    var first = mdibHistorian.createNewStorage(sequenceId);
                    var second = mdibHistorian.createNewStorage(sequenceId);

                    for (final Iterator<AbstractReport> reportIterator = reports.iterator();
                            reportIterator.hasNext(); ) {
                        final AbstractReport report = reportIterator.next();

                        if (report instanceof EpisodicOperationalStateReport) {
                            acceptableSequenceSeen.incrementAndGet();
                            second = mdibHistorian.applyReportOnStorage(second, report);

                            for (var reportPart : ((EpisodicOperationalStateReport) report).getReportPart()) {
                                for (var operationState : reportPart.getOperationState()) {
                                    final var stateBeforeReport = first.getState(
                                            operationState.getDescriptorHandle(), AbstractOperationState.class);
                                    final var stateAfterReport = second.getState(
                                            operationState.getDescriptorHandle(), AbstractOperationState.class);
                                    assertTrue(
                                            stateBeforeReport.isPresent() && stateAfterReport.isPresent(),
                                            String.format(STATE_ABSENT, operationState.getDescriptorHandle()));
                                    assertNotEquals(
                                            stateAfterReport.orElseThrow(),
                                            stateBeforeReport.orElseThrow(),
                                            String.format(STATE_UNCHANGED, operationState.getDescriptorHandle()));
                                }
                            }
                            first = mdibHistorian.applyReportOnStorage(first, report);
                        } else {
                            first = mdibHistorian.applyReportOnStorage(first, report);
                            second = mdibHistorian.applyReportOnStorage(second, report);
                        }
                    }
                } catch (PreprocessingException | ReportProcessingException e) {
                    fail(e);
                }
            });
        }
        assertTestData(acceptableSequenceSeen.get(), "No OperationalStateReports seen during test run, test failed.");
    }
}
