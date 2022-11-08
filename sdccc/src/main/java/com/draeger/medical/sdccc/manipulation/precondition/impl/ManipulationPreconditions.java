/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.manipulation.precondition.impl;

import com.draeger.medical.sdccc.manipulation.Manipulations;
import com.draeger.medical.sdccc.manipulation.precondition.ManipulationPrecondition;
import com.draeger.medical.sdccc.sdcri.testclient.TestClient;
import com.draeger.medical.sdccc.tests.util.ImpliedValueUtil;
import com.draeger.medical.sdccc.util.TestRunObserver;
import com.draeger.medical.t2iapi.ResponseTypes;
import com.google.inject.Injector;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.somda.sdc.biceps.common.MdibEntity;
import org.somda.sdc.biceps.common.access.MdibAccess;
import org.somda.sdc.biceps.model.participant.AbstractAlertState;
import org.somda.sdc.biceps.model.participant.AbstractDeviceComponentDescriptor;
import org.somda.sdc.biceps.model.participant.AbstractDeviceComponentState;
import org.somda.sdc.biceps.model.participant.AbstractMetricDescriptor;
import org.somda.sdc.biceps.model.participant.AbstractMetricState;
import org.somda.sdc.biceps.model.participant.AbstractMultiState;
import org.somda.sdc.biceps.model.participant.AlertActivation;
import org.somda.sdc.biceps.model.participant.AlertConditionDescriptor;
import org.somda.sdc.biceps.model.participant.AlertConditionState;
import org.somda.sdc.biceps.model.participant.AlertSignalDescriptor;
import org.somda.sdc.biceps.model.participant.AlertSignalManifestation;
import org.somda.sdc.biceps.model.participant.AlertSignalState;
import org.somda.sdc.biceps.model.participant.AlertSystemDescriptor;
import org.somda.sdc.biceps.model.participant.AlertSystemState;
import org.somda.sdc.biceps.model.participant.ComponentActivation;
import org.somda.sdc.biceps.model.participant.ContextAssociation;
import org.somda.sdc.biceps.model.participant.LocationContextDescriptor;
import org.somda.sdc.biceps.model.participant.LocationContextState;
import org.somda.sdc.biceps.model.participant.MetricCategory;
import org.somda.sdc.biceps.model.participant.PatientContextDescriptor;
import org.somda.sdc.biceps.model.participant.PatientContextState;
import org.somda.sdc.glue.consumer.SdcRemoteDevice;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A collection of manipulation preconditions.
 */
public class ManipulationPreconditions {

    private static boolean manipulateMetricStatus(final Injector injector, final Logger log,
                                                  final MetricCategory metricCategory,
                                                  final ComponentActivation activationState,
                                                  final ComponentActivation startingActivationState) {
        final var manipulations = injector.getInstance(Manipulations.class);
        final var testClient = injector.getInstance(TestClient.class);
        final var manipulationResults = new HashSet<ResponseTypes.Result>();
        final var metricEntities = testClient.getSdcRemoteDevice()
            .getMdibAccess().findEntitiesByType(AbstractMetricDescriptor.class);
        for (var entity : metricEntities) {
            final var metricDescriptor = entity.getDescriptor(AbstractMetricDescriptor.class);
            final var category = metricDescriptor.orElseThrow().getMetricCategory();
            if (category.equals(metricCategory)) {
                final var metricState = entity.getStates(AbstractMetricState.class).get(0);
                final var handle = metricState.getDescriptorHandle();
                final var resultStartingActivationState =
                    manipulations.setComponentActivation(handle, startingActivationState);
                // change activation state to something other than expected result to ensure the transition
                if (resultStartingActivationState != ResponseTypes.Result.RESULT_SUCCESS) {
                    log.debug("Manipulating the activation state to {} was {} for metric state with handle {}."
                            + " This needs to be successful, to ensure that the metric is set to {} after performing"
                            + " the setMetricStatus manipulation.",
                        startingActivationState, resultStartingActivationState, handle, activationState);
                    return false;
                }
                final var manipulationResult = manipulations.setMetricStatus(handle, category, activationState);
                log.debug("Manipulation was {} for metric state with handle {}", manipulationResult, handle);
                manipulationResults.add(manipulationResult);
            }
        }
        return manipulationResults.contains(ResponseTypes.Result.RESULT_SUCCESS)
            && !manipulationResults.contains(ResponseTypes.Result.RESULT_FAIL)
            && !manipulationResults.contains(ResponseTypes.Result.RESULT_NOT_IMPLEMENTED);
    }

    /**
     * Associates two <em>new</em> patients for every available PatientContextDescriptor present in the provider mdib.
     */
    public static class AssociatePatientsManipulation extends ManipulationPrecondition {

        private static final Logger LOG = LogManager.getLogger(AssociatePatientsManipulation.class);

        /**
         * Creates a patient association precondition.
         */
        public AssociatePatientsManipulation() {
            super(AssociatePatientsManipulation::manipulation);
        }

