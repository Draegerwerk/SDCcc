/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2023 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.messages.guice;

import com.draeger.medical.sdccc.messages.ManipulationInfo;
import com.draeger.medical.sdccc.tests.util.ManipulationParameterUtil;
import com.draeger.medical.t2iapi.ResponseTypes;
import com.google.inject.assistedinject.Assisted;

/**
 * Guice factory for {@linkplain ManipulationInfo}.
 */
public interface ManipulationInfoFactory {
    /**
     * Creates a {@linkplain ManipulationInfo} instance.
     *
     * @param startTimestamp of the manipulation
     * @param stopTimestamp  of the manipulation
     * @param result         of the manipulation
     * @param response       of the manipulation, serialized
     * @param methodName     of the manipulation
     * @param parameters     of the manipulation
     * @return a new {@linkplain ManipulationInfo} instance
     */
    ManipulationInfo create(
            @Assisted(value = "startTime") long startTimestamp,
            @Assisted(value = "stopTime") long stopTimestamp,
            @Assisted ResponseTypes.Result result,
            @Assisted String response,
            @Assisted(value = "methodName") String methodName,
            @Assisted ManipulationParameterUtil.ManipulationParameterData parameters);
}
