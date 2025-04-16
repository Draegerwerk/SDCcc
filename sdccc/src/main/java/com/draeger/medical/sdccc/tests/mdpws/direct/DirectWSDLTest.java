/*
 * This Source Code Form is subject to the terms of the "SDCcc non-commercial use license".
 *
 * Copyright (C) 2025 Draegerwerk AG & Co. KGaA
 */

package com.draeger.medical.sdccc.tests.mdpws.direct;

import static com.draeger.medical.sdccc.util.Constants.dpws;
import static com.draeger.medical.sdccc.util.Constants.mdpws;
import static com.draeger.medical.sdccc.util.Constants.wsdl;
import static com.draeger.medical.sdccc.util.Constants.wsp;
import static com.draeger.medical.sdccc.util.Constants.wsu;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.draeger.medical.sdccc.configuration.EnabledTestConfig;
import com.draeger.medical.sdccc.sdcri.testclient.TestClient;
import com.draeger.medical.sdccc.tests.InjectorTestBase;
import com.draeger.medical.sdccc.tests.annotations.TestDescription;
import com.draeger.medical.sdccc.tests.annotations.TestIdentifier;
import com.draeger.medical.sdccc.tests.util.HostedServiceVerifier;
import com.draeger.medical.sdccc.tests.util.NoTestData;
import com.draeger.medical.sdccc.util.Constants;
import com.draeger.medical.sdccc.util.HttpClientUtil;
import com.draeger.medical.sdccc.util.MessageGeneratingUtil;
import com.draeger.medical.sdccc.util.XPathExtractor;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import javax.xml.namespace.QName;
import javax.xml.xpath.XPathExpressionException;
import org.apache.http.client.HttpClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.somda.sdc.biceps.model.message.GetMdibResponse;
import org.somda.sdc.biceps.model.message.ObjectFactory;
import org.somda.sdc.dpws.http.apache.ApacheTransportBindingFactoryImpl;
import org.somda.sdc.dpws.soap.SoapConstants;
import org.somda.sdc.dpws.soap.SoapMessage;
import org.somda.sdc.dpws.soap.SoapUtil;
import org.somda.sdc.dpws.soap.exception.MarshallingException;
import org.somda.sdc.dpws.soap.exception.SoapFaultException;
import org.somda.sdc.dpws.soap.exception.TransportException;
import org.somda.sdc.dpws.soap.interception.InterceptorException;
import org.somda.sdc.dpws.soap.wsaddressing.model.AttributedURIType;
import org.somda.sdc.dpws.wsdl.WsdlRetriever;
import org.somda.sdc.glue.common.ActionConstants;
import org.w3c.dom.Node;

/**
 * MDPWS WSDL tests (ch. 6.2).
 */
public class DirectWSDLTest extends InjectorTestBase {
    protected static final QName CUSTOM_NAMESPACE = new QName("ftp://namespace.example.com", "MyFunkyRoot");
    protected static final String MUST_UNDERSTAND_TEXT = "Youwillnotunderstandthis";
    protected static final String NO_FAULT_TEMPLATE = "A soap fault was expected in response to message with ";
    protected static final String WRONG_FAULT_TEMPLATE = " was expected in response to invalid message";
    protected static final String UNKNOWN_ACTION = "https://www.example.com/action-have-fault-consequences";
    protected static final String DUPLICATE_PORT_TYPE_TEMPLATE =
            "portType was defined in multiple WSDLs for" + " one service. Cannot determine which is correct.";

    private static final Logger LOG = LogManager.getLogger();

    private TestClient client;
    private SoapUtil soapUtil;
    private ObjectFactory messageModelFactory;
    private HttpClient httpClient;
    private HttpClientUtil httpClientUtil;
    private WsdlRetriever wsdlRetriever;
    private HostedServiceVerifier hostedServiceVerifier;

