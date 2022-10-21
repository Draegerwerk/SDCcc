/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.tests.glue.direct;

import com.draeger.medical.sdccc.sdcri.testclient.TestClient;
import org.somda.sdc.dpws.service.HostedServiceProxy;

import java.util.Optional;

/**
 * Closure to get a serviceProxy from a TestClient.
 */
public interface GetServiceProxyClosure {

    /**
     * Gets the Service Proxy from a given TestClient.
     * @param tc the TextClient
     * @return the HostedServiceProxy
     */
    Optional<HostedServiceProxy> execute(TestClient tc);

}
