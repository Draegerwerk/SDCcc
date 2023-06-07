/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2023 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.tests.mdpws.direct;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.draeger.medical.sdccc.sdcri.testclient.TestClient;
import com.draeger.medical.sdccc.sdcri.testclient.TestClientUtil;
import com.draeger.medical.sdccc.tests.InjectorTestBase;
import com.draeger.medical.sdccc.tests.test_util.InjectorUtil;
import com.draeger.medical.sdccc.util.HttpClientUtil;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import org.apache.http.client.HttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.somda.sdc.biceps.model.message.ObjectFactory;
import org.somda.sdc.dpws.helper.JaxbMarshalling;
import org.somda.sdc.dpws.http.apache.ApacheTransportBindingFactoryImpl;
import org.somda.sdc.dpws.service.HostedServiceProxy;
import org.somda.sdc.dpws.soap.SoapConstants;
import org.somda.sdc.dpws.soap.SoapMessage;
import org.somda.sdc.dpws.soap.SoapUtil;
import org.somda.sdc.dpws.soap.exception.SoapFaultException;
import org.somda.sdc.dpws.wsdl.WsdlMarshalling;
import org.somda.sdc.dpws.wsdl.WsdlRetriever;
import org.somda.sdc.glue.common.ActionConstants;
import org.somda.sdc.glue.common.WsdlConstants;
import org.somda.sdc.glue.provider.SdcDevice;

/**
 * Unit tests for MDPWS:R0013.
 */
public class DirectWSDLR0013Test {

    private static final Duration MAX_WAIT = Duration.ofSeconds(10);

    private DirectWSDLTest testClass;
    private TestClient testClient;
    private HttpClientUtil mockClientUtil;
    private SoapUtil soapUtil;
    private ObjectFactory messageFactory;
    private WsdlRetriever mockWsdlRetriever;
    private JaxbMarshalling jaxbMarshalling;
    private WsdlMarshalling wsdlMarshalling;

