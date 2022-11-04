/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package it.com.draeger.medical.sdccc.testsuite_it_mock_tests.invariant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

import com.draeger.medical.sdccc.tests.InjectorTestBase;
import com.draeger.medical.sdccc.tests.annotations.TestDescription;
import com.draeger.medical.sdccc.tests.annotations.TestIdentifier;
import it.com.draeger.medical.sdccc.testsuite_it_mock_tests.Identifiers;
import it.com.draeger.medical.sdccc.testsuite_it_mock_tests.WasRunObserver;
import org.junit.jupiter.api.Test;

/**
 * Mock invariant tests.
 */
public class InvariantTest extends InjectorTestBase {

    @Test
    @TestIdentifier(Identifiers.INVARIANT_TEST_IDENTIFIER)
    @TestDescription("mock test")
    void testHasBeenRunEverythingIsFine() {
        final var obs = getInjector().getInstance(WasRunObserver.class);
        assertFalse(obs.hadInvariantRun());
        obs.setInvariantRun(true);
    }

    @Test
    @TestIdentifier(Identifiers.INVARIANT_TEST_IDENTIFIER_FAILING)
    @TestDescription("mock test")
    void testIsFailing() {
        fail("Intended failure");
    }
}
