/*
 * The Android app: MythoStory's backend running inside the app on 127.0.0.1, its page in a WebView.
 * Built by Maven (android/pom.xml), which resolves the dependencies into target/libs first; see settings.gradle.kts.
 *
 * The release APK is signed with the key keystore.properties names (not under source control):
 *   storeFile=/path/to/release.jks
 *   storePassword=…
 *   keyAlias=…
 *   keyPassword=…
 * and without it, with the debug key: installable for testing, not publishable.
 */
import java.util.Properties

plugins {
    id("com.android.application")
    // only its version (pom.xml's, settings.gradle.kts): AGP's built-in Kotlin compiles with it
    id("org.jetbrains.kotlin.android") apply false
}

// the version, from pom.xml: said once, there
val revision = Regex("<revision>(.+?)</revision>").find(file("../pom.xml").readText())?.groupValues?.get(1)
    ?: error("../pom.xml: no <revision>")

// Gradle's output under target/, which mvn clean removes
layout.buildDirectory = file("target/gradle")

val keystore = file("keystore.properties").takeIf { it.isFile }?.let { f -> Properties().apply { f.inputStream().use(::load) } }

android {
    namespace = "org.jtools.mythostory.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.jtools.mythostory"
        minSdk = 26
        targetSdk = 36
        versionName = revision
        // a.b.c → a·10000 + b·100 + c: grows with every version
        versionCode = revision.substringBefore('-').split('.').map(String::toInt).let { (a, b, c) -> a * 10000 + b * 100 + c }
    }

    signingConfigs {
        if (keystore != null) {
            create("release") {
                storeFile = file(keystore.getProperty("storeFile"))
                storePassword = keystore.getProperty("storePassword")
                keyAlias = keystore.getProperty("keyAlias")
                keyPassword = keystore.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // no shrinking: Ktor and kotlinx.serialization find classes by reflection and ServiceLoader
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // the list of the libraries and their licences Maven's license plugin writes (android/pom.xml), among the APK's
    // resources: the server reads it from there (backend: Licences)
    sourceSets.getByName("main").resources.srcDir("target/third-party")

    packaging {
        resources {
            // the libraries' licences and notices, which their licences ask to go with them: several jars carry them
            // under the same name, so they are put end to end rather than one kept
            merges += listOf("META-INF/LICENSE*", "META-INF/NOTICE*")
            // what several jars carry under the same name, and the app never reads
            excludes += listOf(
                "META-INF/INDEX.LIST",
                "META-INF/DEPENDENCIES",
                "META-INF/*.kotlin_module",
                "META-INF/versions/9/previous-compilation-data.bin",
                "META-INF/io.netty.versions.properties",
            )
        }
    }
}

if (keystore == null) logger.warn("android/keystore.properties absent: the release APK is signed with the debug key")

dependencies {
    val libs = fileTree("target/libs") { include("*.jar") }
    if (libs.isEmpty) logger.warn("android/target/libs is empty: build through Maven (scripts/build.sh), which resolves the dependencies there")
    implementation(libs)
}
