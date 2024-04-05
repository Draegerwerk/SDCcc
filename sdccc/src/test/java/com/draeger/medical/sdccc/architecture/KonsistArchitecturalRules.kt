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
    fun `the names of requirement test classes should end with 'Test'`() {
        Konsist.scopeFromModule("sdccc").classes()
            .filter { it.resideInPackage("..direct..") || it.resideInPackage("..invariant..") }
            .assertTrue {
                it.name.endsWith("Test")
            }
    }

    @Test
    fun `all requirement tests should have annotations 'TestIdentifier' and 'TestDescription'`() {
        Konsist.scopeFromModule("sdccc").functions()
            .filter { it.resideInPackage("..direct..") || it.resideInPackage("..invariant..") }
            .withAnnotationOf(org.junit.jupiter.api.Test::class)
            .assertTrue {
                it.hasAnnotationOf(TestIdentifier::class) && it.hasAnnotationOf(TestDescription::class)
            }
    }
}
