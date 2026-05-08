plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.zhoulesin.zutils.permissions"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
}
