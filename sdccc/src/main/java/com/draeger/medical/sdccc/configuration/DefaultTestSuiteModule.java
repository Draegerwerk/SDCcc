/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2023 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.configuration;

import com.draeger.medical.sdccc.manipulation.GRpcManipulations;
import com.draeger.medical.sdccc.manipulation.Manipulations;
import com.draeger.medical.sdccc.manipulation.guice.InteractionFactory;
import com.draeger.medical.sdccc.messages.HibernateConfig;
import com.draeger.medical.sdccc.messages.HibernateConfigImpl;
import com.draeger.medical.sdccc.messages.guice.ManipulationInfoFactory;
import com.draeger.medical.sdccc.messages.guice.MessageFactory;
import com.draeger.medical.sdccc.sdcri.CustomCryptoSettings;
import com.draeger.medical.sdccc.sdcri.testclient.TestClient;
import com.draeger.medical.sdccc.sdcri.testclient.TestClientImpl;
import com.draeger.medical.sdccc.util.junit.guice.XmlReportFactory;
import com.draeger.medical.sdccc.util.junit.util.ClassUtil;
import com.draeger.medical.sdccc.util.junit.util.ClassUtilImpl;
import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.Singleton;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.inject.name.Names;
import org.somda.sdc.dpws.crypto.CryptoSettings;

/**
 * Module which provides default guice bindings for SDCcc.
 */
public class DefaultTestSuiteModule extends AbstractModule {

    @Override
    protected void configure() {
        super.configure();

        install(new FactoryModuleBuilder().build(MessageFactory.class));
        install(new FactoryModuleBuilder().build(ManipulationInfoFactory.class));
        install(new FactoryModuleBuilder().build(XmlReportFactory.class));
        install(new FactoryModuleBuilder().build(InteractionFactory.class));

        bind(CryptoSettings.class).to(CustomCryptoSettings.class).in(Singleton.class);
        bind(TestClient.class).to(TestClientImpl.class).in(Singleton.class);
        bind(ClassUtil.class).to(ClassUtilImpl.class);
        bind(HibernateConfig.class).to(HibernateConfigImpl.class).in(Singleton.class);
        bind(Manipulations.class).to(GRpcManipulations.class).in(Singleton.class);
        bind(Key.get(Boolean.class, Names.named(TestSuiteConfig.SUMMARIZE_MESSAGE_ENCODING_ERRORS)))
                .toInstance(true);
    }
}
