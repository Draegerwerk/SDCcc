package com.draeger.medical.sdccc.manipulation

import com.draeger.medical.t2iapi.ResponseTypes
import com.google.gson.Gson
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

data class ExampleObject(val value: String, val number: Int, val list: List<String>)

data class GenericObject<T>(val something: T)

class GsonManipulationSerializerTest {

    private val gsonManipulationSerializer = GsonManipulationSerializer(Gson())

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
    }
}
