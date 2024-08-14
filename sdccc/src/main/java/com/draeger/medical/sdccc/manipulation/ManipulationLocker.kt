package com.draeger.medical.sdccc.manipulation

import com.google.inject.Inject
import com.google.inject.Injector
import com.google.inject.Singleton
import org.apache.logging.log4j.kotlin.Logging
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
        try {
            internalLock.lock()
            logger.debug { "Lock was granted for $callerName, executing lambda" }
            return supplier()
        } finally {
            internalLock.unlock()
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
        try {
            internalLock.lock()
            logger.debug { "Lock was granted for $callerName, executing lambda" }
            return supplier(requestedManipulations)
        } finally {
            internalLock.unlock()
        }
    }

    companion object : Logging
}
