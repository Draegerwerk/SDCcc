/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.util;

import java.util.Map;
import javax.xml.namespace.QName;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.somda.sdc.glue.common.ActionConstants;
import org.somda.sdc.glue.common.CommonConstants;
import org.somda.sdc.glue.common.WsdlConstants;

/**
 * Validates WSDLs parsed by {@linkplain WsdlParser} for their compliance with SDC Glue Annex B.
 */
public final class WsdlValidator {
    private static final Logger LOG = LogManager.getLogger();

    private static final String SDC_NAMESPACE = CommonConstants.NAMESPACE_SDC;
    private static final String MESSAGE_NAMESPACE = org.somda.sdc.biceps.common.CommonConstants.NAMESPACE_MESSAGE;

    /*
    GetService operations
    */
    public static final WsdlParser.OperationArguments GET_MDIB_ARGUMENTS = new WsdlParser.OperationArguments(
            new WsdlParser.OperationArgument(
                    WsdlParser.ArgumentType.INPUT, new QName(MESSAGE_NAMESPACE, WsdlConstants.OPERATION_GET_MDIB)),
            new WsdlParser.OperationArgument(
                    WsdlParser.ArgumentType.OUTPUT,
                    new QName(MESSAGE_NAMESPACE, response(WsdlConstants.OPERATION_GET_MDIB))));

    public static final WsdlParser.OperationArguments GET_MD_DESCRIPTION_ARGUMENTS = new WsdlParser.OperationArguments(
            new WsdlParser.OperationArgument(
                    WsdlParser.ArgumentType.INPUT,
                    new QName(MESSAGE_NAMESPACE, WsdlConstants.OPERATION_GET_MD_DESCRIPTION)),
            new WsdlParser.OperationArgument(
                    WsdlParser.ArgumentType.OUTPUT,
                    new QName(MESSAGE_NAMESPACE, response(WsdlConstants.OPERATION_GET_MD_DESCRIPTION))));

    public static final WsdlParser.OperationArguments GET_MD_STATE_ARGUMENTS = new WsdlParser.OperationArguments(
            new WsdlParser.OperationArgument(
                    WsdlParser.ArgumentType.INPUT, new QName(MESSAGE_NAMESPACE, WsdlConstants.OPERATION_GET_MD_STATE)),
            new WsdlParser.OperationArgument(
                    WsdlParser.ArgumentType.OUTPUT,
                    new QName(MESSAGE_NAMESPACE, response(WsdlConstants.OPERATION_GET_MD_STATE))));

    public static final Map<QName, WsdlParser.OperationArguments> GET_SERVICE_OPERATIONS = Map.of(
            new QName(SDC_NAMESPACE, WsdlConstants.OPERATION_GET_MDIB), GET_MDIB_ARGUMENTS,
            new QName(SDC_NAMESPACE, WsdlConstants.OPERATION_GET_MD_DESCRIPTION), GET_MD_DESCRIPTION_ARGUMENTS,
            new QName(SDC_NAMESPACE, WsdlConstants.OPERATION_GET_MD_STATE), GET_MD_STATE_ARGUMENTS);

    /*
    SetService operations
    */
    public static final WsdlParser.OperationArguments SET_VALUE_ARGUMENTS = new WsdlParser.OperationArguments(
            new WsdlParser.OperationArgument(
                    WsdlParser.ArgumentType.INPUT, new QName(MESSAGE_NAMESPACE, WsdlConstants.OPERATION_SET_VALUE)),
            new WsdlParser.OperationArgument(
                    WsdlParser.ArgumentType.OUTPUT,
                    new QName(MESSAGE_NAMESPACE, response(WsdlConstants.OPERATION_SET_VALUE))));

    public static final WsdlParser.OperationArguments SET_STRING_ARGUMENTS = new WsdlParser.OperationArguments(
            new WsdlParser.OperationArgument(
                    WsdlParser.ArgumentType.INPUT, new QName(MESSAGE_NAMESPACE, WsdlConstants.OPERATION_SET_STRING)),
            new WsdlParser.OperationArgument(
                    WsdlParser.ArgumentType.OUTPUT,
                    new QName(MESSAGE_NAMESPACE, response(WsdlConstants.OPERATION_SET_STRING))));

