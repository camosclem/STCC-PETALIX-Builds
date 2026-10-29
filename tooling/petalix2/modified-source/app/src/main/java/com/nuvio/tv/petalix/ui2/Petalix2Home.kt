package com.nuvio.tv.petalix.ui2

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.LocalContentFocusRequester
import com.nuvio.tv.domain.model.*
import com.nuvio.tv.livetv.model.ChannelGroup
import com.nuvio.tv.livetv.home.LocalLiveTvHomeActions
import com.nuvio.tv.livetv.ui.LiveTvViewModel
import com.nuvio.tv.ui.screens.home.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** The supplied artwork is a sprite only for the seven static category illustrations.
 * All titles, posters, progress, EPG and actions below come from the application's real data. */
@Composable
fun CategoryArtwork(index:Int,modifier:Modifier=Modifier) {
    val context=LocalContext.current
    val bitmap=remember(context) { PetalixReference.bitmap(context) }
    Canvas(modifier) {
        val x=listOf(40,250,462,673,884,1095,1306)[index]
        drawImage(bitmap,srcOffset=IntOffset(x,416),srcSize=IntSize(193,78),dstSize=IntSize(size.width.toInt(),size.height.toInt()),filterQuality=FilterQuality.Medium)
    }
}

@Composable
fun PetalixTile(modifier:Modifier=Modifier,accent:Color=PetalixBlue,onClick:()->Unit,content:@Composable BoxScope.()->Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(modifier.onFocusChanged { focused=it.isFocused }.clip(RoundedCornerShape(8.dp))
        .background(Color(0xFF0A1422)).border(if(focused)3.dp else 1.dp,if(focused)accent else Color(0xFF334155),RoundedCornerShape(8.dp))
        .clickable(onClick=onClick),content=content)
}

@Composable
fun PetalixSection(title:String, actionLabel:String?=null,onAction:()->Unit={},content:@Composable ()->Unit) {
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal=26.dp),verticalAlignment=Alignment.CenterVertically) {
            Text(title,color=Color.White,fontWeight=FontWeight.Bold,fontSize=19.sp,modifier=Modifier.weight(1f))
            if(actionLabel!=null) {
                var focused by remember { mutableStateOf(false) }
                Text("$actionLabel ›",color=if(focused)PetalixBlue else Color(0xFFBDC8D8),fontSize=12.sp,modifier=Modifier.onFocusChanged { focused=it.isFocused }.clickable(onClick=onAction).padding(4.dp))
            }
        }
        content()
    }
}

