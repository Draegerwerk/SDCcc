/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2023 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.tests.biceps.direct;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.draeger.medical.sdccc.configuration.EnabledTestConfig;
import com.draeger.medical.sdccc.manipulation.Manipulations;
import com.draeger.medical.sdccc.sdcri.testclient.TestClient;
import com.draeger.medical.sdccc.tests.InjectorTestBase;
import com.draeger.medical.sdccc.tests.annotations.TestDescription;
import com.draeger.medical.sdccc.tests.annotations.TestIdentifier;
import com.draeger.medical.sdccc.tests.util.NoTestData;
import com.draeger.medical.sdccc.util.MessageGeneratingUtil;
import com.draeger.medical.sdccc.util.MessagingException;
import com.draeger.medical.t2iapi.ResponseTypes;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.somda.sdc.biceps.model.message.GetMdibResponse;
import org.somda.sdc.biceps.model.participant.AbstractMetricDescriptor;
import org.somda.sdc.biceps.model.participant.AbstractMetricState;
import org.somda.sdc.biceps.model.participant.AbstractMetricValue;
import org.somda.sdc.biceps.model.participant.DistributionSampleArrayMetricState;
import org.somda.sdc.biceps.model.participant.EnumStringMetricState;
import org.somda.sdc.biceps.model.participant.MeasurementValidity;
import org.somda.sdc.biceps.model.participant.NumericMetricState;
import org.somda.sdc.biceps.model.participant.NumericMetricValue;
import org.somda.sdc.biceps.model.participant.RealTimeSampleArrayMetricState;
import org.somda.sdc.biceps.model.participant.SampleArrayValue;
import org.somda.sdc.biceps.model.participant.StringMetricState;
import org.somda.sdc.biceps.model.participant.StringMetricValue;

/**
 * BICEPS participant model state part tests (ch. 5.4).
 */
public class DirectParticipantModelStatePartTest extends InjectorTestBase {
    private static final Logger LOG = LogManager.getLogger();
    private static final String MANIPULATION_RESULT = "Manipulation results contain %s";

    private Manipulations manipulations;
    private TestClient testClient;
    private MessageGeneratingUtil messageGeneratingUtil;

    @BeforeEach
    void setUp() {
        this.testClient = getInjector().getInstance(TestClient.class);
        assertTrue(testClient.isClientRunning());
        this.manipulations = getInjector().getInstance(Manipulations.class);
        this.messageGeneratingUtil = getInjector().getInstance(MessageGeneratingUtil.class);
    }

    @Test
    @TestIdentifier(EnabledTestConfig.BICEPS_R0021)
    @TestDescription("Requests the Mdib of the DUT and verifies that an MdState element is present."
            + " This test is stricter than the requirement because although an MDS can be removed, this test expects that"
            + " an MDS is present and therefore MdState is not empty and cannot be omitted.")
    void testRequirementR0021() throws MessagingException {
        final var responseMessage = messageGeneratingUtil.getMdib();
        final var getMdibResponse = (GetMdibResponse)
                responseMessage.getOriginalEnvelope().getBody().getAny().get(0);
        final var mdState = getMdibResponse.getMdib().getMdState();
        assertNotNull(mdState, "No MdState present, test failed.");
    }

    @Test
    @TestIdentifier(EnabledTestConfig.BICEPS_B_61)
    @TestDescription("Attempts to set the metric quality validity for each metric state of the DUT to \"Ong\" or \"Na\""
            + " and verifies, that the validity was set successfully and no determined value is present.")
    void testRequirementB61() throws NoTestData {
        final var metricEntities =
                testClient.getSdcRemoteDevice().getMdibAccess().findEntitiesByType(AbstractMetricDescriptor.class);

        final var manipulationOngSeen = new AtomicInteger();
        final var manipulationNaSeen = new AtomicInteger();
        for (var entity : metricEntities) {
            final var metricState = entity.getStates(AbstractMetricState.class).get(0);

            final var firstManipulationResult =
                    manipulateMetricValidity(metricState.getDescriptorHandle(), MeasurementValidity.ONG);
            final var secondManipulationResult =
                    manipulateMetricValidity(metricState.getDescriptorHandle(), MeasurementValidity.NA);

            if (firstManipulationResult == ResponseTypes.Result.RESULT_SUCCESS) {
                manipulationOngSeen.incrementAndGet();
            }
            if (secondManipulationResult == ResponseTypes.Result.RESULT_SUCCESS) {
                manipulationNaSeen.incrementAndGet();
            }
        }
        assertTestData(
                manipulationNaSeen.get(),
                "The metric quality validity could not be changed to NA during" + " test run, test failed.");
        assertTestData(
                manipulationOngSeen.get(),
                "The metric quality validity could not be changed to ONG" + " during test run, test failed.");
    }

    private ResponseTypes.Result manipulateMetricValidity(final String handle, final MeasurementValidity validity) {
        final var manipulationResult = manipulations.setMetricQualityValidity(handle, validity);
        if (manipulationResult == ResponseTypes.Result.RESULT_SUCCESS) {
            final var state = testClient
                    .getSdcRemoteDevice()
                    .getMdibAccess()
                    .getState(handle, AbstractMetricState.class)
                    .orElseThrow();
            final var metricValue = getMetricValue(state);
            assertNotNull(metricValue, String.format("Metric value of %s is null", handle));
            final var metricValidity = metricValue.getMetricQuality().getValidity();
            assertEquals(
                    validity,
                    metricValidity,
                    String.format("Metric validity could not be set correctly for %s", handle));
            assertTrue(
                    isMetricValueValueAbsent(metricValue),
                    String.format("Metric value should be absent for %s", handle));
        } else if (manipulationResult != ResponseTypes.Result.RESULT_NOT_SUPPORTED) {
            fail(String.format(
                    "Try to set the validity %s for %s, but manipulation failed, response: %s",
                    validity, handle, manipulationResult.name()));
        }
        return manipulationResult;
    }

    private AbstractMetricValue getMetricValue(final AbstractMetricState state) {
        AbstractMetricValue metricValue = null;
        if (state instanceof DistributionSampleArrayMetricState) {
            metricValue = ((DistributionSampleArrayMetricState) state).getMetricValue();
        } else if (state instanceof NumericMetricState) {
            metricValue = ((NumericMetricState) state).getMetricValue();
        } else if (state instanceof RealTimeSampleArrayMetricState) {
            metricValue = ((RealTimeSampleArrayMetricState) state).getMetricValue();
        } else if (state instanceof EnumStringMetricState) {
            metricValue = ((EnumStringMetricState) state).getMetricValue();
        } else if (state instanceof StringMetricState) {
            metricValue = ((StringMetricState) state).getMetricValue();
        }
        return metricValue;
    }

    private boolean isMetricValueValueAbsent(final AbstractMetricValue value) {
        boolean valueIsAbsent = false;
        if (value instanceof SampleArrayValue) {
            valueIsAbsent = ((SampleArrayValue) value).getSamples().isEmpty();
        } else if (value instanceof NumericMetricValue) {
            valueIsAbsent = ((NumericMetricValue) value).getValue() == null;
        } else if (value instanceof StringMetricValue) {
            valueIsAbsent = ((StringMetricValue) value).getValue() == null;
        }
        return valueIsAbsent;
    }
}
