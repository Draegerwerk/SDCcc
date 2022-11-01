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
import org.somda.sdc.biceps.model.message.GetContextStatesResponse;
import org.somda.sdc.biceps.model.message.GetMdibResponse;
import org.somda.sdc.biceps.model.participant.AbstractContextState;
import org.somda.sdc.biceps.model.participant.LocationContextDescriptor;
import org.somda.sdc.biceps.model.participant.LocationContextState;
import org.somda.sdc.biceps.model.participant.LocationDetail;
import org.somda.sdc.biceps.model.participant.Mdib;
import org.somda.sdc.biceps.model.participant.MdsDescriptor;
import org.somda.sdc.dpws.CommunicationLog;
import org.somda.sdc.dpws.http.HttpServerRegistry;
import org.somda.sdc.dpws.service.HostedServiceProxy;
import org.somda.sdc.dpws.soap.CommunicationContext;
import org.somda.sdc.dpws.soap.NotificationSink;
import org.somda.sdc.dpws.soap.RequestResponseClient;
import org.somda.sdc.dpws.soap.SoapMessage;
import org.somda.sdc.dpws.soap.SoapUtil;
import org.somda.sdc.dpws.soap.exception.MarshallingException;
import org.somda.sdc.dpws.soap.exception.SoapFaultException;
import org.somda.sdc.dpws.soap.exception.TransportException;
import org.somda.sdc.dpws.soap.interception.Interceptor;
import org.somda.sdc.dpws.soap.interception.InterceptorException;
import org.somda.sdc.dpws.soap.wsaddressing.WsAddressingUtil;
import org.somda.sdc.dpws.soap.wsaddressing.model.EndpointReferenceType;
import org.somda.sdc.dpws.soap.wseventing.EventSink;
import org.somda.sdc.dpws.soap.wseventing.SubscribeResult;
import org.somda.sdc.dpws.soap.wseventing.WsEventingConstants;
import org.somda.sdc.dpws.soap.wseventing.factory.WsEventingEventSinkFactory;
import org.somda.sdc.dpws.soap.wseventing.model.DeliveryType;
import org.somda.sdc.dpws.soap.wseventing.model.FilterType;
import org.somda.sdc.dpws.soap.wseventing.model.Subscribe;
import org.somda.sdc.glue.common.ActionConstants;
import org.somda.sdc.glue.common.WsdlConstants;

