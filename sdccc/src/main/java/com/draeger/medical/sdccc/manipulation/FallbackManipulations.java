/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2023 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.manipulation;

import com.draeger.medical.sdccc.manipulation.guice.InteractionFactory;
import com.draeger.medical.t2iapi.ResponseTypes;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.FilterInputStream;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.xml.namespace.QName;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.somda.sdc.biceps.model.participant.AbstractDescriptor;
import org.somda.sdc.biceps.model.participant.AlertActivation;
import org.somda.sdc.biceps.model.participant.AlertSignalManifestation;
import org.somda.sdc.biceps.model.participant.ComponentActivation;
import org.somda.sdc.biceps.model.participant.ContextAssociation;
import org.somda.sdc.biceps.model.participant.LocationDetail;
import org.somda.sdc.biceps.model.participant.MetricCategory;

/**
 * Fallback manipulations directly prompting the user to perform the action.
 */
@Singleton
public class FallbackManipulations implements Manipulations {

    private final InteractionFactory interactionFactory;

    @Inject
    FallbackManipulations(final InteractionFactory interactionFactory) {
        this.interactionFactory = interactionFactory;
    }

    @Override
    public ResponseTypes.Result setLocationDetail(final LocationDetail locationDetail) {
        final var interactionMessage = String.format(
                "Set location for currently associated location context to %s",
                // better formatting
                ReflectionToStringBuilder.toString(locationDetail, ToStringStyle.SHORT_PREFIX_STYLE));
        final var interactionResult = interactionFactory
                .createUserInteraction(new FilterInputStream(System.in) {
                    @Override
                    public void close() {}
                })
                .displayYesNoUserInteraction(interactionMessage);
        return interactionResult ? ResponseTypes.Result.RESULT_SUCCESS : ResponseTypes.Result.RESULT_FAIL;
    }

    @Override
    public List<String> getRemovableDescriptorsOfClass() {
        final var interactionMessage = "Provide a whitespace-separated list of removable descriptors."
                + " Includes handles which have been removed already and can be reinserted."
                + " Handles must stay the same once reinserted into the MDIB."
                + " The handles shall be representative of the devices capabilities to remove"
                + " descriptors (at least one of every possible kind).";
        final var data = interactionFactory
                .createUserInteraction(new FilterInputStream(System.in) {
                    @Override
                    public void close() {}
                })
                .displayStringInputUserInteraction(interactionMessage);
        if (data == null || data.isBlank()) {
            return Collections.emptyList();
        }
        return List.of(data.split(" "));
    }

    @Override
    public List<String> getRemovableDescriptorsOfClass(final Class<? extends AbstractDescriptor> descriptorType) {
        final var interactionMessage = "Provide a whitespace-separated list of those removable descriptors"
                + " that are of type " + descriptorType.getName() + " (at least one of every possible kind)."
                + " Includes handles which have been removed already and can be reinserted."
                + " Handles must stay the same once reinserted into the MDIB.";
        final var data = interactionFactory
                .createUserInteraction(new FilterInputStream(System.in) {
                    @Override
                    public void close() {}
                })
                .displayStringInputUserInteraction(interactionMessage);
        if (data == null || data.isBlank()) {
            return Collections.emptyList();
        }
        return List.of(data.split(" "));
    }

    @Override
    public ResponseTypes.Result removeDescriptor(final String handle) {
        final var interactionMessage = String.format("Remove the descriptor %s from the MDIB.", handle);
        final var interactionResult = interactionFactory
                .createUserInteraction(new FilterInputStream(System.in) {
                    @Override
                    public void close() {}
                })
                .displayYesNoUserInteraction(interactionMessage);
        return interactionResult ? ResponseTypes.Result.RESULT_SUCCESS : ResponseTypes.Result.RESULT_FAIL;
    }

    @Override
    public ResponseTypes.Result insertDescriptor(final String handle) {
        final var interactionMessage = String.format("Insert the descriptor %s into the MDIB.", handle);
        final var interactionResult = interactionFactory
                .createUserInteraction(new FilterInputStream(System.in) {
                    @Override
                    public void close() {}
                })
                .displayYesNoUserInteraction(interactionMessage);
        return interactionResult ? ResponseTypes.Result.RESULT_SUCCESS : ResponseTypes.Result.RESULT_FAIL;
    }

    @Override
    public ResponseTypes.Result sendHello() {
        final var interactionMessage =
                "Announce the presence of the device in the network via a WS-Discovery Hello message.";
        final var interactionResult = interactionFactory
                .createUserInteraction(new FilterInputStream(System.in) {
                    @Override
                    public void close() {}
                })
                .displayYesNoUserInteraction(interactionMessage);
        return interactionResult ? ResponseTypes.Result.RESULT_SUCCESS : ResponseTypes.Result.RESULT_FAIL;
    }

