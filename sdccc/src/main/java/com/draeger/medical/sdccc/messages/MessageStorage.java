/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.messages;

import com.draeger.medical.sdccc.configuration.TestSuiteConfig;
import com.draeger.medical.sdccc.messages.guice.MessageFactory;
import com.draeger.medical.sdccc.messages.mapping.ManipulationData;
import com.draeger.medical.sdccc.messages.mapping.ManipulationData_;
import com.draeger.medical.sdccc.messages.mapping.ManipulationParameter;
import com.draeger.medical.sdccc.messages.mapping.ManipulationParameter_;
import com.draeger.medical.sdccc.messages.mapping.MdibVersionGroupEntity;
import com.draeger.medical.sdccc.messages.mapping.MdibVersionGroupEntity_;
import com.draeger.medical.sdccc.messages.mapping.MessageContent;
import com.draeger.medical.sdccc.messages.mapping.MessageContent_;
import com.draeger.medical.sdccc.messages.mapping.HTTPHeaderEntity;
import com.draeger.medical.sdccc.messages.mapping.HTTPHeaderEntity_;
import com.draeger.medical.sdccc.util.Constants;
import com.draeger.medical.sdccc.util.TestRunObserver;
import com.draeger.medical.sdccc.util.XPathExtractor;
import com.draeger.medical.t2iapi.ResponseTypes;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.criteria.Subquery;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import org.apache.commons.io.ByteOrderMark;
import org.apache.commons.io.input.BOMInputStream;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.somda.sdc.dpws.CommunicationLog;
import org.somda.sdc.dpws.soap.ApplicationInfo;
import org.somda.sdc.dpws.soap.CommunicationContext;
import org.somda.sdc.dpws.soap.HttpApplicationInfo;
import org.somda.sdc.dpws.soap.SoapConstants;
import org.somda.sdc.dpws.soap.wsaddressing.WsAddressingConstants;

/**
 * Storage for incoming and outgoing messages.
 */
@Singleton
public class MessageStorage implements AutoCloseable {
    private static final Logger LOG = LogManager.getLogger(MessageStorage.class);

    private static final int FETCH_SIZE = 10;

    // Prefixes of XML declarations according to Appendix F.1 of the XML Standard
    private static final int XML_DECLARATION_PREFIX_LENGTH = 4;
    private static final Map<byte[], Charset> XML_DECLARATION_PREFIXES = Map.of(
            new byte[] {0x00, 0x00, 0x00, 0x3C},
            Charset.forName("UTF-32BE"),
            new byte[] {0x3C, 0x00, 0x00, 0x00},
            Charset.forName("UTF-32LE"),
            new byte[] {0x00, 0x3C, 0x00, 0x3F},
            StandardCharsets.UTF_16BE,
            new byte[] {0x3C, 0x00, 0x3F, 0x00},
            StandardCharsets.UTF_16LE,
            new byte[] {0x3C, 0x3F, 0x78, 0x6D},
            StandardCharsets.US_ASCII, // any ASCII-compatible charset
            // AFAIK, all charsets of the EBCDIC family have common bit-patterns for the characters used
            // in the XML declaration. Hence, any of them could be used to decode the declaration. However,
            // only 'ebcdic-international-500+euro' was tested in this respect and hence it is used as a
            // placeholder for 'any EBCDIC charset'.
            new byte[] {0x4C, 0x6F, (byte) 0xA7, (byte) 0x94},
            Charset.forName("ebcdic-international-500+euro"));
    private static final List<String> SDC_MIME_TYPES = List.of("application/soap+xml", "application/xml");

    private static final String HTTP_HEADER_NAME_CONTENT_TYPE = "content-type";
    private static final String CREATE_MESSAGE_STREAM_CALLED_ON_CLOSED_STORAGE =
            "createMessageStream called on closed storage";
    private static final String GET_UNIQUE_SEQUENCE_IDS_CALLED_ON_CLOSED_STORAGE =
            "getUniqueSequenceIds called on closed storage";
    private static final String GET_INBOUND_MESSAGES_CALLED_ON_CLOSED_STORAGE =
            "getInboundMessages called on closed storage";
    private static final String GET_OUTBOUND_MESSAGES_CALLED_ON_CLOSED_STORAGE =
            "getOutboundMessages called on closed storage";
    private static final String GET_INBOUND_SOAP_MESSAGES_CALLED_ON_CLOSED_STORAGE =
            "getInboundSoapMessages called on closed storage";
    private static final String GET_INBOUND_MESSAGE_BY_BODY_TYPE_CALLED_ON_CLOSED_STORAGE =
            "getInboundMessagesByBodyType called on closed storage";
    private static final String GET_INBOUND_MESSAGE_BY_TIME_INTERVAL_CALLED_ON_CLOSED_STORAGE =
            "getInboundMessagesByTimeInterval called on closed storage";
    private static final String GET_MANIPULATION_DATA_BY_MANIPULATION =
            "getManipulationDataByManipulation called on closed storage";
    private static final String FILTERING_FOR_GIVEN_ELEMENT_NAME_NOT_IMPLEMENTED =
            "Filtering for the given element name is not supported due to MdibVersionGroups "
                    + "not being parsed for elements with the name %s .";
    private static final String INCONSISTENT_CHARSET_DECLARATION_WITH_ORIGINS =
            "MessageID=%s: Inconsistent charset" + " declaration: %s, but %s";
    private static final String HTTP_HEADER_ORIGIN = "HTTP Header states '%s'";
    private static final String BOM_ORIGIN = "Unicode Byte Order Mark for %s found";
    private static final String XML_DECLARATION_ORIGIN = "XML Declaration states '%s'";
    private static final String XML_DECLARATION_PREFIX_ORIGIN = "XML Declaration is encoded in %s";

    private final Pattern charsetPattern = Pattern.compile(".*;\\s*charset\\s*=\\s*([^;]*).*");
    private final Pattern encodingFromXmlDeclarationPatternDoubleQuotes =
            Pattern.compile(".*<\\?.*encoding\\s*=\\s*\"([^\"]*)\".*\\?>.*", Pattern.DOTALL);
    private final Pattern encodingFromXmlDeclarationPatternSingleQuotes =
            Pattern.compile(".*<\\?.*encoding\\s*=\\s*'([^']*)'.*\\?>.*", Pattern.DOTALL);

    private final MessageFactory messageFactory;

    private final AtomicBoolean closed;

    private final XPathExtractor actionExtractor;
    private final XMLInputFactory xmlInputFactory;

    // the containing class has to be singleton, because we want only one SessionFactory
    private final SessionFactory sessionFactory;

    private final HibernateConfig configuration;

    private final ArrayBlockingQueue<DatabaseEntry> messageQueue;
    private final List<DatabaseInteractionThread> databaseInteractionThreads;
    private final ReentrantLock queueExitLock;
    private final ReentrantLock closeLock;

    private final int blockingQueueSize;

    private final CyclicBarrier flushBarrier;

    private final TestRunObserver testRunObserver;

    @Inject
    MessageStorage(
            @Named(TestSuiteConfig.COMMLOG_MESSAGE_BUFFER_SIZE) final int blockingQueueSize,
            final MessageFactory messageFactory,
            final HibernateConfig configuration,
            final TestRunObserver testRunObserver) {
        this.messageFactory = messageFactory;
        this.testRunObserver = testRunObserver;
        this.closed = new AtomicBoolean();
        this.blockingQueueSize = blockingQueueSize;

        this.actionExtractor = new XPathExtractor(String.format("//%s:Action", WsAddressingConstants.NAMESPACE_PREFIX));

        this.configuration = configuration;
        this.sessionFactory = this.configuration.getConfiguration().buildSessionFactory();

        this.messageQueue = new ArrayBlockingQueue<>(this.blockingQueueSize);
        this.queueExitLock = new ReentrantLock();
        this.closeLock = new ReentrantLock();

        final int logicalProcessorCount = Runtime.getRuntime().availableProcessors();
        final int logicalProcessorsToUse;

        if (logicalProcessorCount > 2) {
            logicalProcessorsToUse = logicalProcessorCount / 2;
        } else {
            logicalProcessorsToUse = 1;
        }

        LOG.info(
                "Logical processor count is {}. Will use {} database interaction threads.",
                logicalProcessorCount,
                logicalProcessorsToUse);

        // database interaction threads plus the main flush functions caller thread
        this.flushBarrier = new CyclicBarrier(logicalProcessorsToUse + 1);

        this.databaseInteractionThreads = new ArrayList<>(logicalProcessorsToUse);
        for (int i = 0; i < logicalProcessorsToUse; i++) {
            final DatabaseInteractionThread databaseInteractionThread = new DatabaseInteractionThread();
            databaseInteractionThread.setDaemon(true);
            databaseInteractionThread.start();
            this.databaseInteractionThreads.add(databaseInteractionThread);
        }

        this.xmlInputFactory = XMLInputFactory.newInstance();
    }

    public XPathExtractor getActionExtractor() {
        return actionExtractor;
    }

    /**
     * Adds message to the message database.
     *
     * @param message to add to the database
     */
    public void addMessage(final DatabaseEntry message) {
        this.closeLock.lock();
        try {
            if (this.closed.get()) {
                final String errorString = "addMessageContent called on closed storage";
                LOG.error(errorString);
                testRunObserver.invalidateTestRun(errorString);
                return;
            }

            try {
                this.messageQueue.put(message);
            } catch (final InterruptedException e) {
                LOG.error("unable to put message content into queue", e);
                testRunObserver.invalidateTestRun(e);
            }
        } finally {
            this.closeLock.unlock();
        }
    }

