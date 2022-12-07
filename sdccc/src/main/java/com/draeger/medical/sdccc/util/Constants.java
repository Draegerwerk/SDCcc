/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;
import org.somda.sdc.biceps.common.CommonConstants;
import org.somda.sdc.dpws.DpwsConstants;
import org.somda.sdc.dpws.soap.SoapConstants;
import org.somda.sdc.dpws.soap.wsaddressing.WsAddressingConstants;
import org.somda.sdc.dpws.soap.wsdiscovery.WsDiscoveryConstants;
import org.somda.sdc.dpws.soap.wseventing.WsEventingConstants;
import org.somda.sdc.dpws.soap.wsmetadataexchange.WsMetadataExchangeConstants;
import org.somda.sdc.dpws.soap.wstransfer.WsTransferConstants;
import org.somda.sdc.glue.common.WsdlConstants;

/**
 * Constants used for SDCcc.
 */
public final class Constants {

    public static final String ENABLE_SETTING_POSTFIX = "Enable";
    public static final String DEVICE_EPR_POSTFIX = "DeviceEpr";
    public static final String FIELD_ACCESS_ERROR_BASE_MESSAGE = "Error while accessing field {}";

    /*
    MDPWS constants
    */

    // MDPWS Annex A
    public static final int MAX_LARGE_ENVELOPE_SIZE = 4096 * 1000;

    // query for all LocalizedText elements with a @Ref attribute
    public static final String REF_ELEMENT_QUERY = "//" + pm("CodingSystemName[@Ref]")
            + "| //" + pm("ConceptDescription[@Ref]")
            + "| //" + pm("IdentifierName[@Ref]")
            + "| //" + pm("Manufacturer[@Ref]")
            + "| //" + pm("ModelName[@Ref]")
            + "| //" + pm("Description[@Ref]")
            + "| //" + msg("ErrorInfo[@Ref]")
            + "| //" + msg("InvocationErrorMessage[@Ref]")
            + "| //" + msg("Text[@Ref]")
            + "| //" + pm("Documentation[@Ref]")
            + "| //" + pm("Label[@Ref]");

    /*
    SOAP constants
    */
    public static final QName MUST_UNDERSTAND_ATTRIBUTE = new QName(SoapConstants.NAMESPACE, "mustUnderstand");

    /*
    WSDL constants
    */
    public static final String WSDL_NAMESPACE_PREFIX = "wsdl";
    public static final String WSDL_NAMESPACE = WsMetadataExchangeConstants.DIALECT_WSDL;

    public static final String WSP_NAMESPACE_PREFIX = "wsp";
    public static final String WSP_NAMESPACE = "http://www.w3.org/ns/ws-policy";
    public static final String WSP_POLICY_URIS = "PolicyURIs";

    public static final String WSU_NAMESPACE_PREFIX = "wsu";
    public static final String WSU_NAMESPACE =
            "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd";

    public static final String MDPWS_NAMESPACE_PREFIX = "mdpws";
    public static final String MDPWS_NAMESPACE = org.somda.sdc.mdpws.common.CommonConstants.NAMESPACE;

    public static final QName WSDL_INPUT = new QName(WSDL_NAMESPACE, "input");
    public static final QName WSDL_OUTPUT = new QName(WSDL_NAMESPACE, "output");

    /*
    DPWS constants
    */
    public static final QName DPWS_DISCOVERY_TYPE = new QName(DpwsConstants.NAMESPACE, "DiscoveryType");

    /*
    TLS Constants
     */
    public static final String KEYSTORE = "keystore.pkcs12";
    public static final String TRUSTSTORE = "truststore.pkcs12";
    public static final String PARTICIPANT_PRIVATE = "participant_private.pem";
    public static final String PARTICIPANT_PUBLIC = "participant_public.pem";
    public static final String CA_CERTIFICATE = "ca_certificate.pem";

    /*
    HTTP constants
    */
    public static final String HTTP_SCHEME = "http";
    public static final String HTTPS_SCHEME = "https";

    public static final int HTTP_MULTIPLE_CHOICES = 300;
    public static final int HTTP_PAYLOAD_TOO_LARGE = 413;
    public static final int HTTP_INTERNAL_SERVER_ERROR = 500;

    public static final String HTTP_APPLICATION_XML = "application/xml";
    public static final String HTTP_APPLICATION_SOAP_XML = "application/soap+xml";
    public static final String HTTP_MULTIPART_PREFIX = "Multipart/";
    public static final String HTTP_MULTIPART_RELATED = "Multipart/Related";

