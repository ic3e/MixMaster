plugins {
    // the same versions as MixMaster's build, which CI already has cached and knows to work
    id("com.android.application") version "8.10.1"
    id("org.jetbrains.kotlin.android") version "2.0.21"
}

// Car Mech: one long day in a neon garage, fixing cars for people who should not own them. The game
// is a web page in the app's assets (assets/carmech) with its 3D engine and fonts beside it, so it
// plays with no signal; the app around it only puts it on screen, lends it the phone's voices for
// the customers, and its buzz for the wrench.
android {
    namespace = "com.conwic.carmech"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.conwic.carmech"
        minSdk = 26
        targetSdk = 36
        // the run number of its own workflow, so every APK installs over the one before it
        versionCode = (System.getenv("CARMECH_BUILD_NUMBER") ?: "1").toInt()
        versionName = "1.0.${System.getenv("CARMECH_BUILD_NUMBER") ?: "0"}"
    }

    signingConfigs {
        // The repository's fixed debug keystore, as MixMaster and Pour Day use: a new APK installs
        // over the last one instead of asking for the old one to be removed first.
        getByName("debug") {
            storeFile = file("../keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.activity:activity-ktx:1.9.1")
    implementation("androidx.core:core-ktx:1.13.1")
}
