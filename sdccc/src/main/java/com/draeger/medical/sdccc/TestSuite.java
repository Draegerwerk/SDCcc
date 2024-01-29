/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2023, 2024 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectPackage;

import com.draeger.medical.sdccc.configuration.CommandLineOptions;
import com.draeger.medical.sdccc.configuration.DefaultEnabledTestConfig;
import com.draeger.medical.sdccc.configuration.DefaultTestSuiteConfig;
import com.draeger.medical.sdccc.configuration.DefaultTestSuiteModule;
import com.draeger.medical.sdccc.configuration.EnabledTestConfig;
import com.draeger.medical.sdccc.configuration.TestRunConfig;
import com.draeger.medical.sdccc.configuration.TestSuiteConfig;
import com.draeger.medical.sdccc.guice.TomlConfigParser;
import com.draeger.medical.sdccc.manipulation.precondition.PreconditionException;
import com.draeger.medical.sdccc.manipulation.precondition.PreconditionRegistry;
import com.draeger.medical.sdccc.messages.MessageStorage;
import com.draeger.medical.sdccc.sdcri.testclient.TestClient;
import com.draeger.medical.sdccc.tests.InjectorTestBase;
import com.draeger.medical.sdccc.tests.util.PreconditionFilter;
import com.draeger.medical.sdccc.tests.util.TestDescriptionFilter;
import com.draeger.medical.sdccc.tests.util.TestEnabledFilter;
import com.draeger.medical.sdccc.util.LoggingConfigurator;
import com.draeger.medical.sdccc.util.LoggingOutputStream;
import com.draeger.medical.sdccc.util.MessageGeneratingUtil;
import com.draeger.medical.sdccc.util.MessagingException;
import com.draeger.medical.sdccc.util.TestRunInformation;
import com.draeger.medical.sdccc.util.TestRunObserver;
import com.draeger.medical.sdccc.util.TriggerOnErrorOrWorseLogAppender;
import com.draeger.medical.sdccc.util.junit.XmlReportListener;
import com.draeger.medical.sdccc.util.junit.guice.XmlReportFactory;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.name.Names;
import com.google.inject.util.Modules;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeoutException;
import javax.inject.Named;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import org.apache.commons.lang3.SystemUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.AppenderRef;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.discovery.PackageSelector;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.PostDiscoveryFilter;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.somda.sdc.common.guice.AbstractConfigurationModule;
import org.somda.sdc.dpws.soap.exception.TransportException;
import org.somda.sdc.dpws.soap.interception.InterceptorException;
import org.somda.sdc.glue.common.WsdlConstants;

/**
 * SDCcc main class.
 */
public class TestSuite {
    private static final Logger LOG = LogManager.getLogger(TestSuite.class);
    private static final Duration MAX_WAIT = Duration.ofSeconds(10);
    private static final int TIME_BETWEEN_PHASES = 10000;
    private static final String SUFFIX_DIRECT = ".direct";
    private static final String SUFFIX_INVARIANT = ".invariant";

    private final Injector injector;
    private final String[] sdcTestDirectories;
    private final File testRunDir;
    private final TestRunObserver testRunObserver;
    private final MessageGeneratingUtil messageGenerator;
    private final TestRunInformation testRunInformation;
    private final TestClient client;
    private final boolean testExecutionLogging;

    @Inject
    TestSuite(
            final Injector injector,
            final TestRunObserver testRunObserver,
            @Named(TestSuiteConfig.SDC_TEST_DIRECTORIES) final String[] sdcTestDirectories,
            @Named(TestRunConfig.TEST_RUN_DIR) final File testRunDir,
            @Named(TestSuiteConfig.TEST_EXECUTION_LOGGING) final boolean testExecutionLogging,
            final MessageGeneratingUtil messageGenerator,
            final TestRunInformation testRunInformation,
            final TestClient client) {
        this.injector = injector;
        this.sdcTestDirectories = sdcTestDirectories;
        this.testRunObserver = testRunObserver;
        this.testRunDir = testRunDir;
        this.messageGenerator = messageGenerator;
        this.testRunInformation = testRunInformation;
        this.client = client;
        this.testExecutionLogging = testExecutionLogging;
        LOG.info("Created");
    }

