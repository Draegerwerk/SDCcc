/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2025 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.util

import org.apache.logging.log4j.kotlin.Logging
import java.util.concurrent.BrokenBarrierException
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * A utility class that uses a CyclicBarrier to synchronize threads for reconnect attempts.
 *
 * @param parties the number of parties required to trip the barrier.
 */
class ReconnectWaitBarrier(parties: Int = 2) {

    private val barrier: CyclicBarrier = CyclicBarrier(parties)
    private var wasCalledSuccessful: Boolean = false

    /**
     * Waits for the provider to be ready, with a specified timeout.
     *
     * @param timeout the maximum time to wait in seconds.
     * @throws InterruptedException if the current thread is interrupted while waiting.
     * @throws BrokenBarrierException if the barrier is broken while waiting.
     */
    @Suppress("SwallowedException") // The TimeoutException is expected, since the barrier is used for waiting
    // and the exception is thrown when the barrier is not tripped in time.
    @Throws(InterruptedException::class, BrokenBarrierException::class)
    fun waitForProvider(timeout: Long) {
        logger.info { "Waiting at most $timeout seconds for the provider to be ready" }
        try {
            barrier.await(timeout, TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            logger.info { "Timeout reached while waiting for the provider to be ready" }
        }
    }

    /**
     * Notifies that the provider is ready.
     *
     * @return true if the barrier was successfully tripped, false otherwise.
     */
    fun notifyProviderIsReady(): Boolean {
        if (!wasCalledSuccessful) {
            val arrivalIndex = try {
                barrier.await()
            } catch (e: InterruptedException) {
                logger.debug { "Interrupted while waiting for barrier $e" }
                -1
            } catch (ex: BrokenBarrierException) {
                logger.debug { "Broken barrier exception $ex " }
                -1
            }
            wasCalledSuccessful = arrivalIndex != -1
            return wasCalledSuccessful
        } else {
            return false
        }
    }

    /**
     * Resets the barrier and the state of the class.
     */
    fun reset() {
        wasCalledSuccessful = false
        barrier.reset()
    }

    companion object : Logging
}
