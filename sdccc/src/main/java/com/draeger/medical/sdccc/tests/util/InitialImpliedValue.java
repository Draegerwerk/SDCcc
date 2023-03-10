/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2023 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.tests.util;

import java.util.HashSet;
import java.util.Set;

/**
 * Tracks the occurrences of AbstractDescriptor/@DescriptorVersion, AbstractState/@DescriptorVersion
 * and AbstractState/@StateVersion during a test run.
 */
public class InitialImpliedValue {
    private final Set<String> descriptorVersionMap;
    private final Set<String> stateDescriptorVersionMap;
    private final Set<String> stateVersionMap;

    /**
     * InitialImpliedValue to track whether a AbstractDescriptor/@DescriptorVersion, AbstractState/@DescriptorVersion
     * or AbstractState/@StateVersion is initial when seen during a test run.
     * The descriptor handle, or in case of a multi state the state handle is used to track if a version is non-initial.
     * If the handle is present the version is non-initial, initial otherwise.
     */
    public InitialImpliedValue() {
        descriptorVersionMap = new HashSet<>();
        stateDescriptorVersionMap = new HashSet<>();
        stateVersionMap = new HashSet<>();
    }

    /**
     * Checks whether the descriptor version for the given handle is initial.
     *
     * @param handle of the descriptor
     * @return true if the descriptor version is initial, false otherwise.
     */
    public boolean isDescriptorVersionInitial(final String handle) {
        return !descriptorVersionMap.contains(handle);
    }

    /**
     * Checks whether the state version for the given handle is initial.
     *
     * @param handle of the state
     * @return true if the state version is initial, false otherwise.
     */
    public boolean isStateVersionInitial(final String handle) {
        return !stateVersionMap.contains(handle);
    }

    /**
     * Checks whether the state descriptor version for the given handle is initial.
     *
     * @param handle of the state
     * @return true if the state version is initial, false otherwise.
     */
    public boolean isStateDescriptorVersionInitial(final String handle) {
        return !stateDescriptorVersionMap.contains(handle);
    }

    /**
     * Sets the descriptor version for the given handle to non-initial.
     *
     * @param handle of the descriptor
     */
    public void setDescriptorVersionNonInitial(final String handle) {
        descriptorVersionMap.add(handle);
    }

    /**
     * Sets the state version for the given handle to non-initial.
     *
     * @param handle of the state
     */
    public void setStateVersionNonInitial(final String handle) {
        stateVersionMap.add(handle);
    }

    /**
     * Sets the state descriptor version for the given handle to non-initial.
     *
     * @param handle of the state
     */
    public void setStateDescriptorVersionNonInitial(final String handle) {
        stateDescriptorVersionMap.add(handle);
    }
}
