/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2024 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.manipulation

import com.draeger.medical.t2iapi.ResponseTypes

/**
 * Basic functionality necessary for every manipulation response.
 */
interface Response {
    /**
     * Result of the manipulation.
     */
    val result: ResponseTypes.Result
}
