import java.util.Properties
import java.time.LocalDate

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// ── Load signing properties from external directory ──
//
// The keystore lives outside the repository. local.properties names the folder holding it
// through HAZEL_SIGNING_DIR, and that folder must also contain a signing.properties with:
//
//   storeFile=<keystore file name, relative to that folder>
//   storePassword=...
//   keyAlias=...
//   keyPassword=...
//
// When any of that is missing the release build produces an unsigned APK and says why,
// instead of failing inside validateSigningRelease with no explanation.
val localProps = Properties().apply {
    rootProject.file("local.properties").inputStream().use { load(it) }
}
val signingDirPath: String? = localProps.getProperty("HAZEL_SIGNING_DIR")

val signingProblem: String? = when {
    signingDirPath.isNullOrBlank() ->
        "HAZEL_SIGNING_DIR is not set in local.properties"

    !file(signingDirPath).isDirectory ->
        "Signing folder does not exist: $signingDirPath"

    !file("$signingDirPath/signing.properties").exists() ->
        "signing.properties missing in $signingDirPath"

    else -> null
}

val signingProps: Properties? = if (signingProblem == null) {
    Properties().apply {
        file("$signingDirPath/signing.properties").inputStream().use { load(it) }
    }
} else null

// The keystore itself has to be there too, or signing cannot be configured.
val keystoreFile = signingProps?.getProperty("storeFile")
    ?.let { file("$signingDirPath/$it") }
    ?.takeIf { it.exists() }

val canSignRelease = signingProps != null && keystoreFile != null

gradle.taskGraph.whenReady {
    if (!canSignRelease && allTasks.any { it.name.contains("Release") }) {
        logger.warn(
            "Release builds are UNSIGNED: " +
                    (signingProblem
                        ?: "keystore file '${signingProps?.getProperty("storeFile")}' " +
                        "not found in $signingDirPath")
        )
    }
}

android {
    namespace = "com.hazel.android"
    compileSdk = 36

    // ── Release signing, read from the external signing directory ──
    signingConfigs {
        if (canSignRelease) {
            create("release") {
                storeFile = keystoreFile
                storePassword = signingProps!!.getProperty("storePassword")
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

        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("x86", "x86_64", "armeabi-v7a", "arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            // Left unset when the keystore is unavailable, so the build produces an
            // unsigned APK instead of failing during signing validation.
            signingConfig = signingConfigs.findByName("release")
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

    // Networking (URL validation + yt-dlp release metadata)
    implementation(libs.okhttp)

    // Metadata-only extractor. Used to list what is behind a link on the sites it knows,
    // where it answers in one request instead of a yt-dlp process. It never resolves
    // formats and never downloads: yt-dlp owns both, and this falls back to it on any
    // failure, so a stale extractor costs speed rather than function.
    implementation(libs.newpipe.extractor)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // In-app browser (Chrome Custom Tabs)
    implementation(libs.androidx.browser)

    // Thumbnail loading for fetched media
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

}
