plugins {
    id("com.android.application")
}

android {
    namespace = "dev.soundceiling.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.soundceiling.app"
        minSdk = 29
        targetSdk = 35
        versionCode = 4
        versionName = "0.2.2"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
