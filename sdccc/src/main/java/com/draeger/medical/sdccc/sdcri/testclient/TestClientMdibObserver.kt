/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2024 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.sdcri.testclient

import org.somda.sdc.biceps.common.MdibEntity
import org.somda.sdc.biceps.common.event.AlertStateModificationMessage
import org.somda.sdc.biceps.common.event.ComponentStateModificationMessage
import org.somda.sdc.biceps.common.event.ContextStateModificationMessage
import org.somda.sdc.biceps.common.event.DescriptionModificationMessage
import org.somda.sdc.biceps.common.event.MetricStateModificationMessage
import org.somda.sdc.biceps.common.event.OperationStateModificationMessage
import org.somda.sdc.biceps.common.event.WaveformStateModificationMessage
import org.somda.sdc.biceps.model.participant.AbstractAlertState
import org.somda.sdc.biceps.model.participant.AbstractContextState
import org.somda.sdc.biceps.model.participant.AbstractDeviceComponentState
import org.somda.sdc.biceps.model.participant.AbstractMetricState
import org.somda.sdc.biceps.model.participant.AbstractOperationState
import org.somda.sdc.biceps.model.participant.MdibVersion
import org.somda.sdc.biceps.model.participant.RealTimeSampleArrayMetricState

/**
 * Simplification of [org.somda.sdc.biceps.common.access.MdibAccessObserver] for the [TestClient].
 *
 * All available methods have defaults implemented, override the ones necessary to retrieve the desired data.
 */
interface TestClientMdibObserver {
    /**
     * Called when the description of the device changes.
     *
     * @param description the change to the description.
     */
    fun onDescriptionChange(description: MdibChange.Description) {}

    /**
     * Called when the alert states of the device change.
     *
     * @param alert the change to the alert states.
     */
    fun onAlertChange(alert: MdibChange.Alert) {}

    /**
     * Called when the component states of the device change.
     *
     * @param component the change to the component states.
     */
    fun onComponentChange(component: MdibChange.Component) {}

    /**
     * Called when the context states of the device change.
     *
     * @param context the change to the context states.
     */
    fun onContextChange(context: MdibChange.Context) {}

    /**
     * Called when the metric states of the device change.
     *
     * @param metric the change to the metric states.
     */
    fun onMetricChange(metric: MdibChange.Metric) {}

    /**
     * Called when the operation states of the device change.
     *
     * @param operation the change to the operation states.
     */
    fun onOperationChange(operation: MdibChange.Operation) {}

    /**
     * Called when the waveform states of the device change.
     *
     * @param waveform the change to the waveform states.
     */
    fun onWaveformChange(waveform: MdibChange.Waveform) {}
}

/**
 * Representation of all kinds of change that can be observed by a precondition.
 */
sealed interface MdibChange {
    /**
     * The MDIB version that the change is occurring in.
     */
    val mdibVersion: MdibVersion

    /**
     * Represents a change to the description of the device.
     *
     * @property mdibVersion      the MDIB version that the change is occurring in.
     * @property insertedEntities the entities that were inserted.
     * @property updatedEntities  the entities that were updated.
     * @property deletedEntities  the entities that were deleted.
     */
    data class Description(
        override val mdibVersion: MdibVersion,
        val insertedEntities: List<MdibEntity>,
        val updatedEntities: List<MdibEntity>,
        val deletedEntities: List<MdibEntity>,
    ) : MdibChange

    /**
     * Represents a change to the alert states of the device.
     *
     * @property mdibVersion      the MDIB version that the change is occurring in.
     * @property states the modification where the key is the source MDS and
     *                  the value is a list of all states for that MDS.
     */
    data class Alert(
        override val mdibVersion: MdibVersion,
        val states: Map<String, List<AbstractAlertState>>
    ) : MdibChange

    /**
     * Represents a change to the component states of the device.
     *
     * @property mdibVersion      the MDIB version that the change is occurring in.
     * @property states the modification where the key is the source MDS and
     *                  the value is a list of all states for that MDS.
     */
    data class Component(
        override val mdibVersion: MdibVersion,
        val states: Map<String, List<AbstractDeviceComponentState>>
    ) : MdibChange

