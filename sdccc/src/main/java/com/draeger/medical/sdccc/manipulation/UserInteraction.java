/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2023 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.manipulation;

import com.draeger.medical.sdccc.configuration.TestSuiteConfig;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.name.Named;
import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import javax.imageio.ImageIO;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Provides user interactions using either a {@linkplain javax.swing.JOptionPane} or console input.
 */
public class UserInteraction {
    // console constants
    static final String YES = "y";
    static final String NO = "n";

    // swing constants
    static final String INTERVENTION_BOX_TEXT = "Intervention required";
    static final String WINDOW_OPTION_YES = "Yes";
    static final String WINDOW_OPTION_NO = "No";
    static final String[] WINDOW_OPTIONS = {WINDOW_OPTION_YES, WINDOW_OPTION_NO};

    private static final Logger LOG = LogManager.getLogger(UserInteraction.class);

    private static final String CONSOLE_INTERACTION_REQUEST_STRING = "Confirm the interaction with " + "'" + YES
            + "' if performed successfully, respond with '" + NO + "' otherwise.";

    private final boolean ciMode;
    private final boolean swingPopups;
    private final InputStream interactionInput;

    @Inject
    UserInteraction(
            @Named(TestSuiteConfig.CI_MODE) final boolean ciMode,
            @Named(TestSuiteConfig.GRAPHICAL_POPUPS) final boolean swingPopups,
            @Assisted final InputStream interactionInput) {
        this.ciMode = ciMode;
        this.swingPopups = swingPopups;
        this.interactionInput = interactionInput;
    }

    /**
     * Prompts a user interaction to perform a task, either as a graphical popup or in the console.
     *
     * @param interaction text to display
     * @return true if performed successfully, false otherwise
     */
    boolean displayYesNoUserInteraction(final String interaction) {
        final boolean result;

        LOG.debug("displayUserInteraction called for interaction: {}", interaction);
        if (ciMode) {
            LOG.warn("User interaction called while in CI Mode, cannot execute.");
            result = false;
        } else {
            if (swingPopups && !GraphicsEnvironment.isHeadless()) {
                try {
                    return displayYesNoSwingUserInteraction(interaction);
                } catch (final HeadlessException e) {
                    LOG.warn("Environment is headless, falling back to console input.");
                }
            }
            result = displayYesNoConsoleUserInteraction(interaction);
        }
        return result;
    }

    /**
     * Prompts a user interaction to provide a textual input.
     *
     * @param interaction text to display
     * @return user provided text input
     */
    String displayStringInputUserInteraction(final String interaction) {
        final String result;

        LOG.debug("displayStringInputUserInteraction called for interaction: {}", interaction);
        if (ciMode) {
            LOG.warn("User interaction called while in CI Mode, cannot execute.");
            result = "";
        } else {
            if (swingPopups && !GraphicsEnvironment.isHeadless()) {
                try {
                    return displayStringInputSwingUserInteraction(interaction);
                } catch (final HeadlessException e) {
                    LOG.warn("Environment is headless, falling back to console input.");
                }
            }
            result = displayStringInputConsoleUserInteraction(interaction);
        }
        return result;
    }

    /**
     * Display a console text prompting the user to perform an interaction and confirm it.
     *
     * @param interaction the interaction to perform.
     * @return true if successful, false otherwise.
     */
    boolean displayYesNoConsoleUserInteraction(final String interaction) {
        final boolean result;
        final String announcement = "Please perform the following action: " + interaction + ".";
        try (final Scanner scanner = new Scanner(interactionInput, StandardCharsets.UTF_8)) {
            result = displayYesNoConsoleUserInteraction(announcement, scanner);
        }
        return result;
    }

    private boolean displayYesNoConsoleUserInteraction(final String interaction, final Scanner scanner) {

        boolean result = false;
        boolean inputRead = false;

        System.out.println(interaction);
        LOG.info(interaction);

        System.out.println(CONSOLE_INTERACTION_REQUEST_STRING);
        LOG.info(CONSOLE_INTERACTION_REQUEST_STRING);

        while (scanner.hasNextLine()) {

            final String input = scanner.nextLine();

            LOG.debug("Got \"{}\" as input", input);

            if (YES.equals(input)) {
                result = true;
                inputRead = true;
                break;
            } else if (NO.equals(input)) {
                inputRead = true;
                break;
            }

            System.out.println(CONSOLE_INTERACTION_REQUEST_STRING);
            LOG.info(CONSOLE_INTERACTION_REQUEST_STRING);
        }

        if (!inputRead) {
            LOG.error("No user input could be read");
        }

        return result;
    }

    /**
     * Display a swing-based window prompting the user to perform an interaction and confirm it.
     *
     * @param interaction the interaction to perform.
     * @return true if successful, false otherwise.
     * @throws HeadlessException if the host does not support windowing.
     */
    boolean displayYesNoSwingUserInteraction(final String interaction) throws HeadlessException {
        final var popupText = String.format(
                "<html><body width='%1s'><h2>Please perform the following action</h2><p>%s</p><br>"
                        + "Confirm with yes if successful, no otherwise.",
                200, interaction);

        // create anchor frame to ensure we have an icon on the taskbar
        final JFrame frame = createJFrame();
        final int result = JOptionPane.showOptionDialog(
                frame,
                popupText,
                INTERVENTION_BOX_TEXT,
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                WINDOW_OPTIONS,
                WINDOW_OPTIONS[1]);

        frame.dispose();

        return JOptionPane.YES_OPTION == result;
    }

    String displayStringInputConsoleUserInteraction(final String interaction) {
        String result = "";
        boolean inputRead = false;

        final String announcement = "Please perform the following action: " + interaction + ".";

        try (final Scanner scanner = new Scanner(interactionInput, StandardCharsets.UTF_8)) {

            System.out.println(announcement);
            LOG.info(announcement);

            while (scanner.hasNextLine()) {

                final String input = scanner.nextLine();
                LOG.debug("Got \"{}\" as input", input);

                // Confirm input, console is tricky
                if (displayYesNoConsoleUserInteraction("Confirm the list of descriptors: " + input, scanner)) {
                    result = input;
                    inputRead = true;
                    break;
                }

                System.out.println(interaction);
                LOG.info(interaction);
            }
        }

        if (!inputRead) {
            LOG.error("No user input could be read");
        }

        return result;
    }

    String displayStringInputSwingUserInteraction(final String interaction) throws HeadlessException {
        final var popupText = String.format(
                "<html><body width='%1s'><h2>Please perform the following action</h2><p>%s</p><br>"
                        + "Enter your answer in the box below.",
                200, interaction);

        // create anchor frame to ensure we have an icon on the taskbar
        final JFrame frame = createJFrame();
        final String result = (String) JOptionPane.showInputDialog(
                frame, popupText, INTERVENTION_BOX_TEXT, JOptionPane.QUESTION_MESSAGE, null, null, null);

        frame.dispose();

        return result;
    }

    private JFrame createJFrame() {
        final JFrame frame = new JFrame("SDCcc intervention");
        frame.setUndecorated(true);
        frame.setVisible(true);
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        try {
            frame.setIconImage(ImageIO.read(getClass().getResourceAsStream("icon.png")));
        } catch (final IOException e) {
            LOG.warn("Could not set SDCcc icon for window.");
        }
        return frame;
    }
}
