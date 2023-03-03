/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2023 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.tests.biceps.invariant;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.draeger.medical.biceps.model.message.InvocationState;
import com.draeger.medical.biceps.model.participant.AlertActivation;
import com.draeger.medical.biceps.model.participant.AlertConditionKind;
import com.draeger.medical.biceps.model.participant.AlertConditionPriority;
import com.draeger.medical.biceps.model.participant.LocalizedText;
import com.draeger.medical.biceps.model.participant.MdState;
import com.draeger.medical.biceps.model.participant.MdsDescriptor;
import com.draeger.medical.biceps.model.participant.OperatingMode;
import com.draeger.medical.dpws.soap.model.Envelope;
import com.draeger.medical.sdccc.marshalling.MarshallingUtil;
import com.draeger.medical.sdccc.messages.MessageStorage;
import com.draeger.medical.sdccc.sdcri.testclient.TestClient;
import com.draeger.medical.sdccc.tests.InjectorTestBase;
import com.draeger.medical.sdccc.tests.test_util.InjectorUtil;
import com.draeger.medical.sdccc.tests.util.NoTestData;
import com.draeger.medical.sdccc.util.MdibBuilder;
import com.draeger.medical.sdccc.util.MessageBuilder;
import com.draeger.medical.sdccc.util.MessageStorageUtil;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.somda.sdc.glue.common.ActionConstants;

/**
 * Unit test for the BICEPS {@linkplain InvariantBicepsNormativeAnnexTest}.
 */
public class InvariantBicepsNormativeAnnexTestTest {
    private static final Logger LOG = LogManager.getLogger(InvariantBicepsNormativeAnnexTestTest.class);

    private static MessageStorageUtil messageStorageUtil;
    private static MdibBuilder mdibBuilder;
    private static MessageBuilder messageBuilder;
    private InvariantBicepsNormativeAnnexTest testClass;
    private MessageStorage storage;

    @BeforeAll
    static void setupMarshalling() {
        final Injector marshallingInjector = MarshallingUtil.createMarshallingTestInjector(true);
        messageStorageUtil = marshallingInjector.getInstance(MessageStorageUtil.class);
        mdibBuilder = marshallingInjector.getInstance(MdibBuilder.class);
        messageBuilder = marshallingInjector.getInstance(MessageBuilder.class);
    }

