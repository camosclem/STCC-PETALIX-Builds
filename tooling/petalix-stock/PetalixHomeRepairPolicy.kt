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

/** The rendered Home rows, kept separate so regressions cannot silently drop Live TV again. */
internal fun petalixRepairedHomeRows(
    source: List<com.nuvio.tv.ui.screens.home.HomeRow>,
    charts: List<com.nuvio.tv.domain.model.CatalogRow>
): List<com.nuvio.tv.ui.screens.home.HomeRow> {
    val native = source.filterIsInstance<com.nuvio.tv.ui.screens.home.HomeRow.CollectionRow>()
        .filter { it.collection.id != PETALIX_LIVE_SHORTCUT_COLLECTION }
    return listOf(com.nuvio.tv.ui.screens.home.HomeRow.CollectionRow(petalixHomeLiveCollection())) + native +
        charts.map { com.nuvio.tv.ui.screens.home.HomeRow.Catalog(it) }
}
