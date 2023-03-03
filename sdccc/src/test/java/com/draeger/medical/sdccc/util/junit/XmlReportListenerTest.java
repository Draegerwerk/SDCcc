/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2023 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.util.junit;

import static com.draeger.medical.sdccc.util.junit.XmlReportWriterTest.UNIQUE_ID_PREFIX;
import static com.draeger.medical.sdccc.util.junit.XmlReportWriterTest.createMockedTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.draeger.medical.sdccc.util.junit.guice.XmlReportFactory;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.mockito.ArgumentCaptor;

class XmlReportListenerTest {

    private final XmlReportFactory mockFactory = mock(XmlReportFactory.class);
    private final XmlReportWriter mockWriter = mock(XmlReportWriter.class);

    @BeforeEach
    void setUp() {
        when(mockFactory.createXmlReportWriter(anyList())).thenReturn(mockWriter);
    }

    /**
     * Verifies that skipped tests are transformed into errors.
     */
    @Test
    void testSkippedIsError() {
        final var listener = new XmlReportListener(Path.of("nowhere"), "whatever", mockFactory);

        final var mockTestPlan = mock(TestPlan.class);
        final var mockParent = mock(TestDescriptor.class);
        when(mockParent.getUniqueId()).thenReturn(UniqueId.parse(UNIQUE_ID_PREFIX + "mockparent]"));

        final var identifier = TestIdentifier.from(createMockedTestDescriptor(
                UNIQUE_ID_PREFIX + "abc]",
                "abc",
                null,
                Collections.emptySet(),
                TestDescriptor.Type.TEST,
                mockParent,
                "abc"));
        final var skipReason = "Because i don't care";

        final var containerIdentifier = TestIdentifier.from(createMockedTestDescriptor(
                UNIQUE_ID_PREFIX + "efg]",
                "abc",
                null,
                Collections.emptySet(),
                TestDescriptor.Type.CONTAINER,
                null,
                "abc"));

        listener.testPlanExecutionStarted(mockTestPlan);
        listener.executionStarted(containerIdentifier);
        listener.executionSkipped(identifier, skipReason);
        listener.executionFinished(containerIdentifier, TestExecutionResult.successful());
        listener.testPlanExecutionFinished(mockTestPlan);

        final var argumentCaptor = ArgumentCaptor.forClass(List.class);
        verify(mockFactory).createXmlReportWriter(argumentCaptor.capture());

        assertEquals(1, argumentCaptor.getAllValues().size());

        final List<ReportData> capturedValue = argumentCaptor.getValue();
        assertEquals(1, capturedValue.size());

        final var receivedTest = capturedValue.get(0);
        assertEquals(
                TestExecutionResult.Status.FAILED,
                receivedTest.testExecutionResult().getStatus());
    }

    @Test
    void testResultsPassedCorrectly() {
        final var listener = new XmlReportListener(Path.of("nowhere"), "whatever", mockFactory);

        final var mockTestPlan = mock(TestPlan.class);

        final var mockParent = mock(TestDescriptor.class);
        when(mockParent.getUniqueId()).thenReturn(UniqueId.parse(UNIQUE_ID_PREFIX + "mockparent]"));

        // pass
        final var identifier1 = TestIdentifier.from(createMockedTestDescriptor(
                UNIQUE_ID_PREFIX + "abc]",
                "abc",
                null,
                Collections.emptySet(),
                TestDescriptor.Type.TEST,
                mockParent,
                "abc"));

        // failure
        final var identifier2 = TestIdentifier.from(createMockedTestDescriptor(
                UNIQUE_ID_PREFIX + "abc]",
                "abc",
                null,
                Collections.emptySet(),
                TestDescriptor.Type.TEST,
                mockParent,
                "abc"));
        final var assertionError = new AssertionError("error occurred");

        // error
        final var identifier3 = TestIdentifier.from(createMockedTestDescriptor(
                UNIQUE_ID_PREFIX + "abc]",
                "abc",
                null,
                Collections.emptySet(),
                TestDescriptor.Type.TEST,
                mockParent,
                "abc"));
        final var genericError = new Exception("broke");

        final var containerIdentifier = TestIdentifier.from(createMockedTestDescriptor(
                UNIQUE_ID_PREFIX + "efg]",
                "abc",
                null,
                Collections.emptySet(),
                TestDescriptor.Type.CONTAINER,
                null,
                "abc"));

        listener.testPlanExecutionStarted(mockTestPlan);
        listener.executionStarted(containerIdentifier);

        listener.executionStarted(identifier1);
        listener.executionFinished(identifier1, TestExecutionResult.successful());

        listener.executionStarted(identifier2);
        listener.executionFinished(identifier2, TestExecutionResult.failed(assertionError));

        listener.executionStarted(identifier3);
        listener.executionFinished(identifier3, TestExecutionResult.failed(genericError));

        listener.executionFinished(containerIdentifier, TestExecutionResult.successful());
        listener.testPlanExecutionFinished(mockTestPlan);

        final var argumentCaptor = ArgumentCaptor.forClass(List.class);
        verify(mockFactory).createXmlReportWriter(argumentCaptor.capture());

        assertEquals(1, argumentCaptor.getAllValues().size());

        final List<ReportData> capturedValue = argumentCaptor.getValue();
        assertEquals(3, capturedValue.size());

        {
            final var receivedTest = capturedValue.get(0);
            assertEquals(
                    TestExecutionResult.Status.SUCCESSFUL,
                    receivedTest.testExecutionResult().getStatus());
        }
        {
            final var receivedTest = capturedValue.get(1);
            assertEquals(
                    TestExecutionResult.Status.FAILED,
                    receivedTest.testExecutionResult().getStatus());
            assertEquals(
                    assertionError,
                    receivedTest.testExecutionResult().getThrowable().orElseThrow());
        }
        {
            final var receivedTest = capturedValue.get(2);
            assertEquals(
                    TestExecutionResult.Status.FAILED,
                    receivedTest.testExecutionResult().getStatus());
            assertEquals(
                    genericError,
                    receivedTest.testExecutionResult().getThrowable().orElseThrow());
        }
    }
}
