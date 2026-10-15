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
        versionCode = 37
        versionName = "0.23.2"
        buildConfigField("String", "RANKINGS_API_URL", "\"https://frezzamusic-global-rankings.fondfield256.chatgpt.site\"")
        buildConfigField("String", "FIREBASE_API_KEY", "\"" + (providers.gradleProperty("frezzamusicFirebaseApiKey").orNull ?: "") + "\"")
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"" + (providers.gradleProperty("frezzamusicFirebaseProjectId").orNull ?: "") + "\"")
        buildConfigField("String", "PURCHASE_API_URL", "\"" + (providers.gradleProperty("frezzamusicPurchaseApiUrl").orNull ?: "") + "\"")
    }
    flavorDimensions += "project"
    productFlavors {
        create("frezzamusic") {
            dimension = "project"
            buildConfigField("String", "FIREBASE_APP_ID", "\"" + (providers.gradleProperty("frezzamusicFirebaseAppId").orNull ?: "") + "\"")
            applicationId = "com.frezzamusic.app"
            resValue("string", "app_name", "FREZZAMUSIC")
            buildConfigField("String", "PROJECT_MODE", "\"FREZZAMUSIC\"")
            buildConfigField("String", "ARTIST_FILTER", "\"\"")
        }
        create("solasias") {
            dimension = "project"
            buildConfigField("String", "FIREBASE_APP_ID", "\"" + (providers.gradleProperty("solasiasFirebaseAppId").orNull ?: "") + "\"")
            applicationId = "com.frezzamusic.solasias"
            resValue("string", "app_name", "SOLASIAS")
            buildConfigField("String", "PROJECT_MODE", "\"SOLASIAS\"")
            buildConfigField("String", "ARTIST_FILTER", "\"Solasias\"")
        }
        create("thefrezza") {
            dimension = "project"
            buildConfigField("String", "FIREBASE_APP_ID", "\"" + (providers.gradleProperty("thefrezzaFirebaseAppId").orNull ?: "") + "\"")
            applicationId = "com.frezzamusic.thefrezza"
            resValue("string", "app_name", "theFrezza")
            buildConfigField("String", "PROJECT_MODE", "\"THEFREZZA\"")
            buildConfigField("String", "ARTIST_FILTER", "\"theFrezza\"")
        }
    }
    // Signing material comes from the environment, never from this public repository.
    val releaseStore = providers.environmentVariable("FREZZAMUSIC_KEYSTORE_PATH").orNull
    signingConfigs {
        if (releaseStore != null) create("production") {
            storeFile = file(releaseStore)
            storePassword = providers.environmentVariable("FREZZAMUSIC_KEYSTORE_PASSWORD").get()
            keyAlias = providers.environmentVariable("FREZZAMUSIC_KEY_ALIAS").get()
            keyPassword = providers.environmentVariable("FREZZAMUSIC_KEY_PASSWORD").get()
        }
    }
    buildTypes {
        getByName("release") {
            isDebuggable = false
            isJniDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseStore != null) signingConfig = signingConfigs.getByName("production")
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
    implementation("org.jsoup:jsoup:1.18.3")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
    implementation(platform("androidx.compose:compose-bom:2025.01.00"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("com.mpatric:mp3agic:0.9.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("io.coil-kt.coil3:coil-compose:3.2.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.2.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-session:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.5.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    implementation("com.google.guava:guava:33.4.0-android")
    implementation("com.google.firebase:firebase-auth:23.2.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.1")
}