    public static final WsdlParser.OperationArguments ACTIVATE_ARGUMENTS = new WsdlParser.OperationArguments(
            new WsdlParser.OperationArgument(
                    WsdlParser.ArgumentType.INPUT, new QName(MESSAGE_NAMESPACE, WsdlConstants.OPERATION_ACTIVATE)),
            new WsdlParser.OperationArgument(
                    WsdlParser.ArgumentType.OUTPUT,
                    new QName(MESSAGE_NAMESPACE, response(WsdlConstants.OPERATION_ACTIVATE))));

    public static final WsdlParser.OperationArguments SET_ALERT_STATE_ARGUMENTS = new WsdlParser.OperationArguments(
            new WsdlParser.OperationArgument(
                    WsdlParser.ArgumentType.INPUT,
                    new QName(MESSAGE_NAMESPACE, WsdlConstants.OPERATION_SET_ALERT_STATE)),
            new WsdlParser.OperationArgument(
                    WsdlParser.ArgumentType.OUTPUT,
                    new QName(MESSAGE_NAMESPACE, response(WsdlConstants.OPERATION_SET_ALERT_STATE))));

    public static final WsdlParser.OperationArguments SET_COMPONENT_STATE_ARGUMENTS = new WsdlParser.OperationArguments(
            new WsdlParser.OperationArgument(
                    WsdlParser.ArgumentType.INPUT,
                    new QName(MESSAGE_NAMESPACE, WsdlConstants.OPERATION_SET_COMPONENT_STATE)),
            new WsdlParser.OperationArgument(
                    WsdlParser.ArgumentType.OUTPUT,
                    new QName(MESSAGE_NAMESPACE, response(WsdlConstants.OPERATION_SET_COMPONENT_STATE))));

    public static final WsdlParser.OperationArguments SET_METRIC_STATE_ARGUMENTS = new WsdlParser.OperationArguments(
            new WsdlParser.OperationArgument(
                    WsdlParser.ArgumentType.INPUT,
                    new QName(MESSAGE_NAMESPACE, WsdlConstants.OPERATION_SET_METRIC_STATE)),
            new WsdlParser.OperationArgument(
                    WsdlParser.ArgumentType.OUTPUT,
                    new QName(MESSAGE_NAMESPACE, response(WsdlConstants.OPERATION_SET_METRIC_STATE))));

    public static final WsdlParser.OperationArguments OPERATION_INVOKED_REPORT_ARGUMENTS =
            new WsdlParser.OperationArguments(new WsdlParser.OperationArgument(
                    WsdlParser.ArgumentType.OUTPUT,
                    new QName(MESSAGE_NAMESPACE, WsdlConstants.OPERATION_OPERATION_INVOKED_REPORT)));

    public static final Map<QName, WsdlParser.OperationArguments> SET_SERVICE_OPERATIONS = Map.of(
            new QName(SDC_NAMESPACE, WsdlConstants.OPERATION_SET_VALUE), SET_VALUE_ARGUMENTS,
            new QName(SDC_NAMESPACE, WsdlConstants.OPERATION_SET_STRING), SET_STRING_ARGUMENTS,
            new QName(SDC_NAMESPACE, WsdlConstants.OPERATION_ACTIVATE), ACTIVATE_ARGUMENTS,
            new QName(SDC_NAMESPACE, WsdlConstants.OPERATION_SET_ALERT_STATE), SET_ALERT_STATE_ARGUMENTS,
            new QName(SDC_NAMESPACE, WsdlConstants.OPERATION_SET_COMPONENT_STATE), SET_COMPONENT_STATE_ARGUMENTS,
            new QName(SDC_NAMESPACE, WsdlConstants.OPERATION_SET_METRIC_STATE), SET_METRIC_STATE_ARGUMENTS,
            new QName(SDC_NAMESPACE, WsdlConstants.OPERATION_OPERATION_INVOKED_REPORT),
                    OPERATION_INVOKED_REPORT_ARGUMENTS);

