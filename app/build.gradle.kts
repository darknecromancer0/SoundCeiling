plugins { id("com.android.application") }
android { namespace="dev.soundceiling.app"; compileSdk=35
 defaultConfig { applicationId="dev.soundceiling.app"; minSdk=29; targetSdk=35; versionCode=9; versionName="0.6.0" }
 buildFeatures { buildConfig = true }
 buildTypes { release { isMinifyEnabled=false; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"),"proguard-rules.pro") } }
 compileOptions { sourceCompatibility=JavaVersion.VERSION_17; targetCompatibility=JavaVersion.VERSION_17 }
}

dependencies { implementation("androidx.core:core:1.16.0") }
