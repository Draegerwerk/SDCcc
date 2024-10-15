package org.somda.sdc

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar

interface CompileXjc {
    val jaxbClasspath: Property<FileCollection>
    val args: ListProperty<String>
    val schemaLocation: DirectoryProperty
}

const val EXTENSION_NAME = "xjc"
const val CREATE_DIRECTORY_TASK_NAME = "createXjcOutputDir"
const val MAIN_SOURCE_SET = "main"
const val XJC_MAIN_CLASS = "com.sun.tools.xjc.XJCFacade"
const val XJC_TASK_NAME = "xmlSchemaToJava"
const val CONFIG_NAME = "xjc"
const val EPISODE_FILE_NAME = "sun-jaxb.episode"

const val GENERATED_SOURCES_BASE = "generated/sources/xjc"
const val GENERATED_SOURCES_FOLDER_PATH = "$GENERATED_SOURCES_BASE/main"
const val GENERATED_SOURCES_EPISODE_PATH = "$GENERATED_SOURCES_BASE/$EPISODE_FILE_NAME"

class XjcPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create(
            EXTENSION_NAME,
            CompileXjc::class.java
        )

        // retrieve the version catalog from the toml, sadly not in a type safe manner here
        val libs = project
            .extensions
            .getByType(VersionCatalogsExtension::class.java)
            .named("libs")

        val xjcOutputDir = project.layout.buildDirectory.dir(GENERATED_SOURCES_FOLDER_PATH).get()
        val episodeOutputFile = project.layout.buildDirectory.file(GENERATED_SOURCES_EPISODE_PATH).get()

        @Suppress("ObjectLiteralToLambda") // conversion doesn't work, SAM support is messed up
        val createXjcOutputDirAction = object : Action<Task> {
            override fun execute(it: Task) {
                it.doLast {
                    project.layout.buildDirectory.dir(GENERATED_SOURCES_FOLDER_PATH).get().asFile.mkdirs()
                }
            }
        }
        val createXjcOutputDir = project.tasks.register(CREATE_DIRECTORY_TASK_NAME, createXjcOutputDirAction)

        @Suppress("ObjectLiteralToLambda") // conversion doesn't work, SAM support is messed up
        val xjcTaskAction = object : Action<JavaExec> {
            override fun execute(javaExec: JavaExec) {
                // always add the base dependencies needed for our models
                val jaxb = project.configurations.create(CONFIG_NAME)
                project.dependencies.add(CONFIG_NAME, libs.findLibrary("org.glassfish.jaxb.jaxb.core").get().get())
                project.dependencies.add(CONFIG_NAME, libs.findLibrary("org.glassfish.jaxb.jaxb.runtime").get().get())
                project.dependencies.add(CONFIG_NAME, libs.findLibrary("org.glassfish.jaxb.jaxb.xjc").get().get())
                project.dependencies.add(CONFIG_NAME, libs.findLibrary("org.jvnet.jaxb.jaxb.plugins").get().get())
                project.dependencies.add(CONFIG_NAME, libs.findLibrary("org.jvnet.jaxb.jaxb.plugins.tools").get().get())

                // append additional user-specified arguments
                val newClassPath = jaxb + extension.jaxbClasspath.getOrElse(project.objects.fileCollection())

                javaExec.dependsOn(createXjcOutputDir)
                javaExec.classpath = newClassPath
                javaExec.mainClass.set(XJC_MAIN_CLASS)

                project.logger.debug("episodeOutputFile = {}", episodeOutputFile)
                project.logger.debug("newClassPath = {}", newClassPath)
                project.logger.debug("currentJaxbClasspath = {}", extension.jaxbClasspath.orNull)
                project.logger.debug("args = {}", extension.args.orNull)
                project.logger.debug("schemaLocation = {}", extension.schemaLocation.orNull)

                // force generated javadoc to english
                javaExec.jvmArgs = listOf("-Duser.language=en-US")

                javaExec.args = mutableListOf(
                    "-d",
                    xjcOutputDir.toString(),
                    "-episode",
                    episodeOutputFile.toString(),
                    "-encoding",
                    "UTF-8",
                    "-quiet",
                    "-extension",
                    "-npa",
                    "-no-header",
                    "-Xsetters",
                    "-Xsetters-mode=accessor",
                    "-XsimpleEquals",
                    "-XsimpleHashCode",
                    "-XtoString",
                    "-Xcopyable",
                ) + extension.args.orElse(emptyList()).get() + listOf(
                    "-b",
                    extension.schemaLocation.get().toString(),
                    extension.schemaLocation.get().toString()
                )
            }

        }
        val xjcTask = project.tasks.register(XJC_TASK_NAME, JavaExec::class.java, xjcTaskAction)

        // ensure xjc as run before any java compilation
        @Suppress("ObjectLiteralToLambda") // conversion doesn't work, SAM support is messed up
        val dependsOnAction = object : Action<JavaCompile> {
            override fun execute(it: JavaCompile) {
                it.dependsOn(xjcTask)
            }

        }
        project.tasks.withType(JavaCompile::class.java, dependsOnAction)


        // claim the generated sources as ours
        val sourceSets: SourceSetContainer = project.extensions.getByType(SourceSetContainer::class.java)
        val mainSourceSet = sourceSets.findByName(MAIN_SOURCE_SET)!!

        @Suppress("ObjectLiteralToLambda") // conversion doesn't work, SAM support is messed up
        val mainSourceSetBuildByAction = object : Action<ConfigurableFileCollection> {
            override fun execute(it: ConfigurableFileCollection) {
                it.builtBy(xjcTask)
            }
        }
        @Suppress("ObjectLiteralToLambda") // conversion doesn't work, SAM support is messed up
        val mainSourceSetAction = object : Action<SourceDirectorySet> {
            override fun execute(javaSourceDir: SourceDirectorySet) {
                javaSourceDir.srcDirs(
                    project.files(xjcOutputDir, mainSourceSetBuildByAction)
                )
            }
        }

        // add episode file to jars
        @Suppress("ObjectLiteralToLambda") // conversion doesn't work, SAM support is messed up
        val jarAction = object : Action<Jar> {
            override fun execute(jar: Jar) {
                jar.metaInf {
                    from(episodeOutputFile)
                }
            }
        }
        project.tasks.withType(Jar::class.java, jarAction)

        mainSourceSet.java(mainSourceSetAction)

    }
}