    /*
    DescriptionEventService operations
    */
    public static final WsdlParser.OperationArguments DESCRIPTION_MODIFICATION_REPORT_ARGUMENTS =
            new WsdlParser.OperationArguments(new WsdlParser.OperationArgument(
                    WsdlParser.ArgumentType.OUTPUT,
                    new QName(MESSAGE_NAMESPACE, WsdlConstants.OPERATION_DESCRIPTION_MODIFICATION_REPORT)));

    public static final Map<QName, WsdlParser.OperationArguments> DESCRIPTION_EVENT_SERVICE_OPERATIONS = Map.of(
            new QName(SDC_NAMESPACE, WsdlConstants.OPERATION_DESCRIPTION_MODIFICATION_REPORT),
            DESCRIPTION_MODIFICATION_REPORT_ARGUMENTS);

    /*
    StateEventService operations
    */
    public static final WsdlParser.OperationArguments EPISODIC_ALERT_REPORT_ARGUMENTS =
            new WsdlParser.OperationArguments(new WsdlParser.OperationArgument(
                    WsdlParser.ArgumentType.OUTPUT,
                    new QName(MESSAGE_NAMESPACE, WsdlConstants.OPERATION_EPISODIC_ALERT_REPORT)));

    public static final WsdlParser.OperationArguments PERIODIC_ALERT_REPORT_ARGUMENTS =
            new WsdlParser.OperationArguments(new WsdlParser.OperationArgument(
                    WsdlParser.ArgumentType.OUTPUT,
                    new QName(MESSAGE_NAMESPACE, WsdlConstants.OPERATION_PERIODIC_ALERT_REPORT)));

    public static final WsdlParser.OperationArguments EPISODIC_COMPONENT_REPORT_ARGUMENTS =
            new WsdlParser.OperationArguments(new WsdlParser.OperationArgument(
                    WsdlParser.ArgumentType.OUTPUT,
                    new QName(MESSAGE_NAMESPACE, WsdlConstants.OPERATION_EPISODIC_COMPONENT_REPORT)));

    public static final WsdlParser.OperationArguments PERIODIC_COMPONENT_REPORT_ARGUMENTS =
            new WsdlParser.OperationArguments(new WsdlParser.OperationArgument(
                    WsdlParser.ArgumentType.OUTPUT,
                    new QName(MESSAGE_NAMESPACE, WsdlConstants.OPERATION_PERIODIC_COMPONENT_REPORT)));

    public static final WsdlParser.OperationArguments EPISODIC_METRIC_REPORT_ARGUMENTS =
            new WsdlParser.OperationArguments(new WsdlParser.OperationArgument(
                    WsdlParser.ArgumentType.OUTPUT,
                    new QName(MESSAGE_NAMESPACE, WsdlConstants.OPERATION_EPISODIC_METRIC_REPORT)));

    public static final WsdlParser.OperationArguments PERIODIC_METRIC_REPORT_ARGUMENTS =
            new WsdlParser.OperationArguments(new WsdlParser.OperationArgument(
                    WsdlParser.ArgumentType.OUTPUT,
                    new QName(MESSAGE_NAMESPACE, WsdlConstants.OPERATION_PERIODIC_METRIC_REPORT)));

    public static final WsdlParser.OperationArguments EPISODIC_OPERATIONAL_STATE_REPORT_ARGUMENTS =
            new WsdlParser.OperationArguments(new WsdlParser.OperationArgument(
                    WsdlParser.ArgumentType.OUTPUT,
                    new QName(MESSAGE_NAMESPACE, WsdlConstants.OPERATION_EPISODIC_OPERATIONAL_STATE_REPORT)));

    public static final WsdlParser.OperationArguments PERIODIC_OPERATIONAL_STATE_REPORT_ARGUMENTS =
            new WsdlParser.OperationArguments(new WsdlParser.OperationArgument(
                    WsdlParser.ArgumentType.OUTPUT,
                    new QName(MESSAGE_NAMESPACE, WsdlConstants.OPERATION_PERIODIC_OPERATIONAL_STATE_REPORT)));