    @BeforeEach
    void setUp() {
        client = getInjector().getInstance(TestClient.class);
        httpClient = client.getInjector()
                .getInstance(ApacheTransportBindingFactoryImpl.class)
                .getClient();
        soapUtil = client.getInjector().getInstance(SoapUtil.class);
        messageModelFactory = client.getInjector().getInstance(ObjectFactory.class);
        httpClientUtil = client.getInjector().getInstance(HttpClientUtil.class);
        wsdlRetriever = client.getInjector().getInstance(WsdlRetriever.class);
        hostedServiceVerifier = getInjector().getInstance(HostedServiceVerifier.class);
    }

    @Test
    @TestIdentifier(EnabledTestConfig.MDPWS_R0012)
    @TestDescription("Sends a message triggering each fault to the DUT and evaluates whether"
            + " the correct faults were send as response."
            + " First it verifies that the GetService endpoint provided by the DUT only provides SDC services"
            + " which are described correctly in the WSDL. Unknown services or incorrect portTypes in the "
            + " WSDLs will fail."
            + " Sender fault is triggered by a GetMdib message with a GetMdDescription body, a GetMdib"
            + " message without a body, as well as a message with an unknown action."
            + " VersionMismatch is triggered by a GetMdib message where the Envelope namespace is wrong."
            + " MustUnderstand is triggered by a GetMdib message with a custom header element set"
            + " to mustUnderstand. Additionally sends one GetMdib message without malformation to"
            + " ensure the DUT doesn't just fault all the time.")
    void testRequirementR0012()
            throws InterceptorException, SoapFaultException, MarshallingException, TransportException {
        // verify the hosted service for GetService is matching the SDC Glue services.
        hostedServiceVerifier.verifyHostedService(MessageGeneratingUtil.getGetService(client));

        testSendGetMdibNoFault();
        testRequirementR0012SenderBodyMismatch();
        testRequirementR0012SenderBodyEmpty();
        testRequirementR0012SenderActionUnknown();
        testRequirementR0012VersionMismatch();
        testRequirementR0012MustUnderstand();
    }

    void testRequirementR0012SenderBodyMismatch() {
        final var senderFault = assertThrows(
                SoapFaultException.class,
                () -> sendSenderFaultActionBodyMismatch(client),
                NO_FAULT_TEMPLATE + "wrong body");
        final var faultCode = senderFault.getFault().getCode().getValue();
        LOG.debug("R0012: Sender fault message fault was: {}", faultCode);
        assertEquals(SoapConstants.SENDER, faultCode, "Sender fault" + WRONG_FAULT_TEMPLATE);
    }

    void testRequirementR0012SenderBodyEmpty() {
        final var senderFault = assertThrows(
                SoapFaultException.class, () -> sendSenderFaultEmptyBody(client), NO_FAULT_TEMPLATE + "empty body");
        final var faultCode = senderFault.getFault().getCode().getValue();
        LOG.debug("R0012: Sender fault message fault was: {}", faultCode);
        assertEquals(SoapConstants.SENDER, faultCode, "Sender fault" + WRONG_FAULT_TEMPLATE);
    }

    void testRequirementR0012SenderActionUnknown() {
        final var senderFault = assertThrows(
                SoapFaultException.class,
                () -> sendSenderFaultUnknownAction(client),
                NO_FAULT_TEMPLATE + "unknown action " + UNKNOWN_ACTION);
        final var faultCode = senderFault.getFault().getCode().getValue();
        LOG.debug("R0012: Sender fault message fault was: {}", faultCode);
        assertEquals(SoapConstants.SENDER, faultCode, "Sender fault" + WRONG_FAULT_TEMPLATE);
    }

    void testRequirementR0012VersionMismatch() {
        final var versionMismatch = assertThrows(
                SoapFaultException.class, () -> sendVersionMismatch(client), NO_FAULT_TEMPLATE + "VersionMismatch");
        final var faultCode = versionMismatch.getFault().getCode().getValue();
        LOG.debug("R0012: VersionMismatch message fault was: {}", faultCode);
        assertEquals(SoapConstants.VERSION_MISMATCH, faultCode, "VersionMismatch fault" + WRONG_FAULT_TEMPLATE);
    }

