package com.nuvio.tv.petalix

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.data.remote.api.TmdbApi
import com.nuvio.tv.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File
import javax.inject.Inject

/** Real movie and TV charts; saved successful responses survive offline launches. */
@HiltViewModel
class PetalixHomeCatalogViewModel @Inject constructor(
    private val api: TmdbApi, @ApplicationContext context: Context
) : ViewModel() {
    private val _rows = MutableStateFlow<List<CatalogRow>>(emptyList())
    val rows = _rows.asStateFlow()
    private val disk = File(context.filesDir, "petalix-home-charts-v2.json")
    private val cached = JSONObject()
    private var restored = false
    private var loading: Job? = null
    private val failedAt = mutableMapOf<String, Long>()
    init { load() }

    fun load() {
        if (loading?.isActive == true) return
        loading = viewModelScope.launch(Dispatchers.IO) {
            if (!restored) {
                try {
                    val saved = JSONObject(disk.readText())
                    for (chart in PetalixCatalogPolicy.charts) saved.optJSONObject(chart.path)?.let { cached.put(chart.path, it) }
                    publish()
                } catch (_: Exception) { }
                restored = true
            }
            val key = PetalixRuntimeConfig.apiKey
            if (key.isBlank()) return@launch
            // Sequential, bounded requests avoid a burst against the API on every TV startup.
            for (chart in PetalixCatalogPolicy.charts) {
                val now = System.currentTimeMillis()
                val prior = cached.optJSONObject(chart.path)
                if (!PetalixCatalogPolicy.shouldRefresh(now, prior?.optLong("fetchedAt") ?: 0L,
                        failedAt[chart.path] ?: 0L)) continue
                try {
                    val json = withTimeout(20_000) {
                        val response = api.petalixHomeChart(chart.path, key)
                        if (!response.isSuccessful) { response.errorBody()?.close(); error("Chart request failed") }
                        response.body()?.use { JSONObject(it.string()) }
                    } ?: error("Empty chart")
                    require((json.optJSONArray("results")?.length() ?: 0) > 0)
                    cached.put(chart.path, JSONObject().put("fetchedAt", now).put("data", json))
                    failedAt.remove(chart.path)
                    publish()
                    // Atomic replacement leaves the last valid snapshot intact on interrupted writes.
                    val temporary = File(disk.parentFile, disk.name + ".tmp")
                    temporary.writeText(cached.toString())
                    check(temporary.renameTo(disk))
                } catch (e: TimeoutCancellationException) { failedAt[chart.path] = now }
                  catch (e: CancellationException) { throw e }
                  catch (_: Exception) { failedAt[chart.path] = now }
            }
        }
    }

    private fun publish() {
        fun image(path: String, size: String) = path.takeIf { it.startsWith("/") }?.let { "https://image.tmdb.org/t/p/$size$it" }
        _rows.value = PetalixCatalogPolicy.charts.mapNotNull { chart ->
            val results = cached.optJSONObject(chart.path)?.optJSONObject("data")?.optJSONArray("results") ?: return@mapNotNull null
            val items = (0 until results.length()).mapNotNull item@ { index ->
                val item = results.optJSONObject(index) ?: return@item null
                val title = item.optString(if (chart.tv) "name" else "title")
                if (item.optBoolean("adult") || title.isBlank() || item.optInt("id") <= 0) return@item null
                MetaPreview(id = "tmdb:${item.getInt("id")}", type = if (chart.tv) ContentType.SERIES else ContentType.MOVIE,
                    name = title, poster = image(item.optString("poster_path"), "w500"), posterShape = PosterShape.POSTER,
                    background = image(item.optString("backdrop_path"), "w1280"), logo = null,
                    description = item.optString("overview"), releaseInfo = item.optString(if (chart.tv) "first_air_date" else "release_date"),
                    // TMDB vote averages are not IMDb ratings. The native enrichment loads actual ratings.
                    imdbRating = null, genres = emptyList())
            }
            if (items.isEmpty()) null else CatalogRow(addonId = "tmdb", addonName = "TMDB", addonBaseUrl = "",
                catalogId = "petalix-home-${chart.path}", catalogName = chart.title,
                type = if (chart.tv) ContentType.SERIES else ContentType.MOVIE,
                items = items.take(20), hasMore = false)
        }
    }
}
