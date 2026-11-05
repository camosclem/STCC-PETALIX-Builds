"""Extend native panel settings and honor controls in Petalix UI2."""
from pathlib import Path
import json
import shutil


def one(text, old, new, label):
    if text.count(old) != 1:
        raise RuntimeError("Petalix v13 anchor changed: " + label)
    return text.replace(old, new)


def apply(work):
    work = Path(work)
    root = work / "app/src/main/java/com/nuvio/tv"
    extra = json.loads(Path(__file__).with_name("extended-native-controls.json").read_text())
    p = work / "app/src/main/assets/managed-native-controls.json"
    controls = json.loads(p.read_text())
    known = {c['key'] for c in controls}
    controls += [c for c in extra if c['key'] not in known]
    p.write_text(json.dumps(controls, indent=2))

    p = root / "petalix/ManagedNativeSettings.kt"
    s = p.read_text()
    s = one(s, '        "string"->stringPreferencesKey(preference)',
        '        "string"->stringPreferencesKey(preference)\n        "stringSet"->stringSetPreferencesKey(preference)', "set preference type")
    s = one(s, '    fun native(value:Any,checkValue:Boolean=true):Any {', '''    fun native(value:Any,checkValue:Boolean=true):Any {
        if(kind=="stringSet") {
            val array=when(value) { is String->JSONArray(value); is JSONArray->value; else->error("Invalid set value") }
            require(array.length()<=500)
            return (0 until array.length()).map { i ->
                val item=array.get(i);require(item is String && item.length<=2000);item as String
            }.toSet()
        }''', "set wire format")
    s = one(s, 'before.put(s.key,old[s.typed]?:JSONObject.NULL)',
        'before.put(s.key,(old[s.typed] as? Set<*>)?.let { JSONArray(it.toList()) } ?: old[s.typed] ?: JSONObject.NULL)', "set originals restore")
    s = one(s, '    private val collections:CollectionsDataStore,',
        '''    private val collections:CollectionsDataStore,
    private val profileSettings:ProfileDataStore,
    private val sentrySettings:SentrySettingsDataStore,
    private val torrentSettings:com.nuvio.tv.core.torrent.TorrentSettings,''', "standalone settings stores")
    s = one(s, 'setOf("sports","live_tv","device_local_player_prefs")',
        'setOf("sports","live_tv","device_local_player_prefs","profile_settings","sentry_settings","torrent_settings")', "device settings backup scope")
    s = one(s, '"device_local_player_prefs"->device.managedPreferences();else->factory.get(active,feature)',
        '"device_local_player_prefs"->device.managedPreferences();"profile_settings"->profileSettings.managedPreferences();"sentry_settings"->sentrySettings.managedPreferences();"torrent_settings"->torrentSettings.managedPreferences();else->factory.get(active,feature)', "apply to original standalone datastore")
    p.write_text(s)

    tests=work/"app/src/test/java/com/nuvio/tv/petalix"
    tests.mkdir(parents=True,exist_ok=True)
    shutil.copyfile(Path(__file__).with_name("PetalixPanelControlsTest.kt"),tests/"PetalixPanelControlsTest.kt")

    for file,anchor,addition in [
        ("data/local/ProfileDataStore.kt", "    private val dataStore = context.profileDataStore", "    internal fun managedPreferences() = dataStore"),
        ("data/local/SentrySettingsDataStore.kt", "    private val dataStore = context.sentrySettingsDataStore", "    internal fun managedPreferences() = dataStore"),
        ("core/torrent/TorrentSettings.kt", "    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)", "    internal fun managedPreferences() = context.torrentDataStore"),
    ]:
        p=root/file
        p.write_text(one(p.read_text(),anchor,anchor+"\n"+addition,"standalone datastore access: "+file))

    p = root / "petalix/ManagedCoordinator.kt"
    s = p.read_text()
    s = one(s, '"management-v2","management-v3",', '"management-v2","management-v3","management-v4",', "full settings capability")
    p.write_text(s)

    p = root / "livetv/model/LiveTvModels.kt"
    s = p.read_text()
    s = one(s, '    val launcherRows: String = ""',
        '    val launcherRows: String = "",\n    val petalixHideOtherLocals: Boolean = true', "locals visibility setting")
    p.write_text(s)
    p = root / "livetv/data/LiveTvPreferences.kt"
    s = p.read_text()
    s = one(s, '        val launcherRows = stringPreferencesKey("launcher_rows")',
        '        val launcherRows = stringPreferencesKey("launcher_rows")\n        val petalixHideOtherLocals = booleanPreferencesKey("petalix_hide_other_locals")', "locals visibility key")
    s = one(s, '            launcherRows = p[Keys.launcherRows] ?: d.launcherRows',
        '            launcherRows = p[Keys.launcherRows] ?: d.launcherRows,\n            petalixHideOtherLocals = p[Keys.petalixHideOtherLocals] ?: d.petalixHideOtherLocals', "locals visibility read")
    s = one(s, '        p[Keys.launcherRows] = s.launcherRows',
        '        p[Keys.launcherRows] = s.launcherRows\n        p[Keys.petalixHideOtherLocals] = s.petalixHideOtherLocals', "locals visibility write")
    p.write_text(s)

    p = root / "livetv/ui/LiveTvViewModel.kt"
    s = p.read_text()
    s = one(s, '        val shown = channels.filter { it.key !in user.hiddenChannels && it.groupId !in user.hiddenGroups }',
        '''        val localKeys=user.customGroups.firstOrNull { it.id==ChannelGroup.CUSTOM_PREFIX+"petalix_locals" ||
            it.name.equals("Local News",true) || it.name.equals("My Locals",true) }?.channelKeys.orEmpty().toSet()
        val hideNationwide=s.petalixHideOtherLocals && localKeys.isNotEmpty()
        val available = channels.filter { it.key !in user.hiddenChannels && it.groupId !in user.hiddenGroups }
        val shown = available
            .filter { !hideNationwide || it.key in localKeys || !Regex("(?i)\\\\blocals?\\\\b").containsMatchIn(it.group) }''', "hide other locals on this TV only")
    s = one(s, '        shown.distinctBy { it.groupId }.forEach { c ->',
        '        available.distinctBy { it.groupId }.forEach { c ->', "preserve parental rules when editing locals")
    s = one(s, '            allVisibleChannels = sorted(visible),',
        '            allVisibleChannels = sorted(available.filter { it.groupId !in lockedIds }),', "allow changing locals after hiding nationwide groups")
    p.write_text(s)

    p = root / "livetv/ui/LiveTvGuideScreen.kt"
    s = p.read_text()
    s = one(s, 'val gapFill = Color(0xF223344C)', 'val gapFill = guideSurface()', "honor guide darkness in gaps")
    s = one(s, 'val idleFill = Color(0xF223344C)', 'val idleFill = guideSurface()', "honor guide darkness in program cells")
    s = one(s, 'if(focused)Color(0xF05B8CC1) else Color(0xF223344C)',
        'if(focused)androidx.compose.ui.graphics.lerp(guideSurface(),Color(0xFF5B8CC1),.38f) else guideSurface()', "honor guide darkness in focused cell")
    s = one(s, 'if(ui.selectedGroupId.contains("sport",true))Color(0xFF103D4A)',
        '''when(settings.guideBackdrop) {
                "onyx"->Color(0xFF080B12);"arctic"->Color(0xFF254F70);"emerald"->Color(0xFF164B3A)
                "rose"->Color(0xFF542B45);"copper"->Color(0xFF503729);"graphite"->Color(0xFF303943)
                else->if(ui.selectedGroupId.contains("sport",true))Color(0xFF103D4A)
            else if(ui.selectedGroupId.contains("news",true))Color(0xFF173B63)
            else Color(0xFF253D61)
            }''', "honor guide background palette")
    # The previous patch replaced just the first arm; remove its old remaining arms.
    s = one(s, '''            }
            else if(ui.selectedGroupId.contains("news",true))Color(0xFF173B63)
            else Color(0xFF253D61),label="guideAmbient")''',
        '''            },label="guideAmbient")''', "finish guide palette expression")
    s = one(s, '            if (false) {\n                LiveText(\n                    channel.number.toString(),',
        '            if (settings.showChannelNumbers) {\n                LiveText(\n                    channel.number.toString(),', "honor channel numbers setting")
    s = one(s, '            if (channel.logo.isNullOrBlank()) {',
        '            if (settings.showChannelNames || !settings.showChannelLogos || channel.logo.isNullOrBlank()) {', "honor channel names setting")
    s = one(s, '"petalix-guide-locals-v12"', '"petalix-guide-locals-v12"); android.util.Log.i("PetalixSettings","petalix-panel-controls-v13"', "v13 marker")
    p.write_text(s)

    p = root / "petalix/ui2/Petalix2Home.kt"
    s = p.read_text()
    s = one(s, '        item(key="hero") {', '        if(state.heroSectionEnabled)item(key="hero") {', "honor hero setting")
    # Keep TV-friendly landscape geometry while honoring visibility settings.
    s = one(s, '        item(key="continue") {',
        '        if(state.continueWatchingEnabled)item(key="continue") {', "honor continue watching setting")
    p.write_text(s)
