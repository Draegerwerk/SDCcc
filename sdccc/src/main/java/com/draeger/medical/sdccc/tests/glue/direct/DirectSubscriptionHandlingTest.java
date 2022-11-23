/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.tests.glue.direct;

import com.draeger.medical.sdccc.configuration.EnabledTestConfig;
import com.draeger.medical.sdccc.configuration.TestSuiteConfig;
import com.draeger.medical.sdccc.manipulation.Manipulations;
import com.draeger.medical.sdccc.sdcri.testclient.TestClient;
import com.draeger.medical.sdccc.tests.InjectorTestBase;
import com.draeger.medical.sdccc.tests.annotations.TestDescription;
import com.draeger.medical.sdccc.tests.annotations.TestIdentifier;
import com.draeger.medical.sdccc.tests.util.HostedServiceVerifier;
import com.draeger.medical.sdccc.util.MessageGeneratingUtil;
import com.draeger.medical.sdccc.util.MessagingException;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Names;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.somda.sdc.biceps.model.message.DescriptionModificationReport;
import org.somda.sdc.biceps.model.message.EpisodicAlertReport;
import org.somda.sdc.biceps.model.message.EpisodicComponentReport;
import org.somda.sdc.biceps.model.message.EpisodicContextReport;
import org.somda.sdc.biceps.model.message.EpisodicMetricReport;
import org.somda.sdc.biceps.model.message.EpisodicOperationalStateReport;
import org.somda.sdc.biceps.model.message.GetContextStatesResponse;
import org.somda.sdc.biceps.model.message.GetMdibResponse;
import org.somda.sdc.biceps.model.message.OperationInvokedReport;
import org.somda.sdc.biceps.model.participant.AbstractContextState;
import org.somda.sdc.biceps.model.participant.LocationContextDescriptor;
import org.somda.sdc.biceps.model.participant.LocationContextState;
import org.somda.sdc.biceps.model.participant.LocationDetail;
import org.somda.sdc.biceps.model.participant.Mdib;
import org.somda.sdc.biceps.model.participant.MdsDescriptor;
import org.somda.sdc.dpws.CommunicationLog;
import org.somda.sdc.dpws.factory.CommunicationLogFactory;
import org.somda.sdc.dpws.http.HttpServerRegistry;
import org.somda.sdc.dpws.service.HostedServiceProxy;
import org.somda.sdc.dpws.soap.NotificationSink;
import org.somda.sdc.dpws.soap.RequestResponseClient;
import org.somda.sdc.dpws.soap.SoapMessage;
import org.somda.sdc.dpws.soap.SoapUtil;
import org.somda.sdc.dpws.soap.exception.SoapFaultException;
import org.somda.sdc.dpws.soap.factory.NotificationSinkFactory;
import org.somda.sdc.dpws.soap.factory.SoapFaultFactory;
import org.somda.sdc.dpws.soap.interception.Direction;
import org.somda.sdc.dpws.soap.interception.Interceptor;
import org.somda.sdc.dpws.soap.interception.MessageInterceptor;
import org.somda.sdc.dpws.soap.interception.NotificationObject;
import org.somda.sdc.dpws.soap.wsaddressing.WsAddressingServerInterceptor;
import org.somda.sdc.dpws.soap.wsaddressing.WsAddressingUtil;
import org.somda.sdc.dpws.soap.wseventing.EventSink;
import org.somda.sdc.dpws.soap.wseventing.SubscribeResult;
import org.somda.sdc.dpws.soap.wseventing.factory.WsEventingEventSinkFactory;
import org.somda.sdc.glue.common.ActionConstants;
import org.somda.sdc.glue.common.WsdlConstants;

import javax.xml.namespace.QName;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Glue Communication model binding tests.
 */
public class DirectSubscriptionHandlingTest extends InjectorTestBase {

    public static final long NANOS_IN_A_MILLISECOND = 1000000L;
    public static final Duration DURATION = Duration.ofHours(1);
    public static final long TIMEOUT_NANOS = 5000 * NANOS_IN_A_MILLISECOND;
    public static final int POLLING_WAIT_TIME_MILLIS = 1000;

    private static final Logger LOG = LogManager.getLogger(DirectSubscriptionHandlingTest.class);

