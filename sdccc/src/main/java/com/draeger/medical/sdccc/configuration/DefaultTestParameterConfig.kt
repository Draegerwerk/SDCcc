/*
 * This Source Code Form is subject to the terms of the "SDCcc non-commercial use license".
 *
 * Copyright (C) 2025 Draegerwerk AG & Co. KGaA
 */
package com.draeger.medical.sdccc.configuration

import org.somda.sdc.common.guice.AbstractConfigurationModule

/**
 * Provides default test parameter configuration for SDCcc.
 *
 * @see TestParameterConfig
 */
@Suppress("MagicNumber")
class DefaultTestParameterConfig : AbstractConfigurationModule() {
    override fun defaultConfigure() {
        bind(TestParameterConfig.BICEPS_547_TIME_INTERVAL, Long::class.javaPrimitiveType, 5L)
    }
}
