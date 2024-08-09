/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2023 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.util;

import com.draeger.medical.biceps.model.participant.ActivateOperationDescriptor;
import com.draeger.medical.biceps.model.participant.ActivateOperationState;
import com.draeger.medical.biceps.model.participant.AlertActivation;
import com.draeger.medical.biceps.model.participant.AlertConditionDescriptor;
import com.draeger.medical.biceps.model.participant.AlertConditionKind;
import com.draeger.medical.biceps.model.participant.AlertConditionPriority;
import com.draeger.medical.biceps.model.participant.AlertConditionState;
import com.draeger.medical.biceps.model.participant.AlertSignalDescriptor;
import com.draeger.medical.biceps.model.participant.AlertSignalManifestation;
import com.draeger.medical.biceps.model.participant.AlertSignalState;
import com.draeger.medical.biceps.model.participant.AlertSystemDescriptor;
import com.draeger.medical.biceps.model.participant.AlertSystemState;
import com.draeger.medical.biceps.model.participant.BatteryDescriptor;
import com.draeger.medical.biceps.model.participant.BatteryState;
import com.draeger.medical.biceps.model.participant.CalibrationInfo;
import com.draeger.medical.biceps.model.participant.CauseInfo;
import com.draeger.medical.biceps.model.participant.ChannelDescriptor;
import com.draeger.medical.biceps.model.participant.ChannelState;
import com.draeger.medical.biceps.model.participant.ClockDescriptor;
import com.draeger.medical.biceps.model.participant.ClockState;
import com.draeger.medical.biceps.model.participant.CodedValue;
import com.draeger.medical.biceps.model.participant.DistributionSampleArrayMetricDescriptor;
import com.draeger.medical.biceps.model.participant.DistributionSampleArrayMetricState;
import com.draeger.medical.biceps.model.participant.EnsembleContextDescriptor;
import com.draeger.medical.biceps.model.participant.EnsembleContextState;
import com.draeger.medical.biceps.model.participant.EnumStringMetricDescriptor;
import com.draeger.medical.biceps.model.participant.EnumStringMetricState;
import com.draeger.medical.biceps.model.participant.InstanceIdentifier;
import com.draeger.medical.biceps.model.participant.LocalizedText;
import com.draeger.medical.biceps.model.participant.LocationContextDescriptor;
import com.draeger.medical.biceps.model.participant.LocationContextState;
import com.draeger.medical.biceps.model.participant.MdDescription;
import com.draeger.medical.biceps.model.participant.MdState;
import com.draeger.medical.biceps.model.participant.Mdib;
import com.draeger.medical.biceps.model.participant.MdsDescriptor;
import com.draeger.medical.biceps.model.participant.MdsState;
import com.draeger.medical.biceps.model.participant.MeansContextDescriptor;
import com.draeger.medical.biceps.model.participant.MeansContextState;
import com.draeger.medical.biceps.model.participant.MeasurementValidity;
import com.draeger.medical.biceps.model.participant.MetricAvailability;
import com.draeger.medical.biceps.model.participant.MetricCategory;
import com.draeger.medical.biceps.model.participant.NumericMetricDescriptor;
import com.draeger.medical.biceps.model.participant.NumericMetricState;
import com.draeger.medical.biceps.model.participant.NumericMetricValue;
import com.draeger.medical.biceps.model.participant.ObjectFactory;
import com.draeger.medical.biceps.model.participant.OperatingMode;
import com.draeger.medical.biceps.model.participant.OperatorContextDescriptor;
import com.draeger.medical.biceps.model.participant.OperatorContextState;
import com.draeger.medical.biceps.model.participant.PatientContextDescriptor;
import com.draeger.medical.biceps.model.participant.PatientContextState;
import com.draeger.medical.biceps.model.participant.PhysicalConnectorInfo;
import com.draeger.medical.biceps.model.participant.Range;
import com.draeger.medical.biceps.model.participant.RealTimeSampleArrayMetricDescriptor;
import com.draeger.medical.biceps.model.participant.RealTimeSampleArrayMetricState;
import com.draeger.medical.biceps.model.participant.SampleArrayValue;
import com.draeger.medical.biceps.model.participant.ScoDescriptor;
import com.draeger.medical.biceps.model.participant.ScoState;
import com.draeger.medical.biceps.model.participant.SetStringOperationDescriptor;
import com.draeger.medical.biceps.model.participant.SetStringOperationState;
import com.draeger.medical.biceps.model.participant.StringMetricDescriptor;
import com.draeger.medical.biceps.model.participant.StringMetricState;
import com.draeger.medical.biceps.model.participant.StringMetricValue;
import com.draeger.medical.biceps.model.participant.SystemContextDescriptor;
import com.draeger.medical.biceps.model.participant.SystemContextState;
import com.draeger.medical.biceps.model.participant.VmdDescriptor;
import com.draeger.medical.biceps.model.participant.VmdState;
import com.draeger.medical.biceps.model.participant.WorkflowContextDescriptor;
import com.draeger.medical.biceps.model.participant.WorkflowContextState;
import com.google.inject.Inject;
import java.math.BigDecimal;
import java.util.List;
import javax.xml.datatype.Duration;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Utility to build mdibs used for unit testing.
 *
 * <p>
 * Only mandatory attributes are passed as arguments.
 */
public class MdibBuilder {

    public static final String DEFAULT_SEQUENCE_ID = "123456";
    public static final String DEFAULT_MDS_HANDLE = "mds0";

    private final ObjectFactory participantModelFactory;

    @Inject
    MdibBuilder(final ObjectFactory participantModelFactory) {
        this.participantModelFactory = participantModelFactory;
    }

