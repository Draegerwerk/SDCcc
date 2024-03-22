/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2023 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.messages;

import com.draeger.medical.sdccc.configuration.TestRunConfig;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.io.File;
import java.nio.file.Path;

/**
 * Hibernate configuration using a file based backend.
 */
@Singleton
public class HibernateConfigImpl extends HibernateConfigBase {
    @Inject
    HibernateConfigImpl(@Named(TestRunConfig.TEST_RUN_DIR) final File dir) {
        super(Path.of(dir.getAbsolutePath(), "Database").toString());
    }
}
