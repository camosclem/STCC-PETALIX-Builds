package com.openguide.tv

import java.util.Locale

/** Only identifies candidate alternates within the user's own playlist.
 * Do not group completely unrelated channels based on league/network alone. */
object FeedMatcher {
    data class Feed(val name: String, val url: String)
    private val decorations = Regex("(?i)\\b(?:UHD|FULL\\s*HD|FHD|HD|SD|4K|HEVC|H\\.?265|BACKUP|ALT(?:ERNATE)?|SERVER|SOURCE|FEED|LINK|USA|US|UK|CA)\\b|\\[[^]]*]|\\([^)]*\\)")
    fun key(name: String): String = name.uppercase(Locale.US)
        .replace(Regex("(?i)\\b(?:FEED|LINK|ALT|BACKUP|SOURCE|SERVER)\\s*#?\\d+\\b")," ")
        .replace(decorations, " ")
        .replace(Regex("[^A-Z0-9]+"), " ").trim()
    fun candidates(primary: Feed, available: List<Feed>, max: Int = 8): List<Feed> {
        val wanted = key(primary.name)
        if (wanted.length < 4) return listOf(primary)
        return (listOf(primary) + available.filter { key(it.name) == wanted })
            .filter { it.url.startsWith("https://", true) || it.url.startsWith("http://", true) }
            .distinctBy { it.url }.take(max)
    }
}
