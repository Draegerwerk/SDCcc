/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.sdcri;

import com.draeger.medical.sdccc.messages.MessageStorage;
import com.google.inject.Inject;
import java.io.IOException;
import java.io.OutputStream;
import org.somda.sdc.dpws.CommunicationLog;
import org.somda.sdc.dpws.CommunicationLogSink;
import org.somda.sdc.dpws.soap.CommunicationContext;

/**
 * Connector between the SDCri {@linkplain CommunicationLog} and SDCcc's {@linkplain MessageStorage}.
 */
public class CommunicationLogMessageStorage implements CommunicationLogSink {

    private final MessageStorage messageStorage;

    /**
     * Creates a {@linkplain CommunicationLogSink} connected to a {@linkplain MessageStorage}.
     *
     * @param messageStorage to write incoming messages to
     */
    @Inject
    CommunicationLogMessageStorage(final MessageStorage messageStorage) {
        this.messageStorage = messageStorage;
    }

    /**
     * Creates an output stream which is stored in the {@linkplain MessageStorage}.
     *
     * @param path                 transport type, i.e. UDP or TCP
     * @param direction            message direction, i.e. inbound or outbound
     * @param messageType          type of the message, i.e. request, response
     * @param communicationContext transport and application information for the message
     * @return stream to write message content into
     */
    public OutputStream createTargetStream(
            final CommunicationLog.TransportType path,
            final CommunicationLog.Direction direction,
            final CommunicationLog.MessageType messageType,
            final CommunicationContext communicationContext) {
        try {
            return messageStorage.createMessageStream(path, direction, messageType, communicationContext);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }
}