    /**
     * Represents a change to the context states of the device.
     *
     * @property mdibVersion      the MDIB version that the change is occurring in.
     * @property states the modification where the key is the source MDS and
     *                  the value is a list of all states for that MDS.
     */
    data class Context(
        override val mdibVersion: MdibVersion,
        val states: Map<String, List<AbstractContextState>>
    ) : MdibChange

    /**
     * Represents a change to the metric states of the device.
     *
     * @property mdibVersion      the MDIB version that the change is occurring in.
     * @property states the modification where the key is the source MDS and
     *                  the value is a list of all states for that MDS.
     */
    data class Metric(
        override val mdibVersion: MdibVersion,
        val states: Map<String, List<AbstractMetricState>>
    ) : MdibChange

    /**
     * Represents a change to the operation states of the device.
     *
     * @property mdibVersion      the MDIB version that the change is occurring in.
     * @property states the modification where the key is the source MDS and
     *                  the value is a list of all states for that MDS.
     */
    data class Operation(
        override val mdibVersion: MdibVersion,
        val states: Map<String, List<AbstractOperationState>>
    ) : MdibChange

    /**
     * Represents a change to the waveform states of the device.
     *
     * @property mdibVersion      the MDIB version that the change is occurring in.
     * @property states the modification where the key is the source MDS and
     *                  the value is a list of all states for that MDS.
     */
    data class Waveform(
        override val mdibVersion: MdibVersion,
        val states: Map<String, List<RealTimeSampleArrayMetricState>>
    ) : MdibChange

    // These methods are intentionally named the same to provide a unified interface, there is no ambiguity
    @Suppress("MethodOverloading")
    companion object {
        /**
         * Factory method to create a [Description] from a [DescriptionModificationMessage].
         *
         * @param version the MDIB version that the change is occurring in.
         * @param change the change to create a [Description] from.
         */
        @JvmStatic
        fun from(version: MdibVersion, change: DescriptionModificationMessage): Description =
            Description(version, change.insertedEntities, change.updatedEntities, change.deletedEntities)

        /**
         * Factory method to create an [Alert] from an [AlertStateModificationMessage].
         *
         * @param version the MDIB version that the change is occurring in.
         * @param change the change to create an [Alert] from.
         */
        @JvmStatic
        fun from(version: MdibVersion, change: AlertStateModificationMessage): Alert = Alert(version, change.states)

        /**
         * Factory method to create a [Component] from a [ComponentStateModificationMessage].
         *
         * @param version the MDIB version that the change is occurring in.
         * @param change the change to create a [Component] from.
         */
        @JvmStatic
        fun from(version: MdibVersion, change: ComponentStateModificationMessage): Component = Component(
            version,
            change.states
        )

        /**
         * Factory method to create a [Context] from a [ContextStateModificationMessage].
         *
         * @param version the MDIB version that the change is occurring in.
         * @param change the change to create a [Context] from.
         */
        @JvmStatic
        fun from(version: MdibVersion, change: ContextStateModificationMessage): Context = Context(
            version,
            change.states
        )

        /**
         * Factory method to create a [Metric] from a [MetricStateModificationMessage].
         *
         * @param version the MDIB version that the change is occurring in.
         * @param change the change to create a [Metric] from.
         */
        @JvmStatic
        fun from(version: MdibVersion, change: MetricStateModificationMessage): Metric = Metric(version, change.states)

        /**
         * Factory method to create an [Operation] from an [OperationStateModificationMessage].
         *
         * @param version the MDIB version that the change is occurring in.
         * @param change the change to create an [Operation] from.
         */
        @JvmStatic
        fun from(version: MdibVersion, change: OperationStateModificationMessage): Operation = Operation(
            version,
            change.states
        )

        /**
         * Factory method to create a [Waveform] from a [WaveformStateModificationMessage].
         *
         * @param version the MDIB version that the change is occurring in.
         * @param change the change to create a [Waveform] from.
         */
        @JvmStatic
        fun from(version: MdibVersion, change: WaveformStateModificationMessage): Waveform = Waveform(
            version,
            change.states
        )
    }
}
