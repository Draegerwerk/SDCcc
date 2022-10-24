/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.configuration;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Optional;

/**
 * Command line option parser.
 */
public class CommandLineOptions {
    private static final Logger LOG = LogManager.getLogger(CommandLineOptions.class);

    private static final String CONFIG_OPT = "config";
    private static final String TEST_CONFIG_OPT = "testconfig";
    private static final String DEVICE_EPR = "device_epr";
    private static final String IP_ADDRESS = "ipaddress";
    private static final String TEST_RUN_DIRECTORY = "test_run_directory";
    private final Path configPath;
    private final Path testConfigPath;
    private final String deviceEpr;
    private final String ipAddress;
    private final String testRunDirectory;

    /**
     * Parse the command line options passed.
     *
     * @param commandLineArguments array of commandline options, as usually passed to a main function.
     */
    @SuppressFBWarnings(value = {"DM_EXIT"}, justification = "Invalid arguments must lead to a halt.")
    public CommandLineOptions(final String[] commandLineArguments) {

        final var options = setupOptions();
        final var parser = new DefaultParser();
        final var help = new HelpFormatter();

        CommandLine cmd = null;

        try {
            cmd = parser.parse(options, commandLineArguments);
        } catch (final ParseException e) {
            System.out.println(e.getMessage());
            help.printHelp("SDCcc", options);

            try {
                printNetworkAdapterInformation();
            } catch (final SocketException e2) {
                LOG.error("Error while printing network adapter info", e2);
                throw new RuntimeException(e2);
            }
            System.exit(1);
        }

        assert cmd != null;
        this.configPath = Path.of(cmd.getOptionValue(CONFIG_OPT));
        this.testConfigPath = Path.of(cmd.getOptionValue(TEST_CONFIG_OPT));
        this.deviceEpr = cmd.getOptionValue(DEVICE_EPR);
        this.ipAddress = cmd.getOptionValue(IP_ADDRESS);
        this.testRunDirectory = cmd.getOptionValue(TEST_RUN_DIRECTORY);
    }

    private Options setupOptions() {
        final var options = new Options();

        {
            final String description = "configuration file path";
            final var configFilePath = new Option("c", CONFIG_OPT, true, description);
            configFilePath.setRequired(true);
            options.addOption(configFilePath);
        }
        {
            final String description = "enabled tests configuration file path";
            final var testConfigFilePath = new Option("t", TEST_CONFIG_OPT, true, description);
            testConfigFilePath.setRequired(true);
            options.addOption(testConfigFilePath);
        }
        {
            final String description = "EPR of the target provider, overrides setting from configuration if provided";
            final var deviceEprOpt = new Option("de", DEVICE_EPR, true, description);
            deviceEprOpt.setRequired(false);
            options.addOption(deviceEprOpt);
        }
        {
            final String description = "IP address of the adapter to use for communication,"
                + " overrides setting from configuration if provided";
            final var consumerTargetEprOpt = new Option("ip", IP_ADDRESS, true, description);
            consumerTargetEprOpt.setRequired(false);
            options.addOption(consumerTargetEprOpt);
        }
        {
            final String description = "Base directory to store test runs in, creates a timestamped SDCcc run"
                + " directory inside the base directory. Defaults to current working directory as base.";
            final var testRunDirectoryOpt = new Option("d", TEST_RUN_DIRECTORY, true, description);
            testRunDirectoryOpt.setRequired(false);
            options.addOption(testRunDirectoryOpt);
        }

        return options;
    }

    public Path getConfigPath() {
        return configPath;
    }

    public Path getTestConfigPath() {
        return testConfigPath;
    }

    /**
     * @return epr the consumer searches for, provided via cli, empty if not set
     */
    public Optional<String> getDeviceEpr() {
        return Optional.ofNullable(deviceEpr);
    }

    /**
     * @return ip address of the adapter to use for communication, provided via cli, empty if not set
     */
    public Optional<String> getIpAddress() {
        return Optional.ofNullable(ipAddress);
    }

    /**
     * @return directory to store test run data in, provided via cli, empty if not set
     */
    public Optional<String> getTestRunDirectory() {
        return Optional.ofNullable(testRunDirectory);
    }

    private static void printNetworkAdapterInformation() throws SocketException {
        System.out.println("%nAvailable network adapters are:%n");
        final Iterator<NetworkInterface> networkInterfaceIterator =
                NetworkInterface.getNetworkInterfaces().asIterator();
        while (networkInterfaceIterator.hasNext()) {
            final NetworkInterface networkInterface = networkInterfaceIterator.next();
            System.out.printf(
                    "\tNetwork interface: %s [isUp=%s;isLoopBack=%s,supportsMulticast=%s,MTU=%s,isVirtual=%s]%n",
                    networkInterface.getName(),
                    networkInterface.isUp(),
                    networkInterface.isLoopback(),
                    networkInterface.supportsMulticast(),
                    networkInterface.getMTU(),
                    networkInterface.isVirtual()
            );
            final Iterator<InetAddress> inetAddressIterator = networkInterface.getInetAddresses().asIterator();
            int i = 0;
            while (inetAddressIterator.hasNext()) {
                final var addr = inetAddressIterator.next();
                System.out.printf(
                        "\t\tAddress[%s]: %s%n",
                        i++,
                        addr
                );
            }
            System.out.println("");
        }
    }
}