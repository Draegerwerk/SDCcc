import org.gradle.api.tasks.JavaExec
import org.gradle.kotlin.dsl.creating
import org.gradle.kotlin.dsl.getValue
import org.gradle.kotlin.dsl.register

plugins {
    id("com.draeger.medical.java-conventions")
    id("org.jetbrains.kotlin.jvm")
}

val javaVersion = property("javaVersion").toString()

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = javaVersion
    }
}

val detekt by configurations.creating

val detektConfigPath = projectDir.path + File.separator +
    (project.findProperty("detektConfigFilePath")?.toString() ?: "dev_config/detekt.yml")

val detektTask = tasks.register<JavaExec>("detekt") {
    dependsOn("assemble")
    mainClass.set("io.gitlab.arturbosch.detekt.cli.Main")
    classpath = detekt

    val input = projectDir
    val config = detektConfigPath
    val exclude = ".*/build/.*,.*/resources/.*,**/build.gradle.kts,**/settings.gradle.kts"
    val classpathNeededForDetekt = files(
        sourceSets.main.get().runtimeClasspath,
        sourceSets.test.get().runtimeClasspath
    ).asPath

    val jdkHome = System.getProperty("java.home")
    args(
        "--input", input.absolutePath,
        "--config", config,
        "--excludes", exclude,
        "--report", "html:${layout.buildDirectory.get().asFile}/reports/detekt/detekt.html",
        "--classpath", classpathNeededForDetekt,
        "--jdk-home", jdkHome,
        "--jvm-target", javaVersion,
        "--build-upon-default-config"
    )
}

dependencies {
    detekt(libs.detekt.cli)
    detekt(libs.detekt.formatting)
    api(libs.org.jetbrains.kotlin.kotlin.stdlib)
}