        /**
         * @return true if successful, false otherwise
         */
        static boolean manipulation(final Injector injector) {
            LOG.info("Executing AssociatePatientsManipulation");
            final var testClient = injector.getInstance(TestClient.class);
            final var manipulations = injector.getInstance(Manipulations.class);
            final var testRunObserver = injector.getInstance(TestRunObserver.class);

            final MdibAccess mdibAccess;
            final SdcRemoteDevice remoteDevice;

            remoteDevice = testClient.getSdcRemoteDevice();
            if (remoteDevice == null) {
                LOG.error("remote device could not be accessed, likely due to a disconnect");
                return false;
            }
            mdibAccess = remoteDevice.getMdibAccess();

            final var patientContextEntities = mdibAccess
                .findEntitiesByType(PatientContextDescriptor.class);

            LOG.info("Associating new patients for {} handle(s)", patientContextEntities.size());
            boolean noEmptyStateEncountered = true;
            for (MdibEntity patientContextEntity : patientContextEntities) {
                // store previously existing handles for comparison
                final var originalStates = patientContextEntity.getStates(PatientContextState.class).stream()
                    .map(AbstractMultiState::getHandle)
                    .collect(Collectors.toSet());
                // associate first new patient
                var newStateHandle = associateNewPatientForHandle(
                    testClient.getSdcRemoteDevice(),
                    manipulations,
                    patientContextEntity.getHandle(),
                    originalStates
                );

                // associate another one if the first one worked out
                if (newStateHandle.isPresent()) {
                    // add new state to known states
                    originalStates.add(newStateHandle.orElseThrow());
                    newStateHandle = associateNewPatientForHandle(
                        testClient.getSdcRemoteDevice(),
                        manipulations,
                        patientContextEntity.getHandle(),
                        originalStates
                    );
                }

                if (newStateHandle.isEmpty()) {
                    testRunObserver.invalidateTestRun(String.format(
                        "Associating a new patient for handle %s failed",
                        patientContextEntity.getHandle()
                    ));
                    noEmptyStateEncountered = false;
                }
            }

            return noEmptyStateEncountered && !patientContextEntities.isEmpty();
        }

        /**
         * Associates a new patient context state for a given descriptor handle.
         *
         * @param device               the patient context state will appear in, used for validation
         * @param manipulations        to call for insertion of state
         * @param handle               of the descriptor to insert a new state for
         * @param previousStateHandles previously present state handles, to ensure new state is actually new
         * @return handle of new valid state, or empty
         */
        static Optional<String> associateNewPatientForHandle(
            final SdcRemoteDevice device,
            final Manipulations manipulations,
            final String handle,
            final Collection<String> previousStateHandles) {
            LOG.debug("Associating new patient for handle {}", handle);
            var stateHandle = manipulations.createContextStateWithAssociation(handle, ContextAssociation.ASSOC);
            if (stateHandle.isEmpty()) {
                LOG.error("Associating new patient failed for handle {}", handle);
                return Optional.empty();
            }
            LOG.debug("New patient created, state handle is {}", stateHandle.orElseThrow());
            final var validState = verifyStatePresentAndAssociated(
                device, handle, stateHandle.orElseThrow(), previousStateHandles
            );
            if (!validState) {
                LOG.error("Validation for new context state {} failed", stateHandle);
                // remove state handle from return value in invalid cases
                stateHandle = Optional.empty();
            }
            return stateHandle;
        }

        /**
         * Verifies that a PatientContextState with the given handle is present, associated and attached to the
         * correct descriptor. Additionally, verifies that the new state handle was not present before executing
         * the manipulation.
         *
         * @param device               to verify mdib for
         * @param descriptorHandle     the state must be attached to
         * @param stateHandle          of state to verify
         * @param previousStateHandles previously present state handles, to ensure new state is actually new
         * @return true if valid, false otherwise
         */
        static boolean verifyStatePresentAndAssociated(
            final SdcRemoteDevice device,
            final String descriptorHandle,
            final String stateHandle,
            final Collection<String> previousStateHandles) {
            final var contextStateOpt = device.getMdibAccess().getState(stateHandle, PatientContextState.class);

            if (contextStateOpt.isEmpty()) {
                return false;
            }
            final var contextState = contextStateOpt.orElseThrow();
            LOG.debug("verifyStatePresentAndAssociated: New context state for handle found. {}", contextState);

            var valid = ContextAssociation.ASSOC.equals(ImpliedValueUtil.getContextAssociation(contextState));
            LOG.info("Validity for {} after association check: {}", stateHandle, valid);
            valid &= descriptorHandle.equals(contextState.getDescriptorHandle());
            LOG.info("Validity for {} after descriptor handle correctness check: {}", stateHandle, valid);
            valid &= !previousStateHandles.contains(stateHandle);
            LOG.info("Validity for {} after checking if state handle is already known: {}", stateHandle, valid);

            return valid;
        }
    }

    /**
     * Set every AbstractDeviceComponent's activation state to OFF if possible.
     */
    public static class AbstractDeviceComponentStateOFFManipulation extends ManipulationPrecondition {

        private static final Logger LOG =
            LogManager.getLogger(ManipulationPreconditions.AbstractDeviceComponentStateOFFManipulation.class);

        /**
         * Creates a AbstractDeviceComponentStateOFFManipulation precondition.
         */
        public AbstractDeviceComponentStateOFFManipulation() {
            super(ManipulationPreconditions.AbstractDeviceComponentStateOFFManipulation::manipulation);
        }

        /**
         * @return true if successful, false otherwise
         */
        static boolean manipulation(final Injector injector) {
            LOG.info("Executing AbstractDeviceComponentStateOFFManipulation");
            final var testClient = injector.getInstance(TestClient.class);
            final var manipulations = injector.getInstance(Manipulations.class);

            final MdibAccess mdibAccess;
            final SdcRemoteDevice remoteDevice;

            remoteDevice = testClient.getSdcRemoteDevice();
            if (remoteDevice == null) {
                LOG.error("remote device could not be accessed, likely due to a disconnect");
                return false;
            }
            mdibAccess = remoteDevice.getMdibAccess();

            final var abstractDeviceComponentEntities = mdibAccess
                .findEntitiesByType(AbstractDeviceComponentDescriptor.class);


            boolean atLeastOneSuccessful = false;
            boolean noFailure = true;
            for (MdibEntity abstractDeviceComponentEntity : abstractDeviceComponentEntities) {
                final var state = abstractDeviceComponentEntity.getFirstState(AbstractDeviceComponentState.class)
                    .orElseThrow();

                final ResponseTypes.Result result =
                    manipulations.setComponentActivation(state.getDescriptorHandle(), ComponentActivation.OFF);

                switch (result) {
                    case RESULT_NOT_SUPPORTED:
                        break;
                    case RESULT_SUCCESS:
                        atLeastOneSuccessful = true;
                        break;
                    default:
                        noFailure = false;
                        break;
                }
            }
            return atLeastOneSuccessful && noFailure;
        }
    }

