plugins {
    `java-platform`
}

repositories {
    mavenCentral()
}

group = "com.draeger.medical"

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
