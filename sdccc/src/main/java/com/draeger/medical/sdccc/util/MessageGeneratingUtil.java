/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.util;

import static com.draeger.medical.sdccc.util.Constants.REF_ELEMENT_QUERY;

import com.draeger.medical.sdccc.messages.MessageStorage;
import com.draeger.medical.sdccc.sdcri.testclient.TestClient;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.namespace.QName;
import javax.xml.xpath.XPathExpressionException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.somda.sdc.biceps.model.message.GetContainmentTree;
import org.somda.sdc.biceps.model.message.GetContextStates;
import org.somda.sdc.biceps.model.message.GetDescriptor;
import org.somda.sdc.biceps.model.message.GetSupportedLanguages;
import org.somda.sdc.biceps.model.message.ObjectFactory;
import org.somda.sdc.dpws.http.HttpException;
import org.somda.sdc.dpws.service.HostedServiceProxy;
import org.somda.sdc.dpws.soap.SoapMessage;
import org.somda.sdc.dpws.soap.SoapUtil;
import org.somda.sdc.dpws.soap.exception.MarshallingException;
import org.somda.sdc.dpws.soap.exception.SoapFaultException;
import org.somda.sdc.dpws.soap.exception.TransportException;
import org.somda.sdc.dpws.soap.interception.InterceptorException;
import org.somda.sdc.glue.common.ActionConstants;
import org.somda.sdc.glue.common.WsdlConstants;

/**
 * Utility to generate message data with a provider DUT.
 */
public class MessageGeneratingUtil {
    private static final Logger LOG = LogManager.getLogger();

    private final TestClient client;
    private final TestRunObserver testRunObserver;
    private final ObjectFactory messageModelFactory;
    private final SoapUtil soapUtil;
    private final MessageStorage storage;

    @Inject
    MessageGeneratingUtil(
            final TestClient client,
            final TestRunObserver testRunObserver,
            final ObjectFactory messageModelFactory,
            final MessageStorage storage) {
        this.client = client;
        this.testRunObserver = testRunObserver;
        this.messageModelFactory = messageModelFactory;
        this.soapUtil = client.getInjector().getInstance(SoapUtil.class);
        this.storage = storage;
    }

    private static Optional<HostedServiceProxy> getProxyForPortType(
            final Collection<HostedServiceProxy> services, final QName portType) {
        return services.stream()
                .filter(service -> service.getType().getTypes().contains(portType))
                .findFirst();
    }

    /**
     * Extracts the get service proxy from a client.
     *
     * @param client to extract from
     * @return a get service or an empty optional
     */
    public static Optional<HostedServiceProxy> getGetService(final TestClient client) {
        return getProxyForPortType(
                client.getHostingServiceProxy().getHostedServices().values(), WsdlConstants.PORT_TYPE_GET_QNAME);
    }

    /**
     * Extracts the set service proxy from a client.
     *
     * @param client to extract from
     * @return a set service or an empty optional
     */
    public static Optional<HostedServiceProxy> getSetService(final TestClient client) {
        return getProxyForPortType(
                client.getHostingServiceProxy().getHostedServices().values(), WsdlConstants.PORT_TYPE_SET_QNAME);
    }

    /**
     * Extracts the descriptionEvent service proxy from a client.
     *
     * @param client to extract from
     * @return a descriptionEvent service or an empty optional
     */
    public static Optional<HostedServiceProxy> getDescriptionEventService(final TestClient client) {
        return getProxyForPortType(
                client.getHostingServiceProxy().getHostedServices().values(),
                WsdlConstants.PORT_TYPE_DESCRIPTION_EVENT_QNAME);
    }

    /**
     * Extracts the stateEvent service proxy from a client.
     *
     * @param client to extract from
     * @return a stateEvent service or an empty optional
     */
    public static Optional<HostedServiceProxy> getStateEventService(final TestClient client) {
        return getProxyForPortType(
                client.getHostingServiceProxy().getHostedServices().values(),
                WsdlConstants.PORT_TYPE_STATE_EVENT_QNAME);
    }

    /**
     * Extracts the context service proxy from a client.
     *
     * @param client to extract from
     * @return a context service or an empty optional
     */
    public static Optional<HostedServiceProxy> getContextService(final TestClient client) {
        return getProxyForPortType(
                client.getHostingServiceProxy().getHostedServices().values(), WsdlConstants.PORT_TYPE_CONTEXT_QNAME);
    }

