/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2023, 2024 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.configuration;

import edu.umd.cs.findbugs.annotations.Nullable;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Optional;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Command line option parser.
 */
public class CommandLineOptions {
    private static final Logger LOG = LogManager.getLogger(CommandLineOptions.class);

    private static final String CONFIG_OPT = "config";
    private static final String TEST_CONFIG_OPT = "testconfig";
    private static final String TEST_PARAMETER_OPT = "testparam";
    private static final String DEVICE_EPR = "device_epr";
    private static final String DEVICE_LOCATION_FACILITY = "device_facility";
    private static final String DEVICE_LOCATION_BUILDING = "device_building";
    private static final String DEVICE_LOCATION_POINT_OF_CARE = "device_point_of_care";
    private static final String DEVICE_LOCATION_FLOOR = "device_floor";
    private static final String DEVICE_LOCATION_ROOM = "device_room";
    private static final String DEVICE_LOCATION_BED = "device_bed";
    private static final String IP_ADDRESS = "ipaddress";
    private static final String TEST_RUN_DIRECTORY = "test_run_directory";
    private static final String NO_SUBDIRECTORIES = "no_subdirectories";
    private static final String FILE_LOG_LEVEL = "file_log_level";
    private final Path configPath;
    private final Path testConfigPath;
    private final Path testParameterPath;
    private final String deviceEpr;
    private final String deviceFacility;
    private final String deviceBuilding;
    private final String devicePointOfCare;
    private final String deviceFloor;
    private final String deviceRoom;
    private final String deviceBed;
    private final String ipAddress;
    private final String testRunDirectory;
    private final Boolean noSubdirectories;
    private final Level fileLogLevel;