    public static final String HTTP_APPLICATION_EXI = "application/exi";
    /*
    BICEPS constants
    */
    public static final int HANDLE_UNICODE_LOWER_BOUND = 0x0021;
    public static final int HANDLE_UNICODE_UPPER_BOUND = 0x10ffff;
    public static final int HANDLE_UNICODE_EXCEPTION = 0xfffd;
    public static final QName MDIB_VERSION = new QName("MdibVersion");
    public static final QName SEQUENCE_ID = new QName("SequenceId");

    /*
    XML constants
    */
    public static final NamespaceContext NAMESPACES;

    static {
        final Map<String, String> namespaceMap = new HashMap<>();

        namespaceMap.put(SoapConstants.NAMESPACE_XSI_PREFIX, SoapConstants.NAMESPACE_XSI);
        namespaceMap.put(WsAddressingConstants.NAMESPACE_PREFIX, WsAddressingConstants.NAMESPACE);
        namespaceMap.put(WsEventingConstants.NAMESPACE_PREFIX, WsEventingConstants.NAMESPACE);
        namespaceMap.put(WsDiscoveryConstants.NAMESPACE_PREFIX, WsDiscoveryConstants.NAMESPACE);
        namespaceMap.put(WsMetadataExchangeConstants.NAMESPACE_PREFIX, WsMetadataExchangeConstants.NAMESPACE);
        namespaceMap.put(WsTransferConstants.NAMESPACE_PREFIX, WsTransferConstants.NAMESPACE);
        namespaceMap.put(DpwsConstants.NAMESPACE_PREFIX, DpwsConstants.NAMESPACE);
        namespaceMap.put(SoapConstants.NAMESPACE_PREFIX, SoapConstants.NAMESPACE);
        namespaceMap.put(WSDL_NAMESPACE_PREFIX, WSDL_NAMESPACE);
        namespaceMap.put(WSP_NAMESPACE_PREFIX, WSP_NAMESPACE);
        namespaceMap.put(WSU_NAMESPACE_PREFIX, WSU_NAMESPACE);

        namespaceMap.put(CommonConstants.NAMESPACE_MESSAGE_PREFIX, CommonConstants.NAMESPACE_MESSAGE);
        namespaceMap.put(CommonConstants.NAMESPACE_PARTICIPANT_PREFIX, CommonConstants.NAMESPACE_PARTICIPANT);
        namespaceMap.put(CommonConstants.NAMESPACE_EXTENSION_PREFIX, CommonConstants.NAMESPACE_EXTENSION);
        namespaceMap.put(MDPWS_NAMESPACE_PREFIX, MDPWS_NAMESPACE);

        namespaceMap.put(
                org.somda.sdc.glue.common.CommonConstants.NAMESPACE_SDC_PREFIX,
                org.somda.sdc.glue.common.CommonConstants.NAMESPACE_SDC);

        NAMESPACES = new SimpleNamespaceContext(namespaceMap);
    }

    /*
    Body QNames
    */

    // WS-Discovery
    public static final QName WSD_HELLO_BODY = new QName(WsDiscoveryConstants.NAMESPACE, "Hello");

    public static final QName WSD_PROBE_BODY = new QName(WsDiscoveryConstants.NAMESPACE, "Probe");
    public static final QName WSD_PROBE_MATCHES_BODY = new QName(WsDiscoveryConstants.NAMESPACE, "ProbeMatches");

    public static final QName WSD_RESOLVE_BODY = new QName(WsDiscoveryConstants.NAMESPACE, "Resolve");
    public static final QName WSD_RESOLVE_MATCHES_BODY = new QName(WsDiscoveryConstants.NAMESPACE, "ResolveMatches");

    // Message Model
    public static final QName MSG_GET_MDIB =
            new QName(CommonConstants.NAMESPACE_MESSAGE, WsdlConstants.OPERATION_GET_MDIB);
    public static final QName MSG_GET_MDIB_RESPONSE =
            new QName(CommonConstants.NAMESPACE_MESSAGE, WsdlConstants.OPERATION_GET_MDIB + "Response");

    // Message Model: Report
    public static final QName MSG_SYSTEM_ERROR_REPORT =
            new QName(CommonConstants.NAMESPACE_MESSAGE, WsdlConstants.OPERATION_SYSTEM_ERROR_REPORT);

