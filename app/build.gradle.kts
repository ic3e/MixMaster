plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.conwic.mixmaster"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.conwic.mixmaster"
        minSdk = 26
        targetSdk = 34
        // CI passes its run number so each build is a distinct version; without this every
        // build shipped as version 1 and nothing told one APK apart from the next.
        versionCode = (System.getenv("MIXMASTER_BUILD_NUMBER") ?: "1").toInt()
        versionName = "1.0.${System.getenv("MIXMASTER_BUILD_NUMBER") ?: "0"}"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Fixed debug keystore committed at keystore/debug.keystore so every build — CI or
    // local — signs with the same certificate. Without this, each CI runner is a fresh
    // VM and Android Gradle Plugin auto-generates a new random debug key per run, which
    // makes every new APK fail to install over the previous one ("App not installed").
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
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

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Writes the schema Room expects for each database version to app/schemas.
// That file is the ground truth a hand-written Migration has to match, and CI commits
// it back to the repo so the two can be compared after a build.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.navigation:navigation-compose:2.8.0")

    // Room (local, exportable SQLite database)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Preferences DataStore (role, theme, app lock, onboarding state)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Biometric app-lock. BiometricPrompt needs a FragmentActivity, and the fragment version
    // biometric drags in on its own is far older than this project's activity/lifecycle
    // versions — so fragment is pinned to something that matches them.
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.fragment:fragment-ktx:1.8.3")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")


    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
