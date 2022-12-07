/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.tests.util;

import static org.junit.jupiter.api.Assertions.fail;

import com.draeger.medical.sdccc.messages.MessageStorage;
import com.draeger.medical.sdccc.messages.mapping.MessageContent;
import com.draeger.medical.sdccc.util.Constants;
import com.draeger.medical.sdccc.util.TestRunObserver;
import com.google.inject.Guice;
import com.google.inject.TypeLiteral;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.stream.Stream;
import javax.inject.Provider;
import javax.xml.namespace.QName;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.somda.sdc.biceps.common.CommonConfig;
import org.somda.sdc.biceps.common.MdibEntity;
import org.somda.sdc.biceps.common.MdibEntityImpl;
import org.somda.sdc.biceps.common.access.ReadTransaction;
import org.somda.sdc.biceps.common.access.ReadTransactionImpl;
import org.somda.sdc.biceps.common.access.factory.ReadTransactionFactory;
import org.somda.sdc.biceps.common.factory.MdibEntityGuiceAssistedFactory;
import org.somda.sdc.biceps.common.preprocessing.DescriptorChildRemover;
import org.somda.sdc.biceps.common.storage.DescriptionPreprocessingSegment;
import org.somda.sdc.biceps.common.storage.MdibStorage;
import org.somda.sdc.biceps.common.storage.MdibStorageImpl;
import org.somda.sdc.biceps.common.storage.MdibStoragePreprocessingChain;
import org.somda.sdc.biceps.common.storage.PreprocessingException;
import org.somda.sdc.biceps.common.storage.StatePreprocessingSegment;
import org.somda.sdc.biceps.common.storage.factory.MdibStorageFactory;
import org.somda.sdc.biceps.common.storage.factory.MdibStoragePreprocessingChainFactory;
import org.somda.sdc.biceps.consumer.access.RemoteMdibAccess;
import org.somda.sdc.biceps.consumer.access.RemoteMdibAccessImpl;
import org.somda.sdc.biceps.consumer.access.factory.RemoteMdibAccessFactory;
import org.somda.sdc.biceps.consumer.preprocessing.DuplicateContextStateHandleHandler;
import org.somda.sdc.biceps.model.message.AbstractReport;
import org.somda.sdc.biceps.model.message.GetMdibResponse;
import org.somda.sdc.biceps.model.participant.Mdib;
import org.somda.sdc.biceps.model.participant.MdibVersion;
import org.somda.sdc.biceps.provider.preprocessing.DuplicateChecker;
import org.somda.sdc.common.guice.AbstractConfigurationModule;
import org.somda.sdc.common.guice.DefaultCommonModule;
import org.somda.sdc.dpws.soap.MarshallingService;
import org.somda.sdc.dpws.soap.SoapUtil;
import org.somda.sdc.dpws.soap.exception.MarshallingException;
import org.somda.sdc.glue.common.factory.ModificationsBuilderFactory;
import org.somda.sdc.glue.consumer.report.ReportProcessingException;
import org.somda.sdc.glue.consumer.report.ReportProcessor;

/**
 * The {@linkplain MdibHistorian} provides methods to generate histories of the Mdib during a test run. It currently
 * supports episodic report based histories.
 */
public class MdibHistorian {
    public static final String NO_MDIB_ERROR = "No initial mdib present";
    private static final Logger LOG = LogManager.getLogger(MdibHistorian.class);

    private static final QName GET_MDIB_RESPONSE = Constants.MSG_GET_MDIB_RESPONSE;

    private final MessageStorage messageStorage;
    private final TestRunObserver testRunObserver;
    private final MarshallingService marshalling;
    private final SoapUtil soapUtil;
    private final RemoteMdibAccessFactory remoteMdibAccessFactory;
    private final ModificationsBuilderFactory modificationsBuilderFactory;
    private final Provider<ReportProcessor> reportProcessorProvider;

