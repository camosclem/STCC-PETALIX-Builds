"""Guide-only grouping/deduplication; measured quality and the full backup pool stay separate."""
from pathlib import Path
from shutil import copy2
from home_visual_owner_patch import replace
HERE=Path(__file__).resolve().parent

def apply(root):
    source=root/'app/src/main/java/com/nuvio/tv'
    for name in ('PetalixGuidePresentation.kt','PetalixStreamMeasurements.kt','PetalixChannelLogos.kt'):
        copy2(HERE/name,source/'petalix'/name)
    copy2(HERE/'channel-logo-sources.json',root/'app/src/main/assets/petalix/channel-logo-sources.json')
    copy2(HERE/'channel-logo-attribution.txt',root/'app/src/main/assets/petalix/channel-logo-attribution.txt')
    copy2(HERE/'PetalixGuidePresentationTest.kt',root/'app/src/test/java/com/nuvio/tv/petalix/PetalixGuidePresentationTest.kt')
    prefs=source/'livetv/data/LiveTvPreferences.kt'
    replace(prefs, '    private val sourceMutations = Mutex()', '''    init { com.nuvio.tv.petalix.PetalixStreamMeasurements.initialize(context) }
    private val sourceMutations = Mutex()''')
    # Strip display suffixes only after stable identities and saved name edits have been applied.
    store=source/'livetv/data/LiveTvChannelStore.kt'
    replace(store,'withPickedLogos(withLogos(named(channels, user, s), epgLogos, preferEpg), user.channelLogos)', '''withPickedLogos(withLogos(named(channels, user, s), epgLogos, preferEpg), user.channelLogos).map { channel ->
            com.nuvio.tv.petalix.PetalixChannelLogos.improve(context, channel.copy(name = com.nuvio.tv.petalix.petalixCleanChannelName(channel.name)))
        }''')
    guide=source/'livetv/ui/LiveTvViewModel.kt'
    replace(guide,'!com.nuvio.tv.petalix.hideSportsEventInGuide(channel.group, channel.name) &&', '''(com.nuvio.tv.petalix.petalixStreamingGuideChannel(channel) ||
            !com.nuvio.tv.petalix.hideSportsEventInGuide(channel.group, channel.name)) &&''')
    replace(guide,'    val shown = visibility.shown', '    val shown = com.nuvio.tv.petalix.petalixGuidePresentation(visibility.shown, com.nuvio.tv.petalix.PetalixStreamMeasurements.bitrates.value)')
    replace(guide,'    val visible = visibility.visible', '    val visible = com.nuvio.tv.petalix.petalixGuidePresentation(visibility.visible, com.nuvio.tv.petalix.PetalixStreamMeasurements.bitrates.value)')
    replace(guide,'    val byKey = visible.associateBy { it.key }', '    val byKey = visibility.visible.associateBy { it.key }')
    replace(guide,'combine(hasSources, com.nuvio.tv.livetv.parental.ParentalControls.unlocked) { h, _ -> h }', 'combine(hasSources, com.nuvio.tv.livetv.parental.ParentalControls.unlocked, com.nuvio.tv.petalix.PetalixStreamMeasurements.bitrates) { h, _, _ -> h }')
    # Every new media chunk refines bitrate without opening extra streams or using more connections.
    player=source/'livetv/ui/LiveTvPlayerScreen.kt'
    replace(player,'    var videoFps by remember { mutableStateOf(-1f) }', '''    val measurementPlayer = viewModel.playback.player
    DisposableEffect(measurementPlayer, playback.channelKey, playback.catchupTitle) {
        val key = playback.channelKey
        val listener = object : androidx.media3.exoplayer.analytics.AnalyticsListener {
            override fun onLoadCompleted(
                eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                loadEventInfo: androidx.media3.exoplayer.source.LoadEventInfo,
                mediaLoadData: androidx.media3.exoplayer.source.MediaLoadData
            ) {
                if (key != null && playback.catchupTitle == null &&
                    mediaLoadData.dataType == androidx.media3.common.C.DATA_TYPE_MEDIA &&
                    mediaLoadData.trackType != androidx.media3.common.C.TRACK_TYPE_AUDIO) {
                    com.nuvio.tv.petalix.PetalixStreamMeasurements.record(key, loadEventInfo.bytesLoaded,
                        mediaLoadData.mediaEndTimeMs - mediaLoadData.mediaStartTimeMs)
                }
            }
        }
        measurementPlayer?.addAnalyticsListener(listener)
        onDispose { measurementPlayer?.removeAnalyticsListener(listener) }
    }
    var videoFps by remember { mutableStateOf(-1f) }''')
    # Sports badges update from measured dimensions immediately, including already-cached matches.
    sports=source/'livetv/sports/SportsRepository.kt'
    replace(sports, '''        _addonLive
    ) { m0, region, addon ->
        val m = if (region.isBlank()) m0 else m0.mapValues { (_, list) -> ChannelRegions.regionFirst(list, region) }''', '''        _addonLive,
        prefs.userState.map { it.channelQuality }.distinctUntilChanged()
    ) { m0, region, addon, measuredQuality ->
        val verified = m0.mapValues { (_, list) -> list.map { match ->
            match.copy(quality = measuredQuality[match.channel.key])
        } }
        val m = if (region.isBlank()) verified else verified.mapValues { (_, list) -> ChannelRegions.regionFirst(list, region) }''')
    # Quality text is unnecessary in backup lists as well; the clean channel name is sufficient.
    for relative in ('livetv/ui/LiveTvPlayerScreen.kt','livetv/ui/LiveTvOverlayMode.kt'):
        path=source/relative
        text=path.read_text().replace('backupQuality(ch.name)?.let { q ->','(null as String?)?.let { q ->')
        path.write_text(text)
    coordinator=source/'petalix/PetalixManagedCoordinator.kt'
    replace(coordinator,'    private val repository: LiveTvRepository', '    private val playerSettings: com.nuvio.tv.data.local.PlayerSettingsDataStore,\n    private val repository: LiveTvRepository')
    replace(coordinator,'        applyPanelSettings(doc)', '''        applyPanelSettings(doc)
        val viewingKey = "netflixViewingV1:${profiles.activeProfileId.value}"
        if (!prefs.getBoolean(viewingKey, false)) {
            live.updateSettings { it.copy(channelSort = com.nuvio.tv.livetv.model.ChannelSort.NAME, showQualityBadges = false) }
            playerSettings.setSkipIntroEnabled(true)
            playerSettings.setPostPlayRecommendationsEnabled(true)
            playerSettings.setStreamAutoPlayNextEpisodeEnabled(true)
            playerSettings.setStreamAutoPlayNextEpisodeFallbackEnabled(true)
            playerSettings.setStillWatchingEnabled(true)
            check(prefs.edit().putBoolean(viewingKey, true).commit())
        }''')
    components=source/'livetv/ui/LiveTvComponents.kt'
    replace(components,'    val imageVersion = com.nuvio.tv.core.image.rememberImageRevalidationKey(url)', '''    var logoFailed by androidx.compose.runtime.remember(url) { androidx.compose.runtime.mutableStateOf(false) }
    val displayUrl = if (logoFailed) com.nuvio.tv.petalix.PetalixChannelLogos.fallback(url) ?: url else url
    val imageVersion = com.nuvio.tv.core.image.rememberImageRevalidationKey(displayUrl)''')
    replace(components,'androidx.compose.runtime.remember(url, imageVersion)', 'androidx.compose.runtime.remember(displayUrl, imageVersion)')
    replace(components,'coil3.request.ImageRequest.Builder(logoContext).data(url)', 'coil3.request.ImageRequest.Builder(logoContext).data(displayUrl)')
    replace(components,'onError = { DeadLogos.failed(url, it.result.throwable) }', 'onError = { logoFailed = true; DeadLogos.failed(displayUrl ?: url, it.result.throwable) }')

    for relative in ('livetv/sports/SportsDesign.kt','livetv/sports/SportsScreen.kt'):
        path=source/relative
        text=path.read_text().replace('m.quality?.let { q ->','(null as String?)?.let { q ->').replace('match?.quality?.let {','(null as String?)?.let {').replace('val qs = copies.mapNotNull { it.quality }.distinct().filter { it != m.quality }', 'val qs = emptyList<String>()')
        path.write_text(text)

    replace(components,'if (!url.isNullOrBlank() && !DeadLogos.isDead(url)) {', 'if (!displayUrl.isNullOrBlank() && !DeadLogos.isDead(displayUrl)) {')
    replace(components,'.memoryCacheKey("$url:logo:$imageVersion")', '.memoryCacheKey("$displayUrl:logo:$imageVersion")')
