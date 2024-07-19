/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2023-2024 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.manipulation;

import com.draeger.medical.sdccc.messages.ManipulationInfo;
import com.draeger.medical.sdccc.messages.guice.ManipulationInfoFactory;
import com.draeger.medical.sdccc.tests.annotations.TestDescription;
import com.draeger.medical.t2iapi.BasicRequests;
import com.draeger.medical.t2iapi.BasicResponses;
import com.draeger.medical.t2iapi.ResponseTypes;
import com.draeger.medical.t2iapi.context.ContextRequests;
import com.draeger.medical.t2iapi.context.ContextServiceGrpc;
import com.draeger.medical.t2iapi.context.ContextTypes;
import com.draeger.medical.t2iapi.device.DeviceRequests;
import com.draeger.medical.t2iapi.device.DeviceResponses;
import com.draeger.medical.t2iapi.device.DeviceServiceGrpc;
import com.google.common.util.concurrent.SettableFuture;
import com.google.gson.GsonBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.somda.sdc.biceps.model.participant.LocationDetail;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.List;
import java.util.function.BiFunction;

import static com.draeger.medical.t2iapi.context.ContextServiceGrpc.getSetLocationDetailMethod;
import static com.draeger.medical.t2iapi.device.DeviceServiceGrpc.getGetRemovableDescriptorsOfClassMethod;
import static com.draeger.medical.t2iapi.device.DeviceServiceGrpc.getInsertDescriptorMethod;
import static com.draeger.medical.t2iapi.device.DeviceServiceGrpc.getRemoveDescriptorMethod;
import static io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the gRPC {@linkplain Manipulations} implementation.
 */
public class GRpcManipulationsTest {
    private static final Logger LOG = LogManager.getLogger(GRpcManipulationsTest.class);

    private static final int TEST_TIMEOUT = 20;

    private FallbackManipulations fallback;
    private GRpcManipulations manipulations;
    private ContextStub contextHandler;
    private DeviceStub deviceHandler;
    private Server server;

    @BeforeEach
    void setUp() throws IOException {
        contextHandler = new ContextStub();
        deviceHandler = new DeviceStub();
        server = ServerBuilder.forPort(0)
                .addService(contextHandler)
                .addService(deviceHandler)
                .build();
        server.start();

        final String serverAddress = "localhost:" + server.getPort();
        LOG.info("Server is up at {}", serverAddress);
        fallback = mock(FallbackManipulations.class);

        final ManipulationInfoFactory manipulationInfoFactory = mock(ManipulationInfoFactory.class);
        final ManipulationInfo manipulationInfo = mock(ManipulationInfo.class);
        when(manipulationInfoFactory.create(anyLong(), anyLong(), any(), anyString(), anyString(), any()))
                .thenReturn(manipulationInfo);
        manipulations = new GRpcManipulations(
                serverAddress, fallback, manipulationInfoFactory, new GsonManipulationSerializer(new GsonBuilder()));
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        server.shutdownNow().awaitTermination();
    }

