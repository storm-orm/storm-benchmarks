plugins {
    java
    alias(libs.plugins.jooq)
    alias(libs.plugins.jmh)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

configurations["jmhImplementation"].extendsFrom(configurations["implementation"])
configurations["jmhRuntimeOnly"].extendsFrom(configurations["runtimeOnly"])

dependencies {
    implementation(project(":bench-common"))
    implementation(libs.jooq)
    jooqCodegen(libs.jooq.meta.extensions)
    runtimeOnly(libs.pgjdbc)
}

// Generate the jOOQ classes from the shared DDL, so the schema stays single-sourced.
jooq {
    configuration {
        generator {
            database {
                name = "org.jooq.meta.extensions.ddl.DDLDatabase"
                properties {
                    property {
                        key = "scripts"
                        value = rootProject.file("bench-common/src/main/resources/schema.sql").absolutePath
                    }
                    property {
                        key = "sort"
                        value = "semantic"
                    }
                    property {
                        key = "unqualifiedSchema"
                        value = "none"
                    }
                    property {
                        key = "defaultNameCase"
                        value = "lower"
                    }
                }
            }
            target {
                packageName = "st.orm.benchmarks.jooq.generated"
                directory = layout.buildDirectory.dir("generated-jooq").get().asFile.path
            }
        }
    }
}

sourceSets["main"].java.srcDir(layout.buildDirectory.dir("generated-jooq"))

tasks.named("compileJava") {
    dependsOn("jooqCodegen")
}

jmh {
    resultFormat.set("JSON")
    resultsFile.set(layout.buildDirectory.file("results/${project.name}.json"))
    (project.findProperty("benchIncludes") as String?)?.let { includes.add(it) }
    providers.systemProperty("bench.jdbc.url").orNull?.let { url ->
        jvmArgsAppend.add("-Dbench.jdbc.url=$url")
    }
    if (project.hasProperty("sample")) {
        benchmarkMode.set(listOf("sample"))
    }
    // Opt-in A/B mode (-PabGc): deterministic GC + fixed heap to cut pause-driven variance, plus the
    // allocation profiler for a variance-free bytes/op read. Off by default so normal suite runs are unaffected.
    if (project.hasProperty("abGc")) {
        jvmArgsAppend.add("-XX:+UseParallelGC")
        jvmArgsAppend.add("-Xms4g")
        jvmArgsAppend.add("-Xmx4g")
        profilers.add("gc")
    }
    (project.findProperty("benchThreads") as String?)?.let { threads.set(it.toInt()) }
    if (project.hasProperty("quick")) {
        fork.set(1)
        warmupIterations.set(2)
        warmup.set("1s")
        iterations.set(3)
        timeOnIteration.set("1s")
    }
}
