/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2024 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.manipulation

import com.google.inject.Injector
import org.apache.logging.log4j.kotlin.Logging
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.doReturn
import java.util.concurrent.CompletableFuture
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

internal class ManipulationLockerTest {

    private val mockInjector = mock<Injector>()

    @BeforeEach
    internal fun setUp() {
        doReturn(mock(Manipulations::class.java)).`when`(mockInjector).getInstance(Manipulations::class.java)
    }

    @Test
    internal fun `test lock generic`() {
        val locker = ManipulationLocker(mockInjector)
        val timeToBlock = 1.seconds
        val timeTolerance = 100.milliseconds

        assertFalse { locker.isLocked() }

        // lock and block
        val blockingLock = startThreadWithFuture(locker) {
            Thread.sleep(timeToBlock.inWholeMilliseconds)
        }

        // wait for the thread to lock
        blockingLock.future.join()
        val elapsedBlocked = measureTime {
            locker.lock(LOCKED_BY_NAME) {
                logger.debug { LOCK_LOG_MESSAGE }
                assertEquals(LOCKED_BY_NAME, locker.lockedBy())
                assertTrue { locker.isLocked() }
            }
        }
        assertNull(locker.lockedBy())
        assertFalse { locker.isLocked() }

        val elapsed = timeToBlock - elapsedBlocked
        assertTrue(
            "Elapsed time $elapsedBlocked is not within $timeTolerance of $timeToBlock "
        ) { elapsed.absoluteValue <= timeTolerance }
    }

    @Test
    internal fun `test lock manipulations`() {
        val locker = ManipulationLocker(mockInjector)
        val timeToBlock = 1.seconds
        val timeTolerance = 100.milliseconds

        assertFalse { locker.isLocked() }

        // lock and block
        val blockingLock = startThreadWithFuture(locker) {
            Thread.sleep(timeToBlock.inWholeMilliseconds)
        }

        // wait for the thread to lock
        blockingLock.future.join()
        val elapsedBlocked = measureTime {
            locker.lockManipulations(LOCKED_BY_NAME, Manipulations::class.java) {
                logger.debug { LOCK_LOG_MESSAGE }
                assertEquals(LOCKED_BY_NAME, locker.lockedBy())
                assertTrue { locker.isLocked() }
            }
        }
        assertNull(locker.lockedBy())
        assertFalse { locker.isLocked() }

        val elapsed = timeToBlock - elapsedBlocked
        assertTrue(
            "Elapsed time $elapsedBlocked is not within $timeTolerance of $timeToBlock "
        ) { elapsed.absoluteValue <= timeTolerance }
    }

    @Test
    internal fun `test lock observer`() {
        val locker = ManipulationLocker(mockInjector)

        val lockedNames1 = mutableListOf<String?>()
        val lockedNames2 = mutableListOf<String?>()
        val lockedNames3 = mutableListOf<String?>()

        val observer1: (String?) -> Unit = { lockedNames1.add(it) }
        val observer2: (String?) -> Unit = { lockedNames2.add(it) }
        val observer3: (String?) -> Unit = { lockedNames3.add(it) }

        locker.subscribe(observer1)
        locker.subscribe(observer2)
        locker.subscribe(observer3)

        val timeToBlock = 1.seconds
        // lock and block
        val firstLockName = "myFirstLock :)"
        val blockingLock = startThreadWithFuture(locker, firstLockName) {
            Thread.sleep(timeToBlock.inWholeMilliseconds)
        }

        // wait for the thread to complete
        blockingLock.thread.join()
        // unsubscribe one
        locker.unsubscribe(observer3)
        assertEquals(listOf(firstLockName, null), lockedNames3)

        locker.lock(LOCKED_BY_NAME) {
            logger.debug { LOCK_LOG_MESSAGE }
            assertEquals(LOCKED_BY_NAME, locker.lockedBy())
        }
        locker.unsubscribe(observer2)

        assertNull(locker.lockedBy())
        assertEquals(listOf(firstLockName, null), lockedNames3) // remains unchanged
        assertEquals(listOf(firstLockName, null, LOCKED_BY_NAME, null), lockedNames2)

        locker.lock(LOCKED_BY_NAME) {
            logger.debug { LOCK_LOG_MESSAGE }
            assertEquals(LOCKED_BY_NAME, locker.lockedBy())
        }

        locker.unsubscribe(observer1)

        assertEquals(listOf(firstLockName, null), lockedNames3) // remains unchanged
        assertEquals(listOf(firstLockName, null, LOCKED_BY_NAME, null), lockedNames2) // remains unchanged
        assertEquals(listOf(firstLockName, null, LOCKED_BY_NAME, null, LOCKED_BY_NAME, null), lockedNames1)
    }

    // this is obviously not a proper test for fairness, functions as a sanity check for fairness
    @Test
    internal fun `test lock fairness`() {
        val locker = ManipulationLocker(mockInjector)
        val timeToBlock = 1.seconds
        val locksToAcquire = 10_000
        // lock and block
        val hasLock = startThreadWithFuture(locker) {
            Thread.sleep(timeToBlock.inWholeMilliseconds)
        }

        // wait for the thread to lock
        hasLock.future.join()
        val resultList = mutableListOf<Int>()
        // start tasks that should remain in order

        val threads = (0..<locksToAcquire).map {
            val res = startThreadWithFuture(locker) {
                resultList.add(it)
            }
            res.future.join()
            res
        }

        threads.forEach { it.thread.join() }

        locker.lock(LOCKED_BY_NAME) {
            logger.debug { LOCK_LOG_MESSAGE }
            assertEquals(LOCKED_BY_NAME, locker.lockedBy())
        }

        assertEquals(locksToAcquire, resultList.size)
        assertEquals(resultList.sorted(), resultList)
    }

    private fun startThreadWithFuture(
        locker: ManipulationLocker,
        lockName: String = "result",
        supplier: () -> Unit,
    ): ThreadAndFuture {
        val hasLock = CompletableFuture<Unit>()
        val t = thread(isDaemon = true) {
            locker.lock(lockName) {
                hasLock.complete(null)
                supplier()
            }
        }
        return ThreadAndFuture(t, hasLock)
    }

    private data class ThreadAndFuture(val thread: Thread, val future: CompletableFuture<Unit>)

    companion object : Logging {
        private const val LOCKED_BY_NAME: String = "blockedResult"
        private const val LOCK_LOG_MESSAGE: String = "blockedResult is in the lock"
    }
}
