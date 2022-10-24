/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.architecture;

import com.draeger.medical.sdccc.tests.annotations.TestIdentifier;
import com.draeger.medical.sdccc.tests.util.ImpliedValueUtil;
import com.draeger.medical.sdccc.tests.util.InitialImpliedValue;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.somda.sdc.biceps.model.message.AbstractReport;
import org.somda.sdc.biceps.model.message.DescriptionModificationReport;
import org.somda.sdc.biceps.model.message.ObservedValueStream;
import org.somda.sdc.biceps.model.message.RetrievabilityInfo;
import org.somda.sdc.biceps.model.participant.AbstractContextState;
import org.somda.sdc.biceps.model.participant.AbstractDescriptor;
import org.somda.sdc.biceps.model.participant.AbstractDeviceComponentState;
import org.somda.sdc.biceps.model.participant.AbstractMetricState;
import org.somda.sdc.biceps.model.participant.AbstractMetricValue;
import org.somda.sdc.biceps.model.participant.AbstractOperationDescriptor;
import org.somda.sdc.biceps.model.participant.AbstractState;
import org.somda.sdc.biceps.model.participant.AlertConditionDescriptor;
import org.somda.sdc.biceps.model.participant.AlertConditionState;
import org.somda.sdc.biceps.model.participant.AlertSignalDescriptor;
import org.somda.sdc.biceps.model.participant.AlertSignalState;
import org.somda.sdc.biceps.model.participant.CalibrationInfo;
import org.somda.sdc.biceps.model.participant.ClinicalInfo;
import org.somda.sdc.biceps.model.participant.ClockState;
import org.somda.sdc.biceps.model.participant.CodedValue;
import org.somda.sdc.biceps.model.participant.LimitAlertConditionDescriptor;
import org.somda.sdc.biceps.model.participant.MdDescription;
import org.somda.sdc.biceps.model.participant.MdState;
import org.somda.sdc.biceps.model.participant.Mdib;
import org.somda.sdc.biceps.model.participant.MdibVersion;
import org.somda.sdc.biceps.model.participant.MdsState;

import java.util.Optional;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.equivalentTo;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.nameMatching;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.conditions.ArchPredicates.are;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;


/**
 * Checks the listed Architectural Rules.
 */
@AnalyzeClasses(packages = "com.draeger.medical.sdccc", importOptions = {ImportOption.DoNotIncludeTests.class})
public class ArchitecturalRulesTest {

    private static DescribedPredicate<JavaAnnotation> theTestIdentifierOfAStandardRequirement =
        new DescribedPredicate<>("is annotated with the @TestIdentifier of a Standard "
            + "Requirement (not a Replacement Requirement)") {
            @Override
            public boolean apply(final JavaAnnotation input) {
                if (((JavaClass) input.getType()).isAssignableTo(TestIdentifier.class)) {
                    final String displayName = (String) input.getProperties().get("value");
                    return !displayName.contains("_");
                } else {
                    return false;
                }
            }
        };

    @ArchTest
    private static ArchRule doNotGiveRequirementsTextForStandardRequirements = noMethods()
        .that().areAnnotatedWith(TestIdentifier.class)
        .should().beAnnotatedWith(theTestIdentifierOfAStandardRequirement)
        .andShould().beAnnotatedWith(DisplayName.class)
        .because("Standard Requirements are copyrighted and should not be published as part of the SDCcc source code.");

    @ArchTest
    private static ArchRule doNotUseOptionalGet = noClasses().should().callMethodWhere(
        target(nameMatching("get")).and(
            target(owner(assignableTo(Optional.class))))
    ).because("We want to use Optional.orElseThrow() instead of Optional.get().");

    // Implied value rules
    private static final String REASON = "Use ImpliedValueUtil to retrieve %s from %s.";

    @ArchTest
    private static ArchRule checkAbstractContextStateGetContextAssociation = checkImpliedValue(
        "getContextAssociation", AbstractContextState.class,
        String.format(REASON, "context association", "AbstractContextState"));

    @ArchTest
    private static ArchRule checkAbstractDescriptorGetDescriptorVersion = checkImpliedValue(
        "getDescriptorVersion", AbstractDescriptor.class,
        String.format(REASON, "descriptor version", "AbstractDescriptor"));

