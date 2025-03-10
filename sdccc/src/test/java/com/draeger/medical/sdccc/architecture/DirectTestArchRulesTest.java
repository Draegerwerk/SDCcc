/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2025 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.architecture;

import com.draeger.medical.sdccc.tests.util.ImpliedValueUtil;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.core.domain.JavaAccess.Predicates.target;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.nameMatching;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Checks the listed Architectural Rules.
 */
@AnalyzeClasses(
        packages = "com.draeger.medical.sdccc",
        importOptions = {ImportOption.DoNotIncludeTests.class})
public class DirectTestArchRulesTest {

    @ArchTest
    private static final ArchRule CHECK_DIRECT_TESTS_INITIAL_IMPLIED_VERSIONS = noClasses()
            .that()
            .resideInAPackage("..direct..")
            .should()
            .accessTargetWhere(
                    target(owner(assignableTo(ImpliedValueUtil.class))).and(nameMatching("getDescriptorVersion")))
            .orShould()
            .accessTargetWhere(
                    target(owner(assignableTo(ImpliedValueUtil.class))).and(nameMatching("getStateVersion")))
            .orShould()
            .accessTargetWhere(
                    target(owner(assignableTo(ImpliedValueUtil.class))).and(nameMatching("getStateDescriptorVersion")))
            .because("Initial implied value can not be reliable checked in direct tests");
}
