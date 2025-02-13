/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2023 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.marshalling;

import com.draeger.medical.dpws.soap.model.Envelope;
import com.draeger.medical.sdccc.marshalling.guice.MarshallingConfig;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.xml.sax.SAXException;

/**
 * Utility for marshalling classes to XML.
 */
public class SoapMarshalling {

    private static final Logger LOG = LogManager.getLogger(SoapMarshalling.class);

    private static final String PKG_BASE = "com.draeger.medical.";

    private static final String DPWS_BASE = PKG_BASE + "dpws.";
    private static final String PKG_DPWS = DPWS_BASE + "model";

    private static final String SOAP_BASE = DPWS_BASE + "soap.";
    private static final String PKG_SOAP = SOAP_BASE + "model";
    private static final String PKG_WSA = SOAP_BASE + "wsaddressing.model";
    private static final String PKG_WSD = SOAP_BASE + "wsdiscovery.model";
    private static final String PKG_WSE = SOAP_BASE + "wseventing.model";
    private static final String PKG_MEX = SOAP_BASE + "wsmetadataexchange.model";
    private static final String PKG_WST = SOAP_BASE + "wstransfer.model";

    private static final String BICEPS_BASE = PKG_BASE + "biceps.model.";
    private static final String PKG_EXT = BICEPS_BASE + "extension";
    private static final String PKG_PM = BICEPS_BASE + "participant";
    private static final String PKG_MSG = BICEPS_BASE + "message";

    private static final String SCHEMA_SOAP = "xml.xsd:wsdl-1.1-schema.xsd" + ":soap-1.2-schema.xsd";
    private static final String SCHEMA_WSA = "ws-addressing-1.0-schema.xsd";
    private static final String SCHEMA_WSD = "ws-discovery-1.1-schema.xsd";
    private static final String SCHEMA_WSE = "ws-eventing-schema.xsd";
    private static final String SCHEMA_MEX = "ws-metadataexchange-schema.xsd";
    private static final String SCHEMA_WST = "ws-transfer-schema.xsd";
    private static final String SCHEMA_DPWS = "wsdd-dpws-1.1-schema.xsd";
    private static final String SCHEMA_BICEPS =
            "ExtensionPoint.xsd" + ":BICEPS_ParticipantModel.xsd" + ":BICEPS_MessageModel.xsd";

    private static final String PKG_DELIM = ":";
    private static final String SCHEMA_DELIM = ":";

    public static final List<String> PACKAGES =
            List.of(PKG_EXT, PKG_PM, PKG_MSG, PKG_SOAP, PKG_DPWS, PKG_WSA, PKG_WSD, PKG_WSE, PKG_WST, PKG_MEX);
    public static final List<String> SCHEMAS = List.of(
            SCHEMA_SOAP, SCHEMA_WSA, SCHEMA_WSD, SCHEMA_WSE, SCHEMA_MEX, SCHEMA_WST, SCHEMA_DPWS, SCHEMA_BICEPS);

    private final JAXBContext jaxbContext;
    private final Schema schema;

    /**
     * Create a SoapMarshalling instance for use in unit tests.
     *
     * @param validateMessages enable schema validation for messages
     * @param packages         packages to scan for JAXB classes
     * @param schemas          schemas to validate against
     * @throws ParserConfigurationException on error when preparing schema
     * @throws SAXException                 on error when preparing schema
     * @throws IOException                  on error when preparing schema
     */
    @Inject
    public SoapMarshalling(
            @Named(MarshallingConfig.VALIDATE_SOAP_MESSAGES) final boolean validateMessages,
            @Named(MarshallingConfig.PACKAGES) final List<String> packages,
            @Named(MarshallingConfig.SCHEMAS) final List<String> schemas)
            throws ParserConfigurationException, SAXException, IOException {

        try {
            jaxbContext = JAXBContext.newInstance(String.join(PKG_DELIM, packages));
        } catch (final JAXBException e) {
            LOG.error("JAXB context for SOAP model(s) could not be created", e);
            throw new RuntimeException("JAXB context for SOAP model(s) could not be created");
        }

        if (validateMessages) {
            schema = generateTopLevelSchema(String.join(SCHEMA_DELIM, schemas));
        } else {
            schema = null;
        }
    }

