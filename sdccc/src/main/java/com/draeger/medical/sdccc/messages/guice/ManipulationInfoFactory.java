/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2023 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.messages.guice;

import com.draeger.medical.sdccc.messages.ManipulationInfo;
import com.draeger.medical.t2iapi.ResponseTypes;
import com.google.inject.assistedinject.Assisted;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;

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
     * @param methodName     of the manipulation
     * @param parameters     of the manipulation
     * @return a new {@linkplain ManipulationInfo} instance
     */
    ManipulationInfo create(
            @Assisted(value = "startTime") long startTimestamp,
            @Assisted(value = "stopTime") long stopTimestamp,
            @Assisted ResponseTypes.Result result,
            @Assisted(value = "methodName") String methodName,
            @Assisted List<Pair<String, String>> parameters);
}
