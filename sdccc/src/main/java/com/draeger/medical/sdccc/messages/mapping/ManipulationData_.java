/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2023 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.messages.mapping;

import com.draeger.medical.t2iapi.ResponseTypes;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import javax.persistence.metamodel.ListAttribute;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;

/**
 * Used for building database query criteria.
 */
@SuppressFBWarnings(
        value = {"UUF_UNUSED_PUBLIC_OR_PROTECTED_FIELD", "UWF_UNWRITTEN_PUBLIC_OR_PROTECTED_FIELD"},
        justification = "This is a commonly made design choice for persistence meta models.")
@StaticMetamodel(ManipulationData.class)
public final class ManipulationData_ {
    public static volatile SingularAttribute<ManipulationData, Long> incId;
    public static volatile SingularAttribute<ManipulationData, Long> startTimestamp;
    public static volatile SingularAttribute<ManipulationData, Long> finishTimestamp;
    public static volatile SingularAttribute<ManipulationData, ResponseTypes.Result> result;
    public static volatile SingularAttribute<ManipulationData, String> response;
    public static volatile SingularAttribute<ManipulationData, String> methodName;
    public static volatile ListAttribute<ManipulationData, ManipulationParameter> parameters;
    public static volatile SingularAttribute<ManipulationData, String> uuid;

    private ManipulationData_() {}
}
