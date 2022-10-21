/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.messages;

import com.draeger.medical.sdccc.util.XPathExtractor;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.somda.sdc.dpws.CommunicationLog;
import org.somda.sdc.dpws.soap.CommunicationContext;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.internal.verification.VerificationModeFactory.times;

/**
 * Unit tests for the Message container.
 */
public class TestMessage {

    /**
     * Tests whether writing data to a message and closing the message works.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testMessageWriteAndClose() throws Exception {
        final CommunicationContext mockContext = mock(CommunicationContext.class, RETURNS_DEEP_STUBS);
        when(mockContext.getTransportInfo().getScheme()).thenReturn("https");

        final MessageStorage mockStorage = mock(MessageStorage.class);
        when(mockStorage.getActionExtractor()).thenReturn(mock(XPathExtractor.class));

        final Message message = new Message(
            CommunicationLog.Direction.OUTBOUND,
            CommunicationLog.MessageType.REQUEST,
            mockContext,
            mockStorage
        );

        assertFalse(message.isClosed());
        verify(mockStorage, never()).addMessage(any());

        final var testString = "Testäöó";
        final var testBytes = testString.getBytes(StandardCharsets.UTF_8);

        message.write(testBytes);

        verify(mockStorage, never()).addMessage(any());

        final ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);

        message.close();

        verify(mockStorage, times(1)).addMessage(captor.capture());
        final List<Message> captorValues = captor.getAllValues();
        assertFalse(captorValues.isEmpty());
        for (final Message captorValue : captorValues) {
            assertEquals(Arrays.toString(testBytes), Arrays.toString(captorValue.getFinalMemory()));
        }

        assertTrue(message.isClosed());
        assertThrows(IOException.class, () -> message.write(1));
    }

    /**
     * Test whether message can handle content using complex characters.
     *
     * @throws Exception on any exception
     */
    @Test
    @SuppressFBWarnings(value = {"RCN_REDUNDANT_NULLCHECK_WOULD_HAVE_BEEN_A_NPE"},
        justification = "No null check performed.")
    public void testBigListOfNaughtyStrings() throws Exception {

        final CommunicationContext mockContext = mock(CommunicationContext.class, RETURNS_DEEP_STUBS);
        when(mockContext.getTransportInfo().getScheme()).thenReturn("https");

        try (final InputStream naughtyStrings = getClass().getResourceAsStream("blns.txt");
             final var data = new BufferedReader(new InputStreamReader(naughtyStrings, StandardCharsets.UTF_8))) {
            assertTrue(naughtyStrings.available() > 0);

            data.lines().forEach(naughtyString -> {

                final MessageStorage mockStorage = mock(MessageStorage.class);
                when(mockStorage.getActionExtractor()).thenReturn(mock(XPathExtractor.class));

                final var message = new Message(
                    CommunicationLog.Direction.OUTBOUND,
                    CommunicationLog.MessageType.REQUEST,
                    mockContext,
                    mockStorage
                );

                assertFalse(message.isClosed());
                verify(mockStorage, never()).addMessage(any());

                final var testBytes = naughtyString.getBytes(StandardCharsets.UTF_8);

                try {
                    message.write(testBytes);
                } catch (final IOException e) {
                    throw new RuntimeException(e);
                }

                verify(mockStorage, never()).addMessage(any());

                final ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);

                try {
                    message.close();
                } catch (final IOException e) {
                    throw new RuntimeException(e);
                }

                verify(mockStorage, times(1)).addMessage(captor.capture());
                final List<Message> captorValues = captor.getAllValues();
                assertFalse(captorValues.isEmpty());
                for (final Message captorValue : captorValues) {
                    assertEquals(Arrays.toString(testBytes), Arrays.toString(captorValue.getFinalMemory()));
                }

                assertTrue(message.isClosed());
                assertThrows(IOException.class, () -> message.write(1));

            });
        }
    }
}