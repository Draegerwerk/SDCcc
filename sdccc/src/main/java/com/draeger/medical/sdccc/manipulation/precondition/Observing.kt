/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2024 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.manipulation.precondition

import com.draeger.medical.sdccc.sdcri.testclient.MdibChange

/**
 * Interface for preconditions that can observe changes to the device and trigger upon them.
 */
interface Observing : Precondition {

    /**
     * Receives an mdib change from the device to process.
     *
     * @param incomingChange to the device mdib.
     */
    fun observeChange(incomingChange: MdibChange)
}
