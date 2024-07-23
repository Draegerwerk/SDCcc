package com.draeger.medical.sdccc.manipulation.precondition

import com.google.inject.Injector
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

internal class SynchronizedObservingPreconditionTest {

    /**
     * Creates a blocking manipulation that blocks for [timeToBlock] and calls [callBeforeSleep] before blocking.
     */
    private fun blockingManipulation(
        timeToBlock: Duration,
        callBeforeSleep: (Unit) -> Unit = {}
    ): (Injector) -> Boolean =
        { _ ->
            callBeforeSleep(Unit)
            Thread.sleep(timeToBlock.inWholeMilliseconds)
            true
        }

    @Test
    internal fun `test synchronization is blocking tasks correctly`() {
        // Arrange
        val timeToBlock = 5.seconds

        val expectedChanges = listOf(
            mock<PreconditionChange.Metric>(),
            mock<PreconditionChange.Alert>(),
            mock<PreconditionChange.Metric>(),
        )
        val mockInjector = mock<Injector>()

        // used to pinpoint when we can start running events
        val threadRunningFuture = CompletableFuture<Unit>()
        val changesCompleteFuture = CompletableFuture<Unit>()
        val receivedChanges = mutableListOf<PreconditionChange>()
        val exampleObserving = object : SynchronizedObservingPrecondition(
            blockingManipulation(timeToBlock, callBeforeSleep = { _ -> threadRunningFuture.complete(Unit) })
        ) {
            override fun change(change: PreconditionChange) {
                receivedChanges.add(change)
                if (receivedChanges.size == expectedChanges.size) {
                    changesCompleteFuture.complete(Unit)
                }
            }
        }

        // run verify in background
        thread(isDaemon = true) {
            exampleObserving.verifyPrecondition(mockInjector)
        }

        threadRunningFuture.get(timeToBlock.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        val measuredObserveChange: Duration
        val timeUntilComplete = measureTime {
            // run observeChange and measure the time we are blocked, must be super low
            measuredObserveChange = measureTime {
                expectedChanges.forEach { exampleObserving.observeChange(it) }
            }

            changesCompleteFuture.get((timeToBlock + 1.seconds).inWholeMilliseconds, TimeUnit.MILLISECONDS)
        }

        val maxWaitForNonBlockingChange = 1.seconds
        assertTrue(
            measuredObserveChange < maxWaitForNonBlockingChange,
            "Expected to be blocked for at most $maxWaitForNonBlockingChange," +
                " but was blocked for $measuredObserveChange"
        )

        val timeTolerance = 1.seconds
        assertTrue(
            timeUntilComplete > timeToBlock - timeTolerance,
            "Expected to be blocked for at least $timeToBlock, but was blocked for $timeUntilComplete"
        )
    }
}
