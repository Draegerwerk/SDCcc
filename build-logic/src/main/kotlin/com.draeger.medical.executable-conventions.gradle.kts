import org.gradle.api.tasks.Copy
import org.gradle.kotlin.dsl.register


plugins {
    id("com.draeger.medical.java-conventions")
    id("com.draeger.medical.kotlin-conventions")
    id("de.undercouch.download")
    id("edu.sc.seis.launch4j")
}

val javaVersion = property("javaVersion").toString()
val jreDirectoryName = "jdk-17.0.5+8-jre"
val jreBasePath = "jre"
val jreFullPath = "${jreBasePath}/${jreDirectoryName}"
val jreDownloadUrlPrefix = "https://github.com/adoptium/temurin17-binaries/releases/download/"
val jreDownloadFileName = "OpenJDK17U-jre_x64_windows_hotspot_17.0.5_8.zip"
val jreDownloadUrlSuffix = "jdk-17.0.5%2B8/${jreDownloadFileName}"
val jreDownloadUrl = "${jreDownloadUrlPrefix}${jreDownloadUrlSuffix}"

tasks.register<de.undercouch.gradle.tasks.download.Download>("downloadJre") {
    src(jreDownloadUrl)
    dest(file("${layout.buildDirectory.get().asFile}/${jreBasePath}/${jreDownloadFileName}"))
    overwrite(false)
    onlyIfModified(true)
}

tasks.register<Copy>("unpackJre") {
    doFirst {
        file("${layout.buildDirectory.get().asFile}/${jreBasePath}").walk().forEach { file ->
            if (!file.setWritable(true)) {
                println("Failed to set writable permission for ${file.absolutePath}")
            }
        }
    }
    dependsOn("downloadJre")
    from(zipTree(file("${layout.buildDirectory.get().asFile}/${jreBasePath}/${jreDownloadFileName}")))
    into("${layout.buildDirectory.get().asFile}/${jreBasePath}")
}

tasks.register("downloadAndUnpackJre") {
    dependsOn("unpackJre")
}

tasks.register<Copy>("copyRuntimeLibs") {
    from(configurations.runtimeClasspath) {
        exclude { it.isDirectory }
    }
    into("${layout.buildDirectory.get().asFile}/lib")
}

tasks.createExe {
    headerType = "console"
    jar = "${layout.buildDirectory.get().asFile}/libs/${project.name}-${project.version}.jar"
    outfile = "${project.name}-${project.version}.exe" // Absolute path not allowed. File gets placed in build/launch4j
    mainClassName = findProperty("mainClass")?.toString() ?: "com.draeger.medical.sdccc.TestSuite"
    classpath = setOf("lib/**")
    jreMinVersion = javaVersion
    bundledJrePath = "./${jreFullPath}"

    version = "${project.version}.0"
    textVersion = "${project.version}"
    fileDescription = "${project.name}"
    copyright = "2023-2024 Draegerwerk AG & Co. KGaA"

    productName = "${project.name}"
    companyName = "Draegerwerk AG & Co. KGaA"
    internalName = "sdccc"
}

tasks.named("createExe") {
    dependsOn("copyRuntimeLibs", "downloadAndUnpackJre")
}

tasks.named("build") {
    dependsOn("createExe")
}

