/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2023 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.draeger.medical.sdccc.sdcri.testclient.TestClientUtil;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import javax.xml.namespace.QName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.somda.sdc.dpws.helper.JaxbMarshalling;
import org.somda.sdc.dpws.wsdl.WsdlMarshalling;
import org.somda.sdc.glue.common.CommonConstants;
import org.somda.sdc.glue.common.WsdlConstants;
import org.somda.sdc.glue.provider.SdcDevice;

/**
 * Unit tests for the {@linkplain WsdlParser}.
 */
public class WsdlParserTest {
    private static final Duration MAX_WAIT = Duration.ofSeconds(10);
    private JaxbMarshalling jaxbMarshalling;
    private WsdlMarshalling wsdlMarshalling;
    private WsdlParser wsdlParser;

    @BeforeEach
    void setUp() throws TimeoutException {
        final var injector = TestClientUtil.createClientInjector();

        jaxbMarshalling = injector.getInstance(JaxbMarshalling.class);
        wsdlMarshalling = injector.getInstance(WsdlMarshalling.class);

        jaxbMarshalling.startAsync().awaitRunning(MAX_WAIT);
        wsdlMarshalling.startAsync().awaitRunning(MAX_WAIT);

        wsdlParser = injector.getInstance(WsdlParser.class);
    }

    @AfterEach
    void tearDown() throws TimeoutException {
        jaxbMarshalling.stopAsync().awaitTerminated(MAX_WAIT);
        wsdlMarshalling.stopAsync().awaitTerminated(MAX_WAIT);
    }

    /**
     * Tests whether parsing and validating the SDCri WSDLs passes.
     * Additionally tests that altering the parsing result fails the validation.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testParsePortTypes() throws Exception {

        final var numHighPriorityServices = 7;
        final var highPrioWsdl = "wsdl/IEEE11073-20701-HighPriority-Services.wsdl";
        final var numLowPriorityServices = 2;
        final var lowPrioWsdl = "wsdl/IEEE11073-20701-LowPriority-Services.wsdl";
        {
            final String wsdl;
            try (final var wsdlStream = SdcDevice.class.getClassLoader().getResourceAsStream(highPrioWsdl)) {
                assertNotNull(wsdlStream);
                wsdl = new String(wsdlStream.readAllBytes(), StandardCharsets.UTF_8);
            }
            assertFalse(wsdl.isBlank());

            final var wsdlData = wsdlParser.parseWsdlPortTypes(wsdl);
            assertEquals(numHighPriorityServices, wsdlData.size(), "Parser did not retrieve all portTypes");

            wsdlData.forEach((service, operations) -> assertTrue(
                    WsdlValidator.isEqualService(service, operations),
                    "Service " + service + " does not match Service definition in SDC Glue Annex B"));

            final var getService = wsdlData.get(WsdlConstants.PORT_TYPE_GET_QNAME);
            assertNotNull(
                    getService.remove(new QName(CommonConstants.NAMESPACE_SDC, WsdlConstants.OPERATION_GET_MDIB)));

            // service is no longer valid
            assertFalse(
                    WsdlValidator.isEqualService(WsdlConstants.PORT_TYPE_GET_QNAME, getService),
                    "Service should have been invalid, but wasn't");
        }
        {
            final String wsdl;

            try (final var wsdlStream = SdcDevice.class.getClassLoader().getResourceAsStream(lowPrioWsdl)) {
                assertNotNull(wsdlStream);
                wsdl = new String(wsdlStream.readAllBytes(), StandardCharsets.UTF_8);
            }
            assertFalse(wsdl.isBlank());

            final var wsdlData = wsdlParser.parseWsdlPortTypes(wsdl);
            assertEquals(numLowPriorityServices, wsdlData.size(), "Parser did not retrieve all portTypes");

            wsdlData.forEach((service, operations) -> assertTrue(
                    WsdlValidator.isEqualService(service, operations),
                    "Service " + service + " does not match Service definition in SDC Glue Annex B"));
        }
    }

    /**
     * Tests using the method parsePortTypes to parse request-response operations.
     * @throws jakarta.xml.bind.JAXBException when this exception is thrown.
     */
    @Test
    public void testParsePortTypesGood() throws jakarta.xml.bind.JAXBException {
        final String wsdl = "<wsdl:definitions xmlns:dpws=\"http://docs.oasis-open.org/ws-dd/ns/dpws/2009/01\" "
                + "xmlns:mdpws=\"http://standards.ieee.org/downloads/11073/11073-20702-2016\" "
                + "xmlns:msg=\"http://standards.ieee.org/downloads/11073/11073-10207-2017/message\" "
                + "xmlns:s12=\"http://schemas.xmlsoap.org/wsdl/soap12/\" "
                + "xmlns:tns=\"http://standards.ieee.org/downloads/11073/11073-20701-2018\" "
                + "xmlns:wsdl=\"http://schemas.xmlsoap.org/wsdl/\" "
                + "xmlns:wsp=\"http://www.w3.org/ns/ws-policy\" "
                + "xmlns:ns=\"http://standards.ieee.org/downloads/11073/11073-10207-2017/extension\" "
                + "xmlns:ns1=\"http://standards.ieee.org/downloads/11073/11073-10207-2017/participant\" "
                + "xmlns:dt=\"http://standards.ieee.org/downloads/11073/11073-10207-2017\" "
                + "xmlns:wse=\"http://schemas.xmlsoap.org/ws/2004/08/eventing\" "
                + "targetNamespace=\"http://standards.ieee.org/downloads/11073/11073-20701-2018\">\n"
                + "\t<wsdl:message name=\"GetMdib\">\n"
                + "\t\t<wsdl:part name=\"parameters\" element=\"msg:GetMdib\"/>\n"
                + "\t</wsdl:message>\n"
                + "\t<wsdl:message name=\"GetMdibResponse\">\n"
                + "\t\t<wsdl:part name=\"parameters\" element=\"msg:GetMdibResponse\"/>\n"
                + "\t</wsdl:message>\n"
                + "\t<wsdl:portType name=\"GetService\" dpws:DiscoveryType=\"dt:ServiceProvider\">\n"
                + "\t\t<wsdl:operation name=\"GetMdib\">\n"
                + "\t\t\t<wsdl:input message=\"tns:GetMdib\"/>\n"
                + "\t\t\t<wsdl:output message=\"tns:GetMdibResponse\"/>\n"
                + "\t\t</wsdl:operation>\n"
                + "\t</wsdl:portType>\n"
                + "</wsdl:definitions>\n";

        final Map<QName, Map<QName, WsdlParser.OperationArguments>> result = wsdlParser.parseWsdlPortTypes(wsdl);

        final String namespaceURI = "http://standards.ieee.org/downloads/11073/11073-20701-2018";
        final String msg = "http://standards.ieee.org/downloads/11073/11073-10207-2017/message";
        final var expected = Map.of(
                new QName(namespaceURI, "GetService"),
                Map.of(
                        new QName(namespaceURI, "GetMdib"),
                        new WsdlParser.OperationArguments(
                                new WsdlParser.OperationArgument(
                                        WsdlParser.ArgumentType.INPUT, new QName(msg, "GetMdib")),
                                new WsdlParser.OperationArgument(
                                        WsdlParser.ArgumentType.OUTPUT, new QName(msg, "GetMdibResponse")))));
        assertEquals(expected, result);
    }

