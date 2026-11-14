"""Expose saved startup choices and clear live-channel recovery without resetting user data."""
from pathlib import Path
from shutil import copy2
from home_visual_owner_patch import replace

HERE = Path(__file__).resolve().parent


def apply(root):
    source = root / 'app/src/main/java/com/nuvio/tv'
    for name in ('PetalixDailyViewingPolicy.kt', 'PetalixPlaybackRecovery.kt'):
        copy2(HERE / name, source / 'petalix' / name)
    copy2(HERE / 'PetalixDailyViewingPolicyTest.kt',
          root / 'app/src/test/java/com/nuvio/tv/petalix/PetalixDailyViewingPolicyTest.kt')
    copy2(HERE / 'PetalixActualPlaybackTest.kt',
          root / 'app/src/test/java/com/nuvio/tv/petalix/PetalixActualPlaybackTest.kt')

    # The existing preferences and StartPage cache remain the only source of truth.
    settings = source / 'ui/screens/settings/LiveTvSettingsContent.kt'
    replace(settings, '            if (show(LiveTvPage.MAIN)) item(key = "petalix_locals") {', '''            if (show(LiveTvPage.MAIN)) item(key = "petalix_startup") {
                SettingsGroupCard {
                    SettingsActionRow(
                        "Open app on", "Choose Home, the Live TV guide, or your last channel",
                        when (com.nuvio.tv.petalix.petalixStartupChoice(s.openOn, s.autoPlayLastChannel)) {
                            "LAST_CHANNEL" -> "Last channel"
                            "LIVE_TV" -> "Live TV Guide"
                            else -> openOnLabel(s.openOn)
                        }, onClick = { dialog = LiveDialog.StartPage }
                    )
                }
            }
            if (show(LiveTvPage.MAIN)) item(key = "petalix_locals") {''')
    replace(settings, '                if (s.showInSidebar) add(SettingsPickerOption("LIVE_TV", "Live TV"))', '''                if (s.showInSidebar) {
                    add(SettingsPickerOption("LIVE_TV", "Live TV Guide"))
                    add(SettingsPickerOption("LAST_CHANNEL", "Last watched channel"))
                }''')
    replace(settings, '''            selectedValue = s.openOn,
            onOptionSelected = { v -> update { it.copy(openOn = v) }; close() },''', '''            selectedValue = com.nuvio.tv.petalix.petalixStartupChoice(s.openOn, s.autoPlayLastChannel),
            onOptionSelected = { v ->
                update { it.copy(
                    openOn = if (v == "LAST_CHANNEL") "LIVE_TV" else v,
                    autoPlayLastChannel = when (v) {
                        "LAST_CHANNEL" -> true
                        "LIVE_TV" -> false
                        else -> it.autoPlayLastChannel
                    }
                ) }
                close()
            },''')

    # Wait for the visible group before restoring the last watched row. No playback is started.
    guide = source / 'livetv/ui/LiveTvGuideScreen.kt'
    replace(guide, '''    LaunchedEffect(ui.selectedGroupId, channels.size) {
        if (highlightKey != null) return@LaunchedEffect''', '''    LaunchedEffect(ui.selectedGroupId, channels.size, user.lastChannelKey) {
        if (highlightKey != null || channels.isEmpty()) return@LaunchedEffect''')
    replace(guide, '''            val playingIdx = channels.indexOfFirst { it.key == playback.channelKey }
            row = if (playingIdx >= 0) playingIdx else row.coerceIn(0, (channels.size - 1).coerceAtLeast(0))''', '''            row = com.nuvio.tv.petalix.petalixGuideResumeRow(
                channels.map { it.key }, playback.channelKey, user.lastChannelKey,
                settings.rememberLastGroup, row
            )''')

    player = source / 'livetv/ui/LiveTvPlayerScreen.kt'
    replace(player, '    BackHandler(enabled = listVisible) { listVisible = false }', '''    val recoveryVisible = com.nuvio.tv.petalix.petalixPlaybackNeedsRecovery(
        playback.error != null, settings.autoReconnect, playback.reconnectAttempt
    ) && !listVisible && dialog == PlayerDialog.NONE && !panelFull
    BackHandler(enabled = recoveryVisible) { onBack() }
    LaunchedEffect(recoveryVisible) {
        if (!recoveryVisible && !listVisible && dialog == PlayerDialog.NONE) {
            runCatching { rootFocus.requestFocus() }
        }
    }
    BackHandler(enabled = listVisible) { listVisible = false }''')
    replace(player, '''                if (listVisible || dialog != PlayerDialog.NONE) return@onPreviewKeyEvent false''', '''                if (listVisible || dialog != PlayerDialog.NONE) return@onPreviewKeyEvent false
                // Recovery buttons own OK, Left/Right and Back; channel keys still work.
                if (recoveryVisible && e.key != Key.ChannelUp && e.key != Key.ChannelDown) {
                    return@onPreviewKeyEvent false
                }''')
    replace(player, '''            PlayerStatusMessage(
                title = if (playback.reconnectAttempt > 8) "Stream unavailable · press OK to retry"
                    else "Reconnecting… (attempt ${playback.reconnectAttempt})",
                detail = reason,
                modifier = Modifier.align(Alignment.Center)
            )''', '''            if (recoveryVisible) {
                com.nuvio.tv.petalix.PetalixPlaybackRecovery(
                    reason = reason, onRetry = { viewModel.playback.retry() }, onGuide = onBack,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (!listVisible && dialog == PlayerDialog.NONE) {
                PlayerStatusMessage(
                    title = if (!settings.autoReconnect || playback.reconnectAttempt > 8) "Channel unavailable"
                        else "Reconnecting… (attempt ${playback.reconnectAttempt})",
                    detail = reason, modifier = Modifier.align(Alignment.Center)
                )
            }''')
