/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2023-2024 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.manipulation;

import static com.draeger.medical.t2iapi.alert.AlertServiceGrpc.getSetAlertConditionPresenceMethod;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.draeger.medical.sdccc.messages.ManipulationInfo;
import com.draeger.medical.sdccc.messages.guice.ManipulationInfoFactory;
import com.draeger.medical.sdccc.tests.annotations.TestDescription;
import com.draeger.medical.sdccc.util.Constants;
import com.draeger.medical.t2iapi.BasicRequests;
import com.draeger.medical.t2iapi.BasicResponses;
import com.draeger.medical.t2iapi.ResponseTypes;
import com.draeger.medical.t2iapi.activation_state.ActivationStateRequests;
import com.draeger.medical.t2iapi.activation_state.ActivationStateServiceGrpc;
import com.draeger.medical.t2iapi.activation_state.ActivationStateTypes;
import com.draeger.medical.t2iapi.alert.AlertRequests;
import com.draeger.medical.t2iapi.alert.AlertServiceGrpc;
import com.draeger.medical.t2iapi.context.ContextRequests;
import com.draeger.medical.t2iapi.context.ContextResponses;
import com.draeger.medical.t2iapi.context.ContextServiceGrpc;
import com.draeger.medical.t2iapi.context.ContextTypes;
import com.draeger.medical.t2iapi.device.DeviceRequests;
import com.draeger.medical.t2iapi.device.DeviceResponses;
import com.draeger.medical.t2iapi.device.DeviceServiceGrpc;
import com.draeger.medical.t2iapi.device.DeviceTypes;
import com.draeger.medical.t2iapi.metric.MetricRequests;
import com.draeger.medical.t2iapi.metric.MetricServiceGrpc;
import com.google.common.util.concurrent.SettableFuture;
import com.google.gson.Gson;
import com.google.protobuf.Empty;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.util.List;
import java.util.function.BiFunction;
import javax.annotation.Nullable;
import javax.xml.namespace.QName;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.somda.sdc.biceps.model.participant.AlertActivation;
import org.somda.sdc.biceps.model.participant.AlertSignalManifestation;
import org.somda.sdc.biceps.model.participant.ComponentActivation;
import org.somda.sdc.biceps.model.participant.ContextAssociation;
import org.somda.sdc.biceps.model.participant.LocationDetail;
import org.somda.sdc.biceps.model.participant.MetricCategory;
import org.somda.sdc.biceps.model.participant.NumericMetricDescriptor;

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
    private ActivationStateStub activationStateHandler;
    private MetricStub metricHandler;
    private AlertStub alertHandler;

    @BeforeEach
    void setUp() throws IOException {
        contextHandler = new ContextStub();
        deviceHandler = new DeviceStub();
        activationStateHandler = new ActivationStateStub();
        metricHandler = new MetricStub();
        alertHandler = new AlertStub();

        server = ServerBuilder.forPort(0)
                .addService(contextHandler)
                .addService(deviceHandler)
                .addService(activationStateHandler)
                .addService(metricHandler)
                .addService(alertHandler)
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
                serverAddress, fallback, manipulationInfoFactory, new GsonManipulationSerializer(new Gson()));
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

            assertTrue(response == null || response.isEmpty(), "manipulation succeeded but shouldn't have");
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

    /**
     * Verifies whether data is correctly transmitted to server and results are sent correctly.
     *
     * @throws Exception on any exception
     */
    @Test
    @Timeout(TEST_TIMEOUT)
    @TestDescription("Verifies whether data is correctly transmitted to server and results are sent correctly")
    public void testGetRemovableDescriptorsOfClassWithParameter() {
        final var removableHandles = List.of("handle1", "handle2", "handle3");

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

            final var response = manipulations
                    .getRemovableDescriptorsOfClass(NumericMetricDescriptor.class)
                    .getResponse();
            assertNotNull(response, "Manipulation failed, but shouldn't have");
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

            final var response = manipulations
                    .getRemovableDescriptorsOfClass(NumericMetricDescriptor.class)
                    .getResponse();

            assertTrue(response == null || response.isEmpty(), "Manipulation succeeded but shouldn't have");
            verifyNoInteractions(fallback);
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
    public void testSetMetricStatus() throws Exception {
        final String sequenceId = "seq123";
        final String handle = "metricHandle";
        final MetricCategory category = MetricCategory.SET;
        final ComponentActivation activation = ComponentActivation.ON;

        // success
        {
            final SettableFuture<MetricRequests.SetMetricStatusRequest> requestFuture = SettableFuture.create();
            metricHandler.setSetMetricStatusCall((request, responseObserver) -> {
                requestFuture.set(request);
                final var reply = BasicResponses.BasicResponse.newBuilder()
                        .setResult(ResponseTypes.Result.RESULT_SUCCESS)
                        .build();
                responseObserver.onNext(reply);
                responseObserver.onCompleted();
                return null;
            });

            final ResultResponse response = manipulations.setMetricStatus(sequenceId, handle, category, activation);
            assertSame(
                    ResponseTypes.Result.RESULT_SUCCESS,
                    response.getResult(),
                    "Manipulation failed, but shouldn't have");
            final var receivedRequest = requestFuture.get();
            assertEquals(handle, receivedRequest.getHandle(), "Metric handle mismatch");
            assertNotNull(receivedRequest.getStatus(), "Metric status should not be null");
            verifyNoInteractions(fallback);
        }

        // failure
        {
            final SettableFuture<MetricRequests.SetMetricStatusRequest> failureRequestFuture = SettableFuture.create();
            metricHandler.setSetMetricStatusCall((request, responseObserver) -> {
                failureRequestFuture.set(request);
                final var reply = BasicResponses.BasicResponse.newBuilder()
                        .setResult(ResponseTypes.Result.RESULT_FAIL)
                        .build();
                responseObserver.onNext(reply);
                responseObserver.onCompleted();
                return null;
            });

            final ResultResponse response = manipulations.setMetricStatus(sequenceId, handle, category, activation);
            assertNotSame(
                    ResponseTypes.Result.RESULT_SUCCESS,
                    response.getResult(),
                    "Manipulation succeeded, but shouldn't have");
            verifyNoInteractions(fallback);
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
    public void testTriggerReport() throws Exception {

        final QName report = Constants.MSG_EPISODIC_METRIC_REPORT;
        final DeviceTypes.ReportType reportType = DeviceTypes.ReportType.REPORT_TYPE_EPISODIC_METRIC_REPORT;

        // success
        {
            final SettableFuture<DeviceTypes.ReportType> receivedReport = SettableFuture.create();

            deviceHandler.setTriggerReportCall((request, responseObserver) -> {
                receivedReport.set(request.getReport());
                final var reply = BasicResponses.BasicResponse.newBuilder()
                        .setResult(ResponseTypes.Result.RESULT_SUCCESS)
                        .build();
                responseObserver.onNext(reply);
                responseObserver.onCompleted();
                return null;
            });

            final var response = manipulations.triggerReport(report).getResult();
            assertSame(response, ResponseTypes.Result.RESULT_SUCCESS, "Manipulation failed, but shouldn't have");
            assertEquals(reportType, receivedReport.get());
            verifyNoInteractions(fallback);
        }

        // failure
        {
            final SettableFuture<DeviceTypes.ReportType> receivedReport = SettableFuture.create();
            deviceHandler.setTriggerReportCall((request, responseObserver) -> {
                receivedReport.set(request.getReport());
                final var reply = BasicResponses.BasicResponse.newBuilder()
                        .setResult(ResponseTypes.Result.RESULT_FAIL)
                        .build();
                responseObserver.onNext(reply);
                responseObserver.onCompleted();
                return null;
            });

            final var response = manipulations.triggerReport(report).getResult();
            assertNotSame(response, ResponseTypes.Result.RESULT_SUCCESS, "Manipulation succeeded but shouldn't have");
            assertEquals(reportType, receivedReport.get());
            verifyNoInteractions(fallback);
        }

        // not implemented
        {
            when(fallback.triggerReport(any())).thenReturn(ResultResponse.notImplemented());
            final SettableFuture<DeviceTypes.ReportType> receivedReport = SettableFuture.create();
            deviceHandler.setTriggerReportCall((request, responseObserver) -> {
                receivedReport.set(request.getReport());
                final var reply = BasicResponses.BasicResponse.newBuilder()
                        .setResult(ResponseTypes.Result.RESULT_NOT_IMPLEMENTED)
                        .build();
                responseObserver.onNext(reply);
                responseObserver.onCompleted();
                return null;
            });

            final var response = manipulations.triggerReport(report).getResult();
            assertNotSame(response, ResponseTypes.Result.RESULT_SUCCESS, "Manipulation succeeded but shouldn't have");
            assertEquals(reportType, receivedReport.get());
            verify(fallback, times(1)).triggerReport(report);
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
    public void testSetSystemSignalActivation() throws Exception {

        final String handle = "testHandle";
        final AlertSignalManifestation manifestation = AlertSignalManifestation.OTH;
        final AlertActivation activation = AlertActivation.ON;

        // success
        {
            final SettableFuture<String> receivedHandle = SettableFuture.create();
            activationStateHandler.setSetSystemSignalActivationCall((request, responseObserver) -> {
                receivedHandle.set(request.getHandle());
                final var reply = BasicResponses.BasicResponse.newBuilder()
                        .setResult(ResponseTypes.Result.RESULT_SUCCESS)
                        .build();
                responseObserver.onNext(reply);
                responseObserver.onCompleted();
                return null;
            });

            final var response = manipulations
                    .setSystemSignalActivation(handle, manifestation, activation)
                    .getResult();

            assertEquals(handle, receivedHandle.get());
            verifyNoInteractions(fallback);
        }

        // failure
        {
            final SettableFuture<String> receivedHandle = SettableFuture.create();
            activationStateHandler.setSetSystemSignalActivationCall((request, responseObserver) -> {
                receivedHandle.set(request.getHandle());
                final var reply = BasicResponses.BasicResponse.newBuilder()
                        .setResult(ResponseTypes.Result.RESULT_FAIL)
                        .build();
                responseObserver.onNext(reply);
                responseObserver.onCompleted();
                return null;
            });

            final var response = manipulations
                    .setSystemSignalActivation(handle, manifestation, activation)
                    .getResult();
            assertNotSame(response, ResponseTypes.Result.RESULT_SUCCESS, "Manipulation succeeded but shouldn't have");

            assertEquals(handle, receivedHandle.get());
            verifyNoInteractions(fallback);
        }

        // not implemented
        {
            when(fallback.setSystemSignalActivation(anyString(), any(), any()))
                    .thenReturn(ResultResponse.notImplemented());
            activationStateHandler.setSetSystemSignalActivationCall((request, responseObserver) -> {
                final var reply = BasicResponses.BasicResponse.newBuilder()
                        .setResult(ResponseTypes.Result.RESULT_NOT_IMPLEMENTED)
                        .build();
                responseObserver.onNext(reply);
                responseObserver.onCompleted();
                return null;
            });
            manipulations.setSystemSignalActivation(handle, manifestation, activation);
            verify(fallback, times(1)).setSystemSignalActivation(handle, manifestation, activation);
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
    public void testSetAlertActivation() throws Exception {
        final String handle = "handle";
        final AlertActivation activationState = AlertActivation.ON;
        final ActivationStateTypes.AlertActivation expected = ActivationStateTypes.AlertActivation.ALERT_ACTIVATION_ON;

        // success
        {
            final SettableFuture<ActivationStateTypes.AlertActivation> receivedActivation = SettableFuture.create();
            activationStateHandler.setSetAlertActivationCall((request, responseObserver) -> {
                assertEquals(handle, request.getHandle());
                receivedActivation.set(request.getActivation());
                final var reply = BasicResponses.BasicResponse.newBuilder()
                        .setResult(ResponseTypes.Result.RESULT_SUCCESS)
                        .build();
                responseObserver.onNext(reply);
                responseObserver.onCompleted();
                return null;
            });

            final ResultResponse response = manipulations.setAlertActivation(handle, activationState);
            assertSame(
                    response.getResult(),
                    ResponseTypes.Result.RESULT_SUCCESS,
                    "Manipulation failed, but shouldn't have");
            assertEquals(expected, receivedActivation.get());
            verifyNoInteractions(fallback);
        }

        // failure
        {
            final SettableFuture<ActivationStateTypes.AlertActivation> receivedActivation = SettableFuture.create();
            activationStateHandler.setSetAlertActivationCall((request, responseObserver) -> {
                assertEquals(handle, request.getHandle());
                receivedActivation.set(request.getActivation());
                final var reply = BasicResponses.BasicResponse.newBuilder()
                        .setResult(ResponseTypes.Result.RESULT_FAIL)
                        .build();
                responseObserver.onNext(reply);
                responseObserver.onCompleted();
                return null;
            });

            final ResultResponse response = manipulations.setAlertActivation(handle, activationState);
            assertNotSame(
                    response.getResult(),
                    ResponseTypes.Result.RESULT_SUCCESS,
                    "Manipulation succeeded but shouldn't have");
            assertEquals(expected, receivedActivation.get());
            verifyNoInteractions(fallback);
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
    public void testCreateContextStateWithAssociationSuccessAndFailure() throws Exception {
        final String descriptorHandle = "descHandle";
        final String expectedContextStateHandle = "contextHandle";

        final ContextAssociation association = ContextAssociation.NO;

        // success
        {
            final SettableFuture<ContextRequests.CreateContextStateWithAssociationRequest> requestFuture =
                    SettableFuture.create();
            contextHandler.setCreateContextStateWithAssociationCall((request, responseObserver) -> {
                requestFuture.set(request);
                final ContextResponses.CreateContextStateWithAssociationResponse reply =
                        ContextResponses.CreateContextStateWithAssociationResponse.newBuilder()
                                .setStatus(BasicResponses.BasicResponse.newBuilder()
                                        .setResult(ResponseTypes.Result.RESULT_SUCCESS)
                                        .build())
                                .setContextStateHandle(expectedContextStateHandle)
                                .build();
                responseObserver.onNext(reply);
                responseObserver.onCompleted();
                return null;
            });

            final ManipulationResponse<String> response =
                    manipulations.createContextStateWithAssociation(descriptorHandle, association);
            assertNotNull(response, "Manipulation failed, but shouldn't have");
            assertSame(
                    ResponseTypes.Result.RESULT_SUCCESS,
                    response.getResult(),
                    "Manipulation failed, but shouldn't have");
            assertEquals(expectedContextStateHandle, response.getResponse());
            final ContextRequests.CreateContextStateWithAssociationRequest capturedRequest = requestFuture.get();
            assertEquals(descriptorHandle, capturedRequest.getDescriptorHandle());
            verifyNoInteractions(fallback);
        }

        // failure
        {
            final SettableFuture<ContextRequests.CreateContextStateWithAssociationRequest> requestFutureFailure =
                    SettableFuture.create();
            contextHandler.setCreateContextStateWithAssociationCall((request, responseObserver) -> {
                requestFutureFailure.set(request);
                final ContextResponses.CreateContextStateWithAssociationResponse reply =
                        ContextResponses.CreateContextStateWithAssociationResponse.newBuilder()
                                .setStatus(BasicResponses.BasicResponse.newBuilder()
                                        .setResult(ResponseTypes.Result.RESULT_FAIL)
                                        .build())
                                .setContextStateHandle("")
                                .build();
                responseObserver.onNext(reply);
                responseObserver.onCompleted();
                return null;
            });

            final ManipulationResponse<String> response =
                    manipulations.createContextStateWithAssociation(descriptorHandle, association);
            assertNotSame(
                    ResponseTypes.Result.RESULT_SUCCESS,
                    response.getResult(),
                    "Manipulation succeeded but shouldn't have");
            verifyNoInteractions(fallback);
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
    public void testTriggerAnyDescriptorUpdate() throws Exception {
        // success
        {
            deviceHandler.setTriggerAnyDescriptorUpdateCall((request, responseObserver) -> {
                final var reply = BasicResponses.BasicResponse.newBuilder()
                        .setResult(ResponseTypes.Result.RESULT_SUCCESS)
                        .build();
                responseObserver.onNext(reply);
                responseObserver.onCompleted();
                return null;
            });

            final var response = manipulations.triggerAnyDescriptorUpdate();
            assertSame(
                    response.getResult(),
                    ResponseTypes.Result.RESULT_SUCCESS,
                    "Manipulation failed, but shouldn't have");
            verifyNoInteractions(fallback);
        }

        // failure
        {
            deviceHandler.setTriggerAnyDescriptorUpdateCall((request, responseObserver) -> {
                final var reply = BasicResponses.BasicResponse.newBuilder()
                        .setResult(ResponseTypes.Result.RESULT_FAIL)
                        .build();
                responseObserver.onNext(reply);
                responseObserver.onCompleted();
                return null;
            });

            final var response = manipulations.triggerAnyDescriptorUpdate();
            assertNotSame(
                    response.getResult(),
                    ResponseTypes.Result.RESULT_SUCCESS,
                    "Manipulation succeeded but shouldn't have");
            verifyNoInteractions(fallback);
        }

        // not implemented
        {
            when(fallback.triggerAnyDescriptorUpdate()).thenReturn(ResultResponse.notImplemented());
            deviceHandler.setTriggerAnyDescriptorUpdateCall((request, responseObserver) -> {
                final var reply = BasicResponses.BasicResponse.newBuilder()
                        .setResult(ResponseTypes.Result.RESULT_NOT_IMPLEMENTED)
                        .build();
                responseObserver.onNext(reply);
                responseObserver.onCompleted();
                return null;
            });

            final var response = manipulations.triggerAnyDescriptorUpdate();
            assertNotSame(
                    response.getResult(),
                    ResponseTypes.Result.RESULT_SUCCESS,
                    "Manipulation succeeded but shouldn't have");
            verify(fallback, times(1)).triggerAnyDescriptorUpdate();
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
    public void testSetComponentActivationSuccessAndFailure() throws Exception {
        final String handle = "handle";
        final ComponentActivation activationState = ComponentActivation.ON;

        // success
        {
            final SettableFuture<ActivationStateRequests.SetComponentActivationRequest> requestFuture =
                    SettableFuture.create();
            activationStateHandler.setSetComponentActivationCall((request, responseObserver) -> {
                requestFuture.set(request);
                final var reply = BasicResponses.BasicResponse.newBuilder()
                        .setResult(ResponseTypes.Result.RESULT_SUCCESS)
                        .build();
                responseObserver.onNext(reply);
                responseObserver.onCompleted();
                return null;
            });

            final var response = manipulations
                    .setComponentActivation(handle, activationState)
                    .getResult();
            assertSame(response, ResponseTypes.Result.RESULT_SUCCESS, "Manipulation failed, but shouldn't have");
            final var actualRequest = requestFuture.get();
            assertEquals(handle, actualRequest.getHandle(), "The handle in the request does not match.");
            verifyNoInteractions(fallback);
        }
        // failure
        {
            final SettableFuture<ActivationStateRequests.SetComponentActivationRequest> requestFuture =
                    SettableFuture.create();
            activationStateHandler.setSetComponentActivationCall((request, responseObserver) -> {
                requestFuture.set(request);
                final var reply = BasicResponses.BasicResponse.newBuilder()
                        .setResult(ResponseTypes.Result.RESULT_FAIL)
                        .build();
                responseObserver.onNext(reply);
                responseObserver.onCompleted();
                return null;
            });

            final var response = manipulations
                    .setComponentActivation(handle, activationState)
                    .getResult();
            assertNotSame(response, ResponseTypes.Result.RESULT_SUCCESS, "Manipulation succeeded but shouldn't have");
            verifyNoInteractions(fallback);
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
    public void testSetAlertConditionPresence() throws Exception {
        final String handle = "handle";
        final boolean presence = true;

        // success
        {
            final SettableFuture<AlertRequests.SetAlertConditionPresenceRequest> requestFuture =
                    SettableFuture.create();
            alertHandler.setSetAlertConditionPresenceCall((request, responseObserver) -> {
                requestFuture.set(request);
                final var reply = BasicResponses.BasicResponse.newBuilder()
                        .setResult(ResponseTypes.Result.RESULT_SUCCESS)
                        .build();
                responseObserver.onNext(reply);
                responseObserver.onCompleted();
                return null;
            });

            final var response =
                    manipulations.setAlertConditionPresence(handle, presence).getResult();
            assertSame(response, ResponseTypes.Result.RESULT_SUCCESS, "Manipulation failed, but shouldn't have");
            final var receivedRequest = requestFuture.get();
            assertEquals(handle, receivedRequest.getHandle(), "Handle mismatch in alert request");
            assertEquals(presence, receivedRequest.getPresence(), "Presence flag mismatch in alert request");
            verifyNoInteractions(fallback);
        }

        // failure
        {
            final SettableFuture<AlertRequests.SetAlertConditionPresenceRequest> requestFuture =
                    SettableFuture.create();
            alertHandler.setSetAlertConditionPresenceCall((request, responseObserver) -> {
                requestFuture.set(request);
                final var reply = BasicResponses.BasicResponse.newBuilder()
                        .setResult(ResponseTypes.Result.RESULT_FAIL)
                        .build();
                responseObserver.onNext(reply);
                responseObserver.onCompleted();
                return null;
            });

            final var response =
                    manipulations.setAlertConditionPresence(handle, presence).getResult();
            assertNotSame(response, ResponseTypes.Result.RESULT_SUCCESS, "Manipulation succeeded but shouldn't have");
            final var receivedRequest = requestFuture.get();
            assertEquals(handle, receivedRequest.getHandle(), "Handle mismatch in alert request");
            assertEquals(presence, receivedRequest.getPresence(), "Presence flag mismatch in alert request");
            verifyNoInteractions(fallback);
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
    public void testSendHello() throws Exception {
        // success
        {
            deviceHandler.setSendHelloCall((request, responseObserver) -> {
                final var reply = BasicResponses.BasicResponse.newBuilder()
                        .setResult(ResponseTypes.Result.RESULT_SUCCESS)
                        .build();
                responseObserver.onNext(reply);
                responseObserver.onCompleted();
                return null;
            });
            final var response = manipulations.sendHello().getResult();
            assertSame(response, ResponseTypes.Result.RESULT_SUCCESS, "Manipulation failed, but shouldn't have");
            verifyNoInteractions(fallback);
        }
        // failure
        {
            deviceHandler.setSendHelloCall((request, responseObserver) -> {
                final var reply = BasicResponses.BasicResponse.newBuilder()
                        .setResult(ResponseTypes.Result.RESULT_FAIL)
                        .build();
                responseObserver.onNext(reply);
                responseObserver.onCompleted();
                return null;
            });
            final var response = manipulations.sendHello().getResult();
            assertNotSame(response, ResponseTypes.Result.RESULT_SUCCESS, "Manipulation succeeded but shouldn't have");
            verifyNoInteractions(fallback);
        }
        // not implemented
        {
            when(fallback.sendHello()).thenReturn(ResultResponse.notImplemented());
            deviceHandler.setSendHelloCall((request, responseObserver) -> {
                final var reply = BasicResponses.BasicResponse.newBuilder()
                        .setResult(ResponseTypes.Result.RESULT_NOT_IMPLEMENTED)
                        .build();
                responseObserver.onNext(reply);
                responseObserver.onCompleted();
                return null;
            });
            final var response = manipulations.sendHello().getResult();
            assertNotSame(response, ResponseTypes.Result.RESULT_SUCCESS, "Manipulation succeeded but shouldn't have");
            verify(fallback, times(1)).sendHello();
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
    public void testTriggerDescriptorUpdateSingleHandle() throws Exception {
        final String handle = "handle";

        // success
        {
            final SettableFuture<List<String>> receivedHandles = SettableFuture.create();
            deviceHandler.setTriggerDescriptorUpdateCall((request, responseObserver) -> {
                receivedHandles.set(request.getHandleList());
                final var reply = BasicResponses.BasicResponse.newBuilder()
                        .setResult(ResponseTypes.Result.RESULT_SUCCESS)
                        .build();
                responseObserver.onNext(reply);
                responseObserver.onCompleted();
                return null;
            });

            final var response = manipulations.triggerDescriptorUpdate(handle).getResult();
            assertSame(response, ResponseTypes.Result.RESULT_SUCCESS, "Manipulation failed, but shouldn't have");
            assertEquals(List.of(handle), receivedHandles.get());
            verifyNoInteractions(fallback);
        }

        // failure
        {
            final SettableFuture<List<String>> receivedHandles = SettableFuture.create();
            deviceHandler.setTriggerDescriptorUpdateCall((request, responseObserver) -> {
                receivedHandles.set(request.getHandleList());
                final var reply = BasicResponses.BasicResponse.newBuilder()
                        .setResult(ResponseTypes.Result.RESULT_FAIL)
                        .build();
                responseObserver.onNext(reply);
                responseObserver.onCompleted();
                return null;
            });

            final var response = manipulations.triggerDescriptorUpdate(handle).getResult();
            assertNotSame(response, ResponseTypes.Result.RESULT_SUCCESS, "Manipulation succeeded but shouldn't have");
            assertEquals(List.of(handle), receivedHandles.get());
            verifyNoInteractions(fallback);
        }

        // not implemented
        {
            when(fallback.triggerDescriptorUpdate(anyList())).thenReturn(ResultResponse.notImplemented());

            final SettableFuture<List<String>> receivedHandles = SettableFuture.create();
            deviceHandler.setTriggerDescriptorUpdateCall((request, responseObserver) -> {
                receivedHandles.set(request.getHandleList());
                final var reply = BasicResponses.BasicResponse.newBuilder()
                        .setResult(ResponseTypes.Result.RESULT_NOT_IMPLEMENTED)
                        .build();
                responseObserver.onNext(reply);
                responseObserver.onCompleted();
                return null;
            });

            final var response = manipulations.triggerDescriptorUpdate(handle).getResult();
            assertNotSame(response, ResponseTypes.Result.RESULT_SUCCESS, "Manipulation succeeded but shouldn't have");
            assertEquals(List.of(handle), receivedHandles.get());
            verify(fallback, times(1)).triggerDescriptorUpdate(List.of(handle));
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
    public void testTriggerDescriptorUpdateListHandles() throws Exception {
        final List<String> handles = List.of("handle1", "handle2", "handle3");

        // success
        {
            final SettableFuture<List<String>> receivedHandles = SettableFuture.create();
            deviceHandler.setTriggerDescriptorUpdateCall((request, responseObserver) -> {
                receivedHandles.set(request.getHandleList());
                final var reply = BasicResponses.BasicResponse.newBuilder()
                        .setResult(ResponseTypes.Result.RESULT_SUCCESS)
                        .build();
                responseObserver.onNext(reply);
                responseObserver.onCompleted();
                return null;
            });

            final var response = manipulations.triggerDescriptorUpdate(handles).getResult();
            assertSame(response, ResponseTypes.Result.RESULT_SUCCESS, "Manipulation failed, but shouldn't have");
            assertEquals(handles, receivedHandles.get());
            verifyNoInteractions(fallback);
        }

        // failure
        {
            final SettableFuture<List<String>> receivedHandles = SettableFuture.create();
            deviceHandler.setTriggerDescriptorUpdateCall((request, responseObserver) -> {
                receivedHandles.set(request.getHandleList());
                final var reply = BasicResponses.BasicResponse.newBuilder()
                        .setResult(ResponseTypes.Result.RESULT_FAIL)
                        .build();
                responseObserver.onNext(reply);
                responseObserver.onCompleted();
                return null;
            });

            final var response = manipulations.triggerDescriptorUpdate(handles).getResult();
            assertNotSame(response, ResponseTypes.Result.RESULT_SUCCESS, "Manipulation succeeded but shouldn't have");
            assertEquals(handles, receivedHandles.get());
            verifyNoInteractions(fallback);
        }

        // not implemented
        {
            when(fallback.triggerDescriptorUpdate(anyList())).thenReturn(ResultResponse.notImplemented());

            final SettableFuture<List<String>> receivedHandles = SettableFuture.create();
            deviceHandler.setTriggerDescriptorUpdateCall((request, responseObserver) -> {
                receivedHandles.set(request.getHandleList());
                final var reply = BasicResponses.BasicResponse.newBuilder()
                        .setResult(ResponseTypes.Result.RESULT_NOT_IMPLEMENTED)
                        .build();
                responseObserver.onNext(reply);
                responseObserver.onCompleted();
                return null;
            });

            final var response = manipulations.triggerDescriptorUpdate(handles).getResult();
            assertNotSame(response, ResponseTypes.Result.RESULT_SUCCESS, "Manipulation succeeded but shouldn't have");
            assertEquals(handles, receivedHandles.get());
            verify(fallback, times(1)).triggerDescriptorUpdate(handles);
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

        private BiFunction<
                        ContextRequests.CreateContextStateWithAssociationRequest,
                        StreamObserver<ContextResponses.CreateContextStateWithAssociationResponse>,
                        Void>
                createContextStateWithAssociationCall = (request, responseObserver) -> {
                    asyncUnimplementedUnaryCall(
                            ContextServiceGrpc.getCreateContextStateWithAssociationMethod(), responseObserver);
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

        public void setCreateContextStateWithAssociationCall(
                final BiFunction<
                                ContextRequests.CreateContextStateWithAssociationRequest,
                                StreamObserver<ContextResponses.CreateContextStateWithAssociationResponse>,
                                Void>
                        call) {
            this.createContextStateWithAssociationCall = call;
        }

        @Override
        public void createContextStateWithAssociation(
                final ContextRequests.CreateContextStateWithAssociationRequest request,
                final StreamObserver<ContextResponses.CreateContextStateWithAssociationResponse> responseObserver) {
            createContextStateWithAssociationCall.apply(request, responseObserver);
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

        private BiFunction<DeviceRequests.TriggerReportRequest, StreamObserver<BasicResponses.BasicResponse>, Void>
                triggerReportCall = (request, responseObserver) -> {
                    asyncUnimplementedUnaryCall(DeviceServiceGrpc.getTriggerReportMethod(), responseObserver);
                    return null;
                };

        private BiFunction<com.google.protobuf.Empty, StreamObserver<BasicResponses.BasicResponse>, Void>
                triggerAnyDescriptorUpdateCall = (request, responseObserver) -> {
                    asyncUnimplementedUnaryCall(
                            DeviceServiceGrpc.getTriggerAnyDescriptorUpdateMethod(), responseObserver);
                    return null;
                };

        private BiFunction<Empty, StreamObserver<BasicResponses.BasicResponse>, Void> sendHelloCall =
                (request, responseObserver) -> {
                    asyncUnimplementedUnaryCall(DeviceServiceGrpc.getSendHelloMethod(), responseObserver);
                    return null;
                };

        private BiFunction<
                        DeviceRequests.TriggerDescriptorUpdateRequest,
                        StreamObserver<BasicResponses.BasicResponse>,
                        Void>
                triggerDescriptorUpdateCall = (request, responseObserver) -> {
                    asyncUnimplementedUnaryCall(DeviceServiceGrpc.getTriggerDescriptorUpdateMethod(), responseObserver);
                    return null;
                };

        public void setTriggerReportCall(
                final BiFunction<
                                DeviceRequests.TriggerReportRequest, StreamObserver<BasicResponses.BasicResponse>, Void>
                        triggerReportCall) {
            this.triggerReportCall = triggerReportCall;
        }

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

        @Override
        public void triggerReport(
                final DeviceRequests.TriggerReportRequest request,
                final StreamObserver<BasicResponses.BasicResponse> responseObserver) {
            triggerReportCall.apply(request, responseObserver);
        }

        public void setTriggerAnyDescriptorUpdateCall(
                final BiFunction<com.google.protobuf.Empty, StreamObserver<BasicResponses.BasicResponse>, Void> call) {
            this.triggerAnyDescriptorUpdateCall = call;
        }

        @Override
        public void triggerAnyDescriptorUpdate(
                final com.google.protobuf.Empty request,
                final StreamObserver<BasicResponses.BasicResponse> responseObserver) {
            triggerAnyDescriptorUpdateCall.apply(request, responseObserver);
        }

        public void setSendHelloCall(
                final BiFunction<Empty, StreamObserver<BasicResponses.BasicResponse>, Void> sendHelloCall) {
            this.sendHelloCall = sendHelloCall;
        }

        @Override
        public void sendHello(
                final Empty request, final StreamObserver<BasicResponses.BasicResponse> responseObserver) {
            sendHelloCall.apply(request, responseObserver);
        }

        public void setTriggerDescriptorUpdateCall(
                final BiFunction<
                                DeviceRequests.TriggerDescriptorUpdateRequest,
                                StreamObserver<BasicResponses.BasicResponse>,
                                Void>
                        call) {
            this.triggerDescriptorUpdateCall = call;
        }

        @Override
        public void triggerDescriptorUpdate(
                final DeviceRequests.TriggerDescriptorUpdateRequest request,
                final StreamObserver<BasicResponses.BasicResponse> responseObserver) {
            triggerDescriptorUpdateCall.apply(request, responseObserver);
        }
    }

    static class ActivationStateStub extends ActivationStateServiceGrpc.ActivationStateServiceImplBase {

        private BiFunction<
                        ActivationStateRequests.SetSystemSignalActivationRequest,
                        StreamObserver<BasicResponses.BasicResponse>,
                        Void>
                setSystemSignalActivationCall = (request, responseObserver) -> {
                    asyncUnimplementedUnaryCall(
                            ActivationStateServiceGrpc.getSetSystemSignalActivationMethod(), responseObserver);
                    return null;
                };
        private BiFunction<
                        ActivationStateRequests.SetAlertActivationRequest,
                        StreamObserver<BasicResponses.BasicResponse>,
                        Void>
                setAlertActivationCall = (request, responseObserver) -> {
                    asyncUnimplementedUnaryCall(
                            ActivationStateServiceGrpc.getSetAlertActivationMethod(), responseObserver);
                    return null;
                };

        private BiFunction<
                        ActivationStateRequests.SetComponentActivationRequest,
                        StreamObserver<BasicResponses.BasicResponse>,
                        Void>
                setComponentActivationCall = (request, responseObserver) -> {
                    asyncUnimplementedUnaryCall(
                            ActivationStateServiceGrpc.getSetComponentActivationMethod(), responseObserver);
                    return null;
                };

        public void setSetSystemSignalActivationCall(
                final BiFunction<
                                ActivationStateRequests.SetSystemSignalActivationRequest,
                                StreamObserver<BasicResponses.BasicResponse>,
                                Void>
                        call) {
            this.setSystemSignalActivationCall = call;
        }

        @Override
        public void setSystemSignalActivation(
                final ActivationStateRequests.SetSystemSignalActivationRequest request,
                final StreamObserver<BasicResponses.BasicResponse> responseObserver) {
            setSystemSignalActivationCall.apply(request, responseObserver);
        }

        public void setSetAlertActivationCall(
                final BiFunction<
                                ActivationStateRequests.SetAlertActivationRequest,
                                StreamObserver<BasicResponses.BasicResponse>,
                                Void>
                        call) {
            this.setAlertActivationCall = call;
        }

        @Override
        public void setAlertActivation(
                final ActivationStateRequests.SetAlertActivationRequest request,
                final StreamObserver<BasicResponses.BasicResponse> responseObserver) {
            setAlertActivationCall.apply(request, responseObserver);
        }

        public void setSetComponentActivationCall(
                final BiFunction<
                                ActivationStateRequests.SetComponentActivationRequest,
                                StreamObserver<BasicResponses.BasicResponse>,
                                Void>
                        call) {
            this.setComponentActivationCall = call;
        }

        @Override
        public void setComponentActivation(
                final ActivationStateRequests.SetComponentActivationRequest request,
                final StreamObserver<BasicResponses.BasicResponse> responseObserver) {
            setComponentActivationCall.apply(request, responseObserver);
        }
    }

    static class MetricStub extends MetricServiceGrpc.MetricServiceImplBase {
        private BiFunction<MetricRequests.SetMetricStatusRequest, StreamObserver<BasicResponses.BasicResponse>, Void>
                setMetricStatusCall = (request, responseObserver) -> {
                    asyncUnimplementedUnaryCall(MetricServiceGrpc.getSetMetricStatusMethod(), responseObserver);
                    return null;
                };

        public void setSetMetricStatusCall(
                final BiFunction<
                                MetricRequests.SetMetricStatusRequest,
                                StreamObserver<BasicResponses.BasicResponse>,
                                Void>
                        call) {
            this.setMetricStatusCall = call;
        }

        @Override
        public void setMetricStatus(
                final MetricRequests.SetMetricStatusRequest request,
                final StreamObserver<BasicResponses.BasicResponse> responseObserver) {
            setMetricStatusCall.apply(request, responseObserver);
        }
    }

    static class AlertStub extends AlertServiceGrpc.AlertServiceImplBase {
        private BiFunction<
                        AlertRequests.SetAlertConditionPresenceRequest,
                        StreamObserver<BasicResponses.BasicResponse>,
                        Void>
                setAlertConditionPresenceCall = (request, responseObserver) -> {
                    asyncUnimplementedUnaryCall(getSetAlertConditionPresenceMethod(), responseObserver);
                    return null;
                };

        public void setSetAlertConditionPresenceCall(
                final BiFunction<
                                AlertRequests.SetAlertConditionPresenceRequest,
                                StreamObserver<BasicResponses.BasicResponse>,
                                Void>
                        call) {
            this.setAlertConditionPresenceCall = call;
        }

        @Override
        public void setAlertConditionPresence(
                final AlertRequests.SetAlertConditionPresenceRequest request,
                final StreamObserver<BasicResponses.BasicResponse> responseObserver) {
            setAlertConditionPresenceCall.apply(request, responseObserver);
        }
    }
}
