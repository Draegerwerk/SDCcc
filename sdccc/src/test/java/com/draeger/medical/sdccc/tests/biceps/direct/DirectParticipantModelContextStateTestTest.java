/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2023 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.tests.biceps.direct;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.draeger.medical.sdccc.manipulation.Manipulations;
import com.draeger.medical.sdccc.sdcri.testclient.TestClient;
import com.draeger.medical.sdccc.sdcri.testclient.TestClientUtil;
import com.draeger.medical.sdccc.tests.InjectorTestBase;
import com.draeger.medical.sdccc.tests.test_util.InjectorUtil;
import com.draeger.medical.sdccc.tests.util.NoTestData;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.somda.sdc.biceps.model.message.GetMdibResponse;
import org.somda.sdc.biceps.model.participant.AbstractContextState;
import org.somda.sdc.biceps.model.participant.ContextAssociation;
import org.somda.sdc.biceps.model.participant.EnsembleContextDescriptor;
import org.somda.sdc.biceps.model.participant.EnsembleContextState;
import org.somda.sdc.biceps.model.participant.LocationContextDescriptor;
import org.somda.sdc.biceps.model.participant.LocationContextState;
import org.somda.sdc.biceps.model.participant.Mdib;
import org.somda.sdc.biceps.model.participant.MdsDescriptor;
import org.somda.sdc.biceps.model.participant.MeansContextDescriptor;
import org.somda.sdc.biceps.model.participant.MeansContextState;
import org.somda.sdc.biceps.model.participant.ObjectFactory;
import org.somda.sdc.biceps.model.participant.OperatorContextDescriptor;
import org.somda.sdc.biceps.model.participant.OperatorContextState;
import org.somda.sdc.biceps.model.participant.PatientContextDescriptor;
import org.somda.sdc.biceps.model.participant.PatientContextState;
import org.somda.sdc.biceps.model.participant.SystemContextDescriptor;
import org.somda.sdc.biceps.model.participant.WorkflowContextDescriptor;
import org.somda.sdc.biceps.model.participant.WorkflowContextState;
import org.somda.sdc.dpws.service.HostedServiceProxy;
import org.somda.sdc.dpws.service.HostingServiceProxy;
import org.somda.sdc.dpws.soap.SoapMessage;
import org.somda.sdc.dpws.soap.exception.MarshallingException;
import org.somda.sdc.dpws.soap.exception.SoapFaultException;
import org.somda.sdc.dpws.soap.exception.TransportException;
import org.somda.sdc.dpws.soap.interception.InterceptorException;
import org.somda.sdc.glue.common.WsdlConstants;

/**
 * Unit test for the BICEPS {@linkplain DirectParticipantModelContextStateTest}.
 */
public class DirectParticipantModelContextStateTestTest {

    private static final String PATIENT_CONTEXT_DESCRIPTOR_HANDLE = "newPatientContext";
    private static final String LOCATION_CONTEXT_DESCRIPTOR_HANDLE = "newLocationContext";
    private static final String ENSEMBLE_CONTEXT_DESCRIPTOR_HANDLE = "newEnsembleContext";
    private static final String MEANS_CONTEXT_DESCRIPTOR_HANDLE = "newMeansContext";
    private static final String OPERATOR_CONTEXT_DESCRIPTOR_HANDLE = "newOperatorContext";
    private static final String WORKFLOW_CONTEXT_DESCRIPTOR_HANDLE = "newWorkflowContext";
    private static final String WORKFLOW_CONTEXT_DESCRIPTOR_HANDLE2 = "newWorkflowContext2";

    private static final String NEW_PATIENT_CONTEXT_STATE_HANDLE = "newPatientContextState";
    private static final String NEW_LOCATION_CONTEXT_STATE_HANDLE = "newLocationContextState";
    private static final String NEW_ENSEMBLE_CONTEXT_STATE_HANDLE = "newEnsembleContextState";
    private static final String NEW_MEANS_CONTEXT_STATE_HANDLE = "newMeansContextState";
    private static final String NEW_OPERATOR_CONTEXT_STATE_HANDLE = "newOperatorContextState";
    private static final String NEW_WORKFLOW_CONTEXT_STATE_HANDLE = "newWorkflowContextState";
    private static final String NEW_WORKFLOW_CONTEXT_STATE_HANDLE2 = "newWorkflowContextState2";

    private TestClient testClient;
    private DirectParticipantModelContextStateTest testClass;

