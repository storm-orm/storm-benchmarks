plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ksp)
    alias(libs.plugins.storm)
    alias(libs.plugins.jmh)
}

kotlin {
    jvmToolchain(21)
}

configurations["jmhImplementation"].extendsFrom(configurations["implementation"])
configurations["jmhRuntimeOnly"].extendsFrom(configurations["runtimeOnly"])

dependencies {
    implementation(project(":bench-common"))
    runtimeOnly(libs.storm.postgresql)
    // storm-core is already a runtime dependency via the st.orm plugin; the jmh
    // source set also compiles against it for the SQL-printing utility.
    jmhImplementation(libs.storm.core)
}

tasks.register<JavaExec>("printSql") {
    description = "Runs every Storm workload once and prints the generated SQL."
    classpath = sourceSets["jmh"].runtimeClasspath
    mainClass = "st.orm.benchmarks.storm.PrintSqlKt"
    providers.systemProperty("bench.jdbc.url").orNull?.let { systemProperty("bench.jdbc.url", it) }
}

tasks.register<JavaExec>("profileHotLoop") {
    description = "Dedicated hot loops over the by-id build path, one JIT profile per variant."
    classpath = sourceSets["jmh"].runtimeClasspath
    mainClass = "st.orm.benchmarks.storm.ProfileHotLoopKt"
}

tasks.register<JavaExec>("profileSplit") {
    description = "Splits Storm's per-query cost into build versus execution plus hydration."
    classpath = sourceSets["jmh"].runtimeClasspath
    mainClass = "st.orm.benchmarks.storm.ProfileSplitKt"
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
    (project.findProperty("benchThreads") as String?)?.let { threads.set(it.toInt()) }
    if (project.hasProperty("quick")) {
        fork.set(1)
        warmupIterations.set(2)
        warmup.set("1s")
        iterations.set(3)
        timeOnIteration.set("1s")
    }
    // Opt-in A/B mode (-PabGc): deterministic GC + fixed heap to cut pause-driven variance, plus the
    // allocation profiler for a variance-free bytes/op read. Off by default so normal suite runs are unaffected.
    if (project.hasProperty("abGc")) {
        jvmArgsAppend.add("-XX:+UseParallelGC")
        jvmArgsAppend.add("-Xms4g")
        jvmArgsAppend.add("-Xmx4g")
        profilers.add("gc")
    }
}

tasks.register<JavaExec>("profileGrouped") {
    description = "Splits the objectGraph cost: plain, ordered, and grouped over the same query."
    classpath = sourceSets["jmh"].runtimeClasspath
    mainClass = "st.orm.benchmarks.storm.ProfileGroupedKt"
}
