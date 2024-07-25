/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2023-2024 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */
package com.draeger.medical.sdccc.manipulation.precondition

import com.draeger.medical.sdccc.manipulation.precondition.PreconditionException
import com.google.inject.Inject
import com.google.inject.Injector
import com.google.inject.Singleton
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.lang.reflect.InvocationTargetException
import java.util.stream.Collectors
import kotlin.reflect.KClass

/**
 * Registry which allows executing preconditions during a test run.
 */
@Singleton
class PreconditionRegistry @Inject internal constructor(private val injector: Injector) {
    private val preconditions: MutableList<Precondition?> = ArrayList()

    private fun registerPreconditionInternal(precondition: Class<out Precondition?>) {
        try {
            val constructor = precondition.getDeclaredConstructor()
            val instance = constructor.newInstance()
            if (!preconditions.contains(instance)) {
                preconditions.add(instance)
            }
        } catch (e: NoSuchMethodException) {
            val baseMessage = "Error while registering precondition"
            LOG.error(baseMessage, e)
            throw RuntimeException(baseMessage, e)
        } catch (e: IllegalAccessException) {
            val baseMessage = "Error while registering precondition"
            LOG.error(baseMessage, e)
            throw RuntimeException(baseMessage, e)
        } catch (e: InstantiationException) {
            val baseMessage = "Error while registering precondition"
            LOG.error(baseMessage, e)
            throw RuntimeException(baseMessage, e)
        } catch (e: InvocationTargetException) {
            val baseMessage = "Error while registering precondition"
            LOG.error(baseMessage, e)
            throw RuntimeException(baseMessage, e)
        }
    }

    /**
     * Registers a simple precondition for running before disconnecting from the DUT.
     *
     *
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
     *
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
     *
     *
     * Duplicate preconditions will be ignored.
     *
     * @param precondition precondition to run
     */
    fun registerObservingPrecondition(precondition: KClass<out ObservingPreconditionFactory<*>>) {
        try {
            val factoryInstance = precondition.objectInstance!!
            val instance = factoryInstance.create(injector)
            if (!preconditions.contains(instance)) {
                preconditions.add(instance)
            }
        } catch (e: NoSuchMethodException) {
            val baseMessage = "Error while registering precondition"
            LOG.error(baseMessage, e)
            throw RuntimeException(baseMessage, e)
        } catch (e: IllegalAccessException) {
            val baseMessage = "Error while registering precondition"
            LOG.error(baseMessage, e)
            throw RuntimeException(baseMessage, e)
        } catch (e: InstantiationException) {
            val baseMessage = "Error while registering precondition"
            LOG.error(baseMessage, e)
            throw RuntimeException(baseMessage, e)
        } catch (e: InvocationTargetException) {
            val baseMessage = "Error while registering precondition"
            LOG.error(baseMessage, e)
            throw RuntimeException(baseMessage, e)
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
            LOG.info("Running precondition {}", precondition!!.javaClass.simpleName)
            precondition.verifyPrecondition(injector)
        }
    }

    val observingPreconditions: Collection<Observing?>
        get() = preconditions.stream()
            .filter { it: Precondition? -> it is Observing }
            .map { it: Precondition? -> it as Observing? }
            .collect(Collectors.toList())

    companion object {
        private val LOG: Logger = LogManager.getLogger(
            PreconditionRegistry::class.java
        )
    }
}
