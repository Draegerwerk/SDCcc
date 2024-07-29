package com.draeger.medical.sdccc.sdcri.testclient

import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.doReturn
import org.somda.sdc.biceps.common.access.MdibAccess
import org.somda.sdc.biceps.common.event.AlertStateModificationMessage
import org.somda.sdc.biceps.common.event.ComponentStateModificationMessage
import org.somda.sdc.biceps.common.event.ContextStateModificationMessage
import org.somda.sdc.biceps.common.event.DescriptionModificationMessage
import org.somda.sdc.biceps.common.event.MetricStateModificationMessage
import org.somda.sdc.biceps.common.event.OperationStateModificationMessage
import org.somda.sdc.biceps.common.event.WaveformStateModificationMessage
import org.somda.sdc.biceps.model.participant.MdibVersion

/**
 * Test for the [TestClientMdibAccessObserver].
 */
internal class TestClientMdibAccessObserverTest {

    internal class CollectingObserver : TestClientMdibObserver {
        val collected = mutableListOf<MdibChange>()

        override fun onAlertChange(alert: MdibChange.Alert) {
            collected.add(alert)
        }

        override fun onComponentChange(component: MdibChange.Component) {
            collected.add(component)
        }

        override fun onContextChange(context: MdibChange.Context) {
            collected.add(context)
        }

        override fun onDescriptionChange(description: MdibChange.Description) {
            collected.add(description)
        }

        override fun onMetricChange(metric: MdibChange.Metric) {
            collected.add(metric)
        }

        override fun onOperationChange(operation: MdibChange.Operation) {
            collected.add(operation)
        }

        override fun onWaveformChange(waveform: MdibChange.Waveform) {
            collected.add(waveform)
        }
    }

    @Test
    fun testDistribution() {
        // Arrange
        val testClientMdibAccessObserver = TestClientMdibAccessObserver()
        val observers = (0 until 10)
            .map { CollectingObserver() }
            .onEach { obs -> testClientMdibAccessObserver.registerObserver(obs) }

        val mockMdibAccess = mock<MdibAccess>()

        val mdibVersion = MdibVersion.create()
        doReturn(mdibVersion).`when`(mockMdibAccess).mdibVersion

        // mock all kinds of data
        val alertChange = mock<AlertStateModificationMessage>()
        val componentChange = mock<ComponentStateModificationMessage>()
        val contextChange = mock<ContextStateModificationMessage>()
        val descriptionChange = mock<DescriptionModificationMessage>()
        val metricChange = mock<MetricStateModificationMessage>()
        val operationChange = mock<OperationStateModificationMessage>()
        val waveformChange = mock<WaveformStateModificationMessage>()

        val mocked = listOf(
            alertChange,
            componentChange,
            contextChange,
            descriptionChange,
            metricChange,
            operationChange,
            waveformChange
        )

        mocked.forEach {
            doReturn(mockMdibAccess).`when`(it).mdibAccess
        }

        // Act
        testClientMdibAccessObserver.onAlertChange(alertChange)
        testClientMdibAccessObserver.onComponentChange(componentChange)
        testClientMdibAccessObserver.onContextChange(contextChange)
        testClientMdibAccessObserver.onDescriptionChange(descriptionChange)
        testClientMdibAccessObserver.onMetricChange(metricChange)
        testClientMdibAccessObserver.onOperationChange(operationChange)
        testClientMdibAccessObserver.onWaveformChange(waveformChange)

        // Assert
        @Suppress("MagicNumber") // magic numbers represent order of elements
        observers.forEach { obs ->
            assert(obs.collected.size == 7)
            obs.collected.forEach {
                assert(it.mdibVersion == mdibVersion)
            }

            assert(obs.collected[0] is MdibChange.Alert)
            assert(obs.collected[1] is MdibChange.Component)
            assert(obs.collected[2] is MdibChange.Context)
            assert(obs.collected[3] is MdibChange.Description)
            assert(obs.collected[4] is MdibChange.Metric)
            assert(obs.collected[5] is MdibChange.Operation)
            assert(obs.collected[6] is MdibChange.Waveform)
        }
    }
}