    @BeforeEach
    void setUp() throws IOException, TimeoutException {
        final TestClient mockClient = mock(TestClient.class, Mockito.RETURNS_DEEP_STUBS);
        when(mockClient.isClientRunning()).thenReturn(true);
        testClient = mockClient;
        final var mockHttpClient = mock(HttpClient.class);
        mockClientUtil = mock(HttpClientUtil.class);
        mockWsdlRetriever = mock(WsdlRetriever.class);

        final Injector injector = InjectorUtil.setupInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(TestClient.class).toInstance(testClient);
            }
        });
        InjectorTestBase.setInjector(injector);

        final var mockFactory = mock(ApacheTransportBindingFactoryImpl.class);
        when(mockFactory.getClient()).thenReturn(mockHttpClient);
        // setup the injector used by sdcri
        final var clientInjector = TestClientUtil.createClientInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(ApacheTransportBindingFactoryImpl.class).toInstance(mockFactory);
                bind(HttpClientUtil.class).toInstance(mockClientUtil);
                bind(WsdlRetriever.class).toInstance(mockWsdlRetriever);
            }
        });
        when(testClient.getInjector()).thenReturn(clientInjector);

        soapUtil = testClient.getInjector().getInstance(SoapUtil.class);
        messageFactory = testClient.getInjector().getInstance(ObjectFactory.class);

        jaxbMarshalling = testClient.getInjector().getInstance(JaxbMarshalling.class);
        wsdlMarshalling = testClient.getInjector().getInstance(WsdlMarshalling.class);

        jaxbMarshalling.startAsync().awaitRunning(MAX_WAIT);
        wsdlMarshalling.startAsync().awaitRunning(MAX_WAIT);

        testClass = new DirectWSDLTest();
        testClass.setUp();
    }

    @AfterEach
    void tearDown() throws TimeoutException {
        wsdlMarshalling.stopAsync().awaitTerminated(MAX_WAIT);
        jaxbMarshalling.stopAsync().awaitTerminated(MAX_WAIT);
    }

    /**
     * Tests whether correct faults are causing a passing test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR0013Good() throws Exception {
        final var mockFault = mock(SoapFaultException.class, Mockito.RETURNS_DEEP_STUBS);
        when(mockFault.getFault().getCode().getValue())
                .thenReturn(SoapConstants.VERSION_MISMATCH)
                .thenReturn(SoapConstants.MUST_UNDERSTAND)
                .thenReturn(SoapConstants.SENDER);

        final var getMdibResponse = soapUtil.createMessage(
                ActionConstants.getResponseAction(ActionConstants.ACTION_GET_MDIB),
                messageFactory.createGetMdibResponse());

        final var mockGetService = mock(HostedServiceProxy.class, Mockito.RETURNS_DEEP_STUBS);
        when(mockGetService.getType().getTypes()).thenReturn(List.of(WsdlConstants.PORT_TYPE_GET_QNAME));
        when(mockGetService.sendRequestResponse(any()))
                .thenReturn(getMdibResponse) // first request is valid, give valid response
                .thenThrow(mockFault);
        final var getServiceName = "get";
        when(testClient.getHostingServiceProxy().getHostedServices())
                .thenReturn(Map.of(getServiceName, mockGetService));

        // load SDCri wsdl for GetService
        final var wsdlPath = "wsdl/IEEE11073-20701-HighPriority-Services.wsdl";
        final var loader = SdcDevice.class.getClassLoader();
        final String wsdl;
        try (final var wsdlStream = loader.getResourceAsStream(wsdlPath)) {
            assertNotNull(wsdlStream);
            wsdl = new String(wsdlStream.readAllBytes(), StandardCharsets.UTF_8);
        }
        when(mockWsdlRetriever.retrieveWsdls(any())).thenReturn(Map.of(getServiceName, List.of(wsdl)));

        when(mockClientUtil.postMessage(any(), any(), any())).thenThrow(mockFault);

        testClass.testRequirementR0013();

        final var clientUtilInvocations = 1;
        final var serviceInvocations = 3;
        verify(mockClientUtil, times(clientUtilInvocations)).postMessage(any(), any(), any());
        verify(mockGetService, times(serviceInvocations)).sendRequestResponse(any());
    }

    /**
     * Tests whether no fault for the message containing all errors causes a failed test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR0013AllBadNoFault() throws Exception {
        final var mockGetService = mock(HostedServiceProxy.class, Mockito.RETURNS_DEEP_STUBS);
        when(mockGetService.getType().getTypes()).thenReturn(List.of(WsdlConstants.PORT_TYPE_GET_QNAME));
        when(testClient.getHostingServiceProxy().getHostedServices()).thenReturn(Map.of("get", mockGetService));
        when(mockClientUtil.postMessage(any(), any(), any())).thenReturn(mock(SoapMessage.class));

        final var error = assertThrows(AssertionError.class, testClass::testRequirementR0013All);
        assertTrue(error.getMessage().contains(DirectWSDLTest.NO_FAULT_TEMPLATE));
    }

    /**
     * Tests whether a wrong fault for the message containing all errors causes a failed test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR0013AllBadWrongFault() throws Exception {
        final var mockFault = mock(SoapFaultException.class, Mockito.RETURNS_DEEP_STUBS);
        when(mockFault.getFault().getCode().getValue()).thenReturn(SoapConstants.RECEIVER);

        final var mockGetService = mock(HostedServiceProxy.class, Mockito.RETURNS_DEEP_STUBS);
        when(mockGetService.getType().getTypes()).thenReturn(List.of(WsdlConstants.PORT_TYPE_GET_QNAME));
        when(testClient.getHostingServiceProxy().getHostedServices()).thenReturn(Map.of("get", mockGetService));
        when(mockClientUtil.postMessage(any(), any(), any())).thenThrow(mockFault);

        final var error = assertThrows(AssertionError.class, testClass::testRequirementR0013All);
        assertTrue(error.getMessage().contains(DirectWSDLTest.WRONG_FAULT_TEMPLATE));
    }

    /**
     * Tests whether no fault for the must understand and sender fault message fails the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR0013MustUnderstandSenderBadNoFault() throws Exception {
        final var mockGetService = mock(HostedServiceProxy.class, Mockito.RETURNS_DEEP_STUBS);
        when(mockGetService.getType().getTypes()).thenReturn(List.of(WsdlConstants.PORT_TYPE_GET_QNAME));
        when(mockGetService.sendRequestResponse(any())).thenReturn(mock(SoapMessage.class));
        when(testClient.getHostingServiceProxy().getHostedServices()).thenReturn(Map.of("get", mockGetService));

        final var error = assertThrows(AssertionError.class, testClass::testRequirementR0013MustUnderstandSender);
        assertTrue(error.getMessage().contains(DirectWSDLTest.NO_FAULT_TEMPLATE));
    }

    /**
     * Tests whether a wrong fault for the must understand and sender fault message fails the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR0013MustUnderstandSenderBadWrongFault() throws Exception {
        final var mockFault = mock(SoapFaultException.class, Mockito.RETURNS_DEEP_STUBS);
        when(mockFault.getFault().getCode().getValue()).thenReturn(SoapConstants.VERSION_MISMATCH);

        final var mockGetService = mock(HostedServiceProxy.class, Mockito.RETURNS_DEEP_STUBS);
        when(mockGetService.getType().getTypes()).thenReturn(List.of(WsdlConstants.PORT_TYPE_GET_QNAME));
        when(mockGetService.sendRequestResponse(any())).thenThrow(mockFault);
        when(testClient.getHostingServiceProxy().getHostedServices()).thenReturn(Map.of("get", mockGetService));

        final var error = assertThrows(AssertionError.class, testClass::testRequirementR0013MustUnderstandSender);
        assertTrue(error.getMessage().contains(DirectWSDLTest.WRONG_FAULT_TEMPLATE));
    }

    /**
     * Tests whether no fault for the sender fault message fails the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR0013SenderBadNoFault() throws Exception {
        final var mockGetService = mock(HostedServiceProxy.class, Mockito.RETURNS_DEEP_STUBS);
        when(mockGetService.getType().getTypes()).thenReturn(List.of(WsdlConstants.PORT_TYPE_GET_QNAME));
        when(mockGetService.sendRequestResponse(any())).thenReturn(mock(SoapMessage.class));
        when(testClient.getHostingServiceProxy().getHostedServices()).thenReturn(Map.of("get", mockGetService));

        final var error = assertThrows(AssertionError.class, testClass::testRequirementR0013Sender);
        assertTrue(error.getMessage().contains(DirectWSDLTest.NO_FAULT_TEMPLATE));
    }

    /**
     * Tests whether a wrong fault for the sender fault message fails the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR0013SenderBadWrongFault() throws Exception {
        final var mockFault = mock(SoapFaultException.class, Mockito.RETURNS_DEEP_STUBS);
        when(mockFault.getFault().getCode().getValue()).thenReturn(SoapConstants.VERSION_MISMATCH);

        final var mockGetService = mock(HostedServiceProxy.class, Mockito.RETURNS_DEEP_STUBS);
        when(mockGetService.getType().getTypes()).thenReturn(List.of(WsdlConstants.PORT_TYPE_GET_QNAME));
        when(mockGetService.sendRequestResponse(any())).thenThrow(mockFault);
        when(testClient.getHostingServiceProxy().getHostedServices()).thenReturn(Map.of("get", mockGetService));

        final var error = assertThrows(AssertionError.class, testClass::testRequirementR0013Sender);
        assertTrue(error.getMessage().contains(DirectWSDLTest.WRONG_FAULT_TEMPLATE));
    }
}
