from pathlib import Path
from shutil import copy2, copytree

HERE = Path(__file__).resolve().parent

def replace(p, old, new):
    s = p.read_text()
    assert s.count(old) == 1, (p, old[:80], s.count(old))
    p.write_text(s.replace(old, new))

def apply(root):
    source = root / 'app/src/main/java/com/nuvio/tv'
    for name in ('PetalixHomeCategories.kt', 'PetalixSportsGuide.kt'):
        copy2(HERE / name, source / 'petalix' / name)
    copytree(HERE / 'home-art', root / 'app/src/main/assets/petalix/home-art', dirs_exist_ok=True)
    row = source / 'ui/screens/home/ModernHomeRowsList.kt'
    replace(row, '                if (liveTvHere && (liveTvRowAbove == true || !hasContinueWatching)) {',
            '''                if (row.key == com.nuvio.tv.petalix.PETALIX_SERVICES_ROW) {
                    com.nuvio.tv.petalix.PetalixHomeCategories()
                }
                if (liveTvHere && (liveTvRowAbove == true || !hasContinueWatching) && row.key != com.nuvio.tv.petalix.PETALIX_SERVICES_ROW) {''')
    cards = source / 'ui/screens/home/ModernHomeRows.kt'
    replace(cards, '    val imageUrl = petalixLogoUri ?: baseImageUrl', '''    val petalixArtwork = payload?.folderId?.let { com.nuvio.tv.petalix.petalixServiceArtworkUri(it) }
    val imageUrl = petalixArtwork ?: petalixLogoUri ?: baseImageUrl''')
    replace(cards, 'if (petalixLogoUri != null) Modifier.padding(16.dp) else Modifier',
            'if (petalixLogoUri != null && petalixArtwork == null) Modifier.padding(16.dp) else Modifier')
    replace(cards, 'contentScale = if (petalixLogoUri != null) ContentScale.Fit else imageContentScale,',
            '''contentScale = when {
                                petalixArtwork != null -> ContentScale.FillBounds
                                petalixLogoUri != null -> ContentScale.Fit
                                else -> imageContentScale
                            },''')
    actions = source / 'livetv/home/LiveTvHomeRow.kt'
    replace(actions, '    val openGuide: () -> Unit = {}', '    val openGuide: () -> Unit = {},\n    val openCategory: (String) -> Unit = {}')
    nav = source / 'ui/navigation/NuvioNavHost.kt'
    replace(nav, '                    openGuide = { openLiveTv() }', '''                    openGuide = { openLiveTv() },
                    openCategory = { title ->
                        openLiveTv()
                        navController.currentBackStackEntry?.savedStateHandle?.set("petalixHomeCategory", title)
                    }''')
    replace(nav, '''        composable(Screen.LiveTv.route) {
            com.nuvio.tv.livetv.ui.LiveTvGuideScreen(''', '''        composable(Screen.LiveTv.route) { entry ->
            val category by entry.savedStateHandle.getStateFlow<String?>("petalixHomeCategory", null).collectAsStateWithLifecycle()
            val viewModel: com.nuvio.tv.livetv.ui.LiveTvViewModel = androidx.hilt.navigation.compose.hiltViewModel(entry)
            androidx.compose.runtime.LaunchedEffect(category) {
                val title = category ?: return@LaunchedEffect
                val groups = (viewModel.awaitGroups() ?: viewModel.uiState.value).groups
                val match = if (title == "Sports") {
                    groups.firstOrNull { it.title.equals("Sports Networks", true) }
                        ?: groups.firstOrNull { it.title.equals(title, true) }
                } else groups.firstOrNull { it.title.equals(title, true) }
                if (match != null) viewModel.selectGroup(match.id)
                entry.savedStateHandle["petalixHomeCategory"] = null
            }
            com.nuvio.tv.livetv.ui.LiveTvGuideScreen(
                viewModel = viewModel,''')
    live = source / 'livetv/ui/LiveTvViewModel.kt'
    replace(live, '        buildUi(channels, user, s, groupIdRaw, query, hasSrc)',
            '''        val guideChannels = channels.filterNot {
            com.nuvio.tv.petalix.hideSportsEventInGuide(it.group, it.name)
        }
        buildUi(guideChannels, user, s, groupIdRaw, query, hasSrc)''')
    replace(live, '''        channelStore.displayChannels, com.nuvio.tv.petalix.PetalixPanelPolicy.showLocals
    ) { channels, showLocals ->
        if (showLocals) channels else channels.filterNot {
            Regex("(?i)(?:^|[^a-z])(?:locals?|local stations|local tv|ota)(?:$|[^a-z])").containsMatchIn(it.group)
        }''', '''        channelStore.displayChannels, com.nuvio.tv.petalix.PetalixPanelPolicy.showLocals,
        userState.map { state ->
            state.customGroups.firstOrNull { it.id == ChannelGroup.CUSTOM_PREFIX + "petalix_locals" }
                ?.channelKeys.orEmpty().toSet()
        }.distinctUntilChanged()
    ) { channels, showLocals, selectedKeys ->
        if (showLocals) channels else channels.filter { channel ->
            channel.key in selectedKeys || !localProviderGroup.containsMatchIn(channel.group)
        }''')
    replace(live, '    val displayChannels: StateFlow<List<LiveChannel>> = combine(',
            '''    private val localProviderGroup = Regex("(?i)(?:^|[^a-z])(?:locals?|local stations|local tv|ota)(?:$|[^a-z])")
    val displayChannels: StateFlow<List<LiveChannel>> = combine(''')
