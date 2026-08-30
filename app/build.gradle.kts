import com.android.build.api.variant.FilterConfiguration
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
//
// local.properties is a machine-local file and is absent on a build server, so it is read
// only when it is there. A build server sets HAZEL_SIGNING_DIR in the environment instead
// and writes the keystore and signing.properties into that folder before building.
val localProps = Properties().apply {
    rootProject.file("local.properties")
        .takeIf { it.exists() }
        ?.inputStream()
        ?.use { load(it) }
}
val signingDirPath: String? = localProps.getProperty("HAZEL_SIGNING_DIR")
    ?: System.getenv("HAZEL_SIGNING_DIR")

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

// The version this build reports. A tag build passes the tag through -PVERSION_NAME, so a
// release is named by the tag that produced it rather than by whatever this file last said.
val hazelVersionName: String =
    (project.findProperty("VERSION_NAME") as String?)
        ?.trim()
        ?.removePrefix("v")
        ?.takeIf { it.isNotBlank() }
        ?: "1.0.0"

// Whether to package one APK per architecture. On by default, since that is what a release
// publishes, and turned off for a build that only has to prove the code compiles.
val splitAbi: Boolean =
    (project.findProperty("SPLIT_ABI") as String?)?.toBooleanStrictOrNull() ?: true

// The word that goes in the APK name next to the version. A pre-release version carries a
// suffix after the number, and anything with one is a beta as far as a downloader cares.
// Debug builds override this, because there the build type says more than the version does.
val hazelChannel: String = if (hazelVersionName.contains('-')) "beta" else "stable"

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

        // Date-based versionCode: YYYYMMDDNN, which never exhausts and always increases
        val date = LocalDate.now()
        // Clamped to the two digits the code reserves for it, so a build number nobody
        // expected cannot roll over into the day.
        val buildNum = (project.findProperty("BUILD_NUM")?.toString()?.toIntOrNull() ?: 1)
            .coerceIn(1, 99)
        versionCode = date.year * 1_000_000 + date.monthValue * 10_000 +
                date.dayOfMonth * 100 + buildNum

        versionName = hazelVersionName

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

    // Five APKs take five times as long to package, which is worth it for a release and
    // wasted on a check nobody installs. A check passes -PSPLIT_ABI=false and gets one
    // universal APK instead.
    splits {
        abi {
            isEnable = splitAbi
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

// ── APK file names ──
//
// Left alone, every output lands as app-<abi>-<buildType>.apk, which says nothing about
// what it is once several of them share a downloads folder. Named instead for the app, the
// version, the architecture and the channel:
//
//   Hazel-v1.0.0-arm64-v8a-stable.apk
//   Hazel-v1.0.0-universal-stable.apk
//   Hazel-v1.1.0-beta.1-arm64-v8a-beta.apk
//   Hazel-v1.0.0-arm64-v8a-debug.apk
//
// The rename goes through VariantOutputImpl because the public VariantOutput carries the
// ABI filter but not the file name. The cast is safe rather than forced, so a plugin
// version that drops it gives back the default names instead of failing the build.
androidComponents {
    onVariants { variant ->
        val channel = if (variant.buildType == "debug") "debug" else hazelChannel
        variant.outputs.forEach { output ->
            val abi = output.filters
                .firstOrNull { it.filterType == FilterConfiguration.FilterType.ABI }
                ?.identifier
                ?: "universal"
            (output as? com.android.build.api.variant.impl.VariantOutputImpl)
                ?.outputFileName
                ?.set("Hazel-v$hazelVersionName-$abi-$channel.apk")
        }
    }
}

// AGP 9.x built-in Kotlin: compilerOptions at top level
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

    // Download engine: yt-dlp plus FFmpeg for Android
    implementation(libs.youtubedl.android.library)
    implementation(libs.youtubedl.android.ffmpeg)

    // Networking (URL validation + yt-dlp release metadata)
    implementation(libs.okhttp)

    // Metadata-only extractor. Used to list what is behind a link on the sites it knows,
    // where it answers in one request instead of a yt-dlp process. It never resolves
    // formats and never downloads: yt-dlp owns both, and this falls back to it on any
    // failure, so a stale extractor costs speed rather than function.
    implementation(libs.newpipe.extractor)

    // Unit tests, covering the two pure parts worth pinning: the link key and the metadata
    // parser fed saved engine payloads. kotlin-test brings the JUnit runner with it.
    // org.json is the real implementation, because the one the Android stubs provide throws
    // on every call rather than parsing anything.
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.json)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // In-app browser (Chrome Custom Tabs)
    implementation(libs.androidx.browser)

    // Thumbnail loading for fetched media
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

}
