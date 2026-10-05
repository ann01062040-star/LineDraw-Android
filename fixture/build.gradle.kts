plugins { id("com.android.application") }
android {
    namespace = "com.linedraw.fixture"
    compileSdk { version = release(37) }
    defaultConfig { applicationId = "com.linedraw.fixture"; minSdk = 31; targetSdk = 36; versionCode = 1; versionName = "1.0" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
