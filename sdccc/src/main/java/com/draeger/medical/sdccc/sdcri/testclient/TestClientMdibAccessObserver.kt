/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2024 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.sdcri.testclient

import com.google.common.eventbus.Subscribe
import org.somda.sdc.biceps.common.access.MdibAccessObserver
import org.somda.sdc.biceps.common.event.AlertStateModificationMessage
import org.somda.sdc.biceps.common.event.ComponentStateModificationMessage
import org.somda.sdc.biceps.common.event.ContextStateModificationMessage
import org.somda.sdc.biceps.common.event.DescriptionModificationMessage
import org.somda.sdc.biceps.common.event.MetricStateModificationMessage
import org.somda.sdc.biceps.common.event.OperationStateModificationMessage
import org.somda.sdc.biceps.common.event.WaveformStateModificationMessage

/**
 * Observes changes in the sdc-ri MDIB and makes them available to registered [TestClientMdibObserver]s.
 */
@Suppress("MethodOverloading")
class TestClientMdibAccessObserver : MdibAccessObserver {
    private val registeredObservers = mutableSetOf<TestClientMdibObserver>()

    /**
     * Registers an observer to be notified about changes in the MDIB.
     *
     * @param observer the observer instance to register.
     */
    fun registerObserver(observer: TestClientMdibObserver) {
        registeredObservers.add(observer)
    }

    /**
     * Unregisters an observer to stop receiving notifications about changes in the MDIB.
     *
     * @param observer the observer instance to unregister.
     */
    fun unregisterObserver(observer: TestClientMdibObserver) {
        registeredObservers.remove(observer)
    }

    /**
     * Observes changes in the MDIB and notifies the observer about them.
     */
    @Subscribe
    fun onDescriptionChange(report: DescriptionModificationMessage) {
        registeredObservers.forEach {
            it.onDescriptionChange(MdibChange.from(report.mdibAccess.mdibVersion, report))
        }
    }

    /**
     * Observes changes in the MDIB and notifies the observer about them.
     */
    @Subscribe
    fun onAlertChange(report: AlertStateModificationMessage) {
        registeredObservers.forEach {
            it.onAlertChange(MdibChange.from(report.mdibAccess.mdibVersion, report))
        }
    }

    /**
     * Observes changes in the MDIB and notifies the observer about them.
     */
    @Subscribe
    fun onComponentChange(report: ComponentStateModificationMessage) {
        registeredObservers.forEach {
            it.onComponentChange(MdibChange.from(report.mdibAccess.mdibVersion, report))
        }
    }

    /**
     * Observes changes in the MDIB and notifies the observer about them.
     */
    @Subscribe
    fun onContextChange(report: ContextStateModificationMessage) {
        registeredObservers.forEach {
            it.onContextChange(MdibChange.from(report.mdibAccess.mdibVersion, report))
        }
    }

    /**
     * Observes changes in the MDIB and notifies the observer about them.
     */
    @Subscribe
    fun onMetricChange(report: MetricStateModificationMessage) {
        registeredObservers.forEach {
            it.onMetricChange(MdibChange.from(report.mdibAccess.mdibVersion, report))
        }
    }

    /**
     * Observes changes in the MDIB and notifies the observer about them.
     */
    @Subscribe
    fun onOperationChange(report: OperationStateModificationMessage) {
        registeredObservers.forEach {
            it.onOperationChange(MdibChange.from(report.mdibAccess.mdibVersion, report))
        }
    }

    /**
     * Observes changes in the MDIB and notifies the observer about them.
     */
    @Subscribe
    fun onWaveformChange(report: WaveformStateModificationMessage) {
        registeredObservers.forEach {
            it.onWaveformChange(MdibChange.from(report.mdibAccess.mdibVersion, report))
        }
    }
}
