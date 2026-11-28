package com.nuvio.tv.livetv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Precision
import coil3.size.Scale
import com.nuvio.tv.livetv.model.LiveChannel
import com.nuvio.tv.petalix.PetalixGuideArtworkType
import com.nuvio.tv.petalix.petalixGuideArtworkType
import com.nuvio.tv.ui.theme.NuvioTheme

/** One preview asset at a time. A logo is a last fallback, never a poster-loading layer. */
@Composable
internal fun PetalixGuideProgramArt(
    candidates: List<String>, channel: LiveChannel?, name: String, showLogo: Boolean,
    modifier: Modifier = Modifier,
) {
    var index by remember(candidates) { mutableIntStateOf(0) }
    val url = candidates.getOrNull(index)
    BoxWithConstraints(
        modifier.clip(RoundedCornerShape(12.dp))
            .background(NuvioTheme.colors.BackgroundElevated.copy(alpha = 1f)),
        contentAlignment = Alignment.Center,
    ) {
        if (url == null) {
            var hasLogo by remember(channel?.key, showLogo) { mutableStateOf(false) }
            val logoWidth = (maxWidth * .64f).coerceAtMost(250.dp)
            val logoHeight = (maxHeight * .58f).coerceAtMost(140.dp)
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (channel != null && showLogo) {
                    PetalixGuideLogo(channel, logoWidth, logoHeight, hero = true) { hasLogo = it }
                }
                if (!hasLogo) LiveText(name, color = NuvioTheme.colors.TextPrimary, size = 18.sp,
                    modifier = Modifier.padding(horizontal = 12.dp), maxLines = 2)
            }
        } else key(url) {
            var artworkType by remember { mutableStateOf<PetalixGuideArtworkType?>(null) }
            val context = LocalContext.current
            val revalidation = com.nuvio.tv.core.image.rememberImageRevalidationKey(url)
            val request = remember(context, url, revalidation) {
                ImageRequest.Builder(context).data(url)
                    .memoryCacheKey("$url:petalix-guide-single-art:$revalidation")
                    .size(1280, 720).scale(Scale.FIT).precision(Precision.INEXACT).build()
            }
            AsyncImage(
                model = request, contentDescription = null,
                modifier = Modifier.fillMaxSize().padding(10.dp),
                contentScale = if (artworkType == PetalixGuideArtworkType.LANDSCAPE)
                    ContentScale.Crop else ContentScale.Fit,
                alpha = if (artworkType != null) 1f else 0f,
                onSuccess = {
                    val image = it.result.image
                    val type = petalixGuideArtworkType(image.width, image.height)
                    if (type == null) index++ else artworkType = type
                },
                onError = { index++ },
            )
        }
    }
}
