/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.tests.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.draeger.medical.sdccc.sdcri.testclient.TestClient;
import com.draeger.medical.sdccc.util.WsdlParser;
import com.draeger.medical.sdccc.util.WsdlValidator;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.xml.namespace.QName;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.somda.sdc.dpws.service.HostedServiceProxy;
import org.somda.sdc.dpws.soap.exception.TransportException;
import org.somda.sdc.dpws.wsdl.WsdlRetriever;

/**
 * Utility to verify hostedService endpoints are conforming with SDC Glue Annex B and only implements SDC services.
 */
public class HostedServiceVerifier {

    protected static final String DUPLICATE_PORT_TYPE_TEMPLATE =
            "portType was defined in multiple WSDLs for" + " one service. Cannot determine which is correct.";

    private static final Logger LOG = LogManager.getLogger(HostedServiceVerifier.class);

    private final TestClient testClient;
    private final WsdlRetriever wsdlRetriever;
    private final WsdlParser wsdlParser;

    /**
     * Creates a new {@linkplain HostedServiceVerifier}.
     *
     * @param testClient the testClient which provides the hostedService to be verified
     */
    @Inject
    public HostedServiceVerifier(final TestClient testClient) {
        this.testClient = testClient;
        wsdlRetriever = testClient.getInjector().getInstance(WsdlRetriever.class);
        wsdlParser = testClient.getInjector().getInstance(WsdlParser.class);
    }

    /**
     * Verify the hostedService endpoint provided through {@linkplain #testClient} is conforming
     * with SDC Glue Annex B and only implements SDC services.
     *
     * @param hostedService the hostedService to be verified
     */
    public void verifyHostedService(
            @SuppressWarnings("OptionalUsedAsFieldOrParameterType") final Optional<HostedServiceProxy> hostedService) {
        if (hostedService.isEmpty()) {
            fail("No hosted service present");
        }
        final var service = hostedService.orElseThrow();

        // find service name for hosted service, utility doesn't provide this
        final var serviceNameOpt = testClient.getHostingServiceProxy().getHostedServices().entrySet().stream()
                .filter(entry -> service.equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .findFirst();

        assertTrue(serviceNameOpt.isPresent(), "Could not determine service name for hosted service");
        final var serviceId = serviceNameOpt.orElseThrow();

        final Map<String, List<String>> wsdls;
        try {
            wsdls = wsdlRetriever.retrieveWsdls(testClient.getHostingServiceProxy());
        } catch (final IOException | TransportException e) {
            LOG.debug("Could not retrieve WSDL to verify {}", serviceId, e);
            fail(String.format("Could not retrieve WSDL to verify %s. Message: " + e.getMessage(), serviceId));
            // unreachable, silence warning
            throw new RuntimeException(e);
        }
        final var serviceWsdls = wsdls.get(serviceId);

        assertNotNull(serviceWsdls, "No WSDLs found for " + serviceId);
        assertFalse(serviceWsdls.isEmpty(), "No WSDLs found for " + serviceId);

        // collect all portTypes described over all wsdls
        final Map<QName, Map<QName, WsdlParser.OperationArguments>> parsedWsdls = new HashMap<>();
        for (final var wsdl : serviceWsdls) {
            try {
                // detect duplicates, we cannot determine which description is correct if multiple
                // WSDLs contain them.
                final var wsdlPortTypes = wsdlParser.parseWsdlPortTypes(wsdl);
                wsdlPortTypes.forEach((portTypeName, entry) -> assertFalse(
                        parsedWsdls.containsKey(portTypeName),
                        DUPLICATE_PORT_TYPE_TEMPLATE + " portType " + portTypeName + " service " + serviceId));
                parsedWsdls.putAll(wsdlPortTypes);
            } catch (final javax.xml.bind.JAXBException e) {
                LOG.debug("Could not parse WSDL for service {}", serviceId, e);
                fail("Could not parse WSDL for service " + serviceId + ". Message: " + e.getMessage());
                // unreachable, silence warnings
                throw new RuntimeException(e);
            }
        }
        assertFalse(service.getType().getTypes().isEmpty(), "Types for service " + serviceId + " are empty");

        // validate each type in the service
        service.getType().getTypes().forEach(type -> {
            final var parsedService = parsedWsdls.get(type);
            assertNotNull(
                    parsedService, "WSDL for service " + serviceId + " does not contain portType for type " + type);
            assertTrue(
                    WsdlValidator.isEqualService(type, parsedWsdls.get(type)),
                    "Service " + type + " does not match service defined in SDC Glue Annex B.");
        });
    }

    /**
     * Checks the presence and BICEPS conformance of a hosted service.
     * Throws an AssertionFailedError when the service is not present or its WSDL description does not conform to
     * the BICEPS standard.
     * @param targetQName   - QName of the service to check
     * @param hostedService - HostedService to check
     */
    public void checkServicePresenceAndConformance(
            final QName targetQName,
            @SuppressWarnings("OptionalUsedAsFieldOrParameterType") final Optional<HostedServiceProxy> hostedService) {
        final var hostedServices = testClient.getHostingServiceProxy().getHostedServices();
        assertTrue(
                hostedServices.values().stream().anyMatch(value -> value.getType().getTypes().stream()
                        .anyMatch(qname -> qname.equals(targetQName))),
                String.format("No %s present", targetQName.getLocalPart()));
        verifyHostedService(hostedService);
    }
}
