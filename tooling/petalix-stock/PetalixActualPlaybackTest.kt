package com.nuvio.tv.petalix

import com.nuvio.tv.core.player.StreamAutoPlaySelector
import com.nuvio.tv.domain.model.Stream
import com.nuvio.tv.domain.model.StreamBehaviorHints
import com.nuvio.tv.livetv.ondemand.OnDemandStreams
import org.junit.Assert.*
import org.junit.Test

class PetalixActualPlaybackTest {
    private fun stream(pengu: Boolean = false, vod: Boolean = false, url: String? = "https://example.test/video.m3u8") = Stream(
        name = null, title = null, description = null, url = url,
        ytId = null, infoHash = null, fileIdx = null, externalUrl = null,
        behaviorHints = StreamBehaviorHints(null, if (vod) OnDemandStreams.BINGE_GROUP else null, null, null),
        addonName = if (vod) OnDemandStreams.GROUP_NAME else "Provider",
        addonLogo = null, isPanelPengu = pengu
    )

    @Test fun actualStreamObjectsPreferVodAndWaitBeforePenguFallback() {
        val vod = stream(vod = true)
        val pengu = stream(pengu = true)
        assertSame(vod, StreamAutoPlaySelector.selectPanelPlayback(listOf(pengu, vod)))
        assertSame(vod, StreamAutoPlaySelector.selectPanelPlayback(listOf(vod, pengu)))
        assertNull(StreamAutoPlaySelector.selectPanelPlayback(listOf(pengu)))
        assertSame(pengu, StreamAutoPlaySelector.selectPanelPlayback(listOf(pengu), allowFallback = true))
    }

    @Test fun unusablePenguAndUnrelatedProvidersDoNotBlockUsableVod() {
        val vod = stream(vod = true)
        val unusable = stream(pengu = true, url = null)
        assertSame(vod, StreamAutoPlaySelector.selectPanelPlayback(listOf(unusable, vod), true))
        assertNull(StreamAutoPlaySelector.selectPanelPlayback(listOf(stream()), true))
        val renamedVod = vod.copy(addonName = "Renamed VOD")
        assertSame(renamedVod, StreamAutoPlaySelector.selectPanelPlayback(listOf(renamedVod), true))
    }
}
