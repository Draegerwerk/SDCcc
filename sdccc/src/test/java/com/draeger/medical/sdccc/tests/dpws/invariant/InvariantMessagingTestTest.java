/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2023 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.tests.dpws.invariant;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.draeger.medical.dpws.soap.model.Envelope;
import com.draeger.medical.dpws.soap.wsaddressing.model.ObjectFactory;
import com.draeger.medical.sdccc.configuration.TestSuiteConfig;
import com.draeger.medical.sdccc.marshalling.MarshallingUtil;
import com.draeger.medical.sdccc.messages.MessageStorage;
import com.draeger.medical.sdccc.sdcri.testclient.TestClient;
import com.draeger.medical.sdccc.sdcri.testclient.TestClientUtil;
import com.draeger.medical.sdccc.tests.InjectorTestBase;
import com.draeger.medical.sdccc.tests.test_util.InjectorUtil;
import com.draeger.medical.sdccc.tests.util.NoTestData;
import com.draeger.medical.sdccc.util.MdibBuilder;
import com.draeger.medical.sdccc.util.MessageBuilder;
import com.draeger.medical.sdccc.util.MessageStorageUtil;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Names;
import java.io.IOException;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.somda.sdc.glue.common.ActionConstants;

/**
 * Unit test for the DPWS {@linkplain InvariantMessagingTest}.
 */
public class InvariantMessagingTestTest {
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);
    private static final String INVALID_ADDRESSING_HEADER_REASON =
            "A header representing a Message Addressing Property is not valid and the message cannot be processed";
    private static final String UNSPECIFIED_MESSAGE = "http://www.w3.org/2005/08/addressing/unspecified";
    private static MessageStorageUtil messageStorageUtil;
    private static MessageBuilder messageBuilder;
    private static MdibBuilder mdibBuilder;

    private InvariantMessagingTest testClass;
    private MessageStorage storage;
    private TestClient testClient;
    private ObjectFactory wsaFactory;

    @BeforeAll
    static void setupMarshalling() {
        final Injector marshallingInjector = MarshallingUtil.createMarshallingTestInjector(true);
        messageStorageUtil = marshallingInjector.getInstance(MessageStorageUtil.class);
        mdibBuilder = marshallingInjector.getInstance(MdibBuilder.class);
        messageBuilder = marshallingInjector.getInstance(MessageBuilder.class);
    }

    @BeforeEach
    void setUp() throws IOException {
        final TestClient mockClient = mock(TestClient.class);
        when(mockClient.isClientRunning()).thenReturn(true);
        testClient = mockClient;

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
        final var clientInjector = TestClientUtil.createClientInjector();
        when(testClient.getInjector()).thenReturn(clientInjector);

        wsaFactory = clientInjector.getInstance(com.draeger.medical.dpws.soap.wsaddressing.model.ObjectFactory.class);

        storage = injector.getInstance(MessageStorage.class);
        testClass = new InvariantMessagingTest();
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
        assertThrows(NoTestData.class, testClass::testRequirement0019, "R0019 passes without test data");
        assertThrows(NoTestData.class, testClass::testRequirement0040, "R0040 passes without test data");
    }

    /**
     * Tests whether a message which contains wsa:RelatesTo with the RelationshipType "wsa:Reply"
     * causes the test to pass.
     */
    @Test
    void testR0019Good() throws Exception {
        messageStorageUtil.addInboundSecureHttpMessage(storage, buildGoodResponseWithRelatesTo());
        testClass.testRequirement0019();
    }

    /**
     * Tests whether a message which not contains wsa:RelatesTo causes the test to fail.
     */
    @Test
    void testR0019Bad() throws Exception {
        messageStorageUtil.addInboundSecureHttpMessage(storage, buildGoodResponseWithRelatesTo());
        messageStorageUtil.addInboundSecureHttpMessage(storage, buildGoodResponse());
        assertThrows(AssertionError.class, () -> testClass.testRequirement0019());
    }

    /**
     * Tests whether a message which contains wsa:RelatesTo with a RelationshipType
     * unequal to "wsa:Reply" causes the test to fail.
     */
    @Test
    void testR0019BadDefaultRelationshipType() throws Exception {
        messageStorageUtil.addInboundSecureHttpMessage(storage, buildFaultResponseWithRelatesTo());
        messageStorageUtil.addInboundSecureHttpMessage(storage, buildGoodResponseWithRelatesTo());
        assertThrows(AssertionError.class, () -> testClass.testRequirement0019());
    }

    /**
     * Tests whether calling the tests with faults which contains wsa:RelatesTo causes the test to pass.
     */
    @Test
    void testR0040Good() throws Exception {
        messageStorageUtil.addInboundSecureHttpMessage(storage, buildGoodResponse());
        messageStorageUtil.addInboundSecureHttpMessage(storage, buildFaultResponseWithRelatesTo());
        testClass.testRequirement0040();
    }

    /**
     * Tests whether calling the tests with faults which are missing wsa:RelatesTo causes the test to fail.
     */
    @Test
    void testR0040Bad() throws Exception {
        messageStorageUtil.addInboundSecureHttpMessage(storage, buildGoodResponse());
        messageStorageUtil.addInboundSecureHttpMessage(storage, buildFaultResponseWithoutRelatesTo());
        assertThrows(AssertionError.class, testClass::testRequirement0040);
    }

    /**
     * Tests whether calling the tests with multiple faults and one missing wsa:RelatesTo causes the test to fail.
     */
    @Test
    void testR0040BadMultipleFaults() throws Exception {
        messageStorageUtil.addInboundSecureHttpMessage(storage, buildGoodResponse());
        messageStorageUtil.addInboundSecureHttpMessage(storage, buildFaultResponseWithRelatesTo());
        messageStorageUtil.addInboundSecureHttpMessage(storage, buildGoodResponse());
        messageStorageUtil.addInboundSecureHttpMessage(storage, buildFaultResponseWithoutRelatesTo());
        assertThrows(AssertionError.class, testClass::testRequirement0040);
    }

    Envelope buildGoodResponse() {
        final var mdib = mdibBuilder.buildMinimalMdib();
        final var body = messageBuilder.buildGetMdibResponse("someSequence");
        body.setMdib(mdib);
        return messageBuilder.createSoapMessageWithBody(
                ActionConstants.getResponseAction(ActionConstants.ACTION_GET_MDIB), body);
    }

    Envelope buildGoodResponseWithRelatesTo() {
        final var message = messageBuilder.createBasicSoapMessage(
                ActionConstants.getResponseAction(ActionConstants.ACTION_GET_MDIB));
        final var relatesTo = messageBuilder.createRelatesToType(UNSPECIFIED_MESSAGE);
        relatesTo.setRelationshipType("wsa:Reply");

        message.getHeader().getAny().add(wsaFactory.createRelatesTo(relatesTo));

        return message;
    }

    Envelope buildFaultResponseWithRelatesTo() {
        final var faultMsg = buildFaultResponseWithoutRelatesTo();
        faultMsg.getHeader()
                .getAny()
                .add(wsaFactory.createRelatesTo(messageBuilder.createRelatesToType(UNSPECIFIED_MESSAGE)));
        return faultMsg;
    }

    Envelope buildFaultResponseWithoutRelatesTo() {
        return messageBuilder.createFaultResponse(INVALID_ADDRESSING_HEADER_REASON, "");
    }
}
