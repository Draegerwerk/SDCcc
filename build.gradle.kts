plugins {
    id("com.draeger.medical.version-conventions")
}

java {
    registerFeature("tests") {
        usingSourceSet(sourceSets.test.get())
    }
}
