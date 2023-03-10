/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2023 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.manipulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.SettableFuture;
import java.awt.Component;
import java.awt.Container;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Window;
import java.awt.event.WindowEvent;
import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

/**
 * Unit tests for the user interaction handler.
 */
public class UserInteractionTest {
    private static final Logger LOG = LogManager.getLogger(UserInteractionTest.class);
    private static final int TEST_TIMEOUT_SECONDS = 15;
    private static final int TIME_TO_WAIT_FOR_DIALOGUE = 250;
    private static final Duration TEST_WAIT = Duration.ofSeconds(10);

    @BeforeEach
    void setUp() {
        Configurator.setRootLevel(Level.DEBUG);
    }

    /**
     * Tests whether enabling CI Mode disables user interactions.
     */
    @Test
    @Timeout(TEST_TIMEOUT_SECONDS)
    public void testCiModeOn() {
        final var input = mock(InputStream.class);
        final var interactionHandler = new UserInteraction(true, false, input);

        final var interactionText = "text should not appear";
        assertFalse(interactionHandler.displayYesNoUserInteraction(interactionText));
        assertEquals("", interactionHandler.displayStringInputUserInteraction(interactionText));

        verifyNoInteractions(input);
    }

    /**
     * Tests whether a positive response on the console still works after writing random input first.
     */
    @Test
    @Timeout(TEST_TIMEOUT_SECONDS)
    public void testConsoleSuccess() throws IOException {
        final String resultInput = "TestInput"
                + " ЁЂЃЄЅІЇЈЉЊЋЌЍЎЏАБВГДЕЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯабвгдежзийклмнопрстуфхцчшщъыьэюя\n"
                + UserInteraction.YES
                + "\n";

        final InputStream closeableInputStreamHandler =
                new FilterInputStream(new ByteArrayInputStream(resultInput.getBytes(StandardCharsets.UTF_8))) {
                    private boolean closed;

                    @Override
                    public int read(final byte[] b, final int off, final int len) throws IOException {
                        if (this.closed) {
                            throw new IOException("Stream has been closed");
                        }

                        return super.read(b, off, len);
                    }

                    @Override
                    public void close() {
                        System.out.println("close called");
                        this.closed = true;
                    }
                };

        final var interactionHandler = new UserInteraction(false, false, closeableInputStreamHandler);
        final String interactionText = "DO NOT INTERACT WITH THIS POPUP, IT IS FOR UNIT TESTING!";
        assertTrue(
                interactionHandler.displayYesNoConsoleUserInteraction(interactionText),
                "User interaction should have succeeded, failed instead");
        closeableInputStreamHandler.reset();
        assertFalse(
                interactionHandler.displayYesNoConsoleUserInteraction(interactionText),
                "User interaction should have succeeded, failed instead");

        final InputStream nonCloseableInputStream =
                new FilterInputStream(new ByteArrayInputStream(resultInput.getBytes(StandardCharsets.UTF_8))) {
                    @Override
                    public void close() {}
                };

        final var nonCloseableInputStreamHandler = new UserInteraction(false, false, nonCloseableInputStream);
        assertTrue(
                nonCloseableInputStreamHandler.displayYesNoConsoleUserInteraction(interactionText),
                "User interaction should have succeeded, failed instead");
        nonCloseableInputStream.reset();
        assertTrue(
                nonCloseableInputStreamHandler.displayYesNoConsoleUserInteraction(interactionText),
                "User interaction should have succeeded, failed instead");
    }

    /**
     * Tests whether a negative response on the console still works after writing random input first.
     */
    @Test
    @Timeout(TEST_TIMEOUT_SECONDS)
    public void testConsoleFailure() {
        final var resultInput = "TestInput"
                + " ЁЂЃЄЅІЇЈЉЊЋЌЍЎЏАБВГДЕЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯабвгдежзийклмнопрстуфхцчшщъыьэюя\n"
                + UserInteraction.NO
                + "\n";
        final var input = new ByteArrayInputStream(resultInput.getBytes(StandardCharsets.UTF_8));

        final var interactionHandler = new UserInteraction(false, false, input);
        final var interactionText = "DO NOT INTERACT WITH THIS POPUP, IT IS FOR UNIT TESTING!";
        final var result = interactionHandler.displayYesNoUserInteraction(interactionText);
        assertFalse(result, "User interaction should have failed, succeeded instead");
    }

