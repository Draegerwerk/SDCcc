/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2023-2024 Draegerwerk AG & Co. KGaA.
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
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.somda.sdc.common.util.ExecutorWrapperService;
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

    private static final String COULDN_T_CONNECT_TO_TARGET = "Couldn't connect to target";
    private static final String COULDN_T_DISCONNECT = "Could not disconnect the test client";

    private static final String LOCATION_CONTEXT_SCOPE_STRING_START = "sdc.ctxt.loc:";
    private static final String MATCHING = "matching";
    private static final String NON_MATCHING = "non-matching";
    private final Pattern locExtractionPattern = Pattern.compile("^sdc.ctxt.loc:/.*\\?"
            + "(?=(.*fac=(?<fac>[^&]*))?)"
            + "(?=(.*bldng=(?<bldng>[^&]*))?)"
            + "(?=(.*poc=(?<poc>[^&]*))?)"
            + "(?=(.*flr=(?<flr>[^&]*))?)"
            + "(?=(.*rm=(?<rm>[^&]*))?)"
            + "(?=(.*bed=(?<bed>[^&]*))?)"
            + ".*$");

    // max time to wait for futures
    private final Duration maxWait;
    private final String targetDeviceFacility;
    private final String targetDeviceBuilding;
    private final String targetDevicePointOfCare;
    private final String targetDeviceFloor;
    private final String targetDeviceRoom;
    private final String targetDeviceBed;
    private final Injector injector;
    private final String targetEpr;
    private final NetworkInterface networkInterface;
    private final Client client;
    private final SdcRemoteDevicesConnector connector;
    private final TestRunObserver testRunObserver;
    // tracks the expected connection state
    private final AtomicBoolean shouldBeConnected;
    private final String eprSearchLogString;
    private final String facilitySearchLogString;
    private final String buildingSearchLogString;
    private final String pointOfCareSearchLogString;
    private final String floorSearchLogString;
    private final String roomSearchLogString;
    private final String bedSearchLogString;
    private final TestClientMdibAccessObserver testClientMdibAccessObserver;
    private DpwsFramework dpwsFramework;
    private SdcRemoteDevice sdcRemoteDevice;
    private HostingServiceProxy hostingServiceProxy;
    private List<String> targetXAddrs;

    private final ExecutorWrapperService<ExecutorService> reconnectExecutor;
    private final AtomicBoolean isConnected;
    private final Object lock;
    private final long reconnectTries;
    private final long reconnectWait;
    private final AtomicBoolean reconnectEnabled;
    private final AtomicBoolean inReconnectProcess;

    /**
     * Creates an SDCri consumer instance.
     *
     * @param targetDeviceEpr              EPR address to filter for
     * @param targetDeviceFacility         facility to filter for
     * @param targetDeviceBuilding         building to filter for
     * @param targetDevicePointOfCare      point of care to filter for
     * @param targetDeviceFloor            floor to filter for
     * @param targetDeviceRoom             room to filter for
     * @param targetDeviceBed              bed to filter for
     * @param adapterAddress               ip of the network interface to bind to
     * @param maxWait                      max waiting time to find and connect to target device
     * @param reconnectTries               number of tries a reconnection is attempted
     * @param reconnectWait                the wait time between reconnection attempts in seconds
     * @param testClientUtil               test client utility
     * @param testRunObserver              observer for invalidating test runs on unexpected errors
     * @param testClientMdibAccessObserver observer for changes to the mdib
     */
    @Inject
    public TestClientImpl(
            @Named(TestSuiteConfig.CONSUMER_DEVICE_EPR) final @Nullable String targetDeviceEpr,
            @Named(TestSuiteConfig.CONSUMER_DEVICE_LOCATION_FACILITY) final @Nullable String targetDeviceFacility,
            @Named(TestSuiteConfig.CONSUMER_DEVICE_LOCATION_BUILDING) final @Nullable String targetDeviceBuilding,
            @Named(TestSuiteConfig.CONSUMER_DEVICE_LOCATION_POINT_OF_CARE)
                    final @Nullable String targetDevicePointOfCare,
            @Named(TestSuiteConfig.CONSUMER_DEVICE_LOCATION_FLOOR) final @Nullable String targetDeviceFloor,
            @Named(TestSuiteConfig.CONSUMER_DEVICE_LOCATION_ROOM) final @Nullable String targetDeviceRoom,
            @Named(TestSuiteConfig.CONSUMER_DEVICE_LOCATION_BED) final @Nullable String targetDeviceBed,
            @Named(TestSuiteConfig.NETWORK_INTERFACE_ADDRESS) final String adapterAddress,
            @Named(TestSuiteConfig.NETWORK_MAX_WAIT) final long maxWait,
            @Named(TestSuiteConfig.NETWORK_RECONNECT_TRIES) final long reconnectTries,
            @Named(TestSuiteConfig.NETWORK_RECONNECT_WAIT) final long reconnectWait,
            final TestClientUtil testClientUtil,
            final TestRunObserver testRunObserver,
            final TestClientMdibAccessObserver testClientMdibAccessObserver) {
        this.injector = testClientUtil.getInjector();
        this.client = injector.getInstance(Client.class);
        this.connector = injector.getInstance(SdcRemoteDevicesConnector.class);
        this.testRunObserver = testRunObserver;
        this.shouldBeConnected = new AtomicBoolean(false);
        this.isConnected = new AtomicBoolean(false);
        this.reconnectEnabled = new AtomicBoolean(false);
        this.inReconnectProcess = new AtomicBoolean(false);
        this.lock = new Object();
        this.reconnectTries = reconnectTries;
        this.reconnectWait = reconnectWait;
        this.maxWait = Duration.ofSeconds(maxWait);
        this.testClientMdibAccessObserver = testClientMdibAccessObserver;

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

        this.targetEpr = targetDeviceEpr;
        this.targetDeviceFacility = targetDeviceFacility;
        this.targetDeviceBuilding = targetDeviceBuilding;
        this.targetDevicePointOfCare = targetDevicePointOfCare;
        this.targetDeviceFloor = targetDeviceFloor;
        this.targetDeviceRoom = targetDeviceRoom;
        this.targetDeviceBed = targetDeviceBed;

        this.eprSearchLogString = this.targetEpr == null ? "any epr" : "the epr \"" + this.targetEpr + "\"";
        this.facilitySearchLogString = this.targetDeviceFacility == null
                ? "any facility"
                : "the facility \"" + this.targetDeviceFacility + "\"";
        this.buildingSearchLogString = this.targetDeviceBuilding == null
                ? "any building"
                : "the building \"" + this.targetDeviceBuilding + "\"";
        this.pointOfCareSearchLogString = this.targetDevicePointOfCare == null
                ? "any point of care"
                : "the point of care \"" + this.targetDevicePointOfCare + "\"";
        this.floorSearchLogString =
                this.targetDeviceFloor == null ? "any floor" : "the floor \"" + this.targetDeviceFloor + "\"";
        this.roomSearchLogString =
                this.targetDeviceRoom == null ? "any room" : "the room \"" + this.targetDeviceRoom + "\"";
        this.bedSearchLogString = this.targetDeviceBed == null ? "any bed" : "the bed \"" + this.targetDeviceBed + "\"";

        LOG.info(
                "Configured to search for a device with {}, {}, {}, {}, {}, {} and {}",
                this.eprSearchLogString,
                this.facilitySearchLogString,
                this.buildingSearchLogString,
                this.pointOfCareSearchLogString,
                this.floorSearchLogString,
                this.roomSearchLogString,
                this.bedSearchLogString);
        reconnectExecutor = new ExecutorWrapperService<>(
                Executors::newSingleThreadExecutor, "TestClientExecutor", "InstanceIdentifier");
    }

    @Override
    protected void startUp() {
        // provide the name of your network adapter
        this.dpwsFramework = injector.getInstance(DpwsFramework.class);
        this.dpwsFramework.setNetworkInterface(networkInterface);
        dpwsFramework.startAsync().awaitRunning();
        client.startAsync().awaitRunning();
        reconnectExecutor.startAsync().awaitRunning();
    }

    @Override
    protected void shutDown() {
        reconnectExecutor.stopAsync().awaitTerminated();
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

    private static void logDeviceFound(final String type, final DiscoveredDevice payload) {
        LOG.info(
                "Found {} device with epr {} and location context scope(s) {}",
                type,
                payload.getEprAddress(),
                payload.getScopes().stream()
                        .filter(scope -> scope.startsWith(LOCATION_CONTEXT_SCOPE_STRING_START))
                        .toList()
                        .toString());
    }

    @Override
    public void connect() throws InterceptorException, TransportException, IOException {
        // set expected connection state to true
        shouldBeConnected.set(true);

        LOG.info(
                "Starting discovery for a device with {}, {}, {}, {}, {}, {} and {}",
                this.eprSearchLogString,
                this.facilitySearchLogString,
                this.buildingSearchLogString,
                this.pointOfCareSearchLogString,
                this.floorSearchLogString,
                this.roomSearchLogString,
                this.bedSearchLogString);

        final SettableFuture<DiscoveredDevice> discoveredDeviceSettableFuture = SettableFuture.create();
        final DiscoveryObserver obs = new DiscoveryObserver() {
            @SuppressFBWarnings(
                    value = {"UMAC_UNCALLABLE_METHOD_OF_ANONYMOUS_CLASS"},
                    justification = "This is not uncallable")
            @Subscribe
            void deviceFound(final ProbedDeviceFoundMessage message) {
                final DiscoveredDevice payload = message.getPayload();

                if (targetDeviceFacility == null
                        && targetDeviceBuilding == null
                        && targetDevicePointOfCare == null
                        && targetDeviceFloor == null
                        && targetDeviceRoom == null
                        && targetDeviceBed == null) {
                    if (targetEpr == null || Objects.equals(payload.getEprAddress(), targetEpr)) {
                        logDeviceFound(MATCHING, payload);
                        discoveredDeviceSettableFuture.set(payload);
                    } else {
                        logDeviceFound(NON_MATCHING, payload);
                    }
                    return;
                }

                for (final String scope : payload.getScopes()) {
                    if (scope.startsWith("sdc.ctxt.loc:")) {
                        final Matcher matcher = locExtractionPattern.matcher(scope);

                        if (matcher.matches()) {
                            if ((targetDeviceFacility == null
                                            || Objects.equals(matcher.group("fac"), targetDeviceFacility))
                                    && (targetDeviceBuilding == null
                                            || Objects.equals(matcher.group("bldng"), targetDeviceBuilding))
                                    && (targetDevicePointOfCare == null
                                            || Objects.equals(matcher.group("poc"), targetDevicePointOfCare))
                                    && (targetDeviceFloor == null
                                            || Objects.equals(matcher.group("flr"), targetDeviceFloor))
                                    && (targetDeviceRoom == null
                                            || Objects.equals(matcher.group("rm"), targetDeviceRoom))
                                    && (targetDeviceBed == null
                                            || Objects.equals(matcher.group("bed"), targetDeviceBed))
                                    && (targetEpr == null || Objects.equals(payload.getEprAddress(), targetEpr))) {

                                logDeviceFound(MATCHING, payload);
                                discoveredDeviceSettableFuture.set(payload);
                                return;
                            }
                        } else {
                            LOG.error("The location context scope {} could not be parsed", scope);
                        }
                    }
                }

                logDeviceFound(NON_MATCHING, payload);
            }
        };
        client.registerDiscoveryObserver(obs);

        // filter discovery for SDC devices only
        final SdcDiscoveryFilterBuilder discoveryFilterBuilder = SdcDiscoveryFilterBuilder.create();
        client.probe(discoveryFilterBuilder.get());

        final DiscoveredDevice discoveredDevice;
        try {
            discoveredDevice = discoveredDeviceSettableFuture.get(maxWait.toSeconds(), TimeUnit.SECONDS);
            targetXAddrs = discoveredDevice.getXAddrs();

            if (discoveredDevice.getEprAddress() == null) {
                LOG.error("No EPR available for the discoveredDevice object, "
                        + "connections to devices without an EPR are not supported/implemented");
                throw new IOException(COULDN_T_CONNECT_TO_TARGET);
            }
        } catch (final InterruptedException | TimeoutException | ExecutionException e) {
            if (inReconnectProcess.get()) {
                LOG.info(
                        "Tried to reconnect, but couldn't find a device with {}, {}, {}, {}, {}, {} and {}",
                        this.eprSearchLogString,
                        this.facilitySearchLogString,
                        this.buildingSearchLogString,
                        this.pointOfCareSearchLogString,
                        this.floorSearchLogString,
                        this.roomSearchLogString,
                        this.bedSearchLogString,
                        e);
            } else {
                LOG.error(
                        "Couldn't find a device with {}, {}, {}, {}, {}, {} and {}",
                        this.eprSearchLogString,
                        this.facilitySearchLogString,
                        this.buildingSearchLogString,
                        this.pointOfCareSearchLogString,
                        this.floorSearchLogString,
                        this.roomSearchLogString,
                        this.bedSearchLogString,
                        e);
            }
            throw new IOException(COULDN_T_CONNECT_TO_TARGET);
        } finally {
            client.unregisterDiscoveryObserver(obs);
        }

        LOG.info("Connecting to {}", discoveredDevice.getEprAddress());
        final var hostingServiceFuture = client.connect(discoveredDevice.getEprAddress());

        hostingServiceProxy = null;
        try {
            hostingServiceProxy = hostingServiceFuture.get(maxWait.toSeconds(), TimeUnit.SECONDS);
        } catch (final InterruptedException | TimeoutException | ExecutionException e) {
            LOG.error("Couldn't connect to EPR {}", discoveredDevice.getEprAddress(), e);
            throw new IOException(COULDN_T_CONNECT_TO_TARGET);
        }

        LOG.info("Attaching to remote mdib and subscriptions for {}", discoveredDevice.getEprAddress());
        final ListenableFuture<SdcRemoteDevice> remoteDeviceFuture;
        sdcRemoteDevice = null;
        try {
            remoteDeviceFuture = connector.connect(
                    hostingServiceProxy,
                    ConnectConfiguration.create(ConnectConfiguration.ALL_EPISODIC_AND_WAVEFORM_REPORTS),
                    testClientMdibAccessObserver);
            sdcRemoteDevice = remoteDeviceFuture.get(maxWait.toSeconds(), TimeUnit.SECONDS);
        } catch (final PrerequisitesException | InterruptedException | ExecutionException | TimeoutException e) {
            LOG.error("Couldn't attach to remote mdib and subscriptions for {}", discoveredDevice.getEprAddress(), e);
            throw new IOException("Couldn't attach to remote mdib and subscriptions");
        }

        sdcRemoteDevice.registerWatchdogObserver(this);
        isConnected.set(true);
    }

    @Override
    public void shouldReconnect(final Boolean shouldReconnect) {
        this.reconnectEnabled.set(shouldReconnect);
    }

    @Override
    public synchronized void disconnect() throws TimeoutException {
        disconnect(true);
    }

    private synchronized void disconnect(final Boolean expected) throws TimeoutException {
        shouldBeConnected.set(expected);
        if (sdcRemoteDevice != null) {
            sdcRemoteDevice.stopAsync().awaitTerminated(maxWait.toSeconds(), TimeUnit.SECONDS);
            sdcRemoteDevice = null;
        }
        hostingServiceProxy = null;
        if (expected) {
            client.stopAsync().awaitTerminated(maxWait.toSeconds(), TimeUnit.SECONDS);
        }
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
        return restrictedGetter(this::getActualSdcRemoteDevice);
    }

    private SdcRemoteDevice getActualSdcRemoteDevice() {
        return sdcRemoteDevice;
    }

    @Override
    public HostingServiceProxy getHostingServiceProxy() {
        return restrictedGetter(this::getActualHostingServiceProxy);
    }

    private HostingServiceProxy getActualHostingServiceProxy() {
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
        if (shouldBeConnected.get() && reconnectEnabled.get()) {
            isConnected.set(false);
            inReconnectProcess.set(true);
            LOG.info("The disconnect from provider was unexpected, trying to reconnect.");
            if (reconnectExecutor.isRunning()) {
                reconnectExecutor.get().submit(() -> {
                    synchronized (lock) {
                        try {
                            disconnect(false);
                        } catch (TimeoutException e) {
                            LOG.error(COULDN_T_DISCONNECT, e);
                        }
                        reconnect(watchdogMessage);
                        inReconnectProcess.set(false);
                        lock.notifyAll();
                    }
                });
            } else {
                LOG.debug("ReconnectExecutor is not running.");
                invalidateAfterConnectionLoss(watchdogMessage);
            }
        } else if (shouldBeConnected.get()) {
            invalidateAfterConnectionLoss(watchdogMessage);
        } else {
            LOG.info("The disconnect from provider was expected.");
        }
    }

    @Override
    public void registerMdibObserver(final TestClientMdibObserver observer) {
        testClientMdibAccessObserver.registerObserver(observer);
    }

    @Override
    public void unregisterMdibObserver(final TestClientMdibObserver observer) {
        testClientMdibAccessObserver.unregisterObserver(observer);
    }

    private <T> T restrictedGetter(final RestrictedGetter<T> getter) {
        synchronized (lock) {
            while (inReconnectProcess.get()) {
                LOG.debug("Attempted to access a connection-dependent object while the connection is interrupted, "
                        + "wait for connection to be re-established.");
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    LOG.error("Waiting for lock interrupted.", e);
                }
            }
            return getter.invoke();
        }
    }

    private void reconnect(final WatchdogMessage watchdogMessage) {
        var count = 1;
        while (count <= reconnectTries && !isConnected.get()) {
            LOG.info("Trying to reconnect, attempt {} of {}.", count, reconnectTries);
            try {
                connect();
                LOG.info("Successfully reconnected.");
                return;
            } catch (InterceptorException | TransportException | IOException e) {
                LOG.info("{}. reconnection attempt failed.", count);
                try {
                    LOG.info("Wait for {} seconds, to give the provider time to restart.", reconnectWait);
                    TimeUnit.SECONDS.sleep(reconnectWait);
                } catch (InterruptedException ex) {
                    throw new RuntimeException("Error while trying to wait for the provider to restart.", ex);
                }
            }
            count++;
        }
        if (!isConnected.get()) {
            LOG.info("Couldn't reconnect after {} retries", reconnectTries);
            invalidateAfterConnectionLoss(watchdogMessage);
        }
    }

    private void invalidateAfterConnectionLoss(final WatchdogMessage watchdogMessage) {
        testRunObserver.invalidateTestRun(String.format(
                "Lost connection to device %s. Reason: %s",
                watchdogMessage.getPayload(), watchdogMessage.getReason().getMessage()));
        try {
            disconnect();
        } catch (TimeoutException e) {
            LOG.error(COULDN_T_DISCONNECT, e);
        }
    }

    private interface RestrictedGetter<T> {
        T invoke();
    }
}
