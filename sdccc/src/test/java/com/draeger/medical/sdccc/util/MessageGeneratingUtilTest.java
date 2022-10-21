/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.util;

import com.draeger.medical.biceps.model.participant.LocalizedText;
import com.draeger.medical.dpws.soap.model.Envelope;
import com.draeger.medical.sdccc.marshalling.MarshallingUtil;
import com.draeger.medical.sdccc.messages.MessageStorage;
import com.draeger.medical.sdccc.sdcri.testclient.TestClient;
import com.draeger.medical.sdccc.sdcri.testclient.TestClientUtil;
import com.draeger.medical.sdccc.tests.test_util.InjectorUtil;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.somda.sdc.biceps.model.message.GetLocalizedText;
import org.somda.sdc.biceps.model.message.ObjectFactory;
import org.somda.sdc.dpws.http.HttpException;
import org.somda.sdc.dpws.service.HostedServiceProxy;
import org.somda.sdc.dpws.soap.SoapMarshalling;
import org.somda.sdc.dpws.soap.SoapMessage;
import org.somda.sdc.dpws.soap.exception.TransportException;
import org.somda.sdc.glue.common.ActionConstants;
import org.somda.sdc.glue.common.WsdlConstants;

import javax.annotation.Nullable;
import java.io.IOException;
import java.math.BigInteger;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for the {@linkplain MessageGeneratingUtil}.
 */
public class MessageGeneratingUtilTest {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    private TestClient client;
    private MessageStorage storage;
    private TestRunObserver observer;
    private MessageGeneratingUtil genUtil;
    private Injector riInjector;
    private MessageStorageUtil messageStorageUtil;
    private MdibBuilder mdibBuilder;
    private MessageBuilder messageBuilder;

