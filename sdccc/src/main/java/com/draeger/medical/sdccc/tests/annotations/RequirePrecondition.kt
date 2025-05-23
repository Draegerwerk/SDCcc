/*
 * This Source Code Form is subject to the terms of the "SDCcc non-commercial use license".
 *
 * Copyright (C) 2025 Draegerwerk AG & Co. KGaA
 */
package com.draeger.medical.sdccc.tests.annotations

import com.draeger.medical.sdccc.manipulation.precondition.ManipulationPrecondition
import com.draeger.medical.sdccc.manipulation.precondition.SimplePrecondition
import kotlin.reflect.KClass

/**
 * Annotation to add to invariant test cases which require a certain manipulation.
 *
 * Manipulations for direct test cases are triggered during the test case itself, while invariant tests trigger their
 * interactions all at once before SDCcc has disconnected from the DUT.
 */
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER
)
@Retention(
    AnnotationRetention.RUNTIME
)
annotation class RequirePrecondition(
    /**
     * Conditional interactions to trigger.
     *
     * @return array of complex interaction classes to trigger for the test
     */
    val simplePreconditions: Array<KClass<out SimplePrecondition>> = [],
    /**
     * Manipulations to trigger.
     *
     * @return array of manipulation classes to trigger for the test
     */
    val manipulationPreconditions: Array<KClass<out ManipulationPrecondition>> = [],
)
