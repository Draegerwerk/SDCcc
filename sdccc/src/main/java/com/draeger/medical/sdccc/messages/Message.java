/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2023 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.messages;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.time.Instant;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.somda.sdc.dpws.CommunicationLog;
import org.somda.sdc.dpws.soap.CommunicationContext;

/**
 * Message object used for storage purposes.
 */
public class Message extends OutputStream implements DatabaseEntry {
    public static final String MESSAGE_WRITE_CALLED_ON_CLOSED_MESSAGE = "Message write called on closed message";

    private static final Logger LOG = LogManager.getLogger(Message.class);

    private final MessageStorage messageStorage;
    private final CommunicationLog.Direction direction;
    private final CommunicationLog.MessageType messageType;

    private final long timestamp;

    private final long nanoTimestamp;

    private final CommunicationContext communicationContext;

    private ByteArrayOutputStream memory;

    private byte[] finalMemory;

    private UUID id;

    @Inject
    Message(
            @Assisted final CommunicationLog.Direction direction,
            @Assisted final CommunicationLog.MessageType messageType,
            @Assisted final CommunicationContext communicationContext,
            final MessageStorage messageStorage) {
        this.messageStorage = messageStorage;

        this.direction = direction;
        this.messageType = messageType;
        this.communicationContext = communicationContext;

        this.timestamp = Instant.now().toEpochMilli();
        this.nanoTimestamp = System.nanoTime();
        this.memory = new ByteArrayOutputStream();
    }

    @Override
    public void write(final int b) throws IOException {
        if (this.memory == null) {
            LOG.warn(MESSAGE_WRITE_CALLED_ON_CLOSED_MESSAGE);
            throw new IOException(MESSAGE_WRITE_CALLED_ON_CLOSED_MESSAGE);
        }

        memory.write(b);
    }

    @Override
    public void close() throws IOException {
        if (this.memory != null) {
            this.finalMemory = memory.toByteArray();
            this.memory.close();
            this.memory = null;

            this.messageStorage.addMessage(this);
        } else {
            LOG.trace("Message close called on closed message");
        }
    }

    /**
     * @return is this {@linkplain OutputStream} closed
     */
    public boolean isClosed() {
        return this.memory == null;
    }

    @Override
    public String toString() {
        return "Message{"
                + ", direction=" + direction
                + ", timestamp=" + timestamp
                + ", nanoTimestamp=" + nanoTimestamp
                + '}';
    }

    @SuppressFBWarnings(
            value = {"EI_EXPOSE_REP"},
            justification = "it doesn't matter, the field might as well be public.")
    public byte[] getFinalMemory() {
        return finalMemory;
    }

    public CommunicationLog.Direction getDirection() {
        return direction;
    }

    public CommunicationContext getCommunicationContext() {
        return communicationContext;
    }

    public CommunicationLog.MessageType getMessageType() {
        return messageType;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public long getNanoTimestamp() {
        return nanoTimestamp;
    }

    @Override
    public String getID() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }

        return this.id.toString();
    }
}
