// SPDX-License-Identifier: GPL-3.0-only
// Adapted for the Petalix player from IMD TV v0.5.2 GuidePreview.kt and GuideGrid.kt.
package com.nuvio.tv.livetv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import coil3.compose.AsyncImage
import com.nuvio.tv.livetv.model.EpgProgram
import com.nuvio.tv.livetv.model.LiveChannel
import com.nuvio.tv.livetv.model.LiveTvSettings
import com.nuvio.tv.petalix.petalixCleanChannelName

internal val GlassFocus = Color(0xFFEEF3FF)
internal val GlassInk = Color(0xFF101A38)
internal val GlassMuted = Color(0xC3DCE6FC)
internal val GlassPanel = Color(0xDD0B1731)
private val glassStops = listOf(Color(0xFF09142E), Color(0xFF18315D), Color(0xFF344E85))
private val cellShape = RoundedCornerShape(4.dp)

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGlassBackground(screen: Size) {
    drawRect(Brush.linearGradient(glassStops, end = Offset(screen.width, screen.height)), size = screen)
    drawRect(Brush.radialGradient(listOf(Color(0x5978A0FF), Color.Transparent),
        center = Offset(screen.width * .85f, screen.height * .1f), radius = (screen.width * .68f).coerceAtLeast(1f)), size = screen)
}

internal fun Modifier.glassGuideBackground() = drawWithCache {
    val background = ImageBitmap(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1))
    CanvasDrawScope().draw(this, layoutDirection, Canvas(background), size) { drawGlassBackground(size) }
    onDrawBehind { drawImage(background) }
}

/** A matching patch of the full guide blends the hardware video overlay into the screen. */
internal fun Modifier.glassPreviewFade(preview: Rect, root: Rect) = drawWithCache {
    val w = size.width.toInt().coerceAtLeast(1)
    val h = size.height.toInt().coerceAtLeast(1)
    val patch = ImageBitmap(w, h)
    CanvasDrawScope().draw(this, layoutDirection, Canvas(patch), size) {
        translate(root.left - preview.left, root.top - preview.top) { drawGlassBackground(root.size) }
    }
    fun mask(brush: Brush): ImageBitmap {
        val image = ImageBitmap(w, h)
        CanvasDrawScope().draw(this, layoutDirection, Canvas(image), size) {
            drawImage(patch)
            drawRect(brush, blendMode = BlendMode.DstIn)
        }
        return image
    }
    val left = mask(Brush.horizontalGradient(0f to Color.Black, .45f to Color.Transparent))
    val bottom = mask(Brush.verticalGradient(.60f to Color.Transparent, 1f to Color.Black))
    onDrawBehind { drawImage(left); drawImage(bottom) }
}