    /**
     * Run the test suite.
     *
     * @return number of failed tests during the run
     */
    public long runTestSuite() {
        final var directTests = collectEnabledTests(SUFFIX_DIRECT);
        final var invariantTests =
                collectEnabledTests(SUFFIX_INVARIANT, injector.getInstance(PreconditionFilter.class));

        final OutputStream consoleOutput = new LoggingOutputStream(LOG, Level.INFO, StandardCharsets.UTF_8);
        final PrintWriter outWriter = new PrintWriter(consoleOutput, false, StandardCharsets.UTF_8);

        final var directTestLauncher = createLauncher(this.testRunDir, "SDCcc_direct");
        final var invariantTestLauncher = createLauncher(this.testRunDir, "SDCcc_invariant");

        // trigger all filters
        final var directTestPlan = directTestLauncher.discover(directTests);
        final var invariantTestPlan = invariantTestLauncher.discover(invariantTests);

        final SummaryGeneratingListener invariantSummary = new SummaryGeneratingListener();
        invariantTestLauncher.registerTestExecutionListeners(invariantSummary);

        final SummaryGeneratingListener directSummary = new SummaryGeneratingListener();
        directTestLauncher.registerTestExecutionListeners(directSummary);

        final Boolean isConsumerEnabled = setupDeviceAndProvider();

        long totalTestFailures = 0L;
        /*
         * Phase 1, generate messages
         */
        phase1(isConsumerEnabled);

        /*
         * Phase 2, execute direct tests
         */
        totalTestFailures = phase2(totalTestFailures, outWriter, directTestLauncher, directTestPlan, directSummary);

        /*
         * Phase 3, invariant preconditions
         */
        phase3();

        /*
         * Phase 4, invariant tests
         */
        // stop client and provider now
        totalTestFailures =
                phase4(totalTestFailures, outWriter, invariantTestLauncher, invariantTestPlan, invariantSummary);

        // close message cache
        postProcessing();

        final TestRunObserver observer = injector.getInstance(TestRunObserver.class);
        observer.setTotalNumberOfTestsRun(directSummary.getSummary().getTestsStartedCount()
                + invariantSummary.getSummary().getTestsStartedCount());

        return totalTestFailures;
    }

    private void postProcessing() {
        LOG.info("Stopping SDCri");
        try {
            injector.getInstance(TestClient.class).stopService(MAX_WAIT);
        } catch (final TimeoutException e) {
            testRunObserver.invalidateTestRun("Could not stop the test client", e);
        }

        injector.getInstance(MessageStorage.class).close();

        if (testRunObserver.isInvalid()) {
            LOG.info("This test run has been deemed invalid, because of:");
            testRunObserver.getReasons().forEach(LOG::info);
        }
    }

    private long phase4(
            final long totalTestFailures,
            final PrintWriter outWriter,
            final Launcher invariantTestLauncher,
            final TestPlan invariantTestPlan,
            final SummaryGeneratingListener invariantSummary) {
        LOG.info("Disconnecting TestSuite Client");
        try {
            injector.getInstance(TestClient.class).disconnect();
        } catch (final TimeoutException e) {
            testRunObserver.invalidateTestRun("Could not stop the test client", e);
        }
        // TODO: Stop provider (https://github.com/Draegerwerk/SDCcc/issues/1)

        // flush all data so invariant tests run on most current data
        injector.getInstance(MessageStorage.class).flush();

        final long result =
                phase2(totalTestFailures, outWriter, invariantTestLauncher, invariantTestPlan, invariantSummary);
        LOG.debug("Had total failures of {}", totalTestFailures);
        return result;
    }

    private void phase3() {
        // flush all data so preconditions evaluate most current data
        injector.getInstance(MessageStorage.class).flush();

        final var preconditions = injector.getInstance(PreconditionRegistry.class);
        try {
            preconditions.runPreconditions();
        } catch (final PreconditionException | RuntimeException e) { // catch RuntimeException to ensure post processing
            // null pointer exception can occur after disconnect
            LOG.error("Error occurred while running preconditions", e);
            testRunObserver.invalidateTestRun("Error occurred while running preconditions", e);
        }
    }

