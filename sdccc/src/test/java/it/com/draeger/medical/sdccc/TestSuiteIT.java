/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2023-2025 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package it.com.draeger.medical.sdccc;

import static com.draeger.medical.sdccc.util.TriggerOnErrorOrWorseLogAppender.APPENDER_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.draeger.medical.sdccc.TestSuite;
import com.draeger.medical.sdccc.configuration.DefaultEnabledTestConfig;
import com.draeger.medical.sdccc.configuration.DefaultTestSuiteConfig;
import com.draeger.medical.sdccc.configuration.DefaultTestSuiteModule;
import com.draeger.medical.sdccc.configuration.TestRunConfig;
import com.draeger.medical.sdccc.configuration.TestSuiteConfig;
import com.draeger.medical.sdccc.manipulation.precondition.PreconditionException;
import com.draeger.medical.sdccc.manipulation.precondition.PreconditionRegistry;
import com.draeger.medical.sdccc.messages.HibernateConfig;
import com.draeger.medical.sdccc.sdcri.testclient.TestClient;
import com.draeger.medical.sdccc.sdcri.testclient.TestClientImpl;
import com.draeger.medical.sdccc.tests.InjectorTestBase;
import com.draeger.medical.sdccc.util.Constants;
import com.draeger.medical.sdccc.util.HibernateConfigInMemoryImpl;
import com.draeger.medical.sdccc.util.ReconnectException;
import com.draeger.medical.sdccc.util.TestRunObserver;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.AbstractModule;
import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.inject.util.Modules;
import it.com.draeger.medical.sdccc.test_util.SslMetadata;
import it.com.draeger.medical.sdccc.test_util.testprovider.TestProvider;
import it.com.draeger.medical.sdccc.test_util.testprovider.TestProviderConfig;
import it.com.draeger.medical.sdccc.test_util.testprovider.TestProviderImpl;
import it.com.draeger.medical.sdccc.test_util.testprovider.guice.ProviderFactory;
import it.com.draeger.medical.sdccc.testsuite_it_mock_tests.Identifiers;
import it.com.draeger.medical.sdccc.testsuite_it_mock_tests.WasRunObserver;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nullable;
import javax.net.ssl.HttpsURLConnection;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.builder.api.AppenderComponentBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilderFactory;
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.somda.sdc.biceps.common.storage.PreprocessingException;
import org.somda.sdc.common.guice.AbstractConfigurationModule;
import org.somda.sdc.common.util.ExecutorWrapperService;
import org.somda.sdc.dpws.DpwsConfig;
import org.somda.sdc.dpws.crypto.CryptoSettings;
import org.somda.sdc.dpws.device.DiscoveryAccess;
import org.somda.sdc.dpws.factory.TransportBindingFactory;
import org.somda.sdc.dpws.soap.SoapUtil;
import org.somda.sdc.dpws.soap.factory.RequestResponseClientFactory;
import org.somda.sdc.dpws.soap.wseventing.SubscriptionManager;
import org.somda.sdc.dpws.soap.wseventing.WsEventingConstants;
import org.somda.sdc.dpws.soap.wseventing.model.ObjectFactory;
import org.somda.sdc.glue.GlueConstants;
import org.somda.sdc.glue.common.CommonConstants;

/**
 * SDCcc system test.
 */
public class TestSuiteIT {
    static {
        final ConfigurationBuilder<BuiltConfiguration> builder = ConfigurationBuilderFactory.newConfigurationBuilder();
        builder.setConfigurationName("SDCccIT");
        final AppenderComponentBuilder triggerOnErrorOrWorseLogAppenderBuilder =
                builder.newAppender(APPENDER_NAME, "TriggerOnErrorOrWorseLogAppender");
        builder.add(triggerOnErrorOrWorseLogAppenderBuilder);
        Configurator.reconfigure(builder.build());
        Configurator.setRootLevel(Level.INFO);
    }

    private static final Logger LOG = LogManager.getLogger();

    private static final int TEST_TIMEOUT = 120;

    private static final SslMetadata SSL_METADATA = new SslMetadata();
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(20);

    static {
        SSL_METADATA.startAsync().awaitRunning();
        HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
    }

    private TestProvider testProvider;

    private static Injector createInjector(final AbstractModule... override) {
        return Guice.createInjector(Modules.override(
                        new DefaultTestSuiteModule(), new DefaultTestSuiteConfig(), new DefaultEnabledTestConfig())
                .with(override));
    }

    private static Injector createTestSuiteITInjector(
            final CryptoSettings cryptoSettings,
            final Boolean failingTests,
            @Nullable final LocationConfig locationConfig,
            final String eprAddress,
            final AbstractModule... override)
            throws IOException {
        final var tempDir = Files.createTempDirectory("SDCccIT_TestSuiteIT");
        tempDir.toFile().deleteOnExit();

        LOG.info("Creating injector for epr {}", eprAddress);

        return createInjector(ArrayUtils.addAll(
                override, new MockConfiguration(cryptoSettings, tempDir, failingTests, locationConfig, eprAddress)));
    }

    static String getRandomEpr() {
        return "urn:uuid:" + UUID.randomUUID();
    }

