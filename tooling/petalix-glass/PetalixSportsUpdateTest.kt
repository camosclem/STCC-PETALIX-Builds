package com.nuvio.tv.petalix

import com.nuvio.tv.livetv.sports.SportsFollows
import com.nuvio.tv.livetv.sports.SportsSettings
import com.nuvio.tv.livetv.sports.SportsVideos
import com.nuvio.tv.livetv.sports.SportsWatchPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class PetalixSportsUpdateTest {
    @Test fun officialClipsKeepTheirOwnSourceAndRankBesideFullReplays() {
        fun clip(id: String, kind: SportsVideos.Kind) = SportsVideos.match(
            SportsVideos.Video(id, id, kind, "ESPN", 180, "https://example.invalid/$id")
        )
        val highlights = clip("highlights", SportsVideos.Kind.HIGHLIGHTS)
        val condensed = clip("condensed", SportsVideos.Kind.CONDENSED)
        val replay = highlights.copy(
            channel = highlights.channel.copy(key = "provider-replay", sourceId = "owner-addon"),
            how = "Full game"
        )
        val ordered = SportsVideos.merge(listOf(replay), listOf(highlights, condensed))
        assertEquals(listOf(replay, condensed, highlights), ordered)
        assertEquals(SportsVideos.SOURCE_ID, ordered.last().channel.sourceId)
    }

    @Test fun multiviewFiltersRestoreTheSportsSelection() {
        val follows = SportsFollows(leagues = setOf("nfl"))
        val options = SportsWatchPolicy.filters(emptyList(), follows, SportsSettings())
        assertEquals(listOf("My Sports", "All Events", "Live Now"), options.map { it.label })
        for (option in options) {
            assertEquals(option.key, SportsWatchPolicy.filterKey(SportsWatchPolicy.filterQuery(option.key)))
        }
    }
}
