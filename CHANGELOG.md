# Changelog
All notable changes to SDCcc will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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
- support for SystemErrorReport to test case glue:R0036_0
- test for biceps:R5053

### Fixed
- dpws:R0013 sent a request that was not WS-Transfer compliant
 
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
