/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2023 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.tests.dpws.direct;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.draeger.medical.sdccc.configuration.TestSuiteConfig;
import com.draeger.medical.sdccc.marshalling.MarshallingUtil;
import com.draeger.medical.sdccc.sdcri.testclient.TestClient;
import com.draeger.medical.sdccc.sdcri.testclient.TestClientUtil;
import com.draeger.medical.sdccc.tests.InjectorTestBase;
import com.draeger.medical.sdccc.tests.test_util.InjectorUtil;
import com.draeger.medical.sdccc.tests.util.NoTestData;
import com.draeger.medical.sdccc.util.HttpClientUtil;
import com.draeger.medical.sdccc.util.MdibBuilder;
import com.draeger.medical.sdccc.util.MessageBuilder;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Names;
import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import javax.xml.namespace.QName;
import org.apache.http.HttpResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.somda.sdc.biceps.model.message.ObjectFactory;
import org.somda.sdc.common.guice.AbstractConfigurationModule;
import org.somda.sdc.dpws.DpwsConfig;
import org.somda.sdc.dpws.helper.JaxbMarshalling;
import org.somda.sdc.dpws.http.apache.ApacheTransportBindingFactoryImpl;
import org.somda.sdc.dpws.model.HostedServiceType;
import org.somda.sdc.dpws.service.HostedServiceProxy;
import org.somda.sdc.dpws.service.HostingServiceProxy;
import org.somda.sdc.dpws.soap.SoapConstants;
import org.somda.sdc.dpws.soap.SoapMarshalling;
import org.somda.sdc.dpws.soap.SoapMessage;
import org.somda.sdc.dpws.soap.SoapUtil;
import org.somda.sdc.dpws.soap.exception.MarshallingException;
import org.somda.sdc.dpws.soap.exception.SoapFaultException;
import org.somda.sdc.dpws.soap.wsaddressing.WsAddressingConstants;
import org.somda.sdc.glue.common.ActionConstants;
import org.somda.sdc.glue.common.WsdlConstants;

/**
 * Unit test for the DPWS {@linkplain DirectMessagingTest}.
 */
