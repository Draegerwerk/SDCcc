/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2024 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.manipulation.precondition

import com.draeger.medical.sdccc.sdcri.testclient.MdibChange
import com.draeger.medical.sdccc.sdcri.testclient.TestClientMdibObserver

/**
 * Observes changes in the TestClient MDIB and notifies the precondition about them.
 */
@Suppress("MethodOverloading") // the names are irrelevant, the dispatching is based on types
class ObservingPreconditionMdibObserver(
    private val precondition: Observing
) : TestClientMdibObserver {

    override fun onDescriptionChange(description: MdibChange.Description) {
        precondition.observeChange(description)
    }

    override fun onAlertChange(alert: MdibChange.Alert) {
        precondition.observeChange(alert)
    }

    override fun onComponentChange(component: MdibChange.Component) {
        precondition.observeChange(component)
    }

    override fun onContextChange(context: MdibChange.Context) {
        precondition.observeChange(context)
    }

    override fun onMetricChange(metric: MdibChange.Metric) {
        precondition.observeChange(metric)
    }

    override fun onOperationChange(operation: MdibChange.Operation) {
        precondition.observeChange(operation)
    }

    override fun onWaveformChange(waveform: MdibChange.Waveform) {
        precondition.observeChange(waveform)
    }
}
