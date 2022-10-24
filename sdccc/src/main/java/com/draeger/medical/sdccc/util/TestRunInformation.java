/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.util;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Utility to store information for the test run.
 */
@Singleton
public class TestRunInformation {
    private static final Logger LOG = LogManager.getLogger();

    private boolean archiveServicePresent;

    @Inject
    TestRunInformation() {
        this.archiveServicePresent = false;
    }

    /**
     * @return true if the DUT has an archive service
     */
    public boolean hasArchiveService() {
        return archiveServicePresent;
    }

    /**
     * Set whether the DUT has an archive service.
     *
     * @param archiveServicePresent presence of archive service
     */
    public void setArchiveServicePresent(final boolean archiveServicePresent) {
        LOG.debug("archiveServicePresent set to {}", archiveServicePresent);
        this.archiveServicePresent = archiveServicePresent;
    }
}
