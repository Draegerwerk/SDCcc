/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2023 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.tests.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.draeger.medical.sdccc.configuration.TestSuiteConfig;
import com.draeger.medical.sdccc.sdcri.testclient.TestClient;
import com.draeger.medical.sdccc.sdcri.testclient.TestClientUtil;
import com.draeger.medical.sdccc.tests.InjectorTestBase;
import com.draeger.medical.sdccc.tests.test_util.InjectorUtil;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Names;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.namespace.QName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.somda.sdc.dpws.helper.JaxbMarshalling;
import org.somda.sdc.dpws.service.HostedServiceProxy;
import org.somda.sdc.dpws.soap.exception.TransportException;
import org.somda.sdc.dpws.wsdl.WsdlMarshalling;
import org.somda.sdc.dpws.wsdl.WsdlRetriever;
import org.somda.sdc.glue.common.WsdlConstants;
import org.somda.sdc.glue.provider.SdcDevice;

/**
 * Unit tests for the {@linkplain HostedServiceVerifier}.
 */
public class HostedServiceVerifierTest {
    private static final String DUPLICATE_PORT_TYPE_TEMPLATE =
            "portType was defined in multiple WSDLs for" + " one service. Cannot determine which is correct.";

    private static final String LOW_PRIORITY_WSDL = "wsdl/IEEE11073-20701-LowPriority-Services.wsdl";
    private static final String HIGH_PRIORITY_WSDL = "wsdl/IEEE11073-20701-HighPriority-Services.wsdl";

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    private static final List<QName> HIGH_PRIORITY_SERVICES = List.of(
            WsdlConstants.PORT_TYPE_GET_QNAME,
            WsdlConstants.PORT_TYPE_SET_QNAME,
            WsdlConstants.PORT_TYPE_DESCRIPTION_EVENT_QNAME,
            WsdlConstants.PORT_TYPE_STATE_EVENT_QNAME,
            WsdlConstants.PORT_TYPE_CONTEXT_QNAME,
            WsdlConstants.PORT_TYPE_WAVEFORM_QNAME,
            WsdlConstants.PORT_TYPE_CONTAINMENT_TREE_QNAME);

    private static final List<QName> LOW_PRIORITY_SERVICES =
            List.of(WsdlConstants.PORT_TYPE_ARCHIVE_QNAME, WsdlConstants.PORT_TYPE_LOCALIZATION_QNAME);

    private TestClient testClient;
    private WsdlRetriever mockRetriever;
    private HostedServiceVerifier hostedServiceVerifier;
    private JaxbMarshalling baseMarshalling;
    private WsdlMarshalling wsdlMarshalling;

