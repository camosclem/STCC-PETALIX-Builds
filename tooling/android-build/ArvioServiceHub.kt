// ARVIO-derived collection presentation. See assets/licenses/ARVIO-NOTICE.txt.
// Adapted for Petalix catalog/search state, landscape cards and existing playback routes.
package com.nuvio.tv.petalix.ui2

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.domain.model.*

object ArvioServiceMedia {
    private val videos=setOf("netflix","disney","prime","hulu","max","paramount","apple","peacock")
    private val logos=setOf("netflix","disney","prime","hulu","max","paramount","peacock")
    fun video(id:String)=if(id in videos)"file:///android_asset/petalix2/service-media/$id.mp4" else null
    fun logo(id:String)=if(id in logos)"file:///android_asset/petalix2/service-logos/$id.svg" else null
}
@Composable fun ArvioServiceLogo(service:PetalixService,modifier:Modifier=Modifier) {
    val logo=ArvioServiceMedia.logo(service.id)
    if(logo!=null)AsyncImage(logo,service.name,modifier.padding(16.dp),contentScale=ContentScale.Fit)
    else Box(modifier,contentAlignment=Alignment.Center){Text(service.name,color=service.accent,fontSize=28.sp,fontWeight=FontWeight.Black)}
}

/** ARVIO CollectionTabChip, adapted to TV focus actions in Petalix. */
@Composable private fun ArvioCollectionTab(label:String,selected:Boolean,onClick:()->Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape=RoundedCornerShape(50)
    val bg=when {selected->Color.White;focused->Color.Transparent;else->Color.White.copy(alpha=.08f)}
    val fg=if(selected)Color(0xFF080B10) else Color.White.copy(alpha=if(focused)1f else .75f)
    val edge=when {selected && focused->Color(0xFF4F7FB0);focused->Color.White;else->Color.Transparent}
    Box(Modifier.clip(shape).background(bg).border(if(focused)2.dp else 0.dp,edge,shape)
        .onFocusChanged {focused=it.hasFocus}.focusable().clickable(onClick=onClick).padding(horizontal=22.dp,vertical=10.dp)) {
        Text(label,color=fg,fontSize=14.sp,fontWeight=if(selected)FontWeight.Bold else FontWeight.SemiBold,letterSpacing=.4.sp)
    }
}

