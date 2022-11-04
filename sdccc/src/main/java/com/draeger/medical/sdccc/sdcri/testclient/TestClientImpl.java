/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.sdcri.testclient;

import com.draeger.medical.sdccc.configuration.TestSuiteConfig;
import com.draeger.medical.sdccc.util.TestRunObserver;
import com.google.common.eventbus.Subscribe;
import com.google.common.util.concurrent.AbstractIdleService;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.somda.sdc.dpws.DpwsFramework;
import org.somda.sdc.dpws.client.Client;
import org.somda.sdc.dpws.client.DiscoveredDevice;
import org.somda.sdc.dpws.client.DiscoveryObserver;
import org.somda.sdc.dpws.client.event.ProbedDeviceFoundMessage;
import org.somda.sdc.dpws.service.HostingServiceProxy;
import org.somda.sdc.dpws.soap.exception.TransportException;
import org.somda.sdc.dpws.soap.interception.InterceptorException;
import org.somda.sdc.glue.consumer.ConnectConfiguration;
import org.somda.sdc.glue.consumer.PrerequisitesException;
import org.somda.sdc.glue.consumer.SdcDiscoveryFilterBuilder;
import org.somda.sdc.glue.consumer.SdcRemoteDevice;
import org.somda.sdc.glue.consumer.SdcRemoteDevicesConnector;
import org.somda.sdc.glue.consumer.WatchdogObserver;
import org.somda.sdc.glue.consumer.event.WatchdogMessage;

/**
 * SDCri consumer used to test SDC providers.
 */
@Singleton
public class TestClientImpl extends AbstractIdleService implements TestClient, WatchdogObserver {
    private static final Logger LOG = LogManager.getLogger(TestClientImpl.class);

    // max time to wait for futures
    private static final Duration MAX_WAIT = Duration.ofSeconds(10);
    private static final String COULDN_T_CONNECT_TO_TARGET = "Couldn't connect to target";

    private final Injector injector;
    private final String targetEpr;
    private final NetworkInterface networkInterface;
    private final Client client;
    private final SdcRemoteDevicesConnector connector;
    private final TestRunObserver testRunObserver;
    // tracks the expected connection state
    private final AtomicBoolean shouldBeConnected;
    private DpwsFramework dpwsFramework;
    private SdcRemoteDevice sdcRemoteDevice;
    private HostingServiceProxy hostingServiceProxy;
    private List<String> targetXAddrs;

    /**
     * Creates an SDCri consumer instance.
     *
     * @param targetDevicePr  DUT EPR address
     * @param adapterAddress  ip of the network interface to bind to
     * @param testClientUtil  test client utility
     * @param testRunObserver observer for invalidating test runs on unexpected errors
     */
    @Inject
    public TestClientImpl(
            @Named(TestSuiteConfig.CONSUMER_DEVICE_EPR) final String targetDevicePr,
            @Named(TestSuiteConfig.NETWORK_INTERFACE_ADDRESS) final String adapterAddress,
            final TestClientUtil testClientUtil,
            final TestRunObserver testRunObserver) {
        this.injector = testClientUtil.getInjector();
        this.client = injector.getInstance(Client.class);
        this.connector = injector.getInstance(SdcRemoteDevicesConnector.class);
        this.testRunObserver = testRunObserver;
        this.shouldBeConnected = new AtomicBoolean(false);

        // get interface for address
        try {
            this.networkInterface = NetworkInterface.getByInetAddress(Inet4Address.getByName(adapterAddress));
        } catch (final SocketException | UnknownHostException e) {
            LOG.error("Error while retrieving network adapter for ip {}", adapterAddress, e);
            throw new RuntimeException(e);
        }

        if (this.networkInterface == null) {
            LOG.error(
                    "Error while setting network interface, adapter for address {} seems unavailable", adapterAddress);
            throw new RuntimeException("Error while setting network interface, adapter seems unavailable");
        }

        this.targetEpr = targetDevicePr;
        LOG.info("Configured target epr is {}", targetEpr);
    }

    @Override
    protected void startUp() throws Exception {
        // provide the name of your network adapter
        this.dpwsFramework = injector.getInstance(DpwsFramework.class);
        this.dpwsFramework.setNetworkInterface(networkInterface);
        dpwsFramework.startAsync().awaitRunning();
        client.startAsync().awaitRunning();
    }

    @Override
    protected void shutDown() throws Exception {
        client.stopAsync().awaitTerminated();
        dpwsFramework.stopAsync().awaitTerminated();
    }

    @Override
    public boolean isClientRunning() {
        return this.isRunning();
    }

    @Override
    public void startService(final Duration waitTime) throws TimeoutException {
        startAsync().awaitRunning(waitTime);
    }

    @Override
    public void stopService(final Duration waitTime) throws TimeoutException {
        stopAsync().awaitTerminated(waitTime);
    }

