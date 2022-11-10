/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.tests.mdpws.direct;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.draeger.medical.sdccc.configuration.EnabledTestConfig;
import com.draeger.medical.sdccc.sdcri.testclient.TestClient;
import com.draeger.medical.sdccc.tests.InjectorTestBase;
import com.draeger.medical.sdccc.tests.annotations.TestDescription;
import com.draeger.medical.sdccc.tests.annotations.TestIdentifier;
import com.draeger.medical.sdccc.tests.util.NoTestData;
import com.draeger.medical.sdccc.util.HttpClientUtil;
import com.google.inject.Key;
import com.google.inject.name.Names;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;
import javax.xml.namespace.QName;
import org.apache.http.client.HttpClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.somda.sdc.dpws.DpwsConfig;
import org.somda.sdc.dpws.http.apache.ApacheTransportBindingFactoryImpl;
import org.somda.sdc.dpws.soap.MarshallingService;
import org.somda.sdc.dpws.soap.SoapMessage;
import org.somda.sdc.dpws.soap.SoapUtil;
import org.somda.sdc.dpws.soap.exception.MarshallingException;
import org.somda.sdc.dpws.soap.exception.SoapFaultException;
import org.somda.sdc.dpws.soap.exception.TransportException;
import org.somda.sdc.dpws.soap.wsaddressing.WsAddressingUtil;
import org.somda.sdc.dpws.soap.wsaddressing.model.AttributedURIType;
import org.somda.sdc.dpws.soap.wsdiscovery.MatchBy;
import org.somda.sdc.dpws.soap.wsdiscovery.WsDiscoveryConstants;
import org.somda.sdc.dpws.soap.wsdiscovery.model.ObjectFactory;
import org.somda.sdc.dpws.soap.wsdiscovery.model.ProbeMatchesType;
import org.somda.sdc.dpws.soap.wsdiscovery.model.ProbeType;
import org.somda.sdc.dpws.soap.wsdiscovery.model.ScopesType;
import org.somda.sdc.glue.consumer.SdcDiscoveryFilterBuilder;

/**
 * MDPWS security considerations tests (ch. 10).
 */
public class DirectSecurityConsiderationsTest extends InjectorTestBase {
    protected static final String HTTP_ENABLED_ERROR = "unencrypted http is enabled in http client, cannot test";
    protected static final String HTTPS_DISABLED_ERROR = "https is disabled in http client, cannot test";

    private static final Logger LOG = LogManager.getLogger();

    private TestClient testClient;
    private HttpClient httpClient;
    private SoapUtil soapUtil;
    private HttpClientUtil httpClientUtil;
    private ObjectFactory wsdFactory;
    private MarshallingService marshalling;
    private WsAddressingUtil wsaUtil;

    @BeforeEach
    void setUp() {
        testClient = getInjector().getInstance(TestClient.class);
        httpClient = testClient
                .getInjector()
                .getInstance(ApacheTransportBindingFactoryImpl.class)
                .getClient();
        soapUtil = testClient.getInjector().getInstance(SoapUtil.class);
        httpClientUtil = testClient.getInjector().getInstance(HttpClientUtil.class);
        marshalling = testClient.getInjector().getInstance(MarshallingService.class);
        wsaUtil = testClient.getInjector().getInstance(WsAddressingUtil.class);
        wsdFactory = testClient.getInjector().getInstance(ObjectFactory.class);
    }

    @Test
    @TestIdentifier(EnabledTestConfig.MDPWS_R0015)
    @TestDescription("Sends directed probe to all xAddresses provided by the DUT")
    void testRequirementR0015() throws NoTestData, TransportException, SoapFaultException, MarshallingException {

        // ensure the http client can only use tls
        final var httpEnabled =
                testClient.getInjector().getInstance(Key.get(Boolean.class, Names.named(DpwsConfig.HTTP_SUPPORT)));
        final var httpsEnabled =
                testClient.getInjector().getInstance(Key.get(Boolean.class, Names.named(DpwsConfig.HTTPS_SUPPORT)));
        assertFalse(httpEnabled, HTTP_ENABLED_ERROR);
        assertTrue(httpsEnabled, HTTPS_DISABLED_ERROR);

        final var xAddrs = testClient.getTargetXAddrs();
        assertTestData(xAddrs, "no xAddresses are available for the DUT");

        for (final var xAddr : xAddrs) {

            final var filterBuilder = SdcDiscoveryFilterBuilder.create();
            final var filter = filterBuilder.get();

            final var probeMessage = createProbeMessage(filter.getTypes(), filter.getScopes());
            final AttributedURIType msgId =
                    wsaUtil.createAttributedURIType(soapUtil.createUriFromUuid(UUID.randomUUID()));
            probeMessage.getWsAddressingHeader().setMessageId(msgId);
            final var output = new ByteArrayOutputStream();
            try {
                marshalling.marshal(probeMessage, output);
            } catch (final MarshallingException e) {
                LOG.error("Error occurred while sending directed probe to {}. Message: {}", xAddr, e.getMessage());
                LOG.debug("Error occurred while sending directed probe to {}", xAddr, e);
                fail("Error occurred while sending directed probe to " + xAddr);
                // unreachable, silence warnings
                throw e;
            }

            final SoapMessage response;
            try {
                response = httpClientUtil.postMessage(httpClient, xAddr, output.toByteArray());
            } catch (final TransportException e) {
                LOG.error("Did not receive a response to directed probe to {}. Message: {}", xAddr, e.getMessage());
                LOG.debug("Did not receive a response to directed probe to {}.", xAddr, e);
                fail("Did not receive a response to directed probe to " + xAddr);
                // unreachable, silence warnings
                throw e;
            } catch (final SoapFaultException e) {
                LOG.error("Received soap fault in response to probe to {}. Message: {}", xAddr, e.getMessage());
                LOG.debug("Received soap fault in response to probe to {}.", xAddr, e);
                fail("Received soap fault in response to probe to " + xAddr);
                // unreachable, silence warnings
                throw e;
            }

            final var bodyOpt = soapUtil.getBody(response, ProbeMatchesType.class);
            assertTrue(bodyOpt.isPresent());
            assertNotNull(bodyOpt.orElseThrow());
        }
    }

    private SoapMessage createProbeMessage(final Collection<QName> types, final Collection<String> scopes) {
        final ProbeType probeType = wsdFactory.createProbeType();
        probeType.setTypes(new ArrayList<>(types));
        final ScopesType scopesType = wsdFactory.createScopesType();
        // Always create RFC3986 by default
        // See http://docs.oasis-open.org/ws-dd/discovery/1.1/os/wsdd-discovery-1.1-spec-os.html#_Toc234231831
        scopesType.setMatchBy(MatchBy.RFC3986.getUri());
        scopesType.setValue(new ArrayList<>(scopes));
        probeType.setScopes(scopesType);

        return soapUtil.createMessage(
                WsDiscoveryConstants.WSA_ACTION_PROBE,
                // If sent to a Target Service, MUST be "urn:docs-oasis-open-org:ws-dd:ns:discovery:2009:01" [RFC 2141]
                WsDiscoveryConstants.WSA_UDP_TO,
                wsdFactory.createProbe(probeType));
    }
}
