/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.tests.biceps.direct;

import com.draeger.medical.sdccc.configuration.EnabledTestConfig;
import com.draeger.medical.sdccc.sdcri.testclient.TestClient;
import com.draeger.medical.sdccc.tests.InjectorTestBase;
import com.draeger.medical.sdccc.tests.annotations.TestDescription;
import com.draeger.medical.sdccc.tests.annotations.TestIdentifier;
import com.draeger.medical.sdccc.tests.util.NoTestData;
import com.draeger.medical.sdccc.util.MessageGeneratingUtil;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.somda.sdc.biceps.common.MdibEntity;
import org.somda.sdc.biceps.common.access.MdibAccess;
import org.somda.sdc.biceps.model.message.GetMdDescriptionResponse;
import org.somda.sdc.biceps.model.participant.AbstractDescriptor;
import org.somda.sdc.biceps.model.participant.MdsDescriptor;
import org.somda.sdc.dpws.soap.SoapUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BICEPS Annex C message model tests.
 */
public class DirectMessageModelAnnexTest extends InjectorTestBase {
    private TestClient testClient;
    private MessageGeneratingUtil messageGeneratingUtil;
    private SoapUtil soapUtil;

    @BeforeEach
    void setUp() {
        this.testClient = getInjector().getInstance(TestClient.class);
        assertTrue(testClient.isClientRunning());

        this.messageGeneratingUtil = getInjector().getInstance(MessageGeneratingUtil.class);
        this.soapUtil = testClient.getInjector().getInstance(SoapUtil.class);
    }

    @Test
    @DisplayName("MdDescription comprises the requested set of MDS descriptors. A SERVICE PROVIDER SHALL include MDS"
        + " descriptors based on HANDLEs specified in the msg:GetMdDescription/msg:HandleRef list:"
        + " - If the HANDLE reference list is empty, all MDS descriptors are included in the result list."
        + " - If a HANDLE reference matches an MDS descriptor, it is included in the result list."
        + " - If a HANDLE reference matches a descriptor that is not an MDS descriptor, the MDS descriptor that is an"
        + " ancestor of the descriptor referenced by the HANDLE reference is included in the result list."
        + " - If a HANDLE reference does not match any descriptor, the HANDLE reference is ignored.")
    @TestIdentifier(EnabledTestConfig.BICEPS_C55_0)
    @TestDescription("Retrieves the mdib of the DUT to collect all mds descriptors present, and then sends four"
        + " different GetMdDescription messages to the DUT. One with an empty handle ref, the second with an mds"
        + " descriptor handle, the third with a descriptor handle from a descendant element of an mds, and fourth with"
        + " an mds handle and a handle not matching any descriptor and checks if the expected mds descriptors are"
        + " present in the response.")
    void testRequirementC55() throws NoTestData {
        final var remoteMdibAccess = testClient.getSdcRemoteDevice().getMdibAccess();
        final var mdsEntities = remoteMdibAccess.findEntitiesByType(MdsDescriptor.class);
        final var mdsList = mdsEntities.stream().map(MdibEntity::getHandle).collect(Collectors.toList());
        assertTestData(mdsList, "No Mds provided by the DUT, test failed.");

        // empty handle reference
        sendGetMdDescriptionAndCheckResult(List.of(), mdsList);
        // handle reference matches mds descriptor
        sendGetMdDescriptionAndCheckResult(List.of(mdsList.get(0)), List.of(mdsList.get(0)));
        final var mdsWithChild = mdsEntities.stream().filter(mds -> mds.getChildren().size() > 0).findFirst();
        assertTrue(mdsWithChild.isPresent(), "No Mds with child elements found, test failed.");
        final var children = mdsWithChild.orElseThrow().getChildren();
        // handle reference does not match mds descriptor
        sendGetMdDescriptionAndCheckResult(
            getChildrenOfChildren(remoteMdibAccess, children), List.of(mdsWithChild.orElseThrow().getHandle()));
        final var entities = remoteMdibAccess.findEntitiesByType(AbstractDescriptor.class);
        final var entitiesHandleList = entities.stream().map(MdibEntity::getHandle).collect(Collectors.toList());
        final var unknownHandle = generateUnknownHandle(entitiesHandleList);
        // one handle reference does not match any descriptor
        sendGetMdDescriptionAndCheckResult(List.of(unknownHandle, mdsList.get(0)), List.of(mdsList.get(0)));
    }

    private List<String> getChildrenOfChildren(final MdibAccess remoteMdibAccess, final List<String> children) {
        final var childrenOfChildren = new ArrayList<String>();
        for (var child: children) {
            final var childEntity = remoteMdibAccess.getEntity(child).orElseThrow();
            childrenOfChildren.addAll(childEntity.getChildren());
        }
        return childrenOfChildren.isEmpty() ? children : childrenOfChildren;
    }

    private String generateUnknownHandle(final List<String> entitiesHandleList) {
        final var length = 10;
        var unknownHandle = RandomStringUtils.random(length, true, true);
        while (entitiesHandleList.contains(unknownHandle)) {
            unknownHandle = RandomStringUtils.random(length, true, true);
        }
        return unknownHandle;
    }

    private void sendGetMdDescriptionAndCheckResult(final List<String> handleRef, final List<String> expectedResult) {
        final var message = messageGeneratingUtil.getMdDescription(handleRef);
        final var responseBodyOpt = soapUtil.getBody(message, GetMdDescriptionResponse.class);
        assertTrue(responseBodyOpt.isPresent(), "No GetMdDescriptionResponse received, test failed.");
        final var actualResult = responseBodyOpt.orElseThrow().getMdDescription().getMds().stream()
            .map(AbstractDescriptor::getHandle).collect(Collectors.toList());
        assertTrue(actualResult.containsAll(expectedResult), String.format(
                "The Mds in the GetMdDescriptionResponse are %s, but should also contain %s, test failed.",
            actualResult, expectedResult));
    }
}
