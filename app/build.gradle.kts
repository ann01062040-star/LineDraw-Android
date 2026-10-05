plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}
android {
    namespace = "com.linedraw.app"
    compileSdk { version = release(37) }
    defaultConfig {
        applicationId = "com.linedraw.standalone"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-standalone"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("boolean", "FIVE_LINK_TEST", "false")
        manifestPlaceholders["appLabel"] = "LineDraw 獨立版"
    }
    buildTypes {
        getByName("debug") { isDefault = true }
        create("pilot") {
            initWith(getByName("debug"))
            // 獨立測試 APK 僅載入測試清單，預設建置仍為 Funbox 版本。
            isDefault = false
            applicationIdSuffix = ".pilot"
            versionNameSuffix = "-test-links"
            matchingFallbacks += "debug"
            buildConfigField("boolean", "FIVE_LINK_TEST", "true")
            manifestPlaceholders["appLabel"] = "LineDraw 獨立測試版"
        }
    }
    testBuildType = providers.gradleProperty("linedraw.testBuildType").getOrElse("debug")
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    testOptions { unitTests.isIncludeAndroidResources = true }
}
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.02.01"))
    implementation("androidx.activity:activity-compose:1.8.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jsoup:jsoup:1.21.2")
    ksp("androidx.room:room-compiler:2.8.4")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.02.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:runner:1.5.2")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