    @AssistedInject
    MdibHistorian(
            final @Assisted MessageStorage messageStorage,
            final @Assisted TestRunObserver testRunObserver,
            final MarshallingService marshalling,
            final SoapUtil soapUtil,
            final ModificationsBuilderFactory modificationsBuilderFactory,
            final Provider<ReportProcessor> reportProcessorProvider) {
        this.messageStorage = messageStorage;
        this.testRunObserver = testRunObserver;
        this.marshalling = marshalling;
        this.soapUtil = soapUtil;
        this.modificationsBuilderFactory = modificationsBuilderFactory;
        this.reportProcessorProvider = reportProcessorProvider;

        class MdibHistorianBicepsModule extends AbstractConfigurationModule {
            @Override
            protected void defaultConfigure() {
                install(new FactoryModuleBuilder()
                        .implement(MdibEntity.class, MdibEntityImpl.class)
                        .build(MdibEntityGuiceAssistedFactory.class));
                install(new FactoryModuleBuilder()
                        .implement(MdibStoragePreprocessingChain.class, MdibStoragePreprocessingChain.class)
                        .build(MdibStoragePreprocessingChainFactory.class));
                install(new FactoryModuleBuilder()
                        .implement(MdibStorage.class, MdibStorageImpl.class)
                        .build(MdibStorageFactory.class));
                install(new FactoryModuleBuilder()
                        .implement(ReadTransaction.class, ReadTransactionImpl.class)
                        .build(ReadTransactionFactory.class));
                install(new FactoryModuleBuilder()
                        .implement(RemoteMdibAccess.class, RemoteMdibAccessImpl.class)
                        .build(RemoteMdibAccessFactory.class));
            }
        }

        class MdibHistorianConfigurationModule extends AbstractConfigurationModule {
            @Override
            protected void defaultConfigure() {
                bind(org.somda.sdc.common.CommonConfig.INSTANCE_IDENTIFIER, String.class, "");
                bind(CommonConfig.STORE_NOT_ASSOCIATED_CONTEXT_STATES, Boolean.class, true);
                bind(CommonConfig.ALLOW_STATES_WITHOUT_DESCRIPTORS, Boolean.class, false);
                bind(CommonConfig.COPY_MDIB_INPUT, Boolean.class, true);
                bind(CommonConfig.COPY_MDIB_OUTPUT, Boolean.class, true);
                bind(
                        CommonConfig.CONSUMER_STATE_PREPROCESSING_SEGMENTS,
                        new TypeLiteral<List<Class<? extends StatePreprocessingSegment>>>() {},
                        List.of(DuplicateContextStateHandleHandler.class));
                bind(
                        CommonConfig.CONSUMER_DESCRIPTION_PREPROCESSING_SEGMENTS,
                        new TypeLiteral<List<Class<? extends DescriptionPreprocessingSegment>>>() {},
                        List.of(DescriptorChildRemover.class, DuplicateChecker.class));
            }
        }

        final var historianBicepsModule = new MdibHistorianBicepsModule();
        final var historianConfiguration = new MdibHistorianConfigurationModule();

        final var injector =
                Guice.createInjector(new DefaultCommonModule(), historianBicepsModule, historianConfiguration);
        this.remoteMdibAccessFactory = injector.getInstance(RemoteMdibAccessFactory.class);
    }

    /**
     * Retrieve all sequence ids used throughout the storage by looking at MDIBs and episodic reports.
     *
     * @return stream of sequence ids
     * @throws IOException on errors retrieving messages from storage
     */
    public Stream<String> getKnownSequenceIds() throws IOException {
        return messageStorage.getUniqueSequenceIds().filter(Objects::nonNull);
    }

    /**
     * Generates a storage for a sequence id using the first available GetMdibResponse for said sequence id.
     *
     * @param sequenceId of the sequence to generate new storage for
     * @return new read and write access for the Mdib based on the first GetMdibResponse.
     * @throws PreprocessingException if converting the initial mdib fails
     */
    public RemoteMdibAccess createNewStorage(final String sequenceId) throws PreprocessingException {
        final Mdib initialMdib;
        try (final var messages =
                messageStorage.getInboundMessagesByBodyTypeAndSequenceId(sequenceId, GET_MDIB_RESPONSE)) {
            initialMdib = messages.getStream()
                    .map(this::unmarshallMdib)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(NO_MDIB_ERROR + " for sequence id " + sequenceId));
        } catch (IOException e) {
            final var errorMessage = "Error while trying to retrieve initial mdib from storage";
            LOG.error("{}: {}", errorMessage, e.getMessage());
            LOG.debug("{}", errorMessage, e);
            fail(e);
            // unreachable, silence warnings
            throw new RuntimeException(e);
        }

