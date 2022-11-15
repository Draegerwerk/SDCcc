/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.tests.glue.direct;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.draeger.medical.sdccc.sdcri.testclient.TestClient;
import com.draeger.medical.sdccc.sdcri.testclient.TestClientUtil;
import com.draeger.medical.sdccc.tests.InjectorTestBase;
import com.draeger.medical.sdccc.tests.test_util.InjectorUtil;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.namespace.QName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.Mockito;
import org.opentest4j.AssertionFailedError;
import org.somda.sdc.dpws.helper.JaxbMarshalling;
import org.somda.sdc.dpws.service.HostedServiceProxy;
import org.somda.sdc.dpws.service.HostingServiceProxy;
import org.somda.sdc.dpws.wsdl.WsdlMarshalling;
import org.somda.sdc.dpws.wsdl.WsdlRetriever;
import org.somda.sdc.glue.common.WsdlConstants;
import org.somda.sdc.glue.provider.SdcDevice;

/**
 * Unit test for the Glue {@linkplain DirectWSDLServiceDescriptionsTest}.
 */
public class DirectWSDLServiceDescriptionsTestTest {

    private static final Duration MAX_WAIT = Duration.ofSeconds(10);
    private static final String LOW_PRIORITY_WSDL = "wsdl/IEEE11073-20701-LowPriority-Services.wsdl";
    private static final String HIGH_PRIORITY_WSDL = "wsdl/IEEE11073-20701-HighPriority-Services.wsdl";
    private static final String HIGH_PRIORITY_WSDL_NO_TARGET_NAMESPACE =
            "com/draeger/medical/sdccc/tests/glue/direct/DirectWSDLServiceDescriptionsTestTest/"
                    + "IEEE11073-20701-HighPriority-Services-No-TargetNamespace.wsdl";
    private static final String HIGH_PRIORITY_WSDL_WRONG_TARGET_NAMESPACE =
            "com/draeger/medical/sdccc/tests/glue/direct/DirectWSDLServiceDescriptionsTestTest/"
                    + "IEEE11073-20701-HighPriority-Services-Wrong-TargetNamespace.wsdl";
    private static final String HIGH_PRIORITY_WSDL_WITH_IMPORT =
            "com/draeger/medical/sdccc/tests/glue/direct/DirectWSDLServiceDescriptionsTestTest/"
                    + "IEEE11073-20701-HighPriority-Services-Import.wsdl";

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
    private DirectWSDLServiceDescriptionsTest testClass;
    private WsdlRetriever wsdlRetriever;
    private JaxbMarshalling jaxbMarshalling;
    private WsdlMarshalling wsdlMarshalling;

