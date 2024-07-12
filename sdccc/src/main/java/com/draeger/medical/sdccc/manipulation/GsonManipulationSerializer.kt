package com.draeger.medical.sdccc.manipulation

import com.google.gson.Gson
import com.google.inject.Inject
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

/**
 * Serializer for manipulation responses using Gson.
 *
 * @param gson the Gson instance to use for serialization and deserialization.
 */
class GsonManipulationSerializer @Inject constructor(
    private val gson: Gson
) : ManipulationSerializer {
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
