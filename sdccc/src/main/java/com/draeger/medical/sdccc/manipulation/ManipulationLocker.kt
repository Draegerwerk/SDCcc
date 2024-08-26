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
import kotlin.properties.Delegates

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
    private val subscribers = mutableListOf<(String?) -> Unit>()
    private var lockedBy: String? by Delegates.observable(null) { _, _, newValue ->
        subscribers.toList()
            .forEach {
                it(newValue)
            }
    }

    /**
     * Locks the manipulation and executes the lambda. Returns the result of the lambda.
     */
    fun <T> lock(
        callerName: String,
        supplier: () -> T
    ): T {
        // use the reified call without passing in the argument
        return this.lockManipulations(callerName, Manipulations::class.java) {
            supplier()
        }
    }

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
        val start = Instant.now()
        try {
            internalLock.lock()
            lockedBy = callerName
            logger.debug { "Lock was granted for $callerName, executing lambda" }
            return supplier(requestedManipulations)
        } finally {
            val finish = Instant.now()
            val elapsed = Duration.between(start, finish)
            logger.debug { "Releasing lock for $callerName after $elapsed" }
            lockedBy = null
            internalLock.unlock()
        }
    }

    /**
     * Returns whether the manipulation is currently locked.
     *
     * This is mostly useful for unit testing.
     */
    fun isLocked() = internalLock.isLocked

    /**
     * Returns the self-declared name of the locking code.
     *
     * This is mostly useful for unit testing.
     */
    fun lockedBy() = lockedBy

    // for testing purposes, this implementation allows observing the names of the lockers
    internal fun subscribe(subscriber: (String?) -> Unit) {
        logger.debug { "Subscribing to lock events" }
        subscribers.add(subscriber)
    }

    internal fun unsubscribe(subscriber: (String?) -> Unit) {
        logger.debug { "Unsubscribing from lock events" }
        subscribers.remove(subscriber)
    }

    companion object : Logging
}
