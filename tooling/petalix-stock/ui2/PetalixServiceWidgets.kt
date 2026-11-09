// Shared controls and focus previews extracted unchanged from panel 8.3 build 1793881218.
package com.nuvio.tv.petalix.ui2

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.data.trailer.TrailerService
import com.nuvio.tv.core.player.LocalTrailerPlayerPool
import com.nuvio.tv.ui.components.TrailerPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

val PetalixBlue = Color(0xFF087CFF)

@Composable
fun PetalixTile(modifier:Modifier=Modifier,accent:Color=PetalixBlue,onClick:()->Unit,onFocus:(Boolean)->Unit={},content:@Composable BoxScope.()->Unit) {
    var focused by remember { mutableStateOf(false) }
    val petalixTileScale by animateFloatAsState(if(focused)1.045f else 1f,tween(170),label="petalixTileScale")
    Box(modifier.graphicsLayer { scaleX=petalixTileScale;scaleY=petalixTileScale }.onFocusChanged { focused=it.hasFocus;onFocus(it.hasFocus) }.clip(RoundedCornerShape(14.dp))
        .background(Color(0xFF0A1422)).border(if(focused)3.dp else 1.dp,if(focused)accent else Color(0xFF334155),RoundedCornerShape(14.dp))
        .clickable(onClick=onClick),content=content)
}

@Composable
fun PetalixAction(label:String,icon:ImageVector?=null,selected:Boolean=false,modifier:Modifier=Modifier,accent:Color=PetalixBlue,onClick:()->Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(modifier.onFocusChanged { focused=it.isFocused }.background(if(focused || selected) accent.copy(alpha=0.28f) else Color.Transparent,RoundedCornerShape(18.dp))
        .border(if(focused)2.dp else 1.dp,if(focused)accent else if(selected)accent.copy(alpha=.7f) else Color.Transparent,RoundedCornerShape(18.dp))
        .clickable(onClick=onClick).padding(horizontal=12.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)) {
        if(icon!=null)Icon(icon,null,Modifier.size(18.dp),tint=Color.White)
        Text(label,color=Color.White,fontSize=13.sp,maxLines=1)
    }
}

@HiltViewModel
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
fun MetadataTile(meta:MetaPreview,accent:Color=PetalixBlue,landscape:Boolean=false,rank:Int?=null,previewEnabled:Boolean=false,modifier:Modifier=Modifier,onClick:()->Unit) {
    val trailerVm:PetalixTileTrailerViewModel=hiltViewModel()
    val sources by trailerVm.sources.collectAsStateWithLifecycle()
    val pool=LocalTrailerPlayerPool.current
    var focused by remember(meta.id) { mutableStateOf(false) }
    val source=sources[meta.id]
    LaunchedEffect(focused,meta.id) {
        if(focused) { trailerVm.setFocusedBackdrop(meta.backdropUrl);if(previewEnabled) {delay(900);trailerVm.resolve(meta)} }
    }
    PetalixTile(modifier.width(if(landscape)220.dp else 116.dp).height(if(landscape)124.dp else 176.dp).onFocusChanged { focused=it.isFocused },accent,onClick) {
        AsyncImage(if(landscape)meta.backdropUrl else meta.poster,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
        if(previewEnabled && source!=null && focused) TrailerPlayer(trailerUrl=source.videoUrl,trailerAudioUrl=source.audioUrl,isPlaying=true,muted=false,onEnded={},modifier=Modifier.fillMaxSize(),enter=androidx.compose.animation.EnterTransition.None,exit=androidx.compose.animation.ExitTransition.None,trailerPlayerPool=pool)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent,Color.Black.copy(alpha=.95f)))))
        if(rank!=null)Text(rank.toString(),color=Color.White,fontSize=54.sp,fontWeight=FontWeight.Black,modifier=Modifier.align(Alignment.BottomStart).padding(5.dp))
        Text(meta.name,color=Color.White,fontSize=12.sp,maxLines=2,overflow=TextOverflow.Ellipsis,modifier=Modifier.align(Alignment.BottomStart).padding(start=if(rank!=null)48.dp else 8.dp,end=8.dp,bottom=9.dp))
    }
}

@Composable
fun ServiceArtwork(id:String,modifier:Modifier=Modifier) {
    val logo=ArvioServiceMedia.logo(id)
    if(logo!=null) {
        AsyncImage(logo,id,modifier.padding(10.dp),contentScale=ContentScale.Fit)
    } else {
        Box(modifier,contentAlignment=Alignment.Center) {
            Text(PetalixServices.find(id)?.name ?: id.replaceFirstChar{it.uppercase()},color=Color.White,
                fontWeight=FontWeight.Bold,fontSize=24.sp,maxLines=1)
        }
    }
}

object ArvioServiceMedia {
    private val videos=setOf("netflix","disney","prime","hulu","max","paramount","apple","peacock")
    private val logos=setOf("netflix","disney","prime","hulu","max","paramount","apple","peacock")
    fun video(id:String)=if(id in videos)"file:///android_asset/petalix2/service-media/$id.mp4" else null
    fun logo(id:String)=if(id in logos)"file:///android_asset/petalix2/service-logos/$id.svg" else null
}
@Composable fun ArvioServiceLogo(service:PetalixService,modifier:Modifier=Modifier) {
    val logo=ArvioServiceMedia.logo(service.id)
    if(logo!=null)AsyncImage(logo,service.name,modifier.padding(16.dp),contentScale=ContentScale.Fit)
    else Box(modifier,contentAlignment=Alignment.Center){Text(service.name,color=service.accent,fontSize=28.sp,fontWeight=FontWeight.Black)}
}

