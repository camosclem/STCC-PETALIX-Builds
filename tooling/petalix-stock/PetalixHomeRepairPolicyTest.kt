package com.nuvio.tv.petalix

import org.junit.Assert.*
import org.junit.Test

class PetalixHomeRepairPolicyTest {
    @Test fun genericReplayFeedsDoNotBecomeSpecificGames() {
        for (league in listOf("NFL", "NHL", "NBA", "MLB")) {
            assertFalse(PetalixHomeRepairPolicy.matchesReplay("$league Replay 1", null, listOf("Bruins", "BOS"), listOf("Canadiens", "MTL")))
        }
        assertTrue(PetalixHomeRepairPolicy.matchesReplay("NHL Replay", "Bruins vs Canadiens", listOf("Bruins"), listOf("Canadiens")))
        assertFalse(PetalixHomeRepairPolicy.matchesReplay("NHL Replay", "Bruins vs Rangers", listOf("Bruins"), listOf("Canadiens")))
        assertTrue(PetalixHomeRepairPolicy.matchesReplay("ESPN", "SportsCenter", emptyList(), emptyList()))
    }
    @Test fun absentLandscapeArtFallsBackToPoster() {
        assertEquals("poster", PetalixHomeRepairPolicy.thumbnail("", "poster"))
        assertEquals("backdrop", PetalixHomeRepairPolicy.thumbnail("backdrop", "poster"))
    }
    @Test fun actualRenderedRowOrderingKeepsLiveTvBeforeServices() {
        val keys = listOf(PETALIX_SERVICES_ROW, "trending", "collection_$PETALIX_LIVE_SHORTCUT_COLLECTION")
        val ordered = petalixServicesFirst(keys) { it }
        assertEquals(listOf("collection_$PETALIX_LIVE_SHORTCUT_COLLECTION", PETALIX_SERVICES_ROW, "trending"), ordered)
    }
    @Test fun defaultHomeStartsWithServicesAndOmitsLiveShortcuts() {
        val service = com.nuvio.tv.domain.model.Collection(id = PETALIX_SERVICES_COLLECTION, title = "Streaming Services")
        val rendered = petalixRepairedHomeRows(listOf(com.nuvio.tv.ui.screens.home.HomeRow.CollectionRow(service)), emptyList())
        val collections = rendered.filterIsInstance<com.nuvio.tv.ui.screens.home.HomeRow.CollectionRow>()
        assertEquals(listOf(PETALIX_SERVICES_COLLECTION), collections.map { it.collection.id })
        val enabled = petalixRepairedHomeRows(emptyList(), emptyList(), PetalixHomeLayout(showLiveTv = true))
        val live = (enabled.single() as com.nuvio.tv.ui.screens.home.HomeRow.CollectionRow).collection
        assertEquals(listOf("My Locals", "Entertainment", "Sports", "News"), live.folders.map { it.title })
    }

    @Test fun homeUsesExistingChartTilesWithoutAdditionalProviderShelves() {
        val chart = com.nuvio.tv.domain.model.CatalogRow(addonId = "tmdb", addonName = "TMDB", addonBaseUrl = "",
            catalogId = "petalix-home-trending/movie/day", catalogName = "Trending Movies Today", type = com.nuvio.tv.domain.model.ContentType.MOVIE,
            items = emptyList())
        val rendered = petalixRepairedHomeRows(emptyList(), listOf(chart))
        val catalogs = rendered.filterIsInstance<com.nuvio.tv.ui.screens.home.HomeRow.Catalog>()
        assertEquals(listOf(chart), catalogs.map { it.row })
        val removed = chart.copy(catalogId = "petalix-home-movie/popular", catalogName = "Popular Movies")
        val filtered = petalixRepairedHomeRows(emptyList(), listOf(chart, removed))
            .filterIsInstance<com.nuvio.tv.ui.screens.home.HomeRow.Catalog>()
        assertEquals(listOf(chart), filtered.map { it.row })
    }

}
