/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import javax.xml.xpath.XPathExpressionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the {@linkplain XPathExtractor}.
 */
public class XPathExtractorTest {

    /**
     * Tests whether an invalid XML input triggers an {@linkplain XPathExpressionException}.
     */
    @Test
    @DisplayName("Invalid xml causes exception")
    void extractFromInvalidMessage() {
        final var invalidMessage = "lolinvalid";
        final var extractor = new XPathExtractor("//someElement");
        assertThrows(XPathExpressionException.class, () -> extractor.extractFrom(invalidMessage));
    }

    /**
     * Tests whether extraction from string inputs is namespace aware.
     *
     * <p>
     * Note: the default behavior of the Java document parser used is to discard namespaces.
     *
     * @throws XPathExpressionException on any error
     */
    @Test
    @DisplayName("Strings are parsed namespace aware, allowing XPaths with namespaces")
    void extractNamespaceAware() throws XPathExpressionException {
        final var message = "<f:table xmlns:f=\"https://www.w3schools.com/furniture\">\n"
                + "  <f:name>African Coffee Table</f:name>\n"
                + "  <f:width>80</f:width>\n"
                + "  <f:length>120</f:length>\n"
                + "</f:table>";

        final var extractor = new XPathExtractor(
                "//*[local-name()='width' and namespace-uri()='https://www.w3schools.com/furniture']");

        final var result = extractor.extractFrom(message);

        assertEquals(1, result.size());
        assertEquals("80", result.stream().findFirst().orElseThrow().getTextContent());
    }
}