    /**
     * Verifies whether data is correctly transmitted to server and results are sent correctly.
     *
     * @throws Exception on any exception
     */
    @Test
    @Timeout(TEST_TIMEOUT)
    @TestDescription("Verifies whether data is correctly transmitted to server and results are sent correctly")
    public void testSetLocationDetail() throws Exception {
        final var location = new LocationDetail();
        location.setFacility("UnitTestFacility");
        location.setBed("FäncyBed");

        // success
        {
            final SettableFuture<ContextTypes.LocationDetail> requestLocation = SettableFuture.create();
            contextHandler.setSetLocationDetailCall((request, responseObserver) -> {
                requestLocation.set(request.getLocation());
                final var reply = BasicResponses.BasicResponse.newBuilder()
                        .setResult(ResponseTypes.Result.RESULT_SUCCESS)
                        .build();
                responseObserver.onNext(reply);
                responseObserver.onCompleted();
                return null;
            });

            final var response = manipulations.setLocationDetail(location).getResult();
            assertSame(response, ResponseTypes.Result.RESULT_SUCCESS, "manipulation failed, but shouldn't have");
            final var actualRequestLocation = requestLocation.get();
            compareLocation(location, actualRequestLocation);
            verifyNoInteractions(fallback);
        }
        // failure
        {
            final SettableFuture<ContextTypes.LocationDetail> requestLocation = SettableFuture.create();
            contextHandler.setSetLocationDetailCall((request, responseObserver) -> {
                requestLocation.set(request.getLocation());
                final var reply = BasicResponses.BasicResponse.newBuilder()
                        .setResult(ResponseTypes.Result.RESULT_FAIL)
                        .build();
                responseObserver.onNext(reply);
                responseObserver.onCompleted();
                return null;
            });

            final var response = manipulations.setLocationDetail(location).getResult();
            assertNotSame(response, ResponseTypes.Result.RESULT_SUCCESS, "manipulation succeeded but shouldn't have");
            final var actualRequestLocation = requestLocation.get();
            compareLocation(location, actualRequestLocation);
            verifyNoInteractions(fallback);
        }
    }

    /**
     * Verifies whether an exception on the server triggers the fallback interaction.
     */
    @Test
    @Timeout(TEST_TIMEOUT)
    @TestDescription("Verifies whether an exception on the server triggers the fallback interaction")
    public void testSetLocationDetailExceptionFallback() {
        when(fallback.setLocationDetail(any())).thenReturn(ResultResponse.success());
        final var location = new LocationDetail();
        location.setFacility("UnitTestFacility");

        // exception while calling
        contextHandler.setSetLocationDetailCall((request, responseObserver) -> {
            throw new RuntimeException("This is an expected exception, don't worry.");
        });

        manipulations.setLocationDetail(location);
        verify(fallback, times(1)).setLocationDetail(location);
    }

    /**
     * Verifies whether an unimplemented response on the server triggers the fallback interaction.
     */
    @Test
    @Timeout(TEST_TIMEOUT)
    @TestDescription("Verifies whether an unimplemented response on the server triggers the fallback interaction")
    public void testSetLocationDetailUnimplementedFallback() {
        when(fallback.setLocationDetail(any())).thenReturn(ResultResponse.notImplemented());

        final var location = new LocationDetail();
        location.setFacility("UnitTestFacility");

        // exception while calling
        contextHandler.setSetLocationDetailCall((request, responseObserver) -> {
            final var reply = BasicResponses.BasicResponse.newBuilder()
                    .setResult(ResponseTypes.Result.RESULT_NOT_IMPLEMENTED)
                    .build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
            return null;
        });
        manipulations.setLocationDetail(location);
        verify(fallback, times(1)).setLocationDetail(location);
    }

    /**
     * Verifies whether data is correctly transmitted to server and results are sent correctly.
     */
    @Test
    @Timeout(TEST_TIMEOUT)
    @TestDescription("Verifies whether data is correctly transmitted to server and results are sent correctly")
    public void testGetRemovableDescriptors() {
        final var removableHandles =
                List.of("Kakakaka", "Kukuku", "K͔̟̜͖̰̿̌͌ͧ͛ͅȉ̞͍̪̩ͣ̿̽͆͞k̰̙̣͗͒ͯͦi̮̺̪͍͉̇ͨ̏̈̿͆̍k̥̙̞ͪͣͅi͐ͪ");

        // success
        {
            deviceHandler.setGetRemovableDescriptorsCall((request, responseObserver) -> {
                final var reply = DeviceResponses.GetRemovableDescriptorsResponse.newBuilder()
                        .setStatus(BasicResponses.BasicResponse.newBuilder()
                                .setResult(ResponseTypes.Result.RESULT_SUCCESS)
                                .build())
                        .addAllHandle(removableHandles)
                        .build();
                responseObserver.onNext(reply);
                responseObserver.onCompleted();
                return null;
            });

            final var response = manipulations.getRemovableDescriptorsOfClass().getResponse();
            assertNotNull(response, "manipulation failed, but shouldn't have");
            assertEquals(removableHandles, response);
            verifyNoInteractions(fallback);
        }
        // failure
        {
            deviceHandler.setGetRemovableDescriptorsCall((request, responseObserver) -> {
                final var reply = DeviceResponses.GetRemovableDescriptorsResponse.newBuilder()
                        .setStatus(BasicResponses.BasicResponse.newBuilder()
                                .setResult(ResponseTypes.Result.RESULT_FAIL)
                                .build())
                        .build();
                responseObserver.onNext(reply);
                responseObserver.onCompleted();
                return null;
            });

            final var response = manipulations.getRemovableDescriptorsOfClass().getResponse();
            // fallback should not have been called here
            verifyNoInteractions(fallback);
            assertTrue(response.isEmpty(), "manipulation succeeded but shouldn't have");
        }
    }

