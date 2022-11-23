/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.tests.glue.direct;

import com.draeger.medical.sdccc.manipulation.Manipulations;
import com.draeger.medical.sdccc.sdcri.testclient.TestClient;
import com.draeger.medical.sdccc.sdcri.testclient.TestClientUtil;
import com.draeger.medical.sdccc.tests.InjectorTestBase;
import com.draeger.medical.sdccc.tests.test_util.InjectorUtil;
import com.draeger.medical.sdccc.tests.util.HostedServiceVerifier;
import com.draeger.medical.sdccc.util.MessageGeneratingUtil;
import com.draeger.medical.t2iapi.ResponseTypes;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.name.Names;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.somda.sdc.biceps.model.message.EpisodicContextReport;
import org.somda.sdc.biceps.model.message.GetContextStatesResponse;
import org.somda.sdc.biceps.model.message.GetMdibResponse;
import org.somda.sdc.biceps.model.message.ObjectFactory;
import org.somda.sdc.biceps.model.participant.LocationContextDescriptor;
import org.somda.sdc.biceps.model.participant.LocationContextState;
import org.somda.sdc.biceps.model.participant.LocationDetail;
import org.somda.sdc.biceps.model.participant.MdDescription;
import org.somda.sdc.biceps.model.participant.Mdib;
import org.somda.sdc.biceps.model.participant.MdsDescriptor;
import org.somda.sdc.biceps.model.participant.SystemContextDescriptor;
import org.somda.sdc.dpws.CommunicationLog;
import org.somda.sdc.dpws.CommunicationLogSink;
import org.somda.sdc.dpws.factory.CommunicationLogFactory;
import org.somda.sdc.dpws.helper.JaxbMarshalling;
import org.somda.sdc.dpws.http.HttpServerRegistry;
import org.somda.sdc.dpws.model.HostedServiceType;
import org.somda.sdc.dpws.service.HostedServiceProxy;
import org.somda.sdc.dpws.service.HostingServiceProxy;
import org.somda.sdc.dpws.soap.CommunicationContext;
import org.somda.sdc.dpws.soap.NotificationSink;
import org.somda.sdc.dpws.soap.RequestResponseClient;
import org.somda.sdc.dpws.soap.SoapMessage;
import org.somda.sdc.dpws.soap.SoapUtil;
import org.somda.sdc.dpws.soap.exception.SoapFaultException;
import org.somda.sdc.dpws.soap.factory.NotificationSinkFactory;
import org.somda.sdc.dpws.soap.factory.SoapFaultFactory;
import org.somda.sdc.dpws.soap.interception.Interceptor;
import org.somda.sdc.dpws.soap.interception.NotificationObject;
import org.somda.sdc.dpws.soap.wseventing.EventSink;
import org.somda.sdc.dpws.soap.wseventing.SubscribeResult;
import org.somda.sdc.dpws.soap.wseventing.factory.WsEventingEventSinkFactory;
import org.somda.sdc.dpws.soap.wseventing.model.Subscribe;
import org.somda.sdc.dpws.soap.wseventing.model.SubscribeResponse;
import org.somda.sdc.dpws.wsdl.WsdlMarshalling;
import org.somda.sdc.dpws.wsdl.WsdlRetriever;
import org.somda.sdc.glue.common.ActionConstants;
import org.somda.sdc.glue.common.WsdlConstants;
import org.somda.sdc.glue.provider.SdcDevice;

import javax.annotation.Nullable;
import javax.xml.namespace.QName;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * Unit test for Glue {@linkplain DirectSubscriptionHandlingTest}.
 */
public class DirectSubscriptionHandlingTestTest {
    public static final long DEFAULT_DURATION_IN_SECONDS = 60 * 60 * 24L;
    public static final int INSIGNIFICANT_DELAY_IN_SECONDS = 3;
    public static final int SIGNIFICANT_DELAY_IN_SECONDS = 6;
    public static final String FRAMEWORK_IDENTIFIER = "frameworkIdentifier";

    private static final java.time.Duration MAX_WAIT = java.time.Duration.ofSeconds(10);
    private static final String LOW_PRIORITY_WSDL = "wsdl/IEEE11073-20701-LowPriority-Services.wsdl";
    private static final String HIGH_PRIORITY_WSDL = "wsdl/IEEE11073-20701-HighPriority-Services.wsdl";

    private static final String END_TO_URI = "endTo-URI";
    private static final String LOC_CONTEXT_DESCRIPTOR_HANDLE = "locContextDesc";
    private static final int NANOS_IN_A_MILLISECOND = 1000000;

    private TestClient testClient;
    private DirectSubscriptionHandlingTest testUnderTest;

    private WsdlRetriever wsdlRetriever;
    private JaxbMarshalling jaxbMarshalling;
    private WsdlMarshalling wsdlMarshalling;
    private HostedServiceVerifier hostedServiceVerifier;
    private HostingServiceProxy hostingServiceProxy;
    private MessageGeneratingUtil messageGeneratingUtil;
    private ObjectFactory messageModelFactory;
    private SoapUtil soapUtil;
    private SoapFaultFactory soapFaultFactory;
    private HttpServerRegistry httpServerRegistry;
    private Manipulations manipulations;
    private WsEventingEventSinkFactory eventSinkFactory;
    private CommunicationLogFactory communicationLogFactory;

    @Mock
    private NotificationSinkFactory notificationSinkFactory;

    private HashSet<String> reportsToCancel;
    private HashSet<String> cancelledReports;
    private HashSet<String> supportedReports;

