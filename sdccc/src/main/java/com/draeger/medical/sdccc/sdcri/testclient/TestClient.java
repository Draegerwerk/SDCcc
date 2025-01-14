/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2023-2024 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.sdcri.testclient;

import com.google.inject.Injector;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.somda.sdc.dpws.client.Client;
import org.somda.sdc.dpws.service.HostingServiceProxy;
import org.somda.sdc.dpws.soap.exception.TransportException;
import org.somda.sdc.dpws.soap.interception.InterceptorException;
import org.somda.sdc.glue.consumer.SdcRemoteDevice;
import org.somda.sdc.glue.consumer.SdcRemoteDevicesConnector;

/**
 * An SDC consumer used for testing.
 */
public interface TestClient {

    /**
     * Start the SDC client.
     *
     * @param waitTime time to wait for startup
     * @throws TimeoutException in case startup did not finish on time
     */
    void startService(Duration waitTime) throws TimeoutException;

    /**
     * Stop the SDC client.
     *
     * @param waitTime time to wait for shutdown
     * @throws TimeoutException in case shutdown did not finish on time
     */
    void stopService(Duration waitTime) throws TimeoutException;

    /**
     * @return is the client service running
     */
    boolean isClientRunning();

    /**
     * Connect the client to the preconfigured EPR.
     *
     * @throws InterceptorException on internal ri error
     * @throws TransportException   on connection error
     * @throws IOException          on connection error
     */
    void connect() throws InterceptorException, TransportException, IOException;

    /**
     * Enable reconnection attempts on connection loss.
     */
    void enableReconnect();

    /**
     * Disconnects the SDC client from the target.
     *
     * @throws TimeoutException in case the disconnect did not finish on time
     */
    void disconnect() throws TimeoutException;

    Client getClient();

    SdcRemoteDevicesConnector getConnector();

    SdcRemoteDevice getSdcRemoteDevice();

    HostingServiceProxy getHostingServiceProxy();

    String getTargetEpr();

    List<String> getTargetXAddrs();

    Injector getInjector();
}
