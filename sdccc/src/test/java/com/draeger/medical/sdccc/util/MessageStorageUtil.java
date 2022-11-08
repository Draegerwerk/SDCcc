/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.util;

import com.draeger.medical.dpws.soap.model.Envelope;
import com.draeger.medical.sdccc.marshalling.SoapMarshalling;
import com.draeger.medical.sdccc.messages.Message;
import com.draeger.medical.sdccc.messages.MessageStorage;
import com.draeger.medical.t2iapi.ResponseTypes;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.inject.Inject;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;
import org.somda.sdc.dpws.CommunicationLog;
import org.somda.sdc.dpws.DpwsConstants;
import org.somda.sdc.dpws.soap.ApplicationInfo;
import org.somda.sdc.dpws.soap.CommunicationContext;
import org.somda.sdc.dpws.soap.HttpApplicationInfo;
import org.somda.sdc.dpws.soap.TransportInfo;

/**
 * Utility to add messages to the message storage.
 */
public class MessageStorageUtil {
    private static final String SECURE_HTTP_SCHEME = "https";
    private static final String INSECURE_HTTP_SCHEME = "http";

    private final SoapMarshalling marshalling;
    private final MessageBuilder messageBuilder;

    @Inject
    MessageStorageUtil(final SoapMarshalling marshalling, final MessageBuilder messageBuilder) {
        this.marshalling = marshalling;
        this.messageBuilder = messageBuilder;
    }

    /**
     * Waits for a given amount of inbound messages to be present.
     *
     * @param messageStorage storage to wait for
     * @param i amount of inbound messages to wait for
     * @throws IOException passed through from getInboundMessages
     */
    public static void waitForInbound(final MessageStorage messageStorage, final long i) throws IOException {
        while (true) {
            try (final var inboundMessages = messageStorage.getInboundMessages()) {
                if (inboundMessages.getStream().count() < i) {
                    Thread.yield();
                } else {
                    break;
                }
            }
        }
    }

    /**
     * Waits for a given amount of manipulation data to be present.
     *
     * @param messageStorage    storage to wait for
     * @param i                 amount of manipulation data to wait for
     * @param name              of the manipulation
     * @throws IOException      passed through from getInboundMessages
     */
    public static void waitForManipulation(final MessageStorage messageStorage, final long i, final String name)
            throws IOException {
        while (true) {
            try (final var manipulations = messageStorage.getManipulationDataByManipulation(name)) {
                if (manipulations.getStream().count() < i) {
                    Thread.yield();
                } else {
                    break;
                }
            }
        }
    }

    /**
     * Adds a message to the provided message storage.
     *
     * @param storage        to write to
     * @param resourceStream message to write to storage
     * @param transportType  transport type of message
     * @param direction      direction of message
     * @param context        message context information
     * @throws IOException if the message could not be closed after writing
     */
    public void addMessage(
            final MessageStorage storage,
            final InputStream resourceStream,
            final CommunicationLog.TransportType transportType,
            final CommunicationLog.Direction direction,
            final CommunicationContext context)
            throws IOException {
        assert resourceStream.available() > 0;
        try (final var message =
                storage.createMessageStream(transportType, direction, CommunicationLog.MessageType.RESPONSE, context)) {
            message.write(resourceStream.readAllBytes());
        }
    }

    /**
     * Adds a message to the provided message storage.
     *
     * @param storage       to write to
     * @param soapMessage   message to write to storage
     * @param transportType transport type of message
     * @param direction     direction of message
     * @param context       message context information
     * @throws IOException if the message could not be closed after writing
     */
    public void addMessage(
            final MessageStorage storage,
            final JAXBElement<Envelope> soapMessage,
            final CommunicationLog.TransportType transportType,
            final CommunicationLog.Direction direction,
            final CommunicationContext context)
            throws IOException, JAXBException {
        try (final var message = new ByteArrayOutputStream()) {
            marshalling.marshal(soapMessage, message);
            addMessage(storage, new ByteArrayInputStream(message.toByteArray()), transportType, direction, context);
        }
    }

    /**
     * Adds a message to the provided message storage.
     *
     * @param storage       to write to
     * @param soapMessage   message to write to storage
     * @param transportType transport type of message
     * @param direction     direction of message
     * @param context       message context information
     * @throws IOException if the message could not be closed after writing
     */
    public void addMessage(
            final MessageStorage storage,
            final Envelope soapMessage,
            final CommunicationLog.TransportType transportType,
            final CommunicationLog.Direction direction,
            final CommunicationContext context)
            throws IOException, JAXBException {
        addMessage(storage, messageBuilder.buildEnvelope(soapMessage), transportType, direction, context);
    }

    /**
     * Adds an inbound message to the provided message storage.
     *
     * @param storage       to write to
     * @param message       to write to storage
     * @throws IOException if the message could not be closed after writing
     */
    public synchronized void addMessage(final MessageStorage storage, final Message message) throws IOException {
        final long previous_count;
        try (final var messages = storage.getInboundMessages()) {
            previous_count = messages.getStream().count();
        }
        storage.addMessage(message);
        waitForInbound(storage, previous_count + 1);
    }

