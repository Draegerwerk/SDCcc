/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.tests.glue.direct;

import com.draeger.medical.sdccc.configuration.EnabledTestConfig;
import com.draeger.medical.sdccc.sdcri.testclient.TestClient;
import com.draeger.medical.sdccc.tests.InjectorTestBase;
import com.draeger.medical.sdccc.tests.annotations.TestDescription;
import com.draeger.medical.sdccc.tests.annotations.TestIdentifier;
import com.draeger.medical.sdccc.tests.util.NoTestData;
import com.draeger.medical.sdccc.util.XPathExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.somda.sdc.dpws.soap.exception.TransportException;
import org.somda.sdc.dpws.wsdl.WsdlRetriever;

import javax.xml.xpath.XPathExpressionException;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.draeger.medical.sdccc.util.Constants.wsdl;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Glue Discovery binding tests.
 */
public class DirectDiscoveryBindingTest extends InjectorTestBase {
    public static final String XML_NAMESPACE_DEFINITION_ATTRIBUTE_PREFIX = "xmlns";
    private static final String DEFAULT_NAMESPACE = "_DEFAULT";
    private static final String DISCOVERY_TYPE = "http://standards.ieee.org/downloads/11073/11073-"
        + "10207-2017/ServiceProvider";
    private TestClient client;
    private WsdlRetriever wsdlRetriever;

    @BeforeEach
    void setup() {
        client = getInjector().getInstance(TestClient.class);
        wsdlRetriever = client.getInjector().getInstance(WsdlRetriever.class);
    }

    @Test
    @DisplayName("R0042_0: An SDC SERVICE PROVIDER SHALL include the dpws:DiscoveryType attribute in its"
        + " portType WSDL description with a value resolving to “{http://standards.ieee.org/downloads/11073/11073-"
        + " 10207-2017}ServiceProvider”.")
    @TestIdentifier(EnabledTestConfig.GLUE_R0042_0)
    @TestDescription("Checks if all portTypes of the wsdl service descriptions of the DUT contain a discovery type"
        + " attribute with a value resolving to"
        + " \"{http://standards.ieee.org/downloads/11073/11073-10207-2017}ServiceProvider\".")
    void testRequirementR0042() throws NoTestData, IOException, TransportException, XPathExpressionException {
        final Map<String, List<String>> wsdlMap = wsdlRetriever.retrieveWsdls(client.getHostingServiceProxy());
        assertTestData(wsdlMap.entrySet(), "No WSDLs could be extracted from DUT");

        final var definitionsExtractor = new XPathExtractor("//" + wsdl("definitions"));
        final var portTypeExtractor = new XPathExtractor("//" + wsdl("portType"));
        for (final Map.Entry<String, List<String>> entry : wsdlMap.entrySet()) {
            final String service = entry.getKey();
            final List<String> wsdls = entry.getValue();
            for (final String wsdl : wsdls) {
                final var definitions = definitionsExtractor.extractFrom(wsdl);
                assertTestData(definitions, "no definitions in WSDL for service " + service);
                final var definitionAttributes = new HashMap<String, String>();
                definitions.forEach(definition -> {
                    final var attributes = definition.getAttributes();
                    for (int i = 0; i < attributes.getLength(); i++) {
                        final var node = attributes.item(i);
                        var localName = node.getLocalName();
                        if (XML_NAMESPACE_DEFINITION_ATTRIBUTE_PREFIX.equals(localName)) {
                            localName = DEFAULT_NAMESPACE;
                        }
                        final var value = node.getNodeValue();
                        definitionAttributes.put(localName, value);
                    }
                });

                final var portTypes = portTypeExtractor.extractFrom(wsdl);
                assertTestData(portTypes, "no portTypes in WSDL for service " + service);
                portTypes.forEach(portType -> {
                    final var discoveryType = portType.getAttributes().getNamedItem("dpws:DiscoveryType");
                    assertNotNull(discoveryType, String.format(
                        "No target namespace attribute defined for service %s", service));
                    final var nodeValue = discoveryType.getNodeValue();
                    final var nodeValueSplit = nodeValue.split(":");
                    var def = "";
                    if (nodeValueSplit.length == 2
                        && definitionAttributes.containsKey(nodeValueSplit[0])) {
                        def = definitionAttributes.get(nodeValueSplit[0]) + "/" + nodeValueSplit[1];
                    } else {
                        if (definitionAttributes.containsKey(DEFAULT_NAMESPACE)) {
                            def = definitionAttributes.get(DEFAULT_NAMESPACE) + "/" + nodeValue;
                        } else {
                            def = nodeValue;
                        }
                    }
                    assertEquals(DISCOVERY_TYPE, def, String.format("The value of dpws:DiscoveryType should be %s,"
                        + "but is %s, test failed.", DISCOVERY_TYPE, def));
                });
            }
        }
    }
}