    static TestProvider getProvider(final String eprAddress) throws IOException {
        final var providerCert = SSL_METADATA.getServerKeySet();
        assert providerCert != null;
        final var providerCrypto = SslMetadata.getCryptoSettings(providerCert);

        final var providerInjector = createTestSuiteITInjector(providerCrypto, false, null, eprAddress);

        // load initial mdib from file
        try (final InputStream mdibAsStream = TestSuiteIT.class.getResourceAsStream("TestSuiteIT/mdib.xml")) {
            assertNotNull(mdibAsStream);
            final var providerFac = providerInjector.getInstance(ProviderFactory.class);
            return providerFac.createProvider(mdibAsStream);
        }
    }

    static Injector getConsumerInjector(
            final Boolean failingTests,
            @Nullable final LocationConfig locationConfig,
            final String dutEpr,
            final AbstractModule... override)
            throws IOException {

        final var consumerCert = SSL_METADATA.getClientKeySet();
        assert consumerCert != null;
        final var consumerCrypto = SslMetadata.getCryptoSettings(consumerCert);

        return createTestSuiteITInjector(consumerCrypto, failingTests, locationConfig, dutEpr, override);
    }

    @BeforeEach
    void setUp() throws IOException {
        this.testProvider = getProvider(getRandomEpr());

        final DiscoveryAccess discoveryAccess =
                this.testProvider.getSdcDevice().getDevice().getDiscoveryAccess();
        discoveryAccess.setTypes(List.of(CommonConstants.MEDICAL_DEVICE_TYPE));
        discoveryAccess.setScopes(List.of(GlueConstants.SCOPE_SDC_PROVIDER));
    }

    @AfterEach
    void tearDown() throws TimeoutException {
        this.testProvider.stopService(DEFAULT_TIMEOUT);
    }

    /**
     * Test whether the location based discovery mechanism only discovers those targets that have a matching
     * location context scope string.
     */
    @Test
    @Timeout(TEST_TIMEOUT * 13)
    public void testLocationBasedDiscovery() throws IOException, PreprocessingException, TimeoutException {
        {
            final UUID bed = UUID.randomUUID();
            runWithSpecificLocation(
                    "sdc.ctxt.loc:/sdc.ctxt.loc.detail/facility23%2Fbuilding23%2Ffloor23%2Fpoc23%2Froom23%2Fbed23"
                            + "?fac=facility23&bed=" + bed + "&bldng=building23&poc=poc23&flr=floor23&rm=room23",
                    new LocationConfig(null, null, null, null, null, bed.toString()),
                    false);
        }

        {
            runWithSpecificLocation(
                    "sdc.ctxt.loc:/sdc.ctxt.loc.detail/facility23%2Fbuilding23%2Ffloor23%2Fpoc23%2Froom23%2Fbed23"
                            + "?fac=facility23&bed=" + UUID.randomUUID()
                            + "&bldng=building23&poc=poc23&flr=floor23&rm=room23",
                    new LocationConfig(UUID.randomUUID().toString(), null, null, null, null, null),
                    true);
        }

        {
            final UUID bed = UUID.randomUUID();
            runWithSpecificLocation(
                    "sdc.ctxt.loc:/sdc.ctxt.loc.detail/facility23%2Fbuilding23%2Ffloor23%2Fpoc23%2Froom23%2Fbed23"
                            + "?fac=facility23&bldng=building23&poc=poc23&flr=floor23&rm=room23&bed=" + bed,
                    new LocationConfig(null, null, null, null, null, bed.toString()),
                    false);
        }

        {
            final UUID bed = UUID.randomUUID();
            runWithSpecificLocation(
                    "sdc.ctxt.loc:/sdc.ctxt.loc.detail/_"
                            + "?fac=facility23&bldng=building23&poc=poc23&flr=floor23&rm=room23&bed=" + bed,
                    new LocationConfig(null, null, null, null, null, bed.toString()),
                    false);
        }

        {
            final UUID bed = UUID.randomUUID();
            runWithSpecificLocation(
                    "sdc.ctxt.loc:/sdc.ctxt.loc.detail/_"
                            + "?fac=facility23&bldng=building23&poc=poc23&flr=floor23&rm=room23&bed=" + bed + "2",
                    new LocationConfig(null, null, null, null, null, bed.toString()),
                    true);
        }

        {
            final UUID bed = UUID.randomUUID();
            runWithSpecificLocation(
                    "sdc.ctxt.loc:/sdc.ctxt.loc.detail/_"
                            + "?fac=facility23&bldng=building23&poc=poc23&flr=floor23&rm=room23&bed=" + "0" + bed,
                    new LocationConfig(null, null, null, null, null, bed.toString()),
                    true);
        }

        {
            final UUID bed = UUID.randomUUID();
            runWithSpecificLocation(
                    "sdc.ctxt.loc:/sdc.ctxt.loc.detail/_"
                            + "?fac=facility23&bldng=building23&poc=poc23&flr=floor23&rm=room23&bed=" + bed,
                    new LocationConfig(null, null, null, null, "room23", bed.toString()),
                    false);
        }

        {
            final UUID bed = UUID.randomUUID();
            runWithSpecificLocation(
                    "sdc.ctxt.loc:/sdc.ctxt.loc.detail/_"
                            + "?fac=facility23&bldng=building23&poc=poc23&flr=floor23&rm=room23&bed=" + bed,
                    new LocationConfig(null, null, null, null, "room32", bed.toString()),
                    true);
        }

        {
            final UUID bed = UUID.randomUUID();
            runWithSpecificLocation(
                    "sdc.ctxt.loc:/sdc.ctxt.loc.detail/_"
                            + "?fac=facility23&bldng=building23&poc=poc23&flr=&rm=room23&bed=" + bed,
                    new LocationConfig(null, null, null, "", null, bed.toString()),
                    false);
        }

        {
            final UUID bed = UUID.randomUUID();
            runWithSpecificLocation(
                    "sdc.ctxt.loc:/sdc.ctxt.loc.detail/_"
                            + "?fac=facility23&bldng=building23&poc=poc23&flr=1&rm=room23&bed=" + bed,
                    new LocationConfig(null, null, null, "", null, bed.toString()),
                    true);
        }

        {
            final UUID bed = UUID.randomUUID();
            runWithSpecificLocation(
                    "sdc.ctxt.loc:/sdc.ctxt.loc.detail/_"
                            + "?fac=facility23&bldng=building23&poc=poc23&flr=&rm=room23&bed=" + bed,
                    new LocationConfig(null, null, null, "1", null, bed.toString()),
                    true);
        }

        {
            final UUID bed = UUID.randomUUID();
            runWithSpecificLocation(
                    "sdc.ctxt.loc:/sdc.ctxt.loc.detail/_" + "?fac=facility23&bldng=building23&poc=poc23&rm=room23&bed="
                            + bed,
                    new LocationConfig(null, null, null, "", null, bed.toString()),
                    true);
        }

        {
            final UUID bed = UUID.randomUUID();
            runWithSpecificLocation(
                    "sdc.ctxt.loc:/sdc.ctxt.loc.detail/_" + "?fac=facility23&bldng=building23&poc=poc23&rm=room23&bed="
                            + bed,
                    new LocationConfig(null, null, null, null, null, bed.toString()),
                    false);
        }
    }

