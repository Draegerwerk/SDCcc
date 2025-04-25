/*
 * This Source Code Form is subject to the terms of the "SDCcc non-commercial use license".
 *
 * Copyright (C) 2025 Draegerwerk AG & Co. KGaA
 */

package com.draeger.medical.sdccc.manipulation

import java.lang.reflect.Type

/**
 * Represents a serializer for manipulation responses.
 */
interface ManipulationSerializer {

    /**
     * Serializes a manipulation response to a string.
     *
     * @param M the type of the manipulation response.
     * @param manipulationResponse data to serialize.
     */
    fun <M> serialize(manipulationResponse: M): String

    /**
     * Deserializes a manipulation response from a string.
     *
     * @param M the type of the manipulation response.
     * @param manipulationResponse the manipulation response as string.
     * @param type the type of the manipulation response.
     */
    fun <M> deserialize(manipulationResponse: String, type: Type): M
}
