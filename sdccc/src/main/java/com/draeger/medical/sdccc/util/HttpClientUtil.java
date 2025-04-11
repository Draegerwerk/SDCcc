/*
 * This Source Code Form is subject to the terms of the "SDCcc non-commercial use license".
 *
 * Copyright (C) 2025 Draegerwerk AG & Co. KGaA
 */

package com.draeger.medical.sdccc.util;

import com.google.common.io.ByteStreams;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.somda.sdc.dpws.TransportBindingException;
import org.somda.sdc.dpws.http.HttpException;
import org.somda.sdc.dpws.soap.SoapConstants;
import org.somda.sdc.dpws.soap.SoapMarshalling;
import org.somda.sdc.dpws.soap.SoapMessage;
import org.somda.sdc.dpws.soap.SoapUtil;
import org.somda.sdc.dpws.soap.exception.MarshallingException;
import org.somda.sdc.dpws.soap.exception.SoapFaultException;
import org.somda.sdc.dpws.soap.exception.TransportException;

/**
 * Utility for use with apache {@linkplain HttpClient} instances.
 */
@Singleton
public class HttpClientUtil {
    private static final Logger LOG = LogManager.getLogger();
    private final SoapUtil soapUtil;
    private final SoapMarshalling marshalling;

    @Inject
    HttpClientUtil(final SoapUtil soapUtil, final SoapMarshalling marshalling) {
        this.soapUtil = soapUtil;
        this.marshalling = marshalling;
    }

    /**
     * Sends a message to an endpoint.
     *
     * @param client   to send from
     * @param endpoint to send to
     * @param message  to send
     * @return response, empty if server response was empty
     * @throws TransportException on transport exceptions (http, ..)
     * @throws SoapFaultException on soap fault responses
     */
    public SoapMessage postMessage(final HttpClient client, final String endpoint, final byte[] message)
            throws TransportException, SoapFaultException {

        final HttpResponse response = postMessageWithHttpResponse(client, endpoint, message);
        final HttpEntity entity = response.getEntity();
        byte[] bytes;

        try (final InputStream contentStream = entity.getContent()) {
            bytes = ByteStreams.toByteArray(contentStream);
        } catch (final IOException e) {
            LOG.error("Couldn't read response", e);
            bytes = new byte[0];
        }

        try (final InputStream inputStream = new ByteArrayInputStream(bytes)) {
            if (inputStream.available() > 0) {
                final SoapMessage msg = soapUtil.createMessage(marshalling.unmarshal(inputStream));
                if (msg.isFault()) {
                    throw new SoapFaultException(
                            msg, new HttpException(response.getStatusLine().getStatusCode()));
                }

                return msg;
            } else {
                // >= 300 status code is trouble
                if (response.getStatusLine().getStatusCode() >= Constants.HTTP_MULTIPLE_CHOICES) {
                    throw new TransportBindingException(
                            String.format(
                                    "Endpoint was not able to process request. HTTP status code: %s",
                                    response.getStatusLine()),
                            new TransportException(
                                    new HttpException(response.getStatusLine().getStatusCode())));
                }
            }
        } catch (final jakarta.xml.bind.JAXBException e) {
            LOG.debug(
                    "Unmarshalling of a message failed: {}. Response payload:\n{}",
                    e.getMessage(),
                    new String(bytes, StandardCharsets.UTF_8));
            LOG.trace("Unmarshalling of a message failed. ", e);
            throw new TransportBindingException(
                    String.format("Receiving of a response failed due to unmarshalling problem: %s", e.getMessage()),
                    new MarshallingException(e));
        } catch (final IOException e) {
            LOG.debug("Error occurred while processing response: {}", e.getMessage());
            LOG.trace("Error occurred while processing response", e);
        } finally {
            try {
                // ensure the entire response was consumed, just in case
                EntityUtils.consume(response.getEntity());
            } catch (final IOException e) {
                // if this fails, we will either all die or it doesn't matter at all...
            }
        }

        return soapUtil.createMessage();
    }

    /**
     * Sends a message to an endpoint.
     *
     * @param client   to send from
     * @param endpoint to send to
     * @param message  to send
     * @return response, empty if server response was empty
     * @throws TransportException on transport exceptions (http, ..)
     */
    public HttpResponse postMessageWithHttpResponse(
            final HttpClient client, final String endpoint, final byte[] message) throws TransportException {

        final HttpPost post = new HttpPost(endpoint);
        post.setHeader(HttpHeaders.ACCEPT, SoapConstants.MEDIA_TYPE_SOAP);
        post.setHeader(HttpHeaders.CONTENT_TYPE, SoapConstants.MEDIA_TYPE_SOAP);

        // attach payload
        final var requestEntity = new ByteArrayEntity(message);
        post.setEntity(requestEntity);

        LOG.debug("Sending POST request to {}", endpoint);

        return executeRequest(client, post, endpoint);
    }

    public HttpResponse getMessage(final HttpClient client, final String endpoint) throws TransportException {
        final HttpGet get = new HttpGet(endpoint);
        LOG.debug("Sending GET request to {}", endpoint);
        return executeRequest(client, get, endpoint);
    }

    private HttpResponse executeRequest(final HttpClient client, final HttpRequestBase request, final String endpoint)
            throws TransportException {
        try {
            return client.execute(request);
        } catch (final SocketException e) {
            LOG.error("No response received in request to {}", endpoint, e);
            throw new TransportException(e);
        } catch (final IOException e) {
            LOG.error("Unexpected IO exception on request to {}", endpoint);
            LOG.trace("Unexpected IO exception on request to {}", endpoint, e);
            throw new TransportException("No response received", e);
        }
    }
}