    public XMLInputFactory getXmlInputFactory() {
        return xmlInputFactory;
    }

    private MessageContent convertMessageToMessageContent(final Message message) {
        boolean isSOAP = false;
        String body = "";
        final Set<String> actions = new HashSet<>();
        final List<MdibVersionGroupEntity.MdibVersionGroup> mdibVersionGroups = new LinkedList<>();
        final byte[] bodyBytes = message.getFinalMemory();
        if (bodyBytes.length > 0) {
            final Charset charset = determineCharsetFromMessage(message);
            // TODO: would it not be better to use CharsetDecoder here? (https://github.com/Draegerwerk/SDCcc/issues/2)
            body = new String(message.getFinalMemory(), charset);
            isSOAP = processMessageBody(body, actions, mdibVersionGroups);
        }
        return new MessageContent(
                body,
                message.getCommunicationContext(),
                message.getDirection(),
                message.getMessageType(),
                message.getTimestamp(),
                message.getNanoTimestamp(),
                mdibVersionGroups,
                actions,
                message.getID(),
                isSOAP);
    }

    private boolean processMessageBody(
            final String body,
            final Set<String> actions,
            final List<MdibVersionGroupEntity.MdibVersionGroup> mdibVersionGroups) {
        var isSOAP = false;
        try {
            final XMLEventReader reader = this.getXmlInputFactory().createXMLEventReader(new StringReader(body));

            while (reader.hasNext()) {
                final XMLEvent nextEvent = reader.nextEvent();

                if (nextEvent.isStartElement()) {
                    final StartElement startElement = nextEvent.asStartElement();
                    if (startElement.getName().getLocalPart().equals("Action")
                            && startElement.getName().getNamespaceURI().equals(WsAddressingConstants.NAMESPACE)) {
                        handleActionEvent(actions, reader);
                    } else if (startElement.getName().getLocalPart().equals("Body")
                            && startElement.getName().getNamespaceURI().equals(SoapConstants.NAMESPACE)) {
                        handleSoapBodyEvent(mdibVersionGroups, reader);
                    } else if (startElement.getName().getLocalPart().equals("Envelope")
                            && startElement.getName().getNamespaceURI().equals(SoapConstants.NAMESPACE)) {
                        isSOAP = true;
                    }
                }
            }
        } catch (final XMLStreamException e) {
            LOG.trace(
                    "unable to extract action or body from message content, " + "this is expected for invalid messages",
                    e);
        }
        return isSOAP;
    }

    private ManipulationData convertManipulationInfoToManipulationData(final ManipulationInfo manipulationInfo) {
        return new ManipulationData(
                manipulationInfo.getStartTimestamp(),
                manipulationInfo.getFinishTimestamp(),
                manipulationInfo.getResult(),
                manipulationInfo.getMethodName(),
                manipulationInfo.getParameter(),
                manipulationInfo.getID());
    }

    protected Charset determineCharsetFromMessage(final Message message) {
        // Note: charset can be determined from (in the order of precedence)
        //       1. HTTP Header
        //       2. Unicode Byte Order Mark
        //       3. XML Declaration
        try {

            // 1. check HTTP Header
            final Charset charsetFromHttpHeader = determineCharsetFromHttpHeader(message);
            // 2. check Unicode Byte Order Mark
            final Charset charsetFromUnicodeByteOrderMark = determineCharsetFromUnicodeByteOrderMark(message);
            // 3. check XML Declaration
            final Charset charsetFromXmlDeclaration = determineCharsetFromXmlDeclaration(message);

            checkFullCharsetConsistency(
                    charsetFromHttpHeader,
                    charsetFromUnicodeByteOrderMark,
                    charsetFromXmlDeclaration,
                    (String originA, String valueA, String originB, String valueB) ->
                            this.testRunObserver.invalidateTestRun(String.format(
                                    INCONSISTENT_CHARSET_DECLARATION_WITH_ORIGINS,
                                    message.getID(),
                                    String.format(originA, valueA),
                                    String.format(originB, valueB))));

            final Charset charset = chooseMessageCharset(
                    charsetFromHttpHeader, charsetFromUnicodeByteOrderMark, charsetFromXmlDeclaration);

            checkMimeType(message);

            if (!charset.equals(StandardCharsets.UTF_8)) {
                String charsetWithOrigin = String.format("'%s' in Unicode Byte Order Mark", charset);
                if (charset.equals(charsetFromHttpHeader)) {
                    charsetWithOrigin = String.format("'%s' in HTTP Header", charset);
                } else if (charset.equals(charsetFromXmlDeclaration)) {
                    charsetWithOrigin = String.format("'%s' in XML Declaration", charset);
                }
                this.testRunObserver.invalidateTestRun(String.format(
                        "Encountered a message whose encoding is declared to be %s. This violates"
                                + " MDPWS:R0007_0 - SOAP ENVELOPEs SHALL be encoded by using UTF-8.",
                        charsetWithOrigin));
            }

            return charset;

        } catch (Exception e) {
            return StandardCharsets.UTF_8;
        }
    }

    private Charset chooseMessageCharset(
            @Nullable final Charset charsetFromHttpHeader,
            @Nullable final Charset charsetFromUnicodeByteOrderMark,
            @Nullable final Charset charsetFromXmlDeclaration) {
        final Charset charset;
        if (charsetFromHttpHeader != null) {
            charset = charsetFromHttpHeader;
        } else {
            if (charsetFromUnicodeByteOrderMark != null) {
                // consistency between BOM and XMLDeclaration has already been checked in
                // determineCharsetFromXMLDeclaration(). No need to do it again.
                charset = charsetFromUnicodeByteOrderMark;
            } else {
                if (charsetFromXmlDeclaration != null) {
                    charset = charsetFromXmlDeclaration;
                } else {
                    this.testRunObserver.invalidateTestRun("Message encoding could not be determined."
                            + " Please ensure that all Messages send by the Device under Test declare their encoding"
                            + " either in the HTTP Header, in the Unicode Byte Order Mark, or in the XML Declaration"
                            + " as mandated by the XML Standard.");
                    charset = StandardCharsets.UTF_8;
                }
            }
        }
        return charset;
    }

    private void checkFullCharsetConsistency(
            @Nullable final Charset charsetFromHttpHeader,
            @Nullable final Charset charsetFromUnicodeByteOrderMark,
            @Nullable final Charset charsetFromXmlDeclaration,
            final InconsistencyReport report) {

        if (charsetFromHttpHeader != null) {
            if (charsetFromUnicodeByteOrderMark != null
                    && !charsetFromUnicodeByteOrderMark.equals(charsetFromHttpHeader)) {
                report.report(
                        HTTP_HEADER_ORIGIN,
                        charsetFromHttpHeader.toString(),
                        BOM_ORIGIN,
                        charsetFromUnicodeByteOrderMark.toString());
            } else if (charsetFromXmlDeclaration != null && !charsetFromXmlDeclaration.equals(charsetFromHttpHeader)) {
                report.report(
                        HTTP_HEADER_ORIGIN,
                        charsetFromHttpHeader.toString(),
                        XML_DECLARATION_ORIGIN,
                        charsetFromXmlDeclaration.toString());
            }
        } else {
            if (charsetFromXmlDeclaration != null
                    && charsetFromUnicodeByteOrderMark != null
                    && !charsetFromUnicodeByteOrderMark.equals(charsetFromXmlDeclaration)) {
                report.report(
                        BOM_ORIGIN,
                        charsetFromUnicodeByteOrderMark.toString(),
                        XML_DECLARATION_ORIGIN,
                        charsetFromXmlDeclaration.toString());
            }
        }
    }

    private void checkMimeType(final Message message) {
        final ApplicationInfo applicationInfo =
                message.getCommunicationContext().getApplicationInfo();
        if (applicationInfo instanceof HttpApplicationInfo) {
            final List<String> contentTypeHeaderValues =
                    ((HttpApplicationInfo) applicationInfo).getHeaders().get(HTTP_HEADER_NAME_CONTENT_TYPE);
            for (final String value : contentTypeHeaderValues) {
                final int index = value.indexOf(";");
                final String mimeType;
                if (index >= 0) {
                    mimeType = value.substring(0, index);
                } else {
                    mimeType = value;
                }
                if (!SDC_MIME_TYPES.contains(mimeType)) {
                    this.testRunObserver.invalidateTestRun(String.format(
                            "encountered a SOAP Envelope whose mimeType '%s' (declared in its "
                                    + "HTTP Header) indicates that it was not serialized as 'application/soap+xml' and"
                                    + "that hence violates the definition of a SOAP TEXT ENVELOPE in MDPWS Section 3.1.",
                            mimeType));
                }
            }
        }
    }

    private Charset determineCharsetFromUnicodeByteOrderMark(final Message message) {
        Charset charsetFromUnicodeByteOrderMark = null;
        try {
            final ByteArrayInputStream in = new ByteArrayInputStream(message.getFinalMemory());
            final BOMInputStream bomIn = new BOMInputStream(
                    in,
                    ByteOrderMark.UTF_8,
                    ByteOrderMark.UTF_16LE,
                    ByteOrderMark.UTF_16BE,
                    ByteOrderMark.UTF_32LE,
                    ByteOrderMark.UTF_32BE);
            if (!bomIn.hasBOM()) {
                LOG.trace("Unable to determine charset from byte order mark for message with ID '" + message.getID()
                        + "'. Will use another option.");
            } else if (bomIn.hasBOM(ByteOrderMark.UTF_8)) {
                charsetFromUnicodeByteOrderMark = StandardCharsets.UTF_8;
            } else if (bomIn.hasBOM(ByteOrderMark.UTF_16LE)) {
                charsetFromUnicodeByteOrderMark = StandardCharsets.UTF_16LE;
            } else if (bomIn.hasBOM(ByteOrderMark.UTF_16BE)) {
                charsetFromUnicodeByteOrderMark = StandardCharsets.UTF_16BE;
            } else if (bomIn.hasBOM(ByteOrderMark.UTF_32LE)) {
                charsetFromUnicodeByteOrderMark = Charset.forName("UTF_32LE");
            } else if (bomIn.hasBOM(ByteOrderMark.UTF_32BE)) {
                charsetFromUnicodeByteOrderMark = Charset.forName("UTF_32BE");
            }
        } catch (IOException e) {
            this.testRunObserver.invalidateTestRun("Unable to read message contents", e);
        }
        return charsetFromUnicodeByteOrderMark;
    }

