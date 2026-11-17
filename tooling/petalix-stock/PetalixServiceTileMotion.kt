package com.nuvio.tv.petalix

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import kotlinx.coroutines.delay

/** Move the artwork and its transparent logo together, inside the native card clip. */
@Composable
internal fun petalixServiceTileMotion(enabled: Boolean, focused: Boolean): Modifier {
    if (!enabled) return Modifier
    val lifecycle = LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val active = focused && lifecycle.value.isAtLeast(Lifecycle.State.RESUMED)
    val motion = remember { Animatable(0f) }
    LaunchedEffect(active) {
        if (active) {
            delay(350)
            while (true) {
                motion.animateTo(1f, tween(4500, easing = FastOutSlowInEasing))
                motion.animateTo(0f, tween(4500, easing = FastOutSlowInEasing))
            }
        } else {
            motion.animateTo(0f, tween(220))
        }
    }
    return Modifier.graphicsLayer {
        // A gentle 2.4% drift keeps marks and captions in view; never crops to a logo box.
        val progress = motion.value
        scaleX = 1f + progress * .024f
        scaleY = scaleX
        translationX = size.width * progress * .004f
    }.drawWithContent {
        drawContent()
        val progress = motion.value
        if (progress > 0f) {
            val center = Offset(size.width * (.1f + progress * .8f), size.height * .25f)
            drawRect(Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = .07f * progress), Color.Transparent),
                center = center, radius = size.width * .7f
            ))
        }
    }
}
