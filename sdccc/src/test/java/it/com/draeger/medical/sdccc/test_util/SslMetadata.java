/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package it.com.draeger.medical.sdccc.test_util;

import com.google.common.util.concurrent.AbstractIdleService;
import com.google.common.util.concurrent.Service;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.crypto.util.PrivateKeyFactory;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DefaultDigestAlgorithmIdentifierFinder;
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.bc.BcRSAContentSignerBuilder;
import org.somda.sdc.dpws.crypto.CryptoSettings;

/**
 * SSL metadata used for crypto in integration tests.
 *
 * <p>
 * Provides key stores and trust stores for client and device side in-memory.
 */
public class SslMetadata extends AbstractIdleService implements Service {
    @Nullable
    private KeySet serverKeySet;

    @Nullable
    private KeySet clientKeySet;

    /**
     * SSL metadata.
     */
    public SslMetadata() {
        serverKeySet = null;
        clientKeySet = null;
    }

    @Override
    protected void startUp()
            throws NoSuchAlgorithmException, CertificateException, KeyStoreException, IOException,
                    OperatorCreationException {

        Security.addProvider(new BouncyCastleProvider());

        final String commonPassword = "secret";

        final KeyPurposeId[] ekuId = {KeyPurposeId.id_kp_serverAuth, KeyPurposeId.id_kp_clientAuth};
        final ExtendedKeyUsage extendedKeyUsage = new ExtendedKeyUsage(ekuId);

        final String serverAlias = "test-device";
        final String clientAlias = "test-client";

        final KeyPair serverKeyPair = generateKeyPair();
        final KeyPair clientKeyPair = generateKeyPair();

        final X509Certificate serverCert = generateCertificate("sdc-lite-server.org", serverKeyPair, extendedKeyUsage);
        final X509Certificate clientCert = generateCertificate("sdc-lite-client.org", clientKeyPair, extendedKeyUsage);

        final KeyStore serverKeyStore = createKeyStore(
                serverAlias, serverKeyPair.getPrivate(),
                commonPassword, Collections.singletonList(serverCert));
        final KeyStore clientKeyStore = createKeyStore(
                clientAlias, clientKeyPair.getPrivate(),
                commonPassword, Collections.singletonList(clientCert));

        final KeyStore serverTrustStore = createTrustStore(serverAlias, commonPassword, clientCert, serverCert);
        final KeyStore clientTrustStore = createTrustStore(clientAlias, commonPassword, serverCert, clientCert);

        serverKeySet = new KeySet(serverKeyStore, commonPassword, serverTrustStore, commonPassword);
        clientKeySet = new KeySet(clientKeyStore, commonPassword, clientTrustStore, commonPassword);
    }

    @Override
    protected void shutDown() {}

    public KeySet getServerKeySet() {
        return serverKeySet;
    }

    public KeySet getClientKeySet() {
        return clientKeySet;
    }

    private static KeyPair generateKeyPair() throws NoSuchAlgorithmException {
        final KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(1024);
        return keyPairGenerator.generateKeyPair();
    }

    private static KeyStore createKeyStore(
            final String alias,
            final PrivateKey privateKey,
            final String password,
            final List<X509Certificate> certificateChain)
            throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException {

        final KeyStore keyStore = KeyStore.getInstance("jks");
        keyStore.load(null, password.toCharArray());

        final X509Certificate[] certificates = certificateChain.toArray(new X509Certificate[0]);
        keyStore.setKeyEntry(alias, privateKey, password.toCharArray(), certificates);
        return keyStore;
    }

