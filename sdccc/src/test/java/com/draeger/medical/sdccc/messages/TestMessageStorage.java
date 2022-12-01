/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.messages;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.draeger.medical.biceps.model.participant.ComponentActivation;
import com.draeger.medical.sdccc.messages.guice.MessageFactory;
import com.draeger.medical.sdccc.messages.mapping.ManipulationData;
import com.draeger.medical.sdccc.messages.mapping.ManipulationParameter;
import com.draeger.medical.sdccc.messages.mapping.MessageContent;
import com.draeger.medical.sdccc.util.CertificateUtil;
import com.draeger.medical.sdccc.util.Constants;
import com.draeger.medical.sdccc.util.TestRunObserver;
import com.draeger.medical.t2iapi.ResponseTypes;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import javax.xml.namespace.QName;
import org.apache.commons.io.ByteOrderMark;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.mockito.internal.verification.VerificationModeFactory;
import org.somda.sdc.biceps.common.CommonConstants;
import org.somda.sdc.dpws.CommunicationLog;
import org.somda.sdc.dpws.soap.ApplicationInfo;
import org.somda.sdc.dpws.soap.CommunicationContext;
import org.somda.sdc.dpws.soap.HttpApplicationInfo;
import org.somda.sdc.dpws.soap.TransportInfo;

/**
 * Tests for the message storage.
 */
public class TestMessageStorage {

    private static final String BASE_MESSAGE_STRING =
            "<s12:Envelope xmlns:dom=\"http://standards.ieee.org/downloads/11073/11073-10207-2017/participant\" "
                    + "xmlns:dpws=\"http://docs.oasis-open.org/ws-dd/ns/dpws/2009/01\" "
                    + "xmlns:ext=\"http://standards.ieee.org/downloads/11073/11073-10207-2017/extension\" "
                    + "xmlns:mdpws=\"http://standards.ieee.org/downloads/11073/11073-20702-2016\" "
                    + "xmlns:msg=\"http://standards.ieee.org/downloads/11073/11073-10207-2017/message\" "
                    + "xmlns:s12=\"http://www.w3.org/2003/05/soap-envelope\" "
                    + "xmlns:sdc=\"http://standards.ieee.org/downloads/11073/11073-20701-2018\" "
                    + "xmlns:wsa=\"http://www.w3.org/2005/08/addressing\" "
                    + "xmlns:wsd=\"http://docs.oasis-open.org/ws-dd/ns/discovery/2009/01\" "
                    + "xmlns:wse=\"http://schemas.xmlsoap.org/ws/2004/08/eventing\" "
                    + "xmlns:wsx=\"http://schemas.xmlsoap.org/ws/2004/09/mex\" "
                    + "xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" "
                    + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"><s12:Header>"
                    + "<wsa:To s12:mustUnderstand=\"true\">"
                    + "https://127.0.0.1:52027/29bf1db0b76e11e982e374e5f9efcfcb"
                    + "</wsa:To><wsa:Action s12:mustUnderstand=\"true\">"
                    + "%s"
                    + "</wsa:Action>"
                    + "<wsa:MessageID>urn:uuid:407229f6-a17d-45ae-9e57-d951d55767c3</wsa:MessageID>"
                    + "</s12:Header><s12:Body>%s</s12:Body></s12:Envelope>";

    private static final String SEQUENCE_ID_METRIC_BODY_STRING =
            "<msg:EpisodicMetricReport MdibVersion=\"%s\" SequenceId=\"urn:uuid:%s\">"
                    + "<msg:ReportPart>"
                    + "<msg:MetricState xsi:type=\"pm:NumericMetricState\" StateVersion=\"1\" "
                    + "DescriptorHandle=\"H0\" DescriptorVersion=\"0\">"
                    + "<pm:MetricValue Value=\"10.0\" DeterminationTime=\"1608791424007\">"
                    + "<pm:MetricQuality Validity=\"Vld\">"
                    + "</pm:MetricQuality>"
                    + "</pm:MetricValue>"
                    + "</msg:MetricState>"
                    + "</msg:ReportPart>"
                    + "</msg:EpisodicMetricReport>";
    private static final String SEQUENCE_ID_ALERT_BODY_STRING =
        "<msg:EpisodicAlertReport MdibVersion=\"%s\" SequenceId=\"urn:uuid:%s\">"
            + "<msg:ReportPart>"
            + "</msg:ReportPart>"
            + "</msg:EpisodicAlertReport>";
    private CommunicationContext messageContext;
    private CommunicationContext insecureMessageContext;
    private CommunicationContext udpMessageContext;

    private TestRunObserver testRunObserver;

    @BeforeEach
    void setUp() throws CertificateException, IOException {

        final X509Certificate certificate = CertificateUtil.getDummyCert();
        messageContext = new CommunicationContext(
                new ApplicationInfo(),
                new TransportInfo(
                        Constants.HTTPS_SCHEME, null, null, null, null, Collections.singletonList(certificate)));

        insecureMessageContext = new CommunicationContext(
                new ApplicationInfo(),
                new TransportInfo(
                        Constants.HTTP_SCHEME.toUpperCase(), null, null, null, null, Collections.emptyList()));

        udpMessageContext = new CommunicationContext(
                new ApplicationInfo(), new TransportInfo("udp", null, null, null, null, Collections.emptyList()));

        this.testRunObserver = mock(TestRunObserver.class, RETURNS_DEEP_STUBS);
    }

    /**
     * Tests whether an MdibVersion can cause an overflow of the long field storing MdibVersion.
     *
     * @param dir message storage directory
     * @throws IOException          on io exceptions
     * @throws CertificateException on certificate exceptions
     */
    @Test
    public void testMdibVersionOverflow(@TempDir final File dir) throws IOException, CertificateException {
        try (final MessageStorage messageStorage =
                new MessageStorage(1, mock(MessageFactory.class), new HibernateConfigImpl(dir), this.testRunObserver)) {
            final ListMultimap<String, String> multimap = ArrayListMultimap.create();

            final String transactionId = "transactionId";
            final String requestUri = "requestUri";

            final X509Certificate certificate = CertificateUtil.getDummyCert();
            final CommunicationContext headerContext = new CommunicationContext(
                    new HttpApplicationInfo(multimap, transactionId, requestUri),
                    new TransportInfo(
                            Constants.HTTPS_SCHEME, null, null, null, null, Collections.singletonList(certificate)));

            try (final Message message = new Message(
                    CommunicationLog.Direction.INBOUND,
                    CommunicationLog.MessageType.REQUEST,
                    headerContext,
                    messageStorage)) {
                message.write(String.format(
                                BASE_MESSAGE_STRING,
                                "action",
                                String.format(SEQUENCE_ID_METRIC_BODY_STRING, "9223372036854775808", "1"))
                        .getBytes(StandardCharsets.UTF_8));
            }
            messageStorage.flush();

            verify(this.testRunObserver, atLeastOnce()).invalidateTestRun(any(NumberFormatException.class));
        }
    }

    /**
     * Tests whether an MdibVersion close to causing an overflow of the long field storing MdibVersion
     * is saved as a positive value.
     *
     * @param dir message storage directory
     * @throws IOException          on io exceptions
     * @throws CertificateException on certificate exceptions
     */
    @Test
    public void testMdibVersionCloseToOverflow(@TempDir final File dir) throws IOException, CertificateException {
        try (final MessageStorage messageStorage =
                 new MessageStorage(1, mock(MessageFactory.class), new HibernateConfigImpl(dir), this.testRunObserver)) {
            final ListMultimap<String, String> multimap = ArrayListMultimap.create();

            final String transactionId = "transactionId";
            final String requestUri = "requestUri";

            final X509Certificate certificate = CertificateUtil.getDummyCert();
            final CommunicationContext headerContext = new CommunicationContext(
                new HttpApplicationInfo(multimap, transactionId, requestUri),
                new TransportInfo(
                    Constants.HTTPS_SCHEME, null, null, null, null, Collections.singletonList(certificate)));

            try (final Message message = new Message(
                CommunicationLog.Direction.INBOUND,
                CommunicationLog.MessageType.REQUEST,
                headerContext,
                messageStorage)) {
                message.write(String.format(
                        BASE_MESSAGE_STRING,
                        "action",
                        String.format(SEQUENCE_ID_METRIC_BODY_STRING, "9223372036854775807", "1"))
                    .getBytes(StandardCharsets.UTF_8));
            }
            messageStorage.flush();

            final MessageContent messageContent = messageStorage.getInboundMessages().getStream().toList().get(0);
            final long mdibVersion = messageContent.getMdibVersionGroups().get(0).getMdibVersion();
            assertTrue(mdibVersion > 0);
        }
    }

    /**
     * Tests whether SequenceId values are stored and only distinct values returned on request.
     *
     * @param dir message storage directory
     * @throws IOException          on io exceptions
     * @throws CertificateException on certificate exceptions
     */
    @Test
    public void testGetUniqueSequenceIds(@TempDir final File dir) throws IOException, CertificateException {
        try (final MessageStorage messageStorage =
                new MessageStorage(1, mock(MessageFactory.class), new HibernateConfigImpl(dir), this.testRunObserver)) {
            final ListMultimap<String, String> multimap = ArrayListMultimap.create();

            final String transactionId = "transactionId";
            final String requestUri = "requestUri";

            final X509Certificate certificate = CertificateUtil.getDummyCert();
            final CommunicationContext headerContext = new CommunicationContext(
                    new HttpApplicationInfo(multimap, transactionId, requestUri),
                    new TransportInfo(
                            Constants.HTTPS_SCHEME, null, null, null, null, Collections.singletonList(certificate)));

            try (final Message message = new Message(
                    CommunicationLog.Direction.INBOUND,
                    CommunicationLog.MessageType.REQUEST,
                    headerContext,
                    messageStorage)) {
                message.write(
                        String.format(BASE_MESSAGE_STRING, "action", String.format(SEQUENCE_ID_METRIC_BODY_STRING, "3", "1"))
                                .getBytes(StandardCharsets.UTF_8));
            }
            messageStorage.flush();

            try (final Message message = new Message(
                    CommunicationLog.Direction.INBOUND,
                    CommunicationLog.MessageType.REQUEST,
                    headerContext,
                    messageStorage)) {
                message.write(
                        String.format(BASE_MESSAGE_STRING, "action", String.format(SEQUENCE_ID_METRIC_BODY_STRING, "3", "2"))
                                .getBytes(StandardCharsets.UTF_8));
            }
            messageStorage.flush();

            try (final Message message = new Message(
                    CommunicationLog.Direction.INBOUND,
                    CommunicationLog.MessageType.REQUEST,
                    headerContext,
                    messageStorage)) {
                message.write(
                        String.format(BASE_MESSAGE_STRING, "action", String.format(SEQUENCE_ID_METRIC_BODY_STRING, "3", "1"))
                                .getBytes(StandardCharsets.UTF_8));
            }
            messageStorage.flush();

            try (final Stream<String> sequenceIdStream = messageStorage.getUniqueSequenceIds()) {
                assertEquals(
                        List.of("urn:uuid:1", "urn:uuid:2"),
                        sequenceIdStream.sorted().toList());
            }

            try (final MessageStorage.GetterResult<MessageContent> inboundMessages =
                    messageStorage.getInboundMessages()) {
                assertEquals(3, inboundMessages.getStream().count());
            }
        }
    }

