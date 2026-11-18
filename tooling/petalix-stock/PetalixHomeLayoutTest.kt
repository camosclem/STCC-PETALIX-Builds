package com.nuvio.tv.petalix

import org.junit.Assert.*
import org.junit.Test

class PetalixHomeLayoutTest {
    @Test fun defaultSectionsAreServicesAndTheTwoDailyTrendingCharts() {
        val layout = PetalixHomeLayout.from(emptyMap())
        assertTrue(layout.showLiveTv)
        assertTrue(layout.showRecentLiveTv)
        assertTrue(layout.showServices)
        assertEquals(setOf("trending/movie/day", "trending/tv/day"), layout.charts)
    }

    @Test fun explicitPanelPreferencesCanChangeEachSection() {
        val layout = PetalixHomeLayout.from(mapOf("petalix_home_live_tv" to true,
            "petalix_home_services" to false, "petalix_home_trending_movies" to false,
            "petalix_home_trending_tv" to false, "petalix_home_popular_movies" to true))
        assertTrue(layout.showLiveTv)
        assertFalse(layout.showServices)
        assertEquals(setOf("movie/popular"), layout.charts)
        assertEquals(PetalixHomeLayout(), PetalixHomeLayout.from(emptyMap()))
    }

    @Test fun artworkOverridesAcceptHttpsAndIgnoreInvalidInput() {
        val layout = PetalixHomeLayout.from(mapOf("petalix_home_service_artwork" to
            """{"folder-petalix-britbox":"https://images.example/britbox.jpg","folder-bad":"file:///private","folder-login":"https://user:password@example.test/art.jpg"}"""))
        assertEquals(mapOf("folder-petalix-britbox" to "https://images.example/britbox.jpg"), layout.serviceArtwork)
        assertTrue(PetalixHomeLayout.from(mapOf("petalix_home_service_artwork" to "broken json")).serviceArtwork.isEmpty())
    }
    @Test fun recentChannelsStayAvailableWhenCategoryTilesAreHidden() {
        val layout = PetalixHomeLayout.from(mapOf("petalix_home_live_tv" to false))
        assertFalse(layout.showLiveTv)
        assertTrue(layout.showRecentLiveTv)
        assertFalse(PetalixHomeLayout.from(mapOf("petalix_home_recent_live_tv" to false)).showRecentLiveTv)
    }

}
