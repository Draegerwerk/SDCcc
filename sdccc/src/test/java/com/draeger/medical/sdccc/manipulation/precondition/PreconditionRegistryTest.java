/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2023 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.manipulation.precondition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.draeger.medical.sdccc.messages.MessageStorage;
import com.google.inject.Injector;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import kotlin.reflect.KClass;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the precondition registry.
 */
public class PreconditionRegistryTest {

    private PreconditionRegistry registry;

    @BeforeEach
    void setUp() {
        final var mockInjector = mock(Injector.class);
        final var messageStorageMock = mock(MessageStorage.class);
        when(mockInjector.getInstance(MessageStorage.class)).thenReturn(messageStorageMock);

        registry = new PreconditionRegistry(mockInjector);

        PreconditionUtil.MockPrecondition.reset();
        PreconditionUtil.MockManipulation.reset();
    }

    /**
     * Tests registering a precondition interaction and whether it's prompted for.
     *
     * @throws Exception on any exception
     */
    @Test
    @DisplayName("Tests registering a complex interaction and whether it's prompted for")
    public void testPreconditionInteractionRegistrationAndPrompt() throws Exception {
        final var preconditionWasCalled = new AtomicBoolean(false);
        PreconditionUtil.MockPrecondition.setIsPreconditionMet(injector -> {
            preconditionWasCalled.set(true);
            // precondition was not met
            return false;
        });

        final var manipulationWasCalled = new AtomicBoolean(false);
        PreconditionUtil.MockPrecondition.setManipulationCall(injector -> {
            manipulationWasCalled.set(true);
            return true;
        });

        registry.registerSimplePrecondition(PreconditionUtil.MockPrecondition.class);
        registry.runPreconditions();

        assertTrue(preconditionWasCalled.get());
        assertTrue(manipulationWasCalled.get());
    }

    /**
     * Tests whether registering the same precondition interaction thrice only prompts for it once.
     *
     * @throws Exception on any exception
     */
    @Test
    @DisplayName("Tests whether registering the same complex interaction thrice only prompts for it once")
    public void testPreconditionInteractionRegisteredOnlyOnce() throws Exception {
        final var preconditionWasCalled = new AtomicInteger(0);
        PreconditionUtil.MockPrecondition.setIsPreconditionMet(injector -> {
            preconditionWasCalled.incrementAndGet();
            // precondition was not met
            return true;
        });

        registry.registerSimplePrecondition(PreconditionUtil.MockPrecondition.class);
        registry.registerSimplePrecondition(PreconditionUtil.MockPrecondition.class);
        registry.registerSimplePrecondition(PreconditionUtil.MockPrecondition.class);
        registry.runPreconditions();

        assertEquals(1, preconditionWasCalled.get());
    }

    /**
     * Tests whether an exception during registration causes a RuntimeException and stops the test run.
     */
    @Test
    @DisplayName("Tests whether an exception during registration causes a RuntimeException and stops the test run")
    public void testPreconditionInteractionRegistrationException() {
        final var mockInteractionWasCalled = new AtomicInteger(0);
        PreconditionUtil.MockPrecondition.setAfterConstructorCall(() -> {
            mockInteractionWasCalled.incrementAndGet();
            throw new PreconditionException("Intentional exception", new Exception("Intentional cause"));
        });

        assertThrows(
                RuntimeException.class,
                () -> registry.registerSimplePrecondition(PreconditionUtil.MockPrecondition.class));
        assertEquals(1, mockInteractionWasCalled.get());
    }

    /**
     * Tests registering a manipulation and whether it's prompted for.
     *
     * @throws Exception on any exception
     */
    @Test
    @DisplayName("Tests registering a manipulation and whether it's prompted for")
    public void testManipulationInteractionRegistrationAndPrompt() throws Exception {
        final var manipulationWasCalled = new AtomicBoolean(false);
        PreconditionUtil.MockManipulation.setManipulationCall(injector -> {
            manipulationWasCalled.set(true);
            return true;
        });

        registry.registerManipulationPrecondition(PreconditionUtil.MockManipulation.class);
        registry.runPreconditions();

        assertTrue(manipulationWasCalled.get());
    }

    /**
     * Tests whether registering the same complex interaction thrice only prompts for it once.
     *
     * @throws Exception on any exception
     */
    @Test
    @DisplayName("Tests whether registering the same manipulation thrice only prompts for it once")
    public void testManipulationInteractionRegisteredOnlyOnce() throws Exception {
        final var manipulationWasCalled = new AtomicInteger(0);
        PreconditionUtil.MockManipulation.setManipulationCall(injector -> {
            manipulationWasCalled.incrementAndGet();
            return true;
        });

        registry.registerManipulationPrecondition(PreconditionUtil.MockManipulation.class);
        registry.registerManipulationPrecondition(PreconditionUtil.MockManipulation.class);
        registry.registerManipulationPrecondition(PreconditionUtil.MockManipulation.class);
        registry.runPreconditions();

        assertEquals(1, manipulationWasCalled.get());
    }

    /**
     * Tests whether an exception during registration causes a RuntimeException and stops the test run.
     */
    @Test
    @DisplayName("Tests whether an exception during registration causes a RuntimeException and stops the test run")
    public void testManipulationInteractionRegistrationException() {
        final var mockInteractionWasCalled = new AtomicInteger(0);
        PreconditionUtil.MockManipulation.setAfterConstructorCall(() -> {
            mockInteractionWasCalled.incrementAndGet();
            throw new PreconditionException("Intentional exception", new Exception("Intentional cause"));
        });

        assertThrows(
                RuntimeException.class,
                () -> registry.registerManipulationPrecondition(PreconditionUtil.MockManipulation.class));
        assertEquals(1, mockInteractionWasCalled.get());
    }
}
