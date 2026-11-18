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
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.io.File
import javax.inject.Inject

/** Real movie and TV charts; saved successful responses survive offline launches. */
@HiltViewModel
class PetalixHomeCatalogViewModel @Inject constructor(
    private val api: TmdbApi, @ApplicationContext context: Context,
    private val liveRepository: com.nuvio.tv.livetv.data.LiveTvRepository,
    private val livePreferences: com.nuvio.tv.livetv.data.LiveTvPreferences,
    private val channels: com.nuvio.tv.livetv.data.LiveTvChannelStore,
    private val homePreferences: PetalixHomePreferences
) : ViewModel() {
    private val _rows = MutableStateFlow<List<CatalogRow>>(emptyList())
    val rows = _rows.asStateFlow()
    val homeLayout = homePreferences.layout
    private val disk = File(context.filesDir, "petalix-home-charts-v2.json")
    private val cached = JSONObject()
    private var restored = false
    private var loading: Job? = null
    private val failedAt = mutableMapOf<String, Long>()
    private var liveArtworkJob: Job? = null
    private var liveArtworkAt = 0L
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
            publish()
            val key = PetalixRuntimeConfig.apiKey
            if (homeLayout.value.showLiveTv && liveArtworkJob?.isActive != true && System.currentTimeMillis() - liveArtworkAt >= 10 * 60_000L) {
                liveArtworkJob = viewModelScope.launch(Dispatchers.IO) { refreshLiveArtwork(key) }
            }
            if (key.isBlank()) return@launch
            // Sequential, bounded requests avoid a burst against the API on every TV startup.
            for (chart in PetalixCatalogPolicy.charts.filter { it.path in homeLayout.value.charts }) {
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

    private suspend fun refreshLiveArtwork(key: String) {
        try {
            val user = livePreferences.userState.first()
            val localKeys = user.customGroups.firstOrNull { it.id == com.nuvio.tv.livetv.model.ChannelGroup.CUSTOM_PREFIX + "petalix_locals" }?.channelKeys.orEmpty().toSet()
            val now = System.currentTimeMillis()
            val shift = livePreferences.settings.first().epgOffsetMinutes * 60_000L
            val next = PetalixLiveArtwork.images.value.toMutableMap()
            var found = false
            for (shortcut in petalixHomeShortcuts) {
                val matching = channels.displayChannels.value.filter { channel ->
                    when (shortcut.slug) {
                        "locals" -> channel.key in localKeys && petalixLocalStationCandidate(channel.name, channel.group)
                        "sports" -> Regex("(?i)\\b(sports?|espn|nfl|nba|nhl|mlb)\\b").containsMatchIn(channel.group + " " + channel.name)
                        "news" -> Regex("(?i)\\b(news|cnn|msnbc|cnbc)\\b").containsMatchIn(channel.group + " " + channel.name)
                        else -> Regex("(?i)entertainment|movies|drama|comedy").containsMatchIn(channel.group)
                    }
                }.take(8)
                liveRepository.loadChannelsNow(matching.map { it.key })
                val program = matching.asSequence().mapNotNull { channel ->
                    liveRepository.programs.value[channel.key]?.firstOrNull {
                        now - shift >= it.startMs && now - shift < it.stopMs &&
                            !com.nuvio.tv.livetv.data.LiveTvRepository.isPlaceholderTitle(it.title)
                    }
                }.firstOrNull() ?: continue
                var image = program.icon?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
                if (key.isNotBlank()) {
                    try {
                        val media = if (program.category?.contains("movie", true) == true) "movie" else "tv"
                        val response = withTimeout(8_000) { api.petalixSearch(media, key, program.title, 1) }
                        val body = response.body()?.use { JSONObject(it.string()) }
                        response.errorBody()?.close()
                        val results = body?.optJSONArray("results")
                        val exact = results?.let { array -> (0 until array.length()).mapNotNull { array.optJSONObject(it) }
                            .firstOrNull { result -> listOf("name", "title", "original_name", "original_title").any {
                                result.optString(it).equals(program.title, true)
                            } } }
                        val backdrop = exact?.optString("backdrop_path")
                        if (!backdrop.isNullOrBlank() && backdrop.startsWith("/")) image = "https://image.tmdb.org/t/p/w1280$backdrop"
                    } catch (e: TimeoutCancellationException) { }
                      catch (e: CancellationException) { throw e }
                      catch (_: Exception) { }
                }
                image?.let { next[shortcut.slug] = it; found = true }
            }
            if (found) { PetalixLiveArtwork.images.value = next; liveArtworkAt = if (next.size == petalixHomeShortcuts.size) now else 0L }
        } catch (e: CancellationException) { throw e }
          catch (_: Exception) { }
    }

    private fun publish() {
        fun image(path: String, size: String) = path.takeIf { it.startsWith("/") }?.let { "https://image.tmdb.org/t/p/$size$it" }
        _rows.value = PetalixCatalogPolicy.charts.filter { it.path in homeLayout.value.charts }.mapNotNull { chart ->
            val results = cached.optJSONObject(chart.path)?.optJSONObject("data")?.optJSONArray("results") ?: return@mapNotNull null
            val items = (0 until results.length()).mapNotNull item@ { index ->
                val item = results.optJSONObject(index) ?: return@item null
                val title = item.optString(if (chart.tv) "name" else "title")
                if (item.optBoolean("adult") || title.isBlank() || item.optInt("id") <= 0) return@item null
                MetaPreview(id = "tmdb:${item.getInt("id")}", type = if (chart.tv) ContentType.SERIES else ContentType.MOVIE,
                    name = title, poster = image(item.optString("poster_path"), "w500"), posterShape = PosterShape.POSTER,
                    background = image(item.optString("backdrop_path"), "w1280"), logo = null,
                    description = item.optString("overview"), releaseInfo = item.optString(if (chart.tv) "first_air_date" else "release_date"),
                    // Preserve the chart rating with its actual provider; never call it IMDb.
                    imdbRating = null, genres = emptyList(),
                    mdbListRatings = item.optDouble("vote_average").takeIf {
                        item.optInt("vote_count") > 0 && it.isFinite() && it > 0.0 && it <= 10.0
                    }?.let { MDBListRatings(tmdb = it) },
                    rawPosterUrl = image(item.optString("poster_path"), "w500"))
            }
            if (items.isEmpty()) null else CatalogRow(addonId = "tmdb", addonName = "TMDB", addonBaseUrl = "",
                catalogId = "petalix-home-${chart.path}", catalogName = chart.title,
                type = if (chart.tv) ContentType.SERIES else ContentType.MOVIE,
                items = items.take(20), hasMore = false)
        }
    }
}
