/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.tests.util.guice;

import com.draeger.medical.sdccc.messages.MessageStorage;
import com.draeger.medical.sdccc.tests.util.MdibHistorian;
import com.draeger.medical.sdccc.util.TestRunObserver;

/**
 * Mdib historian factory.
 */
public interface MdibHistorianFactory {

    /**
     * Create an mdib historian running over a message storage.
     *
     * @param messageStorage  to retrieve messages from
     * @param testRunObserver to indicate failures
     * @return a new mdib historian
     */
    MdibHistorian createMdibHistorian(MessageStorage messageStorage, TestRunObserver testRunObserver);
}
