/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2024 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.manipulation.precondition

import com.draeger.medical.sdccc.sdcri.testclient.MdibChange
import com.google.inject.Injector
import org.apache.logging.log4j.kotlin.Logging
import java.util.Objects
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread

/**
 * Buffered precondition which can only observe changes.
 */
abstract class BufferedObservingPrecondition(
    private val injector: Injector
) : Observing {

    // queue is used to decouple the incoming data from the mdib thread doing the change
    // this is going to be a problem when the manipulation is very long-running,
    // as the queue will fill up (quite quickly with waveforms)
    private val updateQueue = LinkedBlockingQueue<MdibChange>()

    init {
        thread(start = true, isDaemon = true) {
            while (true) {
                if (updateQueue.size > QUEUE_WARNING_THRESHOLD) {
                    // In case the queue size is above the threshold, log a message
                    // that can be used to debug issues
                    logger.debug {
                        "${this.javaClass.simpleName} queue size above threshold" +
                            " $QUEUE_WARNING_THRESHOLD: ${updateQueue.size}"
                    }
                }
                val change = updateQueue.take()
                change(injector, change)
            }
        }
    }

    override fun observeChange(incomingChange: MdibChange) {
        // Do not block here, we need to change the context to avoid blocking the mdib thread
        updateQueue.add(incomingChange)
    }

    /**
     * Abstract method to be overridden by the concrete implementation.
     *
     * Receives all changes _without_ blocking the mdib thread.
     *
     * @param injector
     * @param change the change to process.
     */
    abstract fun change(injector: Injector, change: MdibChange)

    override fun verifyPrecondition(injector: Injector) {
        // do nothing
    }

    // equality of preconditions is based on whether they are the same class to avoid
    // duplicate instances
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as BufferedObservingPrecondition
        return this.javaClass == that.javaClass
    }

    // hasCode of preconditions is based on whether they are the same class to avoid
    // duplicate instances
    override fun hashCode(): Int = Objects.hash(this.javaClass)

    companion object : Logging {

        /**
         * Threshold for the queue size to log a message.
         */
        private const val QUEUE_WARNING_THRESHOLD = 200
    }
}