    /**
     * Associates two <em>new</em> locations for every available LocationContextDescriptor present in the provider mdib.
     */
    public static class AssociateLocationsManipulation extends ManipulationPrecondition {

        private static final Logger LOG = LogManager.getLogger(AssociateLocationsManipulation.class);

        /**
         * Creates a location association precondition.
         */
        public AssociateLocationsManipulation() {
            super(AssociateLocationsManipulation::manipulation);
        }

        /**
         * @return true if successful, false otherwise
         */
        static boolean manipulation(final Injector injector) {
            LOG.info("Executing AssociateLocationsManipulation");
            final var testClient = injector.getInstance(TestClient.class);
            final var manipulations = injector.getInstance(Manipulations.class);
            final var testRunObserver = injector.getInstance(TestRunObserver.class);

            final MdibAccess mdibAccess;
            final SdcRemoteDevice remoteDevice;

            remoteDevice = testClient.getSdcRemoteDevice();
            if (remoteDevice == null) {
                LOG.error("remote device could not be accessed, likely due to a disconnect");
                return false;
            }
            mdibAccess = remoteDevice.getMdibAccess();

            final var locationContextEntities = mdibAccess
                .findEntitiesByType(LocationContextDescriptor.class);

            LOG.info("Associating new locations for {} handle(s)", locationContextEntities.size());
            boolean noEmptyStateEncountered = true;
            for (MdibEntity locationContextEntity : locationContextEntities) {
                // store previously existing handles for comparison
                final var originalStates = locationContextEntity.getStates(LocationContextState.class).stream()
                    .map(AbstractMultiState::getHandle)
                    .collect(Collectors.toSet());
                // associate first new location
                var newStateHandle = associateNewLocationForHandle(
                    testClient.getSdcRemoteDevice(),
                    manipulations,
                    locationContextEntity.getHandle(),
                    originalStates
                );

                // associate another one if the first one worked out
                if (newStateHandle.isPresent()) {
                    // add new state to known states
                    originalStates.add(newStateHandle.orElseThrow());
                    newStateHandle = associateNewLocationForHandle(
                        testClient.getSdcRemoteDevice(),
                        manipulations,
                        locationContextEntity.getHandle(),
                        originalStates
                    );
                }

                if (newStateHandle.isEmpty()) {
                    testRunObserver.invalidateTestRun(String.format(
                        "Associating a new location for handle %s failed",
                        locationContextEntity.getHandle()
                    ));
                    noEmptyStateEncountered = false;
                }
            }
            return noEmptyStateEncountered && !locationContextEntities.isEmpty();
        }

        /**
         * Associates a new location context state for a given descriptor handle.
         *
         * @param device               the location context state will appear in, used for validation
         * @param manipulations        to call for insertion of state
         * @param handle               of the descriptor to insert a new state for
         * @param previousStateHandles previously present state handles, to ensure new state is actually new
         * @return handle of new valid state, or empty
         */
        static Optional<String> associateNewLocationForHandle(
            final SdcRemoteDevice device,
            final Manipulations manipulations,
            final String handle,
            final Collection<String> previousStateHandles) {
            LOG.debug("Associating new location for handle {}", handle);
            var stateHandle = manipulations.createContextStateWithAssociation(handle, ContextAssociation.ASSOC);
            if (stateHandle.isEmpty()) {
                LOG.error("Associating new location failed for handle {}", handle);
                return Optional.empty();
            }
            LOG.debug("New location created, state handle is {}", stateHandle.orElseThrow());
            final var validState = verifyStatePresentAndAssociated(
                device, handle, stateHandle.orElseThrow(), previousStateHandles
            );
            if (!validState) {
                LOG.error("Validation for new context state {} failed", stateHandle);
                // remove state handle from return value in invalid cases
                stateHandle = Optional.empty();
            }
            return stateHandle;
        }

        /**
         * Verifies that a LocationContextState with the given handle is present, associated and attached to the
         * correct descriptor. Additionally, verifies that the new state handle was not present before executing
         * the manipulation.
         *
         * @param device               to verify mdib for
         * @param descriptorHandle     the state must be attached to
         * @param stateHandle          of state to verify
         * @param previousStateHandles previously present state handles, to ensure new state is actually new
         * @return true if valid, false otherwise
         */
        static boolean verifyStatePresentAndAssociated(
            final SdcRemoteDevice device,
            final String descriptorHandle,
            final String stateHandle,
            final Collection<String> previousStateHandles) {
            final var contextStateOpt = device.getMdibAccess().getState(stateHandle, LocationContextState.class);

            if (contextStateOpt.isEmpty()) {
                return false;
            }
            final var contextState = contextStateOpt.orElseThrow();
            LOG.debug("verifyStatePresentAndAssociated: New context state for handle found. {}", contextState);

            var valid = ContextAssociation.ASSOC.equals(ImpliedValueUtil.getContextAssociation(contextState));
            LOG.info("Validity for {} after association check: {}", stateHandle, valid);
            valid &= descriptorHandle.equals(contextState.getDescriptorHandle());
            LOG.info("Validity for {} after descriptor handle correctness check: {}", stateHandle, valid);
            valid &= !previousStateHandles.contains(stateHandle);
            LOG.info("Validity for {} after checking if state handle is already known: {}", stateHandle, valid);

            return valid;
        }
    }

    /**
     * Sets every activation state for every alert system state in the provider mdib.
     */
    public static class AlertSystemActivationStateManipulation extends ManipulationPrecondition {

        private static final Logger LOG = LogManager.getLogger(AlertSystemActivationStateManipulation.class);

        /**
         * Creates an alert system activation state precondition.
         */
        public AlertSystemActivationStateManipulation() {
            super(AlertSystemActivationStateManipulation::manipulation);
        }

