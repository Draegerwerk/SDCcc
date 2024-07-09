/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2023 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.tests.util;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import javax.annotation.Nullable;
import org.somda.sdc.biceps.common.access.MdibAccess;
import org.somda.sdc.biceps.model.message.AbstractReport;
import org.somda.sdc.biceps.model.message.DescriptionModificationReport;
import org.somda.sdc.biceps.model.message.DescriptionModificationType;
import org.somda.sdc.biceps.model.message.ObservedValueStream;
import org.somda.sdc.biceps.model.message.RetrievabilityInfo;
import org.somda.sdc.biceps.model.participant.AbstractContextState;
import org.somda.sdc.biceps.model.participant.AbstractDescriptor;
import org.somda.sdc.biceps.model.participant.AbstractDeviceComponentState;
import org.somda.sdc.biceps.model.participant.AbstractMetricState;
import org.somda.sdc.biceps.model.participant.AbstractMetricValue;
import org.somda.sdc.biceps.model.participant.AbstractMultiState;
import org.somda.sdc.biceps.model.participant.AbstractOperationDescriptor;
import org.somda.sdc.biceps.model.participant.AbstractState;
import org.somda.sdc.biceps.model.participant.AlertConditionDescriptor;
import org.somda.sdc.biceps.model.participant.AlertConditionState;
import org.somda.sdc.biceps.model.participant.AlertSignalDescriptor;
import org.somda.sdc.biceps.model.participant.AlertSignalPresence;
import org.somda.sdc.biceps.model.participant.AlertSignalPrimaryLocation;
import org.somda.sdc.biceps.model.participant.AlertSignalState;
import org.somda.sdc.biceps.model.participant.CalibrationInfo;
import org.somda.sdc.biceps.model.participant.CalibrationType;
import org.somda.sdc.biceps.model.participant.ClinicalInfo;
import org.somda.sdc.biceps.model.participant.ClockState;
import org.somda.sdc.biceps.model.participant.CodedValue;
import org.somda.sdc.biceps.model.participant.ComponentActivation;
import org.somda.sdc.biceps.model.participant.ContextAssociation;
import org.somda.sdc.biceps.model.participant.GenerationMode;
import org.somda.sdc.biceps.model.participant.LimitAlertConditionDescriptor;
import org.somda.sdc.biceps.model.participant.MdDescription;
import org.somda.sdc.biceps.model.participant.MdState;
import org.somda.sdc.biceps.model.participant.Mdib;
import org.somda.sdc.biceps.model.participant.MdibVersion;
import org.somda.sdc.biceps.model.participant.MdsOperatingMode;
import org.somda.sdc.biceps.model.participant.MdsState;
import org.somda.sdc.biceps.model.participant.SafetyClassification;

/**
 * Utility which provides the implied value.
 */
public final class ImpliedValueUtil {

    private ImpliedValueUtil() {}

    /**
     * Retrieves the context association of an abstract context state or the implied value.
     *
     * @param contextState to retrieve the context association from
     * @return the context association
     */
    public static ContextAssociation getContextAssociation(final AbstractContextState contextState) {
        final var association = contextState.getContextAssociation();
        return association != null ? association : ContextAssociation.NO;
    }

    /**
     * Retrieves the descriptor version of an abstract descriptor or the implied value.
     *
     * @param descriptor    to retrieve the descriptor version from
     * @param impliedValues to track whether a version is initial. A separate instance is required for each sequence ID.
     * @return the descriptor version.
     * @throws InitialImpliedValueException when the descriptor version is null, even though the version was
     *                                      non-initial before.
     */
    public static BigInteger getDescriptorVersion(
            final AbstractDescriptor descriptor, final InitialImpliedValue impliedValues)
            throws InitialImpliedValueException {
        final var descriptorVersion = descriptor.getDescriptorVersion();
        final var handle = descriptor.getHandle();
        if (descriptorVersion == null) {
            if (!impliedValues.isDescriptorVersionInitial(handle)) {
                throw new InitialImpliedValueException(String.format(
                        "The descriptor version for handle %s is null but was not allowed to be."
                                + " It occurred previously without an implied value.",
                        handle));
            }
        } else {
            impliedValues.setDescriptorVersionNonInitial(handle);
        }
        return descriptorVersion != null ? descriptorVersion : BigInteger.ZERO;
    }

