/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.tests.biceps.direct;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.draeger.medical.sdccc.configuration.EnabledTestConfig;
import com.draeger.medical.sdccc.sdcri.testclient.TestClient;
import com.draeger.medical.sdccc.tests.InjectorTestBase;
import com.draeger.medical.sdccc.tests.annotations.TestDescription;
import com.draeger.medical.sdccc.tests.annotations.TestIdentifier;
import com.draeger.medical.sdccc.util.MessageGeneratingUtil;
import com.draeger.medical.sdccc.util.MessagingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.somda.sdc.biceps.model.message.GetMdibResponse;

/**
 * BICEPS participant model state part tests (ch. 5.4).
 */
public class DirectParticipantModelStatePartTest extends InjectorTestBase {

    private MessageGeneratingUtil messageGeneratingUtil;

    @BeforeEach
    void setUp() {
        final TestClient testClient = getInjector().getInstance(TestClient.class);
        assertTrue(testClient.isClientRunning());
        this.messageGeneratingUtil = getInjector().getInstance(MessageGeneratingUtil.class);
    }

    @Test
    @TestIdentifier(EnabledTestConfig.BICEPS_R0021)
    @TestDescription("Requests the Mdib of the DUT and verifies that an MdState element is present."
            + " This test is stricter than the requirement because although an MDS can be removed, this test expects that"
            + " an MDS is present and therefore MdState is not empty and cannot be omitted.")
    void testRequirementR0021() throws MessagingException {
        final var responseMessage = messageGeneratingUtil.getMdib();
        final var getMdibResponse = (GetMdibResponse)
                responseMessage.getOriginalEnvelope().getBody().getAny().get(0);
        final var mdState = getMdibResponse.getMdib().getMdState();
        assertNotNull(mdState, "No MdState present, test failed.");
    }
}