    private static final String ROOM1 = "123";
    private static final String ROOM2 = "124";
    private final InterceptorWithTheAbilityToFailReports interceptor = new InterceptorWithTheAbilityToFailReports();


    private TestClient testClient;
    private HostedServiceVerifier hostedServiceVerifier;
    private SoapUtil soapUtil;
    private MessageGeneratingUtil messageGeneratingUtil;
    private Manipulations manipulations;
    private HttpServerRegistry httpServerRegistry;
    private org.somda.sdc.dpws.soap.wseventing.model.ObjectFactory wseFactory;
    private WsAddressingUtil wsaUtil;
    private WsEventingEventSinkFactory eventSinkFactory;
    private String adapterAddress;
    private CommunicationLog commLog;
    private NotificationSinkFactory notificationSinkFactory;
    private WsAddressingServerInterceptor wsaServerInterceptor;
    private SoapFaultFactory soapFaultFactory;

    @BeforeEach
    void setUp() {
        this.testClient = getInjector().getInstance(TestClient.class);
        assertTrue(testClient.isClientRunning());
        final Injector riInjector = this.testClient.getInjector();
        hostedServiceVerifier = getInjector().getInstance(HostedServiceVerifier.class);
        this.soapUtil = riInjector.getInstance(SoapUtil.class);
        this.messageGeneratingUtil = getInjector().getInstance(MessageGeneratingUtil.class);
        this.manipulations = getInjector().getInstance(Manipulations.class);
        this.httpServerRegistry = riInjector.getInstance(HttpServerRegistry.class);
        this.wseFactory = getInjector().getInstance(org.somda.sdc.dpws.soap.wseventing.model.ObjectFactory.class);
        this.wsaUtil = getInjector().getInstance(WsAddressingUtil.class);
        this.eventSinkFactory = riInjector.getInstance(WsEventingEventSinkFactory.class);
        this.adapterAddress = getInjector().getInstance(
            Key.get(String.class, Names.named(TestSuiteConfig.NETWORK_INTERFACE_ADDRESS))
        );
        this.notificationSinkFactory = testClient.getInjector().getInstance(NotificationSinkFactory.class);
        this.wsaServerInterceptor = testClient.getInjector().getInstance(WsAddressingServerInterceptor.class);
        this.commLog = testClient.getInjector().getInstance(CommunicationLogFactory.class)
                                               .createCommunicationLog();
        this.soapFaultFactory = testClient .getInjector().getInstance(SoapFaultFactory.class);
    }

    @Test
    @DisplayName("""
        An SDC SERVICE PROVIDER SHALL implement at least those of the following BICEPS SERVICEs that it supports as one MDPWS HOSTED SERVICE:
         - Description Event Service
         - State Event Service
         - Context Service
         - Waveform Service""")
    @TestIdentifier(EnabledTestConfig.GLUE_R0034_0)
    @TestDescription("Checks whether the DUT has provided a DescriptionEventService, StateEventService, a"
        + " ContextService or a WaveformService and verifies each service is in the same hosted service and"
        + " each service endpoint provided by the DUT is conforming with SDC Glue Annex B and only implements SDC"
        + " services.")
    void testRequirementR0034() {
        final List<QName> seenTypes = List.of(
            WsdlConstants.PORT_TYPE_DESCRIPTION_EVENT_QNAME, WsdlConstants.PORT_TYPE_STATE_EVENT_QNAME,
            WsdlConstants.PORT_TYPE_CONTEXT_QNAME, WsdlConstants.PORT_TYPE_WAVEFORM_QNAME);

        final Set<String> seenHostedServices = new HashSet<>();

        final var hostedServices = testClient.getHostingServiceProxy().getHostedServices();
        final var serviceIds = hostedServices.values().stream()
            .map(hostedServiceProxy -> hostedServiceProxy.getType().getServiceId()).collect(Collectors.toList());
        if (hostedServices.values().size() != new HashSet<>(serviceIds).size()) {
            fail(String.format("Some Hosted Services share the same service id: %s, test failed.",
                serviceIds));
        }
        hostedServices.values().forEach(value ->
            value.getType().getTypes().forEach(type -> {
                if (seenTypes.contains(type)) {
                    checkServiceConformance(type);
                    seenHostedServices.add(value.getType().getServiceId());
                }
            }));

        assertEquals(1, seenHostedServices.size(), String.format(
            "The DescriptionEventService, StateEventService, ContextService and WaveformService should be in the same"
                + " HostedService but they are in different services %s, test failed.", seenHostedServices));
    }

