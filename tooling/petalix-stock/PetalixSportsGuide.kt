package com.nuvio.tv.petalix

private val eventGroups = setOf(
    "sports events", "live sports events", "event feeds", "ppv sports",
    "fubo events", "disney plus events", "espn plus events", "peacock events",
    "paramount plus events", "dazn events", "sports event feeds"
)
private val eventSlot = Regex("""^(?:fubo(?: sports)?|disney plus|espn plus|peacock|paramount plus|dazn) (?:event |feed )?\d+\b""")
private val fixture = Regex("""\b(?:vs|versus)\b""")
private val network = Regex("""\b(?:espn(?: 2| news| u| deportes)?|fs ?[12]|cbs sports network|nbc sports|nfl network|nba tv|mlb network|nhl network|golf channel|tennis channel|fanduel sports network|tsn)\b""")
private val sportsGroup = Regex("""\b(?:sport|sports|fubo|disney plus|espn plus|dazn|ppv)\b""")
private val punctuation = Regex("[^a-z0-9]+")
private val region = Regex("^(?:us|usa|ca|canada|uk) ")

private fun words(value: String): String = value.lowercase().replace("+", " plus ")
    .replace(punctuation, " ").trim().replace(region, "")

internal fun hideSportsEventInGuide(group: String, name: String): Boolean {
    val groupWords = words(group)
    if (!sportsGroup.containsMatchIn(groupWords) && groupWords !in eventGroups) return false
    val channelWords = words(name)
    val hasFixture = fixture.containsMatchIn(channelWords)
    val isEventSlot = eventSlot.containsMatchIn(channelWords)
    val brandedNetwork = network.containsMatchIn(channelWords) && !channelWords.startsWith("espn plus")
    if (brandedNetwork && !hasFixture && !isEventSlot) return false
    return groupWords in eventGroups || hasFixture || isEventSlot
}

private val videoQuality = Regex("""\s+(?:fhd|uhd|hd|sd|4k)$""")

internal fun isRepeatedGuideTitle(channelName: String, programme: String?): Boolean {
    if (programme.isNullOrBlank()) return false
    val channel = words(channelName).replace(videoQuality, "")
    return channel.isNotBlank() && channel == words(programme).replace(videoQuality, "")
}
