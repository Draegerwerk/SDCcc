/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.tests.mdpws.direct;

import static com.draeger.medical.sdccc.util.Constants.s12;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import com.draeger.medical.sdccc.util.Constants;
import com.draeger.medical.sdccc.util.HttpClientUtil;
import com.draeger.medical.sdccc.util.XPathExtractor;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.somda.sdc.dpws.helper.JaxbMarshalling;
import org.somda.sdc.dpws.service.HostedServiceProxy;
import org.somda.sdc.dpws.service.HostingServiceProxy;
import org.somda.sdc.dpws.soap.SoapMarshalling;
import org.somda.sdc.dpws.soap.SoapMessage;
import org.somda.sdc.glue.common.WsdlConstants;

/**
 * Unit test for the MDPWS {@linkplain DirectWSDLTest} utilities.
 */
public class DirectWSDLUtilTest {
    private static final String LOW_PRIORITY_WSDL = "wsdl/IEEE11073-20701-LowPriority-Services.wsdl";
    private static final String HIGH_PRIORITY_WSDL = "wsdl/IEEE11073-20701-HighPriority-Services.wsdl";

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    private DirectWSDLTest testClass;
    private TestClient testClient;
    private HttpClientUtil mockClientUtil;
    private SoapMarshalling marshalling;
    private JaxbMarshalling baseMarshalling;

    @BeforeEach
    void setUp() throws IOException, TimeoutException {
        final TestClient mockClient = mock(TestClient.class, Mockito.RETURNS_DEEP_STUBS);
        when(mockClient.isClientRunning()).thenReturn(true);
        testClient = mockClient;
        mockClientUtil = mock(HttpClientUtil.class);

        final Injector injector = InjectorUtil.setupInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(TestClient.class).toInstance(testClient);
            }
        });
        InjectorTestBase.setInjector(injector);

        // setup the injector used by sdcri
        final var clientInjector = TestClientUtil.createClientInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(HttpClientUtil.class).toInstance(mockClientUtil);
            }
        });
        when(testClient.getInjector()).thenReturn(clientInjector);

        baseMarshalling = clientInjector.getInstance(JaxbMarshalling.class);
        baseMarshalling.startAsync().awaitRunning(DEFAULT_TIMEOUT);

        marshalling = clientInjector.getInstance(SoapMarshalling.class);
        marshalling.startAsync().awaitRunning(DEFAULT_TIMEOUT);

        testClass = new DirectWSDLTest();
        testClass.setUp();
    }

    @AfterEach
    void tearDown() throws TimeoutException {
        baseMarshalling.stopAsync().awaitTerminated(DEFAULT_TIMEOUT);
        marshalling.stopAsync().awaitTerminated(DEFAULT_TIMEOUT);
    }

    /**
     * Tests whether the correct message is transmitted.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testSendVersionMismatch() throws Exception {
        final var mockGetService = mock(HostedServiceProxy.class, Mockito.RETURNS_DEEP_STUBS);
        when(mockGetService.getType().getTypes()).thenReturn(List.of(WsdlConstants.PORT_TYPE_GET_QNAME));
        when(testClient.getHostingServiceProxy().getHostedServices()).thenReturn(Map.of("get", mockGetService));

        testClass.sendVersionMismatch(testClient);

        final var byteCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(mockClientUtil, times(1)).postMessage(any(), any(), byteCaptor.capture());

        final String message;
        try (final var messageStream = new ByteArrayInputStream(byteCaptor.getValue())) {
            message = new String(messageStream.readAllBytes(), StandardCharsets.UTF_8);
        }

        // lazily evaluate whether the message matches our custom message
        assertTrue(message.contains("<s12:Envelope"));
        assertTrue(message.contains("xmlns:s12=\"http://www.w3.org/2003/05/soap-poas\""));
    }

    /**
     * Tests whether the custom must understand attribute is properly added to the outgoing message.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testSendMustUnderstand() throws Exception {
        final var mockGetService = mock(HostedServiceProxy.class, Mockito.RETURNS_DEEP_STUBS);
        when(mockGetService.getType().getTypes()).thenReturn(List.of(WsdlConstants.PORT_TYPE_GET_QNAME));

        final var mockHostingService = mock(HostingServiceProxy.class);
        when(mockHostingService.getHostedServices()).thenReturn(Map.of("get", mockGetService));
        when(testClient.getHostingServiceProxy()).thenReturn(mockHostingService);

        testClass.sendMustUnderstand(testClient);

        final var captor = ArgumentCaptor.forClass(SoapMessage.class);
        verify(mockGetService, times(1)).sendRequestResponse(captor.capture());

        final var messageStream = new ByteArrayOutputStream();
        marshalling.marshal(captor.getValue().getEnvelopeWithMappedHeaders(), messageStream);
        final var message = messageStream.toString(StandardCharsets.UTF_8);

        final var headerExtractor = new XPathExtractor("//" + s12("Header"));
        final var header = headerExtractor.extractFrom(message);
        assertFalse(header.isEmpty());

        final var children =
                XPathExtractor.convert(header.stream().findFirst().orElseThrow().getChildNodes());
        // find the custom element in all children
        final var mustUnderstandNodes = children.stream()
                .filter(child -> child.getNamespaceURI().equals(DirectWSDLTest.CUSTOM_NAMESPACE.getNamespaceURI()))
                .filter(child -> child.getLocalName().equals(DirectWSDLTest.CUSTOM_NAMESPACE.getLocalPart()))
                .collect(Collectors.toList());
        assertEquals(1, mustUnderstandNodes.size(), "Custom element not found in message");

        final var mustUnderstandNode = mustUnderstandNodes.get(0);
        final var mustUnderstandAttribute = mustUnderstandNode
                .getAttributes()
                .getNamedItemNS(
                        Constants.MUST_UNDERSTAND_ATTRIBUTE.getNamespaceURI(),
                        Constants.MUST_UNDERSTAND_ATTRIBUTE.getLocalPart());
        assertNotNull(mustUnderstandAttribute, "s12:mustUnderstand was null on custom element");
    }
}
