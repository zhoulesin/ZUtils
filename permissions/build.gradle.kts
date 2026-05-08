plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.zhoulesin.zutils.permissions"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
}