    @BeforeEach
    void setUp() throws Exception {
        final TestClient mockClient = mock(TestClient.class);
        when(mockClient.isClientRunning()).thenReturn(true);
        testClient = mockClient;
        hostingServiceProxy = mock(HostingServiceProxy.class);
        messageGeneratingUtil = mock(MessageGeneratingUtil.class);
        httpServerRegistry = mock(HttpServerRegistry.class);
        eventSinkFactory = mock(WsEventingEventSinkFactory.class);
        manipulations = mock(Manipulations.class);
        wsdlRetriever = mock(WsdlRetriever.class);
        communicationLogFactory = mock(CommunicationLogFactory.class);
        final var communicationLogSink = mock(CommunicationLogSink.class);
        notificationSinkFactory = mock(NotificationSinkFactory.class); // testClient.getInjector().getInstance(NotificationSinkFactory.class);

        // set up the injector used by sdcri
        final var clientInjector = TestClientUtil.createClientInjector(
            new AbstractModule() {
                @Override
                protected void configure() {
                    bind(WsdlRetriever.class).toInstance(wsdlRetriever);
                    bind(MessageGeneratingUtil.class).toInstance(messageGeneratingUtil);
                    bind(HttpServerRegistry.class).toInstance(httpServerRegistry);
                    bind(WsEventingEventSinkFactory.class).toInstance(eventSinkFactory);
                    bind(String.class).annotatedWith(Names.named("Common.InstanceIdentifier"))
                        .toInstance(FRAMEWORK_IDENTIFIER);
                    bind(CommunicationLogSink.class).toInstance(communicationLogSink);
                    bind(CommunicationLogFactory.class).toInstance(communicationLogFactory);
                    bind(NotificationSinkFactory.class).toInstance(notificationSinkFactory);
                }
            }
        );
        when(testClient.getInjector()).thenReturn(clientInjector);
        when(testClient.getHostingServiceProxy()).thenReturn(hostingServiceProxy);

        final var originalHostedServiceVerifier = new HostedServiceVerifier(testClient);
        hostedServiceVerifier = spy(originalHostedServiceVerifier);

        final Injector injector = InjectorUtil.setupInjector(
            new AbstractModule() {
                @Override
                protected void configure() {
                    bind(TestClient.class).toInstance(testClient);
                    bind(HostedServiceVerifier.class).toInstance(hostedServiceVerifier);
                    bind(MessageGeneratingUtil.class).toInstance(messageGeneratingUtil);
                    bind(HttpServerRegistry.class).toInstance(httpServerRegistry);
                    bind(Manipulations.class).toInstance(manipulations);
                    bind(WsEventingEventSinkFactory.class).toInstance(eventSinkFactory);
                    bind(NotificationSinkFactory.class).toInstance(notificationSinkFactory);
                }
            }
        );

        InjectorTestBase.setInjector(injector);


        jaxbMarshalling = testClient.getInjector().getInstance(JaxbMarshalling.class);
        wsdlMarshalling = testClient.getInjector().getInstance(WsdlMarshalling.class);
        messageModelFactory = testClient.getInjector().getInstance(ObjectFactory.class);
        soapUtil = testClient.getInjector().getInstance(SoapUtil.class);
        soapFaultFactory = testClient.getInjector().getInstance(SoapFaultFactory.class);

        jaxbMarshalling.startAsync().awaitRunning(MAX_WAIT);
        wsdlMarshalling.startAsync().awaitRunning(MAX_WAIT);


        testUnderTest = new DirectSubscriptionHandlingTest() {
            @Override
            public String determineContextSuffix(final String reportName) {
                return reportName + "ContextSuffix";
            }

            @Override
            public String determineSubscriptionIdForAction(final String action) {
                return action;
            }
        };
        testUnderTest.setUp();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (wsdlMarshalling != null) {
            wsdlMarshalling.stopAsync().awaitTerminated(MAX_WAIT);
        }
        if (jaxbMarshalling != null) {
            jaxbMarshalling.stopAsync().awaitTerminated(MAX_WAIT);
        }
    }

    /**
     * Tests whether a device with description event service, state event service, context service and
     * waveform service in the same hosted service is passing the test.
     */
    @Test
    public void testRequirementR0034Good() throws Exception {
        setupHostingService(Map.of("hostedService",
            setupHostedService("hostedServiceId", WsdlConstants.PORT_TYPE_DESCRIPTION_EVENT_QNAME,
                WsdlConstants.PORT_TYPE_STATE_EVENT_QNAME,
                WsdlConstants.PORT_TYPE_CONTEXT_QNAME,
                WsdlConstants.PORT_TYPE_WAVEFORM_QNAME)));

        final String wsdl = loadWsdl(HIGH_PRIORITY_WSDL);
        final String wsdl2 = loadWsdl(LOW_PRIORITY_WSDL);
        when(wsdlRetriever.retrieveWsdls(any())).thenReturn(Map.of(
            "hostedService", List.of(wsdl, wsdl2)
        ));

        testUnderTest.testRequirementR0034();
    }

    /**
     * Tests whether the test passes if not all mentioned services are present but are in the same hosted service.
     */
    @Test
    public void testRequirementR0034GoodNotAllServicesPresent() throws Exception {
        setupHostingService(Map.of("hostedService",
            setupHostedService("hostedServiceId", WsdlConstants.PORT_TYPE_DESCRIPTION_EVENT_QNAME,
                WsdlConstants.PORT_TYPE_STATE_EVENT_QNAME)));

        final String wsdl = loadWsdl(HIGH_PRIORITY_WSDL);
        final String wsdl2 = loadWsdl(LOW_PRIORITY_WSDL);
        when(wsdlRetriever.retrieveWsdls(any())).thenReturn(Map.of(
            "hostedService", List.of(wsdl, wsdl2)
        ));

        testUnderTest.testRequirementR0034();
    }

    /**
     * Tests whether the test fails if the services are not in the same hosted service.
     */
    @Test
    public void testRequirementR0034Bad() throws Exception {
        setupHostingService(Map.of(
            "firstHostedService",
            setupHostedService("firstHostedServiceId",
                WsdlConstants.PORT_TYPE_DESCRIPTION_EVENT_QNAME,
                WsdlConstants.PORT_TYPE_STATE_EVENT_QNAME,
                WsdlConstants.PORT_TYPE_CONTEXT_QNAME),
            "secondHostedService",
            setupHostedService("secondHostedServiceId",
                WsdlConstants.PORT_TYPE_WAVEFORM_QNAME)
        ));
        final String wsdl = loadWsdl(HIGH_PRIORITY_WSDL);
        final String wsdl2 = loadWsdl(LOW_PRIORITY_WSDL);
        when(wsdlRetriever.retrieveWsdls(any())).thenReturn(Map.of(
            "firstHostedService", List.of(wsdl, wsdl2), "secondHostedService", List.of(wsdl, wsdl2)
        ));
        assertThrows(AssertionError.class, testUnderTest::testRequirementR0034);
    }

    /**
     * Tests whether the test fails if hosted services share the same service id.
     */
    @Test
    public void testRequirementR0034BadSameServiceId() throws Exception {
        final String sharedServiceId = "thisIdIsShared";
        setupHostingService(Map.of(
            "firstHostedService",
            setupHostedService(sharedServiceId,
                WsdlConstants.PORT_TYPE_DESCRIPTION_EVENT_QNAME,
                WsdlConstants.PORT_TYPE_STATE_EVENT_QNAME,
                WsdlConstants.PORT_TYPE_CONTEXT_QNAME),
            "secondHostedService",
            setupHostedService(sharedServiceId,
                WsdlConstants.PORT_TYPE_WAVEFORM_QNAME)
        ));
        final String wsdl = loadWsdl(HIGH_PRIORITY_WSDL);
        final String wsdl2 = loadWsdl(LOW_PRIORITY_WSDL);
        when(wsdlRetriever.retrieveWsdls(any())).thenReturn(Map.of(
            "firstHostedService", List.of(wsdl, wsdl2), "secondHostedService", List.of(wsdl, wsdl2)
        ));

        assertThrows(AssertionError.class, testUnderTest::testRequirementR0034);
    }

    private HostedServiceProxy setupHostedService(final String serviceId, final QName... services) {
        // create hosted services
        final var hostedService = mock(HostedServiceProxy.class, Mockito.RETURNS_DEEP_STUBS);
        when(hostedService.getType().getServiceId()).thenReturn(serviceId);
        when(hostedService.getType().getTypes()).thenReturn(List.of(services));
        return hostedService;
    }

    private void setupHostingService(final Map<String, HostedServiceProxy> hostedServices) {
        final var mockHostingService = mock(HostingServiceProxy.class);
        when(testClient.getHostingServiceProxy()).thenReturn(mockHostingService);
        when(mockHostingService.getHostedServices()).thenReturn(hostedServices);
    }

