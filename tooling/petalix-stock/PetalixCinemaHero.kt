package com.nuvio.tv.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.petalix.PetalixCinemaPolicy

/** Read current focus directly: enrichment must never leave a previous service on screen. */
@Composable
internal fun PetalixCinemaHero(
    scene: () -> ModernHeroSceneState,
    selection: () -> ModernPayload?,
    rowTitle: () -> String?,
    showRatings: Boolean,
    modifier: Modifier = Modifier
) {
    val current = scene()
    val preview = current.preview ?: return
    val payload = selection()
    val liveGroup = com.nuvio.tv.petalix.PetalixLiveCategoryFocus.category.value != null
    val liveChannel = !liveGroup && com.nuvio.tv.petalix.PetalixLiveCategoryFocus.recent.value != null
    val service = !liveGroup && !liveChannel && payload is ModernPayload.CollectionFolder
    val resume = !liveGroup && !liveChannel && payload is ModernPayload.ContinueWatching
    val context = when {
        liveChannel -> "RECENTLY WATCHED · LIVE TV"
        liveGroup -> "LIVE TV"
        service -> "STREAMING COLLECTION"
        resume -> "CONTINUE WATCHING"
        else -> rowTitle()?.uppercase() ?: "FIND YOUR NEXT WATCH"
    }
    val metadata = listOfNotNull(preview.yearText, preview.ageRatingText, preview.runtimeText,
        preview.imdbText?.takeIf { showRatings && it.isNotBlank() }?.let { "IMDb $it" })
        .filter { it.isNotBlank() }.joinToString("  ·  ")
    val description = preview.description?.takeIf { it.isNotBlank() } ?: when {
        liveChannel -> "Return to the channel you were watching."
        liveGroup -> "Browse your channels and see what's on now."
        service -> "Discover movies and series in this collection."
        else -> null
    }
    var logoFailed by remember(preview.logo) { mutableStateOf(false) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (liveGroup || liveChannel) Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFFFF4D66)))
            Text(context, color = Color.White.copy(alpha = .68f), fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (!preview.logo.isNullOrBlank() && !logoFailed) {
            AsyncImage(model = preview.logo, contentDescription = preview.title,
                contentScale = ContentScale.Fit, alignment = Alignment.CenterStart,
                onError = { logoFailed = true }, modifier = Modifier.height(68.dp).widthIn(max = 250.dp).fillMaxWidth())
        } else {
            Text(preview.title, color = Color.White, fontSize = 30.sp, lineHeight = 33.sp,
                fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (metadata.isNotBlank()) Text(metadata, color = Color.White.copy(alpha = .82f), fontSize = 11.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        description?.let {
            Text(it, color = Color.White.copy(alpha = .84f), fontSize = 13.sp, lineHeight = 18.sp,
                maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        // A remote hint, deliberately not a second focusable navigation bar.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 3.dp)) {
            Text("OK", color = Color(0xFF101116), fontSize = 10.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Color.White).padding(horizontal = 7.dp, vertical = 4.dp))
            Text(PetalixCinemaPolicy.actionLabel(service, liveGroup, liveChannel, resume, preview.isSeries),
                color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
internal fun PetalixCinemaHeader(modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Home", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Box(Modifier.width(1.dp).height(13.dp).background(Color.White.copy(alpha = .25f)))
        Text("Live & on demand", fontSize = 11.sp, letterSpacing = 0.4.sp, color = Color.White.copy(alpha = .60f))
    }
}