    @BeforeEach
    void setUp() throws IOException, TimeoutException {
        final TestClient mockClient = mock(TestClient.class, Mockito.RETURNS_DEEP_STUBS);
        when(mockClient.isClientRunning()).thenReturn(true);
        testClient = mockClient;
        mockRetriever = mock(WsdlRetriever.class);

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
        final var clientInjector = TestClientUtil.createClientInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(WsdlRetriever.class).toInstance(mockRetriever);
            }
        });
        when(testClient.getInjector()).thenReturn(clientInjector);

        baseMarshalling = clientInjector.getInstance(JaxbMarshalling.class);
        baseMarshalling.startAsync().awaitRunning(DEFAULT_TIMEOUT);

        wsdlMarshalling = clientInjector.getInstance(WsdlMarshalling.class);
        wsdlMarshalling.startAsync().awaitRunning(DEFAULT_TIMEOUT);

        hostedServiceVerifier = new HostedServiceVerifier(testClient);
    }

    @AfterEach
    void tearDown() throws TimeoutException {
        baseMarshalling.stopAsync().awaitTerminated(DEFAULT_TIMEOUT);
        wsdlMarshalling.stopAsync().awaitTerminated(DEFAULT_TIMEOUT);
    }

    /**
     * Tests whether validation of the hostedService endpoint with a correct WSDL passes.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testVerifyHostedServicesGood() throws Exception {
        final var hostedServiceName = "hostedServiceName";
        final var mockHostedService = mock(HostedServiceProxy.class, Mockito.RETURNS_DEEP_STUBS);
        when(mockHostedService.getType().getTypes())
                .thenReturn(Stream.of(HIGH_PRIORITY_SERVICES, LOW_PRIORITY_SERVICES)
                        .flatMap(List::stream)
                        .collect(Collectors.toList()));

        when(testClient.getHostingServiceProxy().getHostedServices())
                .thenReturn(Map.of(hostedServiceName, mockHostedService));

        final String wsdl = loadWsdl(LOW_PRIORITY_WSDL);
        final String wsdl2 = loadWsdl(HIGH_PRIORITY_WSDL);
        when(mockRetriever.retrieveWsdls(any())).thenReturn(Map.of(hostedServiceName, List.of(wsdl, wsdl2)));

        hostedServiceVerifier.verifyHostedService(Optional.of(mockHostedService));
    }

    /**
     * Tests whether validation of the lowPriorityServices with a wrong or missing WSDL or a duplicate PortType fails.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testVerifyLowPriorityServicesBad() throws Exception {
        verifyHostedServicesBadWrongWsdl(LOW_PRIORITY_SERVICES, HIGH_PRIORITY_WSDL);
        verifyHostedServicesBadNoWsdl(LOW_PRIORITY_SERVICES);
        verifyHostedServicesBadDuplicatePortType(LOW_PRIORITY_SERVICES, LOW_PRIORITY_WSDL);
    }

    /**
     * Tests whether validation of the highPriorityServices with a wrong or missing WSDL or a duplicate PortType fails.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testVerifyHighPriorityServicesBad() throws Exception {
        verifyHostedServicesBadWrongWsdl(HIGH_PRIORITY_SERVICES, LOW_PRIORITY_WSDL);
        verifyHostedServicesBadNoWsdl(HIGH_PRIORITY_SERVICES);
        verifyHostedServicesBadDuplicatePortType(HIGH_PRIORITY_SERVICES, HIGH_PRIORITY_WSDL);
    }

    /**
     * Test the validation of Hosted Services with a minimal example.
     */
    @Test
    void testVerifyHostedServicesMinimalGood() throws IOException, TransportException {

        final var hostedServiceName = "hostedServiceName";
        final var services = List.of(WsdlConstants.PORT_TYPE_GET_QNAME);

        for (QName qname : services) {
            final var mockHostedService = mock(HostedServiceProxy.class, Mockito.RETURNS_DEEP_STUBS);
            when(mockHostedService.getType().getTypes()).thenReturn(List.of(qname));

            when(testClient.getHostingServiceProxy().getHostedServices())
                    .thenReturn(Map.of(hostedServiceName, mockHostedService));

            final String wsdl = "<wsdl:definitions xmlns:dpws=\"http://docs.oasis-open.org/ws-dd/ns/dpws/2009/01\" "
                    + "xmlns:mdpws=\"http://standards.ieee.org/downloads/11073/11073-20702-2016\" "
                    + "xmlns:msg=\"http://standards.ieee.org/downloads/11073/11073-10207-2017/message\" "
                    + "xmlns:s12=\"http://schemas.xmlsoap.org/wsdl/soap12/\" "
                    + "xmlns:tns=\"http://standards.ieee.org/downloads/11073/11073-20701-2018\" "
                    + "xmlns:wsdl=\"http://schemas.xmlsoap.org/wsdl/\" "
                    + "xmlns:wsp=\"http://www.w3.org/ns/ws-policy\" "
                    + "xmlns:ns=\"http://standards.ieee.org/downloads/11073/11073-10207-2017/extension\" "
                    + "xmlns:ns1=\"http://standards.ieee.org/downloads/11073/11073-10207-2017/participant\" "
                    + "xmlns:dt=\"http://standards.ieee.org/downloads/11073/11073-10207-2017\" "
                    + "xmlns:wse=\"http://schemas.xmlsoap.org/ws/2004/08/eventing\" "
                    + "targetNamespace=\"http://standards.ieee.org/downloads/11073/11073-20701-2018\">\n"
                    + "\t<wsdl:message name=\"GetMdib\">\n"
                    + "\t\t<wsdl:part name=\"parameters\" element=\"msg:GetMdib\"/>\n"
                    + "\t</wsdl:message>\n"
                    + "\t<wsdl:message name=\"GetMdibResponse\">\n"
                    + "\t\t<wsdl:part name=\"parameters\" element=\"msg:GetMdibResponse\"/>\n"
                    + "\t</wsdl:message>\n"
                    + "\t<wsdl:message name=\"GetMdDescription\">\n"
                    + "\t\t<wsdl:part name=\"parameters\" element=\"msg:GetMdDescription\"/>\n"
                    + "\t</wsdl:message>\n"
                    + "\t<wsdl:message name=\"GetMdDescriptionResponse\">\n"
                    + "\t\t<wsdl:part name=\"parameters\" element=\"msg:GetMdDescriptionResponse\"/>\n"
                    + "\t</wsdl:message>\n"
                    + "\t<wsdl:message name=\"GetMdState\">\n"
                    + "\t\t<wsdl:part name=\"parameters\" element=\"msg:GetMdState\"/>\n"
                    + "\t</wsdl:message>\n"
                    + "\t<wsdl:message name=\"GetMdStateResponse\">\n"
                    + "\t\t<wsdl:part name=\"parameters\" element=\"msg:GetMdStateResponse\"/>\n"
                    + "\t</wsdl:message>\n"
                    + "\t<wsdl:portType name=\"GetService\" dpws:DiscoveryType=\"dt:ServiceProvider\">\n"
                    + "\t\t<wsdl:operation name=\"GetMdib\">\n"
                    + "\t\t\t<wsdl:input message=\"tns:GetMdib\"/>\n"
                    + "\t\t\t<wsdl:output message=\"tns:GetMdibResponse\"/>\n"
                    + "\t\t</wsdl:operation>\n"
                    + "\t\t<wsdl:operation name=\"GetMdDescription\">\n"
                    + "\t\t\t<wsdl:input message=\"tns:GetMdDescription\"/>\n"
                    + "\t\t\t<wsdl:output message=\"tns:GetMdDescriptionResponse\"/>\n"
                    + "\t\t</wsdl:operation>\n"
                    + "\t\t<wsdl:operation name=\"GetMdState\">\n"
                    + "\t\t\t<wsdl:input message=\"tns:GetMdState\"/>\n"
                    + "\t\t\t<wsdl:output message=\"tns:GetMdStateResponse\"/>\n"
                    + "\t\t</wsdl:operation>\n"
                    + "\t</wsdl:portType>\n"
                    + "</wsdl:definitions>\n";

            when(mockRetriever.retrieveWsdls(any())).thenReturn(Map.of(hostedServiceName, List.of(wsdl)));

            hostedServiceVerifier.verifyHostedService(Optional.of(mockHostedService));
        }
    }

    /**
     * Test that the validation of Hosted Services fails when the Inputs and Outputs
     * are switched in the minimal example.
     */
    @Test
    void testVerifyHostedServicesMinimalBadSwitchedInputOutput() throws IOException, TransportException {

        final var hostedServiceName = "hostedServiceName";
        final var services = List.of(WsdlConstants.PORT_TYPE_GET_QNAME);

        for (QName qname : services) {
            final var mockHostedService = mock(HostedServiceProxy.class, Mockito.RETURNS_DEEP_STUBS);
            when(mockHostedService.getType().getTypes()).thenReturn(List.of(qname));

            when(testClient.getHostingServiceProxy().getHostedServices())
                    .thenReturn(Map.of(hostedServiceName, mockHostedService));

            final String wsdl = "<wsdl:definitions xmlns:dpws=\"http://docs.oasis-open.org/ws-dd/ns/dpws/2009/01\" "
                    + "xmlns:mdpws=\"http://standards.ieee.org/downloads/11073/11073-20702-2016\" "
                    + "xmlns:msg=\"http://standards.ieee.org/downloads/11073/11073-10207-2017/message\" "
                    + "xmlns:s12=\"http://schemas.xmlsoap.org/wsdl/soap12/\" "
                    + "xmlns:tns=\"http://standards.ieee.org/downloads/11073/11073-20701-2018\" "
                    + "xmlns:wsdl=\"http://schemas.xmlsoap.org/wsdl/\" "
                    + "xmlns:wsp=\"http://www.w3.org/ns/ws-policy\" "
                    + "xmlns:ns=\"http://standards.ieee.org/downloads/11073/11073-10207-2017/extension\" "
                    + "xmlns:ns1=\"http://standards.ieee.org/downloads/11073/11073-10207-2017/participant\" "
                    + "xmlns:dt=\"http://standards.ieee.org/downloads/11073/11073-10207-2017\" "
                    + "xmlns:wse=\"http://schemas.xmlsoap.org/ws/2004/08/eventing\" "
                    + "targetNamespace=\"http://standards.ieee.org/downloads/11073/11073-20701-2018\">\n"
                    + "\t<wsdl:message name=\"GetMdib\">\n"
                    + "\t\t<wsdl:part name=\"parameters\" element=\"msg:GetMdib\"/>\n"
                    + "\t</wsdl:message>\n"
                    + "\t<wsdl:message name=\"GetMdibResponse\">\n"
                    + "\t\t<wsdl:part name=\"parameters\" element=\"msg:GetMdibResponse\"/>\n"
                    + "\t</wsdl:message>\n"
                    + "\t<wsdl:message name=\"GetMdDescription\">\n"
                    + "\t\t<wsdl:part name=\"parameters\" element=\"msg:GetMdDescription\"/>\n"
                    + "\t</wsdl:message>\n"
                    + "\t<wsdl:message name=\"GetMdDescriptionResponse\">\n"
                    + "\t\t<wsdl:part name=\"parameters\" element=\"msg:GetMdDescriptionResponse\"/>\n"
                    + "\t</wsdl:message>\n"
                    + "\t<wsdl:message name=\"GetMdState\">\n"
                    + "\t\t<wsdl:part name=\"parameters\" element=\"msg:GetMdState\"/>\n"
                    + "\t</wsdl:message>\n"
                    + "\t<wsdl:message name=\"GetMdStateResponse\">\n"
                    + "\t\t<wsdl:part name=\"parameters\" element=\"msg:GetMdStateResponse\"/>\n"
                    + "\t</wsdl:message>\n"
                    + "\t<wsdl:portType name=\"GetService\" dpws:DiscoveryType=\"dt:ServiceProvider\">\n"
                    + "\t\t<wsdl:operation name=\"GetMdib\">\n"
                    + "\t\t\t<wsdl:output message=\"tns:GetMdibResponse\"/>\n"
                    + "\t\t\t<wsdl:input message=\"tns:GetMdib\"/>\n"
                    + "\t\t</wsdl:operation>\n"
                    + "\t\t<wsdl:operation name=\"GetMdDescription\">\n"
                    + "\t\t\t<wsdl:input message=\"tns:GetMdDescription\"/>\n"
                    + "\t\t\t<wsdl:output message=\"tns:GetMdDescriptionResponse\"/>\n"
                    + "\t\t</wsdl:operation>\n"
                    + "\t\t<wsdl:operation name=\"GetMdState\">\n"
                    + "\t\t\t<wsdl:input message=\"tns:GetMdState\"/>\n"
                    + "\t\t\t<wsdl:output message=\"tns:GetMdStateResponse\"/>\n"
                    + "\t\t</wsdl:operation>\n"
                    + "\t</wsdl:portType>\n"
                    + "</wsdl:definitions>\n";

            when(mockRetriever.retrieveWsdls(any())).thenReturn(Map.of(hostedServiceName, List.of(wsdl)));

            assertThrows(
                    AssertionError.class,
                    () -> hostedServiceVerifier.verifyHostedService(Optional.of(mockHostedService)));
        }
    }

    /**
     * Tests whether validation of the hostedService endpoint with a missing WSDL fails.
     *
     * @throws Exception on any exception
     */
    private void verifyHostedServicesBadWrongWsdl(final List<QName> services, final String wsdlPath) throws Exception {
        final var hostedServiceName = "hostedServiceName";

        assertFalse(services.isEmpty());
        for (QName qname : services) {
            final var mockHostedService = mock(HostedServiceProxy.class, Mockito.RETURNS_DEEP_STUBS);
            when(mockHostedService.getType().getTypes()).thenReturn(List.of(qname));

            when(testClient.getHostingServiceProxy().getHostedServices())
                    .thenReturn(Map.of(hostedServiceName, mockHostedService));

            final String wsdl = loadWsdl(wsdlPath);
            when(mockRetriever.retrieveWsdls(any())).thenReturn(Map.of(hostedServiceName, List.of(wsdl)));

            final var error = assertThrows(
                    AssertionError.class,
                    () -> hostedServiceVerifier.verifyHostedService(Optional.of(mockHostedService)));
            assertTrue(error.getMessage().contains(hostedServiceName));
            assertTrue(
                    error.getMessage().contains(qname.toString()),
                    String.format("Error: %s, Name: %s", error.getMessage(), qname));
        }
    }

    /**
     * Tests whether validation of the hostedServices endpoint without a WSDL fails.
     *
     * @throws Exception on any exception
     */
    private void verifyHostedServicesBadNoWsdl(final List<QName> services) throws Exception {
        final var hostedServiceName = "hostedServiceName";

        assertFalse(services.isEmpty());
        for (QName qname : services) {
            final var mockHostedService = mock(HostedServiceProxy.class, Mockito.RETURNS_DEEP_STUBS);
            when(mockHostedService.getType().getTypes()).thenReturn(List.of(qname));

            when(testClient.getHostingServiceProxy().getHostedServices())
                    .thenReturn(Map.of(hostedServiceName, mockHostedService));

            when(mockRetriever.retrieveWsdls(any())).thenReturn(Map.of(hostedServiceName, Collections.emptyList()));

            final var error = assertThrows(
                    AssertionError.class,
                    () -> hostedServiceVerifier.verifyHostedService(Optional.of(mockHostedService)));
            assertTrue(error.getMessage().contains(hostedServiceName));
        }
    }

    /**
     * Tests whether validation of the hostedServices endpoint with duplicate portTypes fails.
     *
     * @throws Exception any Exception
     */
    private void verifyHostedServicesBadDuplicatePortType(final List<QName> services, final String wsdlPath)
            throws Exception {
        final var hostedServiceName = "hostedServiceName";

        assertFalse(services.isEmpty());
        for (QName qname : services) {
            final var mockHostedService = mock(HostedServiceProxy.class, Mockito.RETURNS_DEEP_STUBS);
            when(mockHostedService.getType().getTypes()).thenReturn(List.of(qname));

            when(testClient.getHostingServiceProxy().getHostedServices())
                    .thenReturn(Map.of(hostedServiceName, mockHostedService));

            final String wsdl = loadWsdl(wsdlPath);
            when(mockRetriever.retrieveWsdls(any())).thenReturn(Map.of(hostedServiceName, List.of(wsdl, wsdl)));

            final var error = assertThrows(
                    AssertionError.class,
                    () -> hostedServiceVerifier.verifyHostedService(Optional.of(mockHostedService)));
            assertTrue(error.getMessage().contains(hostedServiceName));
            assertTrue(error.getMessage().contains(DUPLICATE_PORT_TYPE_TEMPLATE));
        }
    }

    private String loadWsdl(final String wsdlPath) throws IOException {
        final String wsdl;
        final var loader = SdcDevice.class.getClassLoader();
        try (final var wsdlStream = loader.getResourceAsStream(wsdlPath)) {
            assertNotNull(wsdlStream);
            wsdl = new String(wsdlStream.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertNotNull(wsdl);
        assertFalse(wsdl.isBlank());
        return wsdl;
    }
}
