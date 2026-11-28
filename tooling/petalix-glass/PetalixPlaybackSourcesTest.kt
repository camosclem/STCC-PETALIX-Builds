package com.nuvio.tv.petalix

import org.junit.Assert.*
import org.junit.Test

class PetalixPlaybackSourcesTest {
    private val url = "https://source.example/manifest.json"

    @Test fun cachedPenguIsExcludedAfterItsPanelAssignmentIsRemoved() {
        assertTrue(petalixPlaybackAddonAllowed("org.pengu.play", "Pengu Play", url, url))
        assertFalse(petalixPlaybackAddonAllowed("org.pengu.play", "Pengu Play", url, null))
        assertFalse(petalixPlaybackAddonAllowed("custom.source", "PENGU PLAY", url, ""))
        assertFalse(petalixPlaybackAddonAllowed("org.pengu", "Source", url, null))
    }

    @Test fun unrelatedUserAddonsAndExplicitAssignmentsRemainAvailable() {
        assertTrue(petalixPlaybackAddonAllowed("personal.addon", "My sources", url, null))
        assertTrue(petalixPlaybackAddonAllowed("personal.addon", "My sources", url, "https://other.example/manifest.json"))
        assertTrue(petalixPlaybackAddonAllowed("pengu-play", "Pengu Play", url, url))
        assertFalse(petalixPlaybackAddonAllowed("pengu-play", "Pengu Play", url, "https://other.example/manifest.json"))
    }
}
