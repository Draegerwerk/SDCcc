/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.sdcri.testprovider;

import com.draeger.medical.sdccc.messages.MessageStorage;
import com.draeger.medical.sdccc.sdcri.CommunicationLogMessageStorage;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.util.Modules;
import org.somda.sdc.biceps.guice.DefaultBicepsConfigModule;
import org.somda.sdc.biceps.guice.DefaultBicepsModule;
import org.somda.sdc.common.guice.DefaultCommonConfigModule;
import org.somda.sdc.common.guice.DefaultCommonModule;
import org.somda.sdc.dpws.CommunicationLog;
import org.somda.sdc.dpws.CommunicationLogImpl;
import org.somda.sdc.dpws.CommunicationLogSink;
import org.somda.sdc.dpws.DpwsConfig;
import org.somda.sdc.dpws.crypto.CryptoConfig;
import org.somda.sdc.dpws.crypto.CryptoSettings;
import org.somda.sdc.dpws.guice.DefaultDpwsModule;
import org.somda.sdc.glue.guice.DefaultGlueConfigModule;
import org.somda.sdc.glue.guice.DefaultGlueModule;
import org.somda.sdc.glue.guice.GlueDpwsConfigModule;

import javax.net.ssl.HostnameVerifier;

/**
 * Utility for a {@linkplain TestProvider} instance.
 */
public class TestProviderUtil {
    private final Injector injector;

    /**
     * Creates a utility instance which prepares the injector for the client.
     *
     * @param cryptoSettings                 crypto setting
     * @param communicationLogMessageStorage connector to the {@linkplain MessageStorage} to write to
     */
    @Inject
    public TestProviderUtil(final CryptoSettings cryptoSettings,
                            final CommunicationLogMessageStorage communicationLogMessageStorage) {
        injector = Guice.createInjector(Modules.override(
            new DefaultCommonConfigModule(),
            new DefaultGlueModule(),
            new DefaultGlueConfigModule(),
            new DefaultBicepsModule(),
            new DefaultBicepsConfigModule(),
            new DefaultCommonModule(),
            new DefaultDpwsModule(),
            new GlueDpwsConfigModule() {
                @Override
                protected void customConfigure() {
                    super.customConfigure();
                    bind(CryptoConfig.CRYPTO_SETTINGS,
                        CryptoSettings.class,
                        cryptoSettings
                    );
                    bind(CryptoConfig.CRYPTO_DEVICE_HOSTNAME_VERIFIER,
                        HostnameVerifier.class,
                        (hostname, session) -> true);
                    bind(DpwsConfig.HTTPS_SUPPORT, Boolean.class, true);
                    bind(DpwsConfig.HTTP_SUPPORT, Boolean.class, false);
                }
            }
        ).with(new AbstractModule() {
            @Override
            protected void configure() {
                super.configure();
                bind(CommunicationLog.class).to(CommunicationLogImpl.class).asEagerSingleton();
                bind(CommunicationLogSink.class).toInstance(communicationLogMessageStorage);
            }
        }));
    }

    /**
     * @return the configured client {@linkplain Injector} instance
     */
    public Injector getInjector() {
        return injector;
    }
}
