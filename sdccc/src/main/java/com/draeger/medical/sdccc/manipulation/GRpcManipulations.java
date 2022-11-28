/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.manipulation;

import com.draeger.medical.sdccc.configuration.TestSuiteConfig;
import com.draeger.medical.sdccc.messages.guice.ManipulationInfoFactory;
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
import com.draeger.medical.t2iapi.context.ContextServiceGrpc;
import com.draeger.medical.t2iapi.context.ContextTypes;
import com.draeger.medical.t2iapi.device.DeviceRequests;
import com.draeger.medical.t2iapi.device.DeviceResponses;
import com.draeger.medical.t2iapi.device.DeviceServiceGrpc;
import com.draeger.medical.t2iapi.device.DeviceTypes;
import com.draeger.medical.t2iapi.metric.MetricRequests;
import com.draeger.medical.t2iapi.metric.MetricServiceGrpc;
import com.draeger.medical.t2iapi.metric.MetricTypes;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.protobuf.Empty;
import com.google.protobuf.StringValue;
import io.grpc.Channel;
import io.grpc.ManagedChannelBuilder;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import javax.xml.namespace.QName;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.somda.sdc.biceps.model.participant.AlertActivation;
import org.somda.sdc.biceps.model.participant.AlertSignalManifestation;
import org.somda.sdc.biceps.model.participant.ComponentActivation;
import org.somda.sdc.biceps.model.participant.ContextAssociation;
import org.somda.sdc.biceps.model.participant.LocationDetail;
import org.somda.sdc.biceps.model.participant.MeasurementValidity;
import org.somda.sdc.biceps.model.participant.MetricCategory;

/**
 * Device manipulations calling on a gRPC backend.
 */
@Singleton
public class GRpcManipulations implements Manipulations {
    private static final Logger LOG = LogManager.getLogger(GRpcManipulations.class);
    private static final Map<QName, DeviceTypes.ReportType> REPORT_TYPE_MAP = Map.of(
            Constants.MSG_EPISODIC_ALERT_REPORT,
            DeviceTypes.ReportType.REPORT_TYPE_EPISODIC_ALERT_REPORT,
            Constants.MSG_EPISODIC_COMPONENT_REPORT,
            DeviceTypes.ReportType.REPORT_TYPE_EPISODIC_COMPONENT_REPORT,
            Constants.MSG_EPISODIC_CONTEXT_REPORT,
            DeviceTypes.ReportType.REPORT_TYPE_EPISODIC_CONTEXT_REPORT,
            Constants.MSG_EPISODIC_METRIC_REPORT,
            DeviceTypes.ReportType.REPORT_TYPE_EPISODIC_METRIC_REPORT,
            Constants.MSG_EPISODIC_OPERATIONAL_STATE_REPORT,
            DeviceTypes.ReportType.REPORT_TYPE_EPISODIC_OPERATIONAL_STATE_REPORT,
            Constants.MSG_DESCRIPTION_MODIFICATION_REPORT,
            DeviceTypes.ReportType.REPORT_TYPE_DESCRIPTION_MODIFICATION_REPORT,
            Constants.MSG_OPERATION_INVOKED_REPORT,
            DeviceTypes.ReportType.REPORT_TYPE_OPERATION_INVOKED_REPORT,
            Constants.MSG_SYSTEM_ERROR_REPORT,
            DeviceTypes.ReportType.REPORT_TYPE_SYSTEM_ERROR_REPORT,
            Constants.MSG_OBSERVED_VALUE_STREAM,
            DeviceTypes.ReportType.REPORT_TYPE_OBSERVED_VALUE_STREAM,
            Constants.MSG_WAVEFORM_STREAM,
            DeviceTypes.ReportType.REPORT_TYPE_WAVEFORM_STREAM);

