// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    // 8.10 is the first that builds for Android 16 (API 36), which Google Play asks of Pour Day.
    id("com.android.application") version "8.10.1" apply false
    id("com.android.library") version "8.10.1" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
}
