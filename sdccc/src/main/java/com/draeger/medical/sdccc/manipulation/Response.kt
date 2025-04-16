/*
 * This Source Code Form is subject to the terms of the "SDCcc non-commercial use license".
 *
 * Copyright (C) 2025 Draegerwerk AG & Co. KGaA
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
