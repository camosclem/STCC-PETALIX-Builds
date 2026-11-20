package com.nuvio.tv.petalix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PetalixArtworkTest {
    @Test
    fun upgradesTmdbArtworkAtTheRequestedDisplaySize() {
        val image = "https://image.tmdb.org/t/p/w342/art.jpg"
        assertEquals("https://image.tmdb.org/t/p/w780/art.jpg", petalixArtwork(image, 780))
        assertEquals("https://image.tmdb.org/t/p/w1280/art.jpg", petalixArtwork(image, 1280))
        assertEquals(image, petalixArtwork(image, 185))
    }

    @Test
    fun keepsOriginalAndExternalArtwork() {
        val original = "https://image.tmdb.org/t/p/original/art.jpg"
        val custom = "https://poster.example/logo.jpg?tmdb=https://image.tmdb.org/t/p/w342/art.jpg"
        assertEquals(original, petalixArtwork(original, 1280))
        assertEquals(custom, petalixArtwork(custom, 1280))
        assertNull(petalixArtwork(" ", 1280))
    }

    @Test
    fun guideFiltersTinyImagesAndKeepsPortraitsOutOfTheWidePreview() {
        assertEquals(PetalixGuideArtworkType.LANDSCAPE, petalixGuideArtworkType(1280, 720))
        assertEquals(PetalixGuideArtworkType.POSTER, petalixGuideArtworkType(480, 720))
        assertNull(petalixGuideArtworkType(320, 180))
        assertNull(petalixGuideArtworkType(300, 300))
    }
}
