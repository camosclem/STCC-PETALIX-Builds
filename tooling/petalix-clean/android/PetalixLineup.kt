package com.nuvio.tv.petalix

import com.nuvio.tv.livetv.model.LiveChannel
import java.util.Locale

/**
 * Personal Petalix Live TV curation. Applies on every locally imported M3U/Xtream refresh.
 * This is strictly the separate com.petalix.iptv.localtest build, never customer Petalix.
 *
 * Rules:
 * - Sports is ONE group: major networks, FanDuel 1–5, TSN 1–5, then other sports.
 * - All groups prefer 4K/UHD > 1080/FHD > HD > SD. Show ONE best source per channel.
 * - No international channels (except explicitly requested TSN 1–5) or VOD in Live TV.
 * - No backup group, duplicate streams or embedded provider credentials.
 * - Never change stream URL, stable key, guide ID, logo, headers, DRM, or catch-up.
 */
internal object PetalixLineup {
    val categoryOrder = listOf(
        "Locals", "Entertainment", "News", "Sports", "Movies", "Kids & Family",
        "Lifestyle", "Documentaries", "Western", "Music",
        "24/7 | TV & Series", "24/7 | Movies", "24/7 | Kids",
        "24/7 | Music", "24/7 | Other", "Other USA"
    )

    private val majorNetworks = listOf(
        "ESPN", "ESPN2", "ESPNEWS", "ESPNU", "FS1", "FS2",
        "NFL NETWORK", "NFL REDZONE", "NBA TV", "MLB NETWORK", "NHL NETWORK",
        "CBS SPORTS NETWORK", "NBC SPORTS", "GOLF CHANNEL", "TENNIS CHANNEL",
        "BIG TEN NETWORK", "SEC NETWORK", "ACC NETWORK"
    )
    private val foreignGroup = Regex(
        """(?i)(?:^|[\s|:/-])(?:INTERNATIONAL|WORLD|CANADA|CANADIAN|UK|UNITED KINGDOM|BRITAIN|INDIA|PAKISTAN|TURKEY|TURKISH|FRANCE|FRENCH|GERMANY|GERMAN|ARABIC|ARABIA|PORTUGAL|BRAZIL|SPAIN|ITALY|MEXICO|LATIN AMERICA|SOUTH AMERICA|EUROPE|ASIA|AFRICA|AFGHANISTAN|RUSSIA|CHINA|JAPAN|KOREA|POLAND|GREECE|ISRAEL)(?:$|[\s|:/-])"""
    )
    private val foreignNamePrefix = Regex(
        """(?i)^(?:INTL|INTERNATIONAL|CANADA|CANADIAN|UK|UNITED KINGDOM|INDIA|PAKISTAN|FRANCE|FRENCH|GERMANY|GERMAN|ARABIC|BRAZIL|SPAIN|ITALY|MEXICO|LATINO|RUSSIA|CHINA|JAPAN|KOREA)\s*[-:|]\s*"""
    )
    private val onDemandGroup = Regex(
        """(?i)(?:^|[\s|:/-])(?:VOD|ON[\s-]*DEMAND|MOVIE[\s-]*VOD|SERIES|TV[\s-]*SHOWS|EPISODES)(?:$|[\s|:/-])"""
    )
    private val onDemandUrl = Regex(
        """(?i)/(?:movie|series|vod|episodes?)/|\.(?:mp4|mkv|avi|wmv)(?:[?#]|$)"""
    )
    private val fanDuel = Regex("""(?i)\b(?:FAN\s*DUEL|FANDUEL|BALLY SPORTS)\b""")
    private val tsn = Regex("""(?i)\bTSN(?:\s*\+)?\b""")
    private val sports = Regex(
        """(?i)\bSPORTS?\b|\b(?:ESPN|NFL|NBA|MLB|NHL|NCAA|UFC|PGA|NASCAR|FORMULA 1|F1|MLS|SOCCER|FOOTBALL|TENNIS|BOXING|MMA|GOLF|CRICKET|RACING|FS1|FS2|B1G|TSN|FANDUEL)\b"""
    )
    private val major = Regex(
        """(?i)^(?:ESPN(?:\+|2|U|NEWS)?|FS[12]|FOX SPORTS [12]|NFL NETWORK|NFL REDZONE|NBA TV|MLB NETWORK|NHL NETWORK|CBS SPORTS(?: NETWORK)?|NBC SPORTS(?: NETWORK)?|GOLF CHANNEL|TENNIS CHANNEL|BIG TEN NETWORK|B1G NETWORK|SEC NETWORK|ACC NETWORK)(?:\b|\s|$)"""
    )
    private val qualitySuffix = Regex(
        """(?i)(?:\s+|\||\[|\()(?:UHD|FHD|HD|SD|4K|2160P|1080P|720P|480P|HEVC|H265|60FPS|50FPS)(?:\]|\))?$"""
    )
    private val qualityToken = Regex("""(?i)\b(UHD|4K|2160P|FHD|1080P|HD|720P|SD|480P)\b""")
    private val prefix = Regex("""(?i)^(?:US|USA)\s*[-:|]\s*""")
    private val whitespace = Regex("""\s+""")
    private val twoToFive = Regex("""(?<!\d)([1-5])(?!\d)""")
    private val genericLocal = Regex("""(?i)^(?:ABC|CBS|NBC|FOX|PBS|CW)$""")

