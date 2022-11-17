/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.util;

import com.draeger.medical.biceps.model.message.AbstractAlertReport;
import com.draeger.medical.biceps.model.message.AbstractComponentReport;
import com.draeger.medical.biceps.model.message.AbstractContextReport;
import com.draeger.medical.biceps.model.message.AbstractMetricReport;
import com.draeger.medical.biceps.model.message.AbstractOperationalStateReport;
import com.draeger.medical.biceps.model.message.DescriptionModificationReport;
import com.draeger.medical.biceps.model.message.EpisodicAlertReport;
import com.draeger.medical.biceps.model.message.EpisodicComponentReport;
import com.draeger.medical.biceps.model.message.EpisodicContextReport;
import com.draeger.medical.biceps.model.message.EpisodicMetricReport;
import com.draeger.medical.biceps.model.message.EpisodicOperationalStateReport;
import com.draeger.medical.biceps.model.message.GetContextStatesResponse;
import com.draeger.medical.biceps.model.message.GetLocalizedTextResponse;
import com.draeger.medical.biceps.model.message.GetMdDescriptionResponse;
import com.draeger.medical.biceps.model.message.GetMdibResponse;
import com.draeger.medical.biceps.model.message.InvocationInfo;
import com.draeger.medical.biceps.model.message.InvocationState;
import com.draeger.medical.biceps.model.message.ObjectFactory;
import com.draeger.medical.biceps.model.message.OperationInvokedReport;
import com.draeger.medical.biceps.model.message.PeriodicContextReport;
import com.draeger.medical.biceps.model.message.SetContextStateResponse;
import com.draeger.medical.biceps.model.message.SetStringResponse;
import com.draeger.medical.biceps.model.message.SystemErrorReport;
import com.draeger.medical.biceps.model.message.WaveformStream;
import com.draeger.medical.biceps.model.participant.CodedValue;
import com.draeger.medical.biceps.model.participant.RealTimeSampleArrayMetricState;
import com.draeger.medical.dpws.soap.model.Envelope;
import com.draeger.medical.dpws.soap.wsaddressing.model.AttributedURIType;
import com.draeger.medical.dpws.soap.wsaddressing.model.EndpointReferenceType;
import com.draeger.medical.dpws.soap.wsaddressing.model.RelatesToType;
import com.draeger.medical.dpws.soap.wsdiscovery.model.HelloType;
import com.draeger.medical.dpws.soap.wsdiscovery.model.ProbeMatchType;
import com.draeger.medical.dpws.soap.wsdiscovery.model.ProbeMatchesType;
import com.draeger.medical.dpws.soap.wsdiscovery.model.ResolveMatchType;
import com.draeger.medical.dpws.soap.wsdiscovery.model.ResolveMatchesType;
import com.draeger.medical.sdccc.marshalling.SoapMarshalling;
import jakarta.xml.bind.JAXBElement;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import javax.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.somda.sdc.dpws.soap.SoapConstants;
import org.somda.sdc.dpws.soap.wsaddressing.WsAddressingConstants;
import org.w3c.dom.Element;

/**
 * Utility to build message objects to be used with {@linkplain SoapMarshalling}.
 */
public class MessageBuilder {
    private static final Logger LOG = LogManager.getLogger(MessageBuilder.class);

    private final ObjectFactory messageModelFactory;
    private final com.draeger.medical.dpws.soap.wsdiscovery.model.ObjectFactory wsDiscoveryFactory;
    private final com.draeger.medical.dpws.soap.wsaddressing.model.ObjectFactory wsAddressingFactory;
    private final com.draeger.medical.dpws.soap.model.ObjectFactory soapFactory;

    @Inject
    MessageBuilder(
            final ObjectFactory messageModelFactory,
            final com.draeger.medical.dpws.soap.wsdiscovery.model.ObjectFactory wsDiscoveryFactory,
            final com.draeger.medical.dpws.soap.wsaddressing.model.ObjectFactory wsAddressingFactory,
            final com.draeger.medical.dpws.soap.model.ObjectFactory soapFactory) {
        this.messageModelFactory = messageModelFactory;
        this.wsAddressingFactory = wsAddressingFactory;
        this.wsDiscoveryFactory = wsDiscoveryFactory;
        this.soapFactory = soapFactory;
    }

