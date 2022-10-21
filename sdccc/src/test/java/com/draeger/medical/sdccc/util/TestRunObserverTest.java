/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the {@linkplain TestRunObserver}.
 */
public class TestRunObserverTest {

    /**
     * Test whether string reasons are stored and retrievable.
     */
    @Test
    void testStringReason() {
        final var reason = "Things broke.";
        final var obs = new TestRunObserver();

        assertFalse(obs.isInvalid());
        assertTrue(obs.getReasons().isEmpty());

        obs.invalidateTestRun(reason);

        assertTrue(obs.isInvalid());
        assertTrue(obs.getReasons().contains(reason));
        assertEquals(1, obs.getReasons().size());
    }

    /**
     * Test whether added throwable reasons are retrievable as strings.
     */
    @Test
    void testThrowableReason() {
        final var throwable = new Exception("Things broke.");
        final var obs = new TestRunObserver();

        assertFalse(obs.isInvalid());
        assertTrue(obs.getReasons().isEmpty());

        obs.invalidateTestRun(throwable);

        assertTrue(obs.isInvalid());
        assertTrue(obs.getReasons().contains(throwable.getMessage()));
        assertEquals(1, obs.getReasons().size());
    }


    /**
     * Test whether reasons with string and throwable have their string stored.
     */
    @Test
    void testTextAndThrowableReason() {
        final var reason = "The thing is broken.";
        final var throwable = new Exception("Things broke.");
        final var obs = new TestRunObserver();

        assertFalse(obs.isInvalid());
        assertTrue(obs.getReasons().isEmpty());

        obs.invalidateTestRun(reason, throwable);

        assertTrue(obs.isInvalid());
        assertTrue(obs.getReasons().contains(reason));
        assertEquals(1, obs.getReasons().size());
    }

    /**
     * Test whether multiple string and throwable reasons are all stored and retrievable.
     */
    @Test
    void testRunMultipleReasons() {
        final var reason1 = "Things broke.";
        final var reason2 = "Things broke more.";
        final var reason3 = new RuntimeException("Now it's totally gone, man");
        final var obs = new TestRunObserver();

        assertFalse(obs.isInvalid());
        assertTrue(obs.getReasons().isEmpty());

        obs.invalidateTestRun(reason1);
        assertTrue(obs.isInvalid());
        assertTrue(obs.getReasons().contains(reason1));
        assertEquals(1, obs.getReasons().size());

        obs.invalidateTestRun(reason2);
        assertTrue(obs.isInvalid());
        assertTrue(obs.getReasons().contains(reason1));
        assertTrue(obs.getReasons().contains(reason2));
        assertEquals(2, obs.getReasons().size());

        obs.invalidateTestRun(reason3);
        assertTrue(obs.isInvalid());
        assertTrue(obs.getReasons().contains(reason1));
        assertTrue(obs.getReasons().contains(reason2));
        assertTrue(obs.getReasons().contains(reason3.getMessage()));
        // CHECKSTYLE.OFF: MagicNumber
        assertEquals(3, obs.getReasons().size());
        // CHECKSTYLE.ON: MagicNumber
    }

}
