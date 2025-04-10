/*
 * This Source Code Form is subject to the terms of the "SDCcc non-commercial use license".
 *
 * Copyright (C) 2025 Draegerwerk AG & Co. KGaA
 */
package com.draeger.medical.sdccc.configuration

/**
 * Test parameter configuration for SDCcc.
 *
 * @see DefaultTestParameterConfig
 */
@Suppress("UtilityClassWithPublicConstructor")
open class TestParameterConfig {

    companion object {
        /**
         * Test parameter configuration.
         */
        private const val TEST_PARAMETER = "TestParameter."

        /**
         * Test parameter for biceps:5-4-7 tests. Time interval in seconds to pause between the SetMetricStatus
         * manipulation calls. The report that follows a SetMetricStatus manipulation is expected
         * within the time interval.
         */
        const val BICEPS_547_TIME_INTERVAL: String = TEST_PARAMETER + "Biceps547TimeInterval"
    }
}
