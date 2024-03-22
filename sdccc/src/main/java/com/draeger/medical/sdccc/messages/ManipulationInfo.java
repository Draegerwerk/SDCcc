/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2023 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.messages;

import com.draeger.medical.sdccc.tests.util.ManipulationParameterUtil;
import com.draeger.medical.t2iapi.ResponseTypes;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.util.List;
import java.util.UUID;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Manipulation Information object used for storage purposes.
 */
public class ManipulationInfo implements DatabaseEntry {
    private final long startTimestamp;
    private final long finishTimestamp;
    private final ResponseTypes.Result result;
    private final String methodName;
    private final List<Pair<String, String>> parameters;
    private final MessageStorage storage;
    private UUID id;

    @Inject
    ManipulationInfo(
            @Assisted(value = "startTime") final long startTimestamp,
            @Assisted(value = "stopTime") final long finishTimestamp,
            @Assisted final ResponseTypes.Result result,
            @Assisted(value = "methodName") final String methodName,
            @Assisted final ManipulationParameterUtil.ManipulationParameterData parameters,
            final MessageStorage messageStorage) {

        this.startTimestamp = startTimestamp;
        this.finishTimestamp = finishTimestamp;
        this.result = result;
        this.methodName = methodName;
        this.parameters = parameters.getParameterData();
        storage = messageStorage;
    }

    /**
     * Add this manipulation to storage.
     */
    public void addToStorage() {
        this.storage.addMessage(this);
    }

    @Override
    public String getID() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        return this.id.toString();
    }

    public long getStartTimestamp() {
        return startTimestamp;
    }

    public long getFinishTimestamp() {
        return finishTimestamp;
    }

    public ResponseTypes.Result getResult() {
        return result;
    }

    public String getMethodName() {
        return methodName;
    }

    public List<Pair<String, String>> getParameter() {
        return parameters;
    }
}
