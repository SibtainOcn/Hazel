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
// tools/release.ps1 rewrites both when a release is cut, so nothing here is edited by hand.
//
// The code is a plain counter with two digits kept free at the end for the architecture. Its
// only rule is that it increases; it says nothing about the version a person reads.

// The name of the release, taken from defaultConfig so there is one copy of it. Nothing on
// the release path overrides it: CI and the F-Droid server both build with the version as
// it stands in this file, which is what keeps their two APKs in agreement. -PVERSION_NAME
// stays only for regenerating the store changelogs of a version other than the current one.
val hazelVersionName: String by lazy {
    (project.findProperty("VERSION_NAME") as String?)
        ?.trim()
        ?.removePrefix("v")
        ?.takeIf { it.isNotBlank() }
        ?: android.defaultConfig.versionName
        ?: "1.0.0"
}

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
        versionCode = 300

        versionName = "1.0.6"

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

            // Each architecture gets its own code, in the one place that already knows
            // which architecture this output is for.
            output.versionCode.set(hazelBaseVersionCode + (abiVersionCodes[abi] ?: 0))

            (output as? com.android.build.api.variant.impl.VariantOutputImpl)
                ?.outputFileName
                ?.set("Hazel-v$hazelVersionName-$abi-$channel.apk")
        }
    }
}

// ── Fastlane changelogs ──
//
// F-Droid looks for a changelog named after the version code of the exact APK it is showing,
// so a release that publishes five APKs needs the same text under five different names. That
// is five files nobody can keep in step by hand, and a missing one is silent: the listing
// just shows no changelog for that architecture.
//
// So they are generated. The text is written once in the release's own section of
// CHANGELOG.md and this fans it out, which also means the changelog a person reads on
// F-Droid and the one in the repository cannot drift apart.
//
//   ./gradlew :app:generateFastlaneChangelogs -PVERSION_NAME=1.0.4
//
// A release section may carry a '### Store' subsection, and when it does that subsection is
// what gets published in place of the rest. The two audiences are not the same one: a
// release can be entirely build plumbing, which is worth recording here and says nothing to
// somebody reading a store listing, and the first listing has no earlier version to
// describe itself against at all. Everything outside '### Store' stays for readers of the
// repository.
//
// The markdown is flattened rather than rendered: F-Droid shows this as plain text, so bold
// markers and wrapped lines would arrive as literal asterisks and mid-sentence breaks.
// What the store listings will actually show before they cut it off.
val FASTLANE_CHANGELOG_LIMIT = 500

val fastlaneChangelogDir =
    rootProject.layout.projectDirectory.dir("fastlane/metadata/android/en-US/changelogs")
val rootChangelog = rootProject.layout.projectDirectory.file("CHANGELOG.md")

tasks.register("generateFastlaneChangelogs") {
    description = "Writes the current release's CHANGELOG.md section to one file per ABI."
    group = "publishing"

    val versionName = hazelVersionName
    val baseCode = hazelBaseVersionCode
    // Every code the release publishes, the universal APK's included.
    val codes = listOf(0) + abiVersionCodes.values.sorted()
    val source = rootChangelog
    val outDir = fastlaneChangelogDir

    inputs.file(source)
    outputs.dir(outDir)

    doLast {
        val text = source.asFile.readText()

        // The section runs from this version's heading to the next heading of any version.
        val heading = Regex("""^##\s*\[${Regex.escape(versionName)}]""", RegexOption.MULTILINE)
        val start = heading.find(text)
            ?: throw GradleException(
                "CHANGELOG.md has no section for $versionName. Add a '## [$versionName] - <date>' " +
                    "heading before cutting the release."
            )
        // From the line after the heading, so the date trailing the version does not
        // survive as a stray first bullet.
        val headingEnd = text.indexOf('\n', start.range.last + 1)
        val rest = if (headingEnd == -1) "" else text.substring(headingEnd + 1)
        val end = Regex("""^##\s*\[""", RegexOption.MULTILINE).find(rest)
        val body = if (end == null) rest else rest.substring(0, end.range.first)

        // '### Store', when the release has one, replaces the section rather than adding to
        // it: what a store listing shows and what the repository records are written for
        // different readers. It runs to the next '###' or to the end of the section.
        val storeStart = Regex("""^###\s+Store\s*$""", RegexOption.MULTILINE).find(body)
        val published = if (storeStart == null) body else {
            val lineEnd = body.indexOf('\n', storeStart.range.last)
            val after = if (lineEnd == -1) "" else body.substring(lineEnd + 1)
            val next = Regex("""^###\s""", RegexOption.MULTILINE).find(after)
            if (next == null) after else after.substring(0, next.range.first)
        }

        // Markdown to plain text, one bullet per line however the source wrapped it.
        val lines = mutableListOf<String>()
        published.trim().lines().forEach { raw ->
            val line = raw.trim()
            when {
                line.isEmpty() -> if (lines.isNotEmpty() && lines.last().isNotEmpty()) lines.add("")
                line.startsWith("###") -> {
                    if (lines.isNotEmpty() && lines.last().isNotEmpty()) lines.add("")
                    lines.add(line.removePrefix("###").trim())
                }
                line.startsWith("- ") || line.startsWith("* ") ->
                    lines.add("* " + line.drop(2).trim())
                // A continuation of the bullet above, rejoined onto it.
                lines.isNotEmpty() && lines.last().startsWith("* ") ->
                    lines[lines.lastIndex] = lines.last().trimEnd() + " " + line
                else -> lines.add(line)
            }
        }
        val plain = lines.joinToString("\n")
            .replace(Regex("""\*\*(.+?)\*\*"""), "$1")
            .replace(Regex("""`(.+?)`"""), "$1")
            .replace(Regex("""\[(.+?)]\((.+?)\)"""), "$1")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()

        if (plain.isBlank()) {
            throw GradleException("The $versionName section of CHANGELOG.md is empty.")
        }

        // F-Droid and IzzyOnDroid both truncate a long changelog in the listing, so a
        // release whose section reads as prose gets cut mid-sentence rather than rejected.
        // Warned about rather than enforced, because only a person can decide what to cut.
        if (plain.length > FASTLANE_CHANGELOG_LIMIT) {
            logger.warn(
                "The $versionName changelog is ${plain.length} characters, over the " +
                    "$FASTLANE_CHANGELOG_LIMIT the listings show. It will be truncated there. " +
                    "Shorten the $versionName section of CHANGELOG.md if that matters."
            )
        }

        val dir = outDir.asFile
        dir.mkdirs()
        // Codes from earlier releases are left alone: F-Droid still serves the versions
        // they belong to, and deleting them would blank the changelog on old builds.
        codes.forEach { abi ->
            dir.resolve("${baseCode + abi}.txt").writeText(plain + "\n")
        }
        logger.lifecycle(
            "Wrote $versionName to ${codes.size} changelogs: " +
                codes.joinToString(", ") { "${baseCode + it}.txt" }
        )
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
