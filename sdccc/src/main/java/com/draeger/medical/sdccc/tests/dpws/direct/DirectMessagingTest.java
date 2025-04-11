/*
 * This Source Code Form is subject to the terms of the "SDCcc non-commercial use license".
 *
 * Copyright (C) 2025 Draegerwerk AG & Co. KGaA
 */

package com.draeger.medical.sdccc.tests.dpws.direct;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.draeger.medical.sdccc.configuration.EnabledTestConfig;
import com.draeger.medical.sdccc.sdcri.testclient.TestClient;
import com.draeger.medical.sdccc.tests.InjectorTestBase;
import com.draeger.medical.sdccc.tests.annotations.TestDescription;
import com.draeger.medical.sdccc.tests.annotations.TestIdentifier;
import com.draeger.medical.sdccc.util.HttpClientUtil;
import com.draeger.medical.sdccc.util.MessageGeneratingUtil;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.UUID;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.somda.sdc.dpws.http.apache.ApacheTransportBindingFactoryImpl;
import org.somda.sdc.dpws.soap.MarshallingService;
import org.somda.sdc.dpws.soap.SoapMarshalling;
import org.somda.sdc.dpws.soap.SoapMessage;
import org.somda.sdc.dpws.soap.SoapUtil;
import org.somda.sdc.dpws.soap.exception.MarshallingException;
import org.somda.sdc.dpws.soap.exception.SoapFaultException;
import org.somda.sdc.dpws.soap.exception.TransportException;
import org.somda.sdc.dpws.soap.wsaddressing.WsAddressingUtil;
import org.somda.sdc.dpws.soap.wsaddressing.model.AttributedURIType;
import org.somda.sdc.dpws.soap.wsaddressing.model.EndpointReferenceType;
import org.somda.sdc.dpws.soap.wsaddressing.model.ObjectFactory;
import org.somda.sdc.dpws.soap.wstransfer.WsTransferConstants;
import org.somda.sdc.glue.common.ActionConstants;

/**
 * DPWS tests for
 * Devices Profile for Web Services Version 1.1
 * OASIS Standard 1 July 2009.
 */
public class DirectMessagingTest extends InjectorTestBase {
    protected static final String NO_FAULT_TEMPLATE = "A soap fault was expected in response to message with ";

    private static final Logger LOG = LogManager.getLogger();

    private static final String RANDOM_WSA_ACTION = "http://schemas.xmlsoap.org/ws/2004/09/transfer/Random";
    private static final String SOME_REPLY_ENDPOINT = "http://localhost";
    private static final String ANONYMOUS_REPLY_ENDPOINT = "http://www.w3.org/2005/08/addressing/anonymous";
    private static final String INVALID_ADDRESSING_HEADER = "InvalidAddressingHeader";
    private static final int[] HTTP_OK = {200, 299};
    private static final int[] HTTP_BAD = {400, 599};

    private TestClient testClient;
    private SoapUtil soapUtil;
    private SoapMarshalling soapMarshalling;
    private HttpClient httpClient;
    private HttpClientUtil httpClientUtil;
    private MarshallingService marshalling;
    private MessageGeneratingUtil messageGeneratingUtil;
    private ObjectFactory wsaFactory;
    private org.somda.sdc.biceps.model.message.ObjectFactory messageModelFactory;
    private WsAddressingUtil wsaUtil;

    @BeforeEach
    void setUp() {
        testClient = getInjector().getInstance(TestClient.class);
        messageGeneratingUtil = getInjector().getInstance(MessageGeneratingUtil.class);
        httpClient = testClient
                .getInjector()
                .getInstance(ApacheTransportBindingFactoryImpl.class)
                .getClient();
        soapUtil = testClient.getInjector().getInstance(SoapUtil.class);
        soapMarshalling = testClient.getInjector().getInstance(SoapMarshalling.class);
        httpClientUtil = testClient.getInjector().getInstance(HttpClientUtil.class);
        marshalling = testClient.getInjector().getInstance(MarshallingService.class);
        wsaFactory =
                testClient.getInjector().getInstance(org.somda.sdc.dpws.soap.wsaddressing.model.ObjectFactory.class);
        messageModelFactory =
                testClient.getInjector().getInstance(org.somda.sdc.biceps.model.message.ObjectFactory.class);
        wsaUtil = testClient.getInjector().getInstance(WsAddressingUtil.class);
    }

    /*
    In this requirement it is sufficient to just check for the existence of a HTTP status code.

    SOAP 1.2 Part 2
    6.2.2
    The SOAP Request-Response MEP defines a pattern for the exchange of a SOAP message acting as a request followed
    by a message acting as a response. The response message MAY contain a SOAP envelope, or else the response MUST
    be a binding-specific message indicating that the request has been received.

    Source: https://www.w3.org/TR/soap12-part2
    Copyright © 2007 World Wide Web Consortium, (MIT, ERCIM, Keio, Beihang).
    http://www.w3.org/Consortium/Legal/2015/doc-license
    Status of W3C document: W3C Recommendation

    -> since HTTP response messages always contain a status line with a status code, there is always a
       binding-specific message indicating that the request was received
     */
    @Test
    @TestIdentifier(EnabledTestConfig.DPWS_R0013)
    @TestDescription("Sends valid and invalid requests and check if the response contains a binding-specific message"
            + " indicating that the request has been received.")
    void testRequirement0013() throws Exception {
        testRequirement0013Good();
        testRequirement0013Bad();
    }

    void testRequirement0013Good() throws Exception {
        sendToAllEpr(WsTransferConstants.WSA_ACTION_GET, HTTP_OK[0], HTTP_OK[1]);
    }

