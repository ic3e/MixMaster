plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// where the Pour Day APK is put for packing (see embedPourDay)
val pourDayShare: File = layout.buildDirectory.dir("generated/pourdayShare").get().asFile

// Two builds, from the same code, the way Pour Day has them (see pourday/build.gradle.kts):
//  - "direct", the APK passed round as a file and used every day: it updates itself from GitHub,
//    carries the Pour Day APK for "Send to a friend", and targets Android 14 as it always has.
//  - "play", for Google Play, in case the company ever puts MixMaster there. Play takes off apps that
//    update themselves outside Play and allows no APK inside another app's files, so it has neither;
//    no USE_EXACT_ALARM either, which Play keeps for alarm clocks and calendars; no manufacturers'
//    PDFs, which are not ours to hand out; and it targets the Android Play asks for.
android {
    namespace = "com.conwic.mixmaster"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.conwic.mixmaster"
        minSdk = 26
        targetSdk = 34
        // CI passes its run number so each build is a distinct version; without this every
        // build shipped as version 1 and nothing told one APK apart from the next.
        versionCode = (System.getenv("MIXMASTER_BUILD_NUMBER") ?: "1").toInt()
        versionName = "1.0.${System.getenv("MIXMASTER_BUILD_NUMBER") ?: "0"}"
    }

    flavorDimensions += "store"
    productFlavors {
        create("direct") { dimension = "store" }
        create("play") {
            dimension = "store"
            // Google Play takes new apps and updates only when they target a recent Android: API 36
            // since 31 August 2026. Android 15 then draws the app edge to edge; MainActivity keeps
            // the screens between the bars. The copy passed round as a file stays on 34 until the
            // whole app has been tried that way on a phone.
            targetSdk = 36
        }
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
        // The upload key for Google Play, the same one as Pour Day's: never in the repository, CI
        // gets it from the repository's secrets (see docs/pourday-google-play.md).
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
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // what goes to Google Play: signed with the upload key when CI has it, unsigned otherwise
            signingConfig = signingConfigs.findByName("upload")
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

    // The company server travels inside the app, so whoever runs the company after the app is
    // handed over can always put one up — from the setup guide — without the source or its author.
    // And the Pour Day app travels inside it too (see embedPourDay below).
    sourceSets {
        getByName("main") {
            assets.srcDir(rootProject.file("server"))
        }
        // Pour Day's APK rides only in the copy passed round as a file (see embedPourDay).
        getByName("direct") {
            assets.srcDir(pourDayShare)
        }
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

// The Pour Day app, built in the same run, packed into MixMaster's assets (share/pourday.apk) for
// "Send to a friend" under Break time: the game goes to somebody outside the company as a file, by
// Quick Share, Bluetooth or a messenger — on a site with no signal too — and nothing of MixMaster
// goes with it. Built with MixMaster, so it's always the same game as the one the sender has.
// The copy passed round as a file ("direct"), which looks for its own updates — not the Play one.
val embedPourDay by tasks.registering(Copy::class) {
    dependsOn(":pourday:assembleDirectDebug")
    from(rootProject.file("pourday/build/outputs/apk/direct/debug")) {
        include("*.apk")
        rename { "pourday.apk" }
    }
    into(pourDayShare.resolve("share"))
}
// Before the direct builds, and said outright to the tasks that read their assets too, rather than
// trusting it to arrive through the pre-build step alone. The Google Play build has none of it.
tasks.configureEach {
    val direct = name.contains("Direct")
    if (direct && name.startsWith("pre") && name.endsWith("Build")) dependsOn(embedPourDay)
    if (direct && name.startsWith("merge") && name.endsWith("Assets")) dependsOn(embedPourDay)
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

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    // LocalLifecycleOwner lives here now; Compose's own copy of it is deprecated.
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
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

    // Pour Day, the break-time game: shared with the Pour Day app for people outside the company.
    implementation(project(":game"))
}
