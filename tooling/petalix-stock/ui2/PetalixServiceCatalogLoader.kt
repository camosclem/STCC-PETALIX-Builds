package com.nuvio.tv.petalix.ui2

import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.TmdbCollectionSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout

internal data class PetalixServiceCatalogPage(
    val rows: List<CatalogRow>,
    val failures: List<String>
) {
    fun message(service: String, emptyIsError: Boolean = true): String? = when {
        failures.isNotEmpty() -> "$service: ${failures.distinct().joinToString("; ")}. Select Retry."
        emptyIsError && rows.none { it.items.isNotEmpty() } -> "$service returned no US streaming titles for these collections. Select Retry."
        else -> null
    }
}

/** Empty successful collections and failed requests are different states; neither is a blank success. */
internal suspend fun loadPetalixServiceCatalog(
    sources: List<TmdbCollectionSource>,
    page: Int,
    timeoutMs: Long = 25_000L,
    resolve: suspend (TmdbCollectionSource, Int) -> NetworkResult<CatalogRow>
): PetalixServiceCatalogPage = supervisorScope {
    val requests = Semaphore(3)
    val results = sources.map { source -> async {
        source.title to requests.withPermit {
            try { withTimeout(timeoutMs) { resolve(source, page) } }
            catch (_: TimeoutCancellationException) { NetworkResult.Error("Request timed out") }
            catch (cancel: CancellationException) { throw cancel }
            // Never show a raw URL/exception that could include the runtime metadata key.
            catch (_: Exception) { NetworkResult.Error("Connection failed") }
        }
    } }.awaitAll()
    PetalixServiceCatalogPage(
        rows = results.mapNotNull { (_, result) -> (result as? NetworkResult.Success)?.data },
        failures = results.mapNotNull { (title, result) -> when (result) {
            is NetworkResult.Error -> "$title: ${result.message}"
            NetworkResult.Loading -> "$title: Request did not complete"
            is NetworkResult.Success -> null
        } }
    )
}

/** Keep prior titles on a failed retry, and include a collection that first succeeds on a later page. */
internal fun mergePetalixServiceRows(
    previous: List<CatalogRow>, incoming: List<CatalogRow>, next: Boolean
): List<CatalogRow> {
    if (!next) return incoming.filter { it.items.isNotEmpty() }.ifEmpty { previous }
    val merged = previous.toMutableList()
    for (row in incoming.filter { it.items.isNotEmpty() }) {
        val index = merged.indexOfFirst { it.catalogId == row.catalogId && it.type == row.type }
        if (index < 0) merged += row
        else merged[index] = row.copy(items = (merged[index].items + row.items).distinctBy { "${it.apiType}:${it.id}" })
    }
    return merged
}