    /**
     * Determines whether the context service is provided by a client.
     *
     * @return true, if a context service is provided, false otherwise.
     */
    public Boolean hasContextService() {
        return getContextService(client).isPresent();
    }

    /**
     * Extracts the waveform service proxy from a client.
     *
     * @param client to extract from
     * @return a waveform service or an empty optional
     */
    public static Optional<HostedServiceProxy> getWaveformService(final TestClient client) {
        return getProxyForPortType(
                client.getHostingServiceProxy().getHostedServices().values(), WsdlConstants.PORT_TYPE_WAVEFORM_QNAME);
    }

    /**
     * Extracts the containment tree service proxy from a client.
     *
     * @param client to extract from
     * @return a containment tree service or an empty optional
     */
    public static Optional<HostedServiceProxy> getContainmentTreeService(final TestClient client) {
        return getProxyForPortType(
                client.getHostingServiceProxy().getHostedServices().values(),
                WsdlConstants.PORT_TYPE_CONTAINMENT_TREE_QNAME);
    }

    /**
     * Determines whether the containmentTree service is provided by a client.
     *
     * @return true, if a containmentTree service is provided, false otherwise.
     */
    public Boolean hasContainmentTreeService() {
        return getContainmentTreeService(client).isPresent();
    }

    /**
     * Extracts the archive service proxy from a client.
     *
     * @param client to extract from
     * @return a archive service or an empty optional
     */
    public static Optional<HostedServiceProxy> getArchiveService(final TestClient client) {
        return getProxyForPortType(
                client.getHostingServiceProxy().getHostedServices().values(), WsdlConstants.PORT_TYPE_ARCHIVE_QNAME);
    }

    /**
     * Extracts the localization service proxy from a client.
     *
     * @param client to extract from
     * @return a get service or an empty optional
     */
    public static Optional<HostedServiceProxy> getLocalizationService(final TestClient client) {
        return getProxyForPortType(
                client.getHostingServiceProxy().getHostedServices().values(),
                WsdlConstants.PORT_TYPE_LOCALIZATION_QNAME);
    }

    /**
     * Determines whether the localization service is provided by a client.
     *
     * @return true, if a localization service is provided, false otherwise.
     */
    public Boolean hasLocalizationService() {
        return getLocalizationService(client).isPresent();
    }

    /**
     * Sends a GetMdib message to the DUT.
     *
     * @return response message
     */
    public SoapMessage getMdib() throws MessagingException {
        final var getService = getGetService(client);
        if (getService.isEmpty()) {
            final String emptyMessage = "DUT did not provide a Get service";
            testRunObserver.invalidateTestRun(emptyMessage);
            // NOTE: in this case, throwing an Exception makes sense as the GET Service is mandatory.
            throw new NoSuchElementException(emptyMessage);
        }
        try {
            LOG.debug("Sending a GetMdib message to the DUT.");
            return getService
                    .orElseThrow()
                    .sendRequestResponse(soapUtil.createMessage(
                            ActionConstants.ACTION_GET_MDIB, messageModelFactory.createGetMdib()));
        } catch (final SoapFaultException | MarshallingException | TransportException | InterceptorException e) {
            final String failedRequestResponseMessage = "Could not send GetMdib request to DUT";
            testRunObserver.invalidateTestRun(failedRequestResponseMessage, e);
            // NOTE: in this case, throwing an Exception makes sense as the GET Service is mandatory and getMdib()
            //       shall be supported by any SDC device.
            throw new MessagingException(failedRequestResponseMessage, e);
        }
    }

    /**
     * Sends a GetMdDescription message to the DUT.
     *
     * @param handleRef list of handles to request
     * @return response message
     */
    public SoapMessage getMdDescription(final List<String> handleRef) {
        final var getService = getGetService(client);
        if (getService.isEmpty()) {
            final String emptyMessage = "DUT did not provide a Get service";
            testRunObserver.invalidateTestRun(emptyMessage);
            // NOTE: in this case, throwing an Exception makes sense as the GET Service is mandatory.
            throw new NoSuchElementException(emptyMessage);
        }
        try {
            LOG.debug("Sending a GetMdDescription message to the DUT.");

            final var mdDescription = messageModelFactory.createGetMdDescription();
            mdDescription.setHandleRef(handleRef);
            return getService
                    .orElseThrow()
                    .sendRequestResponse(
                            soapUtil.createMessage(ActionConstants.ACTION_GET_MD_DESCRIPTION, mdDescription));
        } catch (final SoapFaultException | MarshallingException | TransportException | InterceptorException e) {
            final String failedRequestResponseMessage = "Could not send GetMdDescription request to DUT";
            testRunObserver.invalidateTestRun(failedRequestResponseMessage, e);
            return null;
        }
    }

