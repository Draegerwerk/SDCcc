/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.tests.biceps.direct;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.draeger.medical.sdccc.manipulation.Manipulations;
import com.draeger.medical.sdccc.sdcri.testclient.TestClient;
import com.draeger.medical.sdccc.sdcri.testclient.TestClientUtil;
import com.draeger.medical.sdccc.tests.InjectorTestBase;
import com.draeger.medical.sdccc.tests.test_util.InjectorUtil;
import com.draeger.medical.sdccc.tests.util.NoTestData;
import com.draeger.medical.t2iapi.ResponseTypes;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.somda.sdc.biceps.common.MdibEntity;
import org.somda.sdc.biceps.model.message.GetMdibResponse;
import org.somda.sdc.biceps.model.participant.AbstractMetricDescriptor;
import org.somda.sdc.biceps.model.participant.AbstractMetricState;
import org.somda.sdc.biceps.model.participant.DistributionSampleArrayMetricState;
import org.somda.sdc.biceps.model.participant.EnumStringMetricState;
import org.somda.sdc.biceps.model.participant.MdState;
import org.somda.sdc.biceps.model.participant.Mdib;
import org.somda.sdc.biceps.model.participant.MeasurementValidity;
import org.somda.sdc.biceps.model.participant.NumericMetricState;
import org.somda.sdc.biceps.model.participant.NumericMetricValue;
import org.somda.sdc.biceps.model.participant.RealTimeSampleArrayMetricState;
import org.somda.sdc.biceps.model.participant.SampleArrayValue;
import org.somda.sdc.biceps.model.participant.StringMetricState;
import org.somda.sdc.biceps.model.participant.StringMetricValue;
import org.somda.sdc.dpws.service.HostedServiceProxy;
import org.somda.sdc.dpws.service.HostingServiceProxy;
import org.somda.sdc.dpws.soap.SoapMessage;
import org.somda.sdc.dpws.soap.exception.MarshallingException;
import org.somda.sdc.dpws.soap.exception.SoapFaultException;
import org.somda.sdc.dpws.soap.exception.TransportException;
import org.somda.sdc.dpws.soap.interception.InterceptorException;
import org.somda.sdc.glue.common.WsdlConstants;

/**
 * Unit test for the BICEPS {@linkplain DirectParticipantModelStatePartTest}.
 */
public class DirectParticipantModelStatePartTestTest {
    private static final String NUMERIC_METRIC_HANDLE = "someNumericMetric";
    private static final String STRING_METRIC_HANDLE = "someStringMetric";
    private static final String ENUM_STRING_METRIC_HANDLE = "someEnumNumericMetric";
    private static final String DISTRIBUTION_SAMPLE_ARRAY_METRIC_HANDLE = "someDistributionSampleArrayMetric";
    private static final String REAL_TIME_SAMPLE_ARRAY_METRIC_HANDLE = "someRealTimeSampleArrayMetric";

    private TestClient testClient;
    private DirectParticipantModelStatePartTest testClass;

    private Mdib mdib;

    private Manipulations manipulations;

