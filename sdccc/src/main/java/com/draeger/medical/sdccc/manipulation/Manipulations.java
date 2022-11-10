/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.manipulation;

import com.draeger.medical.t2iapi.ResponseTypes;
import java.util.List;
import java.util.Optional;
import javax.xml.namespace.QName;
import org.somda.sdc.biceps.model.participant.AlertActivation;
import org.somda.sdc.biceps.model.participant.AlertSignalManifestation;
import org.somda.sdc.biceps.model.participant.ComponentActivation;
import org.somda.sdc.biceps.model.participant.ContextAssociation;
import org.somda.sdc.biceps.model.participant.LocationDetail;
import org.somda.sdc.biceps.model.participant.MeasurementValidity;
import org.somda.sdc.biceps.model.participant.MetricCategory;

/**
 * Defines all available manipulations which can be applied to a device.
 */
public interface Manipulations {

    /**
     * Set the location for associated location context.
     *
     * @param locationDetail new location
     * @return the result of the manipulation
     */
    ResponseTypes.Result setLocationDetail(LocationDetail locationDetail);

    /**
     * @return all descriptors which can be removed from and reinserted into the device MDIB.
     */
    List<String> getRemovableDescriptors();

    /**
     * Removes a descriptor from the device MDIB.
     *
     * @param handle to remove from the MDIB
     * @return the result of the manipulation
     */
    ResponseTypes.Result removeDescriptor(String handle);

    /**
     * Inserts a descriptor into the device MDIB.
     *
     * @param handle to insert into the MDIB
     * @return the result of the manipulation
     */
    ResponseTypes.Result insertDescriptor(String handle);

    /**
     * Announce the presence of the device in the network via a WS-Discovery Hello message.
     *
     * @return the result of the manipulation
     */
    ResponseTypes.Result sendHello();

    /**
     * Associates a <em>new</em> context state for the given descriptor handle and sets the association.
     *
     * @param descriptorHandle to associate a new context for
     * @param association      to set for new state
     * @return handle of the newly created state, empty if unsuccessful
     */
    Optional<String> createContextStateWithAssociation(String descriptorHandle, ContextAssociation association);

    /**
     * Set the activation state of an alert system.
     *
     * @param handle          state handle to set activation state for
     * @param activationState new activation state to set
     * @return the result of the manipulation
     */
    ResponseTypes.Result setAlertActivation(String handle, AlertActivation activationState);

    /**
     * Set the presence attribute of an alert condition state.
     *
     * @param handle   state handle to set the presence attribute for
     * @param presence new presence attribute to set
     * @return the result of the manipulation
     */
    ResponseTypes.Result setAlertConditionPresence(String handle, boolean presence);

    /**
     * Set the system signal activation of an alert system.
     *
     * @param handle        state handle to set the alert system activation attribute for
     * @param manifestation the manifestation of the system signal activation
     * @param activation    the activation state
     * @return the result of the manipulation
     */
    ResponseTypes.Result setSystemSignalActivation(
            String handle, AlertSignalManifestation manifestation, AlertActivation activation);

    /**
     * Set the activation state of an component or metric.
     *
     * @param handle          state handle to set activation for
     * @param activationState new activation state to set
     * @return the result of the manipulation
     */
    ResponseTypes.Result setComponentActivation(String handle, ComponentActivation activationState);

    /**
     * Set the metric quality validity of an metric value.
     *
     * @param handle state handle to set the validity attribute of the metric quality for
     * @param validity new validity attribute to set
     * @return the result of the manipulation
     */
    ResponseTypes.Result setMetricQualityValidity(String handle, MeasurementValidity validity);

    /**
     * Set the metric to a specific state to trigger the setting of the ActivationState.
     *
     * @param handle state handle to set the status of the metric for
     * @param category of the metric to set the status for
     * @param activation the activation state the metric should have, after manipulation
     * @return the result of the manipulation
     */
    ResponseTypes.Result setMetricStatus(String handle, MetricCategory category, ComponentActivation activation);

    /**
     * Trigger a descriptor update for the provided descriptor handle.
     *
     * @param handle handle of the descriptor to trigger an Update for.
     * @return the result of the manipulation
     */
    ResponseTypes.Result triggerDescriptorUpdate(String handle);

    /**
     * Trigger a report message of the provided type.
     *
     * @param reportType type of report a message should be triggered for.
     * @return the result of the manipulation
     */
    ResponseTypes.Result triggerReport(QName reportType);
}