    private void checkServiceConformance(final QName targetQName) {
        Optional<HostedServiceProxy> hostedServiceProxyOptional = Optional.empty();
        if (WsdlConstants.PORT_TYPE_DESCRIPTION_EVENT_QNAME.equals(targetQName)) {
            hostedServiceProxyOptional = MessageGeneratingUtil.getDescriptionEventService(testClient);
        } else if (WsdlConstants.PORT_TYPE_STATE_EVENT_QNAME.equals(targetQName)) {
            hostedServiceProxyOptional = MessageGeneratingUtil.getStateEventService(testClient);
        } else if (WsdlConstants.PORT_TYPE_CONTEXT_QNAME.equals(targetQName)) {
            hostedServiceProxyOptional = MessageGeneratingUtil.getContextService(testClient);
        } else if (WsdlConstants.PORT_TYPE_WAVEFORM_QNAME.equals(targetQName)) {
            hostedServiceProxyOptional = MessageGeneratingUtil.getWaveformService(testClient);
        }
        hostedServiceVerifier.verifyHostedService(hostedServiceProxyOptional);
    }

    @Test
    @TestIdentifier(EnabledTestConfig.GLUE_R0036_0)
    @DisplayName("An SDC SERVICE PROVIDER SHALL terminate a subscription if the delivery of one MESSAGE"
        + " related to that subscription failed.")
    @TestDescription("Subscribes to all Reports, checks that all Subscriptions are active, then"
        + " triggers an EpisodicContextReport and intentionally fails to receive it. Afterwards,"
        + " it checks that the Subscription to the EpisodicContextReport has been cancelled by"
        + " the provider while all other subscriptions are still active.")
    void testRequirementR00360() throws InterruptedException {

        // preparation
        final LocationContextState locationContext = getOrCreateLocationContextState();
        assert locationContext != null;

        final ReportTestData triggerableReport = new ReportTestData(
            WsdlConstants.OPERATION_EPISODIC_CONTEXT_REPORT,
            ActionConstants.ACTION_EPISODIC_CONTEXT_REPORT,
            report -> { // subscribe to Report
                subscribeToReport(getLocalBaseURI(),
                    WsdlConstants.OPERATION_EPISODIC_CONTEXT_REPORT,
                    ActionConstants.ACTION_EPISODIC_CONTEXT_REPORT,
                    WsdlConstants.SERVICE_CONTEXT,
                    MessageGeneratingUtil::getContextService,
                    report, true);
                return null;
            },
            () -> { // trigger Report
                final LocationDetail locationDetail = locationContext.getLocationDetail();
                if (ROOM1.equals(locationDetail.getRoom())) {
                    locationDetail.setRoom(ROOM2);
                } else {
                    locationDetail.setRoom(ROOM1);
                }
                manipulations.setLocationDetail(locationDetail);
            }) {
            @Override
            public boolean doesNotificationBodyBelongToThisReport(Object notificationBody) {
                return notificationBody instanceof EpisodicContextReport;
            }
        };
        final List<ReportTestData> otherReports = List.of(
            new ReportTestData(
                WsdlConstants.OPERATION_OPERATION_INVOKED_REPORT,
                ActionConstants.ACTION_OPERATION_INVOKED_REPORT,
                report -> { // subscribe to Report
                    return subscribeToReport(getLocalBaseURI(),
                        WsdlConstants.OPERATION_OPERATION_INVOKED_REPORT,
                        ActionConstants.ACTION_OPERATION_INVOKED_REPORT,
                        WsdlConstants.SERVICE_SET,
                        MessageGeneratingUtil::getSetService,
                        report, false);
                },
                () -> {
                    // does not need to be triggered in this test.
                }) {
                @Override
                public boolean doesNotificationBodyBelongToThisReport(Object notificationBody) {
                    return notificationBody instanceof OperationInvokedReport;
                }
            },
            new ReportTestData(
                WsdlConstants.OPERATION_DESCRIPTION_MODIFICATION_REPORT,
                ActionConstants.ACTION_DESCRIPTION_MODIFICATION_REPORT,
                report -> { // subscribe to Report
                    return subscribeToReport(getLocalBaseURI(),
                        WsdlConstants.OPERATION_DESCRIPTION_MODIFICATION_REPORT,
                        ActionConstants.ACTION_DESCRIPTION_MODIFICATION_REPORT,
                        WsdlConstants.SERVICE_DESCRIPTION_EVENT,
                        MessageGeneratingUtil::getDescriptionEventService,
                        report, false);
                },
                () -> {
                   // does not need to be triggered in this test.
                }
            ) {
                @Override
                public boolean doesNotificationBodyBelongToThisReport(Object notificationBody) {
                    return notificationBody instanceof DescriptionModificationReport;
                }
            },
            new ReportTestData(
                WsdlConstants.OPERATION_EPISODIC_ALERT_REPORT,
                ActionConstants.ACTION_EPISODIC_ALERT_REPORT,
                report -> { // subscribe to Report
                    return subscribeToReport(getLocalBaseURI(),
                        WsdlConstants.OPERATION_EPISODIC_ALERT_REPORT,
                        ActionConstants.ACTION_EPISODIC_ALERT_REPORT,
                        WsdlConstants.SERVICE_STATE_EVENT,
                        MessageGeneratingUtil::getStateEventService,
                        report, false);
                },
                () -> {
                    // does not need to be triggered in this test.
                }) {
                @Override
                public boolean doesNotificationBodyBelongToThisReport(Object notificationBody) {
                    return notificationBody instanceof EpisodicAlertReport;
                }
            },
            new ReportTestData(
                WsdlConstants.OPERATION_EPISODIC_COMPONENT_REPORT,
                ActionConstants.ACTION_EPISODIC_COMPONENT_REPORT,
                report -> { // subscribe to Report
                    return subscribeToReport(getLocalBaseURI(),
                        WsdlConstants.OPERATION_EPISODIC_COMPONENT_REPORT,
                        ActionConstants.ACTION_EPISODIC_COMPONENT_REPORT,
                        WsdlConstants.SERVICE_STATE_EVENT,
                        MessageGeneratingUtil::getStateEventService,
                        report, false);
                },
                () -> {
                    // does not need to be triggered in this test.
                }) {
                @Override
                public boolean doesNotificationBodyBelongToThisReport(Object notificationBody) {
                    return notificationBody instanceof EpisodicComponentReport;
                }
            },
            new ReportTestData(
                WsdlConstants.OPERATION_EPISODIC_METRIC_REPORT,
                ActionConstants.ACTION_EPISODIC_METRIC_REPORT,
                report -> { // subscribe to Report
                    return subscribeToReport(getLocalBaseURI(),
                        WsdlConstants.OPERATION_EPISODIC_METRIC_REPORT,
                        ActionConstants.ACTION_EPISODIC_METRIC_REPORT,
                        WsdlConstants.SERVICE_STATE_EVENT,
                        MessageGeneratingUtil::getStateEventService,
                        report, false);
                },
                () -> {
                    // does not need to be triggered in this test.
                }) {
                @Override
                public boolean doesNotificationBodyBelongToThisReport(Object notificationBody) {
                    return notificationBody instanceof EpisodicMetricReport;
                }
            },
            new ReportTestData(
                WsdlConstants.OPERATION_EPISODIC_OPERATIONAL_STATE_REPORT,
                ActionConstants.ACTION_EPISODIC_OPERATIONAL_STATE_REPORT,
                report -> { // subscribe to Report
                    return subscribeToReport(getLocalBaseURI(),
                        WsdlConstants.OPERATION_EPISODIC_OPERATIONAL_STATE_REPORT,
                        ActionConstants.ACTION_EPISODIC_OPERATIONAL_STATE_REPORT,
                        WsdlConstants.SERVICE_STATE_EVENT,
                        MessageGeneratingUtil::getStateEventService,
                        report, false);
                },
                () -> {
                    // does not need to be triggered in this test.
                }) {
                @Override
                public boolean doesNotificationBodyBelongToThisReport(Object notificationBody) {
                    return notificationBody instanceof EpisodicOperationalStateReport;
                }
            }
        );
        final List<ReportTestData> reports = new ArrayList<>();
        reports.add(triggerableReport);
        reports.addAll(otherReports);

        subscribeToAllReports(reports);

        checkSubscriptionsAreActive(reports);

        triggerReportAndIntentionallyFailReceivingIt(triggerableReport);

        checkThatSubscriptionsHaveBeenCancelled(List.of(triggerableReport));
        checkThatOtherSubscriptionsHaveNotBeenCancelled(otherReports);
    }

