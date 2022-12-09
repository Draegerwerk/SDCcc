/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.tests.mdpws.invariant;

import static com.draeger.medical.sdccc.util.Constants.wsd;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.draeger.medical.sdccc.configuration.EnabledTestConfig;
import com.draeger.medical.sdccc.manipulation.precondition.impl.ConditionalPreconditions;
import com.draeger.medical.sdccc.messages.MessageStorage;
import com.draeger.medical.sdccc.tests.InjectorTestBase;
import com.draeger.medical.sdccc.tests.annotations.RequirePrecondition;
import com.draeger.medical.sdccc.tests.annotations.TestDescription;
import com.draeger.medical.sdccc.tests.annotations.TestIdentifier;
import com.draeger.medical.sdccc.util.Constants;
import com.draeger.medical.sdccc.util.XPathExtractor;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import javax.xml.namespace.QName;
import javax.xml.xpath.XPathExpressionException;
import org.junit.jupiter.api.Test;
import org.somda.sdc.dpws.DpwsConstants;
import org.somda.sdc.mdpws.common.CommonConstants;
import org.w3c.dom.Node;

/**
 * MDPWS dynamic discovery tests (ch. 5).
 */
public class InvariantDynamicDiscoveryTest extends InjectorTestBase {

    @Test
    @TestIdentifier(EnabledTestConfig.MDPWS_R0008)
    @TestDescription("Verifies for all incoming Hello, ProbeMatches and ResolveMatches messages that"
            + " Types includes the dpws:Device and mdpws:MedicalDevice elements.")
    @RequirePrecondition(simplePreconditions = {ConditionalPreconditions.HelloMessagePrecondition.class})
    void testRequirement0008() throws Exception {

        final var messageStorage = getInjector().getInstance(MessageStorage.class);

        final var bodyTypes =
                List.of(Constants.WSD_HELLO_BODY, Constants.WSD_RESOLVE_MATCHES_BODY, Constants.WSD_PROBE_MATCHES_BODY);

        for (final var bodyType : bodyTypes) {
            try (final var messages = messageStorage.getInboundMessagesByBodyType(false, bodyType)) {

                assertTestData(messages.areObjectsPresent(), "No messages found for bodyType " + bodyType);

                final var typesExtractor = new XPathExtractor("//" + wsd("Types"));
                final AtomicInteger typesMessages = new AtomicInteger();

                messages.getStream().forEach(message -> {
                    final Collection<Node> typesNodes;
                    try {
                        typesNodes = typesExtractor.extractFrom(message.getBody());
                    } catch (final XPathExpressionException e) {
                        throw new RuntimeException(e);
                    }

                    if (typesNodes.isEmpty()) {
                        return;
                    }

                    typesMessages.getAndIncrement();

                    for (final var node : typesNodes) {
                        final Collection<QName> types = retrieveQNames(node);

                        assertTrue(
                                types.contains(DpwsConstants.DEVICE_TYPE),
                                String.format(
                                        "Message %s did not contain %s in Types",
                                        message.getMessageHash(), DpwsConstants.DEVICE_TYPE));

                        assertTrue(
                                types.contains(CommonConstants.MEDICAL_DEVICE_TYPE),
                                String.format(
                                        "Message %s did not contain %s in Types",
                                        message.getMessageHash(), CommonConstants.MEDICAL_DEVICE_TYPE));
                    }
                });
                assertTestData(
                        typesMessages.get(), "Saw no messages containing wsd:Types element for bodyType " + bodyType);
            }
        }
    }

    Collection<QName> retrieveQNames(final Node node) {
        return Arrays.stream(node.getTextContent().split("\\s+"))
                // QNames without prefix are in "no namespace", so no match
                .filter(type -> type.contains(":"))
                .map(type -> new QName(node.lookupNamespaceURI(type.split(":")[0]), type.split(":")[1]))
                .collect(Collectors.toList());
    }
}
