/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.util.junit.guice;

import com.draeger.medical.sdccc.util.junit.ReportData;
import com.draeger.medical.sdccc.util.junit.XmlReportListener;
import com.draeger.medical.sdccc.util.junit.XmlReportWriter;
import java.nio.file.Path;
import java.util.List;

/**
 * Factory to create instances of {@linkplain XmlReportWriter} and {@linkplain XmlReportListener}.
 */
public interface XmlReportFactory {

    /**
     * Creates a new XmlReportWriter.
     *
     * @param reportData test results to write into the xml report
     * @return writer instance
     */
    XmlReportWriter createXmlReportWriter(List<ReportData> reportData);

    /**
     * Creates a new XmlReportListener.
     *
     * @param reportsDir    directory to write report to
     * @param xmlReportName filename for the report
     * @return listener instance
     */
    XmlReportListener createXmlReportListener(Path reportsDir, String xmlReportName);
}
