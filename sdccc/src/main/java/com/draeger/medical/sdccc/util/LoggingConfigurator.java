/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2023, 2024 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.util;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.config.DefaultConfiguration;
import org.apache.logging.log4j.core.config.builder.api.AppenderComponentBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilderFactory;
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration;

/**
 * Provides the configuration for the SDCcc log4j2 Logger.
 */
public final class LoggingConfigurator {

    private static final List<String> CHATTY_LOGGERS =
            List.of("org.apache.http.wire", "org.apache.http.headers", "org.eclipse.jetty");

    private LoggingConfigurator() {}

    /**
     * Generates a logger configuration storing the log in the given folder.
     *
     * @param loggingFolder to store log in
     * @param fileLogLevel log level to use for the log file
     * @return new configuration
     */
    public static BuiltConfiguration loggerConfig(final File loggingFolder, final Level fileLogLevel) {
        final ConfigurationBuilder<BuiltConfiguration> builder = ConfigurationBuilderFactory.newConfigurationBuilder();
        builder.setStatusLevel(Level.ERROR);
        builder.setConfigurationName("SDCcc");

        final var layoutBuilder =
                builder.newLayout("PatternLayout").addAttribute("pattern", DefaultConfiguration.DEFAULT_PATTERN);

        final var rootLogger = builder.newAsyncRootLogger(Level.DEBUG);
        final var sdcccLogger = builder.newAsyncLogger("com.draeger.medical.sdccc", Level.DEBUG)
                // don't inherit root logger appenders
                .addAttribute("additivity", false);

        final AppenderComponentBuilder triggerOnErrorOrWorseLogAppenderBuilder =
                builder.newAppender("triggerOnErrorOrWorse", "TriggerOnErrorOrWorseLogAppender");

        builder.add(triggerOnErrorOrWorseLogAppenderBuilder);
        sdcccLogger.add(builder.newAppenderRef(triggerOnErrorOrWorseLogAppenderBuilder.getName()));
        rootLogger.add(builder.newAppenderRef(triggerOnErrorOrWorseLogAppenderBuilder.getName()));
        {
            // create a console appender for info messages
            final var appenderBuilder = builder.newAppender("console_logger", ConsoleAppender.PLUGIN_NAME)
                    .addAttribute("target", ConsoleAppender.Target.SYSTEM_OUT);
            appenderBuilder.add(layoutBuilder);
            // only log INFO or worse to console
            appenderBuilder.addComponent(builder.newFilter("ThresholdFilter", Filter.Result.ACCEPT, Filter.Result.DENY)
                    .addAttribute("level", Level.INFO));
            builder.add(appenderBuilder);

            sdcccLogger.add(builder.newAppenderRef(appenderBuilder.getName()));
        }
        {
            // create a console appender for error messages
            final var appenderBuilder = builder.newAppender("console_warning_logger", ConsoleAppender.PLUGIN_NAME)
                    .addAttribute("target", ConsoleAppender.Target.SYSTEM_OUT);
            appenderBuilder.add(layoutBuilder);
            // only log WARN or worse to console
            appenderBuilder.addComponent(builder.newFilter("ThresholdFilter", Filter.Result.ACCEPT, Filter.Result.DENY)
                    .addAttribute("level", Level.WARN));
            builder.add(appenderBuilder);

            rootLogger.add(builder.newAppenderRef(appenderBuilder.getName()));
        }
        {
            final var filePath =
                    Path.of(loggingFolder.getAbsolutePath(), "SDCcc.log").toAbsolutePath();

            // create a file appender
            final var appenderBuilder = builder.newAppender("file", "File")
                    .addAttribute("fileName", filePath.toString())
                    .addAttribute("append", true)
                    .add(layoutBuilder);
            appenderBuilder.addComponent(builder.newFilter("ThresholdFilter", Filter.Result.ACCEPT, Filter.Result.DENY)
                    .addAttribute("level", fileLogLevel));
            builder.add(appenderBuilder);

            rootLogger.add(builder.newAppenderRef(appenderBuilder.getName()));
            sdcccLogger.add(builder.newAppenderRef(appenderBuilder.getName()));
        }
        {
            // quiet down chatty loggers
            CHATTY_LOGGERS.forEach(logger -> builder.add(builder.newAsyncLogger(logger, Level.INFO)
                    .addAttribute("additivity", true)
                    .add(builder.newAppenderRef(triggerOnErrorOrWorseLogAppenderBuilder.getName()))));
        }

        builder.add(rootLogger);
        builder.add(sdcccLogger);
        return builder.build();
    }
}
