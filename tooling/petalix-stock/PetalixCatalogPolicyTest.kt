package com.nuvio.tv.petalix

import org.junit.Assert.*
import org.junit.Test

class PetalixCatalogPolicyTest {
    @Test fun `daily charts include movies and series and do not imply Netflix view counts`() {
        assertEquals("trending/movie/day", PetalixCatalogPolicy.charts[0].path)
        assertEquals("trending/tv/day", PetalixCatalogPolicy.charts[1].path)
        assertTrue(PetalixCatalogPolicy.charts[1].tv)
        assertTrue(PetalixCatalogPolicy.charts.none { it.title.contains("Netflix") || it.path.contains("upcoming") })
        assertEquals(listOf("Trending Movies Today", "Trending TV Today"),
            PetalixCatalogPolicy.charts.filter { it.defaultEnabled }.map { it.title })
    }
    @Test fun `fresh data avoids repeat calls and failed requests back off`() {
        val now = 10_000_000L
        assertFalse(PetalixCatalogPolicy.shouldRefresh(now, now - 60_000, 0))
        assertTrue(PetalixCatalogPolicy.shouldRefresh(now, now - 31 * 60_000, 0))
        assertFalse(PetalixCatalogPolicy.shouldRefresh(now, 0, now - 60_000))
        assertTrue(PetalixCatalogPolicy.shouldRefresh(now, 0, now - 6 * 60_000))
        assertTrue(PetalixCatalogPolicy.shouldRefresh(now, now + 60_000, 0))
    }
}
