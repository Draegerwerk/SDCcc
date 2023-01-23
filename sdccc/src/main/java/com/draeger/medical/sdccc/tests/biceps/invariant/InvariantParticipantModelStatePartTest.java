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
import com.draeger.medical.sdccc.manipulation.precondition.impl.ManipulationPreconditions;
import com.draeger.medical.sdccc.messages.MessageStorage;
import com.draeger.medical.sdccc.messages.mapping.ManipulationData;
import com.draeger.medical.sdccc.messages.mapping.ManipulationParameter;
import com.draeger.medical.sdccc.sdcri.testclient.TestClient;
import com.draeger.medical.sdccc.tests.InjectorTestBase;
import com.draeger.medical.sdccc.tests.annotations.RequirePrecondition;
import com.draeger.medical.sdccc.tests.annotations.TestDescription;
import com.draeger.medical.sdccc.tests.annotations.TestIdentifier;
import com.draeger.medical.sdccc.tests.util.ImpliedValueUtil;
import com.draeger.medical.sdccc.tests.util.NoTestData;
import com.draeger.medical.sdccc.util.Constants;
import com.draeger.medical.t2iapi.ResponseTypes;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.somda.sdc.biceps.model.message.AbstractMetricReport;
import org.somda.sdc.biceps.model.message.AbstractReport;
import org.somda.sdc.biceps.model.message.WaveformStream;
import org.somda.sdc.biceps.model.participant.ComponentActivation;
import org.somda.sdc.biceps.model.participant.MetricCategory;
import org.somda.sdc.dpws.soap.MarshallingService;
import org.somda.sdc.dpws.soap.SoapUtil;
import org.somda.sdc.dpws.soap.exception.MarshallingException;

/**
 * BICEPS participant model state part tests (ch. 5.4).
 */
public class InvariantParticipantModelStatePartTest extends InjectorTestBase {
    public static final String NO_SET_METRIC_STATUS_MANIPULATION =
            "No setMetricStatus manipulation for metrics with category %s performed, test failed.";
    public static final String NO_SUCCESSFUL_MANIPULATION =
            "No successful setMetricStatus manipulation seen, test failed.";
    public static final String NO_REPORT_IN_TIME_INTERVAL =
            "No metric reports or waveform streams for %s manipulation found between %s and %s, test failed.";
    public static final String NO_REPORT_WITH_EXPECTED_HANDLE =
            "No metric reports or waveform streams with metric handle %s found, test failed.";
    public static final String NO_REPORT_WITH_EXPECTED_ACTIVATION_STATE =
            "No metric reports or waveform streams containing the metric handle %s with the expected activation state"
                    + " %s found, test failed.";
    public static final String WRONG_ACTIVATION_STATE =
            "The manipulated activation state for metric %s should be %s but is %s";
    private static final long BUFFER = TimeUnit.NANOSECONDS.convert(5, TimeUnit.SECONDS);
    private MessageStorage messageStorage;
    private MarshallingService marshalling;
    private SoapUtil soapUtil;

    @BeforeEach
    void setUp() {
        this.messageStorage = getInjector().getInstance(MessageStorage.class);
        final var riInjector = getInjector().getInstance(TestClient.class).getInjector();
        this.marshalling = riInjector.getInstance(MarshallingService.class);
        this.soapUtil = riInjector.getInstance(SoapUtil.class);
    }

    @Test
    @DisplayName("If pm:AbstractMetricDescriptor/@MetricCategory = Msrmt and the measurement for the METRIC is being"
            + " performed, the SERVICE PROVIDER SHALL set pm:AbstractMetricState/@ActivationState = On.")
    @TestIdentifier(EnabledTestConfig.BICEPS_547_0_0)
    @TestDescription("For each metric with the category MSRMT, the device is manipulated to perform measurements and"
            + " then it is verified that the ActivationState of the metric is set to On.")
    @RequirePrecondition(
            manipulationPreconditions = {ManipulationPreconditions.MetricStatusManipulationMSRMTActivationStateON.class
            })
    void testRequirement54700() throws NoTestData {
        final var metricCategory = MetricCategory.MSRMT;
        final var activationState = ComponentActivation.ON;
        testRequirement547(metricCategory, activationState);
    }

