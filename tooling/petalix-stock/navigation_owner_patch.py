"""Restore native focus transfer, fixed Home tabs, source priority and one ZIP locals group."""
from pathlib import Path
from shutil import copy2
from home_visual_owner_patch import replace

HERE = Path(__file__).resolve().parent

def replace_many(path, old, new, count):
    text = path.read_text()
    assert text.count(old) == count, (path, old[:70], text.count(old))
    path.write_text(text.replace(old, new))

def apply(root):
    source = root / 'app/src/main/java/com/nuvio/tv'
    for name in ('PetalixNavigationPolicy.kt', 'PetalixHomeHeader.kt'):
        copy2(HERE / name, source / 'petalix' / name)
    tests = root / 'app/src/test/java/com/nuvio/tv/petalix'
    copy2(HERE / 'PetalixNavigationPolicyTest.kt', tests / 'PetalixNavigationPolicyTest.kt')

    activity = source / 'MainActivity.kt'
    for old, new in (
        ('val openSidebarWidth = 92.dp', 'val openSidebarWidth = sidebarTokens.expandedWidth'),
        ('val sidebarLabelAlpha = 0f', 'val sidebarLabelAlpha = 1f'),
        ('iconOnly = true,', 'iconOnly = isFloatingPillIconOnly && !keepFloatingPillExpanded,'),
        ('val openDrawerWidth = 72.dp', 'val openDrawerWidth = sidebarTokens.legacyExpandedWidth'),
        ('val openDrawerItemWidth = 54.dp', 'val openDrawerItemWidth = sidebarTokens.itemWidth'),
        ('                                    expanded = false,', '                                    expanded = isExpanded,'),
        ('drawerState.setValue(DrawerValue.Closed)\n                                pendingContentFocusTransfer = false',
         'drawerState.setValue(DrawerValue.Closed)\n                                pendingContentFocusTransfer = true'),
        ('pendingContentFocusTransfer = false\n                                    sidebarCollapsePending = true',
         'pendingContentFocusTransfer = true\n                                    sidebarCollapsePending = true'),
    ):
        replace(activity, old, new)
    replace_many(activity, 'pendingContentFocusTransfer = currentRoute == targetRoute', 'pendingContentFocusTransfer = true', 1)
    replace_many(activity, '''        repeat(2) { withFrameNanos { } }
        runCatching { contentFocusRequester.requestFocus() }
        pendingContentFocusTransfer = false''', '''        repeat(12) {
            withFrameNanos { }
            if (runCatching { contentFocusRequester.requestFocus(FocusDirection.Enter) }.getOrDefault(false)) {
                pendingContentFocusTransfer = false
                return@LaunchedEffect
            }
        }
        pendingContentFocusTransfer = false''', 2)

    home = source / 'ui/screens/home/HomeScreen.kt'
    replace(home, 'fun HomeScreen(\n', 'fun HomeScreen(\n    petalixBrowseType: String? = null,\n')
    replace(home, '''            } && referenceShelves.values.any { matchesShelf(placeholder.catalogName, it) }''', '''            } && (petalixBrowseType?.let {
                com.nuvio.tv.petalix.matchesPetalixBrowseType(placeholder.apiType, it)
            } ?: referenceShelves.values.any { matchesShelf(placeholder.catalogName, it) })''')
    replace(home, '    val needsPlaylist = referenceShelves.values.any', '    val needsPlaylist = petalixBrowseType != null || referenceShelves.values.any')
    replace(home, '    val homeRows = remember(sourceState.homeRows, penguRows, playlistRows, homeChartRows) {', '''    val homeRows = remember(sourceState.homeRows, penguRows, playlistRows, homeChartRows, petalixBrowseType) {
        if (petalixBrowseType != null) {
            val selected = com.nuvio.tv.petalix.preferPetalixRows(
                penguRows.filter { com.nuvio.tv.petalix.matchesPetalixBrowseType(it.type.toApiString(), petalixBrowseType) },
                playlistRows.filter { com.nuvio.tv.petalix.matchesPetalixBrowseType(it.type.toApiString(), petalixBrowseType) }
            )
            return@remember selected.map { HomeRow.Catalog(it) }
        }''')
    replace(home, '        homeRows = homeRows, catalogRows = homeRows.filterIsInstance<HomeRow.Catalog>().map { it.row }', '''        continueWatchingEnabled = petalixBrowseType == null && sourceState.continueWatchingEnabled,
        heroItems = if (petalixBrowseType == null) sourceState.heroItems else emptyList(),
        homeRows = homeRows, catalogRows = homeRows.filterIsInstance<HomeRow.Catalog>().map { it.row }''')
    replace(home, '    val vodState by vodViewModel.home.collectAsStateWithLifecycle()',
            '    val vodState by vodViewModel.home.collectAsStateWithLifecycle()\n    val vodStatus by vodViewModel.status.collectAsStateWithLifecycle()')
    replace(home, '    val showStartupLoader = if (petalixFallback)',
            '    val showStartupLoader = if (petalixBrowseType != null) false else if (petalixFallback)')
    replace(home, '        when {\n            petalixFallback ->', '''        when {
            petalixBrowseType != null && uiState.catalogRows.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (sourceState.isLoading || vodStatus.loading) "Loading your library…"
                        else "No titles available yet. Refresh your account and try again.")
                }
            }
            petalixFallback ->''')

    nav = source / 'ui/navigation/NuvioNavHost.kt'
    replace(nav, 'import androidx.compose.runtime.Composable', '''import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable''')
    replace(nav, '            HomeScreen(\n', '''            var petalixBrowseType by androidx.compose.runtime.saveable.rememberSaveable {
                androidx.compose.runtime.mutableStateOf<String?>(null)
            }
            Column(Modifier.fillMaxSize()) {
                com.nuvio.tv.petalix.PetalixHomeHeader(
                    selectedType = petalixBrowseType,
                    onBrowse = { petalixBrowseType = it },
                    onLiveTv = { openLiveTv() },
                    onSports = { navController.navigate(Screen.Sports.route) },
                    onMyList = { navController.navigate(Screen.Library.route) }
                )
                Box(Modifier.weight(1f).fillMaxWidth()) {
            HomeScreen(
                petalixBrowseType = petalixBrowseType,
''')
    replace(nav, '''            )
            }
        }

        composable(
            route = Screen.Detail.route,''', '''            )
                }
            }
            }
        }

        composable(
            route = Screen.Detail.route,''')

    guide = source / 'livetv/ui/LiveTvViewModel.kt'
    replace(guide, '    val custom = user.customGroups\n', '''    val hasZipSelection = user.customGroups.any {
        it.id == com.nuvio.tv.petalix.PETALIX_ZIP_LOCALS && it.channelKeys.isNotEmpty()
    }
    val custom = user.customGroups
        .filter { com.nuvio.tv.petalix.showPetalixGuideGroup(it.id, it.name, hasZipSelection) }
''')
    replace(guide, '''    shown.forEach { c ->
        val cur = playlistGroups[c.groupId]''', '''    shown.filter { com.nuvio.tv.petalix.showPetalixGuideGroup(it.groupId, it.group, hasZipSelection) }.forEach { c ->
        val cur = playlistGroups[c.groupId]''')

    selector = source / 'core/player/StreamAutoPlaySelector.kt'
    replace(selector, '''    internal fun selectPanelPengu(streams: List<Stream>): Stream? =
        streams.firstOrNull { it.isPanelPengu && isPlayable(it) }''', '''    internal fun selectPanelPengu(streams: List<Stream>, allowVodFallback: Boolean = false): Stream? =
        com.nuvio.tv.petalix.preferredPetalixPlayback(
            streams.filter(::isPlayable), allowVodFallback,
            { it.isPanelPengu },
            { it.behaviorHints?.bingeGroup == com.nuvio.tv.livetv.ondemand.OnDemandStreams.BINGE_GROUP ||
                it.addonName == com.nuvio.tv.livetv.ondemand.OnDemandStreams.GROUP_NAME }
        )''')
    replace(source / 'ui/screens/stream/StreamScreenViewModel.kt', 'StreamAutoPlaySelector.selectPanelPengu(allStreams)',
            'StreamAutoPlaySelector.selectPanelPengu(allStreams, allowVodFallback = isAllLoaded)')
