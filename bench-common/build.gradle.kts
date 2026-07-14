plugins {
    `java-library`
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    api(libs.hikari)
    api(libs.testcontainers.postgresql)
    runtimeOnly(libs.pgjdbc)
    runtimeOnly(libs.slf4j.simple)
}
