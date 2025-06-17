/*
 * This Source Code Form is subject to the terms of the "SDCcc non-commercial use license".
 *
 * Copyright (C) 2025 Draegerwerk AG & Co. KGaA
 */

package com.draeger.medical.sdccc.manipulation;

import com.draeger.medical.sdccc.manipulation.guice.InteractionFactory;
import com.draeger.medical.t2iapi.ResponseTypes;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.FilterInputStream;
import java.util.Collections;
import java.util.List;
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

    /**
     * Create FallbackManipulations.
     *
     * @param interactionFactory to create user interactions
     */
    @Inject
    public FallbackManipulations(final InteractionFactory interactionFactory) {
        this.interactionFactory = interactionFactory;
    }

    @Override
    public ResultResponse setLocationDetail(final LocationDetail locationDetail) {
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
        return ResultResponse.from(
                interactionResult ? ResponseTypes.Result.RESULT_SUCCESS : ResponseTypes.Result.RESULT_FAIL);
    }

    @Override
    public ManipulationResponse<List<String>> getRemovableDescriptorsOfClass() {
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
            return ManipulationResponse.from(ResponseTypes.Result.RESULT_FAIL, Collections.emptyList());
        }
        return ManipulationResponse.from(ResponseTypes.Result.RESULT_SUCCESS, List.of(data.split(" ")));
    }

    @Override
    public ManipulationResponse<List<String>> getRemovableDescriptorsOfClass(
            final Class<? extends AbstractDescriptor> descriptorType) {
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
            return ManipulationResponse.from(ResponseTypes.Result.RESULT_FAIL, Collections.emptyList());
        }
        return ManipulationResponse.from(ResponseTypes.Result.RESULT_SUCCESS, List.of(data.split(" ")));
    }

    @Override
    public ResultResponse removeDescriptor(final String handle) {
        final var interactionMessage = String.format("Remove the descriptor %s from the MDIB.", handle);
        final var interactionResult = interactionFactory
                .createUserInteraction(new FilterInputStream(System.in) {
                    @Override
                    public void close() {}
                })
                .displayYesNoUserInteraction(interactionMessage);
        return ResultResponse.from(
                interactionResult ? ResponseTypes.Result.RESULT_SUCCESS : ResponseTypes.Result.RESULT_FAIL);
    }

    @Override
    public ResultResponse insertDescriptor(final String handle) {
        final var interactionMessage = String.format("Insert the descriptor %s into the MDIB.", handle);
        final var interactionResult = interactionFactory
                .createUserInteraction(new FilterInputStream(System.in) {
                    @Override
                    public void close() {}
                })
                .displayYesNoUserInteraction(interactionMessage);
        return ResultResponse.from(
                interactionResult ? ResponseTypes.Result.RESULT_SUCCESS : ResponseTypes.Result.RESULT_FAIL);
    }

    @Override
    public ResultResponse sendHello() {
        final var interactionMessage =
                "Announce the presence of the device in the network via a WS-Discovery Hello message.";
        final var interactionResult = interactionFactory
                .createUserInteraction(new FilterInputStream(System.in) {
                    @Override
                    public void close() {}
                })
                .displayYesNoUserInteraction(interactionMessage);
        return ResultResponse.from(
                interactionResult ? ResponseTypes.Result.RESULT_SUCCESS : ResponseTypes.Result.RESULT_FAIL);
    }

    @Override
    public ManipulationResponse<String> createContextStateWithAssociation(
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
            return ManipulationResponse.fail(null);
        }
        return ManipulationResponse.from(ResponseTypes.Result.RESULT_SUCCESS, data);
    }

    @Override
    public ManipulationResponse<String> createContextStateWithAssocAndBindingMdibVersion(
            final String descriptorHandle, final ContextAssociation association) {
        final var interactionMessage = String.format(
                "For a new or existing context state of the descriptor %s set the context association to %s"
                        + " and set a BindingMdibVersion."
                        + " Provide the state handle of the newly created context state.",
                descriptorHandle, association.value());
        final var data = interactionFactory
                .createUserInteraction(new FilterInputStream(System.in) {
                    @Override
                    public void close() {}
                })
                .displayStringInputUserInteraction(interactionMessage);
        if (data == null || data.isBlank()) {
            return ManipulationResponse.fail(null);
        }
        return ManipulationResponse.from(ResponseTypes.Result.RESULT_SUCCESS, data);
    }

    @Override
    public ManipulationResponse<String> createContextStateWithAssocAndUnbindingMdibVersion(
            final String descriptorHandle, final ContextAssociation association) {
        final var interactionMessage = String.format(
                "For a new or existing context state of the descriptor %s set the context association to %s"
                        + " and set a UnbindingMdibVersion."
                        + " Provide the state handle of the newly created context state.",
                descriptorHandle, association.value());
        final var data = interactionFactory
                .createUserInteraction(new FilterInputStream(System.in) {
                    @Override
                    public void close() {}
                })
                .displayStringInputUserInteraction(interactionMessage);
        if (data == null || data.isBlank()) {
            return ManipulationResponse.fail(null);
        }
        return ManipulationResponse.from(ResponseTypes.Result.RESULT_SUCCESS, data);
    }

    @Override
    public ResultResponse setAlertActivation(final String handle, final AlertActivation activationState) {
        final var interactionMessage =
                String.format("Set activation state for handle %s to %s", handle, activationState.name());
        final var interactionResult = interactionFactory
                .createUserInteraction(new FilterInputStream(System.in) {
                    @Override
                    public void close() {}
                })
                .displayYesNoUserInteraction(interactionMessage);
        return ResultResponse.from(
                interactionResult ? ResponseTypes.Result.RESULT_SUCCESS : ResponseTypes.Result.RESULT_FAIL);
    }

    @Override
    public ResultResponse setAlertConditionPresence(final String handle, final boolean presence) {
        final var interactionMessage =
                String.format("Set the presence attribute for handle %s to %s", handle, presence);
        final var interactionResult = interactionFactory
                .createUserInteraction(new FilterInputStream(System.in) {
                    @Override
                    public void close() {}
                })
                .displayYesNoUserInteraction(interactionMessage);
        return ResultResponse.from(
                interactionResult ? ResponseTypes.Result.RESULT_SUCCESS : ResponseTypes.Result.RESULT_FAIL);
    }

    @Override
    public ResultResponse setSystemSignalActivation(
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
        return ResultResponse.from(
                interactionResult ? ResponseTypes.Result.RESULT_SUCCESS : ResponseTypes.Result.RESULT_FAIL);
    }

    @Override
    public ResultResponse setComponentActivation(final String handle, final ComponentActivation activationState) {
        final var interactionMessage =
                String.format("Set activation state for handle %s to %s", handle, activationState.name());
        final var interactionResult = interactionFactory
                .createUserInteraction(new FilterInputStream(System.in) {
                    @Override
                    public void close() {}
                })
                .displayYesNoUserInteraction(interactionMessage);
        return ResultResponse.from(
                interactionResult ? ResponseTypes.Result.RESULT_SUCCESS : ResponseTypes.Result.RESULT_FAIL);
    }

    @Override
    public ResultResponse setMetricStatus(
            final String sequenceId,
            final String handle,
            final MetricCategory category,
            final ComponentActivation activation) {

        final var metricStatusString = getMetricStatus(activation);
        if (metricStatusString.isEmpty()) return ResultResponse.from(ResponseTypes.Result.RESULT_FAIL);

        final var interactionMessage = String.format(metricStatusString, handle, category);
        final var interactionResult = interactionFactory
                .createUserInteraction(new FilterInputStream(System.in) {
                    @Override
                    public void close() {}
                })
                .displayYesNoUserInteraction(interactionMessage);
        return ResultResponse.from(
                interactionResult ? ResponseTypes.Result.RESULT_SUCCESS : ResponseTypes.Result.RESULT_FAIL);
    }

    @Override
    public ResultResponse triggerDescriptorUpdate(final String handle) {
        final var triggerReportString = "Trigger a descriptor update for handle %s";
        final var interactionMessage = String.format(triggerReportString, handle);
        final var interactionResult = interactionFactory
                .createUserInteraction(new FilterInputStream(System.in) {
                    @Override
                    public void close() {}
                })
                .displayYesNoUserInteraction(interactionMessage);
        return ResultResponse.from(
                interactionResult ? ResponseTypes.Result.RESULT_SUCCESS : ResponseTypes.Result.RESULT_FAIL);
    }

    @Override
    public ResultResponse triggerDescriptorUpdate(final List<String> handles) {
        final var triggerReportString = "Trigger a descriptor update for handles %s";
        final var interactionMessage = String.format(triggerReportString, String.join(", ", handles));
        final var interactionResult = interactionFactory
                .createUserInteraction(new FilterInputStream(System.in) {
                    @Override
                    public void close() {}
                })
                .displayYesNoUserInteraction(interactionMessage);
        return ResultResponse.from(
                interactionResult ? ResponseTypes.Result.RESULT_SUCCESS : ResponseTypes.Result.RESULT_FAIL);
    }

    @Override
    public ResultResponse triggerAnyDescriptorUpdate() {
        final var interactionMessage = "Trigger a descriptor update for some descriptor";
        final var interactionResult = interactionFactory
                .createUserInteraction(new FilterInputStream(System.in) {
                    @Override
                    public void close() {}
                })
                .displayYesNoUserInteraction(interactionMessage);
        return ResultResponse.from(
                interactionResult ? ResponseTypes.Result.RESULT_SUCCESS : ResponseTypes.Result.RESULT_FAIL);
    }

    @Override
    public ResultResponse triggerReport(final QName reportType) {
        final var triggerReportString = "Trigger a report for type %s";
        final var interactionMessage = String.format(triggerReportString, reportType);
        final var interactionResult = interactionFactory
                .createUserInteraction(new FilterInputStream(System.in) {
                    @Override
                    public void close() {}
                })
                .displayYesNoUserInteraction(interactionMessage);
        return ResultResponse.from(
                interactionResult ? ResponseTypes.Result.RESULT_SUCCESS : ResponseTypes.Result.RESULT_FAIL);
    }

    public InteractionFactory getInteractionFactory() {
        return interactionFactory;
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