    private void runWithSpecificLocation(
            final String locationScope, final LocationConfig locationConfig, final boolean expectedFailure)
            throws PreprocessingException, TimeoutException, IOException {

        final TestProvider provider = getProvider(getRandomEpr());

        final DiscoveryAccess discoveryAccess =
                provider.getSdcDevice().getDevice().getDiscoveryAccess();
        discoveryAccess.setTypes(List.of(CommonConstants.MEDICAL_DEVICE_TYPE));
        discoveryAccess.setScopes(List.of(GlueConstants.SCOPE_SDC_PROVIDER, locationScope));
        provider.startService(DEFAULT_TIMEOUT);

        try {
            final var injector = getConsumerInjector(
                    false, locationConfig, provider.getSdcDevice().getEprAddress());

            final var injectorSpy = spy(injector);
            final var testClientSpy = spy(injector.getInstance(TestClient.class));
            when(injectorSpy.getInstance(TestClient.class)).thenReturn(testClientSpy);

            InjectorTestBase.setInjector(injectorSpy);

            final var obs = injector.getInstance(WasRunObserver.class);
            assertFalse(obs.hadDirectRun());
            assertFalse(obs.hadInvariantRun());

            final var testSuite = injector.getInstance(TestSuite.class);

            if (expectedFailure) {
                final Throwable exception = assertThrows(RuntimeException.class, testSuite::runTestSuite);

                assertTrue(exception.getMessage().contains("java.io.IOException: Couldn't connect to target"));
            } else {
                final var run = testSuite.runTestSuite();
                assertEquals(0, run, "SDCcc had an unexpected failure");

                assertTrue(obs.hadDirectRun());
                assertTrue(obs.hadInvariantRun());

                // no invalidation is allowed in the test run
                final var testRunObserver = injector.getInstance(TestRunObserver.class);

                assertFalse(
                        testRunObserver.isInvalid(),
                        "TestRunObserver had unexpected failures: " + testRunObserver.getReasons());
            }
        } finally {
            provider.stopService(DEFAULT_TIMEOUT);
        }
    }

    /**
     * Runs the test suite with a mock client and mock tests, expected to pass.
     */
    @Test
    @Timeout(TEST_TIMEOUT)
    public void testConsumer() throws IOException, PreprocessingException, TimeoutException {
        testProvider.startService(DEFAULT_TIMEOUT);

        final var injector =
                getConsumerInjector(false, null, testProvider.getSdcDevice().getEprAddress());

        final var injectorSpy = spy(injector);
        final var testClientSpy = spy(injector.getInstance(TestClient.class));
        when(injectorSpy.getInstance(TestClient.class)).thenReturn(testClientSpy);

        InjectorTestBase.setInjector(injectorSpy);

        final var obs = injector.getInstance(WasRunObserver.class);
        assertFalse(obs.hadDirectRun());
        assertFalse(obs.hadInvariantRun());

        final var testSuite = injector.getInstance(TestSuite.class);

        final var run = testSuite.runTestSuite();
        assertEquals(0, run, "SDCcc had an unexpected failure");

        assertTrue(obs.hadDirectRun());
        assertTrue(obs.hadInvariantRun());

        // no invalidation is allowed in the test run
        final var testRunObserver = injector.getInstance(TestRunObserver.class);
        assertFalse(
                testRunObserver.isInvalid(),
                "TestRunObserver had unexpected failures: " + testRunObserver.getReasons());
    }

