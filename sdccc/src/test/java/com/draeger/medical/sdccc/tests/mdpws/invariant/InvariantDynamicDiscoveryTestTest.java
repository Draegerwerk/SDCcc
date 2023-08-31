/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2023 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.tests.mdpws.invariant;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.somda.sdc.dpws.soap.wsdiscovery.WsDiscoveryConstants.WSA_ACTION_HELLO;
import static org.somda.sdc.dpws.soap.wsdiscovery.WsDiscoveryConstants.WSA_ACTION_PROBE_MATCHES;
import static org.somda.sdc.dpws.soap.wsdiscovery.WsDiscoveryConstants.WSA_ACTION_RESOLVE_MATCHES;

import com.draeger.medical.dpws.soap.model.Envelope;
import com.draeger.medical.dpws.soap.wsdiscovery.model.ProbeMatchType;
import com.draeger.medical.dpws.soap.wsdiscovery.model.ProbeMatchesType;
import com.draeger.medical.sdccc.marshalling.MarshallingUtil;
import com.draeger.medical.sdccc.messages.MessageStorage;
import com.draeger.medical.sdccc.sdcri.testclient.TestClient;
import com.draeger.medical.sdccc.tests.InjectorTestBase;
import com.draeger.medical.sdccc.tests.test_util.InjectorUtil;
import com.draeger.medical.sdccc.tests.util.NoTestData;
import com.draeger.medical.sdccc.util.MessageBuilder;
import com.draeger.medical.sdccc.util.MessageStorageUtil;
import com.google.common.collect.Sets;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;
import java.util.stream.Collectors;
import javax.xml.namespace.QName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.somda.sdc.dpws.DpwsConstants;
import org.somda.sdc.glue.common.CommonConstants;

/**
 * Unit test for the MDPWS {@linkplain InvariantDynamicDiscoveryTest}.
 */
public class InvariantDynamicDiscoveryTestTest {

    private static MessageStorageUtil messageStorageUtil;
    private static MessageBuilder messageBuilder;

    private InvariantDynamicDiscoveryTest testClass;
    private MessageStorage storage;

    @BeforeAll
    static void setupMarshalling() {
        final Injector marshallingInjector = MarshallingUtil.createMarshallingTestInjector(true);
        messageStorageUtil = marshallingInjector.getInstance(MessageStorageUtil.class);
        messageBuilder = marshallingInjector.getInstance(MessageBuilder.class);
    }