    @Test
    @DisplayName(
            "If pm:AbstractMetricDescriptor/@MetricCategory = Msrmt and the measurement for the METRIC  is"
                    + " currently initializing, the SERVICE PROVIDER SHALL set pm:AbstractMetricState/@ActivationState = NotRdy.")
    @TestIdentifier(EnabledTestConfig.BICEPS_547_1)
    @TestDescription("For each metric with the category MSRMT, the device is manipulated to initialize measurements and"
            + " then it is verified that the ActivationState of the metric is set to NotRdy.")
    @RequirePrecondition(
            manipulationPreconditions = {
                ManipulationPreconditions.MetricStatusManipulationMSRMTActivationStateNOTRDY.class
            })
    void testRequirement5471() throws NoTestData {
        final var metricCategory = MetricCategory.MSRMT;
        final var activationState = ComponentActivation.NOT_RDY;
        testRequirement547(metricCategory, activationState);
    }

    @Test
    @DisplayName("If pm:AbstractMetricDescriptor/@MetricCategory = Msrmt and the measurement for the METRIC has been"
            + " initialized, but is not being performed, the SERVICE PROVIDER SHALL set"
            + " pm:AbstractMetricState/@ActivationState = StndBy.")
    @TestIdentifier(EnabledTestConfig.BICEPS_547_2)
    @TestDescription("For each metric with the category MSRMT, the device is manipulated so that the measurement of the"
            + " metric is initialized but no measurement is performed and then it is verified that the ActivationState of"
            + " the metric is set to StndBy.")
    @RequirePrecondition(
            manipulationPreconditions = {
                ManipulationPreconditions.MetricStatusManipulationMSRMTActivationStateSTNDBY.class
            })
    void testRequirement5472() throws NoTestData {
        final var metricCategory = MetricCategory.MSRMT;
        final var activationState = ComponentActivation.STND_BY;
        testRequirement547(metricCategory, activationState);
    }

    @Test
    @DisplayName("If pm:AbstractMetricDescriptor/@MetricCategory = Msrmt and the measurement for the "
            + "METRIC is currently de-initializing, the SERVICE PROVIDER SHALL set "
            + "pm:AbstractMetricState/@ActivationState = Shtdn.")
    @TestIdentifier(EnabledTestConfig.BICEPS_547_3)
    @TestDescription("For each metric with the category MSRMT, the device is manipulated to de-initialize "
            + "measurements and then it is verified that the ActivationState of the metric is set to Shtdn.")
    @RequirePrecondition(
            manipulationPreconditions = {
                ManipulationPreconditions.MetricStatusManipulationMSRMTActivationStateSHTDN.class
            })
    void testRequirement5473() throws NoTestData {
        final var metricCategory = MetricCategory.MSRMT;
        final var activationState = ComponentActivation.SHTDN;
        testRequirement547(metricCategory, activationState);
    }

    @Test
    @DisplayName("If pm:AbstractMetricDescriptor/@MetricCategory = Msrmt and the measurement for the METRIC is not"
            + " being performed and is de-initialized, the SERVICE PROVIDER SHALL set"
            + " pm:AbstractMetricState/@ActivationState = Off.")
    @TestIdentifier(EnabledTestConfig.BICEPS_547_4)
    @TestDescription("For each metric with the category MSRMT, the device is manipulated so that the measurement of"
            + " the metric is de-initialized and no measurement is performed and then it is verified that the"
            + " ActivationState of the metric is set to Off.")
    @RequirePrecondition(
            manipulationPreconditions = {ManipulationPreconditions.MetricStatusManipulationMSRMTActivationStateOFF.class
            })
    void testRequirement5474() throws NoTestData {
        final var metricCategory = MetricCategory.MSRMT;
        final var activationState = ComponentActivation.OFF;
        testRequirement547(metricCategory, activationState);
    }