    /**
     * Creates an Mdib with the given sequence id.
     *
     * @param sequenceId mdib sequence id
     * @return mdib with sequence
     */
    public Mdib buildMdib(final String sequenceId) {
        final Mdib mdib = participantModelFactory.createMdib();
        mdib.setSequenceId(sequenceId);
        return mdib;
    }

    /**
     * @return a new {@linkplain MdDescription}.
     */
    public MdDescription buildMdDescription() {
        return participantModelFactory.createMdDescription();
    }

    /**
     * @return a new {@linkplain MdState}.
     */
    public MdState buildMdState() {
        return participantModelFactory.createMdState();
    }

    /**
     * @param code for new coded value
     * @return a new coded value
     */
    public CodedValue buildCodedValue(final String code) {
        final var codedValue = participantModelFactory.createCodedValue();
        codedValue.setCode(code);
        return codedValue;
    }

    /**
     * @return a new localized text instance.
     */
    public LocalizedText buildLocalizedText() {
        return participantModelFactory.createLocalizedText();
    }

    /*
    MDS
    */

    /**
     * @param handle for new descriptor
     * @return new mds descriptor
     */
    public MdsDescriptor buildMdsDescriptor(final String handle) {
        final MdsDescriptor mdsDescriptor = participantModelFactory.createMdsDescriptor();
        mdsDescriptor.setHandle(handle);
        return mdsDescriptor;
    }

    /**
     * @param handle of descriptor for new state
     * @return new mds state
     */
    public MdsState buildMdsState(final String handle) {
        final MdsState mdsState = participantModelFactory.createMdsState();
        mdsState.setDescriptorHandle(handle);
        return mdsState;
    }

    /**
     * Builds a new pair of mds descriptor and state.
     *
     * @param handle for new descriptor and state
     * @return new mds descriptor and state
     */
    public Pair<MdsDescriptor, MdsState> buildMds(final String handle) {
        final var mdsDescriptor = buildMdsDescriptor(handle);
        final var mdsState = buildMdsState(handle);
        return new ImmutablePair<>(mdsDescriptor, mdsState);
    }

    /**
     * @return new metadata instance
     */
    public MdsDescriptor.MetaData buildMdsDescriptorMetadata() {
        return participantModelFactory.createMdsDescriptorMetaData();
    }

    /**
     * @param deviceIdentifier  device identifier
     * @param humanReadableForm human readable form
     * @param issuer            issuer
     * @return new udi for mds metadata
     */
    public MdsDescriptor.MetaData.Udi buildMdsDescriptorMetaDataUdi(
            final String deviceIdentifier, final String humanReadableForm, final InstanceIdentifier issuer) {
        final var udi = participantModelFactory.createMdsDescriptorMetaDataUdi();
        udi.setDeviceIdentifier(deviceIdentifier);
        udi.setHumanReadableForm(humanReadableForm);
        udi.setIssuer(issuer);
        return udi;
    }

    /*
    VMD
    */

    /**
     * @param handle for new descriptor
     * @return new vmd descriptor
     */
    public VmdDescriptor buildVmdDescriptor(final String handle) {
        final var vmdDescriptor = participantModelFactory.createVmdDescriptor();
        vmdDescriptor.setHandle(handle);
        return vmdDescriptor;
    }

    /**
     * @param handle of descriptor for new state
     * @return new vmd state
     */
    public VmdState buildVmdState(final String handle) {
        final var vmdState = participantModelFactory.createVmdState();
        vmdState.setDescriptorHandle(handle);
        return vmdState;
    }

    /**
     * Builds a new pair of vmd descriptor and state.
     *
     * @param handle for new descriptor and state
     * @return new vmd descriptor and state
     */
    public Pair<VmdDescriptor, VmdState> buildVmd(final String handle) {
        final var vmdDescriptor = buildVmdDescriptor(handle);
        final var vmdState = buildVmdState(handle);
        return new ImmutablePair<>(vmdDescriptor, vmdState);
    }

    /*
    Channel
    */

    /**
     * @param handle for new descriptor
     * @return new channel descriptor
     */
    public ChannelDescriptor buildChannelDescriptor(final String handle) {
        final var channelDescriptor = participantModelFactory.createChannelDescriptor();
        channelDescriptor.setHandle(handle);
        return channelDescriptor;
    }

    /**
     * @param handle of descriptor for new state
     * @return new channel state
     */
    public ChannelState buildChannelState(final String handle) {
        final var channelState = participantModelFactory.createChannelState();
        channelState.setDescriptorHandle(handle);
        return channelState;
    }

    /**
     * Builds a new pair of channel descriptor and state.
     *
     * @param handle for new descriptor and state
     * @return new channel descriptor and state
     */
    public Pair<ChannelDescriptor, ChannelState> buildChannel(final String handle) {
        final var vmdDescriptor = buildChannelDescriptor(handle);
        final var vmdState = buildChannelState(handle);
        return new ImmutablePair<>(vmdDescriptor, vmdState);
    }

    /**
     * @param handle for new descriptor
     * @return new clock descriptor
     */
    public ClockDescriptor buildClockDescriptor(final String handle) {
        final var channelDescriptor = participantModelFactory.createClockDescriptor();
        channelDescriptor.setHandle(handle);
        return channelDescriptor;
    }

    /**
     * @param handle of descriptor for new state
     * @return new clock state
     */
    public ClockState buildClockState(final String handle) {
        final var channelState = participantModelFactory.createClockState();
        channelState.setDescriptorHandle(handle);
        return channelState;
    }

