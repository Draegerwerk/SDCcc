/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package it.com.draeger.medical.sdccc;

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
import com.draeger.medical.sdccc.sdcri.testprovider.TestProvider;
import com.draeger.medical.sdccc.sdcri.testprovider.TestProviderImpl;
import com.draeger.medical.sdccc.sdcri.testprovider.guice.ProviderFactory;
import com.draeger.medical.sdccc.tests.InjectorTestBase;
import com.draeger.medical.sdccc.util.HibernateConfigInMemoryImpl;
import com.draeger.medical.sdccc.util.TestRunObserver;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.inject.util.Modules;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import it.com.draeger.medical.sdccc.test_util.SslMetadata;
import it.com.draeger.medical.sdccc.testsuite_it_mock_tests.Identifiers;
import it.com.draeger.medical.sdccc.testsuite_it_mock_tests.WasRunObserver;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.DefaultConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.somda.sdc.biceps.common.storage.PreprocessingException;
import org.somda.sdc.common.guice.AbstractConfigurationModule;
import org.somda.sdc.dpws.crypto.CryptoSettings;
import org.somda.sdc.dpws.factory.TransportBindingFactory;
import org.somda.sdc.dpws.soap.SoapUtil;
import org.somda.sdc.dpws.soap.factory.RequestResponseClientFactory;
import org.somda.sdc.dpws.soap.wseventing.WsEventingConstants;
import org.somda.sdc.dpws.soap.wseventing.model.ObjectFactory;

import javax.net.ssl.HttpsURLConnection;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SDCcc system test.
 */
public class TestSuiteIT {
    static {
        Configurator.reconfigure(new DefaultConfiguration());
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

    private static final String DUT_EPR = "urn:uuid:" + UUID.randomUUID();
    private TestProvider testProvider;

    private static Injector createInjector(final AbstractModule... override) {
        return Guice.createInjector(
            Modules.override(
                new DefaultTestSuiteModule(),
                new DefaultTestSuiteConfig(),
                new DefaultEnabledTestConfig()
            ).with(override)
        );
    }

    private static Injector createTestSuiteITInjector(final CryptoSettings cryptoSettings,
                                                      final Boolean failingTests,
                                                      final AbstractModule... override)
        throws IOException {
        final var tempDir = Files.createTempDirectory("SDCccIT_TestSuiteIT");
        tempDir.toFile().deleteOnExit();

        LOG.info("Creating injector for epr {}", DUT_EPR);

        return createInjector(
            ArrayUtils.addAll(override, new MockConfiguration(cryptoSettings, tempDir, failingTests)));
    }

    private static String getLoopbackName() {
        final NetworkInterface loopbackAdapter;
        try {
            loopbackAdapter = NetworkInterface.getByInetAddress(InetAddress.getLoopbackAddress());
        } catch (final SocketException e) {
            throw new RuntimeException(e);
        }
        return loopbackAdapter.getName();
    }

    @SuppressFBWarnings(
        value = {"RCN_REDUNDANT_NULLCHECK_WOULD_HAVE_BEEN_A_NPE"},
        justification = "No null check performed."
    )
    private TestProvider getProvider() throws IOException {
        final var providerCert = SSL_METADATA.getServerKeySet();
        assert providerCert != null;
        final var providerCrypto = SslMetadata.getCryptoSettings(providerCert);

        final var injector = createTestSuiteITInjector(providerCrypto, false);

        // load initial mdib from file
        try (final InputStream mdibAsStream = TestSuiteIT.class.getResourceAsStream("TestSuiteIT/mdib.xml")) {
            final var providerFac = injector.getInstance(ProviderFactory.class);
            return providerFac.createProvider(mdibAsStream);
        }
    }

    private Injector getConsumerInjector(final boolean failingTests,
                                         final AbstractModule... override) throws IOException {

        final var consumerCert = SSL_METADATA.getClientKeySet();
        assert consumerCert != null;
        final var consumerCrypto = SslMetadata.getCryptoSettings(consumerCert);

        return createTestSuiteITInjector(consumerCrypto, failingTests, override);
    }

    @BeforeEach
    void setUp() throws IOException, TimeoutException, PreprocessingException {
        this.testProvider = getProvider();
        testProvider.startService(DEFAULT_TIMEOUT);
    }

    @AfterEach
    void tearDown() throws TimeoutException {
        this.testProvider.stopService(DEFAULT_TIMEOUT);
    }

    /**
     * Runs the test suite with a mock client and mock tests, expected to pass.
     */
    @Test
    @Timeout(TEST_TIMEOUT)
    public void testConsumer() throws IOException {

        final var injector = getConsumerInjector(false);

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
            "TestRunObserver had unexpected failures: " + testRunObserver.getReasons()
        );
    }

