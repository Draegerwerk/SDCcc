package com.draeger.medical.sdccc.manipulation.precondition

import org.somda.sdc.biceps.common.event.AlertStateModificationMessage
import org.somda.sdc.biceps.common.event.ComponentStateModificationMessage
import org.somda.sdc.biceps.common.event.ContextStateModificationMessage
import org.somda.sdc.biceps.common.event.DescriptionModificationMessage
import org.somda.sdc.biceps.common.event.MetricStateModificationMessage
import org.somda.sdc.biceps.common.event.OperationStateModificationMessage
import org.somda.sdc.biceps.common.event.WaveformStateModificationMessage

/**
 * Representation of all kinds of change that can be observed by a precondition.
 */
sealed interface PreconditionChange {
    data class Description(val change: DescriptionModificationMessage): PreconditionChange
    data class Alert(val change: AlertStateModificationMessage): PreconditionChange
    data class Component(val change: ComponentStateModificationMessage): PreconditionChange
    data class Context(val change: ContextStateModificationMessage): PreconditionChange
    data class Metric(val change: MetricStateModificationMessage): PreconditionChange
    data class Operation(val change: OperationStateModificationMessage): PreconditionChange
    data class Waveform(val change: WaveformStateModificationMessage): PreconditionChange

    companion object {
        @JvmStatic
        fun from(change: DescriptionModificationMessage): Description = Description(change)

        @JvmStatic
        fun from(change: AlertStateModificationMessage): Alert = Alert(change)

        @JvmStatic
        fun from(change: ComponentStateModificationMessage): Component = Component(change)

        @JvmStatic
        fun from(change: ContextStateModificationMessage): Context = Context(change)

        @JvmStatic
        fun from(change: MetricStateModificationMessage): Metric = Metric(change)

        @JvmStatic
        fun from(change: OperationStateModificationMessage): Operation = Operation(change)

        @JvmStatic
        fun from(change: WaveformStateModificationMessage): Waveform = Waveform(change)
    }
}

/**
 * Interface for preconditions that can observe changes to the device and trigger upon them.
 */
interface Observing: Precondition {

    /**
     * Receives a change from the device to process.
     */
    fun observeChange(incomingChange: PreconditionChange)
}