    private String createRandomUUIDUri() {
        final var uuid = UUID.randomUUID();
        return "urn:uuid:" + uuid.toString();
    }

    private Envelope createMessage(final String action) {
        final var envelope = soapFactory.createEnvelope();

        final var header = soapFactory.createHeader();
        final var body = soapFactory.createBody();
        envelope.setHeader(header);
        envelope.setBody(body);

        final var act = wsAddressingFactory.createAction(createAttributedUriType(action));
        envelope.getHeader().getAny().add(act);
        return envelope;
    }

    /**
     * @param value of new attributed uri
     * @return new attributed uri with given value
     */
    public AttributedURIType createAttributedUriType(final String value) {
        final var uri = new AttributedURIType();
        uri.setValue(value);
        return uri;
    }

    /**
     * @param value of new relates to
     * @return new relates to element with given value
     */
    public RelatesToType createRelatesToType(final String value) {
        final var relatesToType = new RelatesToType();
        relatesToType.setValue(value);
        return relatesToType;
    }

    /**
     * Creates a new basic soap message with an action.
     *
     * @param action to set in header
     * @return new envelope with action
     */
    public Envelope createBasicSoapMessage(final String action) {
        final var message = createMessage(action);
        message.getHeader()
                .getAny()
                .add(wsAddressingFactory.createMessageID(createAttributedUriType(createRandomUUIDUri())));

        return message;
    }

    /**
     * Creates a new basic soap message with action and body.
     *
     * @param action      to set in header
     * @param bodyElement to set as body
     * @return new envelope with action and body
     */
    public Envelope createSoapMessageWithBody(final String action, final Object bodyElement) {
        final var message = createBasicSoapMessage(action);
        message.getBody().getAny().clear();
        message.getBody().getAny().addAll(List.of(bodyElement));
        return message;
    }

    /**
     * Creates a new fault soap message with given reason.
     * @param reasonText the reason for the fault.
     * @param lang the language attribute of the reasonText.
     * @return new envelope with the fault.
     */
    public Envelope createFaultResponse(final String reasonText, final String lang) {
        final var reason = soapFactory.createReasontext();
        reason.setValue(reasonText);
        reason.setLang(lang);
        final var faultReason = soapFactory.createFaultreason();
        faultReason.getText().clear();
        faultReason.getText().addAll(List.of(reason));
        final var subFaultCode = soapFactory.createSubcode();
        subFaultCode.setValue(SoapConstants.DEFAULT_SUBCODE);
        final var faultCode = soapFactory.createFaultcode();
        faultCode.setValue(SoapConstants.SENDER);
        faultCode.setSubcode(subFaultCode);
        final var fault = soapFactory.createFault();
        fault.setReason(faultReason);
        fault.setCode(faultCode);

        return createSoapMessageWithBody(WsAddressingConstants.FAULT_ACTION, soapFactory.createFault(fault));
    }

    /**
     * Creates a {@linkplain JAXBElement} from an {@linkplain Envelope} for marshalling.
     *
     * @param envelope to put into container
     * @return container for marshalling
     */
    public JAXBElement<Envelope> buildEnvelope(final Envelope envelope) {
        return soapFactory.createEnvelope(envelope);
    }

    /**
     * Creates new endpoint reference.
     *
     * @param address of new endpoint reference
     * @return new endpoint reference
     */
    public EndpointReferenceType buildEndpointReference(final String address) {
        final var epr = wsAddressingFactory.createEndpointReferenceType();
        final var addressType = new AttributedURIType();
        addressType.setValue(address);
        epr.setAddress(addressType);
        return epr;
    }

    /**
     * Creates a new hello message.
     *
     * @param endpointReference of new hello
     * @param metadataVersion   of new hello
     * @return new hello
     */
    public JAXBElement<HelloType> buildHello(
            final EndpointReferenceType endpointReference, final long metadataVersion) {
        final var hello = wsDiscoveryFactory.createHelloType();
        hello.setEndpointReference(endpointReference);
        hello.setMetadataVersion(metadataVersion);
        return wsDiscoveryFactory.createHello(hello);
    }

