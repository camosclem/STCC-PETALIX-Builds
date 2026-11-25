package com.nuvio.tv.petalix

import com.nuvio.tv.domain.model.Addon
import com.nuvio.tv.domain.model.AddonCatalogCollectionSource
import com.nuvio.tv.domain.model.Collection
import com.nuvio.tv.domain.model.CollectionFolder
import com.nuvio.tv.domain.model.PosterShape
import com.nuvio.tv.livetv.model.ChannelGroup

internal const val LOCALS_GROUP = "custom:petalix_locals"
internal const val LIVE_COLLECTION = "petalix-home-live-categories"
internal const val SERVICES_COLLECTION = "collection-streaming-services-only"
internal const val LIVE_CATEGORY_ARGUMENT = "petalix.home.live.category"

private data class LiveTile(val id: String, val title: String, val art: String, val groups: List<String>)

private val liveTiles = listOf(
    LiveTile("locals", "My Locals", "locals.webp", listOf("My Locals", "Locals", "Local TV")),
    LiveTile("entertainment", "Entertainment", "entertainment.webp", listOf("Entertainment")),
    LiveTile("news", "News & Weather", "news.webp", listOf("News & Weather", "News", "Weather")),
    LiveTile("sports", "Sports Networks", "sports.webp", listOf("Sports Networks", "Sports")),
    LiveTile("movies", "Movie Night", "live/westerns.jpg", listOf("Movie Night", "Movies")),
    LiveTile("kids", "Kids & Family", "disney.webp", listOf("Kids & Family", "Kids", "Children")),
    LiveTile("lifestyle", "Home & Living", "home.webp", listOf("Home & Living", "Lifestyle")),
    LiveTile("documentaries", "Docs & Discovery", "live/around-clock.jpg", listOf("Docs & Discovery", "Documentaries")),
    LiveTile("music", "Music Mix", "live/entertainment.jpg", listOf("Music Mix", "Music"))
)

internal fun liveCollection() = Collection(
    id = LIVE_COLLECTION,
    title = "Live TV",
    pinToTop = true,
    folders = liveTiles.map { tile ->
        val art = "file:///android_asset/petalix/home-art/${tile.art}"
        CollectionFolder(
            id = tile.id,
            title = tile.title,
            tileShape = PosterShape.LANDSCAPE,
            coverImageUrl = art,
            heroBackdropUrl = art,
            focusGifEnabled = false
        )
    }
)

private val categoryPrefix = Regex("""(?i)^(?:[^\p{L}\p{N}]+|(?:US|USA)\s*[|:\-]\s*)+""")

internal fun guideGroup(tileId: String, groups: List<ChannelGroup>): ChannelGroup? {
    if (tileId == "locals") return groups.firstOrNull {
        it.id == LOCALS_GROUP && !it.locked && it.count > 0
    }
    val tile = liveTiles.firstOrNull { it.id == tileId } ?: return null
    return tile.groups.firstNotNullOfOrNull { name ->
        groups.firstOrNull { group ->
            !group.locked && group.count > 0 && group.title.replaceFirst(categoryPrefix, "").trim().equals(name, true)
        }
    }
}

private val services = mapOf(
    "folder-WFGY1XPD" to "netflix",
    "folder-AT8KYHZO" to "amazon-prime",
    "folder-8ZBZOMW9" to "disney",
    "folder-LN9SPMQN" to "hulu",
    "folder-OA4T8NYO" to "hbo-max",
    "folder-IQTF27K8" to "apple-tv",
    "folder-9YQK1ZF1" to "peacock",
    "folder-ILJM8PD0" to "paramount-plus",
    "folder-petalix-britbox" to "britbox"
)

internal fun withServiceCharts(folder: CollectionFolder, addons: List<Addon>): CollectionFolder {
    val service = services[folder.id] ?: return folder
    val charts = addons.asSequence().filter { it.enabled }.flatMap { addon ->
        addon.catalogs.asSequence().filter { catalog ->
            val knownChart = catalog.id == "tx.us.$service.movie" || catalog.id == "tx.us.$service.series"
            val type = catalog.apiType.lowercase()
            type in setOf("movie", "series") && knownChart &&
                catalog.extra.none { it.isRequired && it.name != "skip" }
        }.map { catalog ->
            AddonCatalogCollectionSource(addon.id, catalog.apiType, catalog.id)
        }
    }.distinctBy { it.type }.toList()
    if (charts.isEmpty()) return folder
    val existing = folder.sources.filterIsInstance<AddonCatalogCollectionSource>()
    val additions = charts.filter { chart ->
        existing.none { it.type == chart.type && it.catalogId == chart.catalogId }
    }
    return folder.copy(sources = additions + folder.sources)
}
