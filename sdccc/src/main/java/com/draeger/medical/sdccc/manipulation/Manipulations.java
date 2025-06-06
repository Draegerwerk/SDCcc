/*
 * This Source Code Form is subject to the terms of the "SDCcc non-commercial use license".
 *
 * Copyright (C) 2025 Draegerwerk AG & Co. KGaA
 */

package com.draeger.medical.sdccc.manipulation;

import java.util.List;
import javax.xml.namespace.QName;
import org.somda.sdc.biceps.model.participant.AbstractDescriptor;
import org.somda.sdc.biceps.model.participant.AlertActivation;
import org.somda.sdc.biceps.model.participant.AlertSignalManifestation;
import org.somda.sdc.biceps.model.participant.ComponentActivation;
import org.somda.sdc.biceps.model.participant.ContextAssociation;
import org.somda.sdc.biceps.model.participant.LocationDetail;
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
    ResultResponse setLocationDetail(LocationDetail locationDetail);

    /**
     * @return result and all descriptors which can be removed from and reinserted into the device MDIB.
     */
    ManipulationResponse<List<String>> getRemovableDescriptorsOfClass();

    /**
     * Retrieves a list of descriptor handles for descriptors that can be removed from and reinserted into
     * the MDIB.
     * @param descriptorType type of descriptor to filter for. Currently, only AbstractDescriptor
     *                       and MdsDescriptor are supported.
     * @return result and all descriptors of the given type which can be removed from and reinserted into the device MDIB
     *          and have a type matching descriptorType.
     */
    ManipulationResponse<List<String>> getRemovableDescriptorsOfClass(
            Class<? extends AbstractDescriptor> descriptorType);

    /**
     * Removes a descriptor from the device MDIB.
     *
     * @param handle to remove from the MDIB
     * @return the result of the manipulation
     */
    ResultResponse removeDescriptor(String handle);

    /**
     * Inserts a descriptor into the device MDIB.
     *
     * @param handle to insert into the MDIB
     * @return the result of the manipulation
     */
    ResultResponse insertDescriptor(String handle);

    /**
     * Announce the presence of the device in the network via a WS-Discovery Hello message.
     *
     * @return the result of the manipulation
     */
    ResultResponse sendHello();

    /**
     * Associates a <em>new</em> context state for the given descriptor handle and sets the association.
     *
     * @param descriptorHandle to associate a new context for
     * @param association      to set for new state
     * @return result and handle of the newly created state, null if unsuccessful
     */
    ManipulationResponse<String> createContextStateWithAssociation(
            String descriptorHandle, ContextAssociation association);

    /**
     * Sets the association of a new or existing context state for the given descriptor handle.
     * And gives it a BindingMdibVersion.
     *
     * @param descriptorHandle to associate a new context for
     * @param association      to set for new state
     * @return result and handle of the newly created state, null if unsuccessful
     */
    ManipulationResponse<String> createContextStateWithAssocAndBindingMdibVersion(
            String descriptorHandle, ContextAssociation association);

    /**
     * Set the activation state of an alert system.
     *
     * @param handle          state handle to set activation state for
     * @param activationState new activation state to set
     * @return the result of the manipulation
     */
    ResultResponse setAlertActivation(String handle, AlertActivation activationState);

    /**
     * Set the presence attribute of an alert condition state.
     *
     * @param handle   state handle to set the presence attribute for
     * @param presence new presence attribute to set
     * @return the result of the manipulation
     */
    ResultResponse setAlertConditionPresence(String handle, boolean presence);

    /**
     * Set the system signal activation of an alert system.
     *
     * @param handle        state handle to set the alert system activation attribute for
     * @param manifestation the manifestation of the system signal activation
     * @param activation    the activation state
     * @return the result of the manipulation
     */
    ResultResponse setSystemSignalActivation(
            String handle, AlertSignalManifestation manifestation, AlertActivation activation);

    /**
     * Set the activation state of an component or metric.
     *
     * @param handle          state handle to set activation for
     * @param activationState new activation state to set
     * @return the result of the manipulation
     */
    ResultResponse setComponentActivation(String handle, ComponentActivation activationState);

    /**
     * Set the metric to a specific state to trigger the setting of the ActivationState.
     *
     * @param sequenceId during which the manipulation was performed
     * @param handle state handle to set the status of the metric for
     * @param category of the metric to set the status for
     * @param activation the activation state the metric should have, after manipulation
     * @return the result of the manipulation
     */
    ResultResponse setMetricStatus(
            String sequenceId, String handle, MetricCategory category, ComponentActivation activation);

    /**
     * Trigger a descriptor update for the provided descriptor handle.
     *
     * @param handle handle of the descriptor to trigger an Update for.
     * @return the result of the manipulation
     */
    ResultResponse triggerDescriptorUpdate(String handle);

    /**
     * Trigger a descriptor update for the provided descriptor handles.
     *
     * @param handles list of descriptor handles to trigger an update for.
     * @return the result of the manipulation
     */
    ResultResponse triggerDescriptorUpdate(List<String> handles);

    /**
     * Trigger a descriptor update for some descriptor (chosen by the device).
     *
     * @return the result of the manipulation
     */
    ResultResponse triggerAnyDescriptorUpdate();

    /**
     * Trigger a report message of the provided type.
     *
     * @param reportType type of report a message should be triggered for.
     * @return the result of the manipulation
     */
    ResultResponse triggerReport(QName reportType);
}
