package com.hazel.android.gallery

import androidx.annotation.StringRes
import com.hazel.android.R

/** Sites whose profile links can be read section by section. */
enum class ProfileSite(val label: String, val home: String) {
    INSTAGRAM("Instagram", "https://www.instagram.com/"),
    X("X", "https://x.com/")
}

/**
 * One part of a profile gallery-dl can read on its own, by its own link. [key] is what the
 * saved setting holds; [signIn] sections are shown only to signed-in accounts.
 */
enum class ProfileSection(
    val site: ProfileSite,
    val key: String,
    @param:StringRes val label: Int,
    val signIn: Boolean = false
) {
    IG_STORIES(ProfileSite.INSTAGRAM, "stories", R.string.profile_stories, signIn = true),
    IG_HIGHLIGHTS(ProfileSite.INSTAGRAM, "highlights", R.string.profile_highlights, signIn = true),
    IG_POSTS(ProfileSite.INSTAGRAM, "posts", R.string.profile_posts),
    IG_REELS(ProfileSite.INSTAGRAM, "reels", R.string.profile_reels),
    IG_TAGGED(ProfileSite.INSTAGRAM, "tagged", R.string.profile_tagged),
    X_MEDIA(ProfileSite.X, "media", R.string.profile_media),
    X_REPLIES(ProfileSite.X, "with_replies", R.string.profile_posts_replies),
    X_LIKES(ProfileSite.X, "likes", R.string.profile_likes, signIn = true);

    /** The link gallery-dl reads this section of [user] from. */
    fun link(user: String): String = when (this) {
        IG_STORIES -> "https://www.instagram.com/stories/$user/"
        IG_HIGHLIGHTS, IG_POSTS, IG_REELS, IG_TAGGED -> "https://www.instagram.com/$user/$key/"
        X_MEDIA, X_REPLIES, X_LIKES -> "https://x.com/$user/$key"
    }

    companion object {
        fun of(site: ProfileSite): List<ProfileSection> = entries.filter { it.site == site }

        fun defaults(site: ProfileSite): List<ProfileSection> =
            listOf(if (site == ProfileSite.INSTAGRAM) IG_POSTS else X_MEDIA)

        /** The saved sections of [site] from their keys, in the site's order; the default when none. */
        fun parse(site: ProfileSite, saved: String?): List<ProfileSection> {
            val keys = saved.orEmpty().split(',').map { it.trim() }.toSet()
            return of(site).filter { it.key in keys }.take(GalleryProfiles.MAX_SECTIONS).ifEmpty { defaults(site) }
        }

        fun keys(sections: List<ProfileSection>): String = sections.joinToString(",") { it.key }
    }
}

/** A person's profile page on [site], named by [user]. */
data class GalleryProfile(val site: ProfileSite, val user: String) {
    /** The address of the profile itself, for its sign-in. */
    val home: String get() = when (site) {
        ProfileSite.INSTAGRAM -> "https://www.instagram.com/$user/"
        ProfileSite.X -> "https://x.com/$user"
    }
}

object GalleryProfiles {

    /** Sections read at once at most: each is another request, and these sites limit how many. */
    const val MAX_SECTIONS = 2

    private val INSTAGRAM = Regex("""^https?://(?:www\.|m\.)?instagram\.com/([A-Za-z0-9._]{1,30})/?(?:[?#].*)?$""", RegexOption.IGNORE_CASE)
    private val X = Regex("""^https?://(?:www\.|mobile\.)?(?:x|twitter)\.com/([A-Za-z0-9_]{1,15})/?(?:[?#].*)?$""", RegexOption.IGNORE_CASE)

    private val INSTAGRAM_PAGES = setOf(
        "p", "reel", "reels", "tv", "stories", "explore", "accounts", "direct", "about",
        "developer", "legal", "web", "s", "emails", "challenge", "privacy", "terms"
    )
    private val X_PAGES = setOf(
        "home", "explore", "search", "i", "settings", "notifications", "messages", "compose",
        "hashtag", "login", "logout", "signup", "tos", "privacy", "share", "intent", "jobs"
    )

    /** The profile [url] points at, or null for a post, a section, or any other page. */
    fun detect(url: String): GalleryProfile? {
        val link = url.trim()
        INSTAGRAM.matchEntire(link)?.groupValues?.get(1)?.let { user ->
            return if (user.lowercase() in INSTAGRAM_PAGES) null else GalleryProfile(ProfileSite.INSTAGRAM, user)
        }
        X.matchEntire(link)?.groupValues?.get(1)?.let { user ->
            return if (user.lowercase() in X_PAGES) null else GalleryProfile(ProfileSite.X, user)
        }
        return null
    }

    /**
     * [chosen] with [section] switched on or off. On adds it, and drops the earliest choice
     * once more than [MAX_SECTIONS] would be on; the last section left can't be switched off.
     */
    fun toggle(chosen: List<ProfileSection>, section: ProfileSection): List<ProfileSection> =
        if (section in chosen) chosen.takeIf { it.size == 1 } ?: (chosen - section)
        else (chosen + section).takeLast(MAX_SECTIONS)
}
