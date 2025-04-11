/*
 * This Source Code Form is subject to the terms of the "SDCcc non-commercial use license".
 *
 * Copyright (C) 2025 Draegerwerk AG & Co. KGaA
 */
package com.draeger.medical.sdccc.manipulation.precondition

import com.draeger.medical.sdccc.messages.MessageStorage
import com.google.inject.Inject
import com.google.inject.Injector
import com.google.inject.Singleton
import org.apache.logging.log4j.kotlin.Logging
import java.lang.reflect.InvocationTargetException

/**
 * Registry which allows executing preconditions during a test run.
 */
@Singleton
class PreconditionRegistry @Inject internal constructor(private val injector: Injector) {

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
     * Runs all registered preconditions.
     *
     * @throws PreconditionException See [Precondition.verifyPrecondition]
     */
    @Throws(PreconditionException::class)
    fun runPreconditions() {
        for (precondition in preconditions) {
            logger.info { "Running precondition ${precondition.javaClass.simpleName}" }
            precondition.verifyPrecondition(injector)
            // flush data after each precondition to ensure that each precondition has most current data
            injector.getInstance(MessageStorage::class.java).flush()
        }
    }

    companion object : Logging {
        private const val BASE_MESSAGE: String = "Error while registering precondition"
    }
}
