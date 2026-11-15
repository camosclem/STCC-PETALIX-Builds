"""Keep the native sidebar, Home browsing, source priority and one ZIP locals group."""
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
    for name in ('PetalixNavigationPolicy.kt',):
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
