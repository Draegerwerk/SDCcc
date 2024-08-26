/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2024 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.manipulation.precondition

import com.draeger.medical.sdccc.sdcri.testclient.MdibChange
import com.draeger.medical.sdccc.util.TestRunObserver
import com.google.inject.Injector
import org.apache.logging.log4j.kotlin.Logging
import java.util.Objects
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Buffered precondition which can only observe changes.
 */
abstract class BufferedObservingPrecondition(
    injector: Injector
) : Observing {

    // queue is used to decouple the incoming data from the mdib thread doing the change
    // this is going to be a problem when the manipulation is very long-running,
    // as the queue will fill up (quite quickly with waveforms)
    private val updateQueue = LinkedBlockingQueue<MdibChange>()

    // internal visibility to allow unit tests to inspect the thread. do not use for anything else
    internal val processingThread: Thread
    private val processDied = AtomicBoolean(false)
    private val testRunObserver = injector.getInstance(TestRunObserver::class.java)

    init {
        processingThread = thread(start = true, isDaemon = true) {
            while (true) {
                if (updateQueue.size > QUEUE_WARNING_THRESHOLD) {
                    // In case the queue size is above the threshold, log a message
                    // that can be used to debug issues
                    logger.debug {
                        "${this.javaClass.simpleName} queue size above threshold" +
                            " $QUEUE_WARNING_THRESHOLD: ${updateQueue.size}"
                    }
                }
                @Suppress("TooGenericExceptionCaught") // we want to catch all exceptions here
                try {
                    processChange(updateQueue.take())
                } catch (e: Exception) {
                    handleThreadError(e)
                    return@thread
                }
            }
        }
    }

    private fun handleThreadError(e: Exception) {
        testRunObserver.invalidateTestRun(
            "Processing thread for Precondition ${javaClass.simpleName} has" +
                " caught an exception and cannot process incoming change",
            e
        )
    }

    override fun observeChange(incomingChange: MdibChange) {
        // Do not block here, we need to change the context to avoid blocking the mdib thread
        if (!processingThread.isAlive) {
            if (!processDied.getAndSet(true)) {
                // release the remaining elements, no processing will happen
                updateQueue.clear()
            }
            return
        }
        updateQueue.add(incomingChange)
    }

    /**
     * Abstract method to be overridden by the concrete implementation.
     *
     * Receives all changes _without_ blocking the mdib thread.
     *
     * @param change the mdib change to process.
     */
    abstract fun processChange(change: MdibChange)

    override fun verifyPrecondition(injector: Injector) {
        // do nothing, default to observations only
    }

    // equality of preconditions is based on whether they are the same class to avoid
    // duplicate instances
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this.javaClass != other.javaClass) return false
        val that = other as BufferedObservingPrecondition
        return this.javaClass == that.javaClass
    }

    // hashCode of preconditions is based on whether they are the same class to avoid
    // duplicate instances
    override fun hashCode(): Int = Objects.hash(this.javaClass)

    companion object : Logging {

        /**
         * Threshold for the queue size to log a message.
         */
        private const val QUEUE_WARNING_THRESHOLD = 200
    }
}
