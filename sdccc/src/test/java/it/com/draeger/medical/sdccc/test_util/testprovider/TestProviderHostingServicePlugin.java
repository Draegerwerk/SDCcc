/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2023 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package it.com.draeger.medical.sdccc.test_util.testprovider;

import com.google.inject.Inject;
import org.somda.sdc.dpws.DpwsUtil;
import org.somda.sdc.glue.provider.SdcDeviceContext;
import org.somda.sdc.glue.provider.SdcDevicePlugin;

/**
 * Device plugin to set the DPWS model and device.
 */
public class TestProviderHostingServicePlugin implements SdcDevicePlugin {

    private static final String EN = "en";
    private static final String MANUFACTURER_URL = "http://www.draeger.com";
    private final DpwsUtil dpwsUtil;

    @Inject
    TestProviderHostingServicePlugin(final DpwsUtil dpwsUtil) {
        this.dpwsUtil = dpwsUtil;
    }

    @Override
    public void beforeStartUp(final SdcDeviceContext context) {
        context.getDevice()
                .getHostingServiceAccess()
                .setThisDevice(dpwsUtil.createDeviceBuilder()
                        .setFriendlyName(dpwsUtil.createLocalizedStrings()
                                .add(EN, "SDCcc Example Provider")
                                .get())
                        .setFirmwareVersion("v0.1.0")
                        .setSerialNumber("1234-5678-9101-1121")
                        .get());

        context.getDevice()
                .getHostingServiceAccess()
                .setThisModel(dpwsUtil.createModelBuilder()
                        .setManufacturer(dpwsUtil.createLocalizedStrings()
                                .add(EN, "Draeger")
                                .add("de", "Dräger")
                                .get())
                        .setManufacturerUrl(MANUFACTURER_URL)
                        .setModelName(
                                dpwsUtil.createLocalizedStrings().add("PEU").get())
                        .setModelNumber("54-32-1")
                        .setPresentationUrl(MANUFACTURER_URL)
                        .get());
    }
}