    public static final WsdlParser.OperationArguments SYSTEM_ERROR_REPORT_ARGUMENTS =
            new WsdlParser.OperationArguments(new WsdlParser.OperationArgument(
                    WsdlParser.ArgumentType.OUTPUT,
                    new QName(MESSAGE_NAMESPACE, WsdlConstants.OPERATION_SYSTEM_ERROR_REPORT)));

    public static final Map<QName, WsdlParser.OperationArguments> STATE_EVENT_SERVICE_OPERATIONS = Map.of(
            new QName(SDC_NAMESPACE, WsdlConstants.OPERATION_EPISODIC_ALERT_REPORT),
            EPISODIC_ALERT_REPORT_ARGUMENTS,
            new QName(SDC_NAMESPACE, WsdlConstants.OPERATION_PERIODIC_ALERT_REPORT),
            PERIODIC_ALERT_REPORT_ARGUMENTS,
            new QName(SDC_NAMESPACE, WsdlConstants.OPERATION_EPISODIC_COMPONENT_REPORT),
            EPISODIC_COMPONENT_REPORT_ARGUMENTS,
            new QName(SDC_NAMESPACE, WsdlConstants.OPERATION_PERIODIC_COMPONENT_REPORT),
            PERIODIC_COMPONENT_REPORT_ARGUMENTS,
            new QName(SDC_NAMESPACE, WsdlConstants.OPERATION_EPISODIC_METRIC_REPORT),
            EPISODIC_METRIC_REPORT_ARGUMENTS,
            new QName(SDC_NAMESPACE, WsdlConstants.OPERATION_PERIODIC_METRIC_REPORT),
            PERIODIC_METRIC_REPORT_ARGUMENTS,
            new QName(SDC_NAMESPACE, WsdlConstants.OPERATION_EPISODIC_OPERATIONAL_STATE_REPORT),
            EPISODIC_OPERATIONAL_STATE_REPORT_ARGUMENTS,
            new QName(SDC_NAMESPACE, WsdlConstants.OPERATION_PERIODIC_OPERATIONAL_STATE_REPORT),
            PERIODIC_OPERATIONAL_STATE_REPORT_ARGUMENTS,
            new QName(SDC_NAMESPACE, WsdlConstants.OPERATION_SYSTEM_ERROR_REPORT),
            SYSTEM_ERROR_REPORT_ARGUMENTS);

    /*
    ContextService operations
    */
    public static final WsdlParser.OperationArguments GET_CONTEXT_STATES_ARGUMENTS = new WsdlParser.OperationArguments(
            new WsdlParser.OperationArgument(
                    WsdlParser.ArgumentType.INPUT,
                    new QName(MESSAGE_NAMESPACE, WsdlConstants.OPERATION_GET_CONTEXT_STATES)),
            new WsdlParser.OperationArgument(
                    WsdlParser.ArgumentType.OUTPUT,
                    new QName(MESSAGE_NAMESPACE, response(WsdlConstants.OPERATION_GET_CONTEXT_STATES))));

    public static final WsdlParser.OperationArguments SET_CONTEXT_STATE_ARGUMENTS = new WsdlParser.OperationArguments(
            new WsdlParser.OperationArgument(
                    WsdlParser.ArgumentType.INPUT,
                    new QName(MESSAGE_NAMESPACE, WsdlConstants.OPERATION_SET_CONTEXT_STATE)),
            new WsdlParser.OperationArgument(
                    WsdlParser.ArgumentType.OUTPUT,
                    new QName(MESSAGE_NAMESPACE, response(WsdlConstants.OPERATION_SET_CONTEXT_STATE))));

    public static final WsdlParser.OperationArguments GET_CONTEXT_STATES_BY_IDENTIFICATION_ARGUMENTS =
            new WsdlParser.OperationArguments(
                    new WsdlParser.OperationArgument(
                            WsdlParser.ArgumentType.INPUT,
                            new QName(MESSAGE_NAMESPACE, WsdlConstants.OPERATION_GET_CONTEXT_STATES_BY_IDENTIFICATION)),
                    new WsdlParser.OperationArgument(
                            WsdlParser.ArgumentType.OUTPUT,
                            new QName(
                                    MESSAGE_NAMESPACE,
                                    response(WsdlConstants.OPERATION_GET_CONTEXT_STATES_BY_IDENTIFICATION))));

