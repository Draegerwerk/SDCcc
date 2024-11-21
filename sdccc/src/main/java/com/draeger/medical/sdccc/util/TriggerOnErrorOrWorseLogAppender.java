/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2023 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.util;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Core;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;

/**
 * Log4j Appender that offers the ability to react when ERROR-Level Messages (or worse) are logged.
 */
@Plugin(name = "TriggerOnErrorOrWorseLogAppender", category = Core.CATEGORY_NAME, elementType = Appender.ELEMENT_TYPE)
public class TriggerOnErrorOrWorseLogAppender extends AbstractAppender {
    public static final String APPENDER_NAME = "triggerOnErrorOrWorse";
    private OnErrorOrWorseHandler onErrorOrWorseHandler;

    private List<String> threadNameWhiteList = new ArrayList<>();

    protected TriggerOnErrorOrWorseLogAppender(final String name, final Filter filter) {
        super(name, filter, null, true, Property.EMPTY_ARRAY);
    }

    /**
     * Sets the OnErrorHandler that is called when a Log Message with LogLevel ERROR is logged through this
     * TriggerOnErrorOrWorseLogAppender.
     *
     * @param onErrorOrWorseHandler the Handler.
     */
    public void setOnErrorOrWorseHandler(@Nullable final OnErrorOrWorseHandler onErrorOrWorseHandler) {
        this.onErrorOrWorseHandler = onErrorOrWorseHandler;
    }

    /**
     * Sets the whitelist for regex expr for thread names that should be ignored by the onErrorOrWorseHandler.
     *
     * @param whitelist list of regex to match thread names against
     */
    public void setThreadNameWhitelist(final List<String> whitelist) {
        this.threadNameWhiteList = whitelist;
    }

    /**
     * Factory Method for creating TriggerOnErrorOrWorseLogAppender.
     *
     * @param name   name of the Appender
     * @param filter filter of the Appender
     * @return the created Appender.
     */
    @PluginFactory
    public static TriggerOnErrorOrWorseLogAppender createAppender(
            @PluginAttribute("name") final String name, @PluginElement("Filter") final Filter filter) {
        return new TriggerOnErrorOrWorseLogAppender(name, filter);
    }

    @Override
    public void append(final LogEvent event) {
        // call Handler on ERROR or worse
        if ((event.getLevel().intLevel() <= Level.ERROR.intLevel()) && onErrorOrWorseHandler != null) {
            if (threadNameWhiteList.stream().noneMatch(event.getThreadName()::matches)) {
                onErrorOrWorseHandler.onErrorOrWorse(event);
            }
        }
    }

    /**
     * OnErrorHandler interface. When a class implementing this interface is registered using
     * setOnErrorHandler on the TriggerOnErrorOrWorseLogAppender, then the TriggerOnErrorOrWorseLogAppender
     * will call the method onErrorOrWorse() whenever an ERROR-Level LogMessage or worse is observed.
     */
    public interface OnErrorOrWorseHandler {

        /**
         * Called whenever an ERROR-Level LogMessage or worse is observed.
         * @param event the event that triggered the handler.
         */
        void onErrorOrWorse(LogEvent event);
    }
}
