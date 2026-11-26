package com.hazel.android.gallery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GalleryProfilesTest {

    @Test
    fun profilesAreToldFromPostsAndAThirdSectionDropsTheFirst() {
        assertEquals(GalleryProfile(ProfileSite.INSTAGRAM, "aryan.k"), GalleryProfiles.detect("https://www.instagram.com/aryan.k/?igsh=x"))
        assertEquals(GalleryProfile(ProfileSite.X, "nasa"), GalleryProfiles.detect("https://twitter.com/nasa"))
        assertNull(GalleryProfiles.detect("https://www.instagram.com/p/Df1R8Gaklh0/"))
        assertNull(GalleryProfiles.detect("https://www.instagram.com/explore/"))
        assertNull(GalleryProfiles.detect("https://x.com/nasa/status/1"))

        val two = listOf(ProfileSection.IG_STORIES, ProfileSection.IG_POSTS)
        assertEquals(listOf(ProfileSection.IG_POSTS, ProfileSection.IG_REELS), GalleryProfiles.toggle(two, ProfileSection.IG_REELS))
        assertEquals(listOf(ProfileSection.IG_POSTS), GalleryProfiles.toggle(listOf(ProfileSection.IG_POSTS), ProfileSection.IG_POSTS))
    }
}
