"""Use provider VOD first and balanced Pengu fallback, with native playback controls respected."""
from pathlib import Path
from shutil import copy2
from home_visual_owner_patch import replace

HERE = Path(__file__).resolve().parent


def apply(root):
    source = root / 'app/src/main/java/com/nuvio/tv'
    copy2(HERE / 'PetalixStreamSelection.kt', source / 'petalix/PetalixStreamSelection.kt')
    copy2(HERE / 'PetalixStreamSelectionTest.kt',
          root / 'app/src/test/java/com/nuvio/tv/petalix/PetalixStreamSelectionTest.kt')
    copy2(HERE / 'PetalixVodFirstFlowTest.kt',
          root / 'app/src/test/java/com/nuvio/tv/petalix/PetalixVodFirstFlowTest.kt')
    selector = source / 'core/player/StreamAutoPlaySelector.kt'
    replace(selector, '''    internal fun selectPanelPengu(streams: List<Stream>, allowVodFallback: Boolean = false): Stream? =
        com.nuvio.tv.petalix.preferredPetalixPlayback(
            streams.filter(::isPlayable), allowVodFallback,
            { it.isPanelPengu },
            { it.behaviorHints?.bingeGroup == com.nuvio.tv.livetv.ondemand.OnDemandStreams.BINGE_GROUP ||
                it.addonName == com.nuvio.tv.livetv.ondemand.OnDemandStreams.GROUP_NAME }
        )''', '''    internal fun selectPanelPlayback(
        streams: List<Stream>, allowFallback: Boolean = false,
        settings: com.nuvio.tv.data.local.PlayerSettings? = null, preferVod: Boolean = true
    ): Stream? {
        val mode = settings?.streamAutoPlayMode ?: StreamAutoPlayMode.FIRST_STREAM
        if (mode == StreamAutoPlayMode.MANUAL) return null
        val playable = streams.filter(::isPlayable)
        fun choose(candidates: List<Stream>, balanced: Boolean): Stream? =
            if (mode == StreamAutoPlayMode.REGEX_MATCH) selectAutoPlayStream(
                streams = candidates, mode = mode,
                regexPattern = settings?.streamAutoPlayRegex.orEmpty(),
                source = StreamAutoPlaySource.ALL_SOURCES,
                installedAddonNames = candidates.map { it.addonName }.toSet(),
                selectedAddons = emptySet(), selectedPlugins = emptySet(),
                preferredBingeGroup = com.nuvio.tv.livetv.ondemand.OnDemandStreams.BINGE_GROUP
            ) else if (balanced) com.nuvio.tv.petalix.balancedPetalixStream(candidates)
            else candidates.firstOrNull()
        val vod = playable.filter {
                it.behaviorHints?.bingeGroup == com.nuvio.tv.livetv.ondemand.OnDemandStreams.BINGE_GROUP ||
                    it.addonName == com.nuvio.tv.livetv.ondemand.OnDemandStreams.GROUP_NAME
            }
        val pengu = playable.filter { it.isPanelPengu }
        return if (preferVod) choose(vod, balanced = false)
            ?: if (allowFallback) choose(pengu, balanced = true) else null
        else choose(pengu, balanced = true)
            ?: if (allowFallback) choose(vod, balanced = false) else null
    }''')
    replace(selector, '''            StreamAutoPlayMode.FIRST_STREAM ->
                candidateStreams.firstOrNull {
                    it.addonName.contains("pengu", ignoreCase = true) && isPlayable(it)
                } ?: candidateStreams.firstOrNull { isPlayable(it) }''', '''            StreamAutoPlayMode.FIRST_STREAM ->
                candidateStreams.firstOrNull {
                    isPlayable(it) && (it.behaviorHints?.bingeGroup == com.nuvio.tv.livetv.ondemand.OnDemandStreams.BINGE_GROUP ||
                        it.addonName == com.nuvio.tv.livetv.ondemand.OnDemandStreams.GROUP_NAME)
                } ?: com.nuvio.tv.petalix.balancedPetalixStream(candidateStreams.filter {
                    it.isPanelPengu && isPlayable(it)
                }) ?: candidateStreams.firstOrNull {
                    it.addonName.contains("pengu", ignoreCase = true) && isPlayable(it)
                } ?: candidateStreams.firstOrNull { isPlayable(it) }''')
    vm = source / 'ui/screens/stream/StreamScreenViewModel.kt'
    replace(vm, '            val preferPengu = !manualSelection && !serverStreams.isNativeRequest(videoId) &&',
            '            val preferPengu = !manualSelection && playerSettings.streamAutoPlayMode != StreamAutoPlayMode.MANUAL && !serverStreams.isNativeRequest(videoId) &&')
    replace(vm, '                !preferPengu && playerSettings.streamAutoPlayMode == StreamAutoPlayMode.REGEX_MATCH &&',
            '                playerSettings.streamAutoPlayMode == StreamAutoPlayMode.REGEX_MATCH &&')
    replace(vm, 'StreamAutoPlaySelector.selectPanelPengu(allStreams, allowVodFallback = isAllLoaded)',
            'StreamAutoPlaySelector.selectPanelPlayback(allStreams, allowFallback = true, settings = playerSettings, preferVod = com.nuvio.tv.livetv.ondemand.OnDemandStreams.tabPosition == "FIRST")')
    # All successful emissions now include the completed provider lookup (see the flow below).
    # Keep the existing native manual-selection and episode behavior, without a Pengu race.
    vm.write_text(vm.read_text().replace('preferPengu', 'preferManagedVod'))

    streams = source / 'livetv/ondemand/OnDemandStreams.kt'
    text = streams.read_text()
    start = text.index('        return combine(source, onDemand) { result, provider ->')
    end = text.index('\n    private suspend fun lookup(', start)
    streams.write_text(text[:start] + '''        return com.nuvio.tv.petalix.petalixVodFirstStreams(source, onDemand)
    }
''' + text[end:])
    replace(streams, '            val found = runCatching { lookup(entry, type, videoId, season, episode, title, year) }.getOrDefault(emptyList())', '''            val found = try {
                kotlinx.coroutines.withTimeoutOrNull(12_000L) {
                    lookup(entry, type, videoId, season, episode, title, year)
                }.orEmpty()
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { emptyList() }''')
    replace(streams, '            val found = try {', '            tabPosition = position\n            val found = try {')

    panel = source / 'petalix/PetalixPanelSettings.kt'
    replace(panel, '":home-lineup-v4"', '":vod-first-v1"')
    replace(panel, '            onDemandStreamsPosition = if (values.getBoolean("vod.first")) "FIRST" else "LAST", vodMergeDuplicates = values.getBoolean("vod.merge")',
            '            onDemandAutoPlay = values.getBoolean("vod.first"),\n            onDemandStreamsPosition = if (values.getBoolean("vod.first")) "FIRST" else "LAST", vodMergeDuplicates = values.getBoolean("vod.merge")')
