/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2023 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.util;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import javax.xml.namespace.NamespaceContext;

/**
 * A simple implementation of {@linkplain NamespaceContext} to allow XPath extraction.
 */
public class SimpleNamespaceContext implements NamespaceContext {

    private final Map<String, String> prefixMap;

    /**
     * Creates a simple {@linkplain NamespaceContext}.
     *
     * @param prefixMap {@linkplain Map} where a key is a prefix and the value is the namespace uri
     */
    public SimpleNamespaceContext(final Map<String, String> prefixMap) {
        this.prefixMap = new HashMap<>(prefixMap);
    }

    public String getNamespaceURI(final String prefix) {
        return prefixMap.get(prefix);
    }

    // these operations are not required for XPath extraction
    public String getPrefix(final String uri) {
        throw new UnsupportedOperationException();
    }

    public Iterator<String> getPrefixes(final String namespaceURI) {
        throw new UnsupportedOperationException();
    }
}
