/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2024 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.manipulation

import com.google.inject.Inject
import com.google.inject.Injector
import com.google.inject.Singleton
import org.apache.logging.log4j.kotlin.Logging
import java.time.Duration
import java.time.Instant
import java.util.concurrent.locks.ReentrantLock

/**
 * Locker for manipulations. This locker is used to prevent multiple
 * manipulations from running at the same time. This is necessary
 * as observing manipulations can trigger manipulations at arbitrary times.
 */
@Singleton
class ManipulationLocker @Inject constructor(
    private val injector: Injector
) {
    private val internalLock = ReentrantLock(true)

    /**
     * Locks the manipulation and executes the lambda. Returns the result of the lambda.
     */
    fun <T> lock(
        callerName: String,
        supplier: () -> T
    ): T {
        val start = Instant.now()
        try {
            internalLock.lock()
            logger.debug { "Lock was granted for $callerName, executing lambda" }
            return supplier()
        } finally {
            val finish = Instant.now()
            val elapsed = Duration.between(start, finish)
            logger.debug { "Releasing lock for $callerName after $elapsed" }
            internalLock.unlock()
        }
    }

    /**
     * Returns whether the manipulation is currently locked.
     *
     * This is mostly useful for unit testing.
     */
    fun isLocked() = internalLock.isLocked

    // TODO: Do we want his? Implication: internalLock and injector must be internal
    /**
     * Locks the manipulation and executes the lambda. Returns the result of the lambda.
     */
    fun <M : Manipulations, T> lockManipulations(
        callerName: String,
        manipulationsClass: Class<M>,
        supplier: (M) -> T
    ): T {
        val requestedManipulations = injector.getInstance(manipulationsClass)
        try {
            internalLock.lock()
            logger.debug { "Lock was granted for $callerName, executing lambda" }
            return supplier(requestedManipulations)
        } finally {
            logger.debug { "Lock was released for $callerName" }
            internalLock.unlock()
        }
    }

    companion object : Logging
}
