package com.nuvio.tv.petalix

import com.nuvio.tv.livetv.model.LiveChannel

/**
 * Personal Petalix 11.1 guide curation; works on locally imported M3U and Xtream
 * playlists on every refresh. No account URLs, passwords, tokens or EPG data are
 * stored here or sent to the panel.
 *
 * IMPORTANT: Channel.key, tvgId, tvgName, url, headers, DRM and catch-up stay
 * unchanged. Alternative qualities move to Backup Streams instead of being
 * discarded, so Nuvio's alternate-feed selector can still reach them.
 */
internal object PetalixLineup {
    val categoryOrder = listOf(
        "Locals", "Entertainment", "News",
        "Sports | Major Networks", "Sports | FanDuel", "Sports | TSN+",
        "Sports | NFL", "Sports | NBA", "Sports | MLB", "Sports | NHL",
        "Sports | College", "Sports | Soccer", "Sports | Golf & Tennis",
        "Sports | Racing", "Sports | Combat", "Sports | Other",
        "Movies", "Kids & Family", "Lifestyle", "Documentaries", "Western", "Music",
        "24/7 | TV & Series", "24/7 | Movies", "24/7 | Kids",
        "24/7 | Music", "24/7 | Other", "Other USA", "Backup Streams"
    )
    private val mainNetworks = listOf(
        "ESPN", "ESPN2", "ESPNEWS", "ESPNU", "FS1", "FS2",
        "NFL NETWORK", "NFL REDZONE", "NBA TV", "MLB NETWORK", "NHL NETWORK",
        "CBS SPORTS NETWORK", "NBC SPORTS", "GOLF CHANNEL", "TENNIS CHANNEL",
        "BIG TEN NETWORK", "SEC NETWORK", "ACC NETWORK"
    )
    private val foreignGroup = Regex(
        """(?i)(?:^|[\s|:/-])(?:CANADA|CANADIAN|UK|UNITED KINGDOM|BRITAIN|INDIA|PAKISTAN|TURKEY|TURKISH|FRANCE|FRENCH|GERMANY|GERMAN|ARABIC|PORTUGAL|BRAZIL|SPAIN|ITALY|MEXICO|LATIN AMERICA)(?:$|[\s|:/-])"""
    )
    private val tsnPlus = Regex("""(?i)\bTSN\s*(?:\+|PLUS)(?=\W|$)""")
    private val major = Regex(
        """(?i)^(?:ESPN(?:\+|2|U|NEWS)?|FS[12]|FOX SPORTS [12]|NFL NETWORK|NFL REDZONE|NBA TV|MLB NETWORK|NHL NETWORK|CBS SPORTS(?: NETWORK)?|NBC SPORTS(?: NETWORK)?|GOLF CHANNEL|TENNIS CHANNEL|BIG TEN NETWORK|B1G NETWORK|SEC NETWORK|ACC NETWORK)(?:\b|\s|$)"""
    )
    private val sports = Regex(
        """(?i)\bSPORTS?\b|\b(?:ESPN|NFL|NBA|MLB|NHL|NCAA|UFC|PGA|NASCAR|FORMULA 1|F1|MLS|SOCCER|FOOTBALL|TENNIS|BOXING|MMA|GOLF|CRICKET|RACING|FANDUEL|TSN)\b"""
    )
    private val qualitySuffix = Regex(
        """(?i)(?:\s+|[|[])(?:UHD|FHD|HD|SD|4K|HEVC|H265|60FPS)\]?$"""
    )
    private val prefix = Regex("""(?i)^(?:US|USA)\s*[-:|]\s*""")

    fun cleanName(name: String): String {
        var n = prefix.replace(name.trim(), "")
        while (qualitySuffix.containsMatchIn(n)) n = qualitySuffix.replace(n, "").trim()
        return n.replace(Regex("""\s+"""), " ")
    }

    fun isForeign(group: String, name: String): Boolean =
        foreignGroup.containsMatchIn(group) && !tsnPlus.containsMatchIn(name + " " + group)

