plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.build_with_mhonn.memories"

    defaultConfig {
        applicationId = "dev.build_with_mhonn.memories"
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = providers.gradleProperty("VERSION_CODE").get().toInt()
        versionName = providers.gradleProperty("VERSION_NAME").get()
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    assetPacks += listOf(":models_pack")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":data"))
    implementation(project(":ml"))
    implementation(project(":engine"))
    implementation(project(":widget"))
    implementation(project(":vault"))

    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
}