    /**
     * Runs the test suite with a mock client, mock tests and preconditions throwing an exception, expected to fail, but
     * not abort on phase 3.
     */
    @Test
    @Timeout(TEST_TIMEOUT)
    public void testInvalid() throws IOException, PreconditionException, PreprocessingException, TimeoutException {
        testProvider.startService(DEFAULT_TIMEOUT);

        final var preconditionRegistryMock = mock(PreconditionRegistry.class);
        doThrow(new NullPointerException("intentional exception for testing purposes"))
                .when(preconditionRegistryMock)
                .runPreconditions();

        final var injector =
                getConsumerInjector(false, null, testProvider.getSdcDevice().getEprAddress(), new AbstractModule() {
                    /**
                     * Configures a {@link Binder} via the exposed methods.
                     */
                    @Override
                    protected void configure() {
                        super.configure();
                        bind(PreconditionRegistry.class).toInstance(preconditionRegistryMock);
                    }
                });

        InjectorTestBase.setInjector(injector);

        final var obs = injector.getInstance(WasRunObserver.class);
        assertFalse(obs.hadDirectRun());
        assertFalse(obs.hadInvariantRun());

        final var testSuite = injector.getInstance(TestSuite.class);

        final var run = testSuite.runTestSuite();
        assertEquals(0, run, "SDCcc had an unexpected failure");

        assertTrue(obs.hadDirectRun());
        assertTrue(obs.hadInvariantRun());

        verify(preconditionRegistryMock, atLeastOnce()).runPreconditions();

        // no invalidation is allowed in the test run
        final var testRunObserver = injector.getInstance(TestRunObserver.class);
        assertTrue(testRunObserver.isInvalid(), "TestRunObserver did not have a failure");
    }

    /**
     * Runs the test consumer and causes a failed renewal, verifies that test run is marked invalid.
     */
    @Test
    @Timeout(TEST_TIMEOUT)
    public void testConsumerUnexpectedSubscriptionEnd() throws Exception {
        testProvider.startService(DEFAULT_TIMEOUT);
        final var eprAddress = testProvider.getSdcDevice().getEprAddress();
        final var injector = getConsumerInjector(false, null, eprAddress);
        InjectorTestBase.setInjector(injector);

        final var client = injector.getInstance(TestClient.class);
        client.startService(DEFAULT_TIMEOUT);
        client.connect();

        final var soapUtil = client.getInjector().getInstance(SoapUtil.class);
        final var wseFactory = client.getInjector().getInstance(ObjectFactory.class);

        // get active subscription id
        final var activeSubs = testProvider.getActiveSubscriptions();
        assertEquals(1, activeSubs.size(), "Expected only one active subscription");

        final var subMan = activeSubs.values().stream().findFirst().orElseThrow();

        final var subManAddress = subMan.getSubscriptionManagerEpr().getAddress();

        // unsubscribe from outside the client, next renew should mark test run invalid
        final var transportBindingFactory = client.getInjector().getInstance(TransportBindingFactory.class);
        final var transportBinding = transportBindingFactory.createHttpBinding(subManAddress.getValue(), null);

        final var rrClientFactory = client.getInjector().getInstance(RequestResponseClientFactory.class);
        final var requestResponseClient = rrClientFactory.createRequestResponseClient(transportBinding);

        final var unsubscribe =
                soapUtil.createMessage(WsEventingConstants.WSA_ACTION_UNSUBSCRIBE, wseFactory.createUnsubscribe());
        unsubscribe.getWsAddressingHeader().setTo(subManAddress);

        LOG.info("Unsubscribing for address {}", subManAddress.getValue());
        final var response = requestResponseClient.sendRequestResponse(unsubscribe);
        assertFalse(response.isFault(), "unsubscribe faulted");

        // wait until subscription must've ended and renews must've failed
        final var subscriptionEnd = Duration.between(Instant.now(), subMan.getExpiresTimeout());

        if (!subscriptionEnd.isNegative()) {
            Thread.sleep(subscriptionEnd.toMillis());
        }

        // dead subscription must've been marked
        final var testRunObserver = injector.getInstance(TestRunObserver.class);
        assertTrue(testRunObserver.isInvalid(), "TestRunObserver had unexpectedly absent failures");
    }

    /**
     * Runs the test consumer, connects and disconnects. Test runs should not be marked invalid.
     */
    @Test
    @Timeout(TEST_TIMEOUT)
    public void testConsumerExpectedDisconnect() throws Exception {
        testProvider.startService(DEFAULT_TIMEOUT);
        final var eprAddress = testProvider.getSdcDevice().getEprAddress();
        final var injector = getConsumerInjector(false, null, eprAddress);
        InjectorTestBase.setInjector(injector);

        final var client = injector.getInstance(TestClient.class);
        client.startService(DEFAULT_TIMEOUT);
        client.connect();

        // get active subscription id
        final var activeSubs = testProvider.getActiveSubscriptions();
        assertEquals(1, activeSubs.size(), "Expected only one active subscription");

        final var subManTimeout =
                activeSubs.values().stream().findFirst().orElseThrow().getExpiresTimeout();

        client.disconnect();

        // wait until subscription must've ended
        final var subscriptionEnd = Duration.between(Instant.now(), subManTimeout);

        if (!subscriptionEnd.isNegative()) {
            Thread.sleep(subscriptionEnd.toMillis());
        }

        // test run should not be marked invalid, as disconnect was intentional
        final var testRunObserver = injector.getInstance(TestRunObserver.class);
        assertFalse(
                testRunObserver.isInvalid(),
                "TestRunObserver had unexpected failures: " + testRunObserver.getReasons());
    }

