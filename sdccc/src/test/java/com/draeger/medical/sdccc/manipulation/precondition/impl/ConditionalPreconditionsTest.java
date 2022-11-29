/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.manipulation.precondition.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.draeger.medical.biceps.model.message.DescriptionModificationType;
import com.draeger.medical.biceps.model.participant.AbstractContextState;
import com.draeger.medical.biceps.model.participant.ObjectFactory;
import com.draeger.medical.sdccc.manipulation.Manipulations;
import com.draeger.medical.sdccc.manipulation.precondition.PreconditionException;
import com.draeger.medical.sdccc.marshalling.MarshallingUtil;
import com.draeger.medical.sdccc.messages.MessageStorage;
import com.draeger.medical.sdccc.messages.mapping.MessageContent;
import com.draeger.medical.sdccc.sdcri.testclient.TestClient;
import com.draeger.medical.sdccc.sdcri.testclient.TestClientUtil;
import com.draeger.medical.sdccc.tests.InjectorTestBase;
import com.draeger.medical.sdccc.tests.test_util.InjectorUtil;
import com.draeger.medical.sdccc.util.Constants;
import com.draeger.medical.sdccc.util.MessageBuilder;
import com.draeger.medical.sdccc.util.MessageStorageUtil;
import com.draeger.medical.sdccc.util.TestRunObserver;
import com.draeger.medical.t2iapi.ResponseTypes;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import jakarta.xml.bind.JAXBException;
import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import javax.xml.namespace.QName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.somda.sdc.biceps.common.MdibEntity;
import org.somda.sdc.biceps.model.participant.ContextAssociation;
import org.somda.sdc.biceps.model.participant.EnsembleContextDescriptor;
import org.somda.sdc.biceps.model.participant.EnsembleContextState;
import org.somda.sdc.biceps.model.participant.LocationContextDescriptor;
import org.somda.sdc.biceps.model.participant.LocationContextState;
import org.somda.sdc.biceps.model.participant.MeansContextDescriptor;
import org.somda.sdc.biceps.model.participant.MeansContextState;
import org.somda.sdc.biceps.model.participant.OperatorContextDescriptor;
import org.somda.sdc.biceps.model.participant.OperatorContextState;
import org.somda.sdc.biceps.model.participant.PatientContextDescriptor;
import org.somda.sdc.biceps.model.participant.PatientContextState;
import org.somda.sdc.biceps.model.participant.WorkflowContextDescriptor;
import org.somda.sdc.biceps.model.participant.WorkflowContextState;
import org.somda.sdc.dpws.helper.JaxbMarshalling;
import org.somda.sdc.dpws.soap.SoapMarshalling;
import org.somda.sdc.glue.common.ActionConstants;
import org.somda.sdc.glue.consumer.SdcRemoteDevice;

/**
 * Unit tests for conditional preconditions in {@linkplain ConditionalPreconditions}.
 */
public class ConditionalPreconditionsTest {

    private static final Duration MAX_WAIT = Duration.ofSeconds(10);
    private static MessageStorageUtil messageStorageUtil;
    private static MessageBuilder messageBuilder;

    private static final String PATIENT_CONTEXT_DESCRIPTOR_HANDLE = "patpatpatpat";
    private static final String PATIENT_CONTEXT_STATE_HANDLE = "patpatstate";
    private static final String PATIENT_CONTEXT_STATE_HANDLE2 = "patpatstate2";

    private static final String LOCATION_CONTEXT_DESCRIPTOR_HANDLE = "locloclocloc";
    private static final String LOCATION_CONTEXT_STATE_HANDLE = "loclocstate";
    private static final String LOCATION_CONTEXT_STATE_HANDLE2 = "loclocstate2";

    private static final String ENSEMBLE_CONTEXT_DESCRIPTOR_HANDLE = "ensensens";
    private static final String ENSEMBLE_CONTEXT_STATE_HANDLE = "ensensstate";
    private static final String ENSEMBLE_CONTEXT_STATE_HANDLE2 = "ensensstate2";

    private static final String MEANS_CONTEXT_DESCRIPTOR_HANDLE = "meameameamea";
    private static final String MEANS_CONTEXT_STATE_HANDLE = "meameastate";
    private static final String MEANS_CONTEXT_STATE_HANDLE2 = "meameastate2";

    private static final String OPERATOR_CONTEXT_DESCRIPTOR_HANDLE = "opeopeopeope";
    private static final String OPERATOR_CONTEXT_STATE_HANDLE = "opeopestate";
    private static final String OPERATOR_CONTEXT_STATE_HANDLE2 = "opeopestate2";

    private static final String WORKFLOW_CONTEXT_DESCRIPTOR_HANDLE = "worworworwor";
    private static final String WORKFLOW_CONTEXT_STATE_HANDLE = "worworstate";
    private static final String WORKFLOW_CONTEXT_STATE_HANDLE2 = "worworstate2";

    private TestClient testClient;
    private JaxbMarshalling jaxbMarshalling;
    private SoapMarshalling soapMarshalling;
    private MessageStorage storage;
    private Injector testInjector;
    private Manipulations mockManipulations;

    private SdcRemoteDevice mockDevice;

    private PatientContextState mockPatientContextState;
    private PatientContextState mockPatientContextState2;

    private LocationContextState mockLocationContextState;
    private LocationContextState mockLocationContextState2;

    private EnsembleContextState mockEnsembleContextState;
    private EnsembleContextState mockEnsembleContextState2;

    private MeansContextState mockMeansContextState;
    private MeansContextState mockMeansContextState2;

    private OperatorContextState mockOperatorContextState;
    private OperatorContextState mockOperatorContextState2;

    private WorkflowContextState mockWorkflowContextState;
    private WorkflowContextState mockWorkflowContextState2;

    private TestRunObserver testRunObserver;
    private MdibEntity mockPatientEntity;
    private MdibEntity mockLocationEntity;
    private MdibEntity mockEnsembleEntity;
    private MdibEntity mockMeansEntity;
    private MdibEntity mockOperatorEntity;
    private MdibEntity mockWorkflowEntity;

    @BeforeAll
    static void setupMarshalling() {
        final Injector marshallingInjector = MarshallingUtil.createMarshallingTestInjector(true);
        messageStorageUtil = marshallingInjector.getInstance(MessageStorageUtil.class);
        messageBuilder = marshallingInjector.getInstance(MessageBuilder.class);
    }

