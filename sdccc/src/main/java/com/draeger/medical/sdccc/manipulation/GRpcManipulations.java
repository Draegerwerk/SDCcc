/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2023 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.manipulation;

import com.draeger.medical.sdccc.configuration.TestSuiteConfig;
import com.draeger.medical.sdccc.messages.guice.ManipulationInfoFactory;
import com.draeger.medical.sdccc.tests.util.ManipulationParameterUtil;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import javax.xml.namespace.QName;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.somda.sdc.biceps.model.participant.AbstractDescriptor;
import org.somda.sdc.biceps.model.participant.AlertActivation;
import org.somda.sdc.biceps.model.participant.AlertSignalManifestation;
import org.somda.sdc.biceps.model.participant.ComponentActivation;
import org.somda.sdc.biceps.model.participant.ContextAssociation;
import org.somda.sdc.biceps.model.participant.LocationDetail;
import org.somda.sdc.biceps.model.participant.MdsDescriptor;
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
                ManipulationParameterUtil.buildLocationDetailManipulationParameterData(locationDetail));
    }

    @Override
    public List<String> getRemovableDescriptorsOfClass() {
        return getRemovableDescriptorsOfClass(AbstractDescriptor.class);
    }

    @Override
    public List<String> getRemovableDescriptorsOfClass(final Class<? extends AbstractDescriptor> descriptorClass) {
        final DeviceRequests.GetRemovableDescriptorsOfClassRequest request =
                DeviceRequests.GetRemovableDescriptorsOfClassRequest.newBuilder()
                        .setDescriptorClass(toApiDescriptorClass(descriptorClass))
                        .build();
        return performCallWrapper(
                v -> deviceStub.getRemovableDescriptorsOfClass(request),
                v -> fallback.getRemovableDescriptorsOfClass(descriptorClass),
                res -> res.getStatus().getResult(),
                DeviceResponses.GetRemovableDescriptorsResponse::getHandleList,
                ManipulationParameterUtil.buildEmptyManipulationParameterData());
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
                ManipulationParameterUtil.buildHandleManipulationParameterData(handle));
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
                ManipulationParameterUtil.buildHandleManipulationParameterData(handle));
    }

    @Override
    public ResponseTypes.Result sendHello() {
        return performCallWrapper(
                v -> deviceStub.sendHello(Empty.getDefaultInstance()),
                v -> fallback.sendHello(),
                BasicResponses.BasicResponse::getResult,
                BasicResponses.BasicResponse::getResult,
                ManipulationParameterUtil.buildEmptyManipulationParameterData());
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
                ManipulationParameterUtil.buildContextAssociationManipulationParameterData(
                        descriptorHandle, association));
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
                ManipulationParameterUtil.buildAlertActivationManipulationParameterData(handle, activationState));
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
                ManipulationParameterUtil.buildAlertConditionPresenceManipulationParameterData(handle, presence));
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
                ManipulationParameterUtil.buildSystemSignalActivationManipulationParameterData(
                        handle, manifestation, activation));
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
                ManipulationParameterUtil.buildComponentActivationManipulationParameterData(handle, activationState));
    }

    @Override
    public ResponseTypes.Result setMetricStatus(
            final String sequenceId,
            final String handle,
            final MetricCategory category,
            final ComponentActivation activation) {
        final var metricStatus = getMetricStatus(activation);
        if (metricStatus.isEmpty()) return ResponseTypes.Result.RESULT_FAIL;
        final var message = MetricRequests.SetMetricStatusRequest.newBuilder()
                .setHandle(handle)
                .setStatus(metricStatus.orElseThrow())
                .build();

        return performCallWrapper(
                v -> metricStub.setMetricStatus(message),
                v -> fallback.setMetricStatus(sequenceId, handle, category, activation),
                BasicResponses.BasicResponse::getResult,
                BasicResponses.BasicResponse::getResult,
                ManipulationParameterUtil.buildMetricStatusManipulationParameterData(
                        sequenceId, handle, category, activation));
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
                ManipulationParameterUtil.buildHandleManipulationParameterData(handle));
    }

    @Override
    public ResponseTypes.Result triggerAnyDescriptorUpdate() {
        return performCallWrapper(
                v -> deviceStub.triggerAnyDescriptorUpdate(Empty.getDefaultInstance()),
                v -> fallback.triggerAnyDescriptorUpdate(),
                BasicResponses.BasicResponse::getResult,
                BasicResponses.BasicResponse::getResult,
                ManipulationParameterUtil.buildEmptyManipulationParameterData());
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
                ManipulationParameterUtil.buildTriggerReportManipulationParameterData(report));
    }

    private Optional<MetricTypes.MetricStatus> getMetricStatus(final ComponentActivation activation) {
        return switch (activation) {
            case ON -> Optional.of(MetricTypes.MetricStatus.METRIC_STATUS_PERFORMED_OR_APPLIED);
            case NOT_RDY -> Optional.of(MetricTypes.MetricStatus.METRIC_STATUS_CURRENTLY_INITIALIZING);
            case STND_BY -> Optional.of(
                    MetricTypes.MetricStatus.METRIC_STATUS_INITIALIZED_BUT_NOT_PERFORMING_OR_APPLYING);
            case SHTDN -> Optional.of(MetricTypes.MetricStatus.METRIC_STATUS_CURRENTLY_DE_INITIALIZING);
            case OFF -> Optional.of(
                    MetricTypes.MetricStatus.METRIC_STATUS_DE_INITIALIZED_AND_NOT_PERFORMING_OR_APPLYING);
            case FAIL -> Optional.of(MetricTypes.MetricStatus.METRIC_STATUS_FAILED);
        };
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
            final ManipulationParameterUtil.ManipulationParameterData parameter) {
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

        return switch (statusExtractor.apply(response)) {
            case RESULT_NOT_IMPLEMENTED -> {
                LOG.warn("Server has not implemented method");
                yield fallbackFunc.apply(null);
            }
            case RESULT_SUCCESS, RESULT_NOT_SUPPORTED, RESULT_FAIL -> responseExtractor.apply(response);
            default -> {
                LOG.warn("Server has not sent a valid result, going to fallback");
                yield fallbackFunc.apply(null);
            }
        };
    }

    private <RES> ResponseTypes.Result getResultForPersisting(final RES result) {
        ResponseTypes.Result response = ResponseTypes.Result.UNRECOGNIZED;
        if (result instanceof final List resultList) {
            if (resultList.isEmpty()) {
                response = ResponseTypes.Result.RESULT_FAIL;
            } else {
                response = ResponseTypes.Result.RESULT_SUCCESS;
            }
        } else if (result instanceof final Optional<?> resultOptional) {
            if (resultOptional.isEmpty()) {
                response = ResponseTypes.Result.RESULT_FAIL;
            } else {
                response = ResponseTypes.Result.RESULT_SUCCESS;
            }
        } else if (result instanceof ResponseTypes.Result castedResult) {
            response = castedResult;
        }
        return response;
    }

    ContextTypes.ContextAssociation toApiContextType(final ContextAssociation contextAssociation) {
        return switch (contextAssociation) {
            case NO -> ContextTypes.ContextAssociation.CONTEXT_ASSOCIATION_NOT_ASSOCIATED;
            case DIS -> ContextTypes.ContextAssociation.CONTEXT_ASSOCIATION_DISASSOCIATED;
            case PRE -> ContextTypes.ContextAssociation.CONTEXT_ASSOCIATION_PRE_ASSOCIATED;
            case ASSOC -> ContextTypes.ContextAssociation.CONTEXT_ASSOCIATION_ASSOCIATED;
        };
    }

    ActivationStateTypes.AlertSignalManifestation toApiManifestationType(final AlertSignalManifestation manifestation) {
        return switch (manifestation) {
            case VIS -> ActivationStateTypes.AlertSignalManifestation.ALERT_SIGNAL_MANIFESTATION_VIS;
            case AUD -> ActivationStateTypes.AlertSignalManifestation.ALERT_SIGNAL_MANIFESTATION_AUD;
            case TAN -> ActivationStateTypes.AlertSignalManifestation.ALERT_SIGNAL_MANIFESTATION_TAN;
            case OTH -> ActivationStateTypes.AlertSignalManifestation.ALERT_SIGNAL_MANIFESTATION_OTH;
        };
    }

    ActivationStateTypes.AlertActivation toApiActivationStateType(final AlertActivation alertActivation) {
        return switch (alertActivation) {
            case ON -> ActivationStateTypes.AlertActivation.ALERT_ACTIVATION_ON;
            case OFF -> ActivationStateTypes.AlertActivation.ALERT_ACTIVATION_OFF;
            case PSD -> ActivationStateTypes.AlertActivation.ALERT_ACTIVATION_PSD;
        };
    }

    ActivationStateTypes.ComponentActivation toApiComponentActivationStateType(final ComponentActivation activation) {
        return switch (activation) {
            case ON -> ActivationStateTypes.ComponentActivation.COMPONENT_ACTIVATION_ON;
            case NOT_RDY -> ActivationStateTypes.ComponentActivation.COMPONENT_ACTIVATION_NOT_READY;
            case STND_BY -> ActivationStateTypes.ComponentActivation.COMPONENT_ACTIVATION_STANDBY;
            case SHTDN -> ActivationStateTypes.ComponentActivation.COMPONENT_ACTIVATION_SHUTDOWN;
            case OFF -> ActivationStateTypes.ComponentActivation.COMPONENT_ACTIVATION_OFF;
            case FAIL -> ActivationStateTypes.ComponentActivation.COMPONENT_ACTIVATION_FAILURE;
        };
    }

    MetricTypes.MeasurementValidity toApiMeasurementValidityType(final MeasurementValidity validity) {
        return switch (validity) {
            case VLD -> MetricTypes.MeasurementValidity.MEASUREMENT_VALIDITY_VALID;
            case VLDATED -> MetricTypes.MeasurementValidity.MEASUREMENT_VALIDITY_VALIDATED_DATA;
            case ONG -> MetricTypes.MeasurementValidity.MEASUREMENT_VALIDITY_MEASUREMENT_ONGOING;
            case QST -> MetricTypes.MeasurementValidity.MEASUREMENT_VALIDITY_QUESTIONABLE;
            case CALIB -> MetricTypes.MeasurementValidity.MEASUREMENT_VALIDITY_CALIBRATION_ONGOING;
            case INV -> MetricTypes.MeasurementValidity.MEASUREMENT_VALIDITY_INVALID;
            case OFLW -> MetricTypes.MeasurementValidity.MEASUREMENT_VALIDITY_OVERFLOW;
            case UFLW -> MetricTypes.MeasurementValidity.MEASUREMENT_VALIDITY_UNDERFLOW;
            case NA -> MetricTypes.MeasurementValidity.MEASUREMENT_VALIDITY_NA;
        };
    }

    private DeviceTypes.DescriptorClass toApiDescriptorClass(final Class<? extends AbstractDescriptor> descriptorType) {
        if (MdsDescriptor.class.equals(descriptorType)) {
            return DeviceTypes.DescriptorClass.DESCRIPTOR_CLASS_MDS;
        } else {
            return DeviceTypes.DescriptorClass.DESCRIPTOR_CLASS_ABSTRACT;
        }
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