        /**
         * @return true if successful, false otherwise
         */
        static boolean manipulation(final Injector injector) {
            LOG.info("Executing AlertSystemActivationStateManipulation");
            final var testClient = injector.getInstance(TestClient.class);
            final var manipulations = injector.getInstance(Manipulations.class);
            final var testRunObserver = injector.getInstance(TestRunObserver.class);

            final MdibAccess mdibAccess;
            final SdcRemoteDevice remoteDevice;

            remoteDevice = testClient.getSdcRemoteDevice();
            if (remoteDevice == null) {
                LOG.error("remote device could not be accessed, likely due to a disconnect");
                return false;
            }
            mdibAccess = remoteDevice.getMdibAccess();

            final var alertSystemEntities = mdibAccess
                .findEntitiesByType(AlertSystemDescriptor.class);
            LOG.info("Changing activation state for alert systems for {} handle(s)", alertSystemEntities.size());
            boolean noFailedActivationState = true;
            final var manipulationResults = new HashSet<ResponseTypes.Result>();
            for (MdibEntity alertSystemEntity : alertSystemEntities) {
                // change activation state to On
                manipulationResults.add(changeAlertActivationState(
                    testClient.getSdcRemoteDevice(),
                    manipulations,
                    alertSystemEntity.getHandle(),
                    AlertActivation.ON
                ));
                // change activation state to Psd
                manipulationResults.add(changeAlertActivationState(
                    testClient.getSdcRemoteDevice(),
                    manipulations,
                    alertSystemEntity.getHandle(),
                    AlertActivation.PSD
                ));

                // change activation state to Off
                manipulationResults.add(changeAlertActivationState(
                    testClient.getSdcRemoteDevice(),
                    manipulations,
                    alertSystemEntity.getHandle(),
                    AlertActivation.OFF
                ));
                if (manipulationResults.contains(ResponseTypes.Result.RESULT_FAIL)
                    || manipulationResults.contains(ResponseTypes.Result.RESULT_NOT_IMPLEMENTED)) {
                    testRunObserver.invalidateTestRun(String.format(
                        "Setting the activation state for alert system with handle %s failed",
                        alertSystemEntity.getHandle()
                    ));
                    noFailedActivationState = false;
                }
            }
            return noFailedActivationState && manipulationResults.contains(ResponseTypes.Result.RESULT_SUCCESS);
        }

        /**
         * Change the activationState state for a given alert system.
         *
         * @param device          the alert system state will appear in, used for validation
         * @param manipulations   to call for setting the activationState state of the alert system state
         * @param handle          of the alert system state to set the activationState state
         * @param activationState the activationState state to set
         * @return the result of the manipulation
         */
        static ResponseTypes.Result changeAlertActivationState(
            final SdcRemoteDevice device,
            final Manipulations manipulations,
            final String handle,
            final AlertActivation activationState) {
            LOG.debug("Setting the activation state {} for handle {}", activationState, handle);
            var manipulationResult = manipulations.setAlertActivation(handle, activationState);
            if (manipulationResult != ResponseTypes.Result.RESULT_SUCCESS) {
                LOG.error("Setting the activation state {} for handle {} failed", activationState, handle);
            } else if (!verifyStatePresentAndAlertSet(device, handle, activationState)) {
                LOG.error("Validation for alert system {} failed", handle);
                manipulationResult = ResponseTypes.Result.RESULT_FAIL;
            }
            return manipulationResult;
        }

        /**
         * Verifies that the AlertSystem with the given handle exists and the activation state is set as expected.
         *
         * @param device          to verify mdib for
         * @param stateHandle     of state to verify
         * @param activationState the expected activation state
         * @return true if valid, false otherwise
         */
        static boolean verifyStatePresentAndAlertSet(
            final SdcRemoteDevice device,
            final String stateHandle,
            final AlertActivation activationState) {
            final var alertSystemStateOpt = device.getMdibAccess().getState(stateHandle, AlertSystemState.class);

            if (alertSystemStateOpt.isEmpty()) {
                return false;
            }
            final var alertSystemState = alertSystemStateOpt.orElseThrow();
            LOG.debug("verifyStatePresentAndAlertSet: The AlertSystemState for the given handle found. {}",
                alertSystemState);

            final boolean valid = activationState.equals(alertSystemState.getActivationState());
            LOG.info("Validity for {} after activation state is set check: {}", stateHandle, valid);
            return valid;
        }
    }

    /**
     * Sets the presence attribute for every alert condition state in the provider mdib.
     */
    public static class AlertConditionPresenceManipulation extends ManipulationPrecondition {

        private static final Logger LOG = LogManager.getLogger(AlertConditionPresenceManipulation.class);

        /**
         * Creates an alert condition presence precondition.
         */
        public AlertConditionPresenceManipulation() {
            super(AlertConditionPresenceManipulation::manipulation);
        }

        /**
         * @return true if successful, false otherwise
         */
        static boolean manipulation(final Injector injector) {
            LOG.info("Executing AlertConditionPresenceManipulation");
            final var testClient = injector.getInstance(TestClient.class);
            final var manipulations = injector.getInstance(Manipulations.class);

            final MdibAccess mdibAccess;
            final SdcRemoteDevice remoteDevice;

            remoteDevice = testClient.getSdcRemoteDevice();
            if (remoteDevice == null) {
                LOG.error("remote device could not be accessed, likely due to a disconnect");
                return false;
            }
            mdibAccess = remoteDevice.getMdibAccess();

            final var alertConditionEntities = mdibAccess
                .findEntitiesByType(AlertConditionDescriptor.class);
            LOG.info("Changing the presence attribute for alert condition states for {} handle(s)",
                alertConditionEntities.size());

            boolean manipulationSuccessful = false;
            for (MdibEntity alertConditionEntity : alertConditionEntities) {
                final var handle = alertConditionEntity.getHandle();
                final var parentHandle = alertConditionEntity.getParent().orElseThrow();
                // set the alert activation of the alert condition and the parent alert system to off, to see if they
                // turn on, when presence of the alert condition is true
                final var alertConditionStateResult =
                    changeActivationState(testClient.getSdcRemoteDevice(), manipulations, handle, AlertActivation.OFF);
                final var alertSystemStateResult =
                    changeActivationState(testClient.getSdcRemoteDevice(), manipulations, parentHandle,
                        AlertActivation.OFF);
                final var presenceTrueResult = changePresence(
                    testClient.getSdcRemoteDevice(),
                    manipulations,
                    handle,
                    true
                );
                if (alertConditionStateResult == ResponseTypes.Result.RESULT_SUCCESS
                    && alertSystemStateResult == ResponseTypes.Result.RESULT_SUCCESS
                    && presenceTrueResult == ResponseTypes.Result.RESULT_SUCCESS) {
                    manipulationSuccessful = true;
                }
            }
            return manipulationSuccessful;
        }