    /**
     * Builds a new pair of clock descriptor and state.
     *
     * @param handle for new descriptor and state
     * @return new clock descriptor and state
     */
    public Pair<ClockDescriptor, ClockState> buildClock(final String handle) {
        final var clockDescriptor = buildClockDescriptor(handle);
        final var clockState = buildClockState(handle);
        return new ImmutablePair<>(clockDescriptor, clockState);
    }

    /*
    String Metric
    */

    /**
     * Builds a string metric value.
     *
     * @param value of the string metric
     * @return a new string metric value
     */
    public StringMetricValue buildStringMetricValue(final String value) {
        final var metricValue = participantModelFactory.createStringMetricValue();
        metricValue.setMetricQuality(participantModelFactory.createAbstractMetricValueMetricQuality());
        metricValue.getMetricQuality().setValidity(MeasurementValidity.INV);
        metricValue.setValue(value);
        return metricValue;
    }

    /**
     * @param handle       for new descriptor
     * @param category     of new descriptor
     * @param availability of new descriptor
     * @param unit         of new descriptor
     * @return a new string metric descriptor
     */
    public StringMetricDescriptor buildStringMetricDescriptor(
            final String handle,
            final MetricCategory category,
            final MetricAvailability availability,
            final CodedValue unit) {
        final var stringMetricDescriptor = participantModelFactory.createStringMetricDescriptor();
        stringMetricDescriptor.setHandle(handle);
        stringMetricDescriptor.setMetricCategory(category);
        stringMetricDescriptor.setMetricAvailability(availability);
        stringMetricDescriptor.setUnit(unit);
        return stringMetricDescriptor;
    }

    /**
     * @param handle of descriptor for new state
     * @return new string metric state
     */
    public StringMetricState buildStringMetricState(final String handle) {
        final var stringMetricState = participantModelFactory.createStringMetricState();
        stringMetricState.setDescriptorHandle(handle);
        return stringMetricState;
    }

    /**
     * Builds a new pair of string metric descriptor and state.
     *
     * @param handle       for new descriptor and state
     * @param category     of descriptor
     * @param availability of descriptor
     * @param unit         of descriptor
     * @return new string metric descriptor and state
     */
    public Pair<StringMetricDescriptor, StringMetricState> buildStringMetric(
            final String handle,
            final MetricCategory category,
            final MetricAvailability availability,
            final CodedValue unit) {
        final var stringMetricDescriptor = buildStringMetricDescriptor(handle, category, availability, unit);
        final var stringMetricState = buildStringMetricState(handle);
        return new ImmutablePair<>(stringMetricDescriptor, stringMetricState);
    }

    /*
    Enum String Metric
     */

    /**
     * @param handle        for new descriptor
     * @param category      of new descriptor
     * @param availability  of new descriptor
     * @param unit          of new descriptor
     * @param allowedValues List of AllowedValue of new descriptor
     * @return a new enum string metric descriptor
     */
    public EnumStringMetricDescriptor buildEnumStringMetricDescriptor(
            final String handle,
            final MetricCategory category,
            final MetricAvailability availability,
            final CodedValue unit,
            final List<EnumStringMetricDescriptor.AllowedValue> allowedValues) {
        final var enumStringMetricDescriptor = participantModelFactory.createEnumStringMetricDescriptor();
        enumStringMetricDescriptor.setHandle(handle);
        enumStringMetricDescriptor.setMetricCategory(category);
        enumStringMetricDescriptor.setMetricAvailability(availability);
        enumStringMetricDescriptor.setUnit(unit);
        enumStringMetricDescriptor.getAllowedValue().addAll(allowedValues);
        return enumStringMetricDescriptor;
    }

    /**
     * @param handle of descriptor for new state
     * @return new enum string metric state
     */
    public EnumStringMetricState buildEnumStringMetricState(final String handle) {
        final var enumStringMetricState = participantModelFactory.createEnumStringMetricState();
        enumStringMetricState.setDescriptorHandle(handle);
        return enumStringMetricState;
    }

    /**
     * Builds a new pair of enum string metric descriptor and state.
     *
     * @param handle       for new descriptor and state
     * @param category     of descriptor
     * @param availability of descriptor
     * @param unit         of descriptor
     * @param allowedValues List of AllowedValue of new descriptor
     * @return new enum string metric descriptor and state
     */
    public Pair<EnumStringMetricDescriptor, EnumStringMetricState> buildEnumStringMetric(
            final String handle,
            final MetricCategory category,
            final MetricAvailability availability,
            final CodedValue unit,
            final List<EnumStringMetricDescriptor.AllowedValue> allowedValues) {
        final var enumStringMetricDescriptor =
                buildEnumStringMetricDescriptor(handle, category, availability, unit, allowedValues);
        final var enumStringMetricState = buildEnumStringMetricState(handle);
        return new ImmutablePair<>(enumStringMetricDescriptor, enumStringMetricState);
    }

    /*
    Numeric Metric
    */

    /**
     * Builds a numeric metric value.
     *
     * @param value of the numeric metric
     * @return a new numeric metric value
     */
    public NumericMetricValue buildNumericMetricValue(final BigDecimal value) {
        final var metricValue = participantModelFactory.createNumericMetricValue();
        metricValue.setMetricQuality(participantModelFactory.createAbstractMetricValueMetricQuality());
        metricValue.getMetricQuality().setValidity(MeasurementValidity.INV);
        metricValue.setValue(value);
        return metricValue;
    }

