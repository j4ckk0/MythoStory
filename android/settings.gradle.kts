/*
 * The Android app's Gradle build: it packages into an APK the jars Maven resolved into target/libs (android/pom.xml),
 * and Maven runs it — scripts/build.sh & co, as soon as the Android SDK is there. Gradle manages no library here:
 * Maven does. Opened on its own (Android Studio), it needs those jars first: scripts/build.sh once.
 */
pluginManagement {
    // Kotlin: pom.xml's version — the one Ktor and the modules are built with
    val kotlinVersion = Regex("<kotlin.version>(.+?)</kotlin.version>")
        .find(settingsDir.resolve("../pom.xml").readText())?.groupValues?.get(1)
        ?: error("../pom.xml: no <kotlin.version>")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("com.android.application") version "9.4.1"
        id("org.jetbrains.kotlin.android") version kotlinVersion
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "mythostory-android"