@Composable fun ArvioPetalixServiceScreen(onHome:()->Unit,onDetail:(String,String,String)->Unit,viewModel:PetalixServiceViewModel) {
    val service=viewModel.service ?: return
    val state by viewModel.state.collectAsStateWithLifecycle()
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()
    var tab by rememberSaveable(service.id){mutableStateOf("Movies")}
    var search by rememberSaveable(service.id){mutableStateOf(false)}
    var query by rememberSaveable(service.id){mutableStateOf("")}
    var collection by rememberSaveable(service.id){mutableStateOf<String?>(null)}
    var focusedKey by rememberSaveable(service.id){mutableStateOf<String?>(null)}
    // The intro plays once for each service entry. Returning from a title retains this state.
    var introFinished by rememberSaveable(service.id){mutableStateOf(false)}
    var introSkipped by rememberSaveable(service.id){mutableStateOf(false)}
    val searchFocus=remember{FocusRequester()}
    val gridState=rememberLazyListState()
    val returnFocus=remember{FocusRequester()}
    var restoring by remember{mutableStateOf(true)}
    val all=state.rows.flatMap{it.items}.distinctBy{"${it.apiType}:${it.id}"}
    val source=if(search && query.isNotBlank())searchState.items else collection?.let{name->state.rows.firstOrNull{it.catalogName==name}?.items} ?: all
    val items=source.filter {search || if(tab=="Movies")it.type==ContentType.MOVIE else it.type==ContentType.SERIES}
    val focused=items.firstOrNull{"${it.apiType}:${it.id}"==focusedKey} ?: items.firstOrNull()
    val video=ArvioServiceMedia.video(service.id)
    val introActive=video!=null && !introFinished && !introSkipped && !search
    LaunchedEffect(service.id){android.util.Log.i("PetalixServices","petalix-arvio-services-v5")}
    LaunchedEffect(search,query){viewModel.search(if(search)query else "")}
    LaunchedEffect(search){if(search){withFrameNanos{};runCatching{searchFocus.requestFocus()}}}
    LaunchedEffect(items.size) {
        if(restoring && items.isNotEmpty()){
            val index=items.indexOfFirst{"${it.apiType}:${it.id}"==focusedKey}.coerceAtLeast(0)
            gridState.scrollToItem(index/4)
            withFrameNanos{};runCatching{returnFocus.requestFocus()};restoring=false
        }
    }
    BackHandler {
        when {search->{search=false;query=""};collection!=null->collection=null;introActive->{introSkipped=true};else->onHome()}
    }
    Box(Modifier.fillMaxSize().background(service.background)) {
        // ARVIO CollectionBackdrop: one image continues behind spotlight and grid.
        Crossfade(focused?.backdropUrl,label="arvioCollectionBackdrop"){art->
            AsyncImage(art,null,Modifier.fillMaxHeight().fillMaxWidth(.78f).align(Alignment.CenterEnd),contentScale=ContentScale.Crop,alignment=BiasAlignment(1f,-.5f))
        }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to service.background,.25f to service.background,
            .4f to service.background.copy(alpha=.88f),.65f to service.background.copy(alpha=.45f),.92f to Color.Transparent,1f to service.background)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to service.background,.25f to Color.Transparent,.55f to service.background.copy(alpha=.18f),1f to service.background)))
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal=28.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                PetalixAction("‹ Home",accent=service.accent,onClick=onHome)
                ArvioServiceLogo(service,Modifier.width(140.dp).height(42.dp))
                Spacer(Modifier.weight(1f))
                PetalixAction("Search",accent=service.accent){search=!search;collection=null;introSkipped=true}
            }
            Box(Modifier.fillMaxWidth().height(196.dp)) {
                if(introActive) {
                    ArvioVideoHero(video!!,Modifier.fillMaxSize(),onEnded={introFinished=true})
                    PetalixAction("Skip intro",accent=service.accent,modifier=Modifier.align(Alignment.BottomEnd).padding(12.dp)){introSkipped=true}
                } else Column(Modifier.fillMaxSize().padding(horizontal=42.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
                    Text(service.name.uppercase(),color=service.accent,fontSize=11.sp,fontWeight=FontWeight.SemiBold,letterSpacing=1.2.sp)
                    Text(focused?.name ?: service.name,color=Color.White,fontSize=30.sp,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis)
                    Text(listOfNotNull(focused?.releaseInfo,focused?.imdbRating?.takeIf{it>0}?.let{"★ $it"}).joinToString("  •  "),color=Color.LightGray,fontSize=13.sp)
                    Text(focused?.description.orEmpty(),color=Color.LightGray,fontSize=14.sp,maxLines=3,overflow=TextOverflow.Ellipsis,modifier=Modifier.fillMaxWidth(.66f))
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal=42.dp,vertical=6.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                ArvioCollectionTab("Movies",tab=="Movies" && !search){tab="Movies";search=false;collection=null;restoring=true}
                ArvioCollectionTab("TV Shows",tab=="TV Shows" && !search){tab="TV Shows";search=false;collection=null;restoring=true}
                if(state.loading)Text("Loading…",color=service.accent,fontSize=12.sp)
                Spacer(Modifier.weight(1f))
                if(search && searchState.loading)Text("Searching…",color=service.accent,fontSize=12.sp)
            }
            if(search)OutlinedTextField(query,{query=it},singleLine=true,label={androidx.compose.material3.Text("Search ${service.name}")},
                modifier=Modifier.fillMaxWidth().padding(horizontal=42.dp,vertical=4.dp).focusRequester(searchFocus),
                colors=TextFieldDefaults.colors(focusedTextColor=Color.White,unfocusedTextColor=Color.White,
                    focusedContainerColor=service.background,unfocusedContainerColor=service.background))
            else LazyRow(contentPadding=PaddingValues(horizontal=42.dp,vertical=4.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                item {PetalixAction("All",selected=collection==null,accent=service.accent){collection=null;restoring=true}}
                items(state.rows,key={it.catalogName}){row->PetalixAction(row.catalogName,selected=collection==row.catalogName,accent=service.accent){collection=row.catalogName;restoring=true}}
            }
            val error=if(search)searchState.error else state.error
            if(error!=null)Row(Modifier.padding(horizontal=42.dp),verticalAlignment=Alignment.CenterVertically){
                Text(error,color=Color.LightGray,fontSize=12.sp,modifier=Modifier.weight(1f))
                PetalixAction("Retry",accent=service.accent){if(search)viewModel.search(query) else viewModel.load()}
            }
            LazyColumn(state=gridState,modifier=Modifier.weight(1f).fillMaxWidth(),contentPadding=PaddingValues(start=42.dp,end=42.dp,top=10.dp,bottom=20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                if(items.isEmpty() && !state.loading && !searchState.loading)item{Text(if(search)"No verified service matches. Try another title or the next results page." else "No titles in this collection.",color=Color.LightGray)}
                itemsIndexed(items.chunked(4),key={_,row->row.joinToString{"${it.apiType}:${it.id}"}}){rowIndex,row->
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                        row.forEachIndexed {column,item->
                            val key="${item.apiType}:${item.id}"
                            val restore=key==focusedKey || (focusedKey==null && rowIndex==0 && column==0)
                            MetadataTile(item,service.accent,landscape=true,previewEnabled=!introActive,
                                modifier=Modifier.weight(1f).aspectRatio(16f/9f).then(if(restore)Modifier.focusRequester(returnFocus) else Modifier)
                                    .onFocusChanged{if(it.hasFocus){focusedKey=key}}){onDetail(item.id,item.apiType,item.sourceAddonBaseUrl.orEmpty())}
                        }
                        repeat(4-row.size){Spacer(Modifier.weight(1f))}
                    }
                }
                item("more") {
                    if(search && searchState.more && !searchState.loading)PetalixAction("More search results",accent=service.accent){viewModel.search(query,true)}
                    else if(!search && state.more && !state.loading)PetalixAction("Load more",accent=service.accent){viewModel.load(true)}
                    Text("US availability: JustWatch via TMDB • Playback through your sources",color=Color.Gray,fontSize=9.sp,modifier=Modifier.padding(top=8.dp))
                }
            }
        }
    }
}