@Composable
internal fun PetalixGlassHero(
    channel: LiveChannel?, program: EpgProgram?, poster: String?, group: String,
    settings: LiveTvSettings, now: Long, previewPlaying: Boolean,
    onBounds: (Rect?) -> Unit, openCategories: () -> Unit, modifier: Modifier = Modifier,
) {
    val compact = settings.smallHeader
    val hidden = remember(settings.guideInfoHidden) { BarPart.parse(settings.guideInfoHidden) }
    val details = if (settings.showProgramDetails && channel != null)
        key(channel.key, program?.startMs) { rememberShowDetails(channel.key, program) } else null
    val about = if (settings.showProgramDetails && channel != null)
        key(channel.key, program?.startMs) { rememberChannelAbout(channel, details) } else null
    val name = channel?.let { petalixCleanChannelName(it.name).ifBlank { it.name } }.orEmpty()
    val title = program?.title?.takeUnless { it == NO_GUIDE_TITLE }?.let { titleMarks(it).title }
    BoxWithConstraints(modifier.fillMaxWidth().clipToBounds()) {
        if (previewPlaying && settings.showPreview) {
            Box(Modifier.align(Alignment.TopEnd).fillMaxWidth(.56f).fillMaxHeight(.98f)
                .onGloballyPositioned { onBounds(it.boundsInRoot()) })
            DisposableEffect(Unit) { onDispose { onBounds(null) } }
        } else if (settings.showPosters && settings.showProgramDetails) {
            val artwork = about?.backdropUrl ?: details?.icon ?: poster
            if (!artwork.isNullOrBlank()) {
                AsyncImage(artwork, null, Modifier.align(Alignment.TopEnd).fillMaxWidth(.56f).fillMaxHeight(.98f)
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithCache {
                        val left = Brush.horizontalGradient(0f to Color.Transparent, .45f to Color.Black)
                        val bottom = Brush.verticalGradient(.58f to Color.Black, 1f to Color.Transparent)
                        onDrawWithContent {
                            drawContent()
                            drawRect(left, blendMode = BlendMode.DstIn)
                            drawRect(bottom, blendMode = BlendMode.DstIn)
                        }
                    }, contentScale = ContentScale.Crop)
            }
        }
        if (settings.showProgramDetails && channel != null) {
            val short = maxHeight < 166.dp || compact
            Column(Modifier.align(Alignment.CenterStart).fillMaxWidth(.52f)
                .padding(start = 24.dp, end = 10.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(if (short) 3.dp else 6.dp)) {
                if (GuideInfoPart.CHANNEL !in hidden) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (settings.showChannelLogos && !channel.logo.isNullOrBlank()) {
                            Box(Modifier.clip(RoundedCornerShape(3.dp)).background(Color(0x26FFFFFF)).padding(3.dp)) {
                                ChannelLogo(channel.logo, if (short) 21.dp else 32.dp)
                            }
                        }
                        if (title != null) LiveText(name, color = GlassMuted, size = 13.sp)
                    }
                }
                if (GuideInfoPart.TITLE !in hidden)
                    LiveText(title ?: name, color = Color.White, size = if (short) 24.sp else 31.sp,
                        weight = FontWeight.Medium, maxLines = 1)
                if (program != null && GuideInfoPart.TIME !in hidden) {
                    val minutes = (program.stopMs - program.startMs).coerceAtLeast(0L) / 60_000L
                    val duration = when {
                        minutes < 60 -> "${minutes}m"
                        minutes % 60 == 0L -> "${minutes / 60}h"
                        else -> "${minutes / 60}h ${minutes % 60}m"
                    }
                    val verb = if (program.startMs > now) "Starts" else "Started"
                    LiveText("$duration · $verb at ${formatClock(program.startMs, settings.use24HourClock)}",
                        color = Color.White, size = 13.sp, weight = FontWeight.SemiBold)
                }
                val description = program?.description?.takeIf { it.isNotBlank() } ?: details?.description ?: about?.description
                if (GuideInfoPart.DESCRIPTION !in hidden && !description.isNullOrBlank())
                    LiveText(description, color = Color(0xFFEAF0FF), size = 14.sp, lineHeight = 19.sp,
                        maxLines = if (short) 1 else 3)
                if (!short && program?.isLive(now) == true)
                    Box(Modifier.size(210.dp, 2.5.dp).background(Color(0x24FFFFFF), cellShape)) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(program.progress(now)).background(Color.White, cellShape))
                    }
            }
        }
        Row(Modifier.align(Alignment.BottomStart).padding(start = 24.dp, bottom = 3.dp)
            .pointerInput(openCategories) { detectTapGestures { openCategories() } },
            verticalAlignment = Alignment.CenterVertically) {
            LiveText("◀  ${group.ifBlank { "Categories" }}", color = Color.White, size = 12.sp)
            LiveText("    Press left for categories", color = GlassMuted, size = 11.sp)
        }
    }
}

