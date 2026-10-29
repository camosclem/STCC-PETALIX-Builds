"""Opt-in native seek-strip port for the original-Home test build only."""
import datetime,hashlib,json,pathlib,re,shutil,tempfile,urllib.request,zipfile,xml.etree.ElementTree as ET
from playlist_patch import replace_once

RECIPE='seek-service-templates-test-v1'
DONOR_COMMIT='567958ca1c745e11511230c9b0b8527242d63c1a'
DONOR_TAG='1.1.0-beta-nt4.2'
DONOR_SHA256='b17f3d319bb2d359943a537ae29d0004ba775805bdc8ce002e937ce4049908b2'
DONOR_URL='https://github.com/ysosrs123/NuvioTV-Fork/archive/refs/tags/'+DONOR_TAG+'.zip'
ROOT='app/src/main/java/com/nuvio/tv/'

def donor(cache):
    archive=cache/'arc-nt42-seek-source.zip'
    if not archive.exists():
        with urllib.request.urlopen(urllib.request.Request(DONOR_URL,headers={'User-Agent':'STCC-Petalix-Seek-Test'}),timeout=180) as response,archive.open('wb') as target:shutil.copyfileobj(response,target)
    if hashlib.sha256(archive.read_bytes()).hexdigest()!=DONOR_SHA256:raise RuntimeError('Seek-thumbnail donor source checksum mismatch')
    return archive

def resolve_dates(value,today):
    if isinstance(value,list):return [resolve_dates(x,today) for x in value]
    if isinstance(value,dict):return {k:resolve_dates(v,today) for k,v in value.items()}
    if value=='@panel_today':return today.isoformat()
    if isinstance(value,str) and re.fullmatch(r'@panel_days_\d{1,4}',value):
        days=int(value[len('@panel_days_'):]);assert 1<=days<=3650
        return (today-datetime.timedelta(days=days)).isoformat()
    return value