        /**
         * Change the presence attribute for a given alert condition state.
         *
         * @param device        the alert condition state will appear in, used for validation
         * @param manipulations to call for setting the presence attribute of the alert condition state
         * @param handle        of the alert condition state to set the presence attribute
         * @param presence      the presence attribute to set
         * @return the result of the manipulation
         */
        static ResponseTypes.Result changePresence(
            final SdcRemoteDevice device,
            final Manipulations manipulations,
            final String handle,
            final boolean presence) {
            LOG.debug("Setting the presence attribute {} for handle {}", presence, handle);
            var manipulationResult = manipulations.setAlertConditionPresence(handle, presence);
            if (manipulationResult != ResponseTypes.Result.RESULT_SUCCESS) {
                LOG.error("Setting the presence attribute {} for handle {} failed", presence, handle);
            } else if (!verifyStatePresentAndPresenceSet(device, handle, presence)) {
                LOG.error("Validation for alert condition state {} failed", handle);
                manipulationResult = ResponseTypes.Result.RESULT_FAIL;
            }
            return manipulationResult;
        }

        /**
         * Change the presence attribute for a given alert condition state.
         *
         * @param device        the alert condition state will appear in, used for validation
         * @param manipulations to call for setting the presence attribute of the alert condition state
         * @param handle        of the alert condition state to set the presence attribute
         * @param activation    the alert activation attribute to set
         * @return the result of the manipulation
         */
        static ResponseTypes.Result changeActivationState(final SdcRemoteDevice device,
                                                          final Manipulations manipulations,
                                                          final String handle,
                                                          final AlertActivation activation) {
            LOG.debug("Setting the activation state {} for handle {}", activation, handle);
            var manipulationResult = manipulations.setAlertActivation(handle, activation);
            if (manipulationResult != ResponseTypes.Result.RESULT_SUCCESS) {
                LOG.error("Setting the activation state {} for handle {} failed", activation, handle);
            } else if (!verifyStatePresentAndActivationState(device, handle, activation)) {
                LOG.error("Validation for state with handle {} failed", handle);
                manipulationResult = ResponseTypes.Result.RESULT_FAIL;
            }
            return manipulationResult;
        }

        /**
         * Verifies that the alert condition state with the given handle exists and the presence
         * attribute is set as expected.
         *
         * @param device      to verify mdib for
         * @param stateHandle of state to verify
         * @param presence    the expected presence attribute value
         * @return true if valid, false otherwise
         */
        static boolean verifyStatePresentAndPresenceSet(
            final SdcRemoteDevice device,
            final String stateHandle,
            final boolean presence) {
            final var alertConditionStateOpt = device.getMdibAccess().getState(stateHandle, AlertConditionState.class);

            if (alertConditionStateOpt.isEmpty()) {
                return false;
            }
            final var alertConditionState = alertConditionStateOpt.orElseThrow();
            LOG.debug("verifyStatePresentAndPresenceSet: The alert condition state for the given handle found. {}",
                alertConditionState);

            final var valid = ImpliedValueUtil.isPresence(alertConditionState);
            LOG.info("Validity for {} after presence attribute is set check: {}", stateHandle, valid);
            return valid == presence;
        }

        /**
         * Verifies that the alert state with the given handle exists and the alert activation is set as expected.
         *
         * @param device      to verify mdib for
         * @param stateHandle of state to verify
         * @param activation  the expected alert activation attribute value
         * @return true if valid, false otherwise
         */
        static boolean verifyStatePresentAndActivationState(final SdcRemoteDevice device,
                                                            final String stateHandle,
                                                            final AlertActivation activation) {
            final var abstractAlertStateOptional =
                device.getMdibAccess().getState(stateHandle, AbstractAlertState.class);

            if (abstractAlertStateOptional.isEmpty()) {
                return false;
            }
            final var abstractAlertState = abstractAlertStateOptional.orElseThrow();
            LOG.debug("verifyStatePresentAndActivationState: The alert state for the given handle found. {}",
                abstractAlertState);

            final boolean valid = activation.equals(abstractAlertState.getActivationState());
            LOG.info("Validity for {} after activation state is set check: {}", stateHandle, valid);
            return valid;

        }
    }

    /**
     * Sets a system signal activation for every manifestation for every alert system state in the provider mdib and
     * changes the activation state of the alert signals.
     */
    public static class SystemSignalActivationManipulation extends ManipulationPrecondition {

        private static final Logger LOG = LogManager.getLogger(SystemSignalActivationManipulation.class);

        /**
         * Creates a system signal activation precondition.
         */
        public SystemSignalActivationManipulation() {
            super(SystemSignalActivationManipulation::manipulation);
        }