    /**
     * @param handle       for new descriptor
     * @param category     of new descriptor
     * @param availability of new descriptor
     * @param unit         of new descriptor
     * @param resolution   of new descriptor
     * @return a new string metric descriptor
     */
    public NumericMetricDescriptor buildNumericMetricDescriptor(
            final String handle,
            final MetricCategory category,
            final MetricAvailability availability,
            final CodedValue unit,
            final BigDecimal resolution) {
        final var numericMetricDescriptor = participantModelFactory.createNumericMetricDescriptor();
        numericMetricDescriptor.setHandle(handle);
        numericMetricDescriptor.setMetricCategory(category);
        numericMetricDescriptor.setMetricAvailability(availability);
        numericMetricDescriptor.setUnit(unit);
        numericMetricDescriptor.setResolution(resolution);
        return numericMetricDescriptor;
    }

    /**
     * @param handle of descriptor for new state
     * @return new numeric metric state
     */
    public NumericMetricState buildNumericMetricState(final String handle) {
        final var numericMetricState = participantModelFactory.createNumericMetricState();
        numericMetricState.setDescriptorHandle(handle);
        return numericMetricState;
    }

    /**
     * Builds a new pair of numeric metric descriptor and state.
     *
     * @param handle       for new descriptor and state
     * @param category     of descriptor
     * @param availability of descriptor
     * @param unit         of descriptor
     * @param resolution   of descriptor
     * @return new string metric descriptor and state
     */
    public Pair<NumericMetricDescriptor, NumericMetricState> buildNumericMetric(
            final String handle,
            final MetricCategory category,
            final MetricAvailability availability,
            final CodedValue unit,
            final BigDecimal resolution) {
        final var numericMetricDescriptor =
                buildNumericMetricDescriptor(handle, category, availability, unit, resolution);
        final var numericMetricState = buildNumericMetricState(handle);
        return new ImmutablePair<>(numericMetricDescriptor, numericMetricState);
    }

    /**
     * Builds a sample array value.
     *
     * @param samples of the sample array value
     * @return a new sample array value
     */
    public SampleArrayValue buildSampleArrayValue(final List<BigDecimal> samples) {
        final var metricValue = participantModelFactory.createSampleArrayValue();
        metricValue.setMetricQuality(participantModelFactory.createAbstractMetricValueMetricQuality());
        metricValue.getMetricQuality().setValidity(MeasurementValidity.INV);
        metricValue.getSamples().addAll(samples);
        return metricValue;
    }

    /**
     * @param handle       for new descriptor
     * @param category     of new descriptor
     * @param availability of new descriptor
     * @param unit         of new descriptor
     * @param resolution   of new descriptor
     * @param samplePeriod of new descriptor
     * @return a new real time sample array metric descriptor
     */
    public RealTimeSampleArrayMetricDescriptor buildRealTimeSampleArrayMetricDescriptor(
            final String handle,
            final MetricCategory category,
            final MetricAvailability availability,
            final CodedValue unit,
            final BigDecimal resolution,
            final Duration samplePeriod) {
        final var realTimeSampleArrayMetricDescriptor =
                participantModelFactory.createRealTimeSampleArrayMetricDescriptor();
        realTimeSampleArrayMetricDescriptor.setHandle(handle);
        realTimeSampleArrayMetricDescriptor.setMetricCategory(category);
        realTimeSampleArrayMetricDescriptor.setMetricAvailability(availability);
        realTimeSampleArrayMetricDescriptor.setUnit(unit);
        realTimeSampleArrayMetricDescriptor.setResolution(resolution);
        realTimeSampleArrayMetricDescriptor.setSamplePeriod(samplePeriod);
        return realTimeSampleArrayMetricDescriptor;
    }

    /**
     * @param handle of descriptor for new state
     * @return new real time sample array metric state
     */
    public RealTimeSampleArrayMetricState buildRealTimeSampleArrayMetricState(final String handle) {
        final var realTimeSampleArrayMetricState = participantModelFactory.createRealTimeSampleArrayMetricState();
        realTimeSampleArrayMetricState.setDescriptorHandle(handle);
        return realTimeSampleArrayMetricState;
    }

    /**
     * Builds a new pair of real time sample array metric descriptor and state.
     *
     * @param handle       for new descriptor and state
     * @param category     of descriptor
     * @param availability of descriptor
     * @param unit         of descriptor
     * @param resolution   of descriptor
     * @param samplePeriod of descriptor
     * @return new real time sample array metric descriptor and state
     */
    public Pair<RealTimeSampleArrayMetricDescriptor, RealTimeSampleArrayMetricState> buildRealTimeSampleArrayMetric(
            final String handle,
            final MetricCategory category,
            final MetricAvailability availability,
            final CodedValue unit,
            final BigDecimal resolution,
            final Duration samplePeriod) {
        final var realTimeSampleArrayMetricDescriptor = buildRealTimeSampleArrayMetricDescriptor(
                handle, category, availability, unit, resolution, samplePeriod);
        final var realTimeSampleArrayMetricState = buildRealTimeSampleArrayMetricState(handle);
        return new ImmutablePair<>(realTimeSampleArrayMetricDescriptor, realTimeSampleArrayMetricState);
    }

    /**
     * @param handle            for new descriptor
     * @param category          of new descriptor
     * @param availability      of new descriptor
     * @param unit              of new descriptor
     * @param resolution        of new descriptor
     * @param domainUnit        of new descriptor
     * @param distributionRange of new descriptor
     * @return a new distribution sample array metric descriptor
     */
    public DistributionSampleArrayMetricDescriptor buildDistributionSampleArrayMetricDescriptor(
            final String handle,
            final MetricCategory category,
            final MetricAvailability availability,
            final CodedValue unit,
            final BigDecimal resolution,
            final CodedValue domainUnit,
            final Range distributionRange) {
        final var distributionSampleArrayMetricDescriptor =
                participantModelFactory.createDistributionSampleArrayMetricDescriptor();
        distributionSampleArrayMetricDescriptor.setHandle(handle);
        distributionSampleArrayMetricDescriptor.setMetricCategory(category);
        distributionSampleArrayMetricDescriptor.setMetricAvailability(availability);
        distributionSampleArrayMetricDescriptor.setUnit(unit);
        distributionSampleArrayMetricDescriptor.setResolution(resolution);
        distributionSampleArrayMetricDescriptor.setDomainUnit(domainUnit);
        distributionSampleArrayMetricDescriptor.setDistributionRange(distributionRange);

        return distributionSampleArrayMetricDescriptor;
    }