    // Message Model: Episodic Reports
    public static final QName MSG_EPISODIC_ALERT_REPORT =
            new QName(CommonConstants.NAMESPACE_MESSAGE, WsdlConstants.OPERATION_EPISODIC_ALERT_REPORT);
    public static final QName MSG_EPISODIC_COMPONENT_REPORT =
            new QName(CommonConstants.NAMESPACE_MESSAGE, WsdlConstants.OPERATION_EPISODIC_COMPONENT_REPORT);
    public static final QName MSG_EPISODIC_CONTEXT_REPORT =
            new QName(CommonConstants.NAMESPACE_MESSAGE, WsdlConstants.OPERATION_EPISODIC_CONTEXT_REPORT);
    public static final QName MSG_EPISODIC_METRIC_REPORT =
            new QName(CommonConstants.NAMESPACE_MESSAGE, WsdlConstants.OPERATION_EPISODIC_METRIC_REPORT);
    public static final QName MSG_EPISODIC_OPERATIONAL_STATE_REPORT =
            new QName(CommonConstants.NAMESPACE_MESSAGE, WsdlConstants.OPERATION_EPISODIC_OPERATIONAL_STATE_REPORT);
    public static final QName MSG_DESCRIPTION_MODIFICATION_REPORT =
            new QName(CommonConstants.NAMESPACE_MESSAGE, WsdlConstants.OPERATION_DESCRIPTION_MODIFICATION_REPORT);
    public static final QName MSG_OPERATION_INVOKED_REPORT =
            new QName(CommonConstants.NAMESPACE_MESSAGE, WsdlConstants.OPERATION_OPERATION_INVOKED_REPORT);

    // Message Model: Streaming Reports
    public static final QName MSG_OBSERVED_VALUE_STREAM =
            new QName(CommonConstants.NAMESPACE_MESSAGE, WsdlConstants.OPERATION_OBSERVED_VALUE_STREAM);
    public static final QName MSG_WAVEFORM_STREAM =
            new QName(CommonConstants.NAMESPACE_MESSAGE, WsdlConstants.OPERATION_WAVEFORM_STREAM);

    // Periodic*Reports are not supported
    public static final List<QName> RELEVANT_REPORT_BODIES = List.of(
            Constants.MSG_EPISODIC_ALERT_REPORT,
            Constants.MSG_EPISODIC_COMPONENT_REPORT,
            Constants.MSG_EPISODIC_CONTEXT_REPORT,
            Constants.MSG_EPISODIC_METRIC_REPORT,
            Constants.MSG_EPISODIC_OPERATIONAL_STATE_REPORT,
            Constants.MSG_DESCRIPTION_MODIFICATION_REPORT,
            Constants.MSG_OPERATION_INVOKED_REPORT,
            Constants.MSG_WAVEFORM_STREAM,
            Constants.MSG_OBSERVED_VALUE_STREAM);

    // Manipulation Names for Hibernation
    public static final String MANIPULATION_NAME_SET_LOCATION_DETAIL = "setLocationDetail";
    public static final String MANIPULATION_NAME_GET_REMOVABLE_DESCRIPTORS = "getRemovableDescriptors";
    public static final String MANIPULATION_NAME_REMOVE_DESCRIPTOR = "removeDescriptor";
    public static final String MANIPULATION_NAME_INSERT_DESCRIPTOR = "insertDescriptor";
    public static final String MANIPULATION_NAME_SEND_HELLO = "sendHello";
    public static final String MANIPULATION_NAME_CREATE_CONTEXT_STATE_WITH_ASSOCIATION =
            "createContextStateWithAssociation";
    public static final String MANIPULATION_NAME_SET_ALERT_ACTIVATION = "setAlertActivation";
    public static final String MANIPULATION_NAME_SET_ALERT_CONDITION_PRESENCE = "setAlertConditionPresence";
    public static final String MANIPULATION_NAME_SET_SYSTEM_SIGNAL_ACTIVATION = "setSystemSignalActivation";
    public static final String MANIPULATION_NAME_SET_COMPONENT_ACTIVATION = "setComponentActivation";
    public static final String MANIPULATION_NAME_SET_METRIC_QUALITY_VALIDITY = "setMetricQualityValidity";
    public static final String MANIPULATION_NAME_SET_METRIC_STATUS = "setMetricStatus";
    public static final String MANIPULATION_NAME_TRIGGER_REPORT = "triggerReport";

