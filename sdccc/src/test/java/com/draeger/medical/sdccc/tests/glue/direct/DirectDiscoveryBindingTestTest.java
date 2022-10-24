/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.tests.glue.direct;

import com.draeger.medical.sdccc.sdcri.testclient.TestClient;
import com.draeger.medical.sdccc.sdcri.testclient.TestClientUtil;
import com.draeger.medical.sdccc.tests.InjectorTestBase;
import com.draeger.medical.sdccc.tests.test_util.InjectorUtil;
import com.draeger.medical.sdccc.tests.util.NoTestData;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.somda.sdc.dpws.helper.JaxbMarshalling;
import org.somda.sdc.dpws.service.HostedServiceProxy;
import org.somda.sdc.dpws.service.HostingServiceProxy;
import org.somda.sdc.dpws.wsdl.WsdlMarshalling;
import org.somda.sdc.dpws.wsdl.WsdlRetriever;
import org.somda.sdc.glue.common.WsdlConstants;
import org.somda.sdc.glue.provider.SdcDevice;

import javax.xml.namespace.QName;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test for the Glue {@linkplain DirectDiscoveryBindingTest}.
 */

public class DirectDiscoveryBindingTestTest {
    private static final Duration MAX_WAIT = Duration.ofSeconds(10);
    private static final String LOW_PRIORITY_WSDL = "wsdl/IEEE11073-20701-LowPriority-Services.wsdl";
    private static final String HIGH_PRIORITY_WSDL = "wsdl/IEEE11073-20701-HighPriority-Services.wsdl";
    private static final String HIGH_PRIORITY_WSDL_USING_DEFAULT_NAMESPACE
        = "com/draeger/medical/sdccc/tests/glue/direct/DirectDiscoveryBindingTestTest/"
        + "IEEE11073-20701-HighPriority-Services-Using-Default-Namespace.wsdl";
    private static final String HIGH_PRIORITY_WSDL_MISSING_DISCOVERYTYPE =
        "com/draeger/medical/sdccc/tests/glue/direct/DirectDiscoveryBindingTestTest/IEEE11073-20701-HighPriority-"
            + "Services-Missing-DiscoveryType.wsdl";
    private static final String HIGH_PRIORITY_WSDL_WRONG_NAMESPACE =
        "com/draeger/medical/sdccc/tests/glue/direct/DirectDiscoveryBindingTestTest/IEEE11073-20701-HighPriority-"
            + "Services-Wrong-Namespace.wsdl";
    private static final String HIGH_PRIORITY_WSDL_WRONG_DISCOVERYTYPE =
        "com/draeger/medical/sdccc/tests/glue/direct/DirectDiscoveryBindingTestTest/IEEE11073-20701-HighPriority-"
            + "Services-Wrong-DiscoveryType.wsdl";
    private static final String HIGH_PRIORITY_WSDL_UNDEFINED_PREFIX =
        "com/draeger/medical/sdccc/tests/glue/direct/DirectDiscoveryBindingTestTest/IEEE11073-20701-HighPriority-"
            + "Services-Undefined-Prefix.wsdl";

    private static final List<QName> HIGH_PRIORITY_SERVICES = List.of(
        WsdlConstants.PORT_TYPE_GET_QNAME,
        WsdlConstants.PORT_TYPE_SET_QNAME,
        WsdlConstants.PORT_TYPE_DESCRIPTION_EVENT_QNAME,
        WsdlConstants.PORT_TYPE_STATE_EVENT_QNAME,
        WsdlConstants.PORT_TYPE_CONTEXT_QNAME,
        WsdlConstants.PORT_TYPE_WAVEFORM_QNAME,
        WsdlConstants.PORT_TYPE_CONTAINMENT_TREE_QNAME
    );

    private static final List<QName> LOW_PRIORITY_SERVICES = List.of(
        WsdlConstants.PORT_TYPE_ARCHIVE_QNAME,
        WsdlConstants.PORT_TYPE_LOCALIZATION_QNAME
    );