    private Charset determineCharsetFromXmlDeclaration(final Message message) {
        final Charset charsetFromBOM = determineCharsetFromUnicodeByteOrderMark(message);
        final Charset charsetFromPrefix = determineCharsetFromXmlDeclarationPrefix(message);

        Charset result = null;
        if (charsetFromBOM != null) {
            result = determineCharsetFromXmlDeclarationInternal(message, charsetFromBOM);
        } else {
            if (charsetFromPrefix != null) {
                result = determineCharsetFromXmlDeclarationInternal(message, charsetFromPrefix);
            }
        }

        checkCharsetConsistency(
                charsetFromBOM,
                charsetFromPrefix,
                result,
                (String originA, String valueA, String originB, String valueB) ->
                        this.testRunObserver.invalidateTestRun(String.format(
                                INCONSISTENT_CHARSET_DECLARATION_WITH_ORIGINS,
                                message.getID(),
                                String.format(originA, valueA),
                                String.format(originB, valueB))));

        return result;
    }

    private void checkCharsetConsistency(
            final @javax.annotation.Nullable Charset charsetFromBOM,
            final @javax.annotation.Nullable Charset charsetFromPrefix,
            final @javax.annotation.Nullable Charset charsetFromDeclaration,
            final InconsistencyReport report) {
        if (charsetFromDeclaration == null) {
            if (charsetFromBOM != null
                    && charsetFromPrefix != null
                    && !bomAndPrefixAreCompatible(charsetFromBOM, charsetFromPrefix)) {
                report.report(
                        XML_DECLARATION_PREFIX_ORIGIN,
                        charsetFromPrefix.toString(),
                        BOM_ORIGIN,
                        charsetFromBOM.toString());
            }
        } else {
            if (charsetFromBOM != null && !charsetFromBOM.equals(charsetFromDeclaration)) {
                report.report(
                        XML_DECLARATION_ORIGIN,
                        charsetFromDeclaration.toString(),
                        BOM_ORIGIN,
                        charsetFromBOM.toString());
            }

            final boolean charset_declaration_inconsistent =
                    charsetFromPrefix != null && !charsetFromPrefix.equals(charsetFromDeclaration);
            // EBCDIC Prefix is consistent with any charset from the EBCDIC family.
            final boolean special_case_EBCDIC_family = charsetFromPrefix != null
                    && charsetFromPrefix.equals(Charset.forName("ebcdic-international-500+euro"))
                    && isEBCDIC(charsetFromDeclaration);
            // UTF-8 Prefix is consistent with any charset using the same bit pattern for the ASCII characters
            // as UTF-8. Since we were able to read the XML Declaration successfully we can assume this is the
            // case.
            final boolean special_case_ASCII_compatible =
                    charsetFromPrefix != null && charsetFromPrefix.equals(StandardCharsets.US_ASCII);

            if (charset_declaration_inconsistent && !special_case_EBCDIC_family && !special_case_ASCII_compatible) {
                report.report(
                        XML_DECLARATION_ORIGIN,
                        charsetFromDeclaration.toString(),
                        XML_DECLARATION_PREFIX_ORIGIN,
                        charsetFromPrefix.toString());
            }
        }
    }

    private boolean bomAndPrefixAreCompatible(final Charset charsetFromBOM, final Charset charsetFromPrefix) {
        if (StandardCharsets.US_ASCII.equals(charsetFromPrefix) && StandardCharsets.UTF_8.equals(charsetFromBOM)) {
            return true;
        }
        return charsetFromPrefix.equals(charsetFromBOM);
    }

    private boolean isEBCDIC(final Charset charset) {
        boolean result = false;
        for (String alias : charset.aliases()) {
            if (alias.toLowerCase().startsWith("ebcdic")) {
                result = true;
                break;
            }
        }
        return result;
    }

