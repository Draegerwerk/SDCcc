/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2024 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.manipulation.precondition

import com.draeger.medical.sdccc.sdcri.testclient.MdibChange
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

    /**
     * Represents a change to the description of the device.
     *
     * @property change the modification message as provided by the MDIB.
     */
    data class Description(val change: DescriptionModificationMessage) : PreconditionChange

    /**
     * Represents a change to the alert states of the device.
     *
     * @property change the modification message as provided by the MDIB.
     */
    data class Alert(val change: AlertStateModificationMessage) : PreconditionChange

    /**
     * Represents a change to the component states of the device.
     *
     * @property change the modification message as provided by the MDIB.
     */
    data class Component(val change: ComponentStateModificationMessage) : PreconditionChange

    /**
     * Represents a change to the context states of the device.
     *
     * @property change the modification message as provided by the MDIB.
     */
    data class Context(val change: ContextStateModificationMessage) : PreconditionChange

    /**
     * Represents a change to the metric states of the device.
     *
     * @property change the modification message as provided by the MDIB.
     */
    data class Metric(val change: MetricStateModificationMessage) : PreconditionChange

    /**
     * Represents a change to the operation states of the device.
     *
     * @property change the modification message as provided by the MDIB.
     */
    data class Operation(val change: OperationStateModificationMessage) : PreconditionChange

    /**
     * Represents a change to the waveform states of the device.
     *
     * @property change the modification message as provided by the MDIB.
     */
    data class Waveform(val change: WaveformStateModificationMessage) : PreconditionChange

    // These methods are intentionally named the same to provide a unified interface, there is no ambiguity
    @Suppress("MethodOverloading")
    companion object {
        /**
         * Factory method to create a [Description] from a [DescriptionModificationMessage].
         *
         * @param change the change to create a [Description] from.
         */
        @JvmStatic
        fun from(change: DescriptionModificationMessage): Description = Description(change)

        /**
         * Factory method to create an [Alert] from an [AlertStateModificationMessage].
         *
         * @param change the change to create an [Alert] from.
         */
        @JvmStatic
        fun from(change: AlertStateModificationMessage): Alert = Alert(change)

        /**
         * Factory method to create a [Component] from a [ComponentStateModificationMessage].
         *
         * @param change the change to create a [Component] from.
         */
        @JvmStatic
        fun from(change: ComponentStateModificationMessage): Component = Component(change)

        /**
         * Factory method to create a [Context] from a [ContextStateModificationMessage].
         *
         * @param change the change to create a [Context] from.
         */
        @JvmStatic
        fun from(change: ContextStateModificationMessage): Context = Context(change)

        /**
         * Factory method to create a [Metric] from a [MetricStateModificationMessage].
         *
         * @param change the change to create a [Metric] from.
         */
        @JvmStatic
        fun from(change: MetricStateModificationMessage): Metric = Metric(change)

        /**
         * Factory method to create an [Operation] from an [OperationStateModificationMessage].
         *
         * @param change the change to create an [Operation] from.
         */
        @JvmStatic
        fun from(change: OperationStateModificationMessage): Operation = Operation(change)

        /**
         * Factory method to create a [Waveform] from a [WaveformStateModificationMessage].
         *
         * @param change the change to create a [Waveform] from.
         */
        @JvmStatic
        fun from(change: WaveformStateModificationMessage): Waveform = Waveform(change)
    }
}

/**
 * Interface for preconditions that can observe changes to the device and trigger upon them.
 */
interface Observing : Precondition {

    /**
     * Receives a change from the device to process.
     */
    fun observeChange(incomingChange: MdibChange)
}