    /**
     * Test failures are counted for invariant and direct tests with a mock client and mock tests.
     */
    @Test
    @Timeout(TEST_TIMEOUT)
    public void testMockConsumerFailures() throws IOException, PreprocessingException, TimeoutException {
        testProvider.startService(DEFAULT_TIMEOUT);

        final var injector =
                getConsumerInjector(true, null, testProvider.getSdcDevice().getEprAddress());
        InjectorTestBase.setInjector(injector);

        final var obs = injector.getInstance(WasRunObserver.class);
        assertFalse(obs.hadDirectRun());
        assertFalse(obs.hadInvariantRun());

        final var testSuite = injector.getInstance(TestSuite.class);

        final var run = testSuite.runTestSuite();
        assertEquals(2, run, "SDCcc had an unexpected amount of failures");

        assertTrue(obs.hadDirectRun());
        assertTrue(obs.hadInvariantRun());

        // no invalidation is allowed in the test run
        final var testRunObserver = injector.getInstance(TestRunObserver.class);
        assertFalse(
                testRunObserver.isInvalid(),
                "TestRunObserver had unexpected failures: " + testRunObserver.getReasons());
    }

    /**
     * Runs the test consumer and triggers a connection loss to the test provider while reconnect is enabled.
     * Verifies whether a reconnection was successful and detected and whether the test run was not marked as
     * invalid.
     *
     * @throws Exception on any exception
     */
    @Test
    @Timeout(TEST_TIMEOUT * 2)
    public void testReconnectEnabledAndProviderRestarted() throws Exception {
        testProvider.startService(DEFAULT_TIMEOUT);
        final var eprAddress = testProvider.getSdcDevice().getEprAddress();
        final var injector = getConsumerInjector(false, null, eprAddress, new AbstractConfigurationModule() {
            @Override
            protected void defaultConfigure() {
                bind(TestSuiteConfig.NETWORK_RECONNECT_WAIT, long.class, 30L);
            }
        });
        InjectorTestBase.setInjector(injector);

        final var client = injector.getInstance(TestClient.class);
        client.startService(DEFAULT_TIMEOUT);
        client.connect();

        final var reconnectFuture = client.enableReconnect(TEST_TIMEOUT);

        final var activeSubs = verifyOnlyOneActiveSubscription(testProvider);
        stopProviderAndWaitForSubscriptionEnd(testProvider, activeSubs);

        final var restartedProvider = buildTestProvider(eprAddress);
        restartedProvider.startService(DEFAULT_TIMEOUT);

        assertTrue(reconnectFuture.get(), "Reconnect did not happen.");
        client.disableReconnect();

        final var restartedSub = verifyOnlyOneActiveSubscription(restartedProvider);
        final var testRunObserver = injector.getInstance(TestRunObserver.class);
        assertFalse(
                testRunObserver.isInvalid(),
                "TestRunObserver had unexpected failures: " + testRunObserver.getReasons());

        stopProviderAndWaitForSubscriptionEnd(restartedProvider, restartedSub);
    }

    /**
     * Runs the test consumer and triggers a connection loss to the test provider while reconnect is enabled, but the
     * provider is not restarted.
     * Verifies whether a reconnection was unsuccessful and whether the test run was marked as invalid.
     *
     * @throws Exception on any exception
     */
    @Test
    @Timeout(TEST_TIMEOUT)
    public void testReconnectEnabledButProviderNotRestarted() throws Exception {
        testProvider.startService(DEFAULT_TIMEOUT);
        final var eprAddress = testProvider.getSdcDevice().getEprAddress();
        final var injector = getConsumerInjector(false, null, eprAddress);
        InjectorTestBase.setInjector(injector);

        final var client = injector.getInstance(TestClient.class);
        client.startService(DEFAULT_TIMEOUT);
        client.connect();

        final var reconnectFuture = client.enableReconnect(TEST_TIMEOUT);

        final var activeSubs = verifyOnlyOneActiveSubscription(testProvider);
        stopProviderAndWaitForSubscriptionEnd(testProvider, activeSubs);

        assertFalse(reconnectFuture.get(), "Reconnect should not have happened.");
        client.disableReconnect();
        final var testRunObserver = injector.getInstance(TestRunObserver.class);
        assertTrue(testRunObserver.isInvalid(), "TestRunObserver should have been invalidated.");
        final var reasons = testRunObserver.getReasons();
        assertEquals(1, reasons.size(), "Should only have 1 invalidation reason");
        assertTrue(
                reasons.get(0).contains(TestClientImpl.COULDN_T_RECONNECT),
                String.format(
                        "Reason should contain '%s', since all reconnection attempts failed.",
                        TestClientImpl.COULDN_T_RECONNECT));
    }

