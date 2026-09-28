plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Pour Day on its own, for people outside the company: the same game as MixMaster's Break time,
// with nothing of MixMaster in it — no products, projects, stock or company, and no way into them.
// It can still play a day together with co-workers in MixMaster, as long as both carry the same game.
android {
    namespace = "com.conwic.pourday"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.conwic.pourday"
        minSdk = 26
        targetSdk = 34
        // the same run number as MixMaster, so the two APKs from one build say they're from it
        versionCode = (System.getenv("MIXMASTER_BUILD_NUMBER") ?: "1").toInt()
        versionName = "1.0.${System.getenv("MIXMASTER_BUILD_NUMBER") ?: "0"}"
    }

    // The same fixed debug keystore as MixMaster, so each new APK installs over the last one.
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("keystore/debug.keystore")
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
    implementation(project(":game"))
    implementation("androidx.activity:activity-ktx:1.9.1")
    implementation("androidx.core:core-ktx:1.13.1")
}
