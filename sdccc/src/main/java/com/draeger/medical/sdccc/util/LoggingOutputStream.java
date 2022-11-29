/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;

/**
 * A {@linkplain OutputStream} which logs to a log4j2 {@linkplain Logger}.
 */
public class LoggingOutputStream extends OutputStream {

    private final ByteArrayOutputStream memory;
    private final Charset charset;
    private final Level level;
    private final Logger log;

    /**
     * Creates an {@linkplain OutputStream} attached to a {@linkplain Logger}.
     *
     * @param log     the logger to write log messages to
     * @param level   the level log messages should be logged with
     * @param charset the charset of the log messages
     */
    public LoggingOutputStream(final Logger log, final Level level, final Charset charset) {
        this.log = log;
        this.level = level;
        this.charset = charset;
        this.memory = new ByteArrayOutputStream();
    }

    @Override
    public void write(final int b) throws IOException {
        if (b == '\n') {
            this.flush();
        } else {
            this.memory.write(b);
        }
    }

    @Override
    public void flush() throws IOException {
        log.log(level, this.memory.toString(charset));
        this.memory.reset();
    }
}
