/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2023 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.tests.biceps.direct;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.draeger.medical.sdccc.sdcri.testclient.TestClient;
import com.draeger.medical.sdccc.sdcri.testclient.TestClientUtil;
import com.draeger.medical.sdccc.tests.InjectorTestBase;
import com.draeger.medical.sdccc.tests.test_util.InjectorUtil;
import com.draeger.medical.sdccc.tests.util.NoTestData;
import com.draeger.medical.sdccc.util.MessageGeneratingUtil;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.somda.sdc.biceps.common.MdibEntity;
import org.somda.sdc.biceps.model.message.GetContextStatesResponse;
import org.somda.sdc.biceps.model.message.GetMdStateResponse;
import org.somda.sdc.biceps.model.participant.AbstractContextDescriptor;
import org.somda.sdc.biceps.model.participant.AbstractContextState;
import org.somda.sdc.biceps.model.participant.AbstractState;
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
 * Unit test for the BICEPS {@linkplain DirectParticipantModelServiceOperationsTest}.
 */
public class DirectParticipantModelServiceOperationsTestTest {

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
    private DirectParticipantModelServiceOperationsTest testClass;

    private GetMdStateResponse getMdStateResponse;
    private GetContextStatesResponse getContextStatesResponse;

    private List<AbstractState> allStates;
    private List<AbstractContextState> allContextStates;

    private org.somda.sdc.biceps.model.participant.ObjectFactory mdibBuilder;

