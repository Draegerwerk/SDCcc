/*
 * This Source Code Form is subject to the terms of the "SDCcc non-commercial use license".
 *
 * Copyright (C) 2025 Draegerwerk AG & Co. KGaA
 */

package com.draeger.medical.sdccc.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaAccess.Predicates.target;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.equivalentTo;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.nameMatching;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.conditions.ArchPredicates.are;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.draeger.medical.sdccc.sdcri.testclient.TestClient;
import com.draeger.medical.sdccc.tests.annotations.TestIdentifier;
import com.draeger.medical.sdccc.tests.util.ImpliedValueUtil;
import com.draeger.medical.sdccc.tests.util.NoTestData;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.somda.sdc.biceps.common.MdibEntity;
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

/**
 * Checks the listed Architectural Rules.
 */
@AnalyzeClasses(
        packages = "com.draeger.medical.sdccc",
        importOptions = {ImportOption.DoNotIncludeTests.class})
public class GeneralArchRulesTest {

    private static final DescribedPredicate<JavaAnnotation<?>> TEST_IDENTIFIER_OF_A_STANDARD_REQUIREMENT =
            new DescribedPredicate<>("is annotated with the @TestIdentifier of a Standard "
                    + "Requirement (not a Replacement Requirement)") {
                @Override
                public boolean test(final JavaAnnotation input) {
                    if (((JavaClass) input.getType()).isAssignableTo(TestIdentifier.class)) {
                        final String displayName =
                                (String) input.getProperties().get("value");
                        return !displayName.contains("_");
                    } else {
                        return false;
                    }
                }
            };

    @ArchTest
    private static final ArchRule DO_NOT_THROW_NO_TEST_DATA_DIRECTLY = noClasses()
            .that()
            .haveNameMatching(".*Test")
            .should()
            .accessTargetWhere(target(owner(assignableTo(NoTestData.class))))
            .because("Do not throw NoTestData directly, please use assertTestData(), instead.");

    @ArchTest
    private static final ArchRule DO_NOT_GIVE_REQUIREMENTS_TEXT_FOR_STANDARD_REQUIREMENTS = noMethods()
            .that()
            .areAnnotatedWith(TestIdentifier.class)
            .should()
            .beAnnotatedWith(TEST_IDENTIFIER_OF_A_STANDARD_REQUIREMENT)
            .andShould()
            .beAnnotatedWith(DisplayName.class)
            .because(
                    "Standard Requirements are copyrighted and should not be published as part of the SDCcc source code.");

    @ArchTest
    private static final ArchRule DO_NOT_USE_OPTIONAL_GET = noClasses()
            .should()
            .accessTargetWhere(target(owner(assignableTo(Optional.class))).and(nameMatching("get")))
            .because("We want to use Optional.orElseThrow() instead of Optional.get().");

    // Implied value rules
    private static final String REASON = "Use ImpliedValueUtil to retrieve %s from %s.";

    @ArchTest
    private static final ArchRule CHECK_ABSTRACT_CONTEXT_STATE_GET_CONTEXT_ASSOCIATION = checkImpliedValue(
            "getContextAssociation",
            AbstractContextState.class,
            String.format(REASON, "context association", "AbstractContextState"));

    @ArchTest
    private static final ArchRule CHECK_ABSTRACT_DESCRIPTOR_GET_DESCRIPTOR_VERSION = checkImpliedValue(
            "getDescriptorVersion",
            AbstractDescriptor.class,
            String.format(REASON, "descriptor version", "AbstractDescriptor"));

    @ArchTest
    private static final ArchRule CHECK_ABSTRACT_DESCRIPTOR_GET_SAFETY_CLASSIFICATION = checkImpliedValue(
            "getSafetyClassification",
            AbstractDescriptor.class,
            String.format(REASON, "safety classification", "AbstractDescriptor"));

    @ArchTest
    private static final ArchRule CHECK_ABSTRACT_DEVICE_COMPONENT_STATE_GET_ACTIVATION_STATE = checkImpliedValue(
            "getActivationState",
            AbstractDeviceComponentState.class,
            String.format(REASON, "activation state", "AbstractDeviceComponentState"));

    @ArchTest
    private static final ArchRule CHECK_ABSTRACT_METRIC_STATE_GET_ACTIVATION_STATE = checkImpliedValue(
            "getActivationState",
            AbstractMetricState.class,
            String.format(REASON, "activation state", "AbstractMetricState"));

    @ArchTest
    private static final ArchRule CHECK_METRIC_QUALITY_GET_GENERATION_MODE = checkImpliedValue(
            "getMode",
            AbstractMetricValue.MetricQuality.class,
            String.format(REASON, "generation mode", "AbstractMetricValue.MetricQuality"));

