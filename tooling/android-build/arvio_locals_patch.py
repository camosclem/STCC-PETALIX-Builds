"""ARVIO-derived streaming hubs and the integrated Petalix ZIP/local guide test UI."""
from pathlib import Path
import shutil,json,hashlib,urllib.request

def once(text,old,new,label):
    if text.count(old)!=1:raise RuntimeError('ARVIO/locals integration changed: '+label)
    return text.replace(old,new)

def apply(work):
    here=Path(__file__).parent
    source=Path(work)/'app/src/main/java/com/nuvio/tv'
    ui=source/'petalix/ui2'
    for name in ['ArvioVideoHero.kt','ArvioServiceHub.kt','PetalixLocalChannels.kt','PetalixGuideHeader.kt']:
        shutil.copyfile(here/name,ui/name)
    tests=Path(work)/'app/src/test/java/com/nuvio/tv/petalix/ui2'
    tests.mkdir(parents=True,exist_ok=True)
    shutil.copyfile(here/'PetalixLocalStationMatcherTest.kt',tests/'PetalixLocalStationMatcherTest.kt')
    assets=Path(work)/'app/src/main/assets'
    media=assets/'petalix2/service-media';media.mkdir(parents=True,exist_ok=True)
    manifest=json.loads((here/'service-media-manifest.json').read_text())
    for sid,entry in manifest['media'].items():
        with urllib.request.urlopen(urllib.request.Request(entry['url'],headers={'User-Agent':'Petalix-Test-Assets/1'}),timeout=90) as response:data=response.read(12000000)
        if len(data)!=entry['size'] or hashlib.sha256(data).hexdigest()!=entry['sha256']:raise RuntimeError('Service clip integrity failed: '+sid)
        (media/(sid+'.mp4')).write_bytes(data)
    shutil.copyfile(here/'service-media-manifest.json',assets/'petalix2/service-media-manifest.json')
    shutil.copyfile(here/'local-stations.json',assets/'petalix2/local-stations.json')
    shutil.copytree(here/'service-logos',assets/'petalix2/service-logos',dirs_exist_ok=True)
    licenses=assets/'licenses';licenses.mkdir(exist_ok=True)
    for name in ['ARVIO-LICENSE.txt','ARVIO-NOTICE.txt']:shutil.copyfile(here/name,licenses/name)
    layouts=Path(work)/'app/src/main/res/layout';layouts.mkdir(exist_ok=True)
    (layouts/'petalix_service_video.xml').write_text('''<androidx.media3.ui.PlayerView xmlns:android="http://schemas.android.com/apk/res/android" xmlns:app="http://schemas.android.com/apk/res-auto" android:layout_width="match_parent" android:layout_height="match_parent" android:focusable="false" app:use_controller="false" app:surface_type="texture_view" />''')
    p=ui/'Petalix2Services.kt';s=p.read_text();s=once(s,'    DistinctPetalixServiceScreen(onHome,onDetail,viewModel)','    ArvioPetalixServiceScreen(onHome,onDetail,viewModel)','ARVIO hub entry');p.write_text(s)
    p=ui/'PetalixServiceMotion.kt';s=p.read_text();a=s.index('@Composable\nfun MotionServiceTile(')
    s=s[:a]+'''@Composable
fun MotionServiceTile(service:PetalixService,modifier:Modifier=Modifier,onClick:()->Unit) {
    var focused by remember { mutableStateOf(false) }
    val lift by animateFloatAsState(if(focused)1.04f else 1f,tween(180),label="serviceVideoFocus")
    PetalixTile(modifier.width(210.dp).height(118.dp).graphicsLayer {scaleX=lift;scaleY=lift},service.accent,
        onClick=onClick,onFocus={focused=it}) {
        Box(Modifier.fillMaxSize().background(service.background))
        ArvioServiceLogo(service,Modifier.fillMaxSize())
        val clip=ArvioServiceMedia.video(service.id)
        if(focused && clip!=null)ArvioVideoHero(clip,Modifier.fillMaxSize(),muted=true,loop=true,onEnded={})
    }
}
''';p.write_text(s)
    p=source/'livetv/data/LiveTvPreferences.kt';s=p.read_text()
    anchor='    suspend fun createCustomGroup(name: String): String {'
    s=once(s,anchor,'''    suspend fun petalixLocalZip():String = store.data.first()[my(stringPreferencesKey("petalix_local_zip"))].orEmpty()
    suspend fun petalixSaveLocals(zip:String,keys:List<String>):String {
        require(zip.matches(Regex("[0-9]{5}")))
        val profile=currentProfile
        val groupsKey=my(Keys.customGroups,profile)
        val zipKey=my(stringPreferencesKey("petalix_local_zip"),profile)
        var id=""
        store.edit { p ->
            val groups=decodeCustomGroups(p[groupsKey])
            val existing=groups.firstOrNull {it.id==com.nuvio.tv.livetv.model.ChannelGroup.CUSTOM_PREFIX+"petalix_locals" || it.name.equals("My Locals",true)}
            id=existing?.id ?: (com.nuvio.tv.livetv.model.ChannelGroup.CUSTOM_PREFIX+"petalix_locals")
            val group=CustomGroup(id,"My Locals",((existing?.channelKeys ?: emptyList())+keys).distinct())
            p[groupsKey]=encodeCustomGroups(if(existing==null)groups+group else groups.map{if(it.id==id)group else it})
            p[zipKey]=zip
        }
        return id
    }

'''+anchor,'profile ZIP and atomic locals group')
    if 'import kotlinx.coroutines.flow.first' not in s:s=s.replace('import kotlinx.coroutines.flow.', 'import kotlinx.coroutines.flow.first\nimport kotlinx.coroutines.flow.',1)
    p.write_text(s)
    p=source/'livetv/ui/LiveTvGuideScreen.kt';s=p.read_text()
    s=once(s,'    val ui by viewModel.uiState.collectAsStateWithLifecycle()','''    var petalixLocalsOpen by remember { mutableStateOf(false) }
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    if(petalixLocalsOpen)com.nuvio.tv.petalix.ui2.PetalixLocalChannelsDialog(ui.allVisibleChannels,onClose={petalixLocalsOpen=false},onOpenGroup={viewModel.selectGroup(it);petalixLocalsOpen=false})''','guide locals dialog')
    s=once(s,'            .guideBackdrop(settings.guideBackdrop)','            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF08182A),Color(0xFF030910))))','cinematic guide backdrop')
    s=once(s,'            // ---------------------------------------------------------------- header','''            Row(Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                com.nuvio.tv.petalix.ui2.PetalixAction("TV Guide",selected=true){}
                Spacer(Modifier.weight(1f))
                com.nuvio.tv.petalix.ui2.PetalixAction("Find locals • ZIP"){petalixLocalsOpen=true}
                com.nuvio.tv.petalix.ui2.PetalixAction("Search",onClick=onOpenNuvioSearch)
                com.nuvio.tv.petalix.ui2.PetalixAction("Settings",onClick=onOpenSettings)
            }
            // ---------------------------------------------------------------- header''','guide toolbar')
    a=s.index('    if (!settings.showProgramDetails && !showPreview) {',s.index('private fun GuideHeader('))
    b=s.index('\n@Composable',a)
    # Keep the existing function signature and replace its full body. This preserves every
    # single-player surface callback and caller route while taking over the visual layout.
    old=s[a:b]
    if not old.rstrip().endswith('}'):raise RuntimeError('GuideHeader boundary changed')
    s=s[:a]+'''    com.nuvio.tv.petalix.ui2.PetalixGuideHeader(
        channel=channel,program=block?.program,poster=poster,start=block?.startMs,stop=block?.stopMs,now=now,
        showPreview=showPreview,playingKey=playingKey,buffering=playbackBuffering,error=playbackError,
        status=status,playlist=playlistName,previewBounds=onPreviewBounds,glassBounds=onGlassBounds,
        gearFocus=gearFocus,onGear=onGear)
}
'''+s[b:]
    s=s.replace('val idleFill = guideSurface()','val idleFill = Color(0xFF111E30)')
    old='.glassTile(colors.background, if (colors.border == Color.Transparent) GuideGlassEdge else colors.border, shape, clipChildren = progress != null)'
    s=once(s,old,'.clip(shape).background(if(focused)Color(0xFF17447B) else Color(0xFF111E30)).border(if(focused)2.dp else 1.dp,if(focused)Color(0xFF4D9DFF) else Color(0xFF24344A),shape)','flat blue program focus')
    begin=s.index('private fun ProgramBlock(')
    end=s.index('\n@Composable',begin)
    s=s[:begin]+s[begin:end].replace('colors.text','Color.White')+s[end:]
    p.write_text(s)
