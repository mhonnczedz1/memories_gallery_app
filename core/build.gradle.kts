import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Plain Kotlin module (E1.10): Kotlin stdlib, kotlinx-coroutines-core, kotlinx-datetime and
// kotlinx-serialization only. No Android dependencies.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.toVersion(libs.versions.jvmTarget.get())
    targetCompatibility = JavaVersion.toVersion(libs.versions.jvmTarget.get())
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(libs.versions.jvmTarget.get()))
    }
}

dependencies {
    testImplementation(libs.kotlin.test.junit)
}
