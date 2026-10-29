package com.nuvio.tv.petalix

import com.nuvio.tv.petalix.ui2.*
import com.nuvio.tv.domain.model.TmdbCollectionSourceType
import org.junit.Assert.*
import org.junit.Test

class Petalix2CatalogTest {
    private val catalog=javaClass.getResource("/petalix2-services.json")!!.readText()
    @Test fun everyServiceKeepsItsOwnProviderAndExcludesRentalOnlyContent() {
        assertEquals(10,PetalixServices.all.size)
        PetalixServices.all.forEach { service ->
            val sources=ServiceCatalogs.parse(catalog,service)
            assertTrue(service.id,sources.isNotEmpty())
            sources.forEach { source ->
                assertEquals(service.provider,source.filters.withWatchProviders)
                assertEquals("US",source.filters.watchRegion)
                assertEquals("flatrate|free|ads",source.filters.watchMonetizationTypes)
                assertEquals(TmdbCollectionSourceType.DISCOVER,source.sourceType)
            }
        }
    }
    @Test fun disneyFranchisesRemainServiceFiltered() {
        val sources=ServiceCatalogs.parse(catalog,PetalixServices.find("disney")!!)
        assertEquals("3",sources.first {it.title=="Pixar Favorites"}.filters.withCompanies)
        assertEquals("420",sources.first {it.title=="Marvel Studios Movies"}.filters.withCompanies)
        assertTrue(sources.none { it.title=="Star Wars Saga" })
    }
    @Test fun liveCategoryHandoffIsConsumedOnce() {
        PetalixCategoryLaunch.request("Sports")
        assertEquals("Sports",PetalixCategoryLaunch.consume())
        assertNull(PetalixCategoryLaunch.consume())
        assertTrue(PetalixCategoryLaunch.matches("Sports","USA | SPORTS"))
        assertFalse(PetalixCategoryLaunch.matches("Sports","USA | NEWS"))
        assertTrue(PetalixCategoryLaunch.matches("Kids","Children & Family"))
    }
    @Test fun navigationHasMoviesAndShowsWithoutOnDemand() {
        assertEquals(listOf("Home","Live TV","Movies","TV Shows","Search","Settings"),petalixTabs.map { it.label })
        assertEquals(6,petalixTabs.map {it.route}.distinct().size)
    }
}
