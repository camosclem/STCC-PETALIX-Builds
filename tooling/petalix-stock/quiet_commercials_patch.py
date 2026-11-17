"""Persistent Live-TV compression option; its PCM path covers all app-supported Android versions."""
from pathlib import Path
from shutil import copy2
from home_visual_owner_patch import replace
HERE=Path(__file__).resolve().parent

def apply(root):
    source=root/'app/src/main/java/com/nuvio/tv'
    copy2(HERE/'PetalixQuietAudioProcessor.kt',source/'petalix/PetalixQuietAudioProcessor.kt')
    copy2(HERE/'PetalixQuietAudioProcessorTest.kt',root/'app/src/test/java/com/nuvio/tv/petalix/PetalixQuietAudioProcessorTest.kt')
    model=source/'livetv/model/LiveTvModels.kt'
    replace(model,'    val audioPassthrough: Boolean = true,','    val audioPassthrough: Boolean = true,\n    val quietCommercials: Boolean = false,')
    prefs=source/'livetv/data/LiveTvPreferences.kt'
    replace(prefs,'        val audioPassthrough = booleanPreferencesKey("audio_passthrough")', '        val quietCommercials = booleanPreferencesKey("petalix_quiet_commercials")\n        val audioPassthrough = booleanPreferencesKey("audio_passthrough")')
    replace(prefs,'            audioPassthrough = p[Keys.audioPassthrough] ?: d.audioPassthrough,', '            audioPassthrough = p[Keys.audioPassthrough] ?: d.audioPassthrough,\n            quietCommercials = p[Keys.quietCommercials] ?: d.quietCommercials,')
    replace(prefs,'        p[Keys.audioPassthrough] = s.audioPassthrough','        p[Keys.audioPassthrough] = s.audioPassthrough\n        p[Keys.quietCommercials] = s.quietCommercials')
    hooks=source/'livetv/player/LiveTvPlayerHooks.kt'
    replace(hooks,'                playback.audioPassthrough = it.audioPassthrough', '                playback.quietCommercials = it.quietCommercials\n                playback.audioPassthrough = it.audioPassthrough')
    player=source/'livetv/player/LiveTvPlaybackController.kt'
    text=player.read_text()
    start=text.index('    var audioPassthrough: Boolean = true')
    end=text.index('    @Volatile private var watchingFullscreen',start)
    text=text[:start]+text[start:end].replace('var audioPassthrough: Boolean = true','var quietCommercials: Boolean = false')+text[start:]
    player.write_text(text)
    replace(player,'        val passthrough = audioPassthrough', '        val passthrough = audioPassthrough && !quietCommercials\n        val quiet = quietCommercials')
    replace(player,'                    .setEnableFloatOutput(enableFloatOutput)', '''                    .setAudioProcessors(if (quiet) arrayOf(com.nuvio.tv.petalix.PetalixQuietAudioProcessor().apply {
                        setMode(com.nuvio.tv.data.local.NightMode.MEDIUM)
                    }) else emptyArray())
                    .setEnableFloatOutput(false)''')
    settings=source/'ui/screens/settings/LiveTvSettingsContent.kt'
    replace(settings,'                    SubHeading(context.getString(R.string.iptv_settings_picture_sound))', '''                    SubHeading(context.getString(R.string.iptv_settings_picture_sound))
                    SettingsToggleRow(
                        "Quiet Commercials", "Reduces sudden loud volume jumps in Live TV. Applies to all loud passages, including commercials.",
                        s.quietCommercials, { update { it.copy(quietCommercials = !it.quietCommercials) } }
                    )''')
    coordinator=source/'petalix/PetalixManagedCoordinator.kt'
    replace(coordinator,'showQualityBadges = false)', 'showQualityBadges = false, quietCommercials = true)')