    private long phase2(
            final long totalTestFailures,
            final PrintWriter outWriter,
            final Launcher directTestLauncher,
            final TestPlan directTestPlan,
            final SummaryGeneratingListener directSummary) {
        if (testExecutionLogging) {
            directTestLauncher.execute(directTestPlan, new TestCaseExecutionListener());
        } else {
            directTestLauncher.execute(directTestPlan);
        }
        directSummary.getSummary().printTo(outWriter);
        directSummary
                .getSummary()
                .getFailures()
                .forEach(failure -> LOG.info("{}", failure.getTestIdentifier(), failure.getException()));

        return totalTestFailures + directSummary.getSummary().getTotalFailureCount();
    }

    private void phase1(final Boolean isConsumerEnabled) {
        if (isConsumerEnabled) {
            performBasicMessagingCheck();
        }

        try {
            Thread.sleep(TIME_BETWEEN_PHASES);
        } catch (final InterruptedException e) {
            LOG.error("", e);
        }
    }

    /**
     * Sends all SDC service messages to all SDC services available on the connected provider.
     * NOTE: The Basic Message Check should not interrupt test suite execution.
     */
    private void performBasicMessagingCheck() {
        // NOTE: In theory, a full Messaging Check should
        //       - send all Requests (Request-Response interactions)
        //       - subscribe to all Reports (Publish-Subscribe interactions), and
        //       - subscribe to all Streams (Stream interactions)
        // However,
        //       - The SDC.ri client provides a watchdog service that automatically subscribes to
        //          - all episodicReports (Publish-Subscribe) and to
        //          - the waveformStream.
        //         in order to avoid duplication, we thus do not need to cover these in the Basic Messaging Check.
        //       - SDCcc (currently) ignores all periodicReports (Publish-Subscribe) since they are not
        //         specified in the SDC standards.
        //       - SDCcc (currently) ignores the observedValueStream since the meaning of "supported" in BICEPS:R5026
        //         is unclear.
        //       - SDCcc (currently) ignores the ARCHIVE service since it is deprecated and will be replaced by the
        //         new HISTORY service, soon.
        //       - the Basic Messaging Check will not send side-effecting Messages (in particular the SET service).
        // Hence,
        //       - since waveformStream and observedValueStream are the only Streams, the Basic Messaging Check
        //         will not subscribe to any Streams.
        //       - since all Reports are either episodic or periodic, the Basic Messaging Check will not subscribe to
        //         any Reports.

        // GET SERVICE (mandatory)
        try {
            messageGenerator.getMdib();
            messageGenerator.getMdDescription(List.of());
            messageGenerator.getMdState(List.of());
        } catch (final NoSuchElementException | MessagingException e) {
            // do not interrupt test suite execution
        }

        // SET SERVICE
        // NOTE: will not send side-effecting messages. See above for details.

        // DESCRIPTION EVENT SERVICE
        // NOTE: will not subscribe to Reports. See above for details.

        // STATE EVENT SERVICE
        // NOTE: will not subscribe to Reports. See above for details.

        // CONTEXT SERVICE
        if (messageGenerator.hasContextService()) {
            messageGenerator.getContextStates();

            // NOTE: GetContextStatesByIdentification and GetContextStatesByFilter should not be used anymore
            //       and will (hopefully) be removed from the standards, soon.

            // NOTE: will not subscribe to Reports. See above for details.
        }

        // WAVEFORM SERVICE
        // NOTE: will not subscribe to Streams. See above for details.

        // CONTAINMENT TREE SERVICE
        if (messageGenerator.hasContainmentTreeService()) {
            messageGenerator.getContainmentTree(List.of());
            messageGenerator.getDescriptor(List.of());
        }

        // ARCHIVE SERVICE
        // NOTE: deprecated. See above for details.

        // LOCALIZATION SERVICE
        if (messageGenerator.hasLocalizationService()) {
            messageGenerator.getLocalizedTexts();
            messageGenerator.getSupportedLanguages();
        }

        String statusline = " successfully";
        if (testRunObserver.isInvalid()) {
            statusline = " with errors";
        }
        LOG.info("SDC Basic Messaging Check completed" + statusline + ".");
    }

