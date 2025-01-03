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
import com.google.inject.TypeLiteral;
import org.somda.sdc.common.guice.AbstractConfigurationModule;

import java.util.List;

/**
 * Marshalling utility.
 */
public final class MarshallingUtil {

    private MarshallingUtil() {
    }

    /**
     * Create an injector which supports soap marshalling.
     *
     * @param validateMessages enable schema validation
     * @return injector configured for marshalling
     */
    public static Injector createMarshallingTestInjector(final boolean validateMessages) {
        return createMarshallingTestInjector(validateMessages, SoapMarshalling.PACKAGES, SoapMarshalling.SCHEMAS);
    }

    /**
     * Create an injector which supports soap marshalling.
     *
     * @param validateMessages enable schema validation
     * @param packages         packages to scan for JAXB classes
     * @param schemas          schemas to validate against
     * @return injector configured for marshalling
     */
    public static Injector createMarshallingTestInjector(final boolean validateMessages,
                                                         final List<String> packages,
                                                         final List<String> schemas) {
        final var testInjector = Guice.createInjector(
                new AbstractConfigurationModule() {
                    @Override
                    protected void defaultConfigure() {
                        bind(MarshallingConfig.VALIDATE_SOAP_MESSAGES, Boolean.class, validateMessages);
                        bind(MarshallingConfig.PACKAGES, new TypeLiteral<>() {}, packages);
                        bind(MarshallingConfig.SCHEMAS, new TypeLiteral<>() {}, schemas);
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