        /**
         * @return true if successful, false otherwise
         */
        static boolean manipulation(final Injector injector) {
            LOG.info("Executing SystemSignalActivationManipulation");
            final var testClient = injector.getInstance(TestClient.class);
            final var manipulations = injector.getInstance(Manipulations.class);
            final var testRunObserver = injector.getInstance(TestRunObserver.class);
            final var alertSystemEntities = testClient.getSdcRemoteDevice()
                .getMdibAccess().findEntitiesByType(AlertSystemDescriptor.class);
            LOG.info("Changing the system signal activation for alert system states for {} handle(s)",
                alertSystemEntities.size());
            for (MdibEntity alertSystemEntity : alertSystemEntities) {

                var systemSignalActivation = setSystemSignalActivation(testClient.getSdcRemoteDevice(),
                    manipulations, alertSystemEntity.getHandle(), AlertSignalManifestation.AUD,
                    getChildAlertSignalsForManifestation(alertSystemEntity, testClient.getSdcRemoteDevice(),
                        AlertSignalManifestation.AUD), testRunObserver);

                if (systemSignalActivation) {
                    systemSignalActivation = setSystemSignalActivation(testClient.getSdcRemoteDevice(), manipulations,
                        alertSystemEntity.getHandle(), AlertSignalManifestation.VIS,
                        getChildAlertSignalsForManifestation(alertSystemEntity, testClient.getSdcRemoteDevice(),
                            AlertSignalManifestation.VIS), testRunObserver);
                }

                if (systemSignalActivation) {
                    systemSignalActivation = setSystemSignalActivation(testClient.getSdcRemoteDevice(), manipulations,
                        alertSystemEntity.getHandle(), AlertSignalManifestation.TAN,
                        getChildAlertSignalsForManifestation(alertSystemEntity, testClient.getSdcRemoteDevice(),
                            AlertSignalManifestation.TAN), testRunObserver);
                }

                if (systemSignalActivation) {
                    systemSignalActivation = setSystemSignalActivation(testClient.getSdcRemoteDevice(), manipulations,
                        alertSystemEntity.getHandle(), AlertSignalManifestation.OTH,
                        getChildAlertSignalsForManifestation(alertSystemEntity, testClient.getSdcRemoteDevice(),
                            AlertSignalManifestation.OTH), testRunObserver);
                }

                if (!systemSignalActivation) {
                    testRunObserver.invalidateTestRun(String.format(
                        "Setting the system signal activation for alert system state with handle %s failed",
                        alertSystemEntity.getHandle()
                    ));
                    return false;
                }
            }
            return !alertSystemEntities.isEmpty();
        }

        /**
         * Retrieves a list of alert signals for an alert system with the given manifestation.
         *
         * @param entity          of the alert system
         * @param sdcRemoteDevice the alert signals will appear in
         * @param manifestation   of the system signal activation and alert signals
         * @return the alert signals with the given manifestation of the alert system
         */
        static List<AlertSignalState> getChildAlertSignalsForManifestation(
            final MdibEntity entity,
            final SdcRemoteDevice sdcRemoteDevice,
            final AlertSignalManifestation manifestation) {
            final var childAlertSignals = new ArrayList<AlertSignalState>();
            for (var childHandle : entity.getChildren()) {
                final var childAlertSignal = sdcRemoteDevice.getMdibAccess().getDescriptor(childHandle,
                    AlertSignalDescriptor.class);
                if (childAlertSignal.isPresent()
                    && childAlertSignal.orElseThrow().getManifestation().equals(manifestation)) {
                    final var childAlertSignalState = sdcRemoteDevice.getMdibAccess()
                        .getState(childHandle, AlertSignalState.class);
                    childAlertSignalState.ifPresent(childAlertSignals::add);
                }
            }
            return childAlertSignals;
        }

        /**
         * Change the system signal activation attribute for a given alert system state and the activation state of
         * the alert signals of that alert system.
         *
         * @param sdcRemoteDevice   the alert system and alert signals will appear in, used for validation
         * @param manipulations     to call for setting the system signal activation attribute of the alert system
         * @param handle            of the alert system state to set the system signal activation attribute
         * @param manifestation     the manifestation of the system signal activation
         * @param childAlertSignals the alert signals of the alert system
         * @param testRunObserver   to register unexpected failures during test run
         * @return true if successful, false otherwise
         */
        static boolean setSystemSignalActivation(final SdcRemoteDevice sdcRemoteDevice,
                                                 final Manipulations manipulations, final String handle,
                                                 final AlertSignalManifestation manifestation,
                                                 final List<AlertSignalState> childAlertSignals,
                                                 final TestRunObserver testRunObserver) {
            // change alert activation attribute to ON
            final var manipulationResults = new HashSet<ResponseTypes.Result>();
            manipulationResults.add(changeAlertActivation(
                sdcRemoteDevice,
                manipulations,
                handle,
                manifestation,
                AlertActivation.ON,
                childAlertSignals
            ));

            // change alert activation attribute to PSD
            manipulationResults.add(changeAlertActivation(
                sdcRemoteDevice,
                manipulations,
                handle,
                manifestation,
                AlertActivation.PSD,
                childAlertSignals));

            // change alert activation attribute to OFF
            manipulationResults.add(changeAlertActivation(
                sdcRemoteDevice,
                manipulations,
                handle,
                manifestation,
                AlertActivation.OFF,
                childAlertSignals));

            if (manipulationResults.contains(ResponseTypes.Result.RESULT_FAIL)
                || manipulationResults.contains(ResponseTypes.Result.RESULT_NOT_IMPLEMENTED)) {
                testRunObserver.invalidateTestRun(String.format(
                    "Setting the system signal activation for alert system state with handle %s failed",
                    handle
                ));
                return false;
            }
            return manipulationResults.contains(ResponseTypes.Result.RESULT_SUCCESS);
        }

