pluginManagement {
    repositories {
        // mavenLocal() first so a Storm build installed by the benchmark workflow wins over the
        // released artifact of the same version: CI checks out the storm-framework ref under test,
        // installs it as the version pinned in libs.versions.toml, and benchmarks that. Released
        // versions resolve from Maven Central when nothing local shadows them.
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
