/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2023 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.util;

import java.io.IOException;
import java.io.InputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

/**
 * Utility which provides test related functionality for certificates.
 */
public final class CertificateUtil {

    private CertificateUtil() {}

    /**
     * Provides a test certificate to be used in unit tests.
     *
     * @return a test certificate
     * @throws CertificateException on parsing errors
     * @throws IOException          on errors while closing the certificate file
     */
    public static X509Certificate getDummyCert() throws CertificateException, IOException {
        final CertificateFactory factory = CertificateFactory.getInstance("X.509");
        try (final InputStream inputStream = CertificateUtil.class.getResourceAsStream("dummy_cert.pem")) {
            return (X509Certificate) factory.generateCertificate(inputStream);
        }
    }
}