    /**
     * Creates a new probe match element.
     *
     * @param endpointReference of match
     * @param metadataVersion   of match
     * @return match element
     */
    public ProbeMatchType buildProbeMatch(final EndpointReferenceType endpointReference, final long metadataVersion) {
        final var probeMatch = wsDiscoveryFactory.createProbeMatchType();
        probeMatch.setEndpointReference(endpointReference);
        probeMatch.setMetadataVersion(metadataVersion);
        return probeMatch;
    }

    /**
     * Creates a new probe matches element.
     *
     * @param match matching devices
     * @return probe matches element
     */
    public JAXBElement<ProbeMatchesType> buildProbeMatches(final List<ProbeMatchType> match) {
        final var probeMatches = wsDiscoveryFactory.createProbeMatchesType();
        probeMatches.getProbeMatch().clear();
        probeMatches.getProbeMatch().addAll(match);
        return wsDiscoveryFactory.createProbeMatches(probeMatches);
    }

    /**
     * Creates a new resolve match element.
     *
     * @param endpointReference of match
     * @param metadataVersion   of match
     * @return match element
     */
    public ResolveMatchType buildResolveMatch(
            final EndpointReferenceType endpointReference, final long metadataVersion) {
        final var resolveMatch = wsDiscoveryFactory.createResolveMatchType();
        resolveMatch.setEndpointReference(endpointReference);
        resolveMatch.setMetadataVersion(metadataVersion);
        return resolveMatch;
    }

    /**
     * Creates a new resolve matches element.
     *
     * @param match matching device
     * @return resolve matches element
     */
    public JAXBElement<ResolveMatchesType> buildResolveMatches(final ResolveMatchType match) {
        final var resolveMatches = wsDiscoveryFactory.createResolveMatchesType();
        resolveMatches.setResolveMatch(match);
        return wsDiscoveryFactory.createResolveMatches(resolveMatches);
    }

    /**
     * Creates a new get mdib response element.
     *
     * @param sequenceId of mdib to be transmitted
     * @return get mdib response element
     */
    public GetMdibResponse buildGetMdibResponse(final String sequenceId) {
        final var response = messageModelFactory.createGetMdibResponse();
        response.setSequenceId(sequenceId);
        return response;
    }

    /**
     * Creates a new get mddescription response element.
     *
     * @param sequenceId of current Mdib
     * @return get mddescription response element
     */
    public GetMdDescriptionResponse buildGetMdDescriptionResponse(final String sequenceId) {
        final var response = messageModelFactory.createGetMdDescriptionResponse();
        response.setSequenceId(sequenceId);
        return response;
    }

    /**
     * Creates a new get localized text response element.
     *
     * @param sequenceId of current Mdib
     * @return get localized text response element
     */
    public GetLocalizedTextResponse buildGetLocalizedTextResponse(final String sequenceId) {
        final var response = messageModelFactory.createGetLocalizedTextResponse();
        response.setSequenceId(sequenceId);
        return response;
    }

    /**
     * Creates new set string response element.
     *
     * @param sequenceId     of current Mdib
     * @param invocationInfo of the operation
     * @return set string response element
     */
    public SetStringResponse buildSetStringResponse(final String sequenceId, final InvocationInfo invocationInfo) {
        final var response = messageModelFactory.createSetStringResponse();
        response.setSequenceId(sequenceId);
        response.setInvocationInfo(invocationInfo);
        return response;
    }

    /**
     * Creates new system error report element.
     *
     * @param sequenceId  of current Mdib
     * @param reportParts report elements
     * @return system error report element
     */
    public SystemErrorReport buildSystemErrorReport(
            final String sequenceId, final List<SystemErrorReport.ReportPart> reportParts) {
        final var response = messageModelFactory.createSystemErrorReport();
        response.setSequenceId(sequenceId);
        response.getReportPart().clear();
        response.getReportPart().addAll(reportParts);
        return response;
    }

    /**
     * Creates a new report part for a system error report.
     *
     * @param errorCode of the error which occurred
     * @return system error report part element
     */
    public SystemErrorReport.ReportPart buildSystemErrorReportReportPart(final CodedValue errorCode) {
        final var reportPart = messageModelFactory.createSystemErrorReportReportPart();
        reportPart.setErrorCode(errorCode);
        return reportPart;
    }

