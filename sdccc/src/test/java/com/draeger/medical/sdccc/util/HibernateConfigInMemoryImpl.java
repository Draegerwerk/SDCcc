/*
 * This Source Code Form is subject to the terms of the "SDCcc non-commercial use license".
 *
 * Copyright (C) 2025 Draegerwerk AG & Co. KGaA
 */

package com.draeger.medical.sdccc.util;

import com.draeger.medical.sdccc.messages.HibernateConfigBase;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.UUID;

/**
 * Hibernate configuration using an in memory database.
 */
@Singleton
public class HibernateConfigInMemoryImpl extends HibernateConfigBase {
    @Inject
    HibernateConfigInMemoryImpl() {
        super("memory:" + UUID.randomUUID().toString());
    }
}
