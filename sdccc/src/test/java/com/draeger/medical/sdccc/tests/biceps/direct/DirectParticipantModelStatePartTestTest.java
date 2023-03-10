/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2023 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.tests.biceps.direct;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.draeger.medical.sdccc.manipulation.Manipulations;
import com.draeger.medical.sdccc.sdcri.testclient.TestClient;
import com.draeger.medical.sdccc.sdcri.testclient.TestClientUtil;
import com.draeger.medical.sdccc.tests.InjectorTestBase;
import com.draeger.medical.sdccc.tests.test_util.InjectorUtil;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.somda.sdc.biceps.model.message.GetMdibResponse;
import org.somda.sdc.biceps.model.participant.MdState;
import org.somda.sdc.biceps.model.participant.Mdib;
import org.somda.sdc.dpws.service.HostedServiceProxy;
import org.somda.sdc.dpws.service.HostingServiceProxy;
import org.somda.sdc.dpws.soap.SoapMessage;
import org.somda.sdc.dpws.soap.exception.MarshallingException;
import org.somda.sdc.dpws.soap.exception.SoapFaultException;
import org.somda.sdc.dpws.soap.exception.TransportException;
import org.somda.sdc.dpws.soap.interception.InterceptorException;
import org.somda.sdc.glue.common.WsdlConstants;

/**
 * Unit test for the BICEPS {@linkplain DirectParticipantModelStatePartTest}.
 */
public class DirectParticipantModelStatePartTestTest {

    private TestClient testClient;
    private DirectParticipantModelStatePartTest testClass;

    private Mdib mdib;

    private Manipulations manipulations;

    @BeforeEach
    void setUp() throws IOException {
        final TestClient mockClient = mock(TestClient.class, RETURNS_DEEP_STUBS);
        when(mockClient.isClientRunning()).thenReturn(true);
        testClient = mockClient;

        final var clientInjector = TestClientUtil.createClientInjector();
        when(testClient.getInjector()).thenReturn(clientInjector);

        manipulations = mock(Manipulations.class);
        final Injector injector = InjectorUtil.setupInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(TestClient.class).toInstance(testClient);
                bind(Manipulations.class).toInstance(manipulations);
            }
        });

        InjectorTestBase.setInjector(injector);

        testClass = new DirectParticipantModelStatePartTest();
        testClass.setUp();
    }

    /**
     * Tests whether a mdib containing a MdState element is passing the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement0021Good() throws Exception {
        setupTestDevice();
        final var mdState = mock(MdState.class);
        when(mdib.getMdState()).thenReturn(mdState);
        testClass.testRequirementR0021();
    }

    /**
     * Tests whether a mdib not containing a MdState element is failing the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement0021Bad() throws Exception {
        setupTestDevice();
        assertThrows(AssertionError.class, testClass::testRequirementR0021);
    }

    void setupTestDevice() throws InterceptorException, SoapFaultException, MarshallingException, TransportException {
        mdib = mock(Mdib.class);
        final var getMdibResponse = mock(GetMdibResponse.class);
        when(getMdibResponse.getMdib()).thenReturn(mdib);
        final var messageResponse = mock(SoapMessage.class, RETURNS_DEEP_STUBS);
        when(messageResponse.getOriginalEnvelope().getBody().getAny().get(0)).thenReturn(getMdibResponse);

        final var hostedService = mock(HostedServiceProxy.class, Mockito.RETURNS_DEEP_STUBS);
        when(hostedService.getType().getTypes()).thenReturn(List.of(WsdlConstants.PORT_TYPE_GET_QNAME));
        when(hostedService.sendRequestResponse(any())).thenReturn(messageResponse);

        final var map = Map.of("hostedService", hostedService);
        final var mockHostingService = mock(HostingServiceProxy.class);
        when(testClient.getHostingServiceProxy()).thenReturn(mockHostingService);
        when(mockHostingService.getHostedServices()).thenReturn(map);
    }
}