    private static KeyStore createTrustStore(
            final String alias, final String password, final X509Certificate... trustedCertificates)
            throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException {

        final KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, password.toCharArray());
        var i = 0;
        for (var cert : trustedCertificates) {
            keyStore.setCertificateEntry(alias + i++, cert);
        }
        return keyStore;
    }

    private static X509Certificate generateCertificate(
            final String issuer, final KeyPair keyPair, final ExtendedKeyUsage extendedKeyUsage)
            throws OperatorCreationException, IOException, CertificateException {

        final SubjectPublicKeyInfo subPubKeyInfo =
                SubjectPublicKeyInfo.getInstance(keyPair.getPublic().getEncoded());
        final AlgorithmIdentifier sigAlgId =
                new DefaultSignatureAlgorithmIdentifierFinder().find("SHA256WithRSAEncryption");
        final AlgorithmIdentifier digAlgId = new DefaultDigestAlgorithmIdentifierFinder().find(sigAlgId);
        final AsymmetricKeyParameter privateKeyAsymKeyParam =
                PrivateKeyFactory.createKey(keyPair.getPrivate().getEncoded());
        final ContentSigner sigGen = new BcRSAContentSignerBuilder(sigAlgId, digAlgId).build(privateKeyAsymKeyParam);

        // generate the certificate
        final X509v3CertificateBuilder certGen = new X509v3CertificateBuilder(
                new X500Name("CN=" + issuer),
                BigInteger.valueOf(System.currentTimeMillis()),
                new Date(System.currentTimeMillis() - 500000),
                new Date(System.currentTimeMillis() + 500000),
                new X500Name("CN=" + issuer),
                subPubKeyInfo);

        certGen.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
        certGen.addExtension(
                Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment));
        certGen.addExtension(
                Extension.subjectAlternativeName,
                false,
                new GeneralNames(new GeneralName(GeneralName.rfc822Name, "janlukas.deichmann@draeger.com")));
        certGen.addExtension(Extension.extendedKeyUsage, true, extendedKeyUsage);

        final var certificateHolder = certGen.build(sigGen);

        return new JcaX509CertificateConverter().setProvider("BC").getCertificate(certificateHolder);
    }

    /**
     * Convert a keystore to a byte array.
     *
     * @param keyStore to convert
     * @param password of the store
     * @return byte array representing the key store
     * @throws CertificateException     on certificate error
     * @throws NoSuchAlgorithmException on certificate error
     * @throws KeyStoreException        on certificate error
     * @throws IOException              on certificate error
     */
    public static byte[] convertToByteArray(final KeyStore keyStore, final String password)
            throws CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException {
        try (final ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            keyStore.store(bos, password.toCharArray());
            return bos.toByteArray();
        }
    }

    /**
     * Converts a keyset to {@linkplain CryptoSettings}.
     *
     * @param keySet to convert
     * @return matching crypto settings
     */
    public static CryptoSettings getCryptoSettings(final SslMetadata.KeySet keySet) {
        final byte[] keyStoreBytes;
        final byte[] trustStoreBytes;
        try {
            keyStoreBytes = SslMetadata.convertToByteArray(keySet.getKeyStore(), keySet.getKeyStorePassword());
            trustStoreBytes = SslMetadata.convertToByteArray(keySet.getTrustStore(), keySet.getTrustStorePassword());
        } catch (final IOException | KeyStoreException | NoSuchAlgorithmException | CertificateException e) {
            throw new RuntimeException(e);
        }

        return new MyCryptoSettings(keyStoreBytes, keySet, trustStoreBytes);
    }

    /**
     * Container class for generated certificates.
     */
    public static class KeySet {
        private final KeyStore keyStore;
        private final String keyStorePassword;

        private final KeyStore trustStore;
        private final String trustStorePassword;

        /**
         * Generated key set container.
         *
         * @param keyStore           keystore
         * @param keyStorePassword   keystore password
         * @param trustStore         truststore
         * @param trustStorePassword truststore password
         */
        public KeySet(
                final KeyStore keyStore,
                final String keyStorePassword,
                final KeyStore trustStore,
                final String trustStorePassword) {
            this.keyStore = keyStore;
            this.keyStorePassword = keyStorePassword;
            this.trustStore = trustStore;
            this.trustStorePassword = trustStorePassword;
        }

        public KeyStore getKeyStore() {
            return keyStore;
        }

        public String getKeyStorePassword() {
            return keyStorePassword;
        }

        public KeyStore getTrustStore() {
            return trustStore;
        }

        public String getTrustStorePassword() {
            return trustStorePassword;
        }
    }

    private static final class MyCryptoSettings implements CryptoSettings {
        private final byte[] finalKeyStoreBytes;
        private final KeySet keySet;
        private final byte[] finalTrustStoreBytes;

        private MyCryptoSettings(
                final byte[] finalKeyStoreBytes, final KeySet keySet, final byte[] finalTrustStoreBytes) {
            this.finalKeyStoreBytes = finalKeyStoreBytes;
            this.keySet = keySet;
            this.finalTrustStoreBytes = finalTrustStoreBytes;
        }

        @Override
        public Optional<InputStream> getKeyStoreStream() {
            return Optional.of(new ByteArrayInputStream(finalKeyStoreBytes));
        }

        @Override
        public String getKeyStorePassword() {
            return keySet.getKeyStorePassword();
        }

        @Override
        public Optional<InputStream> getTrustStoreStream() {
            return Optional.of(new ByteArrayInputStream(finalTrustStoreBytes));
        }

        @Override
        public String getTrustStorePassword() {
            return keySet.getTrustStorePassword();
        }
    }
}