    @SuppressFBWarnings(value = {"RCN_REDUNDANT_NULLCHECK_WOULD_HAVE_BEEN_A_NPE"},
        justification = "Bug in spotbugs, when using try for resources.")
    private String loadWsdl(final String wsdlPath) throws IOException {
        final String wsdl;
        final var loader = SdcDevice.class.getClassLoader();
        try (final var wsdlStream = loader.getResourceAsStream(wsdlPath)) {
            assertNotNull(wsdlStream);
            wsdl = new String(wsdlStream.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertNotNull(wsdl);
        assertFalse(wsdl.isBlank());
        return wsdl;
    }

    /**
     * Tests whether a device that stops sending Reports after sending a Report fails is passing the test.
     */
    @Test
    public void testRequirementR00360Good() throws Exception {
        reportsToCancel = new HashSet<>(Set.of(
            ActionConstants.ACTION_EPISODIC_CONTEXT_REPORT
        ));
        supportedReports = new HashSet<>(Set.of(
            ActionConstants.ACTION_EPISODIC_CONTEXT_REPORT,
            ActionConstants.ACTION_OPERATION_INVOKED_REPORT,
            ActionConstants.ACTION_DESCRIPTION_MODIFICATION_REPORT,
            ActionConstants.ACTION_EPISODIC_ALERT_REPORT,
            ActionConstants.ACTION_EPISODIC_COMPONENT_REPORT,
            ActionConstants.ACTION_EPISODIC_METRIC_REPORT,
            ActionConstants.ACTION_EPISODIC_OPERATIONAL_STATE_REPORT
        ));
        testDeviceForR00360(false, 0);
    }

    /**
     * Tests whether a device that cancels subscriptions other than EpisodicContextReport fails the test.
     */
    @Test
    public void testRequirementR00360BadTooManyCancelledSubscriptions1() throws Exception {
        reportsToCancel = new HashSet<>(Set.of(
            ActionConstants.ACTION_EPISODIC_CONTEXT_REPORT,
            ActionConstants.ACTION_OPERATION_INVOKED_REPORT
        ));
        supportedReports = new HashSet<>(Set.of(
            ActionConstants.ACTION_EPISODIC_CONTEXT_REPORT,
            ActionConstants.ACTION_OPERATION_INVOKED_REPORT,
            ActionConstants.ACTION_DESCRIPTION_MODIFICATION_REPORT,
            ActionConstants.ACTION_EPISODIC_ALERT_REPORT,
            ActionConstants.ACTION_EPISODIC_COMPONENT_REPORT,
            ActionConstants.ACTION_EPISODIC_METRIC_REPORT,
            ActionConstants.ACTION_EPISODIC_OPERATIONAL_STATE_REPORT
        ));
        testDeviceForR00360(true, 0);
    }

    /**
     * Tests whether a device that cancels subscriptions other than EpisodicContextReport fails the test.
     */
    @Test
    public void testRequirementR00360BadTooManyCancelledSubscriptions2() throws Exception {
        reportsToCancel = new HashSet<>(Set.of(
            ActionConstants.ACTION_EPISODIC_CONTEXT_REPORT,
            ActionConstants.ACTION_DESCRIPTION_MODIFICATION_REPORT
        ));
        supportedReports = new HashSet<>(Set.of(
            ActionConstants.ACTION_EPISODIC_CONTEXT_REPORT,
            ActionConstants.ACTION_OPERATION_INVOKED_REPORT,
            ActionConstants.ACTION_DESCRIPTION_MODIFICATION_REPORT,
            ActionConstants.ACTION_EPISODIC_ALERT_REPORT,
            ActionConstants.ACTION_EPISODIC_COMPONENT_REPORT,
            ActionConstants.ACTION_EPISODIC_METRIC_REPORT,
            ActionConstants.ACTION_EPISODIC_OPERATIONAL_STATE_REPORT
        ));
        testDeviceForR00360(true, 0);
    }

    /**
     * Tests whether a device that cancels subscriptions other than EpisodicContextReport fails the test.
     */
    @Test
    public void testRequirementR00360BadTooManyCancelledSubscriptions3() throws Exception {
        reportsToCancel = new HashSet<>(Set.of(
            ActionConstants.ACTION_EPISODIC_CONTEXT_REPORT,
            ActionConstants.ACTION_EPISODIC_ALERT_REPORT
        ));
        supportedReports = new HashSet<>(Set.of(
            ActionConstants.ACTION_EPISODIC_CONTEXT_REPORT,
            ActionConstants.ACTION_OPERATION_INVOKED_REPORT,
            ActionConstants.ACTION_DESCRIPTION_MODIFICATION_REPORT,
            ActionConstants.ACTION_EPISODIC_ALERT_REPORT,
            ActionConstants.ACTION_EPISODIC_COMPONENT_REPORT,
            ActionConstants.ACTION_EPISODIC_METRIC_REPORT,
            ActionConstants.ACTION_EPISODIC_OPERATIONAL_STATE_REPORT
        ));
        testDeviceForR00360(true, 0);
    }

    /**
     * Tests whether a device that cancels subscriptions other than EpisodicContextReport fails the test.
     */
    @Test
    public void testRequirementR00360BadTooManyCancelledSubscriptions4() throws Exception {
        reportsToCancel = new HashSet<>(Set.of(
            ActionConstants.ACTION_EPISODIC_CONTEXT_REPORT,
            ActionConstants.ACTION_EPISODIC_COMPONENT_REPORT
        ));
        supportedReports = new HashSet<>(Set.of(
            ActionConstants.ACTION_EPISODIC_CONTEXT_REPORT,
            ActionConstants.ACTION_OPERATION_INVOKED_REPORT,
            ActionConstants.ACTION_DESCRIPTION_MODIFICATION_REPORT,
            ActionConstants.ACTION_EPISODIC_ALERT_REPORT,
            ActionConstants.ACTION_EPISODIC_COMPONENT_REPORT,
            ActionConstants.ACTION_EPISODIC_METRIC_REPORT,
            ActionConstants.ACTION_EPISODIC_OPERATIONAL_STATE_REPORT
        ));
        testDeviceForR00360(true, 0);
    }

    /**
     * Tests whether a device that cancels subscriptions other than EpisodicContextReport fails the test.
     */
    @Test
    public void testRequirementR00360BadTooManyCancelledSubscriptions5() throws Exception {
        reportsToCancel = new HashSet<>(Set.of(
            ActionConstants.ACTION_EPISODIC_CONTEXT_REPORT,
            ActionConstants.ACTION_EPISODIC_METRIC_REPORT
        ));
        supportedReports = new HashSet<>(Set.of(
            ActionConstants.ACTION_EPISODIC_CONTEXT_REPORT,
            ActionConstants.ACTION_OPERATION_INVOKED_REPORT,
            ActionConstants.ACTION_DESCRIPTION_MODIFICATION_REPORT,
            ActionConstants.ACTION_EPISODIC_ALERT_REPORT,
            ActionConstants.ACTION_EPISODIC_COMPONENT_REPORT,
            ActionConstants.ACTION_EPISODIC_METRIC_REPORT,
            ActionConstants.ACTION_EPISODIC_OPERATIONAL_STATE_REPORT
        ));
        testDeviceForR00360(true, 0);
    }

    /**
     * Tests whether a device that cancels subscriptions other than EpisodicContextReport fails the test.
     */
    @Test
    public void testRequirementR00360BadTooManyCancelledSubscriptions6() throws Exception {
        reportsToCancel = new HashSet<>(Set.of(
            ActionConstants.ACTION_EPISODIC_CONTEXT_REPORT,
            ActionConstants.ACTION_EPISODIC_OPERATIONAL_STATE_REPORT
        ));
        supportedReports = new HashSet<>(Set.of(
            ActionConstants.ACTION_EPISODIC_CONTEXT_REPORT,
            ActionConstants.ACTION_OPERATION_INVOKED_REPORT,
            ActionConstants.ACTION_DESCRIPTION_MODIFICATION_REPORT,
            ActionConstants.ACTION_EPISODIC_ALERT_REPORT,
            ActionConstants.ACTION_EPISODIC_COMPONENT_REPORT,
            ActionConstants.ACTION_EPISODIC_METRIC_REPORT,
            ActionConstants.ACTION_EPISODIC_OPERATIONAL_STATE_REPORT
        ));
        testDeviceForR00360(true, 0);
    }


    /**
     * Tests whether a device that does not provide a LocationContextState can also pass the test.
     */
    @Test
    public void testRequirementR0036NoLocationContext() throws Exception {
        // given
        reportsToCancel = new HashSet<>(Set.of(
            ActionConstants.ACTION_EPISODIC_CONTEXT_REPORT
        ));
        supportedReports = new HashSet<>(Set.of(
            ActionConstants.ACTION_EPISODIC_CONTEXT_REPORT,
            ActionConstants.ACTION_OPERATION_INVOKED_REPORT,
            ActionConstants.ACTION_DESCRIPTION_MODIFICATION_REPORT,
            ActionConstants.ACTION_EPISODIC_ALERT_REPORT,
            ActionConstants.ACTION_EPISODIC_COMPONENT_REPORT,
            ActionConstants.ACTION_EPISODIC_METRIC_REPORT,
            ActionConstants.ACTION_EPISODIC_OPERATIONAL_STATE_REPORT
        ));
        setupTestScenarioForR0036(false, 0);
        cancelledReports = new HashSet<>();

        final Optional<HostedServiceProxy> contextService = MessageGeneratingUtil.getContextService(testClient);
        final RequestResponseClient requestResponseClient = contextService.orElseThrow().getRequestResponseClient();

        final CommunicationLog communicationLog = mock(CommunicationLog.class);
        when(communicationLogFactory.createCommunicationLog()).thenReturn(communicationLog);

        EventSink eventSink = mock(EventSink.class);
        when(eventSinkFactory.createWsEventingEventSink(
                requestResponseClient,
                testUnderTest.getLocalBaseURI(),
                communicationLog))
            .thenReturn(eventSink);

        @SuppressWarnings("unchecked")
        ListenableFuture<SubscribeResult> subscribeResultFuture = mock(ListenableFuture.class);
        SubscribeResult subscribeResult = new SubscribeResult("subscriptionId", Duration.ofSeconds(10));
        when(subscribeResultFuture.get()).thenReturn(subscribeResult);
        final NotificationSink[] handlers = {null};
        final int INDEX_NOTIFY_TO_EPISODIC_CONTEXT_REPORT_HANDLER = 0;
        final int INDEX_END_TO_EPISODIC_CONTEXT_REPORT_HANDLER = 1;
        when(eventSink
            .subscribe(any(), eq(DirectSubscriptionHandlingTest.DURATION), any()))
            .thenAnswer((args) -> {
                final List<String> actions = args.getArgument(0);
                final NotificationSink sink = args.getArgument(2);

                System.out.println("DEBUG: " + actions.get(0) + " was subscribed to.");

                if (ActionConstants.ACTION_EPISODIC_CONTEXT_REPORT.equals(actions.get(0))) {
                    handlers[0] = args.getArgument(2);
                }

                return subscribeResultFuture;
            });

        final boolean[] hadFailure = {false};
        // When the trigger is triggered, the handler must be called.
        // when the handler fails with an Exception, then the subscriptions must be cancelled.
        // EpisodicContextReport
        final CommunicationContext communicationContext =
            mock(CommunicationContext.class);
        when(manipulations.setLocationDetail(any())).thenAnswer(args -> {
            if (!hadFailure[0]
                || !this.reportsToCancel.contains(ActionConstants.ACTION_EPISODIC_CONTEXT_REPORT)) {
                try {
                    final SoapMessage soapMessage = mock(SoapMessage.class);
                    assertNotNull(handlers[INDEX_NOTIFY_TO_EPISODIC_CONTEXT_REPORT_HANDLER]);
                    handlers[INDEX_NOTIFY_TO_EPISODIC_CONTEXT_REPORT_HANDLER]
                        .receiveNotification(soapMessage, communicationContext);
                } catch (RuntimeException re) {
                    hadFailure[0] = true;
                    // cancel all subscriptions.
                    cancelledReports = reportsToCancel;
                }
            }
            return ResponseTypes.Result.RESULT_SUCCESS;
        });

        testUnderTest.testRequirementR00360();
    }


    /**
     * Tests whether a device that continues sending OperationInvokedReports after sending a Report fails is failing
     * the test.
     */
    @Test
    public void testRequirementR0036DoNotCancelOperationInvokedReportsAfterFailure() throws Exception {
        reportsToCancel = new HashSet<>(Set.of(
            ActionConstants.ACTION_EPISODIC_CONTEXT_REPORT,
            ActionConstants.ACTION_DESCRIPTION_MODIFICATION_REPORT,
            ActionConstants.ACTION_EPISODIC_ALERT_REPORT,
            ActionConstants.ACTION_EPISODIC_COMPONENT_REPORT,
            ActionConstants.ACTION_EPISODIC_METRIC_REPORT,
            ActionConstants.ACTION_EPISODIC_OPERATIONAL_STATE_REPORT
        ));
        supportedReports = new HashSet<>(Set.of(
            ActionConstants.ACTION_EPISODIC_CONTEXT_REPORT,
            ActionConstants.ACTION_OPERATION_INVOKED_REPORT,
            ActionConstants.ACTION_DESCRIPTION_MODIFICATION_REPORT,
            ActionConstants.ACTION_EPISODIC_ALERT_REPORT,
            ActionConstants.ACTION_EPISODIC_COMPONENT_REPORT,
            ActionConstants.ACTION_EPISODIC_METRIC_REPORT,
            ActionConstants.ACTION_EPISODIC_OPERATIONAL_STATE_REPORT
        ));
        testDeviceForR00360(true, 0);
    }

    /**
     * Tests whether a device that continues sending EpisodicAlertReports after sending a Report fails is failing
     * the test.
     */
    @Test
    public void testRequirementR0036DoNotCancelEpisodicAlertReportsAfterFailure() throws Exception {
        reportsToCancel = new HashSet<>(Set.of(
            ActionConstants.ACTION_EPISODIC_CONTEXT_REPORT,
            ActionConstants.ACTION_OPERATION_INVOKED_REPORT,
            ActionConstants.ACTION_DESCRIPTION_MODIFICATION_REPORT,
            ActionConstants.ACTION_EPISODIC_COMPONENT_REPORT,
            ActionConstants.ACTION_EPISODIC_METRIC_REPORT,
            ActionConstants.ACTION_EPISODIC_OPERATIONAL_STATE_REPORT
        ));
        supportedReports = new HashSet<>(Set.of(
            ActionConstants.ACTION_EPISODIC_CONTEXT_REPORT,
            ActionConstants.ACTION_OPERATION_INVOKED_REPORT,
            ActionConstants.ACTION_DESCRIPTION_MODIFICATION_REPORT,
            ActionConstants.ACTION_EPISODIC_ALERT_REPORT,
            ActionConstants.ACTION_EPISODIC_COMPONENT_REPORT,
            ActionConstants.ACTION_EPISODIC_METRIC_REPORT,
            ActionConstants.ACTION_EPISODIC_OPERATIONAL_STATE_REPORT
        ));
        testDeviceForR00360(true, 0);
    }

    /**
     * Tests whether a device that does not cancel the EpisodicComponentReport Subscription fails the test.
     */
    @Test
    public void testRequirementR0036DoNotCancelEpisodicComponentReportAfterFailure() throws Exception {
        reportsToCancel = new HashSet<>(Set.of(
            ActionConstants.ACTION_EPISODIC_CONTEXT_REPORT,
            ActionConstants.ACTION_OPERATION_INVOKED_REPORT,
            ActionConstants.ACTION_DESCRIPTION_MODIFICATION_REPORT,
            ActionConstants.ACTION_EPISODIC_ALERT_REPORT,
            ActionConstants.ACTION_EPISODIC_METRIC_REPORT,
            ActionConstants.ACTION_EPISODIC_OPERATIONAL_STATE_REPORT
        ));
        supportedReports = new HashSet<>(Set.of(
            ActionConstants.ACTION_EPISODIC_CONTEXT_REPORT,
            ActionConstants.ACTION_OPERATION_INVOKED_REPORT,
            ActionConstants.ACTION_DESCRIPTION_MODIFICATION_REPORT,
            ActionConstants.ACTION_EPISODIC_ALERT_REPORT,
            ActionConstants.ACTION_EPISODIC_COMPONENT_REPORT,
            ActionConstants.ACTION_EPISODIC_METRIC_REPORT,
            ActionConstants.ACTION_EPISODIC_OPERATIONAL_STATE_REPORT
        ));
        testDeviceForR00360(true, 0);
    }

    /**
     * Tests whether a device that does not cancel the EpisodicMetricReport Subscription fails the test.
     */
    @Test
    public void testRequirementR0036DoNotCancelEpisodicMetricReportAfterFailure() throws Exception {
        reportsToCancel = new HashSet<>(Set.of(
            ActionConstants.ACTION_EPISODIC_CONTEXT_REPORT,
            ActionConstants.ACTION_OPERATION_INVOKED_REPORT,
            ActionConstants.ACTION_DESCRIPTION_MODIFICATION_REPORT,
            ActionConstants.ACTION_EPISODIC_ALERT_REPORT,
            ActionConstants.ACTION_EPISODIC_COMPONENT_REPORT,
            ActionConstants.ACTION_EPISODIC_OPERATIONAL_STATE_REPORT
        ));
        supportedReports = new HashSet<>(Set.of(
            ActionConstants.ACTION_EPISODIC_CONTEXT_REPORT,
            ActionConstants.ACTION_OPERATION_INVOKED_REPORT,
            ActionConstants.ACTION_DESCRIPTION_MODIFICATION_REPORT,
            ActionConstants.ACTION_EPISODIC_ALERT_REPORT,
            ActionConstants.ACTION_EPISODIC_COMPONENT_REPORT,
            ActionConstants.ACTION_EPISODIC_METRIC_REPORT,
            ActionConstants.ACTION_EPISODIC_OPERATIONAL_STATE_REPORT
        ));
        testDeviceForR00360(true, 0);
    }

    /**
     * Tests whether a device that does not cancel the EpisodicOperationalStateReport Subscription fails the test.
     */
    @Test
    public void testRequirementR0036DoNotCancelEpisodicOperationalStateReportAfterFailure() throws Exception {
        reportsToCancel = new HashSet<>(Set.of(
            ActionConstants.ACTION_EPISODIC_CONTEXT_REPORT,
            ActionConstants.ACTION_OPERATION_INVOKED_REPORT,
            ActionConstants.ACTION_DESCRIPTION_MODIFICATION_REPORT,
            ActionConstants.ACTION_EPISODIC_ALERT_REPORT,
            ActionConstants.ACTION_EPISODIC_COMPONENT_REPORT,
            ActionConstants.ACTION_EPISODIC_METRIC_REPORT
        ));
        supportedReports = new HashSet<>(Set.of(
            ActionConstants.ACTION_EPISODIC_CONTEXT_REPORT,
            ActionConstants.ACTION_OPERATION_INVOKED_REPORT,
            ActionConstants.ACTION_DESCRIPTION_MODIFICATION_REPORT,
            ActionConstants.ACTION_EPISODIC_ALERT_REPORT,
            ActionConstants.ACTION_EPISODIC_COMPONENT_REPORT,
            ActionConstants.ACTION_EPISODIC_METRIC_REPORT,
            ActionConstants.ACTION_EPISODIC_OPERATIONAL_STATE_REPORT
        ));
        testDeviceForR00360(true, 0);
    }

    /**
     * Tests whether a device that does not cancel the DescriptionModificationReport Subscription fails the test.
     */
    @Test
    public void testRequirementR0036DoNotCancelDescriptionModificationReportAfterFailure() throws Exception {
        reportsToCancel = new HashSet<>(Set.of(
            ActionConstants.ACTION_EPISODIC_CONTEXT_REPORT,
            ActionConstants.ACTION_OPERATION_INVOKED_REPORT,
            ActionConstants.ACTION_EPISODIC_ALERT_REPORT,
            ActionConstants.ACTION_EPISODIC_COMPONENT_REPORT,
            ActionConstants.ACTION_EPISODIC_METRIC_REPORT,
            ActionConstants.ACTION_EPISODIC_OPERATIONAL_STATE_REPORT
        ));
        supportedReports = new HashSet<>(Set.of(
            ActionConstants.ACTION_EPISODIC_CONTEXT_REPORT,
            ActionConstants.ACTION_OPERATION_INVOKED_REPORT,
            ActionConstants.ACTION_DESCRIPTION_MODIFICATION_REPORT,
            ActionConstants.ACTION_EPISODIC_ALERT_REPORT,
            ActionConstants.ACTION_EPISODIC_COMPONENT_REPORT,
            ActionConstants.ACTION_EPISODIC_METRIC_REPORT,
            ActionConstants.ACTION_EPISODIC_OPERATIONAL_STATE_REPORT
        ));
        testDeviceForR00360(true, 0);
    }

    // NOTE: the test does not work when the Device does not support EpisodicContextReport.

    /**
     * Tests whether a device that does not support the OperationInvokedReport can still pass the test.
     */
    @Test
    public void testRequirementR0036NoSupportForOperationInvokedReport() throws Exception {
        reportsToCancel = new HashSet<>(Set.of(
            ActionConstants.ACTION_EPISODIC_CONTEXT_REPORT
        ));
        supportedReports = new HashSet<>(Set.of(
            ActionConstants.ACTION_EPISODIC_CONTEXT_REPORT,
            ActionConstants.ACTION_DESCRIPTION_MODIFICATION_REPORT,
            ActionConstants.ACTION_EPISODIC_ALERT_REPORT,
            ActionConstants.ACTION_EPISODIC_COMPONENT_REPORT,
            ActionConstants.ACTION_EPISODIC_METRIC_REPORT,
            ActionConstants.ACTION_EPISODIC_OPERATIONAL_STATE_REPORT
        ));
        testDeviceForR00360(false, 0);
    }

    /**
     * Tests whether a device that does not support the EpisodicAlertReport can still pass the test.
     */
    @Test
    public void testRequirementR0036NoSupportForEpisodicAlertReport() throws Exception {
        reportsToCancel = new HashSet<>(Set.of(
            ActionConstants.ACTION_EPISODIC_CONTEXT_REPORT
        ));
        supportedReports = new HashSet<>(Set.of(
            ActionConstants.ACTION_EPISODIC_CONTEXT_REPORT,
            ActionConstants.ACTION_OPERATION_INVOKED_REPORT,
            ActionConstants.ACTION_DESCRIPTION_MODIFICATION_REPORT,
            ActionConstants.ACTION_EPISODIC_COMPONENT_REPORT,
            ActionConstants.ACTION_EPISODIC_METRIC_REPORT,
            ActionConstants.ACTION_EPISODIC_OPERATIONAL_STATE_REPORT
        ));
        testDeviceForR00360(false, 0);
    }

    /**
     * Tests whether a device that does not support the DescriptionModificationReport can still pass the test.
     */
    @Test
    public void testRequirementR0036NoSupportForDescriptionModificationReport() throws Exception {
        reportsToCancel = new HashSet<>(Set.of(
            ActionConstants.ACTION_EPISODIC_CONTEXT_REPORT
        ));
        supportedReports = new HashSet<>(Set.of(
            ActionConstants.ACTION_EPISODIC_CONTEXT_REPORT,
            ActionConstants.ACTION_OPERATION_INVOKED_REPORT,
            ActionConstants.ACTION_EPISODIC_ALERT_REPORT,
            ActionConstants.ACTION_EPISODIC_COMPONENT_REPORT,
            ActionConstants.ACTION_EPISODIC_METRIC_REPORT,
            ActionConstants.ACTION_EPISODIC_OPERATIONAL_STATE_REPORT
        ));
        testDeviceForR00360(false, 0);
    }

    /**
     * Tests whether a device that does not support the EpisodicComponentReport can still pass the test.
     */
    @Test
    public void testRequirementR0036NoSupportForEpisodicComponentReport() throws Exception {
        reportsToCancel = new HashSet<>(Set.of(
            ActionConstants.ACTION_EPISODIC_CONTEXT_REPORT
        ));
        supportedReports = new HashSet<>(Set.of(
            ActionConstants.ACTION_EPISODIC_CONTEXT_REPORT,
            ActionConstants.ACTION_OPERATION_INVOKED_REPORT,
            ActionConstants.ACTION_DESCRIPTION_MODIFICATION_REPORT,
            ActionConstants.ACTION_EPISODIC_ALERT_REPORT,
            ActionConstants.ACTION_EPISODIC_METRIC_REPORT,
            ActionConstants.ACTION_EPISODIC_OPERATIONAL_STATE_REPORT
        ));
        testDeviceForR00360(false, 0);
    }

    /**
     * Tests whether a device that does not support the EpisodicMetricReport can still pass the test.
     */
    @Test
    public void testRequirementR0036NoSupportForEpisodicMetricReport() throws Exception {
        reportsToCancel = new HashSet<>(Set.of(
            ActionConstants.ACTION_EPISODIC_CONTEXT_REPORT
        ));
        supportedReports = new HashSet<>(Set.of(
            ActionConstants.ACTION_EPISODIC_CONTEXT_REPORT,
            ActionConstants.ACTION_OPERATION_INVOKED_REPORT,
            ActionConstants.ACTION_DESCRIPTION_MODIFICATION_REPORT,
            ActionConstants.ACTION_EPISODIC_ALERT_REPORT,
            ActionConstants.ACTION_EPISODIC_COMPONENT_REPORT,
            ActionConstants.ACTION_EPISODIC_OPERATIONAL_STATE_REPORT
        ));
        testDeviceForR00360(false, 0);
    }

    /**
     * Tests whether a device that does not support the EpisodicOperationalStateReport can still pass the test.
     */
    @Test
    public void testRequirementR0036NoSupportForEpisodicOperationalStateReport() throws Exception {
        reportsToCancel = new HashSet<>(Set.of(
            ActionConstants.ACTION_EPISODIC_CONTEXT_REPORT
        ));
        supportedReports = new HashSet<>(Set.of(
            ActionConstants.ACTION_EPISODIC_CONTEXT_REPORT,
            ActionConstants.ACTION_OPERATION_INVOKED_REPORT,
            ActionConstants.ACTION_DESCRIPTION_MODIFICATION_REPORT,
            ActionConstants.ACTION_EPISODIC_ALERT_REPORT,
            ActionConstants.ACTION_EPISODIC_COMPONENT_REPORT,
            ActionConstants.ACTION_EPISODIC_METRIC_REPORT
        ));
        testDeviceForR00360(false, 0);
    }

    /**
     * Tests whether a device that cancels the subscriptions after a small delay passes the test.
     */
    @Test
    public void testRequirementR0036WithDelayedCancellations() throws Exception {
        reportsToCancel = new HashSet<>(Set.of(
            ActionConstants.ACTION_EPISODIC_CONTEXT_REPORT
        ));
        supportedReports = new HashSet<>(Set.of(
            ActionConstants.ACTION_EPISODIC_CONTEXT_REPORT,
            ActionConstants.ACTION_OPERATION_INVOKED_REPORT,
            ActionConstants.ACTION_DESCRIPTION_MODIFICATION_REPORT,
            ActionConstants.ACTION_EPISODIC_ALERT_REPORT,
            ActionConstants.ACTION_EPISODIC_COMPONENT_REPORT,
            ActionConstants.ACTION_EPISODIC_METRIC_REPORT,
            ActionConstants.ACTION_EPISODIC_OPERATIONAL_STATE_REPORT
        ));
        testDeviceForR00360(false, INSIGNIFICANT_DELAY_IN_SECONDS);
    }

    /**
     * Tests whether a device that cancels the subscriptions after a large delay fails the test.
     */
    @Test
    public void testRequirementR0036WithStronglyDelayedCancellations() throws Exception {
        reportsToCancel = new HashSet<>(Set.of(
            ActionConstants.ACTION_EPISODIC_CONTEXT_REPORT,
            ActionConstants.ACTION_OPERATION_INVOKED_REPORT,
            ActionConstants.ACTION_DESCRIPTION_MODIFICATION_REPORT,
            ActionConstants.ACTION_EPISODIC_ALERT_REPORT,
            ActionConstants.ACTION_EPISODIC_COMPONENT_REPORT,
            ActionConstants.ACTION_EPISODIC_METRIC_REPORT,
            ActionConstants.ACTION_EPISODIC_OPERATIONAL_STATE_REPORT
        ));
        supportedReports = new HashSet<>(Set.of(
            ActionConstants.ACTION_EPISODIC_CONTEXT_REPORT,
            ActionConstants.ACTION_OPERATION_INVOKED_REPORT,
            ActionConstants.ACTION_DESCRIPTION_MODIFICATION_REPORT,
            ActionConstants.ACTION_EPISODIC_ALERT_REPORT,
            ActionConstants.ACTION_EPISODIC_COMPONENT_REPORT,
            ActionConstants.ACTION_EPISODIC_METRIC_REPORT,
            ActionConstants.ACTION_EPISODIC_OPERATIONAL_STATE_REPORT
        ));
        testDeviceForR00360(true, SIGNIFICANT_DELAY_IN_SECONDS);
    }

    /**
     * Does all the work for the Tests for Requirement R0036.
     * @param expectFailure - should the test expect Failure?
     * @param delayBeforeCancellingInSeconds - time (in seconds) to wait before cancelling the subscriptions.
     */
    private void testDeviceForR00360(
        final boolean expectFailure,
        final int delayBeforeCancellingInSeconds) throws Exception {
        // given
        setupTestScenarioForR0036(true, delayBeforeCancellingInSeconds);
        this.cancelledReports = new HashSet<>();

        // when & then
        if (expectFailure) {
            assertThrows(AssertionError.class, testUnderTest::testRequirementR00360);
        } else {
            testUnderTest.testRequirementR00360();
        }
    }

    private <A,B> A keyForValue(HashMap<A, B> hashMap, B value) {
        for (Map.Entry<A,B> entry : hashMap.entrySet()) {
            if (entry.getValue().equals(value)) {
                return entry.getKey();
            }
        }
        return null;
    }

    @SuppressFBWarnings(
        value = {"RCN_REDUNDANT_NULLCHECK_WOULD_HAVE_BEEN_A_NPE"},
        justification = "False positive, caused by a disagreement between Spotbugs and JLS."
            + "See https://github.com/spotbugs/spotbugs/issues/1338"
    )
    private void setupTestScenarioForR0036(final boolean hasLocationContextState,
                                           int delayBeforeCancellingInSeconds) throws Exception {
        final Map<String, HostedServiceProxy> hostedServices = new HashMap<>();

        final RequestResponseClient requestResponseClient = mock(RequestResponseClient.class);

        final SubscribeResponse subscriptionResponse = new SubscribeResponse();
        subscriptionResponse.setExpires(Duration.ofHours(1));
        final SoapMessage subscriptionResponseMessage = soapUtil.createMessage(
            ActionConstants.getResponseAction(ActionConstants.ACTION_DESCRIPTION_MODIFICATION_REPORT),
            subscriptionResponse
        );
        final SoapMessage subscriptionFailureMessage = soapFaultFactory.createReceiverFault("not supported");

        setupRequestResponseClient(requestResponseClient, subscriptionResponseMessage, subscriptionFailureMessage);

        hostedServices.put("ContextService", setupServiceMock(WsdlConstants.PORT_TYPE_CONTEXT_QNAME,
            requestResponseClient));
        hostedServices.put("DescriptionEventService",
            setupServiceMock(WsdlConstants.PORT_TYPE_DESCRIPTION_EVENT_QNAME, requestResponseClient));
        hostedServices.put("StateEventService", setupServiceMock(WsdlConstants.PORT_TYPE_STATE_EVENT_QNAME,
            requestResponseClient));

        final HostedServiceProxy getService = setupServiceMock(WsdlConstants.PORT_TYPE_GET_QNAME,
            requestResponseClient);
        final HostedServiceProxy setService = setupServiceMock(WsdlConstants.PORT_TYPE_SET_QNAME,
            requestResponseClient);
        hostedServices.put("GetService", getService);
        hostedServices.put("SetService", setService);


        when(hostingServiceProxy.getHostedServices()).thenReturn(hostedServices);
        try (MockedStatic<MessageGeneratingUtil> staticMessageGeneratingUtils =
                 Mockito.mockStatic(MessageGeneratingUtil.class)) {
            staticMessageGeneratingUtils.when(() -> MessageGeneratingUtil.getGetService(testClient))
                .thenReturn(Optional.of(getService));
            staticMessageGeneratingUtils.when(() -> MessageGeneratingUtil.getSetService(testClient))
                .thenReturn(Optional.of(setService));
        }

        final GetContextStatesResponse getContextStatesResponse = messageModelFactory.createGetContextStatesResponse();
        if (hasLocationContextState) {
            final LocationContextState locationContextState = new LocationContextState();
            locationContextState.setHandle("locContextHandle");
            locationContextState.setDescriptorHandle(LOC_CONTEXT_DESCRIPTOR_HANDLE);
            final LocationDetail locationDetail = new LocationDetail();
            locationDetail.setRoom("124");
            locationContextState.setLocationDetail(locationDetail);
            getContextStatesResponse.setContextState(List.of(locationContextState));
        }
        final SoapMessage getContextStatesResponseMessage = soapUtil.createMessage(
            ActionConstants.getResponseAction(ActionConstants.ACTION_GET_CONTEXT_STATES),
            getContextStatesResponse
        );
        when(messageGeneratingUtil.getContextStates()).thenReturn(getContextStatesResponseMessage);
        setupGetMdibResponse();

        final EventSink eventSink = mock(EventSink.class);
        when(eventSinkFactory.createWsEventingEventSink(eq(requestResponseClient),
                anyString(), any())).thenReturn(eventSink);

        AtomicInteger lastSubscriptionId = new AtomicInteger();
        final HashMap<String, String> actionsToSubscriptionIds = new HashMap<>();
        final HashMap<String, NotificationSink> subscriptionIdsToNotificationSink = new HashMap<>();
        final HashMap<String, List<Interceptor>> subscriptionIdsToInterceptors = new HashMap<>();
        final HashMap<NotificationSink, String> notificationSinkToSubscriptionId = new HashMap<>();

        when(eventSink.subscribe(anyList(), any(), any())).thenAnswer(invocationOnMock -> {
            final List<String> actions = invocationOnMock.getArgument(0);
            final NotificationSink notificationSink = invocationOnMock.getArgument(2);

            if (this.supportedReports.containsAll(actions)) {
                final String subscriptionId = lastSubscriptionId.toString();
                for (String action : actions) {
                    actionsToSubscriptionIds.put(action, subscriptionId);
                    subscriptionIdsToNotificationSink.put(subscriptionId, notificationSink);
                    notificationSinkToSubscriptionId.put(notificationSink, subscriptionId);
                }
                lastSubscriptionId.addAndGet(1);
                return createListenableFuture(new SubscribeResult(subscriptionId, Duration.ofSeconds(60)));
            } else {
                return createListenableFuture(null);
            }
        });

        when(notificationSinkFactory.createNotificationSink(any())).thenAnswer(invocationOnMock -> {
            final NotificationSink notificationSinkMock = mock(NotificationSink.class);
            doAnswer(invocationOnMock2 -> {
                final Interceptor interceptor = invocationOnMock2.getArgument(0);
                final String subscriptionId = notificationSinkToSubscriptionId.get(notificationSinkMock);
                addToHashMap(subscriptionIdsToInterceptors, subscriptionId, interceptor);
                return null;
            }).when(notificationSinkMock).register(any());
            return notificationSinkMock;
        });

        // When the trigger is triggered, the handler must be called. It is called in another Thread,
        // just like it would be the case when HTTP Request is handled.
        // When the handler fails with an Exception, then the subscriptions must be cancelled.
        // EpisodicContextReport
        when(manipulations.setLocationDetail(any())).thenAnswer(args -> {
            final List<Interceptor> interceptors = subscriptionIdsToInterceptors.get(
                actionsToSubscriptionIds.get(ActionConstants.ACTION_EPISODIC_CONTEXT_REPORT));

            final CallHandlerThread thread =
                new CallHandlerThread(delayBeforeCancellingInSeconds);
            for (Interceptor interceptor : interceptors) {
                thread.addInterceptor(interceptor);
            }
            thread.start();
            return ResponseTypes.Result.RESULT_SUCCESS;
        });

        when(eventSink.getStatus(anyString())).thenAnswer(call -> {
            final String subscriptionId = call.getArgument(0);
            final String action = keyForValue(actionsToSubscriptionIds, subscriptionId);
            if (this.cancelledReports.contains(action)) {
                return createListenableFuture(null);
            } else {
                return createListenableFuture(Duration.ofSeconds(DEFAULT_DURATION_IN_SECONDS));
            }
        });

    }

    private <A,B> void addToHashMap(Map<A, List<B>> hashMap, A key, B value) {
        if (hashMap.containsKey(key)) {
            hashMap.get(key).add(value);
        } else {
            final LinkedList<B> list = new LinkedList<>();
            list.add(value);
            hashMap.put(key, list);
        }
    }

    private void setupGetMdibResponse() throws Exception {
        final String responseAction = ActionConstants.getResponseAction(ActionConstants.ACTION_GET_MDIB);
        final GetMdibResponse mdibResponse = messageModelFactory.createGetMdibResponse();
        final Mdib mdib = new Mdib();
        final MdDescription mdDescription = new MdDescription();
        final MdsDescriptor md1 = new MdsDescriptor();
        final SystemContextDescriptor systemContext = new SystemContextDescriptor();
        final LocationContextDescriptor locationContext = new LocationContextDescriptor();
        locationContext.setHandle(LOC_CONTEXT_DESCRIPTOR_HANDLE);
        systemContext.setLocationContext(locationContext);
        md1.setSystemContext(systemContext);
        mdDescription.setMds(List.of(md1));
        mdib.setMdDescription(mdDescription);
        mdibResponse.setMdib(mdib);
        final SoapMessage mdibResponseMessage = soapUtil.createMessage(responseAction, mdibResponse);
        when(messageGeneratingUtil.getMdib()).thenReturn(mdibResponseMessage);
    }

    private void setupRequestResponseClient(
        final RequestResponseClient requestResponseClient,
        final SoapMessage subscriptionResponseMessage,
        final SoapMessage subscriptionFailureMessage)
        throws Exception {
        when(requestResponseClient.sendRequestResponse(any())).thenAnswer(call -> {
            final SoapMessage subscribeMessage = call.getArgument(0);
            assertEquals("http://schemas.xmlsoap.org/ws/2004/08/eventing/Subscribe",
                subscribeMessage.getWsAddressingHeader().getAction().orElseThrow().getValue());

            final Subscribe subscribe =
                (Subscribe) subscribeMessage.getOriginalEnvelope().getBody().getAny().get(0);
            assertEquals("http://docs.oasis-open.org/ws-dd/ns/dpws/2009/01/Action",
                subscribe.getFilter().getDialect());
            if (supportedReports.contains((String) subscribe.getFilter().getContent().get(0))) {
                return subscriptionResponseMessage;
            } else {
                return subscriptionFailureMessage;
            }
        });
    }


    private <T> ListenableFuture<T> createListenableFuture(@Nullable final T value) {
        return new ListenableFuture<>() {
            @Override public void addListener(final Runnable runnable, final Executor executor) { }

            @Override public boolean cancel(final boolean mayInterruptIfRunning) {
                return false; }

            @Override public boolean isCancelled() {
                return false; }

            @Override public boolean isDone() {
                return false; }

            @Override public T get() {
                return value;
            }

            @Override public T get(final long timeout, final TimeUnit unit) {
                return get();
            }
        };
    }

    private HostedServiceProxy setupServiceMock(final QName portTypeContextQname,
                                                final RequestResponseClient requestResponseClient) {
        final HostedServiceProxy service = mock(HostedServiceProxy.class);
        final HostedServiceType serviceType = new HostedServiceType();
        serviceType.setTypes(List.of(portTypeContextQname));
        when(service.getType()).thenReturn(serviceType);
        when(service.getRequestResponseClient()).thenReturn(requestResponseClient);
        return service;
    }

    class CallHandlerThread extends Thread {

        public static final long NANOS_IN_A_SECOND = 1000000000;
        private final List<Interceptor> interceptors;

        private final int delay;

        CallHandlerThread(final int delayInSeconds) {
            this.delay = delayInSeconds;
            this.interceptors = new ArrayList<>();
        }

        public void addInterceptor(Interceptor interceptor) {
            this.interceptors.add(interceptor);
        }

        public void run() {
            try {
                final NotificationObject notificationObject = mock(NotificationObject.class, RETURNS_DEEP_STUBS);
                EpisodicContextReport episodicContextReport = new EpisodicContextReport();
                when(notificationObject.getNotification().getOriginalEnvelope().getBody().getAny().get(0)).thenReturn(episodicContextReport);

                for (Interceptor interceptor : interceptors) {
                    try {
                        Method method = interceptor.getClass().getMethod("onNotification", NotificationObject.class);
                        method.invoke(interceptor, notificationObject);
                    } catch (NoSuchMethodException | IllegalAccessException e) {
                        fail("failed to invoke Interceptor", e);
                    }
                }
            } catch (InvocationTargetException ite) {
                if (ite.getTargetException() instanceof SoapFaultException) {
                    // wait for the prescribed delay
                    final long start = System.nanoTime();
                    final long endOfWaiting = start + this.delay * NANOS_IN_A_SECOND;
                    while (System.nanoTime() < endOfWaiting) {
                        try {
                            nanoSleep(endOfWaiting - System.nanoTime());
                        } catch (InterruptedException e) {
                            // do nothing
                        }
                    }
                    // cancel all subscriptions
                    cancelledReports = reportsToCancel;
                } else {
                    fail("encountered unexpected exception in interceptor", ite.getTargetException());
                }
            }
        }

        private void nanoSleep(final long duration) throws InterruptedException {
            final long millis = duration / NANOS_IN_A_MILLISECOND;
            sleep(millis, (int) (duration % NANOS_IN_A_MILLISECOND));
        }
 
    }

}