    @BeforeEach
    void setUp() throws IOException {
        final TestClient mockClient = mock(TestClient.class, RETURNS_DEEP_STUBS);
        when(mockClient.isClientRunning()).thenReturn(true);
        testClient = mockClient;

        final var clientInjector = TestClientUtil.createClientInjector();
        when(testClient.getInjector()).thenReturn(clientInjector);

        manipulations = mock(Manipulations.class);
        final Injector injector = InjectorUtil.setupInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(TestClient.class).toInstance(testClient);
                bind(Manipulations.class).toInstance(manipulations);
            }
        });

        InjectorTestBase.setInjector(injector);

        testClass = new DirectParticipantModelStatePartTest();
        testClass.setUp();
    }

    /**
     * Tests whether a mdib containing a MdState element is passing the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement0021Good() throws Exception {
        setupTestDevice();
        final var mdState = mock(MdState.class);
        when(mdib.getMdState()).thenReturn(mdState);
        testClass.testRequirementR0021();
    }

    /**
     * Tests whether a mdib not containing a MdState element is failing the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement0021Bad() throws Exception {
        setupTestDevice();
        assertThrows(AssertionError.class, testClass::testRequirementR0021);
    }

    void setupTestDevice() throws InterceptorException, SoapFaultException, MarshallingException, TransportException {
        mdib = mock(Mdib.class);
        final var getMdibResponse = mock(GetMdibResponse.class);
        when(getMdibResponse.getMdib()).thenReturn(mdib);
        final var messageResponse = mock(SoapMessage.class, RETURNS_DEEP_STUBS);
        when(messageResponse.getOriginalEnvelope().getBody().getAny().get(0)).thenReturn(getMdibResponse);

        final var hostedService = mock(HostedServiceProxy.class, Mockito.RETURNS_DEEP_STUBS);
        when(hostedService.getType().getTypes()).thenReturn(List.of(WsdlConstants.PORT_TYPE_GET_QNAME));
        when(hostedService.sendRequestResponse(any())).thenReturn(messageResponse);

        final var map = Map.of("hostedService", hostedService);
        final var mockHostingService = mock(HostingServiceProxy.class);
        when(testClient.getHostingServiceProxy()).thenReturn(mockHostingService);
        when(mockHostingService.getHostedServices()).thenReturn(map);
    }

    /**
     * Tests if insufficient test data fails the test.
     */
    @Test
    public void testRequirementB61NoTestData() {
        assertThrows(NoTestData.class, testClass::testRequirementB61);
    }

    /**
     * Tests whether absent metric values when the measurement validity is "Ong" or "Na", passes the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementB61Good() throws Exception {
        when(manipulations.setMetricQualityValidity(any(), any())).thenReturn(ResponseTypes.Result.RESULT_SUCCESS);
        final var metric1State = mockNumericMetricWithValue();
        final var entity1 = mock(MdibEntity.class);
        when(entity1.getStates(AbstractMetricState.class)).thenReturn(List.of(metric1State));

        final var metric2State = mockStringMetricWithValue();
        final var entity2 = mock(MdibEntity.class);
        when(entity2.getStates(AbstractMetricState.class)).thenReturn(List.of(metric2State));

        final var metric3State = mockEnumStringMetricWithValue();
        final var entity3 = mock(MdibEntity.class);
        when(entity3.getStates(AbstractMetricState.class)).thenReturn(List.of(metric3State));

        final var metric4State = mockDistributionSampleArrayMetricWithValue();
        final var entity4 = mock(MdibEntity.class);
        when(entity4.getStates(AbstractMetricState.class)).thenReturn(List.of(metric4State));

        final var metric5State = mockRealTimeSampleArrayMetricWithValue();
        final var entity5 = mock(MdibEntity.class);
        when(entity5.getStates(AbstractMetricState.class)).thenReturn(List.of(metric5State));

        when(testClient.getSdcRemoteDevice().getMdibAccess().findEntitiesByType(AbstractMetricDescriptor.class))
                .thenReturn(List.of(entity1, entity2, entity3, entity4, entity5));

        when(testClient.getSdcRemoteDevice().getMdibAccess().getState(NUMERIC_METRIC_HANDLE, AbstractMetricState.class))
                .thenReturn(Optional.of(metric1State));
        when(testClient.getSdcRemoteDevice().getMdibAccess().getState(NUMERIC_METRIC_HANDLE, NumericMetricState.class))
                .thenReturn(Optional.of(metric1State));

        when(testClient.getSdcRemoteDevice().getMdibAccess().getState(STRING_METRIC_HANDLE, AbstractMetricState.class))
                .thenReturn(Optional.of(metric2State));
        when(testClient.getSdcRemoteDevice().getMdibAccess().getState(STRING_METRIC_HANDLE, StringMetricState.class))
                .thenReturn(Optional.of(metric2State));

        when(testClient
                        .getSdcRemoteDevice()
                        .getMdibAccess()
                        .getState(ENUM_STRING_METRIC_HANDLE, AbstractMetricState.class))
                .thenReturn(Optional.of(metric3State));
        when(testClient
                        .getSdcRemoteDevice()
                        .getMdibAccess()
                        .getState(ENUM_STRING_METRIC_HANDLE, EnumStringMetricState.class))
                .thenReturn(Optional.of(metric3State));

        when(testClient
                        .getSdcRemoteDevice()
                        .getMdibAccess()
                        .getState(DISTRIBUTION_SAMPLE_ARRAY_METRIC_HANDLE, AbstractMetricState.class))
                .thenReturn(Optional.of(metric4State));
        when(testClient
                        .getSdcRemoteDevice()
                        .getMdibAccess()
                        .getState(DISTRIBUTION_SAMPLE_ARRAY_METRIC_HANDLE, DistributionSampleArrayMetricState.class))
                .thenReturn(Optional.of(metric4State));

        when(testClient
                        .getSdcRemoteDevice()
                        .getMdibAccess()
                        .getState(REAL_TIME_SAMPLE_ARRAY_METRIC_HANDLE, AbstractMetricState.class))
                .thenReturn(Optional.of(metric5State));
        when(testClient
                        .getSdcRemoteDevice()
                        .getMdibAccess()
                        .getState(REAL_TIME_SAMPLE_ARRAY_METRIC_HANDLE, RealTimeSampleArrayMetricState.class))
                .thenReturn(Optional.of(metric5State));

        testClass.testRequirementB61();
    }

    private NumericMetricState mockNumericMetricWithValue() {
        final var metricQuality = mock(NumericMetricValue.MetricQuality.class);
        when(metricQuality.getValidity()).thenReturn(MeasurementValidity.ONG).thenReturn(MeasurementValidity.NA);
        final var metricValue = mock(NumericMetricValue.class);
        when(metricValue.getValue()).thenReturn(null);
        when(metricValue.getMetricQuality()).thenReturn(metricQuality);
        final var metricState = mock(NumericMetricState.class);
        when(metricState.getDescriptorHandle()).thenReturn(NUMERIC_METRIC_HANDLE);
        when(metricState.getMetricValue()).thenReturn(metricValue);
        return metricState;
    }

    private StringMetricState mockStringMetricWithValue() {
        final var metricQuality = mock(StringMetricValue.MetricQuality.class);
        when(metricQuality.getValidity()).thenReturn(MeasurementValidity.ONG).thenReturn(MeasurementValidity.NA);
        final var metricValue = mock(StringMetricValue.class);
        when(metricValue.getValue()).thenReturn(null);
        when(metricValue.getMetricQuality()).thenReturn(metricQuality);
        final var metricState = mock(StringMetricState.class);
        when(metricState.getDescriptorHandle()).thenReturn(STRING_METRIC_HANDLE);
        when(metricState.getMetricValue()).thenReturn(metricValue);
        return metricState;
    }

    private EnumStringMetricState mockEnumStringMetricWithValue() {
        final var metricQuality = mock(StringMetricValue.MetricQuality.class);
        when(metricQuality.getValidity()).thenReturn(MeasurementValidity.ONG).thenReturn(MeasurementValidity.NA);
        final var metricValue = mock(StringMetricValue.class);
        when(metricValue.getValue()).thenReturn(null);
        when(metricValue.getMetricQuality()).thenReturn(metricQuality);
        final var metricState = mock(EnumStringMetricState.class);
        when(metricState.getDescriptorHandle()).thenReturn(ENUM_STRING_METRIC_HANDLE);
        when(metricState.getMetricValue()).thenReturn(metricValue);
        return metricState;
    }

    private DistributionSampleArrayMetricState mockDistributionSampleArrayMetricWithValue() {
        final var metricQuality = mock(SampleArrayValue.MetricQuality.class);
        when(metricQuality.getValidity()).thenReturn(MeasurementValidity.ONG).thenReturn(MeasurementValidity.NA);
        final var metricValue = mock(SampleArrayValue.class);
        when(metricValue.getSamples()).thenReturn(Collections.emptyList());
        when(metricValue.getMetricQuality()).thenReturn(metricQuality);
        final var metricState = mock(DistributionSampleArrayMetricState.class);
        when(metricState.getDescriptorHandle()).thenReturn(DISTRIBUTION_SAMPLE_ARRAY_METRIC_HANDLE);
        when(metricState.getMetricValue()).thenReturn(metricValue);
        return metricState;
    }

    private RealTimeSampleArrayMetricState mockRealTimeSampleArrayMetricWithValue() {
        final var metricQuality = mock(SampleArrayValue.MetricQuality.class);
        when(metricQuality.getValidity()).thenReturn(MeasurementValidity.ONG).thenReturn(MeasurementValidity.NA);
        final var metricValue = mock(SampleArrayValue.class);
        when(metricValue.getSamples()).thenReturn(Collections.emptyList());
        when(metricValue.getMetricQuality()).thenReturn(metricQuality);
        final var metricState = mock(RealTimeSampleArrayMetricState.class);
        when(metricState.getDescriptorHandle()).thenReturn(REAL_TIME_SAMPLE_ARRAY_METRIC_HANDLE);
        when(metricState.getMetricValue()).thenReturn(metricValue);
        return metricState;
    }

    /**
     * Tests whether absent metric values when the measurement validity is "Ong" or "Na" and atleast one manipulation
     * was successful and the others are not supported, passes the test.
     */
    @Test
    public void testRequirementB61GoodSomeManipulationNotSupported() throws Exception {
        when(manipulations.setMetricQualityValidity(anyString(), eq(MeasurementValidity.NA)))
                .thenReturn(ResponseTypes.Result.RESULT_SUCCESS)
                .thenReturn(ResponseTypes.Result.RESULT_NOT_SUPPORTED);
        when(manipulations.setMetricQualityValidity(anyString(), eq(MeasurementValidity.ONG)))
                .thenReturn(ResponseTypes.Result.RESULT_NOT_SUPPORTED)
                .thenReturn(ResponseTypes.Result.RESULT_SUCCESS);
        final var metric1State = mock(NumericMetricState.class);
        when(metric1State.getDescriptorHandle()).thenReturn(NUMERIC_METRIC_HANDLE);
        final var metric1Quality = mock(NumericMetricValue.MetricQuality.class);
        when(metric1Quality.getValidity()).thenReturn(MeasurementValidity.NA);
        final var metric1Value = mock(NumericMetricValue.class);
        when(metric1Value.getValue()).thenReturn(null);
        when(metric1Value.getMetricQuality()).thenReturn(metric1Quality);
        when(metric1State.getMetricValue()).thenReturn(metric1Value);
        final var metric1 = mock(AbstractMetricDescriptor.class);
        when(metric1.getHandle()).thenReturn(NUMERIC_METRIC_HANDLE);
        final var entity1 = mock(MdibEntity.class);
        when(entity1.getDescriptor(AbstractMetricDescriptor.class)).thenReturn(Optional.of(metric1));
        when(entity1.getStates(AbstractMetricState.class)).thenReturn(List.of(metric1State));

        final var metric2State = mock(StringMetricState.class);
        when(metric2State.getDescriptorHandle()).thenReturn(STRING_METRIC_HANDLE);
        final var metric2Quality = mock(StringMetricValue.MetricQuality.class);
        when(metric2Quality.getValidity()).thenReturn(MeasurementValidity.ONG);
        final var metric2Value = mock(StringMetricValue.class);
        when(metric2Value.getValue()).thenReturn(null);
        when(metric2Value.getMetricQuality()).thenReturn(metric2Quality);
        when(metric2State.getMetricValue()).thenReturn(metric2Value);
        final var metric2 = mock(AbstractMetricDescriptor.class);
        when(metric2.getHandle()).thenReturn(STRING_METRIC_HANDLE);
        final var entity2 = mock(MdibEntity.class);
        when(entity1.getDescriptor(AbstractMetricDescriptor.class)).thenReturn(Optional.of(metric2));
        when(entity2.getStates(AbstractMetricState.class)).thenReturn(List.of(metric2State));

        when(testClient.getSdcRemoteDevice().getMdibAccess().findEntitiesByType(AbstractMetricDescriptor.class))
                .thenReturn(List.of(entity1, entity2));

        when(testClient.getSdcRemoteDevice().getMdibAccess().getState(NUMERIC_METRIC_HANDLE, AbstractMetricState.class))
                .thenReturn(Optional.of(metric1State));
        when(testClient.getSdcRemoteDevice().getMdibAccess().getState(NUMERIC_METRIC_HANDLE, NumericMetricState.class))
                .thenReturn(Optional.of(metric1State));

        when(testClient.getSdcRemoteDevice().getMdibAccess().getState(STRING_METRIC_HANDLE, AbstractMetricState.class))
                .thenReturn(Optional.of(metric2State));
        when(testClient.getSdcRemoteDevice().getMdibAccess().getState(STRING_METRIC_HANDLE, StringMetricState.class))
                .thenReturn(Optional.of(metric2State));

        testClass.testRequirementB61();
    }

    /**
     * Tests whether present metric values when the measurement validity is "Ong" or "Na", fails the test.
     */
    @Test
    public void testRequirementB61Bad() {
        when(manipulations.setMetricQualityValidity(any(), any())).thenReturn(ResponseTypes.Result.RESULT_SUCCESS);
        final var metric1State = mock(NumericMetricState.class);
        when(metric1State.getDescriptorHandle()).thenReturn(NUMERIC_METRIC_HANDLE);
        final var metric1Quality = mock(NumericMetricValue.MetricQuality.class);
        when(metric1Quality.getValidity()).thenReturn(MeasurementValidity.ONG);
        final var metric1Value = mock(NumericMetricValue.class);
        when(metric1Value.getValue()).thenReturn(BigDecimal.ONE);
        when(metric1Value.getMetricQuality()).thenReturn(metric1Quality);
        when(metric1State.getMetricValue()).thenReturn(metric1Value);
        final var metric1 = mock(AbstractMetricDescriptor.class);
        when(metric1.getHandle()).thenReturn(NUMERIC_METRIC_HANDLE);
        final var entity1 = mock(MdibEntity.class);
        when(entity1.getDescriptor(AbstractMetricDescriptor.class)).thenReturn(Optional.of(metric1));
        when(entity1.getStates(AbstractMetricState.class)).thenReturn(List.of(metric1State));

        when(testClient.getSdcRemoteDevice().getMdibAccess().findEntitiesByType(AbstractMetricDescriptor.class))
                .thenReturn(List.of(entity1));

        when(testClient.getSdcRemoteDevice().getMdibAccess().getState(NUMERIC_METRIC_HANDLE, AbstractMetricState.class))
                .thenReturn(Optional.of(metric1State));
        when(testClient.getSdcRemoteDevice().getMdibAccess().getState(NUMERIC_METRIC_HANDLE, NumericMetricState.class))
                .thenReturn(Optional.of(metric1State));

        assertThrows(AssertionError.class, testClass::testRequirementB61);
    }

    /**
     * Tests whether no successful manipulations fails the test.
     */
    @Test
    public void testRequirementB61BadManipulationUnsuccessful() {
        when(manipulations.setMetricQualityValidity(any(), any())).thenReturn(ResponseTypes.Result.RESULT_FAIL);
        final var metric1State = mock(NumericMetricState.class);
        when(metric1State.getDescriptorHandle()).thenReturn(NUMERIC_METRIC_HANDLE);
        final var metric1Quality = mock(NumericMetricValue.MetricQuality.class);
        when(metric1Quality.getValidity()).thenReturn(MeasurementValidity.ONG);
        final var metric1Value = mock(NumericMetricValue.class);
        when(metric1Value.getValue()).thenReturn(BigDecimal.ONE);
        when(metric1Value.getMetricQuality()).thenReturn(metric1Quality);
        when(metric1State.getMetricValue()).thenReturn(metric1Value);
        final var metric1 = mock(AbstractMetricDescriptor.class);
        when(metric1.getHandle()).thenReturn(NUMERIC_METRIC_HANDLE);
        final var entity1 = mock(MdibEntity.class);
        when(entity1.getDescriptor(AbstractMetricDescriptor.class)).thenReturn(Optional.of(metric1));
        when(entity1.getStates(AbstractMetricState.class)).thenReturn(List.of(metric1State));

        when(testClient.getSdcRemoteDevice().getMdibAccess().findEntitiesByType(AbstractMetricDescriptor.class))
                .thenReturn(List.of(entity1));

        when(testClient.getSdcRemoteDevice().getMdibAccess().getState(NUMERIC_METRIC_HANDLE, AbstractMetricState.class))
                .thenReturn(Optional.of(metric1State));
        when(testClient.getSdcRemoteDevice().getMdibAccess().getState(NUMERIC_METRIC_HANDLE, NumericMetricState.class))
                .thenReturn(Optional.of(metric1State));

        assertThrows(AssertionError.class, testClass::testRequirementB61);
    }

    /**
     * Tests whether no supported manipulations fails the test.
     */
    @Test
    public void testRequirementB61BadNoManipulationSupported() {
        when(manipulations.setMetricQualityValidity(anyString(), eq(MeasurementValidity.NA)))
                .thenReturn(ResponseTypes.Result.RESULT_NOT_SUPPORTED);
        when(manipulations.setMetricQualityValidity(anyString(), eq(MeasurementValidity.ONG)))
                .thenReturn(ResponseTypes.Result.RESULT_NOT_SUPPORTED);
        final var metric1State = mock(NumericMetricState.class);
        when(metric1State.getDescriptorHandle()).thenReturn(NUMERIC_METRIC_HANDLE);
        final var metric1Quality = mock(NumericMetricValue.MetricQuality.class);
        when(metric1Quality.getValidity()).thenReturn(MeasurementValidity.NA);
        final var metric1Value = mock(NumericMetricValue.class);
        when(metric1Value.getValue()).thenReturn(null);
        when(metric1Value.getMetricQuality()).thenReturn(metric1Quality);
        when(metric1State.getMetricValue()).thenReturn(metric1Value);
        final var metric1 = mock(AbstractMetricDescriptor.class);
        when(metric1.getHandle()).thenReturn(NUMERIC_METRIC_HANDLE);
        final var entity1 = mock(MdibEntity.class);
        when(entity1.getDescriptor(AbstractMetricDescriptor.class)).thenReturn(Optional.of(metric1));
        when(entity1.getStates(AbstractMetricState.class)).thenReturn(List.of(metric1State));

        when(testClient.getSdcRemoteDevice().getMdibAccess().findEntitiesByType(AbstractMetricDescriptor.class))
                .thenReturn(List.of(entity1));

        when(testClient.getSdcRemoteDevice().getMdibAccess().getState(NUMERIC_METRIC_HANDLE, AbstractMetricState.class))
                .thenReturn(Optional.of(metric1State));
        when(testClient.getSdcRemoteDevice().getMdibAccess().getState(NUMERIC_METRIC_HANDLE, NumericMetricState.class))
                .thenReturn(Optional.of(metric1State));

        assertThrows(NoTestData.class, testClass::testRequirementB61);
    }
}
