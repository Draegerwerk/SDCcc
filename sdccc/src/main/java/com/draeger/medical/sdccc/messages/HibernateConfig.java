/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2023 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.messages;

import org.hibernate.HibernateException;
import org.hibernate.cfg.Configuration;

/**
 * Interface for providing hibernate configurations.
 */
public interface HibernateConfig extends AutoCloseable {

    /**
     * @return the hibernate configuration.
     */
    Configuration getConfiguration();

    /**
     * @return size of jdbc batches for insertion into the database
     */
    int getInsertBatchSize();

    @Override
    void close() throws HibernateException;
}