    /**
     * Retrieves the safety classification of an abstract descriptor or the implied value.
     *
     * @param descriptor to retrieve the safety classification from
     * @return the safety classification
     */
    public static SafetyClassification getSafetyClassification(final AbstractDescriptor descriptor) {
        final var safetyClassification = descriptor.getSafetyClassification();
        return safetyClassification != null ? safetyClassification : SafetyClassification.INF;
    }

    /**
     * Retrieves the component activation of an abstract device component state or the implied value.
     *
     * @param deviceComponentState to retrieve the component activation from
     * @return the component activation
     */
    public static ComponentActivation getComponentActivation(final AbstractDeviceComponentState deviceComponentState) {
        final var activation = deviceComponentState.getActivationState();
        return activation != null ? activation : ComponentActivation.ON;
    }

    /**
     * Retrieves the component activation of an abstract metric state or the implied value.
     *
     * @param metricState to retrieve the component activation from
     * @return the component activation
     */
    public static ComponentActivation getMetricActivation(final AbstractMetricState metricState) {
        final var componentActivation = metricState.getActivationState();
        return componentActivation != null ? componentActivation : ComponentActivation.ON;
    }

    /**
     * Retrieves the generation mode of an abstract metric value or the implied value.
     *
     * @param metricValue to retrieve the generation mode from
     * @return the generation mode
     */
    public static GenerationMode getGenerationMode(final AbstractMetricValue metricValue) {
        final var generationMode = metricValue.getMetricQuality().getMode();
        return generationMode != null ? generationMode : GenerationMode.REAL;
    }

    /**
     * Retrieves the quality indicator of an abstract metric value or the implied value.
     *
     * @param metricValue to retrieve the quality indicator from
     * @return the quality indicator
     */
    public static BigDecimal getQualityIndicator(final AbstractMetricValue metricValue) {
        final var qi = metricValue.getMetricQuality().getQi();
        return qi != null ? qi : BigDecimal.ONE;
    }

    /**
     * Retrieves the retriggerable attribute of an abstract operation descriptor or the implied value.
     *
     * @param descriptor to retrieve the retriggerable attribute from
     * @return the retriggerable attribute
     */
    public static boolean isRetriggerable(final AbstractOperationDescriptor descriptor) {
        final var retriggerable = descriptor.isRetriggerable();
        return retriggerable == null ? true : retriggerable;
    }

    /**
     * Retrieves the access level of an abstract operation descriptor or the implied value.
     *
     * @param descriptor to retrieve the access level from
     * @return the access level
     */
    public static AbstractOperationDescriptor.AccessLevel getAccessLevel(final AbstractOperationDescriptor descriptor) {
        final var accessLevel = descriptor.getAccessLevel();
        return accessLevel != null ? accessLevel : AbstractOperationDescriptor.AccessLevel.USR;
    }

    /**
     * Retrieves the state version of an abstract state or the implied value.
     *
     * @param state         to retrieve the state version from
     * @param impliedValues to track whether a version is initial. A separate instance is required for each sequence ID.
     * @return the state version
     * @throws InitialImpliedValueException when the state version is null, even though the version was non-initial
     *                                      before.
     */
    public static BigInteger getStateVersion(final AbstractState state, final InitialImpliedValue impliedValues)
            throws InitialImpliedValueException {
        final var version = state.getStateVersion();
        final var handle = state instanceof AbstractMultiState
                ? ((AbstractMultiState) state).getHandle()
                : state.getDescriptorHandle();
        if (version == null) {
            if (!impliedValues.isStateVersionInitial(handle)) {
                throw new InitialImpliedValueException(String.format(
                        "The state version for handle %s is null but was not allowed to be."
                                + " It occurred previously without an implied value.",
                        handle));
            }
        } else {
            impliedValues.setStateVersionNonInitial(handle);
        }

        return version != null ? version : BigInteger.ZERO;
    }

