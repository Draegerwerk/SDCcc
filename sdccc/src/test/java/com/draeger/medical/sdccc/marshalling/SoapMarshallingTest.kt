/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2025 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.marshalling

import com.draeger.medical.biceps.model.extension.ExtensionType
import com.draeger.medical.dpws.soap.model.Envelope
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.w3c.dom.Element
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/**
 * Unit tests for the soap marshalling.
 */
class SoapMarshallingTest {
    private lateinit var soapMarshaller: SoapMarshalling

    @BeforeEach
    fun setup() {
        val marshallingInjector = MarshallingUtil.createMarshallingTestInjector(true)
        soapMarshaller = marshallingInjector.getInstance(SoapMarshalling::class.java)
    }

    /**
     * Test the unmarshalling with Envelop return type.
     */
    @Test
    fun testUnmarshal() {
        val rawEnvelope = SoapMarshallingTest::class.java.getResourceAsStream("SoapMarshallingTestData.xml")
        assertNotNull(rawEnvelope, "The test data is not present.")

        val unmarshaledEnvelope = soapMarshaller.unmarshal(rawEnvelope)

        assertEquals(
            Envelope::class.java,
            unmarshaledEnvelope::class.java,
            "The unmarshalled data is not of the expected type."
        )
    }

    /**
     * Test the unmarshalling with generic return type.
     */
    @Test
    fun testUnmarshalToGeneric() {
        val xml = """
            <ns1:Extension xmlns:ns1="http://standards.ieee.org/downloads/11073/11073-10207-2017/extension">
            <ns2:MyExt xmlns:ns2="http://biceps.extension" ns1:MustUnderstand="true">Youwillnotunderstandthis</ns2:MyExt>
            </ns1:Extension>
        """.trimIndent()

        val unmarshaledExtension = soapMarshaller.unmarshalToGeneric(xml.byteInputStream(), ExtensionType::class.java)

        assertEquals(
            ExtensionType::class.java,
            unmarshaledExtension::class.java,
            "The unmarshaled data is not of the expected type."
        )

        assertEquals(1, unmarshaledExtension.any.size, "The unmarshaled data is not present.")

        val myExt = unmarshaledExtension.any[0]
        assertIs<Element>(myExt)

        assertEquals("http://biceps.extension", myExt.namespaceURI, "namespaceURI is not as expected.")
        assertEquals("MyExt", myExt.localName, "localName is not as expected.")
        assertEquals(EXPECTED_NUMBER_ATTRIBUTES, myExt.attributes.length, "attributes length is not as expected.")

        val mustUndertand = myExt.attributes.getNamedItemNS(
            "http://standards.ieee.org/downloads/11073/11073-10207-2017/extension",
            "MustUnderstand"
        )
        assertNotNull(mustUndertand, "The attribute MustUnderstand is not present.")
        assertEquals("true", mustUndertand.nodeValue, "value of attribute MustUnderstand is not as expected.")
        assertEquals(
            "Youwillnotunderstandthis",
            (myExt.firstChild ?: error("The child element is not expected to be null")).nodeValue,
            "The text of the extension element is not as expected."
        )
    }

    companion object {
        /**
         * Error message for failed assertions.
         */
        const val EXPECTED_NUMBER_ATTRIBUTES = 3
    }
}