    @Override
    public Optional<String> createContextStateWithAssociation(
            final String descriptorHandle, final ContextAssociation association) {
        final var interactionMessage = String.format(
                "Create a NEW context state for the descriptor %s and set the context association to %s."
                        + " Provide the state handle of the newly created context state.",
                descriptorHandle, association.value());
        final var data = interactionFactory
                .createUserInteraction(new FilterInputStream(System.in) {
                    @Override
                    public void close() {}
                })
                .displayStringInputUserInteraction(interactionMessage);
        if (data == null || data.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(data);
    }

    @Override
    public ResponseTypes.Result setAlertActivation(final String handle, final AlertActivation activationState) {
        final var interactionMessage =
                String.format("Set activation state for handle %s to %s", handle, activationState.name());
        final var interactionResult = interactionFactory
                .createUserInteraction(new FilterInputStream(System.in) {
                    @Override
                    public void close() {}
                })
                .displayYesNoUserInteraction(interactionMessage);
        return interactionResult ? ResponseTypes.Result.RESULT_SUCCESS : ResponseTypes.Result.RESULT_FAIL;
    }

    @Override
    public ResponseTypes.Result setAlertConditionPresence(final String handle, final boolean presence) {
        final var interactionMessage =
                String.format("Set the presence attribute for handle %s to %s", handle, presence);
        final var interactionResult = interactionFactory
                .createUserInteraction(new FilterInputStream(System.in) {
                    @Override
                    public void close() {}
                })
                .displayYesNoUserInteraction(interactionMessage);
        return interactionResult ? ResponseTypes.Result.RESULT_SUCCESS : ResponseTypes.Result.RESULT_FAIL;
    }

    @Override
    public ResponseTypes.Result setSystemSignalActivation(
            final String handle, final AlertSignalManifestation manifestation, final AlertActivation activation) {
        final var interactionMessage = String.format(
                "Set the system signal activation for handle %s and manifestation %s to %s",
                handle, manifestation, activation);
        final var interactionResult = interactionFactory
                .createUserInteraction(new FilterInputStream(System.in) {
                    @Override
                    public void close() {}
                })
                .displayYesNoUserInteraction(interactionMessage);
        return interactionResult ? ResponseTypes.Result.RESULT_SUCCESS : ResponseTypes.Result.RESULT_FAIL;
    }

    @Override
    public ResponseTypes.Result setComponentActivation(final String handle, final ComponentActivation activationState) {
        final var interactionMessage =
                String.format("Set activation state for handle %s to %s", handle, activationState.name());
        final var interactionResult = interactionFactory
                .createUserInteraction(new FilterInputStream(System.in) {
                    @Override
                    public void close() {}
                })
                .displayYesNoUserInteraction(interactionMessage);
        return interactionResult ? ResponseTypes.Result.RESULT_SUCCESS : ResponseTypes.Result.RESULT_FAIL;
    }

    @Override
    public ResponseTypes.Result setMetricStatus(
            final String handle, final MetricCategory category, final ComponentActivation activation) {

        final var metricStatusString = getMetricStatus(activation);
        if (metricStatusString.isEmpty()) return ResponseTypes.Result.RESULT_FAIL;

        final var interactionMessage = String.format(metricStatusString, handle, category);
        final var interactionResult = interactionFactory
                .createUserInteraction(new FilterInputStream(System.in) {
                    @Override
                    public void close() {}
                })
                .displayYesNoUserInteraction(interactionMessage);
        return interactionResult ? ResponseTypes.Result.RESULT_SUCCESS : ResponseTypes.Result.RESULT_FAIL;
    }

    @Override
    public ResponseTypes.Result triggerDescriptorUpdate(final String handle) {
        final var triggerReportString = "Trigger a descriptor update for handle %s";
        final var interactionMessage = String.format(triggerReportString, handle);
        final var interactionResult = interactionFactory
                .createUserInteraction(new FilterInputStream(System.in) {
                    @Override
                    public void close() {}
                })
                .displayYesNoUserInteraction(interactionMessage);
        return interactionResult ? ResponseTypes.Result.RESULT_SUCCESS : ResponseTypes.Result.RESULT_FAIL;
    }

    @Override
    public ResponseTypes.Result triggerReport(final QName reportType) {
        final var triggerReportString = "Trigger a report for type %s";
        final var interactionMessage = String.format(triggerReportString, reportType);
        final var interactionResult = interactionFactory
                .createUserInteraction(new FilterInputStream(System.in) {
                    @Override
                    public void close() {}
                })
                .displayYesNoUserInteraction(interactionMessage);
        return interactionResult ? ResponseTypes.Result.RESULT_SUCCESS : ResponseTypes.Result.RESULT_FAIL;
    }

    private String getMetricStatus(final ComponentActivation activation) {
        return switch (activation) {
            case ON -> "The measurement/setting for the metric with handle %s and category %s is being performed/applied.";
            case NOT_RDY -> "The measurement/setting for the metric with handle %s and category %s is"
                    + " currently initializing.";
            case STND_BY -> "The measurement/setting for the metric with handle %s and category %s has been initialized, but"
                    + " is not being performed/applied.";
            case SHTDN -> "The measurement/setting for the metric with handle %s and category %s is currently"
                    + " de-initializing.";
            case OFF -> "The measurement/setting for the metric with handle %s and category %s is not"
                    + " being performed/applied and is de-initialized.";
            case FAIL -> "The measurement/setting for the metric with handle %s and category %s has" + " failed.";
        };
    }
}