    /**
     * Tests whether the console input handler is blocking the caller from continuing.
     *
     * @throws Exception on any exception
     */
    @Test
    @Timeout(TEST_TIMEOUT_SECONDS)
    public void testConsoleBlocking() throws Exception {
        final var waitTime = Duration.ofSeconds(2);
        final var initialInput =
                "TestInput ЁЂЃЄЅІЇЈЉЊЋЌЍЎЏАБВГДЕЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯабвгдежзийклмнопрстуфхцчшщъыьэюя\n";
        final var finalInput = "y\n";
        final var input = mock(InputStream.class);
        final var answer = new WaitingAnswer(initialInput, finalInput, waitTime);
        when(input.read(any(byte[].class), anyInt(), anyInt())).thenAnswer(answer);

        final var interactionHandler = new UserInteraction(false, false, input);
        final var interactionText = "DO NOT INTERACT WITH THIS POPUP, IT IS FOR UNIT TESTING!";
        final ExecutorService es = Executors.newSingleThreadExecutor();
        final var resThread = es.submit(() -> {
            interactionHandler.displayYesNoUserInteraction(interactionText);
        });

        // wait half the time and check its not done
        Thread.sleep(waitTime.toMillis() / 2);
        assertFalse(resThread.isDone(), "Thread should not have finished");

        // wait the full amount and check its really done
        Thread.sleep(waitTime.toMillis());
        assertTrue(resThread.isDone(), "Thread should have finished");
    }

    /**
     * Tests whether graphical swing popups are returning the correct result for clicking
     * yes, no and closing the window.
     *
     * @throws Exception on any exception
     */
    @Test
    @Timeout(TEST_TIMEOUT_SECONDS)
    public void testYesNoSwingPopupSelections() throws Exception {
        final var interactionHandler = new UserInteraction(false, true, InputStream.nullInputStream());
        final var interactionText = "DO NOT INTERACT WITH THIS POPUP, IT IS FOR UNIT TESTING!";

        {
            // yes option
            final SettableFuture<Boolean> result = SettableFuture.create();
            EventQueue.invokeLater(() -> {
                final var interactionResult = interactionHandler.displayYesNoSwingUserInteraction(interactionText);
                result.set(interactionResult);
            });

            LOG.info("Looking for window {}", UserInteraction.INTERVENTION_BOX_TEXT);
            final var frame = waitForDialog(UserInteraction.INTERVENTION_BOX_TEXT);
            LOG.info("Found window {}", frame);
            final JButton btn = getButton(frame, UserInteraction.WINDOW_OPTION_YES);
            assertNotNull(btn, "Could not find the button on the popup.");
            SwingUtilities.invokeAndWait(btn::doClick);

            assertTrue(result.get(TEST_WAIT.toSeconds(), TimeUnit.SECONDS));
        }
        {
            // no option
            final SettableFuture<Boolean> result = SettableFuture.create();
            EventQueue.invokeLater(() -> {
                final var interactionResult = interactionHandler.displayYesNoSwingUserInteraction(interactionText);
                result.set(interactionResult);
            });

            LOG.info("Looking for window {}", UserInteraction.INTERVENTION_BOX_TEXT);
            final var frame = waitForDialog(UserInteraction.INTERVENTION_BOX_TEXT);
            LOG.info("Found window {}", frame);
            final JButton btn = getButton(frame, UserInteraction.WINDOW_OPTION_NO);
            assertNotNull(btn, "Could not find the button on the popup.");
            SwingUtilities.invokeAndWait(btn::doClick);

            assertFalse(result.get(TEST_WAIT.toSeconds(), TimeUnit.SECONDS));
        }
        {
            // window closed
            final SettableFuture<Boolean> result = SettableFuture.create();
            EventQueue.invokeLater(() -> {
                final var interactionResult = interactionHandler.displayYesNoSwingUserInteraction(interactionText);
                result.set(interactionResult);
            });

            LOG.info("Looking for window {}", UserInteraction.INTERVENTION_BOX_TEXT);
            final var frame = waitForDialog(UserInteraction.INTERVENTION_BOX_TEXT);
            LOG.info("Found window {}", frame);
            frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING));