    private TestClient testClient;
    private DirectDiscoveryBindingTest testClass;
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
        final var clientInjector = TestClientUtil.createClientInjector(
            new AbstractModule() {
                @Override
                protected void configure() {
                    bind(WsdlRetriever.class).toInstance(wsdlRetriever);
                }
            }
        );
        when(testClient.getInjector()).thenReturn(clientInjector);

        final Injector injector = InjectorUtil.setupInjector(
            new AbstractModule() {
                @Override
                protected void configure() {
                    bind(TestClient.class).toInstance(testClient);
                }
            }
        );

        InjectorTestBase.setInjector(injector);

        jaxbMarshalling = testClient.getInjector().getInstance(JaxbMarshalling.class);
        wsdlMarshalling = testClient.getInjector().getInstance(WsdlMarshalling.class);

        jaxbMarshalling.startAsync().awaitRunning(MAX_WAIT);
        wsdlMarshalling.startAsync().awaitRunning(MAX_WAIT);

        testClass = new DirectDiscoveryBindingTest();
        testClass.setup();
    }

    @AfterEach
    void tearDown() throws TimeoutException {
        wsdlMarshalling.stopAsync().awaitTerminated(MAX_WAIT);
        jaxbMarshalling.stopAsync().awaitTerminated(MAX_WAIT);
    }

    /**
     * Tests whether calling the tests without any input data causes a failure.
     */
    @Test
    public void testRequirementR0042NoTestData() {
        assertThrows(NoTestData.class, testClass::testRequirementR0042);
    }

    /**
     * Test whether all portTypes having a DiscoveryType attribute with a value resolving to
     * http://standards.ieee.org/downloads/11073/11073-10207-2017}ServiceProvider causes the test to pass.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR0042Good() throws Exception {
        final var hostedService = mock(HostedServiceProxy.class, Mockito.RETURNS_DEEP_STUBS);
        when(hostedService.getType().getTypes()).thenReturn(
            Stream.concat(HIGH_PRIORITY_SERVICES.stream(), LOW_PRIORITY_SERVICES.stream()).collect(Collectors.toList())
        );
        final var map = Map.of("hostedService", hostedService);
        final var mockHostingService = mock(HostingServiceProxy.class);
        when(testClient.getHostingServiceProxy()).thenReturn(mockHostingService);
        when(mockHostingService.getHostedServices()).thenReturn(map);

        final String wsdl = loadWsdl(HIGH_PRIORITY_WSDL, false);
        final String wsdl2 = loadWsdl(LOW_PRIORITY_WSDL, false);
        when(wsdlRetriever.retrieveWsdls(any())).thenReturn(Map.of(
            "hostedService", List.of(wsdl, wsdl2)
        ));
        testClass.testRequirementR0042();
    }

    /**
     * Test whether portTypes having a DiscoveryType attribute using the default namespace and resolving to
     * "{http://standards.ieee.org/downloads/11073/11073-10207-2017}ServiceProvider" also pass the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR0042GoodDefaultNamespace() throws Exception {
        final var hostedService = mock(HostedServiceProxy.class, Mockito.RETURNS_DEEP_STUBS);
        when(hostedService.getType().getTypes()).thenReturn(
            Stream.concat(HIGH_PRIORITY_SERVICES.stream(), LOW_PRIORITY_SERVICES.stream()).collect(Collectors.toList())
        );
        final var map = Map.of("hostedService", hostedService);
        final var mockHostingService = mock(HostingServiceProxy.class);
        when(testClient.getHostingServiceProxy()).thenReturn(mockHostingService);
        when(mockHostingService.getHostedServices()).thenReturn(map);

        final String wsdl = loadWsdl(HIGH_PRIORITY_WSDL_USING_DEFAULT_NAMESPACE, false);
        final String wsdl2 = loadWsdl(LOW_PRIORITY_WSDL, false);
        when(wsdlRetriever.retrieveWsdls(any())).thenReturn(Map.of(
            "hostedService", List.of(wsdl, wsdl2)
        ));
        testClass.testRequirementR0042();
    }

    /**
     * Tests whether a portType is missing a DiscoveryType attribute causes the test to fail.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR0042BadMissingDiscoveryType() throws Exception {
        final var hostedService = mock(HostedServiceProxy.class, Mockito.RETURNS_DEEP_STUBS);
        when(hostedService.getType().getTypes()).thenReturn(
            Stream.concat(HIGH_PRIORITY_SERVICES.stream(), LOW_PRIORITY_SERVICES.stream()).collect(Collectors.toList())
        );
        final var map = Map.of("hostedService", hostedService);
        final var mockHostingService = mock(HostingServiceProxy.class);
        when(testClient.getHostingServiceProxy()).thenReturn(mockHostingService);
        when(mockHostingService.getHostedServices()).thenReturn(map);

        final String wsdl = loadWsdl(HIGH_PRIORITY_WSDL_MISSING_DISCOVERYTYPE, true);
        final String wsdl2 = loadWsdl(LOW_PRIORITY_WSDL, false);
        when(wsdlRetriever.retrieveWsdls(any())).thenReturn(Map.of(
            "hostedService", List.of(wsdl, wsdl2)
        ));
        assertThrows(AssertionError.class, testClass::testRequirementR0042);
    }

    /**
     * Tests whether the value of the DiscoveryType attribute is not resolving to
     * http://standards.ieee.org/downloads/11073/11073-10207-2017}ServiceProvider because a wrong Namespace
     * is used causes the test to fail.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR0042BadWrongNamespace() throws Exception {
        final String wsdlWrongNamespace = loadWsdl(HIGH_PRIORITY_WSDL_WRONG_NAMESPACE, true);
        final String lowPriorityServices = loadWsdl(LOW_PRIORITY_WSDL, false);

        testRequirementR0042BadWrongValue(List.of(wsdlWrongNamespace, lowPriorityServices));
    }

    /**
     * Tests whether the value of the DiscoveryType attribute is not resolving to
     * http://standards.ieee.org/downloads/11073/11073-10207-2017}ServiceProvider because a DiscoveryType is set
     * causes the test to fail.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR0042BadWrongDiscoveryType() throws Exception {
        final String lowPriorityServices = loadWsdl(LOW_PRIORITY_WSDL, false);
        final String wsdlWrongDiscoveryType = loadWsdl(HIGH_PRIORITY_WSDL_WRONG_DISCOVERYTYPE, true);

        testRequirementR0042BadWrongValue(List.of(wsdlWrongDiscoveryType, lowPriorityServices));
    }

    /**
     * Tests whether the value of the DiscoveryType attribute is not resolving to
     * http://standards.ieee.org/downloads/11073/11073-10207-2017}ServiceProvider because the used prefix is undefined
     * causes the test to fail.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR0042BadUndefinedPrefix() throws Exception {
        final String lowPriorityServices = loadWsdl(LOW_PRIORITY_WSDL, false);
        final String wsdlUndefinedPrefix = loadWsdl(HIGH_PRIORITY_WSDL_UNDEFINED_PREFIX, true);

        testRequirementR0042BadWrongValue(List.of(wsdlUndefinedPrefix, lowPriorityServices));
    }

    /**
     * General implementation for a negative Test of RequirementR0042. Does the general setup and expects
     * the test to fail.
     *
     * @throws Exception on any exception
     */
    private void testRequirementR0042BadWrongValue(final List<String> wsdls) throws Exception {
        final var hostedService = mock(HostedServiceProxy.class, Mockito.RETURNS_DEEP_STUBS);
        when(hostedService.getType().getTypes()).thenReturn(
            Stream.concat(HIGH_PRIORITY_SERVICES.stream(), LOW_PRIORITY_SERVICES.stream()).collect(Collectors.toList())
        );
        final var map = Map.of("hostedService", hostedService);
        final var mockHostingService = mock(HostingServiceProxy.class);
        when(testClient.getHostingServiceProxy()).thenReturn(mockHostingService);
        when(mockHostingService.getHostedServices()).thenReturn(map);


        when(wsdlRetriever.retrieveWsdls(any()))
            .thenReturn(Map.of("hostedService", wsdls));
        // test with given wsdls and expect failure
        assertThrows(AssertionError.class, testClass::testRequirementR0042);
    }

    @SuppressFBWarnings(value = {"RCN_REDUNDANT_NULLCHECK_WOULD_HAVE_BEEN_A_NPE"},
        justification = "Bug in spotbugs, when using try for resources.")
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
