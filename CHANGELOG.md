# Changelog
All notable changes to SDCcc will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- a command line parameter to change the log level threshold of the log file

### Fixed

- a performance issue with xml unmarshalling due to finding an interface implementation for TransformerFactory
- slowdown and high memory usage due to large unfiltered logs, filter can be configured, default shrinks the log
- autocommit of the database being on, it is now always off
- local address resolver sometimes giving a wrong address
- local address resolver doing a probe which may not be tolerated by some peers
- potential NullPointerException in DescriptionModificationUptPrecondition
- ReportWriter.write() could be called with ReportTypes it did not support.

## [8.0.1] - 2023-09-13

### Fixed
- test failures are treated as errors

## [8.0.0] - 2023-09-12

### Added
- location query based discovery
- requirement test execution logging
- test for glue:R0056
- configuration option to change the maximum waiting time during connection establishment
- SDCcc version is written to the log and into the result XML files.
- message encoding errors are summarized by default, but all information is available via the configuration option
  SDCcc.SummarizeMessageEncodingErrors.
- checking for message encoding errors can be disabled using the configuration option SDCcc.EnableMessageEncodingCheck.
  - when enabled, SDCcc also checks if message bodies can be decoded using the encoding specified.
- configuration option SDCcc.Network.MulticastTTL to configure the Time To Live of the Multicast Packets used for Discovery.
- precondition for biceps:R5024 to trigger description modification reports for test data

### Changed
- the precondition of biceps:R0133 so that it is now easier to satisfy.
- the precondition of biceps:R0034_0 so that it is now easier to satisfy.
- the precondition of biceps:C5 and biceps:R5052 so that it can be satisfied by devices that do not support updating
  MDS descriptors.
- SDCri version 5.0.0
- t2iapi version 3.0.0

### Fixed
- the occurrence of the sequence ']]>' in the content of CDATA sections in the result xmls
- setComponentActivation stores incorrect manipulation data in the database resulting in no test data being available for 5-4-7_* tests
- that preconditions of the following requirements could not handle the T2IAPI manipulation result RESULT_NOT_SUPPORTED: biceps:R0029_0 biceps:R0116 biceps:5-4-7_0_0 biceps:5-4-7_1 biceps:5-4-7_2 biceps:5-4-7_3 biceps:5-4-7_4 biceps:5-4-7_5 biceps:5-4-7_6_0 biceps:5-4-7_7 biceps:5-4-7_8 biceps:5-4-7_9 biceps:5-4-7_10 biceps:5-4-7_11 biceps:5-4-7_12_0 biceps:5-4-7_13 biceps:5-4-7_14 biceps:5-4-7_15 biceps:5-4-7_16 biceps:5-4-7_17
- biceps:R0034_0 does not track changes when reinserting descriptors.
- report duplication issue in biceps:C11-C15, biceps:C5, biceps:R5046_0, biceps:B-284_0 as well as biceps:R5003.
- biceps:5-4-7 tests confusing changes made by SetComponentActivation manipulations with changes made by SetMetricStatus.
- glue:R0036_0 test can be blocked by long-running t2iapi RPCs.
- biceps:5025_0 test case could not be satisfied by devices that do not support inserting and removing descriptors.
- SDCcc previously terminated with exitCode 0 despite certain Errors.

## [7.0.1] - 2023-03-17

### Fixed
- biceps:C-5, biceps:C-11, biceps:C-12, biceps:C-13, biceps:C-14, biceps:C-15, biceps:R5046_0, biceps:B-6_0 failing when reports received before the initial mdib are applied

## [7.0.0] - 2023-03-15

### Added
- test for biceps:5-4-7_1
- test for biceps:5-4-7_3
- test for biceps:5-4-7_5
- test for biceps:5-4-7_7
- test for biceps:5-4-7_9
- test for biceps:5-4-7_11
- test for biceps:5-4-7_13
- test for biceps:5-4-7_15
- test for biceps:5-4-7_17
- test for biceps:R0055_0
- support for SystemErrorReport to test case glue:R0036_0
- test for biceps:R5053
- test for biceps:R0098_0
- test for biceps:R5046_0
- test for biceps:C-7

### Changed
- t2iapi version to 2.0.0
- SDCri version 4.0.0

### Fixed
- dpws:R0013 sent a request that was not WS-Transfer compliant

### Removed
- test for biceps:B-61.
 
## [6.0.0] - 2022-12-08

### Added
- references to standards to test_configuration.toml
- support for SystemErrorReport to test case glue:R0036_0
- Java 17 support
- test for biceps:5-4-7_12_0
- test for biceps:5-4-7_14
- test for biceps:5-4-7_16
- spotless plugin
- test for glue:8-1-3
- test for glue:R0078_0
- test for biceps:c-5

### Changed
- checkstyle plugin scope via checkstyle.xml
- updated test case for glue:R0036 to glue:R0036_0
- update to SDCri 3.0.0

### Fixed
- missing request bodies in glue:R0036 test
- some vulnerabilities due to old dependencies
- biceps:5-4-7 tests to check waveform streams as well

## [5.0.0] - 2022-10-27
### Added
- previously closed source code
