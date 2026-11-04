// Adapted from ARVIO (ProdigyV21/ARVIO), commit 3fc1a3e52af9e3d8cf3cd25f456bc92c5573d927. Apache-2.0.
// Changes: Petalix package; local bundled clips; muted looping tile mode; texture surface.
package com.nuvio.tv.petalix.ui2

import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/**
 * Plays a collection's hero video once with sound and invokes [onEnded]
 * when playback completes or errors. Lifecycle-aware: releases the player
 * on composition leave and on ON_STOP (which also flips playback to ended
 * so the detail screen falls back to the static cover when the app
 * returns to the foreground).
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun ArvioVideoHero(
    videoUrl: String,
    modifier: Modifier = Modifier,
    muted: Boolean = false,
    loop: Boolean = false,
    onEnded: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Always-current reference so listeners never hold a stale lambda.
    val currentOnEnded by rememberUpdatedState(onEnded)

    val player = remember(videoUrl,muted,loop) {
        ExoPlayer.Builder(context).build().apply {
            setAudioAttributes(androidx.media3.common.AudioAttributes.DEFAULT,!muted)
            setMediaItem(MediaItem.fromUri(videoUrl))
            repeatMode = if(loop)Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            volume = if(muted)0f else .8f
            playWhenReady = true
            prepare()
        }
    }

    DisposableEffect(videoUrl, player) {
        // Guard: fire currentOnEnded exactly once per videoUrl lifetime.
        // STATE_ENDED can theoretically arrive more than once during async
        // ExoPlayer cleanup, so we gate with a local flag.
        var ended = false
        val fireOnce = {
            if (!ended) {
                ended = true
                currentOnEnded()
            }
        }

        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) fireOnce()
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                android.util.Log.w(
                    "VideoHero",
                    "hero video playback error — falling back to static: ${error.message}"
                )
                fireOnce()
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                player.pause()
                currentOnEnded()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            (android.view.LayoutInflater.from(ctx).inflate(com.nuvio.tv.R.layout.petalix_service_video,null) as PlayerView).apply {
                useController = false
                setControllerAutoShow(false)
                hideController()
                isFocusable = false
                isFocusableInTouchMode = false
                descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                layoutParams = android.view.ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            }
        },
        update = { view ->
            view.useController = false
            view.setControllerAutoShow(false)
            view.hideController()
            view.player = player
        }
    )
}
