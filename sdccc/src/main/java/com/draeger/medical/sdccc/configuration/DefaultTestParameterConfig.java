package com.draeger.medical.sdccc.configuration;

import org.somda.sdc.common.guice.AbstractConfigurationModule;

/**
 * Provides default test parameter configuration for SDCcc.
 *
 * @see TestParameterConfig
 */
public class DefaultTestParameterConfig extends AbstractConfigurationModule {

    @Override
    protected void defaultConfigure() {
        bind(TestParameterConfig.BICEPS_547_TIME_INTERVAL, long.class, 5L);
    }
}