    /**
     * Tests whether headers and the transaction id are stored properly.
     *
     * <p>
     * Adds a message with test headers and test transaction id into storage and verifies whether the content retrieved
     * from storage is equal.
     *
     * @param dir message storage directory
     * @throws IOException          on io exceptions
     * @throws CertificateException on certificate exceptions
     */
    @Test
    public void testHeadersAndTransactionId(@TempDir final File dir) throws IOException, CertificateException {
        try (final MessageStorage messageStorage =
                new MessageStorage(1, mock(MessageFactory.class), new HibernateConfigImpl(dir), this.testRunObserver)) {
            final ListMultimap<String, String> multimap = ArrayListMultimap.create();

            final List<String> expectedList1 = Arrays.asList("headerContent1", "headerContent2", "headerContent3");
            final List<String> expectedList2 = Arrays.asList("headerContent4", "headerContent5");
            final String expectedTransactionId = "testIfThisTransactionIdIsStoredProperly";
            final String expectedRequestUri = "testIfThisRequestUriIsStoredProperly";

            final String listName1 = "headername1";
            final String listName2 = "headername2";

            multimap.putAll(listName1, expectedList1);
            multimap.putAll(listName2, expectedList2);

            final X509Certificate certificate = CertificateUtil.getDummyCert();
            final CommunicationContext headerContext = new CommunicationContext(
                    new HttpApplicationInfo(multimap, expectedTransactionId, expectedRequestUri),
                    new TransportInfo(
                            Constants.HTTPS_SCHEME, null, null, null, null, Collections.singletonList(certificate)));

            try (final Message message = new Message(
                    CommunicationLog.Direction.INBOUND,
                    CommunicationLog.MessageType.REQUEST,
                    headerContext,
                    messageStorage)) {
                message.write("outbound_body1".getBytes(StandardCharsets.UTF_8));
            }

            messageStorage.flush();

            try (final MessageStorage.GetterResult<MessageContent> inboundMessages =
                    messageStorage.getInboundMessages()) {
                assertTrue(inboundMessages.areObjectsPresent());
                inboundMessages.getStream().forEach(m -> {
                    final Map<String, List<String>> headers = m.getHeaders();

                    assertEquals(expectedList1, headers.get(listName1));
                    assertEquals(expectedList2, headers.get(listName2));
                    assertEquals(expectedTransactionId, m.getTransactionId(), "TransactionId is wrong");
                    assertEquals(expectedRequestUri, m.getRequestUri(), "RequestUri is wrong");
                });
            }

            try (final MessageStorage.GetterResult<MessageContent> inboundMessages =
                    messageStorage.getInboundMessages()) {
                assertTrue(inboundMessages.getStream().findAny().isPresent());
            }
        }
    }

    /**
     * Tests if the transaction id for a message with message type UNKNOWN can be null.
     *
     * <p>
     * Adds a message with UNKNOWN message type and without transaction id into storage and verifies whether the content
     * retrieved from storage is equal.
     *
     * @param dir message storage directory
     * @throws IOException on io exceptions
     */
    @Test
    public void testUdpMessageWithTransactionIdNull(@TempDir final File dir) throws IOException {
        try (final MessageStorage messageStorage =
                new MessageStorage(1, mock(MessageFactory.class), new HibernateConfigImpl(dir), this.testRunObserver)) {
            try (final Message message = new Message(
                    CommunicationLog.Direction.INBOUND,
                    CommunicationLog.MessageType.UNKNOWN,
                    this.udpMessageContext,
                    messageStorage)) {

                message.write("outbound_body1".getBytes(StandardCharsets.UTF_8));
            }

            messageStorage.flush();

            try (final MessageStorage.GetterResult<MessageContent> inboundMessages =
                    messageStorage.getInboundMessages()) {
                assertTrue(inboundMessages.areObjectsPresent());
                inboundMessages.getStream().forEach(m -> assertNull(m.getTransactionId(), "TransactionId is not null"));
            }

            try (final MessageStorage.GetterResult<MessageContent> inboundMessages =
                    messageStorage.getInboundMessages()) {
                assertTrue(inboundMessages.getStream().findAny().isPresent());
            }
        }
    }

    /**
     * Tests whether SOAP body element QNames are extracted properly.
     *
     * <p>
     * Adds a message with test bodies into storage and verifies whether the content retrieved from storage is equal.
     *
     * @param dir message storage directory
     * @throws IOException          on io exceptions
     * @throws CertificateException on certificate exceptions
     */
    @Test
    public void testBodyExtraction(@TempDir final File dir) throws IOException, CertificateException {
        try (final MessageStorage messageStorage =
                new MessageStorage(1, mock(MessageFactory.class), new HibernateConfigImpl(dir), this.testRunObserver)) {
            final ListMultimap<String, String> multimap = ArrayListMultimap.create();

            // test tag with content
            final var expectedQName1 = new QName(CommonConstants.NAMESPACE_MESSAGE, "some_body", "msg");
            final String expectedBody1 = "<msg:some_body><pm:once_told_me>"
                    + "the_world_was_macaroni"
                    + "</pm:once_told_me></msg:some_body>";
            final String messageContent1 = String.format(BASE_MESSAGE_STRING, "1", expectedBody1);

            // test empty tag
            final var expectedQName2 =
                    new QName(CommonConstants.NAMESPACE_MESSAGE, "so_i_took_a_bite_out_of_a_tree", "msg");
            final String expectedBody2 = "<msg:so_i_took_a_bite_out_of_a_tree/>";
            final String messageContent2 = String.format(BASE_MESSAGE_STRING, "2", expectedBody2);

            final String expectedTransactionId = "testIfThisTransactionIdIsStoredProperly";

            final X509Certificate certificate = CertificateUtil.getDummyCert();
            final CommunicationContext headerContext = new CommunicationContext(
                    new HttpApplicationInfo(multimap, expectedTransactionId, null),
                    new TransportInfo(
                            Constants.HTTPS_SCHEME, null, null, null, null, Collections.singletonList(certificate)));

            try (final Message message = new Message(
                    CommunicationLog.Direction.INBOUND,
                    CommunicationLog.MessageType.REQUEST,
                    headerContext,
                    messageStorage)) {

                message.write(messageContent1.getBytes(StandardCharsets.UTF_8));
            }

            try (final Message message = new Message(
                    CommunicationLog.Direction.INBOUND,
                    CommunicationLog.MessageType.REQUEST,
                    headerContext,
                    messageStorage)) {

                message.write(messageContent2.getBytes(StandardCharsets.UTF_8));
            }

            messageStorage.flush();

            try (final MessageStorage.GetterResult<MessageContent> inboundMessages =
                    messageStorage.getInboundMessages()) {
                assertTrue(inboundMessages.areObjectsPresent());
                inboundMessages.getStream().forEach(m -> {
                    // since actions are assigned and known, we can determine the expected body based on actions
                    final var action = m.getActions().stream().findFirst().orElseThrow();
                    if ("1".equals(action)) {
                        assertTrue(m.getMdibVersionGroups().stream()
                                .anyMatch(mdibVersionGroup ->
                                        mdibVersionGroup.getBodyElement().equals(expectedQName1.toString())));
                        assertEquals(
                                1,
                                m.getMdibVersionGroups().stream()
                                        .filter(mdibVersionGroup -> !mdibVersionGroup
                                                .getBodyElement()
                                                .isEmpty())
                                        .count());
                    } else if ("2".equals(action)) {
                        assertTrue(m.getMdibVersionGroups().stream()
                                .anyMatch(mdibVersionGroup ->
                                        mdibVersionGroup.getBodyElement().equals(expectedQName2.toString())));
                        assertEquals(
                                1,
                                m.getMdibVersionGroups().stream()
                                        .filter(mdibVersionGroup -> !mdibVersionGroup
                                                .getBodyElement()
                                                .isEmpty())
                                        .count());
                    } else {
                        fail("Unknown action " + action);
                    }
                });
            }

            try (final MessageStorage.GetterResult<MessageContent> inboundMessages =
                    messageStorage.getInboundMessages()) {
                assertTrue(inboundMessages.getStream().findAny().isPresent());
            }
        }
    }

    /**
     * Tests whether only inbound messages are retrieved from storage.
     *
     * @param dir message storage directory
     * @throws IOException on io exceptions
     */
    @Test
    public void testGetInboundMessages(@TempDir final File dir) throws IOException {
        try (final MessageStorage messageStorage =
                new MessageStorage(3, mock(MessageFactory.class), new HibernateConfigImpl(dir), this.testRunObserver)) {
            final String expected = "inbound_body";

            try (final Message message = new Message(
                    CommunicationLog.Direction.OUTBOUND,
                    CommunicationLog.MessageType.REQUEST,
                    this.messageContext,
                    messageStorage)) {
                message.write("outbound_body1".getBytes(StandardCharsets.UTF_8));
            }

            try (final Message message = new Message(
                    CommunicationLog.Direction.OUTBOUND,
                    CommunicationLog.MessageType.REQUEST,
                    this.messageContext,
                    messageStorage)) {

                message.write("outbound_body2".getBytes(StandardCharsets.UTF_8));
            }

            try (final Message message = new Message(
                    CommunicationLog.Direction.INBOUND,
                    CommunicationLog.MessageType.REQUEST,
                    this.messageContext,
                    messageStorage)) {

                message.write(expected.getBytes(StandardCharsets.UTF_8));
            }

            messageStorage.flush();

            try (final MessageStorage.GetterResult<MessageContent> inboundMessages =
                    messageStorage.getInboundMessages()) {
                assertTrue(inboundMessages.areObjectsPresent());
                inboundMessages.getStream().forEach(m -> assertEquals(expected, m.getBody()));
            }

            try (final MessageStorage.GetterResult<MessageContent> inboundMessages =
                    messageStorage.getInboundMessages()) {
                assertTrue(inboundMessages.getStream().findAny().isPresent());
            }

            try (final MessageStorage.GetterResult<MessageContent> inboundMessages =
                    messageStorage.getInboundMessages()) {
                assertEquals(1, inboundMessages.getStream().count());
            }
        }
    }

    /**
     * Tests whether only outbound messages are retrieved from storage.
     *
     * @param dir message storage directory
     * @throws IOException on io exceptions
     */
    @Test
    public void testGetOutboundMessages(@TempDir final File dir) throws IOException {
        try (final MessageStorage messageStorage =
                new MessageStorage(3, mock(MessageFactory.class), new HibernateConfigImpl(dir), this.testRunObserver)) {
            final String expected = "outbound_body";

            try (final Message message = new Message(
                    CommunicationLog.Direction.OUTBOUND,
                    CommunicationLog.MessageType.REQUEST,
                    this.messageContext,
                    messageStorage)) {
                message.write(expected.getBytes(StandardCharsets.UTF_8));
            }

            try (final Message message = new Message(
                    CommunicationLog.Direction.INBOUND,
                    CommunicationLog.MessageType.RESPONSE,
                    this.messageContext,
                    messageStorage)) {

                message.write("inbound_body".getBytes(StandardCharsets.UTF_8));
            }

            try (final Message message = new Message(
                    CommunicationLog.Direction.INBOUND,
                    CommunicationLog.MessageType.REQUEST,
                    this.messageContext,
                    messageStorage)) {

                message.write("other_inbound_body".getBytes(StandardCharsets.UTF_8));
            }

            messageStorage.flush();

            try (final MessageStorage.GetterResult<MessageContent> outboundMessages =
                    messageStorage.getOutboundMessages()) {
                assertTrue(outboundMessages.areObjectsPresent());
                outboundMessages.getStream().forEach(m -> assertEquals(expected, m.getBody()));
            }

            try (final MessageStorage.GetterResult<MessageContent> outboundMessages =
                    messageStorage.getOutboundMessages()) {
                assertTrue(outboundMessages.getStream().findAny().isPresent());
            }

            try (final MessageStorage.GetterResult<MessageContent> outboundMessages =
                    messageStorage.getOutboundMessages()) {
                assertEquals(1, outboundMessages.getStream().count());
            }
        }
    }