    public static final WsdlParser.OperationArguments GET_CONTEXT_STATES_BY_FILTER_ARGUMENTS =
            new WsdlParser.OperationArguments(
                    new WsdlParser.OperationArgument(
                            WsdlParser.ArgumentType.INPUT,
                            new QName(MESSAGE_NAMESPACE, WsdlConstants.OPERATION_GET_CONTEXT_STATES_BY_FILTER)),
                    new WsdlParser.OperationArgument(
                            WsdlParser.ArgumentType.OUTPUT,
                            new QName(
                                    MESSAGE_NAMESPACE,
                                    response(WsdlConstants.OPERATION_GET_CONTEXT_STATES_BY_FILTER))));

    public static final WsdlParser.OperationArguments EPISODIC_CONTEXT_REPORT_ARGUMENTS =
            new WsdlParser.OperationArguments(new WsdlParser.OperationArgument(
                    WsdlParser.ArgumentType.OUTPUT,
                    new QName(MESSAGE_NAMESPACE, WsdlConstants.OPERATION_EPISODIC_CONTEXT_REPORT)));

    public static final WsdlParser.OperationArguments PERIODIC_CONTEXT_REPORT_ARGUMENTS =
            new WsdlParser.OperationArguments(new WsdlParser.OperationArgument(
                    WsdlParser.ArgumentType.OUTPUT,
                    new QName(MESSAGE_NAMESPACE, WsdlConstants.OPERATION_PERIODIC_CONTEXT_REPORT)));

    public static final Map<QName, WsdlParser.OperationArguments> CONTEXT_SERVICE_OPERATIONS = Map.of(
            new QName(SDC_NAMESPACE, WsdlConstants.OPERATION_GET_CONTEXT_STATES),
            GET_CONTEXT_STATES_ARGUMENTS,
            new QName(SDC_NAMESPACE, WsdlConstants.OPERATION_SET_CONTEXT_STATE),
            SET_CONTEXT_STATE_ARGUMENTS,
            new QName(SDC_NAMESPACE, WsdlConstants.OPERATION_GET_CONTEXT_STATES_BY_IDENTIFICATION),
            GET_CONTEXT_STATES_BY_IDENTIFICATION_ARGUMENTS,
            new QName(SDC_NAMESPACE, WsdlConstants.OPERATION_GET_CONTEXT_STATES_BY_FILTER),
            GET_CONTEXT_STATES_BY_FILTER_ARGUMENTS,
            new QName(SDC_NAMESPACE, WsdlConstants.OPERATION_EPISODIC_CONTEXT_REPORT),
            EPISODIC_CONTEXT_REPORT_ARGUMENTS,
            new QName(SDC_NAMESPACE, WsdlConstants.OPERATION_PERIODIC_CONTEXT_REPORT),
            PERIODIC_CONTEXT_REPORT_ARGUMENTS);

    /*
    WaveformService operations
    */
    public static final WsdlParser.OperationArguments WAVEFORM_STREAM_ARGUMENTS =
            new WsdlParser.OperationArguments(new WsdlParser.OperationArgument(
                    WsdlParser.ArgumentType.OUTPUT,
                    new QName(MESSAGE_NAMESPACE, WsdlConstants.OPERATION_WAVEFORM_STREAM)));

    public static final WsdlParser.OperationArguments OBSERVED_VALUE_STREAM_ARGUMENTS =
            new WsdlParser.OperationArguments(new WsdlParser.OperationArgument(
                    WsdlParser.ArgumentType.OUTPUT,
                    new QName(MESSAGE_NAMESPACE, WsdlConstants.OPERATION_OBSERVED_VALUE_STREAM)));

