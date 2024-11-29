/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2023 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the {@linkplain TriggerOnErrorOrWorseLogAppender}.
 */
public class TriggerOnErrorOrWorseLogAppenderTest {

    /**
     * Test if the append method handles all Log Levels appropriately.
     */
    @Test
    public void testAppendWithDifferentLogLevels() {
        testAppend(Level.ALL, false);
        testAppend(Level.TRACE, false);
        testAppend(Level.DEBUG, false);
        testAppend(Level.INFO, false);
        testAppend(Level.WARN, false);
        testAppend(Level.ERROR, true);
        testAppend(Level.FATAL, true);
        testAppend(Level.OFF, true);
    }

    /**
     * Used by the unit test testAppendWithDifferentLogLevels().
     * @param level the Log Level to test
     * @param expectHandlerCall true if it is expected that the Handler is called, false otherwise.
     */
    private void testAppend(final Level level, final boolean expectHandlerCall) {

        final AtomicBoolean flag = new AtomicBoolean(false);

        // given
        final Filter filter = mock(Filter.class);
        final TriggerOnErrorOrWorseLogAppender classUnderTest = new TriggerOnErrorOrWorseLogAppender("log", filter);
        classUnderTest.setOnErrorOrWorseHandler((LogEvent event) -> flag.set(true));
        final LogEvent event = mock(LogEvent.class);
        when(event.getLevel()).thenReturn(level);

        // when
        classUnderTest.append(event);

        // then
        if (expectHandlerCall) {
            assertTrue(flag.get());
        } else {
            assertFalse(flag.get());
        }
    }
}
