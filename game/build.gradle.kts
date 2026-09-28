import java.security.MessageDigest

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

// Pour Day, the game, in one place for the two apps that carry it: MixMaster's Break time and the
// Pour Day app for people outside the company. The page, its 3D engine and fonts, the phone's
// voices and the Nearby link for co-workers all live here; each app only puts it on screen.

// Phones play one day together only on the same game: the same page, the same rules, the same
// random site from the same seed. The stamp is taken from the game's own files, so MixMaster and
// the Pour Day app can play together whenever they carry the same game, whatever their own
// version numbers are.
val gameStamp: String = MessageDigest.getInstance("SHA-256").run {
    listOf("game.js", "index.html").forEach { update(file("src/main/assets/pourday/$it").readBytes()) }
    digest().take(6).joinToString("") { "%02x".format(it) }
}

android {
    namespace = "com.conwic.pourday.game"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
        buildConfigField("String", "GAME_STAMP", "\"$gameStamp\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    // Co-workers, phone to phone: Nearby Connections finds the other phones over Bluetooth and
    // talks over Wi-Fi Direct or Bluetooth, no server and no signal needed.
    implementation("com.google.android.gms:play-services-nearby:19.1.0")
}
