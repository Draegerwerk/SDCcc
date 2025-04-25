import org.apache.tools.ant.filters.ReplaceTokens

plugins {
    id("com.draeger.medical.version-conventions")
    id("com.draeger.medical.java-conventions")
    id("com.draeger.medical.java-analysis")
    id("com.draeger.medical.kotlin-conventions")
}

val javaVersion = property("javaVersion").toString()

dependencies {
    api(enforcedPlatform(libs.com.draeger.medical.sdccc.bom))
    detekt(libs.detekt.cli)
    detekt(libs.detekt.formatting)
    api(libs.org.jetbrains.kotlin.kotlin.stdlib)
    api(libs.org.junit.jupiter.junit.jupiter.api)
    api(libs.org.junit.jupiter.junit.jupiter.engine)
    api(libs.org.junit.platform.junit.platform.launcher)
    api(libs.org.junit.platform.junit.platform.engine)
    api(libs.org.junit.platform.junit.platform.reporting)
    api(libs.org.somda.sdc.glue)
    api(libs.org.somda.sdc.common)
    api(libs.org.somda.sdc.dpws)
    api(libs.org.somda.sdc.biceps)
    api(libs.org.somda.sdc.biceps.model)
    api(libs.org.somda.sdc.dpws.model)
    api(libs.commons.cli.commons.cli)

    api(libs.com.google.code.findbugs.jsr305)
    api(libs.com.google.inject.guice)
    api(libs.com.google.inject.extensions.guice.assistedinject)
    api(libs.com.google.protobuf.protobuf.java)
    api(libs.io.grpc.grpc.api)
    api(libs.org.tomlj.tomlj)

    api(libs.org.apache.logging.log4j.log4j.api)
    api(libs.org.apache.logging.log4j.log4j.core)
    api(libs.org.apache.logging.log4j.log4j.slf4j.impl)
    api(libs.org.apache.logging.log4j.log4j.api.kotlin)

    api(libs.com.github.spotbugs.spotbugs.annotations)
    api(libs.net.sf.saxon.saxon.he)
    api(libs.commons.io.commons.io)
    api(libs.org.apache.commons.commons.lang3)
    api(libs.org.apache.derby.derby)
    api(libs.org.apache.httpcomponents.httpclient)
    api(libs.org.apache.httpcomponents.httpcore)
    api(libs.org.hibernate.hibernate.core)
    api(libs.com.draeger.medical.t2iapi)
    api(libs.jakarta.xml.bind.jakarta.xml.bind.api)
    api(libs.javax.annotation.javax.annotation.api)
    api(libs.javax.persistence.javax.persistence.api)
    api(libs.org.glassfish.jaxb.jaxb.core)
    api(libs.org.glassfish.jaxb.jaxb.runtime)
    api(libs.org.bouncycastle.bcprov.jdk15on)
    api(libs.org.bouncycastle.bcpkix.jdk15on)
    api(libs.com.lmax.disruptor)
    api(libs.jakarta.inject.jakarta.inject.api)
    api(libs.org.jetbrains.kotlin.kotlin.reflect)
    api(libs.com.lemonappdev.konsist)
    api(libs.com.google.guava.guava)
    api(libs.com.google.code.gson.gson)
    testImplementation(libs.org.mockito.mockito.core)
    testImplementation(libs.org.mockito.kotlin.mockito.kotlin)
    testImplementation(projects.bicepsModel)
    testImplementation(projects.dpwsModel)
    testImplementation(libs.com.tngtech.archunit.archunit.junit5)
    testImplementation(libs.org.junit.jupiter.junit.jupiter.params)
    testImplementation(libs.org.jetbrains.kotlin.kotlin.test.junit5)

    annotationProcessor(libs.org.apache.logging.log4j.log4j.core)
}

description = "sdccc"

val createExecutable: Boolean = project.hasProperty("executableSDCcc")

if (createExecutable) {
    apply(plugin = "com.example.license-report")
    tasks.named("build") {
        dependsOn("generateLicenseReport")
    }
    apply(plugin = "com.draeger.medical.executable-conventions")
}

tasks.check {
    dependsOn("detekt")
}

tasks.test {
    useJUnitPlatform()
    exclude("it/com/draeger/medical/sdccc/testsuite_it_mock_tests/**")
    testLogging.showStandardStreams = true
    maxHeapSize = "3g"
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
}

val testsJar by tasks.registering(Jar::class) {
    archiveClassifier.set("tests")
    from(sourceSets["test"].output)
}

artifacts {
    add("archives", tasks.named("testsJar"))
}

tasks.processResources {
    filter<ReplaceTokens>("tokens" to mapOf(
        "revision" to (project.findProperty("revision")?.toString() ?: ""),
        "changelist" to (project.findProperty("changelist")?.toString() ?: "")
    ))
}

java {
    registerFeature("tests") {
        usingSourceSet(sourceSets.test.get())
    }
}
