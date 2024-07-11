package com.draeger.medical.sdccc.manipulation.precondition

import com.google.inject.Injector
import java.util.*
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread

/**
 * Synchronized precondition which can observe changes.
 */
abstract class SynchronizedObservingPrecondition(
    private val manipulationCall: ManipulationFunction<Injector>
): Observing {
    // used to synchronize calls to this precondition, it can either run directly or through a trigger,
    // but never both in parallel. It is assumed that running them concurrently is not desired, as the known
    // use cases are happier with preconditions not becoming their own side effects.
    private val lock = object {}
    // queue is used to decouple the incoming data from the mdib thread doing the change
    // this is going to be a problem when the manipulation is very long-running,
    // as the queue will fill up (quite quickly with waveforms)
    private val updateQueue = LinkedBlockingQueue<PreconditionChange>()

    private val updateThread = thread(start = true, isDaemon = true) {
        while (true) {
            val change = updateQueue.take()

            synchronized(lock) {
                change(change)
            }
        }
    }

    override fun observeChange(incomingChange: PreconditionChange) {
        // Do not block here, we need to change the context to avoid blocking the mdib thread
        updateQueue.add(incomingChange)
    }

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

    override fun hashCode(): Int {
        return Objects.hash(manipulationCall)
    }
}