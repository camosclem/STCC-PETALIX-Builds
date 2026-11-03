"""Petalix test UI polish: Sports route, animated tiles, dynamic art and focus trailers."""
from pathlib import Path
import hashlib
import shutil


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
        old='''@Composable
fun MetadataTile(meta:MetaPreview,accent:Color=PetalixBlue,landscape:Boolean=false,rank:Int?=null,modifier:Modifier=Modifier,onClick:()->Unit) {
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

    # Use the exact Petalix wordmark already approved for the panel branding.
    logo = Path(__file__).with_name('petalix_metallic_logo.png')
    if hashlib.sha256(logo.read_bytes()).hexdigest() != 'ef789202dbef793eadc6af20840f4beeed241802bbf0167a756ed1e92264a30f':
        raise RuntimeError('The Petalix panel logo checksum does not match')
    resources = Path(work) / 'app/src/main/res'
    (resources / 'drawable-nodpi').mkdir(parents=True, exist_ok=True)
    shutil.copyfile(logo, resources / 'drawable-nodpi/petalix_panel_logo.png')
    for target in (resources / 'drawable').glob('app_logo_wordmark*.png'):
        shutil.copyfile(logo, target)
    logo_home = root / 'Petalix2Home.kt'
    logo_text = logo_home.read_text()
    old_logo = '''@Composable
fun PetalixReferenceLogo(modifier:Modifier=Modifier) {
    val context=LocalContext.current
    val bitmap=remember(context) { PetalixReference.bitmap(context) }
    Canvas(modifier.semantics {contentDescription="Petalix"}) {
        val height=(size.width*42/285).toInt()
        drawImage(bitmap,srcOffset=IntOffset(37,32),srcSize=IntSize(285,42),dstOffset=IntOffset(0,(size.height.toInt()-height)/2),dstSize=IntSize(size.width.toInt(),height),filterQuality=FilterQuality.High)
    }
}'''
    new_logo = '''@Composable
