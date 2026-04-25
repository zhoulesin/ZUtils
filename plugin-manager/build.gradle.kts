plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.zhoulesin.zutils.plugin"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
    }
}

dependencies {
    implementation(project(":engine-core"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
}
