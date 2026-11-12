package com.nuvio.tv.petalix

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.data.remote.api.TmdbApi
import com.nuvio.tv.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import javax.inject.Inject

/** Actual chart endpoints, independent of installed addon and provider category names. */
@HiltViewModel
class PetalixHomeCatalogViewModel @Inject constructor(private val api: TmdbApi) : ViewModel() {
    private val _rows = MutableStateFlow<List<CatalogRow>>(emptyList())
    val rows = _rows.asStateFlow()
    init { load() }
    fun load() {
        viewModelScope.launch {
            val charts = listOf("Top Rated" to "movie/top_rated", "Trending" to "trending/movie/week",
                "Upcoming" to "movie/upcoming", "Popular" to "movie/popular", "Now Playing" to "movie/now_playing")
            kotlinx.coroutines.supervisorScope {
                charts.map { (title, path) -> async {
                    try {
                        val json = withTimeout(20_000) {
                            api.petalixHomeChart(path, PetalixRuntimeConfig.apiKey).body()?.use { JSONObject(it.string()) }
                        } ?: return@async
                        val results = json.optJSONArray("results") ?: return@async
                        fun image(value: String, size: String) = value.takeIf { it.startsWith("/") }?.let { "https://image.tmdb.org/t/p/$size$it" }
                        val items = (0 until results.length()).map { results.getJSONObject(it) }.filter { !it.optBoolean("adult") }.map { item ->
                            MetaPreview(id = "tmdb:${item.getInt("id")}", type = ContentType.MOVIE,
                                name = item.getString("title"), poster = image(item.optString("poster_path"), "w500"),
                                posterShape = PosterShape.POSTER, background = image(item.optString("backdrop_path"), "w1280"),
                                logo = null, description = item.optString("overview"), releaseInfo = item.optString("release_date"),
                                imdbRating = item.optDouble("vote_average").takeIf { it.isFinite() && it > 0 }?.toFloat(), genres = emptyList())
                        }
                        if (items.isNotEmpty()) {
                            val row = CatalogRow(addonId = "tmdb", addonName = "TMDB", addonBaseUrl = "",
                                catalogId = "petalix-home-$path", catalogName = title, type = ContentType.MOVIE,
                                items = items, hasMore = false)
                            // Show each shelf as soon as it arrives; a slow endpoint cannot hold the rest.
                            synchronized(_rows) {
                                val next = _rows.value.filterNot { it.catalogName == title } + row
                                _rows.value = charts.mapNotNull { chart -> next.firstOrNull { it.catalogName == chart.first } }
                            }
                        }
                    } catch (e: kotlinx.coroutines.TimeoutCancellationException) { }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { /* Preserve loaded rows; retry when Home opens again. */ }
                } }.awaitAll()
            }
        }
    }
}
