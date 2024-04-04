/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2023 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.sdcri.testprovider;

import com.draeger.medical.sdccc.configuration.TestSuiteConfig;
import com.google.common.util.concurrent.AbstractIdleService;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.name.Named;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.somda.sdc.biceps.model.participant.Mdib;
import org.somda.sdc.biceps.provider.access.LocalMdibAccess;
import org.somda.sdc.biceps.provider.access.factory.LocalMdibAccessFactory;
import org.somda.sdc.dpws.DpwsFramework;
import org.somda.sdc.dpws.device.DeviceSettings;
import org.somda.sdc.dpws.soap.wsaddressing.WsAddressingUtil;
import org.somda.sdc.dpws.soap.wsaddressing.model.EndpointReferenceType;
import org.somda.sdc.dpws.soap.wseventing.SubscriptionManager;
import org.somda.sdc.glue.common.MdibXmlIo;
import org.somda.sdc.glue.common.factory.ModificationsBuilderFactory;
import org.somda.sdc.glue.provider.SdcDevice;
import org.somda.sdc.glue.provider.factory.SdcDeviceFactory;
import org.somda.sdc.glue.provider.sco.OperationInvocationReceiver;

/**
 * SDCri provider used to test SDC consumers.
 */
public class TestProviderImpl extends AbstractIdleService implements TestProvider {
    private static final Logger LOG = LogManager.getLogger();

    private final Injector injector;
    private final NetworkInterface networkInterface;
    private final DpwsFramework dpwsFramework;
    private final LocalMdibAccess mdibAccess;
    private final SdcDevice sdcDevice;
    private final Mdib mdib;

    @Inject
    TestProviderImpl(
            @Assisted final InputStream mdibAsStream,
            @Named(TestSuiteConfig.PROVIDER_DEVICE_EPR) final String providerEpr,
            @Named(TestSuiteConfig.NETWORK_INTERFACE_ADDRESS) final String adapterAddress,
            final TestProviderUtil testProviderUtil) {
        this.injector = testProviderUtil.getInjector();
        final MdibXmlIo mdibXmlIo = injector.getInstance(MdibXmlIo.class);

        try {
            this.mdib = mdibXmlIo.readMdib(mdibAsStream);
        } catch (final jakarta.xml.bind.JAXBException e) {
            throw new RuntimeException(e);
        }

        try {
            this.networkInterface = NetworkInterface.getByInetAddress(Inet4Address.getByName(adapterAddress));
        } catch (final SocketException | UnknownHostException e) {
            LOG.error("Error while retrieving network adapter with address {}", adapterAddress, e);
            throw new RuntimeException(e);
        }

        if (this.networkInterface == null) {
            LOG.error(
                    "Error while setting network interface, adapter for address {} seems unavailable", adapterAddress);
            throw new RuntimeException("Error while setting network interface, adapter seems unavailable");
        }

        this.dpwsFramework = injector.getInstance(DpwsFramework.class);
        this.dpwsFramework.setNetworkInterface(networkInterface);
        this.mdibAccess = injector.getInstance(LocalMdibAccessFactory.class).createLocalMdibAccess();

        this.sdcDevice = injector.getInstance(SdcDeviceFactory.class)
                .createSdcDevice(
                        new DeviceSettings() {
                            @Override
                            public EndpointReferenceType getEndpointReference() {
                                return injector.getInstance(WsAddressingUtil.class)
                                        .createEprWithAddress(providerEpr);
                            }

                            @Override
                            public NetworkInterface getNetworkInterface() {
                                return networkInterface;
                            }
                        },
                        this.mdibAccess,
                        new OperationInvocationReceiver() {},
                        List.of(injector.getInstance(TestProviderHostingServicePlugin.class)));
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
    public SdcDevice getSdcDevice() {
        return sdcDevice;
    }

    public Mdib getMdib() {
        return mdib;
    }

    @Override
    public Injector getInjector() {
        return injector;
    }

    @Override
    protected void startUp() throws Exception {
        final var modificationsBuilderFactory = injector.getInstance(ModificationsBuilderFactory.class);
        final var modifications =
                modificationsBuilderFactory.createModificationsBuilder(mdib).get();
        mdibAccess.writeDescription(modifications);

        dpwsFramework.startAsync().awaitRunning();
        sdcDevice.startAsync().awaitRunning();
    }

    @Override
    protected void shutDown() {
        sdcDevice.stopAsync().awaitTerminated();
        dpwsFramework.stopAsync().awaitTerminated();
        sdcDevice.stopAsync().awaitTerminated();
    }

    @Override
    public Map<String, SubscriptionManager> getActiveSubscriptions() {
        return sdcDevice.getActiveSubscriptions();
    }
}
