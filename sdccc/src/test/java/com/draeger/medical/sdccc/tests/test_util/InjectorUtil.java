/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.tests.test_util;

import com.draeger.medical.sdccc.configuration.DefaultEnabledTestConfig;
import com.draeger.medical.sdccc.configuration.DefaultTestSuiteConfig;
import com.draeger.medical.sdccc.configuration.DefaultTestSuiteModule;
import com.draeger.medical.sdccc.configuration.TestRunConfig;
import com.draeger.medical.sdccc.configuration.TestSuiteConfig;
import com.draeger.medical.sdccc.messages.HibernateConfig;
import com.draeger.medical.sdccc.util.HibernateConfigInMemoryImpl;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.google.inject.util.Modules;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;
import org.somda.sdc.common.guice.AbstractConfigurationModule;

/**
 * Utility for injector management in tests.
 */
public final class InjectorUtil {

    private InjectorUtil() {}

    /**
     * Creates a guice {@linkplain Injector} for testing requirement tests.
     *
     * @param overrides additional use case specific overrides
     * @return a configured injector
     * @throws IOException on error creating test run directory
     */
    public static Injector setupInjector(final AbstractModule... overrides) throws IOException {
        final var randomPrefix = UUID.randomUUID().toString();
        final var tempDir = Files.createTempDirectory("TestTest" + randomPrefix);
        tempDir.toFile().deleteOnExit();

        final var allOverrides = new ArrayList<AbstractModule>();

        allOverrides.add(new AbstractModule() {
            @Override
            protected void configure() {
                // use in memory database by default
                bind(HibernateConfig.class)
                        .to(HibernateConfigInMemoryImpl.class)
                        .in(Singleton.class);
            }
        });
        allOverrides.addAll(Arrays.asList(overrides));

        return Guice.createInjector(Modules.override(
                        new DefaultTestSuiteModule(),
                        new DefaultTestSuiteConfig() {

                            @Override
                            protected void configureCommlogSettings() {
                                bind(TestSuiteConfig.COMMLOG_MESSAGE_BUFFER_SIZE, int.class, 1);
                            }
                        },
                        new DefaultEnabledTestConfig(),
                        new AbstractConfigurationModule() {
                            @Override
                            protected void defaultConfigure() {
                                // use temp dir for testing
                                this.bind(TestRunConfig.TEST_RUN_DIR, File.class, tempDir.toFile());
                            }
                        })
                .with(allOverrides));
    }
}
