/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.tests.glue.direct;

import org.somda.sdc.dpws.soap.wseventing.EventSink;
import org.somda.sdc.dpws.soap.wseventing.SubscribeResult;

/**
 * Data that the DirectSubscriptionHandlingTest needs to store per Report.
 */
public class ReportTestData {

    private String reportName;
    private String action;
    private Boolean reportReceived;
    private Boolean failOnReceivingReport;
    private Boolean subscriptionEndWithStatusDeliveryFailedReceived;
    private final Object syncPoint;
    private ReportTriggerClosure trigger;
    private ReportSubscribeClosure subscribe;
    private SubscribeResult subscription;
    private EventSink eventSink;

    /**
     * Constructor.
     * @param reportName - name of the Report.
     * @param action - action of the Report.
     * @param subscribe - Closure that subscribes to the Report.
     * @param trigger - Closure that triggers the Report.
     */
    public ReportTestData(
            final String reportName,
            final String action,
            final ReportSubscribeClosure subscribe,
            final ReportTriggerClosure trigger) {
        this.reportName = reportName;
        this.action = action;
        this.reportReceived = false;
        this.failOnReceivingReport = false;
        this.subscriptionEndWithStatusDeliveryFailedReceived = false;
        this.syncPoint = new Object();
        this.subscribe = subscribe;
        this.trigger = trigger; // TODO: it would be simpler to pass the trigger as an override
    }

    public Boolean getReportReceived() {
        return reportReceived;
    }

    public String getReportName() {
        return reportName;
    }

    public String getReportAction() {
        return action;
    }

    /**
     * Setter for reportReceived.
     * @param reportReceived the new value.
     */
    public void setReportReceived(final Boolean reportReceived) {
        this.reportReceived = reportReceived;
    }

    public Boolean getFailOnReceivingReport() {
        return failOnReceivingReport;
    }

    /**
     * Setter for failOnReceivingReport.
     * @param failOnReceivingReport the new value.
     */
    public void setFailOnReceivingReport(final Boolean failOnReceivingReport) {
        this.failOnReceivingReport = failOnReceivingReport;
    }

    public Object getSyncPoint() {
        return syncPoint;
    }

    /**
     * Triggers the Report.
     */
    public void trigger() {
        this.trigger.trigger();
    }

    /**
     * Subscribe to the Report.
     */
    public void subscribe() {
        this.subscribe.subscribe(this);
    }

    public SubscribeResult getSubscription() {
        return subscription;
    }

    /**
     * Set the Subscription.
     * @param subscription - the SubscribeResult returned upon subscribing to the Report.
     */
    public void setSubscription(final SubscribeResult subscription) {
        this.subscription = subscription;
    }

    public EventSink getEventSink() {
        return eventSink;
    }

    /**
     * Set the EventSink used for subscribing.
     * @param eventSink the EventSink.
     */
    public void setEventSink(final EventSink eventSink) {
        this.eventSink = eventSink;
    }

    /**
     * Returns true if the given notificationBody could belong to this subscription.
     * @param notificationBody the notificationBody to check
     * @return true, if it is of the right type, false otherwise.
     */
    public boolean doesNotificationBodyBelongToThisReport(final Object notificationBody) {
        return false; // default implementation - please override
    }

    /**
     * Set the Value of SubscriptionEndWithStatusDeliveryFailedReceived.
     * @param b the new value.
     */
    public void setSubscriptionEndWithStatusDeliveryFailedReceived(final boolean b) {
        this.subscriptionEndWithStatusDeliveryFailedReceived = b;
    }

    /**
     * Gets the value of SubscriptionEndWithStatusDeliveryFailedReceived.
     * @return the value.
     */
    public boolean getSubscriptionEndWithStatusDeliveryFailedReceived() {
        return this.subscriptionEndWithStatusDeliveryFailedReceived;
    }
}
