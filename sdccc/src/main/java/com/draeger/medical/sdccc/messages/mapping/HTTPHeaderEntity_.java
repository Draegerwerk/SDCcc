/*
 * This Source Code Form is subject to the terms of the "SDCcc non-commercial use license".
 *
 * Copyright (C) 2025 Draegerwerk AG & Co. KGaA
 */

package com.draeger.medical.sdccc.messages.mapping;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;

/**
 * Used for building database query criteria.
 */
@SuppressFBWarnings(
        value = {"UUF_UNUSED_PUBLIC_OR_PROTECTED_FIELD", "UWF_UNWRITTEN_PUBLIC_OR_PROTECTED_FIELD"},
        justification = "This is a commonly made design choice for persistence meta models.")
@StaticMetamodel(MessageContent.class)
public final class HTTPHeaderEntity_ {

    public static volatile SingularAttribute<HTTPHeaderEntity, Long> incId;
    public static volatile SingularAttribute<HTTPHeaderEntity, String> headerKey;
    public static volatile SingularAttribute<HTTPHeaderEntity, String> headerValue;
    public static volatile SingularAttribute<HTTPHeaderEntity, MessageContent> messageContent;

    private HTTPHeaderEntity_() {}
}