    @BeforeEach
    void setup() throws IOException, TimeoutException {
        final TestClient mockClient = mock(TestClient.class);
        when(mockClient.isClientRunning()).thenReturn(true);
        testClient = mockClient;

        wsdlRetriever = mock(WsdlRetriever.class);

        // setup the injector used by sdcri
        final var clientInjector = TestClientUtil.createClientInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(WsdlRetriever.class).toInstance(wsdlRetriever);
                bind(TestClient.class).toInstance(testClient);
            }
        });
        when(testClient.getInjector()).thenReturn(clientInjector);

        final Injector injector = InjectorUtil.setupInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(TestClient.class).toInstance(testClient);
            }
        });

        InjectorTestBase.setInjector(injector);

        jaxbMarshalling = testClient.getInjector().getInstance(JaxbMarshalling.class);
        wsdlMarshalling = testClient.getInjector().getInstance(WsdlMarshalling.class);

        jaxbMarshalling.startAsync().awaitRunning(MAX_WAIT);
        wsdlMarshalling.startAsync().awaitRunning(MAX_WAIT);

        testClass = new DirectWSDLServiceDescriptionsTest();
        testClass.setup();
    }

    @AfterEach
    void tearDown() throws TimeoutException {
        wsdlMarshalling.stopAsync().awaitTerminated(MAX_WAIT);
        jaxbMarshalling.stopAsync().awaitTerminated(MAX_WAIT);
    }

    /**
     * Tests whether the test passes, when all wsdls contain the expected target namespace.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement13Good() throws Exception {

        final var hostedService = mock(HostedServiceProxy.class, Mockito.RETURNS_DEEP_STUBS);
        when(hostedService.getType().getTypes())
                .thenReturn(Stream.concat(HIGH_PRIORITY_SERVICES.stream(), LOW_PRIORITY_SERVICES.stream())
                        .collect(Collectors.toList()));
        final var map = Map.of("hostedService", hostedService);
        final var mockHostingService = mock(HostingServiceProxy.class);
        when(testClient.getHostingServiceProxy()).thenReturn(mockHostingService);
        when(mockHostingService.getHostedServices()).thenReturn(map);

        final String wsdl = loadWsdl(HIGH_PRIORITY_WSDL, false);
        final String wsdl2 = loadWsdl(LOW_PRIORITY_WSDL, false);
        when(wsdlRetriever.retrieveWsdls(any())).thenReturn(Map.of("hostedService", List.of(wsdl, wsdl2)));

        testClass.testRequirement13();
    }

    /**
     * Tests whether the test fails, when a wsdl is missing the target namespace attribute.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement13BadTargetNamespaceMissing() throws Exception {
        final var hostedService = mock(HostedServiceProxy.class, Mockito.RETURNS_DEEP_STUBS);
        when(hostedService.getType().getTypes())
                .thenReturn(Stream.concat(HIGH_PRIORITY_SERVICES.stream(), LOW_PRIORITY_SERVICES.stream())
                        .collect(Collectors.toList()));
        final var map = Map.of("hostedService", hostedService);
        final var mockHostingService = mock(HostingServiceProxy.class);
        when(testClient.getHostingServiceProxy()).thenReturn(mockHostingService);
        when(mockHostingService.getHostedServices()).thenReturn(map);

        final String wsdl = loadWsdl(HIGH_PRIORITY_WSDL_NO_TARGET_NAMESPACE, true);
        final String wsdl2 = loadWsdl(LOW_PRIORITY_WSDL, false);
        when(wsdlRetriever.retrieveWsdls(any())).thenReturn(Map.of("hostedService", List.of(wsdl, wsdl2)));

        assertThrows(AssertionError.class, testClass::testRequirement13);
    }

    /**
     * Tests whether the test fails, when the value of the target namespace attribute is not matching the expected
     * value.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement13BadTargetNamespace() throws Exception {
        final var hostedService = mock(HostedServiceProxy.class, Mockito.RETURNS_DEEP_STUBS);
        when(hostedService.getType().getTypes())
                .thenReturn(Stream.concat(HIGH_PRIORITY_SERVICES.stream(), LOW_PRIORITY_SERVICES.stream())
                        .collect(Collectors.toList()));
        final var map = Map.of("hostedService", hostedService);
        final var mockHostingService = mock(HostingServiceProxy.class);
        when(testClient.getHostingServiceProxy()).thenReturn(mockHostingService);
        when(mockHostingService.getHostedServices()).thenReturn(map);

        final String wsdl = loadWsdl(HIGH_PRIORITY_WSDL_WRONG_TARGET_NAMESPACE, true);
        final String wsdl2 = loadWsdl(LOW_PRIORITY_WSDL, false);
        when(wsdlRetriever.retrieveWsdls(any())).thenReturn(Map.of("hostedService", List.of(wsdl, wsdl2)));

        assertThrows(AssertionError.class, testClass::testRequirement13);
    }

    /**
     * Tests whether the test fails, when a wsdl contains an import element.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement13BadImportElementPresent() throws Exception {
        final var hostedService = mock(HostedServiceProxy.class, Mockito.RETURNS_DEEP_STUBS);
        when(hostedService.getType().getTypes())
                .thenReturn(Stream.concat(HIGH_PRIORITY_SERVICES.stream(), LOW_PRIORITY_SERVICES.stream())
                        .collect(Collectors.toList()));
        final var map = Map.of("hostedService", hostedService);
        final var mockHostingService = mock(HostingServiceProxy.class);
        when(testClient.getHostingServiceProxy()).thenReturn(mockHostingService);
        when(mockHostingService.getHostedServices()).thenReturn(map);

        final String wsdl = loadWsdl(HIGH_PRIORITY_WSDL_WITH_IMPORT, true);
        final String wsdl2 = loadWsdl(LOW_PRIORITY_WSDL, false);
        when(wsdlRetriever.retrieveWsdls(any())).thenReturn(Map.of("hostedService", List.of(wsdl, wsdl2)));

        assertThrows(AssertionError.class, testClass::testRequirement13);
    }

    /**
     * Tests whether the test passes when the Device provides a DescriptionEventService.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement813Good() throws Exception {

        final HostingServiceProxy hostingServiceProxy = mock(HostingServiceProxy.class);
        when(testClient.getHostingServiceProxy()).thenReturn(hostingServiceProxy);
        final HostedServiceProxy descriptionEventServiceProxy =
                mock(HostedServiceProxy.class, Answers.RETURNS_DEEP_STUBS);
        when(hostingServiceProxy.getHostedServices())
                .thenReturn(Map.of(WsdlConstants.SERVICE_DESCRIPTION_EVENT, descriptionEventServiceProxy));
        when(descriptionEventServiceProxy.getType().getTypes())
                .thenReturn(List.of(WsdlConstants.PORT_TYPE_DESCRIPTION_EVENT_QNAME));

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
                + "\t<wsdl:message name=\"DescriptionModificationReport\">\n"
                + "\t\t<wsdl:part name=\"parameters\" element=\"msg:DescriptionModificationReport\"/>\n"
                + "\t</wsdl:message>\n"
                + "\t<wsdl:portType name=\"DescriptionEventService\" "
                + "dpws:DiscoveryType=\"dt:ServiceProvider\" wse:EventSource=\"true\">\n"
                + "\t\t<wsdl:operation name=\"DescriptionModificationReport\">\n"
                + "\t\t\t<wsdl:output message=\"tns:DescriptionModificationReport\"/>\n"
                + "\t\t</wsdl:operation>\n"
                + "\t</wsdl:portType>\n"
                + "</wsdl:definitions>\n";
        when(wsdlRetriever.retrieveWsdls(any()))
                .thenReturn(Map.of(WsdlConstants.SERVICE_DESCRIPTION_EVENT, List.of(wsdl)));

        testClass.testRequirement813();
    }

    /**
     * Tests whether the test fails when the DUT does not provide a DescriptionEventService.
     */
    @Test
    public void testRequirement813BadNoDescriptionEventService() {

        final HostingServiceProxy hostingServiceProxy = mock(HostingServiceProxy.class);
        when(testClient.getHostingServiceProxy()).thenReturn(hostingServiceProxy);
        when(hostingServiceProxy.getHostedServices()).thenReturn(Map.of());

        assertThrows(AssertionError.class, testClass::testRequirement813);
    }

    /**
     * Tests whether the test fails when the DUT's declaration of the DescriptionEventService in its WSDL is invalid.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement813BadInvalidSWSDL() throws Exception {

        final HostingServiceProxy hostingServiceProxy = mock(HostingServiceProxy.class);
        when(testClient.getHostingServiceProxy()).thenReturn(hostingServiceProxy);
        final HostedServiceProxy descriptionEventServiceProxy =
                mock(HostedServiceProxy.class, Answers.RETURNS_DEEP_STUBS);
        when(hostingServiceProxy.getHostedServices())
                .thenReturn(Map.of(WsdlConstants.SERVICE_DESCRIPTION_EVENT, descriptionEventServiceProxy));
        when(descriptionEventServiceProxy.getType().getTypes())
                .thenReturn(List.of(WsdlConstants.PORT_TYPE_DESCRIPTION_EVENT_QNAME));

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
                + "\t<wsdl:message name=\"DescriptionModificationReport\">\n"
                + "\t\t<wsdl:part name=\"parameters\" element=\"msg:DescriptionModificationReport\"/>\n"
                + "\t</wsdl:message>\n"
                + "\t<wsdl:portType name=\"DescriptionEventService\" "
                + "dpws:DiscoveryType=\"dt:ServiceProvider\" wse:EventSource=\"true\">\n"
                + "\t\t<wsdl:operation name=\"DescriptionModificationReport\">\n"
                + "\t\t\t<wsdl:input message=\"tns:DescriptionModificationReport\"/>\n"
                + "\t\t</wsdl:operation>\n"
                + "\t</wsdl:portType>\n"
                + "</wsdl:definitions>\n";
        when(wsdlRetriever.retrieveWsdls(any()))
                .thenReturn(Map.of(WsdlConstants.SERVICE_DESCRIPTION_EVENT, List.of(wsdl)));

        assertThrows(AssertionFailedError.class, testClass::testRequirement813);
    }

    private String loadWsdl(final String wsdlPath, final boolean classpath) throws IOException {
        final String wsdl;
        if (classpath) {
            final var loader = getClass().getClassLoader();
            try (final var wsdlStream = loader.getResourceAsStream(wsdlPath)) {
                assertNotNull(wsdlStream);
                wsdl = new String(wsdlStream.readAllBytes(), StandardCharsets.UTF_8);
            }
        } else {
            final var loader = SdcDevice.class.getClassLoader();
            try (final var wsdlStream = loader.getResourceAsStream(wsdlPath)) {
                assertNotNull(wsdlStream);
                wsdl = new String(wsdlStream.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        assertNotNull(wsdl);
        assertFalse(wsdl.isBlank());
        return wsdl;
    }
}