    /**
     * Sends a GetMdState message to the DUT.
     *
     * @param handleRef list of handles to request
     * @return response message
     */
    public SoapMessage getMdState(final List<String> handleRef) {
        final var getService = getGetService(client);
        if (getService.isEmpty()) {
            final String emptyMessage = "DUT did not provide a Get service";
            testRunObserver.invalidateTestRun(emptyMessage);
            // NOTE: in this case, throwing an Exception makes sense as the GET Service is mandatory.
            throw new NoSuchElementException(emptyMessage);
        }
        try {
            LOG.debug("Sending a GetMdState message to the DUT.");

            final var mdState = messageModelFactory.createGetMdState();
            mdState.setHandleRef(handleRef);
            return getService
                    .orElseThrow()
                    .sendRequestResponse(soapUtil.createMessage(ActionConstants.ACTION_GET_MD_STATE, mdState));
        } catch (final SoapFaultException | MarshallingException | TransportException | InterceptorException e) {
            final String failedRequestResponseMessage = "Could not send GetMdState request to DUT";
            testRunObserver.invalidateTestRun(failedRequestResponseMessage, e);
            return null;
        }
    }

    /**
     * Sends a GetContextStates message to the DUT.
     *
     * @return SOAP response message
     */
    public SoapMessage getContextStates() {
        return getContextStates(List.of());
    }

    /**
     * Sends a GetContextStates message to the DUT.
     *
     * @param handleRef list of handle reference strings
     * @return SOAP response message
     */
    public SoapMessage getContextStates(final List<String> handleRef) {
        final var contextService = getContextService(client);
        if (contextService.isEmpty()) {
            final String emptyMessage = "No context service available, not performing getContextStates";
            LOG.error(emptyMessage);
            testRunObserver.invalidateTestRun(emptyMessage);
            return null;
        }
        return handleSoapFaultsAndTechnicalProblems(
                () -> {
                    LOG.debug("Sending a GetContextStates message to the DUT.");

                    final GetContextStates getContextStates = messageModelFactory.createGetContextStates();
                    getContextStates.setHandleRef(handleRef);
                    return contextService
                            .orElseThrow()
                            .sendRequestResponse(soapUtil.createMessage(
                                    ActionConstants.ACTION_GET_CONTEXT_STATES, getContextStates));
                },
                e -> {
                    final String failedRequestResponseMessage = "Could not send GetContextStates request to DUT";
                    testRunObserver.invalidateTestRun(failedRequestResponseMessage, e);
                    return null;
                });
    }

    /**
     * Sends a GetContainmentTree message to the DUT.
     *
     * @param handleRef list of handle reference strings
     * @return SOAP response message
     */
    public SoapMessage getContainmentTree(final List<String> handleRef) {
        final var containmentTreeService = getContainmentTreeService(client);
        if (containmentTreeService.isEmpty()) {
            final String emptyMessage = "No containment tree service available, not performing getContainmentTree";
            LOG.error(emptyMessage);
            testRunObserver.invalidateTestRun(emptyMessage);
            return null;
        }
        return handleSoapFaultsAndTechnicalProblems(
                () -> {
                    LOG.debug("Sending a GetContainmentTree message to the DUT.");

                    final GetContainmentTree getContainmentTree = messageModelFactory.createGetContainmentTree();
                    getContainmentTree.setHandleRef(handleRef);
                    return containmentTreeService
                            .orElseThrow()
                            .sendRequestResponse(soapUtil.createMessage(
                                    ActionConstants.ACTION_GET_CONTAINMENT_TREE, getContainmentTree));
                },
                e -> {
                    final String failedRequestResponseMessage = "Could not send GetContainmentTree request to DUT";
                    testRunObserver.invalidateTestRun(failedRequestResponseMessage, e);
                    return null;
                });
    }

