/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.tests.dpws.direct;

import com.draeger.medical.sdccc.configuration.EnabledTestConfig;
import com.draeger.medical.sdccc.messages.MessageStorage;
import com.draeger.medical.sdccc.sdcri.testclient.TestClient;
import com.draeger.medical.sdccc.tests.InjectorTestBase;
import com.draeger.medical.sdccc.tests.annotations.TestDescription;
import com.draeger.medical.sdccc.tests.annotations.TestIdentifier;
import com.draeger.medical.sdccc.util.Constants;
import com.draeger.medical.sdccc.util.MessageGeneratingUtil;
import com.draeger.medical.sdccc.util.MessagingException;
import com.google.inject.Key;
import com.google.inject.name.Names;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.somda.sdc.dpws.DpwsConfig;
import org.somda.sdc.dpws.soap.MarshallingService;
import org.somda.sdc.dpws.soap.SoapMessage;
import org.somda.sdc.dpws.soap.exception.MarshallingException;

import java.io.IOException;
import java.io.StringReader;
import java.util.AbstractMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Tests for the DPWS HTTP section.
 */
public class DirectHttpTest extends InjectorTestBase {

    private TestClient testClient;
    private MessageGeneratingUtil messageGeneratingUtil;

    @BeforeEach
    void setUp() {
        this.testClient = getInjector().getInstance(TestClient.class);
        this.messageGeneratingUtil = getInjector().getInstance(MessageGeneratingUtil.class);
    }

    @Test
    @TestIdentifier(EnabledTestConfig.DPWS_R0001)
    @TestDescription("Sends a chunked message and verifies that the "
        + " end was able to read it without any errors")
    void testRequirementR0001() throws IOException, MessagingException {

        final boolean chunkedEnforced = this.testClient.getInjector().getInstance(
            Key.get(Boolean.class, Names.named(DpwsConfig.ENFORCE_HTTP_CHUNKED_TRANSFER))
        );
        assertTrue(chunkedEnforced);

        final SoapMessage response = this.messageGeneratingUtil.getMdib();
        assertFalse(response.isFault());


        final MessageStorage messageStorage = getInjector().getInstance(MessageStorage.class);

        // ensure, that the messages from the above call are already in the database
        messageStorage.flush();

        final MarshallingService marshalling = testClient.getInjector().getInstance(MarshallingService.class);

        try (final var messages = messageStorage.getOutboundHttpMessagesByBodyTypeAndHeaders(
            List.of(Constants.MSG_GET_MDIB),
            List.of(new AbstractMap.SimpleImmutableEntry<>("Transfer-Encoding".toLowerCase(), "chunked")))) {
            assertTrue(messages.areObjectsPresent());

            final long getMdibRequestsCount = messages.getStream().filter(x -> {
                try {
                    marshalling.unmarshal(new StringReader(x.getBody()));
                    return true;
                } catch (MarshallingException e) {
                    return false;
                }
            }).count();
            assertTrue(getMdibRequestsCount > 0);
        }

    }
}
