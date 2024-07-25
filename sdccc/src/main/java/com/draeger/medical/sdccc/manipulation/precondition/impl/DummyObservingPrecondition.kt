/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2024 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.manipulation.precondition.impl

import com.draeger.medical.sdccc.manipulation.precondition.ManipulationFunction
import com.draeger.medical.sdccc.manipulation.precondition.Observing
import com.draeger.medical.sdccc.manipulation.precondition.ObservingPreconditionFactory
import com.draeger.medical.sdccc.manipulation.precondition.PreconditionChange
import com.draeger.medical.sdccc.manipulation.precondition.SynchronizedObservingPrecondition
import com.draeger.medical.sdccc.sdcri.testclient.TestClient
import com.google.inject.Injector

/**
 * An example of an observing precondition.
 */
class DummyObservingPrecondition(injector: Injector) : SynchronizedObservingPrecondition(
    injector,
    { _ ->
        println("DummyObservingPrecondition called")
        true
    }
) {
    override fun change(injector: Injector, change: PreconditionChange) {
        val testClient = injector.getInstance(TestClient::class.java)!!

        println("Has test client: $testClient")

        when (change) {
            is PreconditionChange.Metric -> println("Saw metric change ${change.change.states}")
            else -> println("Saw change $change")
        }
    }


    companion object: ObservingPreconditionFactory<DummyObservingPrecondition> {
        /**
         * Factory method to create an [Observing] precondition.
         *
         * @param injector the manipulation to execute when the precondition is triggered.
         */
        @JvmStatic
        override fun create(
            injector: Injector,
        ): DummyObservingPrecondition
        {
            return DummyObservingPrecondition(injector)
        }

    }
}
