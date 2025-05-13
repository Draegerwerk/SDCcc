/*
 * This Source Code Form is subject to the terms of the "SDCcc non-commercial use license".
 *
 * Copyright (C) 2025 Draegerwerk AG & Co. KGaA
 */

package com.draeger.medical.sdccc.architecture;

import com.tngtech.archunit.core.domain.AccessTarget;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Checks the listed Architectural Rules.
 */
@AnalyzeClasses(
        packages = "com.draeger.medical.sdccc.manipulation",
        importOptions = {ImportOption.DoNotIncludeTests.class})
public class GrpcManipulationsArchRulesTest {

    /**
     * Ensures that every method in GRpcManipulations overriding Manipulations method is called in GRpcManipulationsTest.
     *
     * @param importedClasses required by the ArchTest framework (unused)
     * @throws AssertionError if any overridden method is not invoked in the test.
     */
    @ArchTest
    public static void grpcManipulationsMethodsAreTested(final JavaClasses importedClasses) {
        final List<String> violations = new ArrayList<>();

        final JavaClasses productionClasses = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.draeger.medical.sdccc.manipulation");

        final JavaClass grpcManipulations = productionClasses.stream()
                .filter(c -> c.getSimpleName().equals("GRpcManipulations"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("GRpcManipulations class not found in production code."));

        final JavaClass manipulationsInterface = productionClasses.stream()
                .filter(c -> c.getSimpleName().equals("Manipulations"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Manipulations interface not found in production code."));

        final Set<JavaMethod> implementationMethods = grpcManipulations.getMethods().stream()
                .filter(method -> method.getOwner().equals(grpcManipulations))
                .filter(method -> manipulationsInterface.getMethods().stream().anyMatch(interfaceMethod -> {
                    if (!method.getName().equals(interfaceMethod.getName())) {
                        return false;
                    }
                    final List<String> methodParamTypes = method.getRawParameterTypes().stream()
                            .map(t -> t.getFullName())
                            .toList();
                    final List<String> interfaceParamTypes = interfaceMethod.getRawParameterTypes().stream()
                            .map(t -> t.getFullName())
                            .toList();
                    return methodParamTypes.equals(interfaceParamTypes);
                }))
                .collect(Collectors.toSet());

        final JavaClasses testClasses =
                new ClassFileImporter().importPackages("com.draeger.medical.sdccc.manipulation");
        final JavaClass grpcManipulationsTest = testClasses.stream()
                .filter(c -> c.getSimpleName().equals("GRpcManipulationsTest"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("GRpcManipulationsTest class not found in tests."));

        final Set<JavaMethodCall> testCalls = grpcManipulationsTest.getMethodCallsFromSelf();

        for (JavaMethod implMethod : implementationMethods) {
            final boolean isTested = testCalls.stream().anyMatch(call -> {
                final AccessTarget.MethodCallTarget target = call.getTarget();

                if (target.getOwner().getFullName().equals(grpcManipulations.getFullName())) {
                    if (!target.getName().equals(implMethod.getName())) {
                        return false;
                    }
                    final List<String> targetParams = target.getRawParameterTypes().stream()
                            .map(t -> t.getFullName())
                            .toList();
                    final List<String> implParams = implMethod.getRawParameterTypes().stream()
                            .map(t -> t.getFullName())
                            .toList();
                    return targetParams.equals(implParams);
                }

                if (target.getOwner().getFullName().equals(grpcManipulations.getFullName())) {
                    if (!target.getName().equals(implMethod.getName())) {
                        return false;
                    }
                    final List<String> targetParams = target.getRawParameterTypes().stream()
                            .map(t -> t.getFullName())
                            .toList();
                    final List<String> implParams = implMethod.getRawParameterTypes().stream()
                            .map(t -> t.getFullName())
                            .toList();
                    return targetParams.equals(implParams);
                }
                return false;
            });
            if (!isTested) {
                violations.add(String.format(
                        "Implementation method %s (overriding an interface method) is not called in GRpcManipulationsTest.",
                        implMethod.getFullName()));
            }
        }
        if (!violations.isEmpty()) {
            throw new AssertionError(String.join("\n", violations));
        }
    }
}
