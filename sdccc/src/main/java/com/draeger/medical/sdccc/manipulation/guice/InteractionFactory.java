/*
 * This Source Code Form is subject to the terms of the "SDCcc non-commercial use license".
 *
 * Copyright (C) 2025 Draegerwerk AG & Co. KGaA
 */

package com.draeger.medical.sdccc.manipulation.guice;

import com.draeger.medical.sdccc.manipulation.UserInteraction;
import com.google.inject.assistedinject.Assisted;
import java.io.InputStream;

/**
 * Guice factory for {@linkplain UserInteraction}.
 */
public interface InteractionFactory {
    /**
     * Creates a user interaction using a given input stream for console-based interaction.
     *
     * @param interactionInput input stream to read user responses from.
     * @return configured user interaction handler.
     */
    UserInteraction createUserInteraction(@Assisted InputStream interactionInput);
}
