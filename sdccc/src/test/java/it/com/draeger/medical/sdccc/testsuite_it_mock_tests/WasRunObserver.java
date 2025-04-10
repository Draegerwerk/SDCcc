/*
 * This Source Code Form is subject to the terms of the "SDCcc non-commercial use license".
 *
 * Copyright (C) 2025 Draegerwerk AG & Co. KGaA
 */

package it.com.draeger.medical.sdccc.testsuite_it_mock_tests;

import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * Observer for mock tests, tracking if they've been run.
 */
@Singleton
public class WasRunObserver {

    private boolean directRun;
    private boolean invariantRun;

    @Inject
    WasRunObserver() {
        directRun = false;
        invariantRun = false;
    }

    /**
     * @return had a direct test run
     */
    public boolean hadDirectRun() {
        return directRun;
    }

    /**
     * Sets direct test run to value.
     *
     * @param directRun value to set to
     */
    public void setDirectRun(final boolean directRun) {
        this.directRun = directRun;
    }

    /**
     * @return had an invariant test run
     */
    public boolean hadInvariantRun() {
        return invariantRun;
    }

    /**
     * Sets invariant test run to value.
     *
     * @param invariantRun value to set to
     */
    public void setInvariantRun(final boolean invariantRun) {
        this.invariantRun = invariantRun;
    }
}