@Composable
fun Petalix2Home(viewModel:HomeViewModel, onDetail:(String,String,String)->Unit,onResume:(ContinueWatchingItem)->Unit,
                 onService:(String)->Unit,onGuide:()->Unit,onLibrary:()->Unit, live:LiveTvViewModel=hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val channels by live.uiState.collectAsStateWithLifecycle()
    val clock by live.now.collectAsStateWithLifecycle()
    val programs by live.programs.collectAsStateWithLifecycle()
    val playback by live.playback.state.collectAsStateWithLifecycle()
    val chrome=LocalPetalixChrome.current
    val list=rememberLazyListState()
    val scope=rememberCoroutineScope()
    val contentFocus=LocalContentFocusRequester.current
    var heroFocused by remember { mutableStateOf(false) }
    var returnSection by rememberSaveable { mutableStateOf(0) }
    var returnKey by rememberSaveable { mutableStateOf("") }
    val returnFocus=remember { FocusRequester() }
    val actions=LocalLiveTvHomeActions.current
    val hero=state.heroItems.firstOrNull() ?: state.catalogRows.firstNotNullOfOrNull { it.items.firstOrNull() }
    val channel=channels.allVisibleChannels.firstOrNull { it.key==playback.channelKey } ?: channels.allVisibleChannels.firstOrNull()
    val program=channel?.let { live.currentProgram(it.key,clock) }
    var backdrop by remember(channel?.key,program?.title) { mutableStateOf<String?>(program?.icon) }
    LaunchedEffect(channel?.key,program?.title) { if(channel!=null) backdrop=live.channelPoster(channel,program) ?: program?.icon }
    LaunchedEffect(list) { snapshotFlow { list.firstVisibleItemIndex==0 && list.firstVisibleItemScrollOffset<100 }.distinctUntilChanged().collect { chrome.visible=it } }
    LaunchedEffect(Unit) {
        if(returnSection>0) { list.scrollToItem(returnSection);repeat(2){withFrameNanos {}};runCatching { returnFocus.requestFocus() } }
        else {withFrameNanos {};runCatching { contentFocus.requestFocus() }}
        live.awaitGroups();viewModel.refreshHomeCatalogsIfStale()
    }
    val categories=listOf("News","Sports","Entertainment","Kids","Local TV","Lifestyle","Documentaries")
    LazyColumn(state=list,modifier=Modifier.fillMaxSize().onPreviewKeyEvent { e ->
        if(e.type==KeyEventType.KeyDown && e.key==Key.DirectionUp && heroFocused && list.firstVisibleItemIndex==0 && list.firstVisibleItemScrollOffset==0) {
            chrome.open();chrome.hasFocus=true;true
        } else false
    },contentPadding=PaddingValues(bottom=32.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        item(key="hero") {
            Box(Modifier.fillMaxWidth().height(165.dp)) {
                AsyncImage(model=if(channel!=null)backdrop else hero?.backdropUrl,contentDescription=null,contentScale=ContentScale.Crop,modifier=Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(PetalixBackground,PetalixBackground.copy(alpha=.65f),Color.Transparent))))
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent,PetalixBackground))))
                Column(Modifier.align(Alignment.CenterStart).padding(start=44.dp,end=340.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text(if(channel!=null)"● LIVE" else "FEATURED",color=if(channel!=null)Color(0xFFFF3153) else PetalixBlue,fontSize=13.sp,fontWeight=FontWeight.Bold)
                    Text(program?.title ?: channel?.name ?: hero?.name ?: "Welcome to Petalix",color=Color.White,fontWeight=FontWeight.Bold,fontSize=30.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                    if(program!=null) {
                        val time=java.text.SimpleDateFormat("h:mm a",java.util.Locale.getDefault())
                        Text("${time.format(java.util.Date(program.startMs))} – ${time.format(java.util.Date(program.stopMs))}",color=Color.White,fontSize=14.sp)
                        val progress=((clock-program.startMs).toFloat()/(program.stopMs-program.startMs).coerceAtLeast(1)).coerceIn(0f,1f)
                        Box(Modifier.width(240.dp).height(5.dp).background(Color(0xFF334155),RoundedCornerShape(3.dp))) { Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(PetalixBlue,RoundedCornerShape(3.dp))) }
                    }
                    Text(program?.description ?: hero?.description ?: if(channel!=null)"${channel.name} • Live TV" else "Your live channels, movies and series in one place.",color=Color(0xFFD5DDE7),fontSize=13.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                    PetalixAction(if(channel!=null)"Watch Live" else if(hero!=null)"View Details" else "Open Live TV",Icons.Default.PlayArrow,modifier=Modifier.focusRequester(contentFocus).onFocusChanged { heroFocused=it.hasFocus }) {
                        returnSection=0;returnKey=""
                        if(channel!=null) { live.preview(channel);actions.openFullscreen() }
                        else if(hero!=null)onDetail(hero.id,hero.apiType,hero.sourceAddonBaseUrl.orEmpty()) else onGuide()
                    }
                }
            }
        }
        item(key="live-categories") {
            PetalixSection("Live TV","See All",onGuide) {
                LazyRow(modifier=Modifier.focusRestorer(),contentPadding=PaddingValues(horizontal=26.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(categories,key={i,_->i}) { i,name ->
                        PetalixTile(Modifier.width(124.dp).height(69.dp).then(if(returnSection==1 && returnKey==name)Modifier.focusRequester(returnFocus) else Modifier),onClick={ returnSection=1;returnKey=name;PetalixCategoryLaunch.request(name);onGuide() }) {
                            CategoryArtwork(i,Modifier.fillMaxSize())
                            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent,Color.Black.copy(alpha=.9f)))))
                            Text(name,color=Color.White,fontSize=13.sp,fontWeight=FontWeight.Bold,modifier=Modifier.align(Alignment.BottomCenter).padding(8.dp))
                        }
                    }
                }
            }
        }
        item(key="services") {
            PetalixSection("Streaming Services") {
                LazyRow(modifier=Modifier.focusRestorer(),contentPadding=PaddingValues(horizontal=26.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    items(PetalixServices.all,key={it.id}) { service -> ServiceTile(service,modifier=if(returnSection==2 && returnKey==service.id)Modifier.focusRequester(returnFocus) else Modifier) {returnSection=2;returnKey=service.id;onService(service.id)} }
                }
            }
        }
        item(key="continue") {
            PetalixSection("Continue Watching","My Lists",onLibrary) {
                if(state.continueWatchingItems.isEmpty()) Text("Your movies and episodes will appear here when you start watching.",color=Color(0xFF9BAABE),fontSize=13.sp,modifier=Modifier.padding(horizontal=26.dp,vertical=14.dp))
                else LazyRow(modifier=Modifier.focusRestorer(),contentPadding=PaddingValues(horizontal=26.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    items(state.continueWatchingItems,key={when(it){ is ContinueWatchingItem.InProgress->"${it.progress.contentId}:${it.progress.videoId}";is ContinueWatchingItem.NextUp->"${it.info.contentId}:${it.info.videoId}"}}) { item ->
                        val title=when(item){is ContinueWatchingItem.InProgress->item.progress.name;is ContinueWatchingItem.NextUp->item.info.name}
                        val image=when(item){is ContinueWatchingItem.InProgress->item.progress.backdrop?:item.progress.poster;is ContinueWatchingItem.NextUp->item.info.backdrop?:item.info.poster}
                        val episode=when(item){is ContinueWatchingItem.InProgress->item.progress.season?.let { "S$it E${item.progress.episode}" }.orEmpty();is ContinueWatchingItem.NextUp->"S${item.info.season} E${item.info.episode}"}
                        val progress=when(item){is ContinueWatchingItem.InProgress->item.progress.progressPercentage;else->0f}
                        PetalixTile(Modifier.width(168.dp).height(85.dp).then(if(returnSection==3 && returnKey==title+episode)Modifier.focusRequester(returnFocus) else Modifier),onClick={returnSection=3;returnKey=title+episode;onResume(item)}) {
                            AsyncImage(image,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent,Color.Black))))
                            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(9.dp),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                                Text(title,color=Color.White,fontSize=13.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                                Text(episode,color=Color(0xFFCBD5E1),fontSize=11.sp)
                                Box(Modifier.fillMaxWidth().height(3.dp).background(Color.DarkGray)) { Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(PetalixBlue)) }
                            }
                        }
                    }
                }
            }
        }
        item(key="recommended") {
            val recommendations=state.catalogRows.flatMap { row->row.items.map { it to row.addonBaseUrl } }.distinctBy { "${it.first.apiType}:${it.first.id}" }.take(40)
            PetalixSection("Recommended For You") {
                if(recommendations.isEmpty())Text(if(state.isLoading)"Loading your catalog…" else "Connect your authorized content sources in Settings to browse recommendations.",color=Color(0xFF9BAABE),fontSize=13.sp,modifier=Modifier.padding(horizontal=26.dp,vertical=16.dp))
                else LazyRow(modifier=Modifier.focusRestorer(),contentPadding=PaddingValues(horizontal=26.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    items(recommendations,key={"${it.first.apiType}:${it.first.id}"}) { (meta,url)-> MetadataTile(meta,modifier=if(returnSection==4 && returnKey==meta.id+meta.apiType)Modifier.focusRequester(returnFocus) else Modifier,onClick={returnSection=4;returnKey=meta.id+meta.apiType;onDetail(meta.id,meta.apiType,url)}) }
                }
            }
        }
    }
}

