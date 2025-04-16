/*
 * This Source Code Form is subject to the terms of the "SDCcc non-commercial use license".
 *
 * Copyright (C) 2025 Draegerwerk AG & Co. KGaA
 */

package com.draeger.medical.sdccc.messages;

/**
 * An interface to report Inconsistencies.
 */
public interface InconsistencyReport {
    /**
     * Reports an inconsistency.
     * @param originA - the origin of valueA
     * @param valueA - the valueA
     * @param originB - the origin of valueB
     * @param valueB - the valueB
     */
    void report(String originA, String valueA, String originB, String valueB);
}