    public static final Map<QName, WsdlParser.OperationArguments> WAVEFORM_SERVICE_OPERATIONS = Map.of(
            new QName(SDC_NAMESPACE, WsdlConstants.OPERATION_WAVEFORM_STREAM),
            WAVEFORM_STREAM_ARGUMENTS,
            new QName(SDC_NAMESPACE, WsdlConstants.OPERATION_OBSERVED_VALUE_STREAM),
            OBSERVED_VALUE_STREAM_ARGUMENTS);

    /*
    ContainmentTreeService operations
    */
    public static final WsdlParser.OperationArguments GET_CONTAINMENT_TREE_ARGUMENTS =
            new WsdlParser.OperationArguments(
                    new WsdlParser.OperationArgument(
                            WsdlParser.ArgumentType.INPUT,
                            new QName(MESSAGE_NAMESPACE, WsdlConstants.OPERATION_GET_CONTAINMENT_TREE)),
                    new WsdlParser.OperationArgument(
                            WsdlParser.ArgumentType.OUTPUT,
                            new QName(MESSAGE_NAMESPACE, response(WsdlConstants.OPERATION_GET_CONTAINMENT_TREE))));

    public static final WsdlParser.OperationArguments GET_DESCRIPTOR_ARGUMENTS = new WsdlParser.OperationArguments(
            new WsdlParser.OperationArgument(
                    WsdlParser.ArgumentType.INPUT,
                    new QName(MESSAGE_NAMESPACE, WsdlConstants.OPERATION_GET_DESCRIPTOR)),
            new WsdlParser.OperationArgument(
                    WsdlParser.ArgumentType.OUTPUT,
                    new QName(MESSAGE_NAMESPACE, response(WsdlConstants.OPERATION_GET_DESCRIPTOR))));

    public static final Map<QName, WsdlParser.OperationArguments> CONTAINMENT_TREE_SERVICE_OPERATIONS = Map.of(
            new QName(SDC_NAMESPACE, WsdlConstants.OPERATION_GET_CONTAINMENT_TREE),
            GET_CONTAINMENT_TREE_ARGUMENTS,
            new QName(SDC_NAMESPACE, WsdlConstants.OPERATION_GET_DESCRIPTOR),
            GET_DESCRIPTOR_ARGUMENTS);

    /*
    ArchiveService operations
    */
    public static final WsdlParser.OperationArguments GET_DESCRIPTORS_FROM_ARCHIVE_ARGUMENTS =
            new WsdlParser.OperationArguments(
                    new WsdlParser.OperationArgument(
                            WsdlParser.ArgumentType.INPUT,
                            new QName(MESSAGE_NAMESPACE, WsdlConstants.OPERATION_GET_DESCRIPTORS_FROM_ARCHIVE)),
                    new WsdlParser.OperationArgument(
                            WsdlParser.ArgumentType.OUTPUT,
                            new QName(
                                    MESSAGE_NAMESPACE,
                                    response(WsdlConstants.OPERATION_GET_DESCRIPTORS_FROM_ARCHIVE))));

    public static final WsdlParser.OperationArguments GET_STATES_FROM_ARCHIVE_ARGUMENTS =
            new WsdlParser.OperationArguments(
                    new WsdlParser.OperationArgument(
                            WsdlParser.ArgumentType.INPUT,
                            new QName(MESSAGE_NAMESPACE, WsdlConstants.OPERATION_GET_STATES_FROM_ARCHIVE)),
                    new WsdlParser.OperationArgument(
                            WsdlParser.ArgumentType.OUTPUT,
                            new QName(MESSAGE_NAMESPACE, response(WsdlConstants.OPERATION_GET_STATES_FROM_ARCHIVE))));

    public static final Map<QName, WsdlParser.OperationArguments> ARCHIVE_SERVICE_OPERATIONS = Map.of(
            new QName(SDC_NAMESPACE, WsdlConstants.OPERATION_GET_DESCRIPTORS_FROM_ARCHIVE),
            GET_DESCRIPTORS_FROM_ARCHIVE_ARGUMENTS,
            new QName(SDC_NAMESPACE, WsdlConstants.OPERATION_GET_STATES_FROM_ARCHIVE),
            GET_STATES_FROM_ARCHIVE_ARGUMENTS);