    private Boolean setupDeviceAndProvider() {
        final var isConsumerEnabled =
                injector.getInstance(Key.get(Boolean.class, Names.named(TestSuiteConfig.CONSUMER_ENABLE)));
        final var isProviderEnabled =
                injector.getInstance(Key.get(Boolean.class, Names.named(TestSuiteConfig.PROVIDER_ENABLE)));

        if (isConsumerEnabled) {
            LOG.info("Starting TestSuite Client");
            try {
                client.startService(MAX_WAIT);
                client.connect();
            } catch (final InterceptorException | TransportException | IOException | TimeoutException e) {
                LOG.error("Could not connect to target device {}", client.getTargetEpr());
                testRunObserver.invalidateTestRun("Could not connect to target device", e);
                throw new RuntimeException(e);
            }

            // check the DUT for an archive service, currently needed for MDPWS:R0006
            this.testRunInformation.setArchiveServicePresent(
                    client.getHostingServiceProxy().getHostedServices().values().stream()
                            .anyMatch(service ->
                                    service.getType().getTypes().contains(WsdlConstants.PORT_TYPE_ARCHIVE_QNAME)));
        }
        if (isProviderEnabled) {
            LOG.info("Starting TestSuite Provider");
            // TODO: Start provider (https://github.com/Draegerwerk/SDCcc/issues/1)
        }
        return isConsumerEnabled;
    }

    /**
     * Collect all enabled tests with the passed suffix.
     *
     * @param suffix the final part of the package path to search through, i.e. ".invariant"
     * @return launcher request loadable in a {@linkplain Launcher}
     */
    private LauncherDiscoveryRequest collectEnabledTests(final String suffix, final PostDiscoveryFilter... filters) {
        final var packages = new ArrayList<PackageSelector>();
        for (final var base : sdcTestDirectories) {
            packages.add(selectPackage(base + suffix));
        }

        final var tests = LauncherDiscoveryRequestBuilder.request()
                .selectors(packages)
                .filters(injector.getInstance(TestEnabledFilter.class), new TestDescriptionFilter());
        if (filters.length > 0) {
            // cast to ensure all elements are listed, not just the first
            LOG.debug("Registering additional test filters {}", (Object) filters);
            tests.filters(filters);
        }
        return tests.build();
    }

    /**
     * Create a new Junit {@linkplain Launcher} instance and attach default listeners to it.
     *
     * @param reportDirectory directory to write the xml reports to
     * @param reportFileName  name of the xml report file
     * @return a new configured {@linkplain Launcher} instance
     */
    private Launcher createLauncher(final File reportDirectory, final String reportFileName) {
        final Launcher launcher = LauncherFactory.create();

        final XmlReportListener xmlListener = injector.getInstance(XmlReportFactory.class)
                .createXmlReportListener(reportDirectory.toPath(), reportFileName);
        launcher.registerTestExecutionListeners(xmlListener);

        return launcher;
    }

    private static Injector createInjector(final Module... override) {
        return Guice.createInjector(Modules.override(
                        new DefaultTestSuiteModule(), new DefaultTestSuiteConfig(), new DefaultEnabledTestConfig())
                .with(override));
    }

