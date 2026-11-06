plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}
android {
    namespace = "com.pilltack.app"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.pilltack.app"
        minSdk = 26
        targetSdk = 34
        // Must increase on every build for Android to accept it as an update. CI run number does that automatically.
        versionCode = (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1) + 1
        versionName = "1.1"
    }
    // Same key on every build => each new APK installs as an update and keeps your data.
    signingConfigs {
        create("pilltack") {
            storeFile = file("pilltack.jks")
            storePassword = "pilltack123"
            keyAlias = "pilltack"
            keyPassword = "pilltack123"
        }
    }
    buildTypes {
        getByName("debug") { signingConfig = signingConfigs.getByName("pilltack") }
        release { isMinifyEnabled = false; signingConfig = signingConfigs.getByName("pilltack") }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.0")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
