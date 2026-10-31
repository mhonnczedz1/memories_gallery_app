plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "dev.build_with_mhonn.memories.testing"
}

dependencies {
    implementation(project(":core"))
    implementation(project(":data"))
}