    /**
     * @param handle of descriptor for new state
     * @return new distribution sample array metric state
     */
    public DistributionSampleArrayMetricState buildDistributionSampleArrayMetricState(final String handle) {
        final var distributionSampleArrayMetricState =
                participantModelFactory.createDistributionSampleArrayMetricState();
        distributionSampleArrayMetricState.setDescriptorHandle(handle);
        return distributionSampleArrayMetricState;
    }

    /**
     * Builds a new pair of distribution sample array metric descriptor and state.
     *
     * @param handle            for new descriptor and state
     * @param category          of descriptor
     * @param availability      of descriptor
     * @param unit              of descriptor
     * @param resolution        of descriptor
     * @param domainUnit        of new descriptor
     * @param distributionRange of new descriptor
     * @return new distribution sample array metric descriptor and state
     */
    public Pair<DistributionSampleArrayMetricDescriptor, DistributionSampleArrayMetricState>
            buildDistributionSampleArrayMetric(
                    final String handle,
                    final MetricCategory category,
                    final MetricAvailability availability,
                    final CodedValue unit,
                    final BigDecimal resolution,
                    final CodedValue domainUnit,
                    final Range distributionRange) {
        final var distributionSampleArrayMetricDescriptor = buildDistributionSampleArrayMetricDescriptor(
                handle, category, availability, unit, resolution, domainUnit, distributionRange);
        final var distributionSampleArrayMetricState = buildDistributionSampleArrayMetricState(handle);
        return new ImmutablePair<>(distributionSampleArrayMetricDescriptor, distributionSampleArrayMetricState);
    }

    /*
    Alert system
    */

    /**
     * @param handle for new descriptor
     * @return new alert system descriptor
     */
    public AlertSystemDescriptor buildAlertSystemDescriptor(final String handle) {
        final var alertSystemDescriptor = participantModelFactory.createAlertSystemDescriptor();
        alertSystemDescriptor.setHandle(handle);
        return alertSystemDescriptor;
    }

    /**
     * @param handle          of descriptor for new state
     * @param activationState of state
     * @return new alert system state
     */
    public AlertSystemState buildAlertSystemState(final String handle, final AlertActivation activationState) {
        final var alertSystemState = participantModelFactory.createAlertSystemState();
        alertSystemState.setDescriptorHandle(handle);
        alertSystemState.setActivationState(activationState);
        return alertSystemState;
    }

    /**
     * Builds a new pair of alert ystem descriptor and state.
     *
     * @param handle          for new descriptor and state
     * @param activationState of state
     * @return new alert system descriptor and state
     */
    public Pair<AlertSystemDescriptor, AlertSystemState> buildAlertSystem(
            final String handle, final AlertActivation activationState) {
        final var alertSystemDescriptor = buildAlertSystemDescriptor(handle);
        final var alertSystemState = buildAlertSystemState(handle, activationState);
        return new ImmutablePair<>(alertSystemDescriptor, alertSystemState);
    }

    /*
    Alert condition
    */

    /**
     * @param handle   for new descriptor
     * @param kind     of descriptor
     * @param priority of descriptor
     * @return new alert condition descriptor
     */
    public AlertConditionDescriptor buildAlertConditionDescriptor(
            final String handle, final AlertConditionKind kind, final AlertConditionPriority priority) {
        final var alertConditionDescriptor = participantModelFactory.createAlertConditionDescriptor();
        alertConditionDescriptor.setHandle(handle);
        alertConditionDescriptor.setKind(kind);
        alertConditionDescriptor.setPriority(priority);
        return alertConditionDescriptor;
    }

    /**
     * @param handle          of descriptor for new state
     * @param activationState of state
     * @return new alert condition state
     */
    public AlertConditionState buildAlertConditionState(final String handle, final AlertActivation activationState) {
        final var alertConditionState = participantModelFactory.createAlertConditionState();
        alertConditionState.setDescriptorHandle(handle);
        alertConditionState.setActivationState(activationState);
        return alertConditionState;
    }

    /**
     * Builds a new pair of alert condition descriptor and state.
     *
     * @param handle          for new descriptor and state
     * @param kind            of descriptor
     * @param priority        of descriptor
     * @param activationState of state
     * @return new alert condition descriptor and state
     */
    public Pair<AlertConditionDescriptor, AlertConditionState> buildAlertCondition(
            final String handle,
            final AlertConditionKind kind,
            final AlertConditionPriority priority,
            final AlertActivation activationState) {
        final var alertConditionDescriptor = buildAlertConditionDescriptor(handle, kind, priority);
        final var alertConditionState = buildAlertConditionState(handle, activationState);
        return new ImmutablePair<>(alertConditionDescriptor, alertConditionState);
    }

    /*
    Alert signal
    */

    /**
     * @param handle        for new descriptor
     * @param manifestation of descriptor
     * @param latching      of descriptor
     * @return new alert signal descriptor
     */
    public AlertSignalDescriptor buildAlertSignalDescriptor(
            final String handle, final AlertSignalManifestation manifestation, final boolean latching) {
        final var alertSignalDescriptor = participantModelFactory.createAlertSignalDescriptor();
        alertSignalDescriptor.setHandle(handle);
        alertSignalDescriptor.setManifestation(manifestation);
        alertSignalDescriptor.setLatching(latching);
        return alertSignalDescriptor;
    }

