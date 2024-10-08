allprojects {
    // Access properties defined in gradle.properties
    val revision: String by project
    val changelist: String by project

    group = "com.draeger.medical"
    version = "$revision$changelist"
}