/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2023, 2024 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.sdcri.testclient;

import com.draeger.medical.sdccc.configuration.TestSuiteConfig;
import com.draeger.medical.sdccc.messages.MessageStorage;
import com.draeger.medical.sdccc.sdcri.CommunicationLogMessageStorage;
import com.draeger.medical.sdccc.tests.util.MdibHistorian;
import com.draeger.medical.sdccc.tests.util.guice.MdibHistorianFactory;
import com.draeger.medical.sdccc.util.Constants;
import com.draeger.medical.sdccc.util.TestRunObserver;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.inject.name.Named;
import com.google.inject.util.Modules;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.HostnameVerifier;
import org.somda.sdc.biceps.guice.DefaultBicepsConfigModule;
import org.somda.sdc.biceps.guice.DefaultBicepsModule;
import org.somda.sdc.common.CommonConfig;
import org.somda.sdc.common.guice.AbstractConfigurationModule;
import org.somda.sdc.common.guice.DefaultCommonConfigModule;
import org.somda.sdc.common.guice.DefaultCommonModule;
import org.somda.sdc.common.util.ExecutorWrapperService;
import org.somda.sdc.dpws.CommunicationLog;
import org.somda.sdc.dpws.CommunicationLogImpl;
import org.somda.sdc.dpws.CommunicationLogSink;
import org.somda.sdc.dpws.DpwsConfig;
import org.somda.sdc.dpws.crypto.CryptoConfig;
import org.somda.sdc.dpws.crypto.CryptoSettings;
import org.somda.sdc.dpws.factory.CommunicationLogFactory;
import org.somda.sdc.dpws.guice.DefaultDpwsModule;
import org.somda.sdc.dpws.guice.NetworkJobThreadPool;
import org.somda.sdc.dpws.guice.ResolverThreadPool;
import org.somda.sdc.dpws.guice.WsDiscovery;
import org.somda.sdc.dpws.network.LocalAddressResolver;
import org.somda.sdc.glue.consumer.ConsumerConfig;
import org.somda.sdc.glue.guice.Consumer;
import org.somda.sdc.glue.guice.DefaultGlueConfigModule;
import org.somda.sdc.glue.guice.DefaultGlueModule;
import org.somda.sdc.glue.guice.GlueDpwsConfigModule;
import org.somda.sdc.glue.guice.WatchdogScheduledExecutor;

/**
 * Utility for a {@linkplain TestClient} instance.
 */
public class TestClientUtil {
    private static final String SDCCC_PREFIX = "sdccc";
    private static final String THREAD_NAME_FORMAT = "-thread-%d";

    private static final String NETWORK_THREAD_POOL_NAME = SDCCC_PREFIX + "NetworkThreadPool";
    private static final String WS_DISCOVERY_NAME = SDCCC_PREFIX + "WsDiscovery";
    private static final String RESOLVER_THREAD_POOL_NAME = SDCCC_PREFIX + "ResolverThreadPool";
    private static final String CONSUMER_NAME = SDCCC_PREFIX + "Consumer";
    private static final String WATCHDOG_SCHEDULED_EXECUTOR_NAME = SDCCC_PREFIX + "WatchdogScheduledExecutor";

    public static final String NETWORK_THREAD_POOL_NAME_FORMAT = NETWORK_THREAD_POOL_NAME + THREAD_NAME_FORMAT;
    public static final String WS_DISCOVERY_NAME_FORMAT = WS_DISCOVERY_NAME + THREAD_NAME_FORMAT;
    public static final String RESOLVER_THREAD_POOL_NAME_FORMAT = RESOLVER_THREAD_POOL_NAME + THREAD_NAME_FORMAT;
    public static final String CONSUMER_NAME_FORMAT = CONSUMER_NAME + THREAD_NAME_FORMAT;
    public static final String WATCHDOG_SCHEDULED_EXECUTOR_NAME_FORMAT =
            WATCHDOG_SCHEDULED_EXECUTOR_NAME + THREAD_NAME_FORMAT;

    private final Injector injector;

    private ExecutorWrapperService<ListeningExecutorService> networkJobThreadPoolExecutor;
    private ExecutorWrapperService<ListeningExecutorService> wsDiscoveryExecutor;
    private ExecutorWrapperService<ListeningExecutorService> resolveExecutor;
    private ExecutorWrapperService<ListeningExecutorService> consumerExecutor;
    private ExecutorWrapperService<ScheduledExecutorService> watchdogScheduledExecutor;