    private Charset determineCharsetFromXmlDeclarationPrefix(final Message message) {
        final ByteArrayInputStream in = new ByteArrayInputStream(message.getFinalMemory());
        final BOMInputStream bomIn = new BOMInputStream(in, ByteOrderMark.UTF_8);
        try {
            Charset result = null;
            final byte[] msg = bomIn.readNBytes(XML_DECLARATION_PREFIX_LENGTH);
            for (Map.Entry<byte[], Charset> e : XML_DECLARATION_PREFIXES.entrySet()) {
                if (startsWith(msg, e.getKey())) {
                    result = e.getValue();
                    break;
                }
            }
            return result;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Utility method to check if one byte array starts with a specified sequence of bytes.
     *
     * @param array  The array to check
     * @param prefix The prefix bytes to test for
     * @return true if the array starts with the bytes from the prefix
     */
    private static boolean startsWith(final byte[] array, final byte[] prefix) {
        if (array == prefix) {
            return true;
        }
        return startsWithInternal1(array, prefix);
    }

    private static boolean startsWithInternal1(final byte[] array, final byte[] prefix) {
        if (prefix.length > array.length) {
            return false;
        }
        return startsWithInternal2(array, prefix);
    }

    private static boolean startsWithInternal2(final byte[] array, final byte[] prefix) {
        final int prefixLength = prefix.length;
        for (int i = 0; i < prefixLength; i++) {
            if (array[i] != prefix[i]) {
                return false;
            }
        }

        return true;
    }

    private Charset determineCharsetFromXmlDeclarationInternal(final Message message, final Charset encoding) {
        Charset charsetFromXmlDeclaration = null;

        final String content = new String(message.getFinalMemory(), encoding);
        final Matcher matcher = encodingFromXmlDeclarationPatternDoubleQuotes.matcher(content);
        if (matcher.matches()) {
            charsetFromXmlDeclaration = Charset.forName(matcher.group(1));
        } else {
            final Matcher matcher2 = encodingFromXmlDeclarationPatternSingleQuotes.matcher(content);
            if (matcher2.matches()) {
                charsetFromXmlDeclaration = Charset.forName(matcher2.group(1));
            }
        }
        return charsetFromXmlDeclaration;
    }

    private Charset determineCharsetFromHttpHeader(final Message message) {
        Charset charsetFromHttpHeader = null;
        final ApplicationInfo applicationInfo =
                message.getCommunicationContext().getApplicationInfo();
        if (applicationInfo instanceof HttpApplicationInfo) {
            final List<String> contentTypeHeaderValues =
                    ((HttpApplicationInfo) applicationInfo).getHeaders().get(HTTP_HEADER_NAME_CONTENT_TYPE);
            for (final String value : contentTypeHeaderValues) {
                final Matcher matcher = charsetPattern.matcher(value);
                if (matcher.matches()) {
                    final String charsetName =
                            stripQuotes(matcher.group(1).trim()).trim();
                    try {
                        charsetFromHttpHeader = Charset.forName(charsetName);
                    } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
                        // in this case the message contained a Content-Type Header with a charset directive,
                        // but the encoding given in that directive was invalid - which is a finding the user should
                        // be notified about.
                        // NOTE: returning null in this case is ok as determineCharsetFromMessage() will then try to
                        //       use other clues to determine the charset and hopefully succeed in correctly decoding
                        //       the Message before storing it in the DB.
                        this.testRunObserver.invalidateTestRun(
                                String.format("Encountered invalid/unknown charset '%s' in HTTP Header", charsetName),
                                e);
                    }
                }
            }
        }
        return charsetFromHttpHeader;
    }

    private String stripQuotes(final String str) {
        if ((str.startsWith("\"") && str.endsWith("\"")) || (str.startsWith("'") && str.endsWith("'"))) {
            return str.substring(1, str.length() - 1);
        } else {
            return str;
        }
    }

    private void handleActionEvent(final Set<String> actions, final XMLEventReader reader) throws XMLStreamException {
        final XMLEvent nextEvent;
        nextEvent = reader.nextEvent();
        if (nextEvent.isCharacters()) {
            actions.add(nextEvent.asCharacters().getData());
        } else {
            LOG.warn("empty action element encountered");
            actions.add("");
        }
    }

    private boolean checkElementSupportsMdibVersionSorting(final QName name) {
        return Constants.RELEVANT_REPORT_BODIES.contains(name) || Constants.MSG_GET_MDIB_RESPONSE.equals(name);
    }

    private void handleSoapBodyEvent(
            final List<MdibVersionGroupEntity.MdibVersionGroup> mdibVersionGroups, final XMLEventReader reader)
            throws XMLStreamException {
        long childCounter = 0;

        XMLEvent nextEvent;
        var level = 0;
        while (level >= 0) {
            nextEvent = reader.nextEvent();
            if (nextEvent.isStartElement()) {
                level++;
                // only add elements on level 1, i.e. direct children of the SOAP body
                if (level == 1) {
                    childCounter++;

                    final StartElement startElement = nextEvent.asStartElement();

                    final String bodyElementNameString = startElement.getName().toString();

                    long mdibVersion = -3L;
                    String sequenceId = null;

                    if (this.checkElementSupportsMdibVersionSorting(startElement.getName())) {

                        final Attribute mdibVersionAttribute = startElement.getAttributeByName(Constants.MDIB_VERSION);

                        if (mdibVersionAttribute == null) {
                            mdibVersion = 0L;
                        } else if (mdibVersionAttribute.getValue() == null) {
                            mdibVersion = -2L;
                            this.testRunObserver.invalidateTestRun(
                                    "Calling getValue on mdibVersionAttribute resulted in null. "
                                            + "Saved -2L as a replacement value.");
                        } else if (mdibVersionAttribute.getValue().equals("")) {
                            mdibVersion = -1L;
                            this.testRunObserver.invalidateTestRun(
                                    "Encountered MdibVersion attribute that has an empty string as its value"
                                            + "and saved -1L as a replacement value.");
                        } else {
                            try {
                                mdibVersion = Long.parseLong(mdibVersionAttribute.getValue());
                            } catch (NumberFormatException e) {
                                this.testRunObserver.invalidateTestRun(e);
                            }
                        }

                        final Attribute sequenceIdAttribute = startElement.getAttributeByName(Constants.SEQUENCE_ID);

                        if (sequenceIdAttribute != null) {
                            sequenceId = sequenceIdAttribute.getValue();
                        } else {
                            this.testRunObserver.invalidateTestRun(String.format(
                                    "Encountered body with the QName %s " + "without a SequenceId attribute.",
                                    startElement.getName()));
                        }
                    }
                    mdibVersionGroups.add(new MdibVersionGroupEntity.MdibVersionGroup(
                            mdibVersion, sequenceId, bodyElementNameString));
                }
            } else if (nextEvent.isEndElement()) {
                level--;
            }
        }

        if (childCounter > 1) {
            this.testRunObserver.invalidateTestRun(
                    "Encountered multiple elements in soap body, but more than one are not allowed.");
        }
    }

    /**
     * Creates a {@linkplain Message} object in which the message information can be written.
     *
     * @param path                 protocol type used, i.e. UDP or TCP
     * @param direction            the direction of the message, i.e. inbound or outbound
     * @param messageType          type of the message, i.e. request, response
     * @param communicationContext context containing transport and application information
     * @return new message instance, already inserted into the global message list
     * @throws IOException if MessageCache was already closed
     */
    public Message createMessageStream(
            final CommunicationLog.TransportType path,
            final CommunicationLog.Direction direction,
            final CommunicationLog.MessageType messageType,
            final CommunicationContext communicationContext)
            throws IOException {
        if (this.closed.get()) {
            LOG.error(CREATE_MESSAGE_STREAM_CALLED_ON_CLOSED_STORAGE);
            throw new IOException(CREATE_MESSAGE_STREAM_CALLED_ON_CLOSED_STORAGE);
        }
        return messageFactory.create(direction, messageType, communicationContext);
    }

    /**
     * Creates a {@linkplain ManipulationInfo} object and adds it to the storage.
     *
     * @param startTime  of the manipulation
     * @param finishTime of the manipulation
     * @param result     of the manipulation
     * @param name       of the manipulation
     * @param parameters of the manipulation
     */
    public void createManipulationInfo(
            final long startTime,
            final long finishTime,
            final ResponseTypes.Result result,
            final String name,
            final List<Pair<String, String>> parameters) {
        final var manipulation = new ManipulationInfo(startTime, finishTime, result, name, parameters, this);
        manipulation.addToStorage();
    }

    /**
     * Closes the message cache, preventing any new messages from being added.
     */
    @Override
    public void close() {
        this.closeLock.lock();
        try {
            if (this.closed.compareAndSet(false, true)) {

                this.flush();

                this.databaseInteractionThreads.forEach(DatabaseInteractionThread::setStopped);
                this.databaseInteractionThreads.forEach(thread -> {
                    try {
                        thread.join();
                    } catch (final InterruptedException e) {
                        LOG.error("unable to wait for database interaction thread termination due to an interrupt", e);
                        testRunObserver.invalidateTestRun(e);
                    }
                });

                this.sessionFactory.close();
                this.configuration.close();
            }
        } finally {
            this.closeLock.unlock();
        }
    }

    /**
     * Sends all queued and buffered messages to the database and does a plausibility check via a select query.
     */
    public synchronized void flush() {

        this.databaseInteractionThreads.forEach(DatabaseInteractionThread::triggerFlush);
        final List<DatabaseEntry> temp = new ArrayList<>(this.blockingQueueSize);
        this.messageQueue.drainTo(temp, this.blockingQueueSize);
        this.awaitFlushBarrier();
        // will not block at the barrier since other threads had already arrived at the barrier
        this.flush(temp);
    }

    private void flush(final List<DatabaseEntry> messageList) {
        this.flush(messageList, false, null);
    }

    private void flush(
            final List<DatabaseEntry> messageList,
            final boolean await,
            @Nullable final DatabaseInteractionThread databaseInteractionThread) {

        if (!messageList.isEmpty()) {

            final var firstElement = messageList.get(0);
            final var lastElement = messageList.get(messageList.size() - 1);
            final String firstID = firstElement.getID();
            final String lastID = lastElement.getID();

            this.transmit(messageList);

            // only uuid shall be selected, thus it is a string criteria query
            final CriteriaQuery<String> criteriaFirst = buildCriteria(firstElement.getClass(), firstID);
            final CriteriaQuery<String> criteriaLast = buildCriteria(lastElement.getClass(), lastID);

            long uuidCount = 0;

            while (uuidCount < 2) {

                uuidCount = 0;

                try (final Stream<String> uuidStream = this.getStreamForQuery(criteriaLast)) {
                    final long lastIDCount = uuidStream.filter(lastID::equals).count();

                    if (lastIDCount < 2) {
                        uuidCount += lastIDCount;
                    } else {
                        throw new RuntimeException("duplicate uuid found in database");
                    }
                }

                try (final Stream<String> uuidStream = this.getStreamForQuery(criteriaFirst)) {
                    final long firstIDCount = uuidStream.filter(firstID::equals).count();

                    if (firstIDCount < 2) {
                        uuidCount += firstIDCount;
                    } else {
                        throw new RuntimeException("duplicate uuid found in database");
                    }
                }

                Thread.yield();
            }
        }

        if (databaseInteractionThread != null) {
            databaseInteractionThread.resetFlushEvent();
        }

        if (await) {
            this.awaitFlushBarrier();
        }
    }

    private CriteriaQuery<String> buildCriteria(final Class<? extends DatabaseEntry> entryClass, final String id) {
        final CriteriaQuery<String> criteria;
        try (final Session session = sessionFactory.openSession()) {
            session.beginTransaction();

            final CriteriaBuilder criteriaBuilder = session.getCriteriaBuilder();
            criteria = criteriaBuilder.createQuery(String.class);
            if (entryClass.equals(Message.class)) {
                final Root<MessageContent> messageContentRoot = criteria.from(MessageContent.class);

                // here we want only the uuid, not the whole row
                criteria.select(messageContentRoot.get(MessageContent_.uuid));

                criteria.where(criteriaBuilder.equal(messageContentRoot.get(MessageContent_.uuid), id));
            } else if (entryClass.equals(ManipulationInfo.class)) {
                final Root<ManipulationData> manipulationDataRoot = criteria.from(ManipulationData.class);

                // here we want only the uuid, not the whole row
                criteria.select(manipulationDataRoot.get(ManipulationData_.uuid));

                criteria.where(criteriaBuilder.equal(manipulationDataRoot.get(ManipulationData_.uuid), id));
            }
        }
        return criteria;
    }

    private void awaitFlushBarrier() {
        try {
            this.flushBarrier.await();
        } catch (InterruptedException | BrokenBarrierException e) {
            LOG.error("the message flush was interrupted", e);
            testRunObserver.invalidateTestRun(e);
        }
    }

    /**
     * Retrieves all SequenceId attribute values that have been seen.
     *
     * @return stream of all SequenceId attribute values that have been seen
     * @throws IOException if storage is closed
     */
    public Stream<String> getUniqueSequenceIds() throws IOException {

        if (this.closed.get()) {
            LOG.error(GET_UNIQUE_SEQUENCE_IDS_CALLED_ON_CLOSED_STORAGE);
            throw new IOException(GET_UNIQUE_SEQUENCE_IDS_CALLED_ON_CLOSED_STORAGE);
        }

        final CriteriaQuery<String> criteria;

        try (final Session session = sessionFactory.openSession()) {
            final CriteriaBuilder criteriaBuilder = session.getCriteriaBuilder();
            criteria = criteriaBuilder.createQuery(String.class);
            final Root<MdibVersionGroupEntity> mdibVersionGroupEntityRoot = criteria.from(MdibVersionGroupEntity.class);
            criteria.select(mdibVersionGroupEntityRoot.get(MdibVersionGroupEntity_.sequenceId));
            criteria.distinct(true);
        }

        return this.getQueryResult(criteria);
    }

    /**
     * Retrieves all incoming messages.
     *
     * @return container with stream of all inbound {@linkplain MessageContent}s
     * @throws IOException if storage is closed
     */
    public GetterResult<MessageContent> getInboundMessages() throws IOException {

        if (this.closed.get()) {
            LOG.error(GET_INBOUND_MESSAGES_CALLED_ON_CLOSED_STORAGE);
            throw new IOException(GET_INBOUND_MESSAGES_CALLED_ON_CLOSED_STORAGE);
        }

        final CriteriaQuery<MessageContent> criteria;

        try (final Session session = sessionFactory.openSession()) {
            final CriteriaBuilder criteriaBuilder = session.getCriteriaBuilder();
            criteria = criteriaBuilder.createQuery(MessageContent.class);
            final Root<MessageContent> messageContentRoot = criteria.from(MessageContent.class);
            criteria.select(messageContentRoot);
            criteria.where(criteriaBuilder.equal(
                    messageContentRoot.get(MessageContent_.direction), CommunicationLog.Direction.INBOUND));
        }

        final boolean present;
        try (final Stream<MessageContent> countingStream = this.getQueryResult(criteria)) {
            present = countingStream.findAny().isPresent();
        }

        return new GetterResult<>(this.getQueryResult(criteria), present);
    }

    /**
     * Retrieves all outgoing messages.
     *
     * @return container with stream of all outbound {@linkplain MessageContent}s
     * @throws IOException if storage is closed
     */
    public GetterResult<MessageContent> getOutboundMessages() throws IOException {

        if (this.closed.get()) {
            LOG.error(GET_OUTBOUND_MESSAGES_CALLED_ON_CLOSED_STORAGE);
            throw new IOException(GET_OUTBOUND_MESSAGES_CALLED_ON_CLOSED_STORAGE);
        }

        final CriteriaQuery<MessageContent> criteria;

        try (final Session session = sessionFactory.openSession()) {
            final CriteriaBuilder criteriaBuilder = session.getCriteriaBuilder();
            criteria = criteriaBuilder.createQuery(MessageContent.class);
            final Root<MessageContent> messageContentRoot = criteria.from(MessageContent.class);
            criteria.select(messageContentRoot);
            criteria.where(criteriaBuilder.equal(
                    messageContentRoot.get(MessageContent_.direction), CommunicationLog.Direction.OUTBOUND));
        }

        final boolean present;
        try (final Stream<MessageContent> countingStream = this.getQueryResult(criteria)) {
            present = countingStream.findAny().isPresent();
        }

        return new GetterResult<>(this.getQueryResult(criteria), present);
    }

    /**
     * Retrieves all incoming SOAP messages.
     *
     * <p>
     * SOAP messages are considered messages in storage which have a SOAP 1.2 envelope element or application/soap+xml
     * content type.
     *
     * @return container with stream of all matching inbound {@linkplain MessageContent}s
     * @throws IOException if storage is closed
     */
    public GetterResult<MessageContent> getInboundSoapMessages() throws IOException {
        if (this.closed.get()) {
            LOG.error(GET_INBOUND_SOAP_MESSAGES_CALLED_ON_CLOSED_STORAGE);
            throw new IOException(GET_INBOUND_SOAP_MESSAGES_CALLED_ON_CLOSED_STORAGE);
        }

        final CriteriaQuery<MessageContent> messageContentQuery;
        try (final Session session = sessionFactory.openSession()) {
            session.beginTransaction();

            final CriteriaBuilder criteriaBuilder = session.getCriteriaBuilder();
            messageContentQuery = criteriaBuilder.createQuery(MessageContent.class);
            final Root<MessageContent> messageContentRoot = messageContentQuery.from(MessageContent.class);
            messageContentQuery.select(messageContentRoot);

            final Subquery<HTTPHeaderEntity> headerSubQuery = messageContentQuery.subquery(HTTPHeaderEntity.class);
            final Root<HTTPHeaderEntity> httpHeaderEntityRoot = headerSubQuery.from(HTTPHeaderEntity.class);
            headerSubQuery.select(httpHeaderEntityRoot);

            headerSubQuery.where(criteriaBuilder.and(
                    criteriaBuilder.equal(
                            httpHeaderEntityRoot.get(HTTPHeaderEntity_.messageContent),
                            messageContentRoot.get(MessageContent_.incId)),
                    criteriaBuilder.and(
                            criteriaBuilder.equal(
                                    criteriaBuilder.lower(httpHeaderEntityRoot.get(HTTPHeaderEntity_.entryKey)),
                                    HTTP_HEADER_NAME_CONTENT_TYPE),
                            criteriaBuilder.like(
                                    criteriaBuilder.lower(httpHeaderEntityRoot.get(HTTPHeaderEntity_.entryValue)),
                                    criteriaBuilder.literal("%application/soap+xml%")))));

            messageContentQuery.where(criteriaBuilder.and(
                    criteriaBuilder.equal(
                            messageContentRoot.get(MessageContent_.direction), CommunicationLog.Direction.INBOUND),
                    criteriaBuilder.or(
                            criteriaBuilder.isTrue(messageContentRoot.get(MessageContent_.isSOAP)),
                            criteriaBuilder.exists(headerSubQuery))));
        }

        final boolean present;
        try (final Stream<MessageContent> countingStream = this.getQueryResult(messageContentQuery)) {
            present = countingStream.findAny().isPresent();
        }

        return new GetterResult<>(this.getQueryResult(messageContentQuery), present);
    }

    /**
     * Retrieves all incoming SOAP response messages.
     *
     * <p>
     * SOAP messages are considered messages in storage which have a SOAP 1.2 envelope element or application/soap+xml
     * content type.
     *
     * @return container with stream of all matching inbound {@linkplain MessageContent}s
     * @throws IOException if storage is closed
     */
    public GetterResult<MessageContent> getInboundSoapResponseMessages() throws IOException {
        if (this.closed.get()) {
            LOG.error(GET_INBOUND_SOAP_MESSAGES_CALLED_ON_CLOSED_STORAGE);
            throw new IOException(GET_INBOUND_SOAP_MESSAGES_CALLED_ON_CLOSED_STORAGE);
        }

        final CriteriaQuery<MessageContent> messageContentQuery;
        try (final Session session = sessionFactory.openSession()) {
            session.beginTransaction();

            final CriteriaBuilder criteriaBuilder = session.getCriteriaBuilder();
            messageContentQuery = criteriaBuilder.createQuery(MessageContent.class);
            final Root<MessageContent> messageContentRoot = messageContentQuery.from(MessageContent.class);
            messageContentQuery.select(messageContentRoot);

            final Subquery<HTTPHeaderEntity> headerSubQuery = messageContentQuery.subquery(HTTPHeaderEntity.class);
            final Root<HTTPHeaderEntity> httpHeaderEntityRoot = headerSubQuery.from(HTTPHeaderEntity.class);
            headerSubQuery.select(httpHeaderEntityRoot);

            headerSubQuery.where(criteriaBuilder.and(
                    criteriaBuilder.equal(
                            httpHeaderEntityRoot.get(HTTPHeaderEntity_.messageContent),
                            messageContentRoot.get(MessageContent_.incId)),
                    criteriaBuilder.and(
                            criteriaBuilder.equal(
                                    criteriaBuilder.lower(httpHeaderEntityRoot.get(HTTPHeaderEntity_.entryKey)),
                                    HTTP_HEADER_NAME_CONTENT_TYPE),
                            criteriaBuilder.like(
                                    criteriaBuilder.lower(httpHeaderEntityRoot.get(HTTPHeaderEntity_.entryValue)),
                                    criteriaBuilder.literal("%application/soap+xml%")))));

            messageContentQuery.where(criteriaBuilder.and(
                    criteriaBuilder.equal(
                            messageContentRoot.get(MessageContent_.direction), CommunicationLog.Direction.INBOUND),
                    criteriaBuilder.equal(
                            messageContentRoot.get(MessageContent_.messageType), CommunicationLog.MessageType.RESPONSE),
                    criteriaBuilder.or(
                            criteriaBuilder.isTrue(messageContentRoot.get(MessageContent_.isSOAP)),
                            criteriaBuilder.exists(headerSubQuery))));
        }

        final boolean present;
        try (final Stream<MessageContent> countingStream = this.getQueryResult(messageContentQuery)) {
            present = countingStream.findAny().isPresent();
        }

        return new GetterResult<>(this.getQueryResult(messageContentQuery), present);
    }

    /**
     * Retrieves all outgoing HTTP messages which match any of the provided actions and any of the provided headers.
     *
     * <p>
     * HTTP messages are considered messages in storage which have their scheme set to http or https.
     *
     * @param bodyTypes to match messages against
     * @param headers   to match messages against
     * @return container with stream of all matching outgoing {@linkplain MessageContent}s
     * @throws IOException if storage is closed
     */
    public GetterResult<MessageContent> getOutboundHttpMessagesByBodyTypeAndHeaders(
            final List<QName> bodyTypes, final List<AbstractMap.SimpleImmutableEntry<String, String>> headers)
            throws IOException {
        if (this.closed.get()) {
            final String failureString = "getOutboundHttpMessagesByBodyTypeAndHeaders called on closed storage";
            LOG.error(failureString);
            throw new IOException(failureString);
        }

        final CriteriaQuery<MessageContent> messageContentQuery;
        try (final Session session = sessionFactory.openSession()) {
            session.beginTransaction();

            final CriteriaBuilder criteriaBuilder = session.getCriteriaBuilder();
            messageContentQuery = criteriaBuilder.createQuery(MessageContent.class);
            final Root<MessageContent> messageContentRoot = messageContentQuery.from(MessageContent.class);
            messageContentQuery.select(messageContentRoot);

            final Subquery<MdibVersionGroupEntity> mdibVersionGroupSubQuery =
                    messageContentQuery.subquery(MdibVersionGroupEntity.class);
            final Root<MdibVersionGroupEntity> mdibVersionGroupEntityRoot =
                    mdibVersionGroupSubQuery.from(MdibVersionGroupEntity.class);
            mdibVersionGroupSubQuery.select(mdibVersionGroupEntityRoot);
            final List<Predicate> bodyElementPredicates = new ArrayList<>();

            for (final QName bodyElement : bodyTypes) {
                bodyElementPredicates.add(criteriaBuilder.equal(
                        mdibVersionGroupEntityRoot.get(MdibVersionGroupEntity_.bodyElement), bodyElement.toString()));
            }

            mdibVersionGroupSubQuery.where(criteriaBuilder.and(
                    criteriaBuilder.equal(
                            mdibVersionGroupEntityRoot.get(MdibVersionGroupEntity_.messageContent),
                            messageContentRoot.get(MessageContent_.incId)),
                    criteriaBuilder.or(bodyElementPredicates.toArray(new Predicate[0]))));

            final Subquery<HTTPHeaderEntity> headerSubQuery = messageContentQuery.subquery(HTTPHeaderEntity.class);
            final Root<HTTPHeaderEntity> httpHeaderEntityRoot = headerSubQuery.from(HTTPHeaderEntity.class);
            headerSubQuery.select(httpHeaderEntityRoot);
            final var headerPredicates = new ArrayList<Predicate>();
            for (final AbstractMap.SimpleImmutableEntry<String, String> header : headers) {
                headerPredicates.add(criteriaBuilder.and(
                        criteriaBuilder.equal(
                                criteriaBuilder.lower(httpHeaderEntityRoot.get(HTTPHeaderEntity_.entryKey)),
                                header.getKey()),
                        criteriaBuilder.equal(
                                criteriaBuilder.lower(httpHeaderEntityRoot.get(HTTPHeaderEntity_.entryValue)),
                                header.getValue())));
            }
            headerSubQuery.where(criteriaBuilder.and(
                    criteriaBuilder.equal(
                            httpHeaderEntityRoot.get(HTTPHeaderEntity_.messageContent),
                            messageContentRoot.get(MessageContent_.incId)),
                    criteriaBuilder.or(headerPredicates.toArray(new Predicate[0]))));

            messageContentQuery.where(criteriaBuilder.and(
                    criteriaBuilder.equal(
                            messageContentRoot.get(MessageContent_.direction), CommunicationLog.Direction.OUTBOUND),
                    criteriaBuilder.or(
                            criteriaBuilder.equal(
                                    criteriaBuilder.lower(messageContentRoot.get(MessageContent_.scheme)),
                                    Constants.HTTP_SCHEME),
                            criteriaBuilder.equal(
                                    criteriaBuilder.lower(messageContentRoot.get(MessageContent_.scheme)),
                                    Constants.HTTPS_SCHEME)),
                    criteriaBuilder.exists(mdibVersionGroupSubQuery),
                    criteriaBuilder.exists(headerSubQuery)));
        }

        final boolean present;
        try (final Stream<MessageContent> countingStream = this.getQueryResult(messageContentQuery)) {
            present = countingStream.findAny().isPresent();
        }

        return new GetterResult<>(this.getQueryResult(messageContentQuery), present);
    }

    /**
     * Retrieves all incoming HTTP messages.
     *
     * <p>
     * HTTP messages are considered messages in storage which have their scheme set to http or https.
     *
     * @return container with stream of all matching inbound {@linkplain MessageContent}s
     * @throws IOException if storage is closed
     */
    public GetterResult<MessageContent> getInboundHttpMessages() throws IOException {
        if (this.closed.get()) {
            LOG.error(GET_INBOUND_SOAP_MESSAGES_CALLED_ON_CLOSED_STORAGE);
            throw new IOException(GET_INBOUND_SOAP_MESSAGES_CALLED_ON_CLOSED_STORAGE);
        }

        final CriteriaQuery<MessageContent> criteria;
        try (final Session session = sessionFactory.openSession()) {
            session.beginTransaction();

            final CriteriaBuilder criteriaBuilder = session.getCriteriaBuilder();
            criteria = criteriaBuilder.createQuery(MessageContent.class);
            final Root<MessageContent> messageContentRoot = criteria.from(MessageContent.class);
            criteria.select(messageContentRoot);
            criteria.where(criteriaBuilder.and(
                    criteriaBuilder.equal(
                            messageContentRoot.get(MessageContent_.direction), CommunicationLog.Direction.INBOUND),
                    criteriaBuilder.or(
                            criteriaBuilder.equal(
                                    criteriaBuilder.lower(messageContentRoot.get(MessageContent_.scheme)),
                                    Constants.HTTP_SCHEME),
                            criteriaBuilder.equal(
                                    criteriaBuilder.lower(messageContentRoot.get(MessageContent_.scheme)),
                                    Constants.HTTPS_SCHEME))));
        }

        final boolean present;
        try (final Stream<MessageContent> countingStream = this.getQueryResult(criteria)) {
            present = countingStream.findAny().isPresent();
        }

        return new GetterResult<>(this.getQueryResult(criteria), present);
    }

    /**
     * Retrieves all incoming messages which match any of the provided body element QNames and at the same time belong
     * to the given SequenceId.
     *
     * <p>
     * Messages are sorted by MdibVersion on the inner join result.
     * </p>
     *
     * @param sequenceId SequenceId attribute value to filter for
     * @param bodyTypes  to match messages against
     * @return container with stream of all matching inbound {@linkplain MessageContent}s
     * @throws IOException if storage is closed
     */
    public GetterResult<MessageContent> getInboundMessagesByBodyTypeAndSequenceId(
            final String sequenceId, final QName... bodyTypes) throws IOException { // TODO: add unittest for this
        if (this.closed.get()) {
            LOG.error(GET_INBOUND_MESSAGE_BY_BODY_TYPE_CALLED_ON_CLOSED_STORAGE);
            throw new IOException(GET_INBOUND_MESSAGE_BY_BODY_TYPE_CALLED_ON_CLOSED_STORAGE);
        }

        for (final QName qname : bodyTypes) {
            if (!this.checkElementSupportsMdibVersionSorting(qname)) {

                final String localErrorMessage = String.format(FILTERING_FOR_GIVEN_ELEMENT_NAME_NOT_IMPLEMENTED, qname);
                this.testRunObserver.invalidateTestRun(localErrorMessage);
                throw new UnsupportedOperationException(localErrorMessage);
            }
        }

        final CriteriaQuery<MessageContent> messageContentQuery;
        try (final Session session = sessionFactory.openSession()) {
            session.beginTransaction();

            final CriteriaBuilder criteriaBuilder = session.getCriteriaBuilder();
            messageContentQuery = criteriaBuilder.createQuery(MessageContent.class);
            final Root<MessageContent> messageContentRoot = messageContentQuery.from(MessageContent.class);
            messageContentQuery.select(messageContentRoot);

            final Subquery<MdibVersionGroupEntity> mdibVersionGroupSubQuery =
                    messageContentQuery.subquery(MdibVersionGroupEntity.class);
            final Root<MdibVersionGroupEntity> mdibVersionGroupEntityRoot =
                    mdibVersionGroupSubQuery.from(MdibVersionGroupEntity.class);
            mdibVersionGroupSubQuery.select(mdibVersionGroupEntityRoot);
            final List<Predicate> bodyElementPredicates = new ArrayList<>();

            for (final QName bodyElement : bodyTypes) {
                bodyElementPredicates.add(criteriaBuilder.and(
                        criteriaBuilder.equal(
                                mdibVersionGroupEntityRoot.get(MdibVersionGroupEntity_.bodyElement),
                                bodyElement.toString()),
                        criteriaBuilder.equal(
                                mdibVersionGroupEntityRoot.get(MdibVersionGroupEntity_.sequenceId), sequenceId)));
            }

            mdibVersionGroupSubQuery.where(criteriaBuilder.and(
                    criteriaBuilder.equal(
                            mdibVersionGroupEntityRoot.get(MdibVersionGroupEntity_.messageContent),
                            messageContentRoot.get(MessageContent_.incId)),
                    criteriaBuilder.or(bodyElementPredicates.toArray(new Predicate[0]))));

            messageContentQuery.where(criteriaBuilder.and(
                    criteriaBuilder.equal(
                            messageContentRoot.get(MessageContent_.direction), CommunicationLog.Direction.INBOUND),
                    criteriaBuilder.exists(mdibVersionGroupSubQuery)));

            messageContentQuery.orderBy(criteriaBuilder.asc(messageContentRoot
                    .join(MessageContent_.mdibVersionGroups)
                    .get(MdibVersionGroupEntity_.mdibVersion)));
        }

        final boolean present;
        try (final Stream<MessageContent> countingStream = this.getQueryResult(messageContentQuery)) {
            present = countingStream.findAny().isPresent();
        }

        return new GetterResult<>(this.getQueryResult(messageContentQuery), present);
    }

    /**
     * Retrieves all incoming messages which match any of the provided body element QNames.
     *
     * <p>
     * Messages are sorted by MdibVersion on the inner join result.
     *
     * @param enableSorting switch to turn off or turn on MdibVersion based sorting
     * @param bodyTypes  to match messages against
     * @return container with stream of all matching inbound {@linkplain MessageContent}s
     * @throws IOException if storage is closed
     */
    public GetterResult<MessageContent> getInboundMessagesByBodyType(
            final boolean enableSorting, final QName... bodyTypes) throws IOException {
        if (this.closed.get()) {
            LOG.error(GET_INBOUND_MESSAGE_BY_BODY_TYPE_CALLED_ON_CLOSED_STORAGE);
            throw new IOException(GET_INBOUND_MESSAGE_BY_BODY_TYPE_CALLED_ON_CLOSED_STORAGE);
        }

        if (enableSorting) {
            for (final QName qname : bodyTypes) {
                if (!this.checkElementSupportsMdibVersionSorting(qname)) {

                    final String localErrorMessage =
                            String.format(FILTERING_FOR_GIVEN_ELEMENT_NAME_NOT_IMPLEMENTED, qname);
                    this.testRunObserver.invalidateTestRun(localErrorMessage);
                    throw new UnsupportedOperationException(localErrorMessage);
                }
            }
        }

        final CriteriaQuery<MessageContent> messageContentQuery;
        try (final Session session = sessionFactory.openSession()) {
            session.beginTransaction();

            final CriteriaBuilder criteriaBuilder = session.getCriteriaBuilder();
            messageContentQuery = criteriaBuilder.createQuery(MessageContent.class);
            final Root<MessageContent> messageContentRoot = messageContentQuery.from(MessageContent.class);
            messageContentQuery.select(messageContentRoot);

            final Subquery<MdibVersionGroupEntity> mdibVersionGroupSubQuery =
                    messageContentQuery.subquery(MdibVersionGroupEntity.class);
            final Root<MdibVersionGroupEntity> mdibVersionGroupEntityRoot =
                    mdibVersionGroupSubQuery.from(MdibVersionGroupEntity.class);
            mdibVersionGroupSubQuery.select(mdibVersionGroupEntityRoot);
            final List<Predicate> bodyElementPredicates = new ArrayList<>();

            for (final QName bodyElement : bodyTypes) {
                bodyElementPredicates.add(criteriaBuilder.equal(
                        mdibVersionGroupEntityRoot.get(MdibVersionGroupEntity_.bodyElement), bodyElement.toString()));
            }

            mdibVersionGroupSubQuery.where(criteriaBuilder.and(
                    criteriaBuilder.equal(
                            mdibVersionGroupEntityRoot.get(MdibVersionGroupEntity_.messageContent),
                            messageContentRoot.get(MessageContent_.incId)),
                    criteriaBuilder.or(bodyElementPredicates.toArray(new Predicate[0]))));

            messageContentQuery.where(criteriaBuilder.and(
                    criteriaBuilder.equal(
                            messageContentRoot.get(MessageContent_.direction), CommunicationLog.Direction.INBOUND),
                    criteriaBuilder.exists(mdibVersionGroupSubQuery)));

            if (enableSorting) {
                messageContentQuery.orderBy(criteriaBuilder.asc(messageContentRoot
                        .join(MessageContent_.mdibVersionGroups)
                        .get(MdibVersionGroupEntity_.mdibVersion)));
            }
        }

        final boolean present;
        try (final Stream<MessageContent> countingStream = this.getQueryResult(messageContentQuery)) {
            present = countingStream.findAny().isPresent();
        }

        return new GetterResult<>(this.getQueryResult(messageContentQuery), present);
    }

    /**
     * Retrieves all incoming messages which match any of the provided body element QNames.
     *
     * <p>
     * Messages are sorted by MdibVersion on the inner join result.
     *
     * @param bodyTypes to match messages against
     * @return container with stream of all matching inbound {@linkplain MessageContent}s
     * @throws IOException if storage is closed
     */
    public GetterResult<MessageContent> getInboundMessagesByBodyType(final QName... bodyTypes) throws IOException {
        return this.getInboundMessagesByBodyType(true, bodyTypes);
    }

    /**
     * Retrieves all manipulation data from storage.
     *
     * <p>
     * Messages are sorted by MdibVersion on the inner join result.
     *
     * @return container with stream of all matching {@linkplain ManipulationData}s
     * @throws IOException if storage is closed
     */
    public GetterResult<ManipulationData> getManipulationData() throws IOException {
        if (this.closed.get()) {
            LOG.error(GET_MANIPULATION_DATA_BY_MANIPULATION);
            throw new IOException(GET_MANIPULATION_DATA_BY_MANIPULATION);
        }

        final CriteriaQuery<ManipulationData> criteria;
        try (final Session session = sessionFactory.openSession()) {
            session.beginTransaction();

            final CriteriaBuilder criteriaBuilder = session.getCriteriaBuilder();
            criteria = criteriaBuilder.createQuery(ManipulationData.class);
            final Root<ManipulationData> manipulationDataRoot = criteria.from(ManipulationData.class);
            criteria.select(manipulationDataRoot);
            // the answer should adhere to the order in which the manipulations have been performed,
            // even when mixing bodies
            criteria.orderBy(criteriaBuilder.asc(manipulationDataRoot.get(ManipulationData_.startTimestamp)));
        }

        final boolean present;
        try (final Stream<ManipulationData> countingStream = this.getQueryResult(criteria)) {
            present = countingStream.findAny().isPresent();
        }

        return new GetterResult<>(this.getQueryResult(criteria), present);
    }

    /**
     * Retrieves all incoming messages which match any of the provided body element QNames and that are in the given
     * time interval.
     *
     * <p>
     * Messages are sorted by MdibVersion on the inner join result.
     *
     * @param startTimestamp  of relevant time interval
     * @param finishTimestamp of relevant time interval
     * @param reportTypes     to match message against
     * @return container with stream of all matching inbound {@linkplain MessageContent}s
     * @throws IOException if storage is closed
     */
    public GetterResult<MessageContent> getInboundMessagesByTimeIntervalAndBodyType(
            final long startTimestamp, final long finishTimestamp, final QName... reportTypes) throws IOException {
        if (this.closed.get()) {
            LOG.error(GET_INBOUND_MESSAGE_BY_TIME_INTERVAL_CALLED_ON_CLOSED_STORAGE);
            throw new IOException(GET_INBOUND_MESSAGE_BY_TIME_INTERVAL_CALLED_ON_CLOSED_STORAGE);
        }

        for (final QName qname : reportTypes) {
            if (!this.checkElementSupportsMdibVersionSorting(qname)) {

                final String localErrorMessage = String.format(FILTERING_FOR_GIVEN_ELEMENT_NAME_NOT_IMPLEMENTED, qname);
                this.testRunObserver.invalidateTestRun(localErrorMessage);
                throw new UnsupportedOperationException(localErrorMessage);
            }
        }

        final CriteriaQuery<MessageContent> messageContentQuery;
        try (final Session session = sessionFactory.openSession()) {
            session.beginTransaction();

            final CriteriaBuilder criteriaBuilder = session.getCriteriaBuilder();
            messageContentQuery = criteriaBuilder.createQuery(MessageContent.class);
            final Root<MessageContent> messageContentRoot = messageContentQuery.from(MessageContent.class);
            messageContentQuery.select(messageContentRoot);

            final Subquery<MdibVersionGroupEntity> mdibVersionGroupSubQuery =
                    messageContentQuery.subquery(MdibVersionGroupEntity.class);
            final Root<MdibVersionGroupEntity> mdibVersionGroupEntityRoot =
                    mdibVersionGroupSubQuery.from(MdibVersionGroupEntity.class);
            mdibVersionGroupSubQuery.select(mdibVersionGroupEntityRoot);
            final List<Predicate> bodyElementPredicates = new ArrayList<>();

            for (final QName bodyElement : reportTypes) {
                bodyElementPredicates.add(criteriaBuilder.equal(
                        mdibVersionGroupEntityRoot.get(MdibVersionGroupEntity_.bodyElement), bodyElement.toString()));
            }

            mdibVersionGroupSubQuery.where(criteriaBuilder.and(
                    criteriaBuilder.equal(
                            mdibVersionGroupEntityRoot.get(MdibVersionGroupEntity_.messageContent),
                            messageContentRoot.get(MessageContent_.incId)),
                    criteriaBuilder.or(bodyElementPredicates.toArray(new Predicate[0]))));

            messageContentQuery.where(criteriaBuilder.and(
                    criteriaBuilder.and(
                            criteriaBuilder.ge(messageContentRoot.get(MessageContent_.nanoTimestamp), startTimestamp),
                            criteriaBuilder.le(messageContentRoot.get(MessageContent_.nanoTimestamp), finishTimestamp)),
                    criteriaBuilder.and(
                            criteriaBuilder.equal(
                                    messageContentRoot.get(MessageContent_.direction),
                                    CommunicationLog.Direction.INBOUND),
                            criteriaBuilder.exists(mdibVersionGroupSubQuery))));

            messageContentQuery.orderBy(criteriaBuilder.asc(messageContentRoot
                    .join(MessageContent_.mdibVersionGroups)
                    .get(MdibVersionGroupEntity_.mdibVersion)));
        }

        final boolean present;
        try (final Stream<MessageContent> countingStream = this.getQueryResult(messageContentQuery)) {
            present = countingStream.findAny().isPresent();
        }

        return new GetterResult<>(this.getQueryResult(messageContentQuery), present);
    }

    /**
     * Retrieves all manipulation data which match any of the provided manipulation names.
     *
     * <p>
     * Manipulations are sorted by their timestamp.
     *
     * @param manipulationNames to match manipulation data against
     * @return container with stream of all matching {@linkplain ManipulationData}s
     * @throws IOException if storage is closed
     */
    public GetterResult<ManipulationData> getManipulationDataByManipulation(final String... manipulationNames)
            throws IOException {
        if (this.closed.get()) {
            LOG.error(GET_MANIPULATION_DATA_BY_MANIPULATION);
            throw new IOException(GET_MANIPULATION_DATA_BY_MANIPULATION);
        }

        final CriteriaQuery<ManipulationData> criteria;
        try (final Session session = sessionFactory.openSession()) {
            session.beginTransaction();

            final CriteriaBuilder criteriaBuilder = session.getCriteriaBuilder();
            criteria = criteriaBuilder.createQuery(ManipulationData.class);
            final Root<ManipulationData> manipulationDataRoot = criteria.from(ManipulationData.class);
            criteria.select(manipulationDataRoot);
            final var predicates = new ArrayList<Predicate>();
            for (final var manipulationName : manipulationNames) {
                predicates.add(criteriaBuilder.equal(
                        manipulationDataRoot.get(ManipulationData_.methodName), manipulationName));
            }
            criteria.where(criteriaBuilder.and(predicates.toArray(new Predicate[0])));
            // the answer should adhere to the order in which the manipulations have been performed,
            // even when mixing bodies
            criteria.orderBy(criteriaBuilder.asc(manipulationDataRoot.get(ManipulationData_.startTimestamp)));
        }

        final boolean present;
        try (final Stream<ManipulationData> countingStream = this.getQueryResult(criteria)) {
            present = countingStream.findAny().isPresent();
        }

        return new GetterResult<>(this.getQueryResult(criteria), present);
    }

    /**
     * Retrieves all manipulation data which match the provided manipulation names and manipulation parameters.
     *
     * <p>
     * Manipulations are sorted by their timestamp.
     *
     * @param parameters       of the manipulation
     * @param manipulationName to match manipulation data against
     * @return container with stream of all matching {@linkplain ManipulationData}s
     * @throws IOException if storage is closed
     */
    public GetterResult<ManipulationData> getManipulationDataByParametersAndManipulation(
            final List<Pair<String, String>> parameters, final String manipulationName) throws IOException {
        if (this.closed.get()) {
            LOG.error(GET_MANIPULATION_DATA_BY_MANIPULATION);
            throw new IOException(GET_MANIPULATION_DATA_BY_MANIPULATION);
        }

        if (parameters.isEmpty()) {
            return getManipulationDataByManipulation(manipulationName);
        }

        final CriteriaQuery<ManipulationData> criteria;
        try (final Session session = sessionFactory.openSession()) {
            session.beginTransaction();

            final CriteriaBuilder criteriaBuilder = session.getCriteriaBuilder();
            criteria = criteriaBuilder.createQuery(ManipulationData.class);
            final Root<ManipulationData> root = criteria.from(ManipulationData.class);
            criteria.select(root);

            final var rootPredicates = new ArrayList<Predicate>();
            rootPredicates.add(criteriaBuilder.equal(root.get(ManipulationData_.methodName), manipulationName));

            final Subquery<ManipulationParameter> parameterSubQuery = criteria.subquery(ManipulationParameter.class);
            final Root<ManipulationParameter> manipulationParameterRoot =
                    parameterSubQuery.from(ManipulationParameter.class);

            final var parameterPredicates = new ArrayList<Predicate>();

            for (var parameter : parameters) {
                parameterPredicates.add(criteriaBuilder.and(
                        criteriaBuilder.equal(
                                manipulationParameterRoot.get(ManipulationParameter_.parameterName),
                                parameter.getKey()),
                        criteriaBuilder.equal(
                                manipulationParameterRoot.get(ManipulationParameter_.parameterValue),
                                parameter.getValue())));
            }
            parameterSubQuery
                    .select(manipulationParameterRoot)
                    .where(criteriaBuilder.and(
                            criteriaBuilder.equal(
                                    manipulationParameterRoot.get(ManipulationParameter_.manipulationData),
                                    root.get(ManipulationData_.incId)),
                            xor(criteriaBuilder, parameterPredicates)));
            criteria.where(criteriaBuilder.and(
                    criteriaBuilder.and(rootPredicates.toArray(new Predicate[0])),
                    criteriaBuilder.exists(parameterSubQuery)));
        }
        final boolean present;
        try (final Stream<ManipulationData> countingStream = this.getQueryResult(criteria)) {
            present = countingStream.findAny().isPresent();
        }
        return new GetterResult<>(this.getQueryResult(criteria), present);
    }

    private static Predicate xor(final CriteriaBuilder builder, final List<Predicate> predicates) {
        if (predicates.size() < 2) return builder.and(predicates.toArray(new Predicate[0]));
        return predicates.subList(1, predicates.size() - 1).stream()
                .reduce(predicates.get(0), (subquery, predicate) -> xor(builder, subquery, predicate));
    }

    private static Predicate xor(
            final CriteriaBuilder builder, final Predicate predicate1, final Predicate predicate2) {
        return builder.or(
                builder.and(predicate1, builder.not(predicate2)), builder.and(predicate2, builder.not(predicate1)));
    }

    private <T> Stream<T> getQueryResult(final CriteriaQuery<T> criteriaQuery) {
        final Session session = sessionFactory.openSession();
        final Stream<T> results = getStreamForQuery(session, criteriaQuery);

        final ResultIterator<T> resultIterator = new ResultIterator<>(session, results);

        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(resultIterator, Spliterator.ORDERED), false)
                .onClose(resultIterator::close);
    }