    /**
     * Adds manipulation data to the provided message storage.
     *
     * @param storage       to write to
     * @param startTime     of the manipulation
     * @param finishTime    of the manipulation
     * @param result        of the manipulation
     * @param name          of the manipulation
     * @param parameters    of the manipulation
     * @throws IOException if the message could not be closed after writing
     */
    public synchronized void addManipulation(
            final MessageStorage storage,
            final long startTime,
            final long finishTime,
            final ResponseTypes.Result result,
            final String name,
            final List<Pair<String, String>> parameters)
            throws IOException {
        final long previous_count;
        try (final var manipulations = storage.getManipulationDataByManipulation(name)) {
            previous_count = manipulations.getStream().count();
        }
        storage.createManipulationInfo(startTime, finishTime, result, name, parameters);
        waitForManipulation(storage, previous_count + 1, name);
    }

    /**
     * Adds an inbound tls secured message to the provided message storage.
     *
     * @param storage       to write to
     * @param messageStream message to write to storage
     * @param certificates  to attach to message
     * @param httpHeaders   headers transmitted for message
     * @throws IOException if the message could not be closed after writing
     */
    public synchronized void addInboundSecureHttpMessage(
            final MessageStorage storage,
            final InputStream messageStream,
            final List<X509Certificate> certificates,
            final ListMultimap<String, String> httpHeaders)
            throws IOException {

        final long previous_count;
        try (final var inboundMessages = storage.getInboundMessages()) {
            previous_count = inboundMessages.getStream().count();
        }

        final CommunicationContext messageContext = new CommunicationContext(
                new HttpApplicationInfo(httpHeaders, "", ""),
                new TransportInfo(SECURE_HTTP_SCHEME, null, null, null, null, certificates));
        addMessage(
                storage,
                messageStream,
                CommunicationLog.TransportType.HTTP,
                CommunicationLog.Direction.INBOUND,
                messageContext);

        waitForInbound(storage, previous_count + 1);
    }

    /**
     * Adds an inbound tls secured message to the provided message storage.
     *
     * @param storage      to write to
     * @param message      message to write to storage
     * @param certificates to attach to message
     * @param httpHeaders  headers transmitted for message
     * @throws IOException if the message could not be closed after writing
     */
    public void addInboundSecureHttpMessage(
            final MessageStorage storage,
            final Envelope message,
            final List<X509Certificate> certificates,
            final ListMultimap<String, String> httpHeaders)
            throws IOException, JAXBException {

        final long previous_count;
        try (final var inboundMessages = storage.getInboundMessages()) {
            previous_count = inboundMessages.getStream().count();
        }

        final CommunicationContext messageContext = new CommunicationContext(
                new HttpApplicationInfo(httpHeaders, "", ""),
                new TransportInfo(SECURE_HTTP_SCHEME, null, null, null, null, certificates));
        addMessage(
                storage,
                message,
                CommunicationLog.TransportType.HTTP,
                CommunicationLog.Direction.INBOUND,
                messageContext);

        waitForInbound(storage, previous_count + 1);
    }

    /**
     * Adds an inbound http message using the default certificate provided by {@linkplain CertificateUtil}.
     *
     * @param storage       to write to
     * @param messageStream message to write to storage
     * @throws IOException if the message could not be closed after writing or the certificate could not be loaded
     */
    public void addInboundSecureHttpMessage(final MessageStorage storage, final InputStream messageStream)
            throws IOException {
        try {
            addInboundSecureHttpMessage(
                    storage, messageStream, List.of(CertificateUtil.getDummyCert()), ArrayListMultimap.create());
        } catch (final CertificateException e) {
            throw new IOException(e);
        }
    }

    /**
     * Adds an inbound http message using the default certificate provided by {@linkplain CertificateUtil}.
     *
     * @param storage to write to
     * @param message message to write to storage
     * @throws IOException if the message could not be closed after writing or the certificate could not be loaded
     */
    public void addInboundSecureHttpMessage(final MessageStorage storage, final Envelope message)
            throws IOException, JAXBException {
        try {
            addInboundSecureHttpMessage(
                    storage, message, List.of(CertificateUtil.getDummyCert()), ArrayListMultimap.create());
        } catch (final CertificateException e) {
            throw new IOException(e);
        }
    }

    /**
     * Adds an inbound udp message.
     *
     * @param storage to write to
     * @param message message to write to storage
     * @throws IOException if the message could not be closed after writing or the certificate could not be loaded
     */
    public void addInboundUdpMessage(final MessageStorage storage, final Envelope message)
            throws IOException, JAXBException {

        final long previous_count;
        try (final var inboundMessages = storage.getInboundMessages()) {
            previous_count = inboundMessages.getStream().count();
        }

        final CommunicationContext messageContext = new CommunicationContext(
                new ApplicationInfo(),
                new TransportInfo(
                        DpwsConstants.URI_SCHEME_SOAP_OVER_UDP, null, null, null, null, Collections.emptyList()));
        addMessage(
                storage,
                message,
                CommunicationLog.TransportType.UDP,
                CommunicationLog.Direction.INBOUND,
                messageContext);

        waitForInbound(storage, previous_count + 1);
    }
}
