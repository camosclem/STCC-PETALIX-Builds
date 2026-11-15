"""Keep Pengu provider priority while honoring playback controls and avoiding heavy first links."""
from pathlib import Path
from shutil import copy2
from home_visual_owner_patch import replace

HERE = Path(__file__).resolve().parent


def apply(root):
    source = root / 'app/src/main/java/com/nuvio/tv'
    copy2(HERE / 'PetalixStreamSelection.kt', source / 'petalix/PetalixStreamSelection.kt')
    copy2(HERE / 'PetalixStreamSelectionTest.kt',
          root / 'app/src/test/java/com/nuvio/tv/petalix/PetalixStreamSelectionTest.kt')
    selector = source / 'core/player/StreamAutoPlaySelector.kt'
    replace(selector, '''    internal fun selectPanelPengu(streams: List<Stream>, allowVodFallback: Boolean = false): Stream? =
        com.nuvio.tv.petalix.preferredPetalixPlayback(
            streams.filter(::isPlayable), allowVodFallback,
            { it.isPanelPengu },
            { it.behaviorHints?.bingeGroup == com.nuvio.tv.livetv.ondemand.OnDemandStreams.BINGE_GROUP ||
                it.addonName == com.nuvio.tv.livetv.ondemand.OnDemandStreams.GROUP_NAME }
        )''', '''    internal fun selectPanelPengu(
        streams: List<Stream>, allowVodFallback: Boolean = false,
        settings: com.nuvio.tv.data.local.PlayerSettings? = null
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
        return choose(playable.filter { it.isPanelPengu }, balanced = true)
            ?: if (allowVodFallback) choose(playable.filter {
                it.behaviorHints?.bingeGroup == com.nuvio.tv.livetv.ondemand.OnDemandStreams.BINGE_GROUP ||
                    it.addonName == com.nuvio.tv.livetv.ondemand.OnDemandStreams.GROUP_NAME
            }, balanced = false) else null
    }''')
    replace(selector, '''            StreamAutoPlayMode.FIRST_STREAM ->
                candidateStreams.firstOrNull {
                    it.addonName.contains("pengu", ignoreCase = true) && isPlayable(it)
                } ?: candidateStreams.firstOrNull { isPlayable(it) }''', '''            StreamAutoPlayMode.FIRST_STREAM ->
                com.nuvio.tv.petalix.balancedPetalixStream(candidateStreams.filter {
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
            'StreamAutoPlaySelector.selectPanelPengu(allStreams, allowVodFallback = isAllLoaded, settings = playerSettings)')