import javax.xml.namespace.QName;
import java.time.Duration;
import java.util.Collections;
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
    }

    @Test
    @DisplayName("An SDC SERVICE PROVIDER SHALL implement at least those of the following BICEPS SERVICEs that it"
        + " supports as one MDPWS HOSTED SERVICE:\n"
        + " - Description Event Service\n"
        + " - State Event Service\n"
        + " - Context Service\n"
        + " - Waveform Service")
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
    @TestIdentifier(EnabledTestConfig.GLUE_R0036)
    @TestDescription("Subscribes to all Reports, checks that all Subscriptions are active, then triggers"
        + " an EpisodicContextReport and intentionally fails to receive it. Afterwards, it checks that all"
        + " Subscriptions have been cancelled by the provider.")
    void testRequirementR0036() throws InterruptedException {

        // preparation
        final LocationContextState locationContext = getOrCreateLocationContextState();
        assert locationContext != null;

        final ReportTestData triggerableReport = new ReportTestData(
            WsdlConstants.OPERATION_EPISODIC_CONTEXT_REPORT,
            ActionConstants.ACTION_EPISODIC_CONTEXT_REPORT,
            report -> { // subscribe to Report
                subscribeToReportWithTheAbilityToFail(getLocalBaseURI(),
                    WsdlConstants.OPERATION_EPISODIC_CONTEXT_REPORT,
                    ActionConstants.ACTION_EPISODIC_CONTEXT_REPORT,
                    WsdlConstants.SERVICE_CONTEXT,
                    MessageGeneratingUtil::getContextService,
                    report);
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
            });
        final List<ReportTestData> reports = List.of(
            triggerableReport,
            new ReportTestData(
                WsdlConstants.OPERATION_OPERATION_INVOKED_REPORT,
                ActionConstants.ACTION_OPERATION_INVOKED_REPORT,
                report -> { // subscribe to Report
                    return subscribeToReport(getLocalBaseURI(),
                        WsdlConstants.OPERATION_OPERATION_INVOKED_REPORT,
                        ActionConstants.ACTION_OPERATION_INVOKED_REPORT,
                        WsdlConstants.SERVICE_SET,
                        MessageGeneratingUtil::getSetService,
                        report);
                },
                () -> {
                    // does not need to be triggered in this test.
                }),
            new ReportTestData(
                WsdlConstants.OPERATION_DESCRIPTION_MODIFICATION_REPORT,
                ActionConstants.ACTION_DESCRIPTION_MODIFICATION_REPORT,
                report -> { // subscribe to Report
                    return subscribeToReport(getLocalBaseURI(),
                        WsdlConstants.OPERATION_DESCRIPTION_MODIFICATION_REPORT,
                        ActionConstants.ACTION_DESCRIPTION_MODIFICATION_REPORT,
                        WsdlConstants.SERVICE_DESCRIPTION_EVENT,
                        MessageGeneratingUtil::getDescriptionEventService,
                        report);
                },
                () -> {
                   // does not need to be triggered in this test.
                }
            ),
            new ReportTestData(
                WsdlConstants.OPERATION_EPISODIC_ALERT_REPORT,
                ActionConstants.ACTION_EPISODIC_ALERT_REPORT,
                report -> { // subscribe to Report
                    return subscribeToReport(getLocalBaseURI(),
                        WsdlConstants.OPERATION_EPISODIC_ALERT_REPORT,
                        ActionConstants.ACTION_EPISODIC_ALERT_REPORT,
                        WsdlConstants.SERVICE_STATE_EVENT,
                        MessageGeneratingUtil::getStateEventService,
                        report);
                },
                () -> {
                    // does not need to be triggered in this test.
                }),
            new ReportTestData(
                WsdlConstants.OPERATION_EPISODIC_COMPONENT_REPORT,
                ActionConstants.ACTION_EPISODIC_COMPONENT_REPORT,
                report -> { // subscribe to Report
                    return subscribeToReport(getLocalBaseURI(),
                        WsdlConstants.OPERATION_EPISODIC_COMPONENT_REPORT,
                        ActionConstants.ACTION_EPISODIC_COMPONENT_REPORT,
                        WsdlConstants.SERVICE_STATE_EVENT,
                        MessageGeneratingUtil::getStateEventService,
                        report);
                },
                () -> {
                    // does not need to be triggered in this test.
                }),
            new ReportTestData(
                WsdlConstants.OPERATION_EPISODIC_METRIC_REPORT,
                ActionConstants.ACTION_EPISODIC_METRIC_REPORT,
                report -> { // subscribe to Report
                    return subscribeToReport(getLocalBaseURI(),
                        WsdlConstants.OPERATION_EPISODIC_METRIC_REPORT,
                        ActionConstants.ACTION_EPISODIC_METRIC_REPORT,
                        WsdlConstants.SERVICE_STATE_EVENT,
                        MessageGeneratingUtil::getStateEventService,
                        report);
                },
                () -> {
                    // does not need to be triggered in this test.
                }),
            new ReportTestData(
                WsdlConstants.OPERATION_EPISODIC_OPERATIONAL_STATE_REPORT,
                ActionConstants.ACTION_EPISODIC_OPERATIONAL_STATE_REPORT,
                report -> { // subscribe to Report
                    return subscribeToReport(getLocalBaseURI(),
                        WsdlConstants.OPERATION_EPISODIC_OPERATIONAL_STATE_REPORT,
                        ActionConstants.ACTION_EPISODIC_OPERATIONAL_STATE_REPORT,
                        WsdlConstants.SERVICE_STATE_EVENT,
                        MessageGeneratingUtil::getStateEventService,
                        report);
                },
                () -> {
                    // does not need to be triggered in this test.
                })
        );

        subscribeToAllReports(reports);

        checkSubscriptionsAreActive(reports);

        triggerReportAndIntentionallyFailReceivingIt(triggerableReport);

        checkThatAllSubscriptionsAreCancelled(reports);
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

    private void checkThatAllSubscriptionsAreCancelled(final List<ReportTestData> reports) throws InterruptedException {
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
                + " although they should have been according to glue:R0036.");
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
                    getInjector().getInstance(CommunicationLog.class));
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

    @SuppressWarnings("SameParameterValue")
    private void subscribeToReportWithTheAbilityToFail(final String baseURI,
                                                       final String reportName,
                                                       final String action,
                                                       final String serviceName,
                                                       final GetServiceProxyClosure getServiceProxy,
                                                       final ReportTestData reportTestData) {
        final Optional<HostedServiceProxy> hostedServiceProxy = getServiceProxy.execute(testClient);
        if (hostedServiceProxy.isEmpty()) {
            fail("failed to retrieve serviceProxy for " + serviceName);
        }

        final String contextSuffix = determineContextSuffix(reportName);
        final String endToContext = "/EventSink/EndTo/" + contextSuffix;
        final String endToUri = this.httpServerRegistry.registerContext(
            baseURI,
            endToContext,
            (inStream, outStream, communicationContext) -> {
                // end of subscription
            });
        final String notifyToContext = "/EventSink/NotifyTo/" + contextSuffix;
        final String notifyToUri = this.httpServerRegistry.registerContext(
            baseURI,
            notifyToContext,
            new FailingHttpHandler(reportTestData));
        final Subscribe subscribeBody = this.wseFactory.createSubscribe();
        final DeliveryType deliveryType = this.wseFactory.createDeliveryType();
        deliveryType.setMode("http://schemas.xmlsoap.org/ws/2004/08/eventing/DeliveryModes/Push");
        final EndpointReferenceType notifyToEpr = this.wsaUtil.createEprWithAddress(notifyToUri);
        deliveryType.setContent(Collections.singletonList(this.wseFactory.createNotifyTo(notifyToEpr)));
        subscribeBody.setDelivery(deliveryType);
        final EndpointReferenceType endToEpr = this.wsaUtil.createEprWithAddress(endToUri);
        subscribeBody.setEndTo(endToEpr);
        final FilterType filterType = this.wseFactory.createFilterType();
        filterType.setDialect("http://docs.oasis-open.org/ws-dd/ns/dpws/2009/01/Action");
        filterType.setContent(Collections.singletonList(this.implodeUriList(Collections.singletonList(action))));
        subscribeBody.setExpires(DURATION);
        subscribeBody.setFilter(filterType);
        final SoapMessage subscribeRequest =
            this.soapUtil.createMessage(
                WsEventingConstants.WSA_ACTION_SUBSCRIBE,
                subscribeBody);

        final RequestResponseClient requestResponseClient = hostedServiceProxy
                .orElseThrow()
                .getRequestResponseClient();

        final SoapMessage soapResponse;
        try {
            soapResponse = requestResponseClient.sendRequestResponse(subscribeRequest);
            if (soapResponse.isFault()) {
                throw new RuntimeException("Could not subscribe to " + reportName + ".");
            }
        } catch (SoapFaultException | MarshallingException | TransportException | InterceptorException e) {
            fail("encountered Exception while subscribing to " + reportName, e);
        }
    }

    private String getLocalBaseURI() {
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


}