    @Test
    @DisplayName("If pm:AbstractMetricDescriptor/@MetricCategory = Msrmt and the measurement for the METRIC has"
            + " failed, the SERVICE PROVIDER SHALL set pm:AbstractMetricState/@ActivationState = Fail.")
    @TestIdentifier(EnabledTestConfig.BICEPS_547_5)
    @TestDescription("For each metric with the category MSRMT, the device is manipulated so that the measurement of"
            + " the metric has failed and then it is verified that the ActivationState of the metric is set to Fail.")
    @RequirePrecondition(
            manipulationPreconditions = {
                ManipulationPreconditions.MetricStatusManipulationMSRMTActivationStateFAIL.class
            })
    void testRequirement5475() throws NoTestData {
        final var metricCategory = MetricCategory.MSRMT;
        final var activationState = ComponentActivation.FAIL;
        testRequirement547(metricCategory, activationState);
    }

    @Test
    @DisplayName("If pm:AbstractMetricDescriptor/@MetricCategory = Set and the setting is currently being applied, the"
            + " SERVICE PROVIDER SHALL set pm:AbstractMetricState/@ActivationState = On.")
    @TestIdentifier(EnabledTestConfig.BICEPS_547_6_0)
    @TestDescription("For each metric with the category SET, the device is manipulated to apply settings and"
            + " then it is verified that the ActivationState of the metric is set to On.")
    @RequirePrecondition(
            manipulationPreconditions = {ManipulationPreconditions.MetricStatusManipulationSETActivationStateON.class})
    void testRequirement54760() throws NoTestData {
        final var metricCategory = MetricCategory.SET;
        final var activationState = ComponentActivation.ON;
        testRequirement547(metricCategory, activationState);
    }

    @Test
    @DisplayName("If pm:AbstractMetricDescriptor/@MetricCategory = Set and the setting is currently initializing, "
        + "the SERVICE PROVIDER SHALL set pm:AbstractMetricState/@ActivationState = NotRdy.")
    @TestIdentifier(EnabledTestConfig.BICEPS_547_7)
    @TestDescription("For each metric with the category SET, the device is manipulated to initialize the setting "
        + "and then it is verified that the ActivationState of the metric is set to NotRdy.")
    @RequirePrecondition(
        manipulationPreconditions = {
            ManipulationPreconditions.MetricStatusManipulationSETActivationStateNOTRDY.class
        })
    void testRequirement5477() throws NoTestData {
        final var metricCategory = MetricCategory.SET;
        final var activationState = ComponentActivation.NOT_RDY;
        testRequirement547(metricCategory, activationState);
    }

    @Test
    @DisplayName("If pm:AbstractMetricDescriptor/@MetricCategory = Set and the setting has been initialized, but is not"
            + " being applied, the SERVICE PROVIDER SHALL set pm:AbstractMetricState/@ActivationState = StndBy.")
    @TestIdentifier(EnabledTestConfig.BICEPS_547_8)
    @TestDescription("For each metric with the category SET, the device is manipulated so that the setting of the"
            + " metric is initialized but no setting is applied and then it is verified that the ActivationState of"
            + " the metric is set to StndBy.")
    @RequirePrecondition(
            manipulationPreconditions = {
                ManipulationPreconditions.MetricStatusManipulationSETActivationStateSTNDBY.class
            })
    void testRequirement5478() throws NoTestData {
        final var metricCategory = MetricCategory.SET;
        final var activationState = ComponentActivation.STND_BY;
        testRequirement547(metricCategory, activationState);
    }

    @Test
    @DisplayName("If pm:AbstractMetricDescriptor/@MetricCategory = Set and the setting is not being performed and is"
            + " de-initialized, the SERVICE PROVIDER SHALL set pm:AbstractMetricState/@ActivationState = Off.")
    @TestIdentifier(EnabledTestConfig.BICEPS_547_10)
    @TestDescription("For each metric with the category SET, the device is manipulated so that the setting"
            + " of the metric is de-initialized and no setting is applied and then it is verified that the ActivationState"
            + " of the metric is set to Off.")
    @RequirePrecondition(
            manipulationPreconditions = {ManipulationPreconditions.MetricStatusManipulationSETActivationStateOFF.class})
    void testRequirement54710() throws NoTestData {
        final var metricCategory = MetricCategory.SET;
        final var activationState = ComponentActivation.OFF;
        testRequirement547(metricCategory, activationState);
    }

