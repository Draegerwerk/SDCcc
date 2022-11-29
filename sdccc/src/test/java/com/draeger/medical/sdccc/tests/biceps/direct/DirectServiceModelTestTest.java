/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.tests.biceps.direct;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.draeger.medical.sdccc.sdcri.testclient.TestClient;
import com.draeger.medical.sdccc.sdcri.testclient.TestClientUtil;
import com.draeger.medical.sdccc.tests.InjectorTestBase;
import com.draeger.medical.sdccc.tests.test_util.InjectorUtil;
import com.draeger.medical.sdccc.tests.util.HostedServiceVerifier;
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
import org.mockito.Mockito;
import org.somda.sdc.dpws.helper.JaxbMarshalling;
import org.somda.sdc.dpws.service.HostedServiceProxy;
import org.somda.sdc.dpws.service.HostingServiceProxy;
import org.somda.sdc.dpws.wsdl.WsdlMarshalling;
import org.somda.sdc.dpws.wsdl.WsdlRetriever;
import org.somda.sdc.glue.common.WsdlConstants;
import org.somda.sdc.glue.provider.SdcDevice;

/**
 * Unit test for the BICEPS DirectServiceModelTest.
 */
public class DirectServiceModelTestTest {
    private static final Duration MAX_WAIT = Duration.ofSeconds(10);
    private static final String LOW_PRIORITY_WSDL = "wsdl/IEEE11073-20701-LowPriority-Services.wsdl";
    private static final String HIGH_PRIORITY_WSDL = "wsdl/IEEE11073-20701-HighPriority-Services.wsdl";

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
    private DirectServiceModelTest testClass;
    private WsdlRetriever wsdlRetriever;
    private JaxbMarshalling jaxbMarshalling;
    private WsdlMarshalling wsdlMarshalling;
    private HostedServiceVerifier hostedServiceVerifier;