    void testRequirementR0012MustUnderstand() {
        final var mustUnderstand = assertThrows(
                SoapFaultException.class, () -> sendMustUnderstand(client), NO_FAULT_TEMPLATE + "MustUnderstand");
        final var faultCode = mustUnderstand.getFault().getCode().getValue();
        LOG.debug("R0012: MustUnderstand message fault was: {}", faultCode);
        assertEquals(SoapConstants.MUST_UNDERSTAND, faultCode, "MustUnderstand fault" + WRONG_FAULT_TEMPLATE);
    }

    /*
    This requirement is testable because transmitting these faults in the correct order is mandatory.
    Soap 1.2 Part 2
    2.8
    If a SOAP node receives a message whose version is not supported it MUST generate a fault (see 5.4 SOAP Fault)
     with a Value of Code set to "env:VersionMismatch". Any other malformation of the message construct MUST result in
     the generation of a fault with a Value of Code set to "env:Sender".

    Source: https://www.w3.org/TR/soap12-part2
    Copyright © 2007 World Wide Web Consortium, (MIT, ERCIM, Keio, Beihang).
    http://www.w3.org/Consortium/Legal/2015/doc-license
    Status of W3C document: W3C Recommendation

    -> from this follows that VersionMismatch faults MUST be generated for messages with unknown envelopes.

    SOAP 1.2 Part 1
    2.4
    Mandatory SOAP header blocks are presumed to somehow modify the semantics of other SOAP header blocks or SOAP body
     elements. Therefore, for every mandatory SOAP header block targeted to a node, that node MUST either process the
     header block or not process the SOAP message at all, and instead generate a fault (see 2.6 Processing SOAP
     Messages and 5.4 SOAP Fault).


    Source: https://www.w3.org/TR/soap12-part1
    Copyright © 2007 World Wide Web Consortium, (MIT, ERCIM, Keio, Beihang).
    http://www.w3.org/Consortium/Legal/2015/doc-license
    Status of W3C document: W3C Recommendation

    -> from this follows that an unknown element with mustUnderstand=true MUST generate a fault

    Soap 1.2 Part 2
    2.6
    A message may contain or result in multiple errors during processing. Except where the order of detection is
     specifically indicated (as in 2.4 Understanding SOAP Header Blocks), a SOAP node is at liberty to reflect any
     single fault from the set of possible faults prescribed for the errors encountered. The selection of a fault
     need not be predicated on the application of the "MUST", "SHOULD" or "MAY" keywords to the generation of the
     fault, with the exception that if one or more of the prescribed faults is qualified with the "MUST" keyword,
     then any one fault from the set of possible faults MUST be generated.

    Source: https://www.w3.org/TR/soap12-part2
    Copyright © 2007 World Wide Web Consortium, (MIT, ERCIM, Keio, Beihang).
    http://www.w3.org/Consortium/Legal/2015/doc-license
    Status of W3C document: W3C Recommendation

    -> from this follows that once a fault is required, one of the possible faults for an action must be generated.
       As VersionMismatch is mandatory for unknown envelopes, and mustUnderstand also requires to at least fault,
       the precedence of faults to be generated is the same as to be checked
     */
    @Test
    @TestIdentifier(EnabledTestConfig.MDPWS_R0013)
    @TestDescription(" First it verifies that the GetService endpoint provided by the DUT only provides SDC services"
            + " which are described correctly in the WSDL. Unknown services or incorrect portTypes in the "
            + " WSDLs will fail."
            + " Then it sends three messages to the DUT:"
            + " A message containing errors for all faults,"
            + " a message containing errors for a MustUnderstand and a sender fault and"
            + " a message containing only an error for a sender fault."
            + " Additionally sends one GetMdib message without malformation to"
            + " ensure the DUT doesn't just fault all the time.")
    void testRequirementR0013()
            throws InterceptorException, SoapFaultException, MarshallingException, TransportException {
        hostedServiceVerifier.verifyHostedService(MessageGeneratingUtil.getGetService(client));

        testSendGetMdibNoFault();
        testRequirementR0013All();
        testRequirementR0013MustUnderstandSender();
        testRequirementR0013Sender();
    }

