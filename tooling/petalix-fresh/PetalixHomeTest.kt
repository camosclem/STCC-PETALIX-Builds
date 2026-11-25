package com.nuvio.tv.petalix

import com.nuvio.tv.domain.model.Addon
import com.nuvio.tv.domain.model.AddonCatalogCollectionSource
import com.nuvio.tv.domain.model.CatalogDescriptor
import com.nuvio.tv.domain.model.CollectionFolder
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.livetv.model.ChannelGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PetalixHomeTest {
    @Test fun liveTilesUseNativeGroupsAndPreserveSavedLocals() {
        val locals = ChannelGroup(LOCALS_GROUP, "My Locals", 5)
        val news = ChannelGroup("news-id", "USA | News", 18)
        val locked = ChannelGroup("locked-id", "News & Weather", 9, locked = true)
        assertEquals(9, liveCollection().folders.size)
        assertEquals(locals, guideGroup("locals", listOf(ChannelGroup("other", "Locals", 20), locals)))
        assertNull(guideGroup("locals", listOf(ChannelGroup("other", "Locals", 20))))
        assertEquals(news, guideGroup("news", listOf(locked, news)))
    }

    @Test fun netflixChartsComeFromAnInstalledMatchingCatalog() {
        val netflix = CollectionFolder(id = "folder-WFGY1XPD", title = "Netflix")
        val catalogue = Addon(
            id = "fixture.chart",
            name = "Ranks",
            version = "1.0",
            description = null,
            logo = null,
            baseUrl = "https://example.invalid",
            catalogs = listOf(
                CatalogDescriptor(type = ContentType.MOVIE, id = "tx.us.netflix.movie", name = "Netflix Top 10"),
                CatalogDescriptor(type = ContentType.MOVIE, id = "tx.us.disney.movie", name = "Disney Top 10")
            ),
            types = listOf(ContentType.MOVIE),
            resources = emptyList()
        )
        assertEquals(netflix, withServiceCharts(netflix, emptyList()))
        assertEquals(netflix, withServiceCharts(netflix, listOf(catalogue.copy(enabled = false))))
        val configured = withServiceCharts(netflix, listOf(catalogue))
        assertEquals(
            listOf(AddonCatalogCollectionSource("fixture.chart", "movie", "tx.us.netflix.movie")),
            configured.sources
        )
        assertEquals(configured, withServiceCharts(configured, listOf(catalogue)))
    }
}