    @BeforeEach
    void setUp() throws IOException, TimeoutException {
        // sdcri injector for sending messages
        riInjector = TestClientUtil.createClientInjector();
        final var riMarshalling = riInjector.getInstance(SoapMarshalling.class);
        riMarshalling.startAsync().awaitRunning(DEFAULT_TIMEOUT);

        // message builder injector for building test messages
        final Injector marshallingInjector = MarshallingUtil.createMarshallingTestInjector(true);
        messageStorageUtil = marshallingInjector.getInstance(MessageStorageUtil.class);
        mdibBuilder = marshallingInjector.getInstance(MdibBuilder.class);
        messageBuilder = marshallingInjector.getInstance(MessageBuilder.class);

        client = mock(TestClient.class, Mockito.RETURNS_DEEP_STUBS);
        final var injector = InjectorUtil.setupInjector(
                new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(TestClient.class).toInstance(client);
                    }
                }
        );
        observer = injector.getInstance(TestRunObserver.class);
        storage = injector.getInstance(MessageStorage.class);
        when(client.getInjector()).thenReturn(riInjector);
        genUtil = new MessageGeneratingUtil(client, observer, new ObjectFactory(), storage);
    }

    @AfterEach
    void tearDown() throws TimeoutException, IOException {
        final var riMarshalling = riInjector.getInstance(SoapMarshalling.class);
        riMarshalling.stopAsync().awaitTerminated(DEFAULT_TIMEOUT);

        storage.close();
    }

    /**
     * Verifies that the correct combination of localized text references are queried.
     *
     * <p>
     * Note: Testing using only one type of message is sufficient, as the XPath expression
     * used by {@linkplain MessageGeneratingUtil#getLocalizedTexts()} is fully covered in
     * {@linkplain com.draeger.medical.sdccc.tests.biceps.invariant.InvariantBicepsNormativeAnnexTestTest}
     *
     * @throws Exception on any exception
     */
    @Test
    public void testGetLocalizedText() throws Exception {
        final var mockHostedService = mock(HostedServiceProxy.class, Mockito.RETURNS_DEEP_STUBS);
        when(mockHostedService.getType().getTypes()).thenReturn(List.of(WsdlConstants.PORT_TYPE_LOCALIZATION_QNAME));
        when(client.getHostingServiceProxy().getHostedServices()).thenReturn(
                Map.of("loc", mockHostedService)
        );

        final var soapCaptor = ArgumentCaptor.forClass(SoapMessage.class);

        final var ref1 = "ref1";
        final var ref2 = "ref2";
        messageStorageUtil.addInboundSecureHttpMessage(
                storage,
                createSystemErrorReportWithRef(ref1, null, BigInteger.TEN)
        );
        messageStorageUtil.addInboundSecureHttpMessage(
                storage,
                createSystemErrorReportWithRef(ref2, null, BigInteger.ONE)
        );
        // this one is unavailable from the service
        messageStorageUtil.addInboundSecureHttpMessage(
                storage,
                createSystemErrorReportWithRef(null, "de", BigInteger.TWO)
        );

        genUtil.getLocalizedTexts();
        final var sentMessageNum = 4;

        verify(mockHostedService, times(sentMessageNum)).sendRequestResponse(soapCaptor.capture());

        final var sentMessages = soapCaptor.getAllValues();
        assertEquals(sentMessageNum, sentMessages.size());

        verifySentRefs(sentMessages.get(0), List.of(ref2));
        verifySentRefs(sentMessages.get(1), List.of(ref1));
        verifySentRefs(sentMessages.get(2), List.of(ref1, ref2));
        // CHECKSTYLE.OFF: MagicNumber
        verifySentRefs(sentMessages.get(3), Collections.emptyList());
        // CHECKSTYLE.ON: MagicNumber
    }

    /**
     * Verifies that errors trigger run invalidations.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testGetLocalizedTextErrors() throws Exception {
        final var mockHostedService = mock(HostedServiceProxy.class, Mockito.RETURNS_DEEP_STUBS);
        when(mockHostedService.sendRequestResponse(any()))
                .thenThrow(new TransportException(new HttpException(Constants.HTTP_INTERNAL_SERVER_ERROR)));

        when(mockHostedService.getType().getTypes()).thenReturn(List.of(WsdlConstants.PORT_TYPE_LOCALIZATION_QNAME));
        when(client.getHostingServiceProxy().getHostedServices()).thenReturn(
                Map.of("loc", mockHostedService)
        );

        genUtil.getLocalizedTexts();
        assertTrue(observer.isInvalid());
    }


    /**
     * Verifies that 413 payload too large errors do not invalidate the test run.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testGetLocalizedTextPayloadTooLarge() throws Exception {
        final var mockHostedService = mock(HostedServiceProxy.class, Mockito.RETURNS_DEEP_STUBS);
        when(mockHostedService.sendRequestResponse(any()))
                .thenThrow(new TransportException(new HttpException(Constants.HTTP_PAYLOAD_TOO_LARGE)));

        when(mockHostedService.getType().getTypes()).thenReturn(List.of(WsdlConstants.PORT_TYPE_LOCALIZATION_QNAME));
        when(client.getHostingServiceProxy().getHostedServices()).thenReturn(
                Map.of("loc", mockHostedService)
        );

        genUtil.getLocalizedTexts();
        assertFalse(observer.isInvalid());
    }

    private void verifySentRefs(final SoapMessage message, final List<String> refs) {
        final var body = message.getOriginalEnvelope().getBody().getAny().get(0);
        final GetLocalizedText sentMessage = (GetLocalizedText) body;
        final var sentRefs = sentMessage.getRef();
        assertEquals(refs.size(), sentRefs.size());

        final var intersection = refs.stream().distinct().filter(sentRefs::contains).count();
        assertEquals(refs.size(), intersection);
    }

    Envelope createSystemErrorReportWithRef(
            @Nullable final String ref, @Nullable final String lang,
            final BigInteger version
    ) {
        final var errorCode = mdibBuilder.buildCodedValue("errorcode");
        final var reportPart = messageBuilder.buildSystemErrorReportReportPart(errorCode);

        reportPart.setErrorInfo(createLocalizedText(
                ref, lang, version
        ));

        final var response = messageBuilder.buildSystemErrorReport("0", List.of(reportPart));

        return messageBuilder.createSoapMessageWithBody(
                ActionConstants.ACTION_SYSTEM_ERROR_REPORT,
                response
        );
    }

    LocalizedText createLocalizedText(
            @Nullable final String ref, @Nullable final String lang,
            @Nullable final BigInteger version
    ) {
        final var localizedText = mdibBuilder.buildLocalizedText();
        localizedText.setRef(ref);
        localizedText.setVersion(version);
        localizedText.setLang(lang);
        return localizedText;
    }
}
