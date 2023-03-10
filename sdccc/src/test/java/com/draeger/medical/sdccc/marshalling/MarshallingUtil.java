/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2023 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.marshalling;

import com.draeger.medical.sdccc.marshalling.guice.MarshallingConfig;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.somda.sdc.common.guice.AbstractConfigurationModule;

/**
 * Marshalling utility.
 */
public final class MarshallingUtil {

    private MarshallingUtil() {}

    /**
     * Create an injector which supports soap marshalling.
     *
     * @param validateMessages enable schema validation
     * @return injector configured for marshalling
     */
    public static Injector createMarshallingTestInjector(final boolean validateMessages) {
        final var testInjector = Guice.createInjector(
                new AbstractConfigurationModule() {
                    @Override
                    protected void defaultConfigure() {
                        bind(MarshallingConfig.VALIDATE_SOAP_MESSAGES, Boolean.class, validateMessages);
                    }
                },
                new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(SoapMarshalling.class).asEagerSingleton();
                    }
                });

        return testInjector;
    }
}
