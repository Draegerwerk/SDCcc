/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2023 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.manipulation.precondition.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.draeger.medical.sdccc.manipulation.Manipulations;
import com.draeger.medical.sdccc.sdcri.testclient.TestClient;
import com.draeger.medical.sdccc.tests.test_util.InjectorUtil;
import com.draeger.medical.sdccc.util.TestRunObserver;
import com.draeger.medical.t2iapi.ResponseTypes;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.somda.sdc.biceps.common.MdibEntity;
import org.somda.sdc.biceps.common.access.MdibAccess;
import org.somda.sdc.biceps.model.participant.AbstractAlertState;
import org.somda.sdc.biceps.model.participant.AbstractMetricDescriptor;
import org.somda.sdc.biceps.model.participant.AbstractMetricState;
import org.somda.sdc.biceps.model.participant.AlertActivation;
import org.somda.sdc.biceps.model.participant.AlertConditionDescriptor;
import org.somda.sdc.biceps.model.participant.AlertConditionState;
import org.somda.sdc.biceps.model.participant.AlertSystemDescriptor;
import org.somda.sdc.biceps.model.participant.AlertSystemState;
import org.somda.sdc.biceps.model.participant.ComponentActivation;
import org.somda.sdc.biceps.model.participant.ContextAssociation;
import org.somda.sdc.biceps.model.participant.LocationContextDescriptor;
import org.somda.sdc.biceps.model.participant.LocationContextState;
import org.somda.sdc.biceps.model.participant.MetricCategory;
import org.somda.sdc.biceps.model.participant.PatientContextDescriptor;
import org.somda.sdc.biceps.model.participant.PatientContextState;
import org.somda.sdc.glue.consumer.SdcRemoteDevice;

/**
 * Unit tests for manipulation preconditions in {@linkplain ManipulationPreconditions}.
 */
public class ManipulationPreconditionsTest {

    private static final String PATIENT_CONTEXT_DESCRIPTOR_HANDLE = "patpatpatpat";
    private static final String PATIENT_CONTEXT_STATE_HANDLE = "patpatstate";
    private static final String PATIENT_CONTEXT_STATE_HANDLE2 = "patpatstate2";
    private static final String LOCATION_CONTEXT_DESCRIPTOR_HANDLE = "locloclocloc";
    private static final String LOCATION_CONTEXT_STATE_HANDLE = "loclocstate";
    private static final String LOCATION_CONTEXT_STATE_HANDLE2 = "loclocstate2";
    private static final String ALERT_SYSTEM_CONTEXT_HANDLE = "alerthandle";
    private static final String ALERT_SYSTEM_CONTEXT_HANDLE2 = "alerthandle2";
    private static final String ALERT_CONDITION_HANDLE = "alertconditionhandle";
    private static final String ALERT_CONDITION_HANDLE2 = "alertconditionhandle2";
    private static final String METRIC_HANDLE = "someMetric";
    private static final String SOME_HANDLE = "someHandle";

    private Injector injector;
    private SdcRemoteDevice mockDevice;
    private Manipulations mockManipulations;
    private PatientContextState mockPatientContextState;
    private PatientContextState mockPatientContextState2;
    private LocationContextState mockLocationContextState;
    private LocationContextState mockLocationContextState2;
    private AlertSystemState mockAlertSystemState;
    private AlertSystemState mockAlertSystemState2;
    private AlertConditionState mockAlertConditionState;
    private AlertConditionState mockAlertConditionState2;
    private AbstractMetricDescriptor mockMetricDescriptor;
    private AbstractMetricDescriptor mockMetricDescriptor2;
    private AbstractMetricState mockMetricState;
    private AbstractMetricState mockMetricState2;
    private TestRunObserver testRunObserver;
    private MdibEntity mockEntity;
    private MdibEntity mockEntity2;
    private MdibAccess mockMdibAccess;