def apply(work,cache):
    archive=donor(cache);local=pathlib.Path(__file__).parent
    with tempfile.TemporaryDirectory(prefix='seek-donor-') as temp:
        temp=pathlib.Path(temp)
        with zipfile.ZipFile(archive) as source:
            for member in source.infolist():
                parts=pathlib.PurePosixPath(member.filename).parts[1:]
                if not parts or member.is_dir():continue
                name='/'.join(parts)
                keep=name.startswith(ROOT+'core/player/thumbnail/') or name.startswith('app/src/main/cpp/') or name.startswith('app/src/test/java/com/nuvio/tv/core/player/thumbnail/') or name in [ROOT+'ui/screens/player/SeekThumbnailPane.kt',ROOT+'ui/screens/player/SeekStripLayout.kt','app/src/main/res/values/strings.xml','LICENSE']
                if not keep:continue
                target=temp.joinpath(*parts)
                if not target.resolve().is_relative_to(temp.resolve()):raise RuntimeError('Unsafe donor source path')
                target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(source.read(member))
        module=pathlib.Path(ROOT+'core/player/thumbnail')
        shutil.copytree(temp/module,work/module,dirs_exist_ok=True)
        tests=pathlib.Path('app/src/test/java/com/nuvio/tv/core/player/thumbnail')
        shutil.copytree(temp/tests,work/tests,dirs_exist_ok=True)
        cpp=pathlib.Path('app/src/main/cpp')
        for file in (temp/cpp).rglob('*'):
            relative=file.relative_to(temp/cpp)
            if not file.is_file() or relative.name in {'CMakeLists.txt','dovi_bridge.cpp','aml_fel_native.cpp'}:continue
            target=work/cpp/relative;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(file,target)
        cmake=work/cpp/'CMakeLists.txt'
        native=(temp/cpp/'CMakeLists.txt').read_text();marker='# Seek thumbnails:'
        if marker not in native:raise RuntimeError('Seek decoder CMake definition needs review')
        cmake.write_text(cmake.read_text()+'\n'+native[native.index(marker):])
        player=work/ROOT/'ui/screens/player'
        shutil.copyfile(local/'SeekThumbnailPane.kt',player/'SeekThumbnailPane.kt')
        shutil.copyfile(temp/ROOT/'ui/screens/player/SeekStripLayout.kt',player/'SeekStripLayout.kt')
        resources=work/'app/src/main/res/values/seek_preview_test.xml'
        tree=ET.parse(temp/'app/src/main/res/values/strings.xml');new=ET.Element('resources')
        for node in tree.getroot():
            if node.get('name','').startswith('seek_thumbnails_'):new.append(node)
        resources.write_text(ET.tostring(new,encoding='unicode'))
    trial_rows=work/'app/src/test/java/com/nuvio/tv/petalix/ServiceHubRowsTest.kt'
    trial_rows.write_text(re.sub(r'    @Test fun homeKeepsServiceTilesAndViewingHistoryWhileRemovingRepeatedDiscoveryRows\(\) \{.*?\n    \}\n','',trial_rows.read_text(),flags=re.S))
    policy=work/ROOT/'petalix/ManagedPolicy.kt'
    replace_once(policy,'        ServiceHubPolicy.initialize(context)','        ServiceHubPolicy.initialize(context)\n        com.nuvio.tv.ui.screens.player.SeekPreviewTrialSessions.cleanupIfUnused(context)')
    # The original Home build did not invoke CMake when Dolby Vision was off.
    gradle=work/'app/build.gradle.kts'
    replace_once(gradle,'    if (enableDoviNative) {\n        externalNativeBuild {\n            cmake {\n                path = file("src/main/cpp/CMakeLists.txt")\n            }\n        }\n    }','    externalNativeBuild {\n        cmake {\n            path = file("src/main/cpp/CMakeLists.txt")\n        }\n    }')
    with gradle.open('a') as output:output.write('\nandroid.defaultConfig.ndk.abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a"))\n')
    # Keep JNI class/method names matching the audited donor shim.
    with (work/'app/proguard-rules.pro').open('a') as output:output.write('\n# Native seek-thumbnail JNI entrypoints.\n-keep class com.nuvio.tv.core.player.thumbnail.ThumbNative { *; }\n')
    # This trial is session-only: no cross-title cache retention or post-play generation.
    engine=work/module/'SeekThumbnailEngine.kt'
    replace_once(engine,'        if (!s.finishAfterPlayback()) stopSession(handBackPlayer = false)','        stopSession(handBackPlayer = false)')
    replace_once(engine,'const val SPOOL_MAX_BYTES = 64L * 1024 * 1024','const val SPOOL_MAX_BYTES = 8L * 1024 * 1024')
    store=work/module/'ThumbStore.kt'
    replace_once(store,'const val MEM_FRAMES = 48','const val MEM_FRAMES = 24')
    replace_once(store,'private const val MAX_BUDGET_BYTES = 64L * 1024 * 1024','private const val MAX_BUDGET_BYTES = 16L * 1024 * 1024')
    replace_once(store,'            val offset = datFile.length()\n            runCatching { datFile.appendBytes(webp) }','            val offset = datFile.length()\n            if (offset + webp.size + (cachedIndex?.size ?: 0) > MAX_BUDGET_BYTES) return\n            runCatching { datFile.appendBytes(webp) }')
    reader=work/module/'HttpRangeReader.kt'
    replace_once(reader,'        require(offset >= 0 && length > 0)','        require(offset >= 0 && length > 0)\n        if (length > 16 * 1024 * 1024) throw UnsupportedMediaException("thumbnail range exceeds trial memory limit")')
    # Preferences remain local to this test APK; the live panel gains no new controls.
    for name,target in [('SeekPreviewTestControls.kt',work/ROOT/'petalix/SeekPreviewTestControls.kt'),('SeekPreviewPlayerSession.kt',player/'SeekPreviewPlayerSession.kt')]:shutil.copyfile(local/name,target)
    screen=player/'PlayerScreen.kt'
    replace_once(screen,'    val activity = LocalContext.current as? android.app.Activity','    SeekPreviewPlayerSession(viewModel, uiState)\n    val activity = LocalContext.current as? android.app.Activity')
    replace_once(screen,'        // Seek-only overlay (progress bar + time) when controls are hidden','        SeekPreviewOverlays(uiState, viewModel)\n\n        // Seek-only overlay (progress bar + time) when controls are hidden')
    replace_once(screen,'                // Consume the confirm KEY_UP that opened the subtitle timing dialog before','''                if (com.nuvio.tv.core.player.thumbnail.SeekThumbnails.prepareUi.value !is com.nuvio.tv.core.player.thumbnail.SeekThumbnails.PrepareUi.Hidden) {
                    if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_UP && keyEvent.nativeKeyEvent.keyCode in listOf(KeyEvent.KEYCODE_DPAD_CENTER,KeyEvent.KEYCODE_ENTER,KeyEvent.KEYCODE_NUMPAD_ENTER,KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)) com.nuvio.tv.core.player.thumbnail.SeekThumbnails.startWatchingNow()
                    return@onPreviewKeyEvent true
                }
                // Consume the confirm KEY_UP that opened the subtitle timing dialog before''')
    vm=player/'PlayerViewModel.kt'
    replace_once(vm,'    fun getCurrentHeaders(): Map<String, String> = controller.getCurrentHeaders()','''    fun getCurrentHeaders(): Map<String, String> = controller.getCurrentHeaders()
    fun seekPreviewIdentity(): String = controller.currentVideoId?.takeIf { it.isNotBlank() } ?: getCurrentStreamUrl()
    fun seekPreviewFileSize(): Long? = controller.currentVideoSize
    fun seekPreviewMimeType(): String? = controller.currentStreamMimeType''')
    settings=work/ROOT/'ui/screens/settings/PlaybackSettingsScreen.kt'
    replace_once(settings,'        SettingsGroupCard(','        com.nuvio.tv.petalix.SeekPreviewTestControls()\n\n        SettingsGroupCard(')
    coordinator=work/ROOT/'petalix/ManagedCoordinator.kt'
    replace_once(coordinator,'"device-updates-v1"','"device-updates-v1","seek-previews-test-v1"')
    asset=resolve_dates(json.loads((local/'service-templates-test.json').read_text()),datetime.datetime.now(datetime.timezone.utc).date())
    assert asset[0]['id']=='collection-service-templates-test-v1'
    (work/'app/src/main/assets/service-templates-test.json').write_text(json.dumps(asset,separators=(',',':')))
    (work/'SEEK_THUMBNAIL_NOTICES.md').write_text('Native seek-thumbnail trial\n\nPorted from ysosrs123/NuvioTV-Fork '+DONOR_TAG+' ('+DONOR_COMMIT+'), GPL-3.0. Original license and copyright notices remain. The matching source includes the original thumbnail engine, tests, JNI shim and vendored FFmpeg headers/licenses.\n\nTrial changes: original Petalix/STCC player adapter, local test controls, a separate opt-in service-template collection, a bounded 16 MiB frame cache and 8 MiB spool, no post-play generation, and session/crash cleanup confined to preview-owned caches. Playback/activation/Home code remains from the verified original Home build except the explicit adapters.\n\nPhysical Fire TV, onn and Shield validation is required before customer use. No API key is used by thumbnail generation.\n')