    /**
     * Tests whether only inbound messages with a SOAP envelope or content-type application/soap+xml are retrieved.
     *
     * @param dir message storage directory
     * @throws IOException on io exceptions
     */
    @Test
    public void testGetInboundSoapMessages(@TempDir final File dir) throws IOException, CertificateException {
        final ListMultimap<String, String> multimap = ArrayListMultimap.create();

        multimap.putAll("Content-Type", Collections.singletonList("application/soap+xml; charset=UTF-8"));

        final CommunicationContext headerContext = new CommunicationContext(
                new HttpApplicationInfo(multimap, "someId", "someUri"),
                new TransportInfo(
                        Constants.HTTPS_SCHEME,
                        null,
                        null,
                        null,
                        null,
                        Collections.singletonList(CertificateUtil.getDummyCert())));

        try (final MessageStorage messageStorage =
                new MessageStorage(1, mock(MessageFactory.class), new HibernateConfigImpl(dir), this.testRunObserver)) {
            final var expected = String.format(BASE_MESSAGE_STRING, "action1", "expected_body");

            try (final Message message = new Message(
                    CommunicationLog.Direction.OUTBOUND,
                    CommunicationLog.MessageType.RESPONSE,
                    this.messageContext,
                    messageStorage)) {

                message.write(expected.getBytes(StandardCharsets.UTF_8));
            }

            try (final Message message = new Message(
                    CommunicationLog.Direction.OUTBOUND,
                    CommunicationLog.MessageType.RESPONSE,
                    headerContext,
                    messageStorage)) {

                message.write("troll".getBytes(StandardCharsets.UTF_8));
            }

            try (final Message message = new Message(
                    CommunicationLog.Direction.OUTBOUND,
                    CommunicationLog.MessageType.RESPONSE,
                    this.messageContext,
                    messageStorage)) {

                message.write("notExpected".getBytes(StandardCharsets.UTF_8));
            }

            try (final Message message = new Message(
                    CommunicationLog.Direction.INBOUND,
                    CommunicationLog.MessageType.RESPONSE,
                    this.messageContext,
                    messageStorage)) {

                message.write(expected.getBytes(StandardCharsets.UTF_8));
            }

            try (final Message message = new Message(
                    CommunicationLog.Direction.INBOUND,
                    CommunicationLog.MessageType.RESPONSE,
                    this.messageContext,
                    messageStorage)) {

                message.write("notExpected".getBytes(StandardCharsets.UTF_8));
            }

            try (final Message message = new Message(
                    CommunicationLog.Direction.INBOUND,
                    CommunicationLog.MessageType.RESPONSE,
                    this.messageContext,
                    messageStorage)) {

                message.write(expected.getBytes(StandardCharsets.UTF_8));
            }

            try (final Message message = new Message(
                    CommunicationLog.Direction.INBOUND,
                    CommunicationLog.MessageType.RESPONSE,
                    headerContext,
                    messageStorage)) {

                message.write("troll".getBytes(StandardCharsets.UTF_8));
            }

            messageStorage.flush();

            {
                try (final var inboundMessages = messageStorage.getInboundSoapMessages()) {
                    final var count = new AtomicInteger(0);
                    inboundMessages.getStream().forEach(message -> {
                        assertTrue(expected.equals(message.getBody()) || "troll".equals(message.getBody()));
                        count.incrementAndGet();
                    });
                    assertEquals(3, count.get());
                }
            }
        }
    }

    /**
     * Tests whether only inbound messages with messageType RESPONSE are retrieved.
     *
     * @param dir message storage directory
     * @throws IOException on io exceptions
     */
    @Test
    public void testGetInboundSoapResponseMessages(@TempDir final File dir) throws IOException, CertificateException {
        final ListMultimap<String, String> multimap = ArrayListMultimap.create();

        multimap.putAll("Content-Type", Collections.singletonList("application/soap+xml; charset=UTF-8"));

        final CommunicationContext headerContext = new CommunicationContext(
                new HttpApplicationInfo(multimap, "someId", "someUri"),
                new TransportInfo(
                        Constants.HTTPS_SCHEME,
                        null,
                        null,
                        null,
                        null,
                        Collections.singletonList(CertificateUtil.getDummyCert())));

        try (final MessageStorage messageStorage =
                new MessageStorage(1, mock(MessageFactory.class), new HibernateConfigImpl(dir), this.testRunObserver)) {
            final var expected = String.format(BASE_MESSAGE_STRING, "action1", "expected_body");

            try (final Message message = new Message(
                    CommunicationLog.Direction.OUTBOUND,
                    CommunicationLog.MessageType.REQUEST,
                    this.messageContext,
                    messageStorage)) {

                message.write(expected.getBytes(StandardCharsets.UTF_8));
            }

            try (final Message message = new Message(
                    CommunicationLog.Direction.OUTBOUND,
                    CommunicationLog.MessageType.RESPONSE,
                    headerContext,
                    messageStorage)) {

                message.write("troll".getBytes(StandardCharsets.UTF_8));
            }

            try (final Message message = new Message(
                    CommunicationLog.Direction.OUTBOUND,
                    CommunicationLog.MessageType.REQUEST,
                    this.messageContext,
                    messageStorage)) {

                message.write(expected.getBytes(StandardCharsets.UTF_8));
            }

            try (final Message message = new Message(
                    CommunicationLog.Direction.INBOUND,
                    CommunicationLog.MessageType.RESPONSE,
                    this.messageContext,
                    messageStorage)) {

                message.write(expected.getBytes(StandardCharsets.UTF_8));
            }

            try (final Message message = new Message(
                    CommunicationLog.Direction.INBOUND,
                    CommunicationLog.MessageType.RESPONSE,
                    this.messageContext,
                    messageStorage)) {

                message.write(expected.getBytes(StandardCharsets.UTF_8));
            }

            try (final Message message = new Message(
                    CommunicationLog.Direction.INBOUND,
                    CommunicationLog.MessageType.RESPONSE,
                    this.messageContext,
                    messageStorage)) {

                message.write(expected.getBytes(StandardCharsets.UTF_8));
            }

            try (final Message message = new Message(
                    CommunicationLog.Direction.INBOUND,
                    CommunicationLog.MessageType.REQUEST,
                    this.messageContext,
                    messageStorage)) {

                message.write(expected.getBytes(StandardCharsets.UTF_8));
            }

            try (final Message message = new Message(
                    CommunicationLog.Direction.INBOUND,
                    CommunicationLog.MessageType.RESPONSE,
                    headerContext,
                    messageStorage)) {

                message.write("troll".getBytes(StandardCharsets.UTF_8));
            }

            try (final Message message = new Message(
                    CommunicationLog.Direction.OUTBOUND,
                    CommunicationLog.MessageType.RESPONSE,
                    this.messageContext,
                    messageStorage)) {

                message.write(expected.getBytes(StandardCharsets.UTF_8));
            }

            messageStorage.flush();

            {
                try (final var inboundMessages = messageStorage.getInboundSoapResponseMessages()) {
                    final var count = new AtomicInteger(0);
                    inboundMessages.getStream().forEach(message -> {
                        assertTrue(expected.equals(message.getBody()) || "troll".equals(message.getBody()));
                        assertEquals(CommunicationLog.Direction.INBOUND, message.getDirection());
                        assertEquals(CommunicationLog.MessageType.RESPONSE, message.getMessageType());
                        count.incrementAndGet();
                    });
                    assertEquals(4, count.get());
                }
            }
        }
    }

    /**
     * Tests whether only inbound messages with http scheme are retrieved.
     *
     * @param dir message storage directory
     * @throws IOException on io exceptions
     */
    @Test
    public void testGetInboundHttpMessages(@TempDir final File dir) throws IOException {
        try (final MessageStorage messageStorage =
                new MessageStorage(5, mock(MessageFactory.class), new HibernateConfigImpl(dir), this.testRunObserver)) {

            try (final Message message = new Message(
                    CommunicationLog.Direction.OUTBOUND,
                    CommunicationLog.MessageType.UNKNOWN,
                    this.udpMessageContext,
                    messageStorage)) {

                message.write("notExpected".getBytes(StandardCharsets.UTF_8));
            }

            try (final Message message = new Message(
                    CommunicationLog.Direction.OUTBOUND,
                    CommunicationLog.MessageType.REQUEST,
                    this.messageContext,
                    messageStorage)) {

                message.write("notExpected".getBytes(StandardCharsets.UTF_8));
            }

            try (final Message message = new Message(
                    CommunicationLog.Direction.INBOUND,
                    CommunicationLog.MessageType.UNKNOWN,
                    this.udpMessageContext,
                    messageStorage)) {

                message.write("notExpected".getBytes(StandardCharsets.UTF_8));
            }

            try (final Message message = new Message(
                    CommunicationLog.Direction.INBOUND,
                    CommunicationLog.MessageType.RESPONSE,
                    this.messageContext,
                    messageStorage)) {

                message.write("expected".getBytes(StandardCharsets.UTF_8));
            }

            try (final Message message = new Message(
                    CommunicationLog.Direction.INBOUND,
                    CommunicationLog.MessageType.RESPONSE,
                    this.insecureMessageContext,
                    messageStorage)) {

                message.write("expected".getBytes(StandardCharsets.UTF_8));
            }

            messageStorage.flush();

            {
                try (final var inboundMessages = messageStorage.getInboundHttpMessages()) {
                    final var count = new AtomicInteger(0);
                    inboundMessages.getStream().forEach(message -> {
                        assertEquals("expected", message.getBody());
                        count.incrementAndGet();
                    });
                    assertEquals(2, count.get());
                }
            }
        }
    }