    // be aware, that this does not use evict on cached objects
    private <T> Stream<T> getStreamForQuery(final CriteriaQuery<T> criteriaQuery) {
        final Session session = sessionFactory.openSession();
        final Stream<T> results =
                session
                        .createQuery(criteriaQuery)
                        .setReadOnly(true)
                        .setCacheable(false)
                        .setFetchSize(FETCH_SIZE)
                        .stream();

        return results.onClose(session::close);
    }

    // be aware, that this does not use evict on cached objects
    private <T> Stream<T> getStreamForQuery(final Session session, final CriteriaQuery<T> criteriaQuery) {
        return session
                .createQuery(criteriaQuery)
                .setReadOnly(true)
                .setCacheable(false)
                .setFetchSize(FETCH_SIZE)
                .stream();
    }

    private void transmit(final List<DatabaseEntry> results) {
        try (final Session session = sessionFactory.openSession()) {
            final Transaction transaction = session.beginTransaction();

            for (int i = 0; i < results.size(); i++) {
                final var entry = results.get(i);
                if (entry instanceof Message) {
                    final MessageContent content = convertMessageToMessageContent((Message) entry);
                    session.save(content);
                } else if (entry instanceof ManipulationInfo) {
                    final ManipulationData content =
                            convertManipulationInfoToManipulationData((ManipulationInfo) entry);
                    session.save(content);
                }

                if (i % configuration.getInsertBatchSize() == 0) {
                    session.flush();
                    session.clear();
                }
            }

            transaction.commit();
        }
    }