    private Manipulations mockManipulations;
    private org.somda.sdc.biceps.model.participant.ObjectFactory mdibBuilder;

    @BeforeEach
    void setUp() throws IOException {
        final TestClient mockClient = mock(TestClient.class, RETURNS_DEEP_STUBS);
        when(mockClient.isClientRunning()).thenReturn(true);
        testClient = mockClient;

        mockManipulations = mock(Manipulations.class);
        // setup the injector used by sdcri
        final var clientInjector = TestClientUtil.createClientInjector();
        when(testClient.getInjector()).thenReturn(clientInjector);

        mdibBuilder = clientInjector.getInstance(ObjectFactory.class);
        final Injector injector = InjectorUtil.setupInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(TestClient.class).toInstance(testClient);
                bind(Manipulations.class).toInstance(mockManipulations);
            }
        });

        InjectorTestBase.setInjector(injector);

        testClass = new DirectParticipantModelContextStateTest();
        testClass.setUp();
    }

    /**
     * Tests whether calling the tests without any input data causes a failure.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement0125NoTestData() throws Exception {
        testSetup(
                null,
                null,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList());
        assertThrows(NoTestData.class, testClass::testRequirement0125);
    }

    /**
     * Tests whether a successful manipulation for each context passes the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement0125Good() throws Exception {
        final var patientContextState = createPatientContextState(
                PATIENT_CONTEXT_DESCRIPTOR_HANDLE, NEW_PATIENT_CONTEXT_STATE_HANDLE, ContextAssociation.ASSOC);
        final var locationContextState = createLocationContextState(
                LOCATION_CONTEXT_DESCRIPTOR_HANDLE, NEW_LOCATION_CONTEXT_STATE_HANDLE, ContextAssociation.ASSOC);
        final var ensembleContextState = createEnsembleContextState(
                ENSEMBLE_CONTEXT_DESCRIPTOR_HANDLE, NEW_ENSEMBLE_CONTEXT_STATE_HANDLE, ContextAssociation.ASSOC);
        final var meansContextState = createMeansContextState(
                MEANS_CONTEXT_DESCRIPTOR_HANDLE, NEW_MEANS_CONTEXT_STATE_HANDLE, ContextAssociation.ASSOC);
        final var operatorContextState = createOperatorContextState(
                OPERATOR_CONTEXT_DESCRIPTOR_HANDLE, NEW_OPERATOR_CONTEXT_STATE_HANDLE, ContextAssociation.ASSOC);
        final var workflowContextState = createWorkflowContextState(
                WORKFLOW_CONTEXT_DESCRIPTOR_HANDLE, NEW_WORKFLOW_CONTEXT_STATE_HANDLE, ContextAssociation.ASSOC);

        when(testClient
                        .getSdcRemoteDevice()
                        .getMdibAccess()
                        .getState(NEW_PATIENT_CONTEXT_STATE_HANDLE, AbstractContextState.class))
                .thenReturn(Optional.of(patientContextState));
        when(testClient
                        .getSdcRemoteDevice()
                        .getMdibAccess()
                        .getState(NEW_LOCATION_CONTEXT_STATE_HANDLE, AbstractContextState.class))
                .thenReturn(Optional.of(locationContextState));
        when(testClient
                        .getSdcRemoteDevice()
                        .getMdibAccess()
                        .getState(NEW_ENSEMBLE_CONTEXT_STATE_HANDLE, AbstractContextState.class))
                .thenReturn(Optional.of(ensembleContextState));
        when(testClient
                        .getSdcRemoteDevice()
                        .getMdibAccess()
                        .getState(NEW_MEANS_CONTEXT_STATE_HANDLE, AbstractContextState.class))
                .thenReturn(Optional.of(meansContextState));
        when(testClient
                        .getSdcRemoteDevice()
                        .getMdibAccess()
                        .getState(NEW_OPERATOR_CONTEXT_STATE_HANDLE, AbstractContextState.class))
                .thenReturn(Optional.of(operatorContextState));
        when(testClient
                        .getSdcRemoteDevice()
                        .getMdibAccess()
                        .getState(NEW_WORKFLOW_CONTEXT_STATE_HANDLE, AbstractContextState.class))
                .thenReturn(Optional.of(workflowContextState));

        testSetup(
                createPatientContextDescriptor(PATIENT_CONTEXT_DESCRIPTOR_HANDLE),
                createLocationContextDescriptor(LOCATION_CONTEXT_DESCRIPTOR_HANDLE),
                createEnsembleContextDescriptor(ENSEMBLE_CONTEXT_DESCRIPTOR_HANDLE),
                createMeansContextDescriptor(MEANS_CONTEXT_DESCRIPTOR_HANDLE),
                createOperatorContextDescriptor(OPERATOR_CONTEXT_DESCRIPTOR_HANDLE),
                createWorkflowContextDescriptor(WORKFLOW_CONTEXT_DESCRIPTOR_HANDLE));

        when(mockManipulations.createContextStateWithAssociation(
                        PATIENT_CONTEXT_DESCRIPTOR_HANDLE, ContextAssociation.ASSOC))
                .thenReturn(Optional.of(NEW_PATIENT_CONTEXT_STATE_HANDLE));
        when(mockManipulations.createContextStateWithAssociation(
                        LOCATION_CONTEXT_DESCRIPTOR_HANDLE, ContextAssociation.ASSOC))
                .thenReturn(Optional.of(NEW_LOCATION_CONTEXT_STATE_HANDLE));
        when(mockManipulations.createContextStateWithAssociation(
                        ENSEMBLE_CONTEXT_DESCRIPTOR_HANDLE, ContextAssociation.ASSOC))
                .thenReturn(Optional.of(NEW_ENSEMBLE_CONTEXT_STATE_HANDLE));
        when(mockManipulations.createContextStateWithAssociation(
                        MEANS_CONTEXT_DESCRIPTOR_HANDLE, ContextAssociation.ASSOC))
                .thenReturn(Optional.of(NEW_MEANS_CONTEXT_STATE_HANDLE));
        when(mockManipulations.createContextStateWithAssociation(
                        OPERATOR_CONTEXT_DESCRIPTOR_HANDLE, ContextAssociation.ASSOC))
                .thenReturn(Optional.of(NEW_OPERATOR_CONTEXT_STATE_HANDLE));
        when(mockManipulations.createContextStateWithAssociation(
                        WORKFLOW_CONTEXT_DESCRIPTOR_HANDLE, ContextAssociation.ASSOC))
                .thenReturn(Optional.of(NEW_WORKFLOW_CONTEXT_STATE_HANDLE));

        testClass.testRequirement0125();
    }

    /**
     * Tests whether a successful manipulation for each supported context passes the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement0125GoodMultipleContextsOfSameType() throws Exception {
        final var workflowContextState = createWorkflowContextState(
                WORKFLOW_CONTEXT_DESCRIPTOR_HANDLE, NEW_WORKFLOW_CONTEXT_STATE_HANDLE, ContextAssociation.ASSOC);

        final var workflowContextState2 = createWorkflowContextState(
                WORKFLOW_CONTEXT_DESCRIPTOR_HANDLE2, NEW_WORKFLOW_CONTEXT_STATE_HANDLE2, ContextAssociation.ASSOC);

        when(testClient
                        .getSdcRemoteDevice()
                        .getMdibAccess()
                        .getState(NEW_WORKFLOW_CONTEXT_STATE_HANDLE, AbstractContextState.class))
                .thenReturn(Optional.of(workflowContextState));
        when(testClient
                        .getSdcRemoteDevice()
                        .getMdibAccess()
                        .getState(NEW_WORKFLOW_CONTEXT_STATE_HANDLE2, AbstractContextState.class))
                .thenReturn(Optional.of(workflowContextState2));

        testSetup(
                null,
                null,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                createWorkflowContextDescriptor(
                        WORKFLOW_CONTEXT_DESCRIPTOR_HANDLE, WORKFLOW_CONTEXT_DESCRIPTOR_HANDLE2));

        when(mockManipulations.createContextStateWithAssociation(
                        WORKFLOW_CONTEXT_DESCRIPTOR_HANDLE, ContextAssociation.ASSOC))
                .thenReturn(Optional.of(NEW_WORKFLOW_CONTEXT_STATE_HANDLE));
        when(mockManipulations.createContextStateWithAssociation(
                        WORKFLOW_CONTEXT_DESCRIPTOR_HANDLE2, ContextAssociation.ASSOC))
                .thenReturn(Optional.of(NEW_WORKFLOW_CONTEXT_STATE_HANDLE2));

        testClass.testRequirement0125();
    }

    /**
     * Tests whether a successful manipulation for each supported context passes the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement0125GoodNotAllContextSupported() throws Exception {
        final var patientContextState = createPatientContextState(
                PATIENT_CONTEXT_DESCRIPTOR_HANDLE, NEW_PATIENT_CONTEXT_STATE_HANDLE, ContextAssociation.ASSOC);
        final var operatorContextState = createOperatorContextState(
                OPERATOR_CONTEXT_DESCRIPTOR_HANDLE, NEW_OPERATOR_CONTEXT_STATE_HANDLE, ContextAssociation.ASSOC);

        when(testClient
                        .getSdcRemoteDevice()
                        .getMdibAccess()
                        .getState(NEW_PATIENT_CONTEXT_STATE_HANDLE, AbstractContextState.class))
                .thenReturn(Optional.of(patientContextState));
        when(testClient
                        .getSdcRemoteDevice()
                        .getMdibAccess()
                        .getState(NEW_OPERATOR_CONTEXT_STATE_HANDLE, AbstractContextState.class))
                .thenReturn(Optional.of(operatorContextState));

        testSetup(
                createPatientContextDescriptor(PATIENT_CONTEXT_DESCRIPTOR_HANDLE),
                null,
                Collections.emptyList(),
                Collections.emptyList(),
                createOperatorContextDescriptor(OPERATOR_CONTEXT_DESCRIPTOR_HANDLE),
                Collections.emptyList());

        when(mockManipulations.createContextStateWithAssociation(
                        PATIENT_CONTEXT_DESCRIPTOR_HANDLE, ContextAssociation.ASSOC))
                .thenReturn(Optional.of(NEW_PATIENT_CONTEXT_STATE_HANDLE));
        when(mockManipulations.createContextStateWithAssociation(
                        OPERATOR_CONTEXT_DESCRIPTOR_HANDLE, ContextAssociation.ASSOC))
                .thenReturn(Optional.of(NEW_OPERATOR_CONTEXT_STATE_HANDLE));

        testClass.testRequirement0125();
    }

    /**
     * Tests whether a failed manipulation fails the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement0125BadManipulationFailed() throws Exception {
        testSetup(
                createPatientContextDescriptor(PATIENT_CONTEXT_DESCRIPTOR_HANDLE),
                null,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList());

        when(mockManipulations.createContextStateWithAssociation(
                        PATIENT_CONTEXT_DESCRIPTOR_HANDLE, ContextAssociation.ASSOC))
                .thenReturn(Optional.empty());
        final var error = assertThrows(AssertionError.class, testClass::testRequirement0125);
        assertTrue(error.getMessage().contains(PATIENT_CONTEXT_DESCRIPTOR_HANDLE));
    }

    /**
     * Tests whether a missing context state after the manipulation fails the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement0125BadContextStateMissing() throws Exception {
        final var patientContextState = createPatientContextState(
                PATIENT_CONTEXT_DESCRIPTOR_HANDLE, NEW_PATIENT_CONTEXT_STATE_HANDLE, ContextAssociation.ASSOC);

        when(testClient
                        .getSdcRemoteDevice()
                        .getMdibAccess()
                        .getState(NEW_PATIENT_CONTEXT_STATE_HANDLE, AbstractContextState.class))
                .thenReturn(Optional.of(patientContextState));
        when(testClient
                        .getSdcRemoteDevice()
                        .getMdibAccess()
                        .getState(NEW_LOCATION_CONTEXT_STATE_HANDLE, AbstractContextState.class))
                .thenReturn(Optional.empty());

        testSetup(
                createPatientContextDescriptor(PATIENT_CONTEXT_DESCRIPTOR_HANDLE),
                createLocationContextDescriptor(LOCATION_CONTEXT_DESCRIPTOR_HANDLE),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList());

        when(mockManipulations.createContextStateWithAssociation(
                        PATIENT_CONTEXT_DESCRIPTOR_HANDLE, ContextAssociation.ASSOC))
                .thenReturn(Optional.of(NEW_PATIENT_CONTEXT_STATE_HANDLE));
        when(mockManipulations.createContextStateWithAssociation(
                        LOCATION_CONTEXT_DESCRIPTOR_HANDLE, ContextAssociation.ASSOC))
                .thenReturn(Optional.of(NEW_LOCATION_CONTEXT_STATE_HANDLE));

        final var error = assertThrows(AssertionError.class, testClass::testRequirement0125);
        assertTrue(error.getMessage().contains(NEW_LOCATION_CONTEXT_STATE_HANDLE));
    }

    /**
     * Tests whether the wrong context association state fails the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement0125BadStateNotAssociated() throws Exception {

        final var patientContextState = createPatientContextState(
                PATIENT_CONTEXT_DESCRIPTOR_HANDLE, NEW_PATIENT_CONTEXT_STATE_HANDLE, ContextAssociation.ASSOC);
        final var locationContextState = createLocationContextState(
                LOCATION_CONTEXT_DESCRIPTOR_HANDLE, NEW_LOCATION_CONTEXT_STATE_HANDLE, ContextAssociation.DIS);

        when(testClient
                        .getSdcRemoteDevice()
                        .getMdibAccess()
                        .getState(NEW_PATIENT_CONTEXT_STATE_HANDLE, AbstractContextState.class))
                .thenReturn(Optional.of(patientContextState));
        when(testClient
                        .getSdcRemoteDevice()
                        .getMdibAccess()
                        .getState(NEW_LOCATION_CONTEXT_STATE_HANDLE, AbstractContextState.class))
                .thenReturn(Optional.of(locationContextState));

        testSetup(
                createPatientContextDescriptor(PATIENT_CONTEXT_DESCRIPTOR_HANDLE),
                createLocationContextDescriptor(LOCATION_CONTEXT_DESCRIPTOR_HANDLE),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList());

        when(mockManipulations.createContextStateWithAssociation(
                        PATIENT_CONTEXT_DESCRIPTOR_HANDLE, ContextAssociation.ASSOC))
                .thenReturn(Optional.of(NEW_PATIENT_CONTEXT_STATE_HANDLE));
        when(mockManipulations.createContextStateWithAssociation(
                        LOCATION_CONTEXT_DESCRIPTOR_HANDLE, ContextAssociation.ASSOC))
                .thenReturn(Optional.of(NEW_LOCATION_CONTEXT_STATE_HANDLE));

        assertThrows(AssertionError.class, testClass::testRequirement0125);
    }

    @SuppressFBWarnings(
            value = {"NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE"},
            justification = "everything is mocked")
    private void testSetup(
            final @Nullable PatientContextDescriptor patientContextDescriptor,
            final @Nullable LocationContextDescriptor locationContextDescriptor,
            final List<EnsembleContextDescriptor> ensembleContextDescriptor,
            final List<MeansContextDescriptor> meansContextDescriptor,
            final List<OperatorContextDescriptor> operatorContextDescriptor,
            final List<WorkflowContextDescriptor> workflowContextDescriptor)
            throws InterceptorException, SoapFaultException, MarshallingException, TransportException {
        final var systemContext = mock(SystemContextDescriptor.class);
        when(systemContext.getPatientContext()).thenReturn(patientContextDescriptor);
        when(systemContext.getLocationContext()).thenReturn(locationContextDescriptor);
        when(systemContext.getEnsembleContext()).thenReturn(ensembleContextDescriptor);
        when(systemContext.getMeansContext()).thenReturn(meansContextDescriptor);
        when(systemContext.getOperatorContext()).thenReturn(operatorContextDescriptor);
        when(systemContext.getWorkflowContext()).thenReturn(workflowContextDescriptor);

        final var mds = mock(MdsDescriptor.class);
        when(mds.getSystemContext()).thenReturn(systemContext);
        final var mdib = mock(Mdib.class, RETURNS_DEEP_STUBS);
        when(mdib.getMdDescription().getMds()).thenReturn(List.of(mds));

        final var getMdibResponse = mock(GetMdibResponse.class);
        when(getMdibResponse.getMdib()).thenReturn(mdib);

        final var messageResponse = mock(SoapMessage.class, RETURNS_DEEP_STUBS);
        when(messageResponse.getOriginalEnvelope().getBody().getAny().get(0)).thenReturn(getMdibResponse);

        final var hostedService = mock(HostedServiceProxy.class, Mockito.RETURNS_DEEP_STUBS);
        when(hostedService.getType().getTypes())
                .thenReturn(List.of(WsdlConstants.PORT_TYPE_GET_QNAME, WsdlConstants.PORT_TYPE_CONTEXT_QNAME));
        when(hostedService.sendRequestResponse(any())).thenReturn(messageResponse);

        final var map = Map.of("hostedService", hostedService);
        final var mockHostingService = mock(HostingServiceProxy.class);
        when(testClient.getHostingServiceProxy()).thenReturn(mockHostingService);
        when(mockHostingService.getHostedServices()).thenReturn(map);
    }

    private PatientContextDescriptor createPatientContextDescriptor(final String descriptorHandle) {
        final var descriptor = mdibBuilder.createPatientContextDescriptor();
        descriptor.setHandle(descriptorHandle);
        return descriptor;
    }

    private PatientContextState createPatientContextState(
            final String descriptorHandle, final String stateHandle, final ContextAssociation association) {
        final var state = mdibBuilder.createPatientContextState();
        state.setDescriptorHandle(descriptorHandle);
        state.setHandle(stateHandle);
        state.setContextAssociation(association);
        return state;
    }

    private LocationContextDescriptor createLocationContextDescriptor(final String descriptorHandle) {
        final var descriptor = mdibBuilder.createLocationContextDescriptor();
        descriptor.setHandle(descriptorHandle);
        return descriptor;
    }

    private LocationContextState createLocationContextState(
            final String descriptorHandle, final String stateHandle, final ContextAssociation association) {
        final var state = mdibBuilder.createLocationContextState();
        state.setDescriptorHandle(descriptorHandle);
        state.setHandle(stateHandle);
        state.setContextAssociation(association);
        return state;
    }

    private List<EnsembleContextDescriptor> createEnsembleContextDescriptor(final String... descriptorHandles) {
        final var descriptors = new ArrayList<EnsembleContextDescriptor>();
        for (var descriptorHandle : descriptorHandles) {
            final var descriptor = mdibBuilder.createEnsembleContextDescriptor();
            descriptor.setHandle(descriptorHandle);
            descriptors.add(descriptor);
        }
        return descriptors;
    }

    private EnsembleContextState createEnsembleContextState(
            final String descriptorHandle, final String stateHandle, final ContextAssociation association) {
        final var state = mdibBuilder.createEnsembleContextState();
        state.setDescriptorHandle(descriptorHandle);
        state.setHandle(stateHandle);
        state.setContextAssociation(association);
        return state;
    }

    private List<MeansContextDescriptor> createMeansContextDescriptor(final String... descriptorHandles) {
        final var descriptors = new ArrayList<MeansContextDescriptor>();
        for (var descriptorHandle : descriptorHandles) {
            final var descriptor = mdibBuilder.createMeansContextDescriptor();
            descriptor.setHandle(descriptorHandle);
            descriptors.add(descriptor);
        }
        return descriptors;
    }

    private MeansContextState createMeansContextState(
            final String descriptorHandle, final String stateHandle, final ContextAssociation association) {
        final var state = mdibBuilder.createMeansContextState();
        state.setDescriptorHandle(descriptorHandle);
        state.setHandle(stateHandle);
        state.setContextAssociation(association);
        return state;
    }

    private List<OperatorContextDescriptor> createOperatorContextDescriptor(final String... descriptorHandles) {
        final var descriptors = new ArrayList<OperatorContextDescriptor>();
        for (var descriptorHandle : descriptorHandles) {
            final var descriptor = mdibBuilder.createOperatorContextDescriptor();
            descriptor.setHandle(descriptorHandle);
            descriptors.add(descriptor);
        }
        return descriptors;
    }

    private OperatorContextState createOperatorContextState(
            final String descriptorHandle, final String stateHandle, final ContextAssociation association) {
        final var state = mdibBuilder.createOperatorContextState();
        state.setDescriptorHandle(descriptorHandle);
        state.setHandle(stateHandle);
        state.setContextAssociation(association);
        return state;
    }

    private List<WorkflowContextDescriptor> createWorkflowContextDescriptor(final String... descriptorHandles) {
        final var descriptors = new ArrayList<WorkflowContextDescriptor>();
        for (var descriptorHandle : descriptorHandles) {
            final var descriptor = mdibBuilder.createWorkflowContextDescriptor();
            descriptor.setHandle(descriptorHandle);
            descriptors.add(descriptor);
        }
        return descriptors;
    }

    private WorkflowContextState createWorkflowContextState(
            final String descriptorHandle, final String stateHandle, final ContextAssociation association) {
        final var state = mdibBuilder.createWorkflowContextState();
        state.setDescriptorHandle(descriptorHandle);
        state.setHandle(stateHandle);
        state.setContextAssociation(association);
        return state;
    }
}
