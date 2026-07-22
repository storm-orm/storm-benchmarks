plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.jmh)
}

kotlin {
    jvmToolchain(21)
}

configurations["jmhImplementation"].extendsFrom(configurations["implementation"])
configurations["jmhRuntimeOnly"].extendsFrom(configurations["runtimeOnly"])

dependencies {
    implementation(project(":bench-common"))
    implementation(libs.ktorm.core)
    implementation(libs.ktorm.support.postgresql)
    runtimeOnly(libs.pgjdbc)
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