    /**
     * Container for the query result stream and the information on whether or not the objects are present. This shall
     * always be closed after usage!
     *
     * @param <T> query result stream type
     */
    public static final class GetterResult<T> implements AutoCloseable {
        private final Stream<T> stream;
        private final boolean objectsPresent;

        private GetterResult(final Stream<T> stream, final boolean objectsPresent) {
            this.stream = stream;
            this.objectsPresent = objectsPresent;
        }

        public Stream<T> getStream() {
            return stream;
        }

        /**
         * @return true if at least one object is present, otherwise false.
         */
        public boolean areObjectsPresent() {
            return objectsPresent;
        }

        @Override
        public void close() {
            stream.close();
        }
    }

    private static class ResultIterator<T> implements Iterator<T>, AutoCloseable {
        private final Session session;
        private final Iterator<T> iterator;
        private final Stream<T> originStream;
        private T currentElement;

        ResultIterator(final Session session, final Stream<T> originStream) {
            this.session = session;
            this.iterator = originStream.iterator();
            this.originStream = originStream;
        }

        @Override
        public boolean hasNext() {
            return this.iterator.hasNext();
        }

        @Override
        public T next() {
            if (this.currentElement != null) {
                try {
                    this.session.evict(this.currentElement);
                } catch (IllegalArgumentException e) {
                    this.session.clear();
                }
            }

            this.currentElement = this.iterator.next();
            return this.currentElement;
        }

