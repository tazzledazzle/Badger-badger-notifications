plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ktor) apply false
}

subprojects {
    group = "com.badger.notifications"
    version = "0.1.0-SNAPSHOT"
}