@Composable
internal fun PetalixGlassRow(
    channel: LiveChannel, line: PetalixAirtimes, settings: LiveTvSettings, marks: CableChannelMarks,
    now: Long, start: Long, duration: Long, channelWidth: Dp, timelineWidth: Dp,
    rowHeight: Dp, selectedAt: Long?, playing: Boolean,
    onSelect: (PetalixAiring, Boolean) -> Unit,
) {
    val spans = remember(line, start, duration) { line.visible(start, start + duration) }
    val name = remember(channel.name) { petalixCleanChannelName(channel.name).ifBlank { channel.name } }
    val active = selectedAt != null
    val fontScale = guideScale(settings)
    Row(Modifier.fillMaxWidth().height(rowHeight)) {
        Row(Modifier.width((channelWidth - 4.dp).coerceAtLeast(1.dp)).fillMaxHeight()
            .background(if (active) GlassFocus else Color(0x63334569), cellShape)
            .cableTarget(active, { onSelect(line.at(now), false) }, { onSelect(line.at(now), true) })
            .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            val ink = if (active) GlassInk else Color.White
            if (playing && settings.markPlayingChannel)
                Box(Modifier.size(4.dp).background(if (active) GlassInk else GlassFocus, RoundedCornerShape(50)))
            if (settings.showChannelLogos && !channel.logo.isNullOrBlank())
                ChannelLogo(channel.logo, (rowHeight * .56f).coerceIn(12.dp, 25.dp))
            if (settings.showChannelNames || !settings.showChannelLogos || channel.logo.isNullOrBlank())
                LiveText(name, Modifier.weight(1f), ink, (14 * fontScale).sp, FontWeight.Medium,
                    maxLines = if (settings.twoLineChannelNames) 2 else 1)
            if (settings.showChannelNumbers) LiveText(channel.number.toString(), color = ink, size = (11 * fontScale).sp)
            if (channel.key in marks.favorites) LiveText("★", color = ink, size = 11.sp)
        }
        Spacer(Modifier.width(4.dp))
        Box(Modifier.weight(1f).fillMaxHeight().clipToBounds()) {
            for (span in spans) {
                val left = maxOf(span.start, start)
                val right = minOf(span.end, start + duration)
                val x = timelineWidth * ((left - start).toFloat() / duration)
                val w = timelineWidth * ((right - left).toFloat() / duration)
                val selected = selectedAt != null && span.contains(selectedAt)
                val past = span.end <= now
                val p = span.program
                val bg = when {
                    selected -> Color(0x8C7891AA)
                    past -> Color(0x204C6287)
                    else -> Color(0x483C5072)
                }
                val border = if (selected) Color(0xCCFFFFFF) else Color.White.copy(alpha = if (past) .08f else .16f)
                Box(Modifier.offset(x = x).width((w - 2.dp).coerceAtLeast(0.dp)).fillMaxHeight()
                    .background(bg, cellShape).border(1.dp, border, cellShape).clipToBounds()
                    .cableTarget(selected, { onSelect(span, false) }, { onSelect(span, true) }),
                    contentAlignment = Alignment.CenterStart) {
                    if (w > 21.dp) {
                        val recording = p != null && (p.startMs in marks.scheduled[channel.key].orEmpty() ||
                            marks.recording[channel.key]?.let { p.stopMs > it.first && p.startMs < (it.second ?: Long.MAX_VALUE) } == true)
                        val prefix = when {
                            recording -> "● "
                            p != null && p.startMs in marks.reminders[channel.key].orEmpty() -> "• "
                            else -> ""
                        }
                        Column(Modifier.padding(horizontal = 8.dp)) {
                            val title = p?.title?.takeUnless { it == NO_GUIDE_TITLE }?.let { titleMarks(it).title } ?: "No listing"
                            val episode = p?.episode?.takeIf { settings.showEpisodeNumbers && w > 145.dp }
                            LiveText(prefix + title + (episode?.let { " · $it" } ?: ""), color = if (past && !selected) GlassMuted else Color.White,
                                size = (13 * fontScale).sp, weight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                            if (settings.showProgramTimes && p != null && w > 65.dp)
                                LiveText(formatRange(p.startMs, p.stopMs, settings.use24HourClock), color = GlassMuted, size = (10.5f * fontScale).sp)
                        }
                    }
                }
            }
        }
    }
}
