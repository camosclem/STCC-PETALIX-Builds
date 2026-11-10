// Original Petalix 8.3 v14 service clips, adapted for stock Nuvio collection cards.
// Playback adaptation derives from ARVIO VideoHero, commit
// 3fc1a3e52af9e3d8cf3cd25f456bc92c5573d927 (Apache-2.0).
// See assets/licenses/ARVIO-NOTICE.txt and petalix2/service-media-manifest.json.
package com.nuvio.tv.petalix

import android.graphics.Color as AndroidColor
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.nuvio.tv.R
import kotlinx.coroutines.delay

private val petalixOriginalServiceIds = mapOf(
    "folder-WFGY1XPD" to "netflix",
    "folder-AT8KYHZO" to "prime",
    "folder-8ZBZOMW9" to "disney",
    "folder-LN9SPMQN" to "hulu",
    "folder-OA4T8NYO" to "max",
    "folder-ILJM8PD0" to "paramount",
    "folder-IQTF27K8" to "apple",
    "folder-9YQK1ZF1" to "peacock"
)

/** Original logos are bundled so a service still has its identity while offline. */
fun petalixServiceLogoUri(folderId: String): String? =
    if (folderId == "folder-petalix-britbox") "file:///android_asset/petalix/britbox-intro/logo.png" else petalixOriginalServiceIds[folderId]?.let {
        if (it == "apple") "file:///android_asset/petalix/apple-current.svg"
        else "file:///android_asset/petalix2/service-logos/$it.svg"
    }

/** No substitute clip or chime is supplied for services without a recovered original. */
fun petalixServiceIntroUri(folderId: String): String? =
    if (folderId == "folder-petalix-britbox") "file:///android_asset/petalix/britbox-intro/intro.mp4" else petalixOriginalServiceIds[folderId]?.let {
        "file:///android_asset/petalix2/service-media/$it.mp4"
    }

/** A soft tinted surface makes transparent service marks readable without black squares. */
@Composable
fun PetalixServiceTileBackdrop(folderId: String, modifier: Modifier = Modifier) {
    val colors = when (folderId) {
        "folder-WFGY1XPD" -> listOf(Color(0xFF402733), Color(0xFF262B42))
        "folder-AT8KYHZO" -> listOf(Color(0xFF28465C), Color(0xFF30374E))
        "folder-8ZBZOMW9" -> listOf(Color(0xFF324868), Color(0xFF413958))
        "folder-LN9SPMQN" -> listOf(Color(0xFF31605A), Color(0xFF35434D))
        "folder-OA4T8NYO" -> listOf(Color(0xFF493B64), Color(0xFF354254))
        "folder-ILJM8PD0" -> listOf(Color(0xFF365674), Color(0xFF3A4567))
        "folder-IQTF27K8" -> listOf(Color(0xFF485768), Color(0xFF303F55))
        "folder-9YQK1ZF1" -> listOf(Color(0xFF43536A), Color(0xFF3F3D5B))
        "folder-petalix-britbox" -> listOf(Color(0xFF6E9CB4), Color(0xFF536D91))
        else -> return
    }
    Box(modifier.background(Brush.linearGradient(colors)))
}

/**
 * Draw after the native card's image/GIF. It stays transparent until the first
 * video frame, and disappears after completion or error so the original fallback
 * remains visible. Pass focused=false when the card's animation setting is off.
 */
@Composable
fun PetalixServiceMediaOverlay(
    folderId: String,
    focused: Boolean,
    modifier: Modifier = Modifier
) {
    val videoUri = petalixServiceIntroUri(folderId) ?: return
    val lifecycleOwner = LocalLifecycleOwner.current
    var foreground by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    var dwellFinished by remember(folderId) { mutableStateOf(false) }
    var clipFinished by remember(folderId) { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner, folderId) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> foreground = true
                Lifecycle.Event.ON_STOP -> {
                    foreground = false
                    dwellFinished = false
                    // Do not unexpectedly replay a chime on return from another app.
                    clipFinished = true
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(folderId, focused, foreground) {
        dwellFinished = false
        if (!focused) {
            clipFinished = false
        } else if (foreground && !clipFinished) {
            delay(350)
            dwellFinished = true
        }
    }

    if (focused && foreground && dwellFinished && !clipFinished) {
        PetalixOriginalServiceClip(videoUri, modifier) {
            clipFinished = true
            dwellFinished = false
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun PetalixOriginalServiceClip(
    videoUri: String,
    modifier: Modifier,
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnFinished by rememberUpdatedState(onFinished)
    var firstFrameRendered by remember(videoUri) { mutableStateOf(false) }
    val player = remember(videoUri, context.applicationContext, lifecycleOwner) {
        ExoPlayer.Builder(context.applicationContext).build()
    }

    DisposableEffect(player, lifecycleOwner) {
        var finished = false
        var released = false
        fun finishOnce() {
            if (!finished) {
                finished = true
                currentOnFinished()
            }
        }
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                firstFrameRendered = true
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) finishOnce()
            }
            override fun onPlayerError(error: PlaybackException) {
                // The existing card image/GIF remains visible on unsupported devices.
                finishOnce()
            }
        }
        fun releasePlayer() {
            if (!released) {
                released = true
                player.removeListener(listener)
                player.playWhenReady = false
                player.release()
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP || event == Lifecycle.Event.ON_DESTROY) {
                releasePlayer()
                finishOnce()
            }
        }
        player.addListener(listener)
        lifecycleOwner.lifecycle.addObserver(observer)
        player.setAudioAttributes(AudioAttributes.DEFAULT, true)
        player.repeatMode = Player.REPEAT_MODE_OFF
        player.volume = 0.8f
        player.setMediaItem(MediaItem.fromUri(videoUri))
        player.prepare()
        player.playWhenReady = true
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            releasePlayer()
        }
    }

    AndroidView(
        modifier = modifier.graphicsLayer { alpha = if (firstFrameRendered) 1f else 0f },
        factory = { viewContext ->
            (LayoutInflater.from(viewContext)
                .inflate(R.layout.petalix_service_media_tile, null) as PlayerView).apply {
                useController = false
                setControllerAutoShow(false)
                hideController()
                isFocusable = false
                isFocusableInTouchMode = false
                isClickable = false
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShutterBackgroundColor(AndroidColor.TRANSPARENT)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        update = { view -> view.player = player },
        onRelease = { view -> view.player = null }
    )
}
