/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2023 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.util;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LogEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the {@linkplain TriggerOnErrorOrWorseLogAppender}
 */
public class TriggerOnErrorOrWorseLogAppenderTest {

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

    public void testAppend(Level level, boolean expectHandlerCall) {

        AtomicBoolean flag = new AtomicBoolean(false);

        // given
        TriggerOnErrorOrWorseLogAppender classUnderTest = new TriggerOnErrorOrWorseLogAppender("log", null);
        classUnderTest.setOnErrorOrWorseHandler(new TriggerOnErrorOrWorseLogAppender.OnErrorOrWorseHandler() {
            @Override
            public void onErrorOrWorse(LogEvent event) {
                flag.set(true);
            }
        });
        LogEvent event = mock(LogEvent.class);
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
