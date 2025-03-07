import com.github.jk1.license.LicenseReportExtension
import com.github.jk1.license.ModuleData
import com.github.jk1.license.ProjectData
import com.github.jk1.license.render.ReportRenderer
import org.gradle.api.Project
import java.io.File
import java.net.URL

class LicenseDownloaderRenderer(
    private val outputDirName: String = "downloaded-licenses"
) : ReportRenderer {

    companion object {
        private const val CONNECT_TIMEOUT = 5000 // 5 seconds
        private const val READ_TIMEOUT = 10000   // 10 seconds
    }

    private lateinit var project: Project
    private lateinit var outputDir: File
    private val downloadedLicenses = mutableSetOf<String>()

    override fun render(data: ProjectData) {
        project = data.project
        val config = project.extensions.getByType(LicenseReportExtension::class.java)
        outputDir = File(config.outputDir, outputDirName)
        outputDir.mkdirs()

        data.allDependencies.forEach { moduleData ->
            val moduleName = "${moduleData.group}-${moduleData.name}-${moduleData.version}"
            val licenseDetails = extractPomLicenseDetails(moduleData)

            if (licenseDetails.isNotEmpty()) {
                licenseDetails.forEach { (licenseUrl, fileName) ->
                    try {
                        if (downloadedLicenses.add(licenseUrl)) {
                            downloadLicense(licenseUrl, moduleName, fileName)
                        } else {
                            project.logger.lifecycle("Skipping duplicate license download for $licenseUrl")
                        }
                    } catch (e: Exception) {
                        project.logger.warn("Failed to download license from $licenseUrl for $moduleName: ${e.message}")
                    }
                }
            } else {
                project.logger.warn("No POM license URLs found for $moduleName")
            }
        }
    }

    private fun extractPomLicenseDetails(moduleData: ModuleData): List<Pair<String, String>> {
        val licenseDetails = mutableListOf<Pair<String, String>>()

        moduleData.poms.forEach { pomData ->
            pomData.licenses.forEach { license ->
                val url = license.url?.trim()
                val fileName = license.name?.replace(Regex("""[/\\:*?"<>|]"""), "_") ?: "LICENSE"
                if (!url.isNullOrEmpty()) {
                    licenseDetails.add(url to fileName)
                }
            }
        }

        return licenseDetails
    }

    private fun downloadLicense(licenseUrl: String, moduleName: String, fileName: String) {
        val sanitizedUrl = licenseUrl.trim()
        val url = URL(sanitizedUrl)

        val connection = url.openConnection()
        connection.setRequestProperty("User-Agent", "LicenseDownloader")
        connection.connectTimeout = CONNECT_TIMEOUT
        connection.readTimeout = READ_TIMEOUT
        connection.connect()

        val contentType = connection.contentType ?: "application/octet-stream"

        val extension = when {
            contentType.contains("text/html", ignoreCase = true) -> ".html"
            contentType.contains("text/plain", ignoreCase = true) -> ".txt"
            contentType.contains("application/pdf", ignoreCase = true) -> ".pdf"
            contentType.contains("text/markdown", ignoreCase = true) -> ".md"
            else -> ".txt"
        }

        val file = File(outputDir, "$fileName$extension")

        connection.getInputStream().use { input ->
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        project.logger.lifecycle("Downloaded license for $moduleName from $licenseUrl to $file")
    }
}