    void testRequirementR0013All() {
        final var versionMismatch = assertThrows(
                SoapFaultException.class,
                () -> sendMessageAllFaults(client),
                NO_FAULT_TEMPLATE + "errors for VersionMismatch, MustUnderstand and Sender faults");
        final var faultCode = versionMismatch.getFault().getCode().getValue();
        LOG.debug("R0012: All message fault was: {}", faultCode);
        assertEquals(SoapConstants.VERSION_MISMATCH, faultCode, "VersionMismatch fault" + WRONG_FAULT_TEMPLATE);
    }

    void testRequirementR0013MustUnderstandSender() {
        final var mustUnderstand = assertThrows(
                SoapFaultException.class,
                () -> sendMustUnderstand(client, messageModelFactory.createGetMdDescription()),
                NO_FAULT_TEMPLATE + "MustUnderstand");
        final var faultCode = mustUnderstand.getFault().getCode().getValue();
        LOG.debug("R0013: MustUnderstand message fault was: {}", faultCode);
        assertEquals(SoapConstants.MUST_UNDERSTAND, faultCode, "MustUnderstand fault" + WRONG_FAULT_TEMPLATE);
    }

    void testRequirementR0013Sender() {
        final var senderFault = assertThrows(
                SoapFaultException.class,
                () -> sendSenderFaultActionBodyMismatch(client),
                NO_FAULT_TEMPLATE + "wrong body");
        final var faultCode = senderFault.getFault().getCode().getValue();
        LOG.debug("R0013: Sender fault message fault was: {}", faultCode);
        assertEquals(SoapConstants.SENDER, faultCode, "Sender fault" + WRONG_FAULT_TEMPLATE);
    }

    @Test
    @TestIdentifier(EnabledTestConfig.MDPWS_R0014)
    @TestDescription("Retrieves all WSDLs of the DUT and verifies that each portType has dpws:DiscoveryType set.")
    void testRequirementR0014() throws IOException, NoTestData, XPathExpressionException, TransportException {
        final Map<String, List<String>> wsdlMap = wsdlRetriever.retrieveWsdls(client.getHostingServiceProxy());
        LOG.debug("R0014: Retrieved WSDLs for {}", wsdlMap.values());

        assertTestData(wsdlMap.entrySet(), "No WSDLs could be extracted from DUT");

        final var portTypeExtractor = new XPathExtractor("//" + wsdl("portType"));

        for (final Map.Entry<String, List<String>> entry : wsdlMap.entrySet()) {
            final String service = entry.getKey();
            final List<String> wsdls = entry.getValue();
            for (final String wsdl : wsdls) {
                final var portTypes = portTypeExtractor.extractFrom(wsdl);
                assertTestData(portTypes, "no portTypes in WSDL for service " + service);

                portTypes.forEach(portType -> {
                    final var discoveryType = portType.getAttributes()
                            .getNamedItemNS(
                                    Constants.DPWS_DISCOVERY_TYPE.getNamespaceURI(),
                                    Constants.DPWS_DISCOVERY_TYPE.getLocalPart());
                    final var name = portType.getAttributes().getNamedItem("name");
                    assertNotNull(
                            discoveryType,
                            "Service " + service + " did not include the dpws:DiscoveryType" + " in its WSDL portType "
                                    + name);
                });
            }
        }
    }

    @Test
    @TestIdentifier(EnabledTestConfig.MDPWS_R0010)
    @TestDescription("Verifies that a policy containing the dpws:Profile is attached to a wsdl:binding with the"
            + " wsp:Optional attribute set to true and also not attached to a wsdl:portType, as it is explicitly"
            + " forbidden. Ports are not used and therefore not checked.")
    void testRequirementR0010() throws IOException, TransportException, NoTestData {
        final Map<String, List<String>> wsdlMap = wsdlRetriever.retrieveWsdls(client.getHostingServiceProxy());
        LOG.debug("R0010: Retrieved WSDLs for {}", wsdlMap.values());

        assertTestData(wsdlMap.entrySet(), "No WSDLs could be extracted from DUT");

        profileAssertion(dpws("Profile"), wsdlMap);
    }

