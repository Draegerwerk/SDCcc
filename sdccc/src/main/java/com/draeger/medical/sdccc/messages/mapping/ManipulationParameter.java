/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.messages.mapping;

import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import org.hibernate.annotations.GenericGenerator;

/**
 * POJO for persisting manipulation parameter.
 */
@Entity(name = "ManipulationParameter")
@Table(name = "manipulation_parameter")
public class ManipulationParameter {
    @Id
    @GenericGenerator(name = "ManipulationParameterIDGen", strategy = "increment")
    @GeneratedValue(generator = "ManipulationParameterIDGen")
    private long incId;

    private String parameterName;

    @ManyToOne(fetch = FetchType.LAZY)
    private ManipulationData manipulationData;

    private String parameterValue;

    /**
     * This will be used by hibernate when creating the POJO from database entries.
     */
    public ManipulationParameter() {}

    /**
     * This will be used when creating the POJO before loading it into the database.
     *
     * @param parameterName    of the manipulation parameter
     * @param parameterValue   of the manipulation parameter
     * @param manipulationData row to link to
     */
    public ManipulationParameter(
            final String parameterName, final String parameterValue, final ManipulationData manipulationData) {
        this.parameterName = parameterName;
        this.parameterValue = parameterValue;
        this.manipulationData = manipulationData;
    }

    public String getParameterName() {
        return parameterName;
    }

    public String getParameterValue() {
        return parameterValue;
    }
}
