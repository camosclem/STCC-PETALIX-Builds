package com.nuvio.tv.petalix

import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.core.player.StreamAutoPlaySelector
import com.nuvio.tv.domain.model.AddonStreams
import com.nuvio.tv.domain.model.Stream
import com.nuvio.tv.domain.model.StreamBehaviorHints
import com.nuvio.tv.livetv.ondemand.OnDemandStreams
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PetalixVodFirstFlowTest {
    private fun link(vod: Boolean = false) = Stream(
        name = "1080p", title = "Movie or episode", description = null,
        url = if (vod) "https://provider.example/video" else "https://fallback.example/video",
        ytId = null, infoHash = null, fileIdx = null, externalUrl = null,
        behaviorHints = StreamBehaviorHints(null, if (vod) OnDemandStreams.BINGE_GROUP else null, null, null),
        addonName = if (vod) OnDemandStreams.GROUP_NAME else "Pengu", addonLogo = null,
        isPanelPengu = !vod
    )

    @Test fun fastPenguCannotStartBeforeSlowVodLookupFinishes() = runTest {
        val pengu = link()
        val vod = link(vod = true)
        val source = MutableStateFlow<NetworkResult<List<AddonStreams>>>(
            NetworkResult.Success(listOf(AddonStreams("Pengu", null, listOf(pengu))))
        )
        val provider = MutableStateFlow<Pair<List<Stream>, String>?>(null)
        val seen = mutableListOf<NetworkResult<List<AddonStreams>>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            petalixVodFirstStreams(source, provider).collect { seen += it }
        }
        runCurrent()
        assertTrue(seen.isNotEmpty())
        assertTrue(seen.all { it is NetworkResult.Loading })
        provider.value = listOf(vod) to "FIRST"
        runCurrent()
        val streams = (seen.last() as NetworkResult.Success<List<AddonStreams>>).data.flatMap { it.streams }
        assertEquals(listOf(vod, pengu), streams)
        assertSame(vod, StreamAutoPlaySelector.selectPanelPlayback(streams, true))
    }

    @Test fun readyVodStartsEvenWhenAddonSearchHasNotEmittedAnything() = runTest {
        val vod = link(vod = true)
        val source = flow<NetworkResult<List<AddonStreams>>> { awaitCancellation() }
        val provider = MutableStateFlow<Pair<List<Stream>, String>?>(listOf(vod) to "FIRST")
        val seen = mutableListOf<NetworkResult<List<AddonStreams>>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            petalixVodFirstStreams(source, provider).collect { seen += it }
        }
        runCurrent()
        val streams = (seen.last() as NetworkResult.Success<List<AddonStreams>>).data.flatMap { it.streams }
        assertEquals(listOf(vod), streams)
        assertSame(vod, StreamAutoPlaySelector.selectPanelPlayback(streams))
    }

    @Test fun completedEmptyVodLookupAllowsPenguFallback() = runTest {
        val pengu = link()
        val source = MutableStateFlow<NetworkResult<List<AddonStreams>>>(
            NetworkResult.Success(listOf(AddonStreams("Pengu", null, listOf(pengu))))
        )
        val provider = MutableStateFlow<Pair<List<Stream>, String>?>(null)
        val seen = mutableListOf<NetworkResult<List<AddonStreams>>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            petalixVodFirstStreams(source, provider).collect { seen += it }
        }
        runCurrent()
        assertTrue(seen.last() is NetworkResult.Loading)
        provider.value = emptyList<Stream>() to "FIRST"
        runCurrent()
        val streams = (seen.last() as NetworkResult.Success<List<AddonStreams>>).data.flatMap { it.streams }
        assertSame(pengu, StreamAutoPlaySelector.selectPanelPlayback(streams, true))
        assertEquals(listOf(pengu), streams)
    }

    @Test fun addonFailureDoesNotHideReadyVodAndRealNoSourceErrorsRemainVisible() = runTest {
        val error = NetworkResult.Error("No matching addon stream", 503)
        val source = MutableStateFlow<NetworkResult<List<AddonStreams>>>(error)
        val provider = MutableStateFlow<Pair<List<Stream>, String>?>(null)
        val seen = mutableListOf<NetworkResult<List<AddonStreams>>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            petalixVodFirstStreams(source, provider).collect { seen += it }
        }
        runCurrent()
        assertTrue(seen.last() is NetworkResult.Loading)
        val vod = link(vod = true)
        provider.value = listOf(vod) to "FIRST"
        runCurrent()
        assertEquals(listOf(vod), (seen.last() as NetworkResult.Success<List<AddonStreams>>).data.flatMap { it.streams })
        provider.value = emptyList<Stream>() to "FIRST"
        runCurrent()
        assertSame(error, seen.last())
    }
}