    /**
     * Retrieves the descriptor version of an abstract state or the implied value.
     *
     * @param state         to retrieve the descriptor version from
     * @param impliedValues to track whether a version is initial. A separate instance is required for each sequence ID.
     * @return the descriptor version
     * @throws InitialImpliedValueException when the state descriptor version is null, even though the version was
     *                                      non-initial before.
     */
    public static BigInteger getStateDescriptorVersion(
            final AbstractState state, final InitialImpliedValue impliedValues) throws InitialImpliedValueException {
        final var descriptorVersion = state.getDescriptorVersion();
        final var handle = state instanceof AbstractMultiState
                ? ((AbstractMultiState) state).getHandle()
                : state.getDescriptorHandle();
        if (descriptorVersion == null) {
            if (!impliedValues.isStateDescriptorVersionInitial(handle)) {
                throw new InitialImpliedValueException(String.format(
                        "The state descriptor version for handle %s is null but was not allowed to be."
                                + " It occurred previously without an implied value.",
                        handle));
            }
        } else {
            impliedValues.setStateDescriptorVersionNonInitial(handle);
        }
        return descriptorVersion != null ? descriptorVersion : BigInteger.ZERO;
    }

    /**
     * Retrieves the default condition generation delay of an alert condition descriptor or the implied value.
     *
     * @param alertConditionDescriptor to retrieve the duration from
     * @return the duration for the default condition generation delay
     */
    public static Duration getDefaultConditionGenerationDelay(final AlertConditionDescriptor alertConditionDescriptor) {
        final var duration = alertConditionDescriptor.getDefaultConditionGenerationDelay();
        return duration != null ? duration : Duration.ZERO;
    }

    /**
     * Retrieves the presence of an alert condition state or the implied value.
     *
     * @param alertConditionState to retrieve the presence from
     * @return the presence
     */
    public static boolean isPresence(final AlertConditionState alertConditionState) {
        final var presence = alertConditionState.isPresence();
        return presence != null && presence;
    }

    /**
     * Retrieves the default signal generation delay of an alert signal descriptor or the implied value.
     *
     * @param alertSignalDescriptor to retrieve the default signal generation delay from
     * @return the duration for the default signal generation delay
     */
    public static Duration getDefaultSignalGenerationDelay(final AlertSignalDescriptor alertSignalDescriptor) {
        final var duration = alertSignalDescriptor.getDefaultSignalGenerationDelay();
        return duration != null ? duration : Duration.ZERO;
    }

    /**
     * Retrieves the signal delegation supported attribute of an alert signal descriptor or the implied value.
     *
     * @param descriptor to retrieve the signal delegation supported attribute from
     * @return the signal delegation supported attribute
     */
    public static boolean isSignalDelegationSupported(final AlertSignalDescriptor descriptor) {
        final var signalDelegationSupported = descriptor.isSignalDelegationSupported();
        return signalDelegationSupported != null && signalDelegationSupported;
    }

    /**
     * Retrieves the acknowledgement supported attribute of an alert signal descriptor or the implied value.
     *
     * @param descriptor to retrieve the acknowledgement supported attribute from
     * @return the acknowledgement supported attribute
     */
    public static boolean isAcknowledgementSupported(final AlertSignalDescriptor descriptor) {
        final var acknowledgementSupported = descriptor.isAcknowledgementSupported();
        return acknowledgementSupported != null && acknowledgementSupported;
    }

    /**
     * Retrieves the presence of an alert signal state or the implied value.
     *
     * @param alertSignalState to retrieve the presence from
     * @return the presence
     */
    public static AlertSignalPresence getPresence(final AlertSignalState alertSignalState) {
        final var presence = alertSignalState.getPresence();
        return presence != null ? presence : AlertSignalPresence.OFF;
    }

