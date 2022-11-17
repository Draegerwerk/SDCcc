/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.tests.glue.invariant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import com.draeger.medical.sdccc.configuration.EnabledTestConfig;
import com.draeger.medical.sdccc.manipulation.precondition.impl.ConditionalPreconditions;
import com.draeger.medical.sdccc.messages.MessageStorage;
import com.draeger.medical.sdccc.messages.mapping.MessageContent;
import com.draeger.medical.sdccc.sdcri.testclient.TestClient;
import com.draeger.medical.sdccc.tests.InjectorTestBase;
import com.draeger.medical.sdccc.tests.annotations.RequirePrecondition;
import com.draeger.medical.sdccc.tests.annotations.TestDescription;
import com.draeger.medical.sdccc.tests.annotations.TestIdentifier;
import com.draeger.medical.sdccc.tests.util.CryptoUtil;
import com.draeger.medical.sdccc.tests.util.NoTestData;
import com.draeger.medical.sdccc.util.Constants;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.somda.sdc.biceps.model.message.OperationInvokedReport;
import org.somda.sdc.dpws.soap.MarshallingService;
import org.somda.sdc.dpws.soap.SoapUtil;
import org.somda.sdc.dpws.soap.exception.MarshallingException;

/**
 * Glue remote-control capabilities tests.
 */
public class InvariantRemoteControlCapabilitiesTest extends InjectorTestBase {

    public static final String WRONG_ROOT_ERROR_MESSAGE = "Root is not the expected root%n%s%nbut is%n%s%n";
    public static final String WRONG_EXTENSION_ERROR_MESSAGE =
            "Extension is not the expected extension%n%s%nbut" + " is%n%s%n";
    private MessageStorage messageStorage;
    private MarshallingService marshalling;
    private SoapUtil soapUtil;

    @BeforeEach
    void setup() {
        this.messageStorage = getInjector().getInstance(MessageStorage.class);
        final var riInjector = getInjector().getInstance(TestClient.class).getInjector();
        this.marshalling = riInjector.getInstance(MarshallingService.class);
        this.soapUtil = riInjector.getInstance(SoapUtil.class);
    }

    @Test
    @DisplayName("An SDC SERVICE PROVIDER SHALL set pm:OperationInvokedReport/pm:ReportPart/pm:InvocationSource to"
            + " - @Root = “http://standards.ieee.org/downloads/11073/11073-20701-2018/X509Certificate/PEM”\n"
            + " - @Extension =  being the X.509 certificate of the SDC PARTICIPANT that invoked the SERVICE OPERATION"
            + " referenced by the enclosing report part, encoded as PEM text.")
    @TestIdentifier(EnabledTestConfig.GLUE_R0078_0)
    @TestDescription("Starting from the initially retrieved mdib, checks every operation invoked report and verifies,"
            + " that for each report part the root and extension of the invocation source is set as specified.")
    @RequirePrecondition(
            simplePreconditions = {ConditionalPreconditions.TriggerOperationInvokedReportPrecondition.class})
    void testRequirementR00780() throws NoTestData, IOException {
        final var expectedRoot = "http://standards.ieee.org/downloads/11073/11073-20701-2018/X509Certificate/PEM";

        try (final var messages = messageStorage.getInboundMessagesByBodyType(Constants.MSG_OPERATION_INVOKED_REPORT)) {
            final var operationInvokedReportsSeen = new AtomicInteger(0);
            messages.getStream().forEach(messageContent -> {
                try {
                    final var soapMessage = marshalling.unmarshal(
                            new ByteArrayInputStream(messageContent.getBody().getBytes(StandardCharsets.UTF_8)));
                    final var reportOpt = soapUtil.getBody(soapMessage, OperationInvokedReport.class);
                    if (reportOpt.isPresent()) {
                        for (var reportPart : reportOpt.orElseThrow().getReportPart()) {
                            operationInvokedReportsSeen.incrementAndGet();
                            final var expectedExtension = buildCertPemString(messageContent);

                            final var invocationSource = reportPart.getInvocationSource();
                            final var root = invocationSource.getRootName();
                            final var extension = invocationSource.getExtensionName();

                            assertEquals(
                                    expectedRoot, root, String.format(WRONG_ROOT_ERROR_MESSAGE, expectedRoot, root));
                            assertEquals(
                                    expectedExtension,
                                    extension,
                                    String.format(WRONG_EXTENSION_ERROR_MESSAGE, expectedExtension, extension));
                        }
                    }
                } catch (MarshallingException e) {
                    fail("Error unmarshalling MessageContent " + e);
                } catch (IOException e) {
                    fail("Error converting certificate to PEM String " + e);
                }
            });

            assertTestData(
                    operationInvokedReportsSeen.get(), "No OperationInvokedReports seen during test run, test failed.");
        }
    }

    private String buildCertPemString(final MessageContent messageContent) throws IOException {
        final StringBuilder certsPEMString = new StringBuilder();
        for (X509Certificate cert : messageContent.getCerts()) {
            certsPEMString.append(CryptoUtil.getCertificateAsPEMString(cert));
        }
        return certsPEMString.toString();
    }
}
