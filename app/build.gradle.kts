import com.android.build.api.variant.FilterConfiguration
import java.util.Properties

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

// ── Versions ──
//
// Both the name and the code are written as literals in defaultConfig below, and that is a
// requirement rather than a style.
//
// F-Droid reads them out of this file with a regular expression and never runs Gradle to do
// it, so anything computed is not a value it can see. Holding them in gradle.properties, as
// this once did, left their update check reporting "Couldn't find any version information"
// and failing the build. The same is true of reading them from a property: a build argument
// is not in the file either.
//
// tools/release/release.ps1 rewrites both when a release is cut, so nothing here is edited by hand.
//
// The code is a plain counter with two digits kept free at the end for the architecture. Its
// only rule is that it increases; it says nothing about the version a person reads.

// The name of the release, taken from defaultConfig so there is one copy of it. Nothing on
// the release path overrides it: CI and the F-Droid server both build with the version as
// it stands in this file, which is what keeps their two APKs in agreement.
val hazelVersionName: String by lazy { android.defaultConfig.versionName ?: "1.0.0" }

// The release's own code, without the architecture digits. Read back from defaultConfig for
// the same reason the name is: one copy of the number, and that copy is the literal F-Droid
// can read out of the file.
val hazelBaseVersionCode: Int by lazy { android.defaultConfig.versionCode ?: 100 }

// The architecture numbers.
//
// A device offered several APKs installs the one with the highest code it can run, so this
// order is the choice. Universal is absent and therefore zero, which puts it below all of
// them: it is the fallback for a device none of the others fit. Each 64-bit entry sits
// above the 32-bit build it is also capable of running, or a 64-bit phone would be handed
// the 32-bit APK for having the larger number.
val abiVersionCodes = mapOf(
    "armeabi-v7a" to 1,
    "x86" to 2,
    "x86_64" to 3,
    "arm64-v8a" to 4
)

// Whether to package one APK per architecture. On by default, since that is what a release
// publishes, and turned off for a build that only has to prove the code compiles.
val splitAbi: Boolean =
    (project.findProperty("SPLIT_ABI") as String?)?.toBooleanStrictOrNull() ?: true

// The word that goes in the APK name next to the version. A pre-release version carries a
// suffix after the number, and anything with one is a beta as far as a downloader cares.
// Debug builds override this, because there the build type says more than the version does.
val hazelChannel: String by lazy {
    if (hazelVersionName.contains('-')) "beta" else "stable"
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

        // The release's own code, with the architecture digits left at zero. This is what
        // the universal APK keeps and what a build with the splits turned off reports;
        // every per-architecture output replaces it further down.
        versionCode = 1100
        versionName = "1.1.13"
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

    flavorDimensions += "distribution"

    productFlavors {
        create("github") {
            dimension = "distribution"
            isDefault = true
            buildConfigField("boolean", "IS_FDROID", "false")
            buildConfigField("String", "DISTRIBUTION_FLAVOR", "\"github\"")
        }
        create("fdroid") {
            dimension = "distribution"
            buildConfigField("boolean", "IS_FDROID", "true")
            buildConfigField("String", "DISTRIBUTION_FLAVOR", "\"fdroid\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
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
    // F-Droid's scanner rejects APKs that carry the Gradle dependency-metadata block
    // inside the APK Signing Block. AGP injects it by default; turning it off keeps the
    // binary APKs clean and reproducible.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true

            // Ship this one prebuilt exactly as the dependency provides it.
            //
            // AGP strips the debug symbols out of bundled native libraries using the strip
            // tool from whichever NDK the machine happens to have, and the result differs
            // between NDK versions. That is invisible until someone rebuilds the app and
            // compares: F-Droid does exactly that, and it was the only file out of the nine
            // in the APK that failed to match, at 7784 bytes here against the 10360 the
            // dependency ships.
            //
            // Named rather than "**/*.so" on purpose. The engine binaries are tens of
            // megabytes and strip is worth keeping for them; they already reproduce.
            keepDebugSymbols += "**/libdatastore_shared_counter.so"
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
        val flavor = variant.flavorName ?: "github"
        variant.outputs.forEach { output ->
            val abi = output.filters
                .firstOrNull { it.filterType == FilterConfiguration.FilterType.ABI }
                ?.identifier
                ?: "universal"

            // Each architecture gets its own code, in the one place that already knows
            // which architecture this output is for.
            output.versionCode.set(hazelBaseVersionCode + (abiVersionCodes[abi] ?: 0))

            val apkName = if (flavor == "github") {
                "Hazel-v$hazelVersionName-$abi-$channel.apk"
            } else {
                "Hazel-v$hazelVersionName-$flavor-$abi-$channel.apk"
            }

            (output as? com.android.build.api.variant.impl.VariantOutputImpl)
                ?.outputFileName
                ?.set(apkName)
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

    // Fast reader for listing, search and playback streams on the sites it knows. It never
    // downloads, and every use falls back to yt-dlp, so a stale extractor costs speed rather
    // than function. Only download/extractor/newpipe touches its API.
    implementation(libs.newpipe.extractor)

    // Playback of search results and links, and the cut preview. Only download/playback
    // builds players from it.
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.ui)

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

// Forward compatibility: map legacy testDebugUnitTest to flavor test tasks for CI and tooling
tasks.register("testDebugUnitTest") {
    dependsOn("testGithubDebugUnitTest", "testFdroidDebugUnitTest")
    description = "Runs unit tests for all debug variants."
    group = "verification"
}
