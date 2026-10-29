package com.nuvio.tv.ui.screens.player

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.nuvio.tv.core.player.thumbnail.SeekThumbnails
import kotlin.math.abs

private fun seekPainter(shown:SeekThumbnails.Shown?):BitmapPainter? {
    val bitmap=shown?.bitmap?:return null
    val crop=shown.crop?.takeIf { it.fits(bitmap.width,bitmap.height) }
    return BitmapPainter(bitmap.asImageBitmap(),IntOffset(crop?.left?:0,crop?.top?:0),IntSize((bitmap.width-(crop?.let{it.left+it.right}?:0)).coerceAtLeast(1),(bitmap.height-(crop?.let{it.top+it.bottom}?:0)).coerceAtLeast(1)))
}

/** Local frame strip above the existing seek overlay; no change to the player's commit or resume behavior. */
@Composable
fun SeekThumbnailOverlayHost(uiState:PlayerUiState,viewModel:PlayerViewModel,modifier:Modifier=Modifier) {
    val tick by SeekThumbnails.tick
    val target=uiState.pendingPreviewSeekPosition?:return
    val timeline by viewModel.playbackTimeline.collectAsState()
    if(timeline.duration<=0||timeline.isLive)return
    val aspect=SeekThumbnails.frameAspect()?:return
    val spacing=SeekThumbnails.stripSpacingMs()?:SeekThumbnails.SPACING_MS
    val steps=remember { longArrayOf(-1,0,0) }
    if(steps[0]>=0&&steps[0]!=target){steps[2]=steps[1];steps[1]=abs(target-steps[0])}
    steps[0]=target
    val multiple=SeekStripLayout.multiple(steps[1],steps[2],spacing)
    val step=spacing*multiple
    Box(modifier.fillMaxSize().padding(start=40.dp,end=40.dp,bottom=140.dp),contentAlignment=Alignment.BottomCenter) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)) {
            for(index in -2..2) {
                val time=if(index==0)target else SeekStripLayout.pictureMs((target+index*step).toDouble(),spacing)
                val shown=remember(time,tick) { if(index==0)SeekThumbnails.shownFor(time) else SeekThumbnails.tileFor(time)?.takeIf{it.exact} }
                val painter=remember(shown){seekPainter(shown)}
                val height=if(index==0)118.dp else 80.dp
                val shape=RoundedCornerShape(8.dp)
                Box(Modifier.height(height).width(height*aspect.coerceIn(.6f,2.5f)).clip(shape).background(Color.Black.copy(alpha=.85f)).then(if(index==0)Modifier.border(2.dp,Color(0xff63c8f2),shape) else Modifier),contentAlignment=Alignment.Center) {
                    if(time in 0..timeline.duration&&painter!=null)Image(painter,contentDescription=null,contentScale=ContentScale.FillBounds,modifier=Modifier.fillMaxSize())
                    else if(index==0)Text("Preview loading…")
                }
            }
        }
    }
}

@Composable
fun SeekThumbnailProgressHint(uiState:PlayerUiState,modifier:Modifier=Modifier) {
    val tick by SeekThumbnails.tick
    if(uiState.isPlaying||uiState.isBuffering||uiState.pendingPreviewSeekPosition!=null||uiState.error!=null)return
    val percent=remember(tick){SeekThumbnails.progressPercent()}?:return
    Box(modifier.fillMaxSize().padding(40.dp),contentAlignment=Alignment.BottomEnd) {
        Text("Seek previews $percent%",modifier=Modifier.background(Color.Black.copy(alpha=.8f),RoundedCornerShape(6.dp)).padding(10.dp))
    }
}
