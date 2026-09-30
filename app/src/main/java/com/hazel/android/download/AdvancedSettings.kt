package com.hazel.android.download

import com.yausername.youtubedl_android.YoutubeDLRequest
import java.util.Locale

/**
 * Engine options for sources that need more than the defaults: YouTube's player clients and
 * proof-of-origin tokens, a pause between requests for a site that rate limits, and raw
 * arguments for anything the app has no setting for.
 *
 * Every one of these is passed to yt-dlp as it reads it (checked against yt-dlp 2026.08's
 * own option parsing), and every one is off or empty by default, so a user who never opens
 * the screen gets exactly the requests they always did.
 */
data class AdvancedSettings(
    /** YouTube player clients to ask, in order. Empty leaves the choice to yt-dlp. */
    val playerClients: List<String> = emptyList(),
    /** PO tokens as yt-dlp takes them: `CLIENT.CONTEXT+TOKEN`, several separated by commas. */
    val poTokens: String = "",
    /** The visitor data the PO tokens were made for. */
    val visitorData: String = "",
    /** YouTube's titles and descriptions in the app's language, where YouTube has it. */
    val metadataInAppLanguage: Boolean = false,
    /** More YouTube extractor arguments, `key=value;key=value`, for anything not above. */
    val youtubeExtraArgs: String = "",
    /** Extra yt-dlp arguments added to every download, as they would be typed. */
    val downloadExtraArgs: String = "",
    /** `--no-check-certificates`, for a site whose certificate the device does not trust. */
    val noCheckCertificates: Boolean = false,
    /** `--sleep-requests`: seconds between requests while reading, for rate limited sites. */
    val sleepRequestsSeconds: Int = 0
) {
    /**
     * The `--extractor-args` value for YouTube, or null when nothing is set. Built as one
     * value because yt-dlp keeps only the last `youtube:` it is given.
     */
    fun youtubeExtractorArgs(appLanguage: String = Locale.getDefault().toLanguageTag()): String? {
        val parts = buildList {
            val clients = playerClients.filter { it in PLAYER_CLIENTS || it == "default" }
            if (clients.isNotEmpty()) add("player_client=${clients.joinToString(",")}")
            val tokens = validPoTokens()
            if (tokens.isNotEmpty()) add("po_token=${tokens.joinToString(",")}")
            visitorData.trim().takeIf { it.isNotEmpty() && ';' !in it }?.let { add("visitor_data=$it") }
            if (metadataInAppLanguage) youtubeLanguage(appLanguage)?.let { add("lang=$it") }
            youtubeExtraArgs.split(';').map { it.trim() }
                .filter { '=' in it && !it.startsWith("=") }
                .forEach { add(it) }
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(";", prefix = "youtube:")
    }

    /**
     * The tokens that are in the form yt-dlp accepts. A token it cannot read is skipped by
     * yt-dlp with a warning; dropping it here keeps the request clean, and the screen says
     * which ones were not taken.
     */
    fun validPoTokens(): List<String> =
        poTokens.split(',', '\n').map { it.trim() }.filter { PO_TOKEN_FORMAT.matches(it) }

    /** The extra download arguments as a list, with the ones the app relies on removed. */
    fun downloadArguments(): List<String> =
        splitArguments(downloadExtraArgs).let(::withoutReservedOptions)

    companion object {
        /**
         * YouTube player clients yt-dlp 2026.08 knows. A name it does not know is skipped
         * with a warning, so the list only has to be current, not complete.
         */
        val PLAYER_CLIENTS = listOf(
            "web", "web_safari", "web_embedded", "web_music", "web_creator", "mweb",
            "android", "android_vr", "ios", "visionos", "tv", "tv_downgraded", "tv_simply"
        )

        /** `CLIENT.CONTEXT+TOKEN`, the context being gvs, player or subs. */
        private val PO_TOKEN_FORMAT = Regex("""^[a-z_]+\.(gvs|player|subs)\+[A-Za-z0-9_\-=%]+$""")

        /**
         * The language codes YouTube accepts for its metadata, as yt-dlp lists them. It
         * refuses the whole read for any other code, so only one of these is ever sent.
         */
        private val YOUTUBE_LANGUAGES = setOf(
            "af", "az", "id", "ms", "bs", "ca", "cs", "da", "de", "et", "en-IN", "en-GB", "en",
            "es", "es-419", "es-US", "eu", "fil", "fr", "fr-CA", "gl", "hr", "zu", "is", "it", "sw",
            "lv", "lt", "hu", "nl", "no", "uz", "pl", "pt-PT", "pt", "ro", "sq", "sk", "sl",
            "sr-Latn", "fi", "sv", "vi", "tr", "be", "bg", "ky", "kk", "mk", "mn", "ru", "sr", "uk",
            "el", "hy", "iw", "ur", "ar", "fa", "ne", "mr", "hi", "as", "bn", "pa", "gu", "or",
            "ta", "te", "kn", "ml", "si", "th", "lo", "my", "ka", "am", "km", "zh-CN", "zh-TW",
            "zh-HK", "ja", "ko"
        )

        /**
         * The YouTube code for an app language tag: the tag itself where YouTube has it,
         * then the language alone, with the few codes Android and YouTube write differently
         * ("in" and "id", "he" and "iw", Brazilian Portuguese as plain "pt"). Null when
         * YouTube has none.
         */
        fun youtubeLanguage(tag: String): String? {
            val locale = Locale.forLanguageTag(tag.replace('_', '-'))
            val language = when (locale.language) {
                "in" -> "id"
                "he" -> "iw"
                "nb", "nn" -> "no"
                else -> locale.language
            }
            val region = locale.country
            val candidates = listOfNotNull(
                region.takeIf { it.isNotBlank() }?.let { "$language-$it" },
                if (language == "zh") "zh-CN" else null,
                language
            )
            return candidates.firstOrNull { it in YOUTUBE_LANGUAGES }
        }

        /**
         * Options the app sets itself and reads the output of. Given again from here they
         * would move the file where the app cannot find it, or change the output the progress
         * is read from, so they are dropped with their values.
         */
        private val RESERVED = setOf(
            "-o", "--output", "-P", "--paths", "--load-info-json", "-a", "--batch-file",
            "-q", "--quiet", "--no-progress", "-s", "--simulate", "--skip-download",
            "-j", "--dump-json", "-J", "--dump-single-json", "-O", "--print", "--print-to-file",
            "--newline", "--progress-template", "--cookies", "--cookies-from-browser"
        )

        /**
         * Splits a line of arguments the way a shell would: on spaces, keeping quoted text
         * together and taking a backslash to escape the next character.
         */
        fun splitArguments(line: String): List<String> {
            val out = mutableListOf<String>()
            val current = StringBuilder()
            var quote: Char? = null
            var escaped = false
            var inToken = false
            for (c in line) {
                when {
                    escaped -> { current.append(c); escaped = false; inToken = true }
                    c == '\\' && quote != '\'' -> escaped = true
                    quote != null -> if (c == quote) quote = null else current.append(c)
                    c == '"' || c == '\'' -> { quote = c; inToken = true }
                    c.isWhitespace() -> if (inToken) {
                        out += current.toString(); current.clear(); inToken = false
                    }
                    else -> { current.append(c); inToken = true }
                }
            }
            if (inToken) out += current.toString()
            return out
        }

        private fun withoutReservedOptions(args: List<String>): List<String> {
            val out = mutableListOf<String>()
            var skipValue = false
            for (arg in args) {
                if (skipValue) {
                    skipValue = false
                    if (!arg.startsWith("-")) continue
                }
                val name = arg.substringBefore('=')
                if (name in RESERVED) {
                    // The value is dropped with its option, whether it came as "--opt value"
                    // or "--opt=value".
                    skipValue = '=' !in arg
                    continue
                }
                out += arg
            }
            return out
        }
    }
}

/**
 * Applies the advanced settings to a request. Called beside [applySiteAccess] on every read
 * and every download, so the two always ask the site the same way.
 */
fun YoutubeDLRequest.applyAdvanced(url: String, settings: AdvancedSettings = AdvancedSettingsStore.current) {
    if (settings.noCheckCertificates) addOption("--no-check-certificates")
    if (settings.sleepRequestsSeconds > 0) {
        addOption("--sleep-requests", settings.sleepRequestsSeconds.toString())
    }
    if (isYouTube(url) || url.contains("music.youtube.com")) {
        settings.youtubeExtractorArgs()?.let { addOption("--extractor-args", it) }
    }
}

/**
 * Adds the extra download arguments. Kept apart from [applyAdvanced] because they belong to
 * downloads only, and go last, so that one of them can change what the app set before it.
 */
fun YoutubeDLRequest.applyExtraDownloadArguments(settings: AdvancedSettings = AdvancedSettingsStore.current) {
    val args = settings.downloadArguments()
    var i = 0
    while (i < args.size) {
        val arg = args[i]
        val next = args.getOrNull(i + 1)
        if (arg.startsWith("-") && next != null && !next.startsWith("-")) {
            addOption(arg, next)
            i += 2
        } else {
            addOption(arg)
            i += 1
        }
    }
}

/**
 * The advanced settings in force, kept in memory so a request can read them without waiting
 * on storage. Filled from storage as the app starts and on every change.
 */
object AdvancedSettingsStore {
    @Volatile
    var current: AdvancedSettings = AdvancedSettings()
        internal set
}