    /**
     * Test the header search and subquery linkage to the main query through the entity IDs.
     *
     * @param dir message storage directory
     * @throws IOException          on io exceptions
     * @throws CertificateException on dummy cert read errors
     */
    @Test
    public void testGetOutboundHttpMessagesByBodyTypeAndHeaders(@TempDir final File dir)
            throws IOException, CertificateException {
        try (final MessageStorage messageStorage =
                new MessageStorage(3, mock(MessageFactory.class), new HibernateConfigImpl(dir), this.testRunObserver)) {

            final var expectedQName1 = new QName(CommonConstants.NAMESPACE_MESSAGE, "some_body", "msg");
            final String expectedBody1 = "<msg:some_body><pm:once_told_me>"
                    + "the_world_was_macaroni"
                    + "</pm:once_told_me></msg:some_body>";
            final String messageContent1 = String.format(BASE_MESSAGE_STRING, "1", expectedBody1);

            final ListMultimap<String, String> multimap = ArrayListMultimap.create();

            final List<String> expectedList1 = Arrays.asList("headerContent1", "headerContent2", "headerContent3");
            final List<String> expectedList2 = Arrays.asList("headerContent4", "chunked");

            final String listName1 = "headername1";
            final String listName2 = "Transfer-Encoding";

            multimap.putAll(listName1, expectedList1);
            multimap.putAll(listName2, expectedList2);

            final X509Certificate certificate = CertificateUtil.getDummyCert();
            final CommunicationContext headerContextFull = new CommunicationContext(
                    new HttpApplicationInfo(multimap, "someId", "someUri"),
                    new TransportInfo(
                            Constants.HTTPS_SCHEME, null, null, null, null, Collections.singletonList(certificate)));
            final CommunicationContext headerContextEmpty = new CommunicationContext(
                    new HttpApplicationInfo(ArrayListMultimap.create(), "someId", "someUri"),
                    new TransportInfo(
                            Constants.HTTPS_SCHEME, null, null, null, null, Collections.singletonList(certificate)));

            try (final Message message = new Message(
                    CommunicationLog.Direction.OUTBOUND,
                    CommunicationLog.MessageType.REQUEST,
                    headerContextEmpty,
                    messageStorage)) {

                message.write(messageContent1.getBytes(StandardCharsets.UTF_8));
            }

            try (final Message message = new Message(
                    CommunicationLog.Direction.OUTBOUND,
                    CommunicationLog.MessageType.REQUEST,
                    headerContextFull,
                    messageStorage)) {

                message.write(messageContent1.getBytes(StandardCharsets.UTF_8));
            }

            try (final Message message = new Message(
                    CommunicationLog.Direction.OUTBOUND,
                    CommunicationLog.MessageType.REQUEST,
                    headerContextFull,
                    messageStorage)) {

                message.write("troll".getBytes(StandardCharsets.UTF_8));
            }

            messageStorage.flush();

            {
                try (final var outboundMessages = messageStorage.getOutboundHttpMessagesByBodyTypeAndHeaders(
                        List.of(expectedQName1),
                        List.of(new AbstractMap.SimpleImmutableEntry<>(
                                "Transfer-Encoding".toLowerCase(), "chunked")))) {
                    final var count = new AtomicInteger(0);
                    outboundMessages.getStream().forEach(message -> {
                        assertEquals(messageContent1, message.getBody());
                        assertTrue(message.getMdibVersionGroups().stream()
                                .anyMatch(mdibVersionGroup ->
                                        mdibVersionGroup.getBodyElement().equals(expectedQName1.toString())));
                        assertFalse(message.getHeaders().isEmpty());
                        count.incrementAndGet();
                    });
                    assertEquals(1, count.get());
                }
            }
        }
    }

    /**
     * Tests whether only inbound messages matching the body type are retrieved.
     *
     * @param dir message storage directory
     * @throws IOException on io exceptions
     */
    @Test
    public void testGetInboundMessagesByBodyType(@TempDir final File dir) throws IOException {
        try (final MessageStorage messageStorage =
                new MessageStorage(6, mock(MessageFactory.class), new HibernateConfigImpl(dir), this.testRunObserver)) {
            // test tag with content
            final var expectedQName1 = new QName(CommonConstants.NAMESPACE_MESSAGE, "EpisodicAlertReport", "msg");
            final String expectedBody1 = "<msg:EpisodicAlertReport><pm:once_told_me>"
                    + "the_world_was_macaroni"
                    + "</pm:once_told_me></msg:EpisodicAlertReport>";
            final String messageContent1 = String.format(BASE_MESSAGE_STRING, "1", expectedBody1);

            // test empty tag
            final var expectedQName2 = new QName(CommonConstants.NAMESPACE_MESSAGE, "EpisodicMetricReport", "msg");
            final String expectedBody2 = "<msg:EpisodicMetricReport/>";
            final String messageContent2 = String.format(BASE_MESSAGE_STRING, "2", expectedBody2);

            try (final Message message = new Message(
                    CommunicationLog.Direction.OUTBOUND,
                    CommunicationLog.MessageType.RESPONSE,
                    this.messageContext,
                    messageStorage)) {

                message.write(messageContent2.getBytes(StandardCharsets.UTF_8));
            }

            try (final Message message = new Message(
                    CommunicationLog.Direction.INBOUND,
                    CommunicationLog.MessageType.RESPONSE,
                    this.messageContext,
                    messageStorage)) {

                message.write(messageContent1.getBytes(StandardCharsets.UTF_8));
            }

            try (final Message message = new Message(
                    CommunicationLog.Direction.INBOUND,
                    CommunicationLog.MessageType.REQUEST,
                    this.messageContext,
                    messageStorage)) {

                message.write(messageContent2.getBytes(StandardCharsets.UTF_8));
            }

            try (final Message message = new Message(
                    CommunicationLog.Direction.INBOUND,
                    CommunicationLog.MessageType.RESPONSE,
                    this.messageContext,
                    messageStorage)) {

                message.write(messageContent2.getBytes(StandardCharsets.UTF_8));
            }

            try (final Message message = new Message(
                    CommunicationLog.Direction.INBOUND,
                    CommunicationLog.MessageType.RESPONSE,
                    this.messageContext,
                    messageStorage)) {

                message.write(String.format(BASE_MESSAGE_STRING, "other", "<msg:my_body/>")
                        .getBytes(StandardCharsets.UTF_8));
            }

            try (final Message message = new Message(
                    CommunicationLog.Direction.INBOUND,
                    CommunicationLog.MessageType.RESPONSE,
                    this.messageContext,
                    messageStorage)) {

                message.write(String.format(BASE_MESSAGE_STRING, "other2", "<pm:pmpmpm>ok</pm:pmpmpm>")
                        .getBytes(StandardCharsets.UTF_8));
            }

            {
                messageStorage.flush();
            }

            {
                try (final var inboundMessages = messageStorage.getInboundMessagesByBodyType(expectedQName1)) {
                    final var count = new AtomicInteger(0);
                    inboundMessages.getStream().forEach(message -> {
                        assertEquals(messageContent1, message.getBody());
                        assertTrue(message.getMdibVersionGroups().stream()
                                .anyMatch(mdibVersionGroup ->
                                        mdibVersionGroup.getBodyElement().equals(expectedQName1.toString())));
                        count.incrementAndGet();
                    });
                    assertEquals(1, count.get());
                }
            }
            {
                try (final var inboundMessages = messageStorage.getInboundMessagesByBodyType(expectedQName2)) {
                    final var count = new AtomicInteger(0);
                    inboundMessages.getStream().forEach(message -> {
                        assertEquals(messageContent2, message.getBody());
                        assertTrue(message.getMdibVersionGroups().stream()
                                .anyMatch(mdibVersionGroup ->
                                        mdibVersionGroup.getBodyElement().equals(expectedQName2.toString())));
                        count.incrementAndGet();
                    });
                    assertEquals(2, count.get());
                }
            }
            {
                // multiple body types
                try (final var inboundMessages =
                        messageStorage.getInboundMessagesByBodyType(expectedQName1, expectedQName2)) {
                    assertEquals(3, inboundMessages.getStream().count());
                }
            }
            {
                // same types multiple times
                try (final var inboundMessages =
                        messageStorage.getInboundMessagesByBodyType(expectedQName1, expectedQName1)) {
                    final var count = new AtomicInteger(0);
                    inboundMessages.getStream().forEach(message -> {
                        assertEquals(messageContent1, message.getBody());
                        count.incrementAndGet();
                    });
                    assertEquals(1, count.get());
                }
            }
        }
    }

    /**
     * Tests whether only inbound messages matching the body type and sequence id are retrieved.
     *
     * @param dir message storage directory
     * @throws IOException on io exceptions
     */
    @Test
    public void testGetInboundMessagesByBodyTypeAndSequenceId(@TempDir final File dir) throws IOException {
        try (final MessageStorage messageStorage =
                 new MessageStorage(6, mock(MessageFactory.class), new HibernateConfigImpl(dir), this.testRunObserver)) {

            final var expectedQName1 = new QName(CommonConstants.NAMESPACE_MESSAGE, "EpisodicAlertReport", "msg");
            final var expectedQName2 = new QName(CommonConstants.NAMESPACE_MESSAGE, "EpisodicMetricReport", "msg");

            final String messageContent1 = String.format(
                    BASE_MESSAGE_STRING,
                    "action",
                    String.format(SEQUENCE_ID_METRIC_BODY_STRING, "1", "s1"));

            final String messageContent2 = String.format(
                    BASE_MESSAGE_STRING,
                    "action",
                    String.format(SEQUENCE_ID_METRIC_BODY_STRING, "1", "s2"));

            final String messageContent3 = String.format(
                BASE_MESSAGE_STRING,
                "action",
                String.format(SEQUENCE_ID_ALERT_BODY_STRING, "1", "s1"));

            try (final Message message = new Message(
                CommunicationLog.Direction.OUTBOUND,
                CommunicationLog.MessageType.RESPONSE,
                this.messageContext,
                messageStorage)) {

                message.write(messageContent2.getBytes(StandardCharsets.UTF_8));
            }

            try (final Message message = new Message(
                CommunicationLog.Direction.INBOUND,
                CommunicationLog.MessageType.RESPONSE,
                this.messageContext,
                messageStorage)) {

                message.write(messageContent1.getBytes(StandardCharsets.UTF_8));
            }

            try (final Message message = new Message(
                CommunicationLog.Direction.INBOUND,
                CommunicationLog.MessageType.REQUEST,
                this.messageContext,
                messageStorage)) {

                message.write(messageContent2.getBytes(StandardCharsets.UTF_8));
            }

            try (final Message message = new Message(
                CommunicationLog.Direction.INBOUND,
                CommunicationLog.MessageType.RESPONSE,
                this.messageContext,
                messageStorage)) {

                message.write(messageContent2.getBytes(StandardCharsets.UTF_8));
            }

            try (final Message message = new Message(
                CommunicationLog.Direction.INBOUND,
                CommunicationLog.MessageType.RESPONSE,
                this.messageContext,
                messageStorage)) {

                message.write(messageContent3
                    .getBytes(StandardCharsets.UTF_8));
            }

            try (final Message message = new Message(
                CommunicationLog.Direction.OUTBOUND,
                CommunicationLog.MessageType.RESPONSE,
                this.messageContext,
                messageStorage)) {

                message.write(messageContent3
                    .getBytes(StandardCharsets.UTF_8));
            }

            {
                messageStorage.flush();
            }

            {
                try (final var inboundMessages =
                         messageStorage.getInboundMessagesByBodyTypeAndSequenceId("urn:uuid:s1", expectedQName1)) {
                    final var count = new AtomicInteger(0);
                    inboundMessages.getStream().forEach(message -> {
                        assertEquals(messageContent3, message.getBody());
                        assertTrue(message.getMdibVersionGroups().stream()
                            .anyMatch(mdibVersionGroup ->
                                mdibVersionGroup.getBodyElement().equals(expectedQName1.toString())));
                        count.incrementAndGet();
                    });
                    assertEquals(1, count.get());
                }
            }
            {
                try (final var inboundMessages =
                         messageStorage.getInboundMessagesByBodyTypeAndSequenceId("urn:uuid:s2", expectedQName2)) {
                    final var count = new AtomicInteger(0);
                    inboundMessages.getStream().forEach(message -> {
                        assertEquals(messageContent2, message.getBody());
                        assertTrue(message.getMdibVersionGroups().stream()
                            .anyMatch(mdibVersionGroup ->
                                mdibVersionGroup.getBodyElement().equals(expectedQName2.toString())));
                        count.incrementAndGet();
                    });
                    assertEquals(2, count.get());
                }
            }
            {
                // multiple body types
                try (final var inboundMessages =
                         messageStorage.getInboundMessagesByBodyTypeAndSequenceId("urn:uuid:s1", expectedQName1, expectedQName2)) {
                    assertEquals(2, inboundMessages.getStream().count());
                }
            }
            {
                // multiple body types
                try (final var inboundMessages =
                         messageStorage.getInboundMessagesByBodyTypeAndSequenceId("urn:uuid:s2", expectedQName1, expectedQName2)) {
                    assertEquals(2, inboundMessages.getStream().count());
                }
            }
            {
                // same types multiple times
                try (final var inboundMessages =
                         messageStorage.getInboundMessagesByBodyTypeAndSequenceId("urn:uuid:s1", expectedQName1, expectedQName1)) {
                    final var count = new AtomicInteger(0);
                    inboundMessages.getStream().forEach(message -> {
                        assertEquals(messageContent3, message.getBody());
                        count.incrementAndGet();
                    });
                    assertEquals(1, count.get());
                }
            }
        }
    }

