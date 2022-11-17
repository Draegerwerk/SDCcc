/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.tests.mdpws.invariant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.draeger.medical.sdccc.configuration.EnabledTestConfig;
import com.draeger.medical.sdccc.messages.MessageStorage;
import com.draeger.medical.sdccc.tests.InjectorTestBase;
import com.draeger.medical.sdccc.tests.annotations.TestDescription;
import com.draeger.medical.sdccc.tests.annotations.TestIdentifier;
import com.draeger.medical.sdccc.tests.util.NoTestData;
import com.draeger.medical.sdccc.util.Constants;
import com.draeger.medical.sdccc.util.TestRunInformation;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.http.HttpHeaders;
import org.junit.jupiter.api.Test;

/**
 * MDPWS SOAP-over-HTTP tests (ch. 4.3).
 */
public class InvariantSOAPOverHTTPTest extends InjectorTestBase {

    @Test
    @TestIdentifier(EnabledTestConfig.MDPWS_R0006)
    @TestDescription("Verifies for all incoming http messages that the transmitted"
            + " SOAP message does not exceed MAX_LARGE_ENVELOPE_SIZE and that no other content-type"
            + " than application/soap+xml and application/xml is used.")
    void testRequirement0006() throws IOException, NoTestData {

        // TODO: Due to ambiguous interpretations of the standard, this test will fail
        //       when there is an archive service present. It is expected to send
        //       potentially very large messages, which would need to be accounted
        //       for in this test. As they are currently not being generated during
        //       the test run, this test cannot provide an acceptable answer under
        //       those circumstances. see https://github.com/Draegerwerk/SDCcc/issues/4
        //       and https://github.com/Draegerwerk/SDCcc/issues/5
        final var testRunInfo = getInjector().getInstance(TestRunInformation.class);
        if (testRunInfo.hasArchiveService()) {
            fail("mdpws:R0006 cannot currently be tested with an archive service present."
                    + " If this affects you, contact the developers of SDCcc."
                    + " Due to ambiguous interpretations of the standard, this test will fail "
                    + " when there is an archive service present. It is expected to send"
                    + " potentially very large messages, which would need to be accounted"
                    + " for in this test. As they are currently not being generated during"
                    + " the test run, this test cannot provide an acceptable answer under"
                    + " those circumstances.");
        }

        final var messageStorage = getInjector().getInstance(MessageStorage.class);

        final var hadSoapXml = new AtomicBoolean(false);
        try (final var inboundGetter = messageStorage.getInboundHttpMessages()) {
            assertTestData(inboundGetter.areObjectsPresent(), "No inbound messages to perform test on.");

            inboundGetter.getStream().forEach(message -> {

                // TODO: Messages using the attachment mechanism mandated by DPWS, e.g. Multipart/Related,
                //  are currently unsupported, https://github.com/Draegerwerk/SDCcc/issues/4
                //  and https://github.com/Draegerwerk/SDCcc/issues/6
                final var contentType = message.getHeaders()
                        .getOrDefault(HttpHeaders.CONTENT_TYPE.toLowerCase(), Collections.emptyList());

                var isSoapXml = false;
                for (final String entry : contentType) {
                    // explicitly fail on multipart, as that is doable in theory
                    assertFalse(
                            entry.contains(Constants.HTTP_MULTIPART_PREFIX),
                            String.format(
                                    "Inbound message %s uses the HTTP %s %s. Multipart content types are currently"
                                            + " unsupported, message length cannot be determined.",
                                    message.getMessageHash(), HttpHeaders.CONTENT_TYPE, entry));

                    // fail on anything other than application/xml and application/soap+xml
                    final var isCorrectContentType = entry.contains(Constants.HTTP_APPLICATION_SOAP_XML)
                            || entry.contains(Constants.HTTP_APPLICATION_XML);

                    assertTrue(
                            isCorrectContentType,
                            String.format(
                                    "%s for message %s is not allowed. %s or %s required, but %s present.",
                                    HttpHeaders.CONTENT_TYPE,
                                    message.getMessageHash(),
                                    Constants.HTTP_APPLICATION_SOAP_XML,
                                    Constants.HTTP_APPLICATION_XML,
                                    entry));

                    isSoapXml |= entry.contains(Constants.HTTP_APPLICATION_SOAP_XML);
                }

                if (isSoapXml) {
                    hadSoapXml.set(true);
                    assertTrue(
                            message.getBody().getBytes(StandardCharsets.UTF_8).length
                                    <= Constants.MAX_LARGE_ENVELOPE_SIZE,
                            "The DUT transmitted a message with more than MAX_LARGE_ENVELOPE_SIZE bytes."
                                    + " Message hash was " + message.getMessageHash());
                }
            });
        }

        assertTestData(
                hadSoapXml.get(),
                String.format(
                        "No incoming message was matching %s %s",
                        HttpHeaders.CONTENT_TYPE, Constants.HTTP_APPLICATION_SOAP_XML));
    }
}
