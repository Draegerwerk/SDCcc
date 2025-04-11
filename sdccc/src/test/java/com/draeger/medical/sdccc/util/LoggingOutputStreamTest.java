/*
 * This Source Code Form is subject to the terms of the "SDCcc non-commercial use license".
 *
 * Copyright (C) 2025 Draegerwerk AG & Co. KGaA
 */

package com.draeger.medical.sdccc.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/**
 * Unit tests for the {@linkplain LoggingOutputStream} which writes to an log4j2 logger.
 */
public class LoggingOutputStreamTest {

    /**
     * Tests whether writing messages containing complex characters succeeds.
     *
     * @throws Exception on any exception.
     */
    @Test
    public void testBigListOfNaughtyStrings() throws Exception {

        final var logger = mock(Logger.class, Mockito.RETURNS_DEEP_STUBS);
        final var logLevel = Level.INFO;
        try (final var outputStream = new LoggingOutputStream(logger, logLevel, StandardCharsets.UTF_8);
                final var naughtyStrings = getClass().getResourceAsStream("../messages/blns.txt");
                final var data = new BufferedReader(new InputStreamReader(naughtyStrings, StandardCharsets.UTF_8))) {

            assertTrue(naughtyStrings.available() > 0);

            final AtomicInteger invocationNo = new AtomicInteger(1);
            data.lines().forEach(naughtyString -> {
                try {
                    outputStream.write(naughtyString.getBytes(StandardCharsets.UTF_8));
                    outputStream.write('\n');
                } catch (final IOException e) {
                    fail(e);
                }

                final ArgumentCaptor<Level> logLevelCaptor = ArgumentCaptor.forClass(Level.class);
                final ArgumentCaptor<String> logMessageCaptor = ArgumentCaptor.forClass(String.class);

                verify(logger, times(invocationNo.get())).log(logLevelCaptor.capture(), logMessageCaptor.capture());

                assertEquals(logLevel, logLevelCaptor.getValue());
                assertEquals(naughtyString, logMessageCaptor.getValue());
                invocationNo.getAndIncrement();
            });
        }
    }

    /**
     * Tests whether passing a log level to the {@linkplain LoggingOutputStream} causes the calls to
     * the logger to use that log level.
     */
    @Test
    public void testSettingLogLevel() {
        final var logger = mock(Logger.class, Mockito.RETURNS_DEEP_STUBS);

        Arrays.stream(Level.values()).forEach(level -> {
            reset(logger);

            try (final var outputStream = new LoggingOutputStream(logger, level, StandardCharsets.UTF_8)) {
                final var logMessage = String.format("message for level %s", level.toString());

                try {
                    outputStream.write(logMessage.getBytes(StandardCharsets.UTF_8));
                    outputStream.write('\n');
                } catch (final IOException e) {
                    fail(e);
                }

                final ArgumentCaptor<Level> logLevelCaptor = ArgumentCaptor.forClass(Level.class);
                final ArgumentCaptor<String> logMessageCaptor = ArgumentCaptor.forClass(String.class);

                verify(logger).log(logLevelCaptor.capture(), logMessageCaptor.capture());

                assertEquals(level, logLevelCaptor.getValue());
                assertEquals(logMessage, logMessageCaptor.getValue());
            } catch (final IOException e) {
                fail(e);
            }
        });
    }
}