    @ArchTest
    private static final ArchRule CHECK_METRIC_QUALITY_GET_QI = checkImpliedValue(
            "getQi",
            AbstractMetricValue.MetricQuality.class,
            String.format(REASON, "quality indicator", "AbstractMetricValue.MetricQuality"));

    @ArchTest
    private static final ArchRule CHECK_ABSTRACT_OPERATION_DESCRIPTOR_IS_RETRIGGERABLE = checkImpliedValue(
            "isRetriggerable",
            AbstractOperationDescriptor.class,
            String.format(REASON, "retriggerable", "AbstractOperationDescriptor"));

    @ArchTest
    private static final ArchRule CHECK_ABSTRACT_OPERATION_DESCRIPTOR_GET_ACCESS_LEVEL = checkImpliedValue(
            "getAccessLevel",
            AbstractOperationDescriptor.class,
            String.format(REASON, "access level", "AbstractOperationDescriptor"));

    @ArchTest
    private static final ArchRule CHECK_ABSTRACT_STATE_GET_STATE_VERSION = checkImpliedValue(
            "getStateVersion", AbstractState.class, String.format(REASON, "state version", "AbstractState"));

    @ArchTest
    private static final ArchRule CHECK_ABSTRACT_STATE_GET_DESCRIPTOR_VERSION = checkImpliedValue(
            "getDescriptorVersion", AbstractState.class, String.format(REASON, "descriptor version", "AbstractState"));

    @ArchTest
    private static final ArchRule CHECK_ALERT_CONDITION_DESCRIPTOR_GET_DEFAULT_CONDITION_GENERATION_DELAY =
            checkImpliedValue(
                    "getDefaultConditionGenerationDelay",
                    AlertConditionDescriptor.class,
                    String.format(REASON, "default condition generation delay", "AlertConditionDescriptor"));

    @ArchTest
    private static final ArchRule CHECK_ALERT_CONDITION_STATE_IS_PRESENCE = checkImpliedValue(
            "isPresence", AlertConditionState.class, String.format(REASON, "presence", "AlertConditionState"));

    @ArchTest
    private static final ArchRule CHECK_ALERT_SIGNAL_DESCRIPTOR_GET_DEFAULT_SIGNAL_GENERATION_DELAY = checkImpliedValue(
            "getDefaultSignalGenerationDelay",
            AlertSignalDescriptor.class,
            String.format(REASON, "default signal generation delay", "AlertSignalDescriptor"));

    @ArchTest
    private static final ArchRule CHECK_ALERT_SIGNAL_DESCRIPTOR_IS_SIGNAL_DELEGATION_SUPPORTED = checkImpliedValue(
            "isSignalDelegationSupported",
            AlertSignalDescriptor.class,
            String.format(REASON, "signal delegation supported", "AlertSignalDescriptor"));

    @ArchTest
    private static final ArchRule CHECK_ALERT_SIGNAL_DESCRIPTOR_IS_ACKNOWLEDGEMENT_SUPPORTED = checkImpliedValue(
            "isAcknowledgementSupported",
            AlertSignalDescriptor.class,
            String.format(REASON, "acknowledgement supported", "AlertSignalDescriptor"));

    @ArchTest
    private static final ArchRule CHECK_ALERT_SIGNAL_STATE_GET_PRESENCE = checkImpliedValue(
            "getPresence", AlertSignalState.class, String.format(REASON, "presence", "AlertSignalState"));

    @ArchTest
    private static final ArchRule CHECK_ALERT_SIGNAL_STATE_GET_LOCATION = checkImpliedValue(
            "getLocation", AlertSignalState.class, String.format(REASON, "location", "AlertSignalState"));

    @ArchTest
    private static final ArchRule CHECK_CALIBRATION_INFO_GET_TYPE =
            checkImpliedValue("getType", CalibrationInfo.class, String.format(REASON, "type", "CalibrationInfo"));

    @ArchTest
    private static final ArchRule CHECK_CLINICAL_INFO_GET_CRITICALITY = checkImpliedValue(
            "getCriticality", ClinicalInfo.class, String.format(REASON, "criticality", "ClinicalInfo"));

    @ArchTest
    private static final ArchRule CHECK_CLOCK_STATE_IS_CRITICAL_USE =
            checkImpliedValue("isCriticalUse", ClockState.class, String.format(REASON, "critical use", "ClockState"));

    @ArchTest
    private static final ArchRule CHECK_CODED_VALUE_GET_CODING_SYSTEM = checkImpliedValue(
            "getCodingSystem", CodedValue.class, String.format(REASON, "coding system", "CodedValue"));

    @ArchTest
    private static final ArchRule CHECK_LIMIT_ALERT_CONDITION_DESCRIPTOR_IS_AUTO_LIMIT_SUPPORTED = checkImpliedValue(
            "isAutoLimitSupported",
            LimitAlertConditionDescriptor.class,
            String.format(REASON, "auto limit supported", "LimitAlertConditionDescriptor"));

