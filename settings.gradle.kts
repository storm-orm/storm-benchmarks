pluginManagement {
    repositories {
        // Storm 1.13.0 is resolved from mavenLocal until the release lands on Maven Central.
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        mavenCentral()
    }
}

rootProject.name = "storm-benchmarks"

include(
    "bench-common",
    "bench-jdbc",
    "bench-storm",
    "bench-hibernate",
    "bench-jooq",
    "bench-exposed",
    "bench-exposed-dao",
    "bench-ktorm",
    "bench-jimmer",
)
