package com.nuvio.tv.petalix.ui2

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.nuvio.tv.R

/** Original short chords, shared by focus, selection and the startup reveal. */
object PetalixServiceAudio {
    private var pool: SoundPool? = null
    private val sounds = mutableMapOf<String, Int>()
    private val ready = mutableSetOf<Int>()
    private var pending: String? = null
    private var lastPlay = 0L
    @Synchronized fun prepare(context: Context) {
        if (pool != null) return
        android.util.Log.i("PetalixMotion", "petalix-motion-v2")
        val player = SoundPool.Builder().setMaxStreams(1).setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
        pool = player
        player.setOnLoadCompleteListener { _, sample, status ->
            synchronized(this) {
                if (status == 0) ready.add(sample)
                val requested = pending
                if (requested != null && sounds[requested] == sample && status == 0) {
                    pending = null
                    play(context, requested)
                }
            }
        }
        val resources = mapOf(
            "netflix" to R.raw.petalix_cue_netflix, "prime" to R.raw.petalix_cue_prime,
            "hulu" to R.raw.petalix_cue_hulu, "disney" to R.raw.petalix_cue_disney,
            "max" to R.raw.petalix_cue_max, "paramount" to R.raw.petalix_cue_paramount,
            "apple" to R.raw.petalix_cue_apple, "peacock" to R.raw.petalix_cue_peacock,
            "tubi" to R.raw.petalix_cue_tubi, "pluto" to R.raw.petalix_cue_pluto,
            "startup" to R.raw.petalix_cue_startup)
        resources.forEach { (name, resource) -> sounds[name] = player.load(context.applicationContext, resource, 1) }
    }
    @Synchronized fun play(context: Context, service: String) {
        prepare(context)
        val sample = sounds[service] ?: return
        if (sample !in ready) { pending = service; return }
        val now = SystemClock.uptimeMillis()
        if (now - lastPlay < 180) return
        lastPlay = now
        pending = null
        pool?.autoPause()
        pool?.play(sample, 0.65f, 0.65f, 1, 0, 1f)
    }
    @Synchronized fun cancelPending(service: String) {
        if (pending == service) pending = null
    }
}

@Composable
fun MotionServiceTile(service: PetalixService, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val context = LocalContext.current.applicationContext
    var focused by remember { mutableStateOf(false) }
    val clock = rememberInfiniteTransition(label = "serviceArtworkMotionV2")
    val sweep by clock.animateFloat(-0.4f, 1.4f,
        infiniteRepeatable(tween(3600, easing = LinearEasing)), label = "serviceLightSweep")
    val breathe by clock.animateFloat(0f, 1f,
        infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "serviceLogoBreath")
    val focusAmount by animateFloatAsState(if (focused) 1f else 0f, tween(220), label = "serviceFocusLift")
    DisposableEffect(context, service.id) {
        PetalixServiceAudio.prepare(context)
        onDispose { PetalixServiceAudio.cancelPending(service.id) }
    }
    LaunchedEffect(focused) {
        if (focused) {
            kotlinx.coroutines.delay(100)
            PetalixServiceAudio.play(context, service.id)
        } else PetalixServiceAudio.cancelPending(service.id)
    }
    PetalixTile(
        modifier.width(148.dp).height(78.dp).graphicsLayer {
            scaleX = 1f + focusAmount * 0.07f
            scaleY = 1f + focusAmount * 0.07f
            translationY = -focusAmount * 3.dp.toPx()
            shadowElevation = focusAmount * 18.dp.toPx()
        }.semantics { contentDescription = service.name },
        accent = service.accent,
        onClick = { PetalixServiceAudio.play(context, service.id); onClick() },
        onFocus = { focused = it }
    ) {
        Box(Modifier.fillMaxSize().background(service.background))
        val logoMotion = Modifier.fillMaxSize().graphicsLayer {
            val amount = if (focused) 0.035f else 0.012f
            scaleX = 1f + breathe * amount
            scaleY = 1f + breathe * amount
            translationY = (breathe - 0.5f) * if (focused) 3.dp.toPx() else 1.dp.toPx()
        }
        if (service.id == "pluto") {
            Box(logoMotion, contentAlignment = Alignment.Center) {
                Text("pluto tv", color = service.accent, fontSize = 22.sp)
            }
        } else ServiceArtwork(service.id, logoMotion)
        Canvas(Modifier.fillMaxSize()) {
            val x = size.width * sweep
            drawLine(
                brush = Brush.linearGradient(
                    listOf(Color.Transparent, Color.White.copy(alpha = if (focused) 0.23f else 0.09f), Color.Transparent),
                    start = Offset(x - 24.dp.toPx(), 0f), end = Offset(x + 24.dp.toPx(), size.height)),
                start = Offset(x - 24.dp.toPx(), 0f), end = Offset(x + 24.dp.toPx(), size.height),
                strokeWidth = 36.dp.toPx())
            if (focused) drawLine(service.accent.copy(alpha = 0.5f + 0.5f * breathe),
                Offset(size.width * 0.12f, size.height - 3.dp.toPx()),
                Offset(size.width * 0.88f, size.height - 3.dp.toPx()), strokeWidth = 3.dp.toPx())
        }
        Box(Modifier.fillMaxSize().border(if (focused) 3.dp else 1.dp,
            service.accent.copy(alpha = if (focused) 0.7f + 0.3f * breathe else 0.2f), RoundedCornerShape(8.dp)))
    }
}
