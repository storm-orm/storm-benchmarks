plugins {
    base
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.ksp) apply false
}

allprojects {
    group = "st.orm.benchmarks"
    version = "0.1.0-SNAPSHOT"
}
