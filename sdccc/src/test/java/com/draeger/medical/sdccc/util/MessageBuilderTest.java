/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.util;

import com.draeger.medical.dpws.soap.model.Envelope;
import com.draeger.medical.dpws.soap.wsaddressing.model.AttributedURIType;
import com.draeger.medical.sdccc.marshalling.MarshallingUtil;
import com.draeger.medical.sdccc.marshalling.SoapMarshalling;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.somda.sdc.dpws.soap.wsaddressing.WsAddressingConstants;

import javax.xml.namespace.QName;
import javax.xml.xpath.XPathExpressionException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import static com.draeger.medical.sdccc.util.Constants.wsa;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for the message builder utility.
 */
public class MessageBuilderTest {

    private MessageBuilder builder;
    private SoapMarshalling marshaller;

    @BeforeEach
    void setUp() {
        final var injector = MarshallingUtil.createMarshallingTestInjector(true);
        builder = injector.getInstance(MessageBuilder.class);
        marshaller = injector.getInstance(SoapMarshalling.class);
    }

    /**
     * Tests whether setting the wsa:To element twice leads to it still having only one value.
     */
    @Test
    public void testDuplicateHeaders() throws JAXBException, XPathExpressionException {
        final var action = "http://some.do";
        final var envelope = builder.createBasicSoapMessage(action);
        final var extractor = new XPathExtractor("//" + wsa("To"));

        final var to1 = "SomeTarget";
        final var to2 = "AnotherTarget";

        {
            builder.setMessageTo(envelope, to1);
            final var toElements = extractToHeader(envelope);
            assertEquals(1, toElements.size());
            final var to = toElements.get(0);
            assertEquals(to1, to.getValue().getValue());

            final var baos = new ByteArrayOutputStream();
            marshaller.marshal(builder.buildEnvelope(envelope), baos);

            final var message = baos.toString(StandardCharsets.UTF_8);
            final var elements = extractor.extractFrom(message);
            assertEquals(1, elements.size());
            assertEquals(to1, elements.stream().findFirst().orElseThrow().getTextContent());
        }
        {
            builder.setMessageTo(envelope, to2);
            final var toElements = extractToHeader(envelope);
            assertEquals(1, toElements.size());
            final var to = toElements.get(0);
            assertEquals(to2, to.getValue().getValue());

            final var baos = new ByteArrayOutputStream();
            marshaller.marshal(builder.buildEnvelope(envelope), baos);

            final var message = baos.toString(StandardCharsets.UTF_8);
            final var elements = extractor.extractFrom(message);
            assertEquals(1, elements.size());
            assertEquals(to2, elements.stream().findFirst().orElseThrow().getTextContent());
        }
    }

    private List<JAXBElement<AttributedURIType>> extractToHeader(final Envelope envelope) {
        final var toQname = new QName(WsAddressingConstants.NAMESPACE, "To");

        return envelope.getHeader().getAny().stream()
                .filter(elem -> elem instanceof JAXBElement)
                .map(elem -> (JAXBElement<AttributedURIType>) elem)
                .filter(elem -> elem.getName().equals(toQname))
                .collect(Collectors.toList());
    }

}
