plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

// Pure Kotlin/JVM port of Il2CppDumper. Used by the Android app and runnable on desktop:
//   ./gradlew :core:run --args="libil2cpp.so global-metadata.dat out/"
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

application {
    mainClass.set("dev.il2cpp.dumper.core.cli.MainKt")
    applicationName = "il2cppdumper"
}

dependencies {
    testImplementation(libs.junit)
}

tasks.test {
    // Integration tests read sample binaries from -Dil2cpp.samples=<dir> when available.
    // Allow passing sample locations via -P, -D or env so the daemon always sees them.
    fun cfg(name: String, env: String) = (project.findProperty(name) as String?) ?: System.getProperty(name) ?: System.getenv(env) ?: ""
    systemProperty("il2cpp.samples", cfg("il2cpp.samples", "IL2CPP_SAMPLES"))
    systemProperty("il2cpp.apk", cfg("il2cpp.apk", "IL2CPP_APK"))
    inputs.property("il2cpp.samples", cfg("il2cpp.samples", "IL2CPP_SAMPLES"))
    inputs.property("il2cpp.apk", cfg("il2cpp.apk", "IL2CPP_APK"))
    maxHeapSize = "3g"
    testLogging { events("passed", "failed", "skipped"); showStandardStreams = true }
}
