/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.sdcri.testclient;

import com.draeger.medical.sdccc.messages.MessageStorage;
import com.draeger.medical.sdccc.sdcri.CommunicationLogMessageStorage;
import com.draeger.medical.sdccc.tests.util.MdibHistorian;
import com.draeger.medical.sdccc.tests.util.guice.MdibHistorianFactory;
import com.draeger.medical.sdccc.util.TestRunObserver;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.inject.util.Modules;
import org.somda.sdc.biceps.guice.DefaultBicepsConfigModule;
import org.somda.sdc.biceps.guice.DefaultBicepsModule;
import org.somda.sdc.common.guice.AbstractConfigurationModule;
import org.somda.sdc.common.guice.DefaultCommonConfigModule;
import org.somda.sdc.common.guice.DefaultCommonModule;
import org.somda.sdc.dpws.CommunicationLog;
import org.somda.sdc.dpws.CommunicationLogImpl;
import org.somda.sdc.dpws.CommunicationLogSink;
import org.somda.sdc.dpws.DpwsConfig;
import org.somda.sdc.dpws.crypto.CryptoConfig;
import org.somda.sdc.dpws.crypto.CryptoSettings;
import org.somda.sdc.dpws.guice.DefaultDpwsModule;
import org.somda.sdc.glue.consumer.ConsumerConfig;
import org.somda.sdc.glue.guice.DefaultGlueConfigModule;
import org.somda.sdc.glue.guice.DefaultGlueModule;
import org.somda.sdc.glue.guice.GlueDpwsConfigModule;

import javax.net.ssl.HostnameVerifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Utility for a {@linkplain TestClient} instance.
 */
public class TestClientUtil {
    private final Injector injector;

    /**
     * Creates a utility instance which prepares the injector for the client.
     *
     * @param cryptoSettings                 crypto setting
     * @param communicationLogMessageStorage connector to the {@linkplain MessageStorage} to write to
     * @param testRunObserver                observer for invalidating test runs on unexpected errors
     */
    @Inject
    public TestClientUtil(final CryptoSettings cryptoSettings,
                          final CommunicationLogMessageStorage communicationLogMessageStorage,
                          final TestRunObserver testRunObserver) {

        injector = createClientInjector(
            List.of(new AbstractConfigurationModule() {
                    @Override
                    protected void defaultConfigure() {
                        bind(CryptoConfig.CRYPTO_SETTINGS,
                            CryptoSettings.class,
                            cryptoSettings
                        );
                        bind(CryptoConfig.CRYPTO_CLIENT_HOSTNAME_VERIFIER,
                            HostnameVerifier.class,
                            (hostname, session) -> true);
                        bind(DpwsConfig.HTTPS_SUPPORT, Boolean.class, true);
                        bind(DpwsConfig.HTTP_SUPPORT, Boolean.class, false);
                    }
                },
                new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(CommunicationLog.class).to(CommunicationLogImpl.class).asEagerSingleton();
                        bind(CommunicationLogSink.class).toInstance(communicationLogMessageStorage);
                        bind(TestRunObserver.class).toInstance(testRunObserver);
                    }
                })
        );

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
                    install(new FactoryModuleBuilder().implement(MdibHistorian.class, MdibHistorian.class)
                        .build(MdibHistorianFactory.class));
                }
            }
        );

        return Guice.createInjector(Modules.override(BASE_MODULES).with(overrides));
    }

    /**
     * @return the configured client {@linkplain Injector} instance
     */
    public Injector getInjector() {
        return injector;
    }
}
