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
        assertSame(hd, StreamAutoPlaySelector.selectPanelPengu(listOf(uhd, hd)))
        assertSame(hd, StreamAutoPlaySelector.selectPanelPengu(listOf(hd, uhd)))
    }

    @Test fun smallerEquivalentFileAndNonRemuxLinksArePreferred() {
        val big = link("1080p", 12_000_000_000L)
        val small = link("1080p", 2_000_000_000L)
        assertSame(small, StreamAutoPlaySelector.selectPanelPengu(listOf(big, small)))
        assertSame(small, StreamAutoPlaySelector.selectPanelPengu(listOf(link("1080p REMUX", 1L), small)))
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
        assertSame(first, StreamAutoPlaySelector.selectPanelPengu(listOf(first, link("Another link"))))
        val uhd = link("4K")
        assertSame(uhd, StreamAutoPlaySelector.selectPanelPengu(listOf(uhd)))
        assertNull(StreamAutoPlaySelector.selectPanelPengu(emptyList()))
    }

    @Test fun explicitQualityPatternOverridesBalancedAutoplay() {
        val hd = link("1080p")
        val uhd = link("2160p")
        val settings = PlayerSettings(streamAutoPlayMode = StreamAutoPlayMode.REGEX_MATCH,
            streamAutoPlayRegex = "2160p")
        assertSame(uhd, StreamAutoPlaySelector.selectPanelPengu(listOf(hd, uhd), true, settings))
        assertNull(StreamAutoPlaySelector.selectPanelPengu(listOf(hd), true, settings))
        assertNull(StreamAutoPlaySelector.selectPanelPengu(listOf(hd), true,
            settings.copy(streamAutoPlayRegex = "[")))
    }

    @Test fun manualModeAndUnplayableLinksAreRespected() {
        val hd = link("1080p")
        assertNull(StreamAutoPlaySelector.selectPanelPengu(listOf(hd), true,
            PlayerSettings(streamAutoPlayMode = StreamAutoPlayMode.MANUAL)))
        val unusable = hd.copy(url = null)
        val uhd = link("2160p")
        assertSame(uhd, StreamAutoPlaySelector.selectPanelPengu(listOf(unusable, uhd)))
    }

    @Test fun configuredPatternStillAllowsTheRequestedVodFallback() {
        val vod = link("1080p", pengu = false)
        val settings = PlayerSettings(streamAutoPlayMode = StreamAutoPlayMode.REGEX_MATCH,
            streamAutoPlayRegex = "1080p")
        val prior = OnDemandStreams.autoPlayAllowed
        try {
            OnDemandStreams.autoPlayAllowed = false
            assertNull(StreamAutoPlaySelector.selectPanelPengu(listOf(vod), false, settings))
            assertSame(vod, StreamAutoPlaySelector.selectPanelPengu(listOf(vod), true, settings))
        } finally { OnDemandStreams.autoPlayAllowed = prior }
    }

    @Test fun penguKeepsProviderPriorityAndVodWaitsForCompletedLookup() {
        val pengu = link("2160p")
        val vod = link("1080p", pengu = false)
        assertSame(pengu, StreamAutoPlaySelector.selectPanelPengu(listOf(vod, pengu), true))
        assertNull(StreamAutoPlaySelector.selectPanelPengu(listOf(vod)))
        assertSame(vod, StreamAutoPlaySelector.selectPanelPengu(listOf(vod), true))
        assertEquals("2160p", pengu.title)
    }
}
