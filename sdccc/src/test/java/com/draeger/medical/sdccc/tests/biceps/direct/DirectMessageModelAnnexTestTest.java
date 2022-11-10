/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.tests.biceps.direct;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.draeger.medical.sdccc.sdcri.testclient.TestClient;
import com.draeger.medical.sdccc.sdcri.testclient.TestClientUtil;
import com.draeger.medical.sdccc.tests.InjectorTestBase;
import com.draeger.medical.sdccc.tests.test_util.InjectorUtil;
import com.draeger.medical.sdccc.tests.util.NoTestData;
import com.draeger.medical.sdccc.util.MdibBuilder;
import com.draeger.medical.sdccc.util.MessageGeneratingUtil;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.somda.sdc.biceps.common.MdibEntity;
import org.somda.sdc.biceps.model.message.GetMdDescriptionResponse;
import org.somda.sdc.biceps.model.participant.AbstractDescriptor;
import org.somda.sdc.biceps.model.participant.ChannelDescriptor;
import org.somda.sdc.biceps.model.participant.MdDescription;
import org.somda.sdc.biceps.model.participant.MdsDescriptor;
import org.somda.sdc.biceps.model.participant.VmdDescriptor;
import org.somda.sdc.dpws.service.HostingServiceProxy;
import org.somda.sdc.dpws.soap.SoapMessage;
import org.somda.sdc.glue.consumer.SdcRemoteDevice;

/**
 * Unit test for the BICEPS {@linkplain DirectMessageModelAnnexTest}.
 */
public class DirectMessageModelAnnexTestTest {
    private static final String SECOND_MDS_HANDLE = "mds1";
    private static final String SECOND_VMD_HANDLE = "vmdOfMds1";
    private static final String SECOND_CHANNEL_HANDLE = "channelOfMds1";
    private static final String THIRD_MDS_HANDLE = "mds2";

    private SdcRemoteDevice mockDevice;
    private DirectMessageModelAnnexTest testClass;
    private TestClient testClient;

    private MessageGeneratingUtil messageGeneratingUtil;

