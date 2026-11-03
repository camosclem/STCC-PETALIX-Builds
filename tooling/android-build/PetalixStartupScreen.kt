package com.nuvio.tv.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.nuvio.tv.R
import com.nuvio.tv.petalix.ui2.PetalixServiceAudio
import kotlin.math.sin

@Composable
fun StartupSplashScreen(
    profileColorHex: String?, profileBackgroundUrl: String?, backgroundCacheKey: String? = null,
    skipGradient: Boolean = false, brandWordmarkRes: Int? = null, modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        android.util.Log.i("PetalixStartup", "petalix-visible-intro-v2")
        revealed = true
        PetalixServiceAudio.play(context.applicationContext, "startup")
    }
    val reveal by animateFloatAsState(if (revealed) 1f else 0f, tween(850, easing = FastOutSlowInEasing), label = "petalixLogoRevealV2")
    val clock = rememberInfiniteTransition(label = "petalixCinematicStartupV2")
    val phase by clock.animateFloat(0f, 1f, infiniteRepeatable(tween(4000, easing = LinearEasing)), label = "startupLightMotion")
    Box(modifier.fillMaxSize().background(Color(0xFF020613)), contentAlignment = Alignment.Center) {
        if (!profileBackgroundUrl.isNullOrBlank()) {
            val request = remember(profileBackgroundUrl, backgroundCacheKey) {
                ImageRequest.Builder(context).data(profileBackgroundUrl).crossfade(false).apply {
                    backgroundCacheKey?.let { memoryCacheKey(it); diskCacheKey(it) }
                }.build()
            }
            AsyncImage(request, null, Modifier.fillMaxSize().graphicsLayer { alpha = 0.18f }, contentScale = ContentScale.Crop)
        }
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.radialGradient(listOf(Color(0xFF07467A).copy(alpha = 0.65f), Color.Transparent),
                center = Offset(size.width * (0.25f + phase * 0.5f), size.height * 0.45f), radius = size.width * 0.55f))
            val x = size.width * (phase * 1.5f - 0.25f)
            drawLine(Color(0xFF2D9CFF).copy(alpha = 0.16f), Offset(x, 0f),
                Offset(x + size.width * 0.25f, size.height), strokeWidth = size.width * 0.09f)
            drawLine(Color(0xFFFF3157).copy(alpha = 0.10f), Offset(size.width - x, 0f),
                Offset(size.width - x - size.width * 0.2f, size.height), strokeWidth = size.width * 0.05f)
            repeat(24) { n ->
                val px = ((n * 0.618034f + phase * 0.08f) % 1f) * size.width
                val py = ((n * 0.381966f + phase * 0.12f) % 1f) * size.height
                val alpha = 0.15f + 0.3f * ((sin(phase * 6.283f + n) + 1f) / 2f)
                drawCircle(Color(0xFF6CCBFF).copy(alpha = alpha), (1 + n % 3).dp.toPx(), Offset(px, py))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Image(painterResource(R.drawable.petalix_panel_logo), "Petalix",
                Modifier.width(330.dp).height(95.dp).graphicsLayer {
                    alpha = reveal
                    scaleX = 0.78f + reveal * 0.22f
                    scaleY = 0.78f + reveal * 0.22f
                    translationY = (1f - reveal) * 24.dp.toPx()
                }, contentScale = ContentScale.Fit)
            Text("LIVE TV  •  MOVIES  •  SERIES", color = Color(0xFFD5E6FF), fontSize = 14.sp,
                letterSpacing = 3.sp, fontWeight = FontWeight.Medium)
            Canvas(Modifier.width(260.dp).height(3.dp)) {
                drawRoundRect(Color.White.copy(alpha = 0.12f))
                val start = size.width * (phase * 1.5f - 0.25f)
                drawLine(Brush.horizontalGradient(listOf(Color.Transparent, Color(0xFF38BFFF), Color.White, Color.Transparent)),
                    Offset(start, size.height / 2), Offset(start + size.width * 0.4f, size.height / 2), strokeWidth = size.height)
            }
            Text("Starting Petalix", color = Color(0xFF8EA8CA), fontSize = 12.sp)
        }
        Text("Test build ${com.nuvio.tv.BuildConfig.VERSION_CODE}", color = Color(0xFF8195B0), fontSize = 10.sp,
            modifier = Modifier.align(Alignment.BottomEnd).padding(22.dp))
    }
}
