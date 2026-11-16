package com.nuvio.tv.ui.screens.home

import org.junit.Assert.*
import org.junit.Test

class PetalixCategoryHeroTest {
    private val service = ModernHeroSceneState("netflix-art", null, true, true, true,
        "service-trailer", "service-audio", "service-key", true, true)

    @Test fun eachCategoryReplacesStaleServiceArtAndMedia() {
        val artwork = listOf("Sports" to "sports", "News" to "news",
            "Entertainment" to "entertainment", "My Locals" to "locals")
        for ((title, art) in artwork) {
            val scene = petalixCategoryHero(service, title)
            assertEquals("file:///android_asset/petalix/home-art/$art.webp", scene.heroBackdrop)
            assertEquals(title, scene.preview?.title)
            assertNull(scene.preview?.logo)
            assertFalse(scene.shouldPlayTrailer)
            assertFalse(scene.enrichmentActive)
            assertNull(scene.trailerUrl)
            assertNull(scene.trailerPlaybackKey)
        }
    }
    @Test fun leavingCategoryRestoresTheOriginalServiceScene() {
        assertSame(service, petalixCategoryHero(service, null))
        assertSame(service, petalixCategoryHero(service, "unknown"))
    }
}
