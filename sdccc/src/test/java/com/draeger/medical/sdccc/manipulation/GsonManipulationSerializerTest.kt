/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2024 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.manipulation

import com.draeger.medical.t2iapi.ResponseTypes
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonIOException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.assertEquals

/**
 * Test class for de-/serialization testing.
 * @property value to test strings
 * @property number to test numbers
 * @property list to test lists
 */
data class ExampleObject(val value: String, val number: Int, val list: List<String>)

/**
 * Test class for de-/serialization testing.
 * @param T type of something
 * @property something to test objects
 */
data class GenericObject<T>(val something: T)

/**
 * Unit tests for [GsonManipulationSerializer].
 */
class GsonManipulationSerializerTest {

    private val gsonManipulationSerializer = GsonManipulationSerializer(GsonBuilder())

    @Test
    fun roundTrip() {
        val expectedString = "abc"
        run {
            assertEquals(
                expectedString,
                gsonManipulationSerializer.deserialize(
                    gsonManipulationSerializer.serialize(expectedString),
                    String::class.java
                )
            )
        }

        val expectedObject = ExampleObject(expectedString, 123, listOf("a", "b", "c"))
        assertEquals(
            expectedObject,
            gsonManipulationSerializer.deserialize(
                gsonManipulationSerializer.serialize(expectedObject),
                ExampleObject::class.java
            )
        )

        val expectedGenericObject = GenericObject(expectedObject)
        run {
            val result = gsonManipulationSerializer.deserialize<GenericObject<ExampleObject>>(
                gsonManipulationSerializer.serialize(expectedGenericObject),
                getParameterizedGenericType(GenericObject::class.java, ExampleObject::class.java)
            )
            assertEquals(
                expectedGenericObject,
                result
            )
        }

        val manipulationResponse = ManipulationResponse(ResponseTypes.Result.RESULT_SUCCESS, expectedObject)
        run {
            val result = ManipulationResponse.deserialize<ExampleObject>(
                gsonManipulationSerializer.serialize(manipulationResponse),
                gsonManipulationSerializer
            )
            assertEquals(
                manipulationResponse,
                result
            )
        }

        val randomLocalDate = LocalDate.of(2021, Month.FEBRUARY, 28)
        val randomLocalTime = LocalTime.of(12, 1, 0, 0)
        val myLocalDateTime = LocalDateTime.of(randomLocalDate, randomLocalTime)
        val randomOffsetDateTime = OffsetDateTime.of(myLocalDateTime, ZoneOffset.UTC)
        val anotherManipulationResponse = ManipulationResponse(
            ResponseTypes.Result.RESULT_SUCCESS,
            randomOffsetDateTime
        )

        run {
            val resultSameType = ManipulationResponse.deserialize<OffsetDateTime>(
                gsonManipulationSerializer.serialize(anotherManipulationResponse),
                gsonManipulationSerializer
            )
            assertEquals(
                anotherManipulationResponse.result,
                resultSameType.result
            )
        }

        val gsonWithoutTimeClassAdapters = Gson()
        assertThrows<JsonIOException> { gsonWithoutTimeClassAdapters.toJson(randomLocalDate) }
        assertThrows<JsonIOException> { gsonWithoutTimeClassAdapters.toJson(randomLocalTime) }
        assertThrows<JsonIOException> { gsonWithoutTimeClassAdapters.toJson(myLocalDateTime) }
        assertThrows<JsonIOException> { gsonWithoutTimeClassAdapters.toJson(randomOffsetDateTime) }
    }
}
