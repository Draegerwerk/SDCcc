/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2023 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.messages.mapping;

import com.draeger.medical.t2iapi.ResponseTypes;
import java.util.ArrayList;
import java.util.List;
import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import org.apache.commons.lang3.tuple.Pair;
import org.hibernate.annotations.GenericGenerator;

/**
 * POJO for persisting relevant manipulation data.
 */
@Entity(name = "ManipulationData")
@Table(name = "manipulation_data")
public class ManipulationData {

    @Id
    @GenericGenerator(name = "ManipulationDataIDGen", strategy = "increment")
    @GeneratedValue(generator = "ManipulationDataIDGen")
    private long incId;

    private long startTimestamp;
    private long finishTimestamp;
    private String response;
    private ResponseTypes.Result result;
    private String methodName;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "manipulationData", orphanRemoval = true)
    private List<ManipulationParameter> parameters;

    private String uuid;

    /**
     * This will be used by hibernate when creating the POJO from database entries.
     */
    public ManipulationData() {}

    /**
     * This will be used when creating the POJO before loading it into the database.
     *
     * @param startTimestamp  of the manipulation
     * @param finishTimestamp of the manipulation
     * @param result          of the manipulation
     * @param response        of the manipulation, serialized
     * @param methodName      of the manipulation
     * @param parameters      of the manipulation
     * @param uuid            of the manipulation
     */
    public ManipulationData(
            final long startTimestamp,
            final long finishTimestamp,
            final ResponseTypes.Result result,
            final String response,
            final String methodName,
            final List<Pair<String, String>> parameters,
            final String uuid) {
        this.startTimestamp = startTimestamp;
        this.finishTimestamp = finishTimestamp;
        this.response = response;
        this.result = result;
        this.methodName = methodName;
        final List<ManipulationParameter> manipulationParameters = new ArrayList<>();
        for (var parameter : parameters) {
            manipulationParameters.add(new ManipulationParameter(parameter.getKey(), parameter.getValue(), this));
        }
        this.parameters = manipulationParameters;
        this.uuid = uuid;
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

    public String getUuid() {
        return uuid;
    }

    public List<ManipulationParameter> getParameters() {
        return parameters;
    }

    public String getResponse() {
        return response;
    }
}
