/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2023 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.manipulation.precondition;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Registry which allows executing preconditions during a test run.
 */
@Singleton
public class PreconditionRegistry {
    private static final Logger LOG = LogManager.getLogger(PreconditionRegistry.class);
    private final Injector injector;
    private final List<Precondition> preconditions;

    @Inject
    PreconditionRegistry(final Injector injector) {
        this.injector = injector;
        this.preconditions = new ArrayList<>();
    }

    /**
     * Registers a simple precondition for running before disconnecting from the DUT.
     *
     * <p>
     * Duplicate preconditions will be ignored.
     *
     * @param precondition precondition to run
     */
    public void registerSimplePrecondition(final Class<? extends SimplePrecondition> precondition) {
        try {
            final var constructor = precondition.getDeclaredConstructor();
            final var instance = constructor.newInstance();
            if (!this.preconditions.contains(instance)) {
                this.preconditions.add(instance);
            }
        } catch (final NoSuchMethodException
                | IllegalAccessException
                | InstantiationException
                | InvocationTargetException e) {
            final String baseMessage = "Error while registering precondition";
            LOG.error(baseMessage, e);
            throw new RuntimeException(baseMessage, e);
        }
    }

    /**
     * Registers a manipulation precondition for running before disconnecting from the DUT.
     *
     * <p>
     * Duplicate preconditions will be ignored.
     *
     * @param precondition precondition to run
     */
    public void registerManipulationPrecondition(final Class<? extends ManipulationPrecondition> precondition) {
        try {
            final var constructor = precondition.getDeclaredConstructor();
            final var instance = constructor.newInstance();
            if (!this.preconditions.contains(instance)) {
                this.preconditions.add(instance);
            }
        } catch (final NoSuchMethodException
                | IllegalAccessException
                | InstantiationException
                | InvocationTargetException e) {
            final String baseMessage = "Error while registering precondition";
            LOG.error(baseMessage, e);
            throw new RuntimeException(baseMessage, e);
        }
    }

    /**
     * Runs all registered preconditions.
     *
     * @throws PreconditionException See {@link Precondition#verifyPrecondition}
     */
    public void runPreconditions() throws PreconditionException {

        for (final var precondition : preconditions) {
            LOG.info("Running precondition {}", precondition.getClass().getSimpleName());
            precondition.verifyPrecondition(injector);
        }
    }
}