def verify_trial_source(original,updated,brand):
    from home_baseline import source_files,verify_archive,ALLOWED,FILES
    verify_archive(original,brand);before,after=source_files(original),source_files(updated)
    changed={name for name in before.keys()|after.keys() if before.get(name)!=after.get(name)}
    extra={ROOT+name for name in ['petalix/ManagedPolicy.kt','petalix/ServiceHubPolicy.kt','petalix/ServiceHubRows.kt','petalix/SeekPreviewTestControls.kt','ui/screens/player/PlayerScreen.kt','ui/screens/player/PlayerViewModel.kt','ui/screens/player/SeekThumbnailPane.kt','ui/screens/player/SeekStripLayout.kt','ui/screens/player/SeekPreviewPlayerSession.kt','ui/screens/settings/PlaybackSettingsScreen.kt','ui/screens/collection/FolderDetailViewModel.kt','ui/screens/collection/FolderDetailScreen.kt','ui/screens/home/ModernHomeContent.kt','domain/model/Collection.kt','data/local/CollectionsDataStore.kt','core/tmdb/TmdbCollectionSourceResolver.kt']}
    extra.update({'app/proguard-rules.pro','app/src/main/res/values/seek_preview_test.xml','app/src/main/assets/service-templates-test.json','app/src/main/assets/managed-native-controls.json','app/src/test/java/com/nuvio/tv/petalix/ServiceHubRowsTest.kt','SEEK_THUMBNAIL_NOTICES.md'})
    prefixes=(ROOT+'core/player/thumbnail/','app/src/test/java/com/nuvio/tv/core/player/thumbnail/','app/src/main/cpp/')
    unexpected={name for name in changed if name not in ALLOWED|extra and not name.startswith(prefixes)}
    if unexpected:raise RuntimeError('Trial source changed outside reviewed integration files: '+', '.join(sorted(unexpected)))
    # Home content is unchanged except the no-autoplay front-door guard already audited in the hubs patch.
    required={ROOT+'ui/screens/player/SeekPreviewPlayerSession.kt',ROOT+'core/player/thumbnail/SeekThumbnailEngine.kt','app/src/main/assets/service-templates-test.json'}
    if not required<=changed:raise RuntimeError('The combined seek/template trial was not included')
    return FILES[brand][1]