    /**
     * @param handle          of descriptor for new state
     * @param activationState of state
     * @return new alert signal state
     */
    public AlertSignalState buildAlertSignalState(final String handle, final AlertActivation activationState) {
        final var alertConditionState = participantModelFactory.createAlertSignalState();
        alertConditionState.setDescriptorHandle(handle);
        alertConditionState.setActivationState(activationState);
        return alertConditionState;
    }

    /**
     * Builds a new pair of alert signal descriptor and state.
     *
     * @param handle          for new descriptor and state
     * @param manifestation   of descriptor
     * @param latching        of descriptor
     * @param activationState of state
     * @return new alert signal descriptor and state
     */
    public Pair<AlertSignalDescriptor, AlertSignalState> buildAlertSignal(
            final String handle,
            final AlertSignalManifestation manifestation,
            final boolean latching,
            final AlertActivation activationState) {
        final var alertSignalDescriptor = buildAlertSignalDescriptor(handle, manifestation, latching);
        final var alertSignalState = buildAlertSignalState(handle, activationState);
        return new ImmutablePair<>(alertSignalDescriptor, alertSignalState);
    }

    /*
    Sco
    */

    /**
     * @param handle for new descriptor
     * @return new sco descriptor
     */
    public ScoDescriptor buildScoDescriptor(final String handle) {
        final var scoDescriptor = participantModelFactory.createScoDescriptor();
        scoDescriptor.setHandle(handle);
        return scoDescriptor;
    }

    /**
     * @param handle of descriptor for new state
     * @return new sco state
     */
    public ScoState buildScoState(final String handle) {
        final var scoState = participantModelFactory.createScoState();
        scoState.setDescriptorHandle(handle);
        return scoState;
    }

    /**
     * Builds a new pair of sco descriptor and state.
     *
     * @param handle for new descriptor and state
     * @return new sco descriptor and state
     */
    public Pair<ScoDescriptor, ScoState> buildSco(final String handle) {
        final var scoDescriptor = buildScoDescriptor(handle);
        final var scoState = buildScoState(handle);
        return new ImmutablePair<>(scoDescriptor, scoState);
    }

    /*
    Set string operation
    */

    /**
     * @param handle          for new descriptor
     * @param operationTarget of descriptor
     * @return new set string operation descriptor
     */
    public SetStringOperationDescriptor buildSetStringOperationDescriptor(
            final String handle, final String operationTarget) {
        final var setStringOperationDescriptor = participantModelFactory.createSetStringOperationDescriptor();
        setStringOperationDescriptor.setHandle(handle);
        setStringOperationDescriptor.setOperationTarget(operationTarget);
        return setStringOperationDescriptor;
    }

    /**
     * @param handle        of descriptor for new state
     * @param operatingMode of state
     * @return new set string operation state
     */
    public SetStringOperationState buildSetStringOperationState(
            final String handle, final OperatingMode operatingMode) {
        final var setStringOperationState = participantModelFactory.createSetStringOperationState();
        setStringOperationState.setDescriptorHandle(handle);
        setStringOperationState.setOperatingMode(operatingMode);
        return setStringOperationState;
    }

    /**
     * Builds a new pair of set string operation descriptor and state.
     *
     * @param handle          for new descriptor and state
     * @param operationTarget of descriptor
     * @param operatingMode   of state
     * @return new set string operation descriptor and state
     */
    public Pair<SetStringOperationDescriptor, SetStringOperationState> buildSetStringOperation(
            final String handle, final String operationTarget, final OperatingMode operatingMode) {
        final var setStringOperationDescriptor = buildSetStringOperationDescriptor(handle, operationTarget);
        final var setStringOperationState = buildSetStringOperationState(handle, operatingMode);
        return new ImmutablePair<>(setStringOperationDescriptor, setStringOperationState);
    }

    /*
    Activate operation operation
    */

    /**
     * @param handle          for new descriptor
     * @param operationTarget of descriptor
     * @return new activate operation descriptor
     */
    public ActivateOperationDescriptor buildActivateOperationDescriptor(
            final String handle, final String operationTarget) {
        final var activateOperationDescriptor = participantModelFactory.createActivateOperationDescriptor();
        activateOperationDescriptor.setHandle(handle);
        activateOperationDescriptor.setOperationTarget(operationTarget);
        return activateOperationDescriptor;
    }

    /**
     * @param handle        of descriptor for new state
     * @param operatingMode of state
     * @return new activate operation state
     */
    public ActivateOperationState buildActivateOperationState(final String handle, final OperatingMode operatingMode) {
        final var activateOperationState = participantModelFactory.createActivateOperationState();
        activateOperationState.setDescriptorHandle(handle);
        activateOperationState.setOperatingMode(operatingMode);
        return activateOperationState;
    }

    /**
     * Builds a new pair of activate operation descriptor and state.
     *
     * @param handle          for new descriptor and state
     * @param operationTarget of descriptor
     * @param operatingMode   of state
     * @return new activate operation descriptor and state
     */
    public Pair<ActivateOperationDescriptor, ActivateOperationState> buildActivateOperation(
            final String handle, final String operationTarget, final OperatingMode operatingMode) {
        final var activateOperationDescriptor = buildActivateOperationDescriptor(handle, operationTarget);
        final var activateOperationState = buildActivateOperationState(handle, operatingMode);
        return new ImmutablePair<>(activateOperationDescriptor, activateOperationState);
    }

    /*
    Battery
    */