    /**
     * Runs the test consumer and triggers no connection loss to the test provider while reconnect is enabled.
     * Verifies that no reconnection was detected and the test run was not marked as invalid.
     *
     * @throws Exception on any exception
     */
    @Test
    @Timeout(TEST_TIMEOUT)
    public void testReconnectEnabledButNoConnectionLoss() throws Exception {
        testProvider.startService(DEFAULT_TIMEOUT);
        final var injector =
                getConsumerInjector(false, null, testProvider.getSdcDevice().getEprAddress());
        InjectorTestBase.setInjector(injector);

        final var client = injector.getInstance(TestClient.class);
        client.startService(DEFAULT_TIMEOUT);
        client.connect();
        final var reconnectFuture = client.enableReconnect(20);

        verifyOnlyOneActiveSubscription(testProvider);

        // wait until reconnect timeout is reached without losing connection
        assertFalse(reconnectFuture.get(), "Should be false, since the connection was never lost.");
        client.disableReconnect();
        // test run should not be marked invalid, as disconnect was intentional
        final var testRunObserver = injector.getInstance(TestRunObserver.class);
        assertFalse(
                testRunObserver.isInvalid(),
                "TestRunObserver had unexpected failures: " + testRunObserver.getReasons());
    }

    /**
     * Runs the test consumer but without the "reconnectExecutor" running. The connection to the provider is
     * improperly closed, and it is verified that the test run is invalidated.
     * This test is intended to ensure that an exception is thrown in such a faulty case.
     *
     * @throws Exception on any exception
     */
    @Test
    @Timeout(TEST_TIMEOUT)
    public void testReconnectExecutorNotStarted() throws Exception {
        testProvider.startService(DEFAULT_TIMEOUT);
        final var eprAddress = testProvider.getSdcDevice().getEprAddress();
        final var injector = getConsumerInjector(false, null, eprAddress);
        InjectorTestBase.setInjector(injector);

        final var client = injector.getInstance(TestClient.class);
        client.startService(DEFAULT_TIMEOUT);
        client.connect();

        final var reconnectFuture = client.enableReconnect(TEST_TIMEOUT);

        final var activeSubs = verifyOnlyOneActiveSubscription(testProvider);

        // get the private reconnectExecutor field via reflection and replace it with one that is not running
        final Field reconnectExecutor = client.getClass().getDeclaredField("reconnectExecutor");
        reconnectExecutor.setAccessible(true);
        reconnectExecutor.set(
                client,
                new ExecutorWrapperService<>(
                        () -> Executors.newSingleThreadExecutor(new ThreadFactoryBuilder()
                                .setNameFormat("customSdcccReconnectThread")
                                .setDaemon(true)
                                .build()),
                        "ReconnectExecutor",
                        "InstanceIdentifier"));

        stopProviderAndWaitForSubscriptionEnd(testProvider, activeSubs);

        final var exception = assertThrows(ExecutionException.class, reconnectFuture::get);
        client.disableReconnect();
        assertInstanceOf(ReconnectException.class, exception.getCause());
        assertTrue(exception.getMessage().contains(TestClientImpl.RECONNECT_EXECUTOR_SERVICE_NOT_RUNNING));
        final var testRunObserver = injector.getInstance(TestRunObserver.class);
        assertTrue(testRunObserver.isInvalid(), "TestRunObserver should be invalid.");
        final var reasons = testRunObserver.getReasons();
        assertEquals(1, reasons.size(), "Should only have 1 invalidation reason");
        assertTrue(
                reasons.get(0).contains(TestClientImpl.RECONNECT_EXECUTOR_SERVICE_NOT_RUNNING),
                String.format(
                        "Reason should contain '%s', since the reconnectExecutor was replaced with one that was not started.",
                        TestClientImpl.RECONNECT_EXECUTOR_SERVICE_NOT_RUNNING));
    }

    /**
     * Runs the test consumer and triggers a connection loss to the test provider while reconnect is enabled.
     * The reconnect feature times out during a reconnection attempt.
     * Verifies that the connection is not reestablished and the test run is marked as invalid.
     *
     * @throws Exception on any exception
     */
    @Test
    @Timeout(TEST_TIMEOUT * 2)
    public void testReconnectEnabledTimeoutDuringAttempt() throws Exception {
        final var waitTime = 20L;
        final var enableReconnectTime = waitTime * 3;
        testProvider.startService(DEFAULT_TIMEOUT);
        final var eprAddress = testProvider.getSdcDevice().getEprAddress();
        final var injector = getConsumerInjector(false, null, eprAddress, new AbstractConfigurationModule() {
            @Override
            protected void defaultConfigure() {
                bind(TestSuiteConfig.NETWORK_RECONNECT_WAIT, long.class, waitTime);
            }
        });
        InjectorTestBase.setInjector(injector);

        final var client = injector.getInstance(TestClient.class);
        client.startService(DEFAULT_TIMEOUT);
        client.connect();

        final var reconnectFuture = client.enableReconnect(enableReconnectTime);

        final var activeSubs = verifyOnlyOneActiveSubscription(testProvider);
        stopProviderAndWaitForSubscriptionEnd(testProvider, activeSubs);

        // wait until the reconnect timeout is reached
        Thread.sleep(TimeUnit.SECONDS.toMillis(enableReconnectTime));

        final var restartedProvider = buildTestProvider(eprAddress);
        restartedProvider.startService(DEFAULT_TIMEOUT);

        final var exception = assertThrows(
                ExecutionException.class,
                reconnectFuture::get,
                "Reconnect should not have happened, reconnect feature should have been disabled.");
        client.disableReconnect();
        assertInstanceOf(ReconnectException.class, exception.getCause());
        assertTrue(exception.getMessage().contains(TestClientImpl.TIMEOUT_REACHED_WITH_NO_RECONNECT));
        final var testRunObserver = injector.getInstance(TestRunObserver.class);
        assertTrue(testRunObserver.isInvalid(), "TestRunObserver should have been invalid.");
        final var reasons = testRunObserver.getReasons();
        assertEquals(1, reasons.size(), "Should only have 1 invalidation reason");
        assertTrue(
                reasons.get(0).contains(TestClientImpl.TIMEOUT_REACHED_WITH_NO_RECONNECT),
                String.format(
                        "Reason should contain '%s', since the provider was started too late, the reconnect timeout was reached "
                                + "and the feature is disabled again.",
                        TestClientImpl.TIMEOUT_REACHED_WITH_NO_RECONNECT));
        restartedProvider.stopService(DEFAULT_TIMEOUT);
    }

