package com.nuvio.tv.livetv.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.nuvio.tv.R
import com.nuvio.tv.livetv.model.EpgProgram
import com.nuvio.tv.livetv.model.LiveChannel
import com.nuvio.tv.livetv.model.LiveTvSettings

internal val LocalPetalixGuide = staticCompositionLocalOf { false }
internal val GuideInk = Color(0xFF091124)
internal val GuidePanel = Color(0xFF151F34)
internal val GuideGold = Color(0xFFEAC680)
internal val GuideSilver = Color(0xFFE7ECF5)
private val guideMuted = Color(0xFFB9C4D7)

internal fun Modifier.petalixPreviewFade(base: Color): Modifier = drawWithCache {
    val clear = base.copy(alpha = 0f)
    val side = Brush.horizontalGradient(listOf(base, clear), endX = size.width * 0.25f)
    val bottom = Brush.verticalGradient(listOf(clear, base), startY = size.height * 0.72f)
    onDrawWithContent {
        drawContent()
        drawRect(side)
        drawRect(bottom)
    }
}

@Composable
private fun GuideCommand(
    label: String,
    onClick: () -> Unit,
    onDown: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier.onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionDown) {
                    onDown()
                    true
                } else false
            }
            .background(if (focused) GuideSilver else Color.Transparent, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        LiveText(label, size = 13.sp, color = if (focused) GuideInk else GuideSilver, weight = FontWeight.Medium)
    }
}

