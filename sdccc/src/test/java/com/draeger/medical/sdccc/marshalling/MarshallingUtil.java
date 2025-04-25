/*
 * This Source Code Form is subject to the terms of the "SDCcc non-commercial use license".
 *
 * Copyright (C) 2025 Draegerwerk AG & Co. KGaA
 */

package com.draeger.medical.sdccc.marshalling;

import com.draeger.medical.sdccc.marshalling.guice.MarshallingConfig;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.TypeLiteral;
import java.util.List;
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
    public static Injector createMarshallingTestInjector(
            final boolean validateMessages, final List<String> packages, final List<String> schemas) {
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
