import org.gradle.api.tasks.JavaExec
import org.gradle.kotlin.dsl.creating
import org.gradle.kotlin.dsl.getValue
import org.gradle.kotlin.dsl.register

plugins {
    id("com.draeger.medical.java-conventions")
    id("org.jetbrains.kotlin.jvm")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "17"
    }
}

val detekt by configurations.creating

val detektTask = tasks.register<JavaExec>("detekt") {
    mainClass.set("io.gitlab.arturbosch.detekt.cli.Main")
    classpath = detekt

    val input = projectDir
    val config = "$projectDir/../dev_config/detekt.yml"
    val exclude = ".*/build/.*,.*/resources/.*,**/build.gradle.kts,**/settings.gradle.kts"
    val classpathNeededForDetekt = files(
        sourceSets.main.get().runtimeClasspath,
        sourceSets.test.get().runtimeClasspath
    )
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

tasks.check {
    dependsOn(detektTask)
}

dependencies {
    detekt(libs.detekt.cli)
    api(libs.org.jetbrains.kotlin.kotlin.stdlib)
}