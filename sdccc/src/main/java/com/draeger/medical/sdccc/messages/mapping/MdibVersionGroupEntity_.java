/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
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
public final class MdibVersionGroupEntity_ {

    public static volatile SingularAttribute<MdibVersionGroupEntity, Long> incId;
    public static volatile SingularAttribute<MdibVersionGroupEntity, Long> mdibVersion;
    public static volatile SingularAttribute<MdibVersionGroupEntity, String> sequenceId;
    public static volatile SingularAttribute<MdibVersionGroupEntity, String> bodyElement;
    public static volatile SingularAttribute<MdibVersionGroupEntity, MessageContent> messageContent;

    private MdibVersionGroupEntity_() {}
}