    @ArchTest
    private static ArchRule checkAbstractDescriptorGetSafetyClassification = checkImpliedValue(
        "getSafetyClassification", AbstractDescriptor.class,
        String.format(REASON, "safety classification", "AbstractDescriptor"));

    @ArchTest
    private static ArchRule checkAbstractDeviceComponentStateGetActivationState = checkImpliedValue(
        "getActivationState", AbstractDeviceComponentState.class,
        String.format(REASON, "activation state", "AbstractDeviceComponentState"));

    @ArchTest
    private static ArchRule checkAbstractMetricStateGetActivationState = checkImpliedValue(
        "getActivationState", AbstractMetricState.class,
        String.format(REASON, "activation state", "AbstractMetricState"));

    @ArchTest
    private static ArchRule checkMetricQualityGetGenerationMode = checkImpliedValue(
        "getMode", AbstractMetricValue.MetricQuality.class,
        String.format(REASON, "generation mode", "AbstractMetricValue.MetricQuality"));

    @ArchTest
    private static ArchRule checkMetricQualityGetQi = checkImpliedValue(
        "getQi", AbstractMetricValue.MetricQuality.class,
        String.format(REASON, "quality indicator", "AbstractMetricValue.MetricQuality"));

    @ArchTest
    private static ArchRule checkAbstractOperationDescriptorIsRetriggerable = checkImpliedValue(
        "isRetriggerable", AbstractOperationDescriptor.class,
        String.format(REASON, "retriggerable", "AbstractOperationDescriptor"));

    @ArchTest
    private static ArchRule checkAbstractOperationDescriptorGetAccessLevel = checkImpliedValue(
        "getAccessLevel", AbstractOperationDescriptor.class,
        String.format(REASON, "access level", "AbstractOperationDescriptor"));

    @ArchTest
    private static ArchRule checkAbstractStateGetStateVersion = checkImpliedValue(
        "getStateVersion", AbstractState.class,
        String.format(REASON, "state version", "AbstractState"));

    @ArchTest
    private static ArchRule checkAbstractStateGetDescriptorVersion = checkImpliedValue(
        "getDescriptorVersion", AbstractState.class,
        String.format(REASON, "descriptor version", "AbstractState"));

    @ArchTest
    private static ArchRule checkAlertConditionDescriptorGetDefaultConditionGenerationDelay = checkImpliedValue(
        "getDefaultConditionGenerationDelay", AlertConditionDescriptor.class,
        String.format(REASON, "default condition generation delay", "AlertConditionDescriptor"));

    @ArchTest
    private static ArchRule checkAlertConditionStateIsPresence = checkImpliedValue(
        "isPresence", AlertConditionState.class,
        String.format(REASON, "presence", "AlertConditionState"));

    @ArchTest
    private static ArchRule checkAlertSignalDescriptorGetDefaultSignalGenerationDelay = checkImpliedValue(
        "getDefaultSignalGenerationDelay", AlertSignalDescriptor.class,
        String.format(REASON, "default signal generation delay", "AlertSignalDescriptor"));

    @ArchTest
    private static ArchRule checkAlertSignalDescriptorIsSignalDelegationSupported = checkImpliedValue(
        "isSignalDelegationSupported", AlertSignalDescriptor.class,
        String.format(REASON, "signal delegation supported", "AlertSignalDescriptor"));

    @ArchTest
    private static ArchRule checkAlertSignalDescriptorIsAcknowledgementSupported = checkImpliedValue(
        "isAcknowledgementSupported", AlertSignalDescriptor.class,
        String.format(REASON, "acknowledgement supported", "AlertSignalDescriptor"));

    @ArchTest
    private static ArchRule checkAlertSignalStateGetPresence = checkImpliedValue(
        "getPresence", AlertSignalState.class,
        String.format(REASON, "presence", "AlertSignalState"));

    @ArchTest
    private static ArchRule checkAlertSignalStateGetLocation = checkImpliedValue(
        "getLocation", AlertSignalState.class,
        String.format(REASON, "location", "AlertSignalState"));

    @ArchTest
    private static ArchRule checkCalibrationInfoGetType = checkImpliedValue(
        "getType", CalibrationInfo.class,
        String.format(REASON, "type", "CalibrationInfo"));

