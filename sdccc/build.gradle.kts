plugins {
    id("com.draeger.medical.java-conventions")
    id("com.draeger.medical.kotlin-conventions")
    id("com.draeger.medical.executable-conventions")
    id("com.draeger.medical.java-analysis")
    id("com.example.license-report")
}

val javaVersion = property("javaVersion").toString()

tasks.named("build") {
    dependsOn("generateLicenseReport")
}

dependencies {
    api(libs.org.junit.jupiter.junit.jupiter.api)
    api(libs.org.junit.jupiter.junit.jupiter.engine)
    api(libs.org.junit.platform.junit.platform.launcher)
    api(libs.org.junit.platform.junit.platform.reporting)
    api(libs.org.somda.sdc.glue)
    api(libs.org.somda.sdc.common)
    api(libs.commons.cli.commons.cli)

    api(libs.com.google.inject.guice)
    api(libs.com.google.inject.extensions.guice.assistedinject)

    api(libs.org.tomlj.tomlj)

    api(libs.org.apache.logging.log4j.log4j.api)
    api(libs.org.apache.logging.log4j.log4j.core)
    api(libs.org.apache.logging.log4j.log4j.slf4j.impl)

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
    api(libs.org.jetbrains.kotlin.kotlin.reflect)
    api(libs.com.lemonappdev.konsist)
    api(libs.com.google.code.gson.gson)
    testImplementation(libs.org.mockito.mockito.core)
    testImplementation(libs.org.mockito.kotlin.mockito.kotlin)
    testImplementation(projects.bicepsModel)
    testImplementation(projects.dpwsModel)
    testImplementation(libs.com.tngtech.archunit.archunit.junit5)
    testImplementation(libs.org.junit.jupiter.junit.jupiter.params)
    testImplementation(libs.org.jetbrains.kotlin.kotlin.test.junit5)
}

description = "sdccc"

tasks.test {
    useJUnitPlatform()
    exclude("it/com/draeger/medical/sdccc/testsuite_it_mock_tests/**")
    maxHeapSize = "3g"
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
}