    private static Injector createTestRunInjector(final CommandLineOptions cmdLine, final File testRunDir)
            throws IOException {

        final AbstractConfigurationModule baseConfigModule = new AbstractConfigurationModule() {
            @Override
            protected void defaultConfigure() {}
        };

        final AbstractConfigurationModule configModule = new AbstractConfigurationModule() {
            @Override
            protected void defaultConfigure() {}
        };

        final AbstractConfigurationModule testConfigModule = new AbstractConfigurationModule() {
            @Override
            protected void defaultConfigure() {}
        };

        final AbstractConfigurationModule cliOverrideModule = new AbstractConfigurationModule() {
            @Override
            protected void defaultConfigure() {
                final String epr = cmdLine.getDeviceEpr();
                if (epr != null) {
                    LOG.info("Using target provider epr from cli: {}", epr);
                    bind(TestSuiteConfig.CONSUMER_DEVICE_EPR, String.class, epr);
                }

                final String deviceFacility = cmdLine.getDeviceFacility();
                if (deviceFacility != null) {
                    LOG.info("Using target provider location facility from cli: {}", deviceFacility);
                    bind(TestSuiteConfig.CONSUMER_DEVICE_LOCATION_FACILITY, String.class, deviceFacility);
                }

                final String deviceBuilding = cmdLine.getDeviceBuilding();
                if (deviceBuilding != null) {
                    LOG.info("Using target provider location building from cli: {}", deviceBuilding);
                    bind(TestSuiteConfig.CONSUMER_DEVICE_LOCATION_BUILDING, String.class, deviceBuilding);
                }

                final String devicePointOfCare = cmdLine.getDevicePointOfCare();
                if (devicePointOfCare != null) {
                    LOG.info("Using target provider location point of care from cli: {}", devicePointOfCare);
                    bind(TestSuiteConfig.CONSUMER_DEVICE_LOCATION_POINT_OF_CARE, String.class, devicePointOfCare);
                }

                final String deviceFloor = cmdLine.getDeviceFloor();
                if (deviceFloor != null) {
                    LOG.info("Using target provider location floor from cli: {}", deviceFloor);
                    bind(TestSuiteConfig.CONSUMER_DEVICE_LOCATION_FLOOR, String.class, deviceFloor);
                }

                final String deviceRoom = cmdLine.getDeviceRoom();
                if (deviceRoom != null) {
                    LOG.info("Using target provider location room from cli: {}", deviceRoom);
                    bind(TestSuiteConfig.CONSUMER_DEVICE_LOCATION_ROOM, String.class, deviceRoom);
                }

                final String deviceBed = cmdLine.getDeviceBed();
                if (deviceBed != null) {
                    LOG.info("Using target provider location bed from cli: {}", deviceBed);
                    bind(TestSuiteConfig.CONSUMER_DEVICE_LOCATION_BED, String.class, deviceBed);
                }

                cmdLine.getIpAddress().ifPresent(ip -> {
                    LOG.info("Using adapter ip from cli: {}", ip);
                    bind(TestSuiteConfig.NETWORK_INTERFACE_ADDRESS, String.class, ip);
                });
            }
        };

        try (final var configFileStream =
                new FileInputStream(cmdLine.getConfigPath().toFile())) {
            final var configModuleParser = new TomlConfigParser(TestSuiteConfig.class);

            baseConfigModule.bind(TestSuiteConfig.CONSUMER_DEVICE_EPR, String.class, null);
            baseConfigModule.bind(TestSuiteConfig.CONSUMER_DEVICE_LOCATION_FACILITY, String.class, null);
            baseConfigModule.bind(TestSuiteConfig.CONSUMER_DEVICE_LOCATION_BUILDING, String.class, null);
            baseConfigModule.bind(TestSuiteConfig.CONSUMER_DEVICE_LOCATION_POINT_OF_CARE, String.class, null);
            baseConfigModule.bind(TestSuiteConfig.CONSUMER_DEVICE_LOCATION_FLOOR, String.class, null);
            baseConfigModule.bind(TestSuiteConfig.CONSUMER_DEVICE_LOCATION_ROOM, String.class, null);
            baseConfigModule.bind(TestSuiteConfig.CONSUMER_DEVICE_LOCATION_BED, String.class, null);

            configModuleParser.parseToml(configFileStream, configModule);
        }
        try (final var testConfigFileStream =
                new FileInputStream(cmdLine.getTestConfigPath().toFile())) {
            final var testConfigModuleParser = new TomlConfigParser(EnabledTestConfig.class);
            testConfigModuleParser.parseToml(testConfigFileStream, testConfigModule);
        }

        // cli overrides
        final var configurationModule = Modules.override(baseConfigModule)
                .with(Modules.override(configModule, testConfigModule).with(cliOverrideModule));

        return createInjector(configurationModule, new TestRunConfig(testRunDir));
    }

