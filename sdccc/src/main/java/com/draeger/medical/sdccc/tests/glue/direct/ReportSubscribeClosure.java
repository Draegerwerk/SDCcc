/*
 * This Source Code Form is subject to the terms of the "SDCcc non-commercial use license".
 *
 * Copyright (C) 2025 Draegerwerk AG & Co. KGaA
 */

package com.draeger.medical.sdccc.tests.glue.direct;

import org.somda.sdc.dpws.soap.wseventing.SubscribeResult;

/**
 * Closure to a Report Subscribe Handler.
 */
public interface ReportSubscribeClosure {

    /**
     * Subscribe to the Report.
     * @param report - reportTestData of the report to subscribe to.
     * @return SubscribeResult of the subscription.
     */
    SubscribeResult subscribe(ReportTestData report);
}