    @Test
    @TestIdentifier(EnabledTestConfig.MDPWS_R0011)
    @TestDescription("Verifies that a policy containing the mdpws:Profile is attached to a wsdl:binding with the"
            + " wsp:Optional attribute set to true and also not attached to a wsdl:portType, as it is explicitly "
            + " forbidden. Ports are not used and therefore not checked.")
    void testRequirementR0011() throws IOException, TransportException, NoTestData {
        final Map<String, List<String>> wsdlMap = wsdlRetriever.retrieveWsdls(client.getHostingServiceProxy());
        LOG.debug("R0010: Retrieved WSDLs for {}", wsdlMap.values());

        assertTestData(wsdlMap.entrySet(), "No WSDLs could be extracted from DUT");

        profileAssertion(mdpws("Profile"), wsdlMap);
    }

    void profileAssertion(final String profileAssertion, final Map<String, List<String>> wsdlMap) {

        final String bindingQuery = "//" + wsdl("binding");
        final var bindingExtractor = new XPathExtractor(bindingQuery);

        final String portTypeQuery = "//" + wsdl("portType");
        final var portTypeExtractor = new XPathExtractor(portTypeQuery);

        for (final Map.Entry<String, List<String>> entry : wsdlMap.entrySet()) {
            final String serviceName = entry.getKey();
            final List<String> wsdlList = entry.getValue();

            for (final var wsdl : wsdlList) {

                // validate policy is at binding
                Collection<Node> bindings = null;
                try {
                    bindings = bindingExtractor.extractFrom(wsdl);
                } catch (final XPathExpressionException e) {
                    LOG.error(
                            "Unexpected error occurred while extracting bindings from wsdl of service {}: {}",
                            serviceName,
                            e.getMessage());
                    LOG.debug(
                            "Unexpected error occurred while extracting bindings from wsdl of service {}",
                            serviceName,
                            e);
                    fail("Unexpected error occurred while extracting bindings from wsdl", e);
                }

                // silence warning
                assert bindings != null;

                for (final Node binding : bindings) {
                    validateBinding(binding, profileAssertion, serviceName);
                }

                // validate nothing is at portType
                Collection<Node> portTypes = null;
                try {
                    portTypes = portTypeExtractor.extractFrom(wsdl);
                } catch (final XPathExpressionException e) {
                    LOG.error(
                            "Unexpected error occurred while extracting portType from wsdl of service {}: {}",
                            serviceName,
                            e.getMessage());
                    LOG.debug(
                            "Unexpected error occurred while extracting portType from wsdl of service {}",
                            serviceName,
                            e);
                    fail("Unexpected error occurred while extracting portType from wsdl", e);
                }

                // silence warning
                assert portTypes != null;

                for (final Node portType : portTypes) {
                    validatePortType(portType, profileAssertion, serviceName);
                }
            }
        }
    }

    void validateBinding(final Node binding, final String profileAssertion, final String serviceName) {
        final var bindingName = binding.getAttributes().getNamedItem("name");

        assertTrue(
                hasAssertionAttachedTo(binding, profileAssertion, serviceName),
                String.format(
                        "Service %s binding %s has no wsp:Policy attached for %s, this is not allowed",
                        serviceName, bindingName, profileAssertion));
    }

    void validatePortType(final Node portType, final String profileAssertion, final String serviceName) {
        final var portTypeName = portType.getAttributes().getNamedItem("name");

        assertFalse(
                hasAssertionAttachedTo(portType, profileAssertion, serviceName),
                String.format(
                        "Service %s portType %s has wsp:Policy attached for %s, this is not allowed",
                        serviceName, portTypeName, profileAssertion));
    }