    private final ActivationStateServiceGrpc.ActivationStateServiceBlockingStub activationStateStub;
    private final AlertServiceGrpc.AlertServiceBlockingStub alertStub;
    private final ContextServiceGrpc.ContextServiceBlockingStub contextStub;
    private final DeviceServiceGrpc.DeviceServiceBlockingStub deviceStub;
    private final MetricServiceGrpc.MetricServiceBlockingStub metricStub;
    private final Manipulations fallback;
    private final ManipulationInfoFactory manipulationInfoFactory;
    private final StackWalker walker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    /**
     * Creates an instance of gRPC-based manipulations.
     *
     * @param serverAddress           to connect to
     * @param fallbackManipulations   fallback manipulations should the server fail
     * @param manipulationInfoFactory factory to create manipulation info
     */
    @Inject
    public GRpcManipulations(
            @Named(TestSuiteConfig.GRPC_SERVER_ADDRESS) final String serverAddress,
            final FallbackManipulations fallbackManipulations,
            final ManipulationInfoFactory manipulationInfoFactory) {
        this.fallback = fallbackManipulations;
        this.manipulationInfoFactory = manipulationInfoFactory;
        final Channel channel = ManagedChannelBuilder.forTarget(serverAddress)
                // Channels are secure by default (via SSL/TLS), which we don't really need
                .usePlaintext()
                .build();

        activationStateStub = ActivationStateServiceGrpc.newBlockingStub(channel);
        alertStub = AlertServiceGrpc.newBlockingStub(channel);
        contextStub = ContextServiceGrpc.newBlockingStub(channel);
        deviceStub = DeviceServiceGrpc.newBlockingStub(channel);
        metricStub = MetricServiceGrpc.newBlockingStub(channel);
    }

    @Override
    public ResponseTypes.Result setLocationDetail(final LocationDetail locationDetail) {
        final var protoLocation = locationDetailToProto(locationDetail);

        final var message = ContextRequests.SetLocationDetailRequest.newBuilder()
                .setLocation(protoLocation)
                .build();

        return performCallWrapper(
                v -> contextStub.setLocationDetail(message),
                v -> fallback.setLocationDetail(locationDetail),
                BasicResponses.BasicResponse::getResult,
                BasicResponses.BasicResponse::getResult,
                List.of(new ImmutablePair<>(
                        Constants.MANIPULATION_PARAMETER_LOCATION_DETAIL,
                        String.format(
                                "poC=%s, " + "room=%s, " + "bed=%s, " + "facility=%s, " + "building=%s, " + "floor=%s",
                                locationDetail.getPoC(),
                                locationDetail.getRoom(),
                                locationDetail.getBed(),
                                locationDetail.getFacility(),
                                locationDetail.getBuilding(),
                                locationDetail.getFloor()))));
    }

    @Override
    public List<String> getRemovableDescriptors() {
        return performCallWrapper(
                v -> deviceStub.getRemovableDescriptors(Empty.getDefaultInstance()),
                v -> fallback.getRemovableDescriptors(),
                res -> res.getStatus().getResult(),
                DeviceResponses.GetRemovableDescriptorsResponse::getHandleList,
                Collections.emptyList());
    }