    /**
     * Parse the command line options passed.
     *
     * @param commandLineArguments array of commandline options, as usually passed to a main function.
     */
    @SuppressFBWarnings(
            value = {"DM_EXIT"},
            justification = "Invalid arguments must lead to a halt.")
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
        final var testParameter = cmd.getOptionValue(TEST_PARAMETER_OPT);
        this.configPath = Path.of(cmd.getOptionValue(CONFIG_OPT));
        this.testConfigPath = Path.of(cmd.getOptionValue(TEST_CONFIG_OPT));
        this.testParameterPath = testParameter != null ? Path.of(testParameter) : null;
        this.deviceEpr = cmd.getOptionValue(DEVICE_EPR);
        this.deviceFacility = cmd.getOptionValue(DEVICE_LOCATION_FACILITY);
        this.deviceBuilding = cmd.getOptionValue(DEVICE_LOCATION_BUILDING);
        this.devicePointOfCare = cmd.getOptionValue(DEVICE_LOCATION_POINT_OF_CARE);
        this.deviceFloor = cmd.getOptionValue(DEVICE_LOCATION_FLOOR);
        this.deviceRoom = cmd.getOptionValue(DEVICE_LOCATION_ROOM);
        this.deviceBed = cmd.getOptionValue(DEVICE_LOCATION_BED);
        this.ipAddress = cmd.getOptionValue(IP_ADDRESS);
        this.testRunDirectory = cmd.getOptionValue(TEST_RUN_DIRECTORY);
        this.noSubdirectories = Boolean.parseBoolean(cmd.getOptionValue(NO_SUBDIRECTORIES));
        this.fileLogLevel = Level.toLevel(cmd.getOptionValue(FILE_LOG_LEVEL), Level.INFO);
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
            final String description = "test parameter file path";
            final var parameterFilePath = new Option("p", TEST_PARAMETER_OPT, true, description);
            parameterFilePath.setRequired(false);
            options.addOption(parameterFilePath);
        }
        {
            final String description = "EPR of the target provider, overrides setting from configuration if provided";
            final var deviceEprOpt = new Option("de", DEVICE_EPR, true, description);
            deviceEprOpt.setRequired(false);
            options.addOption(deviceEprOpt);
        }
        {
            final String description =
                    "Facility of the target provider, overrides setting from configuration if provided";
            final var deviceLocOpt = new Option("fac", DEVICE_LOCATION_FACILITY, true, description);
            deviceLocOpt.setRequired(false);
            options.addOption(deviceLocOpt);
        }
        {
            final String description =
                    "Building of the target provider, overrides setting from configuration if provided";
            final var deviceLocOpt = new Option("bldng", DEVICE_LOCATION_BUILDING, true, description);
            deviceLocOpt.setRequired(false);
            options.addOption(deviceLocOpt);
        }
        {
            final String description =
                    "Point of care of the target provider, overrides setting from configuration if provided";
            final var deviceLocOpt = new Option("poc", DEVICE_LOCATION_POINT_OF_CARE, true, description);
            deviceLocOpt.setRequired(false);
            options.addOption(deviceLocOpt);
        }
        {
            final String description = "Floor of the target provider, overrides setting from configuration if provided";
            final var deviceLocOpt = new Option("flr", DEVICE_LOCATION_FLOOR, true, description);
            deviceLocOpt.setRequired(false);
            options.addOption(deviceLocOpt);
        }
        {
            final String description = "Room of the target provider, overrides setting from configuration if provided";
            final var deviceLocOpt = new Option("rm", DEVICE_LOCATION_ROOM, true, description);
            deviceLocOpt.setRequired(false);
            options.addOption(deviceLocOpt);
        }
        {
            final String description = "Bed of the target provider, overrides setting from configuration if provided";
            final var deviceLocOpt = new Option("bed", DEVICE_LOCATION_BED, true, description);
            deviceLocOpt.setRequired(false);
            options.addOption(deviceLocOpt);
        }
        {
            final String description = "IP address of the adapter to use for communication,"
                    + " overrides setting from configuration if provided";
            final var consumerTargetIpOpt = new Option("ip", IP_ADDRESS, true, description);
            consumerTargetIpOpt.setRequired(false);
            options.addOption(consumerTargetIpOpt);
        }
        {
            final String description = "Base directory to store test runs in, creates a timestamped SDCcc run"
                    + " directory inside the base directory. Defaults to current working directory as base.";
            final var testRunDirectoryOpt = new Option("d", TEST_RUN_DIRECTORY, true, description);
            testRunDirectoryOpt.setRequired(false);
            options.addOption(testRunDirectoryOpt);
        }
        {
            final String description =
                    "If set to true creates no timestamped SDCcc run directory inside the test run directory"
                            + " directory.";
            final var noSubdirectoriesOpt = new Option("ns", NO_SUBDIRECTORIES, true, description);
            noSubdirectoriesOpt.setRequired(false);
            noSubdirectoriesOpt.setType(Boolean.class);
            options.addOption(noSubdirectoriesOpt);
        }
        {
            final String description = "The log level to be used for the log file. e.g. DEBUG . The default is INFO.";
            final var fileLogLevelOpt = new Option("fll", FILE_LOG_LEVEL, true, description);
            fileLogLevelOpt.setRequired(false);
            options.addOption(fileLogLevelOpt);
        }

        return options;
    }

    public Path getConfigPath() {
        return configPath;
    }

    public Path getTestConfigPath() {
        return testConfigPath;
    }

    @Nullable
    public Path getTestParameterPath() {
        return testParameterPath;
    }

    @Nullable
    public String getDeviceEpr() {
        return deviceEpr;
    }

    @Nullable
    public String getDeviceFacility() {
        return deviceFacility;
    }

    @Nullable
    public String getDeviceBuilding() {
        return deviceBuilding;
    }

    @Nullable
    public String getDevicePointOfCare() {
        return devicePointOfCare;
    }

    @Nullable
    public String getDeviceFloor() {
        return deviceFloor;
    }

    @Nullable
    public String getDeviceRoom() {
        return deviceRoom;
    }

    @Nullable
    public String getDeviceBed() {
        return deviceBed;
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

    public Boolean getNoSubdirectories() {
        return noSubdirectories;
    }

    public Level getFileLogLevel() {
        return this.fileLogLevel;
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
                    networkInterface.isVirtual());
            final Iterator<InetAddress> inetAddressIterator =
                    networkInterface.getInetAddresses().asIterator();
            int i = 0;
            while (inetAddressIterator.hasNext()) {
                final var addr = inetAddressIterator.next();
                System.out.printf("\t\tAddress[%s]: %s%n", i++, addr);
            }
            System.out.println("");
        }
    }
}