    @Override
    public void connect() throws InterceptorException, TransportException, IOException {
        // set expected connection state to true
        shouldBeConnected.set(true);
        // see if device using the provided epr address is available
        LOG.info("Starting discovery for {}", targetEpr);
        final SettableFuture<List<String>> xAddrs = SettableFuture.create();
        final DiscoveryObserver obs = new DiscoveryObserver() {
            @SuppressFBWarnings(
                    value = {"UMAC_UNCALLABLE_METHOD_OF_ANONYMOUS_CLASS"},
                    justification = "This is not uncallable")
            @Subscribe
            void deviceFound(final ProbedDeviceFoundMessage message) {
                final DiscoveredDevice payload = message.getPayload();
                if (payload.getEprAddress().equals(targetEpr)) {
                    LOG.info("Found device with epr {}", payload.getEprAddress());
                    xAddrs.set(payload.getXAddrs());
                } else {
                    LOG.info("Found non-matching device with epr {}", payload.getEprAddress());
                }
            }
        };
        client.registerDiscoveryObserver(obs);

        // filter discovery for SDC devices only
        final SdcDiscoveryFilterBuilder discoveryFilterBuilder = SdcDiscoveryFilterBuilder.create();
        client.probe(discoveryFilterBuilder.get());

        try {
            targetXAddrs = xAddrs.get(MAX_WAIT.toSeconds(), TimeUnit.SECONDS);
        } catch (final InterruptedException | TimeoutException | ExecutionException e) {
            LOG.error("Couldn't find target with EPR {}", targetEpr, e);
            throw new IOException(COULDN_T_CONNECT_TO_TARGET);
        } finally {
            client.unregisterDiscoveryObserver(obs);
        }

        LOG.info("Connecting to {}", targetEpr);
        final var hostingServiceFuture = client.connect(targetEpr);

        hostingServiceProxy = null;
        try {
            hostingServiceProxy = hostingServiceFuture.get(MAX_WAIT.toSeconds(), TimeUnit.SECONDS);
        } catch (final InterruptedException | TimeoutException | ExecutionException e) {
            LOG.error("Couldn't connect to EPR {}", targetEpr, e);
            throw new IOException(COULDN_T_CONNECT_TO_TARGET);
        }

        LOG.info("Attaching to remote mdib and subscriptions for {}", targetEpr);
        final ListenableFuture<SdcRemoteDevice> remoteDeviceFuture;
        sdcRemoteDevice = null;
        try {
            remoteDeviceFuture = connector.connect(
                    hostingServiceProxy,
                    ConnectConfiguration.create(ConnectConfiguration.ALL_EPISODIC_AND_WAVEFORM_REPORTS));
            sdcRemoteDevice = remoteDeviceFuture.get(MAX_WAIT.toSeconds(), TimeUnit.SECONDS);
        } catch (final PrerequisitesException | InterruptedException | ExecutionException | TimeoutException e) {
            LOG.error("Couldn't attach to remote mdib and subscriptions for {}", targetEpr, e);
            throw new IOException("Couldn't attach to remote mdib and subscriptions");
        }

        sdcRemoteDevice.registerWatchdogObserver(this);
    }

    @Override
    public synchronized void disconnect() throws TimeoutException {
        shouldBeConnected.set(false);
        if (sdcRemoteDevice != null) {
            sdcRemoteDevice.stopAsync().awaitTerminated(MAX_WAIT.toSeconds(), TimeUnit.SECONDS);
            sdcRemoteDevice = null;
        }
        hostingServiceProxy = null;
        client.stopAsync().awaitTerminated(MAX_WAIT.toSeconds(), TimeUnit.SECONDS);
    }

    @Override
    public Client getClient() {
        return client;
    }

    @Override
    public SdcRemoteDevicesConnector getConnector() {
        return connector;
    }

    @Override
    public SdcRemoteDevice getSdcRemoteDevice() {
        return sdcRemoteDevice;
    }

    @Override
    public HostingServiceProxy getHostingServiceProxy() {
        return hostingServiceProxy;
    }

    @Override
    public String getTargetEpr() {
        return targetEpr;
    }

    @Override
    public List<String> getTargetXAddrs() {
        return targetXAddrs;
    }

    @Override
    public Injector getInjector() {
        return injector;
    }

    @Subscribe
    void onConnectionLoss(final WatchdogMessage watchdogMessage) {
        LOG.info("Watchdog detected disconnect from provider.");
        if (shouldBeConnected.get()) {
            testRunObserver.invalidateTestRun(String.format(
                    "Lost connection to device %s. Reason: %s",
                    watchdogMessage.getPayload(), watchdogMessage.getReason().getMessage()));
            try {
                disconnect();
            } catch (TimeoutException e) {
                LOG.error("Error while disconnecting device after connection loss.", e);
            }
        } else {
            LOG.info("Watchdog detected expected disconnect from provider.");
        }
    }
}