    /**
     * Creates a utility instance which prepares the injector for the client.
     *
     * @param cryptoSettings                 crypto setting
     * @param communicationLogMessageStorage connector to the {@linkplain MessageStorage} to write to
     * @param testRunObserver                observer for invalidating test runs on unexpected errors
     * @param localAddressResolver           resolver for getting the local address to use
     * @param multicastTTL                   TTL for multicast packets used in Discovery.
     *                                       Values from 1 to 255 are valid.
     * @param enabledTlsProtocols            TLS protocol versions to be enabled
     * @param enabledCiphers                 ciphers to be enabled
     * @param configurationModule            configuration for AbstractConfigurationModule
     */
    @Inject
    public TestClientUtil(
            final CryptoSettings cryptoSettings,
            final CommunicationLogMessageStorage communicationLogMessageStorage,
            final TestRunObserver testRunObserver,
            final LocalAddressResolver localAddressResolver,
            @Named(TestSuiteConfig.NETWORK_MULTICAST_TTL) final Long multicastTTL,
            @Named(TestSuiteConfig.TLS_ENABLED_PROTOCOLS) final String[] enabledTlsProtocols,
            @Named(TestSuiteConfig.TLS_ENABLED_CIPHERS) final String[] enabledCiphers,
            @Named(Constants.CONFIGURATION_MODULE) final AbstractConfigurationModule configurationModule) {

        injector = createClientInjector(List.of(
                new AbstractConfigurationModule() {
                    @Override
                    protected void defaultConfigure() {
                        bind(CryptoConfig.CRYPTO_SETTINGS, CryptoSettings.class, cryptoSettings);
                        bind(CryptoConfig.CRYPTO_TLS_ENABLED_VERSIONS, String[].class, enabledTlsProtocols);
                        bind(CryptoConfig.CRYPTO_TLS_ENABLED_CIPHERS, String[].class, enabledCiphers);
                        bind(
                                CryptoConfig.CRYPTO_CLIENT_HOSTNAME_VERIFIER,
                                HostnameVerifier.class,
                                (hostname, session) -> true);
                        bind(DpwsConfig.HTTPS_SUPPORT, Boolean.class, true);
                        bind(DpwsConfig.HTTP_SUPPORT, Boolean.class, false);
                        bind(DpwsConfig.MULTICAST_TTL, Integer.class, multicastTTL.intValue());
                    }
                },
                new AbstractModule() {
                    @Override
                    protected void configure() {
                        install(new FactoryModuleBuilder()
                                .implement(CommunicationLog.class, CommunicationLogImpl.class)
                                .build(CommunicationLogFactory.class));
                        bind(CommunicationLogSink.class).toInstance(communicationLogMessageStorage);
                        bind(TestRunObserver.class).toInstance(testRunObserver);
                        bind(LocalAddressResolver.class).toInstance(localAddressResolver);
                    }
                },
                new AbstractModule() {

                    @Provides
                    @NetworkJobThreadPool
                    ExecutorWrapperService<ListeningExecutorService> getNetworkJobThreadPool(
                            final @Named(CommonConfig.INSTANCE_IDENTIFIER) String frameworkIdentifier) {
                        if (networkJobThreadPoolExecutor == null) {
                            final Callable<ListeningExecutorService> executor =
                                    () -> MoreExecutors.listeningDecorator(new ThreadPoolExecutor(
                                            10,
                                            50,
                                            60L,
                                            TimeUnit.SECONDS,
                                            new LinkedBlockingQueue<>(),
                                            new ThreadFactoryBuilder()
                                                    .setNameFormat(NETWORK_THREAD_POOL_NAME_FORMAT)
                                                    .setDaemon(true)
                                                    .build()));
                            networkJobThreadPoolExecutor = new ExecutorWrapperService<>(
                                    executor, NETWORK_THREAD_POOL_NAME, frameworkIdentifier);
                        }

                        return networkJobThreadPoolExecutor;
                    }

                    @Provides
                    @WsDiscovery
                    ExecutorWrapperService<ListeningExecutorService> getWsDiscoveryExecutor(
                            final @Named(CommonConfig.INSTANCE_IDENTIFIER) String frameworkIdentifier) {
                        if (wsDiscoveryExecutor == null) {
                            final Callable<ListeningExecutorService> executor =
                                    () -> MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(
                                            10,
                                            new ThreadFactoryBuilder()
                                                    .setNameFormat(WS_DISCOVERY_NAME_FORMAT)
                                                    .setDaemon(true)
                                                    .build()));

                            wsDiscoveryExecutor =
                                    new ExecutorWrapperService<>(executor, WS_DISCOVERY_NAME, frameworkIdentifier);
                        }

                        return wsDiscoveryExecutor;
                    }

                    @Provides
                    @ResolverThreadPool
                    ExecutorWrapperService<ListeningExecutorService> getResolverThreadPool(
                            final @Named(CommonConfig.INSTANCE_IDENTIFIER) String frameworkIdentifier) {
                        if (resolveExecutor == null) {
                            final Callable<ListeningExecutorService> executor =
                                    () -> MoreExecutors.listeningDecorator(new ThreadPoolExecutor(
                                            10,
                                            50,
                                            60L,
                                            TimeUnit.SECONDS,
                                            new LinkedBlockingQueue<>(),
                                            new ThreadFactoryBuilder()
                                                    .setNameFormat(RESOLVER_THREAD_POOL_NAME_FORMAT)
                                                    .setDaemon(true)
                                                    .build()));
                            resolveExecutor = new ExecutorWrapperService<>(
                                    executor, RESOLVER_THREAD_POOL_NAME, frameworkIdentifier);
                        }

                        return resolveExecutor;
                    }

                    @Provides
                    @Consumer
                    ExecutorWrapperService<ListeningExecutorService> getConsumerExecutor(
                            final @Named(CommonConfig.INSTANCE_IDENTIFIER) String frameworkIdentifier) {
                        if (consumerExecutor == null) {
                            final Callable<ListeningExecutorService> executor =
                                    () -> MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(
                                            10,
                                            new ThreadFactoryBuilder()
                                                    .setNameFormat(CONSUMER_NAME_FORMAT)
                                                    .setDaemon(true)
                                                    .build()));
                            consumerExecutor =
                                    new ExecutorWrapperService<>(executor, CONSUMER_NAME, frameworkIdentifier);
                        }
                        return consumerExecutor;
                    }

                    @Provides
                    @WatchdogScheduledExecutor
                    ExecutorWrapperService<ScheduledExecutorService> getWatchdogScheduledExecutor(
                            final @Named(CommonConfig.INSTANCE_IDENTIFIER) String frameworkIdentifier) {
                        if (watchdogScheduledExecutor == null) {
                            final Callable<ScheduledExecutorService> executor = () -> Executors.newScheduledThreadPool(
                                    10,
                                    new ThreadFactoryBuilder()
                                            .setNameFormat(WATCHDOG_SCHEDULED_EXECUTOR_NAME_FORMAT)
                                            .setDaemon(true)
                                            .build());

                            watchdogScheduledExecutor = new ExecutorWrapperService<>(
                                    executor, WATCHDOG_SCHEDULED_EXECUTOR_NAME, frameworkIdentifier);
                        }

                        return watchdogScheduledExecutor;
                    }
                },
                configurationModule));
    }

    /**
     * Creates a client injector for use in test cases and unit tests alike to ensure same configuration,
     * except for needed overrides.
     *
     * @param overrides modules used for overriding bindings and installs
     * @return injector created from the default config and the overrides
     */
    public static Injector createClientInjector(final AbstractModule... overrides) {
        final var overrideList = new ArrayList<>(Arrays.asList(overrides));

        return createClientInjector(overrideList);
    }

    private static Injector createClientInjector(final List<AbstractModule> overrides) {

        final List<Module> BASE_MODULES = List.of(
                new DefaultCommonConfigModule(),
                new DefaultGlueModule(),
                new DefaultGlueConfigModule() {
                    @Override
                    protected void customConfigure() {
                        super.customConfigure();
                        bind(ConsumerConfig.APPLY_REPORTS_SAME_MDIB_VERSION, Boolean.class, true);
                    }
                },
                new DefaultBicepsModule(),
                new DefaultBicepsConfigModule(),
                new DefaultCommonModule(),
                new DefaultDpwsModule(),
                new GlueDpwsConfigModule() {

                    @Override
                    protected void customConfigure() {
                        super.customConfigure();
                        bind(DpwsConfig.ENFORCE_HTTP_CHUNKED_TRANSFER, Boolean.class, true);
                    }
                },
                new AbstractModule() {
                    @Override
                    protected void configure() {
                        super.configure();
                        install(new FactoryModuleBuilder()
                                .implement(MdibHistorian.class, MdibHistorian.class)
                                .build(MdibHistorianFactory.class));
                    }
                });

        return Guice.createInjector(Modules.override(BASE_MODULES).with(overrides));
    }

    /**
     * @return the configured client {@linkplain Injector} instance
     */
    public Injector getInjector() {
        return injector;
    }
}
