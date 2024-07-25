/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2024 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.manipulation.precondition

import com.google.inject.Injector
import java.util.Objects
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread

/**
 * Synchronized precondition which can observe changes.
 *
 * This class is synchronized, meaning calls to [verifyPrecondition] and [change] are serialized
 * and cannot occur in parallel. This has certain caveats that must be considered when using this class.
 *
 * 1. **Avoid long-running manipulations**. Changes are buffered and can consume a lot of memory, as every single
 *    change is stored. Memory is finite and can be exhausted. Consider introducing timeouts or other mechanisms
 *    to prevent users from blocking indefinitely.
 * 2. After executing the precondition, changes resume processing. This can mean that you have outdated changes
 *    that do not represent the current configuration of the device under test.
 *
 * In case synchronization is not required or an issue, implementing an [Observing]
 * precondition directly is possible.
 */
abstract class SynchronizedObservingPrecondition(
    private val manipulationCall: ManipulationFunction<Injector>
) : Observing {
    // used to synchronize calls to this precondition, it can either run directly or through a trigger,
    // but never both in parallel. It is assumed that running them concurrently is not desired, as the known
    // use cases are happier with preconditions not becoming their own side effects.
    private val lock = object {}

    // queue is used to decouple the incoming data from the mdib thread doing the change
    // this is going to be a problem when the manipulation is very long-running,
    // as the queue will fill up (quite quickly with waveforms)
    private val updateQueue = LinkedBlockingQueue<PreconditionChange>()

    init {
        thread(start = true, isDaemon = true) {
            while (true) {
                val change = updateQueue.take()

                synchronized(lock) {
                    change(change)
                }
            }
        }
    }

    override fun observeChange(incomingChange: PreconditionChange) {
        // Do not block here, we need to change the context to avoid blocking the mdib thread
        updateQueue.add(incomingChange)
    }

    /**
     * Abstract method to be overridden by the concrete implementation.
     *
     * Receives all changes _without_ blocking the mdib thread.
     *
     * @param change the change to process.
     */
    abstract fun change(change: PreconditionChange)

    override fun verifyPrecondition(injector: Injector) {
        synchronized(lock) {
            manipulationCall.apply(injector)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as SynchronizedObservingPrecondition
        return manipulationCall == that.manipulationCall
    }

    override fun hashCode(): Int = Objects.hash(manipulationCall)
}
