/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.tests.glue.direct;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.http.HttpStatus;
import org.somda.sdc.dpws.http.HttpException;
import org.somda.sdc.dpws.http.HttpHandler;
import org.somda.sdc.dpws.soap.CommunicationContext;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * HTTPHandler that fails reception (answers the Request with the HTTP Response Status Code 500)
 * whenever reportTestData.failOnReceivingReport is set to true.
 */
public class FailingHttpHandler implements HttpHandler {

    private static final Logger LOG = LogManager.getLogger(DirectSubscriptionHandlingTest.class);

    private final ReportTestData reportTestData;

    /**
     * Constructor for an HTTPHandler that can optionally fail Report Reception.
     * When the reportTestData.failOnReceivingReport is set to true, it fails reception
     * by answering the Request with a 500 Return Code. Otherwise, it accepts the report.
     * @param reportTestData the ReportTestData instance.
     */
    public FailingHttpHandler(final ReportTestData reportTestData) {
        this.reportTestData = reportTestData;
    }

    /**
     * Implements HTTPHandler.handle().
     * @param inStream - stream of the incoming SOAP request. Do not forget to call {@linkplain
     *                                   InputStream#close()} when the messages was read.
     * @param outStream - stream of the outgoing SOAP response. Do not forget to call {@linkplain
     *                                   OutputStream#close()} when the message is ready to be sent back.
     * @param communicationContext - information from the transport and application layer, e.g., local address, local
     *                                   port, certificate data etc.
     * @throws HttpException in order to fail the Report.
     */
    public void handle(final InputStream inStream,
                       final OutputStream outStream,
                       final CommunicationContext communicationContext)
        throws HttpException {
        // read message, so it is properly stored in the commLog.
        try {
            inStream.readAllBytes();
            inStream.close();
        } catch (IOException e) {
            LOG.warn("Failed to read message", e);
        }
        // notification
        reportTestData.setReportReceived(true);
        synchronized (reportTestData.getSyncPoint()) {
            reportTestData.getSyncPoint().notifyAll();
        }
        if (reportTestData.getFailOnReceivingReport()) {
            throw new HttpException(
                HttpStatus.INTERNAL_SERVER_ERROR_500,
                "intentional failure for testing purposes.");
        }
    }

}