    /**
     * @param handle for new descriptor
     * @return new battery descriptor
     */
    public BatteryDescriptor buildBatteryDescriptor(final String handle) {
        final var batteryDescriptor = participantModelFactory.createBatteryDescriptor();
        batteryDescriptor.setHandle(handle);
        return batteryDescriptor;
    }

    /**
     * @param handle of descriptor for new state
     * @return new battery state
     */
    public BatteryState buildBatteryState(final String handle) {
        final var batteryState = participantModelFactory.createBatteryState();
        batteryState.setDescriptorHandle(handle);
        return batteryState;
    }

    /**
     * Builds a new pair of battery descriptor and state.
     *
     * @param handle for new descriptor and state
     * @return new battery descriptor and state
     */
    public Pair<BatteryDescriptor, BatteryState> buildBattery(final String handle) {
        final var batteryDescriptor = buildBatteryDescriptor(handle);
        final var batteryState = buildBatteryState(handle);
        return new ImmutablePair<>(batteryDescriptor, batteryState);
    }

    /*
    Calibration
    */

    /**
     * @return new calibration info
     */
    public CalibrationInfo buildCalibrationInfo() {
        return participantModelFactory.createCalibrationInfo();
    }

    /**
     * @return new calibration info calibration documentation
     */
    public CalibrationInfo.CalibrationDocumentation buildCalibrationInfoCalibrationDocumentation() {
        return participantModelFactory.createCalibrationInfoCalibrationDocumentation();
    }

    /*
    Contexts
    */

    /**
     * @param handle for new descriptor
     * @return new system context descriptor
     */
    public SystemContextDescriptor buildSystemContextDescriptor(final String handle) {
        final var descriptor = participantModelFactory.createSystemContextDescriptor();
        descriptor.setHandle(handle);
        return descriptor;
    }

    /**
     * @param handle of descriptor for state
     * @return new system context state
     */
    public SystemContextState buildSystemContextState(final String handle) {
        final var state = participantModelFactory.createSystemContextState();
        state.setDescriptorHandle(handle);
        return state;
    }

    /**
     * Builds a new pair of system context descriptor and state.
     *
     * @param handle for new descriptor and state
     * @return new system context descriptor and state
     */
    public Pair<SystemContextDescriptor, SystemContextState> buildSystemContext(final String handle) {
        final var descriptor = buildSystemContextDescriptor(handle);
        final var state = buildSystemContextState(handle);
        return new ImmutablePair<>(descriptor, state);
    }

    /**
     * @param handle for new descriptor
     * @return new patient context descriptor
     */
    public PatientContextDescriptor buildPatientContextDescriptor(final String handle) {
        final var descriptor = participantModelFactory.createPatientContextDescriptor();
        descriptor.setHandle(handle);
        return descriptor;
    }

    /**
     * @param descriptorHandle of descriptor for new state
     * @param stateHandle      of new state
     * @return new patient context state
     */
    public PatientContextState buildPatientContextState(final String descriptorHandle, final String stateHandle) {
        final var state = participantModelFactory.createPatientContextState();
        state.setDescriptorHandle(descriptorHandle);
        state.setHandle(stateHandle);
        return state;
    }

    /**
     * Builds a new pair of patient context descriptor and state.
     *
     * @param descriptorHandle for new descriptor and state
     * @param stateHandle      for new state
     * @return new patient descriptor and state
     */
    public Pair<PatientContextDescriptor, PatientContextState> buildPatientContext(
            final String descriptorHandle, final String stateHandle) {
        final var descriptor = buildPatientContextDescriptor(descriptorHandle);
        final var state = buildPatientContextState(descriptorHandle, stateHandle);
        return new ImmutablePair<>(descriptor, state);
    }

    /**
     * @param handle for new descriptor
     * @return new location context descriptor
     */
    public LocationContextDescriptor buildLocationContextDescriptor(final String handle) {
        final var descriptor = participantModelFactory.createLocationContextDescriptor();
        descriptor.setHandle(handle);
        return descriptor;
    }

    /**
     * @param descriptorHandle of descriptor for new state
     * @param stateHandle      of new state
     * @return new location context state
     */
    public LocationContextState buildLocationContextState(final String descriptorHandle, final String stateHandle) {
        final var state = participantModelFactory.createLocationContextState();
        state.setDescriptorHandle(descriptorHandle);
        state.setHandle(stateHandle);
        return state;
    }

    /**
     * Builds a new pair of location context descriptor and state.
     *
     * @param descriptorHandle for new descriptor and state
     * @param stateHandle      for new state
     * @return new location descriptor and state
     */
    public Pair<LocationContextDescriptor, LocationContextState> buildLocationContext(
            final String descriptorHandle, final String stateHandle) {
        final var descriptor = buildLocationContextDescriptor(descriptorHandle);
        final var state = buildLocationContextState(descriptorHandle, stateHandle);
        return new ImmutablePair<>(descriptor, state);
    }

    /**
     * @param handle for new descriptor
     * @return new ensemble context descriptor
     */
    public EnsembleContextDescriptor buildEnsembleContextDescriptor(final String handle) {
        final var descriptor = participantModelFactory.createEnsembleContextDescriptor();
        descriptor.setHandle(handle);
        return descriptor;
    }

    /**
     * @param descriptorHandle of descriptor for new state
     * @param stateHandle      of new state
     * @return new ensemble context state
     */
    public EnsembleContextState buildEnsembleContextState(final String descriptorHandle, final String stateHandle) {
        final var state = participantModelFactory.createEnsembleContextState();
        state.setDescriptorHandle(descriptorHandle);
        state.setHandle(stateHandle);
        return state;
    }

