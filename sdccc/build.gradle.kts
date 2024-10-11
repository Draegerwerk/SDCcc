

plugins {
    id("com.draeger.medical.java-conventions")
    kotlin("jvm") version "1.9.0"
    id("com.github.spotbugs") version "5.0.14"
    checkstyle
    id("com.diffplug.spotless") version "6.21.0"
    id("de.undercouch.download") version "5.3.0"
    id("edu.sc.seis.launch4j") version "2.5.0"
    id("com.example.license-report")
}

tasks.named("build") {
    dependsOn("generateLicenseReport")
}

val detekt by configurations.creating

val detektTask = tasks.register<JavaExec>("detekt") {
    mainClass.set("io.gitlab.arturbosch.detekt.cli.Main")
    classpath = detekt

    val input = projectDir
    val config = "$projectDir/../dev_config/detekt.yml"
    val exclude = ".*/build/.*,.*/resources/.*,**/build.gradle.kts,**/settings.gradle.kts"
    val classpathNeededForDetekt = sourceSets.main.get().runtimeClasspath.asPath
    val jdkHome = System.getProperty("java.home")
    args(
        "--input", input.absolutePath,
        "--config", config,
        "--excludes", exclude,
        "--report", "html:${layout.buildDirectory.get().asFile}/reports/detekt/detekt.html",
        "--classpath", classpathNeededForDetekt,
        "--jdk-home", jdkHome,
        "--jvm-target", "17",
        "--build-upon-default-config"
    )
}

val log4jVersion = "2.23.1"

dependencies {
    detekt("io.gitlab.arturbosch.detekt:detekt-cli:1.23.3")
    api(libs.org.junit.jupiter.junit.jupiter.api)
    api(libs.org.junit.jupiter.junit.jupiter.engine)
    api(libs.org.junit.platform.junit.platform.launcher)
    api(libs.org.junit.platform.junit.platform.reporting)
    api(libs.org.somda.sdc.glue)
    api(libs.org.somda.sdc.common)
    api(libs.commons.cli.commons.cli)
    api(libs.com.google.inject.guice)
    api(libs.org.tomlj.tomlj)
    api("org.apache.logging.log4j:log4j-api:$log4jVersion")
    api("org.apache.logging.log4j:log4j-core:$log4jVersion")
    api("org.apache.logging.log4j:log4j-slf4j-impl:$log4jVersion")
    api(libs.com.github.spotbugs.spotbugs.annotations)
    api(libs.net.sf.saxon.saxon.he)
    api(libs.org.apache.derby.derby)
    api(libs.org.hibernate.hibernate.core)
    api(libs.com.draeger.medical.t2iapi)
    api(libs.jakarta.xml.bind.jakarta.xml.bind.api)
    api(libs.org.glassfish.jaxb.jaxb.core)
    api(libs.org.glassfish.jaxb.jaxb.runtime)
    api(libs.org.bouncycastle.bcprov.jdk15on)
    api(libs.org.bouncycastle.bcpkix.jdk15on)
    api(libs.com.lmax.disruptor)
    api(libs.jakarta.inject.jakarta.inject.api)
    api(libs.org.jetbrains.kotlin.kotlin.stdlib)
    api(libs.org.jetbrains.kotlin.kotlin.reflect)
    api(libs.com.lemonappdev.konsist)
    api(libs.com.google.code.gson.gson)
    testImplementation(libs.org.mockito.mockito.core)
    testImplementation(libs.org.mockito.kotlin.mockito.kotlin)
    testImplementation(project(":biceps-model"))
    testImplementation(project(":dpws-model"))
    testImplementation(libs.com.tngtech.archunit.archunit.junit5)
    testImplementation(libs.org.junit.jupiter.junit.jupiter.params)
    testImplementation(libs.org.jetbrains.kotlin.kotlin.test.junit5)
}

description = "sdccc"

val testsJar by tasks.registering(Jar::class) {
    archiveClassifier.set("tests")
    from(sourceSets["test"].output)
}

(publishing.publications["maven"] as MavenPublication).artifact(testsJar)

tasks.withType<JavaCompile> {
    options.release.set(17)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "17"
    }
}

tasks.check {
    dependsOn(detektTask)
    dependsOn("spotbugsMain")
    dependsOn("spotbugsTest")
    dependsOn("checkstyleMain", "checkstyleTest")
    dependsOn("downloadAndUnpackJre")
}

tasks.withType<com.github.spotbugs.snom.SpotBugsTask> {
    excludeFilter.set(file("$projectDir/../dev_config/filter.xml"))
    reports.create("xml") { required = true }
}

checkstyle {
    configFile = file("../checkstyle/checkstyle.xml")
}

spotless {
    java {
        palantirJavaFormat()
        target("src/**/*.java")
    }
}

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

val projectName = "SDCcc-gradle"

launch4j {
    headerType = "console"
    jar = "${layout.buildDirectory.get().asFile}/libs/${projectName}-${project.version}.jar"
    outfile = "${projectName}-${project.version}.exe" // Absolute path not allowed. File gets placed in build/launch4j
    mainClassName = "com.draeger.medical.sdccc.TestSuite"
    classpath = mutableSetOf("lib/**")
    jreMinVersion = "17"
    bundledJrePath = "\$\${jreFullPath}"

    version = "${project.version}.0"
    textVersion = "${project.version}"
    fileDescription = "${project.name}"
    copyright = "2023-2024 Draegerwerk AG & Co. KGaA"

    productName = "${project.name}"
    companyName = "Draegerwerk AG & Co. KGaA"
    internalName = "sdccc"
}

tasks.named("launch4j") {
    dependsOn("copyRuntimeLibs", "downloadAndUnpackJre")
}

tasks.named("build") {
    dependsOn("launch4j")
}

tasks.test {
    useJUnitPlatform()
    exclude("it/com/draeger/medical/sdccc/testsuite_it_mock_tests/**")
    maxHeapSize = "3g"
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
}
