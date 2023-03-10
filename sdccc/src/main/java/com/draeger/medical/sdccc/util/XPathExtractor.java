/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2023 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.util;

import static com.draeger.medical.sdccc.util.Constants.NAMESPACES;

import com.google.common.collect.Lists;
import java.io.IOException;
import java.io.StringReader;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import net.sf.saxon.xpath.XPathFactoryImpl;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * Utility to extract nodes from messages matching a given XPath expression.
 */
public class XPathExtractor {
    private static final Logger LOG = LogManager.getLogger(XPathExtractor.class);
    private final String query;
    private final XPath xpath;

    /**
     * Creates a new {@linkplain XPathExtractor}.
     *
     * @param query the XPath expression to extract
     */
    public XPathExtractor(final String query) {
        this.query = query;
        this.xpath = XPathFactoryImpl.newInstance().newXPath();
        this.xpath.setNamespaceContext(NAMESPACES);
    }

    /**
     * Extract all matching nodes from a given string.
     *
     * @param target string to extract from
     * @return list of all matching {@linkplain Node}s
     * @throws XPathExpressionException if the XPath extraction encountered an error,
     *                                  i.e. when an invalid expression was used
     */
    public Collection<Node> extractFrom(final String target) throws XPathExpressionException {
        return extract(target);
    }

    /**
     * Extract all matching nodes from a given node.
     *
     * @param node node to extract from
     * @return list of all matching {@linkplain Node}s
     * @throws XPathExpressionException if the XPath extraction encountered an error,
     *                                  i.e. when an invalid expression was used
     */
    public Collection<Node> extractFrom(final Node node) throws XPathExpressionException {
        return extract(node);
    }

    private Collection<Node> extract(final Node node) throws XPathExpressionException {
        final NodeList nl = (NodeList) xpath.compile(query).evaluate(node, XPathConstants.NODESET);
        return convert(nl);
    }

    private Collection<Node> extract(final String target) throws XPathExpressionException {
        if (target.isBlank()) {
            return Collections.emptyList();
        }

        // We need to parse the string first. If we don't, the default string parser in xpath
        // logs errors into stderr, which we really do not want or need
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        final DocumentBuilder builder;
        final Document document;
        try {
            builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new ErrorLogger());
            document = builder.parse(new InputSource(new StringReader(target)));
        } catch (final ParserConfigurationException | IOException | SAXException e) {
            throw new XPathExpressionException(e);
        }

        final NodeList nl =
                (NodeList) xpath.compile(query).evaluate(document.getDocumentElement(), XPathConstants.NODESET);
        return convert(nl);
    }

    /**
     * Converts a NodeList instance into an actual list for convenience.
     *
     * @param nodeList to convert
     * @return list containing all elements
     */
    public static List<Node> convert(final NodeList nodeList) {
        final List<Node> result = Lists.newArrayList();
        for (int i = 0; i < nodeList.getLength(); i++) {
            result.add(nodeList.item(i));
        }
        return result;
    }

    /**
     * Custom error handler which does not log into stderr but into log4j2.
     *
     * <p>
     * According to the W3C XML specification, only fatal errors must be reported to the
     * application, while errors and warnings are optionally reported. As such, fatal
     * errors are the only ones logged as warning messages, while error and warning are
     * logged as debug messages.
     * Fatal errors still trigger the {@linkplain XPathExpressionException} in
     * {@linkplain XPathExtractor#extract(String)}, allowing the caller to handle it.
     *
     * @see <a href="https://www.w3.org/TR/xml/#sec-terminology">W3C terminology</a>
     */
    public static class ErrorLogger implements ErrorHandler {

        @Override
        public void warning(final SAXParseException exception) {
            LOG.debug("Warning while parsing document", exception);
        }

        @Override
        public void error(final SAXParseException exception) {
            LOG.debug("Error while parsing document", exception);
        }

        @Override
        public void fatalError(final SAXParseException exception) {
            LOG.warn("Fatal error while parsing document", exception);
        }
    }
}