    @Test
    @DisplayName("If pm:AbstractMetricDescriptor/@MetricCategory = Clc and the calculation for the METRIC is being"
            + " performed, the SERVICE PROVIDER SHALL set pm:AbstractMetricState/@ActivationState = On.")
    @TestIdentifier(EnabledTestConfig.BICEPS_547_12_0)
    @TestDescription("For each metric with the category CLC, the device is manipulated to perform calculations and"
            + " then it is verified that the ActivationState of the metric is set to On.")
    @RequirePrecondition(
            manipulationPreconditions = {ManipulationPreconditions.MetricStatusManipulationCLCActivationStateON.class})
    void testRequirement547120() throws NoTestData {
        final var metricCategory = MetricCategory.CLC;
        final var activationState = ComponentActivation.ON;
        testRequirement547(metricCategory, activationState);
    }

    @Test
    @DisplayName("If pm:AbstractMetricDescriptor/@MetricCategory = Clc and the calculation for the METRIC has been"
            + " initialized, but is not being performed, the SERVICE PROVIDER SHALL set"
            + " pm:AbstractMetricState/@ActivationState = StndBy.")
    @TestIdentifier(EnabledTestConfig.BICEPS_547_14)
    @TestDescription("For each metric with the category CLC, the device is manipulated to perform calculations and"
            + " then it is verified that the ActivationState of the metric is set to StndBy.")
    @RequirePrecondition(
            manipulationPreconditions = {
                ManipulationPreconditions.MetricStatusManipulationCLCActivationStateSTNDBY.class
            })
    void testRequirement54714() throws NoTestData {
        final var metricCategory = MetricCategory.CLC;
        final var activationState = ComponentActivation.STND_BY;
        testRequirement547(metricCategory, activationState);
    }

    @Test
    @DisplayName("If pm:AbstractMetricDescriptor/@MetricCategory = Clc and the calculation for the METRIC is not being"
            + " performed and is de-initialized, the SERVICE PROVIDER SHALL set pm:AbstractMetricState/@ActivationState"
            + " = Off.")
    @TestIdentifier(EnabledTestConfig.BICEPS_547_16)
    @TestDescription("For each metric with the category CLC, the device is manipulated so that the calculation of"
            + " the metric is de-initialized and no calculation is performed and then it is verified that the"
            + " ActivationState of the metric is set to Off.")
    @RequirePrecondition(
            manipulationPreconditions = {ManipulationPreconditions.MetricStatusManipulationCLCActivationStateOFF.class})
    void testRequirement54716() throws NoTestData {
        final var metricCategory = MetricCategory.CLC;
        final var activationState = ComponentActivation.OFF;
        testRequirement547(metricCategory, activationState);
    }

