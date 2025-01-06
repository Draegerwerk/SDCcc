plugins {
    id("com.draeger.medical.java-conventions")
    id("com.github.spotbugs")
    id("com.diffplug.spotless")
    checkstyle
}

tasks.check{
    dependsOn("spotbugsMain")
    dependsOn("spotbugsTest")
}

tasks.withType<com.github.spotbugs.snom.SpotBugsTask> {
    excludeFilter.set(file("$projectDir/../dev_config/filter.xml"))
    reports.create("xml") { required = true }
}

spotless {
    java {
        palantirJavaFormat()
        target("src/**/*.java")
    }
}

checkstyle {
    configFile = file("../checkstyle/checkstyle.xml")
}