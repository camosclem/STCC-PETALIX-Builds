"""Repair the tested Home composition, artwork fallbacks and explicit source opt-in."""
from pathlib import Path
from shutil import copy2
from home_visual_owner_patch import replace
HERE=Path(__file__).resolve().parent

def apply(root):
    source=root/'app/src/main/java/com/nuvio/tv'
    for name in ('PetalixHomeRepairPolicy.kt','PetalixLiveArtwork.kt'):
        copy2(HERE/name,source/'petalix'/name)
    copy2(HERE/'PetalixHomeRepairPolicyTest.kt',root/'app/src/test/java/com/nuvio/tv/petalix/PetalixHomeRepairPolicyTest.kt')
    home=source/'ui/screens/home/HomeScreen.kt'
    s=home.read_text(); start=s.index('    val homeRows = remember(sourceState.homeRows, homeChartRows) {');end=s.index('    val uiState = sourceState.copy(',start)
    s=s[:start]+'''    val liveArtwork = com.nuvio.tv.petalix.PetalixLiveArtwork.images.value
    val homeRows = remember(sourceState.homeRows, homeChartRows, vodState.rows, liveArtwork) {
        com.nuvio.tv.petalix.petalixRepairedHomeRows(sourceState.homeRows, homeChartRows, vodState.rows)
    }
'''+s[end:];home.write_text(s)
    hero=source/'ui/screens/home/PetalixCategoryHero.kt'
    replace(hero,'val backdrop = "file:///android_asset/petalix/home-art/$art.webp"',
        'val backdrop = com.nuvio.tv.petalix.PetalixLiveArtwork.forCategory(art)')
    cards=source/'ui/screens/home/ModernHomeRows.kt'
    replace(cards,'                item.metaPreview?.rawPosterUrl\n            }','                item.metaPreview?.rawPosterUrl ?: item.heroPreview.poster\n            }')
    replace(cards,'    if (!useLandscapeOverlayTreatment && !enrichedBackdropUrl.isNullOrBlank() && frozenBackdropUrl.value != enrichedBackdropUrl) {',
        '    if (!enrichedBackdropUrl.isNullOrBlank() && frozenBackdropUrl.value != enrichedBackdropUrl) {')
    replace(cards, 'val imageUrl = petalixArtwork ?: petalixLogoUri ?: baseImageUrl',
        'val imageUrl = petalixArtwork ?: petalixLogoUri ?: com.nuvio.tv.petalix.PetalixHomeRepairPolicy.thumbnail(baseImageUrl, item.heroPreview.poster)')
    models=source/'ui/screens/home/ModernHomeModels.kt'
    replace(models,'val frozenBackdrop = carriedBackdrop?.takeIf { it.isNotBlank() }\n        ?: currentBackdrop',
        'val frozenBackdrop = currentBackdrop?.takeIf { it.isNotBlank() } ?: carriedBackdrop')
    # Provider VOD must remain discoverable even while its first import is empty or failed.
    main=source/'MainActivity.kt'
    replace(main,'val onDemandInSidebar = onDemandHasContent && liveTvSettingsNow.onDemandEnabled && liveTvSettingsNow.onDemandInSidebar',
        'val onDemandInSidebar = liveTvSettingsNow.onDemandEnabled && liveTvSettingsNow.onDemandInSidebar')
    coordinator=source/'petalix/PetalixManagedCoordinator.kt'
    replace(coordinator,'    private val repository: LiveTvRepository\n', '''    private val theme: com.nuvio.tv.data.local.ThemeDataStore,
    private val onDemand: com.nuvio.tv.livetv.ondemand.OnDemandRepository,
    private val repository: LiveTvRepository
''')
    replace(coordinator,'        ensureBritBoxService(profiles.activeProfileId.value)\n', '''        ensureBritBoxService(profiles.activeProfileId.value)
        repairHomeDefaults(profiles.activeProfileId.value, true)
''')
    replace(coordinator,'    private suspend fun applySettings(doc: JSONObject) {', '''    private suspend fun repairHomeDefaults(profileId: Int, afterPanel: Boolean) {
        val key = "deviceTestRepairV3:$profileId:$afterPanel"
        if (prefs.getBoolean(key, false)) return
        live.updateSettings { it.copy(openOn = "HOME", startPage = "HOME", startOnLiveTv = false,
            autoPlayLastChannel = false, guideBackdrop = "auto", onDemandEnabled = true,
            onDemandInSidebar = true, onDemandAutoPlay = true, onDemandStreamsPosition = "FIRST") }
        com.nuvio.tv.livetv.startup.StartPage.save(context, null)
        theme.setTheme(com.nuvio.tv.domain.model.AppTheme.WHITE)
        tmdb.setEnabled(true)
        tmdb.setUseArtwork(true)
        tmdb.setUseCredits(true)
        tmdb.setUseDetails(true)
        tmdb.setUseBasicInfo(true)
        tmdb.setUseEpisodes(true)
        if (afterPanel) {
            live.setOnDemandImportTime("petalix-managed", 0L)
            val current = collections.getCurrentCollections(profileId)
            val bundled = context.assets.open("petalix/streaming-services.json").bufferedReader()
                .use { collections.importFromJson(it.readText()) }.single()
            val britbox = bundled.folders.single { it.id == "folder-petalix-britbox" }
            collections.setCollections(current.map { collection ->
                if (collection.id != bundled.id) collection else collection.copy(folders =
                    collection.folders.filterNot { it.id == britbox.id } + britbox)
            }, profileId)
        }
        check(prefs.edit().putBoolean(key, true).commit())
    }

    private suspend fun applySettings(doc: JSONObject) {''')
    replace(coordinator,'    private suspend fun ensureLocalHome(profileId: Int) {', '''    private suspend fun ensureLocalHome(profileId: Int) {
        repairHomeDefaults(profileId, false)''')
    replace(coordinator,'old!!.copy(importVod = true)', 'old!!.copy(importVod = true, enabled = true)')
    replace(coordinator,'        val guidePoolRevision = doc.optJSONObject("nativeConfig")',
        '        onDemand.start()\n        onDemand.loadCatalogOnDemand()\n        val guidePoolRevision = doc.optJSONObject("nativeConfig")')
    # A same-ID feed URL/login replacement must invalidate import timestamps, not wait a day.
    vod=source/'livetv/ondemand/OnDemandRepository.kt'
    replace(vod,'''            prefs.playlists
                .map { list -> list.filter { it.enabled && it.isXtream && it.importVod }.map { it.id }.toSet() }
                .distinctUntilChanged()
                .drop(1)
                .collect { refreshSoon() }''', '''            var previous: Map<String, List<String>>? = null
            prefs.playlists
                .map { list -> list.filter { it.enabled && it.isXtream && it.importVod }.associate {
                    it.id to listOf(it.xtreamServer, it.xtreamUsername, it.xtreamPassword)
                } }
                .distinctUntilChanged()
                .collect { current ->
                    val old = previous
                    previous = current
                    if (old != null) {
                        current.filter { (id, connection) -> old[id] != connection }.keys.forEach {
                            prefs.setOnDemandImportTime(it, 0L)
                        }
                        refreshSoon()
                    }
                }''')
    replace(vod,'System.currentTimeMillis() - REFRESH_MS + 60 * 60 * 1000L',
        'System.currentTimeMillis() - REFRESH_MS + 5 * 60 * 1000L')
    # Opt in only from a raw, explicit panel assignment. Old inherited assignments require off/on.
    settings=source/'petalix/PetalixPanelSettings.kt'
    replace(settings,'        native.applyPenguAssignment(values)', '''        val penguKey = "explicitPenguOptInV1:${profiles.activeProfileId.value}"
        if (!prefs.contains(penguKey)) prefs.edit().putBoolean(penguKey, !raw.optBoolean("addon:penguplay")).commit()
        if (!raw.optBoolean("addon:penguplay")) prefs.edit().putBoolean(penguKey, true).commit()
        val pengu = JSONObject(values.toString()).put("addon:penguplay",
            raw.optBoolean("addon:penguplay") && prefs.getBoolean(penguKey, false))
        if (raw.has("addon:penguplay_manifest")) pengu.put("addon:penguplay_manifest", raw.getString("addon:penguplay_manifest"))
        native.applyPenguAssignment(pengu)''')
    addons=source/'data/local/AddonPreferences.kt'
    replace(addons,'            if (prior == null && assigned == null) return@edit\n','')
    replace(addons,'val updated = current.filterNot { it == prior && it != assigned }.toMutableList()',
        'val updated = current.filterNot { (it == prior && it != assigned) || (assigned == null && it.contains("pengu", true)) }.toMutableList()')
    # All matching paths, including a manually mapped channel, pass through this guard.
    sports=source/'livetv/sports/SportsMatcher.kt'
    replace(sports,'        val byKey = input.channels.associateBy { it.key }',
        '        val eventById = input.events.associateBy { it.id }\n        val byKey = input.channels.associateBy { it.key }')
    replace(sports,'            if (key !in byKey || score <= 0) return', '''            if (key !in byKey || score <= 0) return
            val event = eventById[eventId]
            fun aliases(team: TeamInfo?) = team?.let { listOf(it.name, it.fullName, it.abbr) }.orEmpty()
            if (!com.nuvio.tv.petalix.PetalixHomeRepairPolicy.matchesReplay(
                    byKey.getValue(key).name, program?.title,
                    aliases(event?.home?.team), aliases(event?.away?.team))) return''')
    replace(sports, '            if (cur == null || score > cur.score) m[key] = Found(key, score, how, program ?: cur?.program)', '''            val matchLabel = if (byKey.getValue(key).name.contains("replay", true) && !program?.title.isNullOrBlank())
                "Replay: ${program!!.title}" else how
            if (cur == null || score > cur.score) m[key] = Found(key, score, matchLabel, program ?: cur?.program)''')
