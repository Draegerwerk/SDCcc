/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.tests.dpws.invariant;

import com.draeger.medical.sdccc.configuration.EnabledTestConfig;
import com.draeger.medical.sdccc.messages.MessageStorage;
import com.draeger.medical.sdccc.tests.InjectorTestBase;
import com.draeger.medical.sdccc.tests.annotations.TestDescription;
import com.draeger.medical.sdccc.tests.annotations.TestIdentifier;
import com.draeger.medical.sdccc.util.XPathExtractor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.somda.sdc.dpws.soap.wsaddressing.WsAddressingConstants;

import javax.xml.xpath.XPathExpressionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.draeger.medical.sdccc.util.Constants.s12;
import static com.draeger.medical.sdccc.util.Constants.wsa;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * DPWS Messaging tests for
 * Devices Profile for Web Services Version 1.1
 * OASIS Standard 1 July 2009.
 */
public class InvariantMessagingTest extends InjectorTestBase {

    private static final Logger LOG = LogManager.getLogger();
    private static final String WS_ADDRESSING_RELATIONSHIP = WsAddressingConstants.NAMESPACE + "/reply";
    private static final String RELATIONSHIP_ATTRIBUTE = "RelationshipType";
    private static final String BROKEN_R0019_IRI = "wsa:Reply";

    @Test
    @TestIdentifier(EnabledTestConfig.DPWS_R0019)
    @TestDescription("Verifies the relationship property is set in all response messages from the DUT.")
    void testRequirement0019() throws Exception {
        final var messageStorage = getInjector().getInstance(MessageStorage.class);

        final var relatesToExtractor = new XPathExtractor(s12("Header") + "/" + wsa("RelatesTo"));

        try (final var inboundSoaps = messageStorage.getInboundSoapResponseMessages()) {
            assertTestData(inboundSoaps.areObjectsPresent(), "No inbound messages to perform test on.");

            inboundSoaps.getStream().forEach(message -> {

                final var messageBody = message.getBody();
                try {
                    final var relationshipProperty = relatesToExtractor.extractFrom(messageBody)
                        .stream().findFirst();
                    assertFalse(relationshipProperty.isEmpty());
                    final var attribute = relationshipProperty.orElseThrow().getAttributes()
                        .getNamedItem(RELATIONSHIP_ATTRIBUTE);
                    assertNotNull(attribute, "No RelationshipType is set");
                    assertEquals(BROKEN_R0019_IRI, attribute.getNodeValue());
                } catch (XPathExpressionException e) {
                    LOG.error("XPathExpressionException while trying to traverse {}. Message: {}",
                        messageBody, e.getMessage());
                    fail("Encountered XPathExpressionException trying to traverse " + messageBody);
                }
            });
        }
    }

    @Test
    @TestIdentifier(EnabledTestConfig.DPWS_R0040)
    @TestDescription("Checks all response messages from the DUT containing a SOAP Fault and verifies the relationship"
        + " property is set.")
    void testRequirement0040() throws Exception {
        final var messageStorage = getInjector().getInstance(MessageStorage.class);

        final var faultExtractor = new XPathExtractor(s12("Body") + "/" + s12("Fault"));
        final var relatesToExtractor = new XPathExtractor(s12("Header") + "/" + wsa("RelatesTo"));

        try (final var inboundSoaps = messageStorage.getInboundSoapMessages()) {
            assertTestData(inboundSoaps.areObjectsPresent(), "No inbound messages to perform test on.");

            final AtomicBoolean faultsPresent = new AtomicBoolean(false);

            inboundSoaps.getStream().forEach(message -> {
                final var messageBody = message.getBody();
                try {
                    final var fault = faultExtractor.extractFrom(messageBody).stream().findFirst().orElse(null);
                    if (fault != null) {
                        faultsPresent.set(true);
                        final var relatesTo = relatesToExtractor.extractFrom(messageBody).stream().findFirst();
                        assertFalse(relatesTo.isEmpty());
                        final var attribute = relatesTo.orElseThrow()
                            .getAttributes().getNamedItem(RELATIONSHIP_ATTRIBUTE);
                        assertTrue(attribute == null || attribute.getNodeValue().equals(WS_ADDRESSING_RELATIONSHIP));
                    }
                } catch (XPathExpressionException e) {
                    LOG.error("XPathExpressionException while trying to traverse {}. Message: {}",
                        messageBody, e.getMessage());
                    fail("Encountered XPathExpressionException trying to traverse " + messageBody);
                }
            });

            assertTrue(faultsPresent.get(), "No Soap Faults present.");
        }
    }
}
