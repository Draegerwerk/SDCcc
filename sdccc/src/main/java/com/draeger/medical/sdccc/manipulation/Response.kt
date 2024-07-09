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
