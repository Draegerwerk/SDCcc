/*
 * This Source Code Form is subject to the terms of the "SDCcc non-commercial use license".
 *
 * Copyright (C) 2025 Draegerwerk AG & Co. KGaA
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
 *
 * <p>
 * It is possible to enable a reconnect feature that will try to reconnect the provider after a connection loss.
 * To use this feature, the following steps are necessary:
 * </p>
 * <p>
 *     Example to enable the reconnect feature for 10 seconds and check if a reconnect happened:
 *     <pre>
 *         TestClient testClient = getInjector().getInstance(TestClient.class);
 *         // enable reconnect feature for 10 seconds
 *         testClient.enableReconnect(10);
 *         // some other code here that may cause a connection loss
 *         ...
 *         // optional: if the provider is ready to reconnect {@link TestClient#notifyReconnectProviderReady()}
 *         // can be used to skip the initial wait time for the first reconnect attempt
 *         boolean successfullyNotified = testClient.notifyReconnectProviderReady();
 *         // get the result of the reconnect. This call blocks until the process is completed
 *         boolean reconnectHappened = testClient.getReconnectResult();
 *         // disable reconnect feature to reset the feature, this is necessary to call
 *         // {@link TestClient#enableReconnect(long)} again
 *         testClient.disableReconnect();
 *         </pre>
 * </p>
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
     * Enable reconnection attempts on connection loss. {@link TestClient#disableReconnect()} needs to be called before
     * calling this method again, otherwise an exception will be thrown.
     * @param timeoutInSeconds time to wait until the reconnect needs to be finished, when the provided timeout is
     *                         smaller than
     *                         {@link com.draeger.medical.sdccc.configuration.TestSuiteConfig#NETWORK_RECONNECT_WAIT} *
     *                         {@link com.draeger.medical.sdccc.configuration.TestSuiteConfig#NETWORK_RECONNECT_TRIES}
     *                         it will be set to that value
     * @throws IllegalStateException if the reconnect feature is already enabled
     */
    void enableReconnect(long timeoutInSeconds) throws IllegalStateException;

    /**
     * Notify that the provider is ready. Can be used to skip the initial wait time for the provider startup during the
     * reconnect process.
     *
     * @return true if the provider was successfully notified, false otherwise
     */
    boolean notifyReconnectProviderReady();

    /**
     * @return true if a successful reconnection happened within the timeout,
     *         and false if the timeout was reached or the reconnect was unsuccessful
     */
    boolean getReconnectResult();

    /**
     * Disable the reconnect feature again. This needs to be called when {@link TestClient#enableReconnect(long)} was
     * called before, otherwise another call to {@link TestClient#enableReconnect(long)} will result in an exception.
     */
    void disableReconnect();

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
