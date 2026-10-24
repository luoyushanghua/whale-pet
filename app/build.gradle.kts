plugins {
    id("com.android.application")
}

android {
    namespace = "com.whale.pet"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.whale.pet"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    androidResources {
        noCompress += listOf("mp4")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