    /**
     * Runs the test suite with a mock client, mock tests and preconditions throwing an exception,
     * expected to fail, but not abort on phase 3.
     */
    @Test
    @Timeout(TEST_TIMEOUT)
    public void testInvalid() throws IOException, PreconditionException {

        final var preconditionRegistryMock = mock(PreconditionRegistry.class);
        doThrow(new NullPointerException()).when(preconditionRegistryMock).runPreconditions();

        final var injector = getConsumerInjector(false, new AbstractModule() {
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
     * Runs the test consumer and causes a failed renew, verifies that test run is marked invalid.
     */
    @Test
    @Timeout(TEST_TIMEOUT)
    public void testConsumerUnexpectedSubscriptionEnd() throws Exception {
        final var injector = getConsumerInjector(false);
        InjectorTestBase.setInjector(injector);

        final var obs = injector.getInstance(WasRunObserver.class);
        assertFalse(obs.hadDirectRun());
        assertFalse(obs.hadInvariantRun());

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
        final var transportBinding = transportBindingFactory.createHttpBinding(subManAddress.getValue());

        final var rrClientFactory = client.getInjector().getInstance(RequestResponseClientFactory.class);
        final var requestResponseClient = rrClientFactory.createRequestResponseClient(transportBinding);

        final var unsubscribe = soapUtil.createMessage(
            WsEventingConstants.WSA_ACTION_UNSUBSCRIBE,
            wseFactory.createUnsubscribe()
        );
        unsubscribe.getWsAddressingHeader().setTo(subManAddress);

        LOG.info("Unsubscribing for address {}", subManAddress.getValue());
        final var response = requestResponseClient.sendRequestResponse(unsubscribe);
        assertFalse(response.isFault(), "unsubscribe faulted");

        // wait until subscription must've ended and renews must've failed
        final var subscriptionEnd = Duration.between(LocalDateTime.now(), subMan.getExpiresTimeout());

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
        final var injector = getConsumerInjector(false);
        InjectorTestBase.setInjector(injector);

        final var obs = injector.getInstance(WasRunObserver.class);
        assertFalse(obs.hadDirectRun());
        assertFalse(obs.hadInvariantRun());

        final var client = injector.getInstance(TestClient.class);
        client.startService(DEFAULT_TIMEOUT);
        client.connect();

        // get active subscription id
        final var activeSubs = testProvider.getActiveSubscriptions();
        assertEquals(1, activeSubs.size(), "Expected only one active subscription");

        final var subManTimeout = activeSubs.values().stream().findFirst().orElseThrow().getExpiresTimeout();

        client.disconnect();

        // wait until subscription must've ended
        final var subscriptionEnd = Duration.between(LocalDateTime.now(), subManTimeout);

        if (!subscriptionEnd.isNegative()) {
            Thread.sleep(subscriptionEnd.toMillis());
        }

        // test run should not be marked invalid, as disconnect was intentional
        final var testRunObserver = injector.getInstance(TestRunObserver.class);
        assertFalse(
            testRunObserver.isInvalid(),
            "TestRunObserver had unexpected failures: " + testRunObserver.getReasons()
        );
    }

    /**
     * Test failures are counted for invariant and direct tests with a mock client and mock tests.
     */
    @Test
    @Timeout(TEST_TIMEOUT)
    public void testMockConsumerFailures() throws IOException {
        final var injector = getConsumerInjector(true);
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
            "TestRunObserver had unexpected failures: " + testRunObserver.getReasons()
        );
    }

    private static final class MockConfiguration extends AbstractConfigurationModule {

        private final CryptoSettings cryptoSettings;
        private final Path tempDir;
        private final boolean failingTests;

        private MockConfiguration(final CryptoSettings cryptoSettings, final Path tempDir, final boolean failingTests) {
            this.cryptoSettings = cryptoSettings;
            this.tempDir = tempDir;
            this.failingTests = failingTests;
        }

        @Override
        protected void defaultConfigure() {
            bind(CryptoSettings.class).toInstance(cryptoSettings);

            bind(TestSuiteConfig.CI_MODE, Boolean.class, true);
            bind(TestSuiteConfig.CONSUMER_ENABLE, Boolean.class, true);
            bind(TestSuiteConfig.CONSUMER_DEVICE_EPR, String.class, DUT_EPR);

            bind(TestSuiteConfig.PROVIDER_ENABLE, Boolean.class, true);
            bind(TestSuiteConfig.PROVIDER_DEVICE_EPR, String.class, DUT_EPR);

            bind(TestSuiteConfig.NETWORK_INTERFACE_ADDRESS, String.class, "127.0.0.1");

            bind(HibernateConfig.class).to(HibernateConfigInMemoryImpl.class).in(Singleton.class);
            install(
                new FactoryModuleBuilder()
                    .implement(TestProvider.class, TestProviderImpl.class)
                    .build(ProviderFactory.class)
            );

            bind(TestSuiteConfig.SDC_TEST_DIRECTORIES,
                String[].class,
                new String[]{
                    "it.com.draeger.medical.sdccc.testsuite_it_mock_tests",
                }
            );

            bind(TestRunConfig.TEST_RUN_DIR, File.class, tempDir.toFile());

            // enable the mock tests
            bind(Identifiers.DIRECT_TEST_IDENTIFIER, Boolean.class, true);
            bind(Identifiers.INVARIANT_TEST_IDENTIFIER, Boolean.class, true);

            bind(Identifiers.DIRECT_TEST_IDENTIFIER_FAILING, Boolean.class, failingTests);
            bind(Identifiers.INVARIANT_TEST_IDENTIFIER_FAILING, Boolean.class, failingTests);

        }
    }

}