    /**
     * Tests using the method parsePortTypes to parse solicit-response operations.
     * @throws jakarta.xml.bind.JAXBException when this exception is thrown.
     */
    @Test
    public void testParsePortTypesGoodInputAndOutputSwitched() throws jakarta.xml.bind.JAXBException {
        final String wsdl = "<wsdl:definitions xmlns:dpws=\"http://docs.oasis-open.org/ws-dd/ns/dpws/2009/01\" "
                + "xmlns:mdpws=\"http://standards.ieee.org/downloads/11073/11073-20702-2016\" "
                + "xmlns:msg=\"http://standards.ieee.org/downloads/11073/11073-10207-2017/message\" "
                + "xmlns:s12=\"http://schemas.xmlsoap.org/wsdl/soap12/\" "
                + "xmlns:tns=\"http://standards.ieee.org/downloads/11073/11073-20701-2018\" "
                + "xmlns:wsdl=\"http://schemas.xmlsoap.org/wsdl/\" "
                + "xmlns:wsp=\"http://www.w3.org/ns/ws-policy\" "
                + "xmlns:ns=\"http://standards.ieee.org/downloads/11073/11073-10207-2017/extension\" "
                + "xmlns:ns1=\"http://standards.ieee.org/downloads/11073/11073-10207-2017/participant\" "
                + "xmlns:dt=\"http://standards.ieee.org/downloads/11073/11073-10207-2017\" "
                + "xmlns:wse=\"http://schemas.xmlsoap.org/ws/2004/08/eventing\" "
                + "targetNamespace=\"http://standards.ieee.org/downloads/11073/11073-20701-2018\">\n"
                + "\t<wsdl:message name=\"GetMdib\">\n"
                + "\t\t<wsdl:part name=\"parameters\" element=\"msg:GetMdib\"/>\n"
                + "\t</wsdl:message>\n"
                + "\t<wsdl:message name=\"GetMdibResponse\">\n"
                + "\t\t<wsdl:part name=\"parameters\" element=\"msg:GetMdibResponse\"/>\n"
                + "\t</wsdl:message>\n"
                + "\t<wsdl:portType name=\"GetService\" dpws:DiscoveryType=\"dt:ServiceProvider\">\n"
                + "\t\t<wsdl:operation name=\"GetMdib\">\n"
                + "\t\t\t<wsdl:output message=\"tns:GetMdibResponse\"/>\n"
                + "\t\t\t<wsdl:input message=\"tns:GetMdib\"/>\n"
                + "\t\t</wsdl:operation>\n"
                + "\t</wsdl:portType>\n"
                + "</wsdl:definitions>\n";

        final Map<QName, Map<QName, WsdlParser.OperationArguments>> result = wsdlParser.parseWsdlPortTypes(wsdl);

        final String namespaceURI = "http://standards.ieee.org/downloads/11073/11073-20701-2018";
        final String msg = "http://standards.ieee.org/downloads/11073/11073-10207-2017/message";
        final var expected = Map.of(
                new QName(namespaceURI, "GetService"),
                Map.of(
                        new QName(namespaceURI, "GetMdib"),
                        new WsdlParser.OperationArguments(
                                new WsdlParser.OperationArgument(
                                        WsdlParser.ArgumentType.OUTPUT, new QName(msg, "GetMdibResponse")),
                                new WsdlParser.OperationArgument(
                                        WsdlParser.ArgumentType.INPUT, new QName(msg, "GetMdib")))));
        assertEquals(expected, result);
    }
}
