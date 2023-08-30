# SDCcc
This test tool aims to evaluate the conformity of medical devices with the ISO/IEEE 11073 SDC standard family.

[More information on SDC.](https://en.wikipedia.org/wiki/IEEE_11073_service-oriented_device_connectivity)

## Introduction of the Project and Test approach
The test tool assumes the Role of an SDC Service Consumer to connect to the device under test (DUT) and interacts with
the device during the test run. To use the test tool, a one-to-one connection between the test tool and the DUT is
required, i.e. via an isolated network where only the DUT and the test tool are connected.
All offered reports and streams are subscribed to, and all inbound and outbound messages exchanged are stored in a database.

The requirement tests are divided into two categories. *Direct* tests and *invariant* tests.

When executing the direct tests, there is still a connection to the provider (DUT) and they cover the requirements that
need direct interaction with the device. The conformity to the respective requirement is evaluated using a controlled
message exchange.

The invariant tests are executed after the connection has been terminated. These use the stored
messages as a test basis and cover, for example, requirements that ensure that a certain condition is always fulfilled
during operation. To ensure invariant tests have sufficient data to run on, they can declare preconditions which will be
fulfilled before disconnecting from the DUT.

The test run is broadly divided into four phases. During the first phase, the test tool attempts the execution of each
remote procedure call declared by the DUT, except for calls to the SetService.
If no errors occur and the provider behaves correctly, the message `SDC Basic Messaging Check completed successfully.`
is printed at the end of phase one, otherwise the message says `SDC Basic Messaging Check completed with errors.`.
During the second phase, the direct tests are performed. The third phase checks whether all preconditions for the invariant
tests are fulfilled. If this is not the case, the corresponding messages are triggered or manipulations are performed
before the connection is terminated. The invariant tests are executed in the fourth phase.

## Test Consumer Configuration
The test consumer can be configured for the test run by modifying the *config.toml* file. It is located in the
*configuration* directory.

### TLS Configuration
TLS is **mandatory** to use the test tool.

All files required for TLS must be located in the same directory.
The configuration folder contains the *config.toml* file, in which the path to that directory can be set under
`FileDirectory`.
The password for the KeyStore (`KeyStorePassword`), TrustStore(`TrustStorePassword`) or for the Private Key
(`ParticipantPrivatePassword`) must also be set in the *config.toml*.

The test tool first searches for a keystore file, if no keystore file is available, it continues to search for a
public and a private key.

The test tool also first searches for a truststore file, if no truststore file is available, it continues to search for
a ca_certificate file.

A specific naming is required for all tls related files:

| **file**       | **naming**              | 
|----------------|-------------------------|
| KeyStore       | keystore.pkcs12         |
| TrustStore     | truststore.pkcs12       | 
| Public Key     | participant_public.pem  | 
| Private Key    | participant_private.pem |
| CA certificate | ca_certificate.pem      |

Different combinations can be used to establish a connection:
* keystore and truststore
* keystore and ca_certificate
* participant_public, participant_private and ca_certificate
* participant_public, participant_private and truststore

### Network setup
To select the network interface that should be used, the interface address can be set under
```
[SDCcc.Network] 
InterfaceAddress="interfaceAddress"`
```

The maximum waiting time in seconds to find and connect to the target device.
```
[SDCcc.Network]
MaxWait=timeInSeconds
```

The time to live (the number of routers an IP packet may pass before it is discarded) of multicast packets used for
Discovery defaults to 128. When other values are needed, it can be configured using the following option
```
[SDCcc.Network]
MulticastTTL=196
```

### Target Device (DUT) configuration
In order for the test tool to connect to the DUT, appropriate filter criteria have to be set.
It is possible to combine the following filter criteria:

- DeviceEpr
- DeviceLocationFacility
- DeviceLocationBuilding
- DeviceLocationPointOfCare
- DeviceLocationFloor
- DeviceLocationRoom
- DeviceLocationBed

All of them are optional. 
In case that all of them are not set, the first device encountered will be connected to.
In case at least one of them is set, 
all the given filter criteria have to be fulfilled for initiating a connection.

For example the configuration
```
[SDCcc.Consumer] 
Enable=true
DeviceEpr="urn:uuid:857bf583-8a51-475f-a77f-d0ca7de69b11"
```
will make only those devices match during discovery that have the EPR "urn:uuid:857bf583-8a51-475f-a77f-d0ca7de69b11",
the configuration
```
[SDCcc.Consumer] 
Enable=true
DeviceEpr="urn:uuid:857bf583-8a51-475f-a77f-d0ca7de69b11"
DeviceLocationBed="bed32"
```
only those that have the EPR "urn:uuid:857bf583-8a51-475f-a77f-d0ca7de69b11" and the bed "bed32" in the location query,
the configuration
```
[SDCcc.Consumer] 
Enable=true
DeviceLocationBed="bed32"
```
only those that have the bed "bed32" in the location query,
and the configuration
```
[SDCcc.Consumer]
Enable=true
```
will make all devices match during discovery.

### Manipulation API
The test tool uses *T2IAPI* version `2.0.0`. The *T2IAPI* is required for some test cases to put the DUT in a certain
state, or to trigger a certain behavior. When using SDCcc with automated manipulations, it must be ensured that the same
version of *T2IAPI* is used for the test execution by both parties. It must also be ensured that the device's 
manipulations are implemented according to the descriptions in the T2IAPI sources. Further information can be found 
in the changelog.

If an automated manipulation is not possible, the fallback manipulation takes effect. For each manipulation a fallback
manipulation must be provided. A graphical user interface is displayed and the user can confirm that the
manipulation was performed manually or reject performing it. The fallback manipulation can also be performed via the command-line interface,
when *GraphicalPopups* is disabled:
```
[SDCcc] 
GraphicalPopups=false
```

To see which requirement test requires which manipulation, see Section **Which Manipulation is required for which test**.

### CI Mode
In order to prevent the need for user interaction, the test tool can be run in continuous integration mode.
This mode can be enabled under
```
[SDCcc] 
CIMode = true
```
A test fails in CI mode if this test requires manipulations and these have not been automated by the test engineer.

### Further Configuration Options

```
[SDCcc] 
TestExecutionLogging=true
```

TestExecutionLogging can be enabled, to get more information on which test case is currently executed. When enabled,
SDCcc will log when a test case for a requirement has started and finished.


```
[SDCcc] 
EnableMessageEncodingCheck=true
SummarizeMessageEncodingErrors=true
```

EnableMessageEncodingCheck defaults to true and allows the user to control whether SDCcc checks the encoding
and mimeType specified in the messages received from the DUT. Note that disabling the MessageEncodingCheck
causes SDCcc to decode all messages as UTF-8. 

SummarizeMessageEncodingErrors defaults to true and allows the user to control how encoding and mimeType problems
are presented during an SDCcc TestRun. Note that devices that have encoding problems usually produce these errors
in high numbers. When this option is set to true, then the errors will not be displayed individually, but summarized
at the end. When the option is set to false, then the individual errors are displayed, which is useful for fixing
these problems.

## Running SDCcc
The following command line options are supported by the test tool, the first two need to be provided.

| **Option**           | **Short** | **Argument**                                                                                         | **Required** | 
|----------------------|-----------|------------------------------------------------------------------------------------------------------|--------------|
| config               | c         | path to the *config.toml*                                                                            | yes          |
| testconfig           | t         | path to the *test_configuration.toml*                                                                | yes          |
| device_epr           | de        | the epr of the target provider, overrides setting from configuration if provided                     | no           |
| device_facility      | fac       | the facility of the target provider, overrides setting from configuration if provided                | no           |
| device_building      | bldng     | the building of the target provider, overrides setting from configuration if provided                | no           |
| device_point_of_care | poc       | the point of care of the target provider, overrides setting from configuration if provided           | no           |
| device_floor         | flr       | the floor of the target provider, overrides setting from configuration if provided                   | no           |
| device_room          | rm        | the room of the target provider, overrides setting from configuration if provided                    | no           |
| device_bed           | bed       | the bed of the target provider, overrides setting from configuration if provided                     | no           |
| ipaddress            | ip        | ip address of the adapter to use for communication, overrides setting from configuration if provided | no           |
| test_run_directory   | d         | base directory to store test runs in, creates a timestamped SDCcc run                                | no           |

### Enabling Tests
The *test_configuration.toml* file contains the identifiers of all implemented requirement tests. It is located in the
configuration directory. These are grouped according to the SDC standards. Replaced or derived requirements are marked
with an underscore, e.g. instead of BICEPS `R0025` it would be `R0025_0`.

To disable an unwanted test for the test run, it can simply be set to
*false*.

E.g.: to disable BICEPS R0021 set
```
[BICEPS] 
R0021=true
``` 
to
```
[BICEPS] 
R0021=false
```

### Where to find results
After a test run, a folder *testruns* is created in the same directory as the executable by default. The directory can
be changed with the command line argument `test_run_directory` (see Section **Running SDCcc**) and the results are
saved in that folder. Each test run gets its own directory named according to the scheme
*SDCcc-Testrun_YYYY-MM-DDTHH-mm-SS*. Inside this directory there is a subdirectory *Database* and three files
*SDCcc.log*, *TEST-SDCcc_direct.xml* and *TEST-SDCcc_invariant.xml*. *Database* is a database in which all messages
exchanged during the test run are recorded. *SDCcc.log* is the complete log file of the test run. The test results
are located in the two result XML files, *TEST-SDCcc_direct.xml* for the direct tests and *TEST-SDCcc_invariant.xml*
for the invariant tests.

## Prerequisites for building SDCcc
Maven >= 3.8.1 and Java 17 are required to build the project.

## Limitations
The test tool has the following limitations. If the DUT falls under these limitations, the test tool **cannot** be used.
Where it is possible to detect when a DUT falls under these limitations, SDCcc's test cases are designed to fail in
this case in order to minimize the risk of such an invalid application going unnoticed.

[General]

| **Limitation**                                                                                                                                                                                           |
|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| The SDCcc tool does not support the following HTTP Headers in test cases which use messages stored in the database: <ul><li>transfer-encoding</li><li>content-length</li><li>content-encoding</li></ul>  |
| The ArchiveService is not supported and will be ignored by the test tool.                                                                                                                                |
| Safe data transmission (MDPWS Ch. 9) is not supported                                                                                                                                                    |
| Multipart/Related Content-Type is not supported                                                                                                                                                          |

[MDPWS]

| **Limitation**                                                                                                    | **Affected Requirements** |
|-------------------------------------------------------------------------------------------------------------------|---------------------------|
| WSDLs embedded in {http\://schemas.xmlsoap.org/ws/2004/09/mex}MetadataReference entries are currently unsupported | R0010, R0011, R0014       |
| {http\://www\.w3.org/ns/ws-policy}PolicyURIs checking is not supported                                            | R0010, R0011              |
| ArchiveService messages are not supported                                                                         | R0006                     |

## Which Manipulation is required for which test
[BICEPS]

| **Requirement** | **T2IAPI Manipulation**                                                                     | 
|-----------------|---------------------------------------------------------------------------------------------|
| R0025_0         | SetComponentActivation                                                                      |
| R0029_0         | SetAlertActivation, SetAlertConditionPresence                                               |
| R0033           | GetRemovableDescriptorsOfClass, RemoveDescriptor, InsertDescriptor                          |
| R0034_0         | TriggerReport                                                                               |
| R0038_0         | TriggerReport                                                                               |
| R0055_0         | GetRemovableDescriptorsOfClass, RemoveDescriptor, InsertDescriptor                          |
| R0097           | CreateContextStateWithAssociation                                                           |
| R0098_0         | GetRemovableDescriptorsOfClass, RemoveDescriptor, InsertDescriptor                          |
| R0116           | SetAlertActivation                                                                          |
| R0124           | CreateContextStateWithAssociation                                                           |
| R0125           | CreateContextStateWithAssociation                                                           |
| R0133           | CreateContextStateWithAssociation                                                           |
| B-128           | SetSystemSignalActivation, SetAlertActivation                                               |
| C-5             | TriggerAnyDescriptorUpdate                                                                  |
| C-7             | GetRemovableDescriptorsOfClass, RemoveDescriptor, InsertDescriptor, TriggerDescriptorUpdate |
| C-11            | TriggerReport                                                                               |
| C-12            | TriggerReport                                                                               |
| C-13            | TriggerReport                                                                               |
| C-14            | TriggerReport                                                                               |
| C-15            | TriggerReport                                                                               |
| R5024           | TriggerReport                                                                               |
| R5025_0         | GetRemovableDescriptorsOfClass, RemoveDescriptor, InsertDescriptor                          |
| R5046_0         | GetRemovableDescriptorsOfClass, RemoveDescriptor, InsertDescriptor                          |
| R5051           | GetRemovableDescriptorsOfClass, RemoveDescriptor, InsertDescriptor                          |
| R5052           | TriggerAnyDescriptorUpdate                                                                  |
| R5053           | GetRemovableDescriptorsOfClass, RemoveDescriptor, InsertDescriptor                          |
| 5-4-7_0_0       | SetComponentActivation, SetMetricStatus                                                     |
| 5-4-7_1         | SetComponentActivation, SetMetricStatus                                                     |
| 5-4-7_2         | SetComponentActivation, SetMetricStatus                                                     |
| 5-4-7_3         | SetComponentActivation, SetMetricStatus                                                     |
| 5-4-7_4         | SetComponentActivation, SetMetricStatus                                                     |
| 5-4-7_5         | SetComponentActivation, SetMetricStatus                                                     |
| 5-4-7_6_0       | SetComponentActivation, SetMetricStatus                                                     |
| 5-4-7_7         | SetComponentActivation, SetMetricStatus                                                     |
| 5-4-7_8         | SetComponentActivation, SetMetricStatus                                                     |
| 5-4-7_9         | SetComponentActivation, SetMetricStatus                                                     |
| 5-4-7_10        | SetComponentActivation, SetMetricStatus                                                     |
| 5-4-7_11        | SetComponentActivation, SetMetricStatus                                                     |
| 5-4-7_12_0      | SetComponentActivation, SetMetricStatus                                                     |
| 5-4-7_13        | SetComponentActivation, SetMetricStatus                                                     |
| 5-4-7_14        | SetComponentActivation, SetMetricStatus                                                     |
| 5-4-7_15        | SetComponentActivation, SetMetricStatus                                                     |
| 5-4-7_16        | SetComponentActivation, SetMetricStatus                                                     |
| 5-4-7_17        | SetComponentActivation, SetMetricStatus                                                     |

[MDPWS]

| **Requirement** | **T2IAPI Manipulation** | 
|-----------------|-------------------------|
| R0008           | SendHello               |

[GLUE]

| **Requirement** | **T2IAPI Manipulation** |
|-----------------|-------------------------|
| R0036_0         | SetLocationDetail       |
| R0056           | TriggerDescriptorUpdate |
| R0078_0         | TriggerReport           |

## Exit Codes

SDCcc's exitCode should be interpreted as follows:

| **ExitCode** | **Semantics**                                                                                                        |
|--------------|----------------------------------------------------------------------------------------------------------------------|
| 0            | Success - SDC-Compliance could be successfully measured and the Device Under Test satisfies all tested Requirements  |
| 1            | Failure - SDC-Compliance could be successfully measured, but the Device Under Test violated Requirements             |
| 2            | Error - SDC-Compliance could not be measured                                                                         |

## Notices
SDCcc is not intended for use in medical products, clinical trials, clinical studies, or in clinical routine.

### ISO 9001
SDCcc was not developed according to ISO 9001.

## License
[MIT](https://choosealicense.com/licenses/mit/)
