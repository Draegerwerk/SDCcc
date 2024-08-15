package com.draeger.medical.sdccc.manipulation

import com.google.inject.Injector
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.util.concurrent.CompletableFuture
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

class ManipulationLockerTest {

    private val mockInjector = mock<Injector>()

    @Test
    internal fun `test lock`() {
        val locker = ManipulationLocker(mockInjector)
        val timeToBlock = 1.seconds
        val timeTolerance = 100.milliseconds
        // lock and block
        val blockingLock = startThreadWithFuture(locker) {
            Thread.sleep(timeToBlock.inWholeMilliseconds)
        }

        // wait for the thread to lock
        blockingLock.future.join()
        val elapsedBlocked = measureTime {
            locker.lock("blockedResult") {
                println("blockedResult")
            }
        }

        val elapsed = timeToBlock - elapsedBlocked
        assertTrue(
            "Elapsed time $elapsedBlocked is not within $timeTolerance of $timeToBlock "
        ) { elapsed.absoluteValue <= timeTolerance }
    }

    // this is obviously not a proper test for fairness, functions as a sanity check for fairness
    @Test
    internal fun `test lock fairness`() {
        val locker = ManipulationLocker(mockInjector)
        val timeToBlock = 1.seconds
        val locksToAcquire = 10000
        // lock and block
        val hasLock = startThreadWithFuture(locker) {
            Thread.sleep(timeToBlock.inWholeMilliseconds)
        }

        // wait for the thread to lock
        hasLock.future.join()
        val resultList = mutableListOf<Int>()
        // start tasks that should remain in order

        val threads = (0 ..< locksToAcquire).map {
            val res = startThreadWithFuture(locker) {
                resultList.add(it)
            }
            res.future.join()
            res
        }

        threads.forEach { it.thread.join() }

        locker.lock("blockedResult") {
            println("blockedResult")
        }

        assertEquals(locksToAcquire, resultList.size)
        assertEquals(resultList.sorted(), resultList)
    }

    private fun startThreadWithFuture(
        locker: ManipulationLocker,
        supplier: () -> Unit
    ): ThreadAndFuture {
        val hasLock = CompletableFuture<Unit>()
        val t = thread (isDaemon = true) {
            locker.lock("result") {
                hasLock.complete(null)
                supplier()
            }
        }
        return ThreadAndFuture(t, hasLock)
    }

    private data class ThreadAndFuture(val thread: Thread, val future: CompletableFuture<Unit>)
}