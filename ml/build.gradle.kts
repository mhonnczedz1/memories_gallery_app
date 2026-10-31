plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "dev.build_with_mhonn.memories.ml"
}

dependencies {
    implementation(project(":core"))
}
