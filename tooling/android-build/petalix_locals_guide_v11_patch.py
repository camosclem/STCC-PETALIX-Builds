"""Wait for usable channels, curate real local affiliates, and steady guide focus."""
from pathlib import Path
import shutil

def one(text, old, new, label):
    if text.count(old) != 1:
        raise RuntimeError("Petalix v11 anchor changed: " + label)
    return text.replace(old, new)

def apply(work):
    root = Path(work) / "app/src/main/java/com/nuvio/tv"
    ui = root / "petalix/ui2"
    shutil.copyfile(Path(__file__).with_name("PetalixLocalChannelsV11.kt"), ui / "PetalixLocalChannels.kt")

    p = ui / "Petalix2Home.kt"
    s = p.read_text()
    s = one(s, '    val channels by live.uiState.collectAsStateWithLifecycle()',
        '    val channels by live.uiState.collectAsStateWithLifecycle()\n    val liveStatus by live.status.collectAsStateWithLifecycle()',
        'playlist status on Home')
    s = one(s, '''    if(showLocalsSetup)PetalixLocalChannelsDialog(channels.allVisibleChannels,
        onClose={finishLocalsSetup()},onOpenGroup={group->live.selectGroup(group);finishLocalsSetup();onGuide()},onboarding=true)''',
        '''    if(showLocalsSetup && liveStatus.loadedOnce && !liveStatus.loading && channels.allVisibleChannels.isNotEmpty())
        PetalixLocalChannelsDialog(channels.allVisibleChannels,
            onClose={finishLocalsSetup()},onOpenGroup={group->live.selectGroup(group);finishLocalsSetup();onGuide()},
            onboarding=true,onCompleted={finishLocalsSetup()})''',
        'prompt only after playlist load')
    p.write_text(s)

    p = root / "livetv/data/LiveTvPreferences.kt"
    s = p.read_text()
    s = one(s, '''return groups.firstOrNull{it.id==com.nuvio.tv.livetv.model.ChannelGroup.CUSTOM_PREFIX+"petalix_locals"}?.channelKeys.orEmpty()''',
        '''return groups.firstOrNull{it.id==com.nuvio.tv.livetv.model.ChannelGroup.CUSTOM_PREFIX+"petalix_locals" ||
            it.name.equals("Local News",true) || it.name.equals("My Locals",true)}?.channelKeys.orEmpty()''',
        'read migrated local selection')
    s = one(s, '''        require(zip.matches(Regex("[0-9]{5}")))''',
        '''        require(zip.matches(Regex("[0-9]{5}")))
        require(keys.isNotEmpty()) { "Local News needs at least one playlist channel" }''',
        'never persist empty locals')
    p.write_text(s)

    p = root / "livetv/ui/LiveTvViewModel.kt"
    s = p.read_text()
    s = one(s, '''        val petalixCategories = com.nuvio.tv.petalix.ui2.PetalixCategoryLaunch.terms.keys.map { category ->
            ChannelGroup("__petalix__:"+category,category,visible.count { com.nuvio.tv.petalix.ui2.PetalixCategoryLaunch.matches(category,it.group) },special=true)
        }
        groups += petalixCategories
        groups += orderedRegular''',
        '''        // Local News is the user's saved playlist selection, never the generic News filter.
        val localGroup = orderedRegular.firstOrNull { it.id == ChannelGroup.CUSTOM_PREFIX+"petalix_locals" ||
            it.title.equals("Local News",true) || it.title.equals("My Locals",true) }
        localGroup?.let { groups += it }
        val petalixCategories = com.nuvio.tv.petalix.ui2.PetalixCategoryLaunch.terms.keys
            .filterNot { it == "Local News" }.map { category ->
                ChannelGroup("__petalix__:"+category,category,visible.count { com.nuvio.tv.petalix.ui2.PetalixCategoryLaunch.matches(category,it.group) },special=true)
            }
        groups += petalixCategories
        groups += orderedRegular.filterNot { it.id == localGroup?.id }''',
        'put saved local group before categories without duplicate')
    p.write_text(s)

    p = root / "livetv/ui/LiveTvGuideScreen.kt"
    s = p.read_text()
    s = one(s, '''    fun focusGroups() {
        groupsOpen=true
        scope.launch {
            val index=groupListIndex(ui.groups,ui.selectedGroupId,settings.groupPlaylistHeadings,collapsedPlaylists).coerceAtLeast(0)
            runCatching { groupsListState.scrollToItem(index) }
            repeat(2) { androidx.compose.runtime.withFrameNanos {} }
            if(!fullscreenComing && !fullscreen)runCatching { groupsFocus.requestFocus() }
        }
    }''',
        '''    fun focusGroups() {
        groupsOpen = true
        ui.groups.firstOrNull { it.id == ui.selectedGroupId }?.sourceId?.let { src ->
            if (src in viewModel.collapsedPlaylists.value) viewModel.togglePlaylistCollapsed(src)
        }
        scope.launch {
            repeat(2) { androidx.compose.runtime.withFrameNanos { } }
            val index = groupListIndex(ui.groups, ui.selectedGroupId,
                settings.groupPlaylistHeadings, viewModel.collapsedPlaylists.value).coerceAtLeast(0)
            if (index !in groupsListState.layoutInfo.visibleItemsInfo.map { it.index })
                runCatching { groupsListState.scrollToItem((index - 3).coerceAtLeast(0)) }
            for (attempt in 0 until 4) {
                repeat(3) { androidx.compose.runtime.withFrameNanos { } }
                if (fullscreenComing || fullscreen) break
                if (runCatching { groupsFocus.requestFocus() }.isSuccess) break
                runCatching { groupsListState.scrollToItem((index - 3).coerceAtLeast(0)) }
            }
        }
    }''', 'restore original guide focus travel')
    s = one(s, '"petalix-guide-finish-v10"',
        '"petalix-guide-finish-v10"); android.util.Log.i("PetalixGuide","petalix-locals-guide-v11"',
        'v11 marker')
    p.write_text(s)

    tests = Path(work) / "app/src/test/java/com/nuvio/tv/petalix/ui2"
    tests.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(Path(__file__).with_name("PetalixLocalSuggestionsTest.kt"),tests / "PetalixLocalSuggestionsTest.kt")
