package com.draeger.medical.sdccc.tests.util;

import java.io.IOException;
import java.io.StringWriter;
import java.security.cert.X509Certificate;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;

/**
 * Utility to process the custom crypto settings.
 */
public final class CryptoUtil {

    private CryptoUtil() {}

    public static String getCertificateAsPEMString(final X509Certificate cert) throws IOException {
        final StringWriter writer = new StringWriter();
        final JcaPEMWriter pemWriter = new JcaPEMWriter(writer);
        pemWriter.writeObject(cert);
        pemWriter.flush();
        pemWriter.close();
        return writer.toString();
    }
}
