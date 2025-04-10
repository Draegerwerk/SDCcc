/*
 * This Source Code Form is subject to the terms of the "SDCcc non-commercial use license".
 *
 * Copyright (C) 2025 Draegerwerk AG & Co. KGaA
 */

package com.draeger.medical.sdccc.manipulation

import com.draeger.medical.t2iapi.BasicResponses
import com.draeger.medical.t2iapi.ResponseTypes

/**
 * Container for Manipulation responses with only a result.
 */
data class ResultResponse(
    /**
     * Result of the manipulation.
     */
    override val result: ResponseTypes.Result,
) : Response {
    companion object {
        /**
         * Creates a ResultResponse from a ResponseTypes.Result.
         */
        @JvmStatic
        fun from(status: ResponseTypes.Result) = ResultResponse(status)

        /**
         * Creates a ResultResponse from a BasicResponses.BasicResponse.
         */
        @JvmStatic
        fun from(basicResponses: BasicResponses.BasicResponse): ResultResponse = from(basicResponses.result)

        /**
         * Creates a ResultResponse with [ResponseTypes.Result.RESULT_SUCCESS].
         */
        @JvmStatic
        fun success(): ResultResponse = from(ResponseTypes.Result.RESULT_SUCCESS)

        /**
         * Creates a ResultResponse with [ResponseTypes.Result.RESULT_FAIL].
         */
        @JvmStatic
        fun fail(): ResultResponse = from(ResponseTypes.Result.RESULT_FAIL)

        /**
         * Creates a ResultResponse with [ResponseTypes.Result.RESULT_NOT_SUPPORTED].
         */
        @JvmStatic
        fun notSupported(): ResultResponse = from(ResponseTypes.Result.RESULT_NOT_SUPPORTED)

        /**
         * Creates a ResultResponse with [ResponseTypes.Result.RESULT_NOT_IMPLEMENTED].
         */
        @JvmStatic
        fun notImplemented(): ResultResponse = from(ResponseTypes.Result.RESULT_NOT_IMPLEMENTED)
    }
}

/**
 * ManipulationResponse containing the result and the response.
 */
data class ManipulationResponse<T>(
    /**
     * Result of the manipulation.
     */
    override val result: ResponseTypes.Result,
    /**
     * Response of the manipulation.
     */
    val response: T?
) : Response {
    companion object {
        /**
         * Creates a ManipulationResponse from a ResponseTypes.Result and response content.
         */
        @JvmStatic
        fun <T> from(status: ResponseTypes.Result, response: T?): ManipulationResponse<T> =
            ManipulationResponse(status, response)

        /**
         * Creates a ManipulationResponse from a BasicResponses.BasicResponse and response content.
         */
        @JvmStatic
        fun <T> from(status: BasicResponses.BasicResponse, response: T?): ManipulationResponse<T> =
            from(status.result, response)

        /**
         * Creates a ManipulationResponse with [ResponseTypes.Result.RESULT_SUCCESS] and response content.
         */
        @JvmStatic
        fun <T> success(response: T?): ManipulationResponse<T> = from(ResponseTypes.Result.RESULT_SUCCESS, response)

        /**
         * Creates a ManipulationResponse with [ResponseTypes.Result.RESULT_FAIL] and response content.
         */
        @JvmStatic
        fun <T> fail(response: T?): ManipulationResponse<T> = from(ResponseTypes.Result.RESULT_FAIL, response)

        /**
         * Deserializes a [ManipulationResponse] from a string.
         *
         * @param T the type of the manipulation response.
         * @param data to deserialize from
         * @param deserializer to deserialize with
         */
        inline fun <reified T> deserialize(
            data: String,
            deserializer: ManipulationSerializer
        ): ManipulationResponse<T> {
            return deserializer.deserialize(
                data,
                getParameterizedGenericType(ManipulationResponse::class.java, T::class.java)
            )
        }
    }
}