    /**
     * Creates a new get context states response element.
     *
     * @param sequenceId of current Mdib
     * @return get context states response element
     */
    public GetContextStatesResponse buildGetContextStatesResponse(final String sequenceId) {
        final var response = messageModelFactory.createGetContextStatesResponse();
        response.setSequenceId(sequenceId);
        return response;
    }

    /**
     * Creates a new set context states response element.
     *
     * @param sequenceId     of current Mdib
     * @param invocationInfo result of operation
     * @return set context states response element
     */
    public SetContextStateResponse buildSetContextStateResponse(
            final String sequenceId, final InvocationInfo invocationInfo) {
        final var response = messageModelFactory.createSetContextStateResponse();
        response.setSequenceId(sequenceId);
        response.setInvocationInfo(invocationInfo);
        return response;
    }

    /**
     * Creates a new episodic context report element.
     *
     * @param sequenceId of current Mdib
     * @return new episodic context report
     */
    public EpisodicContextReport buildEpisodicContextReport(final String sequenceId) {
        final var report = messageModelFactory.createEpisodicContextReport();
        report.setSequenceId(sequenceId);
        return report;
    }

    /**
     * Creates a new episodic operational state report element.
     *
     * @param sequenceId of current Mdib
     * @return new episodic operational state report
     */
    public EpisodicOperationalStateReport buildEpisodicOperationalStateReport(final String sequenceId) {
        final var report = messageModelFactory.createEpisodicOperationalStateReport();
        report.setSequenceId(sequenceId);
        return report;
    }

    /**
     * Creates a new episodic metric report element.
     *
     * @param sequenceId of current Mdib
     * @return new episodic metric report
     */
    public EpisodicMetricReport buildEpisodicMetricReport(final String sequenceId) {
        final var report = messageModelFactory.createEpisodicMetricReport();
        report.setSequenceId(sequenceId);
        return report;
    }

    /**
     * Creates a new episodic component report element.
     *
     * @param sequenceId of current Mdib
     * @return new episodic component report
     */
    public EpisodicComponentReport buildEpisodicComponentReport(final String sequenceId) {
        final var report = messageModelFactory.createEpisodicComponentReport();
        report.setSequenceId(sequenceId);
        return report;
    }

    /**
     * Creates a new episodic alert report element.
     *
     * @param sequenceId of current Mdib
     * @return new episodic alert report
     */
    public EpisodicAlertReport buildEpisodicAlertReport(final String sequenceId) {
        final var report = messageModelFactory.createEpisodicAlertReport();
        report.setSequenceId(sequenceId);
        return report;
    }

    /**
     * Creates a new periodic context report element.
     *
     * @param sequenceId of current Mdib
     * @return new periodic context report
     */
    public PeriodicContextReport buildPeriodicContextReport(final String sequenceId) {
        final var report = messageModelFactory.createPeriodicContextReport();
        report.setSequenceId(sequenceId);
        return report;
    }

    /**
     * Creates a new description modification report element.
     *
     * @param sequenceId  of current mdib
     * @param reportParts to add to report
     * @return new description modification report
     */
    public DescriptionModificationReport buildDescriptionModificationReport(
            final String sequenceId, final Collection<DescriptionModificationReport.ReportPart> reportParts) {
        final var report = messageModelFactory.createDescriptionModificationReport();
        report.setSequenceId(sequenceId);
        report.getReportPart().addAll(reportParts);
        return report;
    }

    /**
     * Creates a new waveform stream element.
     *
     * @param sequenceId  of current mdib
     * @param states to add to waveform stream
     * @return new waveform stream
     */
    public WaveformStream buildWaveformStream(
            final String sequenceId, final List<RealTimeSampleArrayMetricState> states) {
        final var waveform = messageModelFactory.createWaveformStream();
        waveform.setSequenceId(sequenceId);
        waveform.getState().addAll(states);
        return waveform;
    }

    /**
     * Creates a new operation invoked report element.
     *
     * @param sequenceId  of current mdib
     * @return new operation invoked report
     */
    public OperationInvokedReport buildOperationInvokedReport(final String sequenceId) {
        final var report = messageModelFactory.createOperationInvokedReport();
        report.setSequenceId(sequenceId);
        return report;
    }

    /**
     * Creates a new abstract metric report report part element.
     *
     * @return new report part
     */
    public AbstractComponentReport.ReportPart buildAbstractComponentReportReportPart() {
        return messageModelFactory.createAbstractComponentReportReportPart();
    }

