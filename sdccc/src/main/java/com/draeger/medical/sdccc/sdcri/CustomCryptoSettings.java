/*
 * This Source Code Form is subject to the terms of the "SDCcc non-commercial use license".
 *
 * Copyright (C) 2025 Draegerwerk AG & Co. KGaA
 */

package com.draeger.medical.sdccc.sdcri;

import com.draeger.medical.sdccc.configuration.TestSuiteConfig;
import com.draeger.medical.sdccc.util.Constants;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.Collection;
import java.util.Optional;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JceOpenSSLPKCS8DecryptorProviderBuilder;
import org.bouncycastle.operator.InputDecryptorProvider;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo;
import org.bouncycastle.pkcs.PKCSException;
import org.somda.sdc.dpws.crypto.CryptoSettings;

/**
 * Cryptography configuration to be used by SDCri participants.
 */
@Singleton
public class CustomCryptoSettings implements CryptoSettings {

    private static final String ERR_MSG_KEYSTORE_MULTIPLE = "Too many files for authentication were found,";
    private static final String ERR_MSG_KEYSTORE_MISSING = "Not all files needed for authentication were found,";
    private static final String ERR_MSG_KEYSTORE_BASE = "%s make sure that either only one keystore file with the name:"
            + " %s or else only a public key and a private key with the names: %s and %s exist in the specified file"
            + " directory.";

    private static final String ERR_MSG_TRUSTSTORE_MULTIPLE =
            "Too many files for the authentication of the device were" + " found,";
    private static final String ERR_MSG_TRUSTSTORE_MISSING =
            "Not all files needed for the authentication of the device" + " were found,";
    private static final String ERR_MSG_TRUSTSTORE_BASE = "%s make sure that either only one truststore file named: %s,"
            + "or else only a certificate authority file named: %s is present in the specified file directory.";

    private final byte[] keyStore;
    private final String keyStorePassword;
    private final byte[] trustStore;
    private final String trustStorePassword;

    /**
     * Creates a configuration instance.
     *
     * @param fileDirectory              directory where crypto files are located
     * @param keyStorePassword           keystore password
     * @param trustStorePassword         truststore password
     * @param participantPrivatePassword private key password
     */
    @Inject
    CustomCryptoSettings(
            @Named(TestSuiteConfig.FILE_DIRECTORY) final String fileDirectory,
            @Named(TestSuiteConfig.KEY_STORE_PASSWORD) final String keyStorePassword,
            @Named(TestSuiteConfig.TRUST_STORE_PASSWORD) final String trustStorePassword,
            @Named(TestSuiteConfig.PARTICIPANT_PRIVATE_PASSWORD) final String participantPrivatePassword)
            throws IOException {
        this.keyStorePassword = keyStorePassword;
        this.trustStorePassword = trustStorePassword;

        final Optional<ByteArrayInputStream> participantPrivateFile =
                retrieveFileAsInputStream(fileDirectory, Constants.PARTICIPANT_PRIVATE);
        final Optional<ByteArrayInputStream> participantPublicFile =
                retrieveFileAsInputStream(fileDirectory, Constants.PARTICIPANT_PUBLIC);
        final Optional<ByteArrayInputStream> caCertFile =
                retrieveFileAsInputStream(fileDirectory, Constants.CA_CERTIFICATE);
        final var keystoreFile =
                new File(Path.of(fileDirectory, Constants.KEYSTORE).toString());
        final var truststoreFile =
                new File(Path.of(fileDirectory, Constants.TRUSTSTORE).toString());

        if (keystoreFile.exists() && participantPublicFile.isPresent() && participantPrivateFile.isPresent()) {
            throw new RuntimeException(String.format(
                    ERR_MSG_KEYSTORE_BASE,
                    ERR_MSG_KEYSTORE_MULTIPLE,
                    Constants.KEYSTORE,
                    Constants.PARTICIPANT_PUBLIC,
                    Constants.PARTICIPANT_PRIVATE));
        } else if (truststoreFile.exists() && caCertFile.isPresent()) {
            throw new RuntimeException(String.format(
                    ERR_MSG_TRUSTSTORE_BASE,
                    ERR_MSG_TRUSTSTORE_MULTIPLE,
                    Constants.TRUSTSTORE,
                    Constants.CA_CERTIFICATE));
        }

        if (keystoreFile.exists()) {
            keyStore = Files.readAllBytes(keystoreFile.toPath());
        } else {
            if (participantPrivateFile.isPresent() && participantPublicFile.isPresent()) {
                keyStore = buildKeyStore(
                        participantPrivateFile.orElseThrow(),
                        participantPublicFile.orElseThrow(),
                        participantPrivatePassword);
            } else {
                throw new RuntimeException(String.format(
                        ERR_MSG_KEYSTORE_BASE,
                        ERR_MSG_KEYSTORE_MISSING,
                        Constants.KEYSTORE,
                        Constants.PARTICIPANT_PUBLIC,
                        Constants.PARTICIPANT_PRIVATE));
            }
        }

        if (truststoreFile.exists()) {
            trustStore = Files.readAllBytes(truststoreFile.toPath());
        } else {
            if (caCertFile.isPresent()) {
                trustStore = buildTrustStore(caCertFile.orElseThrow(), trustStorePassword);
            } else {
                throw new RuntimeException(String.format(
                        ERR_MSG_TRUSTSTORE_BASE,
                        ERR_MSG_TRUSTSTORE_MISSING,
                        Constants.TRUSTSTORE,
                        Constants.CA_CERTIFICATE));
            }
        }
    }

