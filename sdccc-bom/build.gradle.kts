plugins {
    `java-platform`
}

repositories {
    mavenCentral()
}

val actualRevision = checkNotNull(project.findProperty("revision")) { "No revision property configured" }
val actualChangeList = checkNotNull(project.findProperty("changelist")) { "No changelist property configured" }

group = "com.draeger.medical"
version = "$actualRevision$actualChangeList"

javaPlatform {
    allowDependencies()
}

dependencies {
    api(enforcedPlatform(libs.org.junit.jupiter.bom))
    api(enforcedPlatform(libs.org.apache.logging.log4j.log4j.bom))
    constraints {
        api(libs.com.google.code.findbugs.jsr305)
        api(libs.com.google.inject.guice)
        api(libs.com.google.inject.extensions.guice.assistedinject)
        api(libs.com.google.protobuf.protobuf.java)
        api(libs.detekt.cli)
        api(libs.detekt.formatting)
        api(libs.io.github.threeten.jaxb.threeten.jaxb.core)
        api(libs.io.grpc.grpc.api)
        api(libs.io.grpc.grpc.stub)
        api(libs.jakarta.xml.bind.jakarta.xml.bind.api)
        api(libs.javax.annotation.javax.annotation.api)
        api(libs.javax.persistence.javax.persistence.api)
        api(libs.org.glassfish.jaxb.jaxb.core)
        api(libs.org.glassfish.jaxb.jaxb.runtime)
        api(libs.org.glassfish.jaxb.jaxb.xjc)
        api(libs.org.junit.jupiter.junit.jupiter.api)
        api(libs.org.junit.jupiter.junit.jupiter.engine)
        api(libs.org.jvnet.jaxb.jaxb.plugins)
        api(libs.org.jvnet.jaxb.jaxb.plugins.tools)
        api(libs.com.github.spotbugs)
        api(libs.com.draeger.medical.t2iapi)
        api(libs.com.github.spotbugs.spotbugs.annotations)
        api(libs.com.google.guava.guava)
        api(libs.com.google.code.gson.gson)
        api(libs.com.lemonappdev.konsist)
        api(libs.com.lmax.disruptor)
        api(libs.com.tngtech.archunit.archunit)
        api(libs.com.tngtech.archunit.archunit.junit5)
        api(libs.com.tngtech.archunit.archunit.junit5.api)
        api(libs.commons.cli.commons.cli)
        api(libs.jakarta.inject.jakarta.inject.api)
        api(libs.net.sf.saxon.saxon.he)
        api(libs.commons.io.commons.io)
        api(libs.org.apache.commons.commons.lang3)
        api(libs.org.apache.derby.derby)
        api(libs.org.apache.httpcomponents.httpclient)
        api(libs.org.apache.httpcomponents.httpcore)
        api(libs.org.apache.logging.log4j.log4j.api)
        api(libs.org.apache.logging.log4j.log4j.core)
        api(libs.org.apache.logging.log4j.log4j.slf4j2.impl)
        api(libs.org.apache.logging.log4j.log4j.api.kotlin)
        api(libs.org.slf4j.slf4j.api)
        api(libs.org.slf4j.jcl.over.slf4j)
        api(libs.org.bouncycastle.bcpkix.jdk15on)
        api(libs.org.bouncycastle.bcprov.jdk15on)
        api(libs.org.hibernate.hibernate.core)
        api(libs.org.jetbrains.annotations)
        api(libs.org.jetbrains.kotlin.kotlin.reflect)
        api(libs.org.jetbrains.kotlin.kotlin.stdlib)
        api(libs.org.jetbrains.kotlin.kotlin.test.junit5)
        api(libs.org.junit.jupiter.junit.jupiter.params)
        api(libs.org.junit.platform.junit.platform.launcher)
        api(libs.org.junit.platform.junit.platform.engine)
        api(libs.org.junit.platform.junit.platform.reporting)
        api(libs.org.mockito.kotlin.mockito.kotlin)
        api(libs.org.mockito.mockito.core)
        api(libs.org.opentest4j.opentest4j)
        api(libs.org.somda.sdc.common)
        api(libs.org.somda.sdc.glue)
        api(libs.org.somda.sdc.dpws)
        api(libs.org.somda.sdc.biceps)
        api(libs.org.somda.sdc.biceps.model)
        api(libs.org.somda.sdc.dpws.model)
        api(libs.org.tomlj.tomlj)
        api(libs.com.github.jk1.license.report)
    }
}
