/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2024 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.manipulation

import com.fatboyindustrial.gsonjavatime.LocalDateConverter
import com.fatboyindustrial.gsonjavatime.LocalDateTimeConverter
import com.fatboyindustrial.gsonjavatime.OffsetDateTimeConverter
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.inject.Inject
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime

/**
 * Serializer for manipulation responses using Gson.
 *
 * @param gsonBuilder the GsonBuilder instance with several registered TypeAdapters to create a Gson instance that is
 * used for serialization and deserialization.
 */
class GsonManipulationSerializer @Inject constructor(
    private val gsonBuilder: GsonBuilder
) : ManipulationSerializer {

    private val gson: Gson = gsonBuilder
        .registerTypeAdapter(LocalDate::class.java, LocalDateConverter())
        .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeConverter())
        .registerTypeAdapter(OffsetDateTime::class.java, OffsetDateTimeConverter())
        .create()

    override fun <M> serialize(manipulationResponse: M): String = gson.toJson(manipulationResponse)

    override fun <M> deserialize(manipulationResponse: String, type: Type): M = gson.fromJson(
        manipulationResponse,
        type
    )
}

/**
 * Helper function to create a parameterized generic [Type].
 *
 * @param R the type of the [rawClass].
 * @param P the type of the [parameter].
 * @param rawClass holding the generic.
 * @param parameter type of the generic parameter of [rawClass].
 */
fun <R, P> getParameterizedGenericType(rawClass: Class<in R>, parameter: Class<in P>): Type {
    return object : ParameterizedType {
        override fun getActualTypeArguments(): Array<Type> = arrayOf(parameter)

        override fun getRawType(): Type = rawClass

        override fun getOwnerType(): Type? = null
    }
}