    @ArchTest
    private static final ArchRule CHECK_MD_DESCRIPTION_GET_DESCRIPTION_VERSION = checkImpliedValue(
            "getDescriptionVersion",
            MdDescription.class,
            String.format(REASON, "description version", "MdDescription"));

    @ArchTest
    private static final ArchRule CHECK_MDS_STATE_GET_LANG =
            checkImpliedValue("getLang", MdsState.class, String.format(REASON, "language", "MdsState"));

    @ArchTest
    private static final ArchRule CHECK_MDS_STATE_GET_OPERATING_MODE =
            checkImpliedValue("getOperatingMode", MdsState.class, String.format(REASON, "operating mode", "MdsState"));

    @ArchTest
    private static final ArchRule CHECK_MD_STATE_GET_STATE_VERSION =
            checkImpliedValue("getStateVersion", MdState.class, String.format(REASON, "state version", "MdState"));

    @ArchTest
    private static final ArchRule CHECK_MDIB_VERSION_GET_VERSION =
            checkImpliedValue("getVersion", MdibVersion.class, String.format(REASON, "version", "MdibVersion"));

    @ArchTest
    private static final ArchRule CHECK_DESCRIPTION_MODIFICATION_REPORT_REPORT_PART_GET_MODIFICATION_TYPE =
            checkImpliedValue(
                    "getModificationType",
                    DescriptionModificationReport.ReportPart.class,
                    String.format(REASON, "modification type", "DescriptionModificationReport.ReportPart"));

    @ArchTest
    private static final ArchRule CHECK_OBSERVED_VALUE_STREAM_VALUE_GET_STATE_VERSION = checkImpliedValue(
            "getStateVersion",
            ObservedValueStream.Value.class,
            String.format(REASON, "state version", "ObservedValueStream.Value"));

    @ArchTest
    private static final ArchRule CHECK_RETRIEVABILITY_INFO_GET_UPDATE_PERIOD = checkImpliedValue(
            "getUpdatePeriod", RetrievabilityInfo.class, String.format(REASON, "update period", "RetrievabilityInfo"));

    @ArchTest
    private static final ArchRule CHECK_ABSTRACT_REPORT_MDIB_VERSION = checkImpliedValue(
            "getMdibVersion", AbstractReport.class, String.format(REASON, "mdib version", "AbstractReport"));

    @ArchTest
    private static final ArchRule CHECK_MDIB_GET_MDIB_VERSION =
            checkImpliedValue("getMdibVersion", Mdib.class, String.format(REASON, "mdib verison", "Mdib"));

    @ArchTest
    private static final ArchRule CHECK_MDIB_GET_INSTANCE_ID =
            checkImpliedValue("getInstanceId", Mdib.class, String.format(REASON, "instance id", "Mdib"));

    @ArchTest
    private static final ArchRule NO_CALLS_TO_GET_DESCRIPTOR = noClasses()
            .should()
            .callMethod(MdibEntity.class, "getDescriptor")
            .because("Direct calls to getDescriptor are disallowed use getDescriptor(Class<T> var1).");

    @ArchTest
    private static final ArchRule NO_CALLS_TO_GET_STATES = noClasses()
            .should()
            .callMethod(MdibEntity.class, "getStates")
            .because("Direct calls to getStates are disallowed use getStates(Class<T> var1) instead.");

    @ArchTest
    private static final ArchRule ENSURE_ENABLE_RECONNECT_FOLLOWED_BY_DISABLE_RECONNECT = classes()
            .should(new ArchCondition<JavaClass>("call enableReconnect followed by disableReconnect") {
                @Override
                public void check(final JavaClass javaClass, final ConditionEvents events) {
                    int callsEnableReconnect = 0;
                    int callsDisableReconnect = 0;
                    for (JavaMethodCall call : javaClass.getMethodCallsFromSelf()) {
                        if (call.getTarget().getName().equals("enableReconnect")
                                && call.getTarget().getOwner().isEquivalentTo(TestClient.class)) {
                            callsEnableReconnect++;
                        }
                        if (call.getTarget().getName().equals("disableReconnect")
                                && call.getTarget().getOwner().isEquivalentTo(TestClient.class)) {
                            callsDisableReconnect++;
                        }
                    }
                    if (callsEnableReconnect != callsDisableReconnect) {
                        final String message = String.format(
                                "Class %s calls enableReconnect but does not call disableReconnect later",
                                javaClass.getName());
                        events.add(SimpleConditionEvent.violated(javaClass, message));
                    }
                }
            });

    private static ArchRule checkImpliedValue(
            final String methodName, final Class<?> targetClass, final String reason) {
        return noClasses()
                .that(are(not(equivalentTo(ImpliedValueUtil.class))))
                .should()
                .accessTargetWhere(target(owner(assignableTo(targetClass))).and(nameMatching(methodName)))
                .because(reason);
    }
}
