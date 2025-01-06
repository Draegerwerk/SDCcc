// buildSrc/src/main/kotlin/license-report.gradle.kts

plugins {
    id("com.github.jk1.dependency-license-report")
}

licenseReport {
    renderers = arrayOf(
        com.github.jk1.license.render.XmlReportRenderer(),
        com.github.jk1.license.render.SimpleHtmlReportRenderer(),
        LicenseDownloaderRenderer(),
    )
}
