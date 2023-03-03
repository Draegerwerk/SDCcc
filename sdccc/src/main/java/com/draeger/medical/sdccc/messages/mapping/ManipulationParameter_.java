/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2023 Draegerwerk AG & Co. KGaA.
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
@StaticMetamodel(ManipulationData.class)
public final class ManipulationParameter_ {
    public static volatile SingularAttribute<ManipulationParameter, Long> incId;
    public static volatile SingularAttribute<ManipulationParameter, String> parameterName;
    public static volatile SingularAttribute<ManipulationParameter, String> parameterValue;
    public static volatile SingularAttribute<ManipulationParameter, ManipulationData> manipulationData;

    private ManipulationParameter_() {}
}
