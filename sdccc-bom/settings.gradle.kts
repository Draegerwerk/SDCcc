rootProject.name = "sdccc-bom"

// make libs.versions.toml available
// see https://stackoverflow.com/a/70878181
dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}