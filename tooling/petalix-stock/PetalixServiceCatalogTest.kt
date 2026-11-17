package com.nuvio.tv.petalix

import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.domain.model.*
import com.nuvio.tv.petalix.ui2.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class PetalixServiceCatalogTest {
    private val source = TmdbCollectionSource(TmdbCollectionSourceType.DISCOVER, "Popular Shows",
        mediaType = TmdbCollectionMediaType.TV,
        filters = TmdbCollectionFilters(watchRegion = "US", withWatchProviders = "151|1852|197"))
    private fun row(id: String, titles: List<String>) = CatalogRow(
        addonId = "tmdb", addonName = "TMDB", addonBaseUrl = "", catalogId = id,
        catalogName = id, type = ContentType.SERIES, hasMore = false,
        items = titles.map { MetaPreview(id = it, type = ContentType.SERIES, name = it,
            poster = null, posterShape = PosterShape.POSTER, background = null, logo = null,
            description = null, releaseInfo = null, imdbRating = null, genres = emptyList()) }
    )

    @Test fun emptySuccessfulBritBoxQueriesAreNotReportedAsLoaded() = runBlocking {
        val result = loadPetalixServiceCatalog(listOf(source), 1) { _, _ -> NetworkResult.Success(row("shows", emptyList())) }
        assertTrue(result.failures.isEmpty())
        assertTrue(result.message("BritBox")!!.contains("no US streaming titles"))
    }

    @Test fun catalogErrorRetainsTheFailedCollectionAndHttpStatus() = runBlocking {
        val result = loadPetalixServiceCatalog(listOf(source, source.copy(title = "Popular Movies")), 1) { query, _ ->
            if (query.title == "Popular Shows") NetworkResult.Success(row("shows", listOf("Vera")))
            else NetworkResult.Error("Metadata request failed (HTTP 503)")
        }
        assertEquals(listOf("Vera"), result.rows.single().items.map { it.name })
        assertTrue(result.message("BritBox")!!.contains("Popular Movies: Metadata request failed (HTTP 503)"))
    }

    @Test fun stalledQueryHasAnActionableTimeout() = runBlocking {
        val result = loadPetalixServiceCatalog(listOf(source), 1, timeoutMs = 10) { _, _ ->
            delay(1_000); NetworkResult.Success(row("shows", emptyList()))
        }
        assertTrue(result.message("BritBox")!!.contains("Request timed out"))
    }

    @Test fun cancellingTheScreenCancelsItsCatalogRequests() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val request = async {
            loadPetalixServiceCatalog(listOf(source), 1) { _, _ ->
                started.complete(Unit); awaitCancellation()
            }
        }
        started.await()
        request.cancelAndJoin()
        assertTrue(request.isCancelled)
    }

    @Test fun retryRetainsVisibleTitlesAndPaginationCanRecoverAMissingRow() {
        val prior = listOf(row("shows", listOf("Vera")))
        assertEquals(prior, mergePetalixServiceRows(prior, emptyList(), false))
        val result = mergePetalixServiceRows(prior,
            listOf(row("shows", listOf("Vera", "Shetland")), row("new", listOf("Blue Lights"))), true)
        assertEquals(listOf("Vera", "Shetland"), result.first().items.map { it.name })
        assertEquals(listOf("shows", "new"), result.map { it.catalogId })
    }
}