object PetalixCategoryLaunch {
    private var requested:String?=null
    @Synchronized fun request(category:String){requested=category}
    @Synchronized fun consume():String?=requested.also { requested=null }
    val terms=mapOf("News" to listOf("news"),"Sports" to listOf("sport","espn"),"Entertainment" to listOf("entertainment","general"),"Kids" to listOf("kids","children","family"),"Local TV" to listOf("local","locals"),"Lifestyle" to listOf("lifestyle","home","food","cooking"),"Documentaries" to listOf("document","discovery","nature","history"))
    fun matches(category:String,group:String):Boolean=terms[category].orEmpty().any { group.contains(it,true) }
}

@Composable
fun MetadataTile(meta:MetaPreview,accent:Color=PetalixBlue,landscape:Boolean=false,rank:Int?=null,modifier:Modifier=Modifier,onClick:()->Unit) {
    PetalixTile(modifier.width(if(landscape)220.dp else 116.dp).height(if(landscape)124.dp else 176.dp),accent,onClick) {
        AsyncImage(if(landscape)meta.backdropUrl else meta.poster,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent,Color.Black.copy(alpha=.95f)))))
        if(rank!=null)Text(rank.toString(),color=Color.White,fontSize=54.sp,fontWeight=FontWeight.Black,modifier=Modifier.align(Alignment.BottomStart).padding(5.dp))
        Text(meta.name,color=Color.White,fontSize=12.sp,maxLines=2,overflow=TextOverflow.Ellipsis,modifier=Modifier.align(Alignment.BottomStart).padding(start=if(rank!=null)48.dp else 8.dp,end=8.dp,bottom=9.dp))
    }
}

object PetalixReference {
    private var cached:ImageBitmap?=null
    @Synchronized fun bitmap(context:android.content.Context):ImageBitmap = cached ?: context.assets.open("petalix2/design-reference.jpg").use { requireNotNull(BitmapFactory.decodeStream(it)).asImageBitmap() }.also { cached=it }
}
@Composable
fun ServiceArtwork(id:String,modifier:Modifier=Modifier) {
    val context=LocalContext.current
    val bitmap=remember(context) { PetalixReference.bitmap(context) }
    val index=mapOf("netflix" to 0,"prime" to 1,"hulu" to 2,"disney" to 3,"max" to 4,"paramount" to 5,"apple" to 7,"peacock" to 8,"tubi" to 9)[id] ?: 0
    val x=listOf(40,194,344,490,636,782,928,1075,1222,1370)[index]
    Canvas(modifier) { drawImage(bitmap,srcOffset=IntOffset(x,588),srcSize=IntSize(132,72),dstSize=IntSize(size.width.toInt(),size.height.toInt()),filterQuality=FilterQuality.Medium) }
}

@Composable
fun PetalixReferenceLogo(modifier:Modifier=Modifier) {
    val context=LocalContext.current
    val bitmap=remember(context) { PetalixReference.bitmap(context) }
    Canvas(modifier.semantics {contentDescription="Petalix"}) {
        val height=(size.width*42/285).toInt()
        drawImage(bitmap,srcOffset=IntOffset(37,32),srcSize=IntSize(285,42),dstOffset=IntOffset(0,(size.height.toInt()-height)/2),dstSize=IntSize(size.width.toInt(),height),filterQuality=FilterQuality.High)
    }
}
