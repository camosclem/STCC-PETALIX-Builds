package com.nuvio.tv.petalix

import com.nuvio.tv.livetv.model.LiveChannel
import com.nuvio.tv.livetv.ui.backupName
import com.nuvio.tv.livetv.ui.stationNote

private val qualitySuffix = Regex("""(?i)(?:[\s|:_-]+|\s*[\[(])(?:4k|8k|uhd|fhd|fd|hd|sd|1080[pi]?|720p?|2160p?|hevc|h[ .]?26[45]|\d{2}\s?fps)(?:[\])])?(?=\s|$)""")
private val streamBrand = Regex("""(?i)\b(?:fubo|disney\s*(?:plus|\+)|espn\s*(?:plus|\+)|peacock|paramount\s*(?:plus|\+)|hbo max|max|btn\s*(?:plus|\+)|dazn|apple tv|amazon|prime video|fanatiz|flo\s?sports?|triller)\b""")
private val fixtureName = Regex("""(?i)\b(?:vs\.?|versus|v\.)\b|\b\d{1,2}:\d{2}\b""")

internal fun petalixCleanChannelName(name: String): String = name.replace(qualitySuffix, " ")
    .replace(Regex("\\s+"), " ").trim(' ', '-', '|', ':')

internal fun petalixStreamingGuideChannel(channel: LiveChannel): Boolean =
    streamBrand.containsMatchIn((channel.name + " " + channel.group).replace("+", " plus ")) &&
        !fixtureName.containsMatchIn(channel.name) &&
        !Regex("(?i)\\b(?:games?|events?|teams?|league pass|sunday ticket|replays?)\\b").containsMatchIn(channel.group)

/** Only a view of the full channel pool. Keys, source records and backup feeds are retained. */
internal fun petalixGuidePresentation(channels: List<LiveChannel>, bitrates: Map<String, Long>): List<LiveChannel> {
    val grouped = channels.map { channel ->
        when {
            petalixStreamingGuideChannel(channel) -> channel.copy(groupId = "petalix-guide:streaming", group = "Streaming Channels")
            Regex("(?i)\\bgolf\\b").containsMatchIn(channel.group) -> channel.copy(groupId = "petalix-guide:sports", group = "Sports")
            else -> channel
        }
    }
    val preferred = LinkedHashMap<String, LiveChannel>()
    for (channel in grouped) {
        // Keep station and regional editions distinct, even when a provider shares guide IDs.
        val name = backupName(channel.name)
        val station = stationNote(channel.name).orEmpty()
        val key = channel.sourceId + "|" + name + "|" + station
        val existing = preferred[key]
        if (existing == null || (bitrates[channel.key] ?: 0L) > (bitrates[existing.key] ?: 0L)) {
            preferred[key] = channel
        }
    }
    return preferred.values.toList()
}
