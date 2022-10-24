/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.guice;

import com.draeger.medical.sdccc.util.Constants;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.somda.sdc.common.guice.AbstractConfigurationModule;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Configuration module which maps a .toml file into an {@linkplain AbstractConfigurationModule}.
 */
public class TomlConfigParser {
    private static final Logger LOG = LogManager.getLogger(TomlConfigParser.class);
    private static final String PARSE_IO_EXCEPTION_MESSAGE = "Could not parse configuration.";

    private final Set<String> allowedKeys;

    /**
     * Parses a .toml file into an {@linkplain AbstractConfigurationModule}.
     *
     * <p>
     * This parser only allows known keys and will throw an exception otherwise.
     *
     * @param constantsClass to verify toml keys against
     * @throws IOException in case an error occurred during parsing.
     */
    public TomlConfigParser(final Class constantsClass) {
        // track allowed keys
        this.allowedKeys = new HashSet<>();

        Arrays.stream(constantsClass.getDeclaredFields()).forEach(field -> {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                try {
                    allowedKeys.add((String) field.get(null));
                } catch (final IllegalAccessException | ClassCastException e) {
                    // these are permitted errors because of possible private keys
                    LOG.debug(Constants.FIELD_ACCESS_ERROR_BASE_MESSAGE, field.getName());
                    LOG.trace(Constants.FIELD_ACCESS_ERROR_BASE_MESSAGE, field.getName(), e);
                }
            }
        });


    }

    /**
     * Parses a .toml file into an {@linkplain AbstractConfigurationModule}.
     *
     * <p>
     * This parser only allows known keys and will throw an exception otherwise.
     *
     * @param module to parse configuration into
     * @param toml   to read toml from
     * @throws IOException in case an error occurred during parsing.
     */
    public void parseToml(final InputStream toml, final AbstractConfigurationModule module) throws IOException {
        final TomlParseResult result = Toml.parse(toml);
        if (result.hasErrors()) {
            result.errors().forEach(error -> LOG.error(error.toString()));
            throw new IOException(PARSE_IO_EXCEPTION_MESSAGE);
        }

        final Set<String> keys = result.dottedKeySet();
        for (final String key : keys) {
            parseKey(module, result, key);
        }
    }

    private void parseKey(final AbstractConfigurationModule module, final TomlParseResult result, final String key)
        throws IOException {
        // verify key has value
        if (result.get(key) == null) {
            LOG.warn("Empty key {}", key);
            return;
        }

        // verify key is allowed
        if (!allowedKeys.contains(key)) {
            LOG.error("Configuration key {} is unknown", key);
            throw new IOException("Unknown configuration key");
        }

        // simple types
        if (result.isString(key)) {
            final var value = result.getString(key);
            LOG.debug("Binding String {} to {}", key, value);
            bind(module, key, String.class, value);
        } else if (result.isBoolean(key)) {
            final var value = result.getBoolean(key);
            LOG.debug("Binding Boolean {} to {}", key, value);
            bind(module, key, Boolean.class, value);
        } else if (result.isDouble(key)) {
            final var value = result.getDouble(key);
            LOG.debug("Binding Double {} to {}", key, value);
            bind(module, key, Double.class, value);
            // complex types
        } else if (result.isArray(key)) {
            parseArray(module, Objects.requireNonNull(result.getArray(key)), key);
        } else {
            final var value = result.get(key);
            assert value != null;
            LOG.error("Could not parse type {}", value.getClass());
            throw new IOException(PARSE_IO_EXCEPTION_MESSAGE);
        }
    }

    private void parseArray(final AbstractConfigurationModule module, final TomlArray value, final String key)
        throws IOException {
        if (value.containsStrings()) {
            LOG.debug("Binding String array {} to {}", key, value);
            final var resultArray = new String[value.size()];
            for (int i = 0; i < value.size(); i++) {
                resultArray[i] = value.getString(i);
            }
            bind(module, key, String[].class, resultArray);
        } else if (value.containsBooleans()) {
            LOG.debug("Binding Boolean array {} to {}", key, value);
            final var resultArray = new Boolean[value.size()];
            for (int i = 0; i < value.size(); i++) {
                resultArray[i] = value.getBoolean(i);
            }
            bind(module, key, Boolean[].class, resultArray);
        } else if (value.containsDoubles()) {
            LOG.debug("Binding Double array {} to {}", key, value);
            final var resultArray = new Double[value.size()];
            for (int i = 0; i < value.size(); i++) {
                resultArray[i] = value.getDouble(i);
            }
            bind(module, key, Double[].class, resultArray);
        } else {
            LOG.error("Could not parse array type {}", value.getClass());
            throw new IOException(PARSE_IO_EXCEPTION_MESSAGE);
        }
    }

    /**
     * Binds a key with a given type to a value.
     *
     * @param configModule module to bind on
     * @param name         the key to bind to
     * @param dataType     the class of type of the binding
     * @param value        the value to bind
     * @param <T>          the type of the value
     */
    private <T> void bind(final AbstractConfigurationModule configModule, final String name, final Class<T> dataType,
                          @Nullable final T value) {
        configModule.bind(name, dataType, value);
    }
}
