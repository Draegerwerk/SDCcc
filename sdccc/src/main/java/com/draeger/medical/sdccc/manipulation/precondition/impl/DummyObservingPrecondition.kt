/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2024 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.manipulation.precondition.impl

import com.draeger.medical.sdccc.manipulation.precondition.PreconditionChange
import com.draeger.medical.sdccc.manipulation.precondition.SynchronizedObservingPrecondition

/**
 * An example of an observing precondition.
 */
class DummyObservingPrecondition : SynchronizedObservingPrecondition(
    { _ ->
        println("DummyObservingPrecondition called")
        true
    }
) {
    override fun change(change: PreconditionChange) {
        when (change) {
            is PreconditionChange.Metric -> println("Saw metric change ${change.change.states}")
            else -> println("Saw change $change")
        }
    }
}
