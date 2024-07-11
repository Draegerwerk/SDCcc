/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2023 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.tests.biceps.invariant;

import static com.draeger.medical.sdccc.util.Constants.REF_ELEMENT_QUERY;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.draeger.medical.sdccc.configuration.EnabledTestConfig;
import com.draeger.medical.sdccc.manipulation.precondition.impl.DummyObservingPrecondition;
import com.draeger.medical.sdccc.messages.MessageStorage;
import com.draeger.medical.sdccc.messages.mapping.MessageContent;
import com.draeger.medical.sdccc.tests.InjectorTestBase;
import com.draeger.medical.sdccc.tests.annotations.RequirePrecondition;
import com.draeger.medical.sdccc.tests.annotations.TestDescription;
import com.draeger.medical.sdccc.tests.annotations.TestIdentifier;
import com.draeger.medical.sdccc.util.XPathExtractor;
import com.google.inject.Injector;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import javax.xml.xpath.XPathExpressionException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Test for the normative Annex of BICEPS.
 */
public class InvariantBicepsNormativeAnnexTest extends InjectorTestBase {
    private static final Logger LOG = LogManager.getLogger(InvariantBicepsNormativeAnnexTest.class);

    private Injector injector;
    private MessageStorage messageStorage;

    @BeforeEach
    void setUp() {
        this.injector = getInjector();
        this.messageStorage = injector.getInstance(MessageStorage.class);
    }

    // NOTE: The way that a Device uses LocalizedTexts is usually static. It does hence not make sense to implement
    //       a manipulation that causes LocalizedTests that have no @Ref attribute to get one.
    //       When a device does not use @Ref attributes in its LocalizedTexts, then this test is not applicable to it.
    @Test
    @TestIdentifier(EnabledTestConfig.BICEPS_R5006)
    @TestDescription(
            "Verifies that for all incoming messages, Localized text with the @Ref attribute always has a version")
    @RequirePrecondition(observingPreconditions = {DummyObservingPrecondition.class})
    void testRequirement5006() throws Exception {

        final var refExtractor = new XPathExtractor(REF_ELEMENT_QUERY);

        try (final MessageStorage.GetterResult<MessageContent> inboundMessages =
                messageStorage.getInboundSoapMessages()) {

            assertTestData(inboundMessages.areObjectsPresent(), "No inbound messages to perform test on.");

            // track whether we've seen any @Ref elements at all
            final AtomicInteger numberOfNodesWithRef = new AtomicInteger();
            inboundMessages.getStream().forEach(message -> {
                try {
                    // collect all elements using @Ref
                    final var nodes = refExtractor.extractFrom(message.getBody());
                    numberOfNodesWithRef.addAndGet(nodes.size());
                    final var nodesWithoutVersion = nodes.stream()
                            .filter(node -> node.getAttributes().getNamedItem("Version") == null)
                            .collect(Collectors.toList());
                    final StringBuilder nodeRefs = new StringBuilder();
                    nodesWithoutVersion.forEach(x -> nodeRefs.append(
                                    x.getAttributes().getNamedItem("Ref").getTextContent())
                            .append(" "));

                    assertTrue(
                            nodesWithoutVersion.isEmpty(),
                            "There are pm:LocalizedText elements with an attribute @Ref but without the"
                                    + " attribute @Version in the soap message with hash "
                                    + message.getMessageHash()
                                    + ". Version was missing for @Ref: "
                                    + nodeRefs.toString());

                } catch (final XPathExpressionException e) {
                    final var error = String.format(
                            "Error while extracting LocalizedText/@Ref elements from message with hash %s",
                            message.getMessageHash());
                    LOG.error(error, e);
                    fail(error, e);
                }
            });

            assertTestData(numberOfNodesWithRef.get(), "No LocalizedTexts using @Ref were found.");
        }
    }
}
