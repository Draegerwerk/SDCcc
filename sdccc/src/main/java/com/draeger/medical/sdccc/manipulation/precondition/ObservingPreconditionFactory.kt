/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2024 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.manipulation.precondition

import com.google.inject.Injector

/**
 * Factory for creating an [Observing] precondition.
 */
interface ObservingPreconditionFactory<PRECONDITION : Observing> {

    /**
     * Creates a new [Observing] precondition.
     */
    fun create(injector: Injector): PRECONDITION
}
