plugins {
    alias(libs.plugins.android.test)
}

android {
    namespace = "dev.build_with_mhonn.memories.benchmark"
    targetProjectPath = ":app"
    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.test.ext.junit)
}
