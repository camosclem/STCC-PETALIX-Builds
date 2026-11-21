package com.nuvio.tv.petalix

internal object ViewingLabels {
    private val brand = Regex("""(?i)^petalix(?: tv| iptv)?$""")
    private val brandPrefix = Regex("""(?i)^petalix(?: tv| iptv)?\s*[:|–—-]\s*""")
    private const val SLOT = """(?:NBA|WNBA|NFL|NHL|MLB|NCAAF|NCAAB(?: W)?|EPL|PPV|ESPN\+|Disney\+ USA|TSN\+|FOX ONE|Peacock Event|Fubo (?:US|Canada))\s*\d{1,3}"""
    private val slotPrefix = Regex("""(?i)^$SLOT\s*[:|–—-]\s*(?=\S)""")
    private val slotSuffix = Regex("""(?i)\s*[:|]\s*$SLOT\s*$""")
    private val categories = mapOf(
        "favorites" to "⭐ Favorites", "★  favorites" to "⭐ Favorites",
        "recently watched" to "🕘 Recently Watched", "my locals" to "📍 My Locals",
        "local tv" to "📍 Locals", "locals" to "📍 Locals", "entertainment" to "📺 Entertainment",
        "news" to "📰 News & Weather", "sports" to "🏟️ Sports Networks",
        "sports networks" to "🏟️ Sports Networks", "sports hub" to "🏆 Sports Hub",
        "movies" to "🎬 Movie Night", "kids & family" to "🧸 Kids & Family",
        "lifestyle" to "🏡 Home & Living", "documentaries" to "🌍 Docs & Discovery",
        "music" to "🎵 Music Mix", "streaming channels" to "📡 Streaming Channels",
        "all channels" to "📡 All Channels", "24/7" to "🕒 Always On · 24/7"
    )

    fun source(name: String?): String? = name?.trim()?.takeIf { it.isNotEmpty() && !brand.matches(it) }

    fun channel(raw: String): String {
        val name = raw.trim().replaceFirst(brandPrefix, "").ifBlank { raw }
        return name.replaceFirst(slotPrefix, "").replace(slotSuffix, "").trim().ifBlank { name.trimStart(':', ' ') }
    }

    fun category(raw: String): String {
        val name = raw.trim()
        return categories[name.lowercase(java.util.Locale.ROOT)]
            ?: if (name.startsWith("24/7 ")) "🕒 $name" else name
    }
}
