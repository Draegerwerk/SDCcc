/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.tests.glue.direct;

/**
 * Closure to a Report Trigger.
 */
public interface ReportTriggerClosure {

    /**
     * Triggers the Report.
     */
    void trigger();

}