    /**
     * Takes a SOAP envelope and marshals it.
     *
     * @param envelope     the source envelope to marshal
     * @param outputStream the destination of the marshalled data
     * @throws JAXBException if marshalling fails
     */
    public void marshal(final JAXBElement<Envelope> envelope, final OutputStream outputStream) throws JAXBException {
        final Marshaller marshaller = jaxbContext.createMarshaller();
        if (schema != null) {
            marshaller.setSchema(schema);
        }
        marshaller.marshal(envelope, outputStream);
    }

    /**
     * Takes an InputStream and unmarshals it.
     *
     * @param inputStream the inputStream to unmarshal
     * @return an Envelope created from the inputstream
     * @throws JAXBException      if marshalling fails
     * @throws ClassCastException if casting to Envelope fails
     */
    public Envelope unmarshal(final InputStream inputStream) throws JAXBException {
        return unmarshalToGeneric(inputStream, Envelope.class);
    }

    /**
     * Takes an InputStream and unmarshals it.
     *
     * @param inputStream the inputStream to unmarshal
     * @param clazz       the class to cast the unmarshalled object to
     * @param <T>         the type of the class
     * @return an object of the give class, created from the input stream
     * @throws JAXBException      if marshalling fails
     * @throws ClassCastException if casting fails
     */
    @SuppressWarnings("unchecked")
    public <T> T unmarshalToGeneric(final InputStream inputStream, final Class<T> clazz) throws JAXBException {
        final Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
        if (schema != null) {
            unmarshaller.setSchema(schema);
        }
        try {
            return ((JAXBElement<T>) unmarshaller.unmarshal(inputStream)).getValue();
        } catch (final ClassCastException e) {
            LOG.error("Could not cast unmarshalled object to {}", clazz.getSimpleName(), e);
            throw e;
        }
    }

    private Schema generateTopLevelSchema(final String schemaPath)
            throws SAXException, IOException, ParserConfigurationException {
        final var topLevelSchemaBeginning =
                "<xsd:schema xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" elementFormDefault=\"qualified\">";
        final var importPattern = "<xsd:import namespace=\"%s\" schemaLocation=\"%s\"/>";
        final var topLevelSchemaEnd = "</xsd:schema>";

        final var stringBuilder = new StringBuilder();
        stringBuilder.append(topLevelSchemaBeginning);
        for (final String path : schemaPath.split(SCHEMA_DELIM)) {
            final var classLoader = getClass().getClassLoader();
            final var schemaUrl = classLoader.getResource(path);
            if (schemaUrl == null) {
                LOG.error("Could not find schema for resource: {}", path);
                throw new IOException(String.format(
                        "Could not find schema for resource while loading in %s: %s",
                        SoapMarshalling.class.getSimpleName(), path));
            }
            final var targetNamespace = resolveTargetNamespace(schemaUrl);
            LOG.info("Register namespace for validation: {}, read from {}", targetNamespace, schemaUrl.toString());
            stringBuilder.append(String.format(importPattern, targetNamespace, schemaUrl.toString()));
        }
        stringBuilder.append(topLevelSchemaEnd);
        final SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        return schemaFactory.newSchema(new StreamSource(
                new ByteArrayInputStream(stringBuilder.toString().getBytes(StandardCharsets.UTF_8))));
    }

    private String resolveTargetNamespace(final URL url)
            throws IOException, ParserConfigurationException, SAXException {
        try (final InputStream inputStream = url.openStream()) {
            final var factory = DocumentBuilderFactory.newInstance();
            final var builder = factory.newDocumentBuilder();
            final var document = builder.parse(inputStream);
            return document.getDocumentElement().getAttribute("targetNamespace");
        }
    }
}