    private static void exit(
            final long numberOfTestFailures, final boolean hadError, final Injector injector, final File testRunDir) {

        final TestRunObserver testRunObserver = injector.getInstance(TestRunObserver.class);
        try {
            LOG.info("Stopping SDCcc");
            printVerdict(numberOfTestFailures, testRunDir, injector);

            injector.getInstance(MessageStorage.class).close();
        } catch (final RuntimeException | Error e) {

            LOG.error("Unchecked exception during cleanup", e);
            printVerdict(numberOfTestFailures, testRunDir, injector);
            System.exit(2); // exitCode != 0 to indicate Error
        }

        if (hadError || testRunObserver.isInvalid()) {
            System.exit(2); // exitCode != 0 to indicate Error
        } else if (numberOfTestFailures > 0) {
            System.exit(1); // exitCode != 0 to indicate Failure
        } else {
            System.exit(0); // exitCode == 0 to indicate Success
        }
    }

    private static void printVerdict(final long numberOfTestFailures, final File testRunDir, final Injector injector) {
        final TestRunObserver testRunObserver = injector.getInstance(TestRunObserver.class);
        final Boolean summarizeMessageEncodingErrors = injector.getInstance(
                Key.get(Boolean.class, Names.named(TestSuiteConfig.SUMMARIZE_MESSAGE_ENCODING_ERRORS)));
        final MessageStorage messageStorage = injector.getInstance(MessageStorage.class);

        if (summarizeMessageEncodingErrors) {
            final long messageEncodingErrorCount = messageStorage.getMessageEncodingErrorCount();
            if (messageEncodingErrorCount > 0) {
                testRunObserver.invalidateTestRun(String.format(
                        "During the Test run, %d messages with invalid encoding declarations were "
                                + "encountered. For more detailed information on these messages, please set "
                                + "SummarizeMessageEncodingErrors=false in the configuration.",
                        messageEncodingErrorCount));
            }
            final long invalidMimeTypeErrorCount = messageStorage.getInvalidMimeTypeErrorCount();
            if (invalidMimeTypeErrorCount > 0) {
                testRunObserver.invalidateTestRun(String.format(
                        "During the Test run, %d messages with invalid Mime Type declarations were "
                                + "encountered. For more detailed information on these messages, please set "
                                + "SummarizeMessageEncodingErrors=false in the configuration.",
                        invalidMimeTypeErrorCount));
            }
        }

        if (numberOfTestFailures == 0) {
            LOG.info(
                    "Test run with {} Tests was completed successfully. No problems were found.",
                    testRunObserver.getTotalNumberOfTestsRun());
        } else {
            LOG.info("Test run found problems. Please consult the logfiles in {} for further information.", testRunDir);
        }

        if (testRunObserver.isInvalid()) {
            LOG.info("Test run was invalid.");
        } else {
            LOG.info("Test run was valid.");
        }
    }

    /**
     * Set up the theme used for Swing-based user interactions to match the system style.
     *
     * <p>
     * Failures are not harmful, it will only mean the style is not applied.
     *
     * @throws ClassNotFoundException          if the selected style is unavailable
     * @throws UnsupportedLookAndFeelException if the selected style is unavailable
     * @throws InstantiationException          if the selected style failed
     * @throws IllegalAccessException          if the selected style could not be instantiated.
     */
    public static void setupSwingTheme()
            throws ClassNotFoundException, UnsupportedLookAndFeelException, InstantiationException,
                    IllegalAccessException {
        if (SystemUtils.IS_OS_LINUX) {
            UIManager.setLookAndFeel("com.sun.java.swing.plaf.gtk.GTKLookAndFeel");
        } else if (SystemUtils.IS_OS_WINDOWS) {
            UIManager.setLookAndFeel("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");
        } else {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        }
        LOG.debug("Swing theme set to {}", UIManager.getLookAndFeel());
    }