fun PetalixReferenceLogo(modifier:Modifier=Modifier) {
    val context=LocalContext.current
    val logoId=remember(context) { context.resources.getIdentifier("petalix_panel_logo","drawable",context.packageName) }
    val bitmap=remember(context,logoId) { BitmapFactory.decodeResource(context.resources,logoId) }
    Canvas(modifier.semantics {contentDescription="Petalix"}) {
        bitmap?.let { image ->
            val scale=minOf(size.width/image.width,size.height/image.height)
            val width=(image.width*scale).toInt()
            val height=(image.height*scale).toInt()
            drawImage(image.asImageBitmap(),dstOffset=IntOffset((size.width.toInt()-width)/2,(size.height.toInt()-height)/2),dstSize=IntSize(width,height),filterQuality=FilterQuality.High)
        }
    }
}'''
    logo_text = once(logo_text, old_logo, new_logo, 'use approved panel Petalix logo in top navigation')
    logo_home.write_text(logo_text)

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

    # Petalix premium service polish: cinematic full-screen artwork, landscape cards,
    # larger animated service tiles, and original service-specific focus tones.
    text = services.read_text()
    replacements = [
        ('modifier.width(112.dp).height(50.dp)', 'modifier.width(148.dp).height(74.dp)'),
        ('landscape=service.style=="apple"', 'landscape=true'),
        ('landscape=service.style in listOf("prime","apple","peacock","pluto") || (service.style=="hulu" && index==0) || (service.style=="max" && index%2==0)', 'landscape=true'),
    ]
    for old, new in replacements:
        if old in text:
            text = text.replace(old, new)
    # Each service gets an original synthesized focus tone; service-owned audio is not copied.
    if 'ToneGenerator(AudioManager.STREAM_MUSIC' not in text:
        text = once(text, 'import androidx.activity.compose.BackHandler',
            'import androidx.activity.compose.BackHandler\nimport android.media.AudioManager\nimport android.media.ToneGenerator', 'service focus sound imports')
        text = once(text, '    var serviceFocused by remember { mutableStateOf(false) }',
            '    val tone=remember { ToneGenerator(AudioManager.STREAM_MUSIC,35) }\n    DisposableEffect(tone) { onDispose { tone.release() } }\n    var serviceFocused by remember { mutableStateOf(false) }',
            'service focus tone lifecycle')
        old_focus = '.onFocusChanged { serviceFocused=it.hasFocus }'
        new_focus = '''.onFocusChanged { state ->
            val gained=state.isFocused && !serviceFocused
            serviceFocused=state.isFocused
            if(gained) {
                val cue=when(service.id) {
                    "netflix"->ToneGenerator.TONE_PROP_ACK
                    "disney"->ToneGenerator.TONE_PROP_BEEP
                    "prime"->ToneGenerator.TONE_PROP_PROMPT
                    "hulu"->ToneGenerator.TONE_PROP_NACK
                    "max"->ToneGenerator.TONE_PROP_BEEP2
                    "paramount"->ToneGenerator.TONE_PROP_ACK
                    "apple"->ToneGenerator.TONE_PROP_PROMPT
                    "peacock"->ToneGenerator.TONE_PROP_NACK
                    "tubi"->ToneGenerator.TONE_PROP_BEEP
                    else->ToneGenerator.TONE_PROP_BEEP2
                }
                runCatching { tone.startTone(cue,95) }
            }
        }'''
        text=once(text,old_focus,new_focus,'service focus sound cue')
    # Put focused title artwork behind the entire service browsing experience.
    if 'serviceBackdropFullScreen' not in text:
        old = '    Column(Modifier.fillMaxSize().background(service.background)) {'
        new = '''    Box(Modifier.fillMaxSize().background(service.background)) {
        androidx.compose.animation.Crossfade(targetState=focusedArtwork ?: hero?.backdropUrl,label="serviceBackdropFullScreen") { art ->
            AsyncImage(art,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(service.background.copy(alpha=.48f),service.background.copy(alpha=.82f),service.background))))
        Column(Modifier.fillMaxSize()) {'''
        text=once(text,old,new,'service full-screen dynamic background')
        old_end = '    }\n}\n\n/** Import the saved, service-specific queries.'
        new_end = '    }\n    }\n}\n\n/** Import the saved, service-specific queries.'
        text=once(text,old_end,new_end,'service full-screen background container')
    services.write_text(text)

    # Distinct service browsing structures backed by each service's actual filtered rows.
    text = services.read_text()
    text=once(text,'    val hero=visible.firstOrNull()', '''    var featureIndex by rememberSaveable(service.id) { mutableStateOf(0) }
    LaunchedEffect(query,kind,collectionTitle) { featureIndex=0 }
    val hero=visible.getOrNull(featureIndex.coerceIn(0,(visible.size-1).coerceAtLeast(0)))''','service featured selection')
    marker='        LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(bottom=30.dp),verticalArrangement=Arrangement.spacedBy(22.dp)) {'
    replacement='''        // These destinations use real provider-filtered collections and retain their titles.
        if(query.isBlank() && state.rows.isNotEmpty()) {
            val collectionHeight=when(service.style) {
                "disney"->76.dp
                "apple"->54.dp
                "max"->88.dp
                "hulu"->60.dp
                else->48.dp
            }
            LazyRow(contentPadding=PaddingValues(horizontal=26.dp,vertical=8.dp),
                horizontalArrangement=Arrangement.spacedBy(if(service.style=="hulu")18.dp else 10.dp),
                modifier=Modifier.focusRestorer()) {
                items(state.rows,key={it.catalogName}) { collection ->
                    when(service.style) {
                        "disney","max" -> PetalixTile(Modifier.width(if(service.style=="max")230.dp else 175.dp).height(collectionHeight),
                            service.accent,onClick={collectionTitle=collection.catalogName;query=""}) {
                            AsyncImage(collection.items.firstOrNull()?.backdropUrl,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent,service.background.copy(alpha=.95f)))))
                            Text(collection.catalogName,color=Color.White,fontWeight=FontWeight.Bold,fontSize=14.sp,
                                modifier=Modifier.align(Alignment.BottomStart).padding(12.dp),maxLines=2)
                        }
                        "hulu" -> PetalixTile(Modifier.width(190.dp).height(collectionHeight),service.accent,
                            onClick={collectionTitle=collection.catalogName;query=""}) {
                            Box(Modifier.fillMaxSize().background(service.accent.copy(alpha=.15f)))
                            Text(collection.catalogName.uppercase(),color=service.accent,fontWeight=FontWeight.Black,fontSize=13.sp,
                                modifier=Modifier.align(Alignment.Center).padding(12.dp),maxLines=2)
                        }
                        else -> PetalixAction(collection.catalogName,selected=collectionTitle==collection.catalogName,
                            accent=service.accent) {collectionTitle=collection.catalogName;query=""}
                    }
                }
            }
        }
        if(collectionTitle!=null) {
            Row(Modifier.padding(horizontal=26.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
                PetalixAction("All collections",accent=service.accent) {collectionTitle=null}
                Text(collectionTitle.orEmpty(),color=Color.White,fontSize=16.sp,fontWeight=FontWeight.Bold)
            }
        }
''' + marker
    text=once(text,marker,replacement,'service collection destinations')
    marker='                        if(hero!=null)PetalixAction("View Details",accent=service.accent) { onDetail(hero.id,hero.apiType,hero.sourceAddonBaseUrl.orEmpty()) }'
    replacement='''                        if(hero!=null) {
                            Row(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
                                PetalixAction("View Details",accent=service.accent) { onDetail(hero.id,hero.apiType,hero.sourceAddonBaseUrl.orEmpty()) }
                                if(visible.size>1) {
                                    PetalixAction("Previous",accent=service.accent) {featureIndex=(featureIndex-1+visible.size)%visible.size}
                                    Text("${featureIndex+1} / ${visible.size}",color=Color.White,fontSize=12.sp)
                                    PetalixAction("Next",accent=service.accent) {featureIndex=(featureIndex+1)%visible.size}
                                }
                            }
                        }'''
    text=once(text,marker,replacement,'featured carousel controls')
    services.write_text(text)
