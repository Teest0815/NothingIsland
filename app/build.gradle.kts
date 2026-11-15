plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.nothingisland"
    compileSdk = 34

    defaultConfig {
        applicationId = "dev.nothingisland"
        minSdk = 29
        targetSdk = 34
        // Muss bei jedem Update steigen; CI nutzt die Build-Nummer.
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        // Version = 1.<Build-Nummer>: steigt mit jedem Push und steht in der App (START) und im Release.
        versionName = "1." + (System.getenv("GITHUB_RUN_NUMBER") ?: "0")
    }

    // Fester Schlüssel aus der Umgebung (GitHub-Secrets): Nur so lassen sich neue Versionen über die alte App installieren.
    // Ohne diese Variablen (lokaler Build, Forks) wird mit dem Standard-Debug-Schlüssel signiert.
    val ksFile = System.getenv("KEYSTORE_FILE")
    val ksPass = System.getenv("KEYSTORE_PASSWORD")
    signingConfigs {
        if (ksFile != null && ksPass != null) {
            create("shared") {
                storeFile = file(ksFile)
                storePassword = ksPass
                keyAlias = "nothingisland"
                keyPassword = ksPass
            }
        }
    }

    buildTypes {
        debug {
            signingConfigs.findByName("shared")?.let { signingConfig = it }
        }
        release {
            isMinifyEnabled = false
            signingConfigs.findByName("shared")?.let { signingConfig = it }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:4.3")
}