    @BeforeEach
    void setUp() throws IOException, TimeoutException {
        final TestClient mockClient = mock(TestClient.class);
        when(mockClient.isClientRunning()).thenReturn(true);
        testClient = mockClient;

        wsdlRetriever = mock(WsdlRetriever.class);

        // setup the injector used by sdcri
        final var clientInjector = TestClientUtil.createClientInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(WsdlRetriever.class).toInstance(wsdlRetriever);
            }
        });
        when(testClient.getInjector()).thenReturn(clientInjector);

        final var originalHostedServiceVerifier = new HostedServiceVerifier(testClient);
        hostedServiceVerifier = spy(originalHostedServiceVerifier);

        final Injector injector = InjectorUtil.setupInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(TestClient.class).toInstance(testClient);
                bind(HostedServiceVerifier.class).toInstance(hostedServiceVerifier);
            }
        });

        InjectorTestBase.setInjector(injector);

        jaxbMarshalling = testClient.getInjector().getInstance(JaxbMarshalling.class);
        wsdlMarshalling = testClient.getInjector().getInstance(WsdlMarshalling.class);

        jaxbMarshalling.startAsync().awaitRunning(MAX_WAIT);
        wsdlMarshalling.startAsync().awaitRunning(MAX_WAIT);

        testClass = new DirectServiceModelTest();
        testClass.setUp();
    }

    @AfterEach
    void tearDown() throws TimeoutException {
        wsdlMarshalling.stopAsync().awaitTerminated(MAX_WAIT);
        jaxbMarshalling.stopAsync().awaitTerminated(MAX_WAIT);
    }

    /**
     * Tests whether valid responses are passing.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement0062Good() throws Exception {
        setupServiceModelRequirementTest();
        testClass.testRequirement0062();
    }

    /**
     * Tests whether a missing GetService endpoint causes the test to fail.
     */
    @Test
    public void testRequirement0062Bad() {
        setupMissingServiceModelRequirementTest(getQNameList(WsdlConstants.PORT_TYPE_GET_QNAME));
        assertThrows(AssertionError.class, () -> testClass.testRequirement0062());
        verify(hostedServiceVerifier, never()).verifyHostedService(any());
    }

    /**
     * Tests whether valid responses are passing.
     *
     * @throws Exception on any Exception
     */
    @Test
    public void testRequirement0064Good() throws Exception {
        setupServiceModelRequirementTest();
        testClass.testRequirement0064();
    }

    /**
     * Tests whether a missing StateEventService endpoint causes the test to fail.
     */
    @Test
    public void testRequirement0064Bad() {
        setupMissingServiceModelRequirementTest(getQNameList(WsdlConstants.PORT_TYPE_STATE_EVENT_QNAME));
        assertThrows(AssertionError.class, () -> testClass.testRequirement0064());
        verify(hostedServiceVerifier, never()).verifyHostedService(any());
    }

    /**
     * Tests whether valid responses are passing.
     *
     * @throws Exception on any Exception
     */
    @Test
    public void testRequirement0066Good() throws Exception {
        setupServiceModelRequirementTest();
        testClass.testRequirement0066();
    }

    /**
     * Tests whether a missing WaveformService endpoint causes the test to fail.
     */
    @Test
    public void testRequirement0066Bad() {
        setupMissingServiceModelRequirementTest(getQNameList(WsdlConstants.PORT_TYPE_WAVEFORM_QNAME));
        assertThrows(AssertionError.class, () -> testClass.testRequirement0066());
        verify(hostedServiceVerifier, never()).verifyHostedService(any());
    }

    /**
     * Tests whether valid responses are passing.
     *
     * @throws Exception on any Exception
     */
    @Test
    public void testRequirement0068Good() throws Exception {
        setupServiceModelRequirementTest();
        testClass.testRequirement0068();
    }

    /**
     * Tests whether a missing SetService endpoint causes the test to fail.
     */
    @Test
    public void testRequirement0068Bad() {
        setupMissingServiceModelRequirementTest(getQNameList(WsdlConstants.PORT_TYPE_SET_QNAME));
        assertThrows(AssertionError.class, () -> testClass.testRequirement0068());
        verify(hostedServiceVerifier, never()).verifyHostedService(any());
    }

    /**
     * Tests whether valid responses are passing.
     *
     * @throws Exception on any Exception
     */
    @Test
    public void testRequirement0069Good() throws Exception {
        setupServiceModelRequirementTest();
        testClass.testRequirement0069();
    }

    /**
     * Tests whether a missing ContextService endpoint causes the test to fail.
     */
    @Test
    public void testRequirement0069Bad() {
        setupMissingServiceModelRequirementTest(getQNameList(WsdlConstants.PORT_TYPE_CONTEXT_QNAME));
        assertThrows(AssertionError.class, () -> testClass.testRequirement0069());
        verify(hostedServiceVerifier, never()).verifyHostedService(any());
    }

    /**
     * Tests whether valid responses are passing.
     *
     * @throws Exception on any Exception
     */
    @Test
    public void testRequirement0100Good() throws Exception {
        setupServiceModelRequirementTest();
        testClass.testRequirement0100();
    }

    /**
     * Tests whether a missing ArchiveService endpoint causes the test to fail.
     */
    @Test
    public void testRequirement0100Bad() {
        setupMissingServiceModelRequirementTest(getQNameList(WsdlConstants.PORT_TYPE_ARCHIVE_QNAME));
        assertThrows(AssertionError.class, () -> testClass.testRequirement0100());
        verify(hostedServiceVerifier, never()).verifyHostedService(any());
    }

    /**
     * Tests whether valid responses are passing.
     *
     * @throws Exception on any Exception
     */
    @Test
    public void testRequirement0101Good() throws Exception {
        setupServiceModelRequirementTest();
        testClass.testRequirement0101();
    }

    /**
     * Tests whether a missing LocalizationService endpoint causes the test to fail.
     */
    @Test
    public void testRequirement0101Bad() {
        setupMissingServiceModelRequirementTest(getQNameList(WsdlConstants.PORT_TYPE_LOCALIZATION_QNAME));
        assertThrows(AssertionError.class, () -> testClass.testRequirement0101());
        verify(hostedServiceVerifier, never()).verifyHostedService(any());
    }

    /**
     * Tests whether valid responses are passing.
     *
     * @throws Exception on any Exception
     */
    @Test
    public void testRequirement0104Good() throws Exception {
        setupServiceModelRequirementTest();
        testClass.testRequirement0104();
    }

    /**
     * Tests whether a missing DescriptionEventService endpoint causes the test to fail.
     */
    @Test
    public void testRequirement0104Bad() {
        setupMissingServiceModelRequirementTest(getQNameList(WsdlConstants.PORT_TYPE_DESCRIPTION_EVENT_QNAME));
        assertThrows(AssertionError.class, () -> testClass.testRequirement0104());
        verify(hostedServiceVerifier, never()).verifyHostedService(any());
    }

    /**
     * Tests whether valid responses are passing.
     *
     * @throws Exception on any Exception
     */
    @Test
    public void testRequirement0119Good() throws Exception {
        setupServiceModelRequirementTest();
        testClass.testRequirement0119();
    }

    /**
     * Tests whether a missing ContainmentTreeService endpoint causes the test to fail.
     */
    @Test
    public void testRequirement0119Bad() {
        setupMissingServiceModelRequirementTest(getQNameList(WsdlConstants.PORT_TYPE_CONTAINMENT_TREE_QNAME));
        assertThrows(AssertionError.class, () -> testClass.testRequirement0119());
        verify(hostedServiceVerifier, never()).verifyHostedService(any());
    }

    private void setupServiceModelRequirementTest() throws Exception {
        // create hosted services
        final var hostedService = mock(HostedServiceProxy.class, Mockito.RETURNS_DEEP_STUBS);
        when(hostedService.getType().getTypes())
                .thenReturn(Stream.concat(HIGH_PRIORITY_SERVICES.stream(), LOW_PRIORITY_SERVICES.stream())
                        .collect(Collectors.toList()));
        final var map = Map.of("hostedService", hostedService);
        final var mockHostingService = mock(HostingServiceProxy.class);
        when(testClient.getHostingServiceProxy()).thenReturn(mockHostingService);
        when(mockHostingService.getHostedServices()).thenReturn(map);

        final String wsdl = loadWsdl(HIGH_PRIORITY_WSDL);
        final String wsdl2 = loadWsdl(LOW_PRIORITY_WSDL);
        when(wsdlRetriever.retrieveWsdls(any())).thenReturn(Map.of("hostedService", List.of(wsdl, wsdl2)));
    }

    private void setupMissingServiceModelRequirementTest(final List<QName> hostedServiceTypes) {
        // create hosted services
        final var hostedService = mock(HostedServiceProxy.class, Mockito.RETURNS_DEEP_STUBS);
        when(hostedService.getType().getTypes()).thenReturn(hostedServiceTypes);
        final var map = Map.of("hostedService", hostedService);
        final var mockHostingService = mock(HostingServiceProxy.class);
        when(testClient.getHostingServiceProxy()).thenReturn(mockHostingService);
        when(mockHostingService.getHostedServices()).thenReturn(map);
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

    private List<QName> getQNameList(final QName removedQName) {
        return Stream.concat(HIGH_PRIORITY_SERVICES.stream(), LOW_PRIORITY_SERVICES.stream())
                .filter(qname -> !qname.equals(removedQName))
                .collect(Collectors.toList());
    }
}