    @BeforeEach
    void setUp() throws IOException {
        mockDevice = mock(SdcRemoteDevice.class, Mockito.RETURNS_DEEP_STUBS);
        mockManipulations = mock(Manipulations.class);
        mockPatientContextState = mock(PatientContextState.class);
        mockPatientContextState2 = mock(PatientContextState.class);
        mockLocationContextState = mock(LocationContextState.class);
        mockLocationContextState2 = mock(LocationContextState.class);
        mockAlertSystemState = mock(AlertSystemState.class);
        mockAlertSystemState2 = mock(AlertSystemState.class);
        mockAlertConditionState = mock(AlertConditionState.class);
        mockAlertConditionState2 = mock(AlertConditionState.class);
        mockMetricDescriptor = mock(AbstractMetricDescriptor.class);
        mockMetricDescriptor2 = mock(AbstractMetricDescriptor.class);
        mockMetricState = mock(AbstractMetricState.class);
        mockMetricState2 = mock(AbstractMetricState.class);
        mockEntity = mock(MdibEntity.class);
        mockEntity2 = mock(MdibEntity.class);
        mockMdibAccess = mock(MdibAccess.class);

        final var mockTestClient = mock(TestClient.class);
        when(mockTestClient.getSdcRemoteDevice()).thenReturn(mockDevice);

        injector = InjectorUtil.setupInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(TestClient.class).toInstance(mockTestClient);
                bind(SdcRemoteDevice.class).toInstance(mockDevice);
                bind(Manipulations.class).toInstance(mockManipulations);
            }
        });

        testRunObserver = injector.getInstance(TestRunObserver.class);
    }

    void associateNewPatientsSetup() {
        // create mock patient context state
        when(mockPatientContextState.getDescriptorHandle()).thenReturn(PATIENT_CONTEXT_DESCRIPTOR_HANDLE);
        when(mockPatientContextState.getContextAssociation()).thenReturn(ContextAssociation.ASSOC);
        when(mockPatientContextState.getHandle()).thenReturn(PATIENT_CONTEXT_STATE_HANDLE);

        // create another mock patient context state
        when(mockPatientContextState2.getDescriptorHandle()).thenReturn(PATIENT_CONTEXT_DESCRIPTOR_HANDLE);
        when(mockPatientContextState2.getContextAssociation()).thenReturn(ContextAssociation.ASSOC);
        when(mockPatientContextState2.getHandle()).thenReturn(PATIENT_CONTEXT_STATE_HANDLE2);

        // make manipulation return our two patient context state handles and nothing afterwards
        when(mockManipulations.createContextStateWithAssociation(
                        PATIENT_CONTEXT_DESCRIPTOR_HANDLE, ContextAssociation.ASSOC))
                .thenReturn(Optional.of(PATIENT_CONTEXT_STATE_HANDLE))
                .thenReturn(Optional.of(PATIENT_CONTEXT_STATE_HANDLE2))
                .thenReturn(Optional.empty());

        // return mock states on request
        when(mockDevice.getMdibAccess().getState(PATIENT_CONTEXT_STATE_HANDLE, PatientContextState.class))
                .thenReturn(Optional.of(mockPatientContextState));
        when(mockDevice.getMdibAccess().getState(PATIENT_CONTEXT_STATE_HANDLE2, PatientContextState.class))
                .thenReturn(Optional.of(mockPatientContextState2));

        // create mock entity to hold the descriptor
        when(mockEntity.getHandle()).thenReturn(PATIENT_CONTEXT_DESCRIPTOR_HANDLE);
        when(mockDevice.getMdibAccess().findEntitiesByType(PatientContextDescriptor.class))
                .thenReturn(List.of(mockEntity));
    }

    void associateNewLocationsSetup() {
        // create mock location context state
        when(mockLocationContextState.getDescriptorHandle()).thenReturn(LOCATION_CONTEXT_DESCRIPTOR_HANDLE);
        when(mockLocationContextState.getContextAssociation()).thenReturn(ContextAssociation.ASSOC);
        when(mockLocationContextState.getHandle()).thenReturn(LOCATION_CONTEXT_STATE_HANDLE);

        // create another mock location context state
        when(mockLocationContextState2.getDescriptorHandle()).thenReturn(LOCATION_CONTEXT_DESCRIPTOR_HANDLE);
        when(mockLocationContextState2.getContextAssociation()).thenReturn(ContextAssociation.ASSOC);
        when(mockLocationContextState2.getHandle()).thenReturn(LOCATION_CONTEXT_STATE_HANDLE2);

        // make manipulation return our two location context state handles and nothing afterwards
        when(mockManipulations.createContextStateWithAssociation(
                        LOCATION_CONTEXT_DESCRIPTOR_HANDLE, ContextAssociation.ASSOC))
                .thenReturn(Optional.of(LOCATION_CONTEXT_STATE_HANDLE))
                .thenReturn(Optional.of(LOCATION_CONTEXT_STATE_HANDLE2))
                .thenReturn(Optional.empty());

        // return mock states on request
        when(mockDevice.getMdibAccess().getState(LOCATION_CONTEXT_STATE_HANDLE, LocationContextState.class))
                .thenReturn(Optional.of(mockLocationContextState));
        when(mockDevice.getMdibAccess().getState(LOCATION_CONTEXT_STATE_HANDLE2, LocationContextState.class))
                .thenReturn(Optional.of(mockLocationContextState2));

        // create mock entity to hold the descriptor
        when(mockEntity.getHandle()).thenReturn(LOCATION_CONTEXT_DESCRIPTOR_HANDLE);
        when(mockDevice.getMdibAccess().findEntitiesByType(LocationContextDescriptor.class))
                .thenReturn(List.of(mockEntity));
    }

    @Test
    @DisplayName("associateNewPatients: Associate new patient correctly")
    void testAssociateNewPatientForHandle() {
        associateNewPatientsSetup();
        final var expectedManipulationCalls = 2;

        assertTrue(
                ManipulationPreconditions.AssociatePatientsManipulation.manipulation(injector),
                "Manipulation should've succeeded");
        assertFalse(
                testRunObserver.isInvalid(),
                "Test run should not have been invalid. Reason(s): " + testRunObserver.getReasons());

        final var handleCaptor = ArgumentCaptor.forClass(String.class);
        final var assocCaptor = ArgumentCaptor.forClass(ContextAssociation.class);
        verify(mockManipulations, times(expectedManipulationCalls))
                .createContextStateWithAssociation(handleCaptor.capture(), assocCaptor.capture());

        assertEquals(PATIENT_CONTEXT_DESCRIPTOR_HANDLE, handleCaptor.getValue());
        assertEquals(ContextAssociation.ASSOC, assocCaptor.getValue());
    }

    @Test
    @DisplayName("associateNewPatients: New patient not associated")
    void testAssociateNewPatientForHandleWrongAssociation() {
        associateNewPatientsSetup();

        // introduce error, context won't be associated
        when(mockPatientContextState.getContextAssociation()).thenReturn(ContextAssociation.DIS);

        assertFalse(
                ManipulationPreconditions.AssociatePatientsManipulation.manipulation(injector),
                "manipulation should've failed.");
        assertTrue(testRunObserver.isInvalid(), "Test run should have been invalid.");
    }

    @Test
    @DisplayName("associateNewPatients: New patient wrong descriptor")
    void testAssociateNewPatientForHandleWrongDescriptor() {
        associateNewPatientsSetup();

        final var wrongDescriptorHandle = "mostindeededly";

        // introduce error, state points to wrong descriptor
        when(mockPatientContextState.getDescriptorHandle()).thenReturn(wrongDescriptorHandle);

        assertFalse(
                ManipulationPreconditions.AssociatePatientsManipulation.manipulation(injector),
                "manipulation should've failed.");
        assertTrue(testRunObserver.isInvalid(), "Test run should have been invalid.");
    }

    @Test
    @DisplayName("associateNewPatients: Already existent state")
    void testAssociateNewPatientForHandleUsedState() {
        associateNewPatientsSetup();

        // introduce error, first state handle already in entity
        when(mockEntity.getStates(PatientContextState.class)).thenReturn(List.of(mockPatientContextState));
        assertFalse(
                ManipulationPreconditions.AssociatePatientsManipulation.manipulation(injector),
                "manipulation should've failed.");
        assertTrue(testRunObserver.isInvalid(), "Test run should have been invalid.");
    }

    @Test
    @DisplayName("associateNewPatients: Manipulation returns same handle twice")
    void testAssociateNewPatientForHandleSameStateTwice() {
        associateNewPatientsSetup();

        // introduce error, manipulation returns same handle twice
        when(mockManipulations.createContextStateWithAssociation(
                        PATIENT_CONTEXT_DESCRIPTOR_HANDLE, ContextAssociation.ASSOC))
                .thenReturn(Optional.of(PATIENT_CONTEXT_STATE_HANDLE))
                .thenReturn(Optional.of(PATIENT_CONTEXT_STATE_HANDLE))
                .thenReturn(Optional.empty());

        assertFalse(
                ManipulationPreconditions.AssociatePatientsManipulation.manipulation(injector),
                "manipulation should've failed.");
        assertTrue(testRunObserver.isInvalid(), "Test run should have been invalid.");
    }

    @Test
    @DisplayName("associateNewLocations: Associate new location correctly")
    void testAssociateNewLocationForHandle() {
        associateNewLocationsSetup();
        final var expectedManipulationCalls = 2;

        assertTrue(
                ManipulationPreconditions.AssociateLocationsManipulation.manipulation(injector),
                "Manipulation should've succeeded");
        assertFalse(
                testRunObserver.isInvalid(),
                "Test run should not have been invalid. Reason(s): " + testRunObserver.getReasons());

        final var handleCaptor = ArgumentCaptor.forClass(String.class);
        final var assocCaptor = ArgumentCaptor.forClass(ContextAssociation.class);
        verify(mockManipulations, times(expectedManipulationCalls))
                .createContextStateWithAssociation(handleCaptor.capture(), assocCaptor.capture());

        assertEquals(LOCATION_CONTEXT_DESCRIPTOR_HANDLE, handleCaptor.getValue());
        assertEquals(ContextAssociation.ASSOC, assocCaptor.getValue());
    }

    @Test
    @DisplayName("associateNewLocations: New location not associated")
    void testAssociateNewLocationForHandleWrongAssociation() {
        associateNewLocationsSetup();

        // introduce error, context won't be associated
        when(mockLocationContextState.getContextAssociation()).thenReturn(ContextAssociation.DIS);

        assertFalse(
                ManipulationPreconditions.AssociateLocationsManipulation.manipulation(injector),
                "manipulation should've failed.");
        assertTrue(testRunObserver.isInvalid(), "Test run should have been invalid.");
    }

    @Test
    @DisplayName("associateNewLocations: New location wrong descriptor")
    void testAssociateNewLocationForHandleWrongDescriptor() {
        associateNewLocationsSetup();

        final var wrongDescriptorHandle = "mostindeededly";

        // introduce error, state points to wrong descriptor
        when(mockLocationContextState.getDescriptorHandle()).thenReturn(wrongDescriptorHandle);

        assertFalse(
                ManipulationPreconditions.AssociateLocationsManipulation.manipulation(injector),
                "manipulation should've failed.");
        assertTrue(testRunObserver.isInvalid(), "Test run should have been invalid.");
    }

    @Test
    @DisplayName("associateNewLocations: Already existent state")
    void testAssociateNewLocationForHandleUsedState() {
        associateNewLocationsSetup();

        // introduce error, first state handle already in entity
        when(mockEntity.getStates(LocationContextState.class)).thenReturn(List.of(mockLocationContextState));
        assertFalse(
                ManipulationPreconditions.AssociateLocationsManipulation.manipulation(injector),
                "manipulation should've failed.");
        assertTrue(testRunObserver.isInvalid(), "Test run should have been invalid.");
    }

    @Test
    @DisplayName("associateNewLocations: Manipulation returns same handle twice")
    void testAssociateNewLocationForHandleSameStateTwice() {
        associateNewLocationsSetup();

        // introduce error, manipulation returns same handle twice
        when(mockManipulations.createContextStateWithAssociation(
                        LOCATION_CONTEXT_DESCRIPTOR_HANDLE, ContextAssociation.ASSOC))
                .thenReturn(Optional.of(LOCATION_CONTEXT_STATE_HANDLE))
                .thenReturn(Optional.of(LOCATION_CONTEXT_STATE_HANDLE))
                .thenReturn(Optional.empty());

        assertFalse(
                ManipulationPreconditions.AssociateLocationsManipulation.manipulation(injector),
                "manipulation should've failed.");
        assertTrue(testRunObserver.isInvalid(), "Test run should have been invalid.");
    }

    @Test
    @DisplayName("testMetricStatusManipulationMSRMTActivationStateSHTDNGood: Set ActivationState "
            + "of all MSRMT-Metrics to SHTDN.")
    void testMetricStatusManipulationMSRMTActivationStateSHTDNGood() {

        // given

        final ComponentActivation startActivationState = ComponentActivation.ON;
        final MetricCategory metricCategory = MetricCategory.MSRMT;
        final ComponentActivation activationState = ComponentActivation.SHTDN;
        final String metricStateHandle = "metricStateHandle";

        setupMetricStatusManipulation(metricCategory, metricStateHandle);

        when(mockManipulations.setComponentActivation(metricStateHandle, startActivationState))
                .thenReturn(ResponseTypes.Result.RESULT_SUCCESS);
        when(mockManipulations.setMetricStatus(metricStateHandle, metricCategory, activationState))
                .thenReturn(ResponseTypes.Result.RESULT_SUCCESS);

        // when

        final boolean result =
                ManipulationPreconditions.MetricStatusManipulationMSRMTActivationStateSHTDN.manipulation(injector);

        // then

        assertTrue(result);
        assertFalse(
                testRunObserver.isInvalid(),
                "Test run should not have been invalidated. Reason(s): " + testRunObserver.getReasons());
        verify(mockManipulations).setComponentActivation(metricStateHandle, startActivationState);
        verify(mockManipulations).setMetricStatus(metricStateHandle, metricCategory, activationState);
    }

    @Test
    @DisplayName(
            "MetricStatusManipulationMSRMTActivationStateSHTDN: The precondition does not fail if setComponentActivation is not supported by all metrics.")
    void testMetricStatusManipulationMSRMTActivationStateSHTDNAllowNotSupported1() {
        metricMockSetup(
                MetricCategory.MSRMT, METRIC_HANDLE, SOME_HANDLE, ComponentActivation.ON, ComponentActivation.SHTDN);

        // let one metric not support setComponentActivation manipulation
        when(mockManipulations.setComponentActivation(eq(SOME_HANDLE), any(ComponentActivation.class)))
                .thenReturn(ResponseTypes.Result.RESULT_NOT_SUPPORTED);

        when(mockDevice.getMdibAccess().findEntitiesByType(AbstractMetricDescriptor.class))
                .thenReturn(List.of(mockEntity2, mockEntity));

        assertTrue(ManipulationPreconditions.MetricStatusManipulationMSRMTActivationStateSHTDN.manipulation(injector));

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.ON);
        verify(mockManipulations).setComponentActivation(SOME_HANDLE, ComponentActivation.ON);
        verify(mockManipulations).setMetricStatus(METRIC_HANDLE, MetricCategory.MSRMT, ComponentActivation.SHTDN);
    }

    @Test
    @DisplayName(
            "MetricStatusManipulationMSRMTActivationStateSHTDN: The precondition does not fail if setMetricStatus is not supported by all metrics.")
    void testMetricStatusManipulationMSRMTActivationStateSHTDNAllowNotSupported2() {
        metricMockSetup(
                MetricCategory.MSRMT, METRIC_HANDLE, SOME_HANDLE, ComponentActivation.ON, ComponentActivation.SHTDN);

        // let one metric not support setMetricStatus manipulation
        when(mockManipulations.setMetricStatus(
                        eq(SOME_HANDLE), any(MetricCategory.class), any(ComponentActivation.class)))
                .thenReturn(ResponseTypes.Result.RESULT_NOT_SUPPORTED);

        when(mockDevice.getMdibAccess().findEntitiesByType(AbstractMetricDescriptor.class))
                .thenReturn(List.of(mockEntity2, mockEntity));

        assertTrue(ManipulationPreconditions.MetricStatusManipulationMSRMTActivationStateSHTDN.manipulation(injector));

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.ON);
        verify(mockManipulations).setComponentActivation(SOME_HANDLE, ComponentActivation.ON);
        verify(mockManipulations).setMetricStatus(METRIC_HANDLE, MetricCategory.MSRMT, ComponentActivation.SHTDN);
    }

    @Test
    @DisplayName("testMetricStatusManipulationMSRMTActivationStateSHTDNBad: First Manipulation failed.")
    void testMetricStatusManipulationMSRMTActivationStateSHTDNBadFirstManipulationFailed() {

        // given

        final ComponentActivation startActivationState = ComponentActivation.ON;
        final MetricCategory metricCategory = MetricCategory.MSRMT;
        final ComponentActivation activationState = ComponentActivation.SHTDN;
        final String metricStateHandle = "metricStateHandle";

        setupMetricStatusManipulation(metricCategory, metricStateHandle);

        when(mockManipulations.setComponentActivation(metricStateHandle, startActivationState))
                .thenReturn(ResponseTypes.Result.RESULT_FAIL);
        when(mockManipulations.setMetricStatus(metricStateHandle, metricCategory, activationState))
                .thenReturn(ResponseTypes.Result.RESULT_SUCCESS);

        // when

        final boolean result =
                ManipulationPreconditions.MetricStatusManipulationMSRMTActivationStateSHTDN.manipulation(injector);

        // then
        assertFalse(result);
        verify(mockManipulations).setComponentActivation(metricStateHandle, startActivationState);
    }

    @Test
    @DisplayName("testMetricStatusManipulationMSRMTActivationStateSHTDNBad: Second Manipulation Failed.")
    void testMetricStatusManipulationMSRMTActivationStateSHTDNBadSecondManipulationFailed() {

        // given
        final ComponentActivation startActivationState = ComponentActivation.ON;
        final MetricCategory metricCategory = MetricCategory.MSRMT;
        final ComponentActivation activationState = ComponentActivation.SHTDN;
        final String metricStateHandle = "metricStateHandle";

        setupMetricStatusManipulation(metricCategory, metricStateHandle);

        when(mockManipulations.setComponentActivation(metricStateHandle, startActivationState))
                .thenReturn(ResponseTypes.Result.RESULT_SUCCESS);
        when(mockManipulations.setMetricStatus(metricStateHandle, metricCategory, activationState))
                .thenReturn(ResponseTypes.Result.RESULT_FAIL);

        // when
        final boolean result =
                ManipulationPreconditions.MetricStatusManipulationMSRMTActivationStateSHTDN.manipulation(injector);

        // then
        assertFalse(result);
        verify(mockManipulations).setComponentActivation(metricStateHandle, startActivationState);
        verify(mockManipulations).setMetricStatus(metricStateHandle, metricCategory, activationState);
    }

    private void setupMetricStatusManipulation(final MetricCategory metricCategory, final String metricStateHandle) {
        when(mockDevice.getMdibAccess()).thenReturn(mockMdibAccess);
        when(mockMdibAccess.findEntitiesByType(AbstractMetricDescriptor.class)).thenReturn(List.of(mockEntity));
        when(mockEntity.getDescriptor(AbstractMetricDescriptor.class)).thenReturn(Optional.of(mockMetricDescriptor));
        when(mockMetricDescriptor.getMetricCategory()).thenReturn(metricCategory);
        when(mockEntity.getStates(AbstractMetricState.class)).thenReturn(List.of(mockMetricState));
        when(mockMetricState.getDescriptorHandle()).thenReturn(metricStateHandle);
    }

    private void alertConditionPresenceManipulationSetup() {
        when(mockDevice.getMdibAccess()).thenReturn(mockMdibAccess);
        when(mockMdibAccess.findEntitiesByType(AlertConditionDescriptor.class))
                .thenReturn(List.of(mockEntity, mockEntity2));
        when(mockEntity.getHandle()).thenReturn(ALERT_CONDITION_HANDLE);
        when(mockEntity.getParent()).thenReturn(Optional.of(ALERT_SYSTEM_CONTEXT_HANDLE));
        when(mockEntity2.getHandle()).thenReturn(ALERT_CONDITION_HANDLE2);
        when(mockEntity2.getParent()).thenReturn(Optional.of(ALERT_SYSTEM_CONTEXT_HANDLE2));

        when(mockMdibAccess.getState(ALERT_SYSTEM_CONTEXT_HANDLE, AbstractAlertState.class))
                .thenReturn(Optional.of(mockAlertSystemState));
        when(mockMdibAccess.getState(ALERT_SYSTEM_CONTEXT_HANDLE2, AbstractAlertState.class))
                .thenReturn(Optional.of(mockAlertSystemState2));
        when(mockMdibAccess.getState(ALERT_CONDITION_HANDLE, AbstractAlertState.class))
                .thenReturn(Optional.of(mockAlertConditionState));
        when(mockMdibAccess.getState(ALERT_CONDITION_HANDLE2, AbstractAlertState.class))
                .thenReturn(Optional.of(mockAlertConditionState2));
        when(mockMdibAccess.getState(ALERT_CONDITION_HANDLE, AlertConditionState.class))
                .thenReturn(Optional.of(mockAlertConditionState));
        when(mockMdibAccess.getState(ALERT_CONDITION_HANDLE2, AlertConditionState.class))
                .thenReturn(Optional.of(mockAlertConditionState2));

        when(mockAlertSystemState.getActivationState()).thenReturn(AlertActivation.OFF);
        when(mockAlertSystemState2.getActivationState()).thenReturn(AlertActivation.OFF);
        when(mockAlertConditionState.getActivationState()).thenReturn(AlertActivation.OFF);
        when(mockAlertConditionState2.getActivationState()).thenReturn(AlertActivation.OFF);

        when(mockAlertConditionState.isPresence()).thenReturn(true);
        when(mockAlertConditionState2.isPresence()).thenReturn(true);
    }

    private void verifyAlertConditionPresenceSetAlertActivationInteractions(
            final int numberOfManipulations,
            final List<String> expectedHandles,
            final List<AlertActivation> expectedAlertActivations) {
        final var activationStateHandleCaptor = ArgumentCaptor.forClass(String.class);
        final var activationStateCaptor = ArgumentCaptor.forClass(AlertActivation.class);
        verify(mockManipulations, times(numberOfManipulations))
                .setAlertActivation(activationStateHandleCaptor.capture(), activationStateCaptor.capture());

        assertEquals(expectedHandles, activationStateHandleCaptor.getAllValues());
        assertEquals(expectedAlertActivations, activationStateCaptor.getAllValues());
    }

    private void verifyAlertConditionPresenceSetAlertConditionPresenceInteractions(
            final int numberOfManipulations,
            final List<String> expectedHandles,
            final List<Boolean> expectedAlertActivations) {
        final var handleCaptor = ArgumentCaptor.forClass(String.class);
        final var presenceCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(mockManipulations, times(numberOfManipulations))
                .setAlertConditionPresence(handleCaptor.capture(), presenceCaptor.capture());

        assertEquals(expectedHandles, handleCaptor.getAllValues());
        assertEquals(expectedAlertActivations, presenceCaptor.getAllValues());
    }

    @Test
    @DisplayName(
            "AlertConditionPresence: set alert activation and set presence correctly and stop when an alert condition was successfully manipulated")
    void testSetPresenceForAlertConditionSuccessful() {
        alertConditionPresenceManipulationSetup();
        when(mockManipulations.setAlertActivation(anyString(), eq(AlertActivation.OFF)))
                .thenReturn(ResponseTypes.Result.RESULT_SUCCESS);
        when(mockManipulations.setAlertConditionPresence(anyString(), eq(true)))
                .thenReturn(ResponseTypes.Result.RESULT_SUCCESS);

        assertTrue(
                ManipulationPreconditions.AlertConditionPresenceManipulation.manipulation(injector),
                "Manipulation should've succeeded");

        // precondition should stop after the first successful
        verifyAlertConditionPresenceSetAlertActivationInteractions(
                2,
                List.of(ALERT_CONDITION_HANDLE, ALERT_SYSTEM_CONTEXT_HANDLE),
                List.of(AlertActivation.OFF, AlertActivation.OFF));
        verifyAlertConditionPresenceSetAlertConditionPresenceInteractions(
                1, List.of(ALERT_CONDITION_HANDLE), List.of(true));
    }

    @Test
    @DisplayName(
            "AlertConditionPresence: allow result not_supported for setAlertConditionPresence when at least one successful is seen")
    void testSetPresenceForAlertConditionAllowNotSupported1() {
        alertConditionPresenceManipulationSetup();
        when(mockManipulations.setAlertActivation(anyString(), eq(AlertActivation.OFF)))
                .thenReturn(ResponseTypes.Result.RESULT_SUCCESS);
        // setAlertConditionPresence is not supported for first alert condition
        when(mockManipulations.setAlertConditionPresence(ALERT_CONDITION_HANDLE, true))
                .thenReturn(ResponseTypes.Result.RESULT_NOT_SUPPORTED);
        when(mockManipulations.setAlertConditionPresence(ALERT_CONDITION_HANDLE2, true))
                .thenReturn(ResponseTypes.Result.RESULT_SUCCESS);

        assertTrue(
                ManipulationPreconditions.AlertConditionPresenceManipulation.manipulation(injector),
                "Manipulation should've succeeded");

        verifyAlertConditionPresenceSetAlertActivationInteractions(
                4,
                List.of(
                        ALERT_CONDITION_HANDLE,
                        ALERT_SYSTEM_CONTEXT_HANDLE,
                        ALERT_CONDITION_HANDLE2,
                        ALERT_SYSTEM_CONTEXT_HANDLE2),
                List.of(AlertActivation.OFF, AlertActivation.OFF, AlertActivation.OFF, AlertActivation.OFF));
        verifyAlertConditionPresenceSetAlertConditionPresenceInteractions(
                2, List.of(ALERT_CONDITION_HANDLE, ALERT_CONDITION_HANDLE2), List.of(true, true));
    }

    @Test
    @DisplayName("AlertConditionPresence: alert activation not supported does not fail the precondition")
    void testSetPresenceForAlertConditionAllowNotSupported2() {
        alertConditionPresenceManipulationSetup();
        // manipulation of alert activation is not supported from DUT
        when(mockManipulations.setAlertActivation(anyString(), eq(AlertActivation.OFF)))
                .thenReturn(ResponseTypes.Result.RESULT_NOT_SUPPORTED);
        when(mockManipulations.setAlertConditionPresence(anyString(), eq(true)))
                .thenReturn(ResponseTypes.Result.RESULT_SUCCESS);

        assertTrue(
                ManipulationPreconditions.AlertConditionPresenceManipulation.manipulation(injector),
                "Manipulation should've succeeded");

        // precondition should stop after the first successful
        verifyAlertConditionPresenceSetAlertActivationInteractions(
                2,
                List.of(ALERT_CONDITION_HANDLE, ALERT_SYSTEM_CONTEXT_HANDLE),
                List.of(AlertActivation.OFF, AlertActivation.OFF));
        verifyAlertConditionPresenceSetAlertConditionPresenceInteractions(
                1, List.of(ALERT_CONDITION_HANDLE), List.of(true));
    }

    @Test
    @DisplayName("AlertConditionPresence: precondition fails when setAlertActivation failed")
    void testSetPresenceForAlertConditionFail1() {
        alertConditionPresenceManipulationSetup();
        // manipulation of alert activation fails
        when(mockManipulations.setAlertActivation(anyString(), eq(AlertActivation.OFF)))
                .thenReturn(ResponseTypes.Result.RESULT_FAIL);
        when(mockManipulations.setAlertConditionPresence(anyString(), eq(true)))
                .thenReturn(ResponseTypes.Result.RESULT_SUCCESS);

        // precondition should return false, since RESULT_FAIL was seen
        assertFalse(
                ManipulationPreconditions.AlertConditionPresenceManipulation.manipulation(injector),
                "Manipulation should've succeeded");

        verifyAlertConditionPresenceSetAlertActivationInteractions(
                4,
                List.of(
                        ALERT_CONDITION_HANDLE,
                        ALERT_SYSTEM_CONTEXT_HANDLE,
                        ALERT_CONDITION_HANDLE2,
                        ALERT_SYSTEM_CONTEXT_HANDLE2),
                List.of(AlertActivation.OFF, AlertActivation.OFF, AlertActivation.OFF, AlertActivation.OFF));
        verifyAlertConditionPresenceSetAlertConditionPresenceInteractions(
                2, List.of(ALERT_CONDITION_HANDLE, ALERT_CONDITION_HANDLE2), List.of(true, true));
    }

    @Test
    @DisplayName("AlertConditionPresence: precondition fails when setAlertActivation is not implemented")
    void testSetPresenceForAlertConditionFail2() {
        alertConditionPresenceManipulationSetup();
        // manipulation of alert activation fails
        when(mockManipulations.setAlertActivation(anyString(), eq(AlertActivation.OFF)))
                .thenReturn(ResponseTypes.Result.RESULT_NOT_IMPLEMENTED);
        when(mockManipulations.setAlertConditionPresence(anyString(), eq(true)))
                .thenReturn(ResponseTypes.Result.RESULT_SUCCESS);

        // precondition should return false, since RESULT_NOT_IMPLEMENTED was seen
        assertFalse(
                ManipulationPreconditions.AlertConditionPresenceManipulation.manipulation(injector),
                "Manipulation should've succeeded");

        verifyAlertConditionPresenceSetAlertActivationInteractions(
                4,
                List.of(
                        ALERT_CONDITION_HANDLE,
                        ALERT_SYSTEM_CONTEXT_HANDLE,
                        ALERT_CONDITION_HANDLE2,
                        ALERT_SYSTEM_CONTEXT_HANDLE2),
                List.of(AlertActivation.OFF, AlertActivation.OFF, AlertActivation.OFF, AlertActivation.OFF));
        verifyAlertConditionPresenceSetAlertConditionPresenceInteractions(
                2, List.of(ALERT_CONDITION_HANDLE, ALERT_CONDITION_HANDLE2), List.of(true, true));
    }

    @Test
    @DisplayName("AlertConditionPresence: precondition fails when setAlertConditionPresence failed")
    void testSetPresenceForAlertConditionFail3() {
        alertConditionPresenceManipulationSetup();
        // manipulation of alert activation fails
        when(mockManipulations.setAlertActivation(anyString(), eq(AlertActivation.OFF)))
                .thenReturn(ResponseTypes.Result.RESULT_SUCCESS);
        when(mockManipulations.setAlertConditionPresence(anyString(), eq(true)))
                .thenReturn(ResponseTypes.Result.RESULT_FAIL);

        // precondition should return false, since RESULT_NOT_IMPLEMENTED was seen
        assertFalse(
                ManipulationPreconditions.AlertConditionPresenceManipulation.manipulation(injector),
                "Manipulation should've succeeded");

        verifyAlertConditionPresenceSetAlertActivationInteractions(
                4,
                List.of(
                        ALERT_CONDITION_HANDLE,
                        ALERT_SYSTEM_CONTEXT_HANDLE,
                        ALERT_CONDITION_HANDLE2,
                        ALERT_SYSTEM_CONTEXT_HANDLE2),
                List.of(AlertActivation.OFF, AlertActivation.OFF, AlertActivation.OFF, AlertActivation.OFF));
        verifyAlertConditionPresenceSetAlertConditionPresenceInteractions(
                2, List.of(ALERT_CONDITION_HANDLE, ALERT_CONDITION_HANDLE2), List.of(true, true));
    }

    @Test
    @DisplayName("AlertConditionPresence: precondition fails when setAlertConditionPresence is not implemented")
    void testSetPresenceForAlertConditionFail4() {
        alertConditionPresenceManipulationSetup();
        // manipulation of alert activation fails
        when(mockManipulations.setAlertActivation(anyString(), eq(AlertActivation.OFF)))
                .thenReturn(ResponseTypes.Result.RESULT_SUCCESS);
        when(mockManipulations.setAlertConditionPresence(anyString(), eq(true)))
                .thenReturn(ResponseTypes.Result.RESULT_NOT_IMPLEMENTED);

        // precondition should return false, since RESULT_NOT_IMPLEMENTED was seen
        assertFalse(
                ManipulationPreconditions.AlertConditionPresenceManipulation.manipulation(injector),
                "Manipulation should've succeeded");

        verifyAlertConditionPresenceSetAlertActivationInteractions(
                4,
                List.of(
                        ALERT_CONDITION_HANDLE,
                        ALERT_SYSTEM_CONTEXT_HANDLE,
                        ALERT_CONDITION_HANDLE2,
                        ALERT_SYSTEM_CONTEXT_HANDLE2),
                List.of(AlertActivation.OFF, AlertActivation.OFF, AlertActivation.OFF, AlertActivation.OFF));
        verifyAlertConditionPresenceSetAlertConditionPresenceInteractions(
                2, List.of(ALERT_CONDITION_HANDLE, ALERT_CONDITION_HANDLE2), List.of(true, true));
    }

    void setActivationStateSetup() {
        // create mock alert system state
        when(mockAlertSystemState.getDescriptorHandle()).thenReturn(ALERT_SYSTEM_CONTEXT_HANDLE);
        when(mockAlertSystemState.getActivationState())
                .thenReturn(AlertActivation.ON)
                .thenReturn(AlertActivation.PSD)
                .thenReturn(AlertActivation.OFF);

        // create second mock alert system state
        when(mockAlertSystemState2.getDescriptorHandle()).thenReturn(ALERT_SYSTEM_CONTEXT_HANDLE2);
        when(mockAlertSystemState2.getActivationState())
                .thenReturn(AlertActivation.ON)
                .thenReturn(AlertActivation.PSD)
                .thenReturn(AlertActivation.OFF);

        // return mock states on request
        when(mockDevice.getMdibAccess().getState(ALERT_SYSTEM_CONTEXT_HANDLE, AlertSystemState.class))
                .thenReturn(Optional.of(mockAlertSystemState));
        when(mockDevice.getMdibAccess().getState(ALERT_SYSTEM_CONTEXT_HANDLE2, AlertSystemState.class))
                .thenReturn(Optional.of(mockAlertSystemState2));

        // create mock entities to hold the states
        when(mockEntity.getHandle()).thenReturn(ALERT_SYSTEM_CONTEXT_HANDLE);
        when(mockEntity2.getHandle()).thenReturn(ALERT_SYSTEM_CONTEXT_HANDLE2);
        when(mockDevice.getMdibAccess().findEntitiesByType(AlertSystemDescriptor.class))
                .thenReturn(List.of(mockEntity, mockEntity2));
    }

    @Test
    @DisplayName("setActivationState: set activation state for an alert system correctly")
    void testSetActivationStateForAlertSystem() {
        setActivationStateSetup();

        // make manipulation return true for the manipulations and false afterwards
        when(mockManipulations.setAlertActivation(any(String.class), any(AlertActivation.class)))
                .thenReturn(ResponseTypes.Result.RESULT_SUCCESS)
                .thenReturn(ResponseTypes.Result.RESULT_SUCCESS)
                .thenReturn(ResponseTypes.Result.RESULT_SUCCESS)
                .thenReturn(ResponseTypes.Result.RESULT_SUCCESS)
                .thenReturn(ResponseTypes.Result.RESULT_SUCCESS)
                .thenReturn(ResponseTypes.Result.RESULT_SUCCESS)
                .thenReturn(ResponseTypes.Result.RESULT_FAIL);

        final var expectedManipulationCalls = 6;
        final var expectedActivationStates = List.of(
                AlertActivation.ON,
                AlertActivation.PSD,
                AlertActivation.OFF,
                AlertActivation.ON,
                AlertActivation.PSD,
                AlertActivation.OFF);
        final var expectedHandles = List.of(
                ALERT_SYSTEM_CONTEXT_HANDLE,
                ALERT_SYSTEM_CONTEXT_HANDLE,
                ALERT_SYSTEM_CONTEXT_HANDLE,
                ALERT_SYSTEM_CONTEXT_HANDLE2,
                ALERT_SYSTEM_CONTEXT_HANDLE2,
                ALERT_SYSTEM_CONTEXT_HANDLE2);

        assertTrue(
                ManipulationPreconditions.AlertSystemActivationStateManipulation.manipulation(injector),
                "Manipulation should've succeeded");
        assertFalse(
                testRunObserver.isInvalid(),
                "Test run should not have been invalid. Reason(s): " + testRunObserver.getReasons());

        final var handleCaptor = ArgumentCaptor.forClass(String.class);
        final var activationStateCaptor = ArgumentCaptor.forClass(AlertActivation.class);
        verify(mockManipulations, times(expectedManipulationCalls))
                .setAlertActivation(handleCaptor.capture(), activationStateCaptor.capture());

        assertEquals(expectedHandles, handleCaptor.getAllValues());
        assertEquals(expectedActivationStates, activationStateCaptor.getAllValues());
    }

    @Test
    @DisplayName(
            "AlertSystemActivationStateManipulation: allow result not_supported for setAlertConditionPresence when at least one successful is seen")
    void testSetAlertActivationManipulationAllowNotSupported() {
        setActivationStateSetup();
        // let one alert system not support manipulations
        when(mockManipulations.setAlertActivation(eq(ALERT_SYSTEM_CONTEXT_HANDLE), any(AlertActivation.class)))
                .thenReturn(ResponseTypes.Result.RESULT_NOT_SUPPORTED);
        // make manipulation for the other alert system return true for the manipulations and false afterwards
        when(mockManipulations.setAlertActivation(eq(ALERT_SYSTEM_CONTEXT_HANDLE2), any(AlertActivation.class)))
                .thenReturn(ResponseTypes.Result.RESULT_SUCCESS)
                .thenReturn(ResponseTypes.Result.RESULT_SUCCESS)
                .thenReturn(ResponseTypes.Result.RESULT_SUCCESS)
                .thenReturn(ResponseTypes.Result.RESULT_SUCCESS)
                .thenReturn(ResponseTypes.Result.RESULT_SUCCESS)
                .thenReturn(ResponseTypes.Result.RESULT_SUCCESS)
                .thenReturn(ResponseTypes.Result.RESULT_FAIL);

        final var expectedManipulationCalls = 6;
        final var expectedActivationStates = List.of(
                AlertActivation.ON,
                AlertActivation.PSD,
                AlertActivation.OFF,
                AlertActivation.ON,
                AlertActivation.PSD,
                AlertActivation.OFF);
        final var expectedHandles = List.of(
                ALERT_SYSTEM_CONTEXT_HANDLE,
                ALERT_SYSTEM_CONTEXT_HANDLE,
                ALERT_SYSTEM_CONTEXT_HANDLE,
                ALERT_SYSTEM_CONTEXT_HANDLE2,
                ALERT_SYSTEM_CONTEXT_HANDLE2,
                ALERT_SYSTEM_CONTEXT_HANDLE2);

        assertTrue(
                ManipulationPreconditions.AlertSystemActivationStateManipulation.manipulation(injector),
                "Manipulation should've succeeded");
        assertFalse(
                testRunObserver.isInvalid(),
                "Test run should not have been invalid. Reason(s): " + testRunObserver.getReasons());

        final var handleCaptor = ArgumentCaptor.forClass(String.class);
        final var activationStateCaptor = ArgumentCaptor.forClass(AlertActivation.class);
        verify(mockManipulations, times(expectedManipulationCalls))
                .setAlertActivation(handleCaptor.capture(), activationStateCaptor.capture());

        assertEquals(expectedHandles, handleCaptor.getAllValues());
        assertEquals(expectedActivationStates, activationStateCaptor.getAllValues());
    }

    @Test
    @DisplayName("setActivationState: wrong ActivationState")
    void testSetActivationStateForAlertSystemWrongActivationState() {
        setActivationStateSetup();
        // make manipulation return true for the manipulations and false afterwards
        when(mockManipulations.setAlertActivation(any(String.class), any(AlertActivation.class)))
                .thenReturn(ResponseTypes.Result.RESULT_SUCCESS)
                .thenReturn(ResponseTypes.Result.RESULT_SUCCESS)
                .thenReturn(ResponseTypes.Result.RESULT_SUCCESS)
                .thenReturn(ResponseTypes.Result.RESULT_SUCCESS)
                .thenReturn(ResponseTypes.Result.RESULT_SUCCESS)
                .thenReturn(ResponseTypes.Result.RESULT_SUCCESS)
                .thenReturn(ResponseTypes.Result.RESULT_FAIL);

        when(mockAlertSystemState.getActivationState()).thenReturn(AlertActivation.OFF);

        assertFalse(
                ManipulationPreconditions.AlertSystemActivationStateManipulation.manipulation(injector),
                "manipulation should've failed.");
        assertTrue(testRunObserver.isInvalid(), "Test run should have been invalid.");
    }

    private void metricMockSetup(
            final MetricCategory category,
            final String metricHandle,
            final String secondMetricHandle,
            final ComponentActivation startingState,
            final ComponentActivation endState) {
        // create mock metric
        when(mockMetricDescriptor.getHandle()).thenReturn(metricHandle);
        when(mockMetricDescriptor.getMetricCategory()).thenReturn(category);
        when(mockMetricState.getDescriptorHandle()).thenReturn(metricHandle);
        when(mockMetricState.getActivationState()).thenReturn(startingState).thenReturn(endState);

        // create second mock metric
        when(mockMetricDescriptor2.getHandle()).thenReturn(secondMetricHandle);
        when(mockMetricDescriptor2.getMetricCategory()).thenReturn(category);
        when(mockMetricState2.getDescriptorHandle()).thenReturn(secondMetricHandle);
        when(mockMetricState2.getActivationState()).thenReturn(startingState).thenReturn(endState);

        // create mock entities to hold the states
        when(mockEntity.getHandle()).thenReturn(metricHandle);
        when(mockEntity.getDescriptor(AbstractMetricDescriptor.class)).thenReturn(Optional.of(mockMetricDescriptor));
        when(mockEntity.getStates(AbstractMetricState.class)).thenReturn(List.of(mockMetricState));
        when(mockEntity2.getHandle()).thenReturn(secondMetricHandle);
        when(mockEntity2.getDescriptor(AbstractMetricDescriptor.class)).thenReturn(Optional.of(mockMetricDescriptor2));
        when(mockEntity2.getStates(AbstractMetricState.class)).thenReturn(List.of(mockMetricState2));

        // make setComponentActivation return true for the manipulations and false afterwards
        when(mockManipulations.setComponentActivation(any(String.class), any(ComponentActivation.class)))
                .thenReturn(ResponseTypes.Result.RESULT_SUCCESS)
                .thenReturn(ResponseTypes.Result.RESULT_SUCCESS)
                .thenReturn(ResponseTypes.Result.RESULT_FAIL);

        // make setMetricStatus return true for the manipulations and false afterwards
        when(mockManipulations.setMetricStatus(
                        any(String.class), any(MetricCategory.class), any(ComponentActivation.class)))
                .thenReturn(ResponseTypes.Result.RESULT_SUCCESS)
                .thenReturn(ResponseTypes.Result.RESULT_SUCCESS)
                .thenReturn(ResponseTypes.Result.RESULT_FAIL);
    }

    private void setMetricStatusSetup(
            final MetricCategory category,
            final String metricHandle,
            final ComponentActivation startingState,
            final ComponentActivation endState) {
        // create mock metric
        when(mockMetricDescriptor.getHandle()).thenReturn(metricHandle);
        when(mockMetricDescriptor.getMetricCategory()).thenReturn(category);
        when(mockMetricState.getDescriptorHandle()).thenReturn(metricHandle);
        when(mockMetricState.getActivationState()).thenReturn(startingState).thenReturn(endState);

        // make setComponentActivation return true for the manipulations and false afterwards
        when(mockManipulations.setComponentActivation(any(String.class), any(ComponentActivation.class)))
                .thenReturn(ResponseTypes.Result.RESULT_SUCCESS)
                .thenReturn(ResponseTypes.Result.RESULT_FAIL);

        // make setMetricStatus return true for the manipulations and false afterwards
        when(mockManipulations.setMetricStatus(
                        any(String.class), any(MetricCategory.class), any(ComponentActivation.class)))
                .thenReturn(ResponseTypes.Result.RESULT_SUCCESS)
                .thenReturn(ResponseTypes.Result.RESULT_FAIL);

        // create mock entities to hold the states
        when(mockEntity.getHandle()).thenReturn(metricHandle);
        when(mockEntity.getDescriptor(AbstractMetricDescriptor.class)).thenReturn(Optional.of(mockMetricDescriptor));
        when(mockEntity.getStates(AbstractMetricState.class)).thenReturn(List.of(mockMetricState));
        when(mockDevice.getMdibAccess().findEntitiesByType(AbstractMetricDescriptor.class))
                .thenReturn(List.of(mockEntity));
    }

    @Test
    @DisplayName(
            "MetricStatusManipulationMSRMTActivationStateON: Setting a metric with category MSRMT to `measurement is"
                    + " being performed` results in activation state ON.")
    void testMetricStatusManipulationMSRMTActivationStateONGood() {
        setMetricStatusSetup(MetricCategory.MSRMT, METRIC_HANDLE, ComponentActivation.OFF, ComponentActivation.ON);

        assertTrue(ManipulationPreconditions.MetricStatusManipulationMSRMTActivationStateON.manipulation(injector));

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.OFF);
        verify(mockManipulations).setMetricStatus(METRIC_HANDLE, MetricCategory.MSRMT, ComponentActivation.ON);
    }

    @Test
    @DisplayName(
            "MetricStatusManipulationMSRMTActivationStateON: The precondition does not fail if setComponentActivation is not supported by all metrics.")
    void testMetricStatusManipulationMSRMTActivationStateONAllowNotSupported1() {
        metricMockSetup(
                MetricCategory.MSRMT, METRIC_HANDLE, SOME_HANDLE, ComponentActivation.OFF, ComponentActivation.ON);

        // let one metric not support setComponentActivation manipulation
        when(mockManipulations.setComponentActivation(eq(SOME_HANDLE), any(ComponentActivation.class)))
                .thenReturn(ResponseTypes.Result.RESULT_NOT_SUPPORTED);

        when(mockDevice.getMdibAccess().findEntitiesByType(AbstractMetricDescriptor.class))
                .thenReturn(List.of(mockEntity2, mockEntity));

        assertTrue(ManipulationPreconditions.MetricStatusManipulationMSRMTActivationStateON.manipulation(injector));

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.OFF);
        verify(mockManipulations).setComponentActivation(SOME_HANDLE, ComponentActivation.OFF);
        verify(mockManipulations).setMetricStatus(METRIC_HANDLE, MetricCategory.MSRMT, ComponentActivation.ON);
    }

    @Test
    @DisplayName(
            "MetricStatusManipulationMSRMTActivationStateON: The precondition does not fail if setMetricStatus is not supported by all metrics.")
    void testMetricStatusManipulationMSRMTActivationStateONAllowNotSupported2() {
        metricMockSetup(
                MetricCategory.MSRMT, METRIC_HANDLE, SOME_HANDLE, ComponentActivation.OFF, ComponentActivation.ON);

        // let one metric not support setMetricStatus manipulation
        when(mockManipulations.setMetricStatus(
                        eq(SOME_HANDLE), any(MetricCategory.class), any(ComponentActivation.class)))
                .thenReturn(ResponseTypes.Result.RESULT_NOT_SUPPORTED);

        when(mockDevice.getMdibAccess().findEntitiesByType(AbstractMetricDescriptor.class))
                .thenReturn(List.of(mockEntity2, mockEntity));

        assertTrue(ManipulationPreconditions.MetricStatusManipulationMSRMTActivationStateON.manipulation(injector));

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.OFF);
        verify(mockManipulations).setComponentActivation(SOME_HANDLE, ComponentActivation.OFF);
        verify(mockManipulations).setMetricStatus(METRIC_HANDLE, MetricCategory.MSRMT, ComponentActivation.ON);
    }

    @Test
    @DisplayName("MetricStatusManipulationMSRMTActivationStateON: setComponentActivation failed.")
    void testMetricStatusManipulationMSRMTActivationStateONBadFirstManipulationFailed() {
        setMetricStatusSetup(MetricCategory.MSRMT, METRIC_HANDLE, ComponentActivation.OFF, ComponentActivation.ON);

        // let setComponentActivation fail
        when(mockManipulations.setComponentActivation(any(String.class), any(ComponentActivation.class)))
                .thenReturn(ResponseTypes.Result.RESULT_FAIL);

        assertFalse(ManipulationPreconditions.MetricStatusManipulationMSRMTActivationStateON.manipulation(injector));

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.OFF);
    }

    @Test
    @DisplayName("MetricStatusManipulationMSRMTActivationStateON: setMetricStatus failed.")
    void testMetricStatusManipulationMSRMTActivationStateONBadSecondManipulationFailed() {
        setMetricStatusSetup(MetricCategory.MSRMT, METRIC_HANDLE, ComponentActivation.OFF, ComponentActivation.ON);

        // let setMetricStatus fail
        when(mockManipulations.setMetricStatus(
                        any(String.class), any(MetricCategory.class), any(ComponentActivation.class)))
                .thenReturn(ResponseTypes.Result.RESULT_FAIL);

        assertFalse(ManipulationPreconditions.MetricStatusManipulationMSRMTActivationStateON.manipulation(injector));

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.OFF);
        verify(mockManipulations).setMetricStatus(METRIC_HANDLE, MetricCategory.MSRMT, ComponentActivation.ON);
    }

    @Test
    @DisplayName("MetricStatusManipulationMSRMTActivationStateNOTRDY: Set metric with category MSRMT to currently"
            + " initializing which results in activation state NOT_RDY.")
    void testMetricStatusManipulationMSRMTActivationStateNOTRDYGood() {
        setMetricStatusSetup(MetricCategory.MSRMT, METRIC_HANDLE, ComponentActivation.OFF, ComponentActivation.NOT_RDY);

        assertTrue(ManipulationPreconditions.MetricStatusManipulationMSRMTActivationStateNOTRDY.manipulation(injector));

        assertFalse(
                testRunObserver.isInvalid(),
                "Test run should not have been invalidated. Reason(s): " + testRunObserver.getReasons());

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.OFF);
        verify(mockManipulations).setMetricStatus(METRIC_HANDLE, MetricCategory.MSRMT, ComponentActivation.NOT_RDY);
    }

    @Test
    @DisplayName(
            "MetricStatusManipulationMSRMTActivationStateNOTRDY: The precondition does not fail if setComponentActivation is not supported by all metrics.")
    void testMetricStatusManipulationMSRMTActivationStateNOTRDYAllowNotSupported1() {
        metricMockSetup(
                MetricCategory.MSRMT, METRIC_HANDLE, SOME_HANDLE, ComponentActivation.OFF, ComponentActivation.NOT_RDY);

        // let one metric not support setComponentActivation manipulation
        when(mockManipulations.setComponentActivation(eq(SOME_HANDLE), any(ComponentActivation.class)))
                .thenReturn(ResponseTypes.Result.RESULT_NOT_SUPPORTED);

        when(mockDevice.getMdibAccess().findEntitiesByType(AbstractMetricDescriptor.class))
                .thenReturn(List.of(mockEntity2, mockEntity));

        assertTrue(ManipulationPreconditions.MetricStatusManipulationMSRMTActivationStateNOTRDY.manipulation(injector));

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.OFF);
        verify(mockManipulations).setComponentActivation(SOME_HANDLE, ComponentActivation.OFF);
        verify(mockManipulations).setMetricStatus(METRIC_HANDLE, MetricCategory.MSRMT, ComponentActivation.NOT_RDY);
    }

    @Test
    @DisplayName(
            "MetricStatusManipulationMSRMTActivationStateNOTRDY: The precondition does not fail if setMetricStatus is not supported by all metrics.")
    void testMetricStatusManipulationMSRMTActivationStateNOTRDYAllowNotSupported2() {
        metricMockSetup(
                MetricCategory.MSRMT, METRIC_HANDLE, SOME_HANDLE, ComponentActivation.OFF, ComponentActivation.NOT_RDY);

        // let one metric not support setMetricStatus manipulation
        when(mockManipulations.setMetricStatus(
                        eq(SOME_HANDLE), any(MetricCategory.class), any(ComponentActivation.class)))
                .thenReturn(ResponseTypes.Result.RESULT_NOT_SUPPORTED);

        when(mockDevice.getMdibAccess().findEntitiesByType(AbstractMetricDescriptor.class))
                .thenReturn(List.of(mockEntity2, mockEntity));

        assertTrue(ManipulationPreconditions.MetricStatusManipulationMSRMTActivationStateNOTRDY.manipulation(injector));

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.OFF);
        verify(mockManipulations).setComponentActivation(SOME_HANDLE, ComponentActivation.OFF);
        verify(mockManipulations).setMetricStatus(METRIC_HANDLE, MetricCategory.MSRMT, ComponentActivation.NOT_RDY);
    }

    @Test
    @DisplayName("MetricStatusManipulationMSRMTActivationStateNOTRDY: setComponentActivation failed.")
    void testMetricStatusManipulationMSRMTActivationStateNOTRDYBadFirstManipulationFailed() {
        setMetricStatusSetup(MetricCategory.MSRMT, METRIC_HANDLE, ComponentActivation.OFF, ComponentActivation.NOT_RDY);

        // let setComponentActivation fail
        when(mockManipulations.setComponentActivation(any(String.class), any(ComponentActivation.class)))
                .thenReturn(ResponseTypes.Result.RESULT_FAIL);

        assertFalse(
                ManipulationPreconditions.MetricStatusManipulationMSRMTActivationStateNOTRDY.manipulation(injector));

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.OFF);
    }

    @Test
    @DisplayName("MetricStatusManipulationMSRMTActivationStateNOTRDY: setMetricStatus failed.")
    void testMetricStatusManipulationMSRMTActivationStateNOTRDYBadSecondManipulationFailed() {
        setMetricStatusSetup(MetricCategory.MSRMT, METRIC_HANDLE, ComponentActivation.OFF, ComponentActivation.NOT_RDY);

        // let setMetricStatus fail
        when(mockManipulations.setMetricStatus(
                        any(String.class), any(MetricCategory.class), any(ComponentActivation.class)))
                .thenReturn(ResponseTypes.Result.RESULT_FAIL);

        assertFalse(
                ManipulationPreconditions.MetricStatusManipulationMSRMTActivationStateNOTRDY.manipulation(injector));

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.OFF);
        verify(mockManipulations).setMetricStatus(METRIC_HANDLE, MetricCategory.MSRMT, ComponentActivation.NOT_RDY);
    }

    @Test
    @DisplayName("MetricStatusManipulationMSRMTActivationStateSTNDBY: Set metric with category MSRMT to 'measurement"
            + " initialized, but is not being performed' which results in activation state STND_BY.")
    void testMetricStatusManipulationMSRMTActivationStateSTNDBYGood() {
        setMetricStatusSetup(MetricCategory.MSRMT, METRIC_HANDLE, ComponentActivation.ON, ComponentActivation.STND_BY);

        assertTrue(ManipulationPreconditions.MetricStatusManipulationMSRMTActivationStateSTNDBY.manipulation(injector));

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.ON);
        verify(mockManipulations).setMetricStatus(METRIC_HANDLE, MetricCategory.MSRMT, ComponentActivation.STND_BY);
    }

    @Test
    @DisplayName(
            "MetricStatusManipulationMSRMTActivationStateSTNDBY: The precondition does not fail if setComponentActivation is not supported by all metrics.")
    void testMetricStatusManipulationMSRMTActivationStateSTNDBYAllowNotSupported1() {
        metricMockSetup(
                MetricCategory.MSRMT, METRIC_HANDLE, SOME_HANDLE, ComponentActivation.ON, ComponentActivation.STND_BY);

        // let one metric not support setComponentActivation manipulation
        when(mockManipulations.setComponentActivation(eq(SOME_HANDLE), any(ComponentActivation.class)))
                .thenReturn(ResponseTypes.Result.RESULT_NOT_SUPPORTED);

        when(mockDevice.getMdibAccess().findEntitiesByType(AbstractMetricDescriptor.class))
                .thenReturn(List.of(mockEntity2, mockEntity));

        assertTrue(ManipulationPreconditions.MetricStatusManipulationMSRMTActivationStateSTNDBY.manipulation(injector));

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.ON);
        verify(mockManipulations).setComponentActivation(SOME_HANDLE, ComponentActivation.ON);
        verify(mockManipulations).setMetricStatus(METRIC_HANDLE, MetricCategory.MSRMT, ComponentActivation.STND_BY);
    }

    @Test
    @DisplayName(
            "MetricStatusManipulationMSRMTActivationStateSTNDBY: The precondition does not fail if setMetricStatus is not supported by all metrics.")
    void testMetricStatusManipulationMSRMTActivationStateSTNDBYAllowNotSupported2() {
        metricMockSetup(
                MetricCategory.MSRMT, METRIC_HANDLE, SOME_HANDLE, ComponentActivation.ON, ComponentActivation.STND_BY);

        // let one metric not support setMetricStatus manipulation
        when(mockManipulations.setMetricStatus(
                        eq(SOME_HANDLE), any(MetricCategory.class), any(ComponentActivation.class)))
                .thenReturn(ResponseTypes.Result.RESULT_NOT_SUPPORTED);

        when(mockDevice.getMdibAccess().findEntitiesByType(AbstractMetricDescriptor.class))
                .thenReturn(List.of(mockEntity2, mockEntity));

        assertTrue(ManipulationPreconditions.MetricStatusManipulationMSRMTActivationStateSTNDBY.manipulation(injector));

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.ON);
        verify(mockManipulations).setComponentActivation(SOME_HANDLE, ComponentActivation.ON);
        verify(mockManipulations).setMetricStatus(METRIC_HANDLE, MetricCategory.MSRMT, ComponentActivation.STND_BY);
    }

    @Test
    @DisplayName("MetricStatusManipulationMSRMTActivationStateSTNDBY: setComponentActivation failed.")
    void testMetricStatusManipulationMSRMTActivationStateSTNDBYBadFirstManipulationFailed() {
        setMetricStatusSetup(MetricCategory.MSRMT, METRIC_HANDLE, ComponentActivation.ON, ComponentActivation.STND_BY);

        // let setComponentActivation fail
        when(mockManipulations.setComponentActivation(any(String.class), any(ComponentActivation.class)))
                .thenReturn(ResponseTypes.Result.RESULT_FAIL);

        assertFalse(
                ManipulationPreconditions.MetricStatusManipulationMSRMTActivationStateSTNDBY.manipulation(injector));

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.ON);
    }

    @Test
    @DisplayName("MetricStatusManipulationMSRMTActivationStateSTNDBY: setMetricStatus failed.")
    void testMetricStatusManipulationMSRMTActivationStateSTNDBYBadSecondManipulationFailed() {
        setMetricStatusSetup(MetricCategory.MSRMT, METRIC_HANDLE, ComponentActivation.ON, ComponentActivation.STND_BY);

        // let setMetricStatus fail
        when(mockManipulations.setMetricStatus(
                        any(String.class), any(MetricCategory.class), any(ComponentActivation.class)))
                .thenReturn(ResponseTypes.Result.RESULT_FAIL);

        assertFalse(
                ManipulationPreconditions.MetricStatusManipulationMSRMTActivationStateSTNDBY.manipulation(injector));

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.ON);
        verify(mockManipulations).setMetricStatus(METRIC_HANDLE, MetricCategory.MSRMT, ComponentActivation.STND_BY);
    }

    @Test
    @DisplayName("MetricStatusManipulationMSRMTActivationStateOFF: Setting a metric with category MSRMT to `measurement"
            + " not being performed and is de-initialized` results in activation state OFF.")
    void testMetricStatusManipulationMSRMTActivationStateOFFGood() {
        setMetricStatusSetup(MetricCategory.MSRMT, METRIC_HANDLE, ComponentActivation.ON, ComponentActivation.OFF);

        assertTrue(ManipulationPreconditions.MetricStatusManipulationMSRMTActivationStateOFF.manipulation(injector));

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.ON);
        verify(mockManipulations).setMetricStatus(METRIC_HANDLE, MetricCategory.MSRMT, ComponentActivation.OFF);
    }

    @Test
    @DisplayName(
            "MetricStatusManipulationMSRMTActivationStateOFF: The precondition does not fail if setComponentActivation is not supported by all metrics.")
    void testMetricStatusManipulationMSRMTActivationStateOFFAllowNotSupported1() {
        metricMockSetup(
                MetricCategory.MSRMT, METRIC_HANDLE, SOME_HANDLE, ComponentActivation.ON, ComponentActivation.OFF);

        // let one metric not support setComponentActivation manipulation
        when(mockManipulations.setComponentActivation(eq(SOME_HANDLE), any(ComponentActivation.class)))
                .thenReturn(ResponseTypes.Result.RESULT_NOT_SUPPORTED);

        when(mockDevice.getMdibAccess().findEntitiesByType(AbstractMetricDescriptor.class))
                .thenReturn(List.of(mockEntity2, mockEntity));

        assertTrue(ManipulationPreconditions.MetricStatusManipulationMSRMTActivationStateOFF.manipulation(injector));

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.ON);
        verify(mockManipulations).setComponentActivation(SOME_HANDLE, ComponentActivation.ON);
        verify(mockManipulations).setMetricStatus(METRIC_HANDLE, MetricCategory.MSRMT, ComponentActivation.OFF);
    }

    @Test
    @DisplayName(
            "MetricStatusManipulationMSRMTActivationStateOFF: The precondition does not fail if setMetricStatus is not supported by all metrics.")
    void testMetricStatusManipulationMSRMTActivationStateOFFAllowNotSupported2() {
        metricMockSetup(
                MetricCategory.MSRMT, METRIC_HANDLE, SOME_HANDLE, ComponentActivation.ON, ComponentActivation.OFF);

        // let one metric not support setMetricStatus manipulation
        when(mockManipulations.setMetricStatus(
                        eq(SOME_HANDLE), any(MetricCategory.class), any(ComponentActivation.class)))
                .thenReturn(ResponseTypes.Result.RESULT_NOT_SUPPORTED);

        when(mockDevice.getMdibAccess().findEntitiesByType(AbstractMetricDescriptor.class))
                .thenReturn(List.of(mockEntity2, mockEntity));

        assertTrue(ManipulationPreconditions.MetricStatusManipulationMSRMTActivationStateOFF.manipulation(injector));

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.ON);
        verify(mockManipulations).setComponentActivation(SOME_HANDLE, ComponentActivation.ON);
        verify(mockManipulations).setMetricStatus(METRIC_HANDLE, MetricCategory.MSRMT, ComponentActivation.OFF);
    }

    @Test
    @DisplayName("MetricStatusManipulationMSRMTActivationStateOFF: setComponentActivation failed.")
    void testMetricStatusManipulationMSRMTActivationStateOFFBadFirstManipulationFailed() {
        setMetricStatusSetup(MetricCategory.MSRMT, METRIC_HANDLE, ComponentActivation.ON, ComponentActivation.OFF);

        // let setComponentActivation fail
        when(mockManipulations.setComponentActivation(any(String.class), any(ComponentActivation.class)))
                .thenReturn(ResponseTypes.Result.RESULT_FAIL);

        assertFalse(ManipulationPreconditions.MetricStatusManipulationMSRMTActivationStateOFF.manipulation(injector));

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.ON);
    }

    @Test
    @DisplayName("MetricStatusManipulationMSRMTActivationStateOFF: setMetricStatus failed.")
    void testMetricStatusManipulationMSRMTActivationStateOFFBadSecondManipulationFailed() {
        setMetricStatusSetup(MetricCategory.MSRMT, METRIC_HANDLE, ComponentActivation.ON, ComponentActivation.OFF);

        // let setMetricStatus fail
        when(mockManipulations.setMetricStatus(
                        any(String.class), any(MetricCategory.class), any(ComponentActivation.class)))
                .thenReturn(ResponseTypes.Result.RESULT_FAIL);

        assertFalse(ManipulationPreconditions.MetricStatusManipulationMSRMTActivationStateOFF.manipulation(injector));

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.ON);
        verify(mockManipulations).setMetricStatus(METRIC_HANDLE, MetricCategory.MSRMT, ComponentActivation.OFF);
    }

    @Test
    @DisplayName("testMetricStatusManipulationMSRMTActivationStateFAIL: Set ActivationState "
            + "of all MSRMT-Metrics to FAIL.")
    void testMetricStatusManipulationMSRMTActivationStateFAILGood() {
        setMetricStatusSetup(MetricCategory.MSRMT, METRIC_HANDLE, ComponentActivation.ON, ComponentActivation.FAIL);

        assertTrue(ManipulationPreconditions.MetricStatusManipulationMSRMTActivationStateFAIL.manipulation(injector));

        assertFalse(
                testRunObserver.isInvalid(),
                "Test run should not have been invalidated. Reason(s): " + testRunObserver.getReasons());

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.ON);
        verify(mockManipulations).setMetricStatus(METRIC_HANDLE, MetricCategory.MSRMT, ComponentActivation.FAIL);
    }

    @Test
    @DisplayName(
            "MetricStatusManipulationMSRMTActivationStateFAIL: The precondition does not fail if setComponentActivation is not supported by all metrics.")
    void testMetricStatusManipulationMSRMTActivationStateFAILAllowNotSupported1() {
        metricMockSetup(
                MetricCategory.MSRMT, METRIC_HANDLE, SOME_HANDLE, ComponentActivation.ON, ComponentActivation.FAIL);

        // let one metric not support setComponentActivation manipulation
        when(mockManipulations.setComponentActivation(eq(SOME_HANDLE), any(ComponentActivation.class)))
                .thenReturn(ResponseTypes.Result.RESULT_NOT_SUPPORTED);

        when(mockDevice.getMdibAccess().findEntitiesByType(AbstractMetricDescriptor.class))
                .thenReturn(List.of(mockEntity2, mockEntity));

        assertTrue(ManipulationPreconditions.MetricStatusManipulationMSRMTActivationStateFAIL.manipulation(injector));

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.ON);
        verify(mockManipulations).setComponentActivation(SOME_HANDLE, ComponentActivation.ON);
        verify(mockManipulations).setMetricStatus(METRIC_HANDLE, MetricCategory.MSRMT, ComponentActivation.FAIL);
    }

    @Test
    @DisplayName(
            "MetricStatusManipulationMSRMTActivationStateFAIL: The precondition does not fail if setMetricStatus is not supported by all metrics.")
    void testMetricStatusManipulationMSRMTActivationStateFAILAllowNotSupported2() {
        metricMockSetup(
                MetricCategory.MSRMT, METRIC_HANDLE, SOME_HANDLE, ComponentActivation.ON, ComponentActivation.FAIL);

        // let one metric not support setMetricStatus manipulation
        when(mockManipulations.setMetricStatus(
                        eq(SOME_HANDLE), any(MetricCategory.class), any(ComponentActivation.class)))
                .thenReturn(ResponseTypes.Result.RESULT_NOT_SUPPORTED);

        when(mockDevice.getMdibAccess().findEntitiesByType(AbstractMetricDescriptor.class))
                .thenReturn(List.of(mockEntity2, mockEntity));

        assertTrue(ManipulationPreconditions.MetricStatusManipulationMSRMTActivationStateFAIL.manipulation(injector));

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.ON);
        verify(mockManipulations).setComponentActivation(SOME_HANDLE, ComponentActivation.ON);
        verify(mockManipulations).setMetricStatus(METRIC_HANDLE, MetricCategory.MSRMT, ComponentActivation.FAIL);
    }

    @Test
    @DisplayName("testMetricStatusManipulationMSRMTActivationStateFAIL: setComponentActivation failed.")
    void testMetricStatusManipulationMSRMTActivationStateFAILBadFirstManipulationFailed() {
        setMetricStatusSetup(MetricCategory.MSRMT, METRIC_HANDLE, ComponentActivation.ON, ComponentActivation.FAIL);

        // let setComponentActivation fail
        when(mockManipulations.setComponentActivation(any(String.class), any(ComponentActivation.class)))
                .thenReturn(ResponseTypes.Result.RESULT_FAIL);

        assertFalse(ManipulationPreconditions.MetricStatusManipulationMSRMTActivationStateFAIL.manipulation(injector));

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.ON);
    }

    @Test
    @DisplayName("testMetricStatusManipulationMSRMTActivationStateFAIL: setMetricStatus failed.")
    void testMetricStatusManipulationMSRMTActivationStateFAILBadSecondManipulationFailed() {
        setMetricStatusSetup(MetricCategory.MSRMT, METRIC_HANDLE, ComponentActivation.ON, ComponentActivation.FAIL);

        // let setMetricStatus fail
        when(mockManipulations.setMetricStatus(
                        any(String.class), any(MetricCategory.class), any(ComponentActivation.class)))
                .thenReturn(ResponseTypes.Result.RESULT_FAIL);

        assertFalse(ManipulationPreconditions.MetricStatusManipulationMSRMTActivationStateFAIL.manipulation(injector));

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.ON);
        verify(mockManipulations).setMetricStatus(METRIC_HANDLE, MetricCategory.MSRMT, ComponentActivation.FAIL);
    }

    @Test
    @DisplayName("MetricStatusManipulationSETActivationStateON: Setting a metric with category SET to `setting is"
            + " currently being applied` results in activation state ON.")
    void testMetricStatusManipulationSETActivationStateONGood() {
        setMetricStatusSetup(MetricCategory.SET, METRIC_HANDLE, ComponentActivation.OFF, ComponentActivation.ON);

        assertTrue(ManipulationPreconditions.MetricStatusManipulationSETActivationStateON.manipulation(injector));

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.OFF);
        verify(mockManipulations).setMetricStatus(METRIC_HANDLE, MetricCategory.SET, ComponentActivation.ON);
    }

    @Test
    @DisplayName(
            "MetricStatusManipulationSETActivationStateON: The precondition does not fail if setComponentActivation is not supported by all metrics.")
    void testMetricStatusManipulationSETActivationStateONAllowNotSupported1() {
        metricMockSetup(
                MetricCategory.SET, METRIC_HANDLE, SOME_HANDLE, ComponentActivation.OFF, ComponentActivation.ON);

        // let one metric not support setComponentActivation manipulation
        when(mockManipulations.setComponentActivation(eq(SOME_HANDLE), any(ComponentActivation.class)))
                .thenReturn(ResponseTypes.Result.RESULT_NOT_SUPPORTED);

        when(mockDevice.getMdibAccess().findEntitiesByType(AbstractMetricDescriptor.class))
                .thenReturn(List.of(mockEntity2, mockEntity));

        assertTrue(ManipulationPreconditions.MetricStatusManipulationSETActivationStateON.manipulation(injector));

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.OFF);
        verify(mockManipulations).setComponentActivation(SOME_HANDLE, ComponentActivation.OFF);
        verify(mockManipulations).setMetricStatus(METRIC_HANDLE, MetricCategory.SET, ComponentActivation.ON);
    }

    @Test
    @DisplayName(
            "MetricStatusManipulationSETActivationStateON: The precondition does not fail if setMetricStatus is not supported by all metrics.")
    void testMetricStatusManipulationSETActivationStateONAllowNotSupported2() {
        metricMockSetup(
                MetricCategory.SET, METRIC_HANDLE, SOME_HANDLE, ComponentActivation.OFF, ComponentActivation.ON);

        // let one metric not support setMetricStatus manipulation
        when(mockManipulations.setMetricStatus(
                        eq(SOME_HANDLE), any(MetricCategory.class), any(ComponentActivation.class)))
                .thenReturn(ResponseTypes.Result.RESULT_NOT_SUPPORTED);

        when(mockDevice.getMdibAccess().findEntitiesByType(AbstractMetricDescriptor.class))
                .thenReturn(List.of(mockEntity2, mockEntity));

        assertTrue(ManipulationPreconditions.MetricStatusManipulationSETActivationStateON.manipulation(injector));

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.OFF);
        verify(mockManipulations).setComponentActivation(SOME_HANDLE, ComponentActivation.OFF);
        verify(mockManipulations).setMetricStatus(METRIC_HANDLE, MetricCategory.SET, ComponentActivation.ON);
    }

    @Test
    @DisplayName("MetricStatusManipulationSETActivationStateON: setComponentActivation failed.")
    void testMetricStatusManipulationSETActivationStateONBadFirstManipulationFailed() {
        setMetricStatusSetup(MetricCategory.SET, METRIC_HANDLE, ComponentActivation.OFF, ComponentActivation.ON);

        // let setComponentActivation fail
        when(mockManipulations.setComponentActivation(any(String.class), any(ComponentActivation.class)))
                .thenReturn(ResponseTypes.Result.RESULT_FAIL);

        assertFalse(ManipulationPreconditions.MetricStatusManipulationSETActivationStateON.manipulation(injector));

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.OFF);
    }

    @Test
    @DisplayName("MetricStatusManipulationSETActivationStateON: setMetricStatus failed.")
    void testMetricStatusManipulationSETActivationStateONBadSecondManipulationFailed() {
        setMetricStatusSetup(MetricCategory.SET, METRIC_HANDLE, ComponentActivation.OFF, ComponentActivation.ON);

        // let setMetricStatus fail
        when(mockManipulations.setMetricStatus(
                        any(String.class), any(MetricCategory.class), any(ComponentActivation.class)))
                .thenReturn(ResponseTypes.Result.RESULT_FAIL);

        assertFalse(ManipulationPreconditions.MetricStatusManipulationSETActivationStateON.manipulation(injector));

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.OFF);
        verify(mockManipulations).setMetricStatus(METRIC_HANDLE, MetricCategory.SET, ComponentActivation.ON);
    }

    @Test
    @DisplayName("testMetricStatusManipulationSETActivationStateNOTRDYGood: Set ActivationState "
            + "of all SET-Metrics to NOTRDY.")
    void testMetricStatusManipulationSETActivationStateNOTRDYGood() {

        // given

        final ComponentActivation startActivationState = ComponentActivation.OFF;
        final MetricCategory metricCategory = MetricCategory.SET;
        final ComponentActivation activationState = ComponentActivation.NOT_RDY;
        final String metricStateHandle = "metricStateHandle";

        setupMetricStatusManipulation(metricCategory, metricStateHandle);

        when(mockManipulations.setComponentActivation(metricStateHandle, startActivationState))
                .thenReturn(ResponseTypes.Result.RESULT_SUCCESS);
        when(mockManipulations.setMetricStatus(metricStateHandle, metricCategory, activationState))
                .thenReturn(ResponseTypes.Result.RESULT_SUCCESS);

        // when

        final boolean result =
                ManipulationPreconditions.MetricStatusManipulationSETActivationStateNOTRDY.manipulation(injector);

        // then

        assertTrue(result);
        assertFalse(
                testRunObserver.isInvalid(),
                "Test run should not have been invalidated. Reason(s): " + testRunObserver.getReasons());
        verify(mockManipulations).setComponentActivation(metricStateHandle, startActivationState);
        verify(mockManipulations).setMetricStatus(metricStateHandle, metricCategory, activationState);
    }

    @Test
    @DisplayName(
            "MetricStatusManipulationSETActivationStateNOTRDY: The precondition does not fail if setComponentActivation is not supported by all metrics.")
    void testMetricStatusManipulationSETActivationStateNOTRDYAllowNotSupported1() {
        metricMockSetup(
                MetricCategory.SET, METRIC_HANDLE, SOME_HANDLE, ComponentActivation.OFF, ComponentActivation.NOT_RDY);

        // let one metric not support setComponentActivation manipulation
        when(mockManipulations.setComponentActivation(eq(SOME_HANDLE), any(ComponentActivation.class)))
                .thenReturn(ResponseTypes.Result.RESULT_NOT_SUPPORTED);

        when(mockDevice.getMdibAccess().findEntitiesByType(AbstractMetricDescriptor.class))
                .thenReturn(List.of(mockEntity2, mockEntity));

        assertTrue(ManipulationPreconditions.MetricStatusManipulationSETActivationStateNOTRDY.manipulation(injector));

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.OFF);
        verify(mockManipulations).setComponentActivation(SOME_HANDLE, ComponentActivation.OFF);
        verify(mockManipulations).setMetricStatus(METRIC_HANDLE, MetricCategory.SET, ComponentActivation.NOT_RDY);
    }

    @Test
    @DisplayName(
            "MetricStatusManipulationSETActivationStateNOTRDY: The precondition does not fail if setMetricStatus is not supported by all metrics.")
    void testMetricStatusManipulationSETActivationStateNOTRDYAllowNotSupported2() {
        metricMockSetup(
                MetricCategory.SET, METRIC_HANDLE, SOME_HANDLE, ComponentActivation.OFF, ComponentActivation.NOT_RDY);

        // let one metric not support setMetricStatus manipulation
        when(mockManipulations.setMetricStatus(
                        eq(SOME_HANDLE), any(MetricCategory.class), any(ComponentActivation.class)))
                .thenReturn(ResponseTypes.Result.RESULT_NOT_SUPPORTED);

        when(mockDevice.getMdibAccess().findEntitiesByType(AbstractMetricDescriptor.class))
                .thenReturn(List.of(mockEntity2, mockEntity));

        assertTrue(ManipulationPreconditions.MetricStatusManipulationSETActivationStateNOTRDY.manipulation(injector));

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.OFF);
        verify(mockManipulations).setComponentActivation(SOME_HANDLE, ComponentActivation.OFF);
        verify(mockManipulations).setMetricStatus(METRIC_HANDLE, MetricCategory.SET, ComponentActivation.NOT_RDY);
    }

    @Test
    @DisplayName("testMetricStatusManipulationSETActivationStateNOTRDYBad: First Manipulation failed.")
    void testMetricStatusManipulationSETActivationStateNOTRDYBadFirstManipulationFailed() {

        // given

        final ComponentActivation startActivationState = ComponentActivation.OFF;
        final MetricCategory metricCategory = MetricCategory.SET;
        final ComponentActivation activationState = ComponentActivation.NOT_RDY;
        final String metricStateHandle = "metricStateHandle";

        setupMetricStatusManipulation(metricCategory, metricStateHandle);

        when(mockManipulations.setComponentActivation(metricStateHandle, startActivationState))
                .thenReturn(ResponseTypes.Result.RESULT_FAIL);
        when(mockManipulations.setMetricStatus(metricStateHandle, metricCategory, activationState))
                .thenReturn(ResponseTypes.Result.RESULT_SUCCESS);

        // when

        assertFalse(testRunObserver.isInvalid());
        final boolean result =
                ManipulationPreconditions.MetricStatusManipulationSETActivationStateNOTRDY.manipulation(injector);

        // then
        assertFalse(result);
        verify(mockManipulations).setComponentActivation(metricStateHandle, startActivationState);
    }

    @Test
    @DisplayName("testMetricStatusManipulationSETActivationStateNOTRDYBad: Second Manipulation Failed.")
    void testMetricStatusManipulationSETActivationStateNOTRDYBadSecondManipulationFailed() {

        // given
        final ComponentActivation startActivationState = ComponentActivation.OFF;
        final MetricCategory metricCategory = MetricCategory.SET;
        final ComponentActivation activationState = ComponentActivation.NOT_RDY;
        final String metricStateHandle = "metricStateHandle";

        setupMetricStatusManipulation(metricCategory, metricStateHandle);

        when(mockManipulations.setComponentActivation(metricStateHandle, startActivationState))
                .thenReturn(ResponseTypes.Result.RESULT_SUCCESS);
        when(mockManipulations.setMetricStatus(metricStateHandle, metricCategory, activationState))
                .thenReturn(ResponseTypes.Result.RESULT_FAIL);

        // when
        assertFalse(testRunObserver.isInvalid());
        final boolean result =
                ManipulationPreconditions.MetricStatusManipulationSETActivationStateNOTRDY.manipulation(injector);

        // then
        assertFalse(result);
        verify(mockManipulations).setComponentActivation(metricStateHandle, startActivationState);
        verify(mockManipulations).setMetricStatus(metricStateHandle, metricCategory, activationState);
    }

    @Test
    @DisplayName("MetricStatusManipulationSETActivationStateSTNDBY: Set metric with category MSRMT to 'setting"
            + " initialized, but is not being performed' which results in activation state STND_BY.")
    void testMetricStatusManipulationSETActivationStateSTNDBYGood() {
        setMetricStatusSetup(MetricCategory.SET, METRIC_HANDLE, ComponentActivation.ON, ComponentActivation.STND_BY);

        assertTrue(ManipulationPreconditions.MetricStatusManipulationSETActivationStateSTNDBY.manipulation(injector));

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.ON);
        verify(mockManipulations).setMetricStatus(METRIC_HANDLE, MetricCategory.SET, ComponentActivation.STND_BY);
    }

    @Test
    @DisplayName(
            "MetricStatusManipulationSETActivationStateSTNDBY: The precondition does not fail if setComponentActivation is not supported by all metrics.")
    void testMetricStatusManipulationSETActivationStateSTNDBYAllowNotSupported1() {
        metricMockSetup(
                MetricCategory.SET, METRIC_HANDLE, SOME_HANDLE, ComponentActivation.ON, ComponentActivation.STND_BY);

        // let one metric not support setComponentActivation manipulation
        when(mockManipulations.setComponentActivation(eq(SOME_HANDLE), any(ComponentActivation.class)))
                .thenReturn(ResponseTypes.Result.RESULT_NOT_SUPPORTED);

        when(mockDevice.getMdibAccess().findEntitiesByType(AbstractMetricDescriptor.class))
                .thenReturn(List.of(mockEntity2, mockEntity));

        assertTrue(ManipulationPreconditions.MetricStatusManipulationSETActivationStateSTNDBY.manipulation(injector));

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.ON);
        verify(mockManipulations).setComponentActivation(SOME_HANDLE, ComponentActivation.ON);
        verify(mockManipulations).setMetricStatus(METRIC_HANDLE, MetricCategory.SET, ComponentActivation.STND_BY);
    }

    @Test
    @DisplayName(
            "MetricStatusManipulationSETActivationStateSTNDBY: The precondition does not fail if setMetricStatus is not supported by all metrics.")
    void testMetricStatusManipulationSETActivationStateSTNDBYAllowNotSupported2() {
        metricMockSetup(
                MetricCategory.SET, METRIC_HANDLE, SOME_HANDLE, ComponentActivation.ON, ComponentActivation.STND_BY);

        // let one metric not support setMetricStatus manipulation
        when(mockManipulations.setMetricStatus(
                        eq(SOME_HANDLE), any(MetricCategory.class), any(ComponentActivation.class)))
                .thenReturn(ResponseTypes.Result.RESULT_NOT_SUPPORTED);

        when(mockDevice.getMdibAccess().findEntitiesByType(AbstractMetricDescriptor.class))
                .thenReturn(List.of(mockEntity2, mockEntity));

        assertTrue(ManipulationPreconditions.MetricStatusManipulationSETActivationStateSTNDBY.manipulation(injector));

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.ON);
        verify(mockManipulations).setComponentActivation(SOME_HANDLE, ComponentActivation.ON);
        verify(mockManipulations).setMetricStatus(METRIC_HANDLE, MetricCategory.SET, ComponentActivation.STND_BY);
    }

    @Test
    @DisplayName("MetricStatusManipulationSETActivationStateSTNDBY: setComponentActivation failed.")
    void testMetricStatusManipulationSETActivationStateSTNDBYBadFirstManipulationFailed() {
        setMetricStatusSetup(MetricCategory.SET, METRIC_HANDLE, ComponentActivation.ON, ComponentActivation.STND_BY);

        // let setComponentActivation fail
        when(mockManipulations.setComponentActivation(any(String.class), any(ComponentActivation.class)))
                .thenReturn(ResponseTypes.Result.RESULT_FAIL);

        assertFalse(ManipulationPreconditions.MetricStatusManipulationSETActivationStateSTNDBY.manipulation(injector));

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.ON);
    }

    @Test
    @DisplayName("MetricStatusManipulationSETActivationStateSTNDBY: setMetricStatus failed.")
    void testMetricStatusManipulationSETActivationStateSTNDBYBadSecondManipulationFailed() {
        setMetricStatusSetup(MetricCategory.SET, METRIC_HANDLE, ComponentActivation.ON, ComponentActivation.STND_BY);

        // let setMetricStatus fail
        when(mockManipulations.setMetricStatus(
                        any(String.class), any(MetricCategory.class), any(ComponentActivation.class)))
                .thenReturn(ResponseTypes.Result.RESULT_FAIL);

        assertFalse(ManipulationPreconditions.MetricStatusManipulationSETActivationStateSTNDBY.manipulation(injector));

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.ON);
        verify(mockManipulations).setMetricStatus(METRIC_HANDLE, MetricCategory.SET, ComponentActivation.STND_BY);
    }

    @Test
    @DisplayName("testMetricStatusManipulationSETActivationStateSHTDN: Set SET metrics to a state where the setting is"
            + " currently de-initializing to trigger the setting of the activation state to SHTDN")
    void testMetricStatusManipulationSETActivationStateSHTDNGood() {
        setMetricStatusSetup(MetricCategory.SET, METRIC_HANDLE, ComponentActivation.ON, ComponentActivation.SHTDN);

        assertTrue(ManipulationPreconditions.MetricStatusManipulationSETActivationStateSHTDN.manipulation(injector));

        assertFalse(
                testRunObserver.isInvalid(),
                "Test run should not have been invalidated. Reason(s): " + testRunObserver.getReasons());

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.ON);
        verify(mockManipulations).setMetricStatus(METRIC_HANDLE, MetricCategory.SET, ComponentActivation.SHTDN);
    }

    @Test
    @DisplayName(
            "MetricStatusManipulationSETActivationStateSHTDN: The precondition does not fail if setComponentActivation is not supported by all metrics.")
    void testMetricStatusManipulationSETActivationStateSHTDNAllowNotSupported1() {
        metricMockSetup(
                MetricCategory.SET, METRIC_HANDLE, SOME_HANDLE, ComponentActivation.ON, ComponentActivation.SHTDN);

        // let one metric not support setComponentActivation manipulation
        when(mockManipulations.setComponentActivation(eq(SOME_HANDLE), any(ComponentActivation.class)))
                .thenReturn(ResponseTypes.Result.RESULT_NOT_SUPPORTED);

        when(mockDevice.getMdibAccess().findEntitiesByType(AbstractMetricDescriptor.class))
                .thenReturn(List.of(mockEntity2, mockEntity));

        assertTrue(ManipulationPreconditions.MetricStatusManipulationSETActivationStateSHTDN.manipulation(injector));

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.ON);
        verify(mockManipulations).setComponentActivation(SOME_HANDLE, ComponentActivation.ON);
        verify(mockManipulations).setMetricStatus(METRIC_HANDLE, MetricCategory.SET, ComponentActivation.SHTDN);
    }

    @Test
    @DisplayName(
            "MetricStatusManipulationSETActivationStateSHTDN: The precondition does not fail if setMetricStatus is not supported by all metrics.")
    void testMetricStatusManipulationSETActivationStateSHTDNAllowNotSupported2() {
        metricMockSetup(
                MetricCategory.SET, METRIC_HANDLE, SOME_HANDLE, ComponentActivation.ON, ComponentActivation.SHTDN);

        // let one metric not support setMetricStatus manipulation
        when(mockManipulations.setMetricStatus(
                        eq(SOME_HANDLE), any(MetricCategory.class), any(ComponentActivation.class)))
                .thenReturn(ResponseTypes.Result.RESULT_NOT_SUPPORTED);

        when(mockDevice.getMdibAccess().findEntitiesByType(AbstractMetricDescriptor.class))
                .thenReturn(List.of(mockEntity2, mockEntity));

        assertTrue(ManipulationPreconditions.MetricStatusManipulationSETActivationStateSHTDN.manipulation(injector));

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.ON);
        verify(mockManipulations).setComponentActivation(SOME_HANDLE, ComponentActivation.ON);
        verify(mockManipulations).setMetricStatus(METRIC_HANDLE, MetricCategory.SET, ComponentActivation.SHTDN);
    }

    @Test
    @DisplayName("testMetricStatusManipulationSETActivationStateSHTDN: setComponentActivation failed.")
    void testMetricStatusManipulationSETActivationStateSHTDNBadFirstManipulationFailed() {
        setMetricStatusSetup(MetricCategory.SET, METRIC_HANDLE, ComponentActivation.ON, ComponentActivation.SHTDN);

        // let setComponentActivation fail
        when(mockManipulations.setComponentActivation(any(String.class), any(ComponentActivation.class)))
                .thenReturn(ResponseTypes.Result.RESULT_FAIL);

        assertFalse(ManipulationPreconditions.MetricStatusManipulationSETActivationStateSHTDN.manipulation(injector));

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.ON);
    }

    @Test
    @DisplayName("testMetricStatusManipulationSETActivationStateSHTDN: setMetricStatus failed.")
    void testMetricStatusManipulationSETActivationStateSHTDNGoodSecondManipulationFailed() {
        setMetricStatusSetup(MetricCategory.SET, METRIC_HANDLE, ComponentActivation.ON, ComponentActivation.SHTDN);

        // let setMetricStatus fail
        when(mockManipulations.setMetricStatus(
                        any(String.class), any(MetricCategory.class), any(ComponentActivation.class)))
                .thenReturn(ResponseTypes.Result.RESULT_FAIL);

        assertFalse(ManipulationPreconditions.MetricStatusManipulationSETActivationStateSHTDN.manipulation(injector));

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.ON);
        verify(mockManipulations).setMetricStatus(METRIC_HANDLE, MetricCategory.SET, ComponentActivation.SHTDN);
    }

    @Test
    @DisplayName("testMetricStatusManipulationSETActivationStateOFF: Set SET metrics to a state where the setting"
            + " not being performed and is de-initialized to trigger the setting of the activation state to OFF")
    void testMetricStatusManipulationSETActivationStateOFFGood() {
        setMetricStatusSetup(MetricCategory.SET, METRIC_HANDLE, ComponentActivation.ON, ComponentActivation.OFF);

        assertTrue(ManipulationPreconditions.MetricStatusManipulationSETActivationStateOFF.manipulation(injector));

        assertFalse(
                testRunObserver.isInvalid(),
                "Test run should not have been invalidated. Reason(s): " + testRunObserver.getReasons());

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.ON);
        verify(mockManipulations).setMetricStatus(METRIC_HANDLE, MetricCategory.SET, ComponentActivation.OFF);
    }

    @Test
    @DisplayName(
            "MetricStatusManipulationSETActivationStateOFF: The precondition does not fail if setComponentActivation is not supported by all metrics.")
    void testMetricStatusManipulationSETActivationStateOFFAllowNotSupported1() {
        metricMockSetup(
                MetricCategory.SET, METRIC_HANDLE, SOME_HANDLE, ComponentActivation.ON, ComponentActivation.OFF);

        // let one metric not support setComponentActivation manipulation
        when(mockManipulations.setComponentActivation(eq(SOME_HANDLE), any(ComponentActivation.class)))
                .thenReturn(ResponseTypes.Result.RESULT_NOT_SUPPORTED);

        when(mockDevice.getMdibAccess().findEntitiesByType(AbstractMetricDescriptor.class))
                .thenReturn(List.of(mockEntity2, mockEntity));

        assertTrue(ManipulationPreconditions.MetricStatusManipulationSETActivationStateOFF.manipulation(injector));

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.ON);
        verify(mockManipulations).setComponentActivation(SOME_HANDLE, ComponentActivation.ON);
        verify(mockManipulations).setMetricStatus(METRIC_HANDLE, MetricCategory.SET, ComponentActivation.OFF);
    }

    @Test
    @DisplayName(
            "MetricStatusManipulationSETActivationStateOFF: The precondition does not fail if setMetricStatus is not supported by all metrics.")
    void testMetricStatusManipulationSETActivationStateOFFAllowNotSupported2() {
        metricMockSetup(
                MetricCategory.SET, METRIC_HANDLE, SOME_HANDLE, ComponentActivation.ON, ComponentActivation.OFF);

        // let one metric not support setMetricStatus manipulation
        when(mockManipulations.setMetricStatus(
                        eq(SOME_HANDLE), any(MetricCategory.class), any(ComponentActivation.class)))
                .thenReturn(ResponseTypes.Result.RESULT_NOT_SUPPORTED);

        when(mockDevice.getMdibAccess().findEntitiesByType(AbstractMetricDescriptor.class))
                .thenReturn(List.of(mockEntity2, mockEntity));

        assertTrue(ManipulationPreconditions.MetricStatusManipulationSETActivationStateOFF.manipulation(injector));

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.ON);
        verify(mockManipulations).setComponentActivation(SOME_HANDLE, ComponentActivation.ON);
        verify(mockManipulations).setMetricStatus(METRIC_HANDLE, MetricCategory.SET, ComponentActivation.OFF);
    }

    @Test
    @DisplayName("testMetricStatusManipulationSETActivationStateOFF: setComponentActivation failed.")
    void testMetricStatusManipulationSETActivationStateOFFBadFirstManipulationFailed() {
        setMetricStatusSetup(MetricCategory.SET, METRIC_HANDLE, ComponentActivation.ON, ComponentActivation.OFF);

        // let setComponentActivation fail
        when(mockManipulations.setComponentActivation(any(String.class), any(ComponentActivation.class)))
                .thenReturn(ResponseTypes.Result.RESULT_FAIL);

        assertFalse(ManipulationPreconditions.MetricStatusManipulationSETActivationStateOFF.manipulation(injector));

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.ON);
    }

    @Test
    @DisplayName("testMetricStatusManipulationSETActivationStateOFF: setMetricStatus failed.")
    void testMetricStatusManipulationSETActivationStateOFFBadSecondManipulationFailed() {
        setMetricStatusSetup(MetricCategory.SET, METRIC_HANDLE, ComponentActivation.ON, ComponentActivation.OFF);

        // let setMetricStatus fail
        when(mockManipulations.setMetricStatus(
                        any(String.class), any(MetricCategory.class), any(ComponentActivation.class)))
                .thenReturn(ResponseTypes.Result.RESULT_FAIL);

        assertFalse(ManipulationPreconditions.MetricStatusManipulationSETActivationStateOFF.manipulation(injector));

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.ON);
        verify(mockManipulations).setMetricStatus(METRIC_HANDLE, MetricCategory.SET, ComponentActivation.OFF);
    }

    @Test
    @DisplayName("MetricStatusManipulationCLCActivationStateON: Setting a metric with category CLC to `calculation is"
            + " being performed` results in activation state ON.")
    void testMetricStatusManipulationCLCActivationStateONActivationStateONGood() {
        setMetricStatusSetup(MetricCategory.CLC, METRIC_HANDLE, ComponentActivation.OFF, ComponentActivation.ON);

        assertTrue(ManipulationPreconditions.MetricStatusManipulationCLCActivationStateON.manipulation(injector));

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.OFF);
        verify(mockManipulations).setMetricStatus(METRIC_HANDLE, MetricCategory.CLC, ComponentActivation.ON);
    }

    @Test
    @DisplayName(
            "MetricStatusManipulationCLCActivationStateON: The precondition does not fail if setComponentActivation is not supported by all metrics.")
    void testMetricStatusManipulationCLCActivationStateONAllowNotSupported1() {
        metricMockSetup(
                MetricCategory.CLC, METRIC_HANDLE, SOME_HANDLE, ComponentActivation.OFF, ComponentActivation.ON);

        // let one metric not support setComponentActivation manipulation
        when(mockManipulations.setComponentActivation(eq(SOME_HANDLE), any(ComponentActivation.class)))
                .thenReturn(ResponseTypes.Result.RESULT_NOT_SUPPORTED);

        when(mockDevice.getMdibAccess().findEntitiesByType(AbstractMetricDescriptor.class))
                .thenReturn(List.of(mockEntity2, mockEntity));

        assertTrue(ManipulationPreconditions.MetricStatusManipulationCLCActivationStateON.manipulation(injector));

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.OFF);
        verify(mockManipulations).setComponentActivation(SOME_HANDLE, ComponentActivation.OFF);
        verify(mockManipulations).setMetricStatus(METRIC_HANDLE, MetricCategory.CLC, ComponentActivation.ON);
    }

    @Test
    @DisplayName(
            "MetricStatusManipulationCLCActivationStateON: The precondition does not fail if setMetricStatus is not supported by all metrics.")
    void testMetricStatusManipulationCLCActivationStateONAllowNotSupported2() {
        metricMockSetup(
                MetricCategory.CLC, METRIC_HANDLE, SOME_HANDLE, ComponentActivation.OFF, ComponentActivation.ON);

        // let one metric not support setMetricStatus manipulation
        when(mockManipulations.setMetricStatus(
                        eq(SOME_HANDLE), any(MetricCategory.class), any(ComponentActivation.class)))
                .thenReturn(ResponseTypes.Result.RESULT_NOT_SUPPORTED);

        when(mockDevice.getMdibAccess().findEntitiesByType(AbstractMetricDescriptor.class))
                .thenReturn(List.of(mockEntity2, mockEntity));

        assertTrue(ManipulationPreconditions.MetricStatusManipulationCLCActivationStateON.manipulation(injector));

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.OFF);
        verify(mockManipulations).setComponentActivation(SOME_HANDLE, ComponentActivation.OFF);
        verify(mockManipulations).setMetricStatus(METRIC_HANDLE, MetricCategory.CLC, ComponentActivation.ON);
    }

    @Test
    @DisplayName("MetricStatusManipulationCLCActivationStateON: setComponentActivation failed.")
    void testMetricStatusManipulationCLCActivationStateONBadFirstManipulationFailed() {
        setMetricStatusSetup(MetricCategory.CLC, METRIC_HANDLE, ComponentActivation.OFF, ComponentActivation.ON);

        // let setComponentActivation fail
        when(mockManipulations.setComponentActivation(any(String.class), any(ComponentActivation.class)))
                .thenReturn(ResponseTypes.Result.RESULT_FAIL);

        assertFalse(ManipulationPreconditions.MetricStatusManipulationCLCActivationStateON.manipulation(injector));

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.OFF);
    }

    @Test
    @DisplayName("MetricStatusManipulationCLCActivationStateON: setMetricStatus failed.")
    void testMetricStatusManipulationCLCActivationStateONBadSecondManipulationFailed() {
        setMetricStatusSetup(MetricCategory.CLC, METRIC_HANDLE, ComponentActivation.OFF, ComponentActivation.ON);

        // let setMetricStatus fail
        when(mockManipulations.setMetricStatus(
                        any(String.class), any(MetricCategory.class), any(ComponentActivation.class)))
                .thenReturn(ResponseTypes.Result.RESULT_FAIL);

        assertFalse(ManipulationPreconditions.MetricStatusManipulationCLCActivationStateON.manipulation(injector));

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.OFF);
        verify(mockManipulations).setMetricStatus(METRIC_HANDLE, MetricCategory.CLC, ComponentActivation.ON);
    }

    @Test
    @DisplayName("testMetricStatusManipulationCLCActivationStateNOTRDY: Set calculations of metrics with category CLC"
            + " to currently initializing to trigger an ActivationState change to NotRdy.")
    void testMetricStatusManipulationCLCActivationStateNOTRDYGood() {
        setMetricStatusSetup(MetricCategory.CLC, METRIC_HANDLE, ComponentActivation.OFF, ComponentActivation.NOT_RDY);

        assertTrue(ManipulationPreconditions.MetricStatusManipulationCLCActivationStateNOTRDY.manipulation(injector));

        assertFalse(
                testRunObserver.isInvalid(),
                "Test run should not have been invalidated. Reason(s): " + testRunObserver.getReasons());

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.OFF);
        verify(mockManipulations).setMetricStatus(METRIC_HANDLE, MetricCategory.CLC, ComponentActivation.NOT_RDY);
    }

    @Test
    @DisplayName(
            "MetricStatusManipulationCLCActivationStateNOTRDY: The precondition does not fail if setComponentActivation is not supported by all metrics.")
    void testMetricStatusManipulationCLCActivationStateNOTRDYAllowNotSupported1() {
        metricMockSetup(
                MetricCategory.CLC, METRIC_HANDLE, SOME_HANDLE, ComponentActivation.OFF, ComponentActivation.NOT_RDY);

        // let one metric not support setComponentActivation manipulation
        when(mockManipulations.setComponentActivation(eq(SOME_HANDLE), any(ComponentActivation.class)))
                .thenReturn(ResponseTypes.Result.RESULT_NOT_SUPPORTED);

        when(mockDevice.getMdibAccess().findEntitiesByType(AbstractMetricDescriptor.class))
                .thenReturn(List.of(mockEntity2, mockEntity));

        assertTrue(ManipulationPreconditions.MetricStatusManipulationCLCActivationStateNOTRDY.manipulation(injector));

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.OFF);
        verify(mockManipulations).setComponentActivation(SOME_HANDLE, ComponentActivation.OFF);
        verify(mockManipulations).setMetricStatus(METRIC_HANDLE, MetricCategory.CLC, ComponentActivation.NOT_RDY);
    }

    @Test
    @DisplayName(
            "MetricStatusManipulationCLCActivationStateNOTRDY: The precondition does not fail if setMetricStatus is not supported by all metrics.")
    void testMetricStatusManipulationCLCActivationStateNOTRDYAllowNotSupported2() {
        metricMockSetup(
                MetricCategory.CLC, METRIC_HANDLE, SOME_HANDLE, ComponentActivation.OFF, ComponentActivation.NOT_RDY);

        // let one metric not support setMetricStatus manipulation
        when(mockManipulations.setMetricStatus(
                        eq(SOME_HANDLE), any(MetricCategory.class), any(ComponentActivation.class)))
                .thenReturn(ResponseTypes.Result.RESULT_NOT_SUPPORTED);

        when(mockDevice.getMdibAccess().findEntitiesByType(AbstractMetricDescriptor.class))
                .thenReturn(List.of(mockEntity2, mockEntity));

        assertTrue(ManipulationPreconditions.MetricStatusManipulationCLCActivationStateNOTRDY.manipulation(injector));

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.OFF);
        verify(mockManipulations).setComponentActivation(SOME_HANDLE, ComponentActivation.OFF);
        verify(mockManipulations).setMetricStatus(METRIC_HANDLE, MetricCategory.CLC, ComponentActivation.NOT_RDY);
    }

    @Test
    @DisplayName("testMetricStatusManipulationCLCActivationStateNOTRDY: setComponentActivation failed.")
    void testMetricStatusManipulationCLCActivationStateNOTRDYBadFirstManipulationFailed() {
        setMetricStatusSetup(MetricCategory.CLC, METRIC_HANDLE, ComponentActivation.OFF, ComponentActivation.NOT_RDY);

        // let setComponentActivation fail
        when(mockManipulations.setComponentActivation(any(String.class), any(ComponentActivation.class)))
                .thenReturn(ResponseTypes.Result.RESULT_FAIL);

        assertFalse(ManipulationPreconditions.MetricStatusManipulationCLCActivationStateNOTRDY.manipulation(injector));

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.OFF);
    }

    @Test
    @DisplayName("testMetricStatusManipulationCLCActivationStateNOTRDY: setMetricStatus failed.")
    void testMetricStatusManipulationCLCActivationStateNOTRDYBadSecondManipulationFailed() {
        setMetricStatusSetup(MetricCategory.CLC, METRIC_HANDLE, ComponentActivation.OFF, ComponentActivation.NOT_RDY);

        // let setMetricStatus fail
        when(mockManipulations.setMetricStatus(
                        any(String.class), any(MetricCategory.class), any(ComponentActivation.class)))
                .thenReturn(ResponseTypes.Result.RESULT_FAIL);

        assertFalse(ManipulationPreconditions.MetricStatusManipulationCLCActivationStateNOTRDY.manipulation(injector));

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.OFF);
        verify(mockManipulations).setMetricStatus(METRIC_HANDLE, MetricCategory.CLC, ComponentActivation.NOT_RDY);
    }

    @Test
    @DisplayName("MetricStatusManipulationCLCActivationStateSTNDBY: Set metric with category CLC to 'calculation"
            + " initialized, but is not being performed' which results in activation state STND_BY.")
    void testMetricStatusManipulationCLCActivationStateSTNDBYGood() {
        setMetricStatusSetup(MetricCategory.CLC, METRIC_HANDLE, ComponentActivation.ON, ComponentActivation.STND_BY);

        assertTrue(ManipulationPreconditions.MetricStatusManipulationCLCActivationStateSTNDBY.manipulation(injector));

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.ON);
        verify(mockManipulations).setMetricStatus(METRIC_HANDLE, MetricCategory.CLC, ComponentActivation.STND_BY);
    }

    @Test
    @DisplayName(
            "MetricStatusManipulationCLCActivationStateSTNDBY: The precondition does not fail if setComponentActivation is not supported by all metrics.")
    void testMetricStatusManipulationCLCActivationStateSTNDBYAllowNotSupported1() {
        metricMockSetup(
                MetricCategory.CLC, METRIC_HANDLE, SOME_HANDLE, ComponentActivation.ON, ComponentActivation.STND_BY);

        // let one metric not support setComponentActivation manipulation
        when(mockManipulations.setComponentActivation(eq(SOME_HANDLE), any(ComponentActivation.class)))
                .thenReturn(ResponseTypes.Result.RESULT_NOT_SUPPORTED);

        when(mockDevice.getMdibAccess().findEntitiesByType(AbstractMetricDescriptor.class))
                .thenReturn(List.of(mockEntity2, mockEntity));

        assertTrue(ManipulationPreconditions.MetricStatusManipulationCLCActivationStateSTNDBY.manipulation(injector));

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.ON);
        verify(mockManipulations).setComponentActivation(SOME_HANDLE, ComponentActivation.ON);
        verify(mockManipulations).setMetricStatus(METRIC_HANDLE, MetricCategory.CLC, ComponentActivation.STND_BY);
    }

    @Test
    @DisplayName(
            "MetricStatusManipulationCLCActivationStateSTNDBY: The precondition does not fail if setMetricStatus is not supported by all metrics.")
    void testMetricStatusManipulationCLCActivationStateSTNDBYAllowNotSupported2() {
        metricMockSetup(
                MetricCategory.CLC, METRIC_HANDLE, SOME_HANDLE, ComponentActivation.ON, ComponentActivation.STND_BY);

        // let one metric not support setMetricStatus manipulation
        when(mockManipulations.setMetricStatus(
                        eq(SOME_HANDLE), any(MetricCategory.class), any(ComponentActivation.class)))
                .thenReturn(ResponseTypes.Result.RESULT_NOT_SUPPORTED);

        when(mockDevice.getMdibAccess().findEntitiesByType(AbstractMetricDescriptor.class))
                .thenReturn(List.of(mockEntity2, mockEntity));

        assertTrue(ManipulationPreconditions.MetricStatusManipulationCLCActivationStateSTNDBY.manipulation(injector));

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.ON);
        verify(mockManipulations).setComponentActivation(SOME_HANDLE, ComponentActivation.ON);
        verify(mockManipulations).setMetricStatus(METRIC_HANDLE, MetricCategory.CLC, ComponentActivation.STND_BY);
    }

    @Test
    @DisplayName("MetricStatusManipulationCLCActivationStateSTNDBY: setComponentActivation failed.")
    void testMetricStatusManipulationCLCActivationStateSTNDBYBadFirstManipulationFailed() {
        setMetricStatusSetup(MetricCategory.CLC, METRIC_HANDLE, ComponentActivation.ON, ComponentActivation.STND_BY);

        // let setComponentActivation fail
        when(mockManipulations.setComponentActivation(any(String.class), any(ComponentActivation.class)))
                .thenReturn(ResponseTypes.Result.RESULT_FAIL);

        assertFalse(ManipulationPreconditions.MetricStatusManipulationCLCActivationStateSTNDBY.manipulation(injector));

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.ON);
    }

    @Test
    @DisplayName("MetricStatusManipulationCLCActivationStateSTNDBY: setMetricStatus failed.")
    void testMetricStatusManipulationCLCActivationStateSTNDBYBadSecondManipulationFailed() {
        setMetricStatusSetup(MetricCategory.CLC, METRIC_HANDLE, ComponentActivation.ON, ComponentActivation.STND_BY);

        // let setMetricStatus fail
        when(mockManipulations.setMetricStatus(
                        any(String.class), any(MetricCategory.class), any(ComponentActivation.class)))
                .thenReturn(ResponseTypes.Result.RESULT_FAIL);

        assertFalse(ManipulationPreconditions.MetricStatusManipulationCLCActivationStateSTNDBY.manipulation(injector));

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.ON);
        verify(mockManipulations).setMetricStatus(METRIC_HANDLE, MetricCategory.CLC, ComponentActivation.STND_BY);
    }

    @Test
    @DisplayName("testMetricStatusManipulationSETActivationStateFAIL: Set ActivationState of all SET-Metrics to FAIL.")
    void testMetricStatusManipulationSETActivationStateFAILGood() {
        setMetricStatusSetup(MetricCategory.SET, METRIC_HANDLE, ComponentActivation.ON, ComponentActivation.FAIL);

        assertTrue(
                ManipulationPreconditions.MetricStatusManipulationSETActivationStateFAIL.manipulation(injector),
                "The manipulation should have been successful.");

        assertFalse(
                testRunObserver.isInvalid(),
                "Test run should not have been invalidated. Reason(s): " + testRunObserver.getReasons());

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.ON);
        verify(mockManipulations).setMetricStatus(METRIC_HANDLE, MetricCategory.SET, ComponentActivation.FAIL);
    }

    @Test
    @DisplayName(
            "MetricStatusManipulationSETActivationStateFAIL: The precondition does not fail if setComponentActivation is not supported by all metrics.")
    void testMetricStatusManipulationSETActivationStateFAILAllowNotSupported1() {
        metricMockSetup(
                MetricCategory.SET, METRIC_HANDLE, SOME_HANDLE, ComponentActivation.ON, ComponentActivation.FAIL);

        // let one metric not support setComponentActivation manipulation
        when(mockManipulations.setComponentActivation(eq(SOME_HANDLE), any(ComponentActivation.class)))
                .thenReturn(ResponseTypes.Result.RESULT_NOT_SUPPORTED);

        when(mockDevice.getMdibAccess().findEntitiesByType(AbstractMetricDescriptor.class))
                .thenReturn(List.of(mockEntity2, mockEntity));

        assertTrue(ManipulationPreconditions.MetricStatusManipulationSETActivationStateFAIL.manipulation(injector));

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.ON);
        verify(mockManipulations).setComponentActivation(SOME_HANDLE, ComponentActivation.ON);
        verify(mockManipulations).setMetricStatus(METRIC_HANDLE, MetricCategory.SET, ComponentActivation.FAIL);
    }

    @Test
    @DisplayName(
            "MetricStatusManipulationSETActivationStateFAIL: The precondition does not fail if setMetricStatus is not supported by all metrics.")
    void testMetricStatusManipulationSETActivationStateFAILAllowNotSupported2() {
        metricMockSetup(
                MetricCategory.SET, METRIC_HANDLE, SOME_HANDLE, ComponentActivation.ON, ComponentActivation.FAIL);

        // let one metric not support setMetricStatus manipulation
        when(mockManipulations.setMetricStatus(
                        eq(SOME_HANDLE), any(MetricCategory.class), any(ComponentActivation.class)))
                .thenReturn(ResponseTypes.Result.RESULT_NOT_SUPPORTED);

        when(mockDevice.getMdibAccess().findEntitiesByType(AbstractMetricDescriptor.class))
                .thenReturn(List.of(mockEntity2, mockEntity));

        assertTrue(ManipulationPreconditions.MetricStatusManipulationSETActivationStateFAIL.manipulation(injector));

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.ON);
        verify(mockManipulations).setComponentActivation(SOME_HANDLE, ComponentActivation.ON);
        verify(mockManipulations).setMetricStatus(METRIC_HANDLE, MetricCategory.SET, ComponentActivation.FAIL);
    }

    @Test
    @DisplayName("testMetricStatusManipulationSETActivationStateFAIL: setComponentActivation failed.")
    void testMetricStatusManipulationSETActivationStateFAILBadFirstManipulationFailed() {
        setMetricStatusSetup(MetricCategory.SET, METRIC_HANDLE, ComponentActivation.ON, ComponentActivation.FAIL);

        // let setComponentActivation fail
        when(mockManipulations.setComponentActivation(any(String.class), any(ComponentActivation.class)))
                .thenReturn(ResponseTypes.Result.RESULT_FAIL);

        assertFalse(
                ManipulationPreconditions.MetricStatusManipulationSETActivationStateFAIL.manipulation(injector),
                "The manipulation should not have been successful.");

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.ON);
    }

    @Test
    @DisplayName("testMetricStatusManipulationSETActivationStateFAIL: setMetricStatus failed.")
    void testMetricStatusManipulationSETActivationStateFAILBadSecondManipulationFailed() {
        setMetricStatusSetup(MetricCategory.SET, METRIC_HANDLE, ComponentActivation.ON, ComponentActivation.FAIL);

        // let setMetricStatus fail
        when(mockManipulations.setMetricStatus(
                        any(String.class), any(MetricCategory.class), any(ComponentActivation.class)))
                .thenReturn(ResponseTypes.Result.RESULT_FAIL);

        assertFalse(
                ManipulationPreconditions.MetricStatusManipulationSETActivationStateFAIL.manipulation(injector),
                "The manipulation should not have been successful.");

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.ON);
        verify(mockManipulations).setMetricStatus(METRIC_HANDLE, MetricCategory.SET, ComponentActivation.FAIL);
    }

    @Test
    @DisplayName("MetricStatusManipulationCLCActivationStateSHTDN: Set CLC metrics to a state where the calculation"
            + " is currently de-initializing to trigger an activation state change to SHTDN.")
    void testMetricStatusManipulationCLCActivationStateSHTDNGood() {
        setMetricStatusSetup(MetricCategory.CLC, METRIC_HANDLE, ComponentActivation.ON, ComponentActivation.SHTDN);

        assertTrue(ManipulationPreconditions.MetricStatusManipulationCLCActivationStateSHTDN.manipulation(injector));

        assertFalse(
                testRunObserver.isInvalid(),
                "Test run should not have been invalidated. Reason(s): " + testRunObserver.getReasons());

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.ON);
        verify(mockManipulations).setMetricStatus(METRIC_HANDLE, MetricCategory.CLC, ComponentActivation.SHTDN);
    }

    @Test
    @DisplayName(
            "MetricStatusManipulationCLCActivationStateSHTDN: The precondition does not fail if setComponentActivation is not supported by all metrics.")
    void testMetricStatusManipulationCLCActivationStateSHTDNAllowNotSupported1() {
        metricMockSetup(
                MetricCategory.CLC, METRIC_HANDLE, SOME_HANDLE, ComponentActivation.ON, ComponentActivation.SHTDN);

        // let one metric not support setComponentActivation manipulation
        when(mockManipulations.setComponentActivation(eq(SOME_HANDLE), any(ComponentActivation.class)))
                .thenReturn(ResponseTypes.Result.RESULT_NOT_SUPPORTED);

        when(mockDevice.getMdibAccess().findEntitiesByType(AbstractMetricDescriptor.class))
                .thenReturn(List.of(mockEntity2, mockEntity));

        assertTrue(ManipulationPreconditions.MetricStatusManipulationCLCActivationStateSHTDN.manipulation(injector));

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.ON);
        verify(mockManipulations).setComponentActivation(SOME_HANDLE, ComponentActivation.ON);
        verify(mockManipulations).setMetricStatus(METRIC_HANDLE, MetricCategory.CLC, ComponentActivation.SHTDN);
    }

    @Test
    @DisplayName(
            "MetricStatusManipulationCLCActivationStateSHTDN: The precondition does not fail if setMetricStatus is not supported by all metrics.")
    void testMetricStatusManipulationCLCActivationStateSHTDNAllowNotSupported2() {
        metricMockSetup(
                MetricCategory.CLC, METRIC_HANDLE, SOME_HANDLE, ComponentActivation.ON, ComponentActivation.SHTDN);

        // let one metric not support setMetricStatus manipulation
        when(mockManipulations.setMetricStatus(
                        eq(SOME_HANDLE), any(MetricCategory.class), any(ComponentActivation.class)))
                .thenReturn(ResponseTypes.Result.RESULT_NOT_SUPPORTED);

        when(mockDevice.getMdibAccess().findEntitiesByType(AbstractMetricDescriptor.class))
                .thenReturn(List.of(mockEntity2, mockEntity));

        assertTrue(ManipulationPreconditions.MetricStatusManipulationCLCActivationStateSHTDN.manipulation(injector));

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.ON);
        verify(mockManipulations).setComponentActivation(SOME_HANDLE, ComponentActivation.ON);
        verify(mockManipulations).setMetricStatus(METRIC_HANDLE, MetricCategory.CLC, ComponentActivation.SHTDN);
    }

    @Test
    @DisplayName("MetricStatusManipulationCLCActivationStateSHTDN: setComponentActivation failed.")
    void testMetricStatusManipulationCLCActivationStateSHTDNBadFirstManipulationFailed() {
        setMetricStatusSetup(MetricCategory.CLC, METRIC_HANDLE, ComponentActivation.ON, ComponentActivation.SHTDN);

        // let setComponentActivation fail
        when(mockManipulations.setComponentActivation(any(String.class), any(ComponentActivation.class)))
                .thenReturn(ResponseTypes.Result.RESULT_FAIL);

        assertFalse(ManipulationPreconditions.MetricStatusManipulationCLCActivationStateSHTDN.manipulation(injector));

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.ON);
    }

    @Test
    @DisplayName("MetricStatusManipulationCLCActivationStateSHTDN: setMetricStatus failed.")
    void testMetricStatusManipulationCLCActivationStateSHTDNBadSecondManipulationFailed() {
        setMetricStatusSetup(MetricCategory.CLC, METRIC_HANDLE, ComponentActivation.ON, ComponentActivation.SHTDN);

        // let setMetricStatus fail
        when(mockManipulations.setMetricStatus(
                        any(String.class), any(MetricCategory.class), any(ComponentActivation.class)))
                .thenReturn(ResponseTypes.Result.RESULT_FAIL);

        assertFalse(ManipulationPreconditions.MetricStatusManipulationCLCActivationStateSHTDN.manipulation(injector));

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.ON);
        verify(mockManipulations).setMetricStatus(METRIC_HANDLE, MetricCategory.CLC, ComponentActivation.SHTDN);
    }

    @Test
    @DisplayName("MetricStatusManipulationCLCActivationStateOFF: Set CLC metrics to a state where the `calculation"
            + " not being performed and is de-initialized` to trigger the setting of the activation state to OFF")
    void testMetricStatusManipulationCLCActivationStateOFFGood() {
        setMetricStatusSetup(MetricCategory.CLC, METRIC_HANDLE, ComponentActivation.ON, ComponentActivation.OFF);

        assertTrue(ManipulationPreconditions.MetricStatusManipulationCLCActivationStateOFF.manipulation(injector));

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.ON);
        verify(mockManipulations).setMetricStatus(METRIC_HANDLE, MetricCategory.CLC, ComponentActivation.OFF);
    }

    @Test
    @DisplayName(
            "MetricStatusManipulationCLCActivationStateOFF: The precondition does not fail if setComponentActivation is not supported by all metrics.")
    void testMetricStatusManipulationCLCActivationStateOFFAllowNotSupported1() {
        metricMockSetup(
                MetricCategory.CLC, METRIC_HANDLE, SOME_HANDLE, ComponentActivation.ON, ComponentActivation.OFF);

        // let one metric not support setComponentActivation manipulation
        when(mockManipulations.setComponentActivation(eq(SOME_HANDLE), any(ComponentActivation.class)))
                .thenReturn(ResponseTypes.Result.RESULT_NOT_SUPPORTED);

        when(mockDevice.getMdibAccess().findEntitiesByType(AbstractMetricDescriptor.class))
                .thenReturn(List.of(mockEntity2, mockEntity));

        assertTrue(ManipulationPreconditions.MetricStatusManipulationCLCActivationStateOFF.manipulation(injector));

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.ON);
        verify(mockManipulations).setComponentActivation(SOME_HANDLE, ComponentActivation.ON);
        verify(mockManipulations).setMetricStatus(METRIC_HANDLE, MetricCategory.CLC, ComponentActivation.OFF);
    }

    @Test
    @DisplayName(
            "MetricStatusManipulationCLCActivationStateOFF: The precondition does not fail if setMetricStatus is not supported by all metrics.")
    void testMetricStatusManipulationCLCActivationStateOFFAllowNotSupported2() {
        metricMockSetup(
                MetricCategory.CLC, METRIC_HANDLE, SOME_HANDLE, ComponentActivation.ON, ComponentActivation.OFF);

        // let one metric not support setMetricStatus manipulation
        when(mockManipulations.setMetricStatus(
                        eq(SOME_HANDLE), any(MetricCategory.class), any(ComponentActivation.class)))
                .thenReturn(ResponseTypes.Result.RESULT_NOT_SUPPORTED);

        when(mockDevice.getMdibAccess().findEntitiesByType(AbstractMetricDescriptor.class))
                .thenReturn(List.of(mockEntity2, mockEntity));

        assertTrue(ManipulationPreconditions.MetricStatusManipulationCLCActivationStateOFF.manipulation(injector));

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.ON);
        verify(mockManipulations).setComponentActivation(SOME_HANDLE, ComponentActivation.ON);
        verify(mockManipulations).setMetricStatus(METRIC_HANDLE, MetricCategory.CLC, ComponentActivation.OFF);
    }

    @Test
    @DisplayName("MetricStatusManipulationCLCActivationStateOFF: setComponentActivation failed.")
    void testMetricStatusManipulationCLCActivationStateOFFBadFirstManipulationFailed() {
        setMetricStatusSetup(MetricCategory.CLC, METRIC_HANDLE, ComponentActivation.ON, ComponentActivation.OFF);

        // let setComponentActivation fail
        when(mockManipulations.setComponentActivation(any(String.class), any(ComponentActivation.class)))
                .thenReturn(ResponseTypes.Result.RESULT_FAIL);

        assertFalse(ManipulationPreconditions.MetricStatusManipulationCLCActivationStateOFF.manipulation(injector));

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.ON);
    }

    @Test
    @DisplayName("MetricStatusManipulationCLCActivationStateOFF: setMetricStatus failed.")
    void testMetricStatusManipulationCLCActivationStateOFFBadSecondManipulationFailed() {
        setMetricStatusSetup(MetricCategory.CLC, METRIC_HANDLE, ComponentActivation.ON, ComponentActivation.OFF);

        // let setMetricStatus fail
        when(mockManipulations.setMetricStatus(
                        any(String.class), any(MetricCategory.class), any(ComponentActivation.class)))
                .thenReturn(ResponseTypes.Result.RESULT_FAIL);

        assertFalse(ManipulationPreconditions.MetricStatusManipulationCLCActivationStateOFF.manipulation(injector));

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.ON);
        verify(mockManipulations).setMetricStatus(METRIC_HANDLE, MetricCategory.CLC, ComponentActivation.OFF);
    }

    @Test
    @DisplayName("testMetricStatusManipulationCLCActivationStateFAIL: Set ActivationState of all CLC-Metrics to FAIL.")
    void testMetricStatusManipulationCLCActivationStateFAILGood() {
        // given
        setMetricStatusSetup(MetricCategory.CLC, METRIC_HANDLE, ComponentActivation.ON, ComponentActivation.FAIL);

        // when
        assertTrue(ManipulationPreconditions.MetricStatusManipulationCLCActivationStateFAIL.manipulation(injector));

        // then
        assertFalse(
                testRunObserver.isInvalid(),
                "Test run should not have been invalidated. Reason(s): " + testRunObserver.getReasons());

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.ON);
        verify(mockManipulations).setMetricStatus(METRIC_HANDLE, MetricCategory.CLC, ComponentActivation.FAIL);
    }

    @Test
    @DisplayName(
            "MetricStatusManipulationCLCActivationStateFAIL: The precondition does not fail if setComponentActivation is not supported by all metrics.")
    void testMetricStatusManipulationCLCActivationStateFAILAllowNotSupported1() {
        metricMockSetup(
                MetricCategory.CLC, METRIC_HANDLE, SOME_HANDLE, ComponentActivation.ON, ComponentActivation.FAIL);

        // let one metric not support setComponentActivation manipulation
        when(mockManipulations.setComponentActivation(eq(SOME_HANDLE), any(ComponentActivation.class)))
                .thenReturn(ResponseTypes.Result.RESULT_NOT_SUPPORTED);

        when(mockDevice.getMdibAccess().findEntitiesByType(AbstractMetricDescriptor.class))
                .thenReturn(List.of(mockEntity2, mockEntity));

        assertTrue(ManipulationPreconditions.MetricStatusManipulationCLCActivationStateFAIL.manipulation(injector));

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.ON);
        verify(mockManipulations).setComponentActivation(SOME_HANDLE, ComponentActivation.ON);
        verify(mockManipulations).setMetricStatus(METRIC_HANDLE, MetricCategory.CLC, ComponentActivation.FAIL);
    }

    @Test
    @DisplayName(
            "MetricStatusManipulationCLCActivationStateFAIL: The precondition does not fail if setMetricStatus is not supported by all metrics.")
    void testMetricStatusManipulationCLCActivationStateFAILAllowNotSupported2() {
        metricMockSetup(
                MetricCategory.CLC, METRIC_HANDLE, SOME_HANDLE, ComponentActivation.ON, ComponentActivation.FAIL);

        // let one metric not support setMetricStatus manipulation
        when(mockManipulations.setMetricStatus(
                        eq(SOME_HANDLE), any(MetricCategory.class), any(ComponentActivation.class)))
                .thenReturn(ResponseTypes.Result.RESULT_NOT_SUPPORTED);

        when(mockDevice.getMdibAccess().findEntitiesByType(AbstractMetricDescriptor.class))
                .thenReturn(List.of(mockEntity2, mockEntity));

        assertTrue(ManipulationPreconditions.MetricStatusManipulationCLCActivationStateFAIL.manipulation(injector));

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.ON);
        verify(mockManipulations).setComponentActivation(SOME_HANDLE, ComponentActivation.ON);
        verify(mockManipulations).setMetricStatus(METRIC_HANDLE, MetricCategory.CLC, ComponentActivation.FAIL);
    }

    @Test
    @DisplayName("testMetricStatusManipulationCLCActivationStateFAIL: setComponentActivation failed.")
    void testMetricStatusManipulationCLCActivationStateFAILBadFirstManipulationFailed() {
        // given
        setMetricStatusSetup(MetricCategory.CLC, METRIC_HANDLE, ComponentActivation.ON, ComponentActivation.FAIL);

        // let setComponentActivation fail
        when(mockManipulations.setComponentActivation(any(String.class), any(ComponentActivation.class)))
                .thenReturn(ResponseTypes.Result.RESULT_FAIL);

        // when
        assertFalse(ManipulationPreconditions.MetricStatusManipulationCLCActivationStateFAIL.manipulation(injector));

        // then
        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.ON);
    }

    @Test
    @DisplayName("testMetricStatusManipulationCLCActivationStateFAIL: setMetricStatus failed.")
    void testMetricStatusManipulationCLCActivationStateFAILBadSecondManipulationFailed() {
        setMetricStatusSetup(MetricCategory.CLC, METRIC_HANDLE, ComponentActivation.ON, ComponentActivation.FAIL);

        // let setMetricStatus fail
        when(mockManipulations.setMetricStatus(
                        any(String.class), any(MetricCategory.class), any(ComponentActivation.class)))
                .thenReturn(ResponseTypes.Result.RESULT_FAIL);

        assertFalse(ManipulationPreconditions.MetricStatusManipulationCLCActivationStateFAIL.manipulation(injector));

        verify(mockManipulations).setComponentActivation(METRIC_HANDLE, ComponentActivation.ON);
        verify(mockManipulations).setMetricStatus(METRIC_HANDLE, MetricCategory.CLC, ComponentActivation.FAIL);
    }

    @Test
    @DisplayName("RemoveAndReinsertDescriptorManipulation: Successful")
    void testRemoveAndReinsertDescriptorManipulation() {
        setupRemoveAndReinsertDescriptor(SOME_HANDLE, List.of(SOME_HANDLE));

        assertTrue(ManipulationPreconditions.RemoveAndReinsertDescriptorManipulation.manipulation(injector));
        assertFalse(
                testRunObserver.isInvalid(),
                "Test run should not have been invalidated." + testRunObserver.getReasons());

        verify(mockManipulations).getRemovableDescriptorsOfClass();
        verify(mockManipulations).removeDescriptor(SOME_HANDLE);
        verify(mockManipulations).insertDescriptor(SOME_HANDLE);
    }

    @Test
    @DisplayName("RemoveAndReinsertDescriptorManipulation: Removable Descriptor is not present")
    void testRemoveAndReinsertDescriptorManipulation2() {
        setupRemoveAndReinsertDescriptor(SOME_HANDLE, List.of(SOME_HANDLE));

        when(mockManipulations.insertDescriptor(any(String.class)))
                .thenReturn(ResponseTypes.Result.RESULT_SUCCESS)
                .thenReturn(ResponseTypes.Result.RESULT_SUCCESS)
                .thenReturn(ResponseTypes.Result.RESULT_FAIL);

        when(mockDevice.getMdibAccess().getEntity(anyString()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(mockEntity))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(mockEntity));

        assertTrue(ManipulationPreconditions.RemoveAndReinsertDescriptorManipulation.manipulation(injector));
        assertFalse(
                testRunObserver.isInvalid(),
                "Test run should not have been invalidated." + testRunObserver.getReasons());

        verify(mockManipulations).getRemovableDescriptorsOfClass();
        verify(mockManipulations).removeDescriptor(SOME_HANDLE);
        verify(mockManipulations, times(2)).insertDescriptor(SOME_HANDLE);
    }

    @Test
    @DisplayName("RemoveAndReinsertDescriptorManipulation: No removable descriptors")
    void testRemoveAndReinsertDescriptorManipulationBad() {
        setupRemoveAndReinsertDescriptor(SOME_HANDLE, List.of());

        assertFalse(ManipulationPreconditions.RemoveAndReinsertDescriptorManipulation.manipulation(injector));
        assertFalse(
                testRunObserver.isInvalid(),
                "Test run should not have been invalidated." + testRunObserver.getReasons());

        verify(mockManipulations).getRemovableDescriptorsOfClass();
        verify(mockManipulations, times(0)).removeDescriptor(SOME_HANDLE);
        verify(mockManipulations, times(0)).insertDescriptor(SOME_HANDLE);
    }

    @Test
    @DisplayName("RemoveAndReinsertDescriptorManipulation: Insert descriptor fails")
    void testRemoveAndReinsertDescriptorManipulationBad2() {
        setupRemoveAndReinsertDescriptor(SOME_HANDLE, List.of(SOME_HANDLE));

        when(mockManipulations.insertDescriptor(anyString())).thenReturn(ResponseTypes.Result.RESULT_FAIL);
        assertFalse(ManipulationPreconditions.RemoveAndReinsertDescriptorManipulation.manipulation(injector));
        assertTrue(testRunObserver.isInvalid(), "Test run should have been invalidated.");

        verify(mockManipulations).getRemovableDescriptorsOfClass();
        verify(mockManipulations).removeDescriptor(SOME_HANDLE);
        verify(mockManipulations).insertDescriptor(SOME_HANDLE);
    }

    @Test
    @DisplayName("RemoveAndReinsertDescriptorManipulation: Remove descriptor fails")
    void testRemoveAndReinsertDescriptorManipulationBad3() {
        setupRemoveAndReinsertDescriptor(SOME_HANDLE, List.of(SOME_HANDLE));

        when(mockManipulations.removeDescriptor(anyString())).thenReturn(ResponseTypes.Result.RESULT_FAIL);
        assertFalse(ManipulationPreconditions.RemoveAndReinsertDescriptorManipulation.manipulation(injector));
        assertTrue(testRunObserver.isInvalid(), "Test run should have been invalidated.");

        verify(mockManipulations).getRemovableDescriptorsOfClass();
        verify(mockManipulations).removeDescriptor(SOME_HANDLE);
        verify(mockManipulations).insertDescriptor(SOME_HANDLE);
    }

    private void setupRemoveAndReinsertDescriptor(
            final String descriptorHandle, final List<String> removableDescriptors) {
        when(mockManipulations.getRemovableDescriptorsOfClass()).thenReturn(removableDescriptors);

        when(mockManipulations.removeDescriptor(any(String.class)))
                .thenReturn(ResponseTypes.Result.RESULT_SUCCESS)
                .thenReturn(ResponseTypes.Result.RESULT_FAIL);

        when(mockManipulations.insertDescriptor(any(String.class)))
                .thenReturn(ResponseTypes.Result.RESULT_SUCCESS)
                .thenReturn(ResponseTypes.Result.RESULT_FAIL);

        when(mockEntity.getHandle()).thenReturn(descriptorHandle);
        when(mockDevice.getMdibAccess().getEntity(anyString()))
                .thenReturn(Optional.of(mockEntity))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(mockEntity));
    }
}
