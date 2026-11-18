package com.nuvio.tv.petalix

import com.nuvio.tv.livetv.model.LiveChannel
import com.nuvio.tv.livetv.ui.backupName
import com.nuvio.tv.livetv.ui.stationNote

private val qualitySuffix = Regex("""(?i)(?:[\s|:_-]+|\s*[\[(])(?:4k|8k|uhd|fhd|fd|hd|sd|1080[pi]?|720p?|2160p?|hevc|h[ .]?26[45]|\d{2}\s?fps)(?:[\])])?(?=\s|$)""")
private val streamBrand = Regex("""(?i)\b(?:pluto(?: tv)?|tubi|roku(?: channel)?|samsung tv plus|freevee|plex|xumo|fubo|disney\s*(?:plus|\+)|espn\s*(?:plus|\+)|peacock|paramount\s*(?:plus|\+)|hbo max|max|btn\s*(?:plus|\+)|dazn|apple tv|amazon|prime video|fanatiz|flo\s?sports?|triller)\b""")
private val golfGroup = Regex("(?i)\\bgolf\\b")
private val fixtureName = Regex("""(?i)\b(?:vs\.?|versus|v\.)\b|\b\d{1,2}:\d{2}\b""")
private val eventFeedGroup = Regex("""(?i)\b(?:sports?|games?|events?|teams?|league pass|sunday ticket|replays?|golf|nfl|nba|wnba|mlb|nhl|college|soccer|football|basketball|baseball|hockey)\b""")
private val aroundTheClock = Regex("""(?i)\b24\s*(?:/|\s)?\s*7\b""")
private val spacing = Regex("""\s+""")
private val slotPunctuation = Regex("""[^a-z0-9]+""")
private val eventSlot = Regex("""^(?:(?:us|usa|uk|ca) )?(?:fubo|disney plus|espn plus|espn3|espn 3|espn extra|peacock|paramount plus|hbo max|max|btn plus|b1g plus|dazn|apple tv|amazon|prime video|fanatiz|flo sports|flosports|triller)(?: (?:us|usa|uk|ca|\d+))*$""")

internal fun petalixCleanChannelName(name: String): String = name.replace(qualitySuffix, " ")
    .replace(Regex("\\s+"), " ").trim(' ', '-', '|', ':')

private fun petalixStreamingEventSlot(name: String): Boolean = eventSlot.matches(
    petalixCleanChannelName(name).lowercase(java.util.Locale.ROOT).replace("+", " plus ")
        .replace(slotPunctuation, " ").replace(spacing, " ").trim()
)

internal fun petalixStreamingGuideChannel(channel: LiveChannel): Boolean =
    streamBrand.containsMatchIn((channel.name + " " + channel.group).replace("+", " plus ")) &&
        !fixtureName.containsMatchIn(channel.name) &&
        !eventFeedGroup.containsMatchIn(channel.group) &&
        !aroundTheClock.containsMatchIn(channel.group) &&
        !petalixStreamingEventSlot(channel.name)

internal fun petalixGuideChannelAllowed(channel: LiveChannel, explicitSavedChoice: Boolean): Boolean =
    !petalixStreamingEventSlot(channel.name) &&
        (petalixStreamingGuideChannel(channel) || !hideSportsEventInGuide(channel.group, channel.name)) &&
        petalixShowGuideFeed(channel.petalixGuideHidden, explicitSavedChoice)

/** Only a view of the full channel pool. Keys, source records and backup feeds are retained. */
internal fun petalixGuidePresentation(channels: List<LiveChannel>, bitrates: Map<String, Long>): List<LiveChannel> {
    val grouped = channels.map { channel ->
        when {
            golfGroup.containsMatchIn(channel.group) -> channel.copy(groupId = "petalix-guide:sports", group = "Sports")
            else -> channel
        }
    }
    val preferred = LinkedHashMap<String, LiveChannel>()
    for (channel in grouped) {
        // Keep station and regional editions distinct, even when a provider shares guide IDs.
        val name = backupName(channel.name.replace("+", " plus "))
        val station = stationNote(channel.name).orEmpty()
        val key = channel.sourceId + "|" + name + "|" + station
        val existing = preferred[key]
        if (existing == null || (bitrates[channel.key] ?: 0L) > (bitrates[existing.key] ?: 0L)) {
            preferred[key] = channel
        }
    }
    return preferred.values.toList()
}