    /**
     * Tests whether manipulation data are retrieved from storage.
     *
     * @param dir message storage directory
     * @throws IOException on io exceptions
     */
    @Test
    public void testGetManipulationData(@TempDir final File dir) throws IOException {
        try (final MessageStorage messageStorage =
                new MessageStorage(3, mock(MessageFactory.class), new HibernateConfigImpl(dir), this.testRunObserver)) {
            final var startTime1 = 1000;
            final var finishTime1 = 1500;
            final var result = ResponseTypes.Result.RESULT_SUCCESS;
            final var expectedMethodName = "someManipulation";

            final var manipulation = new ManipulationInfo(
                    startTime1, finishTime1, result, expectedMethodName, List.of(), messageStorage);
            manipulation.addToStorage();
            final var manipulation2 = new ManipulationInfo(
                    startTime1, finishTime1, result, expectedMethodName, List.of(), messageStorage);
            manipulation2.addToStorage();
            final var manipulation3 = new ManipulationInfo(
                    startTime1, finishTime1, result, expectedMethodName, List.of(), messageStorage);
            manipulation3.addToStorage();
            messageStorage.flush();

            try (final MessageStorage.GetterResult<ManipulationData> manipulationData =
                    messageStorage.getManipulationData()) {
                assertEquals(3, manipulationData.getStream().count());
            }
        }
    }

    /**
     * Tests whether no deadlock occurs when the first and last database entries in the queue have different types. The
     * test times out after 30 seconds in case of a deadlock.
     *
     * @param dir message storage directory
     * @throws IOException on io exceptions
     */
    @Test
    public void testMessageStorageFlushNotInDeadlock(@TempDir final File dir) throws IOException {
        try (final MessageStorage messageStorage =
                new MessageStorage(3, mock(MessageFactory.class), new HibernateConfigImpl(dir), this.testRunObserver)) {
            final String expected = "inbound_body";

            try (final Message message = new Message(
                    CommunicationLog.Direction.OUTBOUND,
                    CommunicationLog.MessageType.REQUEST,
                    this.messageContext,
                    messageStorage)) {
                message.write("outbound_body1".getBytes(StandardCharsets.UTF_8));
            }

            try (final Message message = new Message(
                    CommunicationLog.Direction.OUTBOUND,
                    CommunicationLog.MessageType.REQUEST,
                    this.messageContext,
                    messageStorage)) {

                message.write("outbound_body2".getBytes(StandardCharsets.UTF_8));
            }

            try (final Message message = new Message(
                    CommunicationLog.Direction.OUTBOUND,
                    CommunicationLog.MessageType.REQUEST,
                    this.messageContext,
                    messageStorage)) {

                message.write("outbound_body3".getBytes(StandardCharsets.UTF_8));
            }

            try (final Message message = new Message(
                    CommunicationLog.Direction.INBOUND,
                    CommunicationLog.MessageType.REQUEST,
                    this.messageContext,
                    messageStorage)) {

                message.write(expected.getBytes(StandardCharsets.UTF_8));
            }

            final var manipulation = new ManipulationInfo(
                    1000, 2000, ResponseTypes.Result.RESULT_SUCCESS, "setMetricStatus", List.of(), messageStorage);
            manipulation.addToStorage();

            assertTimeoutPreemptively(
                    Duration.ofSeconds(30),
                    messageStorage::flush,
                    "MessageStorage flush timed out after 30 seconds, might be in a deadlock.");

            try (final MessageStorage.GetterResult<MessageContent> inboundMessages =
                    messageStorage.getInboundMessages()) {
                assertTrue(inboundMessages.getStream().findAny().isPresent());
            }

            try (final MessageStorage.GetterResult<MessageContent> inboundMessages =
                    messageStorage.getInboundMessages()) {
                assertEquals(1, inboundMessages.getStream().count());
            }

            try (final MessageStorage.GetterResult<MessageContent> outboundMessages =
                    messageStorage.getOutboundMessages()) {
                assertTrue(outboundMessages.getStream().findAny().isPresent());
            }

            try (final MessageStorage.GetterResult<MessageContent> outboundMessages =
                    messageStorage.getOutboundMessages()) {
                assertEquals(3, outboundMessages.getStream().count());
            }

            try (final MessageStorage.GetterResult<ManipulationData> manipulationData =
                    messageStorage.getManipulationData()) {
                assertTrue(manipulationData.getStream().findAny().isPresent());
            }

            try (final MessageStorage.GetterResult<ManipulationData> manipulationData =
                    messageStorage.getManipulationData()) {
                assertEquals(1, manipulationData.getStream().count());
            }
        }
    }

    /**
     * Tests whether only inbound messages matching the body type inside the given time interval are retrieved.
     *
     * @param dir message storage directory
     * @throws IOException on io exceptions
     */
    @Test
    public void testGetInboundMessagesByTimeIntervalAndBodyType(@TempDir final File dir) throws IOException {
        try (final MessageStorage messageStorage =
                new MessageStorage(6, mock(MessageFactory.class), new HibernateConfigImpl(dir), this.testRunObserver)) {
            // test tag with content
            final var expectedQName1 = new QName(CommonConstants.NAMESPACE_MESSAGE, "EpisodicAlertReport", "msg");
            final String expectedBody1 = "<msg:EpisodicAlertReport><pm:once_told_me>"
                    + "the_world_was_macaroni"
                    + "</pm:once_told_me></msg:EpisodicAlertReport>";
            final String messageContent1 = String.format(BASE_MESSAGE_STRING, "1", expectedBody1);

            // test empty tag
            final var expectedQName2 = new QName(CommonConstants.NAMESPACE_MESSAGE, "EpisodicMetricReport", "msg");
            final String expectedBody2 = "<msg:EpisodicMetricReport/>";
            final String messageContent2 = String.format(BASE_MESSAGE_STRING, "2", expectedBody2);

            final var startInterval = System.nanoTime();

            try (final Message message = new Message(
                    CommunicationLog.Direction.OUTBOUND,
                    CommunicationLog.MessageType.RESPONSE,
                    this.messageContext,
                    messageStorage)) {

                message.write(messageContent2.getBytes(StandardCharsets.UTF_8));
            }

            try (final Message message = new Message(
                    CommunicationLog.Direction.INBOUND,
                    CommunicationLog.MessageType.RESPONSE,
                    this.messageContext,
                    messageStorage)) {
                message.write(messageContent1.getBytes(StandardCharsets.UTF_8));
            }

            try (final Message message = new Message(
                    CommunicationLog.Direction.INBOUND,
                    CommunicationLog.MessageType.RESPONSE,
                    this.messageContext,
                    messageStorage)) {
                message.write(messageContent2.getBytes(StandardCharsets.UTF_8));
            }

            try (final Message message = new Message(
                    CommunicationLog.Direction.INBOUND,
                    CommunicationLog.MessageType.RESPONSE,
                    this.messageContext,
                    messageStorage)) {
                message.write(messageContent2.getBytes(StandardCharsets.UTF_8));
            }

            try (final Message message = new Message(
                    CommunicationLog.Direction.INBOUND,
                    CommunicationLog.MessageType.RESPONSE,
                    this.messageContext,
                    messageStorage)) {
                message.write(String.format(BASE_MESSAGE_STRING, "other", "<msg:my_body/>")
                        .getBytes(StandardCharsets.UTF_8));
            }
            final var finishInterval = System.nanoTime();

            // should not count since not in time interval
            final var mockMessage = mock(Message.class);
            final var mockMessageId = UUID.randomUUID();
            when(mockMessage.getID()).thenReturn(mockMessageId.toString());
            when(mockMessage.getDirection()).thenReturn(CommunicationLog.Direction.INBOUND);
            when(mockMessage.getMessageType()).thenReturn(CommunicationLog.MessageType.RESPONSE);
            when(mockMessage.getCommunicationContext()).thenReturn(this.messageContext);
            when(mockMessage.getNanoTimestamp()).thenReturn(finishInterval + 10000);
            when(mockMessage.getFinalMemory()).thenReturn(messageContent2.getBytes(StandardCharsets.UTF_8));
            messageStorage.addMessage(mockMessage);

            messageStorage.flush();

            {
                try (final var inboundMessages = messageStorage.getInboundMessagesByTimeIntervalAndBodyType(
                        startInterval, finishInterval, expectedQName2)) {
                    final var count = new AtomicInteger(0);
                    inboundMessages.getStream().forEach(message -> {
                        assertEquals(messageContent2, message.getBody());
                        assertTrue(message.getMdibVersionGroups().stream()
                                .anyMatch(mdibVersionGroup ->
                                        mdibVersionGroup.getBodyElement().equals(expectedQName2.toString())));
                        count.incrementAndGet();
                    });
                    assertEquals(2, count.get());
                }
            }
            {
                try (final var inboundMessages = messageStorage.getInboundMessagesByTimeIntervalAndBodyType(
                        startInterval, finishInterval, expectedQName1, expectedQName2)) {
                    assertEquals(3, inboundMessages.getStream().count());
                }
            }
        }
    }

