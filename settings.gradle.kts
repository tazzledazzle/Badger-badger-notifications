plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "Badger-badger-notifications"

include(
    "shared",
    "broker-api",
    "broker-redis",
    "persistence",
    "channels",
    "gateway",
    "worker",
)
