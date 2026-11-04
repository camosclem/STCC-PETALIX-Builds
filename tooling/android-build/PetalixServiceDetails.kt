package com.nuvio.tv.petalix.ui2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.domain.model.*
import com.nuvio.tv.ui.screens.detail.MetaDetailsUiState

/** Service-owned presentation over the existing metadata, library, resume and playback engine. */
@Composable
fun PetalixServiceDetails(service:PetalixService,state:MetaDetailsUiState,
    playLabel:String,canPlay:Boolean,onPlay:()->Unit,onEpisode:(Video)->Unit,
    onSeason:(Int)->Unit,onLibrary:()->Unit,onBack:()->Unit,
    returnSeason:Int?,returnEpisode:Int?,onReturnConsumed:()->Unit) {
    val meta=state.meta ?: return
    val playFocus=remember { FocusRequester() }
    var lastEpisode by rememberSaveable(meta.id) { mutableStateOf<String?>(null) }
    val episodeFocus=remember { FocusRequester() }
    val restore=state.episodesForSeason.firstOrNull { it.season==returnSeason && it.episode==returnEpisode }
        ?: state.episodesForSeason.firstOrNull { it.id==lastEpisode }
    LaunchedEffect(returnSeason) {
        returnSeason?.takeIf { it in state.seasons && it!=state.selectedSeason }?.let(onSeason)
    }
    LaunchedEffect(meta.id,restore?.id) {
        withFrameNanos{}
        runCatching { if(restore!=null)episodeFocus.requestFocus() else playFocus.requestFocus() }
        if(restore!=null)onReturnConsumed()
    }
    val header:@Composable ()->Unit = {
        Row(Modifier.fillMaxWidth().padding(horizontal=28.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
            PetalixAction("‹ Back to ${service.name}",accent=service.accent,onClick=onBack)
            Spacer(Modifier.weight(1f));HubWordmark(service,132)
        }
    }
    val actions:@Composable ()->Unit = {
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
            PetalixAction(if(canPlay)playLabel else "Check sources",accent=service.accent,modifier=Modifier.focusRequester(playFocus),onClick=onPlay)
            PetalixAction(if(state.isInLibrary)"✓ My List" else "+ My List",accent=service.accent,onClick=onLibrary)
        }
    }
    val episodes:@Composable (Boolean)->Unit = { horizontal ->
        if(state.seasons.isNotEmpty()) {
            Text(if(service.id=="netflix")"Episodes" else if(service.id=="hulu")"Episodes & Details" else "Episodes",color=Color.White,
                fontSize=24.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(horizontal=28.dp,vertical=16.dp))
            LazyRow(Modifier.focusRestorer(),contentPadding=PaddingValues(horizontal=28.dp,vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                items(state.seasons,key={it}) { season ->
                    PetalixAction("Season $season",selected=season==state.selectedSeason,accent=service.accent){onSeason(season)}
                }
            }
            if(state.episodesForSeason.isEmpty())Text("No episodes available for this season.",color=Color.LightGray,modifier=Modifier.padding(28.dp))
            if(horizontal) {
                LazyRow(Modifier.focusRestorer(),contentPadding=PaddingValues(horizontal=28.dp,vertical=12.dp),horizontalArrangement=Arrangement.spacedBy(18.dp)) {
                    items(state.episodesForSeason,key={it.id}) { video ->
                        ServiceEpisodeCard(service,video,state,Modifier.width(274.dp).then(if(video.id==restore?.id)Modifier.focusRequester(episodeFocus) else Modifier),false){lastEpisode=video.id;onEpisode(video)}
                    }
                }
            } else {
                state.episodesForSeason.forEach { video ->
                    key(video.id) { ServiceEpisodeCard(service,video,state,Modifier.fillMaxWidth().padding(horizontal=28.dp,vertical=6.dp)
                        .then(if(video.id==restore?.id)Modifier.focusRequester(episodeFocus) else Modifier),true){lastEpisode=video.id;onEpisode(video)} }
                }
            }
        }
    }
    LazyColumn(Modifier.fillMaxSize().background(service.background),contentPadding=PaddingValues(bottom=32.dp)) {
        item("service-header"){header()}
        when(service.id) {
            "netflix" -> {
                item("billboard") {
                    Box(Modifier.fillMaxWidth().height(340.dp)) {
                        ServiceDetailArt(meta)
                        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Black,Color.Black.copy(alpha=.62f),Color.Transparent))))
                        Column(Modifier.align(Alignment.BottomStart).widthIn(max=580.dp).padding(28.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                            Text("N  ${if(meta.type==ContentType.SERIES)"SERIES" else "FILM"}",color=service.accent,fontSize=17.sp,fontWeight=FontWeight.Black,letterSpacing=4.sp)
                            ServiceDetailCopy(meta,service,false);actions()
                        }
                    }
                }
                item("episode-list"){episodes(false)}
            }
            "prime" -> {
                item("feature") {
                    Row(Modifier.fillMaxWidth().padding(28.dp),horizontalArrangement=Arrangement.spacedBy(30.dp)) {
                        AsyncImage(meta.poster,null,Modifier.width(170.dp).height(255.dp),contentScale=ContentScale.Crop)
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                            Text("Included with ${service.name} catalog",color=service.accent,fontSize=14.sp)
                            ServiceDetailCopy(meta,service,false);actions()
                        }
                    }
                }
                item("episodes"){episodes(false)}
            }
            "disney","paramount","max" -> {
                item("panoramic-feature") {
                    Box(Modifier.fillMaxWidth().height(370.dp)) {
                        ServiceDetailArt(meta)
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent,service.background))))
                        Column(Modifier.align(Alignment.BottomCenter).widthIn(max=680.dp).padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,
                            verticalArrangement=Arrangement.spacedBy(14.dp)) {
                            ServiceDetailCopy(meta,service,true);actions()
                        }
                    }
                }
                item("episodes"){episodes(true)}
            }
            "apple" -> {
                item("panoramic") {Box(Modifier.fillMaxWidth().height(300.dp)){ServiceDetailArt(meta)}}
                item("title") {
                    Row(Modifier.fillMaxWidth().padding(28.dp),horizontalArrangement=Arrangement.spacedBy(36.dp),verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)){ServiceDetailCopy(meta,service,false)}
                        Column(verticalArrangement=Arrangement.spacedBy(12.dp)){actions()}
                    }
                }
                item("episodes"){episodes(true)}
            }
            "hulu" -> {
                item("editorial-feature") {
                    Row(Modifier.fillMaxWidth().height(320.dp),verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1.1f).padding(28.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                            Text(if(meta.type==ContentType.SERIES)"TV SERIES" else "MOVIE",color=service.accent,letterSpacing=3.sp,fontSize=13.sp)
                            ServiceDetailCopy(meta,service,false);actions()
                        }
                        Box(Modifier.weight(1f).fillMaxHeight()){ServiceDetailArt(meta)}
                    }
                }
                item("episodes"){episodes(true)}
            }
            else -> {
                item("feature") {
                    Box(Modifier.fillMaxWidth().height(320.dp)) {
                        ServiceDetailArt(meta)
                        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(service.background,service.background.copy(alpha=.6f),Color.Transparent))))
                        Column(Modifier.align(Alignment.BottomStart).widthIn(max=580.dp).padding(28.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                            ServiceDetailCopy(meta,service,false);actions()
                        }
                    }
                }
                item("episodes"){episodes(service.id!="tubi")}
            }
        }
        item("about") {
            Column(Modifier.fillMaxWidth().padding(28.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text("About ${meta.name}",color=Color.White,fontSize=23.sp,fontWeight=FontWeight.Bold)
                Text(meta.description.orEmpty(),color=Color.LightGray,fontSize=15.sp)
                if(meta.cast.isNotEmpty())Text("Starring ${meta.cast.take(8).joinToString()}",color=Color.Gray,fontSize=13.sp)
                if(meta.director.isNotEmpty())Text("Directed by ${meta.director.joinToString()}",color=Color.Gray,fontSize=13.sp)
            }
        }
    }
}
@Composable private fun ServiceDetailArt(meta:Meta) {
    AsyncImage(meta.backdropUrl,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
}
@Composable private fun ServiceDetailCopy(meta:Meta,service:PetalixService,center:Boolean) {
    Column(horizontalAlignment=if(center)Alignment.CenterHorizontally else Alignment.Start,verticalArrangement=Arrangement.spacedBy(10.dp)) {
        if(meta.logo!=null)AsyncImage(meta.logo,meta.name,Modifier.widthIn(max=350.dp).height(82.dp),contentScale=ContentScale.Fit)
        else Text(meta.name,color=Color.White,fontSize=34.sp,fontWeight=FontWeight.Black,maxLines=2,overflow=TextOverflow.Ellipsis)
        Text(listOfNotNull(meta.releaseInfo,meta.ageRating,meta.runtime,meta.genres.take(2).joinToString().takeIf { it.isNotEmpty() }).joinToString("   •   "),color=service.accent,fontSize=13.sp,maxLines=2)
        Text(meta.description.orEmpty(),color=Color(0xFFE0E2E8),fontSize=14.sp,maxLines=3,overflow=TextOverflow.Ellipsis)
    }
}
@Composable private fun ServiceEpisodeCard(service:PetalixService,video:Video,state:MetaDetailsUiState,modifier:Modifier,wide:Boolean,onClick:()->Unit) {
    val progress=state.episodeProgressMap[Pair(video.season ?: 0,video.episode ?: 0)]?.progressPercentage ?: 0f
    val watched=Pair(video.season ?: 0,video.episode ?: 0) in state.watchedEpisodes
    PetalixTile(modifier,service.accent,onClick=onClick) {
        if(wide)Row(Modifier.fillMaxWidth().background(service.accent.copy(alpha=.05f)).padding(10.dp),horizontalArrangement=Arrangement.spacedBy(20.dp),verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.width(206.dp).height(116.dp)){EpisodeArt(video,progress,service)}
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                EpisodeCopy(video,watched)
                Text(video.overview.orEmpty(),color=Color.LightGray,fontSize=13.sp,maxLines=2,overflow=TextOverflow.Ellipsis)
            }
        } else Column(Modifier.fillMaxWidth().background(service.accent.copy(alpha=.05f))) {
            Box(Modifier.fillMaxWidth().height(154.dp)){EpisodeArt(video,progress,service)}
            Column(Modifier.padding(12.dp)){EpisodeCopy(video,watched)}
        }
    }
}
@Composable private fun EpisodeArt(video:Video,progress:Float,service:PetalixService) {
    Box(Modifier.fillMaxSize().background(service.accent.copy(alpha=.12f))) {
        AsyncImage(video.thumbnail,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
        if(progress>0f)Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(progress.coerceIn(0f,1f)).height(4.dp).background(service.accent))
    }
}
@Composable private fun EpisodeCopy(video:Video,watched:Boolean) {
    Text("${video.episode ?: ""}. ${video.title}",color=Color.White,fontWeight=FontWeight.Bold,fontSize=17.sp,maxLines=2,overflow=TextOverflow.Ellipsis)
    Text(listOfNotNull(if(watched)"✓ Watched" else null,video.runtime?.let { "$it min" },if(video.available==false)"Unavailable" else null).joinToString(" • "),color=Color.LightGray,fontSize=12.sp)
}
