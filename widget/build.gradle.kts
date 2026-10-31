plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "dev.build_with_mhonn.memories.widget"
}

dependencies {
    implementation(project(":core"))
    implementation(project(":data"))
    implementation(project(":engine"))
}
