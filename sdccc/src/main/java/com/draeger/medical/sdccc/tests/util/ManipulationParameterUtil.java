/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2023 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.tests.util;

import com.draeger.medical.sdccc.util.Constants;
import java.util.Collections;
import java.util.List;
import javax.xml.namespace.QName;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.somda.sdc.biceps.model.participant.AlertActivation;
import org.somda.sdc.biceps.model.participant.AlertSignalManifestation;
import org.somda.sdc.biceps.model.participant.ComponentActivation;
import org.somda.sdc.biceps.model.participant.ContextAssociation;
import org.somda.sdc.biceps.model.participant.LocationDetail;
import org.somda.sdc.biceps.model.participant.MeasurementValidity;
import org.somda.sdc.biceps.model.participant.MetricCategory;

/**
 * Utility which provides manipulation parameter.
 */
public final class ManipulationParameterUtil {

    private ManipulationParameterUtil() {}

    /**
     * Build empty manipulation parameter data when the manipulation call does not need any parameters.
     *
     * @return the manipulation parameter data
     */
    public static ManipulationParameterData buildEmptyManipulationParameterData() {
        return new ManipulationParameterData(Collections.emptyList());
    }

    /**
     * Build manipulation parameter data containing just the handle that is needed for the manipulation.
     *
     * @param handle for which manipulation parameter data will be built.
     * @return the manipulation parameter data
     */
    public static ManipulationParameterData buildHandleManipulationParameterData(final String handle) {
        return new ManipulationParameterData(
                List.of(new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, handle)));
    }

    /**
     * Build manipulation parameter data containing the location detail that is needed for the manipulation.
     *
     * @param locationDetail for which manipulation parameter data will be built.
     * @return the manipulation parameter data
     */
    public static ManipulationParameterData buildLocationDetailManipulationParameterData(
            final LocationDetail locationDetail) {
        return new ManipulationParameterData(List.of(new ImmutablePair<>(
                Constants.MANIPULATION_PARAMETER_LOCATION_DETAIL,
                String.format(
                        "poC=%s, " + "room=%s, " + "bed=%s, " + "facility=%s, " + "building=%s, " + "floor=%s",
                        locationDetail.getPoC(),
                        locationDetail.getRoom(),
                        locationDetail.getBed(),
                        locationDetail.getFacility(),
                        locationDetail.getBuilding(),
                        locationDetail.getFloor()))));
    }

    /**
     * Build manipulation parameter data containing the handle and the context association for the context state with
     * association to be created.
     *
     * @param handle of the context state
     * @param association of the context state
     * @return the manipulation parameter data
     */
    public static ManipulationParameterData buildContextAssociationManipulationParameterData(
            final String handle, final ContextAssociation association) {
        return new ManipulationParameterData(List.of(
                new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, handle),
                new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_CONTEXT_ASSOCIATION, association.value())));
    }

    /**
     * Build manipulation parameter data containing the handle of the alert and the alert activation to set.
     *
     * @param handle of the alert
     * @param alertActivation to which the alert should be set
     * @return the manipulation parameter data
     */
    public static ManipulationParameterData buildAlertActivationManipulationParameterData(
            final String handle, final AlertActivation alertActivation) {
        return new ManipulationParameterData(List.of(
                new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, handle),
                new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_ALERT_ACTIVATION, alertActivation.value())));
    }

    /**
     * Build manipulation parameter data containing the handle of the alert condition and the presence to set.
     *
     * @param handle of the alert condition
     * @param presence to which the alert condition should be set
     * @return the manipulation parameter data
     */
    public static ManipulationParameterData buildAlertConditionPresenceManipulationParameterData(
            final String handle, final boolean presence) {
        return new ManipulationParameterData(List.of(
                new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, handle),
                new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_PRESENCE, String.format("%s", presence))));
    }

    /**
     * Build manipulation parameter data containing the handle and manifestation of the alert system and the alert
     * activation to set.
     *
     * @param handle of the alert system
     * @param manifestation of the alert system
     * @param activation to which the alert system should be set
     * @return the manipulation parameter data
     */
    public static ManipulationParameterData buildSystemSignalActivationManipulationParameterData(
            final String handle, final AlertSignalManifestation manifestation, final AlertActivation activation) {
        return new ManipulationParameterData(List.of(
                new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, handle),
                new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_ALERT_SIGNAL_MANIFESTATION, manifestation.value()),
                new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_ALERT_ACTIVATION, activation.value())));
    }

    /**
     * Build manipulation parameter data containing the handle of the device component and the component activation to set.
     *
     * @param handle of the device component
     * @param activation to which the component should be set
     * @return the manipulation parameter data
     */
    public static ManipulationParameterData buildComponentActivationManipulationParameterData(
            final String handle, final ComponentActivation activation) {
        return new ManipulationParameterData(List.of(
                new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, handle),
                new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_COMPONENT_ACTIVATION, activation.value())));
    }

    /**
     * Build manipulation parameter data containing the handle of the metric and the validity to set.
     *
     * @param handle of the metric
     * @param validity to which the metric should be set
     * @return the manipulation parameter data
     */
    public static ManipulationParameterData buildMetricQualityValidityManipulationParameterData(
            final String handle, final MeasurementValidity validity) {
        return new ManipulationParameterData(List.of(
                new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, handle),
                new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_MEASUREMENT_VALIDITY, validity.value())));
    }

    /**
     * Build manipulation parameter data containing the handle, category and activation of the metric to set the status for.
     *
     * @param sequenceId in which the manipulation is performed
     * @param handle of the metric
     * @param category of the metric
     * @param activation of the metric
     * @return the manipulation parameter data
     */
    public static ManipulationParameterData buildMetricStatusManipulationParameterData(
            final String sequenceId,
            final String handle,
            final MetricCategory category,
            final ComponentActivation activation) {
        return new ManipulationParameterData(List.of(
                new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_SEQUENCE_ID, sequenceId),
                new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, handle),
                new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_METRIC_CATEGORY, category.value()),
                new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_COMPONENT_ACTIVATION, activation.value())));
    }

    /**
     * Build manipulation parameter data containing the category and activation of the metric.
     *
     * @param category of the metric
     * @param activation of the metric
     * @return the manipulation parameter data
     */
    public static ManipulationParameterData buildMetricStatusManipulationParameterDataWithoutHandle(
            final MetricCategory category, final ComponentActivation activation) {
        return new ManipulationParameterData(List.of(
                new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_METRIC_CATEGORY, category.value()),
                new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_COMPONENT_ACTIVATION, activation.value())));
    }

    /**
     * Build manipulation parameter data containing the report to trigger.
     *
     * @param report to trigger.
     * @return the manipulation parameter data
     */
    public static ManipulationParameterData buildTriggerReportManipulationParameterData(final QName report) {
        return new ManipulationParameterData(
                List.of(new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_QNAME, report.toString())));
    }

    /**
     * Wrapper for manipulation parameter.
     */
    public static class ManipulationParameterData {
        private final List<Pair<String, String>> data;

        ManipulationParameterData(final List<Pair<String, String>> data) {
            this.data = data;
        }

        public List<Pair<String, String>> getParameterData() {
            return data;
        }
    }
}
