import java.security.MessageDigest

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

// Pour Day, the game, in one place for the two apps that carry it: MixMaster's Break time and the
// Pour Day app for people outside the company. The page, its 3D engine and fonts, the phone's
// voices and the Nearby link for co-workers all live here; each app only puts it on screen.

// Which game this is, as a hash of everything that makes up Pour Day: this module (the page, its
// voices, its Nearby link) and the Pour Day app around it. Phones play one day together only on
// the same game — the same page, the same rules, the same random site from the same seed — so
// MixMaster and the Pour Day app can play together whenever they carry the same one, whatever their
// own version numbers are. And the Pour Day app offers its players an update only when this has
// changed, not every time MixMaster is rebuilt.
val gameStamp: String = MessageDigest.getInstance("SHA-256").run {
    val files = fileTree("src/main").files + rootProject.fileTree("pourday/src/main").files
    files.map { it.relativeTo(rootDir).invariantSeparatorsPath to it }.sortedBy { it.first }.forEach { (path, f) ->
        update(path.toByteArray())
        update(f.readBytes())
    }
    digest().take(6).joinToString("") { "%02x".format(it) }
}

// CI reads the stamp from here for dist/pourday.json, which the Pour Day app compares with its own.
val writeGameStamp by tasks.registering {
    val out = layout.buildDirectory.file("game-stamp.txt")
    inputs.property("stamp", gameStamp)
    outputs.file(out)
    doLast { out.get().asFile.writeText(gameStamp) }
}
tasks.named("preBuild") { dependsOn(writeGameStamp) }

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