    @BeforeEach
    void setUp() throws IOException {
        final TestClient mockClient = mock(TestClient.class);
        when(mockClient.isClientRunning()).thenReturn(true);

        final Injector injector = InjectorUtil.setupInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(TestClient.class).toInstance(mockClient);
            }
        });

        InjectorTestBase.setInjector(injector);

        storage = injector.getInstance(MessageStorage.class);

        testClass = new InvariantBicepsNormativeAnnexTest();
        testClass.setUp();
    }

    @AfterEach
    void testDown() throws IOException {
        storage.close();
    }

    /**
     * Tests whether calling the tests without any input data causes a failure.
     */
    @Test
    public void testNoTestData() {
        assertThrows(NoTestData.class, () -> testClass.testRequirement5006());
    }

    /**
     * Tests whether correct messages containing localized text with ref and version attributes
     * are passing.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5006Good() throws Exception {
        messageStorageUtil.addInboundSecureHttpMessage(
                storage,
                buildMdibWithLocalizedTextVersions(
                        BigInteger.ZERO,
                        BigInteger.ZERO,
                        BigInteger.ZERO,
                        BigInteger.ZERO,
                        BigInteger.ZERO,
                        BigInteger.ZERO,
                        BigInteger.ZERO));

        messageStorageUtil.addInboundSecureHttpMessage(storage, createGetLocalizedTextResponseWithRef(BigInteger.ONE));

        messageStorageUtil.addInboundSecureHttpMessage(storage, createSetStringResponseWithRef(BigInteger.ONE));

        messageStorageUtil.addInboundSecureHttpMessage(storage, createSystemErrorReportWithRef(BigInteger.ONE));

        testClass.testRequirement5006();
    }

    /**
     * Tests whether a missing localized text @Version in pm:ConceptDescription causes a failure.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5006Bad0() throws Exception {
        messageStorageUtil.addInboundSecureHttpMessage(
                storage,
                buildMdibWithLocalizedTextVersions(
                        null,
                        BigInteger.ZERO,
                        BigInteger.ZERO,
                        BigInteger.ZERO,
                        BigInteger.ZERO,
                        BigInteger.ZERO,
                        BigInteger.ZERO));

        assertThrows(AssertionError.class, testClass::testRequirement5006);
    }

    /**
     * Tests whether a missing localized text @Version in pm:CauseInfo/pm:Description causes a failure.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5006Bad1() throws Exception {
        messageStorageUtil.addInboundSecureHttpMessage(
                storage,
                buildMdibWithLocalizedTextVersions(
                        BigInteger.ZERO,
                        null,
                        BigInteger.ZERO,
                        BigInteger.ZERO,
                        BigInteger.ZERO,
                        BigInteger.ZERO,
                        BigInteger.ZERO));
        assertThrows(AssertionError.class, testClass::testRequirement5006);
    }

    /**
     * Tests whether a missing localized text @Version in pm:Issuer/pm:IdentifierName causes a failure.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5006Bad2() throws Exception {
        messageStorageUtil.addInboundSecureHttpMessage(
                storage,
                buildMdibWithLocalizedTextVersions(
                        BigInteger.ZERO,
                        BigInteger.ZERO,
                        null,
                        BigInteger.ZERO,
                        BigInteger.ZERO,
                        BigInteger.ZERO,
                        BigInteger.ZERO));
        assertThrows(AssertionError.class, testClass::testRequirement5006);
    }

    /**
     * Tests whether a missing localized text @Version in pm:Manufacturer causes a failure.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5006Bad3() throws Exception {
        messageStorageUtil.addInboundSecureHttpMessage(
                storage,
                buildMdibWithLocalizedTextVersions(
                        BigInteger.ZERO,
                        BigInteger.ZERO,
                        BigInteger.ZERO,
                        null,
                        BigInteger.ZERO,
                        BigInteger.ZERO,
                        BigInteger.ZERO));
        assertThrows(AssertionError.class, testClass::testRequirement5006);
    }

    /**
     * Tests whether a missing localized text @Version in pm:Type/pm:CodingSystemName causes a failure.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5006Bad4() throws Exception {
        messageStorageUtil.addInboundSecureHttpMessage(
                storage,
                buildMdibWithLocalizedTextVersions(
                        BigInteger.ZERO,
                        BigInteger.ZERO,
                        BigInteger.ZERO,
                        BigInteger.ZERO,
                        null,
                        BigInteger.ZERO,
                        BigInteger.ZERO));
        assertThrows(AssertionError.class, testClass::testRequirement5006);
    }

    /**
     * Tests whether a missing localized text @Version in pm:CalibrationDocumentation/pm:Documentation causes a failure.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5006Bad5() throws Exception {
        messageStorageUtil.addInboundSecureHttpMessage(
                storage,
                buildMdibWithLocalizedTextVersions(
                        BigInteger.ZERO,
                        BigInteger.ZERO,
                        BigInteger.ZERO,
                        BigInteger.ZERO,
                        BigInteger.ZERO,
                        null,
                        BigInteger.ZERO));
        assertThrows(AssertionError.class, testClass::testRequirement5006);
    }

    /**
     * Tests whether a missing localized text @Version in pm:PhysicalConnector/pm:Label causes a failure.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5006Bad6() throws Exception {
        messageStorageUtil.addInboundSecureHttpMessage(
                storage,
                buildMdibWithLocalizedTextVersions(
                        BigInteger.ZERO,
                        BigInteger.ZERO,
                        BigInteger.ZERO,
                        BigInteger.ZERO,
                        BigInteger.ZERO,
                        BigInteger.ZERO,
                        null));
        assertThrows(AssertionError.class, testClass::testRequirement5006);
    }

    /**
     * Tests whether a missing localized text @Version in msg:GetLocalizedTextResponse/msg:Text causes a failure.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5006Bad7() throws Exception {
        messageStorageUtil.addInboundSecureHttpMessage(storage, createGetLocalizedTextResponseWithRef(null));
        assertThrows(AssertionError.class, testClass::testRequirement5006);
    }

    /**
     * Tests whether a missing localized text @Version in msg:InvocationInfo/msg:InvocationErrorMessage
     * causes a failure.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5006Bad8() throws Exception {
        messageStorageUtil.addInboundSecureHttpMessage(storage, createSetStringResponseWithRef(null));
        assertThrows(AssertionError.class, testClass::testRequirement5006);
    }

    /**
     * Tests whether a missing localized text @Version in msg:SystemErrorReport/msg:ReportPart/msg:ErrorInfo
     * causes a failure.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5006Bad9() throws Exception {
        messageStorageUtil.addInboundSecureHttpMessage(storage, createSystemErrorReportWithRef(null));
        assertThrows(AssertionError.class, testClass::testRequirement5006);
    }

    /**
     * Tests whether a missing localized text @Version and @Ref in
     * msg:SystemErrorReport/msg:ReportPart/msg:ErrorInfo
     * is not considered test data.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5006BadNoDataInMessage() throws Exception {
        messageStorageUtil.addInboundSecureHttpMessage(storage, createSystemErrorReportNoRef());
        assertThrows(NoTestData.class, testClass::testRequirement5006);
    }

    /*
     * Localized text mdib building
     */
    Envelope buildMdibWithLocalizedTextVersions(
            @Nullable final BigInteger mdsTypeRef,
            @Nullable final BigInteger alertRef,
            @Nullable final BigInteger operationRef,
            @Nullable final BigInteger metadataRef1,
            @Nullable final BigInteger metadataRef2,
            @Nullable final BigInteger batteryRef1,
            @Nullable final BigInteger batteryRef2) {
        final var mdib = mdibBuilder.buildMinimalMdib();

        final var mdState = mdib.getMdState();
        final var mdsDescriptor = mdib.getMdDescription().getMds().get(0);

        // localized text in mds -> type
        final var mdsType = mdibBuilder.buildCodedValue("mdsCode");
        mdsType.getConceptDescription().clear();
        mdsType.getConceptDescription().addAll(List.of(createLocalizedText("mdsType", mdsTypeRef)));
        mdsDescriptor.setType(mdsType);

        // alert condition localized text
        buildAlertLocalizedText(mdsDescriptor, mdState, alertRef);

        // operation localized text
        buildScoLocalizedText(mdsDescriptor, mdState, operationRef);

        // metadata localized texts
        buildMetadataLocalizedText(mdsDescriptor, metadataRef1, metadataRef2);

        // battery localized texts
        buildBatteryLocalizedText(mdsDescriptor, mdState, batteryRef1, batteryRef2);

        final var getMdibResponse = messageBuilder.buildGetMdibResponse(mdib.getSequenceId());
        getMdibResponse.setMdib(mdib);

        return messageBuilder.createSoapMessageWithBody(
                ActionConstants.getResponseAction(ActionConstants.ACTION_GET_MDIB), getMdibResponse);
    }

    void buildScoLocalizedText(
            final MdsDescriptor mdsDescriptor,
            final MdState mdState,
            @Nullable final BigInteger operationTypeRefVersion) {
        // localized text in sco -> operation -> type -> coding system name
        final var sco = mdibBuilder.buildSco("sco");
        mdsDescriptor.setSco(sco.getLeft());
        mdState.getState().add(sco.getRight());

        final var operation = mdibBuilder.buildSetStringOperation("operation", "target", OperatingMode.NA);
        sco.getLeft().getOperation().add(operation.getLeft());
        mdState.getState().add(operation.getRight());

        final var operationType = mdibBuilder.buildCodedValue("code");
        operationType.getCodingSystemName().clear();
        operationType
                .getCodingSystemName()
                .addAll(List.of(createLocalizedText("operationType", operationTypeRefVersion)));
        operation.getLeft().setType(operationType);
    }

    void buildMetadataLocalizedText(
            final MdsDescriptor mdsDescriptor,
            @Nullable final BigInteger manufacturerRefVersion,
            @Nullable final BigInteger issuerRefVersion) {
        // localized text in mds -> metadata
        final var metadata = mdibBuilder.buildMdsDescriptorMetadata();
        mdsDescriptor.setMetaData(metadata);

        metadata.getManufacturer().clear();
        metadata.getManufacturer().addAll(List.of(createLocalizedText("manufacturer", manufacturerRefVersion)));

        final var udiInstanceIdentifier = mdibBuilder.buildInstanceIdentifier();
        udiInstanceIdentifier.getIdentifierName().clear();
        udiInstanceIdentifier.getIdentifierName().add(createLocalizedText("udiInstanceIdentifier", issuerRefVersion));
        final var udi = mdibBuilder.buildMdsDescriptorMetaDataUdi("device", "human", udiInstanceIdentifier);
        metadata.getUdi().clear();
        metadata.getUdi().add(udi);
    }

    void buildAlertLocalizedText(
            final MdsDescriptor mdsDescriptor,
            final MdState mdState,
            @Nullable final BigInteger alertConditionRefVersion) {
        final var alertActivation = AlertActivation.OFF;

        // localized text in alert system -> alert condition -> cause info
        final var alertSystem = mdibBuilder.buildAlertSystem("alertsystem", alertActivation);
        mdsDescriptor.setAlertSystem(alertSystem.getLeft());
        mdState.getState().add(alertSystem.getRight());

        final var alertCondition = mdibBuilder.buildAlertCondition(
                "ac0", AlertConditionKind.OTH, AlertConditionPriority.NONE, alertActivation);
        alertSystem.getLeft().getAlertCondition().add(alertCondition.getLeft());
        mdState.getState().add(alertCondition.getRight());

        final var alertConditionCauseInfo = mdibBuilder.buildCauseInfo();

        alertConditionCauseInfo.getDescription().clear();
        alertConditionCauseInfo
                .getDescription()
                .add(createLocalizedText("alertConditionCauseInfo", alertConditionRefVersion));
        alertCondition.getLeft().getCauseInfo().clear();
        alertCondition.getLeft().getCauseInfo().add(alertConditionCauseInfo);
    }

    void buildBatteryLocalizedText(
            final MdsDescriptor mdsDescriptor,
            final MdState mdState,
            @Nullable final BigInteger calibrationDocumentationRefVersion,
            @Nullable final BigInteger physicalConnectorRefVersion) {
        // localized text in battery state -> calibration info -> calibration documentation
        final var battery = mdibBuilder.buildBattery("battery");
        mdsDescriptor.getBattery().clear();
        mdsDescriptor.getBattery().add(battery.getLeft());
        mdState.getState().add(battery.getRight());

        final var calibrationDocumentation = mdibBuilder.buildCalibrationInfoCalibrationDocumentation();
        calibrationDocumentation.getDocumentation().clear();
        calibrationDocumentation
                .getDocumentation()
                .add(createLocalizedText("calibrationDocumentation", calibrationDocumentationRefVersion));

        final var calibrationInfo = mdibBuilder.buildCalibrationInfo();
        battery.getRight().setCalibrationInfo(calibrationInfo);
        calibrationInfo.getCalibrationDocumentation().clear();
        calibrationInfo.getCalibrationDocumentation().add(calibrationDocumentation);

        // localized text in battery state -> physical connector -> label
        final var physicalConnector = mdibBuilder.buildPhysicalConnectorInfo();
        battery.getRight().setPhysicalConnector(physicalConnector);

        physicalConnector.getLabel().clear();
        physicalConnector.getLabel().add(createLocalizedText("physicalConnector", physicalConnectorRefVersion));
    }

    Envelope createGetLocalizedTextResponseWithRef(@Nullable final BigInteger version) {
        final var response = messageBuilder.buildGetLocalizedTextResponse("0");
        response.getText().add(createLocalizedText("someLocalizedText", version));

        return messageBuilder.createSoapMessageWithBody(
                ActionConstants.getResponseAction(ActionConstants.ACTION_GET_LOCALIZED_TEXT), response);
    }

    Envelope createSetStringResponseWithRef(@Nullable final BigInteger version) {
        final var invocationInfo = messageBuilder.buildInvocationInfo(123, InvocationState.FAIL);

        invocationInfo.getInvocationErrorMessage().clear();
        invocationInfo.getInvocationErrorMessage().add(createLocalizedText("invocationInfoErrorMessage", version));
        final var response = messageBuilder.buildSetStringResponse("0", invocationInfo);

        return messageBuilder.createSoapMessageWithBody(
                ActionConstants.getResponseAction(ActionConstants.ACTION_SET_STRING), response);
    }

    Envelope createSystemErrorReportWithRef(@Nullable final BigInteger version) {
        final var errorCode = mdibBuilder.buildCodedValue("errorcode");
        final var reportPart = messageBuilder.buildSystemErrorReportReportPart(errorCode);

        reportPart.setErrorInfo(createLocalizedText("errorInfo", version));

        final var response = messageBuilder.buildSystemErrorReport("0", List.of(reportPart));

        return messageBuilder.createSoapMessageWithBody(ActionConstants.ACTION_SYSTEM_ERROR_REPORT, response);
    }

    Envelope createSystemErrorReportNoRef() {
        final var errorCode = mdibBuilder.buildCodedValue("errorcode");
        final var reportPart = messageBuilder.buildSystemErrorReportReportPart(errorCode);
        final var response = messageBuilder.buildSystemErrorReport("0", List.of(reportPart));

        return messageBuilder.createSoapMessageWithBody(ActionConstants.ACTION_SYSTEM_ERROR_REPORT, response);
    }

    LocalizedText createLocalizedText(final String ref, @Nullable final BigInteger version) {
        final var localizedText = mdibBuilder.buildLocalizedText();
        localizedText.setRef(ref);
        localizedText.setVersion(version);
        return localizedText;
    }
}