    // Manipulation Data for Hibernation
    public static final String MANIPULATION_PARAMETER_HANDLE = "Handle";
    public static final String MANIPULATION_PARAMETER_LOCATION_DETAIL = "LocationDetail";
    public static final String MANIPULATION_PARAMETER_CONTEXT_ASSOCIATION = "ContextAssociation";
    public static final String MANIPULATION_PARAMETER_ALERT_ACTIVATION = "AlertActivation";
    public static final String MANIPULATION_PARAMETER_PRESENCE = "Presence";
    public static final String MANIPULATION_PARAMETER_ALERT_SIGNAL_ACTIVATION = "AlertSignalManifestation";
    public static final String MANIPULATION_PARAMETER_METRIC_CATEGORY = "MetricCategory";
    public static final String MANIPULATION_PARAMETER_COMPONENT_ACTIVATION = "ComponentActivation";
    public static final String MANIPULATION_PARAMETER_MEASUREMENT_VALIDITY = "MeasurementValidity";
    public static final String MANIPULATION_PARAMETER_QNAME = "QName";

    private Constants() {}

    /**
     * Adds a prefix resolving to the BICEPS message model namespace matching {@linkplain #NAMESPACES}.
     *
     * @param data to prefix
     * @return data with prefix
     */
    public static String msg(final String data) {
        return String.format("%s:%s", CommonConstants.NAMESPACE_MESSAGE_PREFIX, data);
    }

    /**
     * Adds a prefix resolving to the BICEPS participant model namespace matching {@linkplain #NAMESPACES}.
     *
     * @param data to prefix
     * @return data with prefix
     */
    public static String pm(final String data) {
        return String.format("%s:%s", CommonConstants.NAMESPACE_PARTICIPANT_PREFIX, data);
    }

    /**
     * Adds a prefix resolving to the BICEPS extension namespace matching {@linkplain #NAMESPACES}.
     *
     * @param data to prefix
     * @return data with prefix
     */
    public static String ext(final String data) {
        return String.format("%s:%s", CommonConstants.NAMESPACE_EXTENSION_PREFIX, data);
    }

    /**
     * Adds a prefix resolving to the Ws-Discovery namespace matching {@linkplain #NAMESPACES}.
     *
     * @param data to prefix
     * @return data with prefix
     */
    public static String wsd(final String data) {
        return String.format("%s:%s", WsDiscoveryConstants.NAMESPACE_PREFIX, data);
    }

    /**
     * Adds a prefix resolving to the Ws-Addressing namespace matching {@linkplain #NAMESPACES}.
     *
     * @param data to prefix
     * @return data with prefix
     */
    public static String wsa(final String data) {
        return String.format("%s:%s", WsAddressingConstants.NAMESPACE_PREFIX, data);
    }

    /**
     * Adds a prefix resolving to the Soap 1.2 namespace matching {@linkplain #NAMESPACES}.
     *
     * @param data to prefix
     * @return data with prefix
     */
    public static String s12(final String data) {
        return String.format("%s:%s", SoapConstants.NAMESPACE_PREFIX, data);
    }

    /**
     * Adds a prefix resolving to the WSDL namespace matching {@linkplain #NAMESPACES}.
     *
     * @param data to prefix
     * @return data with prefix
     */
    public static String wsdl(final String data) {
        return String.format("%s:%s", WSDL_NAMESPACE_PREFIX, data);
    }

    /**
     * Adds a prefix resolving to the WS-Policy namespace matching {@linkplain #NAMESPACES}.
     *
     * @param data to prefix
     * @return data with prefix
     */
    public static String wsp(final String data) {
        return String.format("%s:%s", WSP_NAMESPACE_PREFIX, data);
    }

    /**
     * Adds a prefix resolving to the WS-Security 2004 utility namespace matching {@linkplain #NAMESPACES}.
     *
     * @param data to prefix
     * @return data with prefix
     */
    public static String wsu(final String data) {
        return String.format("%s:%s", WSU_NAMESPACE_PREFIX, data);
    }

    /**
     * Adds a prefix resolving to the DPWS namespace matching {@linkplain #NAMESPACES}.
     *
     * @param data to prefix
     * @return data with prefix
     */
    public static String dpws(final String data) {
        return String.format("%s:%s", DpwsConstants.NAMESPACE_PREFIX, data);
    }

    /**
     * Adds a prefix resolving to the MDPWS namespace matching {@linkplain #NAMESPACES}.
     *
     * @param data to prefix
     * @return data with prefix
     */
    public static String mdpws(final String data) {
        return String.format("%s:%s", MDPWS_NAMESPACE_PREFIX, data);
    }
}