    @BeforeEach
    void setUp() throws IOException {
        final TestClient mockClient = mock(TestClient.class, RETURNS_DEEP_STUBS);
        when(mockClient.isClientRunning()).thenReturn(true);
        testClient = mockClient;

        getMdStateResponse = mock(GetMdStateResponse.class, RETURNS_DEEP_STUBS);
        final var messageGeneratingUtil = mock(MessageGeneratingUtil.class, RETURNS_DEEP_STUBS);
        when(messageGeneratingUtil
                        .getMdState(any())
                        .getOriginalEnvelope()
                        .getBody()
                        .getAny()
                        .get(0))
                .thenReturn(getMdStateResponse);

        getContextStatesResponse = mock(GetContextStatesResponse.class, RETURNS_DEEP_STUBS);
        when(messageGeneratingUtil
                        .getContextStates()
                        .getOriginalEnvelope()
                        .getBody()
                        .getAny()
                        .get(0))
                .thenReturn(getContextStatesResponse);
        when(messageGeneratingUtil
                        .getContextStates(any())
                        .getOriginalEnvelope()
                        .getBody()
                        .getAny()
                        .get(0))
                .thenReturn(getContextStatesResponse);

        final var clientInjector = TestClientUtil.createClientInjector();
        when(testClient.getInjector()).thenReturn(clientInjector);

        mdibBuilder = clientInjector.getInstance(ObjectFactory.class);

        final Injector injector = InjectorUtil.setupInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(TestClient.class).toInstance(testClient);
                bind(MessageGeneratingUtil.class).toInstance(messageGeneratingUtil);
            }
        });

        InjectorTestBase.setInjector(injector);

        testClass = new DirectParticipantModelServiceOperationsTest();
        testClass.setUp();

        final int size = 3;
        allStates = new ArrayList<>(size);
        allContextStates = new ArrayList<>(size);

        for (int i = 0; i < size; i++) {
            final AbstractState abstractState = new AbstractState();
            abstractState.setDescriptorHandle(String.valueOf(i));
            allStates.add(abstractState);
        }

        for (int i = 0; i < size; i++) {
            final AbstractContextState abstractContextState = new AbstractContextState();
            abstractContextState.setDescriptorHandle("c" + i);
            abstractContextState.setHandle("cH" + i);
            allContextStates.add(abstractContextState);
        }
    }

    /**
     * Assert fail for no data at all.
     */
    @Test
    public void testRequirementR5040NoTestData() {
        assertThrows(NoTestData.class, testClass::testRequirementR5040);
    }

    /**
     * Assert that returning all context states passes the test.
     *
     * @throws NoTestData on too few descriptor references
     */
    @Test
    public void testRequirementR5040Good() throws NoTestData {
        when(testClient.getSdcRemoteDevice().getMdibAccess().getContextStates()).thenReturn(allContextStates);

        when(getContextStatesResponse.getContextState()).thenReturn(allContextStates);

        testClass.testRequirementR5040();
    }

    /**
     * Assert that returning a state for only one descriptor fails the test.
     */
    @Test
    public void testRequirementR5040Bad() {
        when(testClient.getSdcRemoteDevice().getMdibAccess().getContextStates()).thenReturn(allContextStates);

        when(getContextStatesResponse.getContextState()).thenReturn(List.of(allContextStates.get(1)));

        assertThrows(AssertionError.class, testClass::testRequirementR5040);
    }

    /**
     * Assert fail for no data at all.
     */
    @Test
    public void testRequirementR5041NoTestData() {
        assertThrows(NoTestData.class, testClass::testRequirementR5041);
    }

    /**
     * Assert that returning all context states passes the test.
     *
     * @throws NoTestData on too few descriptor references
     */
    @Test
    public void testRequirementR5041Good() throws NoTestData {
        when(testClient.getSdcRemoteDevice().getMdibAccess().getContextStates()).thenReturn(allContextStates);

        when(getContextStatesResponse.getContextState()).thenReturn(allContextStates);

        testClass.testRequirementR5041();
    }

    /**
     * Assert that returning a state for only one handle fails the test.
     */
    @Test
    public void testRequirementR5041Bad() {
        when(testClient.getSdcRemoteDevice().getMdibAccess().getContextStates()).thenReturn(allContextStates);

        when(getContextStatesResponse.getContextState()).thenReturn(List.of(allContextStates.get(1)));

        assertThrows(AssertionError.class, testClass::testRequirementR5041);
    }

    /**
     * Assert fail for no data at all.
     */
    @Test
    public void testRequirementR5042NoTestData() {
        when(testClient.getSdcRemoteDevice().getMdibAccess().findEntitiesByType(any()))
                .thenReturn(List.of());

        assertThrows(NoTestData.class, testClass::testRequirementR5042);
    }

    /**
     * Assert that for multiple Mds the test results are correctly evaluated for each mds.
     */
    @Test
    public void testRequirementR5042MultipleMds() {
        final var remoteMdibAccess = testClient.getSdcRemoteDevice().getMdibAccess();
        final MdsDescriptor mdsDescriptor0 = new MdsDescriptor();
        mdsDescriptor0.setHandle("mds0");
        final MdsDescriptor mdsDescriptor1 = new MdsDescriptor();
        mdsDescriptor1.setHandle("mds1");
        when(testClient.getSdcRemoteDevice().getMdibAccess().findEntitiesByType(any()).stream()
                        .map(any())
                        .collect(any()))
                .thenReturn(List.of(mdsDescriptor0, mdsDescriptor1));

        final var mdibEntity0 = createMdsContextStateEntity(mdsDescriptor0, "cH0");
        final var mdibEntity1 = createMdsContextStateEntity(mdsDescriptor1, "cH1");
        final var mdibEntity2 = createMdsContextStateEntity(mdsDescriptor1, "cH2");
        final var mdibEntity3 = createMdsContextStateEntity(mdsDescriptor1, "cH3");

        when(remoteMdibAccess.findEntitiesByType(AbstractContextDescriptor.class))
                .thenReturn(List.of(mdibEntity0, mdibEntity1, mdibEntity2, mdibEntity3));

        when(getContextStatesResponse.getContextState()).thenReturn(allContextStates.subList(0, 1));

        assertThrows(AssertionError.class, testClass::testRequirementR5042);
    }

    /**
     * Assert that returning all states passes the test.
     *
     * @throws NoTestData on too few state handles
     */
    @Test
    public void testRequirementR5042Good() throws NoTestData {

        final var remoteMdibAccess = testClient.getSdcRemoteDevice().getMdibAccess();

        final MdsDescriptor mdsDescriptor = new MdsDescriptor();
        mdsDescriptor.setHandle("mds");

        when(remoteMdibAccess.findEntitiesByType(any()).stream().map(any()).collect(any()))
                .thenReturn(List.of(mdsDescriptor));

        final var mdibEntity0 = createMdsContextStateEntity(mdsDescriptor, "cH0");
        final var mdibEntity1 = createMdsContextStateEntity(mdsDescriptor, "cH1");
        final var mdibEntity2 = createMdsContextStateEntity(mdsDescriptor, "cH2");

        when(remoteMdibAccess.findEntitiesByType(AbstractContextDescriptor.class))
                .thenReturn(List.of(mdibEntity0, mdibEntity1, mdibEntity2));

        when(getContextStatesResponse.getContextState()).thenReturn(allContextStates);

        testClass.testRequirementR5042();
    }

    /**
     * Assert that returning not all of the states fails the test.
     */
    @Test
    public void testRequirementR5042Bad() {

        final var remoteMdibAccess = testClient.getSdcRemoteDevice().getMdibAccess();
        final MdsDescriptor mdsDescriptor = new MdsDescriptor();
        mdsDescriptor.setHandle("mds");

        when(remoteMdibAccess.findEntitiesByType(any()).stream().map(any()).collect(any()))
                .thenReturn(List.of(mdsDescriptor));

        final var mdibEntity = createMdsContextStateEntity(mdsDescriptor, "cH0");

        when(remoteMdibAccess.findEntitiesByType(AbstractContextDescriptor.class))
                .thenReturn(List.of(mdibEntity));

        when(getContextStatesResponse.getContextState()).thenReturn(allContextStates);

        assertThrows(AssertionError.class, testClass::testRequirementR5042);
    }

    /**
     * Create a MdibEntity for a newly created context state with a given handleName.
     *
     * @param mdsDescriptor related mds descriptor for which the context state shall be created
     * @param handleName    handle name to be set
     * @return MdibEntity object
     */
    private @NotNull MdibEntity createMdsContextStateEntity(MdsDescriptor mdsDescriptor, String handleName) {

        final var contextState = mdibBuilder.createLocationContextState();
        contextState.setHandle(handleName);
        final var contextDescriptor = mdibBuilder.createLocationContextDescriptor();
        final var mdibEntity = mock(MdibEntity.class);

        when(mdibEntity.getDescriptor()).thenReturn(contextDescriptor);
        when(mdibEntity.getStates(any())).thenReturn(List.of(contextState));
        when(mdibEntity.getParentMds()).thenReturn(mdsDescriptor.getHandle());

        return mdibEntity;
    }

    /**
     * Tests whether returning all context states on a get context state request without handle ref causes the
     * test to pass.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR5039Good() throws Exception {
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

        when(testClient.getSdcRemoteDevice().getMdibAccess().getContextStates())
                .thenReturn(List.of(
                        patientContextState,
                        locationContextState,
                        ensembleContextState,
                        meansContextState,
                        operatorContextState,
                        workflowContextState));

        testSetupR5039(
                createPatientContextDescriptor(PATIENT_CONTEXT_DESCRIPTOR_HANDLE),
                createLocationContextDescriptor(LOCATION_CONTEXT_DESCRIPTOR_HANDLE),
                createEnsembleContextDescriptor(ENSEMBLE_CONTEXT_DESCRIPTOR_HANDLE),
                createMeansContextDescriptor(MEANS_CONTEXT_DESCRIPTOR_HANDLE),
                createOperatorContextDescriptor(OPERATOR_CONTEXT_DESCRIPTOR_HANDLE),
                createWorkflowContextDescriptor(WORKFLOW_CONTEXT_DESCRIPTOR_HANDLE),
                List.of(
                        patientContextState,
                        locationContextState,
                        ensembleContextState,
                        meansContextState,
                        operatorContextState,
                        workflowContextState));

        testClass.testRequirementR5039();
    }

    /**
     * Tests whether returning all context states for all present contexts on a get context state request without
     * handle ref causes the test to pass.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR5039GoodNotAllContextsPresent() throws Exception {
        final var patientContextState = createPatientContextState(
                PATIENT_CONTEXT_DESCRIPTOR_HANDLE, NEW_PATIENT_CONTEXT_STATE_HANDLE, ContextAssociation.ASSOC);

        when(testClient.getSdcRemoteDevice().getMdibAccess().getContextStates())
                .thenReturn(List.of(patientContextState));

        testSetupR5039(
                createPatientContextDescriptor(PATIENT_CONTEXT_DESCRIPTOR_HANDLE),
                null,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                List.of(patientContextState));

        testClass.testRequirementR5039();
    }

    /**
     * Tests whether returning all context states on a get context state request while multiple context states for the
     * same context descriptor exists, causes the test to pass.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR5039GoodMultipleContextStates() throws Exception {
        final var patientContextState = createPatientContextState(
                PATIENT_CONTEXT_DESCRIPTOR_HANDLE, NEW_PATIENT_CONTEXT_STATE_HANDLE, ContextAssociation.ASSOC);
        final var locationContextState = createLocationContextState(
                LOCATION_CONTEXT_DESCRIPTOR_HANDLE, NEW_LOCATION_CONTEXT_STATE_HANDLE, ContextAssociation.ASSOC);
        final var workflowContextState = createWorkflowContextState(
                WORKFLOW_CONTEXT_DESCRIPTOR_HANDLE, NEW_WORKFLOW_CONTEXT_STATE_HANDLE, ContextAssociation.ASSOC);
        final var workflowContextState2 = createWorkflowContextState(
                WORKFLOW_CONTEXT_DESCRIPTOR_HANDLE, NEW_WORKFLOW_CONTEXT_STATE_HANDLE2, ContextAssociation.ASSOC);

        when(testClient.getSdcRemoteDevice().getMdibAccess().getContextStates())
                .thenReturn(List.of(
                        patientContextState, locationContextState, workflowContextState, workflowContextState2));

        testSetupR5039(
                createPatientContextDescriptor(PATIENT_CONTEXT_DESCRIPTOR_HANDLE),
                createLocationContextDescriptor(LOCATION_CONTEXT_DESCRIPTOR_HANDLE),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                createWorkflowContextDescriptor(WORKFLOW_CONTEXT_DESCRIPTOR_HANDLE),
                List.of(patientContextState, locationContextState, workflowContextState, workflowContextState2));

        testClass.testRequirementR5039();
    }

    /**
     * Tests whether returning all context states for multiple contexts of same type on a get context state request
     * without handle ref causes the test to pass.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR5039GoodMultipleContextDescriptors() throws Exception {
        final var patientContextState = createPatientContextState(
                PATIENT_CONTEXT_DESCRIPTOR_HANDLE, NEW_PATIENT_CONTEXT_STATE_HANDLE, ContextAssociation.ASSOC);
        final var locationContextState = createLocationContextState(
                LOCATION_CONTEXT_DESCRIPTOR_HANDLE, NEW_LOCATION_CONTEXT_STATE_HANDLE, ContextAssociation.ASSOC);
        final var workflowContextState = createWorkflowContextState(
                WORKFLOW_CONTEXT_DESCRIPTOR_HANDLE, NEW_WORKFLOW_CONTEXT_STATE_HANDLE, ContextAssociation.ASSOC);
        final var workflowContextState2 = createWorkflowContextState(
                WORKFLOW_CONTEXT_DESCRIPTOR_HANDLE2, NEW_WORKFLOW_CONTEXT_STATE_HANDLE2, ContextAssociation.ASSOC);

        when(testClient.getSdcRemoteDevice().getMdibAccess().getContextStates())
                .thenReturn(List.of(
                        patientContextState, locationContextState, workflowContextState, workflowContextState2));

        testSetupR5039(
                createPatientContextDescriptor(PATIENT_CONTEXT_DESCRIPTOR_HANDLE),
                createLocationContextDescriptor(LOCATION_CONTEXT_DESCRIPTOR_HANDLE),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                createWorkflowContextDescriptor(
                        WORKFLOW_CONTEXT_DESCRIPTOR_HANDLE, WORKFLOW_CONTEXT_DESCRIPTOR_HANDLE2),
                List.of(patientContextState, locationContextState, workflowContextState, workflowContextState2));

        testClass.testRequirementR5039();
    }

    /**
     * Tests whether returning not all context states on a get context state request without handle ref causes the
     * test to fail.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR5039BadContextStatesMissing() throws Exception {
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

        when(testClient.getSdcRemoteDevice().getMdibAccess().getContextStates())
                .thenReturn(List.of(
                        patientContextState,
                        locationContextState,
                        ensembleContextState,
                        meansContextState,
                        operatorContextState,
                        workflowContextState));

        testSetupR5039(
                createPatientContextDescriptor(PATIENT_CONTEXT_DESCRIPTOR_HANDLE),
                createLocationContextDescriptor(LOCATION_CONTEXT_DESCRIPTOR_HANDLE),
                createEnsembleContextDescriptor(ENSEMBLE_CONTEXT_DESCRIPTOR_HANDLE),
                createMeansContextDescriptor(MEANS_CONTEXT_DESCRIPTOR_HANDLE),
                createOperatorContextDescriptor(OPERATOR_CONTEXT_DESCRIPTOR_HANDLE),
                createWorkflowContextDescriptor(WORKFLOW_CONTEXT_DESCRIPTOR_HANDLE),
                List.of(
                        patientContextState,
                        locationContextState,
                        ensembleContextState,
                        meansContextState,
                        operatorContextState));

        assertThrows(AssertionError.class, testClass::testRequirementR5039);
    }

    /**
     * Tests whether returning the expected amount of context states but different states on a get context state request
     * without handle ref causes the test to fail.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR5039BadCorrectAmountDifferentStates() throws Exception {
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
        final var workflowContextState2 = createWorkflowContextState(
                WORKFLOW_CONTEXT_DESCRIPTOR_HANDLE, NEW_WORKFLOW_CONTEXT_STATE_HANDLE2, ContextAssociation.ASSOC);

        when(testClient.getSdcRemoteDevice().getMdibAccess().getContextStates())
                .thenReturn(List.of(
                        patientContextState,
                        locationContextState,
                        ensembleContextState,
                        meansContextState,
                        operatorContextState,
                        workflowContextState));

        testSetupR5039(
                createPatientContextDescriptor(PATIENT_CONTEXT_DESCRIPTOR_HANDLE),
                createLocationContextDescriptor(LOCATION_CONTEXT_DESCRIPTOR_HANDLE),
                createEnsembleContextDescriptor(ENSEMBLE_CONTEXT_DESCRIPTOR_HANDLE),
                createMeansContextDescriptor(MEANS_CONTEXT_DESCRIPTOR_HANDLE),
                createOperatorContextDescriptor(OPERATOR_CONTEXT_DESCRIPTOR_HANDLE),
                createWorkflowContextDescriptor(WORKFLOW_CONTEXT_DESCRIPTOR_HANDLE),
                List.of(
                        patientContextState,
                        locationContextState,
                        ensembleContextState,
                        meansContextState,
                        operatorContextState,
                        workflowContextState2));

        assertThrows(AssertionError.class, testClass::testRequirementR5039);
    }

    private void testSetupR5039(
            final @Nullable PatientContextDescriptor patientContextDescriptor,
            final @Nullable LocationContextDescriptor locationContextDescriptor,
            final List<EnsembleContextDescriptor> ensembleContextDescriptor,
            final List<MeansContextDescriptor> meansContextDescriptor,
            final List<OperatorContextDescriptor> operatorContextDescriptor,
            final List<WorkflowContextDescriptor> workflowContextDescriptor,
            final List<AbstractContextState> contextStates)
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

        when(getContextStatesResponse.getContextState()).thenReturn(contextStates);

        final var messageResponse = mock(SoapMessage.class, RETURNS_DEEP_STUBS);
        when(messageResponse.getOriginalEnvelope().getBody().getAny().get(0)).thenReturn(getContextStatesResponse);

        final var hostedService = mock(HostedServiceProxy.class, Mockito.RETURNS_DEEP_STUBS);
        when(hostedService.getType().getTypes()).thenReturn(List.of(WsdlConstants.PORT_TYPE_CONTEXT_QNAME));
        when(hostedService.sendRequestResponse(any())).thenReturn(messageResponse);

        final var map = Map.of("hostedService", hostedService);
        final var mockHostingService = mock(HostingServiceProxy.class);
        when(testClient.getHostingServiceProxy()).thenReturn(mockHostingService);
        when(mockHostingService.getHostedServices()).thenReturn(map);
    }

    /**
     * Tests whether having 3 relevant states passes the test, if those 3 are also returned.
     */
    @Test
    public void testRequirementC62Good() {
        when(testClient.getSdcRemoteDevice().getMdibAccess().getStatesByType(AbstractState.class))
                .thenReturn(allStates);
        when(testClient.getSdcRemoteDevice().getMdibAccess().getContextStates()).thenReturn(allContextStates);

        when(getMdStateResponse.getMdState().getState()).thenReturn(allStates);

        testClass.testRequirementC62();
    }

    /**
     * Test whether returning no states fails the test.
     */
    @Test
    public void testRequirementC62Bad() {
        when(testClient.getSdcRemoteDevice().getMdibAccess().getStatesByType(AbstractState.class))
                .thenReturn(allStates);
        when(testClient.getSdcRemoteDevice().getMdibAccess().getContextStates()).thenReturn(allContextStates);

        when(getMdStateResponse.getMdState().getState()).thenReturn(List.of());

        assertThrows(AssertionError.class, testClass::testRequirementC62);
    }

    /**
     * Test whether returning 3 wrong states fails the test.
     */
    @Test
    public void testRequirementC62Bad2() {
        when(testClient.getSdcRemoteDevice().getMdibAccess().getStatesByType(AbstractState.class))
                .thenReturn(allStates);
        when(testClient.getSdcRemoteDevice().getMdibAccess().getContextStates()).thenReturn(allContextStates);

        final var castedContexts =
                allContextStates.stream().map(state -> (AbstractState) state).collect(Collectors.toList());
        when(getMdStateResponse.getMdState().getState()).thenReturn(castedContexts);

        assertThrows(AssertionError.class, testClass::testRequirementC62);
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