    fun cleanName(name: String): String {
        var result = prefix.replace(name.trim(), "")
        while (qualitySuffix.containsMatchIn(result)) {
            result = qualitySuffix.replace(result, "").trim()
        }
        return whitespace.replace(result, " ")
    }

    private fun number1to5(name: String): Int? =
        twoToFive.find(name)?.groupValues?.get(1)?.toIntOrNull()

    /** Only the specific numbered Canadian TSN feeds requested by the owner survive. */
    private fun allowedTsn(name: String): Boolean =
        tsn.containsMatchIn(name) && number1to5(name) != null

    fun isForeign(group: String, name: String): Boolean {
        if (!foreignGroup.containsMatchIn(group) && !foreignNamePrefix.containsMatchIn(name)) return false
        return !allowedTsn(name)
    }

    fun isOnDemand(group: String, url: String): Boolean =
        (onDemandGroup.containsMatchIn(group) && !group.contains("24/7") && !group.contains("24 7")) ||
            onDemandUrl.containsMatchIn(url)

    fun category(group: String, name: String): String {
        val text = group + " " + name
        val n = cleanName(name)
        if (fanDuel.containsMatchIn(text) || tsn.containsMatchIn(text) ||
            major.containsMatchIn(n) || sports.containsMatchIn(text)) return "Sports"
        if (text.contains("24/7") || text.contains("24 7")) return "24/7 | " + when {
            Regex("""(?i)MOVIE|FILM|CINEMA""").containsMatchIn(text) -> "Movies"
            Regex("""(?i)KID|CARTOON|FAMILY|ANIME""").containsMatchIn(text) -> "Kids"
            Regex("""(?i)MUSIC|CONCERT|KARAOKE""").containsMatchIn(text) -> "Music"
            Regex("""(?i)DRAMA|CRIME|REALITY|COMEDY|SHOW|SERIES|CLASSIC|TV""").containsMatchIn(text) -> "TV & Series"
            else -> "Other"
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

    /** Name or provider-group resolution: prefer advertised 4K over FHD, HD and SD. */
    private fun quality(ch: LiveChannel): Int {
        val labels = ch.name + " " + ch.group
        val matches = qualityToken.findAll(labels).map { it.groupValues[1].uppercase(Locale.ROOT) }
        return matches.map { when (it) {
            "UHD", "4K", "2160P" -> 5
            "FHD", "1080P" -> 4
            "HD", "720P" -> 3
            "SD", "480P" -> 1
            else -> 2
        } }.maxOrNull() ?: 2
    }

    private fun normalizedName(name: String): String {
        // Different providers use different names for the same numbered feed.
        // Unify aliases before deduplication so only the best stream survives.
        if (fanDuel.containsMatchIn(name)) {
            number1to5(name)?.let { return "FANDUEL $it" }
        }
        if (tsn.containsMatchIn(name)) {
            number1to5(name)?.let { return "TSN $it" }
        }
        val label = cleanName(name).uppercase(Locale.ROOT)
            .replace(Regex("""\bESPN\s+2\b"""), "ESPN2")
            .replace(Regex("""\bESPN\s+NEWS\b"""), "ESPNEWS")
            .replace(Regex("""\bESPN\s+U\b"""), "ESPNU")
            .replace(Regex("""\bFOX SPORTS\s*[- ]?\s*1\b"""), "FS1")
            .replace(Regex("""\bFOX SPORTS\s*[- ]?\s*2\b"""), "FS2")
            .replace(Regex("""\bFS\s*[- ]\s*1\b"""), "FS1")
            .replace(Regex("""\bFS\s*[- ]\s*2\b"""), "FS2")
        return whitespace.replace(label, " ").trim()
    }

    private fun networkIndex(name: String): Int {
        val n = normalizedName(name)
        return majorNetworks.indexOfFirst { n == it || n.startsWith("$it ") }
    }

    /** First all major networks, then numbered FanDuel 1–5, TSN 1–5, then other sports. */
    private fun sportsPriority(ch: LiveChannel): Int {
        val networkIndex = networkIndex(ch.name)
        if (networkIndex >= 0) return networkIndex
        if (fanDuel.containsMatchIn(ch.name)) {
            val n = number1to5(ch.name)
            return if (n != null) 100 + n else 199
        }
        if (tsn.containsMatchIn(ch.name)) {
            val n = number1to5(ch.name)
            return if (n != null) 200 + n else 299
        }
        return 300
    }

    private fun dedupId(ch: LiveChannel): String {
        val n = normalizedName(ch.name)
        // Generic "ABC"/"CBS" labels may represent different stations in
        // different cities; their distinct EPG IDs prevent accidental merging.
        return if (ch.group == "Locals" && genericLocal.matches(n) && !ch.tvgId.isNullOrBlank())
            n + "|" + ch.tvgId.lowercase(Locale.ROOT) else n
    }

    fun organize(channels: List<LiveChannel>, multiplePlaylists: Boolean): List<LiveChannel> {
        val selected = LinkedHashMap<String, LiveChannel>()
        val scores = HashMap<String, Int>()

        fun regroup(ch: LiveChannel, newGroup: String): LiveChannel = ch.copy(
            name = cleanName(ch.name),
            group = newGroup,
            groupId = if (multiplePlaylists) ch.sourceId + "::" + newGroup else newGroup
        )

        for (channel in channels) {
            if (isOnDemand(channel.group, channel.url)) continue
            if (isForeign(channel.group, channel.name)) continue
            // The user requested FanDuel and TSN numbered 1–5, not global
            // event lists, unrelated Canadian channels or unnumbered feeds.
            if (tsn.containsMatchIn(channel.name) && !allowedTsn(channel.name)) continue
            if (fanDuel.containsMatchIn(channel.name) && number1to5(channel.name) == null) continue
            val title = cleanName(channel.name)
            if (title.isBlank()) continue
            val grouped = regroup(channel, category(channel.group, title))
            val id = dedupId(grouped)
            val score = quality(channel)
            val previous = selected[id]
            // Keep exactly ONE stream; prefer higher advertised resolution.
            // Stable input order resolves tied qualities without multiplying feeds.
            if (previous == null || score > (scores[id] ?: 0)) {
                selected[id] = grouped
                scores[id] = score
            }
        }

        return selected.values.sortedWith(
            compareBy<LiveChannel>(
                { ch -> categoryOrder.indexOf(ch.group).let { if (it < 0) 999 else it } },
                { ch -> if (ch.group == "Sports") sportsPriority(ch) else 0 },
                { ch -> -(scores[dedupId(ch)] ?: 2) },
                { ch -> ch.name.lowercase(Locale.ROOT) }
            )
        )
    }
}