    private void checkThatOtherSubscriptionsHaveNotBeenCancelled(final List<ReportTestData> reports) {
        for (ReportTestData report : reports) {
            if (report.getSubscription() != null) {
                final Duration status = getSubscriptionStatus(report);
                assertNotNull(status, "Subscription for report " + report.getReportName()
                    + " has been unexpectedly cancelled.");
            }
        }
    }

    private void subscribeToAllReports(final List<ReportTestData> reports) {
        for (ReportTestData report : reports) {
            report.subscribe();
        }
    }

    private void checkSubscriptionsAreActive(final List<ReportTestData> reports) {
        for (ReportTestData report : reports) {
            if (report.getSubscription() != null) {
                final Duration status = getSubscriptionStatus(report);
                assertNotNull(status, "Subscription for report " + report.getReportName()
                    + " is not active after subscribing.");
            }
        }
    }

    private void triggerReportAndIntentionallyFailReceivingIt(final ReportTestData triggerableReport) {
        LOG.info("Triggering a Report and intentionally causing a failure...");
        triggerableReport.setReportReceived(false);
        triggerableReport.setFailOnReceivingReport(true);
        synchronized (triggerableReport.getSyncPoint()) {
            triggerableReport.trigger();
            final long timeout = System.nanoTime() + TIMEOUT_NANOS;
            while (!triggerableReport.getReportReceived() && System.nanoTime() < timeout) {
                try {
                    nanoWait(triggerableReport.getSyncPoint(), timeout - System.nanoTime());
                } catch (InterruptedException e) {
                    // do nothing
                }
            }
            assertTrue(triggerableReport.getReportReceived(), "expected "
                + triggerableReport.getReportName() + " was not received.");
        }
    }

