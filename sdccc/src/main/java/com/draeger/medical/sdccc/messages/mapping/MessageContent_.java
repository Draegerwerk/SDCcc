/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.messages.mapping;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.somda.sdc.dpws.CommunicationLog;

import javax.persistence.metamodel.ListAttribute;
import javax.persistence.metamodel.SetAttribute;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;
import java.security.cert.X509Certificate;


/**
 * Used for building database query criteria.
 */
// CHECKSTYLE.OFF: TypeName
@SuppressFBWarnings(
        value = {"UUF_UNUSED_PUBLIC_OR_PROTECTED_FIELD", "UWF_UNWRITTEN_PUBLIC_OR_PROTECTED_FIELD"},
        justification = "This is a commonly made design choice for persistence meta models.")
@StaticMetamodel(MessageContent.class)
public final class MessageContent_ {

    // CHECKSTYLE.OFF: VisibilityModifier
    public static volatile SingularAttribute<MessageContent, Long> incId;
    public static volatile SingularAttribute<MessageContent, String> body;
    public static volatile ListAttribute<MessageContent, X509Certificate> certs;
    public static volatile ListAttribute<MessageContent, StringEntryEntity> headers;
    public static volatile SingularAttribute<MessageContent, CommunicationLog.Direction> direction;
    public static volatile SingularAttribute<MessageContent, CommunicationLog.MessageType> messageType;
    public static volatile SingularAttribute<MessageContent, String> transactionId;
    public static volatile SingularAttribute<MessageContent, String> requestUri;
    public static volatile SingularAttribute<MessageContent, Long> timestamp;
    public static volatile SingularAttribute<MessageContent, Long> nanoTimestamp;
    public static volatile SingularAttribute<MessageContent, String> messageHash;
    public static volatile SetAttribute<MessageContent, String> actions;
    public static volatile SetAttribute<MessageContent, String> bodyElements;
    public static volatile SingularAttribute<MessageContent, String> scheme;
    public static volatile SingularAttribute<MessageContent, String> uuid;
    public static volatile SingularAttribute<MessageContent, Boolean> isSOAP;
    // CHECKSTYLE.ON: VisibilityModifier

    private MessageContent_() {}

}
// CHECKSTYLE.ON: TypeName