    boolean hasAssertionAttachedTo(final Node node, final String profileAssertion, final String serviceName) {

        final var policyPath = wsp("Policy") + "/" + profileAssertion;
        final var assertionPath = policyPath + "[@" + wsp("Optional") + "='true']";

        final var assertionPathExtractor = new XPathExtractor(assertionPath);

        var result = false;

        try {
            final var policies = assertionPathExtractor.extractFrom(node);
            // node has policy with assertion as direct child
            if (policies.size() > 0) {
                result = true;
            }
        } catch (final XPathExpressionException e) {
            LOG.error("Unexpected error while extracting policy from service {}: {}", serviceName, e.getMessage());
            LOG.debug("Unexpected error while extracting policy from service {}", serviceName, e);
            fail("Unexpected error while extracting policy from service " + serviceName, e);
        }

        // otherwise it needs to have a policyReference
        if (!result) {
            result = hasAssertionAttachedToPolicyReference(profileAssertion, node, serviceName);
        }

        // TODO: implement support for wsp:PolicyURIs (https://github.com/Draegerwerk/SDCcc/issues/3)
        // wsp:PolicyURIs are currently unsupported and must fail (see https://github.com/Draegerwerk/SDCcc/issues/4)
        final Node policyURIs = node.getAttributes().getNamedItemNS(Constants.WSP_NAMESPACE, Constants.WSP_POLICY_URIS);
        assertNull(
                policyURIs, Constants.WSP_POLICY_URIS + " element attached to node, these are currently unsupported.");
        return result;
    }

    private boolean hasAssertionAttachedToPolicyReference(
            final String profileAssertion, final Node node, final String serviceName) {
        final String policyReferencePath = ".//" + wsp("PolicyReference[@URI]");
        final var policyReferencePathExtractor = new XPathExtractor(policyReferencePath);

        var result = false;

        Collection<Node> references = null;
        try {
            references = policyReferencePathExtractor.extractFrom(node);
        } catch (final XPathExpressionException e) {
            LOG.error(
                    "Unexpected error while extracting policy reference from service {}: {}",
                    serviceName,
                    e.getMessage());
            LOG.debug("Unexpected error while extracting policy reference from service {}", serviceName, e);
            fail("Unexpected error while extracting policy reference from service " + serviceName, e);
        }

        if (references == null || references.size() == 0) {
            return false;
        }

        // validate policy is match
        final var parentNode = node.getParentNode();

        for (final Node reference : references) {
            final var uriNode = reference.getAttributes().getNamedItem("URI");
            if (uriNode == null) {
                continue;
            }
            var uri = uriNode.getNodeValue();
            // remove leading #
            if (uri.charAt(0) == '#') {
                uri = uri.substring(1);
            }

            final var referencedPolicyPath =
                    "//" + wsp("Policy") + "[@" + wsu("Id") + "='" + uri + "']/" + profileAssertion;
            final var assertionPathId = referencedPolicyPath + "[@" + wsp("Optional") + "='true']";
            final var assertionPathIdExtractor = new XPathExtractor(assertionPathId);

            try {
                final var nodes = assertionPathIdExtractor.extractFrom(parentNode);
                if (nodes.size() > 0) {
                    result = true;
                    break;
                }
            } catch (final XPathExpressionException e) {
                LOG.error(
                        "Unexpected error while extracting policy reference from service {}: {}",
                        serviceName,
                        e.getMessage());
                LOG.debug("Unexpected error while extracting policy reference from service {}", serviceName, e);
                fail("Unexpected error while extracting policy reference from service " + serviceName, e);
            }
        }

        return result;
    }

    /**
     * Sends a GetMdib message to the DUT using {@linkplain #client}, expects a response
     * containing a GetMdibResponse body.
     *
     * @throws SoapFaultException   if a SOAP fault comes up during processing.
     * @throws TransportException   if transport-related exceptions come up during processing.
     *                              This will hinder the response from being sent.
     * @throws MarshallingException if any exception occurs during marshalling or unmarshalling of SOAP messages.
     * @throws InterceptorException if one of the interceptors pops up with an error.
     */
    void testSendGetMdibNoFault()
            throws InterceptorException, SoapFaultException, MarshallingException, TransportException {
        final var getServiceOpt = MessageGeneratingUtil.getGetService(client);
        if (getServiceOpt.isEmpty()) {
            fail("No get service present");
        }
        final var getMdibRequest =
                soapUtil.createMessage(ActionConstants.ACTION_GET_MDIB, messageModelFactory.createGetMdib());
        final SoapMessage response;
        try {
            response = getServiceOpt.orElseThrow().sendRequestResponse(getMdibRequest);
        } catch (final SoapFaultException | MarshallingException | TransportException | InterceptorException e) {
            LOG.error("An unexpected error occurred while trying to send a GetMdib request without malformation.", e);
            fail("An unexpected error occurred while trying to send a GetMdib request without malformation."
                    + " Message: " + e.getMessage());
            // unreachable, silence warnings
            throw e;
        }

        final var responseBody = soapUtil.getBody(response, GetMdibResponse.class);
        assertTrue(responseBody.isPresent(), "Invalid response to valid GetMdib received.");
    }