    private void nanoWait(final Object syncPoint, final long duration) throws InterruptedException {
        final long millis = duration / NANOS_IN_A_MILLISECOND;
        final long nanos = duration % NANOS_IN_A_MILLISECOND;
        syncPoint.wait(millis, (int) nanos);
    }

    private void checkThatSubscriptionsHaveBeenCancelled(final List<ReportTestData> reports)
        throws InterruptedException {
        // TODO: Do not actively poll the Status, but expect the Device to cancel the Reports by calling EndTo.
        final long timeoutWaitingForCancellations = System.nanoTime() + TIMEOUT_NANOS;
        final HashSet<ReportTestData> uncancelledSubscriptions = new HashSet<>(reports);
        while (!uncancelledSubscriptions.isEmpty() && System.nanoTime() < timeoutWaitingForCancellations) {
            //noinspection BusyWait
            Thread.sleep(POLLING_WAIT_TIME_MILLIS);
            uncancelledSubscriptions.removeIf(report -> {
                final Duration status = getSubscriptionStatus(report);
                return status == null;
            });
        }
        if (!uncancelledSubscriptions.isEmpty()) {
            final List<String> subscriptions = uncancelledSubscriptions.stream()
                .map(ReportTestData::getReportName)
                .collect(Collectors.toList());
            fail("Reports " + String.join(", ", subscriptions) + " have not been cancelled by the provider"
                + " although they should have been according to glue:R0036_0.");
        }
    }

    private LocationContextState getOrCreateLocationContextState() {
        final SoapMessage mdibResponseMessage;
        LocationContextState result;
        try {
            result = getLocationContextState();
            if (result == null) {
                // when no locationContextState exists, create a default one
                mdibResponseMessage = messageGeneratingUtil.getMdib();
                final GetMdibResponse mdibResponse =
                    (GetMdibResponse) mdibResponseMessage.getOriginalEnvelope().getBody().getAny().get(0);
                final Mdib mdib = mdibResponse.getMdib();
                final List<MdsDescriptor> mds = mdib.getMdDescription().getMds();
                LocationContextDescriptor locationContextDescriptor = null;
                for (MdsDescriptor md : mds) {
                    final LocationContextDescriptor lcDesc = md.getSystemContext().getLocationContext();
                    if (lcDesc != null) {
                        locationContextDescriptor = lcDesc;
                    }
                }
                if (locationContextDescriptor == null) {
                    fail("Could not find a LocationContext in the Device's Mdib.");
                    result = null;
                } else {
                    result = new LocationContextState();
                    result.setHandle("loc");
                    result.setDescriptorHandle(locationContextDescriptor.getHandle());
                    final LocationDetail locDetail = new LocationDetail();
                    locDetail.setBuilding("building1");
                    locDetail.setFacility("hospital");
                    locDetail.setFloor("Floor7");
                    locDetail.setRoom(ROOM1);
                    locDetail.setBed("Bed1");
                    locDetail.setPoC("PoC1");
                    result.setLocationDetail(locDetail);
                }
            }
        } catch (MessagingException e) {
            fail("Could not request Mdib from Device", e);
            result = null;
        }
        return result;
    }

