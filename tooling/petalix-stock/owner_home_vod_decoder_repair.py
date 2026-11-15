"""Repair real Home focus, visible VOD import failures and software-AV1 startup recovery."""
from pathlib import Path
from shutil import copy2
from home_visual_owner_patch import replace

HERE = Path(__file__).resolve().parent


def apply(root):
    source = root / 'app/src/main/java/com/nuvio/tv'
    for name in ('PetalixHomeShortcuts.kt', 'PetalixImportRecovery.kt'):
        copy2(HERE / name, source / 'petalix' / name)
    copy2(HERE / 'PetalixHomeRecoveryTest.kt',
          root / 'app/src/test/java/com/nuvio/tv/petalix/PetalixHomeRecoveryTest.kt')

    home = source / 'ui/screens/home/HomeScreen.kt'
    replace(home, '        sourceState.homeRows.filterIsInstance<HomeRow.CollectionRow>() + rows.map { HomeRow.Catalog(it) }',
            '''        listOf(HomeRow.CollectionRow(com.nuvio.tv.petalix.petalixHomeLiveCollection())) +
            sourceState.homeRows.filterIsInstance<HomeRow.CollectionRow>().filter {
                it.collection.id != com.nuvio.tv.petalix.PETALIX_LIVE_SHORTCUT_COLLECTION
            } + rows.map { HomeRow.Catalog(it) }''')
    rows = source / 'ui/screens/home/ModernHomeRowsList.kt'
    replace(rows, '''                if (row.key == com.nuvio.tv.petalix.PETALIX_SERVICES_ROW) {
                    com.nuvio.tv.livetv.home.LiveTvHomeRow(above = true, startPadding = 52.dp, persistent = true)
                    com.nuvio.tv.petalix.PetalixHomeCategories()
                }
''', '')
    replace(rows, 'if (liveTvHere && (liveTvRowAbove == true || !hasContinueWatching) && row.key != com.nuvio.tv.petalix.PETALIX_SERVICES_ROW)',
            'if (liveTvHere && (liveTvRowAbove == true || !hasContinueWatching))')
    models = source / 'ui/screens/home/ModernHomeModels.kt'
    replace(models, '''    if (folder?.collectionId == com.nuvio.tv.petalix.PETALIX_SERVICES_COLLECTION) {''',
            '''    if (folder?.collectionId == com.nuvio.tv.petalix.PETALIX_LIVE_SHORTCUT_COLLECTION) {
        return ModernCatalogCardMetrics(width = landscapeCardWidth, height = landscapeCardWidth / 2.55f)
    }
    if (folder?.collectionId == com.nuvio.tv.petalix.PETALIX_SERVICES_COLLECTION) {''')
    replace(models, '            title = if (folder.hideTitle) "" else title,',
            '            title = if (folder.hideTitle && collection.id != com.nuvio.tv.petalix.PETALIX_SERVICES_COLLECTION) "" else title,')
    replace(models, '''            logo = folder.titleLogoUrl,
            description = null,''', '''            logo = folder.titleLogoUrl,
            description = if (collection.id == com.nuvio.tv.petalix.PETALIX_LIVE_SHORTCUT_COLLECTION)
                com.nuvio.tv.petalix.petalixHomeShortcut(folder.id)?.description else null,''')
    nav = source / 'ui/navigation/NuvioNavHost.kt'
    replace(nav, '''                onNavigateToFolderDetail = { collectionId, folderId ->
                    navController.navigate(Screen.FolderDetail.createRoute(collectionId, folderId))
                }''', '''                onNavigateToFolderDetail = { collectionId, folderId ->
                    val shortcut = com.nuvio.tv.petalix.petalixHomeShortcut(folderId)
                        ?.takeIf { collectionId == com.nuvio.tv.petalix.PETALIX_LIVE_SHORTCUT_COLLECTION }
                    if (shortcut != null) {
                        openLiveTv()
                        navController.currentBackStackEntry?.savedStateHandle?.set("petalixHomeCategory", shortcut.title)
                    } else navController.navigate(Screen.FolderDetail.createRoute(collectionId, folderId))
                }''')
    hero = source / 'ui/screens/home/ModernHomeContent.kt'
    replace(hero, '''                        val displayedBackdrop = HeroBackdropState.lastDisplayedUrl
                        val corrected = if (!displayedBackdrop.isNullOrBlank() &&
                            displayedBackdrop != currentStable.heroBackdrop
                        ) {
                            currentStable.copy(heroBackdrop = displayedBackdrop)
                        } else {
                            currentStable
                        }
                        stableHeroSceneStateRef.value = corrected''',
            '                        stableHeroSceneStateRef.value = currentStable')
    replace(hero, 'isScrolling && stableHasPreview -> stable\n', 'isScrolling && stableHasPreview -> currentLive\n')
    replace(hero, 'isRapidNav -> currentLive.copy(preview = null, enrichmentActive = false)', 'isRapidNav -> currentLive')
    replace(hero, 'isScrolling && stableHasPreview -> stable!!.copy(heroBackdrop = currentLive.heroBackdrop)',
            'isScrolling && stableHasPreview -> currentLive')
    replace(hero, 'isRapidNav && stable != null -> stable.copy(heroBackdrop = currentLive.heroBackdrop)',
            'isRapidNav && stable != null -> currentLive')

    repo = source / 'livetv/ondemand/OnDemandRepository.kt'
    replace(repo, '                _status.value = OnDemandStatus()\n',
            '                _status.value = com.nuvio.tv.petalix.finishPetalixVodLoad(_status.value)\n')
    replace(repo, '''                .onFailure { Log.w(TAG, "Movies for ${pl.name} failed", it); lastProblem = "Couldn't import movies from ${pl.name}: ${it.message ?: it.javaClass.simpleName}" }''',
            '''                .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; Log.w(TAG, "Movies for ${pl.name} failed", it); lastProblem = "Couldn't import movies from ${pl.name}: ${it.message ?: it.javaClass.simpleName}" }
            _hasContent.value = runCatching { db.hasAny() }.getOrDefault(_hasContent.value)
            _version.value++''')
    replace(repo, '''                .onFailure { Log.w(TAG, "Series for ${pl.name} failed", it); lastProblem = "Couldn't import series from ${pl.name}: ${it.message ?: it.javaClass.simpleName}" }''',
            '''                .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; Log.w(TAG, "Series for ${pl.name} failed", it); lastProblem = "Couldn't import series from ${pl.name}: ${it.message ?: it.javaClass.simpleName}" }
            _hasContent.value = runCatching { db.hasAny() }.getOrDefault(_hasContent.value)''')
    replace(repo, '        _status.value = OnDemandStatus(false, problemByPlaylist.values.joinToString("\\n").ifBlank { null })',
            '''        val noContent = if (_hasContent.value) null else if (playlists.isEmpty())
            "Your assigned playlist is not enabled for movies and series. Check the VOD source in the panel."
        else "Your provider returned no movies or TV shows. Check the assigned VOD source, then retry."
        _status.value = OnDemandStatus(false, problemByPlaylist.values.joinToString("\\n").ifBlank { noContent })''')
    browser = source / 'livetv/ondemand/OnDemandHomeScreen.kt'
    replace(browser, '    LaunchedEffect(Unit) { viewModel.loadHome() }', '''    val importStatus by viewModel.status.collectAsStateWithLifecycle()
    val catalogVersion by viewModel.catalogVersion.collectAsStateWithLifecycle()
    LaunchedEffect(catalogVersion) { viewModel.loadHome() }''')
    replace(browser, '            state.loading && rows.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }',
            '''            (state.loading || importStatus.loading) && rows.isEmpty() -> Column(
                Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LoadingIndicator()
                Spacer(Modifier.height(12.dp))
                LiveText(importStatus.message ?: "Loading your VOD library…", size = 14.sp, color = NuvioTheme.colors.TextSecondary)
            }''')
    replace(browser, '                    "Turn on On Demand for a playlist in Settings → Live TV, or show some groups with the settings button.",',
            '                    importStatus.message ?: "Turn on On Demand for a playlist in Settings → Live TV, or show some groups with the settings button.",')
    replace(browser, '''                    size = 14.sp, color = NuvioTheme.colors.TextSecondary
                )
            }
            seeAllUid != null ->''', '''                    size = 14.sp, color = NuvioTheme.colors.TextSecondary
                )
                Spacer(Modifier.height(16.dp))
                androidx.tv.material3.Button(onClick = viewModel::refreshNow) {
                    androidx.tv.material3.Text("Retry VOD import")
                }
            }
            seeAllUid != null ->''')
    coordinator = source / 'petalix/PetalixManagedCoordinator.kt'
    replace(coordinator, '            val assigned = if (same) old!! else PlaylistSource(',
            '            val assigned = if (same) old!!.copy(importVod = true) else PlaylistSource(')

    player = source / 'ui/screens/player/PlayerRuntimeControllerInitialization.kt'
    replace(player, '                preferSoftwareAudioOnly = isBluetoothAudioOutput,',
            '                preferSoftwareAudioOnly = true,')
    replace(player, 'if (maybeAutoSwitchInternalPlayerOnStartupError(detailedError = detailedError, allowEngineFailover = allowEngineFailover))',
            '''if (maybeAutoSwitchInternalPlayerOnStartupError(
                                detailedError = detailedError, allowEngineFailover = allowEngineFailover,
                                softwareAv1Recovery = com.nuvio.tv.petalix.petalixSoftwareAv1Recovery(
                                    error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED, detailedError
                                )
                            ))''')
    recovery = source / 'ui/screens/player/PlayerRuntimeControllerEngineFailover.kt'
    replace(recovery, '    allowEngineFailover: Boolean\n): Boolean {',
            '    allowEngineFailover: Boolean, softwareAv1Recovery: Boolean = false\n): Boolean {')
    replace(recovery, '    if (!autoSwitchInternalPlayerOnErrorEnabled) return false',
            '    if (!autoSwitchInternalPlayerOnErrorEnabled && !softwareAv1Recovery) return false')

    sports = source / 'livetv/sports/SportsRepository.kt'
    replace(sports, """            channelStore.displayChannels.map { list ->
                var h = 17L
                for (c in list) h = h * 31 + c.key.hashCode()
                list.size to h
            }.distinctUntilChanged(),""", """            channelStore.displayChannels.map { list ->
                com.nuvio.tv.petalix.petalixSportsChannelSignature(list)
            }.distinctUntilChanged(),""")
