/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.tests.biceps.direct;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.draeger.medical.sdccc.configuration.EnabledTestConfig;
import com.draeger.medical.sdccc.sdcri.testclient.TestClient;
import com.draeger.medical.sdccc.tests.InjectorTestBase;
import com.draeger.medical.sdccc.tests.annotations.TestDescription;
import com.draeger.medical.sdccc.tests.annotations.TestIdentifier;
import com.draeger.medical.sdccc.tests.util.HostedServiceVerifier;
import com.draeger.medical.sdccc.util.MessageGeneratingUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.somda.sdc.glue.common.WsdlConstants;

/**
 * BICEPS service model tests directly using the {@linkplain TestClient}.
 */
public class DirectServiceModelTest extends InjectorTestBase {

    private TestClient testClient;
    private HostedServiceVerifier hostedServiceVerifier;

    @BeforeEach
    void setUp() {
        this.testClient = getInjector().getInstance(TestClient.class);
        hostedServiceVerifier = getInjector().getInstance(HostedServiceVerifier.class);
        assertTrue(testClient.isClientRunning());
    }

    @Test
    @TestIdentifier(EnabledTestConfig.BICEPS_R0062)
    @TestDescription("Checks whether the DUT has provided a GetService in the GetMetadataResponse and verifies"
            + " the get service endpoint provided by the DUT is conforming with SDC Glue Annex B"
            + " and only implements SDC services.")
    void testRequirement0062() {
        hostedServiceVerifier.checkServicePresenceAndConformance(
                WsdlConstants.PORT_TYPE_GET_QNAME, MessageGeneratingUtil.getGetService(testClient));
    }

    @Test
    @TestIdentifier(EnabledTestConfig.BICEPS_R0064)
    @TestDescription("Checks whether the DUT has provided a sdc:StateEventService in the wsx:GetMetadataResponse and"
            + " verifies the sdc:StateEventService endpoint provided by the DUT is conforming"
            + " with IEEE Std 11073-20701-2018 SDC Glue Annex B and only implements SDC services.")
    void testRequirement0064() {
        hostedServiceVerifier.checkServicePresenceAndConformance(
                WsdlConstants.PORT_TYPE_STATE_EVENT_QNAME, MessageGeneratingUtil.getStateEventService(testClient));
    }

    @Test
    @TestIdentifier(EnabledTestConfig.BICEPS_R0066)
    @TestDescription("Checks whether the DUT has provided a WaveformService in the GetMetadataResponse and"
            + " verifies the waveform service endpoint provided by the DUT is conforming"
            + " with SDC Glue Annex B and only implements SDC services.")
    void testRequirement0066() {
        hostedServiceVerifier.checkServicePresenceAndConformance(
                WsdlConstants.PORT_TYPE_WAVEFORM_QNAME, MessageGeneratingUtil.getWaveformService(testClient));
    }

    @Test
    @TestIdentifier(EnabledTestConfig.BICEPS_R0068)
    @TestDescription("Checks whether the DUT has provided a SetService in the GetMetadataResponse and verifies"
            + " the set service endpoint provided by the DUT is conforming with SDC Glue Annex B"
            + " and only implements SDC services.")
    void testRequirement0068() {
        hostedServiceVerifier.checkServicePresenceAndConformance(
                WsdlConstants.PORT_TYPE_SET_QNAME, MessageGeneratingUtil.getSetService(testClient));
    }

    @Test
    @TestIdentifier(EnabledTestConfig.BICEPS_R0069)
    @TestDescription("Checks whether the DUT has provided a ContextService in the GetMetadataResponse and"
            + " verifies the context service endpoint provided by the DUT is conforming"
            + " with SDC Glue Annex B and only implements SDC services.")
    void testRequirement0069() {
        hostedServiceVerifier.checkServicePresenceAndConformance(
                WsdlConstants.PORT_TYPE_CONTEXT_QNAME, MessageGeneratingUtil.getContextService(testClient));
    }

    @Test
    @TestIdentifier(EnabledTestConfig.BICEPS_R0100)
    @TestDescription("Checks whether the DUT has provided a ArchiveService in the GetMetadataResponse and"
            + " verifies the archive service endpoint provided by the DUT is conforming"
            + " with SDC Glue Annex B and only implements SDC services.")
    void testRequirement0100() {
        hostedServiceVerifier.checkServicePresenceAndConformance(
                WsdlConstants.PORT_TYPE_ARCHIVE_QNAME, MessageGeneratingUtil.getArchiveService(testClient));
    }

    @Test
    @TestIdentifier(EnabledTestConfig.BICEPS_R0101)
    @TestDescription("Checks whether the DUT has provided a LocalizationService in the GetMetadataResponse and"
            + " verifies the localization service endpoint provided by the DUT is conforming"
            + " with SDC Glue Annex B and only implements SDC services.")
    void testRequirement0101() {
        hostedServiceVerifier.checkServicePresenceAndConformance(
                WsdlConstants.PORT_TYPE_LOCALIZATION_QNAME, MessageGeneratingUtil.getLocalizationService(testClient));
    }

    @Test
    @TestIdentifier(EnabledTestConfig.BICEPS_R0104)
    @TestDescription("Checks whether the DUT has provided a DescriptionEventService in the GetMetadataResponse and "
            + "verifies the description event service endpoint provided by the DUT is conforming "
            + "with SDC Glue Annex B and only implements SDC services.")
    void testRequirement0104() {
        hostedServiceVerifier.checkServicePresenceAndConformance(
                WsdlConstants.PORT_TYPE_DESCRIPTION_EVENT_QNAME,
                MessageGeneratingUtil.getDescriptionEventService(testClient));
    }

    @Test
    @TestIdentifier(EnabledTestConfig.BICEPS_R0119)
    @TestDescription("Checks whether the DUT has provided a ContainmentTreeService in the GetMetadataResponse and"
            + " verifies the containment tree service endpoint provided by the DUT is conforming"
            + " with SDC Glue Annex B and only implements SDC services.")
    void testRequirement0119() {
        hostedServiceVerifier.checkServicePresenceAndConformance(
                WsdlConstants.PORT_TYPE_CONTAINMENT_TREE_QNAME,
                MessageGeneratingUtil.getContainmentTreeService(testClient));
    }
}
