/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.messages;

import com.draeger.medical.sdccc.messages.mapping.ManipulationData;
import com.draeger.medical.sdccc.messages.mapping.ManipulationParameter;
import com.draeger.medical.sdccc.messages.mapping.MdibVersionGroupEntity;
import com.draeger.medical.sdccc.messages.mapping.MessageContent;
import com.draeger.medical.sdccc.messages.mapping.StringEntryEntity;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import javax.inject.Singleton;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.Environment;

/**
 * Hibernate base configuration.
 */
@Singleton
public class HibernateConfigBase implements HibernateConfig {
    private static final Logger LOG = LogManager.getLogger(HibernateConfigBase.class);
    private static final String FALSE_SETTING_VALUE = "false";
    private static final String TRUE_SETTING_VALUE = "true";

    private static final int INSERT_BATCH_SIZE = 20;
    private static final int POOL_SIZE = Runtime.getRuntime().availableProcessors() * 10;

    private final String baseUrl;

    /**
     * Creates a hibernate configuration storing the database at the specified location.
     *
     * @param derbyUrl location to store the database at
     */
    public HibernateConfigBase(final String derbyUrl) {
        this.baseUrl = "jdbc:derby:" + derbyUrl;
        Configurator.setLevel("org.hibernate", Level.ERROR);
    }

    @Override
    public int getInsertBatchSize() {
        return INSERT_BATCH_SIZE;
    }

    @Override
    public Configuration getConfiguration() {
        final var config = new Configuration();

        config.setProperty(Environment.POOL_SIZE, String.valueOf(POOL_SIZE));
        config.setProperty(Environment.DIALECT, "org.hibernate.dialect.DerbyTenFiveDialect");
        config.setProperty(Environment.SHOW_SQL, FALSE_SETTING_VALUE);
        config.setProperty(Environment.HBM2DDL_AUTO, "create");
        config.setProperty(Environment.USE_NEW_ID_GENERATOR_MAPPINGS, FALSE_SETTING_VALUE);
        config.setProperty(Environment.URL, this.baseUrl + ";create=True");

        config.setProperty(Environment.STATEMENT_BATCH_SIZE, String.valueOf(this.getInsertBatchSize()));
        config.setProperty(Environment.ORDER_UPDATES, TRUE_SETTING_VALUE);
        config.setProperty(Environment.ORDER_INSERTS, TRUE_SETTING_VALUE);
        config.setProperty(Environment.BATCH_VERSIONED_DATA, TRUE_SETTING_VALUE);

        config.addAnnotatedClass(StringEntryEntity.class);
        config.addAnnotatedClass(MdibVersionGroupEntity.class);
        config.addAnnotatedClass(MessageContent.class);
        config.addAnnotatedClass(ManipulationParameter.class);
        config.addAnnotatedClass(ManipulationData.class);
        return config;
    }

    @Override
    public void close() {
        try {
            final Connection connection = DriverManager.getConnection(this.baseUrl + ";shutdown=true");
            connection.close();

            LOG.error("unable to close database with base url {}", this.baseUrl);
        } catch (final SQLException e) {
            LOG.trace("the following SQLException occurred during database shutdown", e);
            LOG.debug("successfully closed the database with the base url {}", this.baseUrl);
        }
    }
}
