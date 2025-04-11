/*
 * This Source Code Form is subject to the terms of the "SDCcc non-commercial use license".
 *
 * Copyright (C) 2025 Draegerwerk AG & Co. KGaA
 */

package com.draeger.medical.sdccc.tests.mdpws.direct;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.draeger.medical.sdccc.tests.util.NoTestData;
import com.draeger.medical.sdccc.util.HttpClientUtil;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.somda.sdc.common.guice.AbstractConfigurationModule;
import org.somda.sdc.dpws.DpwsConfig;
import org.somda.sdc.dpws.helper.JaxbMarshalling;
import org.somda.sdc.dpws.http.apache.ApacheTransportBindingFactoryImpl;
import org.somda.sdc.dpws.soap.SoapMarshalling;
import org.somda.sdc.dpws.soap.SoapMessage;
import org.somda.sdc.dpws.soap.SoapUtil;
import org.somda.sdc.dpws.soap.exception.SoapFaultException;
import org.somda.sdc.dpws.soap.model.Fault;
import org.somda.sdc.dpws.soap.wsdiscovery.WsDiscoveryConstants;
import org.somda.sdc.dpws.soap.wsdiscovery.model.ObjectFactory;

/**
 * Unit test for the MDPWS {@linkplain DirectSecurityConsiderationsTest}.
 */
public class DirectSecurityConsiderationsTestTest {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    private DirectSecurityConsiderationsTest testClass;
    private HttpClientUtil mockClientUtil;
    private TestClient testClient;
    private JaxbMarshalling baseMarshalling;
    private SoapMarshalling marshalling;
    private ObjectFactory wsdFactory;
    private SoapUtil soapUtil;
    private ApacheTransportBindingFactoryImpl mockApacheFactory;

    @BeforeEach
    void setUp() throws IOException, TimeoutException {
        testClient = mock(TestClient.class, Mockito.RETURNS_DEEP_STUBS);
        when(testClient.isClientRunning()).thenReturn(true);
        mockClientUtil = mock(HttpClientUtil.class);
        mockApacheFactory = mock(ApacheTransportBindingFactoryImpl.class);

        final Injector injector = InjectorUtil.setupInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(TestClient.class).toInstance(testClient);
            }
        });
        InjectorTestBase.setInjector(injector);

        // setup the injector used by sdcri
        final var clientInjector = TestClientUtil.createClientInjector(
                new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(HttpClientUtil.class).toInstance(mockClientUtil);
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

        wsdFactory = clientInjector.getInstance(ObjectFactory.class);
        soapUtil = clientInjector.getInstance(SoapUtil.class);

        baseMarshalling = clientInjector.getInstance(JaxbMarshalling.class);
        baseMarshalling.startAsync().awaitRunning(DEFAULT_TIMEOUT);

        marshalling = clientInjector.getInstance(SoapMarshalling.class);
        marshalling.startAsync().awaitRunning(DEFAULT_TIMEOUT);

        testClass = new DirectSecurityConsiderationsTest();
        testClass.setUp();
    }

    @AfterEach
    void tearDown() throws TimeoutException {
        baseMarshalling.stopAsync().awaitTerminated(DEFAULT_TIMEOUT);
        marshalling.stopAsync().awaitTerminated(DEFAULT_TIMEOUT);
    }

    private SoapMessage createProbeMatches() {
        final var matchType = wsdFactory.createProbeMatchesType();

        return soapUtil.createMessage(
                WsDiscoveryConstants.WSA_ACTION_PROBE_MATCHES, wsdFactory.createProbeMatches(matchType));
    }

    /**
     * Tests whether present xAddresses are properly queried and the result is checked to be a ProbeMatches message.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR0015Good() throws Exception {
        final var xAddresses = List.of("https://MeanAddress.location", "https://NastyAddress.location/");
        when(testClient.getTargetXAddrs()).thenReturn(xAddresses);

        final var mockResponse = createProbeMatches();
        when(mockClientUtil.postMessage(any(), any(), any())).thenReturn(mockResponse);

        testClass.testRequirementR0015();

        final var addressCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockClientUtil, times(2)).postMessage(any(), addressCaptor.capture(), any());

        assertEquals(xAddresses, addressCaptor.getAllValues());
    }

    /**
     * Tests whether no xAddresses cause the test to fail.
     */
    @Test
    public void testRequirementR0015BadNoXAddr() {
        when(testClient.getTargetXAddrs()).thenReturn(Collections.emptyList());

        assertThrows(NoTestData.class, testClass::testRequirementR0015);
    }

    /**
     * Tests whether a fault response to a probe causes the test to fail.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR0015BadSoapFault() throws Exception {
        final var xAddresses = List.of("https://MeanAddress.location", "https://NastyAddress.location/");
        when(testClient.getTargetXAddrs()).thenReturn(xAddresses);

        final var mockFaultMessage = mock(SoapMessage.class, Mockito.RETURNS_DEEP_STUBS);
        final jakarta.xml.bind.JAXBElement<Fault> mockJaxbFault =
                mock(jakarta.xml.bind.JAXBElement.class, Mockito.RETURNS_DEEP_STUBS);
        when(mockFaultMessage.getOriginalEnvelope().getBody().getAny().get(0)).thenReturn(mockJaxbFault);

        final var mockFault = mock(Fault.class, Mockito.RETURNS_DEEP_STUBS);
        when(mockJaxbFault.getValue()).thenReturn(mockFault);

        final var soapFault = new SoapFaultException(mockFaultMessage);
        final var mockResponse = createProbeMatches();
        when(mockClientUtil.postMessage(any(), any(), any()))
                .thenReturn(mockResponse)
                .thenThrow(soapFault);

        assertThrows(AssertionError.class, testClass::testRequirementR0015);

        final var addressCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockClientUtil, times(2)).postMessage(any(), addressCaptor.capture(), any());

        assertEquals(xAddresses, addressCaptor.getAllValues());
    }

    /**
     * Tests whether the requirement test fails if unencrypted http support is enabled in the http client.
     */
    @Test
    public void testRequirementR0015BadHttpEnabled() {
        final var clientInjector = TestClientUtil.createClientInjector(
                new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(HttpClientUtil.class).toInstance(mockClientUtil);
                        bind(ApacheTransportBindingFactoryImpl.class).toInstance(mockApacheFactory);
                    }
                },
                new AbstractConfigurationModule() {
                    @Override
                    protected void defaultConfigure() {
                        bind(DpwsConfig.HTTP_SUPPORT, Boolean.class, true);
                        bind(DpwsConfig.HTTPS_SUPPORT, Boolean.class, true);
                    }
                });
        when(testClient.getInjector()).thenReturn(clientInjector);

        final var error = assertThrows(AssertionError.class, testClass::testRequirementR0015);
        assertTrue(error.getMessage().contains(DirectSecurityConsiderationsTest.HTTP_ENABLED_ERROR));
    }
}
