package com.nuvio.tv.petalix

private val roundTheClockGroup = Regex("""\b(?:24 7|247|24 seven)\b""")
private val eventGroup = Regex("""\b(?:events?|games?|teams?|replays?|ppv|pay per view|sunday ticket|league pass|center ice|extra innings|nfl|nba|wnba|mlb|nhl|ncaa\w*|ncaaf|ncaab|college|soccer|football|basketball|baseball|hockey|cricket|rugby|ufc|boxing|mma|motorsports?|formula 1|f1|nascar|premier league|epl|uefa|champions league|la liga|bundesliga|serie a|mls|wwe|aew)\b""")
private val streamingFeed = Regex("""\b(?:fubo|disney plus|espn plus|espnplus|espn3|espn 3|espn extra|peacock|paramount plus|hbo max|max|btn plus|btnplus|b1g plus|big ten plus|dazn|apple tv|amazon|prime video|fanatiz|flo ?sports?|fite|triller|bleacher report|b r sports|nfl plus|nba league pass|mlb tv|nhl tv)\b""")
private val fixture = Regex("""\b(?:vs|versus)\b""")
// A complete network name is required: "ESPN 2" qualifies; "ESPN 2: Celtics vs Heat" does not.
private val network = Regex("""^(?:espn(?: ?[12]| ?news| ?u| deportes)?|espnews|fox sports ?[12]|fs ?[12]|cbs sports(?: network)?|cbssn|nbc sports(?: network| bay area| boston| california| chicago| philadelphia| washington)?|nbcsn|nfl network|nfl red ?zone|red ?zone|nba tv|mlb network|nhl network|golf(?: channel)?|tennis(?: channel)?|big ten network|btn|sec(?: network|n)?|acc(?: network|n)?|fanduel(?: sports(?: network)?)?(?: [1-5]| [a-z ]+)?|bally sports(?: [a-z ]+)?|tsn ?[1-5]?|sportsnet(?: one| ontario| east| west| pacific| 360)?|sny|yes(?: network)?|nesn(?: plus)?|msg(?: plus| ?[12])?|marquee sports(?: network)?|altitude(?: sports)?|root sports(?: northwest)?|space city home network|sportstime ohio|tnt|tbs|tru ?tv)$""")
private val sportsGroup = Regex("""\b(?:sports?|sportsnet|espn|fox sports|fs1|fs2|golf|tennis|fanduel|tsn)\b""")
private val punctuation = Regex("[^a-z0-9]+")
private val region = Regex("^(?:(?:us|usa|ca|canada|uk) )+")
private val networkQuality = Regex("""\b(?:fhd|uhd|hd|sd|4k|8k|hevc|h ?26[45]|1080[pi]?|720p?|2160p?|backup|bkp|east|west|us|usa|ca|canada|uk|60fps|30fps)\b""")
private val whitespace = Regex("\\s+")

private fun words(value: String): String = value.lowercase(java.util.Locale.ROOT).replace("+", " plus ")
    .replace(punctuation, " ").trim().replace(region, "")

/** Presentation only. The channel store, EPG, Sports Hub and playback keep the complete feed pool. */
internal fun hideSportsEventInGuide(group: String, name: String): Boolean {
    val groupWords = words(group)
    if (roundTheClockGroup.containsMatchIn(groupWords)) return false
    val channelWords = words(name)
    if (streamingFeed.containsMatchIn(groupWords)) return true
    val cleanName = channelWords.replace(networkQuality, " ").replace(whitespace, " ").trim()
    val cleanGroup = groupWords.replace(networkQuality, " ").replace(whitespace, " ").trim()
    if (eventGroup.containsMatchIn(groupWords) && !network.matches(cleanGroup)) return true
    val sports = sportsGroup.containsMatchIn(groupWords) || network.matches(cleanGroup)
    if (!sports) return false
    return streamingFeed.containsMatchIn(channelWords) || fixture.containsMatchIn(channelWords) || !network.matches(cleanName)
}

private val videoQuality = Regex("""\s+(?:fhd|uhd|hd|sd|4k)$""")

internal fun isRepeatedGuideTitle(channelName: String, programme: String?): Boolean {
    if (programme.isNullOrBlank()) return false
    val channel = words(channelName).replace(videoQuality, "")
    return channel.isNotBlank() && channel == words(programme).replace(videoQuality, "")
}
