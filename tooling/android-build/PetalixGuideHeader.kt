package com.nuvio.tv.petalix.ui2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.livetv.model.LiveChannel
import com.nuvio.tv.livetv.model.EpgProgram

@Composable
fun PetalixGuideHeader(channel:LiveChannel?,program:EpgProgram?,poster:String?,start:Long?,stop:Long?,now:Long,
    showPreview:Boolean,playingKey:String?,buffering:Boolean,error:String?,status:String?,playlist:String?,
    previewBounds:(Rect?)->Unit,glassBounds:(Rect?)->Unit,gearFocus:FocusRequester,onGear:()->Unit) {
    Row(Modifier.fillMaxWidth().height(170.dp).padding(horizontal=24.dp,vertical=10.dp)
        .onGloballyPositioned{glassBounds(it.boundsInRoot())},horizontalArrangement=Arrangement.spacedBy(22.dp)) {
        Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp)).background(Color(0xFF0A192B))) {
            AsyncImage(poster ?: program?.icon,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop,alpha=.3f)
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xFF091727),Color(0xCC091727),Color.Transparent))))
            Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    AsyncImage(channel?.logo,null,Modifier.width(72.dp).height(30.dp),contentScale=ContentScale.Fit)
                    Text(channel?.name ?: "Select a channel",color=Color(0xFF8EBEFF),fontSize=13.sp,fontWeight=FontWeight.SemiBold,maxLines=1)
                    Spacer(Modifier.weight(1f))
                    PetalixAction("Guide options",modifier=Modifier.focusRequester(gearFocus),onClick=onGear)
                }
                Text(program?.title ?: channel?.name ?: "Your live TV",color=Color.White,fontSize=26.sp,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis)
                val live=start!=null && stop!=null && now>=start && now<stop
                Text(if(start!=null && stop!=null)java.text.SimpleDateFormat("h:mm a",java.util.Locale.getDefault()).let{it.format(java.util.Date(start))+" – "+it.format(java.util.Date(stop))}+(if(live)"  •  LIVE" else "")
                    else status ?: "Choose a category and channel below",color=Color(0xFF9FB3CC),fontSize=12.sp)
                Text(program?.description ?: playlist.orEmpty(),color=Color.LightGray,fontSize=12.sp,maxLines=2,overflow=TextOverflow.Ellipsis)
                if(live){val begin=start ?: 0L;val end=stop ?: (begin+1);val p=((now-begin).toFloat()/(end-begin).coerceAtLeast(1)).coerceIn(0f,1f)
                    Box(Modifier.fillMaxWidth().height(3.dp).background(Color.White.copy(alpha=.12f))){Box(Modifier.fillMaxWidth(p).fillMaxHeight().background(PetalixBlue))}}
            }
        }
        if(showPreview)Box(Modifier.fillMaxHeight().aspectRatio(16f/9f).clip(RoundedCornerShape(12.dp)).background(Color.Black)
            .onGloballyPositioned{previewBounds(it.boundsInRoot())},contentAlignment=Alignment.Center) {
            DisposableEffect(Unit){onDispose{previewBounds(null)}}
            if(error!=null)Text(error,color=Color.LightGray,fontSize=12.sp,modifier=Modifier.padding(16.dp),maxLines=3)
            else if(playingKey==null || buffering)Text(if(buffering)"Connecting…" else "Press OK to preview",color=Color.LightGray,fontSize=13.sp)
        }
    }
    DisposableEffect(Unit){onDispose{glassBounds(null)}}
}