    /**
     * Builds a new pair of ensemble context descriptor and state.
     *
     * @param descriptorHandle for new descriptor and state
     * @param stateHandle      for new state
     * @return new ensemble descriptor and state
     */
    public Pair<EnsembleContextDescriptor, EnsembleContextState> buildEnsembleContext(
            final String descriptorHandle, final String stateHandle) {
        final var descriptor = buildEnsembleContextDescriptor(descriptorHandle);
        final var state = buildEnsembleContextState(descriptorHandle, stateHandle);
        return new ImmutablePair<>(descriptor, state);
    }

    /**
     * @param handle for new descriptor
     * @return new operator context descriptor
     */
    public OperatorContextDescriptor buildOperatorContextDescriptor(final String handle) {
        final var descriptor = participantModelFactory.createOperatorContextDescriptor();
        descriptor.setHandle(handle);
        return descriptor;
    }

    /**
     * @param descriptorHandle of descriptor for new state
     * @param stateHandle      of new state
     * @return new operator context state
     */
    public OperatorContextState buildOperatorContextState(final String descriptorHandle, final String stateHandle) {
        final var state = participantModelFactory.createOperatorContextState();
        state.setDescriptorHandle(descriptorHandle);
        state.setHandle(stateHandle);
        return state;
    }

    /**
     * Builds a new pair of operator context descriptor and state.
     *
     * @param descriptorHandle for new descriptor and state
     * @param stateHandle      for new state
     * @return new operator descriptor and state
     */
    public Pair<OperatorContextDescriptor, OperatorContextState> buildOperatorContext(
            final String descriptorHandle, final String stateHandle) {
        final var descriptor = buildOperatorContextDescriptor(descriptorHandle);
        final var state = buildOperatorContextState(descriptorHandle, stateHandle);
        return new ImmutablePair<>(descriptor, state);
    }

    /**
     * @param handle for new descriptor
     * @return new means context descriptor
     */
    public MeansContextDescriptor buildMeansContextDescriptor(final String handle) {
        final var descriptor = participantModelFactory.createMeansContextDescriptor();
        descriptor.setHandle(handle);
        return descriptor;
    }

    /**
     * @param descriptorHandle of descriptor for new state
     * @param stateHandle      of new state
     * @return new means context state
     */
    public MeansContextState buildMeansContextState(final String descriptorHandle, final String stateHandle) {
        final var state = participantModelFactory.createMeansContextState();
        state.setDescriptorHandle(descriptorHandle);
        state.setHandle(stateHandle);
        return state;
    }

    /**
     * Builds a new pair of means context descriptor and state.
     *
     * @param descriptorHandle for new descriptor and state
     * @param stateHandle      for new state
     * @return new means descriptor and state
     */
    public Pair<MeansContextDescriptor, MeansContextState> buildMeansContext(
            final String descriptorHandle, final String stateHandle) {
        final var descriptor = buildMeansContextDescriptor(descriptorHandle);
        final var state = buildMeansContextState(descriptorHandle, stateHandle);
        return new ImmutablePair<>(descriptor, state);
    }

    /**
     * @param handle for new descriptor
     * @return new workflow context descriptor
     */
    public WorkflowContextDescriptor buildWorkflowContextDescriptor(final String handle) {
        final var descriptor = participantModelFactory.createWorkflowContextDescriptor();
        descriptor.setHandle(handle);
        return descriptor;
    }

    /**
     * @param descriptorHandle of descriptor for new state
     * @param stateHandle      of new state
     * @return new workflow context state
     */
    public WorkflowContextState buildWorkflowContextState(final String descriptorHandle, final String stateHandle) {
        final var state = participantModelFactory.createWorkflowContextState();
        state.setDescriptorHandle(descriptorHandle);
        state.setHandle(stateHandle);
        return state;
    }

    /**
     * Builds a new pair of workflow context descriptor and state.
     *
     * @param descriptorHandle for new descriptor and state
     * @param stateHandle      for new state
     * @return new workflow descriptor and state
     */
    public Pair<WorkflowContextDescriptor, WorkflowContextState> buildWorkflowContext(
            final String descriptorHandle, final String stateHandle) {
        final var descriptor = buildWorkflowContextDescriptor(descriptorHandle);
        final var state = buildWorkflowContextState(descriptorHandle, stateHandle);
        return new ImmutablePair<>(descriptor, state);
    }

    /*
    Misc
    */

    /**
     * @return new instance identifier
     */
    public InstanceIdentifier buildInstanceIdentifier() {
        return participantModelFactory.createInstanceIdentifier();
    }

    /**
     * @return new physical connector info
     */
    public PhysicalConnectorInfo buildPhysicalConnectorInfo() {
        return participantModelFactory.createPhysicalConnectorInfo();
    }

    /**
     * @return new cause info
     */
    public CauseInfo buildCauseInfo() {
        return participantModelFactory.createCauseInfo();
    }

    /**
     * Create an Mdib with a single Mds.
     *
     * @return an Mdib with a single mds
     */
    public Mdib buildMinimalMdib() {
        return buildMinimalMdib(DEFAULT_SEQUENCE_ID);
    }

    /**
     * Create an Mdib with a single Mds.
     *
     * @param sequenceId for the new mdib
     * @return an Mdib with a single mds
     */
    public Mdib buildMinimalMdib(final String sequenceId) {
        final var mds = buildMds(DEFAULT_MDS_HANDLE);

        final var mdDescription = buildMdDescription();
        final var mdState = buildMdState();

        mdDescription.getMds().clear();
        mdDescription.getMds().addAll(List.of(mds.getLeft()));
        mdState.getState().clear();
        mdState.getState().addAll(List.of(mds.getRight()));

        final var mdib = buildMdib(sequenceId);
        mdib.setMdDescription(mdDescription);
        mdib.setMdState(mdState);
        return mdib;
    }
}