    @BeforeEach
    void setUp() throws TimeoutException, IOException {
        testClient = mock(TestClient.class, Mockito.RETURNS_DEEP_STUBS);
        when(testClient.isClientRunning()).thenReturn(true);

        mockDevice = mock(SdcRemoteDevice.class, Mockito.RETURNS_DEEP_STUBS);
        when(testClient.getSdcRemoteDevice()).thenReturn(mockDevice);

        mockManipulations = mock(Manipulations.class);

        testInjector = InjectorUtil.setupInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(TestClient.class).toInstance(testClient);
                bind(Manipulations.class).toInstance(mockManipulations);
                bind(SdcRemoteDevice.class).toInstance(mockDevice);
            }
        });
        InjectorTestBase.setInjector(testInjector);
        storage = testInjector.getInstance(MessageStorage.class);

        final var clientInjector = TestClientUtil.createClientInjector();
        when(testClient.getInjector()).thenReturn(clientInjector);

        // marshalling in the client
        jaxbMarshalling = testClient.getInjector().getInstance(JaxbMarshalling.class);
        soapMarshalling = testClient.getInjector().getInstance(SoapMarshalling.class);

        jaxbMarshalling.startAsync().awaitRunning(MAX_WAIT);
        soapMarshalling.startAsync().awaitRunning(MAX_WAIT);

        mockPatientContextState = mock(PatientContextState.class);
        mockPatientContextState2 = mock(PatientContextState.class);
        mockLocationContextState = mock(LocationContextState.class);
        mockLocationContextState2 = mock(LocationContextState.class);
        mockEnsembleContextState = mock(EnsembleContextState.class);
        mockEnsembleContextState2 = mock(EnsembleContextState.class);
        mockMeansContextState = mock(MeansContextState.class);
        mockMeansContextState2 = mock(MeansContextState.class);
        mockOperatorContextState = mock(OperatorContextState.class);
        mockOperatorContextState2 = mock(OperatorContextState.class);
        mockWorkflowContextState = mock(WorkflowContextState.class);
        mockWorkflowContextState2 = mock(WorkflowContextState.class);
        mockPatientEntity = mock(MdibEntity.class);
        mockLocationEntity = mock(MdibEntity.class);
        mockEnsembleEntity = mock(MdibEntity.class);
        mockMeansEntity = mock(MdibEntity.class);
        mockOperatorEntity = mock(MdibEntity.class);
        mockWorkflowEntity = mock(MdibEntity.class);

        testRunObserver = testInjector.getInstance(TestRunObserver.class);
    }

    @AfterEach
    void tearDown() throws TimeoutException {
        jaxbMarshalling.stopAsync().awaitTerminated(MAX_WAIT);
        soapMarshalling.stopAsync().awaitTerminated(MAX_WAIT);
    }

    /**
     * Tests whether HelloMessagePrecondition correctly checks for precondition.
     *
     * @throws PreconditionException on precondition exceptions
     * @throws IOException           on io exceptions
     */
    @Test
    @DisplayName("HelloMessagePrecondition correctly checks for precondition")
    public void testHelloMessagePreconditionCheck() throws PreconditionException, IOException {
        final var mockStorage = mock(MessageStorage.class);
        final var mockMessage = mock(MessageContent.class);
        final MessageStorage.GetterResult<MessageContent> mockGetter = mock(MessageStorage.GetterResult.class);
        when(mockGetter.getStream()).thenReturn(Stream.of(mockMessage)).thenReturn(Stream.empty());
        when(mockGetter.areObjectsPresent()).thenReturn(true).thenReturn(false);

        when(mockStorage.getInboundMessagesByBodyType(ArgumentMatchers.<QName>any()))
                .thenReturn(mockGetter);

        final var injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(MessageStorage.class).toInstance(mockStorage);
            }
        });

        assertTrue(ConditionalPreconditions.HelloMessagePrecondition.preconditionCheck(injector));

        // no messages
        assertFalse(ConditionalPreconditions.HelloMessagePrecondition.preconditionCheck(injector));
    }

    /**
     * Tests whether HelloMessagePrecondition correctly calls manipulation.
     */
    @Test
    @DisplayName("HelloMessagePrecondition correctly calls manipulation")
    public void testHelloMessageManipulation() {
        final var manipulations = mock(Manipulations.class);
        when(manipulations.sendHello())
                .thenReturn(ResponseTypes.Result.RESULT_SUCCESS)
                .thenReturn(ResponseTypes.Result.RESULT_FAIL);

        final var injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(Manipulations.class).toInstance(manipulations);
            }
        });

        assertTrue(ConditionalPreconditions.HelloMessagePrecondition.manipulation(injector));

        verify(manipulations, times(1)).sendHello();

        // second call must return false
        assertFalse(ConditionalPreconditions.HelloMessagePrecondition.manipulation(injector));
    }

    /**
     * Tests whether DescriptionModificationPrecondition correctly checks for precondition.
     *
     * @throws PreconditionException on precondition exceptions
     * @throws IOException           on io exceptions
     * @throws JAXBException         on marshalling failures
     */
    @Test
    @DisplayName("DescriptionChangedPrecondition correctly checks for precondition")
    public void testDescriptionModificationPreconditionCheck()
            throws PreconditionException, IOException, JAXBException {
        // no messages
        assertFalse(ConditionalPreconditions.DescriptionChangedPrecondition.preconditionCheck(testInjector));

        final var reportPart = messageBuilder.buildDescriptionModificationReportReportPart();
        reportPart.setModificationType(DescriptionModificationType.CRT);

        final var report = messageBuilder.buildDescriptionModificationReport("SomeSequence", List.of(reportPart));

        final var message = messageBuilder.createSoapMessageWithBody(
                ActionConstants.ACTION_DESCRIPTION_MODIFICATION_REPORT, report);

        messageStorageUtil.addInboundSecureHttpMessage(storage, message);

        assertTrue(ConditionalPreconditions.DescriptionChangedPrecondition.preconditionCheck(testInjector));
    }

    /**
     * Tests whether DescriptionModificationPrecondition correctly calls manipulation.
     *
     * @throws PreconditionException on precondition exceptions
     */
    @Test
    @DisplayName("DescriptionModificationPrecondition correctly calls manipulation")
    public void testDescriptionModificationManipulation() throws PreconditionException {
        final var descriptor1Handle = "superHandle";
        final var descriptor2Handle = "handle;Süper;";

        final var presenceMap = new HashMap<>(Map.of(
                descriptor1Handle, false,
                descriptor2Handle, true));

        when(mockManipulations.getRemovableDescriptors()).thenReturn(List.of(descriptor1Handle, descriptor2Handle));

        when(mockManipulations.insertDescriptor(anyString())).thenAnswer((Answer<ResponseTypes.Result>) invocation -> {
            presenceMap.put(invocation.getArgument(0), true);
            return ResponseTypes.Result.RESULT_SUCCESS;
        });
        when(mockManipulations.removeDescriptor(anyString())).thenAnswer((Answer<ResponseTypes.Result>) invocation -> {
            presenceMap.put(invocation.getArgument(0), false);
            return ResponseTypes.Result.RESULT_SUCCESS;
        });
        when(testClient.getSdcRemoteDevice().getMdibAccess().getEntity(anyString()))
                .thenAnswer((Answer<Optional<MdibEntity>>) invocation -> {
                    final String handle = invocation.getArgument(0);
                    if (presenceMap.get(handle)) {
                        return Optional.of(mock(MdibEntity.class));
                    } else {
                        return Optional.empty();
                    }
                });

        assertTrue(ConditionalPreconditions.DescriptionChangedPrecondition.manipulation(testInjector));

        final var insertCaptor = ArgumentCaptor.forClass(String.class);
        final var removeCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockManipulations, times(1)).getRemovableDescriptors();
        verify(mockManipulations, times(3)).insertDescriptor(insertCaptor.capture());
        verify(mockManipulations, times(2)).removeDescriptor(removeCaptor.capture());

        assertEquals(
                2,
                insertCaptor.getAllValues().stream()
                        .filter(descriptor1Handle::equals)
                        .count());
        assertEquals(
                1,
                insertCaptor.getAllValues().stream()
                        .filter(descriptor2Handle::equals)
                        .count());

        assertEquals(
                1,
                removeCaptor.getAllValues().stream()
                        .filter(descriptor1Handle::equals)
                        .count());
        assertEquals(
                1,
                removeCaptor.getAllValues().stream()
                        .filter(descriptor2Handle::equals)
                        .count());
    }

    /**
     * Tests whether DescriptionModificationCrtPrecondition correctly checks for precondition.
     *
     * @throws PreconditionException on precondition exceptions
     * @throws IOException           on io exceptions
     * @throws JAXBException         on marshalling failures
     */
    @Test
    @DisplayName("DescriptionModificationCrtPrecondition correctly checks for precondition")
    public void testDescriptionModificationCrtPreconditionCheck()
            throws PreconditionException, IOException, JAXBException {
        // no messages
        assertFalse(ConditionalPreconditions.DescriptionModificationCrtPrecondition.preconditionCheck(testInjector));

        final var reportPartDel = messageBuilder.buildDescriptionModificationReportReportPart();
        reportPartDel.setModificationType(DescriptionModificationType.DEL);

        final var firstReport =
                messageBuilder.buildDescriptionModificationReport("SomeSequence", List.of(reportPartDel));

        final var messageWithDelReportPart = messageBuilder.createSoapMessageWithBody(
                ActionConstants.ACTION_DESCRIPTION_MODIFICATION_REPORT, firstReport);

        messageStorageUtil.addInboundSecureHttpMessage(storage, messageWithDelReportPart);
        // no messages with report parts crt
        assertFalse(ConditionalPreconditions.DescriptionModificationCrtPrecondition.preconditionCheck(testInjector));

        final var reportPartCrt = messageBuilder.buildDescriptionModificationReportReportPart();
        reportPartCrt.setModificationType(DescriptionModificationType.CRT);

        final var secondReport =
                messageBuilder.buildDescriptionModificationReport("SomeSequence", List.of(reportPartCrt));

        final var messageWithCrtReportPart = messageBuilder.createSoapMessageWithBody(
                ActionConstants.ACTION_DESCRIPTION_MODIFICATION_REPORT, secondReport);

        messageStorageUtil.addInboundSecureHttpMessage(storage, messageWithCrtReportPart);
        assertTrue(ConditionalPreconditions.DescriptionModificationCrtPrecondition.preconditionCheck(testInjector));
    }

    /**
     * Tests whether DescriptionModificationCrtPrecondition correctly calls manipulation.
     *
     * @throws PreconditionException on precondition exceptions
     */
    @Test
    @DisplayName("DescriptionModificationCrtPrecondition correctly calls manipulation")
    public void testDescriptionModificationCrtManipulation() throws PreconditionException {
        final var descriptor1Handle = "superHandle";
        final var descriptor2Handle = "handle;Süper;";

        final var presenceMap = new HashMap<>(Map.of(
                descriptor1Handle, false,
                descriptor2Handle, true));

        when(mockManipulations.getRemovableDescriptors()).thenReturn(List.of(descriptor1Handle, descriptor2Handle));

        when(mockManipulations.insertDescriptor(anyString())).thenAnswer((Answer<ResponseTypes.Result>) invocation -> {
            presenceMap.put(invocation.getArgument(0), true);
            return ResponseTypes.Result.RESULT_SUCCESS;
        });
        when(mockManipulations.removeDescriptor(anyString())).thenAnswer((Answer<ResponseTypes.Result>) invocation -> {
            presenceMap.put(invocation.getArgument(0), false);
            return ResponseTypes.Result.RESULT_SUCCESS;
        });
        when(testClient.getSdcRemoteDevice().getMdibAccess().getEntity(anyString()))
                .thenAnswer((Answer<Optional<MdibEntity>>) invocation -> {
                    final String handle = invocation.getArgument(0);
                    if (presenceMap.get(handle)) {
                        return Optional.of(mock(MdibEntity.class));
                    } else {
                        return Optional.empty();
                    }
                });

        assertTrue(ConditionalPreconditions.DescriptionModificationCrtPrecondition.manipulation(testInjector));

        final var insertCaptor = ArgumentCaptor.forClass(String.class);
        final var removeCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockManipulations, times(1)).getRemovableDescriptors();
        verify(mockManipulations, times(3)).insertDescriptor(insertCaptor.capture());
        verify(mockManipulations, times(2)).removeDescriptor(removeCaptor.capture());

        assertEquals(
                2,
                insertCaptor.getAllValues().stream()
                        .filter(descriptor1Handle::equals)
                        .count());
        assertEquals(
                1,
                insertCaptor.getAllValues().stream()
                        .filter(descriptor2Handle::equals)
                        .count());

        assertEquals(
                1,
                removeCaptor.getAllValues().stream()
                        .filter(descriptor1Handle::equals)
                        .count());
        assertEquals(
                1,
                removeCaptor.getAllValues().stream()
                        .filter(descriptor2Handle::equals)
                        .count());
    }

    /**
     * Tests whether DescriptionModificationCrtPrecondition correctly checks for precondition.
     *
     * @throws PreconditionException on precondition exceptions
     * @throws IOException           on io exceptions
     * @throws JAXBException         on marshalling failures
     */
    @Test
    @DisplayName("DescriptionModificationCrtPrecondition correctly checks for precondition")
    public void testDescriptionModificationUptPreconditionCheck()
            throws PreconditionException, IOException, JAXBException {
        // no messages
        assertFalse(ConditionalPreconditions.DescriptionModificationUptPrecondition.preconditionCheck(testInjector));

        final var reportPartDel = messageBuilder.buildDescriptionModificationReportReportPart();
        reportPartDel.setModificationType(DescriptionModificationType.DEL);

        final var reportPartCrt = messageBuilder.buildDescriptionModificationReportReportPart();
        reportPartCrt.setModificationType(DescriptionModificationType.CRT);

        final var firstReport = messageBuilder.buildDescriptionModificationReport(
                "SomeSequence", List.of(reportPartDel, reportPartCrt));

        final var messageWithDelReportPart = messageBuilder.createSoapMessageWithBody(
                ActionConstants.ACTION_DESCRIPTION_MODIFICATION_REPORT, firstReport);

        messageStorageUtil.addInboundSecureHttpMessage(storage, messageWithDelReportPart);
        // no messages with report parts crt
        assertFalse(ConditionalPreconditions.DescriptionModificationUptPrecondition.preconditionCheck(testInjector));

        final var reportPartUpt = messageBuilder.buildDescriptionModificationReportReportPart();
        reportPartUpt.setModificationType(DescriptionModificationType.UPT);

        final var secondReport =
                messageBuilder.buildDescriptionModificationReport("SomeSequence", List.of(reportPartUpt));

        final var messageWithCrtReportPart = messageBuilder.createSoapMessageWithBody(
                ActionConstants.ACTION_DESCRIPTION_MODIFICATION_REPORT, secondReport);

        messageStorageUtil.addInboundSecureHttpMessage(storage, messageWithCrtReportPart);
        assertTrue(ConditionalPreconditions.DescriptionModificationUptPrecondition.preconditionCheck(testInjector));
    }

    /**
     * Tests whether DescriptionModificationUptPrecondition correctly calls manipulation.
     */
    @Test
    @DisplayName("DescriptionModificationUptPrecondition correctly calls manipulation")
    public void testDescriptionModificationUptManipulation() {
        final var descriptorHandle = "coolesHandle";

        when(mockManipulations.triggerDescriptorUpdate(descriptorHandle))
                .thenReturn(ResponseTypes.Result.RESULT_SUCCESS);

        when(mockDevice.getMdibAccess().getRootEntities().get(0).getDescriptor().getHandle())
                .thenReturn(descriptorHandle);

        new ConditionalPreconditions.DescriptionModificationUptPrecondition();

        assertTrue(ConditionalPreconditions.DescriptionModificationUptPrecondition.manipulation(testInjector));

        verify(mockManipulations, times(1)).triggerDescriptorUpdate(descriptorHandle);
    }

    /**
     * Tests whether DescriptionModificationPrecondition throws exception if no removable descriptors are present.
     */
    @Test
    @DisplayName("DescriptionModificationPrecondition throws exception if no removable descriptors are present")
    void testDescriptionModificationModificationNoDescriptors() {
        // must fail without any removable descriptors
        assertThrows(
                PreconditionException.class,
                () -> ConditionalPreconditions.DescriptionChangedPrecondition.manipulation(testInjector));
        reset(mockManipulations);
    }

    /**
     * Tests whether AllKindsOfContextStatesAssociatedPrecondition correctly checks for precondition.
     */
    @Test
    @DisplayName("AllKindsOfContextStatesAssociatedPrecondition correctly checks for precondition")
    public void testAllKindsOfContextStatesAssociatedPreconditionCheck() throws Exception {
        final com.draeger.medical.biceps.model.participant.ObjectFactory mdibBuilder =
                testInjector.getInstance(ObjectFactory.class);

        associateAllContextStatesSetup();
        // no messages
        assertFalse(
                ConditionalPreconditions.AllKindsOfContextStatesAssociatedPrecondition.preconditionCheck(testInjector));

        final var patientContextState = mdibBuilder.createPatientContextState();
        setHandlesAndAssociation(patientContextState, PATIENT_CONTEXT_DESCRIPTOR_HANDLE, PATIENT_CONTEXT_STATE_HANDLE);
        final var patientContextState2 = mdibBuilder.createPatientContextState();
        setHandlesAndAssociation(
                patientContextState2, PATIENT_CONTEXT_DESCRIPTOR_HANDLE, PATIENT_CONTEXT_STATE_HANDLE2);
        final var reportPart = messageBuilder.buildAbstractContextReportReportPart();
        reportPart.getContextState().addAll(List.of(patientContextState, patientContextState2));

        final var report = messageBuilder.buildEpisodicContextReport("SomeSequence");
        report.getReportPart().clear();
        report.getReportPart().addAll(List.of(reportPart));

        final var message =
                messageBuilder.createSoapMessageWithBody(ActionConstants.ACTION_EPISODIC_CONTEXT_REPORT, report);

        messageStorageUtil.addInboundSecureHttpMessage(storage, message);

        // not every kind of context had atleast two different states associated
        assertFalse(
                ConditionalPreconditions.AllKindsOfContextStatesAssociatedPrecondition.preconditionCheck(testInjector));

        final var locationContextState = mdibBuilder.createLocationContextState();
        setHandlesAndAssociation(
                locationContextState, LOCATION_CONTEXT_DESCRIPTOR_HANDLE, LOCATION_CONTEXT_STATE_HANDLE);
        final var locationContextState2 = mdibBuilder.createLocationContextState();
        setHandlesAndAssociation(
                locationContextState2, LOCATION_CONTEXT_DESCRIPTOR_HANDLE, LOCATION_CONTEXT_STATE_HANDLE2);

        final var ensembleContextState = mdibBuilder.createEnsembleContextState();
        setHandlesAndAssociation(
                ensembleContextState, ENSEMBLE_CONTEXT_DESCRIPTOR_HANDLE, ENSEMBLE_CONTEXT_STATE_HANDLE);
        final var ensembleContextState2 = mdibBuilder.createEnsembleContextState();
        setHandlesAndAssociation(
                ensembleContextState2, ENSEMBLE_CONTEXT_DESCRIPTOR_HANDLE, ENSEMBLE_CONTEXT_STATE_HANDLE2);

        final var meansContextState = mdibBuilder.createMeansContextState();
        setHandlesAndAssociation(meansContextState, MEANS_CONTEXT_DESCRIPTOR_HANDLE, MEANS_CONTEXT_STATE_HANDLE);
        final var meansContextState2 = mdibBuilder.createMeansContextState();
        setHandlesAndAssociation(meansContextState2, MEANS_CONTEXT_DESCRIPTOR_HANDLE, MEANS_CONTEXT_STATE_HANDLE2);

        final var operatorContextState = mdibBuilder.createOperatorContextState();
        setHandlesAndAssociation(
                operatorContextState, OPERATOR_CONTEXT_DESCRIPTOR_HANDLE, OPERATOR_CONTEXT_STATE_HANDLE);
        final var operatorContextState2 = mdibBuilder.createOperatorContextState();
        setHandlesAndAssociation(
                operatorContextState2, OPERATOR_CONTEXT_DESCRIPTOR_HANDLE, OPERATOR_CONTEXT_STATE_HANDLE2);

        final var workflowContextState = mdibBuilder.createWorkflowContextState();
        setHandlesAndAssociation(
                workflowContextState, WORKFLOW_CONTEXT_DESCRIPTOR_HANDLE, WORKFLOW_CONTEXT_STATE_HANDLE);
        final var workflowContextState2 = mdibBuilder.createWorkflowContextState();
        setHandlesAndAssociation(
                workflowContextState2, WORKFLOW_CONTEXT_DESCRIPTOR_HANDLE, WORKFLOW_CONTEXT_STATE_HANDLE2);

        final var secondReportPart = messageBuilder.buildAbstractContextReportReportPart();
        secondReportPart.getContextState().clear();
        secondReportPart
                .getContextState()
                .addAll(List.of(
                        locationContextState, locationContextState2,
                        ensembleContextState, ensembleContextState2,
                        meansContextState, meansContextState2,
                        operatorContextState, operatorContextState2,
                        workflowContextState, workflowContextState2));
        final var secondReport = messageBuilder.buildEpisodicContextReport("SomeSequence");
        secondReport.getReportPart().clear();
        secondReport.getReportPart().addAll(List.of(secondReportPart));

        final var secondMessage =
                messageBuilder.createSoapMessageWithBody(ActionConstants.ACTION_EPISODIC_CONTEXT_REPORT, secondReport);

        messageStorageUtil.addInboundSecureHttpMessage(storage, secondMessage);

        assertTrue(
                ConditionalPreconditions.AllKindsOfContextStatesAssociatedPrecondition.preconditionCheck(testInjector));
    }

    /**
     * Tests whether AllKindsOfContextStatesAssociatedPrecondition correctly calls manipulation.
     */
    @Test
    @DisplayName("AllKindsOfContextStatesAssociatedPrecondition: Associate all contexts correctly")
    void testAssociateAllKindsOfContexts() throws Exception {
        associateAllContextStatesSetup();

        final var expectedManipulationCalls = 12;

        final var expectedContextStateHandles = List.of(
                PATIENT_CONTEXT_DESCRIPTOR_HANDLE, PATIENT_CONTEXT_DESCRIPTOR_HANDLE,
                LOCATION_CONTEXT_DESCRIPTOR_HANDLE, LOCATION_CONTEXT_DESCRIPTOR_HANDLE,
                ENSEMBLE_CONTEXT_DESCRIPTOR_HANDLE, ENSEMBLE_CONTEXT_DESCRIPTOR_HANDLE,
                MEANS_CONTEXT_DESCRIPTOR_HANDLE, MEANS_CONTEXT_DESCRIPTOR_HANDLE,
                OPERATOR_CONTEXT_DESCRIPTOR_HANDLE, OPERATOR_CONTEXT_DESCRIPTOR_HANDLE,
                WORKFLOW_CONTEXT_DESCRIPTOR_HANDLE, WORKFLOW_CONTEXT_DESCRIPTOR_HANDLE);
        final var expectedAssociations = Collections.nCopies(12, ContextAssociation.ASSOC);

        allKindsOfContextStatesAssociatedManipulationSucceeded();

        final var handleCaptor = ArgumentCaptor.forClass(String.class);
        final var assocCaptor = ArgumentCaptor.forClass(ContextAssociation.class);
        verify(mockManipulations, times(expectedManipulationCalls))
                .createContextStateWithAssociation(handleCaptor.capture(), assocCaptor.capture());

        assertTrue(expectedContextStateHandles.containsAll(handleCaptor.getAllValues())
                && handleCaptor.getAllValues().containsAll(expectedContextStateHandles));
        assertEquals(expectedAssociations, assocCaptor.getAllValues());
    }

    /**
     * Tests whether AllKindsOfContextStatesAssociatedPrecondition just calls manipulation for needed contexts.
     */
    @Test
    @DisplayName("AllKindsOfContextStatesAssociatedPrecondition: Associate all needed contexts correctly")
    void testAssociateAllKindsOfContextsNotAllNeeded() throws Exception {
        associateAllContextStatesSetup();

        // setup precondition, just patient context has not two different associated context states
        for (var entry :
                ConditionalPreconditions.AllKindsOfContextStatesAssociatedPrecondition.ALREADY_ASSOCIATED_CONTEXTS
                        .entrySet()) {
            if (entry.getKey().equals(PatientContextState.class)) {
                entry.getValue().addAll(List.of(PATIENT_CONTEXT_STATE_HANDLE));
            } else if (entry.getKey().equals(LocationContextState.class)) {
                entry.getValue().addAll(List.of(LOCATION_CONTEXT_STATE_HANDLE, LOCATION_CONTEXT_STATE_HANDLE2));
            } else if (entry.getKey().equals(EnsembleContextState.class)) {
                entry.getValue().addAll(List.of(ENSEMBLE_CONTEXT_STATE_HANDLE, ENSEMBLE_CONTEXT_STATE_HANDLE2));
            } else if (entry.getKey().equals(MeansContextState.class)) {
                entry.getValue().addAll(List.of(MEANS_CONTEXT_STATE_HANDLE, MEANS_CONTEXT_STATE_HANDLE2));
            } else if (entry.getKey().equals(OperatorContextState.class)) {
                entry.getValue().addAll(List.of(OPERATOR_CONTEXT_STATE_HANDLE, OPERATOR_CONTEXT_STATE_HANDLE2));
            } else if (entry.getKey().equals(WorkflowContextState.class)) {
                entry.getValue().addAll(List.of(WORKFLOW_CONTEXT_STATE_HANDLE, WORKFLOW_CONTEXT_STATE_HANDLE2));
            }
        }
        final var expectedManipulationCalls = 2;

        final var expectedContextStateHandles =
                List.of(PATIENT_CONTEXT_DESCRIPTOR_HANDLE, PATIENT_CONTEXT_DESCRIPTOR_HANDLE);

        final var expectedAssociations = Collections.nCopies(2, ContextAssociation.ASSOC);

        allKindsOfContextStatesAssociatedManipulationSucceeded();

        final var handleCaptor = ArgumentCaptor.forClass(String.class);
        final var assocCaptor = ArgumentCaptor.forClass(ContextAssociation.class);
        verify(mockManipulations, times(expectedManipulationCalls))
                .createContextStateWithAssociation(handleCaptor.capture(), assocCaptor.capture());

        assertTrue(expectedContextStateHandles.containsAll(handleCaptor.getAllValues())
                && handleCaptor.getAllValues().containsAll(expectedContextStateHandles));
        assertEquals(expectedAssociations, assocCaptor.getAllValues());
    }

    /**
     * Tests whether AllKindsOfContextStatesAssociatedPrecondition correctly calls manipulation for supported contexts.
     */
    @Test
    @DisplayName("AllKindsOfContextStatesAssociatedPrecondition: Associate all supported contexts correctly")
    void testAssociateAllKindsOfContextsNotAllSupported() throws Exception {
        associateAllContextStatesSetup();

        // just patient context supported
        when(mockDevice.getMdibAccess().findEntitiesByType(WorkflowContextDescriptor.class))
                .thenReturn(List.of());
        when(mockDevice.getMdibAccess().findEntitiesByType(OperatorContextDescriptor.class))
                .thenReturn(List.of());
        when(mockDevice.getMdibAccess().findEntitiesByType(MeansContextDescriptor.class))
                .thenReturn(List.of());
        when(mockDevice.getMdibAccess().findEntitiesByType(EnsembleContextDescriptor.class))
                .thenReturn(List.of());
        when(mockDevice.getMdibAccess().findEntitiesByType(LocationContextDescriptor.class))
                .thenReturn(List.of());

        final var expectedManipulationCalls = 2;

        final var expectedContextStateHandles =
                List.of(PATIENT_CONTEXT_DESCRIPTOR_HANDLE, PATIENT_CONTEXT_DESCRIPTOR_HANDLE);
        final var expectedAssociations = Collections.nCopies(2, ContextAssociation.ASSOC);

        allKindsOfContextStatesAssociatedManipulationSucceeded();

        final var handleCaptor = ArgumentCaptor.forClass(String.class);
        final var assocCaptor = ArgumentCaptor.forClass(ContextAssociation.class);
        verify(mockManipulations, times(expectedManipulationCalls))
                .createContextStateWithAssociation(handleCaptor.capture(), assocCaptor.capture());

        assertEquals(expectedContextStateHandles, handleCaptor.getAllValues());
        assertEquals(expectedAssociations, assocCaptor.getAllValues());
    }

    /**
     * Tests whether AllKindsOfContextStatesAssociatedPrecondition invalidates the TestRun in case
     * one of the manipulations is unsupported.
     * NOTE that this only happens when the DUT includes ContextStates of a certain kind in its Mdib,
     * but does not support associating this kind of ContextStates.
     */
    @Test
    @DisplayName("AllKindsOfContextStatesAssociatedPrecondition: Invalidate TestRun when the Mdib contains a kind of"
            + " ContextState, but associating this kind is unsupported.")
    void testAssociateAllKindsOfContextsManipulationNotSupported() throws Exception {
        associateAllContextStatesSetup();

        final var expectedManipulationCalls = 11;

        // Manipulation of WorkflowContext is not supported
        when(mockManipulations.createContextStateWithAssociation(eq(WORKFLOW_CONTEXT_DESCRIPTOR_HANDLE), any()))
                .thenReturn(Optional.empty());

        final var expectedContextStateHandleCount = Map.of(
                MEANS_CONTEXT_DESCRIPTOR_HANDLE, 2L,
                WORKFLOW_CONTEXT_DESCRIPTOR_HANDLE, 1L,
                LOCATION_CONTEXT_DESCRIPTOR_HANDLE, 2L,
                PATIENT_CONTEXT_DESCRIPTOR_HANDLE, 2L,
                OPERATOR_CONTEXT_DESCRIPTOR_HANDLE, 2L,
                ENSEMBLE_CONTEXT_DESCRIPTOR_HANDLE, 2L);
        final var expectedAssociations = Collections.nCopies(expectedManipulationCalls, ContextAssociation.ASSOC);

        allKindsOfContextStatesAssociatedManipulationFailed();

        final var handleCaptor = ArgumentCaptor.forClass(String.class);
        final var assocCaptor = ArgumentCaptor.forClass(ContextAssociation.class);
        verify(mockManipulations, times(expectedManipulationCalls))
                .createContextStateWithAssociation(handleCaptor.capture(), assocCaptor.capture());

        final List<String> allHandles = handleCaptor.getAllValues();
        for (Map.Entry<String, Long> e : expectedContextStateHandleCount.entrySet()) {
            assertEquals(
                    e.getValue(),
                    allHandles.stream()
                            .filter(handle -> e.getKey().equals(handle))
                            .count());
        }
        assertEquals(expectedAssociations, assocCaptor.getAllValues());
    }

    /**
     * Tests whether AllKindsOfContextStatesAssociatedPrecondition manipulation fails, when no contexts are supported.
     */
    @Test
    @DisplayName("AllKindsOfContextStatesAssociatedPrecondition: No contexts supported")
    void testAssociateAllKindsOfContextsNoContextsSupported() throws Exception {
        associateAllContextStatesSetup();

        // no context supported
        when(mockDevice.getMdibAccess().findEntitiesByType(WorkflowContextDescriptor.class))
                .thenReturn(List.of());
        when(mockDevice.getMdibAccess().findEntitiesByType(OperatorContextDescriptor.class))
                .thenReturn(List.of());
        when(mockDevice.getMdibAccess().findEntitiesByType(MeansContextDescriptor.class))
                .thenReturn(List.of());
        when(mockDevice.getMdibAccess().findEntitiesByType(EnsembleContextDescriptor.class))
                .thenReturn(List.of());
        when(mockDevice.getMdibAccess().findEntitiesByType(LocationContextDescriptor.class))
                .thenReturn(List.of());
        when(mockDevice.getMdibAccess().findEntitiesByType(PatientContextDescriptor.class))
                .thenReturn(List.of());

        assertFalse(
                ConditionalPreconditions.AllKindsOfContextStatesAssociatedPrecondition.manipulation(testInjector),
                "Manipulation should have succeeded");
        assertFalse(
                testRunObserver.isInvalid(),
                "Test run should not have been invalid. Reason(s): " + testRunObserver.getReasons());
    }

    /**
     * Tests whether AllKindsOfContextStatesAssociatedPrecondition manipulation fails, when context is not associated.
     */
    @Test
    @DisplayName("AllKindsOfContextStatesAssociatedPrecondition: New context not associated")
    void testAssociateAllKindsOfContextsWrongAssociation() throws Exception {
        {
            associateAllContextStatesSetup();
            // introduce error, context won't be associated
            when(mockPatientContextState.getContextAssociation()).thenReturn(ContextAssociation.DIS);
            allKindsOfContextStatesAssociatedManipulationFailed();
        }
        {
            associateAllContextStatesSetup();
            when(mockLocationContextState.getContextAssociation()).thenReturn(ContextAssociation.DIS);
            allKindsOfContextStatesAssociatedManipulationFailed();
        }
        {
            associateAllContextStatesSetup();
            when(mockEnsembleContextState.getContextAssociation()).thenReturn(ContextAssociation.DIS);
            allKindsOfContextStatesAssociatedManipulationFailed();
        }
        {
            associateAllContextStatesSetup();
            when(mockMeansContextState.getContextAssociation()).thenReturn(ContextAssociation.DIS);
            allKindsOfContextStatesAssociatedManipulationFailed();
        }
        {
            associateAllContextStatesSetup();
            when(mockOperatorContextState.getContextAssociation()).thenReturn(ContextAssociation.DIS);
            allKindsOfContextStatesAssociatedManipulationFailed();
        }
        {
            associateAllContextStatesSetup();
            when(mockWorkflowContextState.getContextAssociation()).thenReturn(ContextAssociation.DIS);
            allKindsOfContextStatesAssociatedManipulationFailed();
        }
    }

    /**
     * Tests whether AllKindsOfContextStatesAssociatedPrecondition manipulation fails, when context state is associated
     * to wrong descriptor.
     */
    @Test
    @DisplayName("AllKindsOfContextStatesAssociatedPrecondition: New context wrong descriptor")
    void testAssociateAllKindsOfContextsWrongDescriptor() throws Exception {
        final var wrongDescriptorHandle = "notTheRightDescriptor";
        {
            associateAllContextStatesSetup();
            // introduce error, state points to wrong descriptor
            when(mockPatientContextState.getDescriptorHandle()).thenReturn(wrongDescriptorHandle);
            allKindsOfContextStatesAssociatedManipulationFailed();
        }
        {
            associateAllContextStatesSetup();
            when(mockLocationContextState.getDescriptorHandle()).thenReturn(wrongDescriptorHandle);
            allKindsOfContextStatesAssociatedManipulationFailed();
        }
        {
            associateAllContextStatesSetup();
            when(mockEnsembleContextState.getDescriptorHandle()).thenReturn(wrongDescriptorHandle);
            allKindsOfContextStatesAssociatedManipulationFailed();
        }
        {
            associateAllContextStatesSetup();
            when(mockMeansContextState.getDescriptorHandle()).thenReturn(wrongDescriptorHandle);
            allKindsOfContextStatesAssociatedManipulationFailed();
        }
        {
            associateAllContextStatesSetup();
            when(mockOperatorContextState.getDescriptorHandle()).thenReturn(wrongDescriptorHandle);
            allKindsOfContextStatesAssociatedManipulationFailed();
        }
        {
            associateAllContextStatesSetup();
            when(mockWorkflowContextState.getDescriptorHandle()).thenReturn(wrongDescriptorHandle);
            allKindsOfContextStatesAssociatedManipulationFailed();
        }
    }

    /**
     * Tests whether AllKindsOfContextStatesAssociatedPrecondition manipulation fails, when the associated state is not
     * new.
     */
    @Test
    @DisplayName("AllKindsOfContextStatesAssociatedPrecondition: Already existent state")
    void testAssociateAllKindsOfContextsStateAlreadyExisted() throws Exception {
        {
            associateAllContextStatesSetup();
            // introduce error, first state handle already in entity
            when(mockPatientEntity.getStates(PatientContextState.class)).thenReturn(List.of(mockPatientContextState));
            allKindsOfContextStatesAssociatedManipulationFailed();
        }
        {
            associateAllContextStatesSetup();
            when(mockLocationEntity.getStates(LocationContextState.class))
                    .thenReturn(List.of(mockLocationContextState));
            allKindsOfContextStatesAssociatedManipulationFailed();
        }
        {
            associateAllContextStatesSetup();
            when(mockEnsembleEntity.getStates(EnsembleContextState.class))
                    .thenReturn(List.of(mockEnsembleContextState));
            allKindsOfContextStatesAssociatedManipulationFailed();
        }
        {
            associateAllContextStatesSetup();
            when(mockMeansEntity.getStates(MeansContextState.class)).thenReturn(List.of(mockMeansContextState));
            allKindsOfContextStatesAssociatedManipulationFailed();
        }
        {
            associateAllContextStatesSetup();
            when(mockOperatorEntity.getStates(OperatorContextState.class))
                    .thenReturn(List.of(mockOperatorContextState));
            allKindsOfContextStatesAssociatedManipulationFailed();
        }
        {
            associateAllContextStatesSetup();
            when(mockWorkflowEntity.getStates(WorkflowContextState.class))
                    .thenReturn(List.of(mockWorkflowContextState));
            allKindsOfContextStatesAssociatedManipulationFailed();
        }
    }

    /**
     * Tests whether AllKindsOfContextStatesAssociatedPrecondition manipulation fails, when the same state is associated
     * twice.
     */
    @Test
    @DisplayName("AllKindsOfContextStatesAssociatedPrecondition: Manipulation returns same handle twice")
    void testAssociateAllKindsOfContextsSameHandle() throws Exception {
        {
            associateAllContextStatesSetup();
            // introduce error, manipulation returns same handle twice
            when(mockManipulations.createContextStateWithAssociation(
                            PATIENT_CONTEXT_DESCRIPTOR_HANDLE, ContextAssociation.ASSOC))
                    .thenReturn(Optional.of(PATIENT_CONTEXT_STATE_HANDLE))
                    .thenReturn(Optional.of(PATIENT_CONTEXT_STATE_HANDLE))
                    .thenReturn(Optional.empty());

            allKindsOfContextStatesAssociatedManipulationFailed();
        }
        {
            associateAllContextStatesSetup();
            when(mockManipulations.createContextStateWithAssociation(
                            LOCATION_CONTEXT_DESCRIPTOR_HANDLE, ContextAssociation.ASSOC))
                    .thenReturn(Optional.of(LOCATION_CONTEXT_STATE_HANDLE))
                    .thenReturn(Optional.of(LOCATION_CONTEXT_STATE_HANDLE))
                    .thenReturn(Optional.empty());

            allKindsOfContextStatesAssociatedManipulationFailed();
        }
        {
            associateAllContextStatesSetup();
            when(mockManipulations.createContextStateWithAssociation(
                            ENSEMBLE_CONTEXT_DESCRIPTOR_HANDLE, ContextAssociation.ASSOC))
                    .thenReturn(Optional.of(ENSEMBLE_CONTEXT_STATE_HANDLE))
                    .thenReturn(Optional.of(ENSEMBLE_CONTEXT_STATE_HANDLE))
                    .thenReturn(Optional.empty());

            allKindsOfContextStatesAssociatedManipulationFailed();
        }
        {
            associateAllContextStatesSetup();
            when(mockManipulations.createContextStateWithAssociation(
                            MEANS_CONTEXT_DESCRIPTOR_HANDLE, ContextAssociation.ASSOC))
                    .thenReturn(Optional.of(MEANS_CONTEXT_STATE_HANDLE))
                    .thenReturn(Optional.of(MEANS_CONTEXT_STATE_HANDLE))
                    .thenReturn(Optional.empty());

            allKindsOfContextStatesAssociatedManipulationFailed();
        }
        {
            associateAllContextStatesSetup();
            when(mockManipulations.createContextStateWithAssociation(
                            OPERATOR_CONTEXT_DESCRIPTOR_HANDLE, ContextAssociation.ASSOC))
                    .thenReturn(Optional.of(OPERATOR_CONTEXT_STATE_HANDLE))
                    .thenReturn(Optional.of(OPERATOR_CONTEXT_STATE_HANDLE))
                    .thenReturn(Optional.empty());

            allKindsOfContextStatesAssociatedManipulationFailed();
        }
        {
            associateAllContextStatesSetup();
            when(mockManipulations.createContextStateWithAssociation(
                            WORKFLOW_CONTEXT_DESCRIPTOR_HANDLE, ContextAssociation.ASSOC))
                    .thenReturn(Optional.of(WORKFLOW_CONTEXT_STATE_HANDLE))
                    .thenReturn(Optional.of(WORKFLOW_CONTEXT_STATE_HANDLE))
                    .thenReturn(Optional.empty());

            allKindsOfContextStatesAssociatedManipulationFailed();
        }
    }

    void associateAllContextStatesSetup() {
        // create mock patient context state
        when(mockPatientContextState.getDescriptorHandle()).thenReturn(PATIENT_CONTEXT_DESCRIPTOR_HANDLE);
        when(mockPatientContextState.getContextAssociation()).thenReturn(ContextAssociation.ASSOC);
        when(mockPatientContextState.getHandle()).thenReturn(PATIENT_CONTEXT_STATE_HANDLE);

        // create another mock patient context state
        when(mockPatientContextState2.getDescriptorHandle()).thenReturn(PATIENT_CONTEXT_DESCRIPTOR_HANDLE);
        when(mockPatientContextState2.getContextAssociation()).thenReturn(ContextAssociation.ASSOC);
        when(mockPatientContextState2.getHandle()).thenReturn(PATIENT_CONTEXT_STATE_HANDLE2);

        // create mock location context state
        when(mockLocationContextState.getDescriptorHandle()).thenReturn(LOCATION_CONTEXT_DESCRIPTOR_HANDLE);
        when(mockLocationContextState.getContextAssociation()).thenReturn(ContextAssociation.ASSOC);
        when(mockLocationContextState.getHandle()).thenReturn(LOCATION_CONTEXT_STATE_HANDLE);

        // create another mock location context state
        when(mockLocationContextState2.getDescriptorHandle()).thenReturn(LOCATION_CONTEXT_DESCRIPTOR_HANDLE);
        when(mockLocationContextState2.getContextAssociation()).thenReturn(ContextAssociation.ASSOC);
        when(mockLocationContextState2.getHandle()).thenReturn(LOCATION_CONTEXT_STATE_HANDLE2);

        // create mock ensemble context state
        when(mockEnsembleContextState.getDescriptorHandle()).thenReturn(ENSEMBLE_CONTEXT_DESCRIPTOR_HANDLE);
        when(mockEnsembleContextState.getContextAssociation()).thenReturn(ContextAssociation.ASSOC);
        when(mockEnsembleContextState.getHandle()).thenReturn(ENSEMBLE_CONTEXT_STATE_HANDLE);

        // create another mock ensemble context state
        when(mockEnsembleContextState2.getDescriptorHandle()).thenReturn(ENSEMBLE_CONTEXT_DESCRIPTOR_HANDLE);
        when(mockEnsembleContextState2.getContextAssociation()).thenReturn(ContextAssociation.ASSOC);
        when(mockEnsembleContextState2.getHandle()).thenReturn(ENSEMBLE_CONTEXT_STATE_HANDLE2);

        // create mock means context state
        when(mockMeansContextState.getDescriptorHandle()).thenReturn(MEANS_CONTEXT_DESCRIPTOR_HANDLE);
        when(mockMeansContextState.getContextAssociation()).thenReturn(ContextAssociation.ASSOC);
        when(mockMeansContextState.getHandle()).thenReturn(MEANS_CONTEXT_STATE_HANDLE);

        // create another mock means context state
        when(mockMeansContextState2.getDescriptorHandle()).thenReturn(MEANS_CONTEXT_DESCRIPTOR_HANDLE);
        when(mockMeansContextState2.getContextAssociation()).thenReturn(ContextAssociation.ASSOC);
        when(mockMeansContextState2.getHandle()).thenReturn(MEANS_CONTEXT_STATE_HANDLE2);

        // create mock operator context state
        when(mockOperatorContextState.getDescriptorHandle()).thenReturn(OPERATOR_CONTEXT_DESCRIPTOR_HANDLE);
        when(mockOperatorContextState.getContextAssociation()).thenReturn(ContextAssociation.ASSOC);
        when(mockOperatorContextState.getHandle()).thenReturn(OPERATOR_CONTEXT_STATE_HANDLE);

        // create another mock operator context state
        when(mockOperatorContextState2.getDescriptorHandle()).thenReturn(OPERATOR_CONTEXT_DESCRIPTOR_HANDLE);
        when(mockOperatorContextState2.getContextAssociation()).thenReturn(ContextAssociation.ASSOC);
        when(mockOperatorContextState2.getHandle()).thenReturn(OPERATOR_CONTEXT_STATE_HANDLE2);

        // create mock workflow context state
        when(mockWorkflowContextState.getDescriptorHandle()).thenReturn(WORKFLOW_CONTEXT_DESCRIPTOR_HANDLE);
        when(mockWorkflowContextState.getContextAssociation()).thenReturn(ContextAssociation.ASSOC);
        when(mockWorkflowContextState.getHandle()).thenReturn(WORKFLOW_CONTEXT_STATE_HANDLE);

        // create another mock workflow context state
        when(mockWorkflowContextState2.getDescriptorHandle()).thenReturn(WORKFLOW_CONTEXT_DESCRIPTOR_HANDLE);
        when(mockWorkflowContextState2.getContextAssociation()).thenReturn(ContextAssociation.ASSOC);
        when(mockWorkflowContextState2.getHandle()).thenReturn(WORKFLOW_CONTEXT_STATE_HANDLE2);

        // make manipulation return our two patient context state handles and nothing afterwards
        when(mockManipulations.createContextStateWithAssociation(
                        PATIENT_CONTEXT_DESCRIPTOR_HANDLE, ContextAssociation.ASSOC))
                .thenReturn(Optional.of(PATIENT_CONTEXT_STATE_HANDLE))
                .thenReturn(Optional.of(PATIENT_CONTEXT_STATE_HANDLE2))
                .thenReturn(Optional.empty());

        // make manipulation return our two location context state handles and nothing afterwards
        when(mockManipulations.createContextStateWithAssociation(
                        LOCATION_CONTEXT_DESCRIPTOR_HANDLE, ContextAssociation.ASSOC))
                .thenReturn(Optional.of(LOCATION_CONTEXT_STATE_HANDLE))
                .thenReturn(Optional.of(LOCATION_CONTEXT_STATE_HANDLE2))
                .thenReturn(Optional.empty());

        // make manipulation return our two ensemble context state handles and nothing afterwards
        when(mockManipulations.createContextStateWithAssociation(
                        ENSEMBLE_CONTEXT_DESCRIPTOR_HANDLE, ContextAssociation.ASSOC))
                .thenReturn(Optional.of(ENSEMBLE_CONTEXT_STATE_HANDLE))
                .thenReturn(Optional.of(ENSEMBLE_CONTEXT_STATE_HANDLE2))
                .thenReturn(Optional.empty());

        // make manipulation return our two means context state handles and nothing afterwards
        when(mockManipulations.createContextStateWithAssociation(
                        MEANS_CONTEXT_DESCRIPTOR_HANDLE, ContextAssociation.ASSOC))
                .thenReturn(Optional.of(MEANS_CONTEXT_STATE_HANDLE))
                .thenReturn(Optional.of(MEANS_CONTEXT_STATE_HANDLE2))
                .thenReturn(Optional.empty());

        // make manipulation return our two operator context state handles and nothing afterwards
        when(mockManipulations.createContextStateWithAssociation(
                        OPERATOR_CONTEXT_DESCRIPTOR_HANDLE, ContextAssociation.ASSOC))
                .thenReturn(Optional.of(OPERATOR_CONTEXT_STATE_HANDLE))
                .thenReturn(Optional.of(OPERATOR_CONTEXT_STATE_HANDLE2))
                .thenReturn(Optional.empty());

        // make manipulation return our two workflow context state handles and nothing afterwards
        when(mockManipulations.createContextStateWithAssociation(
                        WORKFLOW_CONTEXT_DESCRIPTOR_HANDLE, ContextAssociation.ASSOC))
                .thenReturn(Optional.of(WORKFLOW_CONTEXT_STATE_HANDLE))
                .thenReturn(Optional.of(WORKFLOW_CONTEXT_STATE_HANDLE2))
                .thenReturn(Optional.empty());

        // return mock states on request
        when(mockDevice.getMdibAccess().getState(PATIENT_CONTEXT_STATE_HANDLE, PatientContextState.class))
                .thenReturn(Optional.of(mockPatientContextState));
        when(mockDevice.getMdibAccess().getState(PATIENT_CONTEXT_STATE_HANDLE2, PatientContextState.class))
                .thenReturn(Optional.of(mockPatientContextState2));

        when(mockDevice.getMdibAccess().getState(LOCATION_CONTEXT_STATE_HANDLE, LocationContextState.class))
                .thenReturn(Optional.of(mockLocationContextState));
        when(mockDevice.getMdibAccess().getState(LOCATION_CONTEXT_STATE_HANDLE2, LocationContextState.class))
                .thenReturn(Optional.of(mockLocationContextState2));

        when(mockDevice.getMdibAccess().getState(ENSEMBLE_CONTEXT_STATE_HANDLE, EnsembleContextState.class))
                .thenReturn(Optional.of(mockEnsembleContextState));
        when(mockDevice.getMdibAccess().getState(ENSEMBLE_CONTEXT_STATE_HANDLE2, EnsembleContextState.class))
                .thenReturn(Optional.of(mockEnsembleContextState2));

        when(mockDevice.getMdibAccess().getState(MEANS_CONTEXT_STATE_HANDLE, MeansContextState.class))
                .thenReturn(Optional.of(mockMeansContextState));
        when(mockDevice.getMdibAccess().getState(MEANS_CONTEXT_STATE_HANDLE2, MeansContextState.class))
                .thenReturn(Optional.of(mockMeansContextState2));

        when(mockDevice.getMdibAccess().getState(OPERATOR_CONTEXT_STATE_HANDLE, OperatorContextState.class))
                .thenReturn(Optional.of(mockOperatorContextState));
        when(mockDevice.getMdibAccess().getState(OPERATOR_CONTEXT_STATE_HANDLE2, OperatorContextState.class))
                .thenReturn(Optional.of(mockOperatorContextState2));

        when(mockDevice.getMdibAccess().getState(WORKFLOW_CONTEXT_STATE_HANDLE, WorkflowContextState.class))
                .thenReturn(Optional.of(mockWorkflowContextState));
        when(mockDevice.getMdibAccess().getState(WORKFLOW_CONTEXT_STATE_HANDLE2, WorkflowContextState.class))
                .thenReturn(Optional.of(mockWorkflowContextState2));

        // create mock entity to hold the descriptor
        when(mockPatientEntity.getHandle()).thenReturn(PATIENT_CONTEXT_DESCRIPTOR_HANDLE);
        when(mockLocationEntity.getHandle()).thenReturn(LOCATION_CONTEXT_DESCRIPTOR_HANDLE);
        when(mockEnsembleEntity.getHandle()).thenReturn(ENSEMBLE_CONTEXT_DESCRIPTOR_HANDLE);
        when(mockMeansEntity.getHandle()).thenReturn(MEANS_CONTEXT_DESCRIPTOR_HANDLE);
        when(mockOperatorEntity.getHandle()).thenReturn(OPERATOR_CONTEXT_DESCRIPTOR_HANDLE);
        when(mockWorkflowEntity.getHandle()).thenReturn(WORKFLOW_CONTEXT_DESCRIPTOR_HANDLE);

        // return mock entities on request
        when(mockDevice.getMdibAccess().findEntitiesByType(PatientContextDescriptor.class))
                .thenReturn(List.of(mockPatientEntity));
        when(mockDevice.getMdibAccess().findEntitiesByType(LocationContextDescriptor.class))
                .thenReturn(List.of(mockLocationEntity));
        when(mockDevice.getMdibAccess().findEntitiesByType(EnsembleContextDescriptor.class))
                .thenReturn(List.of(mockEnsembleEntity));
        when(mockDevice.getMdibAccess().findEntitiesByType(MeansContextDescriptor.class))
                .thenReturn(List.of(mockMeansEntity));
        when(mockDevice.getMdibAccess().findEntitiesByType(OperatorContextDescriptor.class))
                .thenReturn(List.of(mockOperatorEntity));
        when(mockDevice.getMdibAccess().findEntitiesByType(WorkflowContextDescriptor.class))
                .thenReturn(List.of(mockWorkflowEntity));

        ConditionalPreconditions.AllKindsOfContextStatesAssociatedPrecondition.ALREADY_ASSOCIATED_CONTEXTS
                .values()
                .forEach(Set::clear);
    }

    private void setHandlesAndAssociation(
            final AbstractContextState state, final String descriptorHandle, final String handle) {
        state.setContextAssociation(com.draeger.medical.biceps.model.participant.ContextAssociation.ASSOC);
        state.setDescriptorHandle(descriptorHandle);
        state.setHandle(handle);
    }

    private void allKindsOfContextStatesAssociatedManipulationFailed() throws Exception {
        assertFalse(
                ConditionalPreconditions.AllKindsOfContextStatesAssociatedPrecondition.manipulation(testInjector),
                "manipulation should have failed.");
        assertTrue(testRunObserver.isInvalid(), "Test run should have been invalid.");
    }

    private void allKindsOfContextStatesAssociatedManipulationSucceeded() throws Exception {
        assertTrue(
                ConditionalPreconditions.AllKindsOfContextStatesAssociatedPrecondition.manipulation(testInjector),
                "manipulation should have succeeded.");
        assertFalse(testRunObserver.isInvalid(), "Test run should have been valid.");
    }

    /**
     * Tests whether the different TriggerReportPreconditions correctly check for precondition.
     *
     * @throws IOException           on io exceptions
     * @throws PreconditionException on precondition exceptions
     */
    @Test
    @DisplayName("Different TriggerReportPreconditions correctly check for precondition")
    public void testTriggerReportPreconditionCheck() throws IOException, PreconditionException {
        final var mockStorage = mock(MessageStorage.class);
        final MessageStorage.GetterResult<MessageContent> mockGetter = mock(MessageStorage.GetterResult.class);
        // TriggerEpisodicAlertReportPrecondition
        {
            when(mockGetter.areObjectsPresent()).thenReturn(true).thenReturn(false);

            when(mockStorage.getInboundMessagesByBodyType(ArgumentMatchers.<QName>any()))
                    .thenReturn(mockGetter);

            final var injector = Guice.createInjector(new AbstractModule() {
                @Override
                protected void configure() {
                    bind(MessageStorage.class).toInstance(mockStorage);
                }
            });

            assertTrue(ConditionalPreconditions.TriggerEpisodicAlertReportPrecondition.preconditionCheck(injector));

            // no messages
            assertFalse(ConditionalPreconditions.TriggerEpisodicAlertReportPrecondition.preconditionCheck(injector));
        }
        // TriggerEpisodicComponentReportPrecondition
        {
            when(mockGetter.areObjectsPresent()).thenReturn(true).thenReturn(false);

            when(mockStorage.getInboundMessagesByBodyType(ArgumentMatchers.<QName>any()))
                    .thenReturn(mockGetter);

            final var injector = Guice.createInjector(new AbstractModule() {
                @Override
                protected void configure() {
                    bind(MessageStorage.class).toInstance(mockStorage);
                }
            });

            assertTrue(ConditionalPreconditions.TriggerEpisodicComponentReportPrecondition.preconditionCheck(injector));

            // no messages
            assertFalse(
                    ConditionalPreconditions.TriggerEpisodicComponentReportPrecondition.preconditionCheck(injector));
        }
        // TriggerEpisodicContextReportPrecondition
        {
            when(mockGetter.areObjectsPresent()).thenReturn(true).thenReturn(false);

            when(mockStorage.getInboundMessagesByBodyType(ArgumentMatchers.<QName>any()))
                    .thenReturn(mockGetter);

            final var injector = Guice.createInjector(new AbstractModule() {
                @Override
                protected void configure() {
                    bind(MessageStorage.class).toInstance(mockStorage);
                }
            });

            assertTrue(ConditionalPreconditions.TriggerEpisodicContextReportPrecondition.preconditionCheck(injector));

            // no messages
            assertFalse(ConditionalPreconditions.TriggerEpisodicContextReportPrecondition.preconditionCheck(injector));
        }
        // TriggerEpisodicMetricReportPrecondition
        {
            when(mockGetter.areObjectsPresent()).thenReturn(true).thenReturn(false);

            when(mockStorage.getInboundMessagesByBodyType(ArgumentMatchers.<QName>any()))
                    .thenReturn(mockGetter);

            final var injector = Guice.createInjector(new AbstractModule() {
                @Override
                protected void configure() {
                    bind(MessageStorage.class).toInstance(mockStorage);
                }
            });

            assertTrue(ConditionalPreconditions.TriggerEpisodicMetricReportPrecondition.preconditionCheck(injector));

            // no messages
            assertFalse(ConditionalPreconditions.TriggerEpisodicMetricReportPrecondition.preconditionCheck(injector));
        }
        // TriggerEpisodicOperationalStateReportPrecondition
        {
            when(mockGetter.areObjectsPresent()).thenReturn(true).thenReturn(false);

            when(mockStorage.getInboundMessagesByBodyType(ArgumentMatchers.<QName>any()))
                    .thenReturn(mockGetter);

            final var injector = Guice.createInjector(new AbstractModule() {
                @Override
                protected void configure() {
                    bind(MessageStorage.class).toInstance(mockStorage);
                }
            });

            assertTrue(ConditionalPreconditions.TriggerEpisodicOperationalStateReportPrecondition.preconditionCheck(
                    injector));

            // no messages
            assertFalse(ConditionalPreconditions.TriggerEpisodicOperationalStateReportPrecondition.preconditionCheck(
                    injector));
        }
        // TriggerOperationInvokedReportPrecondition
        {
            when(mockGetter.areObjectsPresent()).thenReturn(true).thenReturn(false);

            when(mockStorage.getInboundMessagesByBodyType(ArgumentMatchers.<QName>any()))
                    .thenReturn(mockGetter);

            final var injector = Guice.createInjector(new AbstractModule() {
                @Override
                protected void configure() {
                    bind(MessageStorage.class).toInstance(mockStorage);
                }
            });

            assertTrue(ConditionalPreconditions.TriggerOperationInvokedReportPrecondition.preconditionCheck(injector));

            // no messages
            assertFalse(ConditionalPreconditions.TriggerOperationInvokedReportPrecondition.preconditionCheck(injector));
        }
    }

    /**
     * Tests whether the different TriggerReportPreconditions correctly call manipulation.
     */
    @Test
    @DisplayName("Different TriggerReportPreconditions correctly call manipulation")
    public void testTriggerReportPreconditionManipulation() {
        final var manipulations = mock(Manipulations.class);
        // TriggerEpisodicAlertReportPrecondition
        {
            when(manipulations.triggerReport(Constants.MSG_EPISODIC_ALERT_REPORT))
                    .thenReturn(ResponseTypes.Result.RESULT_SUCCESS)
                    .thenReturn(ResponseTypes.Result.RESULT_FAIL);

            final var injector = Guice.createInjector(new AbstractModule() {
                @Override
                protected void configure() {
                    bind(Manipulations.class).toInstance(manipulations);
                }
            });

            assertTrue(ConditionalPreconditions.TriggerEpisodicAlertReportPrecondition.manipulation(injector));

            verify(manipulations, times(1)).triggerReport(Constants.MSG_EPISODIC_ALERT_REPORT);

            // second call must return false
            assertFalse(ConditionalPreconditions.TriggerEpisodicAlertReportPrecondition.manipulation(injector));
        }
        // TriggerEpisodicComponentReportPrecondition
        {
            when(manipulations.triggerReport(Constants.MSG_EPISODIC_COMPONENT_REPORT))
                    .thenReturn(ResponseTypes.Result.RESULT_SUCCESS)
                    .thenReturn(ResponseTypes.Result.RESULT_FAIL);

            final var injector = Guice.createInjector(new AbstractModule() {
                @Override
                protected void configure() {
                    bind(Manipulations.class).toInstance(manipulations);
                }
            });

            assertTrue(ConditionalPreconditions.TriggerEpisodicComponentReportPrecondition.manipulation(injector));

            verify(manipulations, times(1)).triggerReport(Constants.MSG_EPISODIC_COMPONENT_REPORT);

            // second call must return false
            assertFalse(ConditionalPreconditions.TriggerEpisodicComponentReportPrecondition.manipulation(injector));
        }
        // TriggerEpisodicContextReportPrecondition
        {
            when(manipulations.triggerReport(Constants.MSG_EPISODIC_CONTEXT_REPORT))
                    .thenReturn(ResponseTypes.Result.RESULT_SUCCESS)
                    .thenReturn(ResponseTypes.Result.RESULT_FAIL);

            final var injector = Guice.createInjector(new AbstractModule() {
                @Override
                protected void configure() {
                    bind(Manipulations.class).toInstance(manipulations);
                }
            });

            assertTrue(ConditionalPreconditions.TriggerEpisodicContextReportPrecondition.manipulation(injector));

            verify(manipulations, times(1)).triggerReport(Constants.MSG_EPISODIC_CONTEXT_REPORT);

            // second call must return false
            assertFalse(ConditionalPreconditions.TriggerEpisodicContextReportPrecondition.manipulation(injector));
        }
        // TriggerEpisodicMetricReportPrecondition
        {
            when(manipulations.triggerReport(Constants.MSG_EPISODIC_METRIC_REPORT))
                    .thenReturn(ResponseTypes.Result.RESULT_SUCCESS)
                    .thenReturn(ResponseTypes.Result.RESULT_FAIL);

            final var injector = Guice.createInjector(new AbstractModule() {
                @Override
                protected void configure() {
                    bind(Manipulations.class).toInstance(manipulations);
                }
            });

            assertTrue(ConditionalPreconditions.TriggerEpisodicMetricReportPrecondition.manipulation(injector));

            verify(manipulations, times(1)).triggerReport(Constants.MSG_EPISODIC_METRIC_REPORT);

            // second call must return false
            assertFalse(ConditionalPreconditions.TriggerEpisodicMetricReportPrecondition.manipulation(injector));
        }
        // TriggerEpisodicOperationalStateReportPrecondition
        {
            when(manipulations.triggerReport(Constants.MSG_EPISODIC_OPERATIONAL_STATE_REPORT))
                    .thenReturn(ResponseTypes.Result.RESULT_SUCCESS)
                    .thenReturn(ResponseTypes.Result.RESULT_FAIL);

            final var injector = Guice.createInjector(new AbstractModule() {
                @Override
                protected void configure() {
                    bind(Manipulations.class).toInstance(manipulations);
                }
            });

            assertTrue(
                    ConditionalPreconditions.TriggerEpisodicOperationalStateReportPrecondition.manipulation(injector));

            verify(manipulations, times(1)).triggerReport(Constants.MSG_EPISODIC_OPERATIONAL_STATE_REPORT);

            // second call must return false
            assertFalse(
                    ConditionalPreconditions.TriggerEpisodicOperationalStateReportPrecondition.manipulation(injector));
        }
        // TriggerOperationInvokedReportPrecondition
        {
            when(manipulations.triggerReport(Constants.MSG_OPERATION_INVOKED_REPORT))
                    .thenReturn(ResponseTypes.Result.RESULT_SUCCESS)
                    .thenReturn(ResponseTypes.Result.RESULT_FAIL);

            final var injector = Guice.createInjector(new AbstractModule() {
                @Override
                protected void configure() {
                    bind(Manipulations.class).toInstance(manipulations);
                }
            });

            assertTrue(ConditionalPreconditions.TriggerOperationInvokedReportPrecondition.manipulation(injector));

            verify(manipulations, times(1)).triggerReport(Constants.MSG_OPERATION_INVOKED_REPORT);

            // second call must return false
            assertFalse(ConditionalPreconditions.TriggerOperationInvokedReportPrecondition.manipulation(injector));
        }
    }

    /**
     * Tests whether StateChangedPrecondition correctly check for precondition.
     *
     * @throws Exception on any exception
     */
    @Test
    @DisplayName("StateChangedPrecondition correctly checks for preconditions")
    public void testStateChangedPreconditionCheck() throws Exception {
        final var mockStorage = mock(MessageStorage.class);
        final MessageStorage.GetterResult<MessageContent> mockGetter = mock(MessageStorage.GetterResult.class);
        {
            when(mockGetter.areObjectsPresent()).thenReturn(true).thenReturn(false);
            when(mockStorage.getInboundMessagesByBodyType(ArgumentMatchers.<QName>any()))
                    .thenReturn(mockGetter);
            final var injector = Guice.createInjector(new AbstractModule() {
                @Override
                protected void configure() {
                    bind(MessageStorage.class).toInstance(mockStorage);
                }
            });
            assertTrue(ConditionalPreconditions.StateChangedPrecondition.preconditionCheck(injector));
            // no messages
            assertFalse(ConditionalPreconditions.StateChangedPrecondition.preconditionCheck(injector));
        }
        // just metric reports available
        {
            when(mockGetter.areObjectsPresent()).thenReturn(true).thenReturn(false);
            when(mockStorage.getInboundMessagesByBodyType(Constants.MSG_EPISODIC_METRIC_REPORT))
                    .thenReturn(mockGetter);
            final MessageStorage.GetterResult<MessageContent> mockGetter2 = mock(MessageStorage.GetterResult.class);
            when(mockGetter2.areObjectsPresent()).thenReturn(false);
            when(mockStorage.getInboundMessagesByBodyType(
                            Constants.MSG_EPISODIC_ALERT_REPORT,
                            Constants.MSG_EPISODIC_COMPONENT_REPORT,
                            Constants.MSG_EPISODIC_OPERATIONAL_STATE_REPORT,
                            Constants.MSG_EPISODIC_CONTEXT_REPORT))
                    .thenReturn(mockGetter2);
            final var injector = Guice.createInjector(new AbstractModule() {
                @Override
                protected void configure() {
                    bind(MessageStorage.class).toInstance(mockStorage);
                }
            });
            assertTrue(ConditionalPreconditions.StateChangedPrecondition.preconditionCheck(injector));
            // no messages
            assertFalse(ConditionalPreconditions.StateChangedPrecondition.preconditionCheck(injector));
        }
        // no reports available
        {
            when(mockGetter.areObjectsPresent()).thenReturn(false);
            when(mockStorage.getInboundMessagesByBodyType(ArgumentMatchers.<QName>any()))
                    .thenReturn(mockGetter);
            final var injector = Guice.createInjector(new AbstractModule() {
                @Override
                protected void configure() {
                    bind(MessageStorage.class).toInstance(mockStorage);
                }
            });
            assertFalse(ConditionalPreconditions.StateChangedPrecondition.preconditionCheck(injector));
        }
    }

    /**
     * Tests whether StateChangedPrecondition correctly calls manipulation.
     */
    @Test
    @DisplayName("StateChangedPrecondition correctly calls manipulation")
    public void testStateChangedPreconditionManipulation() {
        final var expectedManipulationCalls = 5;
        {
            final var manipulations = mock(Manipulations.class);
            when(manipulations.triggerReport(Constants.MSG_EPISODIC_ALERT_REPORT))
                    .thenReturn(ResponseTypes.Result.RESULT_SUCCESS)
                    .thenReturn(ResponseTypes.Result.RESULT_FAIL);
            when(manipulations.triggerReport(Constants.MSG_EPISODIC_COMPONENT_REPORT))
                    .thenReturn(ResponseTypes.Result.RESULT_SUCCESS)
                    .thenReturn(ResponseTypes.Result.RESULT_FAIL);
            when(manipulations.triggerReport(Constants.MSG_EPISODIC_METRIC_REPORT))
                    .thenReturn(ResponseTypes.Result.RESULT_SUCCESS)
                    .thenReturn(ResponseTypes.Result.RESULT_FAIL);
            when(manipulations.triggerReport(Constants.MSG_EPISODIC_OPERATIONAL_STATE_REPORT))
                    .thenReturn(ResponseTypes.Result.RESULT_SUCCESS)
                    .thenReturn(ResponseTypes.Result.RESULT_FAIL);
            when(manipulations.triggerReport(Constants.MSG_EPISODIC_CONTEXT_REPORT))
                    .thenReturn(ResponseTypes.Result.RESULT_SUCCESS)
                    .thenReturn(ResponseTypes.Result.RESULT_FAIL);
            final var injector = Guice.createInjector(new AbstractModule() {
                @Override
                protected void configure() {
                    bind(Manipulations.class).toInstance(manipulations);
                }
            });
            assertTrue(ConditionalPreconditions.StateChangedPrecondition.manipulation(injector));
            verify(manipulations, times(expectedManipulationCalls)).triggerReport(any());
            assertFalse(ConditionalPreconditions.StateChangedPrecondition.manipulation(injector));
        }
        // just one manipulation supported
        {
            final var manipulations = mock(Manipulations.class);
            when(manipulations.triggerReport(Constants.MSG_EPISODIC_ALERT_REPORT))
                    .thenReturn(ResponseTypes.Result.RESULT_NOT_SUPPORTED);
            when(manipulations.triggerReport(Constants.MSG_EPISODIC_COMPONENT_REPORT))
                    .thenReturn(ResponseTypes.Result.RESULT_NOT_SUPPORTED);
            when(manipulations.triggerReport(Constants.MSG_EPISODIC_METRIC_REPORT))
                    .thenReturn(ResponseTypes.Result.RESULT_NOT_SUPPORTED);
            when(manipulations.triggerReport(Constants.MSG_EPISODIC_OPERATIONAL_STATE_REPORT))
                    .thenReturn(ResponseTypes.Result.RESULT_NOT_SUPPORTED);
            when(manipulations.triggerReport(Constants.MSG_EPISODIC_CONTEXT_REPORT))
                    .thenReturn(ResponseTypes.Result.RESULT_SUCCESS)
                    .thenReturn(ResponseTypes.Result.RESULT_FAIL);
            final var injector = Guice.createInjector(new AbstractModule() {
                @Override
                protected void configure() {
                    bind(Manipulations.class).toInstance(manipulations);
                }
            });
            assertTrue(ConditionalPreconditions.StateChangedPrecondition.manipulation(injector));
            verify(manipulations, times(expectedManipulationCalls)).triggerReport(any());
            assertFalse(ConditionalPreconditions.StateChangedPrecondition.manipulation(injector));
        }
        // a manipulation is not implemented
        {
            final var manipulations = mock(Manipulations.class);
            when(manipulations.triggerReport(Constants.MSG_EPISODIC_ALERT_REPORT))
                    .thenReturn(ResponseTypes.Result.RESULT_SUCCESS);
            when(manipulations.triggerReport(Constants.MSG_EPISODIC_COMPONENT_REPORT))
                    .thenReturn(ResponseTypes.Result.RESULT_SUCCESS);
            when(manipulations.triggerReport(Constants.MSG_EPISODIC_METRIC_REPORT))
                    .thenReturn(ResponseTypes.Result.RESULT_NOT_IMPLEMENTED);
            when(manipulations.triggerReport(Constants.MSG_EPISODIC_OPERATIONAL_STATE_REPORT))
                    .thenReturn(ResponseTypes.Result.RESULT_SUCCESS);
            when(manipulations.triggerReport(Constants.MSG_EPISODIC_CONTEXT_REPORT))
                    .thenReturn(ResponseTypes.Result.RESULT_SUCCESS);
            final var injector = Guice.createInjector(new AbstractModule() {
                @Override
                protected void configure() {
                    bind(Manipulations.class).toInstance(manipulations);
                }
            });

            assertFalse(ConditionalPreconditions.StateChangedPrecondition.manipulation(injector));
        }
        // a manipulation fails
        {
            final var manipulations = mock(Manipulations.class);
            when(manipulations.triggerReport(Constants.MSG_EPISODIC_ALERT_REPORT))
                    .thenReturn(ResponseTypes.Result.RESULT_SUCCESS);
            when(manipulations.triggerReport(Constants.MSG_EPISODIC_COMPONENT_REPORT))
                    .thenReturn(ResponseTypes.Result.RESULT_SUCCESS);
            when(manipulations.triggerReport(Constants.MSG_EPISODIC_METRIC_REPORT))
                    .thenReturn(ResponseTypes.Result.RESULT_SUCCESS);
            when(manipulations.triggerReport(Constants.MSG_EPISODIC_OPERATIONAL_STATE_REPORT))
                    .thenReturn(ResponseTypes.Result.RESULT_FAIL);
            when(manipulations.triggerReport(Constants.MSG_EPISODIC_CONTEXT_REPORT))
                    .thenReturn(ResponseTypes.Result.RESULT_SUCCESS);
            final var injector = Guice.createInjector(new AbstractModule() {
                @Override
                protected void configure() {
                    bind(Manipulations.class).toInstance(manipulations);
                }
            });
            assertFalse(ConditionalPreconditions.StateChangedPrecondition.manipulation(injector));
        }
    }
}