    @ArchTest
    private static ArchRule checkClinicalInfoGetCriticality = checkImpliedValue(
        "getCriticality", ClinicalInfo.class,
        String.format(REASON, "criticality", "ClinicalInfo"));

    @ArchTest
    private static ArchRule checkClockStateIsCriticalUse = checkImpliedValue(
        "isCriticalUse", ClockState.class,
        String.format(REASON, "critical use", "ClockState"));

    @ArchTest
    private static ArchRule checkCodedValueGetCodingSystem = checkImpliedValue(
        "getCodingSystem", CodedValue.class,
        String.format(REASON, "coding system", "CodedValue"));

    @ArchTest
    private static ArchRule checkLimitAlertConditionDescriptorIsAutoLimitSupported = checkImpliedValue(
        "isAutoLimitSupported", LimitAlertConditionDescriptor.class,
        String.format(REASON, "auto limit supported", "LimitAlertConditionDescriptor"));

    @ArchTest
    private static ArchRule checkMdDescriptionGetDescriptionVersion = checkImpliedValue(
        "getDescriptionVersion", MdDescription.class,
        String.format(REASON, "description version", "MdDescription"));

    @ArchTest
    private static ArchRule checkMdsStateGetLang = checkImpliedValue(
        "getLang", MdsState.class,
        String.format(REASON, "language", "MdsState"));

    @ArchTest
    private static ArchRule checkMdsStateGetOperatingMode = checkImpliedValue(
        "getOperatingMode", MdsState.class,
        String.format(REASON, "operating mode", "MdsState"));

    @ArchTest
    private static ArchRule checkMdStateGetStateVersion = checkImpliedValue(
        "getStateVersion", MdState.class,
        String.format(REASON, "state version", "MdState"));

    @ArchTest
    private static ArchRule checkMdibVersionGetVersion = checkImpliedValue(
        "getVersion", MdibVersion.class,
        String.format(REASON, "version", "MdibVersion"));

    @ArchTest
    private static ArchRule checkDescriptionModificationReportReportPartGetModificationType = checkImpliedValue(
        "getModificationType", DescriptionModificationReport.ReportPart.class,
        String.format(REASON, "modification type", "DescriptionModificationReport.ReportPart"));

    @ArchTest
    private static ArchRule checkObservedValueStreamValueGetStateVersion = checkImpliedValue(
        "getStateVersion", ObservedValueStream.Value.class,
        String.format(REASON, "state version", "ObservedValueStream.Value"));

    @ArchTest
    private static ArchRule checkRetrievabilityInfoGetUpdatePeriod = checkImpliedValue(
        "getUpdatePeriod", RetrievabilityInfo.class,
        String.format(REASON, "update period", "RetrievabilityInfo"));

    @ArchTest
    private static ArchRule checkAbstractReportMdibVersion = checkImpliedValue(
        "getMdibVersion", AbstractReport.class,
        String.format(REASON, "mdib version", "AbstractReport"));

    @ArchTest
    private static ArchRule checkMdibMdibVersion = checkImpliedValue(
        "getMdibVersion", Mdib.class,
        String.format(REASON, "mdib verison", "Mdib"));

    @ArchTest
    private static ArchRule checkMdibInstanceId = checkImpliedValue(
        "getInstanceId", Mdib.class,
        String.format(REASON, "instance id", "Mdib"));

    @ArchTest
    private static ArchRule checkDirectTestsInitialImpliedVersions = noClasses().that().resideInAPackage("..direct..")
        .should()
        .callMethod(ImpliedValueUtil.class, "getDescriptorVersion", AbstractDescriptor.class, InitialImpliedValue.class)
        .orShould()
        .callMethod(ImpliedValueUtil.class, "getStateVersion", AbstractState.class, InitialImpliedValue.class)
        .orShould()
        .callMethod(ImpliedValueUtil.class, "getStateDescriptorVersion", AbstractState.class, InitialImpliedValue.class)
        .because("Initial implied value can not be reliable checked in direct tests");

    private static ArchRule checkImpliedValue(final String methodName, final Class targetClass, final String reason) {
        return noClasses().that(are(not(equivalentTo(ImpliedValueUtil.class)))).should().callMethodWhere(
            target(nameMatching(methodName)).and(target(owner(assignableTo(targetClass))))).because(reason);
    }
}
