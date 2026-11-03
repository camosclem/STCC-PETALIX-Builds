"""Petalix test UI polish: Sports route, animated tiles, dynamic art and focus trailers."""
from pathlib import Path


def once(text, old, new, label):
    if text.count(old) != 1:
        raise RuntimeError(f"Petalix UI2 patch source changed: {label}")
    return text.replace(old, new)


def apply(work):
    manifest = Path(work) / 'app/src/main/AndroidManifest.xml'
    xml = manifest.read_text()
    if 'android:name=".MainActivity"' not in xml:
        raise RuntimeError('MainActivity entry missing from manifest')
    main_tag = xml.index('android:name=".MainActivity"')
    tag_end = xml.index('>', main_tag)
    if 'android:screenOrientation=' not in xml[main_tag:tag_end]:
        xml = xml[:tag_end] + '\n            android:screenOrientation="landscape"' + xml[tag_end:]
        manifest.write_text(xml)
    root = Path(work) / 'app/src/main/java/com/nuvio/tv/petalix/ui2'
    chrome = root / 'Petalix2Chrome.kt'
    text = chrome.read_text()
    if 'PetalixTab("Sports"' not in text:
        text = once(text, '    PetalixTab("Search", "search", Icons.Default.Search),',
            '    PetalixTab("Sports", Screen.Sports.route, Icons.Default.SportsSoccer),\n    PetalixTab("Search", "search", Icons.Default.Search),', 'Sports nav')
        chrome.write_text(text)

    home = root / 'Petalix2Home.kt'
    text = home.read_text()
    if 'val petalixTileScale by' not in text:
        text = once(text, 'import androidx.compose.runtime.*',
            'import androidx.compose.runtime.*\nimport androidx.compose.animation.core.animateFloatAsState\nimport androidx.compose.animation.core.tween\nimport androidx.compose.ui.graphics.graphicsLayer', 'tile animation imports')
        text = once(text, '    var focused by remember { mutableStateOf(false) }\n    Box(modifier.onFocusChanged { focused=it.isFocused }.clip(RoundedCornerShape(8.dp))',
            '    var focused by remember { mutableStateOf(false) }\n    val petalixTileScale by animateFloatAsState(if(focused)1.045f else 1f,tween(170),label="petalixTileScale")\n    Box(modifier.graphicsLayer { scaleX=petalixTileScale;scaleY=petalixTileScale }.onFocusChanged { focused=it.isFocused }.clip(RoundedCornerShape(8.dp))', 'shared tile focus animation')
        marker='    val state by viewModel.uiState.collectAsStateWithLifecycle()'
        text=once(text, marker, '    val tileTrailerVm:PetalixTileTrailerViewModel=hiltViewModel()\n    val focusedArtwork by tileTrailerVm.focusedBackdrop.collectAsStateWithLifecycle()\n'+marker, 'Home dynamic artwork state')
        text=text.replace('AsyncImage(model=if(channel!=null)backdrop else hero?.backdropUrl,contentDescription=null', 'AsyncImage(model=focusedArtwork ?: if(channel!=null)backdrop else hero?.backdropUrl,contentDescription=null')
        # A delayed, focus-only trailer preview. TrailerService honors the existing trailer enable setting.
        text = once(text, 'import com.nuvio.tv.ui.screens.home.*',
            'import com.nuvio.tv.ui.screens.home.*\nimport com.nuvio.tv.data.trailer.TrailerService\nimport com.nuvio.tv.core.player.LocalTrailerPlayerPool\nimport com.nuvio.tv.ui.components.TrailerPlayer\nimport androidx.lifecycle.ViewModel\nimport androidx.lifecycle.viewModelScope\nimport dagger.hilt.android.lifecycle.HiltViewModel\nimport javax.inject.Inject\nimport kotlinx.coroutines.delay\nimport kotlinx.coroutines.flow.MutableStateFlow\nimport kotlinx.coroutines.flow.asStateFlow\nimport kotlinx.coroutines.flow.update', 'trailer imports')
        old='''fun MetadataTile(meta:MetaPreview,accent:Color=PetalixBlue,landscape:Boolean=false,rank:Int?=null,modifier:Modifier=Modifier,onClick:()->Unit) {
    PetalixTile(modifier.width(if(landscape)220.dp else 116.dp).height(if(landscape)124.dp else 176.dp),accent,onClick) {
        AsyncImage(if(landscape)meta.backdropUrl else meta.poster,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent,Color.Black.copy(alpha=.95f)))))
        if(rank!=null)Text(rank.toString(),color=Color.White,fontSize=54.sp,fontWeight=FontWeight.Black,modifier=Modifier.align(Alignment.BottomStart).padding(5.dp))
        Text(meta.name,color=Color.White,fontSize=12.sp,maxLines=2,overflow=TextOverflow.Ellipsis,modifier=Modifier.align(Alignment.BottomStart).padding(start=if(rank!=null)48.dp else 8.dp,end=8.dp,bottom=9.dp))
    }
}'''
        new='''@HiltViewModel
class PetalixTileTrailerViewModel @Inject constructor(private val trailers:TrailerService):ViewModel() {
    private val _sources=MutableStateFlow<Map<String,com.nuvio.tv.data.trailer.TrailerPlaybackSource>>(emptyMap())
    val sources=_sources.asStateFlow()
    private val _focusedBackdrop=MutableStateFlow<String?>(null)
    val focusedBackdrop=_focusedBackdrop.asStateFlow()
    fun setFocusedBackdrop(url:String?) { if(!url.isNullOrBlank()) _focusedBackdrop.value=url }
    fun resolve(meta:MetaPreview) {
        if(_sources.value.containsKey(meta.id))return
        viewModelScope.launch {
            val year=meta.releaseInfo?.take(4)?.takeIf { it.all(Char::isDigit) }
            val source=runCatching { trailers.getTrailerPlaybackSource(meta.name,year,meta.id,meta.apiType) }.getOrNull()
            if(source!=null && source.videoUrl.isNotBlank())_sources.update { it + (meta.id to source) }
        }
    }
}

@Composable
fun MetadataTile(meta:MetaPreview,accent:Color=PetalixBlue,landscape:Boolean=false,rank:Int?=null,modifier:Modifier=Modifier,onClick:()->Unit) {
    val trailerVm:PetalixTileTrailerViewModel=hiltViewModel()
    val sources by trailerVm.sources.collectAsStateWithLifecycle()
    val pool=LocalTrailerPlayerPool.current
    var focused by remember(meta.id) { mutableStateOf(false) }
    val source=sources[meta.id]
    LaunchedEffect(focused,meta.id) {
        if(focused) { trailerVm.setFocusedBackdrop(meta.backdropUrl);delay(900);trailerVm.resolve(meta) }
    }
    PetalixTile(modifier.width(if(landscape)220.dp else 116.dp).height(if(landscape)124.dp else 176.dp).onFocusChanged { focused=it.isFocused },accent,onClick) {
        AsyncImage(if(landscape)meta.backdropUrl else meta.poster,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
        if(source!=null && focused) TrailerPlayer(trailerUrl=source.videoUrl,trailerAudioUrl=source.audioUrl,isPlaying=true,muted=false,onEnded={},modifier=Modifier.fillMaxSize(),enter=androidx.compose.animation.EnterTransition.None,exit=androidx.compose.animation.ExitTransition.None,trailerPlayerPool=pool)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent,Color.Black.copy(alpha=.95f)))))
        if(rank!=null)Text(rank.toString(),color=Color.White,fontSize=54.sp,fontWeight=FontWeight.Black,modifier=Modifier.align(Alignment.BottomStart).padding(5.dp))
        Text(meta.name,color=Color.White,fontSize=12.sp,maxLines=2,overflow=TextOverflow.Ellipsis,modifier=Modifier.align(Alignment.BottomStart).padding(start=if(rank!=null)48.dp else 8.dp,end=8.dp,bottom=9.dp))
    }
}'''
        text = once(text, old, new, 'metadata tile trailer preview')
        home.write_text(text)

    services = root / 'Petalix2Services.kt'
    text = services.read_text()
    if 'val serviceFocusScale by' not in text:
        text = once(text, 'import androidx.compose.runtime.*',
            'import androidx.compose.runtime.*\nimport androidx.compose.animation.core.*\nimport androidx.compose.ui.focus.onFocusChanged\nimport androidx.compose.ui.graphics.graphicsLayer\nimport androidx.compose.foundation.border\nimport androidx.compose.foundation.shape.RoundedCornerShape', 'service tile animation imports')
        old='    PetalixTile(modifier.width(112.dp).height(50.dp).semantics { contentDescription=service.name },PetalixBlue,onClick) {'
        new='''    var serviceFocused by remember { mutableStateOf(false) }
    val serviceFocusScale by animateFloatAsState(if(serviceFocused)1.06f else 1f, tween(180), label="serviceFocusScale")
    val serviceGlow = if(serviceFocused) {
        val pulse=rememberInfiniteTransition(label="serviceGlow")
        val alpha by pulse.animateFloat(.35f,.95f,infiniteRepeatable(tween(1100),RepeatMode.Reverse),label="serviceGlowAlpha")
        alpha
    } else 0f
    PetalixTile(modifier.width(112.dp).height(50.dp)
        .graphicsLayer { scaleX=serviceFocusScale;scaleY=serviceFocusScale }
        .onFocusChanged { serviceFocused=it.hasFocus }
        .border(2.dp,service.accent.copy(alpha=serviceGlow),RoundedCornerShape(8.dp))
        .semantics { contentDescription=service.name },service.accent,onClick) {'''
        text = once(text, old, new, 'service tile focus effects')
        services.write_text(text)
    text = services.read_text()
    if 'val focusedArtwork by tileTrailerVm.focusedBackdrop.collectAsStateWithLifecycle()' not in text:
        text=once(text, '    val state by viewModel.state.collectAsStateWithLifecycle()', '    val tileTrailerVm:PetalixTileTrailerViewModel=hiltViewModel()\n    val focusedArtwork by tileTrailerVm.focusedBackdrop.collectAsStateWithLifecycle()\n    val state by viewModel.state.collectAsStateWithLifecycle()', 'service dynamic artwork state')
    # Use brand-accurate display names while retaining the service-specific styles and provider IDs.
    for old,new in [('PetalixService("netflix","NETFLIX"','PetalixService("netflix","Netflix"'),('PetalixService("prime","prime video"','PetalixService("prime","Prime Video"'),('PetalixService("hulu","hulu"','PetalixService("hulu","Hulu"'),('PetalixService("apple","Apple TV"','PetalixService("apple","Apple TV+"'),('PetalixService("peacock","peacock"','PetalixService("peacock","Peacock"'),('PetalixService("tubi","tubi"','PetalixService("tubi","Tubi"'),('PetalixService("pluto","pluto tv"','PetalixService("pluto","Pluto TV"')]:
        if old in text:text=text.replace(old,new,1)
    if 'Crossfade(targetState=focusedArtwork ?: hero?.backdropUrl' not in text:
        text = once(text,
            'import androidx.compose.runtime.*',
            'import androidx.compose.runtime.*\nimport androidx.compose.animation.Crossfade',
            'dynamic artwork transition import')
        text = once(text,
            '                    AsyncImage(hero?.backdropUrl,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)',
            '                    Crossfade(targetState=focusedArtwork ?: hero?.backdropUrl,modifier=Modifier.fillMaxSize(),label=\"serviceBackdrop\") { art -> AsyncImage(art,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop) }',
            'service artwork crossfade')
        services.write_text(text)