    private void testRequirement547(final MetricCategory category, final ComponentActivation activation)
            throws NoTestData {
        final var successfulReportsSeen = new AtomicBoolean(false);
        try (final var manipulations = messageStorage.getManipulationDataByParametersAndManipulation(
                List.of(
                        new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_METRIC_CATEGORY, category.value()),
                        new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_COMPONENT_ACTIVATION, activation.value())),
                Constants.MANIPULATION_NAME_SET_METRIC_STATUS)) {
            assertTestData(
                    manipulations.areObjectsPresent(), String.format(NO_SET_METRIC_STATUS_MANIPULATION, category));
            manipulations
                    .getStream()
                    .filter(it -> it.getResult().equals(ResponseTypes.Result.RESULT_SUCCESS))
                    .forEachOrdered(it -> {
                        successfulReportsSeen.getAndSet(true);
                        checkAssociatedReport(it, activation);
                    });
        } catch (IOException e) {
            fail(e);
            // unreachable
            throw new RuntimeException(e);
        }
        assertTestData(successfulReportsSeen.get(), NO_SUCCESSFUL_MANIPULATION);
    }

    private void checkAssociatedReport(
            final ManipulationData manipulationData, final ComponentActivation expectedActivationState) {
        final var manipulationParameter = manipulationData.getParameters();
        final var manipulatedHandle = manipulationParameter.stream()
                .filter(it -> it.getParameterName().equals(Constants.MANIPULATION_PARAMETER_HANDLE))
                .map(ManipulationParameter::getParameterValue)
                .findFirst()
                .orElseThrow();
        try (final var relevantReports = messageStorage.getInboundMessagesByTimeIntervalAndBodyType(
                manipulationData.getStartTimestamp(),
                manipulationData.getFinishTimestamp() + BUFFER,
                Constants.MSG_EPISODIC_METRIC_REPORT,
                Constants.MSG_WAVEFORM_STREAM)) {
            assertTestData(
                    relevantReports.areObjectsPresent(),
                    String.format(
                            NO_REPORT_IN_TIME_INTERVAL,
                            manipulationData.getMethodName(),
                            manipulationData.getStartTimestamp(),
                            manipulationData.getFinishTimestamp()));

            final var relevantReport = relevantReports
                    .getStream()
                    .map(it -> {
                        try {
                            final var message = marshalling.unmarshal(
                                    new ByteArrayInputStream(it.getBody().getBytes(StandardCharsets.UTF_8)));
                            final var metricReportOpt = soapUtil.getBody(message, AbstractMetricReport.class);
                            if (metricReportOpt.isEmpty()) {
                                final var waveformOpt = soapUtil.getBody(message, WaveformStream.class);
                                return waveformOpt.orElseThrow();
                            } else {
                                return metricReportOpt.orElseThrow();
                            }
                        } catch (MarshallingException e) {
                            fail("Error unmarshalling MessageContent " + e);
                            // unreachable
                            throw new RuntimeException(e);
                        }
                    })
                    .filter(it -> isReportRelevant(it, manipulatedHandle))
                    .findFirst();

            assertTrue(relevantReport.isPresent(), String.format(NO_REPORT_WITH_EXPECTED_HANDLE, manipulatedHandle));

            final var reportWithExpectedActivationStateSeen = isActivationStateAsExpected(
                    relevantReport.orElseThrow(), manipulatedHandle, expectedActivationState);

            assertTrue(
                    reportWithExpectedActivationStateSeen,
                    String.format(
                            NO_REPORT_WITH_EXPECTED_ACTIVATION_STATE, manipulatedHandle, expectedActivationState));
        } catch (IOException | NoTestData e) {
            fail(e);
            // unreachable
            throw new RuntimeException(e);
        }
    }

    private boolean isReportRelevant(final AbstractReport report, final String manipulatedHandle) {
        boolean present = false;
        if (report instanceof AbstractMetricReport) {
            for (var part : ((AbstractMetricReport) report).getReportPart()) {
                if (part.getMetricState().stream()
                        .anyMatch(state -> state.getDescriptorHandle().equals(manipulatedHandle))) {
                    present = true;
                }
            }
        } else if (report instanceof WaveformStream) {
            present = ((WaveformStream) report)
                    .getState().stream().anyMatch(it -> it.getDescriptorHandle().equals(manipulatedHandle));
        }
        return present;
    }

    private boolean isActivationStateAsExpected(
            final AbstractReport report,
            final String manipulatedHandle,
            final ComponentActivation expectedActivationState) {

        final var relevantMetricPresent = new AtomicBoolean(false);
        if (report instanceof final AbstractMetricReport metricReport) {
            for (var part : metricReport.getReportPart()) {
                final var state = part.getMetricState().stream()
                        .filter(it -> it.getDescriptorHandle().equals(manipulatedHandle))
                        .findFirst();
                state.ifPresent(abstractMetricState -> {
                    relevantMetricPresent.set(true);
                    Assertions.assertEquals(
                            expectedActivationState,
                            ImpliedValueUtil.getMetricActivation(abstractMetricState),
                            String.format(
                                    WRONG_ACTIVATION_STATE,
                                    manipulatedHandle,
                                    expectedActivationState,
                                    ImpliedValueUtil.getMetricActivation(abstractMetricState)));
                });
            }
        } else if (report instanceof final WaveformStream waveform) {
            final var state = waveform.getState().stream()
                    .filter(it -> it.getDescriptorHandle().equals(manipulatedHandle))
                    .findFirst();
            state.ifPresent(abstractMetricState -> {
                relevantMetricPresent.set(true);
                Assertions.assertEquals(
                        expectedActivationState,
                        ImpliedValueUtil.getMetricActivation(abstractMetricState),
                        String.format(
                                WRONG_ACTIVATION_STATE,
                                manipulatedHandle,
                                expectedActivationState,
                                ImpliedValueUtil.getMetricActivation(abstractMetricState)));
            });
        }
        return relevantMetricPresent.get();
    }
}
