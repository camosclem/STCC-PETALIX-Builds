package com.nuvio.tv.petalix

import com.nuvio.tv.core.player.StreamAutoPlaySelector
import com.nuvio.tv.data.local.PlayerSettings
import com.nuvio.tv.data.local.StreamAutoPlayMode
import com.nuvio.tv.data.local.StreamAutoPlaySource
import com.nuvio.tv.domain.model.Stream
import com.nuvio.tv.domain.model.StreamBehaviorHints
import com.nuvio.tv.livetv.ondemand.OnDemandStreams
import org.junit.Assert.*
import org.junit.Test

class PetalixStreamSelectionTest {
    private fun link(label: String, size: Long? = null, pengu: Boolean = true) = Stream(
        name = "Source", title = label, description = null,
        url = "https://video.example/$label", ytId = null, infoHash = null,
        fileIdx = null, externalUrl = null, addonName = if (pengu) "Source" else OnDemandStreams.GROUP_NAME,
        addonLogo = null, isPanelPengu = pengu,
        behaviorHints = StreamBehaviorHints(null, if (pengu) null else OnDemandStreams.BINGE_GROUP,
            null, null, videoSize = size)
    )

    @Test fun heavyFirstLinkDoesNotWinOverOrdinary1080p() {
        val uhd = link("2160p REMUX", 60_000_000_000L)
        val hd = link("1080p WEB-DL", 2_000_000_000L)
        assertSame(hd, StreamAutoPlaySelector.selectPanelPlayback(listOf(uhd, hd), true))
        assertSame(hd, StreamAutoPlaySelector.selectPanelPlayback(listOf(hd, uhd), true))
    }

    @Test fun smallerEquivalentFileAndNonRemuxLinksArePreferred() {
        val big = link("1080p", 12_000_000_000L)
        val small = link("1080p", 2_000_000_000L)
        assertSame(small, StreamAutoPlaySelector.selectPanelPlayback(listOf(big, small), true))
        assertSame(small, StreamAutoPlaySelector.selectPanelPlayback(listOf(link("1080p REMUX", 1L), small), true))
    }

    @Test fun nativeAutoplayAlsoUsesBalancedLinksWithoutChangingSourceFilters() {
        val uhd = link("2160p REMUX", 60_000_000_000L)
        val hd = link("1080p WEB-DL", 2_000_000_000L)
        assertSame(hd, StreamAutoPlaySelector.selectAutoPlayStream(
            streams = listOf(uhd, hd), mode = StreamAutoPlayMode.FIRST_STREAM,
            regexPattern = "", source = StreamAutoPlaySource.ALL_SOURCES,
            installedAddonNames = setOf("Source"), selectedAddons = emptySet(), selectedPlugins = emptySet()
        ))
        assertNull(StreamAutoPlaySelector.selectAutoPlayStream(
            streams = listOf(uhd, hd), mode = StreamAutoPlayMode.FIRST_STREAM,
            regexPattern = "", source = StreamAutoPlaySource.ALL_SOURCES,
            installedAddonNames = setOf("Source"), selectedAddons = setOf("Another provider"), selectedPlugins = emptySet()
        ))
    }

    @Test fun unknownMetadataKeepsProviderOrderAndOnly4kIsStillPlayable() {
        val first = link("Movie")
        assertSame(first, StreamAutoPlaySelector.selectPanelPlayback(listOf(first, link("Another link")), true))
        val uhd = link("4K")
        assertSame(uhd, StreamAutoPlaySelector.selectPanelPlayback(listOf(uhd), true))
        assertNull(StreamAutoPlaySelector.selectPanelPlayback(emptyList()))
    }

    @Test fun explicitQualityPatternOverridesBalancedAutoplay() {
        val hd = link("1080p")
        val uhd = link("2160p")
        val settings = PlayerSettings(streamAutoPlayMode = StreamAutoPlayMode.REGEX_MATCH,
            streamAutoPlayRegex = "2160p")
        assertSame(uhd, StreamAutoPlaySelector.selectPanelPlayback(listOf(hd, uhd), true, settings))
        assertNull(StreamAutoPlaySelector.selectPanelPlayback(listOf(hd), true, settings))
        assertNull(StreamAutoPlaySelector.selectPanelPlayback(listOf(hd), true,
            settings.copy(streamAutoPlayRegex = "[")))
    }

    @Test fun manualModeAndUnplayableLinksAreRespected() {
        val hd = link("1080p")
        assertNull(StreamAutoPlaySelector.selectPanelPlayback(listOf(hd), true,
            PlayerSettings(streamAutoPlayMode = StreamAutoPlayMode.MANUAL)))
        val unusable = hd.copy(url = null)
        val uhd = link("2160p")
        assertSame(uhd, StreamAutoPlaySelector.selectPanelPlayback(listOf(unusable, uhd), true))
    }

    @Test fun configuredPatternStillAllowsTheRequestedVodFallback() {
        val vod = link("1080p", pengu = false)
        val settings = PlayerSettings(streamAutoPlayMode = StreamAutoPlayMode.REGEX_MATCH,
            streamAutoPlayRegex = "1080p")
        val prior = OnDemandStreams.autoPlayAllowed
        try {
            OnDemandStreams.autoPlayAllowed = false
            assertSame(vod, StreamAutoPlaySelector.selectPanelPlayback(listOf(vod), false, settings))
            assertSame(vod, StreamAutoPlaySelector.selectPanelPlayback(listOf(vod), true, settings))
        } finally { OnDemandStreams.autoPlayAllowed = prior }
    }

    @Test fun vodKeepsProviderPriorityAndPenguWaitsForCompletedLookup() {
        val pengu = link("1080p")
        val vod = link("2160p", pengu = false)
        assertSame(vod, StreamAutoPlaySelector.selectPanelPlayback(listOf(pengu, vod), true))
        assertSame(vod, StreamAutoPlaySelector.selectPanelPlayback(listOf(vod, pengu), true))
        assertNull(StreamAutoPlaySelector.selectPanelPlayback(listOf(pengu)))
        assertSame(pengu, StreamAutoPlaySelector.selectPanelPlayback(listOf(pengu), true))
        assertSame(pengu, StreamAutoPlaySelector.selectPanelPlayback(listOf(vod.copy(url = null), pengu), true))
        assertSame(pengu, StreamAutoPlaySelector.selectPanelPlayback(listOf(vod, pengu), true, preferVod = false))
    }

    @Test fun nativeAutoplayPrefersAllowedVodOverPengu() {
        val pengu = link("1080p")
        val vod = link("2160p", pengu = false)
        val prior = OnDemandStreams.autoPlayAllowed
        try {
            OnDemandStreams.autoPlayAllowed = true
            assertSame(vod, StreamAutoPlaySelector.selectAutoPlayStream(
                streams = listOf(pengu, vod), mode = StreamAutoPlayMode.FIRST_STREAM,
                regexPattern = "", source = StreamAutoPlaySource.ALL_SOURCES,
                installedAddonNames = setOf("Source", OnDemandStreams.GROUP_NAME),
                selectedAddons = emptySet(), selectedPlugins = emptySet()
            ))
        } finally { OnDemandStreams.autoPlayAllowed = prior }
    }
}
