import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.the


// workaround for making libs available in build-logic
val Project.libs
    get() = the<org.gradle.accessors.dm.LibrariesForLibs>()
