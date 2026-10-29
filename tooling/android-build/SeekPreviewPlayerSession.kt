package com.nuvio.tv.ui.screens.player

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.Player
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.nuvio.tv.core.player.thumbnail.*
import com.nuvio.tv.data.local.InternalPlayerEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

/** Serializes engine teardown and cache removal so an old video cannot delete a newer video's cache. */
internal object SeekPreviewTrialSessions {
    private val generation=AtomicLong()
    private val lock=Mutex()
    private val cleanup=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    fun next():Long=generation.incrementAndGet()
    fun cleanupIfUnused(context:Context) {
        if(generation.get()!=0L)return
        cleanup.launch { lock.withLock { if(generation.get()==0L)ThumbnailCache.clearAll(context) } }
    }
    suspend fun open(ticket:Long,context:Context,start:suspend ()->Unit) {
        lock.withLock {
            if(ticket!=generation.get())return
            SeekThumbnails.stopSession(handBackPlayer=false)
            ThumbnailCache.clearAll(context)
            currentCoroutineContext().ensureActive()
            if(ticket==generation.get())start()
        }
    }
    fun close(context:Context,ticket:Long?=null) {
        if(ticket!=null&&ticket!=generation.get())return
        val ending=next()
        SeekThumbnails.stopSession(handBackPlayer=false)
        cleanup.launch { lock.withLock {
            if(ending==generation.get()) {
                ThumbnailCache.clearAll(context)
                delay(750)
                if(ending==generation.get())ThumbnailCache.clearAll(context)
            }
        } }
    }
}

@Composable
internal fun SeekPreviewPlayerSession(viewModel:PlayerViewModel,uiState:PlayerUiState) {
    val context=LocalContext.current.applicationContext
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val mode by remember(context){SeekThumbnailPreferences.modeFlow(context)}.collectAsState(initial=SeekThumbMode.OFF)
    var ticket by remember { mutableLongStateOf(0L) }
    val mpv=uiState.internalPlayerEngine==InternalPlayerEngine.MVP_PLAYER
    LaunchedEffect(mode,uiState.currentStreamUrl,uiState.isTorrentStream,mpv) {
        val current=SeekPreviewTrialSessions.next();ticket=current
        SeekPreviewTrialSessions.open(current,context) {
            val url=uiState.currentStreamUrl
            if(mode==SeekThumbMode.OFF||url.isNullOrBlank()||mpv||uiState.isTorrentStream||!isThumbnailSource(url,viewModel.seekPreviewMimeType()))return@open
            SeekThumbnails.hostResumed=lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            SeekThumbnails.startWhenEligible(context,ThumbSource(url,viewModel.getCurrentHeaders(),viewModel.seekPreviewIdentity(),viewModel.seekPreviewFileSize(),playbackConnections=1),{viewModel.exoPlayer},pauseForPrepare={
                val player=viewModel.exoPlayer
                if(player==null)false else player.playWhenReady.also { if(it)player.pause() }
            })
        }
    }
    LaunchedEffect(uiState.pendingPreviewSeekPosition) { uiState.pendingPreviewSeekPosition?.let { SeekThumbnails.notePriority(it) } }
    val player=viewModel.exoPlayer
    DisposableEffect(player) {
        val listener=object:Player.Listener {
            override fun onPlaybackStateChanged(playbackState:Int) { if(playbackState==Player.STATE_ENDED)SeekPreviewTrialSessions.close(context,ticket) }
        }
        player?.addListener(listener)
        onDispose { player?.removeListener(listener) }
    }
    DisposableEffect(lifecycle) {
        val observer=LifecycleEventObserver { _,event->
            if(event==Lifecycle.Event.ON_PAUSE||event==Lifecycle.Event.ON_STOP)SeekThumbnails.hostResumed=false
            if(event==Lifecycle.Event.ON_RESUME)SeekThumbnails.hostResumed=true
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer);SeekThumbnails.hostResumed=false;SeekPreviewTrialSessions.close(context) }
    }
}

@Composable
internal fun SeekPreviewOverlays(uiState:PlayerUiState,viewModel:PlayerViewModel) {
    val preparation by SeekThumbnails.prepareUi
    androidx.activity.compose.BackHandler(enabled=preparation !is SeekThumbnails.PrepareUi.Hidden) { SeekThumbnails.startWatchingNow() }
    SeekThumbnailOverlayHost(uiState,viewModel,Modifier.zIndex(2.7f))
    if(preparation is SeekThumbnails.PrepareUi.Hidden)SeekThumbnailProgressHint(uiState,Modifier.zIndex(2.7f))
    else Box(Modifier.fillMaxSize().zIndex(8f).background(Color.Black.copy(alpha=.92f)),contentAlignment=Alignment.Center) {
        Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(18.dp)) {
            Text("Preparing seek previews")
            val progress=preparation as? SeekThumbnails.PrepareUi.Generating
            if(progress!=null)Text("${progress.done} of ${progress.total} thumbnails")
            Text("Your TV generates these locally. You can start playback now.")
            Button(onClick={SeekThumbnails.startWatchingNow()}) { Text("Start watching now") }
        }
    }
}