    /**
     * Retrieves the location of an alert signal state or the implied value.
     *
     * @param alertSignalState to retrieve the location from
     * @return the alert signal location
     */
    public static AlertSignalPrimaryLocation getLocation(final AlertSignalState alertSignalState) {
        final var location = alertSignalState.getLocation();
        return location != null ? location : AlertSignalPrimaryLocation.LOC;
    }

    /**
     * Retrieves the calibration type of a calibration info or the implied value.
     *
     * @param info to retrieve the calibration type from
     * @return the calibration type
     */
    public static CalibrationType getCalibrationType(final CalibrationInfo info) {
        final var calibrationType = info.getType();
        return calibrationType != null ? calibrationType : CalibrationType.UNSPEC;
    }

    /**
     * Retrieves the criticality of a clinical info or the implied value.
     *
     * @param clinicalInfo to retrieve the criticality from
     * @return the criticality
     */
    public static ClinicalInfo.Criticality getCriticality(final ClinicalInfo clinicalInfo) {
        final var criticality = clinicalInfo.getCriticality();
        return criticality != null ? criticality : ClinicalInfo.Criticality.LO;
    }

    /**
     * Retrieves the critical use attribute of a clock state or the implied value.
     *
     * @param state to retrieve the critical use attribute from
     * @return the critical use attribute
     */
    public static boolean isCriticalUse(final ClockState state) {
        final var criticalUse = state.isCriticalUse();
        return criticalUse != null && criticalUse;
    }

    /**
     * Retrieves the coding system of a coded value or the implied value.
     *
     * @param codedValue to retrieve the coding system from
     * @return the coding system
     */
    public static String getCodingSystem(final CodedValue codedValue) {
        final var codingSystem = codedValue.getCodingSystem();
        return codingSystem != null ? codingSystem : "urn:oid:1.2.840.10004.1.1.1.0.0.1";
    }

    /**
     * Retrieves the auto limit supported attribute from a limit alert condition descriptor or the implied value.
     *
     * @param descriptor to retrieve the auto limit supported attribute from
     * @return the auto limit supported attribute
     */
    public static boolean isAutoLimitSupported(final LimitAlertConditionDescriptor descriptor) {
        final var autoLimitSupported = descriptor.isAutoLimitSupported();
        return autoLimitSupported != null && autoLimitSupported;
    }

    /**
     * Retrieves the description version of a md description or the implied value.
     *
     * @param mdDescription to retrieve the description version from
     * @return the description version
     */
    public static BigInteger getDescriptionVersion(final @Nullable MdDescription mdDescription) {
        if (mdDescription != null) {
            final var descriptionVersion = mdDescription.getDescriptionVersion();
            return descriptionVersion != null ? descriptionVersion : BigInteger.ZERO;
        }
        return BigInteger.ZERO;
    }

    /**
     * Retrieves the description version of mdib access or the implied value.
     *
     * @param mdibAccess to retrieve the description version from
     * @return the description version
     */
    public static BigInteger getMdibAccessDescriptionVersion(final MdibAccess mdibAccess) {
        final var descriptionVersion = mdibAccess.getMdDescriptionVersion();
        return descriptionVersion != null ? descriptionVersion : BigInteger.ZERO;
    }

    /**
     * Retrieves the language of an mds state or the implied value.
     *
     * @param mdsState to retrieve the language from
     * @return the language
     */
    public static String getLang(final MdsState mdsState) {
        final var lang = mdsState.getLang();
        return lang != null ? lang : "en";
    }

    /**
     * Retrieves the operating mode of an mds state or the implied value.
     *
     * @param mdsState to retrieve the operating mode from
     * @return the operating mode
     */
    public static MdsOperatingMode getOperatingMode(final MdsState mdsState) {
        final var operatingMode = mdsState.getOperatingMode();
        return operatingMode != null ? operatingMode : MdsOperatingMode.NML;
    }

