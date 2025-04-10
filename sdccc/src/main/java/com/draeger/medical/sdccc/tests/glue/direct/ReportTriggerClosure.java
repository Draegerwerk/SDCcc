/*
 * This Source Code Form is subject to the terms of the "SDCcc non-commercial use license".
 *
 * Copyright (C) 2025 Draegerwerk AG & Co. KGaA
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
