package com.nuvio.tv.petalix

internal object PetalixHomeRepairPolicy {
    private fun words(value: String) = value.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()
    fun matchesReplay(channel: String, programme: String?, home: List<String>, away: List<String>): Boolean {
        val text = words(channel + " " + programme.orEmpty())
        if (!Regex("\\b(replay|replays|rerun|re air)\\b").containsMatchIn(text)) return true
        fun identifies(names: List<String>) = names.map(::words).filter { it.length >= 3 }.any { " $text ".contains(" $it ") }
        return home.isNotEmpty() && away.isNotEmpty() && identifies(home) && identifies(away)
    }
    fun thumbnail(primary: String?, poster: String?): String? =
        primary?.takeIf { it.isNotBlank() } ?: poster?.takeIf { it.isNotBlank() }
}

/** Apply the chosen sections even to cached data from older Home builds. */
internal fun petalixRepairedHomeRows(
    source: List<com.nuvio.tv.ui.screens.home.HomeRow>,
    charts: List<com.nuvio.tv.domain.model.CatalogRow>,
    layout: PetalixHomeLayout = PetalixHomeLayout()
): List<com.nuvio.tv.ui.screens.home.HomeRow> {
    val native = source.filterIsInstance<com.nuvio.tv.ui.screens.home.HomeRow.CollectionRow>()
        .filter { layout.showServices && it.collection.id == PETALIX_SERVICES_COLLECTION }
    val live = if (layout.showLiveTv)
        listOf(com.nuvio.tv.ui.screens.home.HomeRow.CollectionRow(petalixHomeLiveCollection())) else emptyList()
    return live + native + charts.filter { it.catalogId.removePrefix("petalix-home-") in layout.charts }
        .map { com.nuvio.tv.ui.screens.home.HomeRow.Catalog(it) }
}
