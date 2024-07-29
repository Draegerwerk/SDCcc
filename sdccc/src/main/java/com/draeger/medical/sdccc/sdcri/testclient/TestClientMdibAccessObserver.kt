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

class TestClientMdibAccessObserver: MdibAccessObserver {
    private val registeredObservers = mutableSetOf<TestClientMdibObserver>()

    fun registerObserver(observer: TestClientMdibObserver) {
        registeredObservers.add(observer)
    }

    fun unregisterObserver(observer: TestClientMdibObserver) {
        registeredObservers.remove(observer)
    }

    /**
     * Observes changes in the MDIB and notifies the observer about them.
     */
    @Subscribe
    fun onChange(report: DescriptionModificationMessage) {
        registeredObservers.forEach {
            it.onDescriptionChange(MdibChange.from(report.mdibAccess.mdibVersion, report))
        }
    }

    /**
     * Observes changes in the MDIB and notifies the observer about them.
     */
    @Subscribe
    fun onChange(report: AlertStateModificationMessage) {
        registeredObservers.forEach {
            it.onAlertChange(MdibChange.from(report.mdibAccess.mdibVersion, report))
        }
    }

    /**
     * Observes changes in the MDIB and notifies the observer about them.
     */
    @Subscribe
    fun onChange(report: ComponentStateModificationMessage) {
        registeredObservers.forEach {
            it.onComponentChange(MdibChange.from(report.mdibAccess.mdibVersion, report))
        }
    }

    /**
     * Observes changes in the MDIB and notifies the observer about them.
     */
    @Subscribe
    fun onChange(report: ContextStateModificationMessage) {
        registeredObservers.forEach {
            it.onContextChange(MdibChange.from(report.mdibAccess.mdibVersion, report))
        }
    }

    /**
     * Observes changes in the MDIB and notifies the observer about them.
     */
    @Subscribe
    fun onChange(report: MetricStateModificationMessage) {
        registeredObservers.forEach {
            it.onMetricChange(MdibChange.from(report.mdibAccess.mdibVersion, report))
        }
    }

    /**
     * Observes changes in the MDIB and notifies the observer about them.
     */
    @Subscribe
    fun onChange(report: OperationStateModificationMessage) {
        registeredObservers.forEach {
            it.onOperationChange(MdibChange.from(report.mdibAccess.mdibVersion, report))
        }
    }

    /**
     * Observes changes in the MDIB and notifies the observer about them.
     */
    @Subscribe
    fun onChange(report: WaveformStateModificationMessage) {
        registeredObservers.forEach {
            it.onWaveformChange(MdibChange.from(report.mdibAccess.mdibVersion, report))
        }
    }
}