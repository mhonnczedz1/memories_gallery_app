plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "dev.build_with_mhonn.memories.data"
}

dependencies {
    implementation(project(":core"))
}