    /**
     * Verifies whether data is correctly transmitted to server and results are sent correctly.
     *
     * @throws Exception on any exception
     */
    @Test
    @Timeout(TEST_TIMEOUT)
    @TestDescription("Verifies whether data is correctly transmitted to server and results are sent correctly")
    public void testInsertDescriptor() throws Exception {
        final var requestHandle = "blablablubHandle123";

        // success
        {
            final SettableFuture<String> receivedHandle = SettableFuture.create();
            deviceHandler.setInsertDescriptorCall((request, responseObserver) -> {
                assertEquals(requestHandle, request.getHandle());
                receivedHandle.set(request.getHandle());
                final var reply = BasicResponses.BasicResponse.newBuilder()
                        .setResult(ResponseTypes.Result.RESULT_SUCCESS)
                        .build();
                responseObserver.onNext(reply);
                responseObserver.onCompleted();
                return null;
            });

            final var response = manipulations.insertDescriptor(requestHandle).getResult();
            assertSame(response, ResponseTypes.Result.RESULT_SUCCESS, "manipulation failed, but shouldn't have");
            assertEquals(requestHandle, receivedHandle.get());
            verifyNoInteractions(fallback);
        }
        // failure
        {
            final SettableFuture<String> receivedHandle = SettableFuture.create();
            deviceHandler.setInsertDescriptorCall((request, responseObserver) -> {
                assertEquals(requestHandle, request.getHandle());
                receivedHandle.set(request.getHandle());
                final var reply = BasicResponses.BasicResponse.newBuilder()
                        .setResult(ResponseTypes.Result.RESULT_FAIL)
                        .build();
                responseObserver.onNext(reply);
                responseObserver.onCompleted();
                return null;
            });

            final var response = manipulations.insertDescriptor(requestHandle).getResult();
            assertNotSame(response, ResponseTypes.Result.RESULT_SUCCESS, "manipulation succeeded but shouldn't have");
            assertEquals(requestHandle, receivedHandle.get());
            verifyNoInteractions(fallback);
        }
        // not_implemented
        {
            when(fallback.insertDescriptor(any())).thenReturn(ResultResponse.notImplemented());

            final SettableFuture<String> receivedHandle = SettableFuture.create();
            deviceHandler.setInsertDescriptorCall((request, responseObserver) -> {
                assertEquals(requestHandle, request.getHandle());
                receivedHandle.set(request.getHandle());
                final var reply = BasicResponses.BasicResponse.newBuilder()
                        .setResult(ResponseTypes.Result.RESULT_NOT_IMPLEMENTED)
                        .build();
                responseObserver.onNext(reply);
                responseObserver.onCompleted();
                return null;
            });

            final var response = manipulations.insertDescriptor(requestHandle).getResult();
            assertNotSame(response, ResponseTypes.Result.RESULT_SUCCESS, "manipulation succeeded but shouldn't have");
            assertEquals(requestHandle, receivedHandle.get());
            verify(fallback, times(1)).insertDescriptor(requestHandle);
        }
    }

