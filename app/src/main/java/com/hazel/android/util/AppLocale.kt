package com.hazel.android.util

import android.app.LocaleManager
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * Which language the app is shown in.
 *
 * Nothing has to be chosen. With no answer stored, Android resolves every string against the
 * device language and picks the closest `values-xx` folder there is, falling back to English
 * where there is none. The picker exists for the person whose phone is in one language and
 * who wants the app in another, which is common enough on a shared or a work device.
 *
 * From Android 13 the platform owns this: the choice is stored by the system, appears in the
 * system settings beside every other app, and survives an uninstall of the app's own data.
 * Below that there is nowhere to put it but here, so the tag is written to preferences and
 * the activity's context is wrapped with it as it is built.
 *
 * Preferences rather than the DataStore the rest of the settings use, and deliberately so:
 * this is read in `attachBaseContext`, before anything can suspend, and DataStore has no
 * answer to give at that point. One value, read once per activity, is what SharedPreferences
 * is for.
 */
object AppLocale {

    private const val PREFS = "hazel_locale"
    private const val KEY_TAG = "language_tag"

    /** The empty tag, which means the device decides. */
    const val SYSTEM = ""

    /**
     * Every language the app ships, in the order the picker lists them.
     *
     * [endonym] is the language's name in itself, because a person looking for their own
     * language is looking for the word they would write, not the English for it. [english]
     * sits under it for anyone who arrived here by accident and needs to get back.
     */
    data class Language(val tag: String, val endonym: String, val english: String)

    val LANGUAGES: List<Language> = listOf(
        Language("en", "English", "English"),
        Language("es", "Español", "Spanish"),
        Language("zh-CN", "简体中文", "Chinese, Simplified"),
        Language("hi", "हिन्दी", "Hindi"),
        Language("pt-BR", "Português (Brasil)", "Portuguese, Brazil"),
        Language("fr", "Français", "French"),
        Language("ru", "Русский", "Russian"),
        Language("de", "Deutsch", "German"),
        Language("ja", "日本語", "Japanese"),
        Language("in", "Bahasa Indonesia", "Indonesian")
    )

    /** The stored tag, or [SYSTEM] when the device is deciding. */
    fun tag(context: Context): String {
        if (Build.VERSION.SDK_INT >= 33) {
            val system = context.getSystemService(LocaleManager::class.java)
                ?.applicationLocales
            if (system != null && !system.isEmpty) return system[0]?.toLanguageTag().orEmpty()
            return SYSTEM
        }
        return prefs(context).getString(KEY_TAG, SYSTEM).orEmpty()
    }

    /**
     * Records the choice.
     *
     * On Android 13 and later that is the whole of it: the platform re-creates what is on
     * screen in the new language by itself. Below that the caller re-creates the activity,
     * which is what makes the wrapping in [wrap] happen again.
     */
    fun set(context: Context, tag: String) {
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java)?.applicationLocales =
                if (tag.isBlank()) LocaleList.getEmptyLocaleList()
                else LocaleList.forLanguageTags(tag)
            return
        }
        prefs(context).edit().putString(KEY_TAG, tag).apply()
    }

    /**
     * The context an activity should actually use, which below Android 13 is the given one
     * with the chosen language applied.
     *
     * On 13 and later the platform has already done this, so the context arrives correct and
     * is handed straight back. Wrapping it again there would only fight the system over which
     * of two answers is the real one.
     */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return base

        val tag = prefs(base).getString(KEY_TAG, SYSTEM).orEmpty()
        if (tag.isBlank()) return base

        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)

        val config = android.content.res.Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return ContextWrapper(base.createConfigurationContext(config))
    }

    /** What to show as the current choice, for the row that opens the picker. */
    fun label(context: Context): String {
        val tag = tag(context)
        if (tag.isBlank()) return context.getString(com.hazel.android.R.string.language_system)
        return LANGUAGES.firstOrNull { it.tag.equals(tag, ignoreCase = true) }?.endonym
        // A tag the app does not ship, which the system settings can still produce.
            ?: Locale.forLanguageTag(tag).getDisplayName(Locale.forLanguageTag(tag))
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
