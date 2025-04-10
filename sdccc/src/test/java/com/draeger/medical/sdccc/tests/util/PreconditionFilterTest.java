/*
 * This Source Code Form is subject to the terms of the "SDCcc non-commercial use license".
 *
 * Copyright (C) 2025 Draegerwerk AG & Co. KGaA
 */

package com.draeger.medical.sdccc.tests.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.draeger.medical.sdccc.manipulation.precondition.PreconditionRegistry;
import com.draeger.medical.sdccc.manipulation.precondition.PreconditionUtil;
import com.draeger.medical.sdccc.tests.annotations.RequirePrecondition;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

/**
 * Unit tests for the {@linkplain PreconditionFilter}.
 */
public class PreconditionFilterTest {

    @BeforeEach
    void setUp() {
        PreconditionUtil.MockPrecondition.reset();
    }

    /**
     * Tests whether filter collects preconditions from all tests marked with
     * {@linkplain RequirePrecondition}.
     */
    @Test
    @DisplayName("Ensure the filter collects all precondition annotations")
    public void testPreconditionFilterCollectsAnnotations() {
        final var registerCalls = new AtomicInteger(0);
        final var registry = mock(PreconditionRegistry.class);
        doAnswer(invocationOnMock -> {
                    registerCalls.incrementAndGet();
                    return null;
                })
                .when(registry)
                .registerSimplePrecondition(any(Class.class));

        final var filter = new PreconditionFilter(registry);

        final var selector = DiscoverySelectors.selectClass(MockTests.class);
        final var testsRequest = LauncherDiscoveryRequestBuilder.request()
                .selectors(selector)
                .filters(filter)
                .build();
        final Launcher launcher = LauncherFactory.create();
        // trigger filter
        launcher.discover(testsRequest);

        assertEquals(2, registerCalls.get());
    }

    /**
     * Tests whether {@linkplain RuntimeException}s thrown during filtering are passed to the caller.
     */
    @Test
    @DisplayName("Ensure the filter passes RuntimeException upwards")
    public void testPreconditionFilterConstructorThrows() {
        final var registry = mock(PreconditionRegistry.class);
        doAnswer(invocationOnMock -> {
                    throw new RuntimeException("Intended exception");
                })
                .when(registry)
                .registerSimplePrecondition(any(Class.class));

        final var filter = new PreconditionFilter(registry);

        final var selector = DiscoverySelectors.selectClass(MockTests.class);
        final var testsRequest = LauncherDiscoveryRequestBuilder.request()
                .selectors(selector)
                .filters(filter)
                .build();
        final Launcher launcher = LauncherFactory.create();
        // filter should throw
        assertThrows(RuntimeException.class, () -> launcher.discover(testsRequest));
    }

    @RequirePrecondition(simplePreconditions = {PreconditionUtil.MockPrecondition.class})
    static class MockTests {

        @Test
        void innerTest() {}

        @Test
        @RequirePrecondition(simplePreconditions = {PreconditionUtil.MockPrecondition.class})
        void innerTestWithPrecondition() {}

        @Test
        @RequirePrecondition(simplePreconditions = {PreconditionUtil.MockPrecondition.class})
        void innerTestWithAnotherPrecondition() {}

        @RequirePrecondition(simplePreconditions = {PreconditionUtil.MockPrecondition.class})
        void noTestShouldIgnorePrecondition() {}
    }
}