        /**
         * Change the system signal activation attribute for a given alert system state and the activation state of
         * the alert signals of that alert system.
         *
         * @param device            the alert system and alert signals will appear in, used for validation
         * @param manipulations     to call for setting the system signal activation attribute of the alert system
         * @param handle            of the alert system state to set the system signal activation attribute
         * @param manifestation     the manifestation of the system signal activation
         * @param activation        the activation state of the system signal activation
         * @param childAlertSignals the alert signals of the alert system
         * @return the result of the manipulation
         */
        static ResponseTypes.Result changeAlertActivation(
            final SdcRemoteDevice device,
            final Manipulations manipulations,
            final String handle,
            final AlertSignalManifestation manifestation,
            final AlertActivation activation,
            final List<AlertSignalState> childAlertSignals) {

            LOG.debug("Setting the system signal activation attribute {} for handle {}", activation, handle);

            var manipulationResult = manipulations.setSystemSignalActivation(handle, manifestation, activation);
            if (manipulationResult != ResponseTypes.Result.RESULT_SUCCESS
                && manipulationResult != ResponseTypes.Result.RESULT_NOT_SUPPORTED) {
                LOG.error("Setting the system signal activation attribute {} for handle {} failed",
                    activation, handle);
            }

            if (manipulationResult == ResponseTypes.Result.RESULT_SUCCESS
                && !verifySystemSignalActivationPresent(device, handle, manifestation, activation)) {
                LOG.error("Validation for alert system state {} failed", handle);
                manipulationResult = ResponseTypes.Result.RESULT_FAIL;
            }

            for (var child : childAlertSignals) {
                final var childActivation = manipulations.setAlertActivation(
                    child.getDescriptorHandle(), activation);
                if (childActivation != ResponseTypes.Result.RESULT_SUCCESS
                    && childActivation != ResponseTypes.Result.RESULT_NOT_SUPPORTED) {
                    LOG.error("Setting the activation attribute {} for handle {} failed", activation,
                        child.getDescriptorHandle());
                    manipulationResult = ResponseTypes.Result.RESULT_FAIL;
                }
                if (childActivation == ResponseTypes.Result.RESULT_SUCCESS
                    && !verifyStatePresentAndActivationSet(device, handle, activation)) {
                    LOG.error("Validation for alert signal state {} failed", handle);
                    manipulationResult = ResponseTypes.Result.RESULT_FAIL;
                }
            }

            return manipulationResult;
        }

        /**
         * Verifies that the alert system with the given handle exists and the system signal activation is set as
         * expected.
         *
         * @param device        to verify mdib for
         * @param handle        of state to verify
         * @param manifestation of the system signal activation
         * @param activation    of the system signal activation
         * @return true if valid, false otherwise
         */
        static boolean verifySystemSignalActivationPresent(final SdcRemoteDevice device,
                                                           final String handle,
                                                           final AlertSignalManifestation manifestation,
                                                           final AlertActivation activation) {
            final var alertSystemStateOpt = device.getMdibAccess().getState(handle, AlertSystemState.class);

            if (alertSystemStateOpt.isEmpty()) {
                return false;
            }
            final var alertSystemState = alertSystemStateOpt.orElseThrow();
            LOG.debug("verifySystemSignalActivationPresent: The alert system state for the given handle found. {}",
                alertSystemState);

            final var valid = alertSystemState.getSystemSignalActivation().stream().filter(systemSignalActivation ->
                    systemSignalActivation.getManifestation().equals(manifestation)).collect(Collectors.toList())
                .stream().anyMatch(systemSignalActivation -> systemSignalActivation.getState().equals(activation));
            LOG.info("Validity for {} after system signal activation attribute is set check: {}", handle, valid);
            return valid;
        }

        /**
         * Verifies that the alert signal state with the given handle exists and the activation state
         * attribute is set as expected.
         *
         * @param device      to verify mdib for
         * @param stateHandle of state to verify
         * @param activation  of the alert signal activation
         * @return true if valid, false otherwise
         */
        static boolean verifyStatePresentAndActivationSet(
            final SdcRemoteDevice device,
            final String stateHandle,
            final AlertActivation activation) {
            final var alertSignalStateOpt = device.getMdibAccess().getState(stateHandle, AlertSignalState.class);
            if (alertSignalStateOpt.isEmpty()) {
                return false;
            }
            final var alertSignalState = alertSignalStateOpt.orElseThrow();
            LOG.debug("verifyStatePresentAndActivationSet: The alert signal state for the given handle found. {}",
                alertSignalState);

            final var valid = alertSignalState.getActivationState().equals(activation);
            LOG.info("Validity for {} after activation state is set check: {}", stateHandle, valid);
            return valid;
        }
    }

    /**
     * Sets the activation state for every metric with category 'Msrmt' to 'Off' and then the status to 'measurement is
     * being performed' to trigger an activation state change to 'On'.
     */
    public static class MetricStatusManipulationMSRMTActivationStateON extends ManipulationPrecondition {

        private static final Logger LOG = LogManager.getLogger(MetricStatusManipulationMSRMTActivationStateON.class);

        /**
         * Creates a metric status precondition.
         */
        public MetricStatusManipulationMSRMTActivationStateON() {
            super(MetricStatusManipulationMSRMTActivationStateON::manipulation);
        }

        /**
         * @return true if successful, false otherwise
         */
        static boolean manipulation(final Injector injector) {
            final var metricCategory = MetricCategory.MSRMT;
            final var activationState = ComponentActivation.ON;
            final var startingActivationState = ComponentActivation.OFF;
            return manipulateMetricStatus(injector, LOG, metricCategory, activationState, startingActivationState);
        }
    }

    /**
     * Sets the activation state for every metric with category 'Msrmt' to 'On' and then the status to 'measurement
     * initialized, but is not being performed' to trigger an activation state change to 'StndBy'.
     */
    public static class MetricStatusManipulationMSRMTActivationStateSTNDBY extends ManipulationPrecondition {

        private static final Logger LOG = LogManager.getLogger(
            MetricStatusManipulationMSRMTActivationStateSTNDBY.class);

        /**
         * Creates a metric status precondition.
         */
        public MetricStatusManipulationMSRMTActivationStateSTNDBY() {
            super(MetricStatusManipulationMSRMTActivationStateSTNDBY::manipulation);
        }

        /**
         * @return true if successful, false otherwise
         */
        static boolean manipulation(final Injector injector) {
            final var metricCategory = MetricCategory.MSRMT;
            final var activationState = ComponentActivation.STND_BY;
            final var startingActivationState = ComponentActivation.ON;
            return manipulateMetricStatus(injector, LOG, metricCategory, activationState, startingActivationState);
        }
    }

