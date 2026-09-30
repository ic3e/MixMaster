plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Pour Day on its own, for people outside the company: the same game as MixMaster's Break time,
// with nothing of MixMaster in it — no products, projects, stock or company, and no way into them.
// It can still play a day together with co-workers in MixMaster, as long as both carry the same game.
//
// Two builds of it, from the same code:
//  - "direct", the APK passed round as a file (and packed into MixMaster for "Send to a friend"). It
//    looks on GitHub for a newer version of itself and installs it (src/direct).
//  - "play", for Google Play. Play allows no app to update itself any other way than through Play,
//    and the install permission only to apps whose job is installing apps, so this one has neither,
//    and no internet either: nothing in the game needs it (src/play).
android {
    namespace = "com.conwic.pourday"
    // Google Play takes new apps and updates only when they target a recent Android — API 36 since
    // 31 August 2026, and a year later the one after it.
    compileSdk = 36

    defaultConfig {
        applicationId = "com.conwic.pourday"
        minSdk = 26
        targetSdk = 36
        // the same run number as MixMaster, so the two APKs from one build say they're from it
        versionCode = (System.getenv("MIXMASTER_BUILD_NUMBER") ?: "1").toInt()
        versionName = "1.0.${System.getenv("MIXMASTER_BUILD_NUMBER") ?: "0"}"
    }

    flavorDimensions += "store"
    productFlavors {
        create("direct") { dimension = "store" }
        create("play") { dimension = "store" }
    }

    signingConfigs {
        // The same fixed debug keystore as MixMaster, so each new APK installs over the last one.
        getByName("debug") {
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        // The upload key for Google Play. Never in the repository: CI gets it from the repository's
        // secrets (PLAY_UPLOAD_KEYSTORE_BASE64, PLAY_UPLOAD_PASSWORD) and says where it put it here.
        System.getenv("PLAY_UPLOAD_KEYSTORE")?.let { keystore ->
            create("upload") {
                storeFile = file(keystore)
                storePassword = System.getenv("PLAY_UPLOAD_PASSWORD")
                keyAlias = System.getenv("PLAY_UPLOAD_ALIAS") ?: "upload"
                keyPassword = System.getenv("PLAY_UPLOAD_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        // What goes to Google Play: not debuggable (Play turns that away), signed with the upload key
        // when CI has it, and left unsigned otherwise — built all the same, to show it still builds.
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("upload")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    // the version it is, to compare with what CI has published
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":game"))
    implementation("androidx.activity:activity-ktx:1.9.1")
    implementation("androidx.core:core-ktx:1.13.1")
}
