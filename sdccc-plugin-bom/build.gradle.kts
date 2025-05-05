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

dependencies {
    constraints {
        api(libs.com.github.jk1.license.report)
        api(libs.gradleplugins.spotbugs)
        api(libs.gradleplugins.spotless)
        api(libs.gradleplugins.kotlin.jvm)
        api(libs.gradleplugins.download)
        api(libs.gradleplugins.launch4j)
    }
}
