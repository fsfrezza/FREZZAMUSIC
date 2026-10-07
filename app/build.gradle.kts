plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.frezzamusic.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.frezzamusic.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 22
        versionName = "0.21.1"
    }
    flavorDimensions += "project"
    productFlavors {
        create("frezzamusic") {
            dimension = "project"
            applicationId = "com.frezzamusic.app"
            resValue("string", "app_name", "FREZZAMUSIC")
            buildConfigField("String", "PROJECT_MODE", "\"FREZZAMUSIC\"")
            buildConfigField("String", "ARTIST_FILTER", "\"\"")
        }
        create("solasias") {
            dimension = "project"
            applicationId = "com.frezzamusic.solasias"
            resValue("string", "app_name", "Solasias")
            buildConfigField("String", "PROJECT_MODE", "\"SOLASIAS\"")
            buildConfigField("String", "ARTIST_FILTER", "\"Solasias\"")
        }
        create("thefrezza") {
            dimension = "project"
            applicationId = "com.frezzamusic.thefrezza"
            resValue("string", "app_name", "theFrezza")
            buildConfigField("String", "PROJECT_MODE", "\"THEFREZZA\"")
            buildConfigField("String", "ARTIST_FILTER", "\"theFrezza\"")
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.01.00"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("io.coil-kt.coil3:coil-compose:3.2.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.2.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-session:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.5.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    implementation("com.google.guava:guava:33.4.0-android")
}
