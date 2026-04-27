plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("com.badger.notifications.worker.WorkerKt")
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(project(":shared"))
    implementation(project(":broker-api"))
    implementation(project(":broker-redis"))
    implementation(project(":persistence"))
    implementation(project(":channels"))
    implementation(libs.kotlinx.coroutines)
    implementation(libs.micrometer.registry.prometheus)
    implementation(libs.logback.classic)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
