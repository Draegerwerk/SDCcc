/*
 * This Source Code Form is subject to the terms of the "SDCcc non-commercial use license".
 *
 * Copyright (C) 2025 Draegerwerk AG & Co. KGaA
 */

package com.draeger.medical.sdccc.messages.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Utilities for handling of {@linkplain com.draeger.medical.sdccc.messages.Message}s.
 */
public final class MessageUtil {
    private static final Logger LOG = LogManager.getLogger(MessageUtil.class);

    private static final int BYTE_SIZE = 0xff;

    private MessageUtil() {}

    /**
     * Hashes a string using the SHA-256 algorithm.
     *
     * @param data string to hash
     * @return hex representation of the SHA-256 hash
     */
    public static String hashMessage(final String data) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] encodedHash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(encodedHash);
        } catch (final NoSuchAlgorithmException e) {
            LOG.error("Error while initializing message digest", e);
            throw new RuntimeException(e);
        }
    }

    private static String bytesToHex(final byte[] hash) {
        final StringBuilder hexString = new StringBuilder();
        for (final byte b : hash) {
            final String hex = Integer.toHexString(BYTE_SIZE & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
