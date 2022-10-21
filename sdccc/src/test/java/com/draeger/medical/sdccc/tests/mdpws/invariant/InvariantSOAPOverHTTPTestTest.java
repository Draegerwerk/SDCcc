/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.tests.mdpws.invariant;

import com.draeger.medical.sdccc.marshalling.MarshallingUtil;
import com.draeger.medical.sdccc.messages.MessageStorage;
import com.draeger.medical.sdccc.sdcri.testclient.TestClient;
import com.draeger.medical.sdccc.tests.InjectorTestBase;
import com.draeger.medical.sdccc.tests.test_util.InjectorUtil;
import com.draeger.medical.sdccc.tests.util.NoTestData;
import com.draeger.medical.sdccc.util.Constants;
import com.draeger.medical.sdccc.util.MessageStorageUtil;
import com.draeger.medical.sdccc.util.TestRunInformation;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import org.apache.http.HttpHeaders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test for the MDPWS {@linkplain InvariantSOAPOverHTTPTest}.
 */
public class InvariantSOAPOverHTTPTestTest {
    private static final String MESSAGE_TEMPLATE = "<?xml version='1.0' encoding='UTF-8'?>%n"
        + "<s12:Envelope xmlns:ns0=\"http://standards.ieee.org/downloads/11073/11073-10207-2017/message\"%n"
        + "              xmlns:s12=\"http://www.w3.org/2003/05/soap-envelope\"%n"
        + "              xmlns:wsa=\"http://www.w3.org/2005/08/addressing\">%n"
        + "    <s12:Header>%n"
        + "        <wsa:To>urn:uuid:2e39c722-a565-449c-8a1b-d800d9g7bfd7</wsa:To>%n"
        + "        <wsa:Action>http://standards.ieee.org/downloads/11073/11073-20701-2018/"
        + "LocalizationService/GetLocalizedTextResponse</wsa:Action>%n"
        + "        <wsa:MessageID>urn:uuid:2e39c722-a565-449c-8a1b-00ee00ee00ee</wsa:MessageID>%n"
        + "        <wsa:RelatesTo>urn:uuid:2e39c722-a565-449c-8a1b-00ff00ff00ff</wsa:RelatesTo>%n"
        + "    </s12:Header>%n"
        + "    <s12:Body>%n"
        + "        <ns0:GetLocalizedTextResponse SequenceId=\"27\">%n"
        + "            <ns0:Text>%n"
        + "                This is some nice text. Mh, I like it pretty much."
        + " It's my favourite text of all the texts.%s%n"
        + "            </ns0:Text>%n"
        + "        </ns0:GetLocalizedTextResponse>%n"
        + "    </s12:Body>%n"
        + "</s12:Envelope>%n";

    private static MessageStorageUtil messageStorageUtil;
    private MessageStorage storage;
    private InvariantSOAPOverHTTPTest testClass;
    private Injector injector;


    private static String getMessageOfSize(final int size) {
        final var empty = String.format(MESSAGE_TEMPLATE, "");
        final var emptyBytes = empty.getBytes(StandardCharsets.UTF_8);
        assert size >= emptyBytes.length;

        final var toFill = size - emptyBytes.length;

        final var result = String.format(MESSAGE_TEMPLATE, "a".repeat(toFill));
        assert size == result.getBytes(StandardCharsets.UTF_8).length;
        return result;
    }

    @BeforeAll
    static void setupMarshalling() {
        final Injector marshallingInjector = MarshallingUtil.createMarshallingTestInjector(true);
        messageStorageUtil = marshallingInjector.getInstance(MessageStorageUtil.class);
    }

    @BeforeEach
    void setUp() throws IOException {
        final TestClient mockClient = mock(TestClient.class);
        when(mockClient.isClientRunning()).thenReturn(true);

        injector = InjectorUtil.setupInjector(
            new AbstractModule() {
                @Override
                protected void configure() {
                    bind(TestClient.class).toInstance(mockClient);
                }
            }
        );

        InjectorTestBase.setInjector(injector);

        storage = injector.getInstance(MessageStorage.class);
        testClass = new InvariantSOAPOverHTTPTest();
    }

    @AfterEach
    void tearDown() throws IOException {
        storage.close();
    }

    /**
     * Tests whether calling the tests without any input data causes a failure.
     */
    @Test
    public void testNoData() {
        assertThrows(NoTestData.class, testClass::testRequirement0006);
    }