    fun category(group: String, name: String): String {
        val text = group + " " + name
        val title = cleanName(name)
        if (tsnPlus.containsMatchIn(text)) return "Sports | TSN+"
        if (Regex("""(?i)FAN\s*DUEL|FANDUEL|BALLY SPORTS""").containsMatchIn(text)) return "Sports | FanDuel"
        if (major.containsMatchIn(title)) return "Sports | Major Networks"
        if (sports.containsMatchIn(text)) {
            val leagues = listOf(
                "NFL" to """(?i)\bNFL\b|REDZONE|SUNDAY TICKET""",
                "NBA" to """(?i)\bNBA\b|LEAGUE PASS""",
                "MLB" to """(?i)\bMLB\b|BASEBALL""",
                "NHL" to """(?i)\bNHL\b|HOCKEY""",
                "College" to """(?i)\bNCAA\b|COLLEGE|BIG TEN|B1G|SEC NETWORK|ACC NETWORK""",
                "Soccer" to """(?i)\bMLS\b|SOCCER|PREMIER LEAGUE|LA LIGA|CHAMPIONS LEAGUE""",
                "Golf & Tennis" to """(?i)\bGOLF\b|\bPGA\b|TENNIS|ATP|WTA""",
                "Racing" to """(?i)\bNASCAR\b|FORMULA 1|\bF1\b|RACING|MOTOGP""",
                "Combat" to """(?i)\bUFC\b|BOXING|\bMMA\b|WRESTLING|\bWWE\b"""
            )
            return "Sports | " + (leagues.firstOrNull { Regex(it.second).containsMatchIn(text) }?.first ?: "Other")
        }
        if ("24/7" in text || "24 7" in text) {
            return "24/7 | " + when {
                Regex("""(?i)MOVIE|FILM|CINEMA""").containsMatchIn(text) -> "Movies"
                Regex("""(?i)KID|CARTOON|FAMILY|ANIME""").containsMatchIn(text) -> "Kids"
                Regex("""(?i)MUSIC|CONCERT|KARAOKE""").containsMatchIn(text) -> "Music"
                Regex("""(?i)DRAMA|CRIME|REALITY|COMEDY|SHOW|SERIES|CLASSIC|TV""").containsMatchIn(text) -> "TV & Series"
                else -> "Other"
            }
        }
        return when {
            Regex("""(?i)\bLOCALS?\b|AFFILIATE|CALL SIGN""").containsMatchIn(text) -> "Locals"
            Regex("""(?i)\bNEWS\b|WEATHER|BUSINESS NEWS""").containsMatchIn(text) -> "News"
            Regex("""(?i)\bWESTERN\b|COWBOY""").containsMatchIn(text) -> "Western"
            Regex("""(?i)\bMOVIE\b|CINEMA|PREMIUM""").containsMatchIn(text) -> "Movies"
            Regex("""(?i)\bKIDS?\b|CHILDREN|CARTOON|FAMILY""").containsMatchIn(text) -> "Kids & Family"
            Regex("""(?i)DOCUMENTARY|DOCUMENTARIES|SCIENCE|HISTORY|DISCOVERY|NAT GEO""").containsMatchIn(text) -> "Documentaries"
            Regex("""(?i)LIFESTYLE|COOKING|FOOD|TRAVEL|HOME|GARDEN|HGTV""").containsMatchIn(text) -> "Lifestyle"
            Regex("""(?i)\bMUSIC\b|MTV|VH1""").containsMatchIn(text) -> "Music"
            Regex("""(?i)ENTERTAINMENT|GENERAL|USA\s*\|""").containsMatchIn(text) -> "Entertainment"
            else -> "Other USA"
        }
    }

    private fun quality(name: String): Int = when {
        Regex("""(?i)\b(?:UHD|4K)\b""").containsMatchIn(name) -> 5
        Regex("""(?i)\b(?:FHD|1080P)\b""").containsMatchIn(name) -> 4
        Regex("""(?i)\b(?:HD|720P)\b""").containsMatchIn(name) -> 3
        Regex("""(?i)\b(?:SD|480P)\b""").containsMatchIn(name) -> 1
        else -> 2
    }

    fun organize(channels: List<LiveChannel>, multiplePlaylists: Boolean): List<LiveChannel> {
        val selected = LinkedHashMap<String, LiveChannel>()
        val backups = ArrayList<LiveChannel>()

        fun withGroup(ch: LiveChannel, title: String, name: String): LiveChannel =
            ch.copy(
                group = title,
                groupId = if (multiplePlaylists) ch.sourceId + "::" + title else title,
                name = name
            )

        for (channel in channels) {
            if (isForeign(channel.group, channel.name)) continue
            val label = cleanName(channel.name)
            if (label.isBlank()) continue
            val group = category(channel.group, channel.name)
            val curated = withGroup(channel, group, label)
            // Only deduplicate within a playlist. Keep provider variants separate
            // and do not merge stations with distinct call signs or cities.
            val dedupKey = channel.sourceId + "|" + group + "|" + label.lowercase()
            val old = selected[dedupKey]
            if (old == null) {
                selected[dedupKey] = curated
            } else if (quality(channel.name) > quality(old.name)) {
                backups += withGroup(old, "Backup Streams", old.name)
                selected[dedupKey] = curated
            } else {
                backups += withGroup(channel, "Backup Streams", channel.name)
            }
        }

        // Keep missing groups out of the UI; categories only appear when populated.
        // First-seen group order is used by Nuvio's default playlist ordering.
        val networkRank: (LiveChannel) -> Int = { ch ->
            if (ch.group != "Sports | Major Networks") 999
            else mainNetworks.indexOfFirst { ch.name.uppercase() == it || ch.name.uppercase().startsWith(it + " ") }
                .let { if (it < 0) 999 else it }
        }
        return (selected.values + backups).sortedWith(
            compareBy<LiveChannel>(
                { categoryOrder.indexOf(it.group).let { idx -> if (idx < 0) 999 else idx } },
                { networkRank(it) },
                { it.name.lowercase() }
            )
        )
    }
}
