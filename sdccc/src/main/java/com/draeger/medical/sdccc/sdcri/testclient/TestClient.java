/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2023-2025 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.sdcri.testclient;

import com.google.inject.Injector;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
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
     *
     * <p>
     * When the completable future is:
     * <ul> true -> a successful reconnect happened within the timeout </ul>
     * <ul> false -> no successful reconnect attempt happened, or the connection was not lost </ul>
     * <ul> ReconnectException -> if interrupted by timeout, or the feature is not available </ul>
     *
     * <p>
     * Example to enable the reconnect feature for 10 seconds and check if a reconnect happened:
     * <pre>
     *
     * TestClient testClient = getInjector().getInstance(TestClient.class);
     *
     * // enable reconnect feature for 10 seconds
     * var reconnectFuture = testClient.enableReconnect(10);
     * // some other code here that may cause a connection loss
     *
     * // optional: if the provider is ready to reconnect {@link TestClient#notifyReconnectProviderReady()}
     * // can be used to skip the initial wait time for the first reconnect attempt
     * testClient.notifyReconnectProviderReady();
     * // wait for reconnect or for timeout until feature is disabled again
     * reconnectFuture.get();
     * // disable reconnect feature
     * testClient.disableReconnect();
     * </pre>
     *
     * @param timeoutInSeconds time to wait until reconnect is finished or disabled again
     * @return a CompletableFuture that completes with true if a successful reconnection happened within the timeout,
     * and false if no successful reconnect attempt was made, or the timeout was reached without a connection loss.
     */
    CompletableFuture<Boolean> enableReconnect(long timeoutInSeconds);

    /**
     * Disable the reconnect feature after using it.
     */
    void disableReconnect();

    /**
     * Notify that the provider is ready. Can be used to skip the initial wait time for the provider startup during the
     * reconnect process.
     *
     * @see TestClient#enableReconnect(long)
     * @return  true if the reconnect feature was notified successfully
     *          false if something
     */
    boolean notifyReconnectProviderReady();

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
