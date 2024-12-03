val defaultVersion = "9.1.0-SNAPSHOT"
val actualVersion = project.findProperty("revision") ?: defaultVersion
val actualRevision = project.findProperty("changelist") ?: ""

group = "com.draeger.medical"
version = "$actualVersion$actualRevision"