    void testRequirement0013Bad() throws Exception {
        sendToAllEpr(RANDOM_WSA_ACTION, HTTP_BAD[0], HTTP_BAD[1]);
    }

    private void sendToAllEpr(final String wsaAction, final int low, final int high) throws Exception {
        final var xAddrs = testClient.getTargetXAddrs();
        assertTestData(xAddrs, "no xAddresses are available for the DUT");

        for (final String xAddr : xAddrs) {

            final var getMessage = soapUtil.createMessage(wsaAction);
            final AttributedURIType msgId =
                    wsaUtil.createAttributedURIType(soapUtil.createUriFromUuid(UUID.randomUUID()));
            getMessage.getWsAddressingHeader().setMessageId(msgId);
            getMessage.getWsAddressingHeader().setTo(wsaUtil.createAttributedURIType(xAddr));
            final var output = new ByteArrayOutputStream();

            try {
                marshalling.marshal(getMessage, output);
            } catch (final MarshallingException e) {
                LOG.error("Error occurred while sending probe to {}. Message: {}", xAddr, e.getMessage());
                LOG.debug("Error occurred while sending probe to {}", xAddr, e);
                fail("Error occurred while sending probe to " + xAddr);
                // unreachable, silence warnings
                throw e;
            }

            HttpResponse response = null;
            try {
                response = httpClientUtil.postMessageWithHttpResponse(httpClient, xAddr, output.toByteArray());
                assertNotNull(response);
            } catch (final TransportException e) {
                LOG.error("Did not receive a response to probe to {}. Message: {}", xAddr, e.getMessage());
                LOG.debug("Did not receive a response to probe to {}.", xAddr, e);
                fail("Did not receive a response to probe to " + xAddr);
                // unreachable, silence warnings
                throw e;
            } finally {
                if (response != null) {
                    EntityUtils.consume(response.getEntity());
                }
            }
            assertTrue(
                    low <= response.getStatusLine().getStatusCode()
                            && response.getStatusLine().getStatusCode() <= high,
                    "The response contains no HTTP status" + " code, which indicates that the request was received.");
        }
    }

    @Test
    @TestIdentifier(EnabledTestConfig.DPWS_R0031)
    @TestDescription("Provokes an wsa:InvalidAddressingHeader SOAP Fault and verifies, that with an anonymous"
            + " reply endpoint the wsa:InvalidAddressingHeader SOAP Fault is not thrown.")
    void testRequirement0031() throws jakarta.xml.bind.JAXBException {
        final var msgUUID = soapUtil.createRandomUuidUri();
        try {
            sendMessageWithReplyToHeader(msgUUID, SOME_REPLY_ENDPOINT);
        } catch (SoapFaultException e) {
            fail("Unexpected SoapFaultException was thrown", e.getCause());
        }
        try {
            sendMessageWithReplyToHeader(msgUUID, SOME_REPLY_ENDPOINT);
        } catch (SoapFaultException e) {
            assertEquals(
                    INVALID_ADDRESSING_HEADER,
                    e.getFault().getCode().getSubcode().getValue().getLocalPart(),
                    "Duplicate MessageId should cause a wsa:InvalidAddressingHeader SOAP Fault.");
        }
        try {
            sendMessageWithReplyToHeader(msgUUID, ANONYMOUS_REPLY_ENDPOINT);
        } catch (SoapFaultException e) {
            fail("No fault expected.");
        }
    }

    private void sendMessageWithReplyToHeader(final String msgId, final String replyUri)
            throws jakarta.xml.bind.JAXBException, SoapFaultException {
        final var message =
                soapUtil.createMessage(ActionConstants.ACTION_GET_MDIB, messageModelFactory.createGetMdib());

        final var messageId = new AttributedURIType();
        messageId.setValue(msgId);
        final var replyEndpoint = new AttributedURIType();
        replyEndpoint.setValue(replyUri);
        final var replyTo = new EndpointReferenceType();
        replyTo.setAddress(replyEndpoint);

        final List<Object> tmpHeaderList =
                message.getEnvelopeWithMappedHeaders().getHeader().getAny();

        tmpHeaderList.add(wsaFactory.createMessageID(messageId));
        tmpHeaderList.add(wsaFactory.createReplyTo(replyTo));
        message.getOriginalEnvelope().getHeader().setAny(tmpHeaderList);

        final var out = new ByteArrayOutputStream();
        soapMarshalling.marshal(message.getOriginalEnvelope(), out);
        testAnonymousReplyEndpointAddress(out.toByteArray());
    }

    void testAnonymousReplyEndpointAddress(final byte[] message) throws SoapFaultException {
        final var getServiceOpt = MessageGeneratingUtil.getGetService(testClient);
        if (getServiceOpt.isEmpty()) {
            fail("No get service present");
        }

        final SoapMessage response;
        final var getAddress = getServiceOpt.orElseThrow().getActiveEprAddress();

        try {
            response = httpClientUtil.postMessage(httpClient, getAddress, message);
            assertNotNull(response);
        } catch (TransportException e) {
            fail("TransportException was thrown", e.getCause());
        }
    }

    @Test
    @TestIdentifier(EnabledTestConfig.DPWS_R0034)
    @TestDescription("Verifies that a transmitted SOAP message and the response uses SOAP 1.2 Envelopes,"
            + " containing the correct namespace, an Envelope element and a Body element.")
    void testRequirement0034() {
        final var getServiceOpt = MessageGeneratingUtil.getGetService(testClient);
        if (getServiceOpt.isEmpty()) {
            fail("No get service present");
        }
        assertDoesNotThrow(() -> messageGeneratingUtil.getMdib());
    }
}