            assertFalse(result.get(TEST_WAIT.toSeconds(), TimeUnit.SECONDS));
        }
    }

    /**
     * Tests whether complex text is displayed on the swing popup.
     *
     * @throws Exception on any exception
     */
    @Test
    @Timeout(TEST_TIMEOUT_SECONDS)
    public void testSwingPopupText() throws Exception {
        final var interactionHandler = new UserInteraction(false, true, InputStream.nullInputStream());
        final var interactionText = "DO NOT INTERACT WITH THIS POPUP, IT IS FOR UNIT TESTING!"
                + "The following string is strange:"
                + " ЁЂЃЄЅІЇЈЉЊЋЌЍЎЏАБВГДЕЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯабвгдежзийклмнопрстуфхцчшщъыьэюя";

        final SettableFuture<Boolean> result = SettableFuture.create();
        EventQueue.invokeLater(() -> {
            final var interactionResult = interactionHandler.displayYesNoSwingUserInteraction(interactionText);
            result.set(interactionResult);
        });

        LOG.info("Looking for window {}", UserInteraction.WINDOW_OPTION_NO);
        final var frame = waitForDialog(UserInteraction.INTERVENTION_BOX_TEXT);
        LOG.info("Found window {}", frame);

        final JOptionPane label = findJOptionPane(frame);
        assertNotNull(label, "Could not find JOptionPane in frame");
        assertNotNull(label.getMessage());
        assertTrue(((String) label.getMessage()).contains(interactionText));

        final JButton btn = getButton(frame, "No");
        assertNotNull(btn, "Could not find the button on the popup.");
        SwingUtilities.invokeLater(btn::doClick);

        assertFalse(result.get(TEST_WAIT.toSeconds(), TimeUnit.SECONDS));
    }

    /**
     * Tests whether graphical swing popups are returning the correct input for the text box.
     *
     * @throws Exception on any exception
     */
    @Test
    @Timeout(TEST_TIMEOUT_SECONDS)
    public void testSwingStringInput() throws Exception {
        final var interactionHandler = new UserInteraction(false, true, InputStream.nullInputStream());
        final var interactionText = "DO NOT INTERACT WITH THIS POPUP, IT IS FOR UNIT TESTING!";

        final var expectedText = "⒯⒣⒠ ⒬⒰⒤⒞⒦ ⒝⒭⒪⒲⒩ ⒡⒪⒳ ⒥⒰⒨⒫⒮ ⒪⒱⒠⒭ ⒯⒣⒠ ⒧⒜⒵⒴ ⒟⒪⒢";

        {
            final SettableFuture<String> result = SettableFuture.create();
            EventQueue.invokeLater(() -> {
                final var interactionResult =
                        interactionHandler.displayStringInputSwingUserInteraction(interactionText);
                result.set(interactionResult);
            });

            LOG.info("Looking for window {}", UserInteraction.INTERVENTION_BOX_TEXT);
            final var frame = waitForDialog(UserInteraction.INTERVENTION_BOX_TEXT);
            LOG.info("Found window {}", frame);

            final var textField = findMatchingElement(frame, JTextField.class, field -> "".equals(field.getText()));
            textField.setText(expectedText);

            final JButton btn = getButton(frame, "OK");
            assertNotNull(btn, "Could not find the button on the popup.");
            SwingUtilities.invokeAndWait(btn::doClick);

            assertEquals(expectedText, result.get(TEST_WAIT.toSeconds(), TimeUnit.SECONDS));
        }
    }

    /**
     * Looks through the opened windows for a window matching the provided title.
     *
     * <p>
     * Looks through windows forever until a matching one has been found, test case needs
     * to handle timeouts.
     *
     * @param title window title to look for
     * @return the window with matching title
     */
    public static JDialog waitForDialog(final String title) {
        JDialog win = null;
        do {
            for (final Window window : Frame.getWindows()) {
                if (window instanceof JDialog) {
                    final JDialog dialog = (JDialog) window;
                    LOG.info("Found dialog with title {}", dialog.getTitle());
                    // only check for valid windows, invalid (e.g. hidden) windows aren't the one we want
                    if (title.equals(dialog.getTitle()) && dialog.isValid()) {
                        win = dialog;
                        break;
                    }
                }
            }

            if (win == null) {
                try {
                    Thread.sleep(TIME_TO_WAIT_FOR_DIALOGUE);
                } catch (final InterruptedException ex) {
                    break;
                }
            }
        } while (win == null);
        return win;
    }

    /**
     * Returns the {@linkplain JOptionPane} child of a swing gui {@linkplain Container}.
     *
     * @param container to look through
     * @return matching child or null if no matching child was found
     */
    public static JOptionPane findJOptionPane(final Container container) {

        return findMatchingElement(container, JOptionPane.class, element -> true);
    }

    /**
     * Returns the {@linkplain JButton} child of a swing gui {@linkplain Container} matching the
     * given text.
     *
     * @param container to look through
     * @param text      text to look on button for
     * @return matching child or null if no matching child was found
     */
    public static JButton getButton(final Container container, final String text) {
        return findMatchingElement(container, JButton.class, element -> text.equals(element.getText()));
    }

    /**
     * Returns the element matching the provided class and validator method.
     *
     * @param container   to look through
     * @param elementType to look for
     * @param validator   to check against
     * @param <T>         the element type
     * @return matching child or null of no matching child was found
     */
    private static <T> T findMatchingElement(
            final Container container, final Class<T> elementType, final Function<T, Boolean> validator) {
        T result = null;

        final List<Container> children = new ArrayList<>();
        for (final Component child : container.getComponents()) {
            LOG.info("Found child {}", child);
            if (elementType.isInstance(child)) {
                final var castChild = elementType.cast(child);
                if (validator.apply(castChild)) {
                    result = castChild;
                    break;
                }
            } else if (child instanceof Container) {
                children.add((Container) child);
            }
        }

        if (result == null) {
            for (final Container cont : children) {
                final var pane = findMatchingElement(cont, elementType, validator);
                if (pane != null) {
                    result = pane;
                    break;
                }
            }
        }

        return result;
    }

    private static class WaitingAnswer implements Answer<Integer> {

        private final byte[] initialData;
        private final byte[] finalData;
        private final Duration waitInBetween;
        private boolean initialDone;
        private boolean finalDone;
        private boolean waitDone;

        WaitingAnswer(final String initialData, final String finalData, final Duration waitInBetween) {
            this.initialData = initialData.getBytes(StandardCharsets.UTF_8);
            this.finalData = finalData.getBytes(StandardCharsets.UTF_8);
            this.waitInBetween = waitInBetween;
            this.initialDone = false;
            this.finalDone = false;
            this.waitDone = false;
        }

        @Override
        public Integer answer(final InvocationOnMock invocation) throws Throwable {
            final Object[] args = invocation.getArguments();
            final byte[] byteBuffer = (byte[]) args[0];

            if (initialDone && !waitDone) {
                Thread.sleep(waitInBetween.toMillis());
                waitDone = true;
            }
            var length = -1;
            if (!initialDone) {
                System.arraycopy(initialData, 0, byteBuffer, 0, initialData.length);
                length = initialData.length;
                initialDone = true;
            } else if (!finalDone) {
                System.arraycopy(finalData, 0, byteBuffer, 0, finalData.length);
                length = finalData.length;
                finalDone = true;
            }
            return length;
        }
    }
}