    /**
     * Runs the test consumer and enables reconnect but triggers a connection loss to the test provider when it is
     * disabled again. So the consumer should recognize a failed connection, but the reconnect feature should be
     * disabled again.
     * Verifies that the connection is not reestablished and the test run is marked as invalid.
     *
     * @throws Exception on any exception
     */
    @Test
    @Timeout(TEST_TIMEOUT)
    //TODO flaky
    public void testReconnectDisabledAfterTimeout() throws Exception {
        final var reconnectionEnabledTime = 6;
        testProvider.startService(DEFAULT_TIMEOUT);
        final var eprAddress = testProvider.getSdcDevice().getEprAddress();
        final var injector = getConsumerInjector(false, null, eprAddress, new AbstractConfigurationModule() {
            @Override
            protected void defaultConfigure() {
                bind(TestSuiteConfig.NETWORK_RECONNECT_WAIT, long.class, 2L);
            }
        });
        InjectorTestBase.setInjector(injector);

        final var client = injector.getInstance(TestClient.class);
        client.startService(DEFAULT_TIMEOUT);
        client.connect();

        final var reconnectFuture = client.enableReconnect(reconnectionEnabledTime);

        // wait for the reconnect timeout to be reached
        Thread.sleep(TimeUnit.SECONDS.toMillis(reconnectionEnabledTime));

        final var activeSubs = verifyOnlyOneActiveSubscription(testProvider);
        stopProviderAndWaitForSubscriptionEnd(testProvider, activeSubs);

        final var restartedProvider = buildTestProvider(eprAddress);
        restartedProvider.startService(DEFAULT_TIMEOUT);

        assertFalse(reconnectFuture.get(), "Reconnect did not happen.");
        client.disableReconnect();
        final var testRunObserver = injector.getInstance(TestRunObserver.class);
        assertTrue(testRunObserver.isInvalid(), "TestRunObserver should have been invalid.");
        final var reasons = testRunObserver.getReasons();
        assertEquals(1, reasons.size(), "Should only have 1 invalidation reason");
        assertTrue(
                reasons.get(0).contains(TestClientImpl.UNEXPECTED_DISCONNECT_DETECTED),
                String.format(
                        "Reason should contain '%s', since the connection loss happened too late, the reconnect "
                                + "timeout was reached and the feature is disabled again.",
                        TestClientImpl.UNEXPECTED_DISCONNECT_DETECTED));

        restartedProvider.stopService(DEFAULT_TIMEOUT);
    }

    /**
     * Runs the test consumer and enables reconnect but triggers a connection loss to the test provider when it is
     * disabled again. So the consumer should recognize a failed connection, but the reconnect feature should be
     * disabled again.
     * Verifies that the connection is not reestablished and the test run is marked as invalid.
     *
     * @throws Exception on any exception
     */
    @Test
    @Timeout(TEST_TIMEOUT * 2)
    public void testReconnectEnabledSubsequentially() throws Exception {
        testProvider.startService(DEFAULT_TIMEOUT);
        final var eprAddress = testProvider.getSdcDevice().getEprAddress();
        final var injector = getConsumerInjector(false, null, eprAddress, new AbstractConfigurationModule() {
            @Override
            protected void defaultConfigure() {
                bind(TestSuiteConfig.NETWORK_RECONNECT_WAIT, long.class, 20L);
            }
        });
        InjectorTestBase.setInjector(injector);

        final var client = injector.getInstance(TestClient.class);
        client.startService(DEFAULT_TIMEOUT);
        client.connect();

        final var reconnectFuture = client.enableReconnect(100);

        final var activeSubs = verifyOnlyOneActiveSubscription(testProvider);
        stopProviderAndWaitForSubscriptionEnd(testProvider, activeSubs);
        // testProvider.stopService(DEFAULT_TIMEOUT);

        final var restartedProvider = buildTestProvider(eprAddress);
        restartedProvider.startService(DEFAULT_TIMEOUT);

        assertTrue(reconnectFuture.get(), "Reconnect did not happen.");
        client.disableReconnect();
        final var restartedSub = verifyOnlyOneActiveSubscription(restartedProvider);

        final var reconnectFutureSubsequent = client.enableReconnect(TEST_TIMEOUT);
        stopProviderAndWaitForSubscriptionEnd(restartedProvider, restartedSub);

        final var rerestartedProvider = buildTestProvider(eprAddress);
        rerestartedProvider.startService(DEFAULT_TIMEOUT);

        assertTrue(reconnectFutureSubsequent.get(), "Reconnect did not happen.");
        client.disableReconnect();
        // assertThrows(TimeoutException.class, reconnectFutureSubsequent::get);

        final var testRunObserver = injector.getInstance(TestRunObserver.class);
        assertFalse(
                testRunObserver.isInvalid(),
                "TestRunObserver had unexpected failures: " + testRunObserver.getReasons());

        rerestartedProvider.stopService(DEFAULT_TIMEOUT);
    }