    /*
    LocalizationService operations
    */
    public static final WsdlParser.OperationArguments GET_LOCALIZED_TEXT_ARGUMENTS = new WsdlParser.OperationArguments(
            new WsdlParser.OperationArgument(
                    WsdlParser.ArgumentType.INPUT,
                    new QName(MESSAGE_NAMESPACE, WsdlConstants.OPERATION_GET_LOCALIZED_TEXT)),
            new WsdlParser.OperationArgument(
                    WsdlParser.ArgumentType.OUTPUT,
                    new QName(MESSAGE_NAMESPACE, response(WsdlConstants.OPERATION_GET_LOCALIZED_TEXT))));

    public static final WsdlParser.OperationArguments GET_SUPPORTED_LANGUAGES_ARGUMENTS =
            new WsdlParser.OperationArguments(
                    new WsdlParser.OperationArgument(
                            WsdlParser.ArgumentType.INPUT,
                            new QName(MESSAGE_NAMESPACE, WsdlConstants.OPERATION_GET_SUPPORTED_LANGUAGES)),
                    new WsdlParser.OperationArgument(
                            WsdlParser.ArgumentType.OUTPUT,
                            new QName(MESSAGE_NAMESPACE, response(WsdlConstants.OPERATION_GET_SUPPORTED_LANGUAGES))));

    public static final Map<QName, WsdlParser.OperationArguments> LOCALIZATION_SERVICE_OPERATIONS = Map.of(
            new QName(SDC_NAMESPACE, WsdlConstants.OPERATION_GET_LOCALIZED_TEXT),
            GET_LOCALIZED_TEXT_ARGUMENTS,
            new QName(SDC_NAMESPACE, WsdlConstants.OPERATION_GET_SUPPORTED_LANGUAGES),
            GET_SUPPORTED_LANGUAGES_ARGUMENTS);

    // lookup table
    public static final Map<QName, Map<QName, WsdlParser.OperationArguments>> OPERATIONS_LOOKUP = Map.of(
            WsdlConstants.PORT_TYPE_GET_QNAME, GET_SERVICE_OPERATIONS,
            WsdlConstants.PORT_TYPE_SET_QNAME, SET_SERVICE_OPERATIONS,
            WsdlConstants.PORT_TYPE_DESCRIPTION_EVENT_QNAME, DESCRIPTION_EVENT_SERVICE_OPERATIONS,
            WsdlConstants.PORT_TYPE_STATE_EVENT_QNAME, STATE_EVENT_SERVICE_OPERATIONS,
            WsdlConstants.PORT_TYPE_CONTEXT_QNAME, CONTEXT_SERVICE_OPERATIONS,
            WsdlConstants.PORT_TYPE_WAVEFORM_QNAME, WAVEFORM_SERVICE_OPERATIONS,
            WsdlConstants.PORT_TYPE_CONTAINMENT_TREE_QNAME, CONTAINMENT_TREE_SERVICE_OPERATIONS,
            WsdlConstants.PORT_TYPE_ARCHIVE_QNAME, ARCHIVE_SERVICE_OPERATIONS,
            WsdlConstants.PORT_TYPE_LOCALIZATION_QNAME, LOCALIZATION_SERVICE_OPERATIONS);

    private WsdlValidator() {}

    /**
     * Compares a portType to the reference defined by SDC Glue Annex B.
     *
     * @param serviceName to check for
     * @param operations  of the port type
     * @return true if equal to reference, false otherwise
     */
    public static boolean isEqualService(
            final QName serviceName, final Map<QName, WsdlParser.OperationArguments> operations) {
        final var referenceOperations = OPERATIONS_LOOKUP.get(serviceName);

        if (referenceOperations == null) {
            LOG.warn("Service {} is not a known SDC service", serviceName);
            return false;
        }

        LOG.debug("Checking equality for service {}", serviceName);
        return referenceOperations.equals(operations);
    }

    private static String response(final String request) {
        return ActionConstants.getResponseAction(request);
    }
}
