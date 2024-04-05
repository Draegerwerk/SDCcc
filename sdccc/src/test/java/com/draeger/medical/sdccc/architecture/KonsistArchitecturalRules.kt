package com.draeger.medical.sdccc.architecture

import com.draeger.medical.sdccc.tests.annotations.TestDescription
import com.draeger.medical.sdccc.tests.annotations.TestIdentifier
import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.list.withAnnotationOf
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.Test

/**
 * Checks the listed architectural rules for Kotlin.
 */
class KonsistArchitecturalRules {

    @Test
    fun `requirement test classes in module 'sdccc' should end with 'Test'`() {
        `the names of requirement test classes in module should end with 'Test'`(moduleName = "sdccc")
    }

    @Test
    fun `all requirement tests in module 'sdccc' should have annotations 'TestIdentifier' and 'TestDescription'`() {
        `all requirement tests in module should have annotations 'TestIdentifier' and 'TestDescription'`(moduleName = "sdccc")
    }

    companion object {
        fun `the names of requirement test classes in module should end with 'Test'`(moduleName: String) {
            Konsist.scopeFromModule(moduleName).classes()
                .filter { it.resideInPackage("..direct..") || it.resideInPackage("..invariant..") }
                .assertTrue {
                    it.name.endsWith("Test")
                }
        }

        fun `all requirement tests in module should have annotations 'TestIdentifier' and 'TestDescription'`(moduleName: String) {
            Konsist.scopeFromModule(moduleName).functions()
                .filter { it.resideInPackage("..direct..") || it.resideInPackage("..invariant..") }
                .withAnnotationOf(org.junit.jupiter.api.Test::class)
                .assertTrue {
                    it.hasAnnotationOf(TestIdentifier::class) && it.hasAnnotationOf(TestDescription::class)
                }
        }
    }
}
