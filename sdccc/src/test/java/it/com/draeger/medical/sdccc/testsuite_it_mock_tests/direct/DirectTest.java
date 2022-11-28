/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package it.com.draeger.medical.sdccc.testsuite_it_mock_tests.direct;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

import com.draeger.medical.sdccc.sdcri.testclient.TestClient;
import com.draeger.medical.sdccc.tests.InjectorTestBase;
import com.draeger.medical.sdccc.tests.annotations.TestDescription;
import com.draeger.medical.sdccc.tests.annotations.TestIdentifier;
import it.com.draeger.medical.sdccc.testsuite_it_mock_tests.Identifiers;
import it.com.draeger.medical.sdccc.testsuite_it_mock_tests.WasRunObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Mock direct tests.
 */
public class DirectTest extends InjectorTestBase {

    private TestClient testClient;

    @BeforeEach
    void setUp() {
        this.testClient = getInjector().getInstance(TestClient.class);
    }

    @Test
    @TestIdentifier(Identifiers.DIRECT_TEST_IDENTIFIER)
    @TestDescription("mock test with consumer connection check")
    void testHasBeenRunEverythingIsFine() {
        final var obs = getInjector().getInstance(WasRunObserver.class);
        assertFalse(obs.hadDirectRun());

        // assert client is connected
        assertFalse(testClient.getConnector().getConnectedDevices().isEmpty());
        obs.setDirectRun(true);
    }

    @Test
    @TestIdentifier(Identifiers.DIRECT_TEST_IDENTIFIER_FAILING)
    @TestDescription("mock test failure")
    void testFailingTest() {
        fail("Intended failure");
    }
}