    /**
     * Sends a GetDescriptor message to the DUT.
     *
     * @param handleRef list of handle reference strings
     * @return SOAP response message
     */
    public SoapMessage getDescriptor(final List<String> handleRef) {
        final var containmentTreeService = getContainmentTreeService(client);
        if (containmentTreeService.isEmpty()) {
            final String emptyMessage = "No containment tree service available, not performing getDescriptor";
            LOG.error(emptyMessage);
            testRunObserver.invalidateTestRun(emptyMessage);
            return null;
        }
        return handleSoapFaultsAndTechnicalProblems(
                () -> {
                    LOG.debug("Sending a GetDescriptor message to the DUT.");
                    final GetDescriptor getDescriptor = messageModelFactory.createGetDescriptor();
                    getDescriptor.setHandleRef(handleRef);
                    return containmentTreeService
                            .orElseThrow()
                            .sendRequestResponse(
                                    soapUtil.createMessage(ActionConstants.ACTION_GET_DESCRIPTOR, getDescriptor));
                },
                e -> {
                    final String failedRequestResponseMessage = "Could not send GetDescriptor request to DUT";
                    testRunObserver.invalidateTestRun(failedRequestResponseMessage, e);
                    return null;
                });
    }

    /**
     * Send GetLocalizedText requests to the DUT.
     *
     * <p>
     * It sends three kinds of requests
     * <ol>
     *     <li>GetLocalizedText for each encountered @Ref separately</li>
     *     <li>GetLocalizedText for all encountered @Ref together</li>
     *     <li>GetLocalizedText without any arguments, querying all texts in all languages</li>
     * </ol>
     */
    public void getLocalizedTexts() {
        final var localizationService = getLocalizationService(client);
        if (localizationService.isEmpty()) {
            LOG.debug("No localization service available, not performing getLocalizedTexts");
            return;
        }
        final Collection<String> textRefs;
        try {
            textRefs = extractTextRefs();
        } catch (final IOException e) {
            testRunObserver.invalidateTestRun("Could not extract localized text references from inbound messages", e);
            return;
        }
        LOG.debug("Sending a GetLocalizedText message for each known ref.");
        textRefs.forEach(ref -> {
            LOG.debug("Sending a GetLocalizedText message for ref {}", ref);
            final var message = messageModelFactory.createGetLocalizedText();
            message.getRef().add(ref);
            // request each
            try {
                sendLocalizedText(
                        localizationService.orElseThrow(),
                        soapUtil.createMessage(ActionConstants.ACTION_GET_LOCALIZED_TEXT, message));
            } catch (final SoapFaultException | MarshallingException | InterceptorException | TransportException e) {
                testRunObserver.invalidateTestRun("Could not send GetLocalizedText request to DUT", e);
            }
        });
        try {
            LOG.debug("Sending a GetLocalizedText message for all known refs.");
            final var message = messageModelFactory.createGetLocalizedText();
            message.getRef().addAll(textRefs);
            sendLocalizedText(
                    localizationService.orElseThrow(),
                    soapUtil.createMessage(ActionConstants.ACTION_GET_LOCALIZED_TEXT, message));
        } catch (final SoapFaultException | MarshallingException | InterceptorException | TransportException e) {
            testRunObserver.invalidateTestRun("Could not send GetLocalizedText request to DUT", e);
        }
        try {
            LOG.debug("Sending a GetLocalizedText message for all texts.");
            sendLocalizedText(
                    localizationService.orElseThrow(),
                    soapUtil.createMessage(
                            ActionConstants.ACTION_GET_LOCALIZED_TEXT, messageModelFactory.createGetLocalizedText()));
        } catch (final SoapFaultException | MarshallingException | TransportException | InterceptorException e) {
            testRunObserver.invalidateTestRun("Could not send GetLocalizedText request to DUT", e);
        }
    }