    /**
     * Sends a GetMdib request with a modified namespace for the SOAP envelope, a custom MustUnderstand element
     * and a mismatching body element.
     *
     * @param testClient to send the message on
     * @throws SoapFaultException if a SOAP fault comes up during processing.
     * @throws TransportException if transport-related exceptions come up during processing.
     *                            This will hinder the response from being sent.
     */
    protected void sendMessageAllFaults(final TestClient testClient) throws SoapFaultException, TransportException {
        final var getServiceOpt = MessageGeneratingUtil.getGetService(testClient);
        if (getServiceOpt.isEmpty()) {
            fail("No get service present");
        }

        byte[] message = null;
        try (final var msgStream = DirectWSDLTest.class.getResourceAsStream("DirectWSDLTest/GetMdibAllFaults.xml")) {
            assertNotNull(msgStream);
            message = msgStream.readAllBytes();
        } catch (final IOException e) {
            fail("Could not load message file to send for version mismatch", e);
        }

        final var getAddress = getServiceOpt.orElseThrow().getActiveEprAddress();

        httpClientUtil.postMessage(httpClient, getAddress, message);
    }

    /**
     * Sends a GetMdib request with a GetMdDescription element in the body, violating the WSDL.
     *
     * @param testClient to send on
     * @throws SoapFaultException   if a SOAP fault comes up during processing.
     * @throws TransportException   if transport-related exceptions come up during processing.
     *                              This will hinder the response from being sent.
     * @throws MarshallingException if any exception occurs during marshalling or unmarshalling of SOAP messages.
     * @throws InterceptorException if one of the interceptors pops up with an error.
     */
    protected void sendSenderFaultActionBodyMismatch(final TestClient testClient)
            throws InterceptorException, SoapFaultException, MarshallingException, TransportException {

        final var getServiceOpt = MessageGeneratingUtil.getGetService(testClient);
        if (getServiceOpt.isEmpty()) {
            fail("No get service present");
        }
        final var getMdibRequest =
                soapUtil.createMessage(ActionConstants.ACTION_GET_MDIB, messageModelFactory.createGetMdDescription());
        getServiceOpt.orElseThrow().sendRequestResponse(getMdibRequest);
    }

    /**
     * Sends a GetMdib request with an empty body, violating the WSDL.
     *
     * @param testClient to send on
     * @throws SoapFaultException   if a SOAP fault comes up during processing.
     * @throws TransportException   if transport-related exceptions come up during processing.
     *                              This will hinder the response from being sent.
     * @throws MarshallingException if any exception occurs during marshalling or unmarshalling of SOAP messages.
     * @throws InterceptorException if one of the interceptors pops up with an error.
     */
    protected void sendSenderFaultEmptyBody(final TestClient testClient)
            throws InterceptorException, SoapFaultException, MarshallingException, TransportException {

        final var getServiceOpt = MessageGeneratingUtil.getGetService(testClient);
        if (getServiceOpt.isEmpty()) {
            fail("No get service present");
        }
        final var getMdibRequest = soapUtil.createMessage(ActionConstants.ACTION_GET_MDIB);
        getServiceOpt.orElseThrow().sendRequestResponse(getMdibRequest);
    }

