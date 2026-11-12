from pathlib import Path
from shutil import copy2
from home_visual_owner_patch import replace

HERE = Path(__file__).resolve().parent

def apply(root):
    source = root / 'app/src/main/java/com/nuvio/tv'
    for name in ('PetalixPlaybackTarget.kt', 'PetalixAccount.kt'):
        copy2(HERE / name, source / 'petalix' / name)

    panel = source / 'petalix/PetalixPanelSettings.kt'
    replace(panel, '        if (stamp == prefs.getString("stamp", "")', '''        native.applyPenguAssignment(values)
        if (stamp == prefs.getString("stamp", "")''')
    settings = source / 'petalix/ManagedNativeSettings.kt'
    replace(settings, '    suspend fun apply(values:JSONObject)', '''    suspend fun applyPenguAssignment(values: JSONObject) {
        if (values.optBoolean("addon:penguplay")) {
            val manifest = values.getString("addon:penguplay_manifest")
            val url = manifest.toHttpUrlOrNull()
            require(url != null && url.isHttps && url.username.isEmpty() && url.password.isEmpty() &&
                url.fragment == null && url.encodedPath.endsWith("/manifest.json", true))
            addons.applyManagedPengu(manifest)
        } else addons.applyManagedPengu(null)
    }

    suspend fun apply(values:JSONObject)''')
    replace(settings, '        addons.applyManagedPengu(values.optString("addon:penguplay_manifest").takeIf { values.optBoolean("addon:penguplay") })\n','')
    addon = source / 'data/local/AddonPreferences.kt'
    replace(addon, '    suspend fun applyManagedPengu(manifest: String?) = applyPanelAddon("pengu", manifest)', '''    private val managedPenguKey = stringPreferencesKey("managed_panel_pengu")
    internal val managedPenguUrl: Flow<String?> = effectiveProfileIdFlow.flatMapLatest { id ->
        store(id).data.map { it[managedPenguKey]?.let(::canonicalizeUrl) }
    }.distinctUntilChanged()

    suspend fun applyManagedPengu(manifest: String?) {
        val assigned = manifest?.let(::canonicalizeUrl)
        store().edit { preferences ->
            val prior = preferences[managedPenguKey]?.let(::canonicalizeUrl)
            if (prior == null && assigned == null) return@edit
            val current = getCurrentList(preferences).map(::canonicalizeUrl)
            val updated = current.filterNot { it == prior && it != assigned }.toMutableList()
            val oldStates = getCurrentEnabledStates(preferences)
            val states = oldStates.toMutableMap()
            if (prior != null && prior != assigned) states.remove(prior)
            if (assigned == null) preferences.remove(managedPenguKey)
            else {
                if (assigned !in updated) updated += assigned
                states[assigned] = true
                preferences[managedPenguKey] = assigned
            }
            if (updated != current) {
                preferences[orderedUrlsKey] = gson.toJson(updated)
                preferences.remove(legacyUrlsKey)
            }
            if (states != oldStates) preferences[addonEnabledStatesKey] = gson.toJson(states)
        }
    }''')
    coordinator = source / 'petalix/PetalixManagedCoordinator.kt'
    replace(coordinator, '    val shouldPromptForLocals:', '''    internal data class AccountSummary(val name: String?, val activated: Boolean, val active: Boolean, val expiry: Long?)

    internal fun accountSummary(): AccountSummary {
        val activated = hasSavedActivation
        val expiry = runCatching { Instant.parse(prefs.getString("expires", "")).toEpochMilli() }.getOrNull()
        return AccountSummary(prefs.getString("accountName", null)?.takeIf { activated }, activated,
            activated && !prefs.getBoolean("accessDenied", false) && expiry != null && expiry > System.currentTimeMillis(), expiry)
    }

    val shouldPromptForLocals:''')
    replace(coordinator, '            check(prefs.edit().putString("expires", expiry).putLong("verified", System.currentTimeMillis()).commit())', '''            val editor = prefs.edit().putString("expires", expiry).putLong("verified", System.currentTimeMillis())
            doc.optString("customer", "").trim().takeIf { it.isNotEmpty() }?.let { editor.putString("accountName", it.take(120)) }
            check(editor.commit())''')
    screen = source / 'ui/screens/settings/SettingsScreen.kt'
    replace(screen, '''        SettingsCategory.ACCOUNT -> AccountSettingsInline(
            onNavigateToAuthQrSignIn = onNavigateToAuthQrSignIn,
''', '''        SettingsCategory.ACCOUNT -> AccountSettingsInline(
''')
    s = screen.read_text()
    start = s.index('private fun AccountSettingsInline(')
    end = s.index('\n@Composable',start)
    screen.write_text(s[:start]+'''private fun AccountSettingsInline(initialFocusRequester: FocusRequester?) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md)) {
        SettingsDetailHeader(title = stringResource(R.string.settings_account), subtitle = "Your Petalix subscription")
        PetalixAccountSummary(initialFocusRequester)
    }
}
'''+s[end:])
    a = source / 'core/streams/PlaybackAvailability.kt'
    replace(a, '    fun canStream(', '''    fun canRequestPlayback(type: String, videoId: String, contentId: String = videoId, video: Video? = null): Boolean =
        video?.available != false && (com.nuvio.tv.petalix.canLookUpPlayback(type, videoId, contentId, video?.episode)
            || canStream(type, videoId, contentId, video))

    fun canStream(''')
    detail = source / 'ui/screens/detail/MetaDetailsScreen.kt'
    s = detail.read_text()
    assert s.count('playbackAvailability.canStream(') >= 8
    detail.write_text(s.replace('playbackAvailability.canStream(', 'playbackAvailability.canRequestPlayback('))
    provider = source / 'ui/components/PlaybackAvailabilityProvider.kt'
    s = provider.read_text()
    assert s.count('-> canStream(') == 2
    provider.write_text(s.replace('-> canStream(', '-> canRequestPlayback('))
    model = source / 'ui/screens/detail/MetaDetailsViewModel.kt'
    s = model.read_text()
    assert s.count('tryApplyTmdbFallbackMeta()') == 3
    s = s.replace('tryApplyTmdbFallbackMeta()', 'tryApplyTmdbFallbackMeta(metaLookupId)').replace('private suspend fun tryApplyTmdbFallbackMeta(metaLookupId):','private suspend fun tryApplyTmdbFallbackMeta(metaLookupId: String):')
    model.write_text(s)
    replace(model, '        val meta = Meta(\n            id = itemId,', '''        val videos = if (type == ContentType.SERIES || type == ContentType.TV) {
            enrichment.seasons.flatMap { season ->
                val number = season.seasonNumber ?: return@flatMap emptyList()
                val count = season.episodeCount ?: return@flatMap emptyList()
                (1..count).map { episode ->
                    Video(id = "$metaLookupId:$number:$episode", title = context.getString(R.string.ratings_episode_label, episode),
                        released = null, thumbnail = null, season = number, episode = episode, overview = null)
                }
            }
        } else emptyList()
        val meta = Meta(
            id = metaLookupId,''')
    replace(model, '            videos = emptyList(),\n            productionCompanies', '            videos = videos,\n            productionCompanies')
    tmdb = source / 'core/tmdb/TmdbMetadataService.kt'
    replace(tmdb, '                    trailers = trailers\n                )',
            '                    trailers = trailers,\n                    seasons = details?.seasons.orEmpty()\n                )')
    replace(tmdb, '    val trailers: List<MetaTrailer> = emptyList()\n)',
            '    val trailers: List<MetaTrailer> = emptyList(),\n    val seasons: List<com.nuvio.tv.data.remote.api.TmdbSeasonSummary> = emptyList()\n)')
    repo = source / 'data/repository/StreamRepositoryImpl.kt'
    replace(repo, '    private val serverStreams: ServerStreams\n', '''    private val serverStreams: ServerStreams,
    private val addonPreferences: com.nuvio.tv.data.local.AddonPreferences? = null
''')
    replace(repo, '        val profileId: Int,\n        val addons:', '        val profileId: Int,\n        val penguUrl: String?,\n        val addons:')
    replace(repo, '        val sourceConfiguration = captureSourceConfiguration()\n        val isNativeServerRequest = serverStreams.isNativeRequest(videoId)', '''        val isNativeServerRequest = serverStreams.isNativeRequest(videoId)
        val usePengu = !isNativeServerRequest && !continuePluginOnly &&
            com.nuvio.tv.petalix.isPetalixVodType(type)
        val sourceConfiguration = captureSourceConfiguration(resolvePengu = usePengu)''')
    replace(repo, '                    debridSettings = sourceConfiguration.debridSettings,', '''                    usePengu = usePengu,
                    penguUrl = sourceConfiguration.penguUrl,
                    debridSettings = sourceConfiguration.debridSettings,''')
    replace(repo, '        sourceConfiguration = buildSourceConfigurationKey(', '''        sourceConfiguration = sourceConfiguration.penguUrl?.hashCode().toString() + ":" + buildSourceConfigurationKey(''')
    replace(repo, 'private suspend fun captureSourceConfiguration(): StreamSourceConfigurationSnapshot', 'private suspend fun captureSourceConfiguration(resolvePengu: Boolean = false): StreamSourceConfigurationSnapshot')
    replace(repo, '            val addons = addonRepository.getInstalledAddons().first().enabledAddons()', '''            val penguUrl = addonPreferences?.managedPenguUrl?.first()
            val installed = addonRepository.getInstalledAddons().first()
            val pengu = if (resolvePengu && penguUrl != null) {
                val current = installed.firstOrNull { it.baseUrl == penguUrl }
                when {
                    current?.enabled == false -> null
                    current?.resources?.any { it.name == "stream" } == true -> current
                    else -> (kotlinx.coroutines.withTimeoutOrNull(8_000L) {
                        addonRepository.fetchAddon(penguUrl)
                    } as? NetworkResult.Success)?.data
                }
            } else null
            val addons = if (pengu == null) installed.enabledAddons()
                else installed.enabledAddons().filterNot { it.baseUrl == pengu.baseUrl } + pengu''')
    replace(repo, '                profileId = profileId,\n                addons = addons,', '                profileId = profileId,\n                penguUrl = penguUrl,\n                addons = addons,')
    replace(repo, '        addons: List<Addon>,\n        debridSettings:', '        addons: List<Addon>,\n        usePengu: Boolean,\n        penguUrl: String?,\n        debridSettings:')
    replace(repo, '''                addons.filter { addon ->
                    addon.supportsStreamResource(type, videoId)
                }''', '''                val direct = addons.filter { it.supportsStreamResource(type, videoId) }
                val tmdbId = com.nuvio.tv.petalix.tmdbPlaybackId(videoId)
                val pendingImdb = if (tmdbId == null) emptyList() else addons.filter {
                    it !in direct && it.supportsStreamResource(type, "tt1234567")
                }
                val imdbId = if (tmdbId != null && pendingImdb.isNotEmpty()) {
                    kotlinx.coroutines.withTimeoutOrNull(5_000L) { tmdbService.tmdbToImdb(tmdbId, type) }
                        ?.takeIf { com.nuvio.tv.petalix.isImdbPlaybackId(it) }
                        ?.let { com.nuvio.tv.petalix.alternatePlaybackId(videoId, type, it, season, episode) }
                } else null
                val selected = direct.map { it to videoId } + pendingImdb.mapNotNull { addon ->
                    imdbId?.takeIf { addon.supportsStreamResource(type, it) }?.let { addon to it }
                }
                val pengu = addons.firstOrNull { usePengu && it.baseUrl == penguUrl }
                if (pengu != null && selected.none { it.first == pengu } &&
                    pengu.supportsStreamResource(type, "tmdb:1") && com.nuvio.tv.petalix.isImdbPlaybackId(videoId)) {
                    val target = kotlinx.coroutines.withTimeoutOrNull(5_000L) { tmdbService.ensureTmdbId(videoId, type) }
                        ?.toIntOrNull()?.takeIf { it > 0 }
                        ?.let { com.nuvio.tv.petalix.alternatePlaybackId(videoId, type, "tmdb:$it", season, episode) }
                    if (target != null && pengu.supportsStreamResource(type, target)) selected + (pengu to target) else selected
                } else selected''')
    replace(repo, 'streamAddons.map { it.displayName }', 'streamAddons.map { (addon, _) -> addon.displayName }')
    replace(repo, 'streamAddons.forEach { addon ->', 'streamAddons.forEach { (addon, sourceVideoId) ->')
    replace(repo, '                            val streamsResult = getStreamsFromAddon(addon, type, videoId)', '''                            val isPengu = usePengu && addon.baseUrl == penguUrl
                            val streamsResult = getStreamsFromAddon(addon, type, sourceVideoId)''')
    replace(repo, '                                            addon, type, videoId', '                                            addon, type, sourceVideoId')
    replace(repo, '                                            it.copy(addonName = addon.displayName, addonLogo = addon.logo)', '''                                            it.copy(addonName = addon.displayName, addonLogo = addon.logo, isPanelPengu = isPengu)''')
    replace(repo, '                                                    streams = inlineStreams', '''                                                    streams = if (isPengu) inlineStreams.map { it.copy(isPanelPengu = true) } else inlineStreams''')
    replace(repo, '                val errorMessage = buildAggregateFailureMessage(', '''                val penguMessage = if (usePengu && attemptedAddonNames.isEmpty()) when {
                    penguUrl == null -> "Pengu Play is not assigned. Check Settings → Account → Refresh account."
                    addons.none { addon -> addon.baseUrl == penguUrl && addon.resources.any { it.name == "stream" } } ->
                        "Could not load Pengu Play. Refresh your account and retry."
                    else -> "Pengu Play has no compatible source for this title."
                } else null
                val errorMessage = penguMessage ?: buildAggregateFailureMessage(''')
    replace(source / 'domain/model/Stream.kt', '    val pluginSourceName: String? = null\n',
            '    val pluginSourceName: String? = null,\n    val isPanelPengu: Boolean = false\n')
    selector = source / 'core/player/StreamAutoPlaySelector.kt'
    replace(selector, '    private fun isPlayable(stream: Stream): Boolean {', '''    internal fun selectPanelPengu(streams: List<Stream>): Stream? =
        streams.firstOrNull { it.isPanelPengu && isPlayable(it) }

    private fun isPlayable(stream: Stream): Boolean {''')
    vm = source / 'ui/screens/stream/StreamScreenViewModel.kt'
    replace(vm, '            val playerSettings = playerSettingsDataStore.playerSettings.first()\n            if (manualSelection)', '''            val playerSettings = playerSettingsDataStore.playerSettings.first()
            val preferPengu = !manualSelection && !serverStreams.isNativeRequest(videoId) &&
                com.nuvio.tv.petalix.isPetalixVodType(contentType)
            if (preferPengu && forceRefresh) {
                autoPlayHandledForSession = false
                directAutoPlayModeInitializedForSession = false
            }
            if (manualSelection)''')
    replace(vm, 'directAutoPlayFlowEnabledForSession = shouldUseDirectAutoPlayFlow(', 'directAutoPlayFlowEnabledForSession = preferPengu || shouldUseDirectAutoPlayFlow(')
    replace(vm, '                playerSettings.streamAutoPlayMode == StreamAutoPlayMode.REGEX_MATCH &&', '                !preferPengu && playerSettings.streamAutoPlayMode == StreamAutoPlayMode.REGEX_MATCH &&')
    replace(vm, '            if (!autoPlayHandledForSession && playerSettings.streamReuseLastLinkEnabled)', '            if (!preferPengu && !autoPlayHandledForSession && playerSettings.streamReuseLastLinkEnabled)')
    replace(vm, '            val persistedBingeGroup = if (playerSettings.streamAutoPlayPreferBingeGroupForNextEpisode &&', '            val persistedBingeGroup = if (!preferPengu && playerSettings.streamAutoPlayPreferBingeGroupForNextEpisode &&')
    replace(vm, '                val selectedAutoPlayStream = if (earlyBingeGroupMatch != null) {', '''                val selectedAutoPlayStream = if (preferPengu) {
                    if (!autoPlayHandledForSession && !resolvedAutoPlayTarget) {
                        StreamAutoPlaySelector.selectPanelPengu(allStreams)
                    } else null
                } else if (earlyBingeGroupMatch != null) {''')
    replace(vm, '                            } else if (timeoutElapsed) {', '                            } else if (timeoutElapsed && !preferPengu) {')
    replace(vm, '            if (directFlowActive && !resolvedAutoPlayTarget && lastSuccessData != null && !isUnlimitedTimeout)', '            if (!preferPengu && directFlowActive && !resolvedAutoPlayTarget && lastSuccessData != null && !isUnlimitedTimeout)')
    replace(vm, '''            if (shouldAttemptEmbeddedMetaStreamLookup()) {
                getEmbeddedStreamsFromMeta()?.let { embeddedAddonStreams ->''', '''            val embeddedStreams = if (shouldAttemptEmbeddedMetaStreamLookup()) getEmbeddedStreamsFromMeta() else null
            if (!preferPengu) {
                embeddedStreams?.let { embeddedAddonStreams ->''')
    replace(vm, '''                        priorityBingeGroup = persistedBingeGroup
                    ),
                    type = contentType,''', '''                        priorityBingeGroup = persistedBingeGroup
                    ).map { result ->
                        if (!preferPengu || embeddedStreams == null) result
                        else when (result) {
                            is NetworkResult.Success -> NetworkResult.Success(result.data + embeddedStreams)
                            is NetworkResult.Error -> NetworkResult.Success(listOf(embeddedStreams))
                            NetworkResult.Loading -> result
                        }
                    },
                    type = contentType,''')
    db = source / 'livetv/ondemand/OnDemandDatabase.kt'
    replace(db, '    fun replace(playlistId: String, kind: VodKind, categories: List<Pair<String, String>>, items: Sequence<VodItem>): Int {', '''    fun replace(
        playlistId: String, kind: VodKind, categories: List<Pair<String, String>>,
        items: Sequence<VodItem>, complete: () -> Boolean = { true }
    ): Int {''')
    category_start = '''            db.delete("categories", "pl = ? AND kind = ?", arrayOf(playlistId, kind.key))
            categories.forEachIndexed { i, (id, name) ->
                db.insertWithOnConflict("categories", null, ContentValues().apply {
                    put("pl", playlistId); put("kind", kind.key); put("id", id); put("name", name); put("position", i)
                }, SQLiteDatabase.CONFLICT_REPLACE)
            }
'''
    replace(db, category_start, '')
    replace(db, '''            // Titles the provider no longer lists.
            if (existing.size > seen.size) {''', '''            val fullyImported = complete()
            if (fullyImported && categories.isNotEmpty()) {
                db.delete("categories", "pl = ? AND kind = ?", arrayOf(playlistId, kind.key))
            }
''' + category_start.split('\n', 1)[1] + '''            if (fullyImported && existing.size > seen.size) {''')
    vod = source / 'livetv/ondemand/OnDemandRepository.kt'
    replace(vod, '''            streamItems(pl, kind, "$api&action=$listAction", onStop = { stoppedEarly = it }, onItem = ::progress)
''', '''            streamItems(pl, kind, "$api&action=$listAction", onStop = { stoppedEarly = it }, onItem = ::progress),
            complete = { stoppedEarly == null }
''')
    replace(vod, 'if (problem?.message?.contains("429") == true) break', 'if (problem?.message in setOf("HTTP 429", "HTTP 503")) break')
    s = vod.read_text()
    start = s.index('        val total = db.replace(pl.id, kind, categories, byCategory)')
    end = s.index('\n    }', start)
    vod.write_text(s[:start]+'''        val total = db.replace(pl.id, kind, categories, byCategory, complete = { failedCategories == 0 })
        if (failedCategories > 0) {
            lastProblem = "${pl.name}: $what import incomplete (${firstProblem.message ?: "provider error"}). " +
                "Previously imported titles were kept."
            if (saved == 0 && total == 0) throw firstProblem
        }'''+s[end:])
    replace(vod, '''                    if (reader.peek() != JsonToken.BEGIN_ARRAY) { reader.skipValue(); return@sequence }''', '''                    if (reader.peek() != JsonToken.BEGIN_ARRAY) {
                        onStop(IllegalStateException("Provider did not return a catalog list"))
                        return@sequence
                    }''')
    vod_stream = source / 'livetv/ondemand/OnDemandStreams.kt'
    replace(vod_stream, '            emit(emptyList<Stream>() to "LAST")', '            emit(null as Pair<List<Stream>, String>?)')
    replace(vod_stream, '            if (found.isNotEmpty()) emit(found to position)', '            emit(found to position)')
    replace(vod_stream, '''        return combine(source, onDemand) { result, (extra, position) ->
            when {''', '''        return combine(source, onDemand) { result, provider ->
            if (provider == null) {
                return@combine when (result) {
                    is NetworkResult.Error -> NetworkResult.Loading
                    is NetworkResult.Success -> if (result.data.isEmpty()) NetworkResult.Loading else result
                    else -> result
                }
            }
            val (extra, position) = provider
            when {''')