    @Override
    public ResponseTypes.Result removeDescriptor(final String handle) {
        final var request =
                BasicRequests.BasicHandleRequest.newBuilder().setHandle(handle).build();
        return performCallWrapper(
                v -> deviceStub.removeDescriptor(request),
                v -> fallback.removeDescriptor(handle),
                BasicResponses.BasicResponse::getResult,
                BasicResponses.BasicResponse::getResult,
                List.of(new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, handle)));
    }

    @Override
    public ResponseTypes.Result insertDescriptor(final String handle) {
        final var request =
                BasicRequests.BasicHandleRequest.newBuilder().setHandle(handle).build();
        return performCallWrapper(
                v -> deviceStub.insertDescriptor(request),
                v -> fallback.insertDescriptor(handle),
                BasicResponses.BasicResponse::getResult,
                BasicResponses.BasicResponse::getResult,
                List.of(new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, handle)));
    }

    @Override
    public ResponseTypes.Result sendHello() {
        return performCallWrapper(
                v -> deviceStub.sendHello(Empty.getDefaultInstance()),
                v -> fallback.sendHello(),
                BasicResponses.BasicResponse::getResult,
                BasicResponses.BasicResponse::getResult,
                Collections.emptyList());
    }

    @Override
    public Optional<String> createContextStateWithAssociation(
            final String descriptorHandle, final ContextAssociation association) {
        final var request = ContextRequests.CreateContextStateWithAssociationRequest.newBuilder()
                .setDescriptorHandle(descriptorHandle)
                .setContextAssociation(toApiContextType(association))
                .build();

        return performCallWrapper(
                v -> contextStub.createContextStateWithAssociation(request),
                v -> fallback.createContextStateWithAssociation(descriptorHandle, association),
                response -> response.getStatus().getResult(),
                msg -> {
                    if (msg.getContextStateHandle().isBlank()) {
                        return Optional.empty();
                    }
                    return Optional.of(msg.getContextStateHandle());
                },
                List.of(
                        new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, descriptorHandle),
                        new ImmutablePair<>(
                                Constants.MANIPULATION_PARAMETER_CONTEXT_ASSOCIATION, association.value())));
    }

    @Override
    public ResponseTypes.Result setAlertActivation(final String handle, final AlertActivation activationState) {
        final var message = ActivationStateRequests.SetAlertActivationRequest.newBuilder()
                .setHandle(handle)
                .setActivation(toApiActivationStateType(activationState))
                .build();

        return performCallWrapper(
                v -> activationStateStub.setAlertActivation(message),
                v -> fallback.setAlertActivation(handle, activationState),
                BasicResponses.BasicResponse::getResult,
                BasicResponses.BasicResponse::getResult,
                List.of(
                        new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, handle),
                        new ImmutablePair<>(
                                Constants.MANIPULATION_PARAMETER_ALERT_ACTIVATION, activationState.value())));
    }

    @Override
    public ResponseTypes.Result setAlertConditionPresence(final String handle, final boolean presence) {
        final var message = AlertRequests.SetAlertConditionPresenceRequest.newBuilder()
                .setHandle(handle)
                .setPresence(presence)
                .build();

        return performCallWrapper(
                v -> alertStub.setAlertConditionPresence(message),
                v -> fallback.setAlertConditionPresence(handle, presence),
                BasicResponses.BasicResponse::getResult,
                BasicResponses.BasicResponse::getResult,
                List.of(
                        new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, handle),
                        new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_PRESENCE, String.format("%s", presence))));
    }

    @Override
    public ResponseTypes.Result setSystemSignalActivation(
            final String handle, final AlertSignalManifestation manifestation, final AlertActivation activation) {
        final var message = ActivationStateRequests.SetSystemSignalActivationRequest.newBuilder()
                .setHandle(handle)
                .setManifestation(toApiManifestationType(manifestation))
                .setActivation(toApiActivationStateType(activation))
                .build();

        return performCallWrapper(
                v -> activationStateStub.setSystemSignalActivation(message),
                v -> fallback.setSystemSignalActivation(handle, manifestation, activation),
                BasicResponses.BasicResponse::getResult,
                BasicResponses.BasicResponse::getResult,
                List.of(
                        new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, handle),
                        new ImmutablePair<>(
                                Constants.MANIPULATION_PARAMETER_ALERT_SIGNAL_ACTIVATION, manifestation.value()),
                        new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_ALERT_ACTIVATION, activation.value())));
    }

    @Override
    public ResponseTypes.Result setComponentActivation(final String handle, final ComponentActivation activationState) {
        final var message = ActivationStateRequests.SetComponentActivationRequest.newBuilder()
                .setHandle(handle)
                .setActivation(toApiComponentActivationStateType(activationState))
                .build();

        return performCallWrapper(
                v -> activationStateStub.setComponentActivation(message),
                v -> fallback.setComponentActivation(handle, activationState),
                BasicResponses.BasicResponse::getResult,
                BasicResponses.BasicResponse::getResult,
                List.of(
                        new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, handle),
                        new ImmutablePair<>(
                                Constants.MANIPULATION_PARAMETER_COMPONENT_ACTIVATION, activationState.toString())));
    }

    @Override
    public ResponseTypes.Result setMetricQualityValidity(final String handle, final MeasurementValidity validity) {
        final var message = MetricRequests.SetMetricQualityValidityRequest.newBuilder()
                .setHandle(handle)
                .setValidity(toApiMeasurementValidityType(validity))
                .build();

        return performCallWrapper(
                v -> metricStub.setMetricQualityValidity(message),
                v -> fallback.setMetricQualityValidity(handle, validity),
                BasicResponses.BasicResponse::getResult,
                BasicResponses.BasicResponse::getResult,
                List.of(
                        new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, handle),
                        new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_MEASUREMENT_VALIDITY, validity.value())));
    }

    @Override
    public ResponseTypes.Result setMetricStatus(
            final String handle, final MetricCategory category, final ComponentActivation activation) {
        final var metricStatus = getMetricStatus(activation);
        if (metricStatus.isEmpty()) return ResponseTypes.Result.RESULT_FAIL;
        final var message = MetricRequests.SetMetricStatusRequest.newBuilder()
                .setHandle(handle)
                .setStatus(metricStatus.orElseThrow())
                .build();

        return performCallWrapper(
                v -> metricStub.setMetricStatus(message),
                v -> fallback.setMetricStatus(handle, category, activation),
                BasicResponses.BasicResponse::getResult,
                BasicResponses.BasicResponse::getResult,
                List.of(
                        new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, handle),
                        new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_METRIC_CATEGORY, category.value()),
                        new ImmutablePair<>(
                                Constants.MANIPULATION_PARAMETER_COMPONENT_ACTIVATION, activation.toString())));
    }

    @Override
    public ResponseTypes.Result triggerDescriptorUpdate(final String handle) {
        final var message =
                BasicRequests.BasicHandleRequest.newBuilder().setHandle(handle).build();

        return performCallWrapper(
                v -> deviceStub.triggerDescriptorUpdate(message),
                v -> fallback.triggerDescriptorUpdate(handle),
                BasicResponses.BasicResponse::getResult,
                BasicResponses.BasicResponse::getResult,
                List.of(new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_QNAME, handle)));
    }

    @Override
    public ResponseTypes.Result triggerReport(final QName report) {
        final var reportType = REPORT_TYPE_MAP.get(report);
        if (reportType == null) return ResponseTypes.Result.RESULT_FAIL;
        final var message = DeviceRequests.TriggerReportRequest.newBuilder()
                .setReport(reportType)
                .build();

        return performCallWrapper(
                v -> deviceStub.triggerReport(message),
                v -> fallback.triggerReport(report),
                BasicResponses.BasicResponse::getResult,
                BasicResponses.BasicResponse::getResult,
                List.of(new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_QNAME, report.toString())));
    }

    private Optional<MetricTypes.MetricStatus> getMetricStatus(final ComponentActivation activation) {
        final Optional<MetricTypes.MetricStatus> optionalOfMetricStatus;
        switch (activation) {
            case ON:
                optionalOfMetricStatus = Optional.of(MetricTypes.MetricStatus.METRIC_STATUS_PERFORMED_OR_APPLIED);
                break;
            case NOT_RDY:
                optionalOfMetricStatus = Optional.of(MetricTypes.MetricStatus.METRIC_STATUS_CURRENTLY_INITIALIZING);
                break;
            case STND_BY:
                optionalOfMetricStatus =
                        Optional.of(MetricTypes.MetricStatus.METRIC_STATUS_INITIALIZED_BUT_NOT_PERFORMING_OR_APPLYING);
                break;
            case SHTDN:
                optionalOfMetricStatus = Optional.of(MetricTypes.MetricStatus.METRIC_STATUS_CURRENTLY_DE_INITIALIZING);
                break;
            case OFF:
                optionalOfMetricStatus = Optional.of(
                        MetricTypes.MetricStatus.METRIC_STATUS_DE_INITIALIZED_AND_NOT_PERFORMING_OR_APPLYING);
                break;
            case FAIL:
                optionalOfMetricStatus = Optional.of(MetricTypes.MetricStatus.METRIC_STATUS_FAILED);
                break;
            default:
                optionalOfMetricStatus = Optional.empty();
                break;
        }
        return optionalOfMetricStatus;
    }

    /**
     * Performs a gRPC call. Adds manipulation information to the database.
     *
     * @param func              to call for gRPC call
     * @param fallbackFunc      to call in case of gRPC failures
     * @param statusExtractor   to extract status from gRPC call
     * @param responseExtractor to extract response value from gRPC call
     * @param <GRES>            gRPC response type
     * @param <RES>             response type
     * @param parameter         for the manipulation, can be empty
     * @return response
     */
    public <GRES, RES> RES performCallWrapper(
            final Function<Void, GRES> func,
            final Function<Void, RES> fallbackFunc,
            final Function<GRES, ResponseTypes.Result> statusExtractor,
            final Function<GRES, RES> responseExtractor,
            final List<Pair<String, String>> parameter) {
        final var startTime = System.nanoTime();
        final var result = performCall(func, fallbackFunc, statusExtractor, responseExtractor);
        final var endTime = System.nanoTime();
        final var methodName = walker.walk(
                s -> s.map(StackWalker.StackFrame::getMethodName).skip(1).findFirst());
        final var manipulation = manipulationInfoFactory.create(
                startTime, endTime, getResultForPersisting(result), methodName.orElseThrow(), parameter);
        manipulation.addToStorage();
        return result;
    }

    private <GRES, RES> RES performCall(
            final Function<Void, GRES> func,
            final Function<Void, RES> fallbackFunc,
            final Function<GRES, ResponseTypes.Result> statusExtractor,
            final Function<GRES, RES> responseExtractor) {
        final GRES response;
        try {
            response = func.apply(null);
            LOG.debug("API Response was {}", response);
        } catch (final io.grpc.StatusRuntimeException e) {
            LOG.debug("grpc call was unavailable", e);
            LOG.warn("Automated manipulation not available");
            return fallbackFunc.apply(null);
        }

        final RES result;

        switch (statusExtractor.apply(response)) {
            case RESULT_NOT_IMPLEMENTED:
                LOG.warn("Server has not implemented method");
                result = fallbackFunc.apply(null);
                break;
            case RESULT_SUCCESS:
            case RESULT_NOT_SUPPORTED:
            case RESULT_FAIL:
                result = responseExtractor.apply(response);
                break;
            case UNRECOGNIZED:
            default:
                LOG.warn("setLocationDetail: Server has not sent a valid result, going to fallback");
                result = fallbackFunc.apply(null);
                break;
        }

        return result;
    }

    private <RES> ResponseTypes.Result getResultForPersisting(final RES result) {
        ResponseTypes.Result response = ResponseTypes.Result.UNRECOGNIZED;
        if (result instanceof List) {
            final var resultList = (List) result;
            if (resultList.isEmpty()) {
                response = ResponseTypes.Result.RESULT_FAIL;
            } else {
                response = ResponseTypes.Result.RESULT_SUCCESS;
            }
        } else if (result instanceof Optional) {
            final var resultOptional = (Optional) result;
            if (resultOptional.isEmpty()) {
                response = ResponseTypes.Result.RESULT_FAIL;
            } else {
                response = ResponseTypes.Result.RESULT_SUCCESS;
            }
        } else if (result instanceof ResponseTypes.Result) {
            response = (ResponseTypes.Result) result;
        }
        return response;
    }

    ContextTypes.ContextAssociation toApiContextType(final ContextAssociation contextAssociation) {
        final ContextTypes.ContextAssociation apiType;
        switch (contextAssociation) {
            case NO:
                apiType = ContextTypes.ContextAssociation.CONTEXT_ASSOCIATION_NOT_ASSOCIATED;
                break;
            case DIS:
                apiType = ContextTypes.ContextAssociation.CONTEXT_ASSOCIATION_DISASSOCIATED;
                break;
            case PRE:
                apiType = ContextTypes.ContextAssociation.CONTEXT_ASSOCIATION_PRE_ASSOCIATED;
                break;
            case ASSOC:
                apiType = ContextTypes.ContextAssociation.CONTEXT_ASSOCIATION_ASSOCIATED;
                break;
            default:
                throw new IllegalStateException(
                        "Unknown ContextAssociation could not be mapped. " + contextAssociation);
        }
        return apiType;
    }

    ActivationStateTypes.AlertSignalManifestation toApiManifestationType(final AlertSignalManifestation manifestation) {
        final ActivationStateTypes.AlertSignalManifestation apiType;
        switch (manifestation) {
            case VIS:
                apiType = ActivationStateTypes.AlertSignalManifestation.ALERT_SIGNAL_MANIFESTATION_VIS;
                break;
            case AUD:
                apiType = ActivationStateTypes.AlertSignalManifestation.ALERT_SIGNAL_MANIFESTATION_AUD;
                break;
            case TAN:
                apiType = ActivationStateTypes.AlertSignalManifestation.ALERT_SIGNAL_MANIFESTATION_TAN;
                break;
            case OTH:
                apiType = ActivationStateTypes.AlertSignalManifestation.ALERT_SIGNAL_MANIFESTATION_OTH;
                break;
            default:
                throw new IllegalStateException(
                        "Unknown AlertSignalManifestation could not be mapped. " + manifestation);
        }
        return apiType;
    }

    ActivationStateTypes.AlertActivation toApiActivationStateType(final AlertActivation alertActivation) {
        final ActivationStateTypes.AlertActivation apiType;
        switch (alertActivation) {
            case ON:
                apiType = ActivationStateTypes.AlertActivation.ALERT_ACTIVATION_ON;
                break;
            case OFF:
                apiType = ActivationStateTypes.AlertActivation.ALERT_ACTIVATION_OFF;
                break;
            case PSD:
                apiType = ActivationStateTypes.AlertActivation.ALERT_ACTIVATION_PSD;
                break;
            default:
                throw new IllegalStateException("Unknown AlertActivation could not be mapped. " + alertActivation);
        }
        return apiType;
    }

    ActivationStateTypes.ComponentActivation toApiComponentActivationStateType(final ComponentActivation activation) {
        final ActivationStateTypes.ComponentActivation apiType;
        switch (activation) {
            case ON:
                apiType = ActivationStateTypes.ComponentActivation.COMPONENT_ACTIVATION_ON;
                break;
            case NOT_RDY:
                apiType = ActivationStateTypes.ComponentActivation.COMPONENT_ACTIVATION_NOT_READY;
                break;
            case STND_BY:
                apiType = ActivationStateTypes.ComponentActivation.COMPONENT_ACTIVATION_STANDBY;
                break;
            case SHTDN:
                apiType = ActivationStateTypes.ComponentActivation.COMPONENT_ACTIVATION_SHUTDOWN;
                break;
            case OFF:
                apiType = ActivationStateTypes.ComponentActivation.COMPONENT_ACTIVATION_OFF;
                break;
            case FAIL:
                apiType = ActivationStateTypes.ComponentActivation.COMPONENT_ACTIVATION_FAILURE;
                break;
            default:
                throw new IllegalStateException("Unknown ComponentActivation could not be mapped. " + activation);
        }
        return apiType;
    }

    MetricTypes.MeasurementValidity toApiMeasurementValidityType(final MeasurementValidity validity) {
        final MetricTypes.MeasurementValidity apiType;
        switch (validity) {
            case VLD:
                apiType = MetricTypes.MeasurementValidity.MEASUREMENT_VALIDITY_VALID;
                break;
            case VLDATED:
                apiType = MetricTypes.MeasurementValidity.MEASUREMENT_VALIDITY_VALIDATED_DATA;
                break;
            case ONG:
                apiType = MetricTypes.MeasurementValidity.MEASUREMENT_VALIDITY_MEASUREMENT_ONGOING;
                break;
            case QST:
                apiType = MetricTypes.MeasurementValidity.MEASUREMENT_VALIDITY_QUESTIONABLE;
                break;
            case CALIB:
                apiType = MetricTypes.MeasurementValidity.MEASUREMENT_VALIDITY_CALIBRATION_ONGOING;
                break;
            case INV:
                apiType = MetricTypes.MeasurementValidity.MEASUREMENT_VALIDITY_INVALID;
                break;
            case OFLW:
                apiType = MetricTypes.MeasurementValidity.MEASUREMENT_VALIDITY_OVERFLOW;
                break;
            case UFLW:
                apiType = MetricTypes.MeasurementValidity.MEASUREMENT_VALIDITY_UNDERFLOW;
                break;
            case NA:
                apiType = MetricTypes.MeasurementValidity.MEASUREMENT_VALIDITY_NA;
                break;
            default:
                throw new IllegalStateException("Unknown MeasurementValidity could not be mapped. " + validity);
        }
        return apiType;
    }

    private static ContextTypes.LocationDetail locationDetailToProto(final LocationDetail locationDetail) {
        final var builder = ContextTypes.LocationDetail.newBuilder();

        if (locationDetail.getPoC() != null) builder.setPoc(buildStringValue(locationDetail.getPoC()));
        if (locationDetail.getRoom() != null) builder.setRoom(buildStringValue(locationDetail.getRoom()));
        if (locationDetail.getBed() != null) builder.setBed(buildStringValue(locationDetail.getBed()));
        if (locationDetail.getFacility() != null) builder.setFacility(buildStringValue(locationDetail.getFacility()));
        if (locationDetail.getBuilding() != null) builder.setBuilding(buildStringValue(locationDetail.getBuilding()));
        if (locationDetail.getFloor() != null) builder.setFloor(buildStringValue(locationDetail.getFloor()));

        return builder.build();
    }

    private static StringValue buildStringValue(final String value) {
        return StringValue.newBuilder().setValue(value).build();
    }
}