    /**
     * Starts SDCcc.
     *
     * @param args array of command line arguments
     */
    public static void main(final String[] args) throws IOException {
        // improve xml interaction performance
        setSystemProperties();

        // parse command line options
        final var cmdLine = new CommandLineOptions(args);

        // setup logging
        final var testRunDir = TestRunConfig.createTestRunDirectory(
                cmdLine.getTestRunDirectory().orElse(null));
        final var logConfig = LoggingConfigurator.loggerConfig(testRunDir, cmdLine.getFileLogLevel());
        checkLogConfig(logConfig);

        try (final var ignored = Configurator.initialize(logConfig)) {
            final var ctx = (LoggerContext) LogManager.getContext(false);
            ctx.setConfiguration(logConfig);
            ctx.updateLoggers();
            // we can only log this after setting up the logger
            LOG.info("Using test run directory {}", testRunDir);

            try {
                setupSwingTheme();
            } catch (final ClassNotFoundException
                    | UnsupportedLookAndFeelException
                    | InstantiationException
                    | IllegalAccessException e) {
                LOG.warn("Error while setting swing look and feel options.", e);
            }

            final Injector injector = createTestRunInjector(cmdLine, testRunDir);

            final TriggerOnErrorOrWorseLogAppender triggerOnErrorOrWorseLogAppender =
                    findTriggerOnErrorOrWorseLogAppender(logConfig);
            if (triggerOnErrorOrWorseLogAppender == null) {
                // should never happen
                throw new IllegalStateException("Could not find an TriggerOnErrorOrWorseLogAppender in the logConfig.");
            }
            triggerOnErrorOrWorseLogAppender.setOnErrorOrWorseHandler((LogEvent event) -> {
                final TestRunObserver testRunObserver = injector.getInstance(TestRunObserver.class);
                // stop observing the logs
                triggerOnErrorOrWorseLogAppender.setOnErrorOrWorseHandler(null);
                // invalidate test run
                testRunObserver.invalidateTestRun("TriggerOnErrorOrWorseLogAppender observed an ERROR or worse."
                        + " Invalidating TestRun."
                        + " Please see the Log for more Details.");
            });

            String versionString =
                    triggerOnErrorOrWorseLogAppender.getClass().getPackage().getImplementationVersion();
            if (versionString != null) {
                versionString = " version " + versionString;
            } else {
                versionString = "";
            }
            LOG.info("Starting SDCcc {}", versionString);

            try {

                InjectorTestBase.setInjector(injector);
                final var testSuite = injector.getInstance(TestSuite.class);
                TestSuite.exit(testSuite.runTestSuite(), false, injector, testRunDir);
            } catch (final RuntimeException | Error e) {

                LOG.error("Unchecked exception while setting up or running the TestSuite", e);
                TestSuite.exit(0, true, injector, testRunDir);
            }
        }
    }

    private static void setSystemProperties() {
        System.setProperty(
                "javax.xml.xpath.XPathFactory:http://java.sun.com/jaxp/xpath/dom",
                "com.sun.org.apache.xpath.internal.jaxp.XPathFactoryImpl");
        System.setProperty(
                "javax.xml.stream.XMLEventFactory", "com.sun.xml.internal.stream.events.XMLEventFactoryImpl");
        System.setProperty("javax.xml.stream.XMLInputFactory", "com.sun.xml.internal.stream.XMLInputFactoryImpl");
        System.setProperty(
                "javax.xml.parsers.DocumentBuilderFactory",
                "com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderFactoryImpl");
        System.setProperty(
                "javax.xml.validation.SchemaFactory:http://www.w3.org/2001/XMLSchema",
                "com.sun.org.apache.xerces.internal.jaxp.validation.XMLSchemaFactory");
        System.setProperty(
                "javax.xml.parsers.SAXParserFactory", "com.sun.org.apache.xerces.internal.jaxp.SAXParserFactoryImpl");
        System.setProperty("javax.xml.transform.TransformerFactory", "net.sf.saxon.TransformerFactoryImpl");
    }

