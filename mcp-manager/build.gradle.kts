plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.zhoulesin.zutils.mcp"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
    }
}

dependencies {
    implementation(project(":engine-core"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
}