    /**
     * Sends a GetSupportedLanguages message to the DUT.
     *
     * @return SOAP response message
     */
    public SoapMessage getSupportedLanguages() {
        final var localizationService = getLocalizationService(client);
        if (localizationService.isEmpty()) {
            LOG.debug("No localization service available, not performing getSupportedLanguages");
            return null;
        }
        return handleSoapFaultsAndTechnicalProblems(
                () -> {
                    LOG.debug("Sending a GetSupportedLanguages message to the DUT.");

                    final GetSupportedLanguages getSupportedLanguages =
                            messageModelFactory.createGetSupportedLanguages();
                    return localizationService
                            .orElseThrow()
                            .sendRequestResponse(soapUtil.createMessage(
                                    ActionConstants.ACTION_GET_SUPPORTED_LANGUAGES, getSupportedLanguages));
                },
                e -> {
                    final String failedRequestResponseMessage = "Could not send GetSupportedLanguages request to DUT";
                    testRunObserver.invalidateTestRun(failedRequestResponseMessage, e);
                    return null;
                });
    }

    /**
     * Sends a localized text message, but swallows exceptions caused by 413 payload too large responses,
     * as they are expected.
     *
     * @param service to send message on
     * @param message to send
     * @throws SoapFaultException   on soap faults
     * @throws MarshallingException on marshalling errors
     * @throws InterceptorException on interceptor errors
     * @throws TransportException   on transport errors except 413 status codes
     */
    private void sendLocalizedText(final HostedServiceProxy service, final SoapMessage message)
            throws SoapFaultException, MarshallingException, InterceptorException, TransportException {
        try {
            service.sendRequestResponse(message);
        } catch (final TransportException e) {
            if (e.getCause() != null && e.getCause() instanceof HttpException) {
                if (((HttpException) e.getCause()).getStatusCode() == Constants.HTTP_PAYLOAD_TOO_LARGE) {
                    LOG.debug(
                            "TransportException with HttpException cause and {} status code",
                            Constants.HTTP_PAYLOAD_TOO_LARGE);
                    return;
                } else {
                    LOG.debug("TransportException with HttpException cause");
                }
            }
            throw e;
        } catch (final SoapFaultException e) {
            if (e.getCause() != null && e.getCause() instanceof HttpException) {
                if (((HttpException) e.getCause()).getStatusCode() == Constants.HTTP_PAYLOAD_TOO_LARGE) {
                    LOG.debug(
                            "TransportException with HttpException cause and {} status code",
                            Constants.HTTP_PAYLOAD_TOO_LARGE);
                    return;
                } else {
                    LOG.debug("SoapFaultException with HttpException cause");
                }
            }
            throw e;
        }
    }

    /**
     * Retrieves all encountered LocalizedText @Ref elements without @Lang.
     *
     * @return all unique @Ref entries encountered
     * @throws IOException on errors accessing the storage
     */
    private Collection<String> extractTextRefs() throws IOException {
        final var extractor = new XPathExtractor(REF_ELEMENT_QUERY);

        // collect all refs over all messages
        try (final var messages = storage.getInboundSoapMessages()) {
            return messages.getStream()
                    .flatMap(message -> {
                        try {
                            final var nodes = extractor.extractFrom(message.getBody());
                            return nodes.stream()
                                    .filter(node -> node.getAttributes().getNamedItem("Lang") == null)
                                    .map(node -> node.getAttributes()
                                            .getNamedItem("Ref")
                                            .getTextContent());
                        } catch (final XPathExpressionException e) {
                            final var error = String.format(
                                    "Error while extracting LocalizedText/@Ref elements" + " from message with hash %s",
                                    message.getMessageHash());
                            testRunObserver.invalidateTestRun(error, e);
                            return Stream.empty();
                        }
                    })
                    .collect(Collectors.toSet());
        }
    }

    private SoapMessage handleSoapFaultsAndTechnicalProblems(final SoapClosure closure, final ErrorClosure handle) {
        try {
            return closure.execute();
        } catch (final SoapFaultException | MarshallingException | TransportException | InterceptorException e) {
            return handle.execute(e);
        }
    }

    /**
     * A Closure returning a SOAPMessage.
     */
    public interface SoapClosure {
        /**
         * execute the closure.
         *
         * @return SOAP response message
         */
        SoapMessage execute() throws SoapFaultException, MarshallingException, TransportException, InterceptorException;
    }

    /**
     * A Closure taking an Exception.
     */
    public interface ErrorClosure {
        /**
         * execute the closure.
         *
         * @param e - the Exception
         * @return SOAP response message
         */
        SoapMessage execute(Exception e);
    }
}
