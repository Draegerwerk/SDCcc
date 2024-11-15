/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2023-2024 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.messages

import org.hibernate.query.spi.CloseableIterator
import org.hibernate.query.spi.ScrollableResultsImplementor

/**
 * Iterator to be used to create Streams with the ORDERED characteristic from ScrollableResults.
 */
class OrderedStreamIterator<T>(private val results: ScrollableResultsImplementor) : CloseableIterator<T> {

    override fun close() {
        results.close()
    }

    override fun remove() {
        error("not yet implemented!")
    }

    override fun hasNext(): Boolean {
        if (results.isClosed) {
            return false
        }
        return results.next()
    }

    override fun next(): T {
        val element = results.get()
        @Suppress("UNCHECKED_CAST")
        return if (element.size == 1) {
            element[0]
        } else {
            element
        } as T
    }
}