    /**
     * Inserts messages below and exactly on the limit of acceptable.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testR0006Good() throws Exception {
        final ListMultimap<String, String> headers = ArrayListMultimap.create();
        headers.put(HttpHeaders.CONTENT_TYPE.toLowerCase(), Constants.HTTP_APPLICATION_SOAP_XML);

        final var mediumSizeMessage = getMessageOfSize(Constants.MAX_LARGE_ENVELOPE_SIZE / 2);
        final var mediumSizeStream = new ByteArrayInputStream(mediumSizeMessage.getBytes(StandardCharsets.UTF_8));
        messageStorageUtil.addInboundSecureHttpMessage(storage, mediumSizeStream, Collections.emptyList(), headers);

        final var maxSizeMessage = getMessageOfSize(Constants.MAX_LARGE_ENVELOPE_SIZE);
        final var maxSizeStream = new ByteArrayInputStream(maxSizeMessage.getBytes(StandardCharsets.UTF_8));
        messageStorageUtil.addInboundSecureHttpMessage(storage, maxSizeStream, Collections.emptyList(), headers);

        testClass.testRequirement0006();
    }

    /**
     * Inserts a message exactly above the allowed limit.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testR0006Bad() throws Exception {
        final var maxSizeMessage = getMessageOfSize(Constants.MAX_LARGE_ENVELOPE_SIZE + 1);
        final var maxSizeStream = new ByteArrayInputStream(maxSizeMessage.getBytes(StandardCharsets.UTF_8));

        final ListMultimap<String, String> headers = ArrayListMultimap.create();
        headers.put(HttpHeaders.CONTENT_TYPE.toLowerCase(), Constants.HTTP_APPLICATION_SOAP_XML);
        messageStorageUtil.addInboundSecureHttpMessage(storage, maxSizeStream, Collections.emptyList(), headers);

        final AssertionError error = assertThrows(AssertionError.class, () -> testClass.testRequirement0006());
        assertFalse(error.getMessage().contains(Constants.HTTP_MULTIPART_PREFIX));
    }

    /**
     * Tests whether the test fails when an archive service is present.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testR0006ArchiveServicePresent() throws Exception {
        final ListMultimap<String, String> headers = ArrayListMultimap.create();
        headers.put(HttpHeaders.CONTENT_TYPE.toLowerCase(), Constants.HTTP_APPLICATION_SOAP_XML);

        final var maxSizeMessage = getMessageOfSize(Constants.MAX_LARGE_ENVELOPE_SIZE);
        final var maxSizeStream = new ByteArrayInputStream(maxSizeMessage.getBytes(StandardCharsets.UTF_8));
        messageStorageUtil.addInboundSecureHttpMessage(storage, maxSizeStream, Collections.emptyList(), headers);

        final var runInfo = injector.getInstance(TestRunInformation.class);
        runInfo.setArchiveServicePresent(true);

        final AssertionError error = assertThrows(AssertionError.class, () -> testClass.testRequirement0006());
        assertTrue(error.getMessage().contains("archive service"));
    }

    /**
     * Tests whether the test fails when a message using Multipart/Related Content-Type is present.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testR0006BadContentTypeMultipart() throws Exception {
        final ListMultimap<String, String> goodHeaders = ArrayListMultimap.create();
        goodHeaders.put(HttpHeaders.CONTENT_TYPE.toLowerCase(), Constants.HTTP_APPLICATION_SOAP_XML);

        final var mediumSizeMessage = getMessageOfSize(Constants.MAX_LARGE_ENVELOPE_SIZE / 2);
        final var mediumSizeStream = new ByteArrayInputStream(mediumSizeMessage.getBytes(StandardCharsets.UTF_8));
        messageStorageUtil.addInboundSecureHttpMessage(storage, mediumSizeStream, Collections.emptyList(), goodHeaders);

        final ListMultimap<String, String> headers = ArrayListMultimap.create();
        headers.put(HttpHeaders.CONTENT_TYPE.toLowerCase(), Constants.HTTP_MULTIPART_RELATED);
        headers.put(HttpHeaders.LAST_MODIFIED.toLowerCase(), "Wed, 21 Oct 2015 07:28:00 GMT");

        final var maxSizeMessage = getMessageOfSize(Constants.MAX_LARGE_ENVELOPE_SIZE);
        final var maxSizeStream = new ByteArrayInputStream(maxSizeMessage.getBytes(StandardCharsets.UTF_8));
        messageStorageUtil.addInboundSecureHttpMessage(
            storage,
            maxSizeStream,
            Collections.emptyList(),
            headers
        );

        final var error = assertThrows(AssertionError.class, testClass::testRequirement0006);
        assertTrue(error.getMessage().contains(Constants.HTTP_MULTIPART_PREFIX));
    }

    /**
     * Tests whether the test fails when a message using application/exi Content-Type is present.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testR0006BadContentType() throws Exception {
        final ListMultimap<String, String> goodHeaders = ArrayListMultimap.create();
        goodHeaders.put(HttpHeaders.CONTENT_TYPE.toLowerCase(), Constants.HTTP_APPLICATION_SOAP_XML);

        final var mediumSizeMessage = getMessageOfSize(Constants.MAX_LARGE_ENVELOPE_SIZE / 2);
        final var mediumSizeStream = new ByteArrayInputStream(mediumSizeMessage.getBytes(StandardCharsets.UTF_8));
        messageStorageUtil.addInboundSecureHttpMessage(storage, mediumSizeStream, Collections.emptyList(), goodHeaders);

        final ListMultimap<String, String> headers = ArrayListMultimap.create();
        headers.put(HttpHeaders.CONTENT_TYPE.toLowerCase(), Constants.HTTP_APPLICATION_EXI);
        headers.put(HttpHeaders.LAST_MODIFIED.toLowerCase(), "Wed, 21 Oct 2015 07:28:00 GMT");

        final var maxSizeMessage = getMessageOfSize(Constants.MAX_LARGE_ENVELOPE_SIZE);
        final var maxSizeStream = new ByteArrayInputStream(maxSizeMessage.getBytes(StandardCharsets.UTF_8));
        messageStorageUtil.addInboundSecureHttpMessage(
            storage,
            maxSizeStream,
            Collections.emptyList(),
            headers
        );

        final var error = assertThrows(AssertionError.class, testClass::testRequirement0006);
        assertTrue(error.getMessage().contains(HttpHeaders.CONTENT_TYPE));
    }

    /**
     * Tests whether the test fails when no incoming message using application/soap+xml is present.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testR0006BadNoApplicationSoapXml() throws Exception {
        final ListMultimap<String, String> goodHeaders = ArrayListMultimap.create();
        goodHeaders.put(HttpHeaders.CONTENT_TYPE.toLowerCase(), Constants.HTTP_APPLICATION_XML);

        final var mediumSizeMessage = getMessageOfSize(Constants.MAX_LARGE_ENVELOPE_SIZE / 2);
        final var mediumSizeStream = new ByteArrayInputStream(mediumSizeMessage.getBytes(StandardCharsets.UTF_8));
        messageStorageUtil.addInboundSecureHttpMessage(storage, mediumSizeStream, Collections.emptyList(), goodHeaders);

        final var error = assertThrows(NoTestData.class, testClass::testRequirement0006);
        assertTrue(error.getMessage().contains(Constants.HTTP_APPLICATION_SOAP_XML));
    }

    /**
     * Tests that content-type is fully evaluated, i.e. an incorrect entry after a correct entry leads to a failure.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testR0006BadWrongHeaderAfterGoodHeader() throws Exception {
        final ListMultimap<String, String> goodHeaders = ArrayListMultimap.create();
        goodHeaders.put(HttpHeaders.CONTENT_TYPE.toLowerCase(), Constants.HTTP_APPLICATION_SOAP_XML);
        goodHeaders.put(HttpHeaders.CONTENT_TYPE.toLowerCase(), Constants.HTTP_APPLICATION_SOAP_XML);
        goodHeaders.put(HttpHeaders.CONTENT_TYPE.toLowerCase(), Constants.HTTP_APPLICATION_SOAP_XML);
        goodHeaders.put(HttpHeaders.CONTENT_TYPE.toLowerCase(), Constants.HTTP_APPLICATION_SOAP_XML);
        goodHeaders.put(HttpHeaders.CONTENT_TYPE.toLowerCase(), Constants.HTTP_APPLICATION_SOAP_XML);
        goodHeaders.put(HttpHeaders.CONTENT_TYPE.toLowerCase(), "application/green+nose");

        final var mediumSizeMessage = getMessageOfSize(Constants.MAX_LARGE_ENVELOPE_SIZE / 2);
        final var mediumSizeStream = new ByteArrayInputStream(mediumSizeMessage.getBytes(StandardCharsets.UTF_8));
        messageStorageUtil.addInboundSecureHttpMessage(storage, mediumSizeStream, Collections.emptyList(), goodHeaders);

        final var error = assertThrows(AssertionError.class, testClass::testRequirement0006);
        assertTrue(error.getMessage().contains(HttpHeaders.CONTENT_TYPE));
    }

}
