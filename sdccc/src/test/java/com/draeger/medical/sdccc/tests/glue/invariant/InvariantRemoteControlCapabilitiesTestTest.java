/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.tests.glue.invariant;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.draeger.medical.biceps.model.message.InvocationInfo;
import com.draeger.medical.biceps.model.message.InvocationState;
import com.draeger.medical.biceps.model.message.OperationInvokedReport;
import com.draeger.medical.biceps.model.participant.InstanceIdentifier;
import com.draeger.medical.biceps.model.participant.ObjectFactory;
import com.draeger.medical.dpws.soap.model.Envelope;
import com.draeger.medical.sdccc.marshalling.MarshallingUtil;
import com.draeger.medical.sdccc.messages.MessageStorage;
import com.draeger.medical.sdccc.sdcri.testclient.TestClient;
import com.draeger.medical.sdccc.sdcri.testclient.TestClientUtil;
import com.draeger.medical.sdccc.tests.InjectorTestBase;
import com.draeger.medical.sdccc.tests.test_util.InjectorUtil;
import com.draeger.medical.sdccc.tests.util.CryptoUtil;
import com.draeger.medical.sdccc.tests.util.NoTestData;
import com.draeger.medical.sdccc.util.MdibBuilder;
import com.draeger.medical.sdccc.util.MessageBuilder;
import com.draeger.medical.sdccc.util.MessageStorageUtil;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import java.io.IOException;
import java.math.BigInteger;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.somda.sdc.dpws.helper.JaxbMarshalling;
import org.somda.sdc.dpws.soap.SoapMarshalling;
import org.somda.sdc.glue.common.ActionConstants;

/**
 * Unit test for the GLUE {@linkplain InvariantRemoteControlCapabilitiesTest}.
 */
