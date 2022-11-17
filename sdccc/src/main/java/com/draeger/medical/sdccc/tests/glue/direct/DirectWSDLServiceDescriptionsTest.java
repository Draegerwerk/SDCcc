/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.tests.glue.direct;

import static com.draeger.medical.sdccc.util.Constants.wsdl;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.draeger.medical.sdccc.configuration.EnabledTestConfig;
import com.draeger.medical.sdccc.sdcri.testclient.TestClient;
import com.draeger.medical.sdccc.tests.InjectorTestBase;
import com.draeger.medical.sdccc.tests.annotations.TestDescription;
import com.draeger.medical.sdccc.tests.annotations.TestIdentifier;
import com.draeger.medical.sdccc.tests.util.NoTestData;
import com.draeger.medical.sdccc.util.XPathExtractor;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import javax.xml.xpath.XPathExpressionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.somda.sdc.dpws.soap.exception.TransportException;
import org.somda.sdc.dpws.wsdl.WsdlRetriever;

/**
 * Glue Annex B wsdl tests.
 */
public class DirectWSDLServiceDescriptionsTest extends InjectorTestBase {
    private static final String TARGET_NAMESPACE = "http://standards.ieee.org/downloads/11073/11073-20701-2018";
    private TestClient client;
    private WsdlRetriever wsdlRetriever;

    @BeforeEach
    void setup() {
        client = getInjector().getInstance(TestClient.class);
        wsdlRetriever = client.getInjector().getInstance(WsdlRetriever.class);
    }

    @Test
    @TestIdentifier(EnabledTestConfig.GLUE_13)
    @TestDescription("Checks if the wsdl service descriptions of the DUT contain a target namespace attribute with the"
            + " value: http://standards.ieee.org/downloads/11073/11073-20701-2018.")
    void testRequirement13() throws NoTestData, IOException, TransportException, XPathExpressionException {
        final Map<String, List<String>> wsdlMap = wsdlRetriever.retrieveWsdls(client.getHostingServiceProxy());
        assertTestData(wsdlMap.entrySet(), "No WSDLs could be extracted from DUT");

        final var definitionsExtractor = new XPathExtractor("//" + wsdl("definitions"));
        final var importExtractor = new XPathExtractor("//" + wsdl("import"));
        for (final Map.Entry<String, List<String>> entry : wsdlMap.entrySet()) {
            final String service = entry.getKey();
            final List<String> wsdls = entry.getValue();
            for (final String wsdl : wsdls) {
                final var imports = importExtractor.extractFrom(wsdl);
                assertTrue(imports.isEmpty(), String.format("No import elements should be present in %s", service));

                final var definitions = definitionsExtractor.extractFrom(wsdl);
                assertTestData(definitions, "no definitions in WSDL for service " + service);

                definitions.forEach(definition -> {
                    final var targetNamespace = definition.getAttributes().getNamedItem("targetNamespace");
                    assertNotNull(
                            targetNamespace,
                            String.format("No target namespace attribute defined for service %s", service));
                    assertEquals(
                            TARGET_NAMESPACE,
                            targetNamespace.getNodeValue(),
                            String.format("Wrong target namespace attribute defined for service %s", service));
                });
            }
        }
    }
}
