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

// container to lazily call asPath upon evaluation, not during configuration, which can be too early
data class LazyToStringFileCollection(private val fileCollection: FileCollection) {
    override fun toString(): String = fileCollection.asPath
}

val detektTask = tasks.register<JavaExec>("detekt") {
    dependsOn("testClasses")
    mainClass.set("io.gitlab.arturbosch.detekt.cli.Main")
    classpath = detekt

    val input = projectDir
    val config = detektConfigPath
    val exclude = ".*/build/.*,.*/resources/.*,**/build.gradle.kts,**/settings.gradle.kts"
    val classpathNeededForDetekt = LazyToStringFileCollection(
        project.sourceSets.main.get().runtimeClasspath +
            project.sourceSets.test.get().runtimeClasspath +
            project.sourceSets.main.get().compileClasspath +
            project.sourceSets.test.get().compileClasspath
    )

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