public class InvariantRemoteControlCapabilitiesTestTest {
    private static MessageBuilder messageBuilder;
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);
    private static final String EXPECTED_ROOT =
            "http://standards.ieee.org/downloads/11073/11073-20701-2018/X509Certificate/PEM";
    private static final String EXPECTED_EXTENSION = "-----BEGIN CERTIFICATE-----\n"
            + "MIIGDzCCA/egAwIBAgICEAEwDQYJKoZIhvcNAQELBQAwgZwxCzAJBgNVBAYTAkRF\n"
            + "MRswGQYDVQQIDBJTY2hsZXN3aWctSG9sc3RlaW4xFTATBgNVBAoMDERyYWVnZXIg\n"
            + "VGVzdDESMBAGA1UECwwJVGVzdCBVbml0MSUwIwYDVQQDDBxEcmFlZ2VyIFRlc3Qg\n"
            + "SW50ZXJtZWRpYXRlIENBMR4wHAYJKoZIhvcNAQkBFg90ZXN0QGRyYWVnZXIuZGUw\n"
            + "HhcNMjIwNDAxMTEzMzQ0WhcNMjMwNDExMTEzMzQ0WjCBszELMAkGA1UEBhMCREUx\n"
            + "GzAZBgNVBAgMElNjaGxlc3dpZy1Ib2xzdGVpbjEQMA4GA1UEBwwHTHVlYmVjazEV\n"
            + "MBMGA1UECgwMRHJhZWdlciBUZXN0MRIwEAYDVQQLDAlUZXN0IFVuaXQxKjAoBgNV\n"
            + "BAMMIURyYWVnZXIgVGVzdCBDb25zdW1lciBDZXJ0aWZpY2F0ZTEeMBwGCSqGSIb3\n"
            + "DQEJARYPdGVzdEBkcmFlZ2VyLmRlMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIB\n"
            + "CgKCAQEA66Rix3TkImJjtWw/cI2Jh3iH2JTkU3kNnefKdHJjjkKNL/IGgGEddaBN\n"
            + "UtcAl3DOtzdegvtNZj5AdlHmZJ1voAMPlRhtj92L/hWWaRvzDu/HfIrE/P+I2kmG\n"
            + "lWoG/Nd1jsIDZ7h3fefCKOgndu/5YOROegBeKpbQRBYzi0lSdYmWtFTtRqJObs84\n"
            + "p6d8S40YtIwO5dEVoy7STumr8Q9sqOYNICxcC+K1y2EpK19cjuUgQ6Uut/Xz03L4\n"
            + "VW72ykvhvqD/PW9nOzyk21PFBy1393CT5Scb9q55CAufCNJgMqrKeRdijf9lkwFS\n"
            + "YyD0rbknnGfpdogBaG9LKUHxBvm87wIDAQABo4IBQDCCATwwCQYDVR0TBAIwADAd\n"
            + "BgNVHQ4EFgQUzVHzDkHGKkNWyjSo6p+U0tcDYS4wgdQGA1UdIwSBzDCByYAUyqSj\n"
            + "QIC+xiv1arlRia4ra/35BCyhgaykgakwgaYxCzAJBgNVBAYTAkRFMRswGQYDVQQI\n"
            + "DBJTY2hsZXN3aWctSG9sc3RlaW4xEDAOBgNVBAcMB0x1ZWJlY2sxFTATBgNVBAoM\n"
            + "DERyYWVnZXIgVGVzdDESMBAGA1UECwwJVGVzdCBVbml0MR0wGwYDVQQDDBREcmFl\n"
            + "Z2VyIFRlc3QgUm9vdCBDQTEeMBwGCSqGSIb3DQEJARYPdGVzdEBkcmFlZ2VyLmRl\n"
            + "ggIQADAOBgNVHQ8BAf8EBAMCBaAwKQYDVR0lBCIwIAYIKwYBBQUHAwIGCCsGAQUF\n"
            + "BwMBBgoqhkjOFIGhXQECMA0GCSqGSIb3DQEBCwUAA4ICAQBzRw4NQwX3jMqyTnK0\n"
            + "+rQrJPLHDwBgXXmPjAPlzz6LU7IG+rRQBtJrFkcldY0uI8gnNkfR1nccdRgiAwLj\n"
            + "vosabjaCR4kcXZsuvqvNf51GAnkP8h+0m70YTwGu7OU0qFeleslCsDztSyJeQ3Bl\n"
            + "LaAaLUWLd0FkQreXc54JgtF+tyKCeFAnDXz+Vd1JFRRyzAPdZGVvPUF+yUlK3YJw\n"
            + "OVjOyj2uRFA8E5S+tggRzWQnW91MUlXzOLFBZz6JDZyxWWJkIakUnrF8BrK2wf51\n"
            + "R+mS0xAZ7e8EgHQ3F2WGi//G8tQzuWayQs+NEwFbSsD7wbnWWGpEc7nSHNhOdJAw\n"
            + "QNFOmRBU4RmE3VP9ocewR7Ztm+egq0Om5rh9hEHJN1WK54FzTy/zu8XIFC6Iip58\n"
            + "OzpFYohi5D19F8oSny+aMmIsunJXnZMAAN7Tga93xG+w/Dpqjn81bYDiTdBkCQH2\n"
            + "zQ1Zc+BTWXulP8Z7y7vlQtaY05ped8one9YV7cu1Gwd0tFvrAPxVIN8q2CYdTG+u\n"
            + "bh0MVoazFzFrDHww53R1CEa8ftZj17TzQ4e6S5qsTdiUwTf+hwpYvbNJ6A4Tj/Cy\n"
            + "LTyBWGV2cgbUKXNR49Ksi6iwJ0gBwfdI78PDceO2DV/gtHzKnTy1bYenut8G6/jR\n"
            + "Uc82jiDEoyayY7gDHpDc5LQMaQ==\n"
            + "-----END CERTIFICATE-----";
    private static MessageStorageUtil messageStorageUtil;
    private static MockedStatic<CryptoUtil> mockCryptoUtil;
    private MessageStorage storage;
    private ObjectFactory participantFactory;
    private JaxbMarshalling baseMarshalling;
    private SoapMarshalling marshalling;
    private InvariantRemoteControlCapabilitiesTest testClass;

    @BeforeAll
    static void setupMessageBuilder() {
        final Injector marshallingInjector = MarshallingUtil.createMarshallingTestInjector(true);
        messageBuilder = marshallingInjector.getInstance(MessageBuilder.class);
        messageStorageUtil = marshallingInjector.getInstance(MessageStorageUtil.class);
        mockCryptoUtil = mockStatic(CryptoUtil.class);
    }

    @AfterAll
    static void close() {
        mockCryptoUtil.close();
    }

    @BeforeEach
    void setup() throws IOException, TimeoutException {
        mockCryptoUtil.when(() -> CryptoUtil.getCertificateAsPEMString(any())).thenReturn(EXPECTED_EXTENSION);
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
        participantFactory = injector.getInstance(ObjectFactory.class);
        final var riInjector = TestClientUtil.createClientInjector();
        when(mockClient.getInjector()).thenReturn(riInjector);

        baseMarshalling = riInjector.getInstance(JaxbMarshalling.class);
        baseMarshalling.startAsync().awaitRunning(DEFAULT_TIMEOUT);
        marshalling = riInjector.getInstance(SoapMarshalling.class);
        marshalling.startAsync().awaitRunning(DEFAULT_TIMEOUT);

        testClass = new InvariantRemoteControlCapabilitiesTest();
        testClass.setup();
    }

    @AfterEach
    void tearDown() throws TimeoutException {
        baseMarshalling.stopAsync().awaitTerminated(DEFAULT_TIMEOUT);
        marshalling.stopAsync().awaitTerminated(DEFAULT_TIMEOUT);
    }

    /**
     * Tests whether the test case fails, when no operation invoked reports are present.
     */
    @Test
    public void testRequirementR00780NoTestData() {
        assertThrows(NoTestData.class, testClass::testRequirementR00780);
    }

    /**
     * Tests if the test case passes if each report part of every operation invoked report has the expected root and
     * extension in their invocation source set.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR00780Good() throws Exception {
        final var instanceIdentifier = participantFactory.createInstanceIdentifier();
        instanceIdentifier.setRootName(EXPECTED_ROOT);
        instanceIdentifier.setExtensionName(EXPECTED_EXTENSION);
        final var part = buildOperationInvokedReportReportPart(
                "handle", messageBuilder.buildInvocationInfo(1L, InvocationState.START), instanceIdentifier);
        final var report = buildOperationInvokedReport(MdibBuilder.DEFAULT_SEQUENCE_ID, BigInteger.ONE, part);
        messageStorageUtil.addInboundSecureHttpMessage(storage, report);
        testClass.testRequirementR00780();
    }

    /**
     * Tests if the test case fails when a report part does not have the expected root in their invocation source set.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR00780BadRootName() throws Exception {
        final var notTheRoot = "not the expected root name";
        final var instanceIdentifier = participantFactory.createInstanceIdentifier();
        instanceIdentifier.setRootName(notTheRoot);
        instanceIdentifier.setExtensionName(EXPECTED_EXTENSION);
        final var part = buildOperationInvokedReportReportPart(
                "handle", messageBuilder.buildInvocationInfo(1L, InvocationState.START), instanceIdentifier);
        final var report = buildOperationInvokedReport(MdibBuilder.DEFAULT_SEQUENCE_ID, BigInteger.ONE, part);
        messageStorageUtil.addInboundSecureHttpMessage(storage, report);
        final var error = assertThrows(AssertionError.class, testClass::testRequirementR00780);
        assertTrue(error.getMessage().contains(notTheRoot));
    }

    /**
     * Tests if the test case fails when a report part does not have the expected extension in their invocation source
     * set.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR00780BadExtensionName() throws Exception {
        final var notTheExtension = "not the expected extension name";
        final var instanceIdentifier = participantFactory.createInstanceIdentifier();
        instanceIdentifier.setRootName(EXPECTED_ROOT);
        instanceIdentifier.setExtensionName(notTheExtension);
        final var part = buildOperationInvokedReportReportPart(
                "handle", messageBuilder.buildInvocationInfo(1L, InvocationState.START), instanceIdentifier);
        final var report = buildOperationInvokedReport(MdibBuilder.DEFAULT_SEQUENCE_ID, BigInteger.ONE, part);
        messageStorageUtil.addInboundSecureHttpMessage(storage, report);
        final var error = assertThrows(AssertionError.class, testClass::testRequirementR00780);
        assertTrue(error.getMessage().contains(notTheExtension));
    }

    /**
     * Tests if the test case fails when at least one report part does not meet the requirements.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirementR00780BadReportPart() throws Exception {
        final var instanceIdentifier = participantFactory.createInstanceIdentifier();
        instanceIdentifier.setRootName(EXPECTED_ROOT);
        instanceIdentifier.setExtensionName(EXPECTED_EXTENSION);
        final var part = buildOperationInvokedReportReportPart(
                "handle", messageBuilder.buildInvocationInfo(1L, InvocationState.START), instanceIdentifier);
        final var secondInstanceIdentifier = participantFactory.createInstanceIdentifier();
        secondInstanceIdentifier.setRootName("some other root");
        secondInstanceIdentifier.setExtensionName(EXPECTED_EXTENSION);
        final var secondPart = buildOperationInvokedReportReportPart(
                "secondHandle",
                messageBuilder.buildInvocationInfo(1L, InvocationState.START),
                secondInstanceIdentifier);
        final var report =
                buildOperationInvokedReport(MdibBuilder.DEFAULT_SEQUENCE_ID, BigInteger.ONE, part, secondPart);
        messageStorageUtil.addInboundSecureHttpMessage(storage, report);
        assertThrows(AssertionError.class, testClass::testRequirementR00780);
    }

    OperationInvokedReport.ReportPart buildOperationInvokedReportReportPart(
            final String handleRef, final InvocationInfo invocationInfo, final InstanceIdentifier invocationSource) {
        final var reportPart = messageBuilder.buildOperationInvokedReportReportPart();
        reportPart.setOperationHandleRef(handleRef);
        reportPart.setInvocationInfo(invocationInfo);
        reportPart.setInvocationSource(invocationSource);
        return reportPart;
    }

    Envelope buildOperationInvokedReport(
            final String sequenceId,
            final @Nullable BigInteger mdibVersion,
            final OperationInvokedReport.ReportPart... reportParts) {
        final var report = messageBuilder.buildOperationInvokedReport(sequenceId);
        report.setMdibVersion(mdibVersion);
        for (var reportPart : reportParts) {
            report.getReportPart().add(reportPart);
        }
        return messageBuilder.createSoapMessageWithBody(ActionConstants.ACTION_OPERATION_INVOKED_REPORT, report);
    }
}
