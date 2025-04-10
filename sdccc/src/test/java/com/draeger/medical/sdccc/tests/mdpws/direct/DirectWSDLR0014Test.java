/*
 * This Source Code Form is subject to the terms of the "SDCcc non-commercial use license".
 *
 * Copyright (C) 2025 Draegerwerk AG & Co. KGaA
 */

package com.draeger.medical.sdccc.tests.mdpws.direct;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.draeger.medical.sdccc.sdcri.testclient.TestClient;
import com.draeger.medical.sdccc.sdcri.testclient.TestClientUtil;
import com.draeger.medical.sdccc.tests.InjectorTestBase;
import com.draeger.medical.sdccc.tests.test_util.InjectorUtil;
import com.draeger.medical.sdccc.tests.util.NoTestData;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.somda.sdc.dpws.soap.exception.TransportException;
import org.somda.sdc.dpws.wsdl.WsdlRetriever;

/**
 * Unit tests for MDPWS:R0014.
 */
public class DirectWSDLR0014Test {

    private DirectWSDLTest testClass;
    private WsdlRetriever mockRetriever;

    @BeforeEach
    void setUp() throws IOException {
        final TestClient mockClient = mock(TestClient.class, Mockito.RETURNS_DEEP_STUBS);
        when(mockClient.isClientRunning()).thenReturn(true);
        mockRetriever = mock(WsdlRetriever.class);
        final Injector injector = InjectorUtil.setupInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(TestClient.class).toInstance(mockClient);
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
        when(mockClient.getInjector()).thenReturn(clientInjector);

        testClass = new DirectWSDLTest();
        testClass.setUp();
    }

    /**
     * Tests whether no test data is causing the test to fail.
     */
    @Test
    public void testRequirement0014NoTestData() throws IOException, TransportException {
        when(mockRetriever.retrieveWsdls(any())).thenReturn(Collections.emptyMap());
        assertThrows(NoTestData.class, testClass::testRequirementR0014);
    }

    /**
     * Tests whether a WSDL containing the dpws:DiscoveryType passes the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement0014Good() throws Exception {
        final String message;
        try (final var messageStream =
                DirectWSDLUtilTest.class.getResourceAsStream("DirectWSDLTestTest/R0014_Good.xml")) {
            assertNotNull(messageStream);
            message = new String(messageStream.readAllBytes(), StandardCharsets.UTF_8);
        }
        final var serviceName = "ecivres";

        when(mockRetriever.retrieveWsdls(any())).thenReturn(Map.of(serviceName, List.of(message)));
        testClass.testRequirementR0014();
    }

    /**
     * Tests whether a WSDL missing the dpws:DiscoveryType passes the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement0014Bad() throws Exception {
        final String message;
        try (final var messageStream =
                DirectWSDLUtilTest.class.getResourceAsStream("DirectWSDLTestTest/R0014_Bad.xml")) {
            assertNotNull(messageStream);
            message = new String(messageStream.readAllBytes(), StandardCharsets.UTF_8);
        }
        final var serviceName = "someService";

        when(mockRetriever.retrieveWsdls(any())).thenReturn(Map.of(serviceName, List.of(message)));
        assertThrows(AssertionError.class, testClass::testRequirementR0014);
    }
}
