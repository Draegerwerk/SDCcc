package com.draeger.medical.sdccc.architecture

import com.draeger.medical.sdccc.tests.annotations.TestDescription
import com.draeger.medical.sdccc.tests.annotations.TestIdentifier
import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.list.withAnnotationOf
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.jupiter.api.Test

/**
 * Checks the listed architectural rules for Kotlin.
 */
class KonsistArchitecturalRules {

    @Test
    fun `requirement test classes in module 'sdccc' should end with 'Test'`() {
        checkNameOfRequirementTestClasses("sdccc")
    }

    @Test
    fun `all requirement tests in module 'sdccc' should have annotations 'TestIdentifier' and 'TestDescription'`() {
        checkAnnotationsOfRequirementTests("sdccc")
    }

    companion object {

        fun checkNameOfRequirementTestClasses(moduleName: String, sourceSet: String = "main") {
            Konsist.scopeFromProject(moduleName = moduleName, sourceSetName = sourceSet).classes()
                .filter { it.resideInPackage("..direct..") || it.resideInPackage("..invariant..") }
                .assertTrue {
                    it.name.endsWith("Test")
                }
        }

        fun checkAnnotationsOfRequirementTests(
            moduleName: String,
            sourceSet: String = "main"
        ) {
            Konsist.scopeFromProject(moduleName = moduleName, sourceSetName = sourceSet).functions()
                .filter { it.resideInPackage("..direct..") || it.resideInPackage("..invariant..") }
                .withAnnotationOf(org.junit.jupiter.api.Test::class)
                .assertTrue {
                    it.hasAnnotationOf(TestIdentifier::class) && it.hasAnnotationOf(TestDescription::class)
                }
        }
    }
}