    private byte[] buildKeyStore(
            final ByteArrayInputStream privateKeyFileStream,
            final ByteArrayInputStream certFileStream,
            final String privateKeyPassword) {
        final var userCerts = loadCertificates(certFileStream);
        final KeyStore buildKeyStore = retrieveKeyStoreInstance();
        final PrivateKey userKey;

        try {
            userKey = getPrivateKey(privateKeyFileStream, privateKeyPassword);
            buildKeyStore.setKeyEntry("key", userKey, keyStorePassword.toCharArray(), userCerts);
        } catch (IOException e) {
            throw new RuntimeException("Error retrieving private key", e);
        } catch (KeyStoreException e) {
            throw new RuntimeException("Error loading certificate into keystore instance", e);
        }

        final var keyStoreOutputStream = convertKeyStoreToStream(buildKeyStore, keyStorePassword);
        return keyStoreOutputStream.toByteArray();
    }

    private byte[] buildTrustStore(final ByteArrayInputStream caCertFilePath, final String password) {
        final var caCerts = loadCertificates(caCertFilePath);

        final KeyStore buildTrustStore = retrieveKeyStoreInstance();
        try {
            for (int i = 0; i < caCerts.length; i++) {
                buildTrustStore.setCertificateEntry(String.format("ca%s", i), caCerts[i]);
            }
        } catch (KeyStoreException e) {
            throw new RuntimeException("Error loading certificate into keystore instance", e);
        }

        final var trustStoreOutputStream = convertKeyStoreToStream(buildTrustStore, password);
        return trustStoreOutputStream.toByteArray();
    }

    private Optional<ByteArrayInputStream> retrieveFileAsInputStream(
            final String pathToDirectory, final String fileName) {
        final byte[] file;
        try {
            file = Files.readAllBytes(Path.of(pathToDirectory, fileName));
        } catch (IOException e) {
            return Optional.empty();
        }
        return Optional.of(new ByteArrayInputStream(file));
    }

    private Certificate[] loadCertificates(final ByteArrayInputStream certStream) {
        final Collection<Certificate> certificates;
        try {
            final var cf = CertificateFactory.getInstance("X.509");
            certificates = (Collection<Certificate>) cf.generateCertificates(certStream);
        } catch (CertificateException e) {
            throw new RuntimeException("Specified certificate file could not be loaded", e);
        }
        return certificates.toArray(new Certificate[0]);
    }

    private KeyStore retrieveKeyStoreInstance() {
        Security.addProvider(new BouncyCastleProvider());

        final KeyStore buildKeyStore;
        try {
            buildKeyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            buildKeyStore.load(null);
        } catch (CertificateException | NoSuchAlgorithmException | IOException | KeyStoreException e) {
            throw new RuntimeException("Error creating keystore instance", e);
        }
        return buildKeyStore;
    }

    private static PrivateKey getPrivateKey(final ByteArrayInputStream key, final String password) throws IOException {
        final PEMParser pp = new PEMParser(new BufferedReader(new InputStreamReader(key, StandardCharsets.UTF_8)));
        final var parsedObject = pp.readObject();
        pp.close();
        final JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider(new BouncyCastleProvider());

        if (parsedObject instanceof PKCS8EncryptedPrivateKeyInfo) {
            final var pemKey = (PKCS8EncryptedPrivateKeyInfo) parsedObject;
            final InputDecryptorProvider pkcs8Prov;
            try {
                pkcs8Prov = new JceOpenSSLPKCS8DecryptorProviderBuilder().build(password.toCharArray());
            } catch (OperatorCreationException e) {
                throw new IOException(e);
            }

            final PrivateKeyInfo decrypted;
            try {
                decrypted = pemKey.decryptPrivateKeyInfo(pkcs8Prov);
            } catch (PKCSException e) {
                throw new IOException(e);
            }
            return converter.getPrivateKey(decrypted);
        } else {
            final var pemKey = (PEMKeyPair) parsedObject;
            return converter.getPrivateKey(pemKey.getPrivateKeyInfo());
        }
    }

    private ByteArrayOutputStream convertKeyStoreToStream(final KeyStore store, final String password) {
        final var keyStoreOutputStream = new ByteArrayOutputStream();
        try {
            store.store(keyStoreOutputStream, password.toCharArray());
        } catch (KeyStoreException | IOException | NoSuchAlgorithmException | CertificateException e) {
            throw new RuntimeException("Error converting keystore to stream", e);
        }
        return keyStoreOutputStream;
    }

    @Override
    public Optional<InputStream> getKeyStoreStream() {
        return Optional.of(new ByteArrayInputStream(keyStore));
    }

    @Override
    public String getKeyStorePassword() {
        return keyStorePassword;
    }

    @Override
    public Optional<InputStream> getTrustStoreStream() {
        return Optional.of(new ByteArrayInputStream(trustStore));
    }

    @Override
    public String getTrustStorePassword() {
        return trustStorePassword;
    }
}
