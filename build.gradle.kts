import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.dsl.TestExtension

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.androidx.benchmark) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.spotless)
}

spotless {
    val ktlintVersion = libs.versions.ktlint.get()
    // Mirrors .editorconfig, which Spotless does not pick up for these rules.
    val ktlintOverrides =
        mapOf(
            "ktlint_code_style" to "android_studio",
            "ktlint_function_naming_ignore_when_annotated_with" to "Composable",
            // The package ID dev.build_with_mhonn.memories is fixed by the plan (E1.1).
            "ktlint_standard_package-name" to "disabled"
        )
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**")
        ktlint(ktlintVersion).editorConfigOverride(ktlintOverrides)
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**")
        ktlint(ktlintVersion).editorConfigOverride(ktlintOverrides)
    }
}

// Settings shared by every Android module: SDK levels, Java level, lint rules (T0.2 req 4),
// and the Gradle Managed Devices from plan E3.
val minSdkLevel = libs.versions.minSdk.get().toInt()
val compileSdkLevel = libs.versions.compileSdk.get().toInt()
val javaLevel = JavaVersion.toVersion(libs.versions.jvmTarget.get())

fun CommonExtension.configureAndroidCommon() {
    compileSdk = compileSdkLevel
    compileOptions.apply {
        sourceCompatibility = javaLevel
        targetCompatibility = javaLevel
    }
    lint.apply {
        warningsAsErrors = false
        abortOnError = true
        error += setOf("HardcodedText", "SetTextI18n", "MissingPermission", "UnusedResources")
    }
    testOptions.managedDevices.apply {
        localDevices.create("api29") {
            device = "Pixel 6"
            apiLevel = 29
            systemImageSource = "aosp"
        }
        localDevices.create("api34") {
            device = "Pixel 6"
            apiLevel = 34
            systemImageSource = "aosp-atd"
        }
        localDevices.create("apiLatest") {
            device = "Pixel 6"
            apiLevel = compileSdkLevel
            systemImageSource = "aosp-atd"
        }
        groups.create("ci") {
            localDevices.forEach { targetDevices.add(it) }
        }
    }
}

subprojects {
    pluginManager.withPlugin("com.android.application") {
        extensions.configure<ApplicationExtension> {
            configureAndroidCommon()
            defaultConfig.minSdk = minSdkLevel
        }
    }
    pluginManager.withPlugin("com.android.library") {
        extensions.configure<LibraryExtension> {
            configureAndroidCommon()
            defaultConfig.minSdk = minSdkLevel
        }
    }
    pluginManager.withPlugin("com.android.test") {
        extensions.configure<TestExtension> {
            configureAndroidCommon()
            defaultConfig.minSdk = minSdkLevel
        }
    }
}
