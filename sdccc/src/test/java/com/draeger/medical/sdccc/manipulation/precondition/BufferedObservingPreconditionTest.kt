/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2024 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.manipulation.precondition

import com.draeger.medical.sdccc.sdcri.testclient.MdibChange
import com.google.inject.Injector
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

/**
 * Unit tests for [BufferedObservingPrecondition].
 */
internal class BufferedObservingPreconditionTest {

    @Test
    internal fun `test synchronization is blocking tasks correctly`() {
        // Arrange
        val expectedChanges = listOf(
            mock<MdibChange.Metric>(),
            mock<MdibChange.Alert>(),
            mock<MdibChange.Metric>(),
        )
        val mockInjector = mock<Injector>()

        val timeToBlockPerMessage = 1.seconds
        val timeToBlock = timeToBlockPerMessage.times(expectedChanges.size)
        val tolerance = 1.seconds

        val changesCompleteFuture = CompletableFuture<Unit>()
        val receivedChanges = mutableListOf<MdibChange>()
        val exampleObserving = object : BufferedObservingPrecondition(
            injector = mockInjector,
        ) {
            override fun processChange(change: MdibChange) {
                Thread.sleep(timeToBlockPerMessage.inWholeMilliseconds)
                receivedChanges.add(change)
                if (receivedChanges.size == expectedChanges.size) {
                    changesCompleteFuture.complete(Unit)
                }
            }
        }

        // run verify in background
        val measuredObserveChange: Duration
        val timeUntilComplete = measureTime {
            // run observeChange and measure the time we are blocked, must be super low
            measuredObserveChange = measureTime {
                expectedChanges.forEach { exampleObserving.observeChange(it) }
            }

            changesCompleteFuture.get((timeToBlock + tolerance).inWholeMilliseconds, TimeUnit.MILLISECONDS)
        }

        assertTrue(
            measuredObserveChange < tolerance,
            "Expected to be blocked for at most $tolerance," +
                " but was blocked for $measuredObserveChange"
        )

        assertTrue(
            timeUntilComplete > timeToBlock - tolerance,
            "Expected to be blocked for at least $timeToBlock, but was blocked for $timeUntilComplete"
        )
    }

    @Test
    internal fun `test equality check`() {
        val mockInjector = mock<Injector>()
        val exampleObserving = object : BufferedObservingPrecondition(
            injector = mockInjector,
        ) {
            override fun processChange(change: MdibChange) {
                // do nothing
            }
        }

        val exampleObserving2 = object : BufferedObservingPrecondition(
            injector = mockInjector,
        ) {
            override fun processChange(change: MdibChange) {
                // do nothing
            }
        }

        // same class == equal
        assertEquals(exampleObserving, exampleObserving)
        assertEquals(exampleObserving2, exampleObserving2)

        // different class, in this case anonymous classes, are not equal
        assertNotEquals(
            exampleObserving as BufferedObservingPrecondition,
            exampleObserving2 as BufferedObservingPrecondition
        )
    }
}