    /**
     * Retrieves the state version of an md state or the implied value.
     *
     * @param mdState to retrieve the state version from
     * @return the state version
     */
    public static BigInteger getMdStateStateVersion(final @Nullable MdState mdState) {
        if (mdState != null) {
            final var stateVersion = mdState.getStateVersion();
            return stateVersion != null ? stateVersion : BigInteger.ZERO;
        }
        return BigInteger.ZERO;
    }

    /**
     * Retrieves the md state version of mdib access or the implied value.
     *
     * @param mdibAccess to retrieve the state version from
     * @return the state version
     */
    public static BigInteger getMdibAccessMdStateVersion(final MdibAccess mdibAccess) {
        final var stateVersion = mdibAccess.getMdStateVersion();
        return stateVersion != null ? stateVersion : BigInteger.ZERO;
    }

    /**
     * Retrieves the version of a mdib version or the implied value.
     *
     * @param mdibVersion to retrieve the version from
     * @return the version
     */
    public static BigInteger getMdibVersion(final MdibVersion mdibVersion) {
        final var version = mdibVersion.getVersion();
        return version != null ? version : BigInteger.ZERO;
    }

    /**
     * Retrieves the modification type of a description modification report report part or the implied value.
     *
     * @param reportPart to retrieve the modification type from
     * @return the modification type
     */
    public static DescriptionModificationType getModificationType(
            final DescriptionModificationReport.ReportPart reportPart) {
        final var modificationType = reportPart.getModificationType();
        return modificationType != null ? modificationType : DescriptionModificationType.UPT;
    }

    /**
     * Retrieves the state version of an observed value stream value or the implied value.
     *
     * @param value to retrieve the state version from
     * @return the state version
     */
    public static BigInteger getValueStateVersion(final ObservedValueStream.Value value) {
        final var stateVersion = value.getStateVersion();
        return stateVersion != null ? stateVersion : BigInteger.ZERO;
    }

    /**
     * Retrieves the update period of a retrievability info or the implied value.
     *
     * @param info to retrieve the update period from
     * @return the update period
     */
    public static Duration getUpdatePeriod(final RetrievabilityInfo info) {
        final var updatePeriod = info.getUpdatePeriod();
        return updatePeriod != null ? updatePeriod : Duration.ofSeconds(1);
    }

    /**
     * Retrieves the version of an abstract report or the implied value.
     *
     * @param report to retrieve the version from
     * @return the version
     */
    public static BigInteger getReportMdibVersion(final AbstractReport report) {
        final var version = report.getMdibVersion();
        return version != null ? version : BigInteger.ZERO;
    }

    /**
     * Retrieves the version of an mdib or the implied value.
     *
     * @param mdib to retrieve the version from
     * @return the version
     */
    public static BigInteger getMdibMdibVersion(final Mdib mdib) {
        final var version = mdib.getMdibVersion();
        return version != null ? version : BigInteger.ZERO;
    }

    /**
     * Handles the implied value for an mdib version.
     *
     * @param version of the mdib
     * @return the version
     */
    public static BigInteger getMdibMdibVersion(@Nullable final BigInteger version) {
        return version != null ? version : BigInteger.ZERO;
    }

    /**
     * Retrieves the version of an mdib or the implied value.
     *
     * @param mdib to retrieve the version from
     * @return the version
     */
    public static BigInteger getMdibMdibVersion(final MdibAccess mdib) {
        final var version = mdib.getMdibVersion().getVersion();
        return version != null ? version : BigInteger.ZERO;
    }

    /**
     * Retrieves the version of an mdib or the implied value.
     *
     * @param mdibVersion to retrieve the version from
     * @return the version
     */
    public static BigInteger getMdibMdibVersion(final MdibVersion mdibVersion) {
        final var version = mdibVersion.getVersion();
        return version != null ? version : BigInteger.ZERO;
    }


    /**
     * Retrieves the instance id of an mdib or the implied value.
     *
     * @param mdib to retrieve the instance id from
     * @return the instance id
     */
    public static BigInteger getMdibInstanceId(final Mdib mdib) {
        final var instanceId = mdib.getInstanceId();
        return instanceId != null ? instanceId : BigInteger.ZERO;
    }
}
