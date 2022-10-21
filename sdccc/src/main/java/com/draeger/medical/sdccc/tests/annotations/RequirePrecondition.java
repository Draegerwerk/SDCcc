/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.tests.annotations;

import com.draeger.medical.sdccc.manipulation.precondition.ManipulationPrecondition;
import com.draeger.medical.sdccc.manipulation.precondition.SimplePrecondition;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to add to invariant test cases which require a certain manipulation.
 *
 * <p>
 * Manipulations for direct test cases are triggered during the test case itself, while invariant tests trigger their
 * interactions all at once before SDCcc has disconnected from the DUT.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePrecondition {

    /**
     * Conditional interactions to trigger.
     *
     * @return array of complex interaction classes to trigger for the test
     */
    Class<? extends SimplePrecondition>[] simplePreconditions() default {};

    /**
     * Manipulations to trigger.
     *
     * @return array of manipulation classes to trigger for the test
     */
    Class<? extends ManipulationPrecondition>[] manipulationPreconditions() default {};

}
