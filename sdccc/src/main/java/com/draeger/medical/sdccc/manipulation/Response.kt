package com.draeger.medical.sdccc.manipulation

import com.draeger.medical.t2iapi.ResponseTypes
import java.io.Serializable

/**
 * Basic functionality necessary for every manipulation response.
 */
interface Response : Serializable {
    /**
     * Result of the manipulation.
     */
    val result: ResponseTypes.Result
}
