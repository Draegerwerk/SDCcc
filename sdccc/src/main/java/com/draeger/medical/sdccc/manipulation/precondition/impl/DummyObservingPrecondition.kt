package com.draeger.medical.sdccc.manipulation.precondition.impl

import com.draeger.medical.sdccc.manipulation.precondition.SynchronizedObservingPrecondition
import com.draeger.medical.sdccc.manipulation.precondition.PreconditionChange

class DummyObservingPrecondition: SynchronizedObservingPrecondition(
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