    private static TriggerOnErrorOrWorseLogAppender findTriggerOnErrorOrWorseLogAppender(
            final BuiltConfiguration logConfig) {
        for (Map.Entry<String, Appender> entry : logConfig.getAppenders().entrySet()) {
            final Appender appender = entry.getValue();
            if (appender instanceof TriggerOnErrorOrWorseLogAppender) {
                return (TriggerOnErrorOrWorseLogAppender) appender;
            }
        }
        return null;
    }

    private static void checkLogConfig(final BuiltConfiguration logConfig) {
        int count = 0;
        String triggerOnErrorOrWorseLogAppenderName = "";

        // 1. There must be exactly one TriggerOnErrorOrWorseLogAppender
        for (Map.Entry<String, Appender> entry : logConfig.getAppenders().entrySet()) {
            final Appender appender = entry.getValue();
            if (appender instanceof TriggerOnErrorOrWorseLogAppender) {
                count += 1;
                triggerOnErrorOrWorseLogAppenderName = entry.getKey();
            }
        }

        if (count != 1) {
            throw new IllegalStateException("Precondition violated: There must be exactly 1"
                    + "TriggerOnErrorOrWorseLogAppender in the Log4j config.");
        }

        // 2. Each Logger must append to the TriggerOnErrorOrWorseLogAppender
        for (Map.Entry<String, LoggerConfig> entry : logConfig.getLoggers().entrySet()) {
            boolean found = false;
            for (AppenderRef ref : entry.getValue().getAppenderRefs()) {
                if (triggerOnErrorOrWorseLogAppenderName.equals(ref.getRef())) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                throw new IllegalStateException(String.format(
                        "Precondition violated: Logger '%s' does not append to the"
                                + " TriggerOnErrorOrWorseLogAppender '%s'",
                        entry.getKey(), triggerOnErrorOrWorseLogAppenderName));
            }
        }
    }

    private static class TestCaseExecutionListener implements TestExecutionListener {

        @Override
        public void executionStarted(final TestIdentifier testIdentifier) {
            if (testIdentifier.isTest()) {
                final TestSource testSource = testIdentifier.getSource().orElse(null);
                if (testSource instanceof MethodSource methodSource) {
                    try {
                        final var testClass = ClassLoader.getSystemClassLoader().loadClass(methodSource.getClassName());
                        final var testMethod = Arrays.stream(testClass.getDeclaredMethods())
                                .filter(method -> method.getName().equals(methodSource.getMethodName()))
                                .findAny()
                                .orElseThrow();
                        final var testIdentifierAnnotation = testMethod.getAnnotation(
                                com.draeger.medical.sdccc.tests.annotations.TestIdentifier.class);
                        LOG.info("Test case for Requirement {} started", testIdentifierAnnotation.value());
                    } catch (ClassNotFoundException | NoSuchElementException e) {
                        LOG.info(
                                "Error while logging test case execution for method {} ",
                                methodSource.getMethodName(),
                                e);
                    }
                }
            }
        }

        @Override
        public void executionFinished(
                final TestIdentifier testIdentifier, final TestExecutionResult testExecutionResult) {
            if (testIdentifier.isTest()) {
                final TestSource testSource = testIdentifier.getSource().orElse(null);
                if (testSource instanceof MethodSource methodSource) {
                    try {
                        final var testClass = ClassLoader.getSystemClassLoader().loadClass(methodSource.getClassName());
                        final var testMethod = Arrays.stream(testClass.getDeclaredMethods())
                                .filter(method -> method.getName().equals(methodSource.getMethodName()))
                                .findAny()
                                .orElseThrow();
                        final var testIdentifierAnnotation = testMethod.getAnnotation(
                                com.draeger.medical.sdccc.tests.annotations.TestIdentifier.class);
                        LOG.info(
                                "Test case for Requirement {} finished with result {}",
                                testIdentifierAnnotation.value(),
                                testExecutionResult.getStatus());
                    } catch (ClassNotFoundException | NoSuchElementException e) {
                        LOG.info(
                                "Error while logging test case execution for method {} ",
                                methodSource.getMethodName(),
                                e);
                    }
                }
            }
        }
    }
}
