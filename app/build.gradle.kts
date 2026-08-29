plugins { id("com.android.application") }
android { namespace="dev.soundceiling.app"; compileSdk=35
 defaultConfig { applicationId="dev.soundceiling.app"; minSdk=29; targetSdk=35; versionCode=35; versionName="0.8.0" }
 buildFeatures { buildConfig = true }
 signingConfigs {
  create("soundCeilingDev") {
   storeFile = rootProject.file("ci/soundceiling-dev.keystore")
   storePassword = "soundceiling-dev-only"
   keyAlias = "soundceiling-dev"
   keyPassword = "soundceiling-dev-only"
  }
 }
 buildTypes {
  getByName("debug") { signingConfig = signingConfigs.getByName("soundCeilingDev") }
  release { isMinifyEnabled=false; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"),"proguard-rules.pro") }
 }
 compileOptions { sourceCompatibility=JavaVersion.VERSION_17; targetCompatibility=JavaVersion.VERSION_17 }
}

dependencies { implementation("androidx.core:core:1.16.0") }
