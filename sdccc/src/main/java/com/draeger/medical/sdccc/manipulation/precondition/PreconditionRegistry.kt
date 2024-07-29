/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2023-2024 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */
package com.draeger.medical.sdccc.manipulation.precondition

import com.google.inject.Inject
import com.google.inject.Injector
import com.google.inject.Singleton
import org.apache.logging.log4j.kotlin.Logging
import java.lang.reflect.InvocationTargetException
import kotlin.reflect.KClass

/**
 * Registry which allows executing preconditions during a test run.
 */
@Singleton
class PreconditionRegistry @Inject internal constructor(private val injector: Injector) {

    /**
     * Returns all registered preconditions which are of type [Observing].
     */
    val observingPreconditions: Collection<Observing>
        get() = preconditions.filterIsInstance<Observing>()

    private val preconditions: MutableList<Precondition> = ArrayList()

    @Suppress("TooGenericExceptionThrown") // this is an error during startup and cannot be fixed
    private fun handleRegisteringError(error: Throwable, text: String): Nothing {
        logger.error(error) { text }
        throw RuntimeException(text, error)
    }

    private fun registerPreconditionInternal(precondition: Class<out Precondition>) {
        try {
            val constructor = precondition.getDeclaredConstructor()
            val instance = constructor.newInstance()
            if (!preconditions.contains(instance)) {
                preconditions.add(instance)
            }
        } catch (e: NoSuchMethodException) {
            handleRegisteringError(e, BASE_MESSAGE)
        } catch (e: IllegalAccessException) {
            handleRegisteringError(e, BASE_MESSAGE)
        } catch (e: InstantiationException) {
            handleRegisteringError(e, BASE_MESSAGE)
        } catch (e: InvocationTargetException) {
            handleRegisteringError(e, BASE_MESSAGE)
        }
    }

    /**
     * Registers a simple precondition for running before disconnecting from the DUT.
     *
     * Duplicate preconditions will be ignored.
     *
     * @param precondition precondition to run
     */
    fun registerSimplePrecondition(precondition: Class<out SimplePrecondition?>) {
        registerPreconditionInternal(precondition)
    }

    /**
     * Registers a manipulation precondition for running before disconnecting from the DUT.
     *
     * Duplicate preconditions will be ignored.
     *
     * @param precondition precondition to run
     */
    fun registerManipulationPrecondition(precondition: Class<out ManipulationPrecondition?>) {
        registerPreconditionInternal(precondition)
    }

    /**
     * Registers a manipulation precondition for running and observing before disconnecting from the DUT.
     *
     * Duplicate preconditions will be ignored.
     *
     * @param precondition precondition to run
     */
    fun registerObservingPrecondition(precondition: KClass<out ObservingPreconditionFactory<*>>) {
        val factoryInstance = checkNotNull(precondition.objectInstance) {
            "Factory class ${precondition.simpleName} does not provide an object instance. Ensure it is a Companion."
        }
        val instance = factoryInstance.create(injector)
        if (!preconditions.contains(instance)) {
            preconditions.add(instance)
        }
    }

    /**
     * Runs all registered preconditions.
     *
     * @throws PreconditionException See [Precondition.verifyPrecondition]
     */
    @Throws(PreconditionException::class)
    fun runPreconditions() {
        for (precondition in preconditions) {
            logger.info { "Running precondition ${precondition.javaClass.simpleName}" }
            precondition.verifyPrecondition(injector)
        }
    }

    companion object : Logging {
        private const val BASE_MESSAGE: String = "Error while registering precondition"
    }
}
