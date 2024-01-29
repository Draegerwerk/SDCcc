/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2023 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.sdcri;

import com.draeger.medical.sdccc.configuration.TestSuiteConfig;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.somda.sdc.dpws.network.LocalAddressResolver;

import java.util.Optional;


/**
 * Implementation of {@linkplain LocalAddressResolver} that uses the configured address.
 */
@Singleton
public class LocalAddressResolverImpl implements LocalAddressResolver {
    private final String adapterAddress;

    @Inject
    LocalAddressResolverImpl(
        @Named(TestSuiteConfig.NETWORK_INTERFACE_ADDRESS) final String adapterAddress
    ) {
        this.adapterAddress = adapterAddress;
    }

    @Override
    public Optional<String> getLocalAddress(final String remoteUri) {
        return Optional.of(adapterAddress);
    }
}
