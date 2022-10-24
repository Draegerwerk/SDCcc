/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.configuration;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.somda.sdc.common.guice.AbstractConfigurationModule;

import javax.annotation.Nullable;
import java.io.File;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Configuration module, that creates a directory for the test run results and binds its file representation.
 */
public class TestRunConfig extends AbstractConfigurationModule {
    public static final String TEST_RUN_DIR = "SDCcc.TestRunDir";
    private static final Logger LOG = LogManager.getLogger();

    private final File testRunDir;

    /**
     * @param testRunDir directory to store test results and logs into
     */
    public TestRunConfig(final File testRunDir) {
        this.testRunDir = testRunDir;
    }

    @Override
    protected void defaultConfigure() {
        this.bind(TestRunConfig.TEST_RUN_DIR, File.class, testRunDir);
    }

    /**
     * Creates a test run directory with a timestamp in the folder name.
     *
     * <p>
     * Uses the current working directory by default, unless a custom base directory is provided.
     * Also creates the 'testruns' parent directory if it doesn't exist when using default directory.
     *
     * @param baseDirectory directory to create test run dirs in, working directory if null
     * @return File pointing to the new directory.
     * @throws RuntimeException in case directory could not be created or already exists
     */
    public static File createTestRunDirectory(final @Nullable String baseDirectory) {
        // create test run dir and bind it as config parameter
        final String testRunTimestamp = ZonedDateTime.now(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_DATE_TIME)
                .replace(":", "-");

        final String testRunName = "SDCcc_Testrun_" + testRunTimestamp;
        return createTestRunDirectory(baseDirectory, testRunName);
    }

    private static File createTestRunDirectory(final @Nullable String baseDirectory, final String testRunName) {

        // create test run root
        final Path dirPath;
        if (baseDirectory != null) {
            dirPath = Path.of(baseDirectory, testRunName);
        } else {
            dirPath = Path.of(System.getProperty("user.dir"), "testruns", testRunName);
        }
        final File runDir = dirPath.toFile();
        if (runDir.exists()) {
            if (!runDir.mkdir()) {
                throw new RuntimeException("Directory for test result data compromised.");
            }
        }
        if (!runDir.exists()) {
            if (!runDir.mkdirs()) {
                throw new RuntimeException("Could not create directory for test result data");
            }
        }

        return runDir;
    }
}