    @BeforeEach
    void setUp() throws IOException {
        final TestClient mockClient = mock(TestClient.class);
        when(mockClient.isClientRunning()).thenReturn(true);
        testClient = mockClient;

        final var clientInjector = TestClientUtil.createClientInjector();
        when(testClient.getInjector()).thenReturn(clientInjector);
        messageGeneratingUtil = mock(MessageGeneratingUtil.class, RETURNS_DEEP_STUBS);
        final Injector injector = InjectorUtil.setupInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(TestClient.class).toInstance(testClient);
                bind(MessageGeneratingUtil.class).toInstance(messageGeneratingUtil);
            }
        });

        InjectorTestBase.setInjector(injector);

        mockDevice = mock(SdcRemoteDevice.class, Mockito.RETURNS_DEEP_STUBS);
        final var mockHostingService = mock(HostingServiceProxy.class);
        when(mockDevice.getHostingServiceProxy()).thenReturn(mockHostingService);
        when(testClient.getSdcRemoteDevice()).thenReturn(mockDevice);

        testClass = new DirectMessageModelAnnexTest();
        testClass.setUp();
    }

    /**
     * Tests if the test fails when no MDS is present.
     */
    @Test
    public void testRequirementC55NoTestData() {
        when(mockDevice.getMdibAccess().findEntitiesByType(MdsDescriptor.class)).thenReturn(Collections.emptyList());

        assertThrows(NoTestData.class, testClass::testRequirementC55);
    }

    /**
     * Tests whether the test passes if getMdDescriptionResponse messages contain the expected MdsDescriptors,
     * depending on the specified handle reference.
     *
     * @throws Exception on any Exception
     */
    @Test
    public void testRequirementC55Good() throws Exception {
        setupMockDevice(true);
        final var one = buildGetMdDescriptionResponse(
                List.of(MdibBuilder.DEFAULT_MDS_HANDLE, SECOND_MDS_HANDLE, THIRD_MDS_HANDLE));
        final var two = buildGetMdDescriptionResponse(List.of(MdibBuilder.DEFAULT_MDS_HANDLE));
        final var three = buildGetMdDescriptionResponse(List.of(SECOND_MDS_HANDLE));
        final var four = buildGetMdDescriptionResponse(
                List.of(MdibBuilder.DEFAULT_MDS_HANDLE, SECOND_MDS_HANDLE, THIRD_MDS_HANDLE));
        when(messageGeneratingUtil.getMdDescription(any()))
                .thenReturn(one)
                .thenReturn(two)
                .thenReturn(three)
                .thenReturn(four);

        testClass.testRequirementC55();
    }

    /**
     * Tests whether the test fails if no mds has child elements.
     */
    @Test
    public void testRequirementC55BadNoChildElements() {
        setupMockDevice(false);
        final var one = buildGetMdDescriptionResponse(
                List.of(MdibBuilder.DEFAULT_MDS_HANDLE, SECOND_MDS_HANDLE, THIRD_MDS_HANDLE));
        final var two = buildGetMdDescriptionResponse(List.of(MdibBuilder.DEFAULT_MDS_HANDLE));
        final var three = buildGetMdDescriptionResponse(List.of(SECOND_MDS_HANDLE));
        final var four = buildGetMdDescriptionResponse(
                List.of(MdibBuilder.DEFAULT_MDS_HANDLE, SECOND_MDS_HANDLE, THIRD_MDS_HANDLE));
        when(messageGeneratingUtil.getMdDescription(any()))
                .thenReturn(one)
                .thenReturn(two)
                .thenReturn(three)
                .thenReturn(four);

        assertThrows(AssertionError.class, testClass::testRequirementC55);
    }

    /**
     * Tests whether the test fails if not all mds are included in the getMdDescriptionResponse and the handle
     * reference was empty.
     */
    @Test
    public void testRequirementC55BadNotAllMdsReturnedOnEmptyHandleRef() {
        setupMockDevice(true);
        final var one = buildGetMdDescriptionResponse(List.of(MdibBuilder.DEFAULT_MDS_HANDLE));
        final var two = buildGetMdDescriptionResponse(List.of(MdibBuilder.DEFAULT_MDS_HANDLE));
        final var three = buildGetMdDescriptionResponse(List.of(SECOND_MDS_HANDLE));
        final var four = buildGetMdDescriptionResponse(
                List.of(MdibBuilder.DEFAULT_MDS_HANDLE, SECOND_MDS_HANDLE, THIRD_MDS_HANDLE));
        when(messageGeneratingUtil.getMdDescription(any()))
                .thenReturn(one)
                .thenReturn(two)
                .thenReturn(three)
                .thenReturn(four);

        assertThrows(AssertionError.class, testClass::testRequirementC55);
    }

    /**
     * Tests whether the test fails if an mds is not included in the getMdDescriptionResponse and the handle of that
     * mds was included in the handle reference.
     */
    @Test
    public void testRequirementC55BadMdsMissingWhenHandleInHandleRef() {
        setupMockDevice(true);
        final var one = buildGetMdDescriptionResponse(
                List.of(MdibBuilder.DEFAULT_MDS_HANDLE, SECOND_MDS_HANDLE, THIRD_MDS_HANDLE));
        // wrong mds in response, first mds expected
        final var two = buildGetMdDescriptionResponse(List.of(SECOND_MDS_HANDLE));
        final var three = buildGetMdDescriptionResponse(List.of(SECOND_MDS_HANDLE));
        final var four = buildGetMdDescriptionResponse(
                List.of(MdibBuilder.DEFAULT_MDS_HANDLE, SECOND_MDS_HANDLE, THIRD_MDS_HANDLE));
        when(messageGeneratingUtil.getMdDescription(any()))
                .thenReturn(one)
                .thenReturn(two)
                .thenReturn(three)
                .thenReturn(four);
        assertThrows(AssertionError.class, testClass::testRequirementC55);
    }

    /**
     * Tests whether the test fails if an mds is not included in the getMdDescriptionResponse and the handle of a child
     * element was included in the handle reference.
     */
    @Test
    public void testRequirementC55BadWrongMdsReturnedOnChildHandleRef() {
        setupMockDevice(true);
        final var one = buildGetMdDescriptionResponse(
                List.of(MdibBuilder.DEFAULT_MDS_HANDLE, SECOND_MDS_HANDLE, THIRD_MDS_HANDLE));
        final var two = buildGetMdDescriptionResponse(List.of(MdibBuilder.DEFAULT_MDS_HANDLE));
        // second mds is the only mds with children, second mds expected
        final var three = buildGetMdDescriptionResponse(List.of(MdibBuilder.DEFAULT_MDS_HANDLE));
        final var four = buildGetMdDescriptionResponse(
                List.of(MdibBuilder.DEFAULT_MDS_HANDLE, SECOND_MDS_HANDLE, THIRD_MDS_HANDLE));
        when(messageGeneratingUtil.getMdDescription(any()))
                .thenReturn(one)
                .thenReturn(two)
                .thenReturn(three)
                .thenReturn(four);

        assertThrows(AssertionError.class, testClass::testRequirementC55);
    }

    /**
     * Tests whether the test fails if not all expected mds are included in the getMdDescriptionResponse for every
     * known handle reference and a handle in the handle reference does not match any descriptor.
     */
    @Test
    public void testRequirementC55BadNoMatchingDescriptor() {
        setupMockDevice(true);
        final var one = buildGetMdDescriptionResponse(
                List.of(MdibBuilder.DEFAULT_MDS_HANDLE, SECOND_MDS_HANDLE, THIRD_MDS_HANDLE));
        final var two = buildGetMdDescriptionResponse(List.of(MdibBuilder.DEFAULT_MDS_HANDLE));
        final var three = buildGetMdDescriptionResponse(List.of(SECOND_MDS_HANDLE));
        // not the expected mds included in response
        final var four = buildGetMdDescriptionResponse(List.of(SECOND_MDS_HANDLE));
        when(messageGeneratingUtil.getMdDescription(any()))
                .thenReturn(one)
                .thenReturn(two)
                .thenReturn(three)
                .thenReturn(four);

        assertThrows(AssertionError.class, testClass::testRequirementC55);
    }

    private SoapMessage buildGetMdDescriptionResponse(final List<String> mdsHandles) {
        final var mockMdsList = new ArrayList<MdsDescriptor>();
        for (var handle : mdsHandles) {
            final var mockMds = mock(MdsDescriptor.class);
            when(mockMds.getHandle()).thenReturn(handle);
            mockMdsList.add(mockMds);
        }
        final var mockMdDescription = mock(MdDescription.class);
        when(mockMdDescription.getMds()).thenReturn(mockMdsList);
        final var mockGetMdDescriptionResponse = mock(GetMdDescriptionResponse.class);
        when(mockGetMdDescriptionResponse.getMdDescription()).thenReturn(mockMdDescription);
        final var soapMessage = mock(SoapMessage.class, RETURNS_DEEP_STUBS);
        when(soapMessage.getOriginalEnvelope().getBody().getAny().get(0)).thenReturn(mockGetMdDescriptionResponse);
        return soapMessage;
    }

    private <T extends AbstractDescriptor> MdibEntity buildEntity(final String handle, final Class<T> descriptorClass) {
        final var firstMds = mock(descriptorClass);
        when(firstMds.getHandle()).thenReturn(handle);
        final var firstMdsEntity = mock(MdibEntity.class);
        when(firstMdsEntity.getDescriptor(descriptorClass)).thenReturn(Optional.of(firstMds));
        when(firstMdsEntity.getHandle()).thenReturn(handle);
        return firstMdsEntity;
    }

    private void setupMockDevice(final boolean mdsChildrenPresent) {
        final var allEntities = new ArrayList<MdibEntity>();
        final var firstMdsEntity = buildEntity(MdibBuilder.DEFAULT_MDS_HANDLE, MdsDescriptor.class);
        final var secondMdsEntity = buildEntity(SECOND_MDS_HANDLE, MdsDescriptor.class);
        final var thirdMdsEntity = buildEntity(THIRD_MDS_HANDLE, MdsDescriptor.class);
        if (mdsChildrenPresent) {
            final var channelEntity = buildEntity(SECOND_CHANNEL_HANDLE, ChannelDescriptor.class);
            final var vmdEntity = buildEntity(SECOND_VMD_HANDLE, VmdDescriptor.class);
            when(vmdEntity.getChildren()).thenReturn(List.of(SECOND_CHANNEL_HANDLE));
            when(secondMdsEntity.getChildren()).thenReturn(List.of(SECOND_VMD_HANDLE));
            allEntities.addAll(List.of(channelEntity, vmdEntity));
            when(mockDevice.getMdibAccess().getEntity(any())).thenReturn(Optional.of(vmdEntity));
        }
        when(mockDevice.getMdibAccess().findEntitiesByType(MdsDescriptor.class))
                .thenReturn(List.of(firstMdsEntity, secondMdsEntity, thirdMdsEntity));
        allEntities.addAll(List.of(firstMdsEntity, secondMdsEntity, thirdMdsEntity));
        when(mockDevice.getMdibAccess().findEntitiesByType(AbstractDescriptor.class))
                .thenReturn(allEntities);
    }
}