@Composable
internal fun PetalixGuideHeader(
    channel: LiveChannel?,
    program: EpgProgram?,
    start: Long?,
    stop: Long?,
    poster: String?,
    playlist: String?,
    settings: LiveTvSettings,
    now: Long,
    group: String,
    playing: Boolean,
    onBounds: (androidx.compose.ui.geometry.Rect?) -> Unit,
    onCategories: () -> Unit,
    onSearch: () -> Unit,
    onRecordings: () -> Unit,
    onMultiview: () -> Unit,
    onSettings: () -> Unit,
    onDown: () -> Unit,
    settingsFocus: FocusRequester
) {
    val compact = settings.smallHeader
    val hidden = remember(settings.guideInfoHidden) { BarPart.parse(settings.guideInfoHidden) }
    val base = if (settings.guideBackdrop == "auto") GuideInk else backdropBase(resolveBackdrop(settings.guideBackdrop))
    Row(
        Modifier.fillMaxWidth().padding(start = 54.dp, end = 22.dp, top = 5.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(painterResource(R.drawable.petalix_guide_wordmark), "Petalix", Modifier.size(86.dp, 27.dp))
        Spacer(Modifier.width(12.dp))
        LiveText("Live TV", size = 14.sp, color = GuideGold, weight = FontWeight.Bold)
        Spacer(Modifier.width(12.dp))
        GuideCommand("Categories", onCategories, onDown)
        GuideCommand("Search", onSearch, onDown)
        GuideCommand("Recordings", onRecordings, onDown)
        GuideCommand("Multiview", onMultiview, onDown)
        GuideCommand("Guide settings", onSettings, onDown, Modifier.focusRequester(settingsFocus))
        Spacer(Modifier.weight(1f))
        LiveText(formatClock(now, settings.use24HourClock), size = 13.sp, color = GuideSilver)
    }
    if (settings.showProgramDetails || settings.showPreview) {
        val screenHeight = LocalConfiguration.current.screenHeightDp.dp
        val height = if (compact) (screenHeight * 0.23f).coerceAtMost(142.dp)
            else (screenHeight * 0.37f).coerceAtMost(242.dp)
        Box(Modifier.fillMaxWidth().height(height)) {
            val hasVideo = playing && settings.showPreview
            val hasArtwork = settings.showPosters && settings.showProgramDetails && !poster.isNullOrBlank()
            if (settings.showPreview || hasArtwork) {
                Box(
                    Modifier.align(Alignment.CenterEnd)
                        .fillMaxWidth(if (settings.showProgramDetails) 0.49f else 0.76f)
                        .fillMaxHeight()
                ) {
                    if (hasVideo) {
                        // The existing playback surface moves to this rectangle. It also owns full screen.
                        Box(Modifier.fillMaxSize().onGloballyPositioned { onBounds(it.boundsInRoot()) })
                        DisposableEffect(Unit) { onDispose { onBounds(null) } }
                    } else if (hasArtwork) {
                        AsyncImage(poster, null, Modifier.fillMaxSize().petalixPreviewFade(base), contentScale = ContentScale.Crop)
                    } else if (settings.showPreview) {
                        LiveText("Select a channel to preview", Modifier.align(Alignment.Center), guideMuted, 13.sp)
                    }
                }
            }
            if (settings.showProgramDetails && channel != null) {
                val details = rememberShowDetails(channel.key, program)
                val about = rememberChannelAbout(channel, details)
                val onNow = start != null && stop != null && now >= start && now < stop
                Column(
                    Modifier.fillMaxWidth(if (settings.showPreview || hasArtwork) 0.53f else 0.86f)
                        .align(Alignment.CenterStart).padding(start = 54.dp, end = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 4.dp)
                ) {
                    if (GuideInfoPart.CHANNEL !in hidden || GuideInfoPart.TAGS !in hidden) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            if (GuideInfoPart.CHANNEL !in hidden) {
                                if (settings.showChannelLogos && !compact && !channel.logo.isNullOrBlank()) ChannelLogo(channel.logo, 25.dp)
                                LiveText(buildString {
                                    if (settings.showChannelNumbers) append("${channel.number}  ")
                                    append(channel.name)
                                    playlist?.let { append("  ·  $it") }
                                }, Modifier.weight(1f, fill = false), guideMuted, 13.sp, FontWeight.Medium)
                            }
                            if (GuideInfoPart.TAGS !in hidden && onNow) LiveText("LIVE", color = GuideGold, size = 11.sp, weight = FontWeight.Bold)
                            if (GuideInfoPart.TAGS !in hidden && channel.catchup != null) LiveText("Catch-up", color = guideMuted, size = 11.sp)
                        }
                    }
                    if (GuideInfoPart.TITLE !in hidden) {
                        LiveText(details?.title?.let { titleMarks(it).title } ?: channel.name,
                            size = if (compact) 23.sp else 30.sp, color = Color.White, weight = FontWeight.SemiBold,
                            maxLines = if (compact) 1 else 2, lineHeight = if (compact) 25.sp else 32.sp)
                    }
                    val meta = buildList {
                        if (GuideInfoPart.TIME !in hidden && start != null && stop != null)
                            add(formatDayClock(start, now, settings.use24HourClock) + " – " + formatClock(stop, settings.use24HourClock))
                        if (GuideInfoPart.LEFT !in hidden && onNow && stop != null) add(minutesLeftLabel(stop, now))
                        if (settings.showEpisodeNumbers) details?.episode?.let { add(it) }
                        if (GuideInfoPart.CATEGORY !in hidden) details?.category?.let { add(it) }
                    }.joinToString("  ·  ")
                    if (meta.isNotEmpty()) LiveText(meta, color = guideMuted, size = 12.sp)
                    if (GuideInfoPart.PROGRESS !in hidden && onNow && start != null && stop != null) {
                        ProgressBar((now - start).toFloat() / (stop - start), Modifier.width(165.dp), GuideGold, GuidePanel)
                    }
                    if (GuideInfoPart.DESCRIPTION !in hidden) {
                        val description = about?.description?.takeIf { it.isNotBlank() } ?: details?.description
                        if (!description.isNullOrBlank()) LiveText(description, color = GuideSilver, size = 14.sp,
                            maxLines = if (compact) 1 else 3, lineHeight = 17.sp)
                    }
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(start = 54.dp, end = 24.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(3.dp, 13.dp).background(GuideGold))
        Spacer(Modifier.width(8.dp))
        LiveText(group, color = GuideSilver, size = 14.sp, weight = FontWeight.SemiBold)
    }
}