        @Override
        public void close() {
            this.currentElement = null;
            this.originStream.close();
            this.session.close();
        }
    }

    private final class DatabaseInteractionThread extends Thread {
        private final AtomicBoolean stopped;
        private final AtomicBoolean flushEvent;

        private DatabaseInteractionThread() {

            this.stopped = new AtomicBoolean();
            this.flushEvent = new AtomicBoolean();
        }

        public void setStopped() {
            if (!this.stopped.compareAndSet(false, true)) {
                LOG.error("setStopped called on already stopped thread");
            }
        }

        public void triggerFlush() {
            if (!this.flushEvent.compareAndSet(false, true)) {
                throw new RuntimeException("a previous flush has not been finished");
            }
        }

        private void drainQueue() {
            final List<DatabaseEntry> results = new ArrayList<>(blockingQueueSize);

            queueExitLock.lock();
            try {
                while (results.size() < blockingQueueSize && !this.stopped.get()) {
                    final DatabaseEntry polledElement = messageQueue.poll(100L, TimeUnit.MICROSECONDS);
                    if (polledElement != null) {
                        results.add(polledElement);
                    }

                    if (this.flushEvent.get()) {
                        queueExitLock.unlock();
                        try {
                            // will block at the barrier
                            flush(results, true, this);
                            results.clear();
                        } finally {
                            queueExitLock.lock();
                        }
                    }
                }
            } catch (final InterruptedException e) {
                LOG.error("the message content queue poll was interrupted", e);
                testRunObserver.invalidateTestRun(e);
            } finally {
                queueExitLock.unlock();
            }

            transmit(results);
        }

        public void resetFlushEvent() {
            if (!this.flushEvent.compareAndSet(true, false)) {
                final String flushEventEarlyReset = "The flush event was reset too early";
                LOG.error(flushEventEarlyReset);
                testRunObserver.invalidateTestRun(flushEventEarlyReset);
            }
        }

        public void run() {
            while (!this.stopped.get()) {
                drainQueue();
            }
        }
    }
}