    /**
     * Tests whether only manipulation data matching the given manipulation are retrieved.
     *
     * @param dir message storage directory
     * @throws IOException on io exceptions
     */
    @Test
    public void testGetManipulationDataByManipulation(@TempDir final File dir) throws IOException {
        try (final MessageStorage messageStorage =
                new MessageStorage(6, mock(MessageFactory.class), new HibernateConfigImpl(dir), this.testRunObserver)) {
            final var startTime1 = 1000;
            final var finishTime1 = 1500;
            final var result = ResponseTypes.Result.RESULT_SUCCESS;
            final var methodName1 = "setMetricStatus";
            final List<Pair<String, String>> parameters1 = List.of(
                    new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, "someHandle"),
                    new ImmutablePair<>(
                            Constants.MANIPULATION_PARAMETER_COMPONENT_ACTIVATION, ComponentActivation.ON.value()));
            final var manipulationInfo =
                    new ManipulationInfo(startTime1, finishTime1, result, methodName1, parameters1, messageStorage);
            manipulationInfo.addToStorage();

            final var startTime2 = 1200;
            final var finishTime2 = 1300;
            final var methodName2 = "sendHello";
            final var manipulationInfo2 = new ManipulationInfo(
                    startTime2, finishTime2, result, methodName2, Collections.emptyList(), messageStorage);
            manipulationInfo2.addToStorage();

            messageStorage.flush();

            {
                try (final var inboundMessages = messageStorage.getManipulationDataByManipulation(methodName1)) {
                    final var count = new AtomicInteger(0);
                    inboundMessages.getStream().forEach(message -> {
                        assertEquals(manipulationInfo.getStartTimestamp(), message.getStartTimestamp());
                        assertEquals(manipulationInfo.getFinishTimestamp(), message.getFinishTimestamp());
                        assertEquals(manipulationInfo.getResult(), message.getResult());
                        assertEquals(manipulationInfo.getMethodName(), message.getMethodName());
                        for (var parameter : message.getParameters()) {
                            assertTrue(manipulationInfo.getParameter().stream()
                                    .map(Pair::getKey)
                                    .anyMatch(it -> it.equals(parameter.getParameterName())));
                            assertTrue(manipulationInfo.getParameter().stream()
                                    .map(Pair::getValue)
                                    .anyMatch(it -> it.equals(parameter.getParameterValue())));
                        }
                        count.incrementAndGet();
                    });
                    assertEquals(1, count.get());
                }
            }
            {
                try (final var inboundMessages =
                        messageStorage.getManipulationDataByManipulation(methodName1, methodName2)) {
                    inboundMessages.getStream().forEach(message -> {
                        assertEquals(2, inboundMessages.getStream().count());
                    });
                }
            }
        }
    }

    /**
     * Tests whether only manipulation data matching the given manipulation and parameters are retrieved.
     *
     * @param dir message storage directory
     * @throws Exception on any exception
     */
    @Test
    public void testGetManipulationDataByParametersAndManipulation(@TempDir final File dir) throws Exception {
        try (final MessageStorage messageStorage =
                new MessageStorage(1, mock(MessageFactory.class), new HibernateConfigImpl(dir), this.testRunObserver)) {
            final var startTime1 = 1000;
            final var finishTime1 = 1500;
            final var result = ResponseTypes.Result.RESULT_SUCCESS;
            final var expectedMethodName = "setMetricStatus";
            final List<Pair<String, String>> expectedParameters = List.of(
                    new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, "someHandle"),
                    new ImmutablePair<>(
                            Constants.MANIPULATION_PARAMETER_COMPONENT_ACTIVATION, ComponentActivation.ON.value()));
            final var expectedManipulationInfo = new ManipulationInfo(
                    startTime1, finishTime1, result, expectedMethodName, expectedParameters, messageStorage);
            expectedManipulationInfo.addToStorage();

            // same manipulation without parameter
            final var manipulationWithoutParams = new ManipulationInfo(
                    startTime1, finishTime1, result, expectedMethodName, Collections.emptyList(), messageStorage);
            manipulationWithoutParams.addToStorage();

            // same manipulation with different handle parameter
            final List<Pair<String, String>> parameters2 = List.of(
                    new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, "someOtherHandle"),
                    new ImmutablePair<>(
                            Constants.MANIPULATION_PARAMETER_COMPONENT_ACTIVATION, ComponentActivation.ON.value()));
            final var manipulationDifferentHandle = new ManipulationInfo(
                    startTime1, finishTime1, result, expectedMethodName, parameters2, messageStorage);
            manipulationDifferentHandle.addToStorage();

            // different manipulation with same parameter
            final var differentManipulationSameParam = new ManipulationInfo(
                    startTime1, finishTime1, result, "setComponentActivation", expectedParameters, messageStorage);
            differentManipulationSameParam.addToStorage();

            final var otherManipulation =
                    new ManipulationInfo(1200, 1300, result, "sendHello", Collections.emptyList(), messageStorage);
            otherManipulation.addToStorage();

            messageStorage.flush();
            {
                try (final var inboundMessages = messageStorage.getManipulationDataByParametersAndManipulation(
                        expectedParameters, expectedMethodName)) {
                    final var count = new AtomicInteger(0);
                    inboundMessages.getStream().forEach(message -> {
                        assertEquals(expectedManipulationInfo.getStartTimestamp(), message.getStartTimestamp());
                        assertEquals(expectedManipulationInfo.getFinishTimestamp(), message.getFinishTimestamp());
                        assertEquals(expectedManipulationInfo.getResult(), message.getResult());
                        assertEquals(expectedManipulationInfo.getMethodName(), message.getMethodName());
                        for (var parameter : message.getParameters()) {
                            assertTrue(expectedManipulationInfo.getParameter().stream()
                                    .map(Pair::getKey)
                                    .anyMatch(it -> it.equals(parameter.getParameterName())));
                            assertTrue(expectedManipulationInfo.getParameter().stream()
                                    .map(Pair::getValue)
                                    .anyMatch(it -> it.equals(parameter.getParameterValue())));
                        }
                        count.incrementAndGet();
                    });
                    assertEquals(
                            1, count.get(), "Only one matching manipulation should've been retrieved from storage.");
                }
            }
            // add second manipulation
            final var secondManipulation = new ManipulationInfo(
                    startTime1, finishTime1, result, expectedMethodName, expectedParameters, messageStorage);
            secondManipulation.addToStorage();
            messageStorage.flush();
            {
                try (final var inboundMessages = messageStorage.getManipulationDataByParametersAndManipulation(
                        expectedParameters, expectedMethodName)) {
                    final var count = new AtomicInteger(0);
                    inboundMessages.getStream().forEach(message -> {
                        count.incrementAndGet();
                    });
                    assertEquals(2, count.get(), "Two matching manipulation should've been retrieved from storage.");
                }
            }
        }
    }

    /**
     * Tests whether only manipulation data matching the given manipulation and parameters are retrieved.
     *
     * @param dir message storage directory
     * @throws Exception on any exception
     */
    @Test
    public void testGetManipulationDataByParametersAndManipulationOneParameter(@TempDir final File dir)
            throws Exception {
        try (final MessageStorage messageStorage =
                new MessageStorage(1, mock(MessageFactory.class), new HibernateConfigImpl(dir), this.testRunObserver)) {
            final var startTime1 = 1000;
            final var finishTime1 = 1500;
            final var result = ResponseTypes.Result.RESULT_SUCCESS;
            final var expectedMethodName = "setMetricStatus";
            final List<Pair<String, String>> expectedParameters =
                    List.of(new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, "someHandle"));

            final var expectedManipulationInfo = new ManipulationInfo(
                    startTime1,
                    finishTime1,
                    result,
                    expectedMethodName,
                    List.of(
                            expectedParameters.get(0),
                            new ImmutablePair<>(
                                    Constants.MANIPULATION_PARAMETER_COMPONENT_ACTIVATION,
                                    ComponentActivation.ON.value())),
                    messageStorage);
            expectedManipulationInfo.addToStorage();

            // same manipulation without parameter
            final var manipulationWithoutParams = new ManipulationInfo(
                    startTime1, finishTime1, result, expectedMethodName, List.of(), messageStorage);
            manipulationWithoutParams.addToStorage();

            // same manipulation with different handle parameter
            final List<Pair<String, String>> parameters2 = List.of(
                    new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, "someOtherHandle"),
                    new ImmutablePair<>(
                            Constants.MANIPULATION_PARAMETER_COMPONENT_ACTIVATION, ComponentActivation.ON.value()));
            final var manipulationDifferentHandle = new ManipulationInfo(
                    startTime1, finishTime1, result, expectedMethodName, parameters2, messageStorage);
            manipulationDifferentHandle.addToStorage();

            // different manipulation with same parameter
            final var methodName3 = "setComponentActivation";
            final var differentManipulationSameParam = new ManipulationInfo(
                    startTime1, finishTime1, result, methodName3, expectedParameters, messageStorage);
            differentManipulationSameParam.addToStorage();

            final var startTime2 = 1200;
            final var finishTime2 = 1300;
            final var methodName2 = "sendHello";
            final var otherManipulation = new ManipulationInfo(
                    startTime2, finishTime2, result, methodName2, Collections.emptyList(), messageStorage);
            otherManipulation.addToStorage();

            messageStorage.flush();

            {
                try (final var inboundMessages = messageStorage.getManipulationDataByParametersAndManipulation(
                        expectedParameters, expectedMethodName)) {
                    final var count = new AtomicInteger(0);
                    inboundMessages.getStream().forEach(message -> {
                        assertEquals(expectedManipulationInfo.getStartTimestamp(), message.getStartTimestamp());
                        assertEquals(expectedManipulationInfo.getFinishTimestamp(), message.getFinishTimestamp());
                        assertEquals(expectedManipulationInfo.getResult(), message.getResult());
                        assertEquals(expectedManipulationInfo.getMethodName(), message.getMethodName());
                        for (var parameter : expectedParameters) {
                            assertTrue(message.getParameters().stream()
                                    .map(ManipulationParameter::getParameterName)
                                    .anyMatch(it -> it.equals(parameter.getKey())));
                            assertTrue(message.getParameters().stream()
                                    .map(ManipulationParameter::getParameterValue)
                                    .anyMatch(it -> it.equals(parameter.getValue())));
                        }
                        count.incrementAndGet();
                    });
                    assertEquals(1, count.get());
                }
            }
        }
    }

    /**
     * Tests whether only manipulation data matching the given manipulation is retrieved when no parameters are
     * specified.
     *
     * @param dir message storage directory
     * @throws Exception on any exception
     */
    @Test
    public void testGetManipulationDataByParametersAndManipulationEmptyParameters(@TempDir final File dir)
            throws Exception {
        try (final MessageStorage messageStorage =
                new MessageStorage(1, mock(MessageFactory.class), new HibernateConfigImpl(dir), this.testRunObserver)) {
            final var startTime1 = 1000;
            final var finishTime1 = 1500;
            final var result = ResponseTypes.Result.RESULT_SUCCESS;
            final var methodName1 = "setMetricStatus";
            final List<Pair<String, String>> parameters1 =
                    List.of(new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, "someHandle"));
            final var manipulation1 =
                    new ManipulationInfo(startTime1, finishTime1, result, methodName1, List.of(), messageStorage);
            manipulation1.addToStorage();

            // same manipulation without parameter
            final var manipulation2 =
                    new ManipulationInfo(startTime1, finishTime1, result, methodName1, parameters1, messageStorage);
            manipulation2.addToStorage();

            // same manipulation with different handle parameter
            final List<Pair<String, String>> parameters2 = List.of(
                    new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, "someOtherHandle"),
                    new ImmutablePair<>(
                            Constants.MANIPULATION_PARAMETER_COMPONENT_ACTIVATION, ComponentActivation.ON.value()));
            final var manipulation3 =
                    new ManipulationInfo(startTime1, finishTime1, result, methodName1, parameters2, messageStorage);
            manipulation3.addToStorage();

            // different manipulation with same parameter
            final var manipulation4 = new ManipulationInfo(
                    startTime1, finishTime1, result, "setComponentActivation", List.of(), messageStorage);
            manipulation4.addToStorage();

            final var manipulation5 =
                    new ManipulationInfo(1200, 1300, result, "sendHello", Collections.emptyList(), messageStorage);
            manipulation5.addToStorage();

            messageStorage.flush();

            {
                try (final var inboundMessages = messageStorage.getManipulationDataByParametersAndManipulation(
                        Collections.emptyList(), methodName1)) {
                    final var count = new AtomicInteger(0);
                    inboundMessages.getStream().forEach(message -> {
                        assertEquals(methodName1, message.getMethodName());
                        count.incrementAndGet();
                    });
                    assertEquals(
                            3,
                            count.get(),
                            String.format(
                                    "Three manipulation with method name %s should've been retrieved from storage.",
                                    methodName1));
                }
            }
        }
    }

    @Test
    void testDetermineCharsetFromMessageFromHttpHeader(@TempDir final File dir) throws IOException {
        try (final MessageStorage messageStorage =
                new MessageStorage(1, mock(MessageFactory.class), new HibernateConfigImpl(dir), this.testRunObserver)) {
            // given
            final ListMultimap<String, String> headers = ArrayListMultimap.create();
            headers.put("Content-Type", "application/xml; charset=ISO-8859-13");

            final HttpApplicationInfo applicationInfo = new HttpApplicationInfo(headers, "transactionId", "requestURI");
            final TransportInfo transportInfo =
                    new TransportInfo("http", "localhost", 1234, "remotehost", 4567, List.of());
            final CommunicationContext communicationContext = new CommunicationContext(applicationInfo, transportInfo);
            final Message message = new Message(
                    CommunicationLog.Direction.INBOUND,
                    CommunicationLog.MessageType.REQUEST,
                    communicationContext,
                    messageStorage);
            message.close();

            // when
            final Charset actualCharset = messageStorage.determineCharsetFromMessage(message);

            // then
            assertEquals(Charset.forName("ISO-8859-13"), actualCharset);
            Mockito.verify(this.testRunObserver, VerificationModeFactory.atLeastOnce())
                    .invalidateTestRun(anyString());
        }
    }

    @Test
    void testDetermineCharsetFromMessageFromHttpHeaderWithQuotes(@TempDir final File dir) throws IOException {
        try (final MessageStorage messageStorage =
                new MessageStorage(1, mock(MessageFactory.class), new HibernateConfigImpl(dir), this.testRunObserver)) {
            // given
            final ListMultimap<String, String> headers = ArrayListMultimap.create();
            headers.put("Content-Type", "application/soap+xml; charset='ISO-8859-13'");

            final HttpApplicationInfo applicationInfo = new HttpApplicationInfo(headers, "transactionId", "requestURI");
            final TransportInfo transportInfo =
                    new TransportInfo("http", "localhost", 1234, "remotehost", 4567, List.of());
            final CommunicationContext communicationContext = new CommunicationContext(applicationInfo, transportInfo);
            final Message message = new Message(
                    CommunicationLog.Direction.INBOUND,
                    CommunicationLog.MessageType.REQUEST,
                    communicationContext,
                    messageStorage);
            message.close();

            // when
            final Charset actualCharset = messageStorage.determineCharsetFromMessage(message);
            // then
            assertEquals(Charset.forName("ISO-8859-13"), actualCharset);
            Mockito.verify(this.testRunObserver, VerificationModeFactory.atLeastOnce())
                    .invalidateTestRun(anyString());
        }
    }

    @Test
    void testDetermineCharsetFromMessageFromHttpHeaderWithDoubleQuotes(@TempDir final File dir) throws IOException {
        try (final MessageStorage messageStorage =
                new MessageStorage(1, mock(MessageFactory.class), new HibernateConfigImpl(dir), this.testRunObserver)) {
            // given
            final ListMultimap<String, String> headers = ArrayListMultimap.create();
            headers.put("Content-Type", "application/xml; charset=\"ISO-8859-13\"");

            final HttpApplicationInfo applicationInfo = new HttpApplicationInfo(headers, "transactionId", "requestURI");
            final TransportInfo transportInfo =
                    new TransportInfo("http", "localhost", 1234, "remotehost", 4567, List.of());
            final CommunicationContext communicationContext = new CommunicationContext(applicationInfo, transportInfo);
            final Message message = new Message(
                    CommunicationLog.Direction.INBOUND,
                    CommunicationLog.MessageType.REQUEST,
                    communicationContext,
                    messageStorage);
            message.close();

            // when
            final Charset actualCharset = messageStorage.determineCharsetFromMessage(message);

            // then
            assertEquals(Charset.forName("ISO-8859-13"), actualCharset);
            Mockito.verify(this.testRunObserver, VerificationModeFactory.atLeastOnce())
                    .invalidateTestRun(anyString());
        }
    }

    @Test
    void testDetermineCharsetFromMessageFromHttpHeaderWithBoundary(@TempDir final File dir) throws IOException {
        try (final MessageStorage messageStorage =
                new MessageStorage(1, mock(MessageFactory.class), new HibernateConfigImpl(dir), this.testRunObserver)) {
            // given
            final ListMultimap<String, String> headers = ArrayListMultimap.create();
            headers.put("Content-Type", "application/xml; charset=ISO-8859-13; boundary=XYZ");

            final HttpApplicationInfo applicationInfo = new HttpApplicationInfo(headers, "transactionId", "requestURI");
            final TransportInfo transportInfo =
                    new TransportInfo("http", "localhost", 1234, "remotehost", 4567, List.of());
            final CommunicationContext communicationContext = new CommunicationContext(applicationInfo, transportInfo);
            final Message message = new Message(
                    CommunicationLog.Direction.INBOUND,
                    CommunicationLog.MessageType.REQUEST,
                    communicationContext,
                    messageStorage);
            message.close();

            // when
            final Charset actualCharset = messageStorage.determineCharsetFromMessage(message);

            // then
            assertEquals(Charset.forName("ISO-8859-13"), actualCharset);
            Mockito.verify(this.testRunObserver, VerificationModeFactory.atLeastOnce())
                    .invalidateTestRun(anyString());
        }
    }

    @Test
    void testDetermineCharsetFromMessageFromHttpHeaderWithBoundary2(@TempDir final File dir) throws IOException {
        try (final MessageStorage messageStorage =
                new MessageStorage(1, mock(MessageFactory.class), new HibernateConfigImpl(dir), this.testRunObserver)) {
            // given
            final ListMultimap<String, String> headers = ArrayListMultimap.create();
            headers.put("Content-Type", "application/xml; charset=ISO-8859-13 ;boundary=XYZ");

            final HttpApplicationInfo applicationInfo = new HttpApplicationInfo(headers, "transactionId", "requestURI");
            final TransportInfo transportInfo =
                    new TransportInfo("http", "localhost", 1234, "remotehost", 4567, List.of());
            final CommunicationContext communicationContext = new CommunicationContext(applicationInfo, transportInfo);
            final Message message = new Message(
                    CommunicationLog.Direction.INBOUND,
                    CommunicationLog.MessageType.REQUEST,
                    communicationContext,
                    messageStorage);
            message.close();

            // when
            final Charset actualCharset = messageStorage.determineCharsetFromMessage(message);

            // then
            assertEquals(Charset.forName("ISO-8859-13"), actualCharset);
            Mockito.verify(this.testRunObserver, VerificationModeFactory.atLeastOnce())
                    .invalidateTestRun(anyString());
        }
    }

    @Test
    void testDetermineCharsetFromMessageFromXmlDeclaration(@TempDir final File dir) throws IOException {
        try (final MessageStorage messageStorage =
                new MessageStorage(1, mock(MessageFactory.class), new HibernateConfigImpl(dir), this.testRunObserver)) {
            // given
            final ListMultimap<String, String> headers = ArrayListMultimap.create();
            headers.put("Content-Type", "application/soap+xml");

            final HttpApplicationInfo applicationInfo = new HttpApplicationInfo(headers, "transactionId", "requestURI");
            final TransportInfo transportInfo =
                    new TransportInfo("http", "localhost", 1234, "remotehost", 4567, List.of());
            final CommunicationContext communicationContext = new CommunicationContext(applicationInfo, transportInfo);
            final Message message = new Message(
                    CommunicationLog.Direction.INBOUND,
                    CommunicationLog.MessageType.REQUEST,
                    communicationContext,
                    messageStorage);
            final String content = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n<sometag></sometag>";
            final byte[] encodedContent = content.getBytes(StandardCharsets.ISO_8859_1);
            message.write(encodedContent, 0, encodedContent.length);
            message.close();

            // when
            final Charset actualCharset = messageStorage.determineCharsetFromMessage(message);

            // then
            assertEquals(StandardCharsets.ISO_8859_1, actualCharset);
            Mockito.verify(this.testRunObserver, VerificationModeFactory.atLeastOnce())
                    .invalidateTestRun(anyString());
        }
    }

    @Test
    void testDetermineEBCDICCharsetFromMessageFromXmlDeclaration(@TempDir final File dir) throws IOException {
        try (final MessageStorage messageStorage =
                new MessageStorage(1, mock(MessageFactory.class), new HibernateConfigImpl(dir), this.testRunObserver)) {
            // given
            final ListMultimap<String, String> headers = ArrayListMultimap.create();
            headers.put("Content-Type", "application/xml");

            final HttpApplicationInfo applicationInfo = new HttpApplicationInfo(headers, "transactionId", "requestURI");
            final TransportInfo transportInfo =
                    new TransportInfo("http", "localhost", 1234, "remotehost", 4567, List.of());
            final CommunicationContext communicationContext = new CommunicationContext(applicationInfo, transportInfo);
            final Message message = new Message(
                    CommunicationLog.Direction.INBOUND,
                    CommunicationLog.MessageType.REQUEST,
                    communicationContext,
                    messageStorage);
            final String content = "<?xml version=\"1.0\" encoding=\"ebcdic-gb-285+euro\"?>\n<sometag></sometag>";
            final byte[] encodedContent = content.getBytes(Charset.forName("ebcdic-gb-285+euro"));
            message.write(encodedContent, 0, encodedContent.length);
            message.close();

            // when
            final Charset actualCharset = messageStorage.determineCharsetFromMessage(message);

            // then
            assertEquals(Charset.forName("ebcdic-gb-285+euro"), actualCharset);
            Mockito.verify(this.testRunObserver, VerificationModeFactory.atLeastOnce())
                    .invalidateTestRun(anyString());
        }
    }

    @Test
    void testDetermineCharsetFromMessageFromXmlDeclarationUsingSingleQuotes(@TempDir final File dir)
            throws IOException {
        try (final MessageStorage messageStorage =
                new MessageStorage(1, mock(MessageFactory.class), new HibernateConfigImpl(dir), this.testRunObserver)) {
            // given
            final ListMultimap<String, String> headers = ArrayListMultimap.create();
            headers.put("Content-Type", "application/soap+xml");

            final HttpApplicationInfo applicationInfo = new HttpApplicationInfo(headers, "transactionId", "requestURI");
            final TransportInfo transportInfo =
                    new TransportInfo("http", "localhost", 1234, "remotehost", 4567, List.of());
            final CommunicationContext communicationContext = new CommunicationContext(applicationInfo, transportInfo);
            final Message message = new Message(
                    CommunicationLog.Direction.INBOUND,
                    CommunicationLog.MessageType.REQUEST,
                    communicationContext,
                    messageStorage);
            final String content = "<?xml version='1.0' encoding='ISO-8859-1'?>\n<sometag></sometag>";
            final byte[] encodedContent = content.getBytes(StandardCharsets.ISO_8859_1);
            message.write(encodedContent, 0, encodedContent.length);
            message.close();

            // when
            final Charset actualCharset = messageStorage.determineCharsetFromMessage(message);

            // then
            assertEquals(StandardCharsets.ISO_8859_1, actualCharset);
            Mockito.verify(this.testRunObserver, VerificationModeFactory.atLeastOnce())
                    .invalidateTestRun(anyString());
        }
    }

    @Test
    void testDetermineCharsetFromMessageFromByteOrderMark(@TempDir final File dir) throws IOException {
        testForCharset(dir, Charset.forName("UTF-32LE"), ByteOrderMark.UTF_32LE, true);
        testForCharset(dir, Charset.forName("UTF-32BE"), ByteOrderMark.UTF_32BE, true);
        testForCharset(dir, StandardCharsets.UTF_16LE, ByteOrderMark.UTF_16LE, true);
        testForCharset(dir, StandardCharsets.UTF_16BE, ByteOrderMark.UTF_16BE, true);
        testForCharset(dir, StandardCharsets.UTF_8, ByteOrderMark.UTF_8, false);
    }

    private void testForCharset(
            final File dir, final Charset charset, final ByteOrderMark bom, final boolean expectFailure)
            throws IOException {
        Mockito.reset(this.testRunObserver);
        try (final MessageStorage messageStorage =
                new MessageStorage(1, mock(MessageFactory.class), new HibernateConfigImpl(dir), this.testRunObserver)) {
            // given
            final ListMultimap<String, String> headers = ArrayListMultimap.create();
            headers.put("Content-Type", "application/soap+xml");

            final HttpApplicationInfo applicationInfo = new HttpApplicationInfo(headers, "transactionId", "requestURI");
            final TransportInfo transportInfo =
                    new TransportInfo("http", "localhost", 1234, "remotehost", 4567, List.of());
            final CommunicationContext communicationContext = new CommunicationContext(applicationInfo, transportInfo);
            final Message message = new Message(
                    CommunicationLog.Direction.INBOUND,
                    CommunicationLog.MessageType.REQUEST,
                    communicationContext,
                    messageStorage);
            final String content = "<?xml version=\"1.0\"?>\n<sometag></sometag>";
            final byte[] encodedContent = content.getBytes(charset);
            final byte[] bomBytes = bom.getBytes();
            message.write(bomBytes, 0, bomBytes.length);
            message.write(encodedContent, 0, encodedContent.length);
            message.close();

            // when
            final Charset actualCharset = messageStorage.determineCharsetFromMessage(message);

            // then
            assertEquals(charset, actualCharset);
            if (expectFailure) {
                Mockito.verify(this.testRunObserver, VerificationModeFactory.atLeastOnce())
                        .invalidateTestRun(anyString());
            } else {
                Mockito.verifyNoInteractions(this.testRunObserver);
            }
        }
    }

    @Test
    void testDetermineCharsetFromMessageFailureCharsetCannotBeDetermined(@TempDir final File dir) throws IOException {
        try (final MessageStorage messageStorage =
                new MessageStorage(1, mock(MessageFactory.class), new HibernateConfigImpl(dir), this.testRunObserver)) {
            // given
            final ListMultimap<String, String> headers = ArrayListMultimap.create();
            headers.put("Content-Type", "text/html"); // no charset in HTTP Header

            final HttpApplicationInfo applicationInfo = new HttpApplicationInfo(headers, "transactionId", "requestURI");
            final TransportInfo transportInfo =
                    new TransportInfo("http", "localhost", 1234, "remotehost", 4567, List.of());
            final CommunicationContext communicationContext = new CommunicationContext(applicationInfo, transportInfo);
            final Message message = new Message(
                    CommunicationLog.Direction.INBOUND,
                    CommunicationLog.MessageType.REQUEST,
                    communicationContext,
                    messageStorage);
            final String content = "<?xml version=\"1.0\"?>\n<sometag></sometag>"; // no encoding in XML Declaration
            final byte[] encodedContent = content.getBytes(StandardCharsets.UTF_8);
            // Note: no BOM
            message.write(encodedContent, 0, encodedContent.length);
            message.close();

            // when
            final Charset actualCharset = messageStorage.determineCharsetFromMessage(message);

            // then
            assertEquals(StandardCharsets.UTF_8, actualCharset);
            Mockito.verify(this.testRunObserver, VerificationModeFactory.atLeastOnce())
                    .invalidateTestRun(anyString());
        }
    }

    @Test
    void testDetermineCharsetFromMessageConsistent(@TempDir final File dir) throws IOException {
        final Charset actualCharset = testDetermineCharsetFromMessageUsingCharsets(
                dir,
                StandardCharsets.UTF_8,
                ByteOrderMark.UTF_8,
                StandardCharsets.UTF_8,
                StandardCharsets.UTF_8,
                "application/soap+xml");
        // then
        assertEquals(StandardCharsets.UTF_8, actualCharset);
        Mockito.verifyNoInteractions(this.testRunObserver);
    }

    @Test
    void testDetermineCharsetFromMessageFailureEncodingNotUTF8ConsistentEBCDIC(@TempDir final File dir)
            throws IOException {
        final Charset actualCharset = testDetermineCharsetFromMessageUsingCharsets(
                dir,
                Charset.forName("CP1147"),
                null,
                Charset.forName("CP1147"),
                Charset.forName("CP1147"),
                "application/soap+xml");
        // then
        assertEquals(Charset.forName("CP1147"), actualCharset);
        Mockito.verify(this.testRunObserver, VerificationModeFactory.atLeastOnce())
                .invalidateTestRun(anyString());
    }

    @Test
    void testDetermineCharsetFromMessageFailureEncodingNotUTF8ConsistentASCIICompatible(@TempDir final File dir)
            throws IOException {
        final Charset actualCharset = testDetermineCharsetFromMessageUsingCharsets(
                dir,
                StandardCharsets.ISO_8859_1,
                null,
                StandardCharsets.ISO_8859_1,
                StandardCharsets.ISO_8859_1,
                "application/soap+xml");
        // then
        assertEquals(StandardCharsets.ISO_8859_1, actualCharset);
        Mockito.verify(this.testRunObserver, VerificationModeFactory.atLeastOnce())
                .invalidateTestRun(anyString());
    }

    @Test
    void testDetermineCharsetFromMessageFailureHTTPHeaderInconsistent(@TempDir final File dir) throws IOException {
        final Charset actualCharset = testDetermineCharsetFromMessageUsingCharsets(
                dir,
                StandardCharsets.ISO_8859_1,
                ByteOrderMark.UTF_8,
                StandardCharsets.UTF_8,
                StandardCharsets.UTF_8,
                "application/soap+xml");
        // then
        assertEquals(StandardCharsets.ISO_8859_1, actualCharset);
        Mockito.verify(this.testRunObserver, VerificationModeFactory.atLeastOnce())
                .invalidateTestRun(anyString());
    }

    @Test
    void testDetermineCharsetFromMessageFailureBOMInconsistent(@TempDir final File dir) throws IOException {
        final Charset actualCharset = testDetermineCharsetFromMessageUsingCharsets(
                dir,
                StandardCharsets.UTF_8,
                ByteOrderMark.UTF_16BE,
                StandardCharsets.UTF_8,
                StandardCharsets.UTF_8,
                "application/soap+xml");
        // then
        assertEquals(StandardCharsets.UTF_8, actualCharset);
        Mockito.verify(this.testRunObserver, VerificationModeFactory.atLeastOnce())
                .invalidateTestRun(anyString());
    }

    @Test
    void testDetermineCharsetFromMessageFailureXMLDeclarationInconsistent(@TempDir final File dir) throws IOException {
        final Charset actualCharset = testDetermineCharsetFromMessageUsingCharsets(
                dir,
                StandardCharsets.UTF_8,
                ByteOrderMark.UTF_8,
                StandardCharsets.ISO_8859_1,
                StandardCharsets.UTF_8,
                "application/soap+xml");
        // then
        assertEquals(StandardCharsets.UTF_8, actualCharset);
        Mockito.verify(this.testRunObserver, VerificationModeFactory.atLeastOnce())
                .invalidateTestRun(anyString());
    }

    @Test
    void testDetermineCharsetFromMessageFailureXmlDeclarationEncodingInconsistent(@TempDir final File dir)
            throws IOException {
        final Charset actualCharset = testDetermineCharsetFromMessageUsingCharsets(
                dir,
                StandardCharsets.UTF_8,
                ByteOrderMark.UTF_8,
                StandardCharsets.UTF_8,
                StandardCharsets.UTF_16LE,
                "application/soap+xml");
        // then
        assertEquals(StandardCharsets.UTF_8, actualCharset);
        Mockito.verify(this.testRunObserver, VerificationModeFactory.atLeastOnce())
                .invalidateTestRun(anyString());
    }

    @Test
    void testDetermineCharsetFromMessageFailureWrongMimeType(@TempDir final File dir) throws IOException {
        final Charset actualCharset = testDetermineCharsetFromMessageUsingCharsets(
                dir,
                StandardCharsets.UTF_8,
                ByteOrderMark.UTF_8,
                StandardCharsets.UTF_8,
                StandardCharsets.UTF_8,
                "text/xml");
        // then
        assertEquals(StandardCharsets.UTF_8, actualCharset);
        Mockito.verify(this.testRunObserver, VerificationModeFactory.atLeastOnce())
                .invalidateTestRun(anyString());
    }

    @Test
    void testDetermineCharsetFromMessageASCIISpecialCase(@TempDir final File dir) throws IOException {
        final Charset actualCharset = testDetermineCharsetFromMessageUsingCharsets(
                dir, null, null, StandardCharsets.ISO_8859_1, StandardCharsets.UTF_8, "application/soap+xml");
        // then
        assertEquals(StandardCharsets.ISO_8859_1, actualCharset);
        Mockito.verify(this.testRunObserver, VerificationModeFactory.atLeastOnce())
                .invalidateTestRun(anyString());
    }

    private Charset testDetermineCharsetFromMessageUsingCharsets(
            final File dir,
            @Nullable final Charset charsetInHttpHeader,
            @Nullable final ByteOrderMark bom,
            final Charset charsetInXMLDeclaration,
            final Charset charsetInXMLDeclarationEncoding,
            final String mimeType)
            throws IOException {
        try (final MessageStorage messageStorage =
                new MessageStorage(1, mock(MessageFactory.class), new HibernateConfigImpl(dir), this.testRunObserver)) {
            // given
            final ListMultimap<String, String> headers = ArrayListMultimap.create();
            if (charsetInHttpHeader != null) {
                headers.put("Content-Type", String.format("%s;charset=%s", mimeType, charsetInHttpHeader));
            } else {
                headers.put("Content-Type", mimeType);
            }

            final HttpApplicationInfo applicationInfo = new HttpApplicationInfo(headers, "transactionId", "requestURI");
            final TransportInfo transportInfo =
                    new TransportInfo("http", "localhost", 1234, "remotehost", 4567, List.of());
            final CommunicationContext communicationContext = new CommunicationContext(applicationInfo, transportInfo);
            final Message message = new Message(
                    CommunicationLog.Direction.INBOUND,
                    CommunicationLog.MessageType.REQUEST,
                    communicationContext,
                    messageStorage);
            final String content = String.format(
                    "<?xml version=\"1.0\" encoding=\"%s\"?>%n<sometag></sometag>", charsetInXMLDeclaration);
            final byte[] encodedContent = content.getBytes(charsetInXMLDeclarationEncoding);
            if (bom != null) {
                final byte[] bomBytes = bom.getBytes();
                message.write(bomBytes, 0, bomBytes.length);
            }
            message.write(encodedContent, 0, encodedContent.length);
            message.close();

            // when
            return messageStorage.determineCharsetFromMessage(message);
        }
    }
}
