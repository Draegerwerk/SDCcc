/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2023 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.messages.guice;

import com.draeger.medical.sdccc.messages.Message;
import com.google.inject.assistedinject.Assisted;
import org.somda.sdc.dpws.CommunicationLog;
import org.somda.sdc.dpws.soap.CommunicationContext;

/**
 * Guice factory for {@linkplain Message}.
 */
public interface MessageFactory {
    /**
     * Creates a {@linkplain Message} instance.
     *
     * @param direction            the direction of the message, i.e. inbound or outbound
     * @param messageType          type of the message, i.e. request, response
     * @param communicationContext context containing transport and application information
     * @return a new {@linkplain Message} instance
     */
    Message create(
            @Assisted CommunicationLog.Direction direction,
            @Assisted CommunicationLog.MessageType messageType,
            @Assisted CommunicationContext communicationContext);
}
