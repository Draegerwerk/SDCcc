/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2023 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.manipulation.precondition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.draeger.medical.sdccc.manipulation.ManipulationLocker;
import com.google.inject.Injector;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import kotlin.reflect.KClass;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Unit tests for the precondition registry.
 */
public class PreconditionRegistryTest {

    private Injector mockInjector;
    private ManipulationLocker locker;

    @BeforeEach
    void setUp() {
        PreconditionUtil.MockPrecondition.reset();
        PreconditionUtil.MockManipulation.reset();
        mockInjector = mock(Injector.class);
        locker = new ManipulationLocker(mockInjector);
        when(mockInjector.getInstance(ManipulationLocker.class)).thenReturn(locker);
    }

    /**
     * Tests registering a precondition interaction and whether it's prompted for.
     *
     * @throws Exception on any exception
     */
    @Test
    @DisplayName("Tests registering a complex interaction and whether it's prompted for")
    public void testPreconditionInteractionRegistrationAndPrompt() throws Exception {
        final var registry = new PreconditionRegistry(mockInjector);

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
        final var registry = new PreconditionRegistry(mockInjector);

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
        final var registry = new PreconditionRegistry(mockInjector);

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
        final var registry = new PreconditionRegistry(mockInjector);

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
        final var registry = new PreconditionRegistry(mockInjector);

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
        final var registry = new PreconditionRegistry(mockInjector);

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

    @Test
    void testRegisteringObservingPreconditions() throws Exception {
        final var registry = new PreconditionRegistry(mockInjector);

        final KClass<? extends ObservingPreconditionFactory<?>> mockPreconditionFactory = mock(KClass.class);

        final var mockFactory = mock(ObservingPreconditionFactory.class);
        doReturn(mockFactory).when(mockPreconditionFactory).getObjectInstance();

        final var mockPrecondition = mock(Observing.class);
        doReturn(mockPrecondition).when(mockFactory).create(any());

        // call register twice, expect only one to be registered
        registry.registerObservingPrecondition(mockPreconditionFactory);
        registry.registerObservingPrecondition(mockPreconditionFactory);

        final var observing = registry.getObservingPreconditions();

        assertEquals(1, observing.size());
        assertEquals(mockPrecondition, observing.stream().findFirst().orElseThrow());

        registry.runPreconditions();

        verify(mockPrecondition, times(1)).verifyPrecondition(any());
    }

    @Test
    void testRegisteringObservingPreconditionsFailsWhenNoObjectInstanceAvailable() {
        final var registry = new PreconditionRegistry(mockInjector);

        final KClass<? extends ObservingPreconditionFactory<?>> mockPreconditionFactory = mock(KClass.class);

        final var mockFactory = mock(ObservingPreconditionFactory.class);

        final var mockPrecondition = mock(Observing.class);
        doReturn(mockPrecondition).when(mockFactory).create(any());

        assertThrows(
                IllegalStateException.class, () -> registry.registerObservingPrecondition(mockPreconditionFactory));
    }

    /**
     * Verifies whether Preconditions that do not implement {@link LockingPrecondition}'s are executed in the lock
     * by the registry.
     */
    @Test
    @Timeout(value = 5)
    void testPreconditionManipulationLockingLegacy() throws Exception {
        final var registry = new PreconditionRegistry(mockInjector);

        final var preconditionRunning = new CompletableFuture<Void>();
        final var preconditionBlock = new CompletableFuture<Void>();
        final var manipulationWasCalled = new AtomicInteger(0);
        PreconditionUtil.MockManipulation.setManipulationCall(injector -> {
            preconditionRunning.complete(null);
            try {
                preconditionBlock.get();
            } catch (InterruptedException | ExecutionException e) {
                fail(e);
            }
            manipulationWasCalled.incrementAndGet();
            return true;
        });

        registry.registerManipulationPrecondition(PreconditionUtil.MockManipulation.class);
        // run the preconditions in a separate thread
        final var preconditionsFinished = new CompletableFuture<Void>();
        final var preconditionThread = new Thread(() -> {
            try {
                registry.runPreconditions();
            } catch (PreconditionException e) {
                fail(e);
            } finally {
                preconditionsFinished.complete(null);
            }
        });
        preconditionThread.setDaemon(true);
        preconditionThread.start();

        // wait until the precondition is running
        preconditionRunning.get();
        // ensure the lock is held
        assertTrue(locker.isLocked());

        // allow the precondition to finish
        preconditionBlock.complete(null);

        // wait for the precondition execution to have finished
        preconditionsFinished.get();

        assertEquals(1, manipulationWasCalled.get());
        assertFalse(locker.isLocked());
    }

    /**
     * Verifies whether {@link LockingPrecondition}'s are not causing the registry to lock.
     */
    @Test
    @Timeout(value = 5)
    void testPreconditionManipulationLocking() throws Exception {
        final var registry = new PreconditionRegistry(mockInjector);

        final var preconditionRunning = new CompletableFuture<Void>();
        final var preconditionBlock = new CompletableFuture<Void>();
        final var manipulationWasCalled = new AtomicInteger(0);
        PreconditionUtil.MockLockingManipulation.setManipulationCall(injector -> {
            preconditionRunning.complete(null);
            try {
                preconditionBlock.get();
            } catch (InterruptedException | ExecutionException e) {
                fail(e);
            }
            manipulationWasCalled.incrementAndGet();
            return true;
        });

        registry.registerManipulationPrecondition(PreconditionUtil.MockLockingManipulation.class);
        // run the preconditions in a separate thread
        final var preconditionsFinished = new CompletableFuture<Void>();
        final var preconditionThread = new Thread(() -> {
            try {
                registry.runPreconditions();
            } catch (PreconditionException e) {
                fail(e);
            } finally {
                preconditionsFinished.complete(null);
            }
        });
        preconditionThread.setDaemon(true);
        preconditionThread.start();

        // wait until the precondition is running
        preconditionRunning.get();
        // ensure the lock is not held
        assertFalse(locker.isLocked());

        // allow the precondition to finish
        preconditionBlock.complete(null);

        // wait for the precondition execution to have finished
        preconditionsFinished.get();

        assertEquals(1, manipulationWasCalled.get());
        assertFalse(locker.isLocked());
    }
}
