import java.util.Properties
import java.time.LocalDate

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// ── Load signing properties from external directory ──
val localProps = Properties().apply {
    rootProject.file("local.properties").inputStream().use { load(it) }
}
val signingDirPath = localProps.getProperty("HAZEL_SIGNING_DIR")
val signingProps: Properties? = if (signingDirPath != null) {
    val signingFile = file("$signingDirPath/signing.properties")
    if (signingFile.exists()) {
        Properties().apply { signingFile.inputStream().use { load(it) } }
    } else null
} else null

android {
    namespace = "com.hazel.android"
    compileSdk = 36

    // ── Release signing — auto-reads from external signing directory ──
    signingConfigs {
        create("release") {
            if (signingProps != null && signingDirPath != null) {
                storeFile = file("$signingDirPath/${signingProps.getProperty("storeFile")}")
                storePassword = signingProps.getProperty("storePassword")
                keyAlias = signingProps.getProperty("keyAlias")
                keyPassword = signingProps.getProperty("keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "com.hazel.android"
        minSdk = 24
        targetSdk = 35

        // Date-based versionCode: YYYYMMDDNN — never exhausts, always increases
        val date = LocalDate.now()
        val buildNum = project.findProperty("BUILD_NUM")?.toString()?.toIntOrNull() ?: 1
        versionCode = date.year * 1_000_000 + date.monthValue * 10_000 +
                date.dayOfMonth * 100 + buildNum

        // TODO: Bump this for every GitHub Release!
        // If this doesn't match the release tag, the app will always show "update available".
      
        versionName = "1.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("x86", "x86_64", "armeabi-v7a", "arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

// AGP 9.x built-in Kotlin — compilerOptions at top level
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Download Engine — yt-dlp + FFmpeg for Android
    implementation(libs.youtubedl.android.library)
    implementation(libs.youtubedl.android.ffmpeg)

    // Networking (for update checker)
    implementation(libs.okhttp)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // In-app browser (Chrome Custom Tabs)
    implementation(libs.androidx.browser)

    // Media3 — ExoPlayer + MediaSession for music player
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)

    // Image loading (album art)
    implementation(libs.coil.compose)

}