    /**
     * Verifies whether data is correctly transmitted to server and results are sent correctly.
     *
     * @throws Exception on any exception
     */
    @Test
    @Timeout(TEST_TIMEOUT)
    @TestDescription("Verifies whether data is correctly transmitted to server and results are sent correctly")
    public void testRemoveDescriptor() throws Exception {
        final var requestHandle = "blablablubHandle123";

        // success
        {
            final SettableFuture<String> receivedHandle = SettableFuture.create();
            deviceHandler.setRemoveDescriptorCall((request, responseObserver) -> {
                assertEquals(requestHandle, request.getHandle());
                receivedHandle.set(request.getHandle());
                final var reply = BasicResponses.BasicResponse.newBuilder()
                        .setResult(ResponseTypes.Result.RESULT_SUCCESS)
                        .build();
                responseObserver.onNext(reply);
                responseObserver.onCompleted();
                return null;
            });

            final var response = manipulations.removeDescriptor(requestHandle).getResult();
            assertSame(response, ResponseTypes.Result.RESULT_SUCCESS, "manipulation failed, but shouldn't have");
            assertEquals(requestHandle, receivedHandle.get());
            verifyNoInteractions(fallback);
        }
        // failure
        {
            final SettableFuture<String> receivedHandle = SettableFuture.create();
            deviceHandler.setRemoveDescriptorCall((request, responseObserver) -> {
                assertEquals(requestHandle, request.getHandle());
                receivedHandle.set(request.getHandle());
                final var reply = BasicResponses.BasicResponse.newBuilder()
                        .setResult(ResponseTypes.Result.RESULT_FAIL)
                        .build();
                responseObserver.onNext(reply);
                responseObserver.onCompleted();
                return null;
            });

            final var response = manipulations.removeDescriptor(requestHandle).getResult();
            assertNotSame(response, ResponseTypes.Result.RESULT_SUCCESS, "manipulation succeeded but shouldn't have");
            assertEquals(requestHandle, receivedHandle.get());
            verifyNoInteractions(fallback);
        }
        // not implemented
        {
            when(fallback.removeDescriptor(any())).thenReturn(ResultResponse.notImplemented());

            final SettableFuture<String> receivedHandle = SettableFuture.create();
            deviceHandler.setRemoveDescriptorCall((request, responseObserver) -> {
                assertEquals(requestHandle, request.getHandle());
                receivedHandle.set(request.getHandle());
                final var reply = BasicResponses.BasicResponse.newBuilder()
                        .setResult(ResponseTypes.Result.RESULT_NOT_IMPLEMENTED)
                        .build();
                responseObserver.onNext(reply);
                responseObserver.onCompleted();
                return null;
            });

            final var response = manipulations.removeDescriptor(requestHandle).getResult();
            assertNotSame(response, ResponseTypes.Result.RESULT_SUCCESS, "manipulation succeeded but shouldn't have");
            assertEquals(requestHandle, receivedHandle.get());
            verify(fallback, times(1)).removeDescriptor(requestHandle);
        }
    }

    private void compareLocation(final LocationDetail location, final ContextTypes.LocationDetail protoLocation) {
        compareElement(
                location.getPoC(),
                protoLocation.hasPoc(),
                protoLocation.getPoc().getValue());
        compareElement(
                location.getRoom(),
                protoLocation.hasRoom(),
                protoLocation.getRoom().getValue());
        compareElement(
                location.getBed(),
                protoLocation.hasBed(),
                protoLocation.getBed().getValue());
        compareElement(
                location.getFacility(),
                protoLocation.hasFacility(),
                protoLocation.getFacility().getValue());
        compareElement(
                location.getBuilding(),
                protoLocation.hasBuilding(),
                protoLocation.getBuilding().getValue());
        compareElement(
                location.getFloor(),
                protoLocation.hasFloor(),
                protoLocation.getFloor().getValue());
    }

