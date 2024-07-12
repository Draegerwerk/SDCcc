package com.draeger.medical.sdccc.manipulation

import java.lang.reflect.Type

/**
 * Represents a serializer for manipulation responses.
 */
interface ManipulationSerializer {

    /**
     * Serializes a manipulation response to a string.
     *
     * @param manipulationResponse data to serialize.
     */
    fun <M> serialize(manipulationResponse: M): String


    /**
     * Deserializes a manipulation response from a string.
     *
     * @param manipulationResponse the manipulation response as string.
     * @param type the type of the manipulation response.
     * @param M the type of the manipulation response.
     */
    fun <M> deserialize(manipulationResponse: String, type: Type): M
}