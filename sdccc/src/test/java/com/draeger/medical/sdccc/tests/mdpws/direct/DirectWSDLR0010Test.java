/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.tests.mdpws.direct;

import com.draeger.medical.sdccc.sdcri.testclient.TestClient;
import com.draeger.medical.sdccc.sdcri.testclient.TestClientUtil;
import com.draeger.medical.sdccc.tests.InjectorTestBase;
import com.draeger.medical.sdccc.tests.test_util.InjectorUtil;
import com.draeger.medical.sdccc.tests.util.NoTestData;
import com.draeger.medical.sdccc.util.Constants;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.somda.sdc.dpws.soap.exception.TransportException;
import org.somda.sdc.dpws.wsdl.WsdlRetriever;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for MDPWS:R0010.
 */
public class DirectWSDLR0010Test {

    private DirectWSDLTest testClass;
    private WsdlRetriever mockRetriever;

    @BeforeEach
    void setUp() throws IOException {
        final TestClient mockClient = mock(TestClient.class, Mockito.RETURNS_DEEP_STUBS);
        when(mockClient.isClientRunning()).thenReturn(true);
        mockRetriever = mock(WsdlRetriever.class);

        final Injector injector = InjectorUtil.setupInjector(
                new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(TestClient.class).toInstance(mockClient);
                    }
                }
        );
        InjectorTestBase.setInjector(injector);

        // setup the injector used by sdcri
        final var clientInjector = TestClientUtil.createClientInjector(
                new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(WsdlRetriever.class).toInstance(mockRetriever);
                    }
                }
        );
        when(mockClient.getInjector()).thenReturn(clientInjector);

        testClass = new DirectWSDLTest();
        testClass.setUp();
    }

    /**
     * Tests whether no test data is causing the test to fail.
     */
    @Test
    public void testRequirement0010NoTestData() throws IOException, TransportException {
        when(mockRetriever.retrieveWsdls(any())).thenReturn(Collections.emptyMap());
        assertThrows(NoTestData.class, testClass::testRequirementR0010);
    }

    /**
     * Tests whether a WSDL with a wsp:Policy directly in the binding passes.
     *
     * @throws Exception on any exception
     */
    @Test
    @SuppressFBWarnings(
            value = {"RCN_REDUNDANT_NULLCHECK_WOULD_HAVE_BEEN_A_NPE"},
            justification = "No unnecessary null check."
    )
    public void testRequirement0010GoodPolicy() throws Exception {
        final String message;
        try (final var messageStream = DirectWSDLUtilTest.class
                .getResourceAsStream("DirectWSDLTestTest/R0010_R0011_Good_Policy.xml")) {
            message = new String(messageStream.readAllBytes(), StandardCharsets.UTF_8);
        }
        final var serviceName = "someService";

        when(mockRetriever.retrieveWsdls(any())).thenReturn(Map.of(serviceName, List.of(message)));
        testClass.testRequirementR0010();
    }

    /**
     * Tests whether a WSDL with a valid wsp:PolicyReference in the binding passes.
     *
     * @throws Exception on any exception
     */
    @Test
    @SuppressFBWarnings(
            value = {"RCN_REDUNDANT_NULLCHECK_WOULD_HAVE_BEEN_A_NPE"},
            justification = "No unnecessary null check."
    )
    public void testRequirement0010GoodPolicyReference() throws Exception {
        final String message;
        try (final var messageStream = DirectWSDLUtilTest.class
                .getResourceAsStream("DirectWSDLTestTest/R0010_R0011_Good_PolicyReference.xml")) {
            message = new String(messageStream.readAllBytes(), StandardCharsets.UTF_8);
        }
        final var serviceName = "someService";

        when(mockRetriever.retrieveWsdls(any())).thenReturn(Map.of(serviceName, List.of(message)));
        testClass.testRequirementR0010();
    }

    /**
     * Tests whether a WSDL with no policies fails.
     *
     * @throws Exception on any exception
     */
    @Test
    @SuppressFBWarnings(
            value = {"RCN_REDUNDANT_NULLCHECK_WOULD_HAVE_BEEN_A_NPE"},
            justification = "No unnecessary null check."
    )
    public void testRequirement0010BadNoPolicy() throws Exception {
        final String message;
        try (final var messageStream = DirectWSDLUtilTest.class
                .getResourceAsStream("DirectWSDLTestTest/R0010_R0011_Bad_No_Policies.xml")) {
            message = new String(messageStream.readAllBytes(), StandardCharsets.UTF_8);
        }
        final var serviceName = "someService";

        when(mockRetriever.retrieveWsdls(any())).thenReturn(Map.of(serviceName, List.of(message)));
        assertThrows(AssertionError.class, testClass::testRequirementR0010);
    }

    /**
     * Tests whether a WSDL with a missing policy in one binding fails.
     *
     * @throws Exception on any exception
     */
    @Test
    @SuppressFBWarnings(
            value = {"RCN_REDUNDANT_NULLCHECK_WOULD_HAVE_BEEN_A_NPE"},
            justification = "No unnecessary null check."
    )
    public void testRequirement0010BadMissingPolicy() throws Exception {
        final String message;
        try (final var messageStream = DirectWSDLUtilTest.class
                .getResourceAsStream("DirectWSDLTestTest/R0010_R0011_Bad_Missing_Policy.xml")) {
            message = new String(messageStream.readAllBytes(), StandardCharsets.UTF_8);
        }
        final var serviceName = "someService";

        when(mockRetriever.retrieveWsdls(any())).thenReturn(Map.of(serviceName, List.of(message)));
        assertThrows(AssertionError.class, testClass::testRequirementR0010);
    }

    /**
     * Tests whether a WSDL with a policy in a portType fails.
     *
     * @throws Exception on any exception
     */
    @Test
    @SuppressFBWarnings(
            value = {"RCN_REDUNDANT_NULLCHECK_WOULD_HAVE_BEEN_A_NPE"},
            justification = "No unnecessary null check."
    )
    public void testRequirement0010BadPolicyInPortType() throws Exception {
        final String message;
        try (final var messageStream = DirectWSDLUtilTest.class
                .getResourceAsStream("DirectWSDLTestTest/R0010_R0011_Bad_Policy_In_PortType.xml")) {
            message = new String(messageStream.readAllBytes(), StandardCharsets.UTF_8);
        }
        final var serviceName = "someService";

        when(mockRetriever.retrieveWsdls(any())).thenReturn(Map.of(serviceName, List.of(message)));
        assertThrows(AssertionError.class, testClass::testRequirementR0010);
    }

    /**
     * Tests whether a WSDL with a policyURIs attribute in a portType fails.
     *
     * @throws Exception on any exception
     */
    @Test
    @SuppressFBWarnings(
        value = {"RCN_REDUNDANT_NULLCHECK_WOULD_HAVE_BEEN_A_NPE"},
        justification = "No unnecessary null check."
    )
    public void testRequirement0010BadPolicyURIsInPortType() throws Exception {
        final String message;
        try (final var messageStream = DirectWSDLUtilTest.class
            .getResourceAsStream("DirectWSDLTestTest/R0010_R0011_Bad_PolicyURIs_In_PortType.xml")) {
            message = new String(messageStream.readAllBytes(), StandardCharsets.UTF_8);
        }
        final var serviceName = "someService";

        when(mockRetriever.retrieveWsdls(any())).thenReturn(Map.of(serviceName, List.of(message)));
        final var error = assertThrows(AssertionError.class, testClass::testRequirementR0010);
        // make sure the reason contains PolicyURIs
        assertTrue(error.getMessage().contains(Constants.WSP_POLICY_URIS));
    }
}
