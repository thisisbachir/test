plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.seventyfivehard.tracker"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.seventyfivehard.tracker"
        minSdk = 26
        targetSdk = 35
        versionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
        versionName = "1.0." + (System.getenv("VERSION_CODE") ?: "0")
    }

    // One fixed key so every build can be installed over the previous one
    // without uninstalling (which would wipe your data). Override with your
    // own keystore through these environment variables if you prefer.
    signingConfigs {
        create("tracker") {
            storeFile = file(System.getenv("TRACKER_KEYSTORE") ?: "signing/tracker.jks")
            storePassword = System.getenv("TRACKER_KEYSTORE_PASSWORD") ?: "tracker75"
            keyAlias = System.getenv("TRACKER_KEY_ALIAS") ?: "tracker"
            keyPassword = System.getenv("TRACKER_KEY_PASSWORD") ?: "tracker75"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("tracker")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("tracker")
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
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.biometric:biometric:1.1.0")
    // biometric drags in Fragment 1.2, which breaks activity-result APIs; use a current one.
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    testImplementation("junit:junit:4.13.2")
}