    /**
     * Sends a request with an unknown action, violating the WSDL.
     *
     * @param testClient to send on
     * @throws SoapFaultException   if a SOAP fault comes up during processing.
     * @throws TransportException   if transport-related exceptions come up during processing.
     *                              This will hinder the response from being sent.
     * @throws MarshallingException if any exception occurs during marshalling or unmarshalling of SOAP messages.
     * @throws InterceptorException if one of the interceptors pops up with an error.
     */
    protected void sendSenderFaultUnknownAction(final TestClient testClient)
            throws InterceptorException, SoapFaultException, MarshallingException, TransportException {

        final var getServiceOpt = MessageGeneratingUtil.getGetService(testClient);
        if (getServiceOpt.isEmpty()) {
            fail("No get service present");
        }
        final var getMdibRequest = soapUtil.createMessage(UNKNOWN_ACTION);
        getServiceOpt.orElseThrow().sendRequestResponse(getMdibRequest);
    }

    /**
     * Sends a GetMdib request with a modified namespace for the SOAP envelope.
     *
     * @param testClient to send the message on
     * @throws SoapFaultException if a SOAP fault comes up during processing.
     * @throws TransportException if transport-related exceptions come up during processing.
     *                            This will hinder the response from being sent.
     */
    protected void sendVersionMismatch(final TestClient testClient) throws SoapFaultException, TransportException {

        final var getServiceOpt = MessageGeneratingUtil.getGetService(testClient);
        if (getServiceOpt.isEmpty()) {
            fail("No get service present");
        }

        byte[] message = null;
        try (final var msgStream =
                DirectWSDLTest.class.getResourceAsStream("DirectWSDLTest/GetMdibVersionMismatch.xml")) {
            assertNotNull(msgStream);
            message = msgStream.readAllBytes();
        } catch (final IOException e) {
            fail("Could not load message file to send for version mismatch", e);
        }

        final var getAddress = getServiceOpt.orElseThrow().getActiveEprAddress();

        httpClientUtil.postMessage(httpClient, getAddress, message);
    }

    /**
     * Sends a GetMdib request with a custom soap header entry set to MustUnderstand.
     *
     * @param testClient to send on
     * @throws SoapFaultException   if a SOAP fault comes up during processing.
     * @throws TransportException   if transport-related exceptions come up during processing.
     *                              This will hinder the response from being sent.
     * @throws MarshallingException if any exception occurs during marshalling or unmarshalling of SOAP messages.
     * @throws InterceptorException if one of the interceptors pops up with an error.
     */
    protected void sendMustUnderstand(final TestClient testClient)
            throws InterceptorException, SoapFaultException, MarshallingException, TransportException {
        sendMustUnderstand(testClient, messageModelFactory.createGetMdib());
    }

    /**
     * Sends a GetMdib request with a custom soap header entry set to MustUnderstand.
     *
     * @param testClient  to send on
     * @param messageBody body to send with the message
     * @throws SoapFaultException   if a SOAP fault comes up during processing.
     * @throws TransportException   if transport-related exceptions come up during processing.
     *                              This will hinder the response from being sent.
     * @throws MarshallingException if any exception occurs during marshalling or unmarshalling of SOAP messages.
     * @throws InterceptorException if one of the interceptors pops up with an error.
     */
    protected void sendMustUnderstand(final TestClient testClient, final Object messageBody)
            throws InterceptorException, SoapFaultException, MarshallingException, TransportException {

        final var getServiceOpt = MessageGeneratingUtil.getGetService(testClient);
        if (getServiceOpt.isEmpty()) {
            fail("No get service present");
        }
        final var getService = getServiceOpt.orElseThrow();
        final var getMdibRequest = soapUtil.createMessage(ActionConstants.ACTION_GET_MDIB, messageBody);
        final var mustUnderstandNode = createMustUnderstandNode();
        getMdibRequest.getOriginalEnvelope().getHeader().getAny().add(mustUnderstandNode);
        getService.sendRequestResponse(getMdibRequest);
    }

    private jakarta.xml.bind.JAXBElement<AttributedURIType> createMustUnderstandNode() {
        final var entry = new AttributedURIType();
        entry.setValue(MUST_UNDERSTAND_TEXT);
        entry.getOtherAttributes().put(Constants.MUST_UNDERSTAND_ATTRIBUTE, "true");
        return new jakarta.xml.bind.JAXBElement<>(CUSTOM_NAMESPACE, AttributedURIType.class, entry);
    }
}