    private void compareElement(@Nullable final String expected, final boolean presence, final String value) {
        if (expected == null) {
            assertFalse(presence);
        } else {
            assertEquals(expected, value);
        }
    }

    static class ContextStub extends ContextServiceGrpc.ContextServiceImplBase {
        private BiFunction<ContextRequests.SetLocationDetailRequest, StreamObserver<BasicResponses.BasicResponse>, Void>
                setLocationDetailCall = (request, responseObserver) -> {
                    asyncUnimplementedUnaryCall(getSetLocationDetailMethod(), responseObserver);
                    return null;
                };

        public void setSetLocationDetailCall(
                final BiFunction<
                                ContextRequests.SetLocationDetailRequest,
                                StreamObserver<BasicResponses.BasicResponse>,
                                Void>
                        setLocationDetailCallArg) {
            this.setLocationDetailCall = setLocationDetailCallArg;
        }

        @Override
        public void setLocationDetail(
                final ContextRequests.SetLocationDetailRequest request,
                final StreamObserver<BasicResponses.BasicResponse> responseObserver) {
            setLocationDetailCall.apply(request, responseObserver);
        }
    }

    static class DeviceStub extends DeviceServiceGrpc.DeviceServiceImplBase {
        private BiFunction<
                        DeviceRequests.GetRemovableDescriptorsOfClassRequest,
                        StreamObserver<DeviceResponses.GetRemovableDescriptorsResponse>,
                        Void>
                getRemovableDescriptorsCall = (request, responseObserver) -> {
                    asyncUnimplementedUnaryCall(getGetRemovableDescriptorsOfClassMethod(), responseObserver);
                    return null;
                };

        private BiFunction<BasicRequests.BasicHandleRequest, StreamObserver<BasicResponses.BasicResponse>, Void>
                removeDescriptorCall = (request, responseObserver) -> {
                    asyncUnimplementedUnaryCall(getRemoveDescriptorMethod(), responseObserver);
                    return null;
                };

        private BiFunction<BasicRequests.BasicHandleRequest, StreamObserver<BasicResponses.BasicResponse>, Void>
                insertDescriptorCall = (request, responseObserver) -> {
                    asyncUnimplementedUnaryCall(getInsertDescriptorMethod(), responseObserver);
                    return null;
                };

        public void setGetRemovableDescriptorsCall(
                final BiFunction<
                                DeviceRequests.GetRemovableDescriptorsOfClassRequest,
                                StreamObserver<DeviceResponses.GetRemovableDescriptorsResponse>,
                                Void>
                        arg) {
            this.getRemovableDescriptorsCall = arg;
        }

        public void setInsertDescriptorCall(
                final BiFunction<BasicRequests.BasicHandleRequest, StreamObserver<BasicResponses.BasicResponse>, Void>
                        insertDescriptorCall) {
            this.insertDescriptorCall = insertDescriptorCall;
        }

        public void setRemoveDescriptorCall(
                final BiFunction<BasicRequests.BasicHandleRequest, StreamObserver<BasicResponses.BasicResponse>, Void>
                        removeDescriptorCall) {
            this.removeDescriptorCall = removeDescriptorCall;
        }

        @Override
        public void getRemovableDescriptorsOfClass(
                final DeviceRequests.GetRemovableDescriptorsOfClassRequest request,
                final StreamObserver<DeviceResponses.GetRemovableDescriptorsResponse> responseObserver) {
            getRemovableDescriptorsCall.apply(request, responseObserver);
        }

        @Override
        public void insertDescriptor(
                final BasicRequests.BasicHandleRequest request,
                final StreamObserver<BasicResponses.BasicResponse> responseObserver) {
            insertDescriptorCall.apply(request, responseObserver);
        }

        @Override
        public void removeDescriptor(
                final BasicRequests.BasicHandleRequest request,
                final StreamObserver<BasicResponses.BasicResponse> responseObserver) {
            removeDescriptorCall.apply(request, responseObserver);
        }
    }
}
