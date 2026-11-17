package com.nuvio.tv.petalix

import com.nuvio.tv.domain.model.TmdbCollectionMediaType
import com.nuvio.tv.petalix.ui2.PetalixServices
import com.nuvio.tv.petalix.ui2.ServiceCatalogs
import org.junit.Assert.*
import org.junit.Test

class PetalixBritBoxSourcesTest {
    @Test fun britBoxRouteAndFallbackQueriesRemainServiceSpecificAndTvFirst() {
        val service = PetalixServices.findFolder("folder-petalix-britbox")!!
        assertEquals("britbox", service.id)
        val sources = service.sources()
        assertEquals(TmdbCollectionMediaType.TV, sources.first().mediaType)
        assertTrue(sources.all { it.filters.watchRegion == "US" && it.filters.withWatchProviders == "151|1852|197" })
        assertTrue(sources.all { it.filters.watchMonetizationTypes == "flatrate|free|ads" })
    }

    @Test fun bundledQueriesCannotBroadenBritBoxToOtherServices() {
        val json = """[{"folders":[{"id":"folder-petalix-britbox","sources":[
            {"title":"Popular Shows","tmdbSourceType":"DISCOVER","mediaType":"TV","filters":{"withWatchProviders":"8","watchRegion":"GB"}}
        ]}]}]"""
        val query = ServiceCatalogs.parse(json, PetalixServices.find("britbox")!!).single()
        assertEquals("151|1852|197", query.filters.withWatchProviders)
        assertEquals("US", query.filters.watchRegion)
        assertEquals(TmdbCollectionMediaType.TV, query.mediaType)
    }
}