public class DirectMessagingTestTest {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);
    private static final String INVALID_ADDRESSING_HEADER = "InvalidAddressingHeader";
    private static final String INVALID_ADDRESSING_HEADER_REASON =
            "A header representing a Message Addressing Property is not valid and the message cannot be processed";

    private static MdibBuilder mdibBuilder;
    private static MessageBuilder messageBuilder;
    private static com.draeger.medical.sdccc.marshalling.SoapMarshalling soapMarshallingForHttpClient;

    private DirectMessagingTest testClass;
    private TestClient testClient;
    private ObjectFactory messageModelFactory;
    private org.somda.sdc.dpws.soap.factory.SoapFaultFactory soapFaultFactory;
    private SoapUtil soapUtil;
    private JaxbMarshalling baseMarshalling;
    private SoapMarshalling soapMarshalling;
    private HttpClientUtil httpClientUtil;
    private ApacheTransportBindingFactoryImpl mockApacheFactory;

    @BeforeAll
    static void setupMarshalling() {
        final Injector marshallingInjector = MarshallingUtil.createMarshallingTestInjector(true);
        mdibBuilder = marshallingInjector.getInstance(MdibBuilder.class);
        messageBuilder = marshallingInjector.getInstance(MessageBuilder.class);
        soapMarshallingForHttpClient =
                marshallingInjector.getInstance(com.draeger.medical.sdccc.marshalling.SoapMarshalling.class);
    }

    @BeforeEach
    void setUp() throws IOException, TimeoutException {
        final TestClient mockClient = mock(TestClient.class);
        when(mockClient.isClientRunning()).thenReturn(true);
        testClient = mockClient;
        httpClientUtil = mock(HttpClientUtil.class);
        mockApacheFactory = mock(ApacheTransportBindingFactoryImpl.class);

        final Injector injector = InjectorUtil.setupInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(TestClient.class).toInstance(testClient);
                bind(Key.get(Boolean.class, Names.named(TestSuiteConfig.SUMMARIZE_MESSAGE_ENCODING_ERRORS)))
                        .toInstance(true);
            }
        });
        InjectorTestBase.setInjector(injector);

        // setup the injector used by sdcri
        final var clientInjector = TestClientUtil.createClientInjector(
                new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(HttpClientUtil.class).toInstance(httpClientUtil);
                        bind(ApacheTransportBindingFactoryImpl.class).toInstance(mockApacheFactory);
                    }
                },
                new AbstractConfigurationModule() {
                    @Override
                    protected void defaultConfigure() {
                        bind(DpwsConfig.HTTP_SUPPORT, Boolean.class, false);
                        bind(DpwsConfig.HTTPS_SUPPORT, Boolean.class, true);
                    }
                });
        when(testClient.getInjector()).thenReturn(clientInjector);

        soapUtil = clientInjector.getInstance(SoapUtil.class);
        messageModelFactory = clientInjector.getInstance(ObjectFactory.class);
        soapFaultFactory = clientInjector.getInstance(org.somda.sdc.dpws.soap.factory.SoapFaultFactory.class);

        baseMarshalling = clientInjector.getInstance(JaxbMarshalling.class);
        baseMarshalling.startAsync().awaitRunning(DEFAULT_TIMEOUT);

        soapMarshalling = clientInjector.getInstance(SoapMarshalling.class);
        soapMarshalling.startAsync().awaitRunning(DEFAULT_TIMEOUT);

        testClass = new DirectMessagingTest();
        testClass.setUp();
    }

    @AfterEach
    void tearDown() throws TimeoutException {
        baseMarshalling.stopAsync().awaitTerminated(DEFAULT_TIMEOUT);
        soapMarshalling.stopAsync().awaitTerminated(DEFAULT_TIMEOUT);
    }

    /**
     * Tests whether no xAddresses cause the test to fail.
     */
    @Test
    public void testRequirementR0013NoXAddr() {
        when(testClient.getTargetXAddrs()).thenReturn(Collections.emptyList());
        assertThrows(NoTestData.class, testClass::testRequirement0013);
    }

    /**
     * Tests if an empty http response with good status code causes the test to pass.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR0013GoodRequestGoodCode() throws Exception {
        final var xAddresses = List.of("https://MeanAddress.location", "https://NastyAddress.location/");
        when(testClient.getTargetXAddrs()).thenReturn(xAddresses);
        final var mockResponse = createHttpResponseWithNoSoap(200);
        final var hostedServiceTypeMock = mock(HostedServiceType.class);
        final var hostedService = mock(HostedServiceProxy.class);
        when(hostedService.getType()).thenReturn(hostedServiceTypeMock);
        when(hostedService.getType().getTypes()).thenReturn(List.of(WsdlConstants.PORT_TYPE_GET_QNAME));
        when(httpClientUtil.postMessageWithHttpResponse(any(), any(), any())).thenReturn(mockResponse);

        final var map = Map.of("hostedService", hostedService);
        final var mockHostingService = mock(HostingServiceProxy.class);
        when(testClient.getHostingServiceProxy()).thenReturn(mockHostingService);
        when(mockHostingService.getHostedServices()).thenReturn(map);
        testClass.testRequirement0013Good();
    }

    /**
     * Tests if an empty http response with bad status code causes the test to fail.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR0013GoodRequestBadCode() throws Exception {
        final var xAddresses = List.of("https://MeanAddress.location", "https://NastyAddress.location/");
        when(testClient.getTargetXAddrs()).thenReturn(xAddresses);
        final var mockResponse = createHttpResponseWithNoSoap(400);
        final var hostedServiceTypeMock = mock(HostedServiceType.class);
        final var hostedService = mock(HostedServiceProxy.class);
        when(hostedService.getType()).thenReturn(hostedServiceTypeMock);
        when(hostedService.getType().getTypes()).thenReturn(List.of(WsdlConstants.PORT_TYPE_GET_QNAME));
        when(httpClientUtil.postMessageWithHttpResponse(any(), any(), any())).thenReturn(mockResponse);

        final var map = Map.of("hostedService", hostedService);
        final var mockHostingService = mock(HostingServiceProxy.class);
        when(testClient.getHostingServiceProxy()).thenReturn(mockHostingService);
        when(mockHostingService.getHostedServices()).thenReturn(map);
        assertThrows(AssertionError.class, testClass::testRequirement0013Good);
    }

    /**
     * Tests if an empty http response with bad status code causes the test to pass.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR0013BadRequestBadCode() throws Exception {
        final var xAddresses = List.of("https://MeanAddress.location", "https://NastyAddress.location/");
        when(testClient.getTargetXAddrs()).thenReturn(xAddresses);
        final var mockResponse = createHttpResponseWithNoSoap(400);
        final var hostedServiceTypeMock = mock(HostedServiceType.class);
        final var hostedService = mock(HostedServiceProxy.class);
        when(hostedService.getType()).thenReturn(hostedServiceTypeMock);
        when(hostedService.getType().getTypes()).thenReturn(List.of(WsdlConstants.PORT_TYPE_GET_QNAME));
        when(httpClientUtil.postMessageWithHttpResponse(any(), any(), any())).thenReturn(mockResponse);

        final var map = Map.of("hostedService", hostedService);
        final var mockHostingService = mock(HostingServiceProxy.class);
        when(testClient.getHostingServiceProxy()).thenReturn(mockHostingService);
        when(mockHostingService.getHostedServices()).thenReturn(map);
        testClass.testRequirement0013Bad();
    }

    /**
     * Tests if an empty http response with good status code causes the test to fail.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR0013BadRequestGoodCode() throws Exception {
        final var xAddresses = List.of("https://MeanAddress.location", "https://NastyAddress.location/");
        when(testClient.getTargetXAddrs()).thenReturn(xAddresses);
        final var mockResponse = createHttpResponseWithNoSoap(200);
        final var hostedServiceTypeMock = mock(HostedServiceType.class);
        final var hostedService = mock(HostedServiceProxy.class);
        when(hostedService.getType()).thenReturn(hostedServiceTypeMock);
        when(hostedService.getType().getTypes()).thenReturn(List.of(WsdlConstants.PORT_TYPE_GET_QNAME));
        when(httpClientUtil.postMessageWithHttpResponse(any(), any(), any())).thenReturn(mockResponse);

        final var map = Map.of("hostedService", hostedService);
        final var mockHostingService = mock(HostingServiceProxy.class);
        when(testClient.getHostingServiceProxy()).thenReturn(mockHostingService);
        when(mockHostingService.getHostedServices()).thenReturn(map);
        assertThrows(AssertionError.class, testClass::testRequirement0013Bad);
    }

    /**
     * Tests if a correct soap 1.2 envelope causes a passing test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR0034Good() throws Exception {
        final var mockResponse = createMdibResponseMessage();
        final var hostedServiceTypeMock = mock(HostedServiceType.class);
        final var hostedService = mock(HostedServiceProxy.class);
        when(hostedService.getType()).thenReturn(hostedServiceTypeMock);
        when(hostedService.getType().getTypes()).thenReturn(List.of(WsdlConstants.PORT_TYPE_GET_QNAME));
        when(hostedService.sendRequestResponse(any())).thenReturn(mockResponse);
        final var map = Map.of("hostedService", hostedService);
        final var mockHostingService = mock(HostingServiceProxy.class);
        when(testClient.getHostingServiceProxy()).thenReturn(mockHostingService);
        when(mockHostingService.getHostedServices()).thenReturn(map);
        testClass.testRequirement0034();
    }

    /**
     * Tests if a problem during marshalling causes a failing test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR0034Bad() throws Exception {
        final var hostedServiceTypeMock = mock(HostedServiceType.class);
        final var hostedService = mock(HostedServiceProxy.class, RETURNS_DEEP_STUBS);
        when(hostedService.getType()).thenReturn(hostedServiceTypeMock);
        when(hostedService.getType().getTypes()).thenReturn(List.of(WsdlConstants.PORT_TYPE_GET_QNAME));
        when(hostedService.sendRequestResponse(any())).thenThrow(MarshallingException.class);
        final var map = Map.of("hostedService", hostedService);
        final var mockHostingService = mock(HostingServiceProxy.class);
        when(testClient.getHostingServiceProxy()).thenReturn(mockHostingService);
        when(mockHostingService.getHostedServices()).thenReturn(map);
        assertThrows(AssertionError.class, testClass::testRequirement0034);
    }

    private HttpResponse createHttpResponseWithNoSoap(final int code) {
        final var mockHttpResponse = mock(org.apache.http.HttpResponse.class);
        final var statusLine = mock(org.apache.http.StatusLine.class);
        when(statusLine.getStatusCode()).thenReturn(code);
        when(mockHttpResponse.getStatusLine()).thenReturn(statusLine);
        return mockHttpResponse;
    }

    /**
     * Tests if an InvalidAddressingHeader Soap Fault is causing the test to pass.
     *
     * @throws Exception on any exception.
     */
    @Test
    public void testRequirementR0031Good() throws Exception {
        final var goodResponse = createMdibResponseMessage();
        final var faultResponse = soapFaultFactory.createFault(
                WsAddressingConstants.FAULT_ACTION,
                SoapConstants.SENDER,
                new QName(WsAddressingConstants.NAMESPACE, INVALID_ADDRESSING_HEADER),
                INVALID_ADDRESSING_HEADER_REASON);
        testRequirementR0031(goodResponse, faultResponse, goodResponse);
    }

    /**
     * Tests if a different Fault Subcode is causing the test to fail.
     *
     * @throws Exception on any exception.
     */
    @Test
    public void testRequirementR0031BadPrecondition() throws Exception {
        final var goodResponse = createMdibResponseMessage();
        final var faultResponse = soapFaultFactory.createFault(
                WsAddressingConstants.FAULT_ACTION,
                SoapConstants.SENDER,
                SoapConstants.DEFAULT_SUBCODE,
                INVALID_ADDRESSING_HEADER_REASON);
        assertThrows(AssertionError.class, () -> testRequirementR0031(goodResponse, faultResponse, goodResponse));
    }

    /**
     * Tests if an InvalidAddressingHeader Subcode causing the test to fail.
     *
     * @throws Exception on any exception.
     */
    @Test
    public void testRequirementR0031Bad() throws Exception {
        final var goodResponse = createMdibResponseMessage();
        final var faultResponse = soapFaultFactory.createFault(
                WsAddressingConstants.FAULT_ACTION,
                SoapConstants.SENDER,
                new QName(WsAddressingConstants.NAMESPACE, INVALID_ADDRESSING_HEADER),
                INVALID_ADDRESSING_HEADER_REASON);

        final var hostedServiceTypeMock = mock(HostedServiceType.class);
        final var hostedService = mock(HostedServiceProxy.class);
        when(hostedService.getType()).thenReturn(hostedServiceTypeMock);
        when(hostedService.getType().getTypes()).thenReturn(List.of(WsdlConstants.PORT_TYPE_GET_QNAME));
        when(httpClientUtil.postMessage(any(), any(), any()))
                .thenReturn(goodResponse)
                .thenThrow(new SoapFaultException(faultResponse))
                .thenThrow(new SoapFaultException(faultResponse));

        final var map = Map.of("hostedService", hostedService);
        final var mockHostingService = mock(HostingServiceProxy.class);
        when(testClient.getHostingServiceProxy()).thenReturn(mockHostingService);
        when(mockHostingService.getHostedServices()).thenReturn(map);
        assertThrows(AssertionError.class, () -> testClass.testRequirement0031());
    }

    private void testRequirementR0031(final SoapMessage one, final SoapMessage two, final SoapMessage three)
            throws Exception {
        final var hostedServiceTypeMock = mock(HostedServiceType.class);
        final var hostedService = mock(HostedServiceProxy.class);
        when(hostedService.getType()).thenReturn(hostedServiceTypeMock);
        when(hostedService.getType().getTypes()).thenReturn(List.of(WsdlConstants.PORT_TYPE_GET_QNAME));
        when(httpClientUtil.postMessage(any(), any(), any()))
                .thenReturn(one)
                .thenThrow(new SoapFaultException(two))
                .thenReturn(three);

        final var map = Map.of("hostedService", hostedService);
        final var mockHostingService = mock(HostingServiceProxy.class);
        when(testClient.getHostingServiceProxy()).thenReturn(mockHostingService);
        when(mockHostingService.getHostedServices()).thenReturn(map);
        testClass.testRequirement0031();
    }

    private SoapMessage createMdibResponseMessage() {
        return soapUtil.createMessage(
                ActionConstants.getResponseAction(ActionConstants.ACTION_GET_MDIB),
                messageModelFactory.createGetMdibResponse());
    }
}
