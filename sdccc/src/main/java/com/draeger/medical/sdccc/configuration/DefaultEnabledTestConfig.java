/*
 * This Source Code Form is subject to the terms of the "SDCcc non-commercial use license".
 *
 * Copyright (C) 2025 Draegerwerk AG & Co. KGaA
 */

package com.draeger.medical.sdccc.configuration;

import com.draeger.medical.sdccc.util.Constants;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.somda.sdc.common.guice.AbstractConfigurationModule;

/**
 * Configuration module which disables all tests by default.
 */
public class DefaultEnabledTestConfig extends AbstractConfigurationModule {
    private static final Logger LOG = LogManager.getLogger(DefaultEnabledTestConfig.class);

    @Override
    protected void defaultConfigure() {
        disableAllRequirements();
    }

    void disableAllRequirements() {
        final List<String> publicKeys = new ArrayList<>();
        Arrays.stream(EnabledTestConfig.class.getDeclaredFields()).forEach(field -> {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                try {
                    publicKeys.add((String) field.get(null));
                } catch (final IllegalAccessException | ClassCastException e) {
                    // these errors are acceptable because of private fields
                    LOG.debug(Constants.FIELD_ACCESS_ERROR_BASE_MESSAGE, field.getName());
                    LOG.trace(Constants.FIELD_ACCESS_ERROR_BASE_MESSAGE, field.getName(), e);
                }
            }
        });

        publicKeys.forEach(key -> {
            LOG.debug("Disabling key {} by default", key);
            bind(key, Boolean.class, false);
        });
    }
}