    @BeforeEach
    void setUp() throws IOException {
        final TestClient mockClient = mock(TestClient.class);
        when(mockClient.isClientRunning()).thenReturn(true);

        final Injector injector = InjectorUtil.setupInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(TestClient.class).toInstance(mockClient);
            }
        });

        InjectorTestBase.setInjector(injector);

        storage = injector.getInstance(MessageStorage.class);
        testClass = new InvariantDynamicDiscoveryTest();
    }

    @AfterEach
    void tearDown() {
        storage.close();
    }

    /**
     * Tests whether calling the tests without any input data causes a failure.
     */
    @Test
    public void testNoData() {
        assertThrows(NoTestData.class, testClass::testRequirement0008);
    }

    /**
     * Tests whether a valid Hello, ProbeMatches and ResolveMatches message pass the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testR0008Good() throws Exception {
        messageStorageUtil.addInboundUdpMessage(storage, buildHello(true, true));
        messageStorageUtil.addInboundUdpMessage(storage, buildProbeMatches(true, true));
        messageStorageUtil.addInboundUdpMessage(storage, buildResolveMatches(true, true));

        testClass.testRequirement0008();
    }

    /**
     * Tests whether missing one of the actions causes the tests to fail.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testR0008BadMissingOneMessage() throws Exception {

        final var testMessages =
                Set.of(buildHello(true, true), buildProbeMatches(true, true), buildResolveMatches(true, true));

        // generate all sets with one missing message
        final var testSets = Sets.powerSet(testMessages).stream()
                .filter(set -> set.size() == testMessages.size() - 1)
                .collect(Collectors.toSet());

        for (final var set : testSets) {
            tearDown();
            setUp();

            for (final Envelope message : set) {
                messageStorageUtil.addInboundUdpMessage(storage, message);
            }
            assertThrows(NoTestData.class, testClass::testRequirement0008);
        }
    }

    /**
     * Tests whether a message with missing dpws:Device causes a failure.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testR0008BadDpwsDeviceMissing() throws Exception {
        for (int i = 0; i < 3; i++) {
            tearDown();
            setUp();

            messageStorageUtil.addInboundUdpMessage(storage, buildHello(i == 0, true));
            messageStorageUtil.addInboundUdpMessage(storage, buildProbeMatches(i == 1, true));
            messageStorageUtil.addInboundUdpMessage(storage, buildResolveMatches(i == 2, true));

            assertThrows(AssertionError.class, testClass::testRequirement0008);
        }
    }

    /**
     * Tests whether a message missing mdpws:MedicalDevice causes a failure.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testR0008BadMdpwsMedicalDeviceMissing() throws Exception {
        for (int i = 0; i < 3; i++) {
            tearDown();
            setUp();

            messageStorageUtil.addInboundUdpMessage(storage, buildHello(true, i == 0));
            messageStorageUtil.addInboundUdpMessage(storage, buildProbeMatches(true, i == 1));
            messageStorageUtil.addInboundUdpMessage(storage, buildResolveMatches(true, i == 2));

            assertThrows(AssertionError.class, testClass::testRequirement0008);
        }
    }

    @Test
    void testR0008MultipleProbeMatch() throws IOException, JAXBException {
        messageStorageUtil.addInboundUdpMessage(storage, buildHello(true, true));
        messageStorageUtil.addInboundUdpMessage(storage, buildProbeMatches(true, true));
        messageStorageUtil.addInboundUdpMessage(storage, buildResolveMatches(true, true));

        final var numberOfMatches = 3;

        final var proxyProbeMatch = buildProbeMatches(true, true, numberOfMatches);
        final JAXBElement<ProbeMatchesType> matches = (JAXBElement<ProbeMatchesType>)
                proxyProbeMatch.getBody().getAny().get(0);
        assertNotNull(matches);

        // remove one type from the last entry
        final var lastMatch = matches.getValue().getProbeMatch().get(numberOfMatches - 1);
        lastMatch.getTypes().remove(0);

        messageStorageUtil.addInboundUdpMessage(storage, proxyProbeMatch);

        assertThrows(AssertionError.class, testClass::testRequirement0008);
    }

    Envelope buildHello(final boolean dpwsType, final boolean mdpwsType) {
        final var epr = messageBuilder.buildEndpointReference("http://some.place");
        final var body = messageBuilder.buildHello(epr, 1);

        final var types = new ArrayList<QName>();
        if (dpwsType) {
            types.add(DpwsConstants.DEVICE_TYPE);
        }
        if (mdpwsType) {
            types.add(CommonConstants.MEDICAL_DEVICE_TYPE);
        }
        body.getValue().getTypes().addAll(types);

        final var soapMessage = messageBuilder.createSoapMessageWithBody(WSA_ACTION_HELLO, body);

        messageBuilder.setMessageTo(soapMessage, "urn:docs-oasis-open-org:ws-dd:ns:discovery:2009:01");
        return soapMessage;
    }

    Envelope buildProbeMatches(final boolean dpwsType, final boolean mdpwsType) {
        return buildProbeMatches(dpwsType, mdpwsType, 1);
    }

    Envelope buildProbeMatches(final boolean dpwsType, final boolean mdpwsType, final int numEntries) {
        final var epr = messageBuilder.buildEndpointReference("http://some.place");
        final var matches = new ArrayList<ProbeMatchType>();
        for (int i = 0; i < numEntries; i++) {
            final var match = messageBuilder.buildProbeMatch(epr, 1);
            final var types = new ArrayList<QName>();
            // only omit data in the first entry
            if (dpwsType || i != 1) {
                types.add(DpwsConstants.DEVICE_TYPE);
            }
            if (mdpwsType || i != 1) {
                types.add(CommonConstants.MEDICAL_DEVICE_TYPE);
            }
            match.getTypes().addAll(types);
            matches.add(match);
        }
        final var body = messageBuilder.buildProbeMatches(matches);

        final var soapMessage = messageBuilder.createSoapMessageWithBody(WSA_ACTION_PROBE_MATCHES, body);

        messageBuilder.setMessageTo(soapMessage, "http://other.place");
        messageBuilder.setMessageRelatesTo(soapMessage, "ftp://frofro");

        return soapMessage;
    }

    Envelope buildResolveMatches(final boolean dpwsType, final boolean mdpwsType) {
        final var epr = messageBuilder.buildEndpointReference("http://some.place");
        final var match = messageBuilder.buildResolveMatch(epr, 1);
        final var types = new ArrayList<QName>();
        if (dpwsType) {
            types.add(DpwsConstants.DEVICE_TYPE);
        }
        if (mdpwsType) {
            types.add(CommonConstants.MEDICAL_DEVICE_TYPE);
        }
        match.getTypes().addAll(types);

        final var body = messageBuilder.buildResolveMatches(match);

        final var soapMessage = messageBuilder.createSoapMessageWithBody(WSA_ACTION_RESOLVE_MATCHES, body);

        messageBuilder.setMessageTo(soapMessage, "http://other.place");
        messageBuilder.setMessageRelatesTo(soapMessage, "ftp://frofro");

        return soapMessage;
    }
}