    private Duration getSubscriptionStatus(final ReportTestData report) {
        if (report.getEventSink() != null && report.getSubscription() != null) {
            try {
                return report.getEventSink().getStatus(report.getSubscription().getSubscriptionId()).get();
            } catch (InterruptedException | ExecutionException e) {
                LOG.warn("encountered exception while querying the subscription status of report "
                    + report.getReportName(), e);
                // TODO: be more strict. Do not tolerate Errors any more.
                // do nothing here. Returning null will suffice to indicate failure.
            }
        }
        return null;
    }

    private LocationContextState getLocationContextState() {
        final GetContextStatesResponse getContextStatesResponse =
            (GetContextStatesResponse) messageGeneratingUtil
                .getContextStates()
                .getOriginalEnvelope()
                .getBody()
                .getAny()
                .stream()
                .findFirst()
                .orElseThrow();
        final Optional<AbstractContextState> result = getContextStatesResponse
            .getContextState()
            .stream()
            .filter(LocationContextState.class::isInstance).findFirst();
        if (result.isEmpty() || !(result.orElseThrow() instanceof LocationContextState)) {
            return null;
        }
        return (LocationContextState) result
            .orElseThrow();
    }

    private SubscribeResult subscribeToReport(final String baseURI,
                                              final String reportName,
                                              final String action,
                                              final String serviceName,
                                              final GetServiceProxyClosure getServiceProxy,
                                              final ReportTestData reportTestData,
                                              final boolean addAbilityToFail) {
        final Optional<HostedServiceProxy> hostedServiceProxy = getServiceProxy.execute(testClient);
        if (hostedServiceProxy.isEmpty()) {
            fail("failed to retrieve serviceProxy for " + serviceName);
        }

        final List<String> actions =
            List.of(action);

        final RequestResponseClient requestResponseClient = hostedServiceProxy
            .orElseThrow()
            .getRequestResponseClient();

        final EventSink eventSink =
            eventSinkFactory.createWsEventingEventSink(
                    requestResponseClient,
                    baseURI,
                    testClient.getInjector().getInstance(CommunicationLogFactory.class).createCommunicationLog());

        final NotificationSink notificationSink = notificationSinkFactory.createNotificationSink(wsaServerInterceptor);
        final ListenableFuture<SubscribeResult> subscribeResult =
            eventSink.subscribe(actions, DURATION, notificationSink);
        notificationSink.register(new Interceptor() {

            @MessageInterceptor(direction = Direction.NOTIFICATION)
            public void onNotification(final NotificationObject message) {
                final Object body = message.getNotification().getOriginalEnvelope().getBody().getAny().get(0);
                if (reportTestData.doesNotificationBodyBelongToThisReport(body)) {
                    synchronized (reportTestData.getSyncPoint()) {
                        reportTestData.setReportReceived(true);
                        reportTestData.getSyncPoint().notifyAll();
                    }
                }
            }

        });
        if (addAbilityToFail) {
            interceptor.addReportToFail(reportTestData);
            notificationSink.register(interceptor);
        }

//        new NotificationSink() {
//            @Override
//            public void receiveNotification(final SoapMessage soapMessage,
//                                            final CommunicationContext communicationContext) {
//                // notification
//                synchronized (reportTestData.getSyncPoint()) {
//                    reportTestData.setReportReceived(true);
//                    reportTestData.getSyncPoint().notifyAll();
//                }
//            }
//
//            @Override
//            public void register(final Interceptor interceptor) {
//                // not important
//            }
//        }

        SubscribeResult result = null;
        try {
            result = subscribeResult.get();
            reportTestData.setSubscription(result);
            reportTestData.setEventSink(eventSink);
        } catch (InterruptedException | ExecutionException e) {
            fail("encountered exception while subscribing to " + reportName, e);
        }
        return result;
    }


/*
    @SuppressWarnings("SameParameterValue")
    private SubscribeResult subscribeToReportWithTheAbilityToFail(final String baseURI,
                                                       final String reportName,
                                                       final String action,
                                                       final String serviceName,
                                                       final GetServiceProxyClosure getServiceProxy,
                                                       final ReportTestData reportTestData) {
        final Optional<HostedServiceProxy> hostedServiceProxy = getServiceProxy.execute(testClient);
        if (hostedServiceProxy.isEmpty()) {
            fail("failed to retrieve serviceProxy for " + serviceName);
        }

        final List<String> actions =
            List.of(action);

        final RequestResponseClient requestResponseClient = hostedServiceProxy
            .orElseThrow()
            .getRequestResponseClient();

        final EventSink eventSink =
            eventSinkFactory.createWsEventingEventSink(
                requestResponseClient,
                baseURI,
                testClient.getInjector().getInstance(CommunicationLogFactory.class).createCommunicationLog());
        final ListenableFuture<SubscribeResult> subscribeResult =
            eventSink.subscribe(actions, DURATION, new NotificationSink() {
                @Override
                public void receiveNotification(final SoapMessage soapMessage,
                                                final CommunicationContext communicationContext) {
                    // notification
                    synchronized (reportTestData.getSyncPoint()) {
                        reportTestData.setReportReceived(true);
                        reportTestData.getSyncPoint().notifyAll();
                    }
                    LOG.info("receiveNotification was called for report " + reportTestData.getReportName() + ": notification was " + soapMessage);
                    if (reportTestData.getFailOnReceivingReport()) {
                        LOG.info("answering notification with an intentional failure (500).");
                        throw new RuntimeException("intentional failure for testing purposes.");
                    } else {
                        LOG.info("answering notification with success.");
                    }
                }

                @Override
                public void register(final Interceptor interceptor) {
                    // not important
                }
            });

        SubscribeResult result = null;
        try {
            result = subscribeResult.get();
            reportTestData.setSubscription(result);
            reportTestData.setEventSink(eventSink);
        } catch (InterruptedException | ExecutionException e) {
            fail("encountered exception while subscribing to " + reportName, e);
        }
        return result;
    }
*/

