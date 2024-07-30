/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2024 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package it.com.draeger.medical.sdccc

import com.draeger.medical.sdccc.sdcri.testclient.MdibChange
import com.draeger.medical.sdccc.sdcri.testclient.TestClient
import com.draeger.medical.sdccc.sdcri.testclient.TestClientMdibObserver
import com.draeger.medical.sdccc.tests.InjectorTestBase
import com.google.inject.Injector
import it.com.draeger.medical.sdccc.test_util.testprovider.TestProvider
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.somda.sdc.biceps.model.participant.MdsDescriptor
import org.somda.sdc.glue.GlueConstants
import org.somda.sdc.glue.common.CommonConstants
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/**
 * Integration test for the [TestClient].
 */
class TestClientIT {

    private lateinit var testProvider: TestProvider

    @BeforeEach
    internal fun beforeEach() {
        this.testProvider = TestSuiteIT.getProvider()

        this.testProvider.sdcDevice.device.discoveryAccess
            .apply {
                setTypes(listOf(CommonConstants.MEDICAL_DEVICE_TYPE))
                setScopes(listOf(GlueConstants.SCOPE_SDC_PROVIDER))
            }
    }

    @Test
    @Timeout(TEST_TIMEOUT)
    @Throws(Exception::class)
    @DisplayName("Check first change to observer contains mdib")
    internal fun testConsumerDeliversMdibToObservers() {
        testProvider.startService(DEFAULT_TIMEOUT.toJavaDuration())

        val injector: Injector = TestSuiteIT.getConsumerInjector(true, null, testProvider.sdcDevice.eprAddress)
        InjectorTestBase.setInjector(injector)

        val testClient = injector.getInstance(TestClient::class.java)
        val observer = CollectingObserver()
        testClient.registerMdibObserver(observer)
        testClient.startService(DEFAULT_TIMEOUT.toJavaDuration())
        testClient.connect()

        testClient.disconnect()
        testClient.stopService(DEFAULT_TIMEOUT.toJavaDuration())

        val firstChange = observer.collected.first()
        assertIs<MdibChange.Description>(firstChange)

        // technically this doesn't check it contains the mdib, however the first change that represents an mdib
        // must contain the mds from our xml
        assert(firstChange.insertedEntities.any { it.descriptor is MdsDescriptor && it.handle == "mds0" })
    }

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

    companion object {
        private const val TEST_TIMEOUT: Long = 120
        private val DEFAULT_TIMEOUT = 20.seconds
    }
}