        return convertToRemoteMdib(initialMdib);
    }

    /**
     * Generates an mdib history for a sequence id using the first available GetMdibResponse for said sequence id and
     * all related episodic reports.
     *
     * @param sequenceId of the sequence to generate history for
     * @return a new result based on episodic reports
     * @throws PreprocessingException    if converting the initial mdib fails
     * @throws ReportProcessingException if applying reports fails
     */
    public HistorianResult episodicReportBasedHistory(final String sequenceId)
            throws PreprocessingException, ReportProcessingException {

        // create new storage
        final var storage = createNewStorage(sequenceId);
        final var reportProcessor = reportProcessorProvider.get();
        reportProcessor.startApplyingReportsOnMdib(storage, null);
        final var mdibVersionPredicate =
                new InitialMdibVersionPredicate(ImpliedValueUtil.getMdibVersion(storage.getMdibVersion()));

        try {
            final var messages =
                    messageStorage.getInboundMessagesByBodyType(Constants.RELEVANT_REPORT_BODIES.toArray(new QName[0]));
            final var stream = messages.getStream()
                    .map(this::unmarshallReport)
                    .filter(report -> sequenceId.equals(report.getSequenceId()))
                    .filter(mdibVersionPredicate)
                    .map(report -> {
                        try {
                            final var cmp = ImpliedValueUtil.getMdibVersion(storage.getMdibVersion())
                                    .compareTo(ImpliedValueUtil.getReportMdibVersion(report));
                            if (cmp > 0) {
                                fail("Cannot apply report older than current storage."
                                        + " Storage " + ImpliedValueUtil.getMdibVersion(storage.getMdibVersion())
                                        + " Report " + ImpliedValueUtil.getReportMdibVersion(report)
                                        + " " + report.getClass().getSimpleName());
                            } else if (cmp == 0) {
                                LOG.debug(
                                        "Cannot apply report of equal mdib version. This means that another report with the"
                                                + " same version has already been applied, and is expected behavior when e.g."
                                                + " descriptors update, as both a report for description and state will arrive.");
                            }
                            LOG.debug(
                                    "Applying report with mdib version {}, type {}",
                                    report::getMdibVersion,
                                    () -> report.getClass().getSimpleName());
                            reportProcessor.processReport(report);
                        } catch (final Exception e) {
                            fail(e);
                        }
                        return storage;
                    });

            // initial mdib stream
            final var initialMdibStream = Stream.of(storage);

            return new HistorianResult(messages, Stream.concat(initialMdibStream, stream));
        } catch (IOException e) {
            final var errorMessage = "Error while trying to retrieve reports from storage";
            LOG.error("{}: {}", errorMessage, e.getMessage());
            LOG.debug("{}", errorMessage, e);
            testRunObserver.invalidateTestRun(errorMessage, e);
            fail(e);
            // unreachable code, silence warnings
            throw new RuntimeException(e);
        }
    }

    /**
     * Retrieves all episodic reports for a given sequence id.
     *
     * @param sequenceId of the sequence to retrieve reports for
     * @return list of the reports
     */
    public Stream<AbstractReport> getAllReports(final String sequenceId) {
        try {
            final var messages = messageStorage.getInboundMessagesByBodyTypeAndSequenceId(
                    sequenceId, Constants.RELEVANT_REPORT_BODIES.toArray(new QName[0]));

            return messages.getStream().map(this::unmarshallReport);
        } catch (IOException e) {
            final var errorMessage = "Error while trying to retrieve initial mdib from storage";
            LOG.error("{}: {}", errorMessage, e.getMessage());
            LOG.debug("{}", errorMessage, e);
            fail(e);
            // unreachable, silence warnings
            throw new RuntimeException(e);
        }
    }

    /**
     * Applies a report on a stored mdib.
     *
     * @param storage mdib to apply report on
     * @param report  to apply on the mdib
     * @return modified mdib
     * @throws PreprocessingException    is thrown in case writing to the MDIB fails.
     * @throws ReportProcessingException is thrown in case there is any error during processing of a report or accessing
     *                                   data from the queue.
     */
    public RemoteMdibAccess applyReportOnStorage(final RemoteMdibAccess storage, final AbstractReport report)
            throws PreprocessingException, ReportProcessingException {
        final var reportProcessor = reportProcessorProvider.get();
        reportProcessor.startApplyingReportsOnMdib(storage, null);

        final var cmp = ImpliedValueUtil.getMdibVersion(storage.getMdibVersion())
                .compareTo(ImpliedValueUtil.getReportMdibVersion(report));
        if (cmp > 0) {
            fail("Cannot apply report older than current storage."
                    + " Storage " + ImpliedValueUtil.getMdibVersion(storage.getMdibVersion())
                    + " Report " + ImpliedValueUtil.getReportMdibVersion(report)
                    + " " + report.getClass().getSimpleName());
        } else if (cmp == 0) {
            LOG.debug("Cannot apply report of equal mdib version. This means that another report with the"
                    + " same version has already been applied, and is expected behavior when e.g."
                    + " descriptors update, as both a report for description and state will arrive.");
        }
        LOG.debug(
                "Applying report with mdib version {}, type {}",
                ImpliedValueUtil.getReportMdibVersion(report),
                report.getClass().getSimpleName());
        reportProcessor.processReport(report);
        return storage;
    }

    private AbstractReport unmarshallReport(final MessageContent messageContent) {
        final var failMessage = "Could not unmarshall report in message " + messageContent.getMessageHash();
        try {
            final var currentMdib = marshalling.unmarshal(
                    new ByteArrayInputStream(messageContent.getBody().getBytes(StandardCharsets.UTF_8)));
            final var reportOpt = soapUtil.getBody(currentMdib, AbstractReport.class);
            if (reportOpt.isEmpty()) {
                fail(failMessage);
                // unreachable, silence warnings
                throw new RuntimeException();
            }
            return reportOpt.orElseThrow();
        } catch (final MarshallingException e) {
            LOG.error("{}. {}", failMessage, e.getMessage());
            LOG.debug("{}.", failMessage, e);
            fail(failMessage);
            // unreachable, silence warnings
            throw new RuntimeException(e);
        }
    }

    private Mdib unmarshallMdib(final MessageContent messageContent) {
        final var failMessage = "Could not unmarshall Mdib in message " + messageContent.getMessageHash();
        try {
            final var currentMdib = marshalling.unmarshal(
                    new ByteArrayInputStream(messageContent.getBody().getBytes(StandardCharsets.UTF_8)));
            final var mdibOpt = soapUtil.getBody(currentMdib, GetMdibResponse.class);
            if (mdibOpt.isEmpty()) {
                fail(failMessage);
                // unreachable, silence warnings
                throw new RuntimeException();
            }
            return mdibOpt.orElseThrow().getMdib();
        } catch (final MarshallingException e) {
            LOG.error("{} {}", failMessage, e.getMessage());
            LOG.debug("{} {}", failMessage, e);
            fail(failMessage);
            // unreachable, silence warnings
            throw new RuntimeException(e);
        }
    }

    private RemoteMdibAccess convertToRemoteMdib(final Mdib mdib) throws PreprocessingException {
        final var mdibStorage = this.remoteMdibAccessFactory.createRemoteMdibAccess();
        final var modifications =
                modificationsBuilderFactory.createModificationsBuilder(mdib).get();

        final var mdStateVersion = ImpliedValueUtil.getMdStateStateVersion(mdib.getMdState());
        final var mdDescriptionVersion = ImpliedValueUtil.getDescriptionVersion(mdib.getMdDescription());
        final MdibVersion mdibVersion = new MdibVersion(
                mdib.getSequenceId(),
                ImpliedValueUtil.getMdibMdibVersion(mdib),
                ImpliedValueUtil.getMdibInstanceId(mdib));

        mdibStorage.writeDescription(mdibVersion, mdDescriptionVersion, mdStateVersion, modifications);
        return mdibStorage;
    }

    /**
     * Result container providing a {@linkplain RemoteMdibAccess} on which every incoming report is applied in order of
     * arrival.
     */
    public static class HistorianResult implements AutoCloseable {
        // this is intentionally not implementing the iterator interface, as we're only updating the same
        // instance when iterating over the transformed stream, which is just a tremendous side-effect

        private final MessageStorage.GetterResult<MessageContent> messageContent;
        private final Iterator<RemoteMdibAccess> transformedStream;

        HistorianResult(
                final MessageStorage.GetterResult<MessageContent> messageContent,
                final Stream<RemoteMdibAccess> transformedStream) {
            this.messageContent = messageContent;
            this.transformedStream = transformedStream.iterator();
        }

        /**
         * Returns an updated {@linkplain RemoteMdibAccess} reference, applying the next available report.
         *
         * <em>Warning: Updates are happening in place, calling next, peek or similar will also update all previous
         * references, as this only returns the same instance, but updated. If you need separate instances, you need
         * separate historian result instances.</em>
         *
         * @return instance with next report applied, or null if no more elements are available
         */
        public RemoteMdibAccess next() {
            try {
                return transformedStream.next();
            } catch (NoSuchElementException e) {
                return null;
            }
        }

        @Override
        public void close() {
            this.messageContent.close();
        }
    }
}