    /**
     * Creates a new abstract metric report report part element.
     *
     * @return new report part
     */
    public AbstractMetricReport.ReportPart buildAbstractMetricReportReportPart() {
        return messageModelFactory.createAbstractMetricReportReportPart();
    }

    /**
     * Creates a new abstract alert report report part element.
     *
     * @return new report part
     */
    public AbstractAlertReport.ReportPart buildAbstractAlertReportReportPart() {
        return messageModelFactory.createAbstractAlertReportReportPart();
    }

    /**
     * Creates a new abstract context report report part element.
     *
     * @return new report part
     */
    public AbstractContextReport.ReportPart buildAbstractContextReportReportPart() {
        return messageModelFactory.createAbstractContextReportReportPart();
    }

    /**
     * Creates a new abstract operational state report report part element.
     *
     * @return new report part
     */
    public AbstractOperationalStateReport.ReportPart buildAbstractOperationalStateReportReportPart() {
        return messageModelFactory.createAbstractOperationalStateReportReportPart();
    }

    /**
     * Creates a new description modification report report part element.
     *
     * @return new report part
     */
    public DescriptionModificationReport.ReportPart buildDescriptionModificationReportReportPart() {
        return messageModelFactory.createDescriptionModificationReportReportPart();
    }

    /**
     * Creates a new operation invoked report report part element.
     *
     * @return new report part
     */
    public OperationInvokedReport.ReportPart buildOperationInvokedReportReportPart() {
        return messageModelFactory.createOperationInvokedReportReportPart();
    }

    /**
     * Creates a new invocation info element.
     *
     * @param transactionId   of current invocation
     * @param invocationState of current invocation
     * @return new invocation info element
     */
    public InvocationInfo buildInvocationInfo(final long transactionId, final InvocationState invocationState) {
        final var message = messageModelFactory.createInvocationInfo();
        message.setTransactionId(transactionId);
        message.setInvocationState(invocationState);
        return message;
    }

    /*
     Message utility methods
    */

    /**
     * Set the wsa:To element of message.
     *
     * @param envelope to modify
     * @param to       to value to set
     */
    public void setMessageTo(final Envelope envelope, final String to) {
        final var toText = createAttributedUriType(to);
        final var toElement = wsAddressingFactory.createTo(toText);
        addWithDuplicateCheck(toElement, envelope.getHeader().getAny());
    }

    /**
     * Sets the wsa:RelatesTo element of a message.
     *
     * @param envelope  to modify
     * @param relatesTo relatesTo value to set
     */
    public void setMessageRelatesTo(final Envelope envelope, final String relatesTo) {
        final var relatesToType = wsAddressingFactory.createRelatesToType();
        relatesToType.setValue(relatesTo);
        addWithDuplicateCheck(
                wsAddressingFactory.createRelatesTo(relatesToType),
                envelope.getHeader().getAny());
    }

    /**
     * Duplicate check for adding new header elements.
     *
     * <p>
     * Checks for duplicate {@linkplain JAXBElement} instances inside the dest list. When a duplicate is found, i.e.
     * the name, type and scope are equal, it is replaced with the new value.
     *
     * <p>
     * {@linkplain Element} is not checked for duplicates, as they are only passed through for reference parameters.
     *
     * @param obj  to add to the list
     * @param dest to add the new entry to
     */
    private void addWithDuplicateCheck(final Object obj, final List<Object> dest) {
        if (!(obj instanceof JAXBElement)) {
            return;
        }
        final var jaxbObj = (JAXBElement<?>) obj;
        for (final Object element : dest) {
            if (element instanceof JAXBElement) {

                final var jaxbElement = (JAXBElement<?>) element;

                if (jaxbObj.getName().equals(jaxbElement.getName())
                        && jaxbObj.getDeclaredType().equals(jaxbElement.getDeclaredType())
                        && jaxbObj.getScope().equals(jaxbElement.getScope())) {
                    LOG.warn(
                            "Envelope header already contains entry for JAXBElement {}."
                                    + "Removing previously set element with value {} and replacing it with {}",
                            obj,
                            jaxbElement.getValue(),
                            jaxbObj.getValue());
                    dest.remove(jaxbElement);
                    break;
                }
            }
        }
        dest.add(obj);
    }
}
