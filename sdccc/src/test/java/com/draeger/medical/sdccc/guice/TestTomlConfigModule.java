/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.guice;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.somda.sdc.common.guice.AbstractConfigurationModule;

/**
 * Tests for the toml configuration loader.
 */
public class TestTomlConfigModule {

    /**
     * Tests whether loading a valid configuration file returns the correct values.
     *
     * @throws Exception on any exception
     */
    @Test
    @SuppressFBWarnings(
            value = {"RCN_REDUNDANT_NULLCHECK_WOULD_HAVE_BEEN_A_NPE"},
            justification = "No null check performed.")
    public void testLoadValidConfig() throws Exception {
        try (final var resource = this.getClass().getResourceAsStream("valid_config.toml")) {
            final var configModule = mock(AbstractConfigurationModule.class);
            final var parser = new TomlConfigParser(TestKeyClass.class);
            parser.parseToml(resource, configModule);

            final ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
            final ArgumentCaptor<Class<Object>> classCaptor = ArgumentCaptor.forClass(Class.class);
            final ArgumentCaptor<Object> objectCaptor = ArgumentCaptor.forClass(Object.class);

            verify(configModule, times(6)).bind(keyCaptor.capture(), classCaptor.capture(), objectCaptor.capture());

            final var capturedKeys = keyCaptor.getAllValues();
            final var capuredClasses = classCaptor.getAllValues();
            final var capturedValues = objectCaptor.getAllValues();

            // verify simple types
            verifyBinding(
                    TestKeyClass.STRING_VALUE,
                    String.class.getTypeName(),
                    "String",
                    capturedKeys,
                    capuredClasses,
                    capturedValues);
            verifyBinding(
                    TestKeyClass.BOOLEAN_VALUE,
                    Boolean.class.getTypeName(),
                    true,
                    capturedKeys,
                    capuredClasses,
                    capturedValues);
            verifyBinding(
                    TestKeyClass.FLOAT_VALUE,
                    Double.class.getTypeName(),
                    1.0,
                    capturedKeys,
                    capuredClasses,
                    capturedValues);

            // verify array types
            verifyArrayBinding(
                    TestKeyClass.STRING_ARRAY,
                    String[].class.getTypeName(),
                    new String[] {"String1", "String2"},
                    capturedKeys,
                    capuredClasses,
                    capturedValues);
            verifyArrayBinding(
                    TestKeyClass.BOOLEAN_ARRAY,
                    Boolean[].class.getTypeName(),
                    new Boolean[] {true, false},
                    capturedKeys,
                    capuredClasses,
                    capturedValues);
            verifyArrayBinding(
                    TestKeyClass.FLOAT_ARRAY,
                    Double[].class.getTypeName(),
                    new Double[] {-1.0, 1.0},
                    capturedKeys,
                    capuredClasses,
                    capturedValues);
        }
    }

    /**
     * Tests whether an invalid type in a configuration file triggers an exception.
     * @throws Exception on any exception
     */
    @Test
    public void testLoadInvalidConfigTypeMismatch() throws Exception {
        try (final var resource = this.getClass().getResourceAsStream("invalid_config_type_mismatch.toml")) {
            final var configModule = mock(AbstractConfigurationModule.class);
            final var parser = new TomlConfigParser(TestKeyClass.class);
            assertThrows(IOException.class, () -> parser.parseToml(resource, configModule));
        }
    }

    /**
     * Tests whether an unknown setting in a configuration file triggers an exception.
     * @throws Exception on any exception
     */
    @Test
    public void testLoadInvalidConfigUnknownKey() throws Exception {
        try (final var resource = this.getClass().getResourceAsStream("invalid_config_unknown_key.toml")) {
            final var configModule = mock(AbstractConfigurationModule.class);
            final var parser = new TomlConfigParser(TestKeyClass.class);
            assertThrows(IOException.class, () -> parser.parseToml(resource, configModule));
        }
    }

    void verifyBinding(
            final String expectedKey,
            final String expectedType,
            final Object expectedValue,
            final List<String> key,
            final List<Class<Object>> keyType,
            final List<Object> value) {

        assertTrue(key.contains(expectedKey));
        // find key position
        final int idx = key.indexOf(expectedKey);
        assertTrue(idx >= 0);

        assertEquals(expectedType, keyType.get(idx).getTypeName());
        assertEquals(expectedValue, value.get(idx));
    }

    void verifyArrayBinding(
            final String expectedKey,
            final String expectedType,
            final Object[] expectedValue,
            final List<String> key,
            final List<Class<Object>> keyType,
            final List<Object> value) {

        assertTrue(key.contains(expectedKey));
        // find key position
        final int idx = key.indexOf(expectedKey);
        assertTrue(idx >= 0);

        assertEquals(expectedType, keyType.get(idx).getTypeName());
        assertArrayEquals(expectedValue, expectedValue.getClass().cast(value.get(idx)));
    }

    /**
     * Container class providing example keys for unit tests.
     */
    public static class TestKeyClass {

        private static final String PRIVATE_KEY = "PrivateKey.";
        private static final String PRIVATE_ARRAYS = PRIVATE_KEY + "Arrays.";

        public static final String STRING_VALUE = PRIVATE_KEY + "StringValue";
        public static final String BOOLEAN_VALUE = PRIVATE_KEY + "BoolValue";
        public static final String FLOAT_VALUE = PRIVATE_KEY + "FloatValue";

        public static final String STRING_ARRAY = PRIVATE_ARRAYS + "StringArrayValue";
        public static final String BOOLEAN_ARRAY = PRIVATE_ARRAYS + "BoolArrayValue";
        public static final String FLOAT_ARRAY = PRIVATE_ARRAYS + "FloatArrayValue";
    }
}