    /**
     * Determine SubscriptionId for a report with the given action.
     * This method exists so a test case can override it and set the subscriptionId
     * according to its needs.
     * @param action the name of the report for which the context suffix should be determined.
     * @return the context suffix.
     */
    public String determineSubscriptionIdForAction(final String action) {
        return UUID.randomUUID().toString();
    }

    protected String getLocalBaseURI() {
        return String.format("https://%s:0", this.adapterAddress);
    }

    /**
     * Determine Context Suffix. This method exists so a test case can override it and set the context suffix
     * according to its needs.
     * @param reportName the name of the report for which the context suffix should be determined.
     * @return the context suffix.
     */
    public String determineContextSuffix(final String reportName) {
        return UUID.randomUUID().toString();
    }

    private String implodeUriList(final List<String> actionUris) {
        final StringBuilder sb = new StringBuilder();
        actionUris.forEach(s -> {
            sb.append(s);
            sb.append(" ");
        });
        sb.deleteCharAt(sb.length() - 1);
        return sb.toString();
    }

    class InterceptorWithTheAbilityToFailReports implements Interceptor {

        private List<ReportTestData> reportsToFail = new ArrayList<>();

        public void addReportToFail(ReportTestData reportToFail) {
            this.reportsToFail.add(reportToFail);
        }

        @MessageInterceptor(direction = Direction.ANY)
        public void onNotification(NotificationObject message) throws SoapFaultException {
            final Object body = message.getNotification().getOriginalEnvelope().getBody().getAny().get(0);
            for (ReportTestData report : reportsToFail) {
                if (report.doesNotificationBodyBelongToThisReport(body) && report.getFailOnReceivingReport()) {
                    // fail report
                    SoapMessage fault = soapFaultFactory.createSenderFault("Intentional failure for testing purposes.");
                    throw new SoapFaultException(fault);
                }
            }
        }

    }
}