    /**
     * Sets the activation state for every metric with category 'Msrmt' to 'On' and then the status to 'measurement
     * not being performed and is de-initialized,' to trigger an activation state change to 'Off'.
     */
    public static class MetricStatusManipulationMSRMTActivationStateOFF extends ManipulationPrecondition {

        private static final Logger LOG = LogManager.getLogger(MetricStatusManipulationMSRMTActivationStateOFF.class);

        /**
         * Creates a metric status precondition.
         */
        public MetricStatusManipulationMSRMTActivationStateOFF() {
            super(MetricStatusManipulationMSRMTActivationStateOFF::manipulation);
        }

        /**
         * @return true if successful, false otherwise
         */
        static boolean manipulation(final Injector injector) {
            final var metricCategory = MetricCategory.MSRMT;
            final var activationState = ComponentActivation.OFF;
            final var startingActivationState = ComponentActivation.ON;
            return manipulateMetricStatus(injector, LOG, metricCategory, activationState, startingActivationState);
        }
    }

    /**
     * Sets the activation state for every metric with category 'Set' to 'Off' and then the status to 'setting is
     * currently being applied' to trigger an activation state change to 'On'.
     */
    public static class MetricStatusManipulationSETActivationStateON extends ManipulationPrecondition {

        private static final Logger LOG = LogManager.getLogger(MetricStatusManipulationSETActivationStateON.class);

        /**
         * Creates a metric status precondition.
         */
        public MetricStatusManipulationSETActivationStateON() {
            super(MetricStatusManipulationSETActivationStateON::manipulation);
        }

        /**
         * @return true if successful, false otherwise
         */
        static boolean manipulation(final Injector injector) {
            final var metricCategory = MetricCategory.SET;
            final var activationState = ComponentActivation.ON;
            final var startingActivationState = ComponentActivation.OFF;
            return manipulateMetricStatus(injector, LOG, metricCategory, activationState, startingActivationState);
        }
    }

    /**
     * Sets the activation state for every metric with category 'Set' to 'On' and then the status to 'measurement
     * initialized, but is not being performed' to trigger an activation state change to 'StndBy'.
     */
    public static class MetricStatusManipulationSETActivationStateSTNDBY extends ManipulationPrecondition {

        private static final Logger LOG = LogManager.getLogger(MetricStatusManipulationSETActivationStateSTNDBY.class);

        /**
         * Creates a metric status precondition.
         */
        public MetricStatusManipulationSETActivationStateSTNDBY() {
            super(MetricStatusManipulationSETActivationStateSTNDBY::manipulation);
        }

        /**
         * @return true if successful, false otherwise
         */
        static boolean manipulation(final Injector injector) {
            final var metricCategory = MetricCategory.SET;
            final var activationState = ComponentActivation.STND_BY;
            final var startingActivationState = ComponentActivation.ON;
            return manipulateMetricStatus(injector, LOG, metricCategory, activationState, startingActivationState);
        }
    }

    /**
     * Sets the activation state for every metric with category 'Set' to 'On' and then the status to 'measurement
     * not being performed and is de-initialized,' to trigger an activation state change to 'Off'.
     */
    public static class MetricStatusManipulationSETActivationStateOFF extends ManipulationPrecondition {

        private static final Logger LOG = LogManager.getLogger(MetricStatusManipulationSETActivationStateOFF.class);

        /**
         * Creates a metric status precondition.
         */
        public MetricStatusManipulationSETActivationStateOFF() {
            super(MetricStatusManipulationSETActivationStateOFF::manipulation);
        }

        /**
         * @return true if successful, false otherwise
         */
        static boolean manipulation(final Injector injector) {
            final var metricCategory = MetricCategory.SET;
            final var activationState = ComponentActivation.OFF;
            final var startingActivationState = ComponentActivation.ON;
            return manipulateMetricStatus(injector, LOG, metricCategory, activationState, startingActivationState);
        }
    }

    /**
     * Sets the activation state for every metric with category 'Clc' to 'Off' and then the status to 'calculation is
     * being performed' to trigger an activation state change to 'On'.
     */
    public static class MetricStatusManipulationCLCActivationStateON extends ManipulationPrecondition {

        private static final Logger LOG = LogManager.getLogger(MetricStatusManipulationCLCActivationStateON.class);

        /**
         * Creates a metric status precondition.
         */
        public MetricStatusManipulationCLCActivationStateON() {
            super(MetricStatusManipulationCLCActivationStateON::manipulation);
        }

        /**
         * @return true if successful, false otherwise
         */
        static boolean manipulation(final Injector injector) {
            final var metricCategory = MetricCategory.CLC;
            final var activationState = ComponentActivation.ON;
            final var startingActivationState = ComponentActivation.OFF;
            return manipulateMetricStatus(injector, LOG, metricCategory, activationState, startingActivationState);
        }
    }

    /**
     * Sets the activation state for every metric with category 'Clc' to 'On' and then the status to 'calculation
     * initialized, but is not being performed' to trigger an activation state change to 'StndBy'.
     */
    public static class MetricStatusManipulationCLCActivationStateSTNDBY extends ManipulationPrecondition {

        private static final Logger LOG = LogManager.getLogger(
            MetricStatusManipulationCLCActivationStateSTNDBY.class);

        /**
         * Creates a metric status precondition.
         */
        public MetricStatusManipulationCLCActivationStateSTNDBY() {
            super(MetricStatusManipulationCLCActivationStateSTNDBY::manipulation);
        }

        /**
         * @return true if successful, false otherwise
         */
        static boolean manipulation(final Injector injector) {
            final var metricCategory = MetricCategory.CLC;
            final var activationState = ComponentActivation.STND_BY;
            final var startingActivationState = ComponentActivation.ON;
            return manipulateMetricStatus(injector, LOG, metricCategory, activationState, startingActivationState);
        }
    }

}