    private Map<String, SubscriptionManager> verifyOnlyOneActiveSubscription(final TestProvider provider) {
        final var activeSubs = provider.getActiveSubscriptions();
        assertEquals(1, activeSubs.size(), "Expected only one active subscription");
        return activeSubs;
    }

    private void stopProviderAndWaitForSubscriptionEnd(
            final TestProvider provider, final Map<String, SubscriptionManager> activeSubs) throws Exception {
        final var subManTimeout =
                activeSubs.values().stream().findFirst().orElseThrow().getExpiresTimeout();

        provider.stopService(DEFAULT_TIMEOUT);

        // wait until subscription must've ended
        final var subscriptionEnd = Duration.between(Instant.now(), subManTimeout);
        if (!subscriptionEnd.isNegative()) {
            Thread.sleep(subscriptionEnd.toMillis());
        }
    }

    private TestProvider buildTestProvider(final String epr) throws Exception {
        final var provider = getProvider(epr);

        final DiscoveryAccess discoveryAccess =
                provider.getSdcDevice().getDevice().getDiscoveryAccess();
        discoveryAccess.setTypes(List.of(CommonConstants.MEDICAL_DEVICE_TYPE));
        discoveryAccess.setScopes(List.of(GlueConstants.SCOPE_SDC_PROVIDER));

        return provider;
    }

    private record LocationConfig(
            @Nullable String facility,
            @Nullable String building,
            @Nullable String pointOfCare,
            @Nullable String floor,
            @Nullable String room,
            @Nullable String bed) {}

    private static final class MockConfiguration extends AbstractConfigurationModule {

        private final CryptoSettings cryptoSettings;
        private final Path tempDir;
        private final boolean failingTests;
        private final LocationConfig locationConfig;
        private final String dutEpr;

        private MockConfiguration(
                final CryptoSettings cryptoSettings,
                final Path tempDir,
                final boolean failingTests,
                @Nullable final LocationConfig locationConfig,
                final String dutEpr) {
            this.cryptoSettings = cryptoSettings;
            this.tempDir = tempDir;
            this.failingTests = failingTests;
            this.dutEpr = dutEpr;

            this.locationConfig = Objects.requireNonNullElseGet(
                    locationConfig, () -> new LocationConfig(null, null, null, null, null, null));
        }

        @Override
        protected void defaultConfigure() {
            bind(CryptoSettings.class).toInstance(cryptoSettings);

            bind(TestSuiteConfig.CI_MODE, Boolean.class, true);
            bind(TestSuiteConfig.CONSUMER_DEVICE_EPR, String.class, this.dutEpr);
            bind(TestSuiteConfig.CONSUMER_DEVICE_LOCATION_FACILITY, String.class, this.locationConfig.facility);
            bind(TestSuiteConfig.CONSUMER_DEVICE_LOCATION_BUILDING, String.class, this.locationConfig.building);
            bind(TestSuiteConfig.CONSUMER_DEVICE_LOCATION_POINT_OF_CARE, String.class, this.locationConfig.pointOfCare);
            bind(TestSuiteConfig.CONSUMER_DEVICE_LOCATION_FLOOR, String.class, this.locationConfig.floor);
            bind(TestSuiteConfig.CONSUMER_DEVICE_LOCATION_ROOM, String.class, this.locationConfig.room);
            bind(TestSuiteConfig.CONSUMER_DEVICE_LOCATION_BED, String.class, this.locationConfig.bed);

            bind(TestProviderConfig.PROVIDER_DEVICE_EPR, String.class, this.dutEpr);

            bind(TestSuiteConfig.NETWORK_INTERFACE_ADDRESS, String.class, "127.0.0.1");
            bind(DpwsConfig.MULTICAST_TTL, Integer.class, 128);

            bind(HibernateConfig.class).to(HibernateConfigInMemoryImpl.class).in(Singleton.class);
            install(new FactoryModuleBuilder()
                    .implement(TestProvider.class, TestProviderImpl.class)
                    .build(ProviderFactory.class));

            bind(TestSuiteConfig.SDC_TEST_DIRECTORIES, String[].class, new String[] {
                "it.com.draeger.medical.sdccc.testsuite_it_mock_tests",
            });

            bind(TestRunConfig.TEST_RUN_DIR, File.class, tempDir.toFile());

            // enable the mock tests
            bind(Identifiers.DIRECT_TEST_IDENTIFIER, Boolean.class, true);
            bind(Identifiers.INVARIANT_TEST_IDENTIFIER, Boolean.class, true);

            bind(Identifiers.DIRECT_TEST_IDENTIFIER_FAILING, Boolean.class, failingTests);
            bind(Identifiers.INVARIANT_TEST_IDENTIFIER_FAILING, Boolean.class, failingTests);
            bind(Constants.CONFIGURATION_MODULE, AbstractConfigurationModule.class, new AbstractConfigurationModule() {
                @Override
                protected void defaultConfigure() {}
            });
        }
    }
}
