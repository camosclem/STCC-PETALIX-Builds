package com.openguide.tv

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class PlayerActivity : Activity() {
    private lateinit var player: ExoPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        val stream = intent.getStringExtra("url") ?: run { finish(); return }
        val label = intent.getStringExtra("name") ?: "Live TV"

        player = ExoPlayer.Builder(this).build()
        val view = PlayerView(this).apply {
            player = this@PlayerActivity.player
            useController = true
            controllerShowTimeoutMs = 4000
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
        }
        val overlay = TextView(this).apply {
            text = label
            textSize = 18f
            setTextColor(0xffffffff.toInt())
            setBackgroundColor(0xaa101522.toInt())
            setPadding(22, 10, 22, 10)
        }
        val screen = FrameLayout(this).apply {
            setBackgroundColor(0xff000000.toInt())
            addView(view, FrameLayout.LayoutParams(-1, -1))
            addView(overlay, FrameLayout.LayoutParams(-2, -2, Gravity.START or Gravity.TOP).apply {
                topMargin = 16; leftMargin = 24
            })
        }
        setContentView(screen)
        val item = MediaItem.Builder().setUri(stream).apply {
            if (stream.substringBefore('?').endsWith(".m3u8", true)) setMimeType(MimeTypes.APPLICATION_M3U8)
            if (stream.substringBefore('?').endsWith(".mpd", true)) setMimeType(MimeTypes.APPLICATION_MPD)
        }.build()
        player.setMediaItem(item)
        player.prepare()
        player.playWhenReady = true
    }

    override fun onStop() {
        if (::player.isInitialized) player.pause()
        super.onStop()
    }

    override fun onDestroy() {
        if (::player.isInitialized) player.release()
        super.onDestroy()
